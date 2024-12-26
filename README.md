# ScalaNettyWrapper

**ScalaNettyWrapper** is a lightweight, high-performance HTTP server built using **Netty**. It provides a customizable and scalable framework for handling HTTP requests with a focus on non-blocking I/O and structured logging.

---

## Features

- **High Performance**: Built on Netty's event-driven, non-blocking I/O model for efficient handling of concurrent connections.
- **Customizable**: Easily extendable with custom handlers to suit your application's needs.
- **Structured Logging**: Integrated with SLF4J and Logstash for detailed and structured logs.
- **Scalability**: Configurable worker threads and connection options for optimized performance.

---

## Getting Started

### Prerequisites

- **Java**: OpenJDK 11.0.25+
- **Scala**: Scala 3.x
- **Build Tool**: sbt (Scala Build Tool)

---

### Installation

1. Clone the repository:

   ```bash
   git clone https://github.com/<your-username>/ScalaNettyWrapper.git
   cd ScalaNettyWrapper
   ```

2. Build the project:

   ```bash
   sbt compile
   ```

3. Run the server:

   ```bash
   sbt run
   ```

---

## Configuration

The server listens on port `2107` by default. To change the port or add other configurations, update the `start` method in `HttpServer`:

```scala
HttpServer.start(port = 8080)
```

---

## Project Structure

- **`com.tuachotu.http.core.HttpServer`**: The main server entry point. Configures the Netty server and handles incoming connections.
- **`com.tuachotu.http.handlers.HttpHandler`**: Custom request handler. Extend this class to define request processing logic.
- **`com.tuachotu.util.LoggerUtil`**: Utility for structured logging with SLF4J and Logstash.

---

## Logging

ScalaNettyWrapper uses structured logging to enhance observability and debugging. Key-value pairs can be added to logs for better context:

```scala
LoggerUtil.info("Server started", "port", 2107)
```

---

## Contributing

Contributions are welcome! Follow these steps to contribute:

1. Fork the repository.
2. Create a feature branch (`git checkout -b feature-name`).
3. Commit your changes (`git commit -m "Add a new feature"`).
4. Push to your branch (`git push origin feature-name`).
5. Open a pull request.

---

## License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.

---

## Contact

For any questions or support, feel free to reach out:

- **Author**: Vikrant Singh
- **Email**: [vikrant.thakur@gmail.com]
