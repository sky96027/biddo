# 작업 흐름 규칙

## 순서

1. **사용자 명령 수신** — 작업 범위와 요구사항 확인. 불명확하면 질문할 것.
2. **브랜치 결정** — 신규 기능/수정이면 `feature/` 또는 `fix/` 브랜치 생성. 단순 문서/설정은 짧으면 현재 브랜치, 길면 `docs/` 또는 `chore/` 브랜치 생성.
3. **harness 확인** — 대상 도메인의 harness(`port-adapter/` 또는 `layered/`)를 읽을 것.
4. **구현** — 코딩 컨벤션, `.claude/rules/architecture.md`, `.claude/rules/api-response.md`를 따를 것.
5. **테스트** — 단위 테스트 필수. `.claude/harnesses/test/prompt.md` 참조.
6. **커밋** — `.github/commit_template.txt` 형식. 사용자 확인 후 커밋할 것.
7. **PR** — 사용자가 요청한 경우에만 생성. `.github/pull_request_template.md` 형식.

## 규칙

- 사용자 지시 없이 커밋/푸시/PR 생성 **금지**.
- 브랜치 생성 여부가 애매하면 사용자에게 확인할 것.
- 테스트 실패 시 수정 후 재실행. 실패 상태로 커밋 **금지**.