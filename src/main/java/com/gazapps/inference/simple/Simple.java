package com.gazapps.inference.simple;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gazapps.inference.Inference;
import com.gazapps.inference.InferenceObserver;
import com.gazapps.inference.InferenceStrategy;
import com.gazapps.llm.Llm;
import com.gazapps.llm.LlmResponse;
import com.gazapps.mcp.MCPManager;
import com.gazapps.mcp.MCPService;
import com.gazapps.mcp.domain.Tool;

public class Simple implements Inference {
    
    private final MCPManager mcpManager;
    private final Llm llm;
    private InferenceObserver observer;
    private final SimpleLogger logger;
    
    public Simple(MCPManager mcpManager, Llm llm, Map<String, Object> options) {
        this.mcpManager = Objects.requireNonNull(mcpManager, "MCPManager is required");
        this.llm = Objects.requireNonNull(llm, "Llm is required");
        this.observer = (InferenceObserver) options.get("observer");
        this.logger = new SimpleLogger();
        
        logger.logDebug("[SIMPLE] Initialized with LLM: {} - logs in JavaCLI/log/inference/simple-conversations.log", llm.getProviderName());
    }

    @Override
    public String processQuery(String query) {
        logger.logDebug("Processing query: {}", query);
        logger.logInferenceStart(query);
        
        try {
            if (observer != null) {
                observer.onInferenceStart(query, getStrategyName().name());
            }
            
            QueryAnalysis analysis = mcpManager.analyzeQuery(query, llm);
            logger.logQueryAnalysis(analysis);
            
            return switch (analysis.execution) {
                case DIRECT_ANSWER -> {
                    String result = generateDirectResponse(query);
                    if (observer != null) {
                        observer.onInferenceComplete(result);
                    }
                    logger.logInferenceEnd(result);
                    yield result;
                }
                case MULTI_TOOL -> executeWithTools(query, mcpManager.findMultiStepTools(query));
                case SINGLE_TOOL -> executeWithTools(query, mcpManager.findSingleStepTools(query));
            };
            
        } catch (Exception e) {
            logger.logError(e);
            if (observer != null) {
                observer.onError("Error processing query", e);
            }
            return "Error processing query: " + e.getMessage();
        }
    }
    
    private String executeWithTools(String query, Optional<Map<Tool, Map<String, Object>>> optionalSelections) {
        Map<Tool, Map<String, Object>> selections = optionalSelections.orElse(Map.of());
        
        if (observer != null && !selections.isEmpty()) {
            var toolNames = selections.keySet().stream()
                .map(Tool::getName).toList();
            observer.onToolDiscovery(toolNames);
        }
        
        logger.logToolSelections(selections);
        
        String result;
        if (selections.isEmpty()) {
            result = generateDirectResponse(query);
        } else if (selections.size() == 1) {
            Map.Entry<Tool, Map<String, Object>> selection = selections.entrySet().iterator().next();
            result = executeSingleTool(query, selection.getKey(), selection.getValue());
        } else {
            result = executeMultiStep(query, selections);
        }
        
        if (observer != null) {
            observer.onInferenceComplete(result);
        }
        
        logger.logInferenceEnd(result);
        return result;
    }
    
    private String generateDirectResponse(String query) {
        logger.logDebug("Generating direct LLM response");
        LlmResponse response = llm.generateResponse(
            "Answer the following question using your knowledge:\n\n" + query
        );
        return response.isSuccess() ? response.getContent() 
                                    : "Failed to generate response: " + response.getErrorMessage();
    }
    
    private String executeSingleTool(String query, Tool tool, Map<String, Object> parameters) {
        logger.logDebug("Executing single tool: {} with parameters: {}", tool.getName(), parameters);
        if (observer != null) {
            observer.onToolSelection(tool.getName(), parameters);
        }
        try {
            MCPService.ToolExecutionResult result = mcpManager.executeTool(tool, parameters);
            if (observer != null) {
                observer.onToolExecution(tool.getName(), result.message);
            }
            if (!result.success) {
                return "Tool execution failed: " + result.content;
            }
            
            return generateContextualResponse(query, tool, result.content);
            
        } catch (Exception e) {
            return "Failed to execute tool: " + e.getMessage();
        }
    }
    
