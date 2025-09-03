package com.gazapps.inference.simple;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gazapps.mcp.MCPService;
import com.gazapps.mcp.domain.Tool;

public class SimpleLogger {
    private static final Logger conversationLogger = LoggerFactory.getLogger("com.gazapps.inference.simple.Simple.conversations");
    private static final Logger logger = LoggerFactory.getLogger(Simple.class);

    public void logDebug(String message, Object... args) {
        logger.debug(message, args);
    }

    public void logInferenceStart(String query) {
        if (conversationLogger.isInfoEnabled()) {
            conversationLogger.info("=== SIMPLE INFERENCE START ===");
            conversationLogger.info("Query: {}", query);
        }
    }

    public void logQueryAnalysis(QueryAnalysis analysis) {
        if (conversationLogger.isInfoEnabled()) {
            conversationLogger.info("Query analysis - Execution: {}", analysis.execution);
            conversationLogger.info("Reasoning: {}", analysis.reasoning);
        }
    }

    public void logToolSelections(Map<Tool, Map<String, Object>> selections) {
        if (conversationLogger.isInfoEnabled()) {
            conversationLogger.info("Found {} tool(s) for execution", selections.size());
            selections.forEach((tool, params) ->
                conversationLogger.info("  - Tool: {} with params: {}", tool.getName(), params));
        }
    }

    public void logInferenceEnd(String result) {
        if (conversationLogger.isInfoEnabled()) {
            conversationLogger.info("=== SIMPLE INFERENCE END ===");
            conversationLogger.info("Final result: {}", result);
            conversationLogger.info("==============================");
        }
    }

    public void logError(Exception e) {
        logger.error("Error processing query", e);
        if (conversationLogger.isErrorEnabled()) {
            conversationLogger.error("=== SIMPLE INFERENCE ERROR ===");
            conversationLogger.error("Error: {}", e.getMessage());
            conversationLogger.error("===============================");
        }
    }

    public void logToolExecutionOrder(int step, Tool tool, int dependencyLevel) {
        if (conversationLogger.isInfoEnabled()) {
            conversationLogger.info("Step {}: {} (dependency level: {})", step, tool.getName(), dependencyLevel);
        }
    }

    public void logStepParameterResolution(int step, Map<String, Object> originalParams, Map<String, Object> resolvedParams, Map<String, String> stepResults) {
        if (conversationLogger.isInfoEnabled()) {
            conversationLogger.info("=== STEP {} PARAMETER RESOLUTION ===", step);
            conversationLogger.info("Original params: {}", originalParams);
            conversationLogger.info("Resolved params: {}", resolvedParams);
            conversationLogger.info("Available step results: {}", stepResults.keySet());
        }
    }

    public void logStepExecutionResult(int step, Tool tool, MCPService.ToolExecutionResult result) {
        if (conversationLogger.isInfoEnabled()) {
            conversationLogger.info("=== STEP {} EXECUTION RESULT ===", step);
            conversationLogger.info("Tool: {}", tool.getName());
            conversationLogger.info("Success: {}", result.success);
            conversationLogger.info("Message: {}", result.message);
            String contentPreview = result.content != null && result.content.length() > 200
                ? result.content.substring(0, 200) + "..."
                : result.content;
            conversationLogger.info("Content preview: {}", contentPreview);
        }
    }

    public void logStepFailure(int step, String message) {
        if (conversationLogger.isErrorEnabled()) {
            conversationLogger.error("=== STEP {} FAILED ===", step);
            conversationLogger.error("Error: {}", message);
        }
    }

    public void logToolExecutionOrder(List<Map.Entry<Tool, Integer>> orderedEntriesWithLevels) {
        if (conversationLogger.isInfoEnabled()) {
            conversationLogger.info("=== TOOL EXECUTION ORDER ===");
            for (int i = 0; i < orderedEntriesWithLevels.size(); i++) {
                Tool tool = orderedEntriesWithLevels.get(i).getKey();
                int depLevel = orderedEntriesWithLevels.get(i).getValue();
                conversationLogger.info("Step {}: {} (dependency level: {})", i + 1, tool.getName(), depLevel);
            }
        }
    }
}