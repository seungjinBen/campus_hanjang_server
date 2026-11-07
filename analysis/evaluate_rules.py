#!/usr/bin/env python3
"""
캠퍼스한장 학생 인증 에이전트 — 규칙 평가 하네스 (1단계)

입력: analysis/student_verifications.csv (익명화된 AgentDecision 로그)
출력: analysis/report.md 와 analysis/out/*.csv

분석 항목
  1. 개요            — 상태·경로 분포, 포트폴리오 수치와 대조
  2. 게이트 개입     — LLM 원판정이 서버 규칙 게이트에서 바뀐 건수와 사유
  3. 임계치 스윕     — 자동승인 확신도 임계치를 0.70~0.95로 바꿨을 때
                       자동승인/검수/오승인/놓친승인 건수 (운영자 판정 = 정답)
  4. 재촬영·거절 원인 — decision_reason 키워드 분류 (다중 라벨)
  5. 경로별 비교     — SEJONG_QR vs EVERYTIME_PROFILE
  6. 규칙 replay     — 로그에 남은 필드만으로 재현 가능한 게이트 규칙을 다시 적용해
                       실제 status와 일치하는지 확인
  7. 학과 정규화     — search_department 가 돌려준 기존 학과 목록의 오염 정도

사용: python3 analysis/evaluate_rules.py [--csv PATH] [--out DIR]
의존: pandas
"""
from __future__ import annotations

import argparse
import json
import re
from pathlib import Path

import pandas as pd

ALLOWED_UNIVERSITY = "세종대학교"
AUTO_APPROVE_THRESHOLD = 0.85          # verification.auto-approve-threshold
REJECT_TO_REVIEW_THRESHOLD = 0.90      # REJECTED 이면서 conf >= 0.9 → NEEDS_REVIEW
SWEEP = [0.70, 0.75, 0.80, 0.85, 0.90, 0.95]

# 재촬영·거절 원인 분류 규칙 (순서 무관, 다중 라벨)
CAUSES: list[tuple[str, re.Pattern]] = [
    ("API/에이전트 오류",      re.compile(r"에이전트 오류|API 호출 실패|호출 실패")),
    ("생년월일 안 보임",        re.compile(r"생년월일.{0,12}(표시되지 않|보이지 않|확인되지 않|판독|누락|미확인|잘려)")),
    ("8자리 학번 안 보임",      re.compile(r"8자리.{0,20}(표시되지 않|보이지 않|확인되지 않|판독|명확하지 않|노출되지)|학번.{0,10}(표시되지 않|보이지 않|확인되지 않)|학번만|학년 표기|학년 정보")),
    ("대학명 안 보임",          re.compile(r"대학명.{0,15}(표시되지 않|보이지 않|확인되지 않|없음|없어|판독 불가|확인 불가)|대학명 필드")),
    ("학과명 잘림/불명확",      re.compile(r"학과명.{0,20}(잘려|불명확|확인 불가|명확하지|판독)|학과.{0,8}(잘려|불명확)")),
    ("이름 안 보임",            re.compile(r"이름.{0,12}(표시되지 않|보이지 않|확인되지 않|판독 불가|가려)")),
    ("다른 화면 캡처",          re.compile(r"메인 화면|메인 메뉴|QR 화면이 아님|화면이 아님|화면이 아니|다른 서비스|증명사진|설정 메뉴|프로필 카드와|캠퍼스한장' 프로필|QR 코드만|메뉴 버튼만|My QR 탭|My QR 보기|보기 화면이 필요")),
    ("로딩 중 캡처",            re.compile(r"로딩")),
    ("흐림/가림/잘림",          re.compile(r"흐릿|흐려|가려|판독할 수 없|판독되지 않|불명확하게 표시|선명하지")),
    ("위조/UI 불일치 의심",     re.compile(r"위조|UI.{0,10}(일치하지 않|불일치)|표준 스타일과 일치하지")),
    ("학번 중복",               re.compile(r"이미 승인|중복된 학번|already_approved\":true")),
]


