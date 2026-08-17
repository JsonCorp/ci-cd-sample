# ci-cd-sample — 아키텍처 분석

> **이 문서가 원본(source of truth)입니다.**
> `ci-cd-sample-아키텍처분석.pptx`는 이 내용을 바탕으로 `gen_pptx.py`가 생성하는 **최종 산출물**입니다.
> 내용을 바꿀 때는 **반드시 이 마크다운을 먼저 수정**한 뒤 PPTX를 재생성하세요.

- 버전: **v0.1.0**
- 최종 갱신: 2026-07-26
- 대상: 안드로이드 공식 3계층 + DI 위에 GitHub Actions CI/CD 를 올린 레퍼런스 저장소

---

## 문서 관리 방식

| 구분 | 파일 | 역할 | 편집 |
|------|------|------|------|
| **원본** | `docs/ARCHITECTURE.md` (이 문서) | 단일 진실 공급원 | ✍️ 직접 편집 |
| 렌더러 | `docs/gen_pptx.py` | 내용을 슬라이드로 시각화 | 텍스트/표는 MD와 동기화, 다이어그램은 코드 |
| 산출물 | `docs/ci-cd-sample-아키텍처분석.pptx` | 배포·공유용 | ❌ 직접 편집 금지 (재생성) |

### 갱신 절차
```
1. 이 문서 수정 (+ "문서 변경 이력" 표에 행 추가)
2. gen_pptx.py 의 해당 슬라이드 텍스트/표를 이 문서와 일치
3. python docs/gen_pptx.py  → PPTX 재생성
4. 버전 라벨(표지·요약·이 문서 상단) 동기화
```

---

## 01. 시스템 개요

할 일 관리 앱 하나를 안드로이드 **공식 3계층 아키텍처(UI / domain / data)** 와 **Hilt DI** 로 만들고,
그 위에 **GitHub Actions CI/CD** 를 올린 레퍼런스 저장소다.

이 저장소가 증명하려는 명제는 하나다 — **파이프라인의 품질은 워크플로 YAML 이 아니라 앱 구조가 결정한다.**
도메인을 순수 Kotlin 모듈로 떼어냈기 때문에 44개 단위 테스트가 에뮬레이터 없이 초 단위로 끝나고,
화면을 stateless 로 두었기 때문에 Compose UI 테스트에 Hilt 장치가 필요 없다.

| 구성요소 | 역할 | 기술 | 핵심 |
|----------|------|------|------|
| `:app` | 화면·상태·DI 조립 | Compose, ViewModel, Hilt | stateless 화면 + 얇은 Route |
| `:data` | 저장소 구현 | Android Library, **Room**, Coroutines Flow | 인터페이스는 domain 소유 |
| `:domain` | 규칙과 모델 | **순수 Kotlin(JVM)** | Android·DI 프레임워크 무의존 |
| CI | PR·push 검증 | GitHub Actions | 4잡 병렬 + 아티팩트 인계 |
| CD | 태그 기반 배포 | GitHub Actions, gh CLI | 서명 APK/AAB → Releases |
| E2E | 실기기 검증 | Maestro | testTag → resource-id |

## 02. 전체 아키텍처

의존 방향은 **한 방향뿐**이다. 계층 위반은 리뷰가 아니라 **컴파일 에러**로 막힌다.

```
        ┌──────────────────────────────────────────┐
        │  :app   (Android Application)            │
        │  MainActivity · TaskRoute · TaskScreen   │
        │  TaskViewModel · CicdSampleApp(Hilt)     │
        └───────────┬──────────────────┬───────────┘
                    │                  │
                    ▼                  ▼
        ┌───────────────────┐   ┌──────────────────────────┐
        │ :data (Android)   │──▶│ :domain (순수 Kotlin JVM)│
        │ DefaultTaskRepo   │   │ Task · TaskStats         │
        │ RoomTaskDataSource│   │ TaskRepository(interface)│
        │ AppDatabase(v2)   │   │ UseCase × 6 · dueStatus  │
        │ DataModule(Hilt)  │   │                          │
        └───────────────────┘   └──────────────────────────┘
                                          ▲
                                          │ 아무것도 의존하지 않는다
                                          │ (coroutines-core, javax.inject 만)
```

