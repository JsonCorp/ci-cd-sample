# Maestro E2E 플로우

`com.example.cicdsample` 앱을 실제 기기/에뮬레이터에서 조작해 검증하는 플로우 모음이다.
단위 테스트가 못 잡는 것 — 화면 배선, 키보드, 목록 재정렬, 실제 앱 프로세스 — 만 여기서 확인한다.

| 파일 | 시나리오 |
|---|---|
| `01_add_task.yaml` | 제목을 입력해 추가하면 목록과 통계가 함께 바뀐다 |
| `02_validation.yaml` | 너무 짧은 제목과 중복 제목은 목록에 들어가지 않는다 |
| `03_toggle_and_stats.yaml` | 체크하면 완료율이 다시 계산되고 완료 항목은 아래로 내려간다 |
| `04_filter.yaml` | 미완료/완료 탭이 각각 해당하는 항목만 보여준다 |
| `05_clear_completed.yaml` | 완료한 항목만 한 번에 지운다 |
| `common/launch_clean.yaml` | 공통 초기화 서브플로우 (`runFlow` 전용) |

## 실행

```powershell
# 빌드 + 설치 + 실행 + HTML 리포트까지 한 번에
powershell -ExecutionPolicy Bypass -File scripts\run-tests.ps1

# 개별 실행
maestro test .maestro\01_add_task.yaml
```

디렉터리 실행(`maestro test .maestro/`)은 하위 폴더로 내려가지 않는다.
`common/` 을 폴더로 둔 이유가 이것이다 — 서브플로우가 단독 실행되지 않는다.

## 이 저장소의 규칙

- **셀렉터는 `id:` 를 쓴다.** Compose 의 `testTag` 가 `MainActivity` 의
  `Modifier.semantics { testTagsAsResourceId = true }` 덕분에 UIAutomator `resource-id` 로 노출된다.
  화면을 만들 때 testTag 를 같이 붙이는 것이 E2E 의 전제 조건이다.
- **플로우 `name:` 에 큰따옴표(`"`)를 쓰지 않는다.** Maestro 가 `--debug-output` 파일명에 플로우 이름을
  그대로 쓰기 때문에 Windows 에서 파일명 금지 문자로 걸려 러너 전체가 중단된다.
- **`takeScreenshot` 은 반드시 `reports/` 아래 경로를 준다.** 경로 없이 이름만 주면 CWD 에 떨어져
  CI 아티팩트에 담기지 않는다.
- **고정 `sleep` 을 쓰지 않는다.** 필요하면 `extendedWaitUntil` / `waitForAnimationToEnd` 로 조건을 기다린다.
- 입력 후에는 `hideKeyboard` 로 키보드를 내린다. 키보드가 목록 하단과 버튼을 가린다.
