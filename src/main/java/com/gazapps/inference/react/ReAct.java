package com.gazapps.inference.react;

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

public class ReAct implements Inference {
    
    private static final Logger logger = LoggerFactory.getLogger(ReAct.class);
    private static final Logger conversationLogger = LoggerFactory.getLogger("com.gazapps.inference.react.ReAct.conversations");
    
    private final MCPManager mcpManager;
    private final Llm llm;
    private final Map<String, Object> options;
    private final int maxIterations;
    private final boolean debug;
    private InferenceObserver observer;
    
    // Cache simples para tool selection
    private String cachedQuery;
    private Map<Tool, Map<String, Object>> cachedTools;
    private String lastToolResult = ""; // Armazenar resultado da ferramenta anterior

    public ReAct(MCPManager mcpManager, Llm llm, Map<String, Object> options) {
        this.mcpManager = Objects.requireNonNull(mcpManager, "MCPManager is required");
        this.llm = Objects.requireNonNull(llm, "Llm is required");
        this.options = Objects.requireNonNull(options, "Options is required");
        this.maxIterations = (Integer) options.getOrDefault("maxIterations", 5);
        this.debug = (Boolean) options.getOrDefault("debug", false);
        this.observer = (InferenceObserver) options.get("observer");
        
        logger.info("[REACT] Initialized with LLM: {} - logs em JavaCLI/log/inference/react-conversations.log", llm.getProviderName());
    }

    @Override
    public String processQuery(String query) {
        logger.debug("Processing query with ReAct: {}", query);
        
        // Notificar observer do início
        if (observer != null) {
            observer.onInferenceStart(query, "ReAct");
        }
        
        if (conversationLogger.isInfoEnabled()) {
            conversationLogger.info("=== REACT INFERENCE START ===");
            conversationLogger.info("Query: {}", query);
            conversationLogger.info("Max iterations: {}", maxIterations);
        }
        
        try {
            ReActResult result = executeReActCycle(query);
            
            if (conversationLogger.isInfoEnabled()) {
                conversationLogger.info("=== REACT INFERENCE END ===");
                conversationLogger.info("Iterations completed: {}", result.iterations.size());
                conversationLogger.info("Final result: {}", result.finalAnswer);
                conversationLogger.info("===============================");
            }
            
            // Notificar observer da conclusão
            if (observer != null) {
                observer.onInferenceComplete(result.finalAnswer);
            }
            
            return result.finalAnswer;
            
        } catch (Exception e) {
            logger.error("Error processing query with ReAct", e);
            
            // Notificar observer do erro
            if (observer != null) {
                observer.onError("Error processing query with ReAct", e);
            }
            
            if (conversationLogger.isErrorEnabled()) {
                conversationLogger.error("=== REACT INFERENCE ERROR ===");
                conversationLogger.error("Error: {}", e.getMessage());
                conversationLogger.error("==============================");
            }
            return "Error processing query: " + e.getMessage();
        }
    }

    private ReActResult executeReActCycle(String query) {
        List<ReActStep> iterations = new ArrayList<>();
        String context = "Initial query: " + query;
        
        for (int i = 1; i <= maxIterations; i++) {
            if (conversationLogger.isInfoEnabled()) {
                conversationLogger.info("--- Iteration {} ---", i);
            }
            
            // THOUGHT
            String thought = generateThought(query, context);
            if (conversationLogger.isInfoEnabled()) {
                conversationLogger.info("Thought: {}", thought);
            }
            
            // Notificar observer do pensamento
            if (observer != null) {
                observer.onThought(thought);
            }
            
            // ACTION
            ActionDecision decision = decideAction(thought, query, context);
            if (conversationLogger.isInfoEnabled()) {
                conversationLogger.info("Action Decision: {}", decision.actionType);
            }
            
            // Check if final answer
            if ("FINAL_ANSWER".equals(decision.actionType)) {
                ReActStep finalStep = new ReActStep(thought, "Final Answer", decision.finalAnswer, true);
                iterations.add(finalStep);
                return new ReActResult(decision.finalAnswer, iterations);
            }
            
            // Execute action
            String actionResult = executeAction(decision);
            
            // OBSERVATION
            String observation = observeResult(actionResult, decision);
            if (conversationLogger.isInfoEnabled()) {
                conversationLogger.info("Observation: {}", observation);
            }
            
            ReActStep step = new ReActStep(thought, decision.actionType + " " + decision.toolName, observation, false);
            iterations.add(step);
            
            // Update context for next iteration
            context = buildContextForNextIteration(iterations, query);
            
            // Check if should continue
            if (!shouldContinue(iterations, i, query) || i >= 7) {
                break;
            }
        }
        
        // Generate final answer if max iterations reached
        String finalAnswer = generateFinalAnswer(query, iterations);
        return new ReActResult(finalAnswer, iterations);
    }