def classify(reason: str) -> list[str]:
    reason = reason or ""
    hits = [name for name, pat in CAUSES if pat.search(reason)]
    # "위조 신호 없음" 같은 부정문은 위조 의심으로 세지 않는다
    if "위조/UI 불일치 의심" in hits and re.search(r"위조 신호 없음|위조 신호는 없", reason) and not re.search(r"UI.{0,10}일치하지 않|표준 스타일과 일치하지", reason):
        hits.remove("위조/UI 불일치 의심")
    return hits or ["기타/미분류"]


def gate_tag(reason: str) -> str | None:
    """decision_reason 앞에 서버 게이트가 붙인 태그를 추출."""
    m = re.match(r"\[(게이트 강등: [^\]]+|REJECTED→NEEDS_REVIEW: [^\]]+)\]", reason or "")
    return m.group(1) if m else None


def llm_raw_status(row) -> str:
    """게이트 태그로 LLM 원판정을 역추적."""
    tag = row["gate_tag"]
    if not isinstance(tag, str):
        return row["status"] if row["status"] in ("AUTO_APPROVED", "RETRY_REQUESTED", "REJECTED") else "NEEDS_REVIEW(LLM)"
    if tag.startswith("REJECTED→"):
        return "REJECTED"
    return "AUTO_APPROVED"  # 게이트 강등은 LLM 이 AUTO_APPROVED 를 냈을 때만 일어난다


