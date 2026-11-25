# TaskFlow

TaskFlow is a cloud-based task management application built with Java 21, Spring Boot, and MongoDB Atlas.

## Technology Stack

- **Language:** Java 21
- **Framework:** Spring Boot 4.2.0
- **Build Tool:** Maven (with included Maven Wrapper)
- **Web:** Spring Web (`spring-boot-starter-webmvc`)
- **Database:** Spring Data MongoDB (`spring-boot-starter-data-mongodb`)
- **Security:** Spring Security (`spring-boot-starter-security`)
- **Validation:** Jakarta Bean Validation (`spring-boot-starter-validation`)
- **Observability:** Spring Boot Actuator (`spring-boot-starter-actuator`)

## Project Structure

```text
src/
├── main/
│   ├── java/com/taskflow/
│   │   ├── TaskFlowApplication.java           # Application entry point
│   │   ├── config/
│   │   │   ├── MongoConfig.java               # Enables MongoDB auditing (@CreatedDate)
│   │   │   ├── MongoConnectivityVerifier.java # Safe startup database ping check
│   │   │   └── SecurityConfig.java            # Permits health endpoints; secures other routes
│   │   ├── controller/
│   │   │   └── DatabaseHealthController.java  # GET /api/health/db verification endpoint
│   │   ├── dto/
│   │   │   └── DatabaseHealthResponse.java    # Sanitized health payload
│   │   ├── exception/                         # Custom exceptions & global handlers
│   │   ├── model/                             # MongoDB documents (User, Project, Task) & enums
│   │   ├── repository/                        # Spring Data MongoDB repositories
│   │   └── service/
│   │       └── DatabaseHealthService.java     # Executes safe MongoDB ping command
│   └── resources/
│       └── application.properties             # Externalized configuration
└── test/
    └── java/com/taskflow/
        ├── TaskFlowApplicationTests.java
        ├── controller/
        │   └── DatabaseHealthControllerTest.java
        └── service/
            └── DatabaseHealthServiceTest.java
```

## MongoDB Atlas Configuration

TaskFlow connects to MongoDB Atlas using Spring Boot 4.2's `spring.mongodb.uri` and `spring.mongodb.database` properties, populated strictly from environment variables (`MONGODB_URI` and `MONGODB_DATABASE`, defaulting the database name to `taskflow`).

No passwords or connection strings are hardcoded in `application.properties` or anywhere in source control.

### Domain Collections

| Model | Collection | Key Indexes |
| --- | --- | --- |
| `User` | `users` | Unique index on `email` |
| `Project` | `projects` | Index on `ownerId`; compound index `(ownerId, name)` |
| `Task` | `tasks` | Indexes on `projectId` and `status`; compound index `(projectId, status)` |

### 1. Get Your MongoDB Atlas Connection String

