# P1 메이트 API 명세

- 작성일: 2026-09-07
- 최종 수정일: 2026-09-08
- 버전: v0.2
- 상태: 검토 중

## 범위와 정책

- 흐름은 `모집글 → 문의 채팅 → 명시적 참가 신청 → 수락·거절 → 앱 내 알림`이다. 문의와 신청은 독립적이며, 채팅 없이도 신청할 수 있다.
- 정원은 작성자를 포함한다. 생성 시 작성자는 `HOST`/`ACCEPTED` 참가 행이며 `acceptedCount`는 1이다.
- 같은 사용자는 같은 모집글에 한 번만 신청할 수 있다. 거절·취소·철회 뒤 재신청은 허용하지 않는다.
- 거절, 신청 취소, 수락 후 철회, 모집 취소 뒤 해당 문의방은 읽기 전용이다. `CLOSED` 뒤에는 수락자의 기존 문의방만 전송할 수 있고 새 방은 만들 수 없다.
- 문의방 생성은 모집글 행을 잠근 뒤 기존 방을 먼저 찾는다. 기존 방은 종료 상태여도 반환하고, 새 방은 `RECRUITING` 및 시작 전일 때만 만든다.
- 읽음 위치는 방 행 잠금 아래 `max(현재 sequence, 요청 메시지 sequence)`로만 갱신한다. 다른 방의 메시지는 읽음 대상으로 지정할 수 없다.
- 앱 내 메이트 알림은 수신자의 `inAppEnabled && mateEnabled`가 모두 참일 때만 만든다. 알림 미생성은 업무 처리 성공에 영향을 주지 않는다.

## 공통 계약

- 모든 응답은 `ApiResponse<T>`의 `data`에 담긴다. 목록은 `items`, `nextCursor`, `hasNext`를 갖는 `CursorPageResponse<T>`다.
- 목록 `size` 기본값은 20, 최대값은 100이다. cursor는 서버가 반환한 값을 그대로 재사용한다.
- 작성자·신청자·방 참여자·알림 수신자는 access token의 사용자로 결정한다. 요청 본문으로 사용자 ID를 지정할 수 없다.
- `version`이 필요한 수정 요청에서 현재 값과 다르면 `409 VERSION_CONFLICT`다.

## API

| Method | Path | 요청 | 성공 응답 |
|---|---|---|---|
| GET | `/api/v1/mate-posts` | `sportCode`, `regionId`, `from`, `to`, `difficulty`, `status`, `cursor`, `size` | 모집글 cursor 목록 |
| GET | `/api/v1/mate-posts/{postId}` | 없음 | 모집글 상세, 내 신청·문의방·허용 행동 |
| POST | `/api/v1/mate-posts` | `Idempotency-Key: UUID`, `CreateMatePostRequest` | 모집글, `201` |
| PATCH | `/api/v1/mate-posts/{postId}` | `UpdateMatePostRequest` | 수정된 모집글 |
| POST | `/api/v1/mate-posts/{postId}/close` | `{ version }` | 마감된 모집글 |
| POST | `/api/v1/mate-posts/{postId}/cancel` | `{ version }` | 취소된 모집글 |
| POST | `/api/v1/mate-posts/{postId}/applications` | 없음 | 내 신청, `201` |
| GET | `/api/v1/mate-posts/{postId}/applications/me` | 없음 | 내 신청 또는 `null` |
| GET | `/api/v1/mate-posts/{postId}/applications` | `status`, `cursor`, `size` | 작성자용 신청 cursor 목록 |
| POST | `/api/v1/mate-posts/{postId}/applications/{id}/accept` | `{ version }` | 수락된 신청 |
| POST | `/api/v1/mate-posts/{postId}/applications/{id}/reject` | `{ version }` | 거절된 신청 |
| POST | `/api/v1/mate-posts/{postId}/applications/{id}/cancel` | `{ version }` | 신청 취소 또는 수락 후 철회 |
| POST | `/api/v1/mate-posts/{postId}/inquiry-room` | 없음 | 기존 방 `200` 또는 신규 방 `201` |
| GET | `/api/v1/mate-chat-rooms` | `cursor`, `size` | 내 문의방 cursor 목록 |
| GET | `/api/v1/mate-chat-rooms/{id}` | 없음 | 문의방 상세 |
| GET | `/api/v1/mate-chat-rooms/{id}/messages` | `beforeSequence` 또는 `afterSequence`, `size` | 메시지 `items` 목록 |
| POST | `/api/v1/mate-chat-rooms/{id}/messages` | `{ clientMessageId, body }` | 신규 `201`, 동일 재전송 `200` |
| PUT | `/api/v1/mate-chat-rooms/{id}/read` | `{ lastReadMessageId }` | 반영된 방 읽음 상태 |
| GET | `/api/v1/notifications` | `unreadOnly`, `cursor`, `size` | 알림 cursor 목록 |
| PATCH | `/api/v1/notifications/{id}/read` | 없음 | 읽음 알림 |
| GET | `/api/v1/notifications/unread-count` | 없음 | `{ unreadCount }` |

