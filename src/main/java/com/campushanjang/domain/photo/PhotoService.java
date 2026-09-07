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

import java.util.List;
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
    private static final String FORMAT_MOV  = "video/quicktime";
    // HEIC/HEIF ftyp 브랜드 목록 — MP4/MOV 등 영상 포맷과 구별하기 위해 명시
    // "heif"/"heim" 브랜드: iPhone 고해상도(24MP/48MP) 모드 및 일부 Android 기기에서 사용
    private static final Set<String> HEIC_BRANDS = Set.of(
            "heic", "heis", "hevc", "hevx", "mif1", "msf1", "avif",
            "heif", "heim"
    );
    // iPhone Live Photo 영상 컴포넌트 브랜드 — 일반 동영상과 구별하기 위해 QuickTime 브랜드만 허용
    private static final Set<String> MOV_BRANDS = Set.of("qt  ");

    @Transactional
    public PhotoUploadResponseDto upload(UUID userId, MultipartFile file) {
        log.info("사진 업로드 시작 userId={} size={} contentType={}", userId, file.getSize(), file.getContentType());

        // getBytes()로 한 번만 읽어 스트림 이중 소비 방지
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            log.error("파일 읽기 실패 userId={}", userId, e);
            throw new BusinessException(ErrorCode.PHOTO_UPLOAD_FAILED);
        }

        String detectedFormat = validateFileContent(bytes);
        log.info("파일 포맷 감지 userId={} detectedFormat={} size={}", userId, detectedFormat, bytes.length);

        // HEIC/HEIF는 Java ImageIO 미지원 — ImageMagick으로 메모리 내 JPEG 변환 후 처리
        if (FORMAT_HEIC.equals(detectedFormat)) {
            bytes = convertHeicToJpeg(bytes);
        }
        // Live Photo MOV는 첫 프레임을 JPEG로 추출
        if (FORMAT_MOV.equals(detectedFormat)) {
            bytes = convertMovToJpeg(bytes);
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
        // StorageException(RuntimeException)도 함께 포착해 서비스 컨텍스트를 로그에 남김
        String downloadUrl;
        try {
            downloadUrl = uploadToFirebase(new ByteArrayInputStream(bytes), storagePath, bytes.length);
        } catch (IOException | RuntimeException e) {
            log.error("Firebase 업로드 실패 userId={} storagePath={}", userId, storagePath, e);
            throw new BusinessException(ErrorCode.PHOTO_UPLOAD_FAILED);
        }

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
        } catch (OutOfMemoryError e) {
            // 저사양 서버에서 대형 이미지(24MP 이상) 처리 시 JVM 힙 부족 — Error라서 catch(Exception)에 안 걸림
            log.error("이미지 리사이즈 중 메모리 부족 bytes={}", original.length, e);
            throw new BusinessException(ErrorCode.PHOTO_UPLOAD_FAILED);
        } catch (Exception e) {
            // ImageIO가 처리 불가한 이미지 포맷 (비표준 색공간, 손상된 파일 등) — 500이 아닌 400으로 처리
            log.warn("이미지 리사이즈 실패 — 처리 불가 포맷", e);
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
        // ISO BMFF 컨테이너 (HEIC, Live Photo MOV 공통) — offset 4..7 == "ftyp"
        if (bytes.length >= 12
                && bytes[4] == 0x66 && bytes[5] == 0x74 && bytes[6] == 0x79 && bytes[7] == 0x70) {
            String brand = new String(bytes, 8, 4, StandardCharsets.US_ASCII);
            if (HEIC_BRANDS.contains(brand.trim())) return FORMAT_HEIC;
            // Live Photo MOV 브랜드는 공백 포함 4자리로 비교 ("qt  ")
            if (MOV_BRANDS.contains(brand)) return FORMAT_MOV;
            return null;
        }
        return null;
    }

    // ImageMagick으로 메모리 내 HEIC/HEIF→JPEG 변환 (디스크 저장 없음)
    // v7은 `magick`, v6은 `convert` — 두 명령어를 순서대로 시도
    // heif:- 힌트는 HEIC(heic 브랜드)와 HEIF(heif/heim 브랜드) 모두 처리 가능
    private byte[] convertHeicToJpeg(byte[] heicBytes) {
        List<String[]> candidates = List.of(
                new String[]{"magick", "heif:-", "jpeg:-"},
                new String[]{"convert", "heif:-", "jpeg:-"}
        );
        for (String[] cmd : candidates) {
            try {
                return runImageMagick(cmd, heicBytes);
            } catch (BusinessException e) {
                // 프로세스가 실행됐지만 변환 실패 — 같은 ImageMagick이므로 다음 명령어로 재시도해도 의미 없음
                throw e;
            } catch (IOException e) {
                // 명령어가 존재하지 않음 — 다음 후보 시도
                log.debug("HEIC 변환 명령어 없음 cmd={} error={}", cmd[0], e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new BusinessException(ErrorCode.PHOTO_INVALID_FORMAT);
            }
        }
        log.warn("HEIC 변환 실패 — ImageMagick 미설치 (magick/convert 모두 없음)");
        throw new BusinessException(ErrorCode.PHOTO_INVALID_FORMAT);
    }

    // Live Photo MOV 영상 컴포넌트에서 첫 프레임을 JPEG로 추출 (디스크 저장 없음)
    // iPhone MOV는 moov atom이 파일 앞에 위치하므로 stdin 스트리밍으로 처리 가능
    private byte[] convertMovToJpeg(byte[] movBytes) {
        List<String[]> candidates = List.of(
                new String[]{"ffmpeg", "-i", "pipe:0", "-frames:v", "1", "-f", "image2pipe", "-vcodec", "mjpeg", "pipe:1"},
                new String[]{"magick", "mov:-[0]", "jpeg:-"},
                new String[]{"convert", "mov:-[0]", "jpeg:-"}
        );
        for (String[] cmd : candidates) {
            try {
                return runImageMagick(cmd, movBytes);
            } catch (BusinessException e) {
                throw e;
            } catch (IOException e) {
                log.debug("Live Photo 변환 명령어 없음 cmd={} error={}", cmd[0], e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new BusinessException(ErrorCode.PHOTO_INVALID_FORMAT);
            }
        }
        log.warn("Live Photo MOV 변환 실패 — ffmpeg/ImageMagick 미설치");
        throw new BusinessException(ErrorCode.PHOTO_INVALID_FORMAT);
    }

    private byte[] runImageMagick(String[] cmd, byte[] inputBytes) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(cmd).start();
        try {
            ByteArrayOutputStream stdoutBuf = new ByteArrayOutputStream();
            ByteArrayOutputStream stderrBuf = new ByteArrayOutputStream();

            // stdout, stderr를 동시에 읽어야 파이프 버퍼 포화로 인한 데드락을 방지할 수 있음
            Thread stdoutThread = new Thread(() -> {
                try { process.getInputStream().transferTo(stdoutBuf); }
                catch (IOException e) { log.warn("ImageMagick stdout 읽기 실패 cmd={}", cmd[0], e); }
            });
            Thread stderrThread = new Thread(() -> {
                try { process.getErrorStream().transferTo(stderrBuf); }
                catch (IOException e) { log.warn("ImageMagick stderr 읽기 실패 cmd={}", cmd[0], e); }
            });
            stdoutThread.start();
            stderrThread.start();

            try (OutputStream stdin = process.getOutputStream()) {
                stdin.write(inputBytes);
            }

            // 24MP HEIF 변환은 저사양 서버에서 20s 이상 소요될 수 있어 45s로 설정
            stdoutThread.join(45_000);
            stderrThread.join(5_000);

            boolean finished = process.waitFor(10, TimeUnit.SECONDS);
            if (!finished || process.exitValue() != 0 || stdoutBuf.size() == 0) {
                String stderr = stderrBuf.toString(StandardCharsets.UTF_8);
                log.error("ImageMagick 변환 실패 cmd={} finished={} exitCode={} stdoutSize={} stderr={}",
                        cmd[0], finished, finished ? process.exitValue() : "N/A", stdoutBuf.size(), stderr);
                throw new BusinessException(ErrorCode.PHOTO_INVALID_FORMAT);
            }
            return stdoutBuf.toByteArray();
        } finally {
            process.destroyForcibly();
        }
    }

    private String uploadToFirebase(InputStream stream, String path, int size) throws IOException {
        Storage storage = StorageClient.getInstance().bucket(storageBucket).getStorage();
        BlobId blobId = BlobId.of(storageBucket, path);

        // 다운로드 토큰을 메타데이터에 포함 — 만료 없는 Firebase 영구 다운로드 URL 생성용
        String downloadToken = UUID.randomUUID().toString();
        // 프로필 이미지는 변경이 드물므로 30일 브라우저/CDN 캐시 허용
        BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                .setContentType("image/jpeg")
                .setCacheControl("public, max-age=2592000")
                .setMetadata(Map.of("firebaseStorageDownloadTokens", downloadToken))
                .build();

        storage.createFrom(blobInfo, stream);

        String encodedPath = URLEncoder.encode(path, StandardCharsets.UTF_8);
        return String.format(
                "https://firebasestorage.googleapis.com/v0/b/%s/o/%s?alt=media&token=%s",
                storageBucket, encodedPath, downloadToken
        );
    }

    // 탈퇴 시 PII 파기 — DB 행은 ON DELETE CASCADE로 지워지지만 Firebase 파일은 직접 삭제해야 한다
    public void deleteUserPhotoFromStorage(UUID userId) {
        photoRepository.findByUserId(userId)
                .ifPresent(photo -> deleteFromFirebase(photo.getStorageUrl()));
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
