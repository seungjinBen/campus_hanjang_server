package com.campushanjang.domain.photo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HEIF/HEIC magic bytes 감지 로직 단위 테스트.
 *
 * <p>detectFormatFromBytes는 private이지만 핵심 보안 분기점(포맷 허용 여부)이므로
 * reflection으로 직접 검증한다.
 */
class PhotoFormatDetectionTest {

    private Method detectFormatFromBytes;
    private PhotoService photoService;

    @BeforeEach
    void setUp() throws Exception {
        // Repository/UserRepository 없이 리플렉션으로 직접 private 메서드 접근
        photoService = new PhotoService(null, null);
        detectFormatFromBytes = PhotoService.class.getDeclaredMethod("detectFormatFromBytes", byte[].class);
        detectFormatFromBytes.setAccessible(true);
    }

    // --- HEIC/HEIF 브랜드 파라미터화 테스트 ---

    @ParameterizedTest(name = "ftyp brand={0}")
    @ValueSource(strings = {"heic", "heis", "hevc", "hevx", "mif1", "msf1", "avif", "heif", "heim"})
    @DisplayName("지원되는 모든 HEIC/HEIF 브랜드는 image/heic로 감지된다")
    void detectsAllHeifBrands(String brand) throws Exception {
        byte[] bytes = buildFtypBytes(brand);

        String result = (String) detectFormatFromBytes.invoke(photoService, (Object) bytes);

        assertThat(result).isEqualTo("image/heic");
    }

    @Test
    @DisplayName("24MP iPhone HEIF 파일 — heif 브랜드 감지 (핵심 버그 회귀 방지)")
    void detects24MpIphoneHeifBrand() throws Exception {
        // iPhone 고해상도(24MP/48MP) 모드에서 생성되는 HEIF 파일의 ftyp 브랜드
        byte[] bytes = buildFtypBytes("heif");

        String result = (String) detectFormatFromBytes.invoke(photoService, (Object) bytes);

        assertThat(result)
                .as("heif 브랜드가 감지되지 않으면 PHOTO_INVALID_FORMAT 에러로 업로드 실패")
                .isEqualTo("image/heic");
    }

    // --- 기존 포맷 감지 ---

    @Test
    @DisplayName("JPEG magic bytes(FF D8 FF) 감지")
    void detectsJpeg() throws Exception {
        byte[] bytes = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0, 0, 0, 0, 0};

        String result = (String) detectFormatFromBytes.invoke(photoService, (Object) bytes);

        assertThat(result).isEqualTo("image/jpeg");
    }

    @Test
    @DisplayName("PNG magic bytes(89 50 4E 47) 감지")
    void detectsPng() throws Exception {
        byte[] bytes = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};

        String result = (String) detectFormatFromBytes.invoke(photoService, (Object) bytes);

        assertThat(result).isEqualTo("image/png");
    }

    @Test
    @DisplayName("WEBP magic bytes(RIFF....WEBP) 감지")
    void detectsWebp() throws Exception {
        byte[] bytes = new byte[12];
        bytes[0] = 0x52; bytes[1] = 0x49; bytes[2] = 0x46; bytes[3] = 0x46; // RIFF
        bytes[4] = 0; bytes[5] = 0; bytes[6] = 0; bytes[7] = 0;
        bytes[8] = 0x57; bytes[9] = 0x45; bytes[10] = 0x42; bytes[11] = 0x50; // WEBP

        String result = (String) detectFormatFromBytes.invoke(photoService, (Object) bytes);

        assertThat(result).isEqualTo("image/webp");
    }

    // --- 거부 케이스 ---

    @Test
    @DisplayName("알 수 없는 포맷은 null 반환")
    void returnsNullForUnknownFormat() throws Exception {
        byte[] bytes = "unknown file content here !!".getBytes(StandardCharsets.US_ASCII);

        String result = (String) detectFormatFromBytes.invoke(photoService, (Object) bytes);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("MP4 ftyp 브랜드(isom)는 거부된다")
    void rejectsMp4Brand() throws Exception {
        byte[] bytes = buildFtypBytes("isom");

        String result = (String) detectFormatFromBytes.invoke(photoService, (Object) bytes);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("4바이트 미만 파일은 null 반환")
    void returnNullForTooShortBytes() throws Exception {
        byte[] bytes = new byte[]{(byte) 0xFF, (byte) 0xD8};

        String result = (String) detectFormatFromBytes.invoke(photoService, (Object) bytes);

        assertThat(result).isNull();
    }

    // --- 헬퍼 ---

    /**
     * ISO BMFF ftyp 박스 합성.
     * offset 0-3: 박스 크기, 4-7: "ftyp", 8-11: major brand, 12-15: minor version
     */
    private static byte[] buildFtypBytes(String brand) {
        byte[] bytes = new byte[20];
        // 박스 크기 (big-endian) = 20
        bytes[0] = 0; bytes[1] = 0; bytes[2] = 0; bytes[3] = 20;
        // "ftyp"
        bytes[4] = 0x66; bytes[5] = 0x74; bytes[6] = 0x79; bytes[7] = 0x70;
        // major brand (4바이트, 오른쪽 패딩)
        byte[] brandBytes = brand.getBytes(StandardCharsets.US_ASCII);
        for (int i = 0; i < 4; i++) {
            bytes[8 + i] = (i < brandBytes.length) ? brandBytes[i] : 0x20;
        }
        // minor version = 0
        bytes[12] = 0; bytes[13] = 0; bytes[14] = 0; bytes[15] = 0;
        // compatible brand (동일)
        System.arraycopy(bytes, 8, bytes, 16, 4);
        return bytes;
    }
}
