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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PhotoService {

    private final PhotoRepository photoRepository;
    private final UserRepository userRepository;

    @Value("${firebase.storage-bucket}")
    private String storageBucket;

    private static final long MAX_SIZE = 10L * 1024 * 1024;
    private static final Map<String, byte[]> MAGIC_BYTES = Map.of(
            "image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF},
            "image/png",  new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47},
            "image/webp", new byte[]{0x52, 0x49, 0x46, 0x46}
    );
    // iOS Safari는 HEIC를 JPEG로 변환하지만 Content-Type을 다르게 보낼 수 있어 추가 허용 목록
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/webp"
    );

    @Transactional
    public PhotoUploadResponseDto upload(UUID userId, MultipartFile file) throws IOException {
        // getBytes()로 한 번만 읽어 스트림 이중 소비 방지
        byte[] bytes = file.getBytes();
        validateFileContent(file.getContentType(), bytes);
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

    private void validateFileContent(String contentType, byte[] bytes) {
        if (bytes.length > MAX_SIZE) {
            throw new BusinessException(ErrorCode.PHOTO_TOO_LARGE);
        }

        // image/jpg처럼 비표준 MIME도 허용, 정규화
        String normalizedType = normalizeContentType(contentType);
        if (normalizedType == null) {
            throw new BusinessException(ErrorCode.PHOTO_INVALID_FORMAT);
        }

        // magic bytes로 실제 포맷 검증 — Content-Type 조작/불일치 방지
        byte[] expected = MAGIC_BYTES.get(normalizedType);
        if (expected == null || bytes.length < expected.length) {
            throw new BusinessException(ErrorCode.PHOTO_INVALID_FORMAT);
        }
        for (int i = 0; i < expected.length; i++) {
            if (bytes[i] != expected[i]) {
                throw new BusinessException(ErrorCode.PHOTO_INVALID_FORMAT);
            }
        }
    }

    private String normalizeContentType(String contentType) {
        if (contentType == null) return null;
        String lower = contentType.toLowerCase().split(";")[0].trim();
        if (lower.equals("image/jpg")) return "image/jpeg"; // iOS 일부 브라우저
        return ALLOWED_CONTENT_TYPES.contains(lower) ? lower : null;
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
