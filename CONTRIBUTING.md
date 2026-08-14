# 기여 가이드

## 로컬 검증

CI 가 도는 것과 같은 3단 검증을 로컬에서 한 번에 돌릴 수 있습니다.

```bash
# macOS / Linux
./scripts/run-tests.sh
```

```powershell
# Windows
powershell -ExecutionPolicy Bypass -File scripts\run-tests.ps1
```

기기 없이 1층만 빠르게 보려면:

```bash
./gradlew :domain:test :data:testDebugUnitTest :app:testDebugUnitTest lintDebug
```

## 3단 검증 구조

| 티어 | 명령 | 실행 환경 |
|---|---|---|
| 단위 테스트 | `./gradlew :domain:test :data:testDebugUnitTest :app:testDebugUnitTest` | JVM |
| Compose UI 테스트 | `./gradlew :app:pixel6api30DebugAndroidTest` | 관리형 디바이스(GMD) |
| Maestro E2E | `maestro test .maestro/` | 연결된 기기/에뮬레이터 |

Compose UI 테스트는 관리형 디바이스를 쓰므로 **에뮬레이터를 미리 띄울 필요가 없습니다.**
첫 실행은 시스템 이미지를 내려받느라 몇 분 걸립니다. 이미 띄워 둔 기기에서 돌리려면
`./gradlew :app:connectedDebugAndroidTest` 를 쓰세요.

## 아키텍처 규칙

의존 방향은 `:app → :data → :domain` **한 방향뿐입니다.** 이 화살표를 거스르는 변경은 받지 않습니다.

- `:domain` 은 순수 Kotlin(JVM) 모듈입니다. Android SDK 나 DI 프레임워크를 참조하지 마세요.
- 새 화면은 stateless Composable + ViewModel 조합으로 만듭니다. Composable 안에서 상태를 만들지 않습니다.
- E2E 대상이 되는 요소에는 **작성 시점에** `Modifier.testTag("...")` 를 붙입니다.
  나중에 붙이려 하면 Maestro 플로우를 새로 쓰게 됩니다.

자세한 배경은 [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) 를 보세요.

## 커밋 메시지

Conventional Commits 를 따릅니다.

```
feat: 할 일 우선순위 필터 추가
fix(ci): 에뮬레이터 script 의 줄바꿈 백슬래시 제거
docs: 아키텍처 문서 CI 다이어그램 갱신
chore(deps): Bump actions/checkout from 4 to 7
```

## PR 체크리스트

`.github/pull_request_template.md` 가 자동으로 붙습니다. 다음이 통과해야 병합됩니다.

- `단위 테스트 & 린트`
- `Compose UI 테스트`
- `디버그 APK 빌드`
- `Maestro E2E (에뮬레이터)`
- `의존성 검토`

## 릴리스

`app/build.gradle.kts` 의 `versionName` 을 먼저 올린 뒤, **같은 값으로** 태그를 답니다.

```bash
git tag v0.2.0
git push origin v0.2.0
```

태그와 `versionName` 이 다르면 릴리스 워크플로가 빌드 전에 실패합니다.
릴리스 전에 단위 테스트와 린트가 다시 한 번 돌아갑니다 — 태그 푸시는 브랜치 보호를 우회하는 경로이기 때문입니다.
