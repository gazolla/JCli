package com.gazapps.mcp.matching;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gazapps.llm.Llm;
import com.gazapps.llm.LlmResponse;
import com.gazapps.mcp.domain.Tool;
import com.gazapps.mcp.rules.RuleEngine;


public class ToolMatcher {

    private static final Logger logger = LoggerFactory.getLogger(ToolMatcher.class);

    private final SemanticMatcher semanticMatcher;
    
    // Cache para otimização de performance
    private final Map<String, Map<Tool, Map<String, Object>>> toolMatchCache = new ConcurrentHashMap<>();

    public ToolMatcher() {
        this.semanticMatcher = new SemanticMatcher(null);
    }

    public ToolMatcher(RuleEngine ruleEngine) {
        this.semanticMatcher = new SemanticMatcher(ruleEngine);
    }

     public List<Tool> findRelevantTools(String query, Llm llm, List<Tool> availableTools, MatchingOptions options) {
        if (query == null || query.trim().isEmpty() || availableTools.isEmpty()) {
            return Collections.emptyList();
        }

        if (options.useSemanticMatching && llm != null) {
            try {
                String prompt = createToolMatchingPrompt(query, availableTools);
                LlmResponse response = llm.generateResponse(prompt);
                
                if (response.isSuccess()) {
                    return semanticMatcher.parseToolSelection(response.getContent(), availableTools);
                }
            } catch (Exception e) {
                logger.error("Erro durante o matching semântico", e);
                return Collections.emptyList();
            }
        }

        // Fallback: matching básico apenas se semantic matching desabilitado
        return findBasicMatches(query, availableTools);
    }
    
    /**
     * Encontra ferramentas relevantes com parâmetros extraídos.
     */
    public Map<Tool, Map<String, Object>> findRelevantToolsWithParams(String query, Llm llm, List<Tool> availableTools, MatchingOptions options) {
        if (query == null || query.trim().isEmpty() || availableTools.isEmpty()) {
            return Collections.emptyMap();
        }

        // Cache key para evitar recálculos
        String cacheKey = query + "|" + availableTools.size() + "|" + options.hashCode();
        
        // Verificar cache primeiro
        Map<Tool, Map<String, Object>> cached = toolMatchCache.get(cacheKey);
        if (cached != null) {
            logger.debug("Cache hit para findRelevantToolsWithParams: {}", query);
            return cached;
        }

        Map<Tool, Map<String, Object>> result;
        
        // Usar sempre matching semântico quando LLM disponível
        if (options.useSemanticMatching && llm != null) {
            try {
                String prompt = createEnhancedPrompt(query, availableTools);
                LlmResponse response = llm.generateResponse(prompt);
                
                if (response.isSuccess()) {
                    result = semanticMatcher.parseToolSelectionWithParams(response.getContent(), availableTools);
                } else {
                    return Collections.emptyMap();
                }
            } catch (Exception e) {
                logger.error("Erro durante o matching semântico com parâmetros", e);
                return Collections.emptyMap();
            }
        } else {
            // Fallback: matching básico apenas se semantic matching desabilitado
            List<Tool> basicMatches = findBasicMatches(query, availableTools);
            result = new HashMap<>();
            for (Tool tool : basicMatches) {
                result.put(tool, Collections.emptyMap());
            }
        }
        
        // Armazenar no cache
        toolMatchCache.put(cacheKey, result);
        
        return result;
    }
    
    /**
     * Encontra múltiplas ferramentas relevantes com parâmetros extraídos (para multi-step).
     */
    public Map<Tool, Map<String, Object>> findMultipleToolsWithParams(String query, Llm llm, List<Tool> availableTools, MatchingOptions options) {
        if (query == null || query.trim().isEmpty() || availableTools.isEmpty()) {
            return Collections.emptyMap();
        }

        if (llm != null) {
            try {
                String prompt = createMultiToolPrompt(query, availableTools);
                LlmResponse response = llm.generateResponse(prompt);
                
                if (response.isSuccess()) {
                    return semanticMatcher.parseMultiToolSelection(response.getContent(), availableTools, query);
                }
            } catch (Exception e) {
                logger.error("Erro durante o matching multi-tool", e);
                return Collections.emptyMap();
            }
        }
        
        return Collections.emptyMap();
    }

    /**
     * Encontra a melhor ferramenta para uma query dentro de um domínio específico.
     *
     * @param query A query do usuário
     * @param domain O domínio para buscar a ferramenta
     * @param llm A instância do LLM
     * @param availableTools A lista de ferramentas disponíveis
     * @return A melhor ferramenta encontrada ou null
     */
    public Tool findBestTool(String query, String domain, Llm llm, List<Tool> availableTools) {
        // Esta é uma implementação simplificada. Uma versão mais robusta usaria o LLM
        // para determinar a "melhor" ferramenta baseada na intenção da query.
        List<Tool> domainTools = availableTools.stream()
                .filter(tool -> domain.equals(tool.getDomain()))
                .toList();

        if (domainTools.isEmpty()) {
            return null;
        }

        // Por enquanto, retorna a primeira ferramenta encontrada no domínio
        return domainTools.get(0);
    }

