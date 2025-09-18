package com.gazapps.inference.reflection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gazapps.inference.Inference;
import com.gazapps.inference.InferenceObserver;
import com.gazapps.inference.InferenceStrategy;
import com.gazapps.inference.simple.QueryAnalysis;
import com.gazapps.llm.Llm;
import com.gazapps.llm.LlmResponse;
import com.gazapps.mcp.MCPManager;
import com.gazapps.mcp.MCPService;
import com.gazapps.mcp.domain.Tool;
import com.gazapps.session.ConversationContextBuilder;
import com.gazapps.session.Message;

public class Reflection implements Inference {
    
    private static final Logger logger = LoggerFactory.getLogger(Reflection.class);
    private static final Logger conversationLogger = LoggerFactory.getLogger("com.gazapps.inference.reflection.Reflection.conversations");
    
    private final MCPManager mcpManager;
    private final Llm llm;
    private final Map<String, Object> options;
    private final int maxIterations;
    private final boolean debug;
    private InferenceObserver observer;

    public Reflection(MCPManager mcpManager, Llm llm, int maxIterations, boolean debug) {
        this.mcpManager = Objects.requireNonNull(mcpManager, "MCPManager is required");
        this.llm = Objects.requireNonNull(llm, "Llm is required");
        this.maxIterations = maxIterations;
        this.debug = debug;
        this.options = Map.of("maxIterations", maxIterations, "debug", debug);
        
        logger.info("[REFLECTION] Initialized with LLM: {} - logs em JavaCLI/log/inference/reflection-conversations.log", llm.getProviderName());
    }

    public Reflection(MCPManager mcpManager, Llm llm, Map<String, Object> options) {
        this.mcpManager = Objects.requireNonNull(mcpManager, "MCPManager is required");
        this.llm = Objects.requireNonNull(llm, "Llm is required");
        this.options = Objects.requireNonNull(options, "Options is required");
        this.maxIterations = (Integer) options.getOrDefault("maxIterations", 3);
        this.debug = (Boolean) options.getOrDefault("debug", false);
        this.observer = (InferenceObserver) options.get("observer");
        
        logger.info("[REFLECTION] Initialized with LLM: {} - logs em JavaCLI/log/inference/reflection-conversations.log", llm.getProviderName());
    }

    @Override
    public String processQuery(String query) {
        return processQuery(query, List.of());
    }
    
    @Override
    public String processQuery(String query, List<Message> context) {
        logger.debug("Processing query with Reflection: {}", query);
        
        if (observer != null) {
            observer.onInferenceStart(query, getStrategyName().name());
        }
        
        if (conversationLogger.isInfoEnabled()) {
            conversationLogger.info("=== REFLECTION INFERENCE START ===");
            conversationLogger.info("Query: {}", query);
            conversationLogger.info("Max iterations: {}", maxIterations);
        }
        
        try {
            ReflectionResult result = executeReflectionCycle(query, context);
            
            if (observer != null) {
                observer.onInferenceComplete(result.finalResponse);
            }
            
            if (conversationLogger.isInfoEnabled()) {
                conversationLogger.info("=== REFLECTION INFERENCE END ===");
                conversationLogger.info("Iterations completed: {}", result.iterations.size());
                conversationLogger.info("Final quality score: {}", result.finalQuality.overallScore);
                conversationLogger.info("Final result: {}", result.finalResponse);
                conversationLogger.info("====================================");
            }
            
            return result.finalResponse;
            
        } catch (Exception e) {
            logger.error("Error processing query with Reflection", e);
            if (observer != null) {
                observer.onError("Error processing query with Reflection", e);
            }
            if (conversationLogger.isErrorEnabled()) {
                conversationLogger.error("=== REFLECTION INFERENCE ERROR ===");
                conversationLogger.error("Error: {}", e.getMessage());
                conversationLogger.error("===================================");
            }
            return "Error processing query: " + e.getMessage();
        }
    }