## 요청·응답 DTO

| DTO | 핵심 필드 |
|---|---|
| `CreateMatePostRequest` | `title` 1~100, `body` 1~5000, `sportCode`, `regionId`, 미래 `scheduledStartAt`, 양수 `durationSeconds`, `locationSummary` 1~255, `capacity` 2~100, `difficulty`, 0 이상 선택 `estimatedCostKrw` |
| `UpdateMatePostRequest` | `version`과 변경할 필드. `title`, `body`, `capacity`는 항상 수정할 수 있고, `sportCode`, `regionId`, `scheduledStartAt`, `durationSeconds`, `locationSummary`, `difficulty`, `estimatedCostKrw`는 첫 MEMBER 신청 전까지만 수정할 수 있다. 수락 인원보다 정원을 낮출 수 없다. |
| `MatePostResponse` | `id`, `authorId`, `author:{userId,nickname}`, `sportCode`, `regionId`, `title`, `body`, `scheduledStartAt`, `durationSeconds`, `locationSummary`, `capacity`, `difficulty`, `estimatedCostKrw`, `acceptedCount`, `remainingSlots`, `status`, `version`, `createdAt`, `updatedAt` |
| `MatePostDetailResponse` | 모집글 응답 필드와 `myApplication`, `myInquiryRoomId`, `allowedActions` |
| `MateApplicationResponse` | `id`, `matePostId`, `userId`, `applicant`, `status`, `statusReason`, 처리 직후 모집글 상태인 `postStatus`, 최신 `acceptedCount`, `appliedAt`, `respondedAt`, `canceledAt`, `version` |
| `MateRoomResponse` | `id`, `postId`, 작성자·문의자 요약, `status`(`ACTIVE`/`READ_ONLY`), `readOnlyReason`, 마지막 메시지·sequence, 양쪽/내 읽음 sequence, `unreadCount` |
| `MateMessageResponse` | `id`, `roomId`, `senderId`, `clientMessageId`, `sequenceNo`, `body`, `status:SENT`, `createdAt` |
| `NotificationResponse` | `id`, `category:MATE`, `type`, 표시 `message`, 선택 `target:{type,id}`, `status:UNREAD|READ`, `readAt`, `createdAt` |

## 중복·동시성·알림

- 생성의 `Idempotency-Key`는 UUID 필수다. 같은 유효 키와 같은 정규화 요청 hash는 최초 성공 응답(`201`)을 그대로 재생한다. hash가 다르면 `409 IDEMPOTENCY_KEY_CONFLICT`다. 키 유효기간은 24시간이며, 만료 키는 새 생성 요청으로 처리한다.
- 모집글 쓰기는 모집글 행 `PESSIMISTIC_WRITE` 잠금으로 직렬화한다. 마지막 자리 수락과 `FULL` 변경은 한 트랜잭션에서 커밋한다.
- 메시지는 `(room_id, sender_id, client_message_id)`로 멱등 처리한다. 같은 ID·본문은 기존 메시지를 반환하고, 같은 ID·다른 본문은 `409 IDEMPOTENCY_KEY_CONFLICT`다.
- 이벤트 key는 `mate_inquiry:{roomId}`, `mate_message:{messageId}`, `mate_application_received:{applicationId}`, `mate_application:{applicationId}:{status}`, `mate_participation:{applicationId}:{status}`, `mate_post_closed:{postId}:{userId}`, `mate_post_canceled:{postId}:{userId}`다.
- 마감·취소 알림은 참가 신청자와 기존 문의자를 합쳐 사용자당 한 건만 저장한다. 빈 문의방 생성 자체로는 알림을 만들지 않는다.

## 오류와 접근 제어

| 상황 | 오류 |
|---|---|
| 종료·정원 도달·시작 이후의 신청/새 문의 | `409 MATE_POST_NOT_RECRUITING` |
| 정원 초과 수락 | `409 MATE_CAPACITY_EXCEEDED` |
| 기존 신청 이력 재신청 | `409 MATE_APPLICATION_ALREADY_EXISTS` |
| 읽기 전용 방 전송 | `409 MATE_CHAT_READ_ONLY` |
| 다른 payload의 멱등 키 재사용 | `409 IDEMPOTENCY_KEY_CONFLICT` |
| 오래된 version | `409 VERSION_CONFLICT` |
| 타인 방·타인 신청·타인 알림 | `404 RESOURCE_NOT_FOUND` |
| 공개 모집글에서 작성자 권한 위반 | `403 FORBIDDEN_RESOURCE` |
