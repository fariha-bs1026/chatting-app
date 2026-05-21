# ChatFlow Architecture Review

Last updated: May 21, 2026

## Summary

ChatFlow is in a good MVP state for a dummy WhatsApp-style project. The current stack is sensible:

- Spring Boot API
- React frontend
- MongoDB persistence
- WebSocket/STOMP realtime messaging
- SMS-style registration with dummy and Twilio providers

The main remaining work is not about making the UI fancier. The important improvements are around security boundaries, data integrity, scaling behavior, contact modeling, and test coverage.

## Implemented On May 21, 2026

- WebSocket conversation-topic subscription authorization.
- Participant validation for typing events.
- MongoDB auto-index creation for local development.
- Auth-token hashing before MongoDB storage.
- TTL index support for expired auth tokens.
- OTP resend cooldown and daily request limit.
- Direct-conversation uniqueness key.
- Cursor-style message pagination.
- Frontend "Load older messages" support.
- Backend integration tests for auth and chat rules.
- Docker setup for backend and frontend only.
- Frontend auth/reusable UI component split.

## Priority 1: WebSocket Authorization

Status after May 21: baseline conversation-topic authorization is implemented. Keep this section as the design rule for future WebSocket topics.

Current issue:

- The WebSocket connection checks the token during `CONNECT`.
- After connection, subscriptions are not strongly checked per conversation.
- A user could potentially subscribe to `/topic/conversations/{conversationId}` if they know or guess the id.
- Typing events are broadcast without checking that the sender belongs to the conversation.

Recommended improvement:

- Validate `SUBSCRIBE` frames in `WebSocketAuthChannelInterceptor`.
- Check that the authenticated user is a participant of the conversation topic.
- Validate `/app/chat.typing` the same way message sending is validated.
- Consider user-specific queues instead of public conversation topics.

Why it matters:

Realtime chat privacy depends on topic-level authorization, not only login.

## Priority 2: MongoDB Indexes And Data Integrity

Status after May 21: local auto-index creation and direct-conversation uniqueness are implemented. Production still needs explicit migration/index management.

Current issue:

- `@Indexed` is used for usernames, phone numbers, participant ids, and OTP expiry.
- The app does not explicitly enable Mongo auto-index creation in config.
- In production, relying on annotation-driven index creation is not enough.

Recommended improvement:

- For local dev, add:

```properties
spring.data.mongodb.auto-index-creation=true
```

- For serious usage, create indexes through a migration/setup script.
- Add a direct-conversation uniqueness strategy so two users cannot accidentally get duplicate direct chats.
- Add TTL cleanup for expired auth tokens as well as OTP records.

Why it matters:

Without reliable indexes, duplicate users/phones can appear and expired verification records may stay longer than expected.

## Priority 3: SMS Verification Abuse Protection

Status after May 21: basic resend cooldown and daily request limits are implemented. A production deployment still needs distributed rate limiting.

Current issue:

- The SMS flow works.
- Dummy mode works for local testing.
- Twilio mode is wired for real SMS.
- But OTP request abuse is not protected enough yet.

Recommended improvement:

- Add resend cooldown per phone number.
- Add per-IP and per-phone rate limits.
- Add daily SMS caps.
- Track failed verification attempts across resend cycles.
- Hide whether a phone number is registered in public responses if privacy becomes important.
- Add provider failure handling for Twilio errors.

Why it matters:

SMS costs money and can be abused quickly.

## Priority 4: Token And Session Hardening

Status after May 21: auth tokens are stored as hashes and have TTL index support. Refresh-token or cookie-session work is still open.

Current issue:

- Bearer tokens are stored directly in MongoDB.
- The frontend stores the auth response in `localStorage`.
- There is no refresh-token rotation.
- Expired auth tokens are checked logically but not automatically removed by TTL.

Recommended improvement:

- Store only hashed tokens in MongoDB.
- Add TTL index for expired auth tokens.
- Add refresh tokens if sessions need to survive longer.
- For web production, consider HttpOnly/Secure cookies instead of localStorage.
- Add logout-all-devices support.

Why it matters:

The current token system is okay for an MVP, but not for a production chat product.

## Priority 5: Message Status And Read Receipts

Status after May 21: the sender can no longer mark their own message as read, and status cannot move backward. Per-recipient receipt storage is still open.

Current issue:

- A message has one status: `SENT`, `DELIVERED`, or `READ`.
- Any participant can update the status of a message.
- Group chats need per-recipient status, not one global status.

Recommended improvement:

- Create a message receipt model:
  - message id
  - recipient user id
  - delivered at
  - read at
- Only receivers should mark messages as delivered/read.
- Status should move forward only, not backward.
- For group messages, calculate aggregate display state from all receipts.

Why it matters:

