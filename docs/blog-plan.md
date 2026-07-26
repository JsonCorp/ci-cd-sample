# 블로그 계획 — "테스트 가능한 구조가 먼저다"

## 목표

안드로이드 CI/CD 를 다루되, **워크플로 YAML 문법이 아니라 그 앞의 설계 결정**을 이야기한다.
샘플 저장소: `ci-cd-sample` (3계층 + Hilt 할 일 앱 · 단위 44 · Compose UI 8 · Maestro 5 · CI 3잡 + 태그 릴리스)

### 기존 글과의 차별점

- `jayvis_ai/docs/blog/github-actions-android-ci-cd.md` 가 **Actions 입문**(개념, hello 워크플로,
  캐시 기초, 함정 4종, base64 키스토어, `gh release create`, 배지, Dependabot)을 이미 덮었다.
- `android-autotest-maestro-sample` 은 **Maestro E2E 워크플로 1개**만 있고 캐시·단위 테스트·린트·
  서명·배포가 없다.
- 따라서 이 글의 각도는 **"파이프라인이 의미를 가지려면 앱 구조부터 그렇게 만들어야 한다"** 다.
  순수 JVM `:domain` → 초 단위 테스트, stateless 화면 → Hilt 없는 UI 테스트,
  testTag → E2E 가능. 아키텍처 결정이 CI 시간과 신뢰도를 결정한다는 이야기.

## 1편 — 테스트 가능한 구조가 먼저다 (현재 글)

**핵심 메시지**: "CI 가 할 일이 없다면 문제는 CI 가 아니다. 검증할 수 있는 구조를 먼저 만든다."

**목차**
1. 워크플로부터 붙였더니 검증할 게 없었다
2. 공식 3계층으로 앱 다시 짜기 (도메인 / 데이터 / stateless UI)
3. 계층을 모듈로 나누면 CI 가 빨라진다 (순수 JVM, 컴파일 차단, testFixtures)
4. DI 가 있어야 테스트에 페이크를 꽂는다 (Hilt 구성, 버전 조합 선검증)
5. 3단 테스트 전략 — 무엇을 어디서 잡나
6. E2E 에서 실제로 밟은 함정 셋
7. CI 워크플로 — 잡을 어떻게 쪼갰나 (아티팩트 인계, 캐시, 낭비 차단)
8. 빨간불을 읽는 장치 (Job Summary, 아티팩트, PR 코멘트)
9. CD — 태그 하나로 서명된 릴리스
10. 실행 결과와 정리

**준비물**: Android Studio, JDK 17, 공개 GitHub 저장소, Maestro CLI, 실기기 또는 에뮬레이터

