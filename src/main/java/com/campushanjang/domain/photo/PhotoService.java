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

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PhotoService {

    private final PhotoRepository photoRepository;
    private final UserRepository userRepository;

    @Value("${firebase.storage-bucket}")
    private String storageBucket;

    private static final long MAX_SIZE = 5L * 1024 * 1024;
    private static final Map<String, byte[]> MAGIC_BYTES = Map.of(
            "image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF},
            "image/png", new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47},
            "image/webp", new byte[]{0x52, 0x49, 0x46, 0x46}
    );

    @Transactional
    public PhotoUploadResponseDto upload(UUID userId, MultipartFile file) throws IOException {
        validateFile(file);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));

        String fileName = userId + "/" + UUID.randomUUID() + ".jpg";
        String storagePath = "profiles/" + fileName;

        // 기존 사진이 있으면 Firebase에서 삭제
        Optional<UserPhoto> existing = photoRepository.findByUserId(userId);
        existing.ifPresent(photo -> deleteFromFirebase(photo.getStorageUrl()));

        // Firebase에 업로드
        String downloadUrl = uploadToFirebase(file.getInputStream(), storagePath, file.getSize());

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

    private void validateFile(MultipartFile file) throws IOException {
        if (file.getSize() > MAX_SIZE) {
            throw new BusinessException(ErrorCode.PHOTO_TOO_LARGE);
        }

        String contentType = file.getContentType();
        if (contentType == null || !MAGIC_BYTES.containsKey(contentType)) {
            throw new BusinessException(ErrorCode.PHOTO_INVALID_FORMAT);
        }

        // magic bytes 검증 — Content-Type 조작 방지
        byte[] header = file.getInputStream().readNBytes(4);
        byte[] expected = MAGIC_BYTES.get(contentType);
        for (int i = 0; i < expected.length; i++) {
            if (header[i] != expected[i]) {
                throw new BusinessException(ErrorCode.PHOTO_INVALID_FORMAT);
            }
        }
    }

    private String uploadToFirebase(InputStream stream, String path, long size) throws IOException {
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
