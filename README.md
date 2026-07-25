# ci-cd-sample

[![CI](https://github.com/JsonCorp/ci-cd-sample/actions/workflows/ci.yml/badge.svg)](https://github.com/JsonCorp/ci-cd-sample/actions/workflows/ci.yml)

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
├── data/         Android library     — Repository 구현, 로컬 DataSource
├── domain/       순수 Kotlin(JVM)    — Model, Repository 인터페이스, UseCase
├── .maestro/     E2E 플로우 5개 + common/ 서브플로우
├── .github/
│   ├── workflows/ci.yml       PR·push 검증 (단위·린트 / 빌드 / E2E)
│   ├── workflows/release.yml  v* 태그 → 서명 릴리스
│   └── dependabot.yml
├── docs/         아키텍처 문서(+PPTX)·블로그 글
└── scripts/run-tests.ps1      로컬 3단 검증 한 번에
```

의존 방향은 `:app → :data → :domain` 한 방향뿐이다. `:domain` 은 Android 도, DI 프레임워크도 모른다.

## 샘플 앱

할 일 관리 앱 하나. 단위 테스트가 형식적이지 않도록 **분기 있는 규칙**을 도메인에 넣었다.

| 규칙 | 내용 |
|---|---|
| 제목 검증 | 공백 / 2자 미만 / 40자 초과 / 중복(대소문자·연속 공백 무시) 거부 |
| 정렬 | 미완료 먼저 → 우선순위 높은 순 → 먼저 등록한 순 |
| 통계 | 완료율 반올림, 0건일 때 0으로 나누지 않음 |

## 3단 검증

| 티어 | 위치 | 개수 | 어디서 도나 |
|---|---|---:|---|
| 단위 테스트 | `domain/src/test`, `data/src/test`, `app/src/test` | 44 | JVM (에뮬레이터 불필요) |
| Compose UI 테스트 | `app/src/androidTest` | 8 | 기기/에뮬레이터 |
| Maestro E2E | `.maestro/` | 5 | 기기/에뮬레이터 |

## 빠른 시작

```powershell
# 단위 테스트 + 린트 (기기 없이)
.\gradlew.bat :domain:test :data:testDebugUnitTest :app:testDebugUnitTest lintDebug

# 기기 연결 후, 설치 + E2E + HTML 리포트까지 한 번에
powershell -ExecutionPolicy Bypass -File scripts\run-tests.ps1
```

## CI/CD

- **`ci.yml`** — `unit-test` 와 `build` 가 병렬로 돌고, `e2e` 는 `build` 가 만든 APK 아티팩트를
  내려받아 에뮬레이터에서 실행한다. 빌드 실패와 에뮬레이터 실패가 섞이지 않는다.
  Gradle 캐시(`gradle/actions/setup-gradle`)와 AVD 스냅샷 캐시를 함께 쓴다.
- **`release.yml`** — `v*` 태그를 밀면 태그와 `versionName` 이 같은지 확인하고, 시크릿의 키스토어로
  서명한 APK/AAB 를 GitHub Releases 에 올린다. 시크릿이 없는 포크에서는 **서명을 건너뛰고 계속 진행**한다.

## 문서

- [아키텍처 분석](docs/ARCHITECTURE.md) (+ `docs/ci-cd-sample-아키텍처분석.pptx`)
- [블로그 — 테스트 가능한 구조가 먼저다](docs/blog/testable-architecture-and-ci-cd-pipeline.md)
- [Maestro 플로우 규칙](.maestro/README.md)