WhatsApp-style ticks are recipient-specific, especially in groups.

## Priority 6: Message Pagination And Conversation Performance

Status after May 21: cursor-style message pagination is implemented. Denormalized unread counts and last-message summaries are still open.

Current issue:

- Message history loads all messages in a conversation.
- Conversation listing calculates last message dynamically.

Recommended improvement:

- Add cursor pagination for messages:
  - `beforeMessageId`
  - `beforeCreatedAt`
  - `limit`
- Store last message summary on the conversation document.
- Store unread count per user/conversation.
- Add indexes for:
  - `conversationId + createdAt`
  - `participantIds + updatedAt`

Why it matters:

Chat apps become slow quickly if every conversation loads full history or repeatedly looks up the latest message.

## Priority 7: Contacts Model

Current issue:

- The frontend now separates Contacts and Search.
- Contacts currently come from existing direct conversations.
- Search still uses global username/display-name search.

Recommended improvement:

- Add a real contacts collection.
- Support contact import by phone number.
- Match phone numbers to registered users.
- Add invite state for phone numbers that are not registered.
- Add privacy settings:
  - who can find me
  - who can see my online status
  - who can see my last seen
- Add blocking.

Why it matters:

WhatsApp is contact-first. User search alone is more like Slack/Discord than WhatsApp.

## Priority 8: Frontend Structure

Status after May 21: the auth screen and reusable visual components were split from `App.jsx`. The chat screen still needs deeper component/hook extraction.

Current issue:

- Most frontend logic lives in `App.jsx`.
- This is fine for an MVP, but it will become hard to manage.

Recommended improvement:

Split the frontend into:

```text
frontend/src/
  api/
    authApi.js
    conversationApi.js
    userApi.js
  components/
    AuthView.jsx
    Sidebar.jsx
    ChatPane.jsx
    MessageList.jsx
    Composer.jsx
    ThemeToggle.jsx
  hooks/
    useAuth.js
    useChatSocket.js
    useTheme.js
  state/
    authStorage.js
```

Also consider adding:

- React Router if multiple pages are added.
- TanStack Query if API state grows.
- A form library only if forms become complex.

Why it matters:

The current file is still workable, but calls, media upload, profile editing, and contacts will make it too large.

## Priority 9: Testing

Status after May 21: backend integration tests were added for SMS registration, token hashing, message pagination, status permissions, and direct conversation reuse. Frontend tests are still open.

Current issue:

- Backend has only a context-load test.
- Frontend has no test coverage.

Recommended backend tests:

- Register start returns dummy OTP in dummy mode.
- Register verify creates user and token.
- Invalid OTP increments attempts.
- Expired OTP is rejected.
- Duplicate phone number is rejected.
- Direct conversation requires valid users.
- Non-participant cannot read/send messages.
- WebSocket subscribe is rejected for non-participants after the authorization fix.

Recommended frontend tests:

- Register flow shows SMS code step.
- Login stores auth state.
- People tab Contacts/Search behavior.
- Message sending clears composer.
- Theme toggle persists selection.

Why it matters:

Chat logic has many edge cases. Tests will help avoid breaking existing flows while adding features.

## Priority 10: Deployment And Operations

Status after May 21: backend/frontend Dockerfiles and Compose were added without MongoDB. CI and production profiles are still open.

Current issue:

- The app is local-dev focused.
- MongoDB URI and CORS are simple local settings.

Recommended improvement:

- Add Docker Compose:
  - MongoDB
  - backend
  - frontend
- Add backend `.env.example`.
- Add frontend `.env.example`.
- Add Spring profiles:
  - `local`
  - `test`
  - `prod`
- Add CI:
  - backend tests
  - frontend build
- Add structured logging.
- Add readiness/health checks.

Why it matters:

This makes the project easier to resume, share, deploy, and debug.

## Suggested Work Order For Tomorrow

1. Add profile editing and avatar upload.
2. Add proper media upload instead of manual asset URLs.
3. Add a real contacts collection and phone-number matching.
4. Add frontend typing indicators using the existing backend endpoint.
5. Add read-receipt behavior when a conversation is opened.
6. Add refresh tokens or secure cookie sessions.
7. Expand frontend tests.
8. Add production deployment configuration.

## Lower Priority Notes

Lombok:

- Lombok is fine for entities and constructors.
- Keep Java records for DTOs.
- It is useful, but not urgent compared with authorization, indexes, OTP protection, and pagination.

Flutter:

- A Flutter app is possible using the same backend.
- It is better to stabilize the web API first.
- Once auth, conversations, messages, contacts, and media endpoints are stable, Flutter can reuse them.

Production readiness:

- This is not production-ready yet.
- It is a good MVP foundation.
- The biggest production blockers are WebSocket authorization, SMS abuse protection, token hardening, and proper contact modeling.
