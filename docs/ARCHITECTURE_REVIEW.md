# ChatFlow Architecture Review

Last updated: May 25, 2026

## Summary

The project is now a credible chat MVP: the backend boundaries are mostly clear, realtime authorization exists, media is stored outside MongoDB, and the frontend covers the core daily chat flows. The largest architectural risk is no longer a missing single feature; it is frontend growth around `App.jsx`, production hardening, and missing contact/session/notification infrastructure.

## What Was Improved Today

- Added audio and video media upload support on top of the existing MinIO flow.
- Added direct audio/video call signaling over WebSocket.
- Added frontend call controls and WebRTC media handling.
- Added delete for me, delete for everyone, and temporary message flows.
- Added profile update, avatar upload, and avatar delete flows.
- Added frontend typing indicator support using the existing backend typing endpoint.
- Added Lombok to reduce backend entity boilerplate.
- Cleaned wildcard imports in backend production code.
- Removed unsafe/questionable compound indexes involving hidden array fields.
- Added verification lookup indexes for OTP rate-limit and lookup paths.
- Added focused backend unit tests for WebSocket typing/call signaling.
- Added frontend unit/component tests with Vitest and React Testing Library.
- Added frontend coverage script with 40% global coverage thresholds.
- Upgraded Vite to a non-vulnerable line and verified `npm audit` reports 0 vulnerabilities.
- Verified backend and frontend after the changes.

## Structure Assessment

Backend:

- The package structure is appropriate for the current size: controller, service, repository, entity, dto, config.
- DTOs as Java records are a good fit and should stay.
- Entities now use Lombok for repetitive getters/constructors while keeping domain methods explicit.
- `ChatService` is becoming large. The next backend cleanup should split media-message validation, receipt logic, and conversation projection into smaller collaborators.

Frontend:

- The app works, but `frontend/src/App.jsx` is too large for the number of features it now owns.
- The next structural improvement should extract `Sidebar`, `ChatPane`, `MessageList`, `Composer`, `ProfileEditor`, `CallPanel`, and socket hooks.
- Frontend errors should continue to route through the existing central error mapping/helper pattern.

Database:

- MongoDB is acceptable for this MVP.
- Current indexes cover usernames, phone numbers, auth token expiry, OTP expiry/lookups, conversation participant listing, message history, and receipts.
- Production should use explicit migration/index management rather than relying only on annotation-driven creation.
- Last-message and unread-count calculations are still dynamic; denormalizing them will matter when data grows.

Security:

- Strong wins: hashed auth tokens, HttpOnly cookie, participant checks for conversation topics, media ownership checks, request validation, localized validation messages.
- Remaining risks: no refresh-token rotation, no logout-all-devices, no device/session model, no push notification auth model, no end-to-end encryption, no distributed rate limiting.
- WebRTC currently uses a public STUN server. Production calls need configurable STUN/TURN servers.

## Feature Candidates Still Worth Implementing

- Real contacts collection with phone-number matching.
- Blocking and privacy controls for profile photo, online state, and last seen.
- Push notifications for new messages and missed calls.
- Message edit, reactions, replies, and forwarding.
- Conversation search.
- Archived, muted, and pinned chats.
- Call history and missed call records.
- TURN server configuration and call failure handling.
- Password reset and optional email verification.
- Multi-device sessions and logout all devices.
- Broader frontend integration tests for full chat/call workflows.
- CI pipeline for backend tests and frontend build.

## Recommended Next Implementation Order

1. Split `App.jsx` into components/hooks before adding more chat UI.
2. Broaden frontend tests around profile updates, delete flows, typing, media uploads, and call failure paths.
3. Add contacts/blocking/privacy because it changes conversation visibility rules.
4. Add TURN configuration and call history for calls.
5. Add CI, production profiles, index migrations, and secrets handling.
