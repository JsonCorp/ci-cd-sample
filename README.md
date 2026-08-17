# ci-cd-sample

[![CI](https://github.com/JsonCorp/ci-cd-sample/actions/workflows/ci.yml/badge.svg)](https://github.com/JsonCorp/ci-cd-sample/actions/workflows/ci.yml)
[![Release](https://github.com/JsonCorp/ci-cd-sample/actions/workflows/release.yml/badge.svg)](https://github.com/JsonCorp/ci-cd-sample/actions/workflows/release.yml)
[![latest](https://img.shields.io/github/v/release/JsonCorp/ci-cd-sample)](https://github.com/JsonCorp/ci-cd-sample/releases/latest)

안드로이드 **공식 3계층 아키텍처 + DI** 위에 **GitHub Actions CI/CD** 를 올린 레퍼런스 저장소.

이 저장소가 보여주려는 것은 워크플로 YAML 문법이 아니라 그 앞의 결정이다 —
**앱 구조를 어떻게 잡아야 파이프라인이 의미를 갖는가.**

- 도메인을 순수 Kotlin 모듈로 떼면 단위 테스트가 **에뮬레이터 없이 초 단위**로 끝난다
- 화면을 stateless 로 두면 Compose UI 테스트에 **Hilt 가 필요 없다**
- `testTag` 를 만들 때 같이 붙여야 **Maestro E2E** 가 가능해진다

## 구조

```
ci-cd-sample/
├── app/          Android application — Compose UI, ViewModel, Hilt 조립
├── data/         Android library     — Repository 구현, Room DB(+스키마 JSON)
├── domain/       순수 Kotlin(JVM)    — Model, Repository 인터페이스, UseCase
├── .maestro/     E2E 플로우 6개 + common/ 서브플로우
├── .github/
│   ├── workflows/ci.yml       PR·push 검증 (단위·린트 / UI / 마이그레이션 / 빌드 / E2E / 공급망)
│   ├── workflows/release.yml  v* 태그 → 검증 → 서명 릴리스
│   ├── pull_request_template.md
│   └── dependabot.yml
├── docs/         아키텍처 문서(+PPTX)·블로그 글
└── scripts/      run-tests.sh / run-tests.ps1 — 로컬 3단 검증 한 번에
```

의존 방향은 `:app → :data → :domain` 한 방향뿐이다. `:domain` 은 Android 도, DI 프레임워크도 모른다.

## 샘플 앱

할 일 관리 앱 하나. 단위 테스트가 형식적이지 않도록 **분기 있는 규칙**을 도메인에 넣었다.

| 규칙 | 내용 |
|---|---|
| 제목 검증 | 공백 / 2자 미만 / 40자 초과 / 중복(대소문자·연속 공백 무시) 거부 |
| 정렬 | 미완료 먼저 → 우선순위 높은 순 → 먼저 등록한 순 |
| 통계 | 완료율 반올림, 0건일 때 0으로 나누지 않음 |

## 4단 검증

| 티어 | 위치 | 개수 | 어디서 도나 |
|---|---|---:|---|
| 단위 테스트 | `domain/src/test`, `data/src/test`, `app/src/test` | 59 | JVM (에뮬레이터 불필요) |
| Compose UI 테스트 | `app/src/androidTest` | 11 | 관리형 디바이스(GMD) |
| DB·마이그레이션 테스트 | `data/src/androidTest` | 13 | 관리형 디바이스(GMD) |
| Maestro E2E | `.maestro/` | 6 | 기기/에뮬레이터 |

네 티어 모두 CI 에서 돈다. 관리형 디바이스를 쓰므로 계측 테스트는 에뮬레이터를 미리 띄울 필요가 없다.

마이그레이션 테스트는 커밋된 스키마 JSON(`data/schemas/`)으로 **예전 버전 DB 를 실제로 만들어**
마이그레이션을 돌리고 결과를 최신 스키마와 대조한다. 스키마를 바꾸면서 마이그레이션을 빠뜨리면
앱은 '업데이트한 사용자'에게만 죽는데 — 신규 설치는 멀쩡하다 — 그 상황을 CI 가 대신 재현한다.

커버리지는 Kover 로 세 모듈을 하나로 집계해 PR 요약에 표로 붙는다
(`./gradlew :koverXmlReportCustom :koverHtmlReportCustom`).
단, **단위 테스트만** 계측된다 — 계측 테스트는 Kover 가 수집하지 못한다.

## 빠른 시작

```bash
# 단위 테스트 + 린트 (기기 없이)
./gradlew :domain:test :data:testDebugUnitTest :app:testDebugUnitTest lintDebug

# Compose UI 테스트 (관리형 디바이스 — 에뮬레이터를 미리 띄울 필요 없음)
./gradlew :app:pixel6api30DebugAndroidTest

# 3단 전부 한 번에 (macOS / Linux)
./scripts/run-tests.sh          # 기기 없으면 SKIP_E2E=1 ./scripts/run-tests.sh
```

```powershell
# Windows
powershell -ExecutionPolicy Bypass -File scripts\run-tests.ps1
```

## CI/CD

- **`ci.yml`** — `unit-test` · `ui-test` · `build` 가 병렬로 돌고, `e2e` 는 `build` 가 만든 APK 아티팩트를
  내려받아 에뮬레이터에서 실행한다. 빌드 실패와 에뮬레이터 실패가 섞이지 않는다.
  Gradle 캐시(`gradle/actions/setup-gradle`)와 AVD 스냅샷 캐시를 함께 쓴다.
  모든 빌드 잡은 `gradle-wrapper.jar` 무결성을 먼저 검증하고, PR 에는 `dependency-review` 가 붙는다.
- **`release.yml`** — `v*` 태그를 밀면 먼저 단위 테스트·린트를 다시 돌리고(`verify`), 태그와 `versionName`
  이 같은지 확인한 뒤, 시크릿의 키스토어로 서명한 APK/AAB 를 `SHA256SUMS` 와 함께 GitHub Releases 에
  올린다. 게시 전 `apksigner verify` 로 서명을 확인하고, 난독화된 릴리스 APK 를 에뮬레이터에
  실제로 올려 대표 플로우 하나를 태운다(R8 스모크) — 릴리스에서만 터지는 결함을 게시 전에 잡는다.
  시크릿이 없는 포크에서는 **서명을 건너뛰고 계속 진행**한다.

### 브랜치 보호 설정

파이프라인의 절반은 저장소 설정이다. **Settings → Branches → `main`** 에서 다음 상태 검사를
required 로 걸어야 CI 가 게이트로 동작한다.

| required check | 잡 |
|---|---|
| `단위 테스트 & 린트` | `unit-test` |
| `Compose UI 테스트` | `ui-test` |
| `DB 마이그레이션 테스트` | `migration-test` |
| `디버그 APK 빌드` | `build` |
| `Maestro E2E (에뮬레이터)` | `e2e` |

`의존성 검토`(`dependency-review`)는 포크 PR 에서 그래프를 제출할 수 없어 건너뛰므로 required 로 걸지 않는다.
같은 저장소 PR 에서는 정상 동작하며, 결과는 Checks 탭에서 볼 수 있다.

태그 푸시는 브랜치 보호를 우회하므로, `release.yml` 이 자체 `verify` 잡으로 같은 검증을 다시 돈다.

실측(2026-07-26): `unit-test` 1분 10초 · `build` 1분 08초(병렬) · `e2e` 5분 06초 → 전체 6분 16초.
Gradle 캐시 적중 전에는 각각 3분 02초 / 3분 31초였다.

## 문서

- [아키텍처 분석](docs/ARCHITECTURE.md) (+ `docs/ci-cd-sample-아키텍처분석.pptx`)
- [블로그 — 테스트 가능한 구조가 먼저다](docs/blog/testable-architecture-and-ci-cd-pipeline.md)
- [Maestro 플로우 규칙](.maestro/README.md)