- `:domain` 은 Android SDK 를 모른다 → `./gradlew :domain:test` 가 **AGP 없이 JVM 에서** 돈다.
- 저장소 **인터페이스는 domain 이 소유**하고 구현이 domain 을 향한다(의존성 역전).
- `:app` 은 `:data` 를 DI 조립 목적으로만 참조한다. 화면 코드는 구현 타입을 모른다.

## 03. 계층별 상세

| 모듈 | 주요 타입 | 책임 | 테스트 |
|------|-----------|------|--------|
| `:domain` | `Task`, `Priority`, `TaskFilter`, `TaskStats`, `DueStatus` | 불변 모델 + 마감 상태 계산 | 단위 8 |
| `:domain` | `TaskRepository` | 저장소 계약(인터페이스) | 페이크로 대체 |
| `:domain` | `AddTaskUseCase` 외 5개 | 검증·정렬·집계 규칙 | 단위 28 |
| `:domain` | `FakeTaskRepository` (testFixtures) | 모듈 간 공유 페이크 | — |
| `:data` | `TaskLocalDataSource` | 로컬 저장 계약(인터페이스) | — |
| `:data` | `RoomTaskDataSource` · `TaskDao` | Room 구현, 실제 SQL 검증 | 계측 9 |
| `:data` | `AppDatabase` · `MIGRATION_1_2` | 스키마 v2, 스키마 JSON 커밋 | 마이그레이션 4 |
| `:data` | `InMemoryTaskDataSource` (src/test) | JVM 테스트용 페이크 | — |
| `:data` | `DefaultTaskRepository` | 인터페이스 구현(위임) | 단위 7 |
| `:data` | `DataModule` | `@Binds` 로 인터페이스↔구현 연결 | — |
| `:app` | `TaskViewModel` | 상태 조립, 에러 문구 변환 | 단위 10 |
| `:app` | `DueOption` | 선택지 → epoch milli 변환(순수 함수) | 단위 6 |
| `:app` | `TaskScreen` | **stateless** 렌더링 | Compose UI 11 |
| `:app` | `TaskRoute` | ViewModel 연결(얇은 래퍼) | — |
| `:app` | `MainActivity` | `testTagsAsResourceId = true` | E2E 6 |

## 04. DI 구성

Hilt 2.53.1 + KSP. `:domain` 은 **DI 프레임워크를 모른다** — 생성자 주입(JSR-330 `@Inject`)만 쓴다.

```
        ┌────────────────────────────────────────────┐
        │  SingletonComponent                        │
        │                                            │
        │  DataModule (@Binds)                       │
        │    TaskRepository ◀── DefaultTaskRepository│
        │                            │               │
        │                            ▼               │
        │                   TaskLocalDataSource      │
        │                       ◀── RoomTaskDataSource│
        │                            │               │
        │                            ▼               │
        │                   AppDatabase (Room, v2)   │
        └────────────────┬───────────────────────────┘
                         │
                         ▼
        ┌────────────────────────────────────────────┐
        │  ViewModelComponent                        │
        │   TaskViewModel (@HiltViewModel)           │
        │     ← UseCase × 6 (@Inject constructor)    │
        └────────────────────────────────────────────┘
```

| 지점 | 어노테이션 | 파일 |
|------|-----------|------|
| 그래프 시작 | `@HiltAndroidApp` | `CicdSampleApp.kt` |
| 주입 대상 화면 | `@AndroidEntryPoint` | `MainActivity.kt` |
| 인터페이스 바인딩 | `@Module @InstallIn(SingletonComponent)` + `@Binds` | `DataModule.kt` |
| 뷰모델 | `@HiltViewModel` + `hiltViewModel()` | `TaskViewModel.kt`, `TaskRoute.kt` |
| 유스케이스 | `@Inject constructor` (모듈 선언 불필요) | `usecase/*.kt` |

