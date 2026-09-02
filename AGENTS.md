# AGENTS.md

## 프로젝트 작업 규칙

이 저장소는 사람과 AI가 같은 기준으로 작업하기 위해 공통 규칙을 사용한다.

코드 또는 문서를 변경하기 전에 대상 파일과 관련 설정을 먼저 확인한다. 상세 문서 작업 규칙은 아래 문서를 따른다.

- [docs/README.md](./docs/README.md)
- [문서-작성-규칙.md](./docs/00-project/문서-작성-규칙.md)
- [docs/AGENTS.md](./docs/AGENTS.md)
- [docs/CLAUDE.md](./docs/CLAUDE.md)
- [Pull Request Template](./.github/PULL_REQUEST_TEMPLATE.md)

## 최소 작업 원칙

- 요청 범위 안에서 작고 명확하게 변경한다.
- 기존 구조, 스타일, 네이밍, 테스트 방식을 우선 따른다.
- 사용자 변경사항을 임의로 되돌리거나 관련 없는 파일을 정리하지 않는다.
- secret, `.env`, token, password, 개인정보를 commit 또는 문서에 기록하지 않는다.
- 새 파일을 만들기 전에 같은 이름의 파일이 이미 있는지 확인한다.
- 변경 후 가능한 가장 가까운 범위의 검사 또는 test를 실행한다.
- AI가 PR을 생성하거나 PR 본문을 수정할 때는 `.github/PULL_REQUEST_TEMPLATE.md`를 먼저 읽고 따른다.
- PR 체크 항목은 실제로 수행·확인한 내용만 표시하고, 미실행 항목과 후속 조치는 PR 본문에 기록한다.
