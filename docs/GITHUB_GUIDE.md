# GitHub 협업 가이드 (처음 쓰는 2인 팀 + Claude 에이전트용)

> 이 문서 하나만 따라 하면 됩니다. 0단계(최초 1회 준비)는 한 번만, 2단계(매일 작업 흐름)는 매번 반복합니다.

---

## 핵심 개념 5분 정리

| 용어 | 뜻 | 비유 |
|------|----|------|
| **저장소(repository, repo)** | 프로젝트 폴더 + 모든 변경 기록 | 공유 드라이브 폴더 |
| **커밋(commit)** | "여기까지 저장" 하는 스냅샷 | 게임 세이브 포인트 |
| **브랜치(branch)** | 원본(`main`)을 건드리지 않는 내 작업 공간 | 문서 사본 |
| **푸시(push) / 풀(pull)** | 내 컴퓨터 → GitHub 업로드 / GitHub → 내 컴퓨터 다운로드 | 업로드 / 동기화 |
| **PR(Pull Request)** | "내 브랜치를 main에 합쳐 주세요" 요청 + 리뷰 공간 | 결재 요청 |
| **머지(merge)** | 브랜치를 main에 합치기 | 결재 승인 후 반영 |
| **충돌(conflict)** | 둘이 같은 줄을 다르게 고쳐서 Git이 자동으로 못 합침 | 같은 문장을 둘이 다르게 수정 |

**황금 규칙: `main`은 항상 빌드되는 상태. 작업은 브랜치에서, 합치기는 PR로.**

```
main ──●──────────●──────────●───   (항상 동작하는 버전)
        \        /  \        /
 feat/A  ●──●──●     \      /        ← 팀원 A의 작업
                      ●──●──●        ← 팀원 B의 작업 (feat/B)
```

---

## 0단계. 최초 1회 준비 (각자 자기 Mac/PC에서)

### 0-1. GitHub 계정
두 사람 모두 https://github.com 가입. 아이디를 서로 공유.