## 05. 도메인 규칙

단위 테스트가 형식적이지 않도록 **분기 있는 규칙**을 도메인에 모았다.

| 유스케이스 | 입력 | 분기 | 결과 |
|-----------|------|------|------|
| `AddTaskUseCase` | 제목, 우선순위 | 공백 / 2자 미만 / 40자 초과 / 중복 | `Result<Task>` + `TitleError` |
| `ObserveTasksUseCase` | 필터 | ALL / ACTIVE / DONE | 정렬된 `Flow<List<Task>>` |
| `ToggleTaskUseCase` | id | 없는 id / 갱신 실패 | `Result<Boolean>` |
| `DeleteTaskUseCase` | id | 없는 id | `Result<Unit>` |
| `ClearCompletedUseCase` | — | 지울 게 없으면 0 (실패 아님) | `Int` |
| `GetTaskStatsUseCase` | — | **전체 0건 → 0으로 나누지 않음** | `Flow<TaskStats>` |

정렬 기준 3단계: **미완료 먼저 → 우선순위 높은 순 → 먼저 등록한 순**.
중복 판정은 대소문자와 연속 공백을 무시한다(`"Buy Milk"` == `"buy  milk"`).

## 06. 단방향 데이터 흐름

```
  사용자          TaskScreen        TaskViewModel      UseCase        Repository
    │                 │                   │               │               │
    │──탭/입력───────▶│                   │               │               │
    │                 │──onAddClick()────▶│               │               │
    │                 │                   │──invoke()────▶│               │
    │                 │                   │               │──addTask()───▶│
    │                 │                   │               │◀──Task────────│
    │                 │                   │◀─Result<Task>─│               │
    │                 │                   │                               │
    │                 │                   │◀════ Flow<List<Task>> 재방출 ══╡
    │                 │◀─StateFlow<UiState>│                              │
    │◀─재구성─────────│                   │                               │
```

- 이벤트는 위로, 상태는 아래로. `TaskScreen` 은 콜백만 호출하고 상태를 만들지 않는다.
- `uiState` 는 `filter`, 목록, 통계, 입력 폼 **4개 Flow 를 combine** 해 만든다.
- `SharingStarted.WhileSubscribed(5s)` — 화면이 사라지면 5초 뒤 수집을 멈춘다.

## 07. 테스트 전략

**빠르고 많은 테스트를 아래에, 느리고 적은 테스트를 위에** 둔다.

| 티어 | 위치 | 개수 | 실행 환경 | 잡는 결함 | 실측 |
|------|------|-----:|-----------|-----------|------|
| 단위 | `domain/src/test` | 28 | JVM | 검증·정렬·집계 규칙 | 0.4초 |
| 단위 | `data/src/test` | 6 | JVM | id 발급, Flow 재방출 | 1초 미만 |
| 단위 | `app/src/test` | 10 | JVM | 상태 조립, 에러 문구 | 1초 미만 |
| Compose UI | `app/src/androidTest` | 8 | 관리형 디바이스(GMD) | 렌더링, 콜백 배선 | 2분 36초 |
| E2E | `.maestro/` | 5 | 기기/에뮬레이터 | 실제 앱 전체 흐름 | 1분 57초 |

핵심 설계 결정 두 가지.

- **페이크는 `testFixtures` 로 공유한다.** `FakeTaskRepository` 를 `:domain` 의 테스트 픽스처로 공개해
  `:app` 테스트가 그대로 가져다 쓴다. mock 라이브러리가 없다.
- **Compose UI 테스트에 Hilt 를 쓰지 않는다.** `TaskScreen` 이 stateless 이므로
  `createComposeRule()` 에 상태를 직접 넣으면 된다. `HiltTestRunner`·`@HiltAndroidTest` 가 불필요하다.

## 08. CI 파이프라인

