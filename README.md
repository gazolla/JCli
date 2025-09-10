# JCli

Java CLI for interacting with language models using the MCP (Model Context Protocol). The project implements multiple inference strategies for query processing.

## Project Structure

```
JCli/
├── .git/                           # Git version control
├── .settings/                      # Eclipse/IDE settings
├── config/                         # Application configuration
│   ├── application.properties      # Main configuration
│   ├── llm.properties             # LLM configurations
│   ├── mcp/                       # MCP configurations
│   │   ├── servers.json           # Registered MCP servers
│   │   └── domains.json           # Domains and their tools
│   └── rules/                     # Matching rules
│       └── server-rules.json      # Server-specific rules
├── documents/                     # Additional documentation
├── log/                          # Application logs
│   ├── inference/                # Inference strategy logs
│   └── mcp/                     # MCP system logs
├── src/
│   ├── main/
│   │   ├── java/com/gazapps/
│   │   │   ├── JCliApp.java              # Main class
│   │   │   ├── chat/                     # Chat/interface module
│   │   │   │   ├── ChatFormatter.java   # Output formatting
│   │   │   │   ├── ChatProcessor.java   # Command processing
│   │   │   │   ├── CommandHandler.java  # Command handler
│   │   │   │   └── wizard/
│   │   │   │       └── ServerWizard.java # Configuration wizard
│   │   │   ├── config/                   # Configuration system
│   │   │   │   ├── Config.java          # Main configuration
│   │   │   │   └── EnvironmentSetup.java # Environment setup
│   │   │   ├── exceptions/               # Custom exceptions
│   │   │   │   ├── ConfigurationException.java
│   │   │   │   └── MCPConfigException.java
│   │   │   ├── inference/               # Inference strategies
│   │   │   │   ├── Inference.java       # Base interface
│   │   │   │   ├── InferenceFactory.java # Strategy factory
│   │   │   │   ├── InferenceObserver.java # Observer pattern
│   │   │   │   ├── InferenceStrategy.java # Strategy enum
│   │   │   │   ├── simple/              # Simple strategy
│   │   │   │   │   ├── Simple.java
│   │   │   │   │   ├── SimpleLogger.java
│   │   │   │   │   └── QueryAnalysis.java
│   │   │   │   ├── react/               # ReAct strategy
│   │   │   │   │   ├── ReAct.java
│   │   │   │   │   └── ReActLogger.java
│   │   │   │   └── reflection/          # Reflection strategy
│   │   │   │       └── Reflection.java
│   │   │   ├── jtp/                     # Java Tree Parser
│   │   │   │   └── JavaTree.java
│   │   │   ├── llm/                     # LLM abstraction
│   │   │   │   ├── Llm.java             # Base interface
│   │   │   │   ├── LlmBuilder.java      # Builder pattern
│   │   │   │   ├── LlmCapabilities.java # LLM capabilities
│   │   │   │   ├── LlmConfig.java       # Configuration
│   │   │   │   ├── LlmException.java    # Exceptions
│   │   │   │   ├── LlmProvider.java     # Provider enum
│   │   │   │   ├── LlmResponse.java     # Standardized response
│   │   │   │   ├── providers/           # Provider implementations
│   │   │   │   │   ├── Claude.java
│   │   │   │   │   ├── Gemini.java
│   │   │   │   │   ├── Groq.java
│   │   │   │   │   └── OpenAI.java
│   │   │   │   └── tool/               # Tool system
│   │   │   │       ├── ToolCall.java
│   │   │   │       └── ToolDefinition.java
│   │   │   └── mcp/                    # MCP system
│   │   │       ├── MCPConfig.java      # MCP configuration
│   │   │       ├── MCPManager.java     # Main manager
│   │   │       ├── MCPService.java     # MCP services
│   │   │       ├── ToolMatcher.java    # Tool matching
│   │   │       ├── DomainRegistry.java # Domain registry
│   │   │       ├── domain/             # Domain models
│   │   │       │   ├── DomainDefinition.java
│   │   │       │   ├── Server.java
│   │   │       │   └── Tool.java
│   │   │       ├── matching/           # Matching system
│   │   │       │   ├── ParameterExtractor.java
│   │   │       │   └── SemanticMatcher.java
│   │   │       └── rules/              # Rules system
│   │   │           ├── RuleEngine.java
│   │   │           ├── RuleItem.java
│   │   │           └── ServerRules.java
│   │   └── resources/
│   │       └── logback.xml             # Logging configuration
│   └── test/                           # Unit tests
├── target/                             # Build artifacts
├── .classpath                          # Eclipse classpath
├── .project                            # Eclipse project
└── pom.xml                            # Maven configuration
```

