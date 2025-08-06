# HomePro Backend - Scala

**HomePro Backend** is a high-performance, scalable home services platform backend built with **Scala 3**, **Akka HTTP**, and **PostgreSQL**. It provides a robust foundation for managing users, roles, and support requests with Firebase authentication integration.

---

## 🚀 Features

- **🔥 High Performance**: Built with Akka HTTP for non-blocking, reactive request handling
- **🗄️ Direct SQL Control**: Raw PostgreSQL queries with HikariCP connection pooling (no ORM overhead)
- **🔐 Firebase Authentication**: Secure authentication using Firebase Admin SDK
- **👥 Role-Based Access Control**: Flexible user role management system
- **📝 Support Request Management**: Complete lifecycle management for service requests
- **📊 Structured Logging**: JSON-structured logs with Logstash integration
- **♻️ Soft Delete Support**: Logical deletion with audit trails
- **🛡️ SQL Injection Protection**: Prepared statements with parameter binding
- **⚡ Connection Pooling**: Optimized database connections with HikariCP

---

## 🏗️ Architecture

```
com.tuachotu
├── controller/          # HTTP request handlers
│   ├── UserController
│   └── SupportRequestController
├── service/            # Business logic layer
│   ├── UserService
│   ├── RoleService
│   └── SupportRequestService
├── repository/         # Data access layer (Raw SQL)
│   ├── UserRepository
│   ├── RoleRepository
│   ├── UserRoleRepository
│   └── SupportRequestRepository
├── model/
│   ├── db/            # Database entities
│   ├── request/       # API request models
│   └── response/      # API response models
├── util/              # Utilities
│   ├── ConfigUtil     # Configuration management
│   ├── LoggerUtil     # Structured logging
│   ├── FirebaseAuthHandler
│   └── TimeUtil
└── db/                # Database connection management
    └── DatabaseConnection
```

---

## 🛠️ Tech Stack

- **Language**: Scala 3.5.0
- **Framework**: Akka HTTP 10.2.10
- **Database**: PostgreSQL 12+
- **Connection Pool**: HikariCP 5.1.0
- **Authentication**: Firebase Admin SDK 9.4.2
- **JSON**: Spray JSON 1.3.6
- **Logging**: SLF4J + Logback + Logstash
- **Build Tool**: sbt 1.10.1

---

## 📋 Prerequisites

- **Java**: OpenJDK 11+
- **Scala**: 3.5.0
- **PostgreSQL**: 12+
- **sbt**: 1.10.1+
- **Firebase Project**: For authentication

---

## 🚀 Quick Start

### 1. Clone the Repository

```bash
git clone <repository-url>
cd homepro-backend-scala
```

### 2. Set Up PostgreSQL Database

```bash
# Create database and tables
createdb homepro
psql -d homepro -f schema.sql
```

### 3. Configure Environment Variables

**Required:**
```bash
# Database Configuration (Required)
export HOME_PRO_DB_USER="your_postgresql_username"
export HOME_PRO_DB_PASSWORD="your_postgresql_password"

# Firebase Configuration (Required for authentication)
export FIREBASE_CONFIG_PATH="/path/to/your/firebase-service-account.json"
```

**Optional (have defaults):**
```bash
# Database URL (defaults to localhost:5432/homepro)
export HOME_PRO_DB_URL="jdbc:postgresql://localhost:5432/homepro"

# Connection Pool Settings
export HOME_PRO_DB_MAX_POOL_SIZE="10"
export HOME_PRO_DB_MIN_IDLE="5"
export HOME_PRO_DB_CONNECTION_TIMEOUT="30000"
export HOME_PRO_DB_IDLE_TIMEOUT="600000"
export HOME_PRO_DB_MAX_LIFETIME="1800000"
```

### 4. Build and Run

```bash
# Compile the project
sbt compile

# Run the application
sbt run
```

The server will start on `http://localhost:2101`

---

## ⚙️ Configuration

### Database Configuration (`application.conf`)

