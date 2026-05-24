# ChatFlow Project Progress

Last updated: May 21, 2026

## Current Status

ChatFlow is now a working Spring Boot API + React web app MVP. It is not production-ready like WhatsApp, but the main demo flow is in place: users can register with SMS-style verification, log in, search users, start direct chats, create groups, and send realtime messages through WebSocket.

For a dummy project, the app is roughly at a solid MVP stage. For a real consumer chat app, the remaining work is still significant because production messaging needs stronger security, contact syncing, media storage, device handling, notifications, and delivery reliability.

## Completed Backend Work

- Separate backend folder at `backend/`.
- Spring Boot layered structure:
  - `controller`
  - `service`
  - `repository`
  - `entity`
  - `dto`
  - `config`
- MongoDB integration with Spring Data MongoDB.
- User account persistence in the `users` collection.
- Token-based authentication using bearer tokens.
- Hashed auth-token storage in MongoDB.
- TTL cleanup support for expired auth tokens.
- Login and logout APIs.
- SMS registration flow:
  - `POST /api/auth/register/start`
  - `POST /api/auth/register/verify`
- Dummy SMS provider for local development.
- Twilio SMS provider for real SMS delivery.
- Pending verification storage in MongoDB with expiry support.
- OTP resend cooldown and daily request limits.
- Direct conversation creation.
- Direct conversation reuse with a uniqueness key.
- Group conversation creation.
- Per-user chat delete/hide API.
- Cursor-style message history pagination.
- Message sending API.
- WebSocket/STOMP message sending.
- WebSocket conversation-topic subscription authorization.
- WebSocket message status updates.
- WebSocket typing event endpoint with participant validation.
- Message status support:
  - `SENT`
  - `DELIVERED`
  - `READ`
- Basic presence support:
  - online
  - last seen
- CORS setup for the React dev server.
- Global API exception handling.
- Public health endpoint at `/api/health`.
- Backend Dockerfile.
- Backend `.env.example`.

## Completed Frontend Work

- Separate frontend folder at `frontend/`.
- React + Vite setup.
- API helper for backend requests.
- Login screen.
- Registration screen with phone number and SMS verification code.
- Dummy local OTP display when using the dummy SMS provider.
- ChatFlow branding with a custom icon.
- Soft light theme.
- Soft dark theme.
- Theme toggle with persisted theme preference.
- Chat list.
- Direct chat opening.
- Group creation UI.
- Message composer.
- Optional asset URL field for image/file-style messages.
- MinIO-backed image upload endpoint and frontend image picker.
- Inline image rendering in chat bubbles using presigned media URLs.
- Realtime WebSocket message receiving.
- Message status icons.
- Load older messages control for paginated history.
- Contact info panel.
- People tab updated to be closer to WhatsApp behavior:
  - Contacts view shows existing direct-chat contacts.
  - Search view searches for new people.
  - It no longer shows every user by default.
- Stable list row layout so names do not resize the people list awkwardly.
- Auth screen, logo, theme toggle, and status icon split into reusable components.
- Frontend Dockerfile and Nginx static server config.
- Generated UI design samples saved under `images/`.

## Current API Areas

Authentication:

- `POST /api/auth/register/start`
- `POST /api/auth/register/verify`
- `POST /api/auth/register`
- `POST /api/auth/login`
- `POST /api/auth/logout`

Users:

- `GET /api/users?search=...`

Conversations:

- `GET /api/conversations`
- `POST /api/conversations/direct`
- `POST /api/conversations/groups`
- `DELETE /api/conversations/{conversationId}`
- `GET /api/conversations/{conversationId}/messages?limit=50&before=...`
- `POST /api/conversations/{conversationId}/messages`

Messages:

- `PATCH /api/messages/{messageId}/status`

Media:

- `POST /api/media`

Presence:

- `GET /api/presence/{userId}`

WebSocket:

- `/ws`
- `/app/chat.send`
- `/app/message.status`
- `/app/chat.typing`
- `/topic/conversations/{conversationId}`
- `/topic/conversations/{conversationId}/status`
- `/topic/conversations/{conversationId}/typing`

Docker:

- `docker-compose.yml` starts backend, frontend, and MinIO.
- Use `docker compose up --build` or `docker-compose up --build`, depending on the installed Docker CLI.
- MongoDB is not included because the project uses the local MongoDB service.
- MinIO console runs at `http://localhost:9001`.

## SMS Verification Details

Local development uses:

```properties
app.sms.provider=${SMS_PROVIDER:dummy}
```

In dummy mode, the backend returns a `debugCode`, and the frontend displays it as the local test code.

Real SMS can be enabled with Twilio:

```powershell
$env:SMS_PROVIDER = "twilio"
$env:TWILIO_ACCOUNT_SID = "your_account_sid"
$env:TWILIO_AUTH_TOKEN = "your_auth_token"
$env:TWILIO_FROM_NUMBER = "+1234567890"
```

The project does not store real SMS credentials in `application.properties`.

SMS protection currently includes:

- resend cooldown
- daily request cap
- invalid-code attempt tracking

## Verified

These checks have passed:

- Frontend production build:

```powershell
cd frontend
npm run build
```

- Backend Spring test:

```powershell
mvn test
```

- Local API smoke test:
  - `/api/health` returned `running`.
  - `/api/auth/register/start` returned a verification id and dummy code.
  - `/api/auth/register/verify` returned an auth token.
- Backend integration tests:
  - SMS registration creates user and token.
  - tokens are stored hashed.
  - invalid OTP increments attempts.
  - immediate OTP resend is blocked.
  - message pagination works.
  - sender cannot mark own message read.
  - direct conversations are reused for the same participants.
  - media upload endpoint requires authentication and a file.
  - invalid/spoofed image uploads are rejected before storage.

## Running App

Current expected local URLs:

- Frontend: `http://127.0.0.1:5173`
- Backend: `http://localhost:8080`
- MongoDB: `mongodb://localhost:27017/chatting_app`
- MinIO console: `http://localhost:9001`

Docker URLs:

- Frontend: `http://localhost:3000`
- Backend: `http://localhost:8080`
- MongoDB remains local at `mongodb://localhost:27017/chatting_app`.
- MinIO API: `http://localhost:9000`
- MinIO console: `http://localhost:9001`

## Known Gaps

These are not done yet:

- Real phone contact import from device contacts.
- Real contact permission flow.
- Email verification.
- Full two-factor authentication for existing accounts.
- Password reset.
- Profile editing.
- Avatar upload.
- General document/video upload beyond image files.
- Push notifications.
- Mobile app implementation.
- End-to-end encryption.
- Multi-device session management.
- Refresh tokens.
- Strong distributed rate limiting for OTP requests.
- Account lockout rules.
- Admin/moderation tools.
- Search inside a conversation.
- Archived chats.
- Muted chats.
- Pinned chats.
- Message delete/edit.
- Reactions.
- Voice/video calls.
- Production deployment configuration.
- More backend unit/integration tests.
- Frontend component tests.

## Recommended Next Steps

1. Add profile editing and avatar upload.
2. Add proper media upload instead of manual asset URLs.
3. Wire the frontend typing indicator to the existing backend typing WebSocket endpoint.
4. Add message read receipt behavior when a conversation is opened.
5. Add a real contacts collection and phone-number contact matching.
6. Add refresh tokens or secure cookie sessions.
7. Decide whether to introduce Lombok for backend entities.
8. Start a Flutter app using the same backend APIs after the web MVP is stable.