    // O método findBasicMatches foi movido do MCPManager para cá
    public List<Tool> findBasicMatches(String query, List<Tool> availableTools) {
        if (query == null || query.trim().isEmpty() || availableTools.isEmpty()) {
            return Collections.emptyList();
        }

        String normalizedQuery = query.toLowerCase().trim();
        List<ToolMatch> matches = new java.util.ArrayList<>();

        for (Tool tool : availableTools) {
            double score = calculateBasicScore(normalizedQuery, tool);
            if (score > 0.0) {
                matches.add(new ToolMatch(tool, score));
            }
        }

        matches.sort((a, b) -> Double.compare(b.score, a.score));

        return matches.stream().map(m -> m.tool).toList();
    }

    private double calculateBasicScore(String query, Tool tool) {
        double score = 0.0;

        if (tool.getName().toLowerCase().contains(query)) {
            score += 1.0;
        }
        if (tool.getDescription().toLowerCase().contains(query)) {
            score += 0.5;
        }
        if (tool.getDomain().toLowerCase().contains(query)) {
            score += 0.3;
        }

        String[] queryWords = query.split("\s+");
        for (String word : queryWords) {
            if (word.length() > 2) {
                if (tool.getName().toLowerCase().contains(word)) {
                    score += 0.2;
                }
                if (tool.getDescription().toLowerCase().contains(word)) {
                    score += 0.1;
                }
            }
        }

        return score;
    }
    
    /**
     * Avalia se uma observação contém dados úteis para responder à query original.
     * 
     * @param observation A observação a ser avaliada
     * @param originalQuery A query original do usuário
     * @param llm A instância do LLM para processamento semântico
     * @return true se a observação contém dados úteis, false caso contrário
     */
    public boolean evaluateObservationUtility(String observation, String originalQuery, Llm llm) {
        if (observation == null || originalQuery == null || llm == null) {
            return false;
        }
        
        if (observation.trim().isEmpty()) {
            return false;
        }
        
        try {
            String prompt = createObservationEvaluationPrompt(observation, originalQuery);
            LlmResponse response = llm.generateResponse(prompt);
            
            if (response.isSuccess()) {
                return parseUtilityEvaluation(response.getContent());
            }
            
        } catch (Exception e) {
            logger.error("Erro na avaliação de utilidade da observação", e);
        }
        
        return false;
    }
    
    private String createObservationEvaluationPrompt(String observation, String originalQuery) {
        return String.format(
            "Analise se esta observação contém dados úteis para responder à pergunta original.\n\n" +
            "Pergunta original: \"%s\"\n\n" +
            "Observação: \"%s\"\n\n" +
            "A observação contém informações específicas e úteis que ajudam a responder a pergunta?\n" +
            "Considere:\n" +
            "- Contém dados concretos (números, nomes, detalhes específicos)?\n" +
            "- É relevante para o contexto da pergunta?\n" +
            "- Não é apenas uma mensagem genérica de status?\n\n" +
            "Responda apenas: SIM ou NÃO",
            originalQuery, observation
        );
    }
    
    private boolean parseUtilityEvaluation(String llmResponse) {
        if (llmResponse == null) {
            return false;
        }
        
        String response = llmResponse.toLowerCase().trim();
        return response.contains("sim") || response.startsWith("yes");
    }
    
    // FASE 1: Métodos movidos de SemanticMatcher (PRIVADOS)
    
    private String createToolMatchingPrompt(String query, List<Tool> tools) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Analise a query e selecione as ferramentas mais relevantes:\n\n");
        prompt.append("Query: \"").append(query).append("\"\n\n");
        prompt.append("Ferramentas disponíveis:\n");

        for (int i = 0; i < tools.size(); i++) {
            Tool tool = tools.get(i);
            prompt.append(i + 1).append(". ").append(tool.getName()).append(" - ").append(tool.getDescription())
                    .append(" (domain: ").append(tool.getDomain()).append(")\n");
        }

        prompt.append("\nResponda apenas com os números das ferramentas relevantes, separados por vírgula.");
        prompt.append("\nExemplo: 1,3");

        // Apply rules if available
        if (semanticMatcher != null && semanticMatcher.getRuleEngine() != null && semanticMatcher.getRuleEngine().isEnabled()) {
            String serverName = inferServerFromTools(tools);
            List<String> parameters = extractParameterNames(tools);
            return semanticMatcher.getRuleEngine().enhancePrompt(prompt.toString(), serverName, parameters);
        }

