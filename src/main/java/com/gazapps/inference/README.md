# Inference Package Documentation

## Overview

The `com.gazapps.inference` package implements three distinct reasoning strategies for processing user queries and generating responses. Each strategy represents a different approach to problem-solving: direct execution, iterative reasoning with action cycles, and quality-focused iterative refinement.

## Architecture

```
com.gazapps.inference/
├── Inference.java              # Base interface for all strategies
├── InferenceFactory.java       # Factory for strategy creation
├── InferenceObserver.java      # Observer pattern for real-time feedback
├── InferenceStrategy.java      # Strategy enumeration
├── simple/                     # Direct execution strategy
│   ├── Simple.java             # Linear execution implementation
│   ├── SimpleLogger.java       # Logging for Simple strategy
│   └── QueryAnalysis.java     # Query classification
├── react/                      # Reasoning and Acting strategy
│   ├── ReAct.java              # Iterative thought-action cycles
│   └── ReActLogger.java        # Logging for ReAct strategy
└── reflection/                 # Quality refinement strategy
    └── Reflection.java         # Self-critique and improvement
```

## Core Interface

### Inference

The base interface defines the contract for all inference strategies:

```java
public interface Inference extends AutoCloseable {
    String processQuery(String query);
    String buildSystemPrompt();
    InferenceStrategy getStrategyName();
    void close();
}
```

**Key Methods:**
- **`processQuery(String query)`**: Main entry point that processes a user query and returns a response
- **`buildSystemPrompt()`**: Constructs strategy-specific system prompts for LLM interactions
- **`getStrategyName()`**: Returns the strategy identifier for logging and debugging
- **`close()`**: Cleanup method for releasing resources and finalizing logs

### InferenceObserver

Implements the Observer pattern to provide real-time feedback during inference execution:

**Lifecycle Events:**
- **`onInferenceStart(String query, String strategyName)`**: Triggered when inference begins
- **`onInferenceComplete(String finalResponse)`**: Called when inference finishes successfully
- **`onError(String error, Exception exception)`**: Handles error conditions

**Tool Interaction Events:**
- **`onToolDiscovery(List<String> availableTools)`**: Reports discovered tools
- **`onToolSelection(String toolName, Map<String, Object> args)`**: Logs tool selection with parameters
- **`onToolExecution(String toolName, String result)`**: Reports tool execution results

**Strategy-Specific Events:**
- **`onThought(String thought)`**: ReAct strategy thinking process
- **`onPartialResponse(String partialContent)`**: Incremental response updates

## Strategy Implementations

### Simple Strategy

The Simple strategy implements linear query processing with direct tool execution.

**Processing Flow:**
1. **Query Analysis**: Determines execution type using `QueryAnalysis`
2. **Tool Discovery**: Finds relevant tools through MCP system
3. **Execution Decision**: Routes to direct answer, single tool, or multi-step execution
4. **Response Generation**: Produces contextualized responses

**Query Classification:**
The `QueryAnalysis` class categorizes queries into three execution types:

- **`DIRECT_ANSWER`**: Query can be answered using LLM knowledge alone
- **`SINGLE_TOOL`**: Requires one external tool or data source
- **`MULTI_TOOL`**: Requires multiple tools with sequential execution

**Multi-Step Execution:**
For complex queries requiring multiple tools, the Simple strategy implements:

**Dependency Resolution:**
- Analyzes parameter dependencies using placeholder patterns (`{{RESULT_X}}`)
- Orders tool execution based on dependency levels
- Resolves parameter references dynamically during execution

**Parameter Resolution Process:**
```java
private Map<String, Object> resolveParameterReferences(Map<String, Object> params, Map<String, String> stepResults) {
    // Replaces {{RESULT_1}}, {{RESULT_2}}, etc. with actual results
    // from previous tool executions
}
```

**Execution Ordering:**
Tools are ordered by dependency level, where level 0 tools have no dependencies, level 1 tools depend on level 0 results, and so on.

### ReAct Strategy (Reasoning and Acting)

