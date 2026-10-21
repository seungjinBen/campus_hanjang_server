"""
캠퍼스한장 운영 종료 후 분석 스크립트.

사용 방법:
  1. Supabase SQL Editor에서 analysis/sql/*.sql 각각 실행
  2. 결과를 analysis/data/<번호>_<이름>.csv 로 저장
     (03과 04는 쿼리 블록 (A)(B)(C)(D)를 각각 CSV로 저장 — 아래 매핑 참고)
  3. 의존성 설치: pip install -r analysis/requirements.txt
  4. 실행: python analysis/analyze.py
     → analysis/output/*.png 저장 + 요약 지표 콘솔 출력

CSV 매핑:
  data/01_cohort_retention.csv            ← 01_cohort_retention.sql
  data/02_earlybird_effect.csv            ← 02_earlybird_effect.sql
  data/03_match_threshold_backtest.csv    ← 03 (A) 임계값별 비율
  data/03_match_score_raw.csv             ← 03 (B) match_score raw (히스토그램용)
  data/04_verification_metrics.csv        ← 04 (A)(B) status × method 지표
  data/04_confidence_sim.csv              ← 04 (C) confidence 임계값 시뮬
  data/04_verification_raw.csv            ← 04 (D) raw (processing_ms/llm_calls)
  data/05_referral_funnel.csv             ← 05 (A) 퍼널 단계
  data/06_note_acceptance.csv             ← 06 (A) status 분포

민감 정보 처리 원칙:
  - raw user_id, extracted_name, extracted_student_no, extracted_birth_date,
    contact_value_encrypted, kakao_id, note_content 원문은 분석물에 저장하지 않는다.
  - output/*.png 는 집계값/분포만 포함하므로 공유 가능.
"""
from pathlib import Path

import matplotlib.pyplot as plt
import pandas as pd
import seaborn as sns

ROOT = Path(__file__).parent
DATA = ROOT / "data"
OUT = ROOT / "output"
OUT.mkdir(exist_ok=True)

sns.set_theme(style="whitegrid")
plt.rcParams["font.family"] = "Malgun Gothic"
plt.rcParams["axes.unicode_minus"] = False


def _read(name: str) -> pd.DataFrame | None:
    path = DATA / name
    if not path.exists():
        print(f"[skip] {name} not found")
        return None
    return pd.read_csv(path)


def analyze_cohort_retention() -> None:
    df = _read("01_cohort_retention.csv")
    if df is None:
        return
    pivot = df.pivot_table(
        index="signup_date", columns="day_offset", values="retention_pct", aggfunc="mean"
    )
    plt.figure(figsize=(12, max(4, len(pivot) * 0.3)))
    sns.heatmap(pivot, annot=True, fmt=".0f", cmap="Blues", cbar_kws={"label": "retention %"})
    plt.title("가입 코호트별 N일차 활동 리텐션 (%)")
    plt.xlabel("가입 후 경과일")
    plt.ylabel("가입일")
    plt.tight_layout()
    plt.savefig(OUT / "01_cohort_retention.png", dpi=150)
    plt.close()


def analyze_earlybird_effect() -> None:
    df = _read("02_earlybird_effect.csv")
    if df is None:
        return
    print("\n[02] 얼리버드 vs 일반 유저 활동 비교")
    print(df.to_string(index=False))

    melted = df.melt(
        id_vars=["is_early_bird"],
        value_vars=["avg_selections", "avg_contact_reveals", "avg_notes", "avg_active_days"],
        var_name="metric",
        value_name="value",
    )
    plt.figure(figsize=(10, 5))
    sns.barplot(data=melted, x="metric", y="value", hue="is_early_bird")
    plt.title("얼리버드 vs 일반 유저 평균 활동 지표")
    plt.xticks(rotation=15)
    plt.tight_layout()
    plt.savefig(OUT / "02_earlybird_effect.png", dpi=150)
    plt.close()


def analyze_match_threshold() -> None:
    df = _read("03_match_threshold_backtest.csv")
    if df is None:
        return
    print("\n[03] 매칭 임계값 백테스트")
    print(df.to_string(index=False))

    plt.figure(figsize=(8, 5))
    plt.plot(df["threshold"], df["reveal_pct"], "o-", label="연락처 공개율 (%)")
    plt.plot(df["threshold"], df["note_pct"], "s-", label="쪽지 발송율 (%)")
    plt.axvline(0.75, linestyle="--", color="gray", alpha=0.6, label="실제 운영값 0.75")
    plt.axvline(0.70, linestyle=":", color="orange", alpha=0.6, label="1차 초기값 0.70")
    plt.title("매칭 임계값별 분기 비율 (백테스트)")
    plt.xlabel("임계값")
    plt.ylabel("비율 (%)")
    plt.legend()
    plt.tight_layout()
    plt.savefig(OUT / "03_match_threshold.png", dpi=150)
    plt.close()

    raw = _read("03_match_score_raw.csv")
    if raw is not None:
        plt.figure(figsize=(8, 5))
        sns.histplot(data=raw, x="match_score", hue="type", bins=20, kde=True)
        plt.axvline(0.75, linestyle="--", color="gray")
        plt.title("match_score 분포 (type별)")
        plt.tight_layout()
        plt.savefig(OUT / "03_match_score_hist.png", dpi=150)
        plt.close()


