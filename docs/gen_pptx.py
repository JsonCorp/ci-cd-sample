# -*- coding: utf-8 -*-
"""ci-cd-sample 아키텍처 분석 PPTX 생성 (렌더러).

⚠️ 내용 원본은 docs/ARCHITECTURE.md 이다. 이 스크립트는 그 내용을 슬라이드로
   시각화하는 렌더러일 뿐. 내용 변경 시 ARCHITECTURE.md 를 먼저 고치고 여기를 맞춘 뒤
   `python docs/gen_pptx.py` 로 재생성한다.
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from pptx_kit import *  # noqa

PROJECT = "ci-cd-sample"
VERSION = "v0.1.0"
DATE = "2026-07-26"
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), f"{PROJECT}-아키텍처분석.pptx")

set_footer(f"{PROJECT} · 아키텍처 분석 보고서")
prs = new_deck()

# ── 1. 표지 ──────────────────────────────────────────────
cover(
    prs,
    kicker="ARCHITECTURE ANALYSIS",
    title=PROJECT,
    subtitle="안드로이드 3계층 + CI/CD 레퍼런스",
    comps=[
        (":app", "UI 계층", "Compose · ViewModel · Hilt", BRAND),
        (":data", "데이터 계층", "Repository 구현 · DataSource", CYAN),
        (":domain", "도메인 계층", "순수 Kotlin · UseCase 6", PURPLE),
    ],
    version_date=f"{DATE} · {VERSION}",
)

# ── 2. 시스템 개요 ───────────────────────────────────────
bullet_cards(
    prs, 2, "SYSTEM OVERVIEW", "01. 시스템 개요",
    intro="할 일 관리 앱을 공식 3계층 + Hilt DI 로 만들고 그 위에 GitHub Actions CI/CD 를 올린 레퍼런스 저장소. "
          "증명하려는 명제는 하나 — 파이프라인의 품질은 워크플로 YAML 이 아니라 앱 구조가 결정한다.",
    cards=[
        (":app", "UI 계층", BRAND, [
            "Compose 화면과 상태 조립",
            "stateless TaskScreen",
            "Hilt 그래프 시작점",
            "testTag → resource-id 노출",
        ]),
        (":data", "데이터 계층", CYAN, [
            "TaskRepository 구현",
            "InMemory DataSource",
            "Mutex 로 쓰기 직렬화",
            "@Binds 로 인터페이스 연결",
        ]),
        (":domain", "도메인 계층", PURPLE, [
            "순수 Kotlin(JVM) 모듈",
            "모델 · 저장소 인터페이스",
            "UseCase 6개 (규칙 집중)",
            "Android · DI 무의존",
        ]),
    ],
)

# ── 3. 전체 아키텍처 (다이어그램) ────────────────────────
s = add_slide(prs); set_bg(s, LIGHT)
header(s, "ARCHITECTURE", "02. 전체 아키텍처 — 의존 방향은 한 방향")
txt(s, 0.85, 1.42, 11.6, 0.4,
    [("계층 위반은 리뷰가 아니라 컴파일 에러로 막힌다. :domain 은 아무것도 의존하지 않는다.", 12, GREY, False, KOR)])

a = box(s, 3.55, 2.05, 6.2, 1.25, fill=BRAND, shadow=True)
label_in(a, ":app  (Android Application)\nMainActivity · TaskRoute · TaskScreen · TaskViewModel", size=13)

b = box(s, 1.15, 4.05, 4.6, 1.45, fill=CYAN, shadow=True)
label_in(b, ":data  (Android Library)\nDefaultTaskRepository\nInMemoryTaskDataSource · DataModule", size=12.5)

c = box(s, 7.55, 4.05, 4.6, 1.45, fill=PURPLE, shadow=True)
label_in(c, ":domain  (순수 Kotlin JVM)\nTask · TaskStats · TaskRepository(interface)\nUseCase × 6", size=12.5)

arrow(s, 5.2, 3.30, 3.45, 4.05, color=INK, w=2)
arrow(s, 8.1, 3.30, 9.85, 4.05, color=INK, w=2)
arrow(s, 5.75, 4.78, 7.55, 4.78, color=INK, w=2)

d = box(s, 0.85, 6.05, 11.6, 0.72, fill=CARD, line=LINE, line_w=1)
label_in(d, ":domain 의 외부 의존은 coroutines-core 와 javax.inject 뿐 → ./gradlew :domain:test 가 AGP 없이 JVM 에서 0.4초에 끝난다",
         size=12, color=INK)
footer(s, 3)

# ── 4. 계층별 상세 (표) ──────────────────────────────────
section_table(
    prs, 4, "COMPONENTS", "03. 계층별 상세", accent=PURPLE,
    intro="모듈별 주요 타입과 책임, 그리고 어떤 테스트가 지키는지.",
    columns=[("모듈", 0.85, 1.35), ("주요 타입", 2.20, 3.55), ("책임", 5.75, 4.60), ("테스트", 10.35, 2.25)],
    rows=[
        (("domain",), ("Task · Priority · TaskStats",), "불변 모델", "—"),
        (("domain",), ("TaskRepository",), "저장소 계약(인터페이스)", "페이크로 대체"),
        (("domain",), ("UseCase × 6",), "검증 · 정렬 · 집계 규칙", "단위 28"),
        (("domain",), ("FakeTaskRepository",), "testFixtures 로 모듈 간 공유", "—"),
        (("data",), ("InMemoryTaskDataSource",), "id/order 발급, Mutex 직렬화", "단위 6"),
        (("data",), ("DefaultTaskRepository",), "인터페이스 구현(위임)", "단위 6"),
        (("data",), ("DataModule",), "@Binds 인터페이스↔구현", "—"),
        (("app",), ("TaskViewModel",), "상태 조립, 에러 문구 변환", "단위 10"),
        (("app",), ("TaskScreen",), "stateless 렌더링", "Compose UI 8"),
        (("app",), ("MainActivity",), "testTagsAsResourceId = true", "E2E 5"),
    ],
    row_h=0.44,
)

# ── 5. DI 구성 (다이어그램) ──────────────────────────────
s = add_slide(prs); set_bg(s, LIGHT)
header(s, "DEPENDENCY INJECTION", "04. DI 구성 — Hilt 2.53.1 + KSP", accent=CYAN)
txt(s, 0.85, 1.42, 11.6, 0.4,
    [(":domain 은 DI 프레임워크를 모른다. 생성자 주입(JSR-330 @Inject)만 쓴다.", 12, GREY, False, KOR)])

sc = box(s, 0.85, 2.05, 5.6, 2.9, fill=CARD, line=CYAN, line_w=1.5, shadow=True)
box(s, 0.85, 2.05, 5.6, 0.5, fill=CYAN)
txt(s, 1.15, 2.13, 5.0, 0.35, [("SingletonComponent", 13, WHITE, True, MONO)], anchor=MSO_ANCHOR.MIDDLE)
b1 = box(s, 1.15, 2.75, 5.0, 0.62, fill=RGBColor(0xEE, 0xF2, 0xFA), line=LINE, line_w=0.75)
label_in(b1, "DataModule  @Binds", size=11.5, color=INK)
b2 = box(s, 1.15, 3.47, 5.0, 0.62, fill=RGBColor(0xEE, 0xF2, 0xFA), line=LINE, line_w=0.75)
label_in(b2, "TaskRepository ◀── DefaultTaskRepository", size=11, color=INK)
b3 = box(s, 1.15, 4.19, 5.0, 0.62, fill=RGBColor(0xEE, 0xF2, 0xFA), line=LINE, line_w=0.75)
label_in(b3, "InMemoryTaskDataSource  @Singleton", size=11, color=INK)

vc = box(s, 6.95, 2.05, 5.5, 2.9, fill=CARD, line=BRAND, line_w=1.5, shadow=True)
box(s, 6.95, 2.05, 5.5, 0.5, fill=BRAND)
txt(s, 7.25, 2.13, 4.9, 0.35, [("ViewModelComponent", 13, WHITE, True, MONO)], anchor=MSO_ANCHOR.MIDDLE)
v1 = box(s, 7.25, 2.75, 4.9, 0.62, fill=RGBColor(0xEE, 0xF2, 0xFA), line=LINE, line_w=0.75)
label_in(v1, "TaskViewModel  @HiltViewModel", size=11.5, color=INK)
v2 = box(s, 7.25, 3.47, 4.9, 1.34, fill=RGBColor(0xEE, 0xF2, 0xFA), line=LINE, line_w=0.75)
label_in(v2, "UseCase × 6\n@Inject constructor\n(모듈 선언 불필요)", size=11, color=INK)

arrow(s, 6.45, 3.5, 6.95, 3.5, color=INK, w=2)

for i, (t_, d_) in enumerate([
    ("@HiltAndroidApp", "CicdSampleApp — 그래프 시작"),
    ("@AndroidEntryPoint", "MainActivity — 주입 대상"),
    ("hiltViewModel()", "TaskRoute — 화면에 연결"),
]):
    x = 0.85 + i * 3.92
    bb = box(s, x, 5.25, 3.72, 1.05, fill=CARD, line=LINE, line_w=1)
    label_in(bb, f"{t_}\n{d_}", size=11, color=INK)
footer(s, 5)

# ── 6. 도메인 규칙 (표) ──────────────────────────────────
section_table(
    prs, 6, "DOMAIN RULES", "05. 도메인 규칙 — 분기가 있어야 테스트가 의미를 갖는다", accent=PURPLE,
    intro="정렬은 미완료 먼저 → 우선순위 높은 순 → 먼저 등록한 순. 중복은 대소문자·연속 공백을 무시한다.",
    columns=[("유스케이스", 0.85, 3.05), ("입력", 3.90, 1.75), ("분기", 5.65, 4.35), ("결과", 10.00, 2.60)],
    rows=[
        (("AddTaskUseCase",), "제목·우선순위", "공백 / 2자 미만 / 40자 초과 / 중복", ("Result<Task>",)),
        (("ObserveTasksUseCase",), "필터", "ALL / ACTIVE / DONE", ("Flow<List<Task>>",)),
        (("ToggleTaskUseCase",), "id", "없는 id / 갱신 실패", ("Result<Boolean>",)),
        (("DeleteTaskUseCase",), "id", "없는 id", ("Result<Unit>",)),
        (("ClearCompletedUseCase",), "—", "지울 게 없으면 0 (실패 아님)", ("Int",)),
        (("GetTaskStatsUseCase",), "—", "전체 0건 → 0으로 나누지 않음", ("Flow<TaskStats>",)),
    ],
)

# ── 7. 단방향 데이터 흐름 (시퀀스) ───────────────────────
s = add_slide(prs); set_bg(s, LIGHT)
header(s, "DATA FLOW", "06. 단방향 데이터 흐름")
txt(s, 0.85, 1.42, 11.6, 0.4,
    [("이벤트는 위로, 상태는 아래로. TaskScreen 은 콜백만 호출하고 상태를 만들지 않는다.", 12, GREY, False, KOR)])

lanes = [("사용자", 1.05, GREY), ("TaskScreen", 3.55, BRAND), ("TaskViewModel", 6.15, BRAND),
         ("UseCase", 8.75, PURPLE), ("Repository", 11.05, CYAN)]
for name, x, col in lanes:
    hb = box(s, x - 0.55, 2.0, 2.0 if name != "사용자" else 1.5, 0.5, fill=col)
    label_in(hb, name, size=11.5)
    connector(s, x + 0.2, 2.5, x + 0.2, 5.85, color=LINE, w=1.25, dash="dash")

steps = [
    (1.25, 3.55, "탭 / 입력", 2.85),
    (3.75, 6.35, "onAddClick()", 3.35),
    (6.35, 8.95, "invoke()", 3.85),
    (8.95, 11.25, "addTask()", 4.35),
]
for x1, x2, label, y in steps:
    arrow(s, x1, y, x2, y, color=INK, w=1.75)
    txt(s, x1 + 0.1, y - 0.34, x2 - x1, 0.3, [(label, 10.5, INK, True, MONO)])

arrow(s, 11.25, 5.05, 6.35, 5.05, color=CYAN, w=2)
txt(s, 7.0, 4.71, 4.2, 0.3, [("Flow<List<Task>> 재방출", 10.5, CYAN, True, MONO)])
arrow(s, 6.35, 5.55, 3.75, 5.55, color=BRAND, w=2)
txt(s, 4.0, 5.21, 3.0, 0.3, [("StateFlow<UiState>", 10.5, BRAND, True, MONO)])

n = box(s, 0.85, 6.2, 11.6, 0.62, fill=CARD, line=LINE, line_w=1)
label_in(n, "uiState = combine(filter, 목록, 통계, 입력 폼)  ·  SharingStarted.WhileSubscribed(5초)",
         size=12, color=INK)
footer(s, 7)

# ── 8. 테스트 전략 (표) ──────────────────────────────────
section_table(
    prs, 8, "TEST STRATEGY", "07. 3단 테스트 전략", accent=GREEN,
    intro="빠르고 많은 테스트를 아래에, 느리고 적은 테스트를 위에. 실측값 기준.",
    columns=[("티어", 0.85, 2.10), ("위치", 2.95, 3.00), ("개수", 5.95, 0.95),
             ("실행 환경", 6.90, 2.20), ("잡는 결함", 9.10, 2.10), ("실측", 11.20, 1.40)],
    rows=[
        ("단위", ("domain/src/test",), "28", "JVM", "검증·정렬·집계", "0.4초"),
        ("단위", ("data/src/test",), "6", "JVM", "id 발급·Flow", "1초 미만"),
        ("단위", ("app/src/test",), "10", "JVM", "상태 조립·문구", "1초 미만"),
        ("Compose UI", ("app/src/androidTest",), "8", "기기/에뮬레이터", "렌더링·배선", "2분 36초"),
        ("E2E", (".maestro/",), "5", "기기/에뮬레이터", "앱 전체 흐름", "1분 57초"),
    ],
    row_h=0.52,
)

# ── 9. CI 파이프라인 (다이어그램) ────────────────────────
s = add_slide(prs); set_bg(s, LIGHT)
header(s, "CI PIPELINE", "08. CI 파이프라인 — 잡을 어떻게 쪼갰나", accent=GREEN)
txt(s, 0.85, 1.42, 11.6, 0.4,
    [("E2E 는 APK 를 다시 빌드하지 않는다. build 잡의 아티팩트를 받아 설치한다.", 12, GREY, False, KOR)])

trig = box(s, 0.85, 2.15, 2.35, 0.95, fill=INK, shadow=True)
label_in(trig, "PR / push(main)\n수동 실행", size=11.5)

j1 = box(s, 4.05, 1.95, 3.35, 1.05, fill=GREEN, shadow=True)
label_in(j1, "unit-test\n단위 44 + 린트 · 약 3분", size=12)
j2 = box(s, 4.05, 3.30, 3.35, 1.05, fill=BRAND, shadow=True)
label_in(j2, "build\nassembleDebug · 약 3분 30초", size=12)
j3 = box(s, 8.55, 3.30, 3.90, 1.05, fill=ORANGE, shadow=True)
label_in(j3, "e2e   needs: build\n에뮬레이터 + Maestro", size=12)

arrow(s, 3.20, 2.48, 4.05, 2.48, color=INK, w=2)
arrow(s, 3.20, 3.83, 4.05, 3.83, color=INK, w=2)
arrow(s, 7.40, 3.83, 8.55, 3.83, color=INK, w=2)
txt(s, 7.35, 3.42, 1.3, 0.3, [("APK", 10, GREY, True, MONO)])

par = box(s, 4.05, 1.55, 3.35, 0.3, fill=None, line=None)
txt(s, 4.05, 1.52, 3.35, 0.3, [("두 잡은 병렬", 10.5, GREEN, True, KOR)], align=PP_ALIGN.CENTER)

outs = [
    ("test / lint 리포트", BRAND),
    ("app-debug.apk", CYAN),
    ("maestro-report", ORANGE),
    ("실패 시 PR 코멘트", PURPLE),
]
for i, (label, col) in enumerate(outs):
    x = 0.85 + i * 2.98
    ob = box(s, x, 4.90, 2.80, 0.72, fill=CARD, line=col, line_w=1.5)
    label_in(ob, label, size=11, color=INK)

k = box(s, 0.85, 6.05, 11.6, 0.75, fill=CARD, line=LINE, line_w=1)
label_in(k, "concurrency 취소 · Gradle 캐시(setup-gradle) · AVD 스냅샷 캐시 · paths-ignore 는 push 에만",
         size=12, color=INK)
footer(s, 9)

# ── 10. CD 파이프라인 (시퀀스) ───────────────────────────
s = add_slide(prs); set_bg(s, LIGHT)
header(s, "CD PIPELINE", "09. CD 파이프라인 — 태그 하나로 서명 릴리스", accent=ORANGE)
txt(s, 0.85, 1.42, 11.6, 0.4,
    [("공개 저장소이므로 시크릿이 없는 포크에서도 통과해야 한다 — 서명을 건너뛰고 unsigned 로 계속 진행한다.",
      12, GREY, False, KOR)])

stages = [
    ("git tag v0.1.0", "태그 push", INK),
    ("버전 가드", "태그 ↔ versionName\n다르면 즉시 실패", ORANGE),
    ("키스토어 준비", "base64 → runner.temp\n없으면 서명 생략", PURPLE),
    ("빌드", "assembleRelease\nbundleRelease (R8)", BRAND),
    ("게시", "gh release create\n--generate-notes", GREEN),
]
x = 0.85
for i, (t_, d_, col) in enumerate(stages):
    bb = box(s, x, 2.30, 2.10, 1.60, fill=col, shadow=True)
    label_in(bb, f"{t_}\n\n{d_}", size=11)
    if i < len(stages) - 1:
        arrow(s, x + 2.10, 3.10, x + 2.45, 3.10, color=INK, w=2)
    x += 2.45

section_rows = [
    ("ANDROID_KEYSTORE_BASE64", "키스토어 파일 base64", "잡 안에서 runner.temp 로 디코드"),
    ("ANDROID_KEYSTORE_PASSWORD", "스토어 비밀번호", "signingConfigs 환경변수"),
    ("ANDROID_KEY_ALIAS", "키 별칭", "signingConfigs 환경변수"),
    ("ANDROID_KEY_PASSWORD", "키 비밀번호", "signingConfigs 환경변수"),
]
ty = 4.35
box(s, 0.85, ty, 11.6, 0.42, fill=INK)
for h_, hx, hw in [("시크릿", 0.85, 4.20), ("내용", 5.05, 3.40), ("사용처", 8.45, 4.00)]:
    txt(s, hx + 0.12, ty + 0.05, hw - 0.2, 0.32, [(h_, 11.5, WHITE, True, KOR)], anchor=MSO_ANCHOR.MIDDLE)
ty += 0.42
for i, (a_, b_, c_) in enumerate(section_rows):
    bg = CARD if i % 2 == 0 else RGBColor(0xEE, 0xF2, 0xFA)
    box(s, 0.85, ty, 11.6, 0.42, fill=bg, line=LINE, line_w=0.5)
    txt(s, 0.97, ty + 0.05, 4.0, 0.32, [(a_, 10.5, INK, False, MONO)], anchor=MSO_ANCHOR.MIDDLE)
    txt(s, 5.17, ty + 0.05, 3.2, 0.32, [(b_, 10.5, INK, False, KOR)], anchor=MSO_ANCHOR.MIDDLE)
    txt(s, 8.57, ty + 0.05, 3.8, 0.32, [(c_, 10.5, GREY, False, KOR)], anchor=MSO_ANCHOR.MIDDLE)
    ty += 0.42
footer(s, 10)

# ── 11. 운영 · 확장 (표) ─────────────────────────────────
section_table(
    prs, 11, "OPERATIONS", "10. 운영 · 확장", accent=CYAN,
    intro="지금의 선택과 그 근거, 그리고 넓힐 방향.",
    columns=[("항목", 0.85, 2.10), ("현재", 2.95, 3.60), ("근거 / 확장 방향", 6.55, 6.05)],
    rows=[
        ("영속화", "메모리(MutableStateFlow)", "파이프라인이 주제라 Room 제외. 붙여도 인터페이스는 그대로"),
        ("정적 분석", "Android Lint (lintDebug)", "detekt/ktlint 는 플러그인 버전 리스크로 보류"),
        ("캐시", "Gradle + AVD 스냅샷", "AVD 캐시 키를 API 레벨에 묶어 무효화 제어"),
        ("비용", "공개 저장소 = Actions 무료", "concurrency 취소 + needs 로 낭비 차단"),
        ("의존성 갱신", "Dependabot 주간/월간", "kotlin·ksp·compose·hilt 를 그룹으로 묶어 동시 갱신"),
        ("배포 확장", "GitHub Releases", "Firebase App Distribution / Play Console 추가 가능"),
    ],
)

# ── 12. 요약 ─────────────────────────────────────────────
summary(
    prs, "SUMMARY", "11. 요약",
    points=[
        ("한 방향 의존", "계층 위반을 컴파일러가 막는다 — 리뷰에 기대지 않는다", BRAND),
        ("순수 JVM 도메인", "단위 44개가 에뮬레이터 없이 초 단위로 끝난다 — CI 1층이 두꺼워진다", PURPLE),
        ("stateless 화면", "UI 테스트에서 DI 를 걷어낼 수 있다 — Hilt 테스트 장치가 사라진다", CYAN),
        ("E2E 는 개발 단계에", "testTag 와 testTagsAsResourceId 가 전제 조건이다", GREEN),
        ("아티팩트 인계", "E2E 가 빌드 산출물을 받아 써서 빌드 실패와 환경 실패를 분리한다", ORANGE),
        ("태그 하나로 CD", "버전 가드 · 서명 · 게시 자동화, 시크릿이 없으면 우아하게 물러난다", BRAND),
    ],
    version_date=f"{PROJECT} · {VERSION} · {DATE}",
)

# ── 13. 문서 변경 이력 ───────────────────────────────────
revision_history(prs, 13, rows=[
    ("v0.1.0", DATE, "init", "3계층 샘플 앱, 3단 테스트, CI/CD 파이프라인 최초 정리"),
])

save(prs, OUT)