## Technologies and Dependencies

- **Java 17**: Base language of the project
- **Maven**: Dependency management and build
- **MCP SDK 0.10.0**: Model Context Protocol for tool communication
- **Jackson 2.16.1**: JSON serialization/deserialization
- **Logback 1.4.14**: Logging system
- **JFiglet 0.0.9**: ASCII art text
- **JavaParser 3.25.10**: Java code analysis
- **JUnit 5.10.0**: Testing framework

## Inference Strategies

The system implements three distinct strategies for processing queries:

### 1. Simple Strategy

**Concept**: Direct strategy that analyzes the query and executes tools in a linear fashion without complex iterations.

**Operation**:
- Analyzes the query to determine execution type (direct answer, single tool, multiple tools)
- Identifies relevant tools using semantic matching
- Executes tools sequentially if needed
- Generates contextualized response based on results

**Use cases**: Simple queries, direct command execution, operations that don't require iterative reasoning.

### 2. ReAct Strategy (Reasoning and Acting)

**Concept**: Implements the ReAct pattern that combines reasoning with acting in iterative cycles. In each iteration, the system thinks about the next step, decides an action, executes it, and observes the result.

**Operation**:
- **Thought**: Analyzes current state and plans next action
- **Action**: Executes tool or provides final answer
- **Observation**: Observes and evaluates action result
- Repeats cycle until maximum iterations or stopping criterion
- Uses tool caching for optimization
- Applies continuation logic based on collected data utility

**Use cases**: Complex queries requiring multiple steps, problem debugging, analyses needing iterative validation.

### 3. Reflection Strategy

**Concept**: Strategy that generates an initial response and refines it through self-criticism and iterative improvement. Focuses on response quality through reflection on its own output.

**Operation**:
- Generates initial response using query analysis
- **Critique**: Critically evaluates the response (completeness, accuracy, clarity, relevance)
- **Refinement**: Improves response based on criticism
- Continues until quality criterion or maximum iterations
- Final quality metrics for validation

**Use cases**: Queries demanding high response quality, detailed technical analyses, documentation requiring precision.

## MCP (Model Context Protocol) System

The project integrates MCP servers for access to external tools:

- **MCPManager**: Manages connections and tool discovery
- **DomainRegistry**: Organizes tools by thematic domains
- **ToolMatcher**: Finds relevant tools using semantic matching
- **RuleEngine**: Applies custom rules for tool selection

## Supported LLM Providers

- **Gemini**: Google Gemini API
- **Groq**: Groq API with optimized models
- **Claude**: Anthropic Claude API
- **OpenAI**: OpenAI GPT models

## Available Commands

- `/help`: Shows help and available commands
- `/status`: Displays comprehensive system status (servers, tools, LLM, strategy, health)
- `/tools`: Lists all available MCP tools organized by domain
- `/servers`: Lists connected MCP servers with health status
- `/strategy <n>`: Changes inference strategy (simple|react|reflection)
- `/llm <provider>`: Changes LLM provider (openai|claude|gemini|groq)
- `/disable [num]`: Disables MCP server by number (removes tools from LLM)
- `/enable [num]`: Enables disabled MCP server (adds tools back to LLM)
- `/addserver`: Launches interactive wizard to add new MCP server
- `/debug`: Toggles debug mode for detailed execution logging
- `/clear`: Clears the screen
- `/quit` or `/exit`: Exits the application

