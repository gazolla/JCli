# MCP Package Documentation

## Overview

The `com.gazapps.mcp` package implements a comprehensive Model Context Protocol (MCP) system that provides intelligent tool discovery, semantic matching, and execution capabilities. The package orchestrates the connection between multiple MCP servers and LLMs through a sophisticated layered architecture.

## Architecture

```
com.gazapps.mcp/
├── MCPManager.java          # Central coordinator and facade
├── MCPService.java          # MCP server connection management
├── MCPConfig.java           # Configuration and persistence
├── DomainRegistry.java      # Domain-based tool organization
├── ToolMatcher.java         # Tool discovery intelligence
├── domain/                  # Domain models
│   ├── Server.java          # MCP server representation
│   ├── Tool.java            # Tool schema and validation
│   └── DomainDefinition.java # Domain categorization
├── matching/                # Intelligent matching system
│   ├── SemanticMatcher.java # LLM-based semantic analysis
│   └── ParameterExtractor.java # Parameter extraction
└── rules/                   # Rule-based customization
    ├── RuleEngine.java      # Rule processing engine
    ├── RuleItem.java        # Individual rule definitions
    └── ServerRules.java     # Server-specific rules
```

## Core Components

### MCPManager

The `MCPManager` serves as the central coordinator and main entry point for all MCP operations. It acts as a facade that orchestrates interactions between the different subsystems.

**Key Responsibilities:**
- **Unified Interface**: Provides a single point of access for tool discovery and execution
- **Domain Intelligence**: Uses domain-based filtering to optimize tool selection
- **Caching Strategy**: Implements multi-level caching for tool selections and observation utility evaluations
- **Query Analysis**: Performs syntactic and semantic analysis to determine execution requirements
- **Health Monitoring**: Tracks server health and manages automatic reconnection

**Core Methods:**
- `findSingleStepTools(String query)`: Locates tools for single-operation queries
- `findMultiStepTools(String query)`: Identifies tool chains for complex multi-step operations
- `analyzeQuery(String query, Llm llm)`: Determines whether a query needs direct answer, single tool, or multiple tools
- `executeTool(Tool tool, Map<String, Object> args)`: Executes tools with parameter validation
- `isObservationUseful(String observation, String originalQuery)`: Evaluates if tool output contains useful data

**Domain Filtering Strategy:**
The MCPManager uses a domain relevance threshold (0.3) to filter tools. It first identifies the best matching domain using the DomainRegistry, then searches for tools within that domain. For multi-step operations, it considers multiple domains with scores above 0.6.

### Relationship Between Servers, Domains, and Tools

The system uses a three-tier hierarchy to organize and discover capabilities:

#### Servers
- **Physical Layer**: Represent actual MCP server processes (filesystem, time, weather, etc.)
- **Connection Management**: Handle stdio communication, command execution, and health monitoring
- **Lifecycle**: Managed by MCPService with automatic reconnection and error handling

#### Domains
- **Logical Grouping**: Categorize tools by functional purpose (filesystem, time, math, internet, weather)
- **Semantic Organization**: Enable intelligent tool discovery through pattern matching and LLM analysis
- **Auto-Discovery**: Automatically infer domains from tool collections using LLM analysis

#### Tools
- **Functional Units**: Individual capabilities exposed by servers (read_file, get_weather, calculate, etc.)
- **Schema Definition**: Include complete parameter schemas with type validation and default values
- **Domain Assignment**: Inherit domain from parent server but can be reassigned based on functionality

**Relationship Flow:**
```
Server (Physical) → Domain (Logical) → Tools (Functional)
     ↓                    ↓                 ↓
   Command            Semantic          Parameter
   Execution          Matching          Validation
```

### Tool Discovery Intelligence

The package implements a sophisticated multi-layered approach to tool discovery:

#### Layer 1: Domain Filtering
- **Pattern Matching**: Uses predefined patterns and semantic keywords in domain definitions
- **LLM Analysis**: Employs unified domain scoring to determine query-domain relevance
- **Threshold Filtering**: Only considers domains with relevance scores above 0.3
- **Multi-Domain Support**: For complex queries, evaluates multiple domains simultaneously

#### Layer 2: Semantic Tool Matching
The `SemanticMatcher` class implements LLM-powered tool selection:

**Single Tool Selection:**
- Creates structured prompts with tool descriptions and domain context
- Uses numbered selection format for precise tool identification
- Applies rule engine enhancements for server-specific optimizations

**Multi-Tool Orchestration:**
- Analyzes query for sequential operations and dependencies
- Generates execution plans with placeholder chaining (`{{RESULT_X}}`)
- Optimizes for minimal tool usage while maintaining completeness

**Parameter Extraction:**
- Infers missing parameters from query context using LLM knowledge
- Validates parameter types against tool schemas
- Handles dependency injection between chained tools

#### Layer 3: Rule-Based Enhancement
The `RuleEngine` provides customizable behavior modification:

**Rule Types:**
- **Context Addition**: Appends specific guidance to prompts
- **Parameter Replacement**: Modifies prompts based on regex patterns
- **Trigger Matching**: Activates rules based on parameters or content keywords

**Example Rule Application:**
```json
{
  "name": "server-filesystem",
  "items": [{
    "triggers": ["path", "filename"],
    "contentKeywords": ["arquivo", "file", "directory"],
    "rules": {
      "context_add": "\n\nIMPORTANTE: Use sempre caminhos relativos simples."
    }
  }]
}
```

### MCPService

Manages the low-level communication with MCP servers through the official MCP SDK.