    private ReflectionResult executeReflectionCycle(String query, List<Message> context) {
        List<String> iterations = new ArrayList<>();
        
        // Step 1: Generate initial response
        String currentResponse = generateInitialResponse(query, context);
        iterations.add("Initial: " + currentResponse);
        
        if (conversationLogger.isInfoEnabled()) {
            conversationLogger.info("--- Initial Response ---");
            conversationLogger.info("Response: {}", currentResponse);
        }
        
        // Step 2: Reflection cycle
        for (int i = 1; i <= maxIterations; i++) {
            if (conversationLogger.isInfoEnabled()) {
                conversationLogger.info("--- Reflection Iteration {} ---", i);
            }
            
            // Critique current response
            CritiqueResult critique = critique(currentResponse, query, context);
            
            if (conversationLogger.isInfoEnabled()) {
                conversationLogger.info("Critique issues: {}", critique.issues);
                conversationLogger.info("Critique suggestions: {}", critique.suggestions);
                conversationLogger.info("Needs improvement: {}", critique.needsImprovement);
            }
            
            // Check if improvement is needed
            if (!critique.needsImprovement || critique.confidenceScore > 0.8) {
                if (conversationLogger.isInfoEnabled()) {
                    conversationLogger.info("Response quality satisfactory. Stopping iteration.");
                }
                break;
            }
            
            // Refine response
            String refinedResponse = refineResponse(currentResponse, critique, query, context);
            iterations.add("Iteration " + i + ": " + refinedResponse);
            currentResponse = refinedResponse;
            
            if (conversationLogger.isInfoEnabled()) {
                conversationLogger.info("Refined response: {}", refinedResponse);
            }
        }
        
        // Step 3: Final quality assessment
        QualityMetrics finalQuality = evaluateResponseQuality(currentResponse, query);
        
        return new ReflectionResult(currentResponse, iterations, finalQuality);
    }

    private String generateInitialResponse(String query, List<Message> context) {
        // Use unified query analysis (DRY)
        QueryAnalysis analysis = mcpManager.analyzeQuery(query, llm);
        
        // Handle direct answer capability (KISS)
        if (analysis.execution == QueryAnalysis.ExecutionType.DIRECT_ANSWER) {
            return generateDirectResponse(query, context);
        }
        
        // Find relevant tools based on analysis
        Map<Tool, Map<String, Object>> relevantTools = findRelevantTools(query, analysis);
        
        if (!relevantTools.isEmpty()) {
            return generateToolBasedResponse(query, relevantTools);
        } else {
            return generateDirectResponse(query, context);
        }
    }

    private Map<Tool, Map<String, Object>> findRelevantTools(String query, QueryAnalysis analysis) {
        Optional<Map<Tool, Map<String, Object>>> toolsOptional = 
            (analysis.execution == QueryAnalysis.ExecutionType.MULTI_TOOL) 
                ? mcpManager.findMultiStepTools(query) 
                : mcpManager.findSingleStepTools(query);

        Map<Tool, Map<String, Object>> tools = toolsOptional.orElse(Map.of());
        
        // Observer notification for tools discovered
        if (observer != null && !tools.isEmpty()) {
            var toolNames = tools.keySet().stream()
                .map(Tool::getName).toList();
            observer.onToolDiscovery(toolNames);
        }
        
        return tools;
    }

    private String generateToolBasedResponse(String query, Map<Tool, Map<String, Object>> tools) {
        StringBuilder response = new StringBuilder();
        
        for (Map.Entry<Tool, Map<String, Object>> entry : tools.entrySet()) {
            Tool tool = entry.getKey();
            Map<String, Object> params = entry.getValue();
            
            try {
                if (observer != null) {
                    observer.onToolSelection(tool.getName(), params);
                }
                
                MCPService.ToolExecutionResult result = mcpManager.executeTool(tool, params);
                
                if (observer != null) {
                    observer.onToolExecution(tool.getName(), result.message);
                }
                
                if (result.success) {
                    response.append(result.message).append("\n");
                }
            } catch (Exception e) {
                logger.debug("Tool execution failed: {}", e.getMessage());
            }
        }
        
        if (response.length() == 0) {
            return generateDirectResponse(query, Collections.emptyList());
        }
        
        return synthesizeResponse(query, response.toString());
    }