def analyze_verification_metrics() -> None:
    df = _read("04_verification_metrics.csv")
    if df is not None:
        print("\n[04-A/B] 학생인증 LLM 에이전트 운영 지표")
        print(df.to_string(index=False))

        pivot = df.pivot_table(
            index="status", columns="verification_method", values="cnt", fill_value=0
        )
        pivot.plot(kind="bar", figsize=(9, 5))
        plt.title("학생인증 경로별 status 분포")
        plt.ylabel("count")
        plt.tight_layout()
        plt.savefig(OUT / "04_verification_status.png", dpi=150)
        plt.close()

    sim = _read("04_confidence_sim.csv")
    if sim is not None:
        plt.figure(figsize=(8, 5))
        for method in sim["verification_method"].unique():
            sub = sim[sim["verification_method"] == method]
            plt.plot(sub["threshold"], sub["auto_approve_pct"], "o-", label=method)
        plt.axvline(0.85, linestyle="--", color="gray", alpha=0.6, label="실제 운영값 0.85")
        plt.title("confidence 임계값별 자동승인율 (경로별)")
        plt.xlabel("임계값")
        plt.ylabel("자동승인율 (%)")
        plt.legend()
        plt.tight_layout()
        plt.savefig(OUT / "04_confidence_threshold.png", dpi=150)
        plt.close()

    raw = _read("04_verification_raw.csv")
    if raw is not None:
        fig, axes = plt.subplots(1, 2, figsize=(12, 5))
        sns.histplot(data=raw, x="processing_ms", hue="verification_method", bins=30, ax=axes[0])
        axes[0].set_title("처리 시간 분포 (ms)")
        sns.histplot(data=raw, x="llm_calls", hue="verification_method", discrete=True, ax=axes[1])
        axes[1].set_title("LLM 호출 횟수 분포 (턴수)")
        plt.tight_layout()
        plt.savefig(OUT / "04_verification_cost.png", dpi=150)
        plt.close()


def analyze_referral_funnel() -> None:
    df = _read("05_referral_funnel.csv")
    if df is None:
        return
    print("\n[05] 리퍼럴 CSV 미리보기")
    print(df.head(10).to_string(index=False))

    if "step_1_invited" in df.columns:
        steps = ["step_1_invited", "step_2_verified", "step_3_rewarded", "step_4_active"]
        values = [int(df.iloc[0][s]) for s in steps]
        plt.figure(figsize=(8, 5))
        bars = plt.bar(["invited", "verified", "rewarded", "active"], values, color="steelblue")
        for bar, v in zip(bars, values):
            plt.text(bar.get_x() + bar.get_width() / 2, v, str(v), ha="center", va="bottom")
        plt.title("리퍼럴 전환 퍼널")
        plt.ylabel("count")
        plt.tight_layout()
        plt.savefig(OUT / "05_referral_funnel.png", dpi=150)
        plt.close()
    elif {"referrer_id", "rewarded_count"}.issubset(df.columns):
        # 초대자 ID는 외부 공개 금지 — 익명 순위로 표시
        df = df.sort_values("rewarded_count", ascending=False).reset_index(drop=True)
        plt.figure(figsize=(9, 5))
        plt.bar(range(1, len(df) + 1), df["rewarded_count"], color="steelblue")
        plt.title("초대자별 리퍼럴 보상 적립 횟수 (익명 순위)")
        plt.xlabel("초대자 순위")
        plt.ylabel("보상 적립 횟수")
        plt.tight_layout()
        plt.savefig(OUT / "05_referral_top_referrers.png", dpi=150)
        plt.close()
        print(
            f"\n[05] 초대자 {len(df)}명, 총 적립 {int(df['rewarded_count'].sum())}건,"
            f" 최대 {int(df['rewarded_count'].max())}건"
        )


def analyze_note_acceptance() -> None:
    df = _read("06_note_acceptance.csv")
    if df is None:
        return
    print("\n[06] 쪽지 CSV 미리보기")
    print(df.to_string(index=False))

    if "status" in df.columns and "pct" in df.columns:
        plt.figure(figsize=(6, 5))
        sns.barplot(data=df, x="status", y="pct")
        plt.title("쪽지 응답 상태 분포 (%)")
        plt.tight_layout()
        plt.savefig(OUT / "06_note_status.png", dpi=150)
        plt.close()
    elif "length_bucket" in df.columns:
        plt.figure(figsize=(7, 5))
        ax = sns.barplot(data=df, x="length_bucket", y="accept_rate_pct", color="coral")
        for i, row in df.iterrows():
            ax.text(i, row["accept_rate_pct"], f"{row['accept_rate_pct']}%", ha="center", va="bottom")
        plt.title("쪽지 길이 구간별 수락률")
        plt.ylabel("수락률 (%)")
        plt.tight_layout()
        plt.savefig(OUT / "06_note_length_accept.png", dpi=150)
        plt.close()
    elif "hour_of_day" in df.columns:
        plt.figure(figsize=(10, 5))
        plt.plot(df["hour_of_day"], df["accept_rate_pct"], "o-")
        plt.title("쪽지 발신 시간대별 수락률")
        plt.xlabel("hour")
        plt.ylabel("수락률 (%)")
        plt.tight_layout()
        plt.savefig(OUT / "06_note_hour_accept.png", dpi=150)
        plt.close()


def main() -> None:
    import traceback

    steps = [
        ("01_cohort", analyze_cohort_retention),
        ("02_earlybird", analyze_earlybird_effect),
        ("03_match_threshold", analyze_match_threshold),
        ("04_verification", analyze_verification_metrics),
        ("05_referral", analyze_referral_funnel),
        ("06_note", analyze_note_acceptance),
    ]
    for name, fn in steps:
        try:
            fn()
        except Exception as exc:
            print(f"\n[error] {name}: {type(exc).__name__}: {exc}")
            traceback.print_exc()
    print(f"\n출력 폴더: {OUT.resolve()}")


if __name__ == "__main__":
    main()