The ReAct strategy implements iterative cycles of reasoning, action, and observation.

**Core Cycle:**
1. **Thought**: LLM analyzes current state and plans next action
2. **Action**: Either executes a tool or provides final answer
3. **Observation**: Evaluates action results and determines continuation

**Iteration Management:**
- **Maximum Iterations**: Configurable limit (default: 5-7 iterations)
- **Early Termination**: Stops when sufficient useful data is collected
- **Tool Usage Tracking**: Prevents excessive repeated tool calls

**Action Decision Process:**
The strategy uses structured JSON responses to determine actions:

```json
{
  "action": "USE_TOOL" or "FINAL_ANSWER",
  "tool_name": "tool_name",
  "parameters": {"param": "value"},
  "final_answer": "response"
}
```

**Continuation Logic:**
ReAct implements sophisticated logic to determine when to continue iterating:

**Stopping Criteria:**
- **Useful Data Threshold**: Stops after collecting 2+ pieces of useful data
- **Tool Usage Limits**: Prevents same tool being called more than 3 times
- **Iteration Limits**: Hard stop at 7 iterations
- **Recent Progress**: Requires useful data in recent iterations

**Observation Classification:**
Observations are classified into three types:
- **`USEFUL_DATA`**: Contains specific, actionable information
- **`GENERIC_SUCCESS`**: Success message without specific data
- **`ERROR`**: Failure or error condition

**Placeholder Processing:**
Supports dynamic parameter injection using `{{RESULT_1}}` placeholders for tool chaining.

### Reflection Strategy

The Reflection strategy focuses on iterative quality improvement through self-critique.

**Processing Flow:**
1. **Initial Response Generation**: Creates baseline response using query analysis
2. **Critique Cycle**: Evaluates response quality across multiple dimensions
3. **Refinement Process**: Improves response based on identified issues
4. **Quality Assessment**: Final evaluation with quantitative metrics

**Critique Process:**
The strategy performs multi-dimensional analysis:

**Evaluation Dimensions:**
- **Completeness**: Does the response address all aspects of the query?
- **Accuracy**: Is the information correct and reliable?
- **Clarity**: Is the response easy to understand?
- **Relevance**: Does the response stay focused on the query?

**Critique Result Structure:**
```java
public static class CritiqueResult {
    public final List<String> issues;
    public final List<String> suggestions;
    public final double confidenceScore;
    public final boolean needsImprovement;
}
```

**Quality Metrics:**
Final responses are evaluated with quantitative scores:

```java
public static class QualityMetrics {
    public final double completeness;
    public final double accuracy;
    public final double relevance;
    public final double clarity;
    public final double overallScore; // Average of all dimensions
}
```

**Refinement Process:**
Based on critique results, the strategy generates improved versions by:
- Addressing identified issues systematically
- Incorporating suggested improvements
- Maintaining response coherence and structure

## Factory Pattern

### InferenceFactory

Provides centralized creation and configuration of inference strategies:

**Creation Methods:**
- **`createSimple(MCPManager, Llm)`**: Creates Simple strategy with default options
- **`createReAct(MCPManager, Llm, int maxIterations)`**: Creates ReAct with iteration limit
- **`createReflection(MCPManager, Llm, Map<String, Object> params)`**: Creates Reflection with custom parameters

**Configuration Options:**
Common configuration parameters across strategies:
- **`observer`**: InferenceObserver instance for real-time feedback
- **`debug`**: Boolean flag for verbose logging
- **`maxIterations`**: Maximum iteration count for iterative strategies

**Dynamic Strategy Creation:**
```java
public static Inference createInference(InferenceStrategy strategy, MCPManager mcpManager, Llm llm, Map<String, Object> options) {
    return switch (strategy) {
        case REACT -> createReAct(mcpManager, llm, options);
        case REFLECTION -> createReflection(mcpManager, llm, options);
        case SIMPLE -> createSimple(mcpManager, llm, options);
    };
}
```

## Logging Infrastructure

Each strategy implements specialized logging for debugging and analysis:

### SimpleLogger
- **Conversation Flow**: Logs complete inference sessions with start/end markers
- **Tool Execution**: Detailed parameter resolution and execution tracking
- **Multi-Step Analysis**: Dependency ordering and step-by-step execution logs

### ReActLogger
- **Iteration Tracking**: Logs each thought-action-observation cycle
- **Decision Analysis**: Records action decisions and reasoning
- **Progress Monitoring**: Tracks iteration progress and termination reasons

### Reflection Logging
- **Quality Assessment**: Logs critique results and improvement suggestions
- **Refinement Tracking**: Records iterative improvements and quality progression

**Log File Organization:**
```
JavaCLI/log/inference/
├── simple-conversations.log
├── react-conversations.log
└── reflection-conversations.log
```

## Integration Points

### MCP System Integration

All strategies integrate with the MCP system for tool discovery and execution:

**Tool Discovery Process:**
1. **Query Analysis**: Determine tool requirements
2. **Domain Filtering**: Use MCPManager to identify relevant domains
3. **Tool Selection**: Apply semantic matching for tool identification
4. **Parameter Extraction**: Use LLM to extract and validate parameters

**Execution Flow:**
1. **Validation**: Check tool availability and parameter completeness
2. **Execution**: Invoke tools through MCPManager
3. **Result Processing**: Handle success/failure cases appropriately
4. **Context Integration**: Incorporate results into response generation

### LLM Integration

Strategies interact with LLMs through standardized interfaces:

**Prompt Engineering:**
Each strategy implements custom prompt construction for:
- **Query Analysis**: Determining execution requirements
- **Tool Selection**: Identifying relevant tools from available options
- **Parameter Extraction**: Inferring missing parameters from context
- **Response Generation**: Creating contextualized final responses

**Response Processing:**
Strategies handle LLM responses with:
- **JSON Parsing**: Structured response interpretation
- **Error Handling**: Graceful degradation for parsing failures
- **Validation**: Response format and content verification

## Performance Characteristics

### Simple Strategy
- **Execution Time**: Linear with tool count and complexity
- **Memory Usage**: Minimal overhead, stateless execution
- **Scalability**: Handles complex multi-step operations efficiently

### ReAct Strategy
- **Execution Time**: Variable based on iteration count and tool complexity
- **Memory Usage**: Maintains iteration history and tool cache
- **Scalability**: Self-limiting through stopping criteria

### Reflection Strategy
- **Execution Time**: Higher due to multiple refinement cycles
- **Memory Usage**: Stores multiple response versions for comparison
- **Scalability**: Configurable iteration limits for performance control

## Error Handling

### Strategy-Level Error Handling
All strategies implement consistent error handling:

**Exception Types:**
- **LLM Communication Errors**: Fallback to simpler processing
- **Tool Execution Failures**: Graceful degradation with error messages
- **Configuration Errors**: Clear error reporting with remediation guidance

**Recovery Mechanisms:**
- **Retry Logic**: Automatic retry for transient failures
- **Fallback Strategies**: Alternative execution paths when primary methods fail
- **Graceful Degradation**: Partial functionality when components are unavailable

### Observer Error Notification
The InferenceObserver interface ensures error visibility:
- **Real-time Error Reporting**: Immediate notification of failures
- **Context Preservation**: Error messages include sufficient context for debugging
- **Recovery Guidance**: Suggestions for resolving common error conditions

## Extension Points

The package is designed for extensibility:

### Custom Strategies
New strategies can be added by:
1. Implementing the `Inference` interface
2. Adding strategy enum value
3. Updating factory method
4. Implementing custom logging if needed

### Observer Extensions
Custom observers can be implemented for:
- **Performance Monitoring**: Execution time and resource usage tracking
- **Analytics**: Query pattern analysis and success rate monitoring
- **Integration**: External system notifications and data export

### Configuration Extensions
Strategy behavior can be customized through:
- **Option Maps**: Runtime configuration parameters
- **Custom Prompts**: Strategy-specific prompt templates
- **Tool Preferences**: Bias toward specific tool categories or domains