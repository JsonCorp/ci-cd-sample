#!/usr/bin/env bash
# CI/CD 샘플 — 로컬에서 3단 검증을 한 번에 돌리는 스크립트 (macOS / Linux)
#
# 사용법: 저장소 루트에서
#   ./scripts/run-tests.sh
#
# 순서: 단위 테스트/린트 -> Compose UI 테스트(GMD) -> 단말 확인 -> 설치 -> Maestro E2E -> 리포트
#
# 환경변수:
#   SKIP_UI_TEST=1   Compose UI 테스트를 건너뛴다 (시스템 이미지 내려받기가 부담될 때)
#   SKIP_E2E=1       Maestro E2E 를 건너뛴다 (기기가 없을 때)

set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$root"

cyan() { printf '\033[36m%s\033[0m\n' "$1"; }
red()  { printf '\033[31m%s\033[0m\n' "$1"; }
green(){ printf '\033[32m%s\033[0m\n' "$1"; }
yellow(){ printf '\033[33m%s\033[0m\n' "$1"; }

cyan $'\n[1/6] 단위 테스트 + 린트 (에뮬레이터 불필요)'
./gradlew :domain:test :data:testDebugUnitTest :app:testDebugUnitTest lintDebug

if [ "${SKIP_UI_TEST:-0}" = "1" ]; then
  yellow $'\n[2/6] Compose UI 테스트 — SKIP_UI_TEST=1 이라 건너뜁니다'
else
  cyan $'\n[2/6] Compose UI 테스트 (관리형 디바이스 — 첫 실행은 이미지 내려받기로 몇 분 걸립니다)'
  ./gradlew :app:pixel6api30DebugAndroidTest
fi

if [ "${SKIP_E2E:-0}" = "1" ]; then
  yellow $'\n[3/6] Maestro E2E — SKIP_E2E=1 이라 건너뜁니다'
  green $'\n단위 테스트와 UI 테스트를 통과했습니다.'
  exit 0
fi

cyan $'\n[4/6] 단말 연결 확인'
if ! adb devices | awk 'NR>1 && $2=="device"' | grep -q .; then
  red "연결된 Android 단말이 없습니다. 'adb devices' 로 확인 후 다시 실행하세요."
  red "기기 없이 1~2층만 돌리려면: SKIP_E2E=1 ./scripts/run-tests.sh"
  exit 1
fi
adb devices -l
adb shell svc power stayon usb || true   # 테스트 중 절전모드 진입 방지 (미지원 이미지에서도 계속)

cyan $'\n[5/6] 앱 빌드 및 단말 설치'
./gradlew installDebug

cyan $'\n[6/6] Maestro E2E 실행 + HTML 리포트 생성'
maestro_bin="${HOME}/.maestro/bin/maestro"
[ -x "$maestro_bin" ] || maestro_bin="maestro"   # PATH 에 등록된 경우

mkdir -p "$root/reports"

test_exit=0
"$maestro_bin" test "$root/.maestro" \
  --format HTML-DETAILED \
  --output "$root/reports/report.html" \
  --debug-output "$root/reports/debug" || test_exit=$?

# Maestro 리포트에는 <meta charset> 태그가 없어 브라우저가 한글을 깨서 보여주는 경우가 있다.
# 파일 자체는 UTF-8 이므로 charset 메타 태그만 보강한다.
report="$root/reports/report.html"
if [ -f "$report" ] && ! grep -q '<meta charset=' "$report"; then
  perl -0pi -e 's{<head>}{<head>\n    <meta charset="UTF-8">}' "$report"
fi

if [ -f "$report" ]; then
  echo "리포트: $report"
  if command -v open >/dev/null 2>&1; then open "$report" >/dev/null 2>&1 || true
  elif command -v xdg-open >/dev/null 2>&1; then xdg-open "$report" >/dev/null 2>&1 || true
  fi
else
  yellow "리포트 파일이 생성되지 않았습니다: $report"
fi

if [ "$test_exit" -eq 0 ]; then
  green $'\n모든 검증을 통과했습니다.'
else
  yellow $'\n일부 E2E 플로우가 실패했습니다. 리포트를 확인하세요.'
fi

exit "$test_exit"
