-- 두 번째 학생인증 경로(에브리타임 '내 정보' 캡처) 추가 — 세종대 앱 QR 캡처가 안드로이드 일부 기기에서 불가능한 문제 대응
ALTER TABLE student_verifications ADD COLUMN verification_method VARCHAR(20) NOT NULL DEFAULT 'SEJONG_QR';
ALTER TABLE student_verifications ALTER COLUMN verification_method DROP DEFAULT;
