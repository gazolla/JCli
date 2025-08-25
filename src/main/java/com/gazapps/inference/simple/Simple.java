package com.gazapps.inference.simple;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gazapps.inference.Inference;
import com.gazapps.inference.InferenceStrategy;
import com.gazapps.llm.Llm;
import com.gazapps.llm.LlmResponse;
import com.gazapps.mcp.MCPManager;
import com.gazapps.mcp.MCPService;
import com.gazapps.mcp.domain.Tool;

public class Simple implements Inference {
    
    private static final Logger logger = LoggerFactory.getLogger(Simple.class);
    
    private final MCPManager mcpManager;
    private final Llm llm;
    private final Map<String, Object> options;

    public Simple(MCPManager mcpManager, Llm llm, Map<String, Object> options) {
        this.mcpManager = Objects.requireNonNull(mcpManager, "MCPManager is required");
        this.llm = Objects.requireNonNull(llm, "Llm is required");
        this.options = options;
        
        logger.info("[SIMPLE] Initialized with LLM: {}", llm.getProviderName());
    }

    @Override
    public String processQuery(String query) {
        logger.debug("Processing query: {}", query);
        
        try {
            // Usar novo método com parâmetros
            Map<Tool, Map<String, Object>> selections = mcpManager.findToolsWithParameters(query);
            
            if (selections.isEmpty()) {
                return generateDirectResponse(query);
            }
            
            if (selections.size() == 1) {
                Map.Entry<Tool, Map<String, Object>> selection = selections.entrySet().iterator().next();
                return executeSingleTool(query, selection.getKey(), selection.getValue());
            }
            
            return executeMultiStep(query, selections);
            
        } catch (Exception e) {
            logger.error("Error processing query", e);
            return "Error processing query: " + e.getMessage();
        }
    }
    
    private String generateDirectResponse(String query) {
        logger.debug("Generating direct LLM response");
        
        LlmResponse response = llm.generateResponse(
            "Answer the following question using your knowledge:\n\n" + query
        );
        
        return response.isSuccess() ? response.getContent() 
                                    : "Failed to generate response: " + response.getErrorMessage();
    }
    
    private String executeSingleTool(String query, Tool tool, Map<String, Object> parameters) {
        logger.debug("Executing single tool: {} with parameters: {}", tool.getName(), parameters);
        
        try {
            MCPService.ToolExecutionResult result = mcpManager.executeTool(tool, parameters);
            
            if (!result.success) {
                return "Tool execution failed: " + result.content;
            }
            
            return generateContextualResponse(query, tool, result.content);
            
        } catch (Exception e) {
            return "Failed to execute tool: " + e.getMessage();
        }
    }
    
    private String executeMultiStep(String query, Map<Tool, Map<String, Object>> selections) {
        logger.debug("Executing multi-step with {} tools", selections.size());
        
        StringBuilder results = new StringBuilder();
        int step = 1;
        
        for (Map.Entry<Tool, Map<String, Object>> entry : selections.entrySet()) {
            if (step > 3) break; // Limit to 3 tools
            
            Tool tool = entry.getKey();
            Map<String, Object> parameters = entry.getValue();
            MCPService.ToolExecutionResult result = mcpManager.executeTool(tool, parameters);
            
            if (!result.success) {
                return String.format("Step %d failed: %s", step, result.message);
            }
            
            results.append(String.format("Step %d (%s): %s\n", 
                         step, tool.getName(), result.message));
            step++;
        }
        
        return generateConsolidatedResponse(query, results.toString());
    }
    
    private String generateContextualResponse(String query, Tool tool, String toolResult) {
        String prompt = String.format(
            "Based on the tool execution result, provide a comprehensive response:\n\n" +
            "Original query: %s\n" +
            "Tool used: %s\n" +
            "Result: %s\n\n" +
            "Provide a natural, helpful response incorporating the tool result.",
            query, tool.getName(), toolResult
        );
        
        LlmResponse response = llm.generateResponse(prompt);
        return response.isSuccess() ? response.getContent() 
                                    : "Tool executed successfully: " + toolResult;
    }
    
    private String generateConsolidatedResponse(String query, String results) {
        String prompt = String.format(
            "Consolidate these multi-step results into a final response:\n\n" +
            "Original query: %s\n" +
            "Execution results:\n%s\n\n" +
            "Provide a consolidated, helpful summary.",
            query, results
        );
        
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
        logger.debug("[SIMPLE] Inference strategy closed");
    }
}
