# CLAUDE.md — 팀 공용 에이전트 규칙

이 파일은 두 팀원의 Claude 에이전트가 **똑같이** 따르는 규칙입니다.
Claude Code는 이 저장소에서 작업을 시작할 때 이 파일을 자동으로 읽습니다.
규칙을 바꾸고 싶으면 이 파일을 PR로 수정하세요 (혼자 몰래 바꾸지 않기).

## 프로젝트 개요

- 현대자동차 공모전 출품작: **Pleos(플레오스) 디스플레이/시스템용 안드로이드 앱**
- 대상 플랫폼: Android Automotive OS(AAOS) 기반 차량 인포테인먼트
- 언어/빌드: Kotlin, Gradle (Kotlin DSL), Android Studio
- 공모전에서 제공하는 SDK·에뮬레이터·가이드라인이 있으면 `docs/pleos-notes.md`에 정리하고, 그 내용을 이 파일의 일반 지식보다 우선한다.

## 팀원과 담당

| 이름 | GitHub ID | 주 담당 영역 |
|------|-----------|--------------|
| (팀원 A) | @heokyeol | (예: UI / 화면) |
| (팀원 B) | @contlg | (예: 데이터 / 차량 연동) |

담당 영역이 다른 파일을 수정해야 하면 PR 설명에 이유를 적는다.

## Git 작업 규칙 (반드시 지킬 것)

1. **`main`에 직접 커밋·푸시 금지.** 모든 작업은 브랜치에서 하고 PR로 합친다.
2. 작업 시작 전 항상 최신화:
   ```bash
   git switch main && git pull
   git switch -c <브랜치이름>
   ```
3. 브랜치 이름: `타입/이슈번호-짧은설명` (영문 소문자, 하이픈)
   - 예: `feat/12-home-screen`, `fix/18-crash-on-rotate`, `docs/3-readme`
   - 타입: `feat`(기능) `fix`(버그) `refactor` `docs` `chore`(설정/빌드) `test`
4. 커밋 메시지: `타입: 무엇을 했는지 (한국어 OK)`
   - 예: `feat: 홈 화면 차량 상태 카드 추가`
   - 한 커밋 = 한 가지 논리적 변경. 작게 자주 커밋.
5. 푸시 전 반드시:
   - 빌드 확인: `./gradlew assembleDebug`
   - 테스트 확인: `./gradlew test`
   - 실패하면 푸시하지 말고 사람에게 알린다.
6. PR은 `.github/pull_request_template.md` 형식을 채운다. 관련 이슈는 `Closes #번호`로 연결.
7. **강제 푸시(`git push --force`), `git reset --hard`, 브랜치 삭제, 히스토리 재작성은 사람의 명시적 허락 없이 하지 않는다.**
8. 충돌(conflict)이 나면 임의로 상대방 코드를 지우지 말고, 양쪽 의도를 설명한 뒤 사람에게 확인받는다.
9. 비밀값(API 키, 서명 키, `local.properties`)은 절대 커밋하지 않는다. `.gitignore` 참고.

## 동시 작업 충돌을 줄이는 규칙

- 같은 파일을 둘이 동시에 크게 고치지 않도록 이슈에서 담당자를 먼저 지정(Assignee).
- 공용 파일(`build.gradle.kts`, `libs.versions.toml`, `AndroidManifest.xml`, 테마/색상, 네비게이션 그래프)을 고칠 땐 **변경을 최소화**하고 PR 제목에 `[공용]`을 붙인다.
- 의존성 추가는 `gradle/libs.versions.toml`에만, 알파벳 순으로 추가한다 (충돌 위치 분산).
- 브랜치는 오래 살리지 않는다. 1~2일 안에 PR → 머지가 목표.

## 코드 규칙

- Kotlin 공식 스타일. 들여쓰기 4칸 (`.editorconfig` 참고).
- 화면 문자열은 하드코딩하지 말고 `res/values/strings.xml`에.
- 차량 환경 고려: 운전 중 주의분산 최소화(큰 터치 영역, 간단한 화면, 긴 텍스트 입력 지양). 관련 판단이 필요하면 코드 주석이나 PR에 근거를 남긴다.
- 확실하지 않은 Pleos/AAOS API는 추측해서 쓰지 말고, 문서 근거를 확인하거나 사람에게 묻는다.

## 에이전트 작업 방식

- 작업 시작 시: 어떤 이슈를 하는지, 어떤 브랜치에서 하는지 먼저 말한다.
- 작업 끝날 때: 무엇을 바꿨는지, 빌드/테스트 결과, 남은 일을 요약한다.
- 상대 팀원의 PR을 리뷰할 땐: `gh pr checkout <번호>` 후 빌드해 보고, 문제점은 PR 코멘트로 남긴다.
