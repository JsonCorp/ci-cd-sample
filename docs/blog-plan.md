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
- Gradle 캐시 효과가 뚜렷하다: `unit-test` 3분 02초 → 1분 10초, `build` 3분 31초 → 1분 08초.

## 2편 후보 — 앞으로 쓸 만한 것

| 후보 | 핵심 메시지 | 필요한 작업 |
|---|---|---|
| Room 을 붙여도 화면은 안 바뀐다 | 인터페이스가 도메인에 있으면 저장소 교체는 데이터 계층 안에서 끝난다 | `:data` 에 Room + 마이그레이션 테스트 |
| 빨간불을 일부러 만든다 | 일부러 버그를 심은 브랜치로 실패 경로(아티팩트·PR 코멘트)를 검증 | `demo/` 브랜치 + PR |
| Actions 사용량 줄이기 | 무료 한도 안에서 E2E 를 유지하는 전략(경로 필터, 매트릭스 축소, 야간 실행) | 사용량 측정 |
| Compose 화면 스냅샷 테스트 | Paparazzi/Roborazzi 로 렌더링을 JVM 으로 내리기 | 라이브러리 도입 |

## 스크린샷 (직접 캡처 필요)

`docs/blog/images/NN-slug.jpg` 로 저장하고 `![한국어 캡션](images/NN-slug.jpg)` 로 참조한다.

| 번호 | 캡처 대상 | 어디서 |
|---|---|---|
| 01 | Actions 실행 상세 — `unit-test`/`build` 병렬 + `e2e` 후속 | 저장소 → Actions → 2차 실행 |
| 02 | Job Summary — 단위 테스트 집계표, APK 크기 | 같은 실행 결과 페이지 상단 |
| 03 | Artifacts 목록 — 리포트·APK·maestro-report | 같은 페이지 하단 |
| 04 | Maestro HTML 리포트 5/5 통과 | `reports/report.html` |
| 05 | Releases 페이지 — 서명 APK/AAB 첨부 | 저장소 → Releases |
| 06 | Secrets 설정 화면 (값은 마스킹됨) | Settings → Secrets and variables → Actions |
| 07 | 앱 실행 화면 | 실기기 스크린샷 |