### Command Usage Examples

```bash
# Check system status
/status

# Switch to ReAct inference strategy
/strategy react

# Change to OpenAI provider
/llm openai

# List servers and disable server #2
/disable
/disable 2

# Enable previously disabled server #1
/enable
/enable 1

# Add new MCP server using wizard
/addserver

# Toggle debug mode for verbose output
/debug
```

## Configuration

### File `config/application.properties`
```properties
llm.provider=groq
mcp.auto.discovery=true
mcp.refresh.interval=30000
mcp.connection.timeout=30000
mcp.call.timeout=60000
mcp.rules.enabled=true
```

### File `config/llm.properties`
```properties
# API keys for different providers
groq.api.key=YOUR_GROQ_KEY
openai.api.key=YOUR_OPENAI_KEY
claude.api.key=YOUR_CLAUDE_KEY
gemini.api.key=YOUR_GEMINI_KEY
```

## How to Run

```bash
# Compile project
mvn clean compile

# Run application
mvn exec:java -Dexec.mainClass="com.gazapps.JCliApp"

# Or run compiled JAR
java -jar target/JCli-0.0.1-SNAPSHOT-jar-with-dependencies.jar
```

## Implemented Features

- Interactive chat system with CLI interface
- Multiple interchangeable inference strategies
- Integration with multiple LLM providers
- MCP system for external tool access
- Detailed logging by component
- Operation caching for performance optimization
- Rules system for behavior customization
- Auto-discovery of tool domains
- Automatic configuration validation and setup

## Pending Items and Improvements

### Memory System
- [ ] Implement persistent memory system for conversations
- [ ] Add historical context in inference strategies
- [ ] Create mechanism for retrieving previous conversations

### LLM Testing
- [ ] Implement automated tests for OpenAI provider
- [ ] Validate complete integration with Claude API
- [ ] Test robustness of fallback between providers
- [ ] Performance benchmarking between different LLMs

### MCP System Improvements
- [ ] Implement automatic discovery of MCP servers on network
- [ ] Add support for remote MCP servers (HTTP/WebSocket)
- [ ] Create web interface for server management
- [ ] Implement tool API versioning

### Interface and Usability
- [ ] Add graphical interface (JavaFX or web)
- [ ] Implement autocomplete commands
- [ ] Add command history with navigation
- [ ] Create template system for common queries

### Plugin System
- [ ] Plugin architecture for extensibility
- [ ] Plugin system for custom inference strategies
- [ ] Community plugin marketplace

### Monitoring and Metrics
- [ ] Performance metrics dashboard
- [ ] Alerts for MCP connection failures
- [ ] Usage analysis of tools and strategies
- [ ] Log export to external systems

### Security
- [ ] Authentication for MCP server access
- [ ] API key encryption
- [ ] Tool execution auditing
- [ ] Sandbox for safe code execution

### Performance Optimizations
- [ ] Connection pooling for MCP clients
- [ ] Response caching strategies
- [ ] Parallel tool execution
- [ ] Memory usage optimization

### Documentation and Examples
- [ ] API documentation generation
- [ ] Tutorial examples for each strategy
- [ ] Best practices guide
- [ ] Troubleshooting documentation

### Testing and Quality Assurance
- [ ] Comprehensive unit test coverage
- [ ] Integration tests for MCP interactions
- [ ] Load testing for concurrent operations
- [ ] Code quality metrics and reporting

## Contribution

The project follows Clean Code and SOLID principles standards. Contributions are welcome through pull requests with adequate tests and documentation.

## License

Project developed for internal and educational use.