        return prompt.toString();
    }
    
    private String createEnhancedPrompt(String query, List<Tool> tools) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("""
                Analise a query e selecione a ferramenta mais relevante.
                Se a ferramenta selecionada tiver parâmetros obrigatórios
                que não estão presentes na query, use seu conhecimento para
                encontrar as informações ausentes e preencha os parâmetros
                antes de retornar o JSON.

                """);
        prompt.append("Query: \"").append(query).append("\"\n\n");

        for (int i = 0; i < tools.size(); i++) {
            Tool tool = tools.get(i);
            prompt.append(i + 1).append(". ").append(tool.getName()).append(" - ").append(tool.getDescription())
                    .append("\n");

            Map<String, Object> properties = tool.getProperties();
            for (Map.Entry<String, Object> entry : properties.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                prompt.append("parameter: " + key + ", value: " + value + "\n");
            }
            prompt.append("\n");
        }

        prompt.append("NÃO EXPLIQUE NADA\n");
        prompt.append("Responda em JSON:\n");
        prompt.append("{\n  \"tool_number\": 1,\n  \"parameters\": {\"param\": \"value\"}\n}");

        // Apply rules if available
        String basePrompt = prompt.toString();
        if (semanticMatcher != null && semanticMatcher.getRuleEngine() != null && semanticMatcher.getRuleEngine().isEnabled()) {
            String serverName = inferServerFromTools(tools);
            List<String> parameters = extractParameterNames(tools);
            return semanticMatcher.getRuleEngine().enhancePrompt(basePrompt, serverName, parameters);
        }

        return basePrompt;
    }
    
    private String createMultiToolPrompt(String query, List<Tool> tools) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("""
                Analise a query e selecione somente as ferramentas necessárias para concluir a solicitação. 

                1. Planejamento: Identifique a sequência de ações para resolver a query.
                2. Encadeamento: A saída de uma ferramenta pode ser usada como entrada para 
                a próxima. Utilize "{{RESULT_X}}" (onde X é o número da ferramenta anterior) 
                como um placeholder para o conteúdo que será gerado dinamicamente.
                3. Parâmetros: Se as ferramentas selecionadas tiverem parâmetros obrigatórios que não 
                estão presentes na query, use seu conhecimento para encontrar as informações ausentes. 
                Se um parâmetro depende do resultado de outra ferramenta, use o placeholder.
                
                IMPORTANTE: 
                - Use sempre o mínimo de ferramentas necessárias.
                - Evite ferramentas redundantes
                
                """);
        prompt.append("Query: \"").append(query).append("\"\n\n");

        for (int i = 0; i < tools.size(); i++) {
            Tool tool = tools.get(i);
            prompt.append(i + 1).append(". ").append(tool.getName()).append(" - ").append(tool.getDescription())
                    .append("\n");

            Map<String, Object> properties = tool.getProperties();
            for (Map.Entry<String, Object> entry : properties.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                prompt.append("parameter: " + key + ", value: " + value + "\n");
            }
            prompt.append("\n");
        }

        prompt.append("NÃO EXPLIQUE NADA\n");
        prompt.append("Responda em JSON com TODAS as ferramentas necessárias:\n");
        prompt.append("{\n  \"tools\": [\n");
        prompt.append("    {\"tool_number\": 1, \"parameters\": {\"param\": \"value\"}},\n");
        prompt.append("    {\"tool_number\": 2, \"parameters\": {\"param\": \"value\"}}\n");
        prompt.append("  ]\n}");

        // Apply rules if available
        String basePrompt = prompt.toString();
        if (semanticMatcher != null && semanticMatcher.getRuleEngine() != null && semanticMatcher.getRuleEngine().isEnabled()) {
            String serverName = inferServerFromTools(tools);
            List<String> parameters = extractParameterNames(tools);
            return semanticMatcher.getRuleEngine().enhancePrompt(basePrompt, serverName, parameters);
        }

        return basePrompt;
    }
    
    // Helper methods também movidos de SemanticMatcher
    
    private String inferServerFromTools(List<Tool> tools) {
        if (tools == null || tools.isEmpty()) {
            return null;
        }
        String domain = tools.get(0).getDomain();
        if ("time".equals(domain)) {
            return "mcp-server-time";
        } else if ("filesystem".equals(domain)) {
            return "server-filesystem";
        } else if ("weather".equals(domain)) {
            return "server-weather";
        }
        return domain;
    }
    
    private List<String> extractParameterNames(List<Tool> tools) {
        List<String> parameters = new ArrayList<>();
        for (Tool tool : tools) {
            Map<String, Object> properties = tool.getProperties();
            if (properties != null) {
                parameters.addAll(properties.keySet());
            }
        }
        return parameters;
    }

    private static class ToolMatch {
        final Tool tool;
        final double score;

        ToolMatch(Tool tool, double score) {
            this.tool = tool;
            this.score = score;
        }
    }
}
