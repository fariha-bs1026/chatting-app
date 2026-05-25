# ChatFlow Project Progress

Last updated: May 25, 2026

## Current Status

ChatFlow is a working Spring Boot API + React web chat MVP. The app supports SMS-style registration, login/logout, user search, direct chats, groups, realtime messaging, read receipts, profile updates, MinIO-backed media, temporary messages, message deletion, typing indicators, and browser audio/video calls.

It is still not production-ready like WhatsApp. The main remaining work is around contact modeling, push notifications, stronger session/device handling, TURN-backed calls, broader frontend integration coverage, production secrets, CI, and deployment operations.

## Completed Backend Work

- Layered Spring Boot structure: `controller`, `service`, `repository`, `entity`, `dto`, `config`.
- MongoDB persistence with development auto-index creation.
- Token authentication with hashed server-side token storage.
- HttpOnly auth cookie support for browser sessions.
- Login/logout and current-user API.
- SMS registration flow with dummy and Twilio providers.
- OTP expiry, retry limits, resend cooldown, and daily caps.
- Request validation with localized validation messages.
- Swagger/OpenAPI support behind the `SWAGGER_ENABLED` flag.
- Direct and group conversations.
- Direct conversation reuse with a deterministic direct key.
- Per-user chat hide/delete.
- Cursor-style message history pagination.
- Message receipts and aggregate `SENT`, `DELIVERED`, `READ` status.
- Message delete for me and delete for everyone.
- Temporary messages with expiry tombstones.
- MinIO-backed authenticated media upload.
- Media type support for `IMAGE`, `AUDIO`, `VIDEO`, and `FILE` metadata.
- Presigned media URLs in message/profile responses.
- Profile update with avatar upload/delete.
- Presence endpoint with online/last-seen data.
- WebSocket/STOMP messaging with participant authorization.
- Typing event endpoint with participant validation.
- Audio/video call signaling over WebSocket.
- Lombok for entity boilerplate.
- Cleaned wildcard imports in backend production code.
- MongoDB indexes for conversation listing, receipts, tokens, OTP expiry, and verification lookup patterns.

## Completed Frontend Work

- React + Vite app.
- Login and SMS registration UI.
- Language dropdown with persisted English/Bangla preference.
- Theme toggle with persisted light/dark preference.
- Central API helper and central frontend error mapping.
- Chat list with swipe/drag reveal for chat delete.
- Direct chat and group creation flows.
- Message history pagination.
- Realtime message receive.
- Read receipt status icons.
- Delete message for me and delete message for everyone.
- Temporary message selector.
- Media picker for image, audio, and video uploads.
- Inline image, audio, and video rendering.
- Profile editor with name, phone, avatar upload, and avatar delete.
- Frontend media validation errors for invalid file types.
- Typing indicator UI using the existing WebSocket typing endpoint.
- Direct-chat audio and video call controls using WebRTC signaling.
- Nginx CSP updated for blob/media playback and MinIO media URLs.
- Vitest + React Testing Library unit/component tests.
- Frontend coverage script with a 40% global threshold baseline.

## Current API Areas

Authentication:

- `POST /api/auth/register/start`
- `POST /api/auth/register/verify`
- `POST /api/auth/register`
- `POST /api/auth/login`
- `GET /api/auth/me`
- `POST /api/auth/logout`

Users:

- `GET /api/users?search=...`
- `PATCH /api/users/me`

Conversations:

- `GET /api/conversations`
- `POST /api/conversations/direct`
- `POST /api/conversations/groups`
- `GET /api/conversations/{conversationId}`
- `DELETE /api/conversations/{conversationId}`
- `GET /api/conversations/{conversationId}/messages?limit=50&before=...`
- `POST /api/conversations/{conversationId}/messages`

Messages:

- `PATCH /api/messages/{messageId}/status`
- `DELETE /api/messages/{messageId}?scope=ME`
- `DELETE /api/messages/{messageId}?scope=EVERYONE`

Media:

- `POST /api/media`

Presence:

- `GET /api/presence/{userId}`

WebSocket:

- `/ws`
- `/app/chat.send`
- `/app/message.status`
- `/app/chat.typing`
- `/app/call.signal`
- `/topic/conversations/{conversationId}`
- `/topic/conversations/{conversationId}/status`
- `/topic/conversations/{conversationId}/typing`
- `/topic/conversations/{conversationId}/calls`
- `/user/queue/conversations`

## Running App

Local URLs:

- Frontend: `http://127.0.0.1:5173`
- Backend: `http://localhost:8080`
- MongoDB: `mongodb://localhost:27017/chatting_app`
- MinIO API: `http://localhost:9000`
- MinIO console: `http://localhost:9001`

Docker URLs:

- Frontend: `http://localhost:3000`
- Backend: `http://localhost:8080`
- MinIO API: `http://localhost:9000`
- MinIO console: `http://localhost:9001`
- MongoDB remains local at `mongodb://localhost:27017/chatting_app`.

## Verified

These checks passed on May 25, 2026:

```powershell
cd backend
mvn test
```

Result: 53 tests passed, 0 failures.

```powershell
cd frontend
npm run test
npm run test:coverage
npm audit --audit-level=moderate
```

Result: 38 frontend tests passed; coverage gate passed at 40%+ global thresholds; npm audit found 0 vulnerabilities.

```powershell
cd frontend
npm run build
```

Result: Vite production build succeeded.

```powershell
docker-compose up -d --build
curl.exe -sS http://localhost:8080/api/health
curl.exe -sS -I http://localhost:3000
```

Result: backend, frontend, and MinIO containers were running; backend health returned `running`; frontend returned HTTP 200.

## Known Gaps

- Real phone contact import and device contact permission flow.
- Contact model, blocking, and privacy settings.
- Password reset and optional email verification.
- Refresh-token rotation or multi-device session management.
- Logout all devices.
- Push notifications.
- End-to-end encryption.
- TURN server configuration for reliable calls across NAT/firewalls.
- Call history and missed-call state.
- Message edit, reactions, replies, and forwarding.
- Search inside a conversation.
- Archived, muted, and pinned chats.
- Broader frontend integration tests for full chat/call workflows.
- CI pipeline for backend tests and frontend build.
- Production index migrations, secrets handling, logging, and monitoring.

## Recommended Next Steps

1. Split `frontend/src/App.jsx` into chat, sidebar, profile, media, and call components/hooks.
2. Add frontend tests for auth, language switching, media validation, message deletion, and calls.
3. Add a contacts collection with phone-number matching and blocking.
4. Add TURN configuration for WebRTC calls.
5. Add CI and production deployment profiles.
