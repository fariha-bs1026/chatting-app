# Chatting App

Spring Boot API + React web app with separate backend and frontend folders.

## Project Structure

The backend follows the layered Spring Boot architecture described by [GeeksforGeeks](https://www.geeksforgeeks.org/springboot/spring-boot-architecture/): presentation/controller, business/service, persistence/repository, entity, DTO, config, and database.

```text
backend/
  src/main/java/com/fariha/chattingapp/
    controller/   REST controllers and WebSocket message controllers
    service/      Business logic and transactions
    repository/   Spring Data MongoDB repositories
    entity/       MongoDB document models and enums
    dto/          Request/response/event payloads
    config/       Security, CORS, WebSocket, exception config
frontend/
  src/            React app
docs/
  API_DESIGN.md   WhatsApp-style API design notes
```

## Backend

Default URL: `http://localhost:8080`

The backend uses:

- Spring Boot Web
- Spring Security with bearer tokens
- Spring WebSocket + STOMP
- Spring Data MongoDB
- MinIO object storage for uploaded chat images
- MongoDB Community Server at `mongodb://localhost:27017/chatting_app`

Run:

```powershell
cd backend
mvn spring-boot:run
```

If Java/Maven are not on PATH, install JDK 21 and Maven or run the project from IntelliJ IDEA.

MongoDB must be running before starting the backend. On Windows, check:

```powershell
Get-Service MongoDB
```

Registration uses a two-step SMS verification flow:

- `POST /api/auth/register/start` creates a pending registration and sends a 6-digit code.
- `POST /api/auth/register/verify` verifies the code, creates the user, and returns the auth token.

Local development uses the dummy SMS sender by default. The API response includes `debugCode`, and the React registration screen displays it as the local test code.

For real SMS delivery, set Twilio environment variables before starting the backend:

```powershell
$env:SMS_PROVIDER = "twilio"
$env:TWILIO_ACCOUNT_SID = "your_account_sid"
$env:TWILIO_AUTH_TOKEN = "your_auth_token"
$env:TWILIO_FROM_NUMBER = "+1234567890"
mvn spring-boot:run
```

Do not commit real SMS credentials into `application.properties`.

Uploaded chat images are stored in MinIO. MongoDB stores only message metadata and the MinIO object key. The backend returns short-lived presigned URLs in message responses so the React app can render private images in the browser.

Local MinIO defaults:

- API endpoint for Spring Boot outside Docker: `http://localhost:9000`
- Public browser endpoint: `http://localhost:9000`
- Console: `http://localhost:9001`
- Username: `minioadmin`
- Password: `minioadmin`
- Bucket: `chat-media`

Additional local safeguards are enabled by default:

- Mongo index creation for development.
- OTP resend cooldown.
- OTP daily request limit.
- Hashed auth-token storage with TTL cleanup.

## Docker Without MongoDB

MongoDB is expected to keep running locally on your machine. The Docker setup starts the backend, frontend, and MinIO.

```powershell
docker compose up --build
```

If your Docker CLI uses the older command, run `docker-compose up --build` instead.

Docker URLs:

- Frontend: `http://localhost:3000`
- Backend: `http://localhost:8080`
- MinIO console: `http://localhost:9001`
- MongoDB connection from backend container: `mongodb://host.docker.internal:27017/chatting_app`

## Frontend

Default URL: `http://localhost:5173`

Run:

```powershell
cd frontend
npm install
npm run dev
```

## Implemented MVP

- Register/login/logout
- User search
- Direct conversations
- Group conversations
- Delete/hide chats for the current user
- WebSocket live messages
- WebSocket topic authorization for conversation subscriptions
- Message history
- Cursor-style message pagination
- Message types: `TEXT`, `IMAGE`, `FILE`
- MinIO-backed image upload
- Inline image rendering with presigned media URLs
- Message status: `SENT`, `DELIVERED`, `READ`
- Online/last-seen presence
- OTP resend cooldown and daily SMS request limit
- Hashed auth-token storage
- Request validation with localized validation message bundles
- Swagger/OpenAPI docs at `http://localhost:8080/swagger-ui/index.html`

## Verification

```powershell
cd backend
mvn test

cd frontend
npm run build
```
