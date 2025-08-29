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
    
    private static final Logger logger = LoggerFactory.getLogger(Simple.class);
    private static final Logger conversationLogger = LoggerFactory.getLogger("com.gazapps.inference.simple.Simple.conversations");
    
    private final MCPManager mcpManager;
    private final Llm llm;
    private InferenceObserver observer;

    public Simple(MCPManager mcpManager, Llm llm, Map<String, Object> options) {
        this.mcpManager = Objects.requireNonNull(mcpManager, "MCPManager is required");
        this.llm = Objects.requireNonNull(llm, "Llm is required");
        this.observer = (InferenceObserver) options.get("observer");
        
        logger.info("[SIMPLE] Initialized with LLM: {} - logs em JavaCLI/log/inference/simple-conversations.log", llm.getProviderName());
    }

    @Override
    public String processQuery(String query) {
        logger.debug("Processing query: {}", query);

        if (conversationLogger.isInfoEnabled()) {
            conversationLogger.info("=== SIMPLE INFERENCE START ===");
            conversationLogger.info("Query: {}", query);
        }
        
        try {

        	if (observer != null) {
                observer.onInferenceStart(query, getStrategyName().name());
            }
        	
            Optional<Map<Tool, Map<String, Object>>> optionalSelections;
            
            boolean isMultiStep = isMultiStep(query, llm);
            
            if (conversationLogger.isInfoEnabled()) {
                conversationLogger.info("Multi-step analysis: {}", isMultiStep);
            }
            
            if (isMultiStep) {
            	optionalSelections = mcpManager.findMultiStepTools(query);
            } else {
            	optionalSelections = mcpManager.findSingleStepTools(query);
            }
            
            Map<Tool, Map<String, Object>> selections = optionalSelections.orElse(Map.of());
            
            // Notificar observer sobre discovery de ferramentas
            if (observer != null && !selections.isEmpty()) {
                var toolNames = selections.keySet().stream()
                    .map(Tool::getName).toList();
                observer.onToolDiscovery(toolNames);
            }
            
            if (conversationLogger.isInfoEnabled()) {
                conversationLogger.info("Found {} tool(s) for execution", selections.size());
                for (Map.Entry<Tool, Map<String, Object>> entry : selections.entrySet()) {
                    conversationLogger.info("  - Tool: {} with params: {}", 
                        entry.getKey().getName(), entry.getValue());
                }
            }
            
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
            
            if (conversationLogger.isInfoEnabled()) {
                conversationLogger.info("=== SIMPLE INFERENCE END ===");
                conversationLogger.info("Final result: {}", result);
                conversationLogger.info("==============================");
            }
            
            return result;
            
        } catch (Exception e) {
            logger.error("Error processing query", e);
            
            // Notificar observer do erro
            if (observer != null) {
                observer.onError("Error processing query", e);
            }
            
            if (conversationLogger.isErrorEnabled()) {
                conversationLogger.error("=== SIMPLE INFERENCE ERROR ===");
                conversationLogger.error("Error: {}", e.getMessage());
                conversationLogger.error("===============================");
            }
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
        logger.debug("Executing multi-step with {} tools", selections.size());
        
        StringBuilder results = new StringBuilder();
        Map<String, String> stepResults = new HashMap<>();
        
        // NOVA IMPLEMENTAÇÃO: Ordenar ferramentas por dependências
        List<Map.Entry<Tool, Map<String, Object>>> orderedEntries = orderToolsByDependencies(selections);
        
        // NOVO LOG: Mostrar ordem de execução planejada
        if (conversationLogger.isInfoEnabled()) {
            conversationLogger.info("=== TOOL EXECUTION ORDER ===");
            for (int i = 0; i < orderedEntries.size(); i++) {
                Tool tool = orderedEntries.get(i).getKey();
                int depLevel = extractDependencyLevel(orderedEntries.get(i).getValue());
                conversationLogger.info("Step {}: {} (dependency level: {})", 
                    i + 1, tool.getName(), depLevel);
            }
        }
        
        int step = 1;
        
        // MODIFICADO: Iterar sobre lista ordenada em vez do Map original
        for (Map.Entry<Tool, Map<String, Object>> entry : orderedEntries) {
            if (step > 3) break; // Limit to 3 tools
            
            Tool tool = entry.getKey();
            Map<String, Object> originalParams = entry.getValue();
            
            // NOVO LOG: Parâmetros antes da resolução
            if (conversationLogger.isInfoEnabled()) {
                conversationLogger.info("=== STEP {} PARAMETER RESOLUTION ===", step);
                conversationLogger.info("Original params: {}", originalParams);
            }
            
            // RESOLVER REFERÊNCIAS {{RESULT_N}}
            Map<String, Object> resolvedParams = resolveParameterReferences(originalParams, stepResults);
            
            // NOVO LOG: Parâmetros após resolução
            if (conversationLogger.isInfoEnabled()) {
                conversationLogger.info("Resolved params: {}", resolvedParams);
                conversationLogger.info("Available step results: {}", stepResults.keySet());
            }
            
            if (observer != null) {
                observer.onToolSelection(tool.getName(), resolvedParams);
            }
            
            MCPService.ToolExecutionResult result = mcpManager.executeTool(tool, resolvedParams);
            
            if (observer != null) {
                observer.onToolExecution(tool.getName(), result.message);
            }
            
            if (!result.success) {
                if (conversationLogger.isErrorEnabled()) {
                    conversationLogger.error("=== STEP {} FAILED ===", step);
                    conversationLogger.error("Error: {}", result.message);
                }
                return String.format("Step %d failed: %s", step, result.message);
            }
            
            // MODIFICADO: Armazenar result.content em vez de result.message
            stepResults.put("RESULT_" + step, result.content);
            
            // NOVO LOG: Conteúdo da ferramenta
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
            
            results.append(String.format("Step %d (%s): %s\n", 
                         step, tool.getName(), result.message));
            step++;
        }
        

        
        return generateConsolidatedResponse(query, results.toString());
    }
    
    /**
     * Ordena as ferramentas por nível de dependência baseado nos placeholders {{RESULT_X}}.
     * Ferramentas sem dependências (nível 0) executam primeiro, seguidas pelas que dependem
     * de RESULT_1, depois RESULT_2, etc.
     */
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
    
    /**
     * Extrai o nível de dependência analisando placeholders {{RESULT_X}} nos parâmetros.
     * Retorna o maior número X encontrado, ou 0 se não há dependências.
     */
    private int extractDependencyLevel(Map<String, Object> parameters) {
        int maxLevel = 0;
        
        for (Object value : parameters.values()) {
            if (value instanceof String) {
                String strValue = (String) value;
                // Regex para encontrar {{RESULT_X}} onde X é um número
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
                // Resolver referências {{RESULT_N}}
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
        String prompt = String.format(
            "Based on the tool execution result, provide a comprehensive response:\n\n" +
            "Original query: %s\n" +
            "Tool used: %s\n" +
            "Result: %s\n\n" +
            "Provide a natural, always in the same languege of the orinal query, helpful response incorporating the tool result.",
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
            "Provide a consolidated, helpful summary always in the same languege of the orinal query.",
            query, results
        );
        
        LlmResponse response = llm.generateResponse(prompt);
        return response.isSuccess() ? response.getContent() 
                                    : "Multi-step execution completed:\n" + results;
    }
    
    public boolean isMultiStep(String query, Llm llm) {
        if (llm == null) return false;
        
        String prompt = "Analise a query e determine se a sua execução exige uma ou mais ferramentas.\n\n" +
                "Para fazer essa avaliação, procure por:\n" +
                "1. **Verbos ou Ações Múltiplas:** Identifique se a query contém múltiplos verbos que implicam ações distintas (ex: \"criar\" e \"mover\", \"pesquisar\" e \"enviar\").\n" +
                "2. **Conjunções e Conectores:** Procure por palavras como \"e\", \"ou\", \"então\", \"depois\" ou \"além disso\", que conectam diferentes partes da solicitação.\n" +
                "3. **Dependências:** Verifique se uma tarefa depende da conclusão de outra (ex: primeiro encontrar um dado e só então usá-lo em outra ação).\n\n" +
                "Com base nessa análise, responda de forma clara e objetiva se a query requer uma única ferramenta ou múltiplas.\n" +
                "Responda apenas com `true` ou `false`.\n\n" +
                "Query: " + query;
        
        var response = llm.generateResponse(prompt);
        
        return response.isSuccess() && response.getContent().toLowerCase().contains("true");
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
        logger.debug("[SIMPLE] Inference strategy closed - logs salvos em JavaCLI/log/inference/");
    }
}