```hocon
database {
  url = "jdbc:postgresql://localhost:5432/homepro"
  url = ${?HOME_PRO_DB_URL}
  user = ${?HOME_PRO_DB_USER}
  password = ${?HOME_PRO_DB_PASSWORD}
  maxPoolSize = 10
  maxPoolSize = ${?HOME_PRO_DB_MAX_POOL_SIZE}
  minIdle = 5
  minIdle = ${?HOME_PRO_DB_MIN_IDLE}
  connectionTimeout = 30000
  idleTimeout = 600000
  maxLifetime = 1800000
}

server {
  port = 2101
}

firebase {
  auth {
    config-path = ${?FIREBASE_CONFIG_PATH}
  }
}
```

---

## 📊 Database Schema

The application uses the following main tables:

- **`users`**: User accounts with Firebase integration
- **`roles`**: System roles (admin, homeowner, expert, manager)
- **`user_roles`**: User-role assignments (many-to-many)
- **`support_requests`**: Service request management

All tables support soft deletion with `deleted_at` timestamps.

---

## 🔌 API Endpoints

### User Management
- `GET /users` - List all users
- `GET /users/{id}` - Get user by ID
- `POST /users` - Create new user
- `PUT /users/{id}` - Update user
- `DELETE /users/{id}` - Soft delete user

### Support Requests
- `GET /support-requests` - List support requests
- `GET /support-requests/{id}` - Get support request by ID
- `POST /support-requests` - Create new support request
- `PUT /support-requests/{id}/status` - Update request status
- `PUT /support-requests/{id}/assign` - Assign expert

### Role Management
- `GET /roles` - List all roles
- `POST /users/{id}/roles` - Assign role to user
- `DELETE /users/{id}/roles/{roleId}` - Remove role from user

---

## 🔍 Logging

The application uses structured JSON logging:

```scala
// Example log output
{
  "timestamp": "2025-08-03T13:45:30.123Z",
  "level": "INFO",
  "logger": "com.tuachotu.repository.UserRepository",
  "message": "User created",
  "userId": "550e8400-e29b-41d4-a716-446655440000",
  "rowsAffected": 1
}
```

Logs are configured in `logback.xml` and can be easily integrated with log aggregation systems.

---

## 🏗️ Development

### Building

```bash
# Compile
sbt compile

# Run tests (when available)
sbt test

# Create assembly JAR
sbt assembly
```

### Code Style

The project follows Scala 3 best practices:
- Functional programming principles
- Immutable data structures
- Type safety with case classes
- Resource management with `Using`

---

## 🚢 Deployment

### Docker Deployment

```dockerfile
FROM openjdk:11-jre-slim

COPY target/scala-3.5.0/http-assembly-0.1.0.jar app.jar

EXPOSE 2101

CMD ["java", "-jar", "app.jar"]
```

### Environment Setup

```bash
# Production environment variables
export HOME_PRO_DB_URL="jdbc:postgresql://prod-postgres:5432/homepro"
export HOME_PRO_DB_USER="prod_user"
export HOME_PRO_DB_PASSWORD="secure_password"
export FIREBASE_CONFIG_PATH="/etc/firebase/service-account.json"
```

---

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

### Code Guidelines

- Follow Scala 3 conventions
- Add appropriate logging to new features
- Include error handling
- Write clean, self-documenting code
- Update documentation as needed

---

## 📝 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

## 👥 Support

- **Author**: Vikrant Singh
- **Email**: vikrant.thakur@gmail.com
- **Issues**: Please use GitHub Issues for bug reports and feature requests

---

## 📈 Performance

- **Connection Pooling**: HikariCP with configurable pool sizes
- **Non-blocking I/O**: Akka HTTP for concurrent request handling
- **Efficient SQL**: Direct queries without ORM overhead
- **Resource Management**: Automatic cleanup of database connections

---

## 🔒 Security

- **Firebase Authentication**: Secure user authentication
- **SQL Injection Protection**: Prepared statements only
- **Input Validation**: Request validation at controller layer
- **Audit Trails**: Soft delete with modification tracking

---

*Built with ❤️ using Scala 3 and Akka HTTP*