    private String generateThought(String query, String context) {
        String prompt = String.format(
            "Você é um assistente que usa o método ReAct (Reasoning and Acting).\n\n" +
            "Contexto atual:\n%s\n\n" +
            "PENSE sobre qual é o próximo passo para responder: \"%s\"\n\n" +
            "Analise:\n" +
            "- O que você já sabe?\n" +
            "- O que precisa descobrir?\n" +
            "- Qual ferramenta pode ajudar?\n\n" +
            "Responda apenas com seu raciocínio/pensamento.",
            context, query
        );
        
        LlmResponse response = llm.generateResponse(prompt);
        return response.isSuccess() ? response.getContent() : "Não consegui processar o pensamento.";
    }

    private ActionDecision decideAction(String thought, String query, String context) {
       
        boolean isMultiStep = mcpManager.isMultiStep(query, llm);
        

        Map<Tool, Map<String, Object>> availableTools;
        if (query.equals(cachedQuery) && cachedTools != null) {
            availableTools = cachedTools;
        } else {
            Optional<Map<Tool, Map<String, Object>>> toolsOptional = isMultiStep
                    ? mcpManager.findMultiStepTools(query)
                    : mcpManager.findSingleStepTools(query);
            
            availableTools = toolsOptional.orElse(Map.of());
            cachedQuery = query;
            cachedTools = availableTools;
        }

        if (availableTools.isEmpty()) {
            return new ActionDecision("FINAL_ANSWER", null, new HashMap<>(), 
                    "Nenhuma ferramenta relevante disponível - respondendo com conhecimento base.");
        }        
   
        
        String toolsInfo = buildToolsInfo(availableTools);

        boolean hasUsefulData = context.contains("Dados úteis coletados: 1") || context.contains("Dados úteis coletados: 2");
        boolean hasRepeatedTool = context.contains("(2x)") || context.contains("(3x)");
        
        String prompt;
        if (hasUsefulData || hasRepeatedTool) {
            // Se já tem dados ou repetiu ferramentas, ser mais provável a dar resposta final
            prompt = String.format(
                "Com base no pensamento: \"%s\"\n\n" +
                "Contexto: %s\n\n" +
                "Ferramentas disponíveis:\n%s\n\n" +
                "OBSERVAÇÃO: Você já coletou algumas informações ou já tentou ferramentas múltiplas vezes. " +
                "Considere se tem informação suficiente para responder.\n\n" +
                "Para a pergunta: \"%s\"\n" +
                "- Se tem informação suficiente para uma resposta útil, escolha: FINAL_ANSWER\n" +
                "- Se ainda precisa de dados específicos importantes, escolha: USE_TOOL\n\n" +
                "Responda no formato JSON:\n" +
                "{\n" +
                "  \"action\": \"USE_TOOL\" ou \"FINAL_ANSWER\",\n" +
                "  \"tool_name\": \"nome_da_ferramenta\" (se USE_TOOL),\n" +
                "  \"parameters\": {parâmetros} (se USE_TOOL),\n" +
                "  \"final_answer\": \"resposta\" (se FINAL_ANSWER)\n" +
                "}",
                thought, context, toolsInfo, query
            );
        } else {
            prompt = String.format(
                "Com base no pensamento: \"%s\"\n\n" +
                "Contexto: %s\n\n" +
                "Ferramentas disponíveis:\n%s\n\n" +
                "IMPORTANTE: Se há ferramentas disponíveis que podem executar a tarefa, você DEVE usar USE_TOOL.\n" +
                "APENAS use FINAL_ANSWER se não houver ferramentas relevantes ou se a tarefa já foi completada.\n\n" +
                "Para a pergunta: \"%s\"\n" +
                "- Se há ferramenta que pode executar a ação, escolha: USE_TOOL\n" +
                "- Apenas se não há ferramenta adequada, escolha: FINAL_ANSWER\n\n" +
                "Responda no formato JSON:\n" +
                "{\n" +
                "  \"action\": \"USE_TOOL\" ou \"FINAL_ANSWER\",\n" +
                "  \"tool_name\": \"nome_da_ferramenta\" (se USE_TOOL),\n" +
                "  \"parameters\": {parâmetros} (se USE_TOOL),\n" +
                "  \"final_answer\": \"resposta\" (se FINAL_ANSWER)\n" +
                "}",
                thought, context, toolsInfo, query
            );
        }
        
        LlmResponse response = llm.generateResponse(prompt);
        if (response.isSuccess()) {
            return parseActionDecision(response.getContent(), availableTools);
        }
        
        return new ActionDecision("FINAL_ANSWER", null, new HashMap<>(), "Erro ao decidir ação.");
    }

