-- =============================================================================
-- 06. 쪽지 수락률 분석 (2차 시즌 신기능)
-- =============================================================================
-- 목적: 쪽지 발신 → 수신자 수락/거절/무응답(PENDING) 분포 측정.
--       2차 시즌에 추가한 쪽지 응답 기능의 실제 사용 패턴 확인.
--
-- 민감 정보: note_content 원문은 로그/응답/CSV 어디에도 노출 금지.
--           여기서는 길이와 시간 지표만 집계.
-- =============================================================================

-- (A) 전체 status 분포 + 평균 응답 시간
SELECT
    status,
    COUNT(*) AS cnt,
    ROUND(COUNT(*)::numeric / SUM(COUNT(*)) OVER () * 100, 1) AS pct,
    ROUND(AVG(LENGTH(note_content))::numeric, 1) AS avg_length,
    ROUND(AVG(EXTRACT(EPOCH FROM (responded_at - created_at)) / 60)::numeric, 1)
        AS avg_response_minutes
FROM notes
GROUP BY status
ORDER BY status;

-- (B) 발신 시간대별 수락률
SELECT
    EXTRACT(HOUR FROM created_at)::int AS hour_of_day,
    COUNT(*) AS sent,
    COUNT(*) FILTER (WHERE status = 'ACCEPTED') AS accepted,
    ROUND(COUNT(*) FILTER (WHERE status = 'ACCEPTED')::numeric
          / NULLIF(COUNT(*), 0) * 100, 1) AS accept_rate_pct
FROM notes
GROUP BY hour_of_day
ORDER BY hour_of_day;

-- (C) 쪽지 길이 구간별 수락률 (짧은 vs 긴 쪽지)
SELECT
    CASE
        WHEN LENGTH(note_content) <= 15 THEN '1_short(1-15)'
        WHEN LENGTH(note_content) <= 30 THEN '2_mid(16-30)'
        ELSE '3_long(31-50)'
    END AS length_bucket,
    COUNT(*) AS sent,
    COUNT(*) FILTER (WHERE status = 'ACCEPTED') AS accepted,
    ROUND(COUNT(*) FILTER (WHERE status = 'ACCEPTED')::numeric
          / NULLIF(COUNT(*), 0) * 100, 1) AS accept_rate_pct
FROM notes
GROUP BY length_bucket
ORDER BY length_bucket;
