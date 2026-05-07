package com.campushanjang.domain.photo;

import com.campushanjang.common.exception.BusinessException;
import com.campushanjang.common.exception.ErrorCode;
import com.campushanjang.domain.photo.dto.PhotoUploadResponseDto;
import com.campushanjang.domain.photo.entity.UserPhoto;
import com.campushanjang.domain.user.UserRepository;
import com.campushanjang.domain.user.entity.User;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.firebase.cloud.StorageClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import net.coobird.thumbnailator.Thumbnails;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class PhotoService {

    private final PhotoRepository photoRepository;
    private final UserRepository userRepository;

    @Value("${firebase.storage-bucket}")
    private String storageBucket;

    private static final long MAX_SIZE = 10L * 1024 * 1024;
    private static final String FORMAT_JPEG = "image/jpeg";
    private static final String FORMAT_PNG  = "image/png";
    private static final String FORMAT_WEBP = "image/webp";
    private static final String FORMAT_HEIC = "image/heic";
    // HEIC ftyp 브랜드 목록 — MP4/MOV 등 영상 포맷과 구별하기 위해 명시
    private static final Set<String> HEIC_BRANDS = Set.of(
            "heic", "heis", "hevc", "hevx", "mif1", "msf1", "avif"
    );

    @Transactional
    public PhotoUploadResponseDto upload(UUID userId, MultipartFile file) throws IOException {
        // getBytes()로 한 번만 읽어 스트림 이중 소비 방지
        byte[] bytes = file.getBytes();
        String detectedFormat = validateFileContent(bytes);

        // HEIC/HEIF는 Java ImageIO 미지원 — ImageMagick으로 메모리 내 JPEG 변환 후 처리
        if (FORMAT_HEIC.equals(detectedFormat)) {
            bytes = convertHeicToJpeg(bytes);
        }
        bytes = resizeImage(bytes);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));

        String fileName = userId + "/" + UUID.randomUUID() + ".jpg";
        String storagePath = "profiles/" + fileName;

        // 기존 사진이 있으면 Firebase에서 삭제
        Optional<UserPhoto> existing = photoRepository.findByUserId(userId);
        existing.ifPresent(photo -> deleteFromFirebase(photo.getStorageUrl()));

        // Firebase에 업로드 (ByteArrayInputStream으로 처음부터 전송)
        String downloadUrl = uploadToFirebase(new ByteArrayInputStream(bytes), storagePath, bytes.length);

        // DB 저장 (유저당 1건 UNIQUE 보장)
        UserPhoto photo = existing
                .map(p -> {
                    p.updateStorageUrl(downloadUrl);
                    return p;
                })
                .orElseGet(() -> photoRepository.save(UserPhoto.builder()
                        .user(user)
                        .storageUrl(downloadUrl)
                        .build()));

        log.info("사진 업로드 완료 userId={}", userId);

        return PhotoUploadResponseDto.builder()
                .photoUrl(photo.getStorageUrl())
                .thumbnailUrl(photo.getThumbnailUrl())
                .build();
    }

    @Transactional(readOnly = true)
    public PhotoUploadResponseDto getMyPhoto(UUID userId) {
        return photoRepository.findByUserId(userId)
                .map(photo -> PhotoUploadResponseDto.builder()
                        .photoUrl(photo.getStorageUrl())
                        .thumbnailUrl(photo.getThumbnailUrl())
                        .build())
                .orElseThrow(() -> new BusinessException(ErrorCode.PROFILE_INCOMPLETE));
    }

    @Transactional
    public void updateThumbnail(UUID userId, String thumbnailUrl) {
        UserPhoto photo = photoRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROFILE_INCOMPLETE));
        photo.updateThumbnailUrl(thumbnailUrl);
    }

    // 최대 1200x1600(세로형 3:4) 내로 리사이즈, JPEG 85% 압축 — 원본이 이미 작으면 그대로 유지
    private byte[] resizeImage(byte[] original) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Thumbnails.of(new ByteArrayInputStream(original))
                    .size(1200, 1600)
                    .outputFormat("jpeg")
                    .outputQuality(0.85)
                    .toOutputStream(out);
            return out.toByteArray();
        } catch (Exception e) {
            // ImageIO가 처리 불가한 이미지 포맷 (비표준 색공간, 손상된 파일 등) — 500이 아닌 400으로 처리
            log.warn("이미지 리사이즈 실패 — 처리 불가 포맷 error={}", e.getMessage());
            throw new BusinessException(ErrorCode.PHOTO_INVALID_FORMAT);
        }
    }

    // Content-Type 대신 magic bytes로 실제 포맷 확인 — iOS는 Content-Type이 부정확한 경우가 많음
    private String validateFileContent(byte[] bytes) {
        if (bytes.length > MAX_SIZE) {
            throw new BusinessException(ErrorCode.PHOTO_TOO_LARGE);
        }
        String format = detectFormatFromBytes(bytes);
        if (format == null) {
            throw new BusinessException(ErrorCode.PHOTO_INVALID_FORMAT);
        }
        return format;
    }

    private String detectFormatFromBytes(byte[] bytes) {
        if (bytes.length < 4) return null;

        // JPEG: FF D8 FF
        if ((bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF) {
            return FORMAT_JPEG;
        }
        // PNG: 89 50 4E 47
        if ((bytes[0] & 0xFF) == 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4E && bytes[3] == 0x47) {
            return FORMAT_PNG;
        }
        // WEBP: 52 49 46 46 .. .. .. .. 57 45 42 50 (RIFF....WEBP) — 뒤의 WEBP 시그니처까지 확인
        if (bytes.length >= 12
                && bytes[0] == 0x52 && bytes[1] == 0x49 && bytes[2] == 0x46 && bytes[3] == 0x46
                && bytes[8] == 0x57 && bytes[9] == 0x45 && bytes[10] == 0x42 && bytes[11] == 0x50) {
            return FORMAT_WEBP;
        }
        // HEIC/HEIF: ISO BMFF 컨테이너 — offset 4..7 == "ftyp", brand으로 MP4/MOV와 구별
        if (bytes.length >= 12
                && bytes[4] == 0x66 && bytes[5] == 0x74 && bytes[6] == 0x79 && bytes[7] == 0x70) {
            String brand = new String(bytes, 8, 4, StandardCharsets.US_ASCII).trim();
            return HEIC_BRANDS.contains(brand) ? FORMAT_HEIC : null;
        }
        return null;
    }

    // ImageMagick으로 메모리 내 HEIC→JPEG 변환 (디스크 저장 없음)
    private byte[] convertHeicToJpeg(byte[] heicBytes) {
        try {
            Process process = new ProcessBuilder("convert", "heic:-", "jpeg:-").start();
            try {
                ByteArrayOutputStream stdoutBuf = new ByteArrayOutputStream();
                ByteArrayOutputStream stderrBuf = new ByteArrayOutputStream();

                // stdout, stderr를 동시에 읽어야 파이프 버퍼 포화로 인한 데드락을 방지할 수 있음
                Thread stdoutThread = new Thread(() -> {
                    try { process.getInputStream().transferTo(stdoutBuf); }
                    catch (IOException ignored) {}
                });
                Thread stderrThread = new Thread(() -> {
                    try { process.getErrorStream().transferTo(stderrBuf); }
                    catch (IOException ignored) {}
                });
                stdoutThread.start();
                stderrThread.start();

                try (OutputStream stdin = process.getOutputStream()) {
                    stdin.write(heicBytes);
                }

                stdoutThread.join(20_000);
                stderrThread.join(3_000);

                boolean finished = process.waitFor(5, TimeUnit.SECONDS);
                if (!finished || process.exitValue() != 0 || stdoutBuf.size() == 0) {
                    String stderr = stderrBuf.toString(StandardCharsets.UTF_8);
                    log.warn("HEIC 변환 실패 — 종료코드={} stderr={}", finished ? process.exitValue() : "타임아웃", stderr);
                    throw new BusinessException(ErrorCode.PHOTO_INVALID_FORMAT);
                }
                return stdoutBuf.toByteArray();
            } finally {
                process.destroyForcibly();
            }
        } catch (BusinessException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.PHOTO_INVALID_FORMAT);
        } catch (Exception e) {
            log.warn("HEIC 변환 실패 — ImageMagick 미설치이거나 처리 불가 error={}", e.getMessage());
            throw new BusinessException(ErrorCode.PHOTO_INVALID_FORMAT);
        }
    }

    private String uploadToFirebase(InputStream stream, String path, int size) throws IOException {
        Storage storage = StorageClient.getInstance().bucket(storageBucket).getStorage();
        BlobId blobId = BlobId.of(storageBucket, path);

        // 다운로드 토큰을 메타데이터에 포함 — 만료 없는 Firebase 영구 다운로드 URL 생성용
        String downloadToken = UUID.randomUUID().toString();
        BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                .setContentType("image/jpeg")
                .setMetadata(Map.of("firebaseStorageDownloadTokens", downloadToken))
                .build();

        storage.createFrom(blobInfo, stream);

        String encodedPath = URLEncoder.encode(path, StandardCharsets.UTF_8);
        return String.format(
                "https://firebasestorage.googleapis.com/v0/b/%s/o/%s?alt=media&token=%s",
                storageBucket, encodedPath, downloadToken
        );
    }

    private void deleteFromFirebase(String existingUrl) {
        try {
            // URL에서 경로 추출 후 삭제
            Storage storage = StorageClient.getInstance().bucket(storageBucket).getStorage();
            String path = extractPathFromUrl(existingUrl);
            if (path != null) {
                storage.delete(BlobId.of(storageBucket, path));
            }
        } catch (Exception e) {
            log.warn("Firebase 기존 파일 삭제 실패 url={} error={}", existingUrl, e.getMessage());
        }
    }

    private String extractPathFromUrl(String url) {
        try {
            // Firebase 다운로드 URL에서 object path 추출 (/o/{encodedPath}? 패턴)
            int oStart = url.indexOf("/o/") + 3;
            int oEnd = url.indexOf("?");
            if (oStart > 2 && oEnd > oStart) {
                return java.net.URLDecoder.decode(url.substring(oStart, oEnd), "UTF-8");
            }
        } catch (Exception e) {
            log.warn("URL 파싱 실패 url={}", url);
        }
        return null;
    }
}