### 0-2. 도구 설치 (Mac 기준)
```bash
# Homebrew가 없다면 https://brew.sh 의 설치 명령을 먼저 실행
brew install git gh
```
- Android Studio 설치 (https://developer.android.com/studio)
- Claude Code 설치되어 있는지 확인

> 이 명령들은 Claude에게 "0단계 도구 설치 도와줘"라고 해도 됩니다.

### 0-3. Git 내 정보 등록 (커밋에 이름이 찍힘)
```bash
git config --global user.name "내 이름"
git config --global user.email "GitHub에 등록한 이메일"
git config --global init.defaultBranch main
git config --global pull.rebase false
```

### 0-4. GitHub 로그인 (gh CLI)
```bash
gh auth login
```
선택지: `GitHub.com` → `HTTPS` → `Yes (git 인증에도 사용)` → `Login with a web browser` → 화면의 코드를 브라우저에 입력.
확인: `gh auth status`

---

## 1단계. 저장소 만들고 동료 초대 (팀원 A 한 명만)

이 폴더(`pleos-app`)에 이미 협업용 파일이 준비되어 있습니다.

### 1-1. 공개/비공개 결정
공모전 제출 전 아이디어 보호를 위해 **Private(비공개) 권장**.

### 1-2. 저장소 생성 + 업로드
```bash
cd hyundai-app-contest
git init
git add .
git commit -m "chore: 협업 환경 초기 설정"
gh repo create pleos-app --private --source=. --push
```

### 1-3. 동료 초대
```bash
gh api -X PUT repos/heokyeol/hyundai-app-contest/collaborators/<동료아이디> -f permission=push
```
또는 웹: 저장소 → **Settings → Collaborators → Add people**.
동료는 이메일/GitHub 알림에서 **초대 수락** 필수.

### 1-4. main 보호 규칙 켜기 (권장)
```bash
./scripts/setup-github.sh
```
- main에 직접 푸시 금지, PR 필수, 리뷰 1명 승인 필요, CI 통과 필요.
- 무료 계정의 **Private 저장소에서는 브랜치 보호가 안 될 수 있습니다** (GitHub 요금제 제한). 그 경우 스크립트가 알려주고, 대신 `CLAUDE.md` 규칙(main 직접 푸시 금지)으로 지킵니다. 학생이면 GitHub Education(무료 Pro)을 신청하면 사용 가능합니다.

### 1-5. Android 프로젝트 생성
Android Studio → New Project → **Automotive** 탭의 템플릿(또는 공모전 제공 템플릿) → 위치를 이 `pleos-app` 폴더로 지정.
그 다음 브랜치를 만들어 PR로 올리는 것을 **첫 연습**으로 하세요:
```bash
git switch -c chore/1-android-project
git add .
git commit -m "chore: 안드로이드 프로젝트 생성"
git push -u origin chore/1-android-project
gh pr create --fill
```

### 1-6. (팀원 B) 저장소 받기
```bash
gh repo clone heokyeol/hyundai-app-contest
cd hyundai-app-contest
```

---

## 2단계. 매일 작업 흐름 (둘 다, 매번 반복)

```
① 이슈 고르기 → ② main 최신화 → ③ 브랜치 생성 → ④ 작업·커밋
→ ⑤ 푸시 + PR → ⑥ 동료 리뷰 → ⑦ 머지 → ⑧ 정리
```

### ① 할 일은 이슈(Issue)로
```bash
gh issue create --title "홈 화면 만들기" --assignee @me
gh issue list
```
웹: 저장소 → Issues → New issue (템플릿 선택).
**누가 어떤 이슈를 맡았는지(Assignee)가 곧 충돌 방지책**입니다.

### ② ~ ③ 최신화 + 브랜치
```bash
git switch main
git pull
git switch -c feat/12-home-screen
```

### ④ 작업 + 커밋 (작게, 자주)
```bash
git status            # 뭐가 바뀌었는지
git add .
git commit -m "feat: 홈 화면 레이아웃 추가"
```

### ⑤ 푸시 + PR
```bash
./gradlew assembleDebug   # 빌드 되는지 먼저 확인
git push -u origin feat/12-home-screen
gh pr create --fill --reviewer <동료아이디>
```

### ⑥ 리뷰 (동료가)
```bash
gh pr list
gh pr checkout 15        # 동료 브랜치를 내 컴퓨터로 받아 실행해 보기
gh pr review 15 --approve            # 승인
gh pr review 15 --request-changes -b "회전 시 크래시 나요"   # 수정 요청
```

### ⑦ 머지 (승인 + CI 통과 후, PR 작성자가)
```bash
gh pr merge 15 --squash --delete-branch
```

### ⑧ 정리
```bash
git switch main
git pull
```

---

## 3단계. Claude 에이전트와 함께 쓰기

두 사람 모두 Claude가 `CLAUDE.md`를 자동으로 읽으므로 같은 규칙을 따릅니다. 아래 문장을 그대로 복사해서 쓰세요.

| 상황 | Claude에게 이렇게 말하기 |
|------|--------------------------|
| 작업 시작 | `이슈 #12 작업 시작해줘. main 최신화하고 규칙대로 브랜치 만들어줘.` |
| 중간 저장 | `지금까지 변경사항 커밋해줘.` |
| 작업 완료 | `빌드·테스트 확인하고 푸시한 다음 PR 만들어줘. 리뷰어는 <동료아이디>.` |
| 동료 PR 리뷰 | `PR #15 체크아웃해서 빌드해 보고 리뷰 코멘트 남겨줘.` |
| 리뷰 반영 | `PR #15에 달린 리뷰 코멘트 확인하고 수정해줘.` |
| main이 바뀌었을 때 | `main 최신 내용을 내 브랜치에 합쳐줘. 충돌 나면 설명해줘.` |
| 상태 파악 | `지금 열린 이슈랑 PR 현황 정리해줘.` |

**에이전트 사용 시 주의**
- Claude가 `--force`, `reset --hard`, 브랜치 삭제를 하려 하면 이유를 묻고 신중히 허락하세요.
- 둘의 Claude가 **같은 파일을 동시에** 고치면 충돌이 납니다. 이슈 담당자를 나눠서 피하세요.
- Claude가 만든 PR도 **사람이 한 번은 읽고** 승인하세요. 리뷰 = 서로 코드 이해하는 시간.

---

## 4단계. 충돌(conflict) 해결

PR 화면에 "This branch has conflicts" 또는 `git pull`/`git merge` 중 CONFLICT 메시지가 뜨면:

```bash
git switch feat/12-home-screen
git pull origin main          # main을 내 브랜치에 합치기 → 충돌 표시됨
```
파일 안에 이런 표시가 생깁니다:
```
<<<<<<< HEAD
내 코드
=======
main(동료)의 코드
>>>>>>> main
```
1. 둘 중 하나를 고르거나 둘을 합친 최종 코드로 고치고 `<<<` `===` `>>>` 줄을 지웁니다.
   (Android Studio: Git → Resolve Conflicts 화면이 편함 / 또는 Claude에게 "충돌 설명하고 해결안 보여줘")
2. 빌드 확인 후:
```bash
git add .
git commit -m "chore: main 병합 충돌 해결"
git push
```
**애매하면 동료에게 물어보기.** 상대 코드를 함부로 지우지 않기.

---

## 5단계. 자주 하는 실수와 복구

| 상황 | 해결 |
|------|------|
| main에서 작업해 버렸다 (커밋 전) | `git switch -c feat/새브랜치` — 변경사항이 그대로 따라감 |
| main에 커밋해 버렸다 (푸시 전) | `git switch -c feat/새브랜치` → `git switch main` → `git reset --hard origin/main` (Claude에게 "main에 실수로 커밋했어, 브랜치로 옮겨줘"라고 해도 됨) |
| 커밋 메시지 오타 (푸시 전) | `git commit --amend -m "새 메시지"` |
| 비밀 키를 커밋했다 | 즉시 키를 **폐기/재발급**하고 팀에 알리기. 기록에서 지우는 건 그 다음 문제 |
| `git push`가 거절됨 (rejected) | `git pull` 후 다시 `git push` |
| 뭐가 뭔지 모르겠다 | `git status` 결과를 Claude에게 보여주고 "지금 상태 설명해줘" |

---

## 6단계. 공모전 마감 대비

- 마감 전 버전은 태그로 고정: `gh release create v1.0-submit --generate-notes`
  (APK를 첨부하려면 `gh release upload v1.0-submit app/build/outputs/apk/debug/app-debug.apk`)
- 마감 2~3일 전부터는 **새 기능 금지, 버그 수정만** (feature freeze) 같은 규칙을 미리 합의.
- 데모 영상/발표 자료는 `docs/`에 함께 보관하면 버전 관리가 됩니다 (대용량 영상은 제외).