**상태**: 작성 완료. `docs/blog/testable-architecture-and-ci-cd-pipeline.md`
실측 — 단위 44/44, Compose UI 8/8(실기기 2분 36초), Maestro 5/5(실기기 1분 57초).
CI: [1차 실행(E2E 실패)](https://github.com/JsonCorp/ci-cd-sample/actions/runs/30163307143) →
[2차 실행(전체 초록, 6분 16초)](https://github.com/JsonCorp/ci-cd-sample/actions/runs/30163575889)

**진행 중 발견한 이슈**

- **Maestro `inputText` 가 유니코드를 못 넣는다**(issue #146). 한글 제목으로 짠 플로우 5개가 전부 실패.
  → 입력값만 ASCII 로, 화면 검증은 한글 그대로 유지해서 해결.
- **`hideKeyboard` 가 기기에 따라 back 키로 동작해 앱이 종료**된다. 키보드 상태에 따라 통과/실패가
  갈리는 flaky. → 테스트가 만지는 버튼(완료 지우기)을 키보드 위쪽으로 옮겨 `hideKeyboard` 를 전부 제거.
- **`android-emulator-runner` 의 `script` 는 `sh -c` 인자**라 백슬래시 줄바꿈이 인자로 남는다.
  `Flow path does not exist: \` 로 실패. → 긴 명령도 한 줄로.
- **정렬이 있는 화면에서 인덱스 셀렉터는 정렬 규칙과 한 몸**이다. 완료 항목이 아래로 내려가면
  `checkbox_task_0` 이 다른 항목을 가리킨다. → 플로우에 주석 명시.
- **`Unable to launch app`** 이 단발성으로 뜬 적이 있다(재실행 시 정상). Maestro 2.6.1 에는
  CLI 재시도 플래그가 없어 조건 대기로만 완화 가능.
- **PowerShell 파이프로 등록한 base64 시크릿에 CR 이 섞여** 릴리스 워크플로가
  `base64: invalid input` 으로 실패. `gh secret set --body` 로 재등록 + 워크플로에서
  `tr -d '[:space:]'` 로 방어. 두 가지를 다 했다.
- Gradle 캐시 효과가 뚜렷하다: `unit-test` 3분 02초 → 1분 10초, `build` 3분 31초 → 1분 08초.

**릴리스 검증**: [v0.1.0 릴리스](https://github.com/JsonCorp/ci-cd-sample/releases/tag/v0.1.0) 에
`ci-cd-sample-0.1.0.apk`(1.01 MB) + `.aab`(2.44 MB) 게시 확인. 내려받아 `apksigner verify` 로
로컬 키스토어와 같은 인증서(SHA-256 `ee4eb2ce…`)임을 확인했고, `versionCode=2`(run_number)가
들어간 것도 확인했다.

## 2편 후보 — 앞으로 쓸 만한 것

| 후보 | 핵심 메시지 | 필요한 작업 |
|---|---|---|
| Room 을 붙여도 화면은 안 바뀐다 | 인터페이스가 도메인에 있으면 저장소 교체는 데이터 계층 안에서 끝난다 | `:data` 에 Room + 마이그레이션 테스트 |
| 빨간불을 일부러 만든다 | 일부러 버그를 심은 브랜치로 실패 경로(아티팩트·PR 코멘트)를 검증 | `demo/` 브랜치 + PR |
| Actions 사용량 줄이기 | 무료 한도 안에서 E2E 를 유지하는 전략(경로 필터, 매트릭스 축소, 야간 실행) | 사용량 측정 |
| Compose 화면 스냅샷 테스트 | Paparazzi/Roborazzi 로 렌더링을 JVM 으로 내리기 | 라이브러리 도입 |
| 버전 매트릭스 올리기 | Dependabot PR 5개가 한 덩어리인 이유와 compileSdk 37 마이그레이션 기록 | AGP 9.x·Kotlin 2.2·compileSdk 37 |

## 스크린샷

`docs/blog/images/NN-slug.jpg` 로 저장하고 `![한국어 캡션](images/NN-slug.jpg)` 로 참조한다.

| 파일 | 내용 | 출처 | 상태 |
|---|---|---|---|
| `01-run-detail-jobs.jpg` | CI 실행 상세 — 잡 그래프와 각 잡 소요 시간 | Actions 실행 #18 | 완료 |
| `02-artifacts.jpg` | Artifacts — APK·maestro-report·테스트 리포트 | 같은 실행 페이지 하단 | 완료 |
| `03-actions-tab.jpg` | Actions 탭 실행 이력 (Dependabot PR 실패 포함) | Actions → CI | 완료 |
| `04-maestro-report.jpg` | Maestro HTML 리포트 5/5 통과 | 로컬 `reports/report.html` | 완료 |
| `05-release-assets.jpg` | Releases v0.1.0 — 서명 APK/AAB | Releases | 완료 |
| `06-app-screen.jpg` | 앱 실행 화면 | 실기기(Galaxy Z Flip5) `adb screencap` | 완료 |

캡처는 헤드리스 Chrome(`--headless=new --screenshot`)과 `adb screencap` 으로 자동화했다.
로그인이 필요한 화면 두 가지는 **익명 접근으로 렌더링되지 않아 넣지 않았다**:

- **Job Summary** (단위 테스트 집계표·APK 크기) — 잡 페이지에서 로그인해야 보인다.
  본문에는 이를 만드는 스크립트를 코드 블록으로 대신 실었다.
- **Secrets 설정 화면** — Settings → Secrets and variables → Actions. 관리자만 접근 가능.

두 장이 필요하면 로그인한 브라우저에서 직접 캡처해 `02`, `07` 번호로 추가하면 된다.

## Dependabot 처리 결과 (2026-07-26)

켜자마자 PR 10개가 열렸고 결과가 갈렸다.

| 종류 | PR | CI | 처리 |
|---|---|---|---|
| GitHub Actions 버전 업 | #1 `gradle/actions` 4→6, #3 `setup-java` 4→5, #4 `upload-artifact` 4→7, #5 `checkout` 4→7 | 통과 | **병합** |
| GitHub Actions 버전 업 | #2 `download-artifact` 4→8 | 최초 실패(digest-mismatch) → rebase 후 통과 | **병합** |
| Gradle 의존성 | #6 kotlin 그룹, #7 compose-bom 2026.06, #8 hilt 그룹, #9 AGP 9.3.1, #10 core-ktx 1.19 | 실패 | **보류** — `compileSdk 37` 요구. 아래 참고 |

Gradle 쪽 실패 원인은 전부 하나로 모인다.

```
Dependency 'androidx.core:core:1.19.0' requires libraries and applications that
depend on it to compile against version 37 or later of the Android APIs.
:app is currently compiled against android-35.
```

`compileSdk 35 → 37` + AGP `8.7.3 → 9.x` + Kotlin/KSP/Hilt 동시 이동이 필요한 **한 덩어리 마이그레이션**이라
개별 병합이 불가능하다. PR 은 열어 둔 채 후속 작업으로 남긴다.

**병합 후 확인**: `upload-artifact` 는 v7, `download-artifact` 는 v8 로 짝이 맞아야 한다
(v4 + v8 조합은 `digest-mismatch` 로 실패). 병합 완료 후 main CI 전 잡 초록,
Node.js 20 지원 종료 경고 3건 → **0건**.
실행 시간도 더 줄었다: `build` 44초 · `unit-test` 56초 · `e2e` 4분 24초.
