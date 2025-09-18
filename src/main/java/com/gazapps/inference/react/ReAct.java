package com.gazapps.inference.react;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.gazapps.inference.Inference;
import com.gazapps.inference.InferenceObserver;
import com.gazapps.inference.InferenceStrategy;
import com.gazapps.inference.simple.QueryAnalysis;
import com.gazapps.llm.Llm;
import com.gazapps.llm.LlmResponse;
import com.gazapps.mcp.MCPManager;
import com.gazapps.mcp.MCPService;
import com.gazapps.mcp.domain.Tool;
import com.gazapps.session.Message;
import com.gazapps.session.ConversationContextBuilder;

public class ReAct implements Inference {
    
    private final MCPManager mcpManager;
    private final Llm llm;
    private final Map<String, Object> options;
    private final int maxIterations;
    private final boolean debug;
    private InferenceObserver observer;
    private final ReActLogger logger;
    
    private String cachedQuery;
    private Map<Tool, Map<String, Object>> cachedTools;
    private String lastToolResult = "";
    private List<Message> sessionContext = List.of(); 

    public ReAct(MCPManager mcpManager, Llm llm, Map<String, Object> options) {
        this.mcpManager = Objects.requireNonNull(mcpManager, "MCPManager is required");
        this.llm = Objects.requireNonNull(llm, "Llm is required");
        this.options = Objects.requireNonNull(options, "Options is required");
        this.maxIterations = (Integer) options.getOrDefault("maxIterations", 5);
        this.debug = (Boolean) options.getOrDefault("debug", false);
        this.observer = (InferenceObserver) options.get("observer");
        this.logger = new ReActLogger();
        
        logger.logDebug("[REACT] Initialized with LLM: {} - logs em JavaCLI/log/inference/react-conversations.log", llm.getProviderName());
    }

    @Override
    public String processQuery(String query) {
        return processQuery(query, List.of());
    }
    
    @Override
    public String processQuery(String query, List<Message> context) {
        this.sessionContext = context;
        logger.logDebug("Processing query with ReAct: {}", query);
        if (observer != null) {
            observer.onInferenceStart(query, "ReAct");
        }
        logger.logInferenceStart(query, maxIterations);
        
        try {
            ReActResult result = executeReActCycle(query);
            logger.logInferenceEnd(result.finalAnswer, result.iterations.size());
            if (observer != null) {
                observer.onInferenceComplete(result.finalAnswer);
            }
            return result.finalAnswer;
            
        } catch (Exception e) {
            logger.logError(e);
            if (observer != null) {
                observer.onError("Error processing query with ReAct", e);
            }
            return "Error processing query: " + e.getMessage();
        }
    }

    private ReActResult executeReActCycle(String query) {
        List<ReActStep> iterations = new ArrayList<>();
        String context = "Initial query: " + query;
        
        // Check if direct answer is possible
        QueryAnalysis initialAnalysis = mcpManager.analyzeQuery(query, llm);
        if (initialAnalysis.execution == QueryAnalysis.ExecutionType.DIRECT_ANSWER) {
            String directAnswer = generateDirectResponse(query, sessionContext);
            ReActStep directStep = new ReActStep(
                "This query can be answered with internal knowledge.", 
                "Direct Answer", 
                directAnswer, 
                true
            );
            iterations.add(directStep);
            return new ReActResult(directAnswer, iterations);
        }
        
        for (int i = 1; i <= maxIterations; i++) {
            logger.logIterationStart(i);
            
            String thought = generateThought(query, context, sessionContext);
            logger.logThought(thought);
            
            if (observer != null) {
                observer.onThought(thought);
            }
            
            ActionDecision decision = decideAction(thought, query, context);
            logger.logActionDecision(decision.actionType);
            
            if ("FINAL_ANSWER".equals(decision.actionType)) {
                ReActStep finalStep = new ReActStep(thought, "Final Answer", decision.finalAnswer, true);
                iterations.add(finalStep);
                return new ReActResult(decision.finalAnswer, iterations);
            }
            
            String actionResult = executeAction(decision);
            String observation = observeResult(actionResult, decision);
            logger.logObservation(observation);
            ReActStep step = new ReActStep(thought, decision.actionType + " " + decision.toolName, observation, false);
            iterations.add(step);
            context = buildContextForNextIteration(iterations, query);
             if (!shouldContinue(iterations, i, query) || i >= 7) {
                break;
            }
        }
        
        String finalAnswer = generateFinalAnswer(query, iterations);
        return new ReActResult(finalAnswer, iterations);
    }

