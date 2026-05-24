# Chatting App API Design

This design follows the WhatsApp-style API reference used during setup: REST for account and setup work, WebSocket/STOMP for live chat events, a messages store with status, a groups store, and last-seen presence.

The backend package layout follows the layered Spring Boot architecture described by [GeeksforGeeks](https://www.geeksforgeeks.org/springboot/spring-boot-architecture/): controller/presentation, service/business, repository/entity persistence, and database layers.

## REST API

Base URL: `http://localhost:8080/api`

Authentication uses a bearer token returned by login/register. Tokens are stored as hashes on the backend.

| Method | Path | Purpose |
| --- | --- | --- |
| `POST` | `/auth/register/start` | Start SMS registration and send a verification code |
| `POST` | `/auth/register/verify` | Verify SMS code, create account, and return token |
| `POST` | `/auth/register` | Optional direct registration when enabled by config |
| `POST` | `/auth/login` | Login and return token |
| `GET` | `/auth/me` | Read current user |
| `POST` | `/auth/logout` | Logout and update last-seen |
| `GET` | `/users?search=` | Search users/contacts |
| `GET` | `/presence/{userId}` | Read online/last-seen status |
| `GET` | `/conversations` | List direct and group conversations |
| `POST` | `/conversations/direct` | Start or fetch a one-to-one conversation |
| `POST` | `/conversations/groups` | Create a group conversation |
| `GET` | `/conversations/{conversationId}` | Read conversation details |
| `DELETE` | `/conversations/{conversationId}` | Delete/hide a chat for the current user |
| `GET` | `/conversations/{conversationId}/messages?limit=50&before=` | Load paginated message history |
| `POST` | `/conversations/{conversationId}/messages` | Send message over REST fallback |
| `PATCH` | `/messages/{messageId}/status` | Mark message `SENT`, `DELIVERED`, or `READ` |
| `POST` | `/media` | Upload an authenticated image file to MinIO |

### Message Shape

```json
{
  "id": "6a0c0c0e9fb98d4fe48f5b0d",
  "conversationId": "6a0c0c0e9fb98d4fe48f5b0c",
  "sender": {
    "id": "6a0c0c0e9fb98d4fe48f5b0a",
    "username": "max",
    "displayName": "Max",
    "online": true,
    "lastSeenAt": "2026-05-19T06:30:00Z"
  },
  "content": "Hi Emily",
  "assetKey": null,
  "assetUrl": null,
  "assetContentType": null,
  "type": "TEXT",
  "status": "SENT",
  "createdAt": "2026-05-19T06:31:00Z"
}
```

### Message Page Shape

```json
{
  "messages": [],
  "nextBefore": "2026-05-19T06:31:00Z",
  "hasMore": true
}
```

Use `nextBefore` as the next `before` query parameter to load older messages.

For image messages, upload the file first:

1. `POST /api/media` as `multipart/form-data` with field `file`.
2. The backend validates image size/type, stores the bytes in MinIO, and returns `objectKey` plus a short-lived `assetUrl`.
3. Send the chat message with `type: "IMAGE"` and `assetKey`.

MongoDB stores the message metadata and MinIO object key. MinIO stores the actual image bytes. Message responses include a presigned `assetUrl` for browser rendering.

## WebSocket API

Endpoint: `http://localhost:8080/ws`

The browser connects with STOMP and sends:

```text
Authorization: Bearer <token>
```

Conversation topic subscriptions are authorized per conversation. A user must be a participant before subscribing to `/topic/conversations/{conversationId}` or its status/typing child topics.

### Client Sends

| Destination | Purpose |
| --- | --- |
| `/app/chat.send` | Send one-to-one or group message |
| `/app/message.status` | Publish delivered/read status update |
| `/app/chat.typing` | Publish typing state |

### Client Subscribes

| Topic | Payload |
| --- | --- |
| `/topic/conversations/{conversationId}` | New `MessageDto` |
| `/topic/conversations/{conversationId}/status` | `MessageStatusEvent` |
| `/topic/conversations/{conversationId}/typing` | `TypingEvent` |

## Data Model

| Collection | Main Fields |
| --- | --- |
| `users` | username, display name, avatar, online, last seen |
| `auth_tokens` | token hash, user ID, expiry |
| `registration_verifications` | pending SMS registration data, OTP hash, attempts, expiry |
| `conversations` | direct/group flag, direct key, group name, description, creator ID, participant IDs, timestamps |
| `messages` | conversation ID, sender ID, content, asset URL, type, status, timestamp |

## Production Extensions

The current implementation is an MVP. A production version should split or harden these areas later: API gateway, WebSocket handlers, connection manager/cache, message service, asset service, object storage, message queue, groups service, and last-seen service.