1. In [MongoDB Atlas](https://www.mongodb.com/cloud/atlas), open your cluster and click **Connect** -> **Drivers** (Java).
2. Ensure you have a database user in **Database Access** with `readWrite` permissions on the `taskflow` database.
3. Ensure your machine or cloud runtime IP is allowed under **Network Access**.
4. Copy the SRV connection string (and URL-encode any special characters in the password):
   ```text
   mongodb+srv://<username>:<password>@<cluster-host>/?retryWrites=true&w=majority&appName=TaskFlow
   ```

### 2. Configure `.env` or Environment Variables

**Option A — Using a local `.env` file (development):**

Copy `.env.example` to `.env` (already ignored by `.gitignore`) and replace the placeholder values. Spring Boot imports `.env` automatically via `spring.config.import=optional:file:.env[.properties]`. Do not wrap values in quotes.

```powershell
Copy-Item .env.example .env
```

**Option B — Using OS / Cloud Environment Variables (recommended for production):**

OS environment variables always take precedence over `.env`.

**Windows (PowerShell):**
```powershell
$env:MONGODB_URI = "mongodb+srv://<username>:<password>@<cluster-host>/?retryWrites=true&w=majority&appName=TaskFlow"
$env:MONGODB_DATABASE = "taskflow"
```

**macOS / Linux (bash/zsh):**
```bash
export MONGODB_URI="mongodb+srv://<username>:<password>@<cluster-host>/?retryWrites=true&w=majority&appName=TaskFlow"
export MONGODB_DATABASE="taskflow"
```

> **Security Note:** Never commit `.env` files, credentials, or Atlas connection strings. `.gitignore` excludes `.env`, `.env.*` (while keeping `.env.example`), `application-local.*`, and `application-secret.*`.

## Safe Database Connectivity Verification

TaskFlow provides two safe verification mechanisms that never expose URIs, hostnames, or credentials in logs or HTTP responses:

1. **Startup Verification (`MongoConnectivityVerifier`):** Runs a lightweight MongoDB `ping` command on startup and logs only the database name and pass/fail status (or exception class name on failure). Controlled via `MONGODB_VERIFY_ON_STARTUP` (`true` by default).
2. **HTTP Health Endpoints:**
   - `GET /api/health/db` — Returns `200 OK` (`{"status":"UP","database":"taskflow","reachable":true}`) or `503 Service Unavailable` (`{"status":"DOWN","database":"taskflow","reachable":false}`).
   - `GET /actuator/health` — Spring Boot Actuator health endpoint (`show-components=always`, `show-details=never`), reporting `mongo: UP/DOWN` without exposing credentials or internal details.

## Authentication & Security

TaskFlow uses stateless JWT (`HS256`) authentication with BCrypt password hashing and MongoDB-backed token revocation (`revoked_tokens` collection with a TTL index on `expiresAt`).

- **Signing Secret:** Configured via `JWT_SECRET` (minimum 32 bytes; never hardcoded).
- **Protected Routes:** All API endpoints require a valid, non-revoked `Authorization: Bearer <token>` header except registration, login, and health endpoints.

### Authentication Endpoints

| Method & Path | Access | Description |
| --- | --- | --- |
| `POST /api/auth/register` | Public | Registers a user (`name`, `email`, `password`, `confirmPassword`), prevents duplicate emails, hashes password with BCrypt |
| `POST /api/auth/login` | Public | Authenticates credentials with BCrypt and returns a signed JWT access token (`200 OK`) |
| `POST /api/auth/logout` | Authenticated | Revokes the current JWT's `jti` in MongoDB so subsequent requests with that token are rejected (`401`) |
| `GET /api/auth/me` | Authenticated | Returns the authenticated user's profile without password hashes |

## Configuration Reference

| Environment Variable | Description | Default |
| --- | --- | --- |
| `MONGODB_URI` | MongoDB Atlas connection URI (required at runtime) | *(none — must be provided)* |
| `MONGODB_DATABASE` | Target MongoDB database name | `taskflow` |
| `JWT_SECRET` | HMAC-SHA256 signing secret, at least 32 characters (required at runtime) | *(none — must be provided)* |
| `JWT_EXPIRATION_MS` | Access token lifetime in milliseconds | `3600000` |
| `MONGODB_AUTO_INDEX_CREATION` | Automatically create `@Indexed` / `@CompoundIndex` indexes on startup | `true` |
| `MONGODB_VERIFY_ON_STARTUP` | Run safe MongoDB `ping` verification at startup | `true` |
| `SERVER_PORT` | HTTP server port | `8080` |
| `ACTUATOR_ENDPOINTS` | Comma-separated Actuator web endpoints to expose | `health,info` |
| `ACTUATOR_HEALTH_SHOW_COMPONENTS` | Health component visibility (`never`, `when_authorized`, `always`) | `always` |
| `ACTUATOR_HEALTH_SHOW_DETAILS` | Health endpoint detail visibility (`never`, `when_authorized`, `always`) | `never` |
| `LOG_LEVEL_ROOT` | Root logging level | `INFO` |
| `LOG_LEVEL_APP` | Application logging level (`com.taskflow`) | `INFO` |
| `LOG_LEVEL_MONGO_CLIENT` | MongoDB driver client logger level (`WARN` prevents username logging) | `WARN` |

## Build and Verification

Run a full Maven build and unit/context test suite using the Maven Wrapper:

**Windows (PowerShell / CMD):**
```powershell
.\mvnw.cmd clean verify
```

**macOS / Linux:**
```bash
./mvnw clean verify
```

## Running with MongoDB Atlas

Once `MONGODB_URI` and `MONGODB_DATABASE=taskflow` are configured (via environment variables or `.env`), start the application:

```powershell
.\mvnw.cmd spring-boot:run
```

Then verify connectivity:

```powershell
Invoke-RestMethod http://localhost:8080/api/health/db
```