**Connection Management:**
- **Command Validation**: Verifies command availability before connection attempts
- **Transport Abstraction**: Uses StdioClientTransport for cross-platform compatibility
- **Retry Logic**: Implements exponential backoff for failed tool executions
- **Health Monitoring**: Tracks server responsiveness and connection status

**Tool Execution Pipeline:**
1. **Validation**: Checks tool existence and parameter completeness
2. **Normalization**: Applies default values and type conversions
3. **Execution**: Invokes tool through MCP client with timeout handling
4. **Result Processing**: Extracts content from MCP response format
5. **Error Handling**: Provides detailed error messages and fallback behavior

### MCPConfig

Handles all configuration persistence and validation with support for both static and dynamic server discovery.

**Configuration Structure:**
- **mcp.json**: Server definitions with commands, environments, and priorities
- **domains.json**: Domain definitions with patterns and semantic keywords
- **server-rules.json**: Server-specific rule customizations

**Auto-Configuration Features:**
- Creates default server configurations for common MCP servers
- Generates domain definitions with built-in patterns
- Establishes rule files for filesystem and time operations
- Validates configuration integrity on startup

### DomainRegistry

Implements intelligent domain management with LLM-powered semantic analysis.

**Domain Matching Process:**
1. **Pattern Analysis**: Performs fast text matching against predefined patterns
2. **Semantic Evaluation**: Uses LLM to calculate relevance scores (0.0-1.0)
3. **Multi-Domain Support**: Handles queries requiring multiple domains
4. **Fallback Strategy**: Reverts to pattern matching if LLM analysis fails

**Auto-Discovery Algorithm:**
- Analyzes tool collections to infer appropriate domain names
- Creates domain definitions based on tool descriptions and names
- Ensures domain uniqueness and meaningful categorization
- Integrates with existing domain structure to avoid conflicts

## Configuration Examples

### Server Configuration (mcp.json)
```json
{
  "mcpServers": {
    "filesystem": {
      "description": "Sistema de arquivos - Documents",
      "domain": "filesystem",
      "command": "npx -y @modelcontextprotocol/server-filesystem ./documents",
      "priority": 3,
      "enabled": true,
      "env": {"REQUIRES_NODEJS": "true"},
      "args": []
    }
  }
}
```

### Domain Definition (domains.json)
```json
{
  "filesystem": {
    "name": "filesystem",
    "description": "File system operations",
    "patterns": ["file", "directory", "folder", "read", "write"],
    "semanticKeywords": ["create", "edit", "save", "delete"]
  }
}
```

## Intelligence Features

### Query Analysis
The system performs comprehensive query analysis to determine optimal execution strategy:

**Execution Types:**
- **DIRECT_ANSWER**: Query can be answered using LLM knowledge alone
- **SINGLE_TOOL**: Requires one external tool or data source
- **MULTI_TOOL**: Requires multiple tools with sequential execution or dependencies

**Analysis Criteria:**
- **Knowledge Assessment**: Determines if query requires information beyond LLM training data
- **Action Analysis**: Examines linguistic structure for multiple verbs and sequential operations
- **Dependency Detection**: Identifies when one task's output becomes another's input

### Caching Strategy

The MCPManager implements intelligent caching to optimize performance:

**Tool Selection Cache:**
- **Key Structure**: `query + options.hashCode()`
- **Cache Invalidation**: Cleared when LLM provider changes
- **Hit Rate Optimization**: Logs cache hits for performance monitoring

**Observation Utility Cache:**
- **Key Structure**: `observation.hashCode() + originalQuery.hashCode()`
- **Purpose**: Avoids repeated LLM calls for utility evaluation
- **Memory Management**: Uses ConcurrentHashMap for thread safety

### Error Handling and Resilience

**Connection Resilience:**
- **Command Validation**: Pre-flight checks for command availability
- **Graceful Degradation**: Continues operation with subset of available servers
- **Automatic Recovery**: Periodic health checks and reconnection attempts

**Execution Resilience:**
- **Retry Logic**: Exponential backoff for transient failures
- **Parameter Validation**: Comprehensive schema-based validation
- **Fallback Strategies**: Alternative execution paths when primary tools fail

## Integration Points

### LLM Integration
The package integrates deeply with the LLM abstraction layer:
- **Provider Agnostic**: Works with any LLM provider (OpenAI, Claude, Gemini, Groq)
- **Dynamic Switching**: Supports runtime LLM provider changes
- **Prompt Engineering**: Optimizes prompts for different provider capabilities

### Inference Strategy Support
Designed to support all three inference strategies:
- **Simple**: Direct tool execution with minimal overhead
- **ReAct**: Provides observation utility evaluation for iteration decisions
- **Reflection**: Enables quality assessment and refinement cycles

## Performance Characteristics

**Startup Performance:**
- Parallel server connection attempts
- Background health monitoring
- Lazy initialization of expensive components

**Runtime Performance:**
- Multi-level caching reduces redundant computations
- Domain filtering minimizes tool search space
- Concurrent execution where possible

**Memory Management:**
- Bounded cache sizes with LRU eviction
- Efficient object pooling for frequently used components
- Automatic cleanup on shutdown

## Extension Points

The package is designed for extensibility:

**Custom Matchers:**
- Implement custom semantic matching algorithms
- Add domain-specific optimization strategies
- Integrate with external knowledge bases

**Rule Extensions:**
- Add new rule types for specialized behaviors
- Implement custom parameter extraction logic
- Create domain-specific enhancement patterns

**Server Types:**
- Support for HTTP/WebSocket MCP servers
- Custom transport implementations
- Protocol extensions and versioning