def pct(n, d):
    return f"{n} ({n / d * 100:.1f}%)" if d else "0"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--csv", default=str(Path(__file__).parent / "student_verifications.csv"))
    ap.add_argument("--out", default=str(Path(__file__).parent / "out"))
    args = ap.parse_args()
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)

    d = pd.read_csv(args.csv, encoding="utf-8-sig")
    d["decision_reason"] = d["decision_reason"].fillna("")
    d["reviewed"] = d["reviewed_by"].notna()
    d["gate_tag"] = d["decision_reason"].map(gate_tag)
    d["llm_status"] = d.apply(llm_raw_status, axis=1)
    N = len(d)
    md: list[str] = []
    w = md.append

    # ------------------------------------------------------------------ 1
    w("# 학생 인증 에이전트 규칙 평가 리포트\n")
    w(f"- 데이터: {N}건, {d.created_at.min()[:10]} ~ {d.created_at.max()[:10]}")
    w(f"- 운영자 검수 건: {int(d.reviewed.sum())}건 (APPROVED {int((d.status=='APPROVED').sum())}, "
      f"REJECTED {int(((d.status=='REJECTED') & d.reviewed).sum())})\n")
    w("## 1. 개요\n")
    w("| 최종 status | 건수 | 비율 | 의미 |")
    w("|---|---|---|---|")
    meaning = {
        "AUTO_APPROVED": "에이전트 + 서버 게이트 통과, 사람 개입 없음",
        "RETRY_REQUESTED": "필수 항목이 화면에 없어 재촬영 요청 (사람 개입 없음)",
        "REJECTED": "에이전트 거절(자동) 또는 운영자 거절",
        "APPROVED": "NEEDS_REVIEW 큐에서 운영자가 승인",
    }
    for s, c in d.status.value_counts().items():
        w(f"| {s} | {c} | {c/N*100:.1f}% | {meaning.get(s,'')} |")
    w("")
    w("| 경로 | 건수 |")
    w("|---|---|")
    for m, c in d.verification_method.value_counts().items():
        w(f"| {m} | {c} |")
    w("")
    w("포트폴리오 기재치와 대조: 자동승인 73% / 수동검토 18% / 자동거절 5.1% / 운영자 승인 3.8%. "
      "'수동검토 18%'에 해당하는 status 는 RETRY_REQUESTED(재촬영 요청)이며 운영자가 본 건이 아니다. "
      "실제로 사람이 본 건은 NEEDS_REVIEW 큐를 거친 "
      f"{int(d.reviewed.sum())}건({d.reviewed.sum()/N*100:.1f}%)이다.\n")

    # ------------------------------------------------------------------ 2
    w("## 2. 서버 게이트 개입\n")
    w("LLM 원판정이 서버 규칙 게이트에서 바뀐 건. decision_reason 앞의 `[게이트 강등: …]` / `[REJECTED→NEEDS_REVIEW]` 태그로 집계.\n")
    g = d[d.gate_tag.notna()]
    w(f"- 게이트 개입: **{len(g)}건 / {N}건 ({len(g)/N*100:.1f}%)**")
    w("")
    w("| 게이트 사유 | 건수 | 최종 status 분포 |")
    w("|---|---|---|")
    for tag, grp in g.groupby("gate_tag"):
        dist = ", ".join(f"{k} {v}" for k, v in grp.status.value_counts().items())
        w(f"| {tag} | {len(grp)} | {dist} |")
    w("")
    w("LLM 원판정(역추적) × 최종 status:\n")
    ct = pd.crosstab(d.llm_status, d.status)
    w(ct.to_markdown())
    w("")
    g.to_csv(out / "gate_interventions.csv", index=False, encoding="utf-8-sig")

    # ------------------------------------------------------------------ 3
    w("## 3. 확신도 임계치 스윕\n")
    w("대상: LLM 이 AUTO_APPROVED 를 냈고 필수 항목·형식 검사는 통과한 건 (= 임계치만이 자동승인 여부를 가르는 건). "
      "정답은 최종 status 로 정의: AUTO_APPROVED/APPROVED = 유효, REJECTED(운영자) = 무효.\n")
    cand = d[(d.llm_status == "AUTO_APPROVED") & ~d.gate_tag.fillna("").str.contains("누락|형식 위반|불일치")]
    cand = cand[cand.status.isin(["AUTO_APPROVED", "APPROVED", "REJECTED"])]
    valid = cand.status.isin(["AUTO_APPROVED", "APPROVED"])
    w(f"- 대상 건수: {len(cand)} (유효 {int(valid.sum())}, 운영자 거절 {int((~valid).sum())})\n")
    w("| 임계치 | 자동승인 | 그중 오승인(운영자 거절) | 검수 큐 | 그중 운영자가 승인(놓친 자동승인) | 자동승인율 |")
    w("|---|---|---|---|---|---|")
    rows = []
    for t in SWEEP:
        auto = cand.confidence_score >= t
        fp = int((auto & ~valid).sum())
        fn = int((~auto & valid).sum())
        rows.append(dict(threshold=t, auto=int(auto.sum()), false_approve=fp, review=int((~auto).sum()), missed=fn))
        w(f"| {t:.2f} | {int(auto.sum())} | {fp} | {int((~auto).sum())} | {fn} | {auto.sum()/N*100:.1f}% (전체 {N}건 기준) |")
    pd.DataFrame(rows).to_csv(out / "threshold_sweep.csv", index=False)
    w("")
    w("확신도 분포 (LLM 원판정별):\n")
    w(d.groupby("llm_status").confidence_score.describe()[["count", "mean", "50%", "min", "max"]].round(3).to_markdown())
    w("")
    w("주의: 확신도는 자기보고값이라 AUTO_APPROVED 는 거의 전부 0.95~0.99 에 몰려 있다. "
      "임계치는 0.85~0.95 사이에서 결과가 거의 안 바뀌며, 변별력은 임계치가 아니라 필수 항목 검사에서 나온다.\n")

    # ------------------------------------------------------------------ 4
    w("## 4. 재촬영·거절 원인 분류\n")
    nr = d[d.status.isin(["RETRY_REQUESTED", "REJECTED"])].copy()
    nr["causes"] = nr.decision_reason.map(classify)
    ex = nr.explode("causes")
    w(f"대상: RETRY_REQUESTED + REJECTED = {len(nr)}건 (다중 라벨, 합계는 건수를 넘을 수 있음)\n")
    w("| 원인 | 건수 | SEJONG_QR | EVERYTIME | 비율(대상 기준) |")
    w("|---|---|---|---|---|")
    for c, grp in sorted(ex.groupby("causes"), key=lambda x: -len(x[1])):
        q = int((grp.verification_method == "SEJONG_QR").sum())
        e = int((grp.verification_method == "EVERYTIME_PROFILE").sum())
        w(f"| {c} | {len(grp)} | {q} | {e} | {len(grp)/len(nr)*100:.0f}% |")
    w("")
    nr[["id", "status", "verification_method", "confidence_score", "causes", "decision_reason", "admin_rejection_note"]] \
        .assign(causes=lambda x: x.causes.map("; ".join)) \
        .to_csv(out / "retry_reject_causes.csv", index=False, encoding="utf-8-sig")
    unc = nr[nr.causes.map(lambda c: c == ["기타/미분류"])]
    w(f"미분류 {len(unc)}건 — `out/retry_reject_causes.csv` 에서 확인.\n")

    # ------------------------------------------------------------------ 5
    w("## 5. 경로별 비교\n")
    w("| 지표 | SEJONG_QR | EVERYTIME_PROFILE |")
    w("|---|---|---|")
    bym = {m: d[d.verification_method == m] for m in ["SEJONG_QR", "EVERYTIME_PROFILE"]}

    def line(label, f):
        w(f"| {label} | {f(bym['SEJONG_QR'])} | {f(bym['EVERYTIME_PROFILE'])} |")

    line("건수", lambda x: len(x))
    line("자동승인율", lambda x: pct(int((x.status == 'AUTO_APPROVED').sum()), len(x)))
    line("재촬영율", lambda x: pct(int((x.status == 'RETRY_REQUESTED').sum()), len(x)))
    line("거절율", lambda x: pct(int((x.status == 'REJECTED').sum()), len(x)))
    line("운영자 승인", lambda x: pct(int((x.status == 'APPROVED').sum()), len(x)))
    line("게이트 개입율", lambda x: pct(int(x.gate_tag.notna().sum()), len(x)))
    line("LLM 호출 평균", lambda x: f"{x.llm_calls.mean():.2f}")
    line("처리시간 평균(s)", lambda x: f"{x.processing_ms.mean()/1000:.1f}")
    line("처리시간 p50/p95(s)", lambda x: f"{x.processing_ms.quantile(.5)/1000:.1f} / {x.processing_ms.quantile(.95)/1000:.1f}")
    line("확신도 평균(자동승인 건)", lambda x: f"{x[x.status=='AUTO_APPROVED'].confidence_score.mean():.3f}")
    w("")

    # 도구 사용
    def tools(s):
        try:
            return [a.get("tool") for a in json.loads(s)] if isinstance(s, str) else []
        except Exception:
            return []
    d["tools"] = d.agent_actions.map(tools)
    d["n_tools"] = d.tools.map(len)
    w("도구 호출 (Tool Use):\n")
    w("| 경로 | 도구 0회 | check_student_no | search_department | 건당 평균 도구 호출 |")
    w("|---|---|---|---|---|")
    for m, x in bym.items():
        x = d.loc[x.index]
        w(f"| {m} | {int((x.n_tools==0).sum())} | {int(x.tools.map(lambda t: 'check_student_no' in t).sum())} | "
          f"{int(x.tools.map(lambda t: 'search_department' in t).sum())} | {x.n_tools.mean():.2f} |")
    dup = d.agent_actions.fillna("").str.contains('already_approved\\\\":true|already_approved":true')
    w(f"\n`check_student_no` 가 중복 학번(already_approved=true)을 잡은 건: {int(dup.sum())}건\n")
    w("에브리타임 경로는 도구가 없어야 하는데 도구 호출이 기록된 건이 있다면 프롬프트/도구 노출 설정을 점검할 것. "
      f"(EVERYTIME 경로 도구 호출 건: {int(d.loc[bym['EVERYTIME_PROFILE'].index].n_tools.gt(0).sum())}건)\n")

    # ------------------------------------------------------------------ 6
    w("## 6. 규칙 replay (재현 가능한 범위)\n")
    w("로그에서 이름·학번·생년월일은 제거됐으므로 '필수 항목 누락' 과 '형식 위반' 은 재현 불가. "
      "재현 가능한 규칙: (a) 대학명 == '세종대학교' 정확 일치, (b) confidence ≥ 0.85, (c) REJECTED & conf ≥ 0.9 → NEEDS_REVIEW.\n")
    issues = []
    aa = d[d.status == "AUTO_APPROVED"]
    bad_u = aa[aa.extracted_university.fillna("") != ALLOWED_UNIVERSITY]
    bad_c = aa[aa.confidence_score < AUTO_APPROVE_THRESHOLD]
    w(f"- (a) AUTO_APPROVED 인데 대학명이 '{ALLOWED_UNIVERSITY}' 아님: **{len(bad_u)}건**"
      + (f" — 값: {bad_u.extracted_university.fillna('NULL').value_counts().to_dict()}" if len(bad_u) else ""))
    w(f"- (b) AUTO_APPROVED 인데 confidence < {AUTO_APPROVE_THRESHOLD}: **{len(bad_c)}건**")
    rej_hi = d[(d.llm_status == "REJECTED") & (d.confidence_score >= REJECT_TO_REVIEW_THRESHOLD)]
    rej_hi_untagged = rej_hi[rej_hi.gate_tag.isna()]
    w(f"- (c) LLM REJECTED & conf ≥ {REJECT_TO_REVIEW_THRESHOLD} 인데 NEEDS_REVIEW 태그 없음: **{len(rej_hi_untagged)}건**")
    if len(rej_hi_untagged):
        last_untagged = rej_hi_untagged.created_at.max()[:10]
        first_tagged = rej_hi[rej_hi.gate_tag.notna()].created_at.min()[:10] if rej_hi.gate_tag.notna().any() else "없음"
        w(f"  - 태그 없는 건은 모두 {last_untagged} 이전, 태그 있는 건은 모두 {first_tagged} 이후 → "
          "규칙이 시즌 중간에 배포된 것으로 보인다. 배포 전 건은 규칙 평가에서 제외하거나 배포일을 기준으로 나눠 봐야 한다.")
    ev_tools = d[(d.verification_method == "EVERYTIME_PROFILE") & (d.agent_actions.fillna("[]") != "[]")]
    if len(ev_tools):
        w(f"- 에브리타임 경로에 도구 호출이 남은 {len(ev_tools)}건은 모두 {ev_tools.created_at.max()[:10]} 이전 → 같은 배포 경계로 추정.")
    # 대학명 변형
    uni_var = d.extracted_university.fillna("NULL").value_counts()
    w(f"- 추출된 대학명 값 분포: {uni_var.to_dict()}")
    short = d[d.extracted_university == "세종대"]
    if len(short):
        w(f"  - '세종대'(축약형) {len(short)}건의 최종 status: {short.status.value_counts().to_dict()} "
          "→ 정확 일치 규칙 때문에 축약형은 자동승인에서 탈락한다. 에브리타임 화면은 '세종대'로 표기되므로 프롬프트가 정규화를 떠안고 있는 상태.")
    w("")
    if len(bad_u) == 0 and len(bad_c) == 0 and len(rej_hi_untagged) == 0:
        w("→ 재현 가능한 세 규칙 모두 로그와 100% 일치. 게이트는 설계대로 동작했다.\n")

    # ------------------------------------------------------------------ 7
    w("## 7. 학과 정규화 상태\n")
    deps = d.extracted_department.dropna()
    w(f"- SEJONG_QR 건 중 학과 추출: {len(deps)}건, 고유값 {deps.nunique()}개")
    # search_department 결과에 들어있는 전체 목록 (가장 긴 것 하나)
    lists = []
    for s in d.agent_actions.dropna():
        for a in tools(s) and json.loads(s):
            if a.get("tool") == "search_department":
                try:
                    lists.append(json.loads(a["result"]).get("existing_departments", []))
                except Exception:
                    pass
    if lists:
        biggest = max(lists, key=len)
        sus = [x for x in biggest if re.search(r"TV|위조|웅합|응합|자우|제옥|천단|철단|공강|담당|에너미션|이노베이션공$|의식경영|^지정융$", x)]
        w(f"- `search_department` 가 돌려주는 기존 학과 목록 크기: {len(biggest)}개 (마지막 호출 기준)")
        w(f"- 오타·장난 값으로 보이는 항목 {len(sus)}개: {sus}")
        w("- 목록이 에이전트가 승인한 값을 그대로 쌓는 구조라, 한 번 들어간 오타가 다음 판정의 정규화 후보로 노출된다. "
          "12월 개선 1순위 후보: 정식 학과 마스터 테이블 + 유사도 매칭으로 교체.\n")
    pd.Series(sorted(deps.unique())).to_csv(out / "departments_extracted.csv", index=False, header=["department"], encoding="utf-8-sig")

    # ------------------------------------------------------------------ 8
    w("## 8. 요약 — 이 로그가 말해주는 것\n")
    w("1. 확신도 임계치(0.85)는 이 시즌 데이터에서 아무것도 가르지 않았다. 자동승인 건은 전부 0.95 이상이고, 0.85 미만 건은 애초에 LLM 이 AUTO_APPROVED 를 내지 않았다. "
      "임계치를 바꿔 자동승인율을 올리는 길은 없다.")
    w("2. 재촬영 78건의 원인은 '모델이 못 읽음'이 아니라 '화면에 정보가 없음'이 대부분이다 — QR 경로는 생년월일 미표시, 에브리타임 경로는 8자리 학번·대학명 미표시. "
      "에브리타임 '내 정보' 화면에는 8자리 학번이 원래 없으므로, 에브리타임 경로에서 '8자리 학번 없음'으로 재촬영을 보낸 건은 프롬프트가 QR 경로 기준을 잘못 적용한 것이다.")
    w("3. 에브리타임 경로가 QR 경로보다 자동승인율이 10%p 낮고 거절율은 6배다. 대학명 '세종대'(축약) 정규화와 입학년도 기준을 프롬프트/게이트에 명시하면 가장 싸게 개선된다.")
    w("4. 운영자가 승인한 13건 중 9건은 LLM 이 스스로 NEEDS_REVIEW 를 낸 건(확신도 0.72~0.85, 그중 1건은 API 오류 폴백), 4건은 REJECTED→NEEDS_REVIEW 강등 규칙이 구한 건이다. 이 규칙이 없었으면 4명이 부당 거절당했다.")
    w("5. 학과 목록에 오타·장난 값 15개가 들어가 있고 search_department 가 그걸 그대로 다음 판정에 노출한다. 데이터 품질 이슈가 모델 이슈보다 먼저다.")
    w("6. 검증 한계: 운영자 판정이 16건뿐이라 '오승인' 추정이 불가능하다. 자동승인 252건 중 무작위 30건을 사람이 다시 보는 표본 검수가 있어야 정확도를 말할 수 있다.\n")

    (Path(args.out).parent / "report.md").write_text("\n".join(md), encoding="utf-8")
    print("\n".join(md))


if __name__ == "__main__":
    main()