    private String generateDirectResponse(String query, List<Message> context) {
        String contextPrompt = ConversationContextBuilder.buildForReflection(context, 6);
        String prompt = """
                %s
                
                Responda à seguinte pergunta de forma completa e precisa:
                %s
                
                Forneça uma resposta detalhada e bem estruturada considerando o contexto da conversa.
                """.formatted(contextPrompt, query);
        
        LlmResponse response = llm.generateResponse(prompt);
        return response.isSuccess() ? response.getContent() : "Não foi possível gerar resposta inicial.";
    }

    private String synthesizeResponse(String query, String toolResults) {
        String prompt = String.format(
            "Com base nos resultados das ferramentas, forneça uma resposta completa para: \"%s\"\n\n" +
            "Dados coletados:\n%s\n\n" +
            "Sintetize uma resposta clara, completa e bem estruturada.",
            query, toolResults
        );
        
        LlmResponse response = llm.generateResponse(prompt);
        return response.isSuccess() ? response.getContent() : toolResults;
    }

    private CritiqueResult critique(String response, String originalQuery, List<Message> context) {
        String contextPrompt = ConversationContextBuilder.buildForReflection(context, 6);
        String prompt = """
                %s
                
                Analise criticamente a seguinte resposta para a pergunta: "%s"
                
                Resposta a ser analisada:
                %s
                
                Avalie considerando o contexto da conversa:
                1. Completude: A resposta aborda todos os aspectos da pergunta?
                2. Precisão: A informação está correta?
                3. Clareza: A resposta é fácil de entender?
                4. Relevância: A resposta está focada na pergunta e contexto?
                
                Responda no formato:
                ISSUES: [lista de problemas encontrados]
                SUGGESTIONS: [lista de melhorias sugeridas]
                CONFIDENCE: [número de 0.0 a 1.0]
                NEEDS_IMPROVEMENT: [true/false]
                """.formatted(contextPrompt, originalQuery, response);
        
        LlmResponse llmResponse = llm.generateResponse(prompt);
        if (llmResponse.isSuccess()) {
            return parseCritiqueResult(llmResponse.getContent());
        }
        
        return new CritiqueResult(List.of("Erro na análise crítica"), List.of(), 0.5, false);
    }

    private String refineResponse(String originalResponse, CritiqueResult critique, String query, List<Message> context) {
        String contextPrompt = ConversationContextBuilder.buildForReflection(context, 4);
        StringBuilder prompt = new StringBuilder();
        prompt.append(contextPrompt);
        prompt.append(String.format("\nMelhore a seguinte resposta para: \"%s\"\n\n", query));
        prompt.append("Resposta original:\n").append(originalResponse).append("\n\n");
        
        if (!critique.issues.isEmpty()) {
            prompt.append("Problemas identificados:\n");
            for (String issue : critique.issues) {
                prompt.append("- ").append(issue).append("\n");
            }
            prompt.append("\n");
        }
        
        if (!critique.suggestions.isEmpty()) {
            prompt.append("Sugestões de melhoria:\n");
            for (String suggestion : critique.suggestions) {
                prompt.append("- ").append(suggestion).append("\n");
            }
            prompt.append("\n");
        }
        
        prompt.append("Forneça uma versão melhorada que resolva os problemas identificados e mantenha consistência com o contexto.");
        
        LlmResponse response = llm.generateResponse(prompt.toString());
        return response.isSuccess() ? response.getContent() : originalResponse;
    }

    private QualityMetrics evaluateResponseQuality(String response, String query) {
        String prompt = String.format(
            "Avalie a qualidade desta resposta para: \"%s\"\n\n" +
            "Resposta:\n%s\n\n" +
            "Dê notas de 0.0 a 1.0 para:\n" +
            "COMPLETENESS: [quão completa é a resposta]\n" +
            "ACCURACY: [quão precisa é a informação]\n" +
            "RELEVANCE: [quão relevante é para a pergunta]\n" +
            "CLARITY: [quão clara e compreensível é]",
            query, response
        );
        
        LlmResponse llmResponse = llm.generateResponse(prompt);
        if (llmResponse.isSuccess()) {
            return parseQualityMetrics(llmResponse.getContent());
        }
        
        return new QualityMetrics(0.7, 0.7, 0.7, 0.7);
    }

