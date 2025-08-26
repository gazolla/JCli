## Project Overview

This is a Java-based client for the Model Context Protocol (MCP). It connects to external tool servers to discover and execute tools. The project features a robust architecture with components like `MCPManager`, `MCPService`, and `MCPConfig` for managing connections, services, and configurations. It uses a `DomainRegistry` for organizing tools into domains, with support for automatic discovery using an LLM. The project is built with Maven and can be run using provided scripts or directly with Maven commands. It also includes an interactive CLI for exploring its features.

## Building and Running

The project is built and managed using Apache Maven.

### Building the Project

To compile the source code and build the project, run:

```bash
mvn compile
```

To create a runnable JAR file with all dependencies, run:

```bash
mvn package
```

### Running the Project

You can run the application in several ways:

*   **Using the demo scripts:**
    *   On Windows: `run-demo.bat`
    *   On Linux/macOS: `chmod +x run-demo.sh && ./run-demo.sh`

*   **Using Maven:**
    *   To run the main application: `mvn exec:java -Dexec.mainClass="com.gazapps.App"`
    *   To run in interactive mode: `mvn exec:java -Dexec.mainClass="com.gazapps.App" -Dexec.args="interactive"`

*   **Running the JAR file:**

    ```bash
    java -jar target/JCli-0.0.1-SNAPSHOT-jar-with-dependencies.jar
    ```

### Testing the Project

To run the unit tests, use the following command:

```bash
mvn test
```

## Development Conventions

*   **Logging:** The project uses SLF4J with a Logback implementation for logging. Log levels can be configured in `src/main/resources/logback.xml` and `config/application.properties`.
*   **Testing:** Unit tests are written using JUnit 5. Test files are located in the `src/test/java` directory.
*   **Dependencies:** Project dependencies are managed in the `pom.xml` file. Key dependencies include the MCP SDK, Jackson for JSON processing, and Logback for logging.
*   **Configuration:** Application and MCP server configurations are stored in the `config` directory. `mcp.json` is used for server definitions, and `domains.json` is used for tool domain definitions.
*   **Architecture:** The project follows a modular architecture, with clear separation of concerns between the `MCPManager` (facade), `MCPService` (core logic), `MCPConfig` (configuration), and `DomainRegistry` (domain management).
