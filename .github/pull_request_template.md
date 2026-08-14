## 무엇을 바꿨나

<!-- 한두 문장. "왜" 가 자명하지 않으면 함께 적어 주세요. -->

## 검증

<!-- CI 가 자동으로 도는 항목이라도, 로컬에서 확인한 것에 체크해 주세요. -->

- [ ] 단위 테스트 (`./gradlew :domain:test :data:testDebugUnitTest :app:testDebugUnitTest`)
- [ ] 린트 (`./gradlew lintDebug`)
- [ ] Compose UI 테스트 (`./gradlew :app:pixel6api30DebugAndroidTest`)
- [ ] Maestro E2E (`./scripts/run-tests.sh` 또는 `scripts\run-tests.ps1`)

## 아키텍처 규칙

- [ ] 의존 방향이 `:app → :data → :domain` 을 벗어나지 않는다
- [ ] `:domain` 에 Android SDK / DI 프레임워크 참조를 넣지 않았다
- [ ] 새 UI 요소에 `testTag` 를 붙였다 (E2E 대상인 경우)

## 파이프라인에 영향이 있나

- [ ] 워크플로(`.github/workflows/**`)를 고쳤다 → 필수 상태 검사 이름이 바뀌지 않았는지 확인
- [ ] 의존성을 추가/변경했다 → `의존성 검토` 잡 결과 확인
- [ ] `versionName` 을 바꿨다 → 릴리스 태그를 같은 값으로 달 것

## 그 외

<!-- 스크린샷, 후속 작업, 리뷰어가 특히 봐줬으면 하는 부분 -->