    private String executeAction(ActionDecision decision) {
        if (!"USE_TOOL".equals(decision.actionType)) {
            return decision.finalAnswer;
        }
        
        try {
            // Substituir placeholders nos parâmetros
            Map<String, Object> processedParams = processPlaceholders(decision.parameters);
            
            MCPService.ToolExecutionResult result = mcpManager.executeTool(decision.toolName, processedParams);
            
            // Armazenar resultado para próxima iteração
            if (result.success && result.content != null) {
                lastToolResult = result.content;
            }
            
            return result.toString();
        } catch (Exception e) {
            return "Erro ao executar ferramenta: " + e.getMessage();
        }
    }
    
    private Map<String, Object> processPlaceholders(Map<String, Object> parameters) {
        if (parameters == null) return new HashMap<>();
        
        Map<String, Object> processed = new HashMap<>();
        for (Map.Entry<String, Object> entry : parameters.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String) {
                String strValue = (String) value;
                // Substituir {{RESULT_1}} com resultado da ferramenta anterior
                if (strValue.contains("{{RESULT_1}}") && !lastToolResult.isEmpty()) {
                    strValue = strValue.replace("{{RESULT_1}}", lastToolResult);
                }
                processed.put(entry.getKey(), strValue);
            } else {
                processed.put(entry.getKey(), value);
            }
        }
        return processed;
    }

    private String observeResult(String actionResult, ActionDecision decision) {
        if ("FINAL_ANSWER".equals(decision.actionType)) {
            return "Resposta final fornecida.";
        }
        
        return String.format("Resultado da ferramenta %s: %s", decision.toolName, actionResult);
    }

    private boolean shouldContinue(List<ReActStep> iterations, int iteration, String originalQuery) {
        // Limite rígido: máximo 7 iterações
        if (iteration >= 7) {
            return false;
        }
        
        // Parar se já tem dados úteis suficientes
        int usefulDataCount = 0;
        Map<String, Integer> toolUsage = new HashMap<>();
        
        for (ReActStep step : iterations) {
            // Contar dados úteis
            if (classifyObservation(step.observation, originalQuery) == ObservationType.USEFUL_DATA) {
                usefulDataCount++;
            }
            
            // Contar uso de ferramentas
            if (step.action.startsWith("USE_TOOL")) {
                String[] parts = step.action.split(" ");
                if (parts.length > 1) {
                    String toolName = parts[1];
                    toolUsage.put(toolName, toolUsage.getOrDefault(toolName, 0) + 1);
                }
            }
        }
        
        // Parar se já coletou dados úteis suficientes
        if (usefulDataCount >= 2) {
            return false;
        }
        
        // Parar se alguma ferramenta foi usada 3+ vezes sem sucesso
        for (Integer count : toolUsage.values()) {
            if (count >= 3) {
                return false;
            }
        }
        
        // Parar se nas últimas 2 iterações só teve erros ou mensagens genéricas
        if (iterations.size() >= 2) {
            boolean hasRecentUsefulData = false;
            int startIndex = Math.max(0, iterations.size() - 2);
            
            for (int i = startIndex; i < iterations.size(); i++) {
                if (classifyObservation(iterations.get(i).observation, originalQuery) == ObservationType.USEFUL_DATA) {
                    hasRecentUsefulData = true;
                    break;
                }
            }
            
            if (!hasRecentUsefulData && iterations.size() >= 3) {
                return false; // Sem progresso recente
            }
        }
        
        return true;
    }

    private String buildContextForNextIteration(List<ReActStep> iterations, String query) {
        StringBuilder context = new StringBuilder();
        context.append("Query original: ").append(query).append("\n\n");
        
        // Adicionar resumo de progresso
        context.append(buildProgressSummary(iterations, query));
        context.append("\n");
        
        context.append("Histórico de iterações:\n");
        
        for (int i = 0; i < iterations.size(); i++) {
            ReActStep step = iterations.get(i);
            context.append(String.format("Iteração %d:\n", i + 1));
            context.append("Pensamento: ").append(step.thought).append("\n");
            context.append("Ação: ").append(step.action).append("\n");
            context.append("Observação: ").append(step.observation).append("\n\n");
        }
        
        return context.toString();
    }

    private String generateFinalAnswer(String query, List<ReActStep> iterations) {
        StringBuilder context = new StringBuilder();
        for (ReActStep step : iterations) {
            context.append("Ação: ").append(step.action).append("\n");
            context.append("Resultado: ").append(step.observation).append("\n");
        }
        
        String prompt = String.format(
            "Com base nas ações executadas, forneça uma resposta final para: \"%s\"\n\n" +
            "Informações coletadas:\n%s\n\n" +
            "Responda de forma clara e completa.",
            query, context.toString()
        );
        
        LlmResponse response = llm.generateResponse(prompt);
        return response.isSuccess() ? response.getContent() : "Não foi possível gerar resposta final.";
    }

    private String buildToolsInfo(Map<Tool, Map<String, Object>> tools) {
        if (tools.isEmpty()) {
            return "Nenhuma ferramenta disponível.";
        }
        
        StringBuilder info = new StringBuilder();
        for (Map.Entry<Tool, Map<String, Object>> entry : tools.entrySet()) {
            Tool tool = entry.getKey();
            Map<String, Object> params = entry.getValue();
            
            info.append(String.format("- %s: %s\n", tool.getName(), tool.getDescription()));
            if (!params.isEmpty()) {
                info.append("  Parâmetros sugeridos: ").append(params).append("\n");
            }
        }
        
        return info.toString();
    }

    private ActionDecision parseActionDecision(String jsonResponse, Map<Tool, Map<String, Object>> availableTools) {
        try {
            // Clean JSON response
            jsonResponse = jsonResponse.trim();
            if (jsonResponse.startsWith("```json")) {
                jsonResponse = jsonResponse.substring(7);
            }
            if (jsonResponse.endsWith("```")) {
                jsonResponse = jsonResponse.substring(0, jsonResponse.length() - 3);
            }
            jsonResponse = jsonResponse.trim();
            
            if (jsonResponse.contains("USE_TOOL")) {
                // Extract tool name
                String toolName = extractJsonValue(jsonResponse, "tool_name");
                if (toolName != null) {
                    // Find matching tool and use its parameters
                    for (Map.Entry<Tool, Map<String, Object>> entry : availableTools.entrySet()) {
                        if (entry.getKey().getName().equals(toolName)) {
                            return new ActionDecision("USE_TOOL", toolName, entry.getValue(), null);
                        }
                    }
                    // If tool not found in matched tools, try to extract parameters from JSON
                    Map<String, Object> extractedParams = extractParametersFromJson(jsonResponse);
                    return new ActionDecision("USE_TOOL", toolName, extractedParams, null);
                }
            }
            
            if (jsonResponse.contains("FINAL_ANSWER")) {
                int start = jsonResponse.indexOf("\"final_answer\":");
                if (start != -1) {
                    start = jsonResponse.indexOf("\"", start + 15);
                    int end = jsonResponse.indexOf("\"", start + 1);
                    if (start != -1 && end != -1) {
                        String answer = jsonResponse.substring(start + 1, end);
                        return new ActionDecision("FINAL_ANSWER", null, new HashMap<>(), answer);
                    }
                }
                return new ActionDecision("FINAL_ANSWER", null, new HashMap<>(), "Resposta final gerada.");
            }
            
        } catch (Exception e) {
            logger.debug("Error parsing action decision: {}", e.getMessage());
        }
        
        return new ActionDecision("FINAL_ANSWER", null, new HashMap<>(), "Erro ao interpretar decisão.");
    }

    // Novos métodos para detecção inteligente de progresso
    
    private ObservationType classifyObservation(String observation, String originalQuery) {
        if (observation == null) {
            return ObservationType.ERROR;
        }
        
        // Detectar erros explícitos
        String lower = observation.toLowerCase();
        if (lower.contains("erro") || lower.contains("error") || 
            lower.contains("failed") || lower.contains("falhou")) {
            return ObservationType.ERROR;
        }
        
        // Usar análise semântica via MCPManager
        try {
            boolean isUseful = mcpManager.isObservationUseful(observation, originalQuery);
            return isUseful ? ObservationType.USEFUL_DATA : ObservationType.GENERIC_SUCCESS;
        } catch (Exception e) {
            logger.debug("Erro na classificação semântica, usando fallback: {}", e.getMessage());
            
            // Fallback: se tem conteúdo substancial, pode ser útil
            if (observation.trim().length() > 50 && !lower.contains("executada com sucesso")) {
                return ObservationType.USEFUL_DATA;
            }
            
            return ObservationType.GENERIC_SUCCESS;
        }
    }
    
    private Map<String, String> extractKeyInformation(String observation) {
        Map<String, String> keyInfo = new HashMap<>();
        
        if (observation == null) {
            return keyInfo;
        }
        
        String lower = observation.toLowerCase();
        
        // Extrair temperatura
        java.util.regex.Pattern tempPattern = java.util.regex.Pattern.compile("(\\d+)[°]?\\s*[cf]");
        java.util.regex.Matcher tempMatcher = tempPattern.matcher(lower);
        if (tempMatcher.find()) {
            keyInfo.put("temperature", tempMatcher.group());
        }
        
        // Extrair condições climáticas
        String[] conditions = {"sunny", "cloudy", "rainy", "clear", "storm", "snow"};
        for (String condition : conditions) {
            if (lower.contains(condition)) {
                keyInfo.put("condition", condition);
                break;
            }
        }
        
        // Extrair localização se mencionada
        if (lower.contains("nyc") || lower.contains("new york")) {
            keyInfo.put("location", "NYC");
        }
        
        return keyInfo;
    }
    
    private int countToolUsage(String context, String toolName) {
        if (context == null || toolName == null) {
            return 0;
        }
        
        int count = 0;
        String[] lines = context.split("\\n");
        for (String line : lines) {
            if (line.contains("Ação: USE_TOOL " + toolName)) {
                count++;
            }
        }
        return count;
    }
    
    private boolean hasUsefulData(List<ReActStep> iterations, String originalQuery) {
        for (ReActStep step : iterations) {
            if (classifyObservation(step.observation, originalQuery) == ObservationType.USEFUL_DATA) {
                return true;
            }
        }
        return false;
    }
    
    private String buildProgressSummary(List<ReActStep> iterations, String originalQuery) {
        Map<String, Integer> toolUsage = new HashMap<>();
        int usefulDataCount = 0;
        int errorCount = 0;
        StringBuilder keyInfo = new StringBuilder();
        
        for (ReActStep step : iterations) {
            // Contar uso de ferramentas
            if (step.action.startsWith("USE_TOOL")) {
                String[] parts = step.action.split(" ");
                if (parts.length > 1) {
                    toolUsage.put(parts[1], toolUsage.getOrDefault(parts[1], 0) + 1);
                }
            }
            
            // Classificar observações
            ObservationType type = classifyObservation(step.observation, originalQuery);
            if (type == ObservationType.USEFUL_DATA) {
                usefulDataCount++;
                Map<String, String> info = extractKeyInformation(step.observation);
                for (Map.Entry<String, String> entry : info.entrySet()) {
                    keyInfo.append(entry.getKey()).append(": ").append(entry.getValue()).append(", ");
                }
            } else if (type == ObservationType.ERROR) {
                errorCount++;
            }
        }
        
        StringBuilder summary = new StringBuilder("Progresso até agora:\\n");
        summary.append("- Dados úteis coletados: ").append(usefulDataCount).append("\\n");
        summary.append("- Erros encontrados: ").append(errorCount).append("\\n");
        
        if (!toolUsage.isEmpty()) {
            summary.append("- Ferramentas utilizadas: ");
            for (Map.Entry<String, Integer> entry : toolUsage.entrySet()) {
                summary.append(entry.getKey()).append(" (").append(entry.getValue()).append("x), ");
            }
            summary.append("\\n");
        }
        
        if (keyInfo.length() > 0) {
            summary.append("- Informações coletadas: ").append(keyInfo.toString()).append("\\n");
        }
        
        return summary.toString();
    }
    
    private String extractJsonValue(String json, String key) {
        String pattern = "\"" + key + "\":";
        int start = json.indexOf(pattern);
        if (start == -1) return null;
        
        start = json.indexOf("\"", start + pattern.length());
        if (start == -1) return null;
        
        int end = json.indexOf("\"", start + 1);
        if (end == -1) return null;
        
        return json.substring(start + 1, end);
    }
    
    private Map<String, Object> extractParametersFromJson(String json) {
        Map<String, Object> params = new HashMap<>();
        
        try {
            // Look for parameters object
            int paramStart = json.indexOf("\"parameters\":");
            if (paramStart == -1) return params;
            
            int objectStart = json.indexOf("{", paramStart);
            if (objectStart == -1) return params;
            
            int objectEnd = json.indexOf("}", objectStart);
            if (objectEnd == -1) return params;
            
            String paramsJson = json.substring(objectStart + 1, objectEnd);
            
            // Simple parsing of key-value pairs
            String[] pairs = paramsJson.split(",");
            for (String pair : pairs) {
                String[] keyValue = pair.split(":");
                if (keyValue.length == 2) {
                    String key = keyValue[0].trim().replaceAll("\"", "");
                    String value = keyValue[1].trim().replaceAll("\"", "");
                    params.put(key, value);
                }
            }
            
        } catch (Exception e) {
            logger.debug("Error extracting parameters: {}", e.getMessage());
        }
        
        return params;
    }

    @Override
    public String buildSystemPrompt() {
        return "ReAct (Reasoning and Acting) inference strategy that combines reasoning with tool execution in iterative cycles.";
    }

    @Override
    public InferenceStrategy getStrategyName() {
        return InferenceStrategy.REACT;
    }

    @Override
    public void close() {
        logger.debug("[REACT] Inference strategy closed - logs salvos em JavaCLI/log/inference/");
    }

    // Inner classes
    
    // Enum para classificação de observações
    public enum ObservationType {
        USEFUL_DATA,    // Contém dados específicos úteis
        GENERIC_SUCCESS, // Apenas "ferramenta executada com sucesso"
        ERROR           // Erro na execução
    }
    
    // Resultado de uma observação com classificação
    public static class ObservationResult {
        public final String formattedText;
        public final ObservationType type;
        public final Map<String, String> keyInformation;
        
        public ObservationResult(String formattedText, ObservationType type, Map<String, String> keyInformation) {
            this.formattedText = formattedText;
            this.type = type;
            this.keyInformation = keyInformation;
        }
    }

    public static class ReActResult {
        public final String finalAnswer;
        public final List<ReActStep> iterations;

        public ReActResult(String finalAnswer, List<ReActStep> iterations) {
            this.finalAnswer = finalAnswer;
            this.iterations = iterations;
        }
    }

    public static class ReActStep {
        public final String thought;
        public final String action;
        public final String observation;
        public final boolean completed;

        public ReActStep(String thought, String action, String observation, boolean completed) {
            this.thought = thought;
            this.action = action;
            this.observation = observation;
            this.completed = completed;
        }
    }

    public static class ActionDecision {
        public final String actionType;
        public final String toolName;
        public final Map<String, Object> parameters;
        public final String finalAnswer;

        public ActionDecision(String actionType, String toolName, Map<String, Object> parameters, String finalAnswer) {
            this.actionType = actionType;
            this.toolName = toolName;
            this.parameters = parameters;
            this.finalAnswer = finalAnswer;
        }
    }
}
