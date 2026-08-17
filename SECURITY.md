# 보안 정책

## 지원 범위

이 저장소는 안드로이드 아키텍처와 CI/CD 파이프라인을 보여주는 **레퍼런스 샘플**입니다.
프로덕션 배포용 애플리케이션이 아니며, 최신 `main` 브랜치만 관리 대상입니다.

## 취약점 신고

취약점을 발견하면 **공개 이슈로 올리지 말고** 다음 경로로 알려 주세요.

- GitHub Security Advisory: 저장소 상단 **Security → Report a vulnerability**

확인 후 회신하며, 수정이 필요한 경우 패치와 함께 공개합니다.

## 이 저장소가 이미 적용하고 있는 것

| 항목 | 수단 |
|---|---|
| Gradle Wrapper 위·변조 검증 | `gradle/actions/wrapper-validation` (모든 빌드 잡) |
| 새로 유입되는 취약 의존성 차단 | `actions/dependency-review-action` (같은 저장소 PR, high 이상 실패) |
| 알려진 취약점 상시 감시 | `gradle/actions/dependency-submission` → Dependabot 알림 |
| 서명 산출물 재현 확인 | 릴리스 전 `verify` 잡이 단위 테스트·린트를 다시 실행 |
| 의존성 갱신 | Dependabot (gradle 주간 / actions 월간) |
| 서명 키 관리 | 저장소에 키스토어를 두지 않는다. GitHub Secrets 의 base64 를 잡 안 `RUNNER_TEMP` 로만 디코드 |
| 서명 검증 | 릴리스 게시 전 `apksigner verify --print-certs` |
| 산출물 무결성 | 릴리스마다 `SHA256SUMS` 동봉 |
| 토큰 권한 | 워크플로 기본 `permissions: contents: read`, 필요한 잡에서만 상향 |

## 알려진 제약

- CI 워크플로는 시크릿을 **하나도 사용하지 않습니다**. 포크 PR 에서도 안전하게 동작합니다.
- 포크에서 온 PR 의 `GITHUB_TOKEN` 은 읽기 전용이므로 실패 알림 코멘트는 같은 저장소 PR 에만 달립니다.
- 같은 이유로 포크 PR 에서는 의존성 그래프를 제출할 수 없어 `dependency-review` 도 건너뜁니다.
  GitHub 의 의존성 그래프는 Gradle 을 네이티브로 파싱하지 못하므로, 스냅샷 제출이 없으면
  비교 대상이 없어 검토가 무의미하게 통과합니다. 포크 PR 의 의존성 변경은 리뷰어가 직접 확인해야 합니다.
- 샘플 앱은 데이터를 메모리에만 보관하며 네트워크 통신이나 영속 저장을 하지 않습니다.
