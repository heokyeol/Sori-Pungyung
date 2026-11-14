#!/usr/bin/env bash
# 저장소 라벨 생성 + main 브랜치 보호 규칙 설정 (저장소 만든 사람이 1회 실행)
set -euo pipefail

command -v gh >/dev/null || { echo "gh가 없습니다: brew install gh"; exit 1; }
gh auth status >/dev/null 2>&1 || { echo "먼저 gh auth login 하세요"; exit 1; }

REPO=$(gh repo view --json nameWithOwner -q .nameWithOwner)
echo "대상 저장소: $REPO"

echo "▶ 라벨 생성"
for spec in "enhancement:a2eeef:기능" "bug:d73a4a:버그" "공용파일:fbca04:여러 사람이 건드리는 공용 파일 수정" "docs:0075ca:문서" "blocked:000000:막힘 - 도움 필요"; do
  IFS=: read -r name color desc <<<"$spec"
  gh label create "$name" --color "$color" --description "$desc" --force >/dev/null && echo "  - $name"
done

echo "▶ main 브랜치 보호"
if gh api -X PUT "repos/$REPO/branches/main/protection" --input - >/dev/null 2>/tmp/bp_err.txt <<'JSON'
{
  "required_status_checks": { "strict": true, "contexts": ["build"] },
  "enforce_admins": false,
  "required_pull_request_reviews": { "required_approving_review_count": 1 },
  "restrictions": null,
  "allow_force_pushes": false,
  "allow_deletions": false
}
JSON
then
  echo "  ✅ 보호 규칙 적용: PR 필수, 승인 1명, CI(build) 통과 필요, 강제 푸시 금지"
  echo "  (급할 때 관리자는 규칙을 우회할 수 있도록 enforce_admins=false 로 두었습니다)"
else
  echo "  ⚠️  보호 규칙 적용 실패:"
  sed 's/^/     /' /tmp/bp_err.txt
  echo "     무료 계정의 Private 저장소는 브랜치 보호가 지원되지 않을 수 있습니다."
  echo "     → 팀 규칙(main 직접 푸시 금지)으로 대신 지키거나, GitHub Education/Pro를 사용하세요."
fi

echo "▶ 머지 후 브랜치 자동 삭제, squash 머지 사용"
gh repo edit "$REPO" --delete-branch-on-merge --enable-squash-merge >/dev/null && echo "  ✅ 완료"