    private String executeMultiStep(String query, Map<Tool, Map<String, Object>> selections) {
        logger.logDebug("Executing multi-step with {} tools", selections.size());
        
        StringBuilder results = new StringBuilder();
        Map<String, String> stepResults = new HashMap<>();
        
        List<Map.Entry<Tool, Map<String, Object>>> orderedEntries = orderToolsByDependencies(selections);
        
        List<Map.Entry<Tool, Integer>> entriesWithLevels = orderedEntries.stream()
                .map(entry -> Map.entry(entry.getKey(), extractDependencyLevel(entry.getValue())))
                .toList();
        logger.logToolExecutionOrder(entriesWithLevels);
        
        int step = 1;
        
        for (Map.Entry<Tool, Map<String, Object>> entry : orderedEntries) {
            if (step > 3) break; // Limit to 3 tools
            
            Tool tool = entry.getKey();
            Map<String, Object> originalParams = entry.getValue();
            Map<String, Object> resolvedParams = resolveParameterReferences(originalParams, stepResults);
            logger.logStepParameterResolution(step, originalParams, resolvedParams, stepResults);
            
            if (observer != null) {
                observer.onToolSelection(tool.getName(), resolvedParams);
            }
            
            MCPService.ToolExecutionResult result = mcpManager.executeTool(tool, resolvedParams);
            
            if (observer != null) {
                observer.onToolExecution(tool.getName(), result.message);
            }
            
            if (!result.success) {
                logger.logStepFailure(step, result.message);
                return String.format("Step %d failed: %s", step, result.message);
            }
            
            stepResults.put("RESULT_" + step, result.content);
            logger.logStepExecutionResult(step, tool, result);
            results.append(String.format("Step %d (%s): %s\n", 
                         step, tool.getName(), result.message));
            step++;
        }
        return generateConsolidatedResponse(query, results.toString());
    }
    
    private List<Map.Entry<Tool, Map<String, Object>>> orderToolsByDependencies(
            Map<Tool, Map<String, Object>> selections) {
        
        List<Map.Entry<Tool, Map<String, Object>>> orderedTools = new ArrayList<>(selections.entrySet());
        
        orderedTools.sort((a, b) -> {
            int levelA = extractDependencyLevel(a.getValue());
            int levelB = extractDependencyLevel(b.getValue());
            return Integer.compare(levelA, levelB);
        });
        
        return orderedTools;
    }
    
    private int extractDependencyLevel(Map<String, Object> parameters) {
        int maxLevel = 0;
        
        for (Object value : parameters.values()) {
            if (value instanceof String) {
                String strValue = (String) value;

                java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\{\\{RESULT_(\\d+)\\}\\}");
                java.util.regex.Matcher matcher = pattern.matcher(strValue);
                
                while (matcher.find()) {
                    try {
                        int level = Integer.parseInt(matcher.group(1));
                        maxLevel = Math.max(maxLevel, level);
                    } catch (NumberFormatException e) {
                        // Ignore invalid numbers
                    }
                }
            }
        }
        
        return maxLevel;
    }
    
    private Map<String, Object> resolveParameterReferences(Map<String, Object> params, Map<String, String> stepResults) {
        Map<String, Object> resolved = new HashMap<>();
        
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            Object value = entry.getValue();
            
            if (value instanceof String) {
                String strValue = (String) value;

                for (Map.Entry<String, String> resultEntry : stepResults.entrySet()) {
                    String placeholder = "{{" + resultEntry.getKey() + "}}";
                    if (strValue.contains(placeholder)) {
                        strValue = strValue.replace(placeholder, resultEntry.getValue());
                    }
                }
                resolved.put(entry.getKey(), strValue);
            } else {
                resolved.put(entry.getKey(), value);
            }
        }
        
        return resolved;
    }
    
    private String generateContextualResponse(String query, Tool tool, String toolResult) {
        String prompt = """
                Based on the tool execution result, provide a comprehensive response:

                Original query: %s
                Tool used: %s
                Result: %s

                Provide a natural, always in the same language of the original query, helpful response incorporating the tool result.
                """.formatted(query, tool.getName(), toolResult);
        
        LlmResponse response = llm.generateResponse(prompt);
        return response.isSuccess() ? response.getContent() 
                                    : "Tool executed successfully: " + toolResult;
    }
    
    private String generateConsolidatedResponse(String query, String results) {
        String prompt = """
                Consolidate these multi-step results into a final response:

                Original query: %s
                Execution results:
                %s

                Provide a consolidated, helpful summary always in the same language of the original query.
                """.formatted(query, results);
        
        LlmResponse response = llm.generateResponse(prompt);
        return response.isSuccess() ? response.getContent() 
                                    : "Multi-step execution completed:\n" + results;
    }

    @Override
    public String buildSystemPrompt() {
        return "Simple inference strategy using MCP tool matching and execution.";
    }

    @Override
    public InferenceStrategy getStrategyName() {
        return InferenceStrategy.SIMPLE;
    }
    
    @Override
    public void close() {
        logger.logDebug("[SIMPLE] Inference strategy closed - logs salvos em JavaCLI/log/inference/");
    }
}
