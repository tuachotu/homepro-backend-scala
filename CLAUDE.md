# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build and Development Commands

### Core SBT Commands
- `sbt compile` - Compile the Scala 3.5.0 project
- `sbt run` - Run the application (starts HTTP server on port 2101)
- `sbt assembly` - Create fat JAR for deployment
- `sbt test` - Run tests (when available)

### Running the Application
The main class is `com.tuachotu.HomeProMain` and runs an Akka HTTP server on port 2101.

## Required Environment Variables

### Database (PostgreSQL)
- `HOME_PRO_DB_USER` - PostgreSQL username (required)
- `HOME_PRO_DB_PASSWORD` - PostgreSQL password (required)
- `HOME_PRO_DB_URL` - JDBC URL (default: jdbc:postgresql://localhost:5432/homepro)

### Firebase Authentication
- `FIREBASE_CONFIG_PATH` - Path to Firebase service account JSON file (required)

### AWS S3 (Photo Storage)
AWS credentials should be configured via standard AWS credential chain (environment variables, IAM roles, etc.)

## Architecture Overview

This is a **Scala 3** backend using **Akka HTTP** with **direct SQL queries** (no ORM) to PostgreSQL. The architecture follows a layered approach:

### Key Architectural Patterns
- **Controller → Service → Repository** layered architecture
- **Direct SQL with prepared statements** - no ORM overhead
- **HikariCP connection pooling** for database efficiency
- **Firebase Admin SDK** for authentication
- **AWS S3 integration** for photo storage with presigned URLs
- **Structured JSON logging** with Logstash integration

### Package Structure
```
com.tuachotu/
├── controller/          # HTTP route handlers (UserController, PhotoController, HomeController, SupportRequestController)
├── service/            # Business logic (UserService, PhotoService, HomeService, S3Service, etc.)
├── repository/         # Data access with raw SQL (UserRepository, PhotoRepository, HomeRepository, etc.)
├── model/
│   ├── db/            # Database case classes (Users, Photo, Home, SupportRequest, etc.)
│   ├── request/       # API request models
│   └── response/      # API response models
├── util/              # Utilities (ConfigUtil, LoggerUtil, FirebaseAuthHandler, TimeUtil, etc.)
└── db/                # Database connection management (DatabaseConnection)
```

## Database Architecture

### Connection Management
- Uses `DatabaseConnection` object with HikariCP for connection pooling
- All database operations use prepared statements for SQL injection protection
- Helper methods: `executeQuery`, `executeUpdate`, `executeInsert`, `executeQuerySingle`
- Async operations with Future[T] return types

### Key Database Tables
- `users` - User accounts with Firebase UID integration
- `roles` - System roles (admin, homeowner, expert, manager)
- `user_roles` - Many-to-many user-role relationships
- `support_requests` - Service request lifecycle management
- `homes` - Home/property information
- `photos` - Photo metadata with S3 key references

All tables support **soft deletion** with `deleted_at` timestamps.

## Important Implementation Details

### Authentication Flow
Uses Firebase Admin SDK to verify JWT tokens. The `FirebaseAuthHandler` validates tokens and extracts user information.

### Photo Storage - Context-Based S3 Architecture
Photos use a context-based S3 storage system implemented in the recent PR:

**Database Storage**: Only filenames stored in `photos.s3_key` field (e.g., "kitchen.jpg")

**S3 Organization**: Photos organized in context-based folder structure:
- Home Item Photos: `{home_item_id}/{filename}` (highest priority)
- Home Photos: `{home_id}/{filename}` (medium priority)  
- User Photos: `{user_id}/{filename}` (lowest priority)
- Legacy Photos: Direct s3_key usage (fallback)

**S3Service Methods**:
- `generatePresignedUrl(s3Key)` - Direct S3 key (legacy support)
- `generatePresignedUrlForContext(contextId, fileName)` - Context-based paths

**PhotoService Priority Logic**: HomeItemId → HomeId → UserId → Fallback

### Configuration
Uses Typesafe Config with environment variable overrides. Main config in `src/main/resources/application.conf`.

### Logging
Structured JSON logging using SLF4J + Logback + Logstash encoder. Use `LoggerUtil.getLogger(getClass)` for consistent logging.

### Error Handling
Custom `HomeProException` for business logic errors. Controllers handle exceptions and return appropriate HTTP status codes.

## Testing

### S3 Integration Tests

Two integration tests are available in `src/test/scala/com/tuachotu/`:

```bash
# Test S3Service directly (AWS connectivity, context-based URLs)
sbt "Test/runMain com.tuachotu.testS3Service"

# Test end-to-end flow with real database data
sbt "Test/runMain com.tuachotu.testS3Integration <user-id>"
```

These tests verify the context-based S3 URL generation and are useful for:
- Validating AWS S3 configuration
- Testing new S3-related features
- Debugging S3 URL generation issues
- Onboarding new developers

## Development Guidelines

### Database Operations
- Always use `DatabaseConnection` helper methods
- Use prepared statements with parameter binding
- Handle `Future[T]` responses appropriately
- Log database operations with structured data

### Adding New Endpoints
1. Create request/response models in `model/request` and `model/response`
2. Add database operations to appropriate Repository
3. Implement business logic in Service layer
4. Create Controller with HTTP routes
5. Register routes in `HomeProMain`

### Code Style
- Use Scala 3 syntax and features
- Prefer immutable case classes
- Use `Using` for resource management
- Follow existing naming conventions
- Include appropriate error handling and logging

### Testing Database Code
When available, tests should use a separate test database configuration to avoid affecting development data.