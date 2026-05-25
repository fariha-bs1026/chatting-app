# Chatting App API Design

Base URL: `http://localhost:8080/api`

The backend uses REST for account/setup/media/history operations and WebSocket/STOMP for live conversation events. Authentication is cookie-based for the browser, with server-side hashed token storage. Bearer tokens are still accepted where explicitly supplied by clients.

## REST API

| Method | Path | Purpose |
| --- | --- | --- |
| `POST` | `/auth/register/start` | Start SMS registration and send a verification code |
| `POST` | `/auth/register/verify` | Verify SMS code, create account, and set auth cookie |
| `POST` | `/auth/register` | Optional direct registration when enabled by config |
| `POST` | `/auth/login` | Login and set auth cookie |
| `GET` | `/auth/me` | Read current user |
| `POST` | `/auth/logout` | Logout and expire auth cookie |
| `GET` | `/users?search=` | Search users |
| `PATCH` | `/users/me` | Update display name, phone number, and avatar |
| `GET` | `/presence/{userId}` | Read online/last-seen status |
| `GET` | `/conversations` | List direct and group conversations |
| `POST` | `/conversations/direct` | Start or fetch a one-to-one conversation |
| `POST` | `/conversations/groups` | Create a group conversation |
| `GET` | `/conversations/{conversationId}` | Read conversation details |
| `DELETE` | `/conversations/{conversationId}` | Hide/delete a chat for the current user |
| `GET` | `/conversations/{conversationId}/messages?limit=50&before=` | Load paginated message history |
| `POST` | `/conversations/{conversationId}/messages` | Send message over REST fallback |
| `PATCH` | `/messages/{messageId}/status` | Mark message `SENT`, `DELIVERED`, or `READ` |
| `DELETE` | `/messages/{messageId}?scope=ME` | Hide a message for the current user |
| `DELETE` | `/messages/{messageId}?scope=EVERYONE` | Replace sender-owned message with a tombstone |
| `POST` | `/media` | Upload authenticated image, audio, or video media to MinIO |

Swagger/OpenAPI is available at `/swagger-ui/index.html` when `SWAGGER_ENABLED=true`.

## Message Shape

```json
{
  "id": "message-id",
  "conversationId": "conversation-id",
  "sender": {
    "id": "user-id",
    "username": "max",
    "displayName": "Max",
    "avatarUrl": "https://signed-avatar-url",
    "online": true,
    "lastSeenAt": "2026-05-25T01:30:00Z"
  },
  "content": "Hi Emily",
  "assetKey": null,
  "assetUrl": null,
  "assetContentType": null,
  "type": "TEXT",
  "status": "SENT",
  "deletedForEveryone": false,
  "expired": false,
  "deletedAt": null,
  "expiresAt": null,
  "createdAt": "2026-05-25T01:31:00Z"
}
```

Allowed message types are:

- `TEXT`
- `IMAGE`
- `AUDIO`
- `VIDEO`
- `FILE`

For media messages:

1. Upload the file with `POST /api/media` as `multipart/form-data` field `file`.
2. The backend validates ownership, signature/type, and size, then stores bytes in MinIO.
3. Send a message with `assetKey` and the matching type, or send `TEXT` and let the backend infer the type from the uploaded object.

MongoDB stores message metadata and MinIO object keys. MinIO stores the binary media. Message responses include short-lived presigned `assetUrl` values.

Temporary messages use `expiresInSeconds` on send. Valid values are 1 second through 7 days.

## WebSocket API

Endpoint: `http://localhost:8080/ws`

Conversation topic subscriptions are authorized per conversation. A user must be a participant before subscribing to any `/topic/conversations/{conversationId}` topic.

### Client Sends

| Destination | Payload | Purpose |
| --- | --- | --- |
| `/app/chat.send` | `SendMessageRequest` | Send direct or group message |
| `/app/message.status` | `MessageStatusEvent` | Publish delivered/read status update |
| `/app/chat.typing` | `TypingEvent` | Publish typing state |
| `/app/call.signal` | `CallSignalEvent` | Exchange WebRTC offer/answer/ICE/end/reject signals |

### Client Subscribes

| Topic | Payload |
| --- | --- |
| `/user/queue/conversations` | Viewer-specific `ConversationDto` updates |
| `/topic/conversations/{conversationId}` | New or updated `MessageDto` |
| `/topic/conversations/{conversationId}/status` | `MessageStatusEvent` |
| `/topic/conversations/{conversationId}/typing` | `TypingEvent` |
| `/topic/conversations/{conversationId}/calls` | `CallSignalEvent` |

## Data Model

| Collection | Main Fields |
| --- | --- |
| `users` | username, display name, phone, avatar key, online, last seen |
| `auth_tokens` | token hash, user id, expiry |
| `registration_verifications` | pending SMS registration data, OTP hash, attempts, expiry |
| `conversations` | direct/group flag, direct key, group metadata, participant ids, hidden-for ids, timestamps |
| `messages` | conversation id, sender id, content, asset key/type/content-type, deletion/expiry state, timestamp |
| `message_receipts` | message id, conversation id, user id, status, updated timestamp |

## Production Extensions

- Add a real contacts collection and phone-number matching.
- Add refresh-token rotation or a stronger device/session model.
- Add push notifications.
- Add TURN for reliable WebRTC calls.
- Add frontend tests and CI.
- Add production index migrations instead of relying only on annotation-driven index creation.
