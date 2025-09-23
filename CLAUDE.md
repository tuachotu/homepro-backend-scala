# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build and Development Commands

### Core SBT Commands
- `sbt compile` - Compile the Scala 3.5.0 project
- `sbt run` - Run the application (starts HTTP server on port 2107)
- `sbt assembly` - Create fat JAR for deployment
- `sbt test` - Run tests (when available)

### Running the Application
The main class is `com.tuachotu.HomeProMain` and runs an Akka HTTP server on port 2107 (configurable via `server.port`).

## Required Environment Variables

### Database (PostgreSQL)
- `HOME_PRO_DB_USER` - PostgreSQL username (required)
- `HOME_PRO_DB_PASSWORD` - PostgreSQL password (required)
- `HOME_PRO_DB_URL` - JDBC URL (default: jdbc:postgresql://localhost:5432/homepro)

### Firebase Authentication
- `FIREBASE_CONFIG_PATH` - Path to Firebase service account JSON file (required)

### AWS S3 (Photo Storage)
- AWS credentials should be configured via standard AWS credential chain (environment variables, IAM roles, etc.)
- `AWS_S3_PRESIGNED_URL_EXPIRATION_HOURS` - URL expiration time (default: 24 hours)
- S3 bucket: `homepro-photos-east-1` (configured in application.conf)
- Default region: `us-east-1`

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
├── HomeProMain.scala    # Application entry point
├── controller/          # HTTP route handlers
│   ├── UserController
│   ├── PhotoController
│   ├── HomeController
│   ├── HomeItemController
│   └── SupportRequestController
├── service/            # Business logic layer
│   ├── UserService
│   ├── PhotoService
│   ├── HomeService
│   ├── HomeItemService
│   ├── S3Service
│   ├── RoleService
│   ├── UserRoleService
│   └── SupportRequestService
├── repository/         # Data access with raw SQL
│   ├── UserRepository
│   ├── PhotoRepository
│   ├── HomeRepository
│   ├── HomeItemRepository
│   ├── RoleRepository
│   ├── UserRoleRepository
│   └── SupportRequestRepository
├── model/
│   ├── db/            # Database case classes (Users, Photo, Home, HomeItem, etc.)
│   ├── request/       # API request models (AddHomeRequest, CreateSupportRequest, etc.)
│   └── response/      # API response models (PhotoResponse, HomeResponse, etc.)
├── util/              # Utilities
│   ├── ConfigUtil     # Configuration management
│   ├── LoggerUtil     # Structured logging
│   ├── FirebaseAuthHandler # Authentication
│   ├── TimeUtil       # Time utilities
│   ├── IdUtil         # UUID generation
│   ├── JsonFormats    # Spray JSON formats
│   └── HomeProException # Custom exceptions
├── conf/              # Configuration constants (Constant.scala)
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
- `homes` - Home/property information with address and ownership
- `home_items` - Individual items within homes (enum type: room, appliance, utility_control, structural, observation, wiring, sensor, other)
- `photos` - Photo metadata with S3 key references and context relationships
- `home_owners` - Many-to-many relationship between homes and users with roles

**Key Features**:
- All tables support **soft deletion** with `deleted_at` timestamps
- `home_items` uses PostgreSQL ENUM type for `home_item_type`
- `photos` table supports context-based relationships (user_id, home_id, home_item_id)
- UUID primary keys throughout for better scalability

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
Structured JSON logging using SLF4J + Logback + Logstash encoder:
- Use `implicit val logger: Logger = LoggerUtil.getLogger(getClass)` for logger initialization
- Logging methods support key-value pairs: `LoggerUtil.info("message", "key1", value1, "key2", value2)`
- Available methods: `info()`, `error()`, `warn()`, `debug()` with structured logging support

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

**Example with actual user ID:**
```bash
sbt "Test/runMain com.tuachotu.testS3Integration a8f65408-8bce-4662-8fb3-d072b1f6dd34"
```

These tests verify the context-based S3 URL generation and are useful for:
- Validating AWS S3 configuration
- Testing new S3-related features
- Debugging S3 URL generation issues
- Onboarding new developers

### API Integration Tests

Shell scripts for testing API endpoints:
```bash
# Test home item creation and photo upload
./test_home_item_api.sh
./test_photo_upload_api.sh

# Test CORS and general endpoints
./test-cors.sh
./test-home-endpoints.sh
```

Make scripts executable: `chmod +x *.sh`

### Quick Start

Use the provided quick start script for complete environment setup:
```bash
# Full setup with environment checks and application start
./quick_start.sh

# Check prerequisites and environment only
./quick_start.sh --check-only

# Compile only
./quick_start.sh --compile-only
```

The script validates Java, SBT, PostgreSQL, environment variables, and database connectivity.

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