```
  PR / push(main) / 수동
        │
        │  (모든 빌드 잡은 gradle-wrapper.jar 무결성 검증부터)
        ├───────────────┬───────────────┬──────────────┐
        ▼               ▼               ▼              │
  ┌───────────┐ ┌───────────┐ ┌──────────────┐ ┌───────────┐
  │ unit-test │ │ ui-test   │ │migration-test│ │ build     │ ← 네 잡 병렬
  │ 단위+린트  │ │ Compose   │ │ 스키마 가드   │ │assembleDbg│
  │           │ │ GMD       │ │ + Room GMD   │ │           │
  └─────┬─────┘ └─────┬─────┘ └──────┬───────┘ └─────┬─────┘
        │               │               │ app-debug-apk (artifact)
        └───────┬───────┴───────────────┤              │
                │                       ▼              │
                │              ┌─────────────────┐     │
                │              │ e2e             │ needs: [build, unit-test]
                │              │ 에뮬레이터+Maestro│    │
                │              └────────┬────────┘     │
                ▼                       ▼              ▼
        test/lint·UI 리포트       maestro-report   실패 시 PR 코멘트
                                                  (같은 저장소 PR 한정)

  dependency-submission → dependency-review
    GitHub 의존성 그래프는 Gradle 을 네이티브로 파싱하지 못한다.
    PR head 에도 스냅샷을 제출해야 base 와 비교할 대상이 생긴다.
    포크 PR 은 토큰이 읽기 전용이라 둘 다 건너뛴다.
```

실측(2026-07-26, 캐시 적중): `unit-test` 1분 10초 · `build` 57초(병렬) · `e2e` 4분 35초 → 전체 **5분 42초**.
캐시가 없던 첫 실행은 `unit-test` 3분 02초 / `build` 3분 31초였다.

| 잡 | 트리거 조건 | 캐시 | 산출물 |
|----|-------------|------|--------|
| `unit-test` | 항상 | `gradle/actions/setup-gradle` | 테스트·린트 HTML, Kover 커버리지, Job Summary 집계표 |
| `ui-test` | 항상 | 없음(런너 간 AVD 복원이 GMD 를 깨뜨린다) | `androidTests/` HTML 리포트 |
| `migration-test` | 항상 | 없음 | 마이그레이션 리포트. 스키마 가드가 먼저 돌아 값싸게 차단한다 |
| `build` | 항상 | `setup-gradle` | `app-debug.apk`, Job Summary APK 크기 |
| `e2e` | `needs: [build, unit-test]` | AVD 스냅샷(`~/.android/avd`) | `report.html`, 실패 스크린샷 |
| `dependency-submission` | `main` push + 같은 저장소 PR | — | 의존성 그래프(→ Dependabot 알림, 검토 비교 대상) |
| `dependency-review` | 같은 저장소 PR | — | 취약 의존성 차단(high 이상) |
| `notify` | `failure()` && 같은 저장소 PR | — | PR 코멘트(GITHUB_TOKEN 만) |

설계 의도.

- **E2E 는 APK 를 다시 빌드하지 않는다.** `build` 잡의 아티팩트를 내려받아 설치한다 →
  빌드 실패와 에뮬레이터 실패가 로그에서 섞이지 않는다.
- **Compose UI 테스트는 관리형 디바이스(GMD)로 돈다.** AVD 생성·부팅·종료를 Gradle 이 맡으므로
  에뮬레이터 액션에 의존하지 않고, `unit-test`·`build` 와 병렬이라 전체 시간이 늘지 않는다.
  이미지는 `aosp-atd`(테스트 전용 경량본)를 쓴다.
- **`e2e` 는 `unit-test` 도 기다린다.** 로직이 깨진 채로 에뮬레이터를 5분 태우지 않는다.
- **마이그레이션 잡은 값싼 검사를 먼저 한다.** 스키마 JSON 이 커밋됐는지를 셸에서 확인한 뒤에야
  에뮬레이터를 켠다. 5분 태우고 나서 "스키마를 커밋 안 했네" 를 알게 되는 건 낭비다.