    private String generateThought(String query, String context, List<Message> sessionContext) {
        String conversationalContext = ConversationContextBuilder.buildForReAct(sessionContext, 4);
        String fullContext = conversationalContext + context;
        
        String prompt = """
                You are an assistant that uses the ReAct (Reasoning and Acting) method.

                %s
                
                Current reasoning context:
                %s

                THINK about the next step to answer: "%s"

                Analyze:
                - What do you already know from previous conversation?
                - What do you need to find out?
                - Which tool can help?

                Respond only with your reasoning/thought.
                """.formatted(conversationalContext, context, query);
                
        LlmResponse response = llm.generateResponse(prompt);
        return response.isSuccess() ? response.getContent() : "Não consegui processar o pensamento.";
    }
    
    private String generateDirectResponse(String query, List<Message> context) {
        String contextPrompt = ConversationContextBuilder.buildForSimple(context, 3);
        String prompt = contextPrompt + "Answer the following question using your knowledge:\n\n" + query;
        
        LlmResponse response = llm.generateResponse(prompt);
        return response.isSuccess() ? response.getContent() 
                                    : "Failed to generate response: " + response.getErrorMessage();
    }

    private ActionDecision decideAction(String thought, String query, String context) {
        
        QueryAnalysis analysis = mcpManager.analyzeQuery(query, llm);
        
        // Handle direct answer capability 
        if (analysis.execution == QueryAnalysis.ExecutionType.DIRECT_ANSWER) {
            return new ActionDecision("FINAL_ANSWER", null, new HashMap<>(), 
                    "Posso responder com conhecimento interno sem ferramentas externas.");
        }

        Map<Tool, Map<String, Object>> availableTools;
        if (query.equals(cachedQuery) && cachedTools != null) {
            availableTools = cachedTools;
        } else {
            Optional<Map<Tool, Map<String, Object>>> toolsOptional = 
                (analysis.execution == QueryAnalysis.ExecutionType.MULTI_TOOL)
                    ? mcpManager.findMultiStepTools(query)
                    : mcpManager.findSingleStepTools(query);
            
            availableTools = toolsOptional.orElse(Map.of());
            cachedQuery = query;
            cachedTools = availableTools;
        }

        if (observer != null && !availableTools.isEmpty()) {
            var toolNames = availableTools.keySet().stream()
                .map(Tool::getName).toList();
            observer.onToolDiscovery(toolNames);
        }

        if (availableTools.isEmpty()) {
            return new ActionDecision("FINAL_ANSWER", null, new HashMap<>(), 
                    "Nenhuma ferramenta relevante disponível - respondendo com conhecimento base.");
        }        
   
        String toolsInfo = buildToolsInfo(availableTools);

        // Simplified decision logic
        String prompt = """
                Based on the thought: "%s"

                Context: %s

                Available tools:
                %s

                For the question: "%s"
                - If you have enough information for a useful response, choose: FINAL_ANSWER
                - If you need to use a tool to get more information, choose: USE_TOOL

                Respond in JSON format:
                {
                  "action": "USE_TOOL" or "FINAL_ANSWER",
                  "tool_name": "tool_name" (if USE_TOOL),
                  "parameters": {parameters} (if USE_TOOL),
                  "final_answer": "response" (if FINAL_ANSWER)
                }
                """.formatted(thought, context, toolsInfo, query);
        
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
            Map<String, Object> processedParams = processPlaceholders(decision.parameters);
            
            if (observer != null) {
                observer.onToolSelection(decision.toolName, processedParams);
            }
            
            MCPService.ToolExecutionResult result = mcpManager.executeTool(decision.toolName, processedParams);
            
            if (observer != null) {
                observer.onToolExecution(decision.toolName, result.message);
            }
            
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
         if (iteration >= 7) {
            return false;
        }
        
         int usefulDataCount = 0;
        Map<String, Integer> toolUsage = new HashMap<>();
        
        for (ReActStep step : iterations) {
            if (classifyObservation(step.observation, originalQuery) == ObservationType.USEFUL_DATA) {
                usefulDataCount++;
            }
            if (step.action.startsWith("USE_TOOL")) {
                String[] parts = step.action.split(" ");
                if (parts.length > 1) {
                    String toolName = parts[1];
                    toolUsage.put(toolName, toolUsage.getOrDefault(toolName, 0) + 1);
                }
            }
        }
        
        if (usefulDataCount >= 2) {
            return false;
        }
        
        for (Integer count : toolUsage.values()) {
            if (count >= 3) {
                return false;
            }
        }
        
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
                return false; 
            }
        }
        
        return true;
    }

    private String buildContextForNextIteration(List<ReActStep> iterations, String query) {
        StringBuilder context = new StringBuilder();
        context.append("Query original: ").append(query).append("\n\n");
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
        
        String prompt = """
        	    Based on the actions performed, provide a final response for: "%s"

        	    Collected information:
        	    %s

        	    Respond clearly and completely.
        	    """.formatted(query, context.toString());
        
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
            jsonResponse = jsonResponse.trim();
            if (jsonResponse.startsWith("```json")) {
                jsonResponse = jsonResponse.substring(7);
            }
            if (jsonResponse.endsWith("```")) {
                jsonResponse = jsonResponse.substring(0, jsonResponse.length() - 3);
            }
            jsonResponse = jsonResponse.trim();
            
            if (jsonResponse.contains("USE_TOOL")) {
                String toolName = extractJsonValue(jsonResponse, "tool_name");
                if (toolName != null) {
                     for (Map.Entry<Tool, Map<String, Object>> entry : availableTools.entrySet()) {
                        if (entry.getKey().getName().equals(toolName)) {
                            return new ActionDecision("USE_TOOL", toolName, entry.getValue(), null);
                        }
                    }
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
        	 logger.logParseActionError(e.getMessage());
        }
        
        return new ActionDecision("FINAL_ANSWER", null, new HashMap<>(), "Erro ao interpretar decisão.");
    }

    private ObservationType classifyObservation(String observation, String originalQuery) {
        if (observation == null) {
            return ObservationType.ERROR;
        }
        
        String lower = observation.toLowerCase();
        if (lower.contains("erro") || lower.contains("error") || 
            lower.contains("failed") || lower.contains("falhou")) {
            return ObservationType.ERROR;
        }
        try {
            boolean isUseful = mcpManager.isObservationUseful(observation, originalQuery);
            return isUseful ? ObservationType.USEFUL_DATA : ObservationType.GENERIC_SUCCESS;
        } catch (Exception e) {
            logger.logClassifyObservationError(e.getMessage());
            if (observation.trim().length() > 50 && !lower.contains("executada com sucesso")) {
                return ObservationType.USEFUL_DATA;
            }
            
            return ObservationType.GENERIC_SUCCESS;
        }
    }
    
    private String buildProgressSummary(List<ReActStep> iterations, String originalQuery) {
        Map<String, Integer> toolUsage = new HashMap<>();
        int usefulDataCount = 0;
        int errorCount = 0;
        
        for (ReActStep step : iterations) {
             if (step.action.startsWith("USE_TOOL")) {
                String[] parts = step.action.split(" ");
                if (parts.length > 1) {
                    toolUsage.put(parts[1], toolUsage.getOrDefault(parts[1], 0) + 1);
                }
            }
            
            ObservationType type = classifyObservation(step.observation, originalQuery);
            if (type == ObservationType.USEFUL_DATA) {
                usefulDataCount++;
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
           int paramStart = json.indexOf("\"parameters\":");
            if (paramStart == -1) return params;
            
            int objectStart = json.indexOf("{", paramStart);
            if (objectStart == -1) return params;
            
            int objectEnd = json.indexOf("}", objectStart);
            if (objectEnd == -1) return params;
            
            String paramsJson = json.substring(objectStart + 1, objectEnd);
            
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
            logger.logParseActionError(e.getMessage());
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
    	 logger.logDebug("[REACT] Inference strategy closed - logs salvos em JavaCLI/log/inference/");
    	 logger.logClose();
    }

    public enum ObservationType {
        USEFUL_DATA,    
        GENERIC_SUCCESS, 
        ERROR           
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