    private CritiqueResult parseCritiqueResult(String content) {
        List<String> issues = new ArrayList<>();
        List<String> suggestions = new ArrayList<>();
        double confidence = 0.5;
        boolean needsImprovement = false;
        
        try {
            String[] lines = content.split("\n");
            for (String line : lines) {
                line = line.trim();
                if (line.startsWith("ISSUES:")) {
                    String issuesStr = line.substring(7).trim();
                    if (!issuesStr.isEmpty() && !issuesStr.equals("[]") && !issuesStr.toLowerCase().contains("nenhum")) {
                        issues.add(issuesStr);
                    }
                } else if (line.startsWith("SUGGESTIONS:")) {
                    String suggestionsStr = line.substring(12).trim();
                    if (!suggestionsStr.isEmpty() && !suggestionsStr.equals("[]")) {
                        suggestions.add(suggestionsStr);
                    }
                } else if (line.startsWith("CONFIDENCE:")) {
                    try {
                        confidence = Double.parseDouble(line.substring(11).trim());
                    } catch (NumberFormatException e) {
                        confidence = 0.5;
                    }
                } else if (line.startsWith("NEEDS_IMPROVEMENT:")) {
                    needsImprovement = line.substring(18).trim().toLowerCase().contains("true");
                }
            }
        } catch (Exception e) {
            logger.debug("Error parsing critique result: {}", e.getMessage());
        }
        
        // If issues found, needs improvement
        if (!issues.isEmpty() || confidence < 0.7) {
            needsImprovement = true;
        }
        
        return new CritiqueResult(issues, suggestions, confidence, needsImprovement);
    }

    private QualityMetrics parseQualityMetrics(String content) {
        double completeness = 0.7;
        double accuracy = 0.7;
        double relevance = 0.7;
        double clarity = 0.7;
        
        try {
            String[] lines = content.split("\n");
            for (String line : lines) {
                line = line.trim();
                if (line.startsWith("COMPLETENESS:")) {
                    completeness = parseScore(line.substring(13));
                } else if (line.startsWith("ACCURACY:")) {
                    accuracy = parseScore(line.substring(9));
                } else if (line.startsWith("RELEVANCE:")) {
                    relevance = parseScore(line.substring(10));
                } else if (line.startsWith("CLARITY:")) {
                    clarity = parseScore(line.substring(8));
                }
            }
        } catch (Exception e) {
            logger.debug("Error parsing quality metrics: {}", e.getMessage());
        }
        
        return new QualityMetrics(completeness, accuracy, relevance, clarity);
    }

    private double parseScore(String scoreStr) {
        try {
            return Double.parseDouble(scoreStr.trim());
        } catch (NumberFormatException e) {
            return 0.7;
        }
    }

    @Override
    public String buildSystemPrompt() {
        return "Reflection inference strategy that iteratively improves responses through self-critique and refinement.";
    }

    @Override
    public InferenceStrategy getStrategyName() {
        return InferenceStrategy.REFLECTION;
    }

    @Override
    public void close() {
        logger.debug("[REFLECTION] Inference strategy closed - logs salvos em JavaCLI/log/inference/");
    }

    // Inner classes
    public static class ReflectionResult {
        public final String finalResponse;
        public final List<String> iterations;
        public final QualityMetrics finalQuality;

        public ReflectionResult(String finalResponse, List<String> iterations, QualityMetrics finalQuality) {
            this.finalResponse = finalResponse;
            this.iterations = iterations;
            this.finalQuality = finalQuality;
        }
    }

    public static class CritiqueResult {
        public final List<String> issues;
        public final List<String> suggestions;
        public final double confidenceScore;
        public final boolean needsImprovement;

        public CritiqueResult(List<String> issues, List<String> suggestions, double confidenceScore, boolean needsImprovement) {
            this.issues = issues;
            this.suggestions = suggestions;
            this.confidenceScore = confidenceScore;
            this.needsImprovement = needsImprovement;
        }
    }

    public static class QualityMetrics {
        public final double completeness;
        public final double accuracy;
        public final double relevance;
        public final double clarity;
        public final double overallScore;

        public QualityMetrics(double completeness, double accuracy, double relevance, double clarity) {
            this.completeness = completeness;
            this.accuracy = accuracy;
            this.relevance = relevance;
            this.clarity = clarity;
            this.overallScore = (completeness + accuracy + relevance + clarity) / 4.0;
        }
    }
}