- **린트는 `if: always()`** — 단위 테스트가 실패해도 린트 결과를 함께 보여준다. 왕복이 한 번 줄어든다.
- `concurrency: cancel-in-progress` — 같은 브랜치에 새 커밋이 오면 이전 실행을 취소한다.
- `paths-ignore` 는 **push 에만** 건다. PR 에서는 항상 돌아 상태 검사가 비지 않는다.
- **포크 PR 의 `GITHUB_TOKEN` 은 읽기 전용이다.** `permissions` 를 적어도 승격되지 않으므로
  `notify` 는 같은 저장소에서 온 PR 로 한정하고 `continue-on-error` 를 건다.

### 필수 상태 검사

파이프라인의 절반은 저장소 설정이다. `main` 브랜치 보호에서 `단위 테스트 & 린트`,
`Compose UI 테스트`, `디버그 APK 빌드`, `Maestro E2E (에뮬레이터)`, `의존성 검토` 를
required 로 걸어야 위 잡들이 실제 게이트가 된다. 워크플로 파일만으로는 아무것도 막지 못한다.

## 09. CD 파이프라인

```
  git tag v0.1.0 && git push --tags
        │
        ▼
  ┌───────────────────────────────────────────────────────┐
  │ 0. verify 잡: 단위 테스트 + 린트  ← 태그는 브랜치 보호를 │
  │              우회하는 경로라 여기서 다시 막는다          │
  ├───────────────────────────────────────────────────────┤
  │ 1. 태그 ↔ versionName 일치 확인   ← 다르면 즉시 실패    │
  │ 2. 키스토어 준비                                       │
  │      시크릿 있음 → runner.temp 에 base64 디코드         │
  │      시크릿 없음 → 서명 건너뜀(실패 아님)               │
  │ 3. VERSION_CODE = github.run_number                    │
  │ 4. assembleRelease + bundleRelease (R8 축소)           │
  │ 5. apksigner verify  ← 서명이 실제로 붙었는지 확인       │
  │ 6. sha256sum → SHA256SUMS                              │
  │ 7. R8 스모크: 릴리스 APK 설치 + Maestro 1개            │
  │      ← 난독화된 빌드가 실행되는지 게시 전에 확인         │
  │ 8. 릴리스가 있으면 upload --clobber, 없으면 create      │
  └───────────────────────────────────────────────────────┘
        │
        ▼
  GitHub Releases: ci-cd-sample-0.1.0.apk / .aab / SHA256SUMS
```

| 시크릿 | 내용 | 사용처 |
|--------|------|--------|
| `ANDROID_KEYSTORE_BASE64` | 키스토어 파일 base64 | 잡 안에서 `runner.temp` 로 디코드 |
| `ANDROID_KEYSTORE_PASSWORD` | 스토어 비밀번호 | `signingConfigs` 환경변수 |
| `ANDROID_KEY_ALIAS` | 키 별칭 | 〃 |
| `ANDROID_KEY_PASSWORD` | 키 비밀번호 | 〃 |

공개 저장소이므로 **시크릿 없이도 통과**해야 한다. `app/build.gradle.kts` 는 환경변수 4개가 모두
있을 때만 `signingConfigs` 를 구성하고, 없으면 unsigned 로 빌드한 뒤 릴리스 노트에 명시한다.
CI 워크플로는 시크릿을 **하나도 쓰지 않으므로** 포크 PR 에서도 정상 동작한다.

## 10. 운영 · 확장

| 항목 | 현재 | 근거 / 확장 방향 |
|------|------|------------------|
| 영속화 | Room (스키마 v2) | 스키마 JSON 을 커밋하고 마이그레이션을 계측 테스트로 검증한다. `TaskRepository` 는 그대로였다 — 도메인은 한 줄도 안 바뀌었다 |
| 마이그레이션 | `MigrationTestHelper` + 스키마 가드 | 예전 버전 DB 를 실제로 만들어 검증. 버전만 올리고 스키마를 커밋 안 하면 에뮬레이터를 켜기 전에 차단된다 |
| 정적 분석 | Android Lint (`lintDebug`) | detekt/ktlint 는 플러그인 버전 리스크로 보류. `:domain` 은 순수 JVM 이라 Lint 대상 밖이다 |
| 공급망 | wrapper 검증 + dependency-submission/review | 커밋된 wrapper jar 무결성, 취약 의존성 유입 차단, Dependabot 알림. 포크 PR 은 토큰 제약으로 제외 |
| 커버리지 | Kover 집계 리포트 | 세 모듈을 `custom` 변형으로 묶어 루트에서 하나로 낸다. 단위 테스트만 계측되고 계측 테스트는 빠진다 |
| R8 검증 | 릴리스 APK 스모크 테스트 | 게시 직전 난독화된 APK 를 에뮬레이터에 올려 대표 플로우 1개를 태운다. 미서명 빌드는 설치가 안 되므로 건너뛴다 |
| 캐시 | Gradle + AVD 스냅샷 | AVD 캐시 키를 API 레벨에 묶어 무효화 제어 |
| 비용 | 공개 저장소 = Actions 무료 | `concurrency` 취소 + `needs` 로 낭비 차단 |
| 의존성 갱신 | Dependabot (gradle 주간 / actions 월간) | gradle 쪽 보류분(`compileSdk 37` 요구)은 툴체인 일괄 상향으로 해소 — AGP 9.3.1 / Gradle 9.7 / Kotlin 2.4 / compileSdk 37 |
| 배포 확장 | GitHub Releases | Firebase App Distribution / Play Console 추가 가능 |

## 11. 요약

- **의존 방향이 한 방향이면 계층 위반을 컴파일러가 막는다** — 리뷰에 기대지 않는다.
- **도메인을 순수 JVM 모듈로 떼면 44개 테스트가 초 단위로 끝난다** — CI 의 1층이 두꺼워진다.
- **화면을 stateless 로 두면 UI 테스트에서 DI 를 걷어낼 수 있다** — Hilt 테스트 장치가 사라진다.
- **E2E 는 앱을 만들 때 준비된다** — `testTag` 와 `testTagsAsResourceId` 가 전제 조건이다.
- **E2E 잡은 빌드 산출물을 받아 쓴다** — 빌드 실패와 환경 실패를 분리해서 진단한다.
- **CD 는 태그 하나** — 버전 가드·서명·게시를 자동화하고, 시크릿이 없으면 우아하게 물러난다.

## 12. 문서 변경 이력

| 버전 | 날짜 | 유형 | 변경 내용 |
|------|------|------|-----------|
| v0.1.0 | 2026-07-26 | init | 3계층 샘플 앱, 3단 테스트, CI/CD 파이프라인 최초 정리 |
| v0.1.1 | 2026-07-26 | fix | Actions 버전 4건 갱신(Node 20 경고 해소), CI 실측 시간 반영 |
| v0.2.2 | 2026-08-14 | feat | 릴리스 게시 직전 R8 스모크 테스트 추가 — 난독화·리소스 축소된 APK 를 실제로 실행해 본다 |
| v0.2.1 | 2026-08-14 | feat | Kover 커버리지 집계(`:koverXmlReportCustom`)와 Job Summary 연동. `:domain` 의 testFixtures 는 계측에서 제외 |
| v0.2.0 | 2026-08-14 | feat | Compose UI 테스트를 GMD 로 CI 에 편입(3단 검증 실제 완성), 릴리스 전 `verify` 잡, wrapper 검증, dependency-review/submission, apksigner 검증 + SHA256SUMS, `e2e` 가 `unit-test` 도 대기, 포크 PR 알림 가드 |

> **PPTX 동기화 시 위 표를 gen_pptx.py 의 revision_history() rows 와 일치시킬 것.**
