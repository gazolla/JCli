package com.gazapps.mcp;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gazapps.llm.Llm;
import com.gazapps.mcp.domain.Tool;
import com.gazapps.mcp.matching.SemanticMatcher;

/**
 * Coordenador de estratégias de matching de ferramentas que utiliza uma
 * abordagem híbrida, combinando matching rápido baseado em padrões com
 * análise semântica profunda via LLM para garantir resultados precisos e relevantes.
 */
public class ToolMatcher {

    private static final Logger logger = LoggerFactory.getLogger(ToolMatcher.class);

    private final SemanticMatcher semanticMatcher;

    public ToolMatcher() {
        this.semanticMatcher = new SemanticMatcher();
    }

    /**
     * Encontra ferramentas relevantes para uma query usando uma abordagem híbrida.
     *
     * @param query A query do usuário
     * @param llm A instância do LLM para processamento semântico
     * @param availableTools A lista de ferramentas disponíveis
     * @param options As opções de matching
     * @return Uma lista de ferramentas relevantes
     */
    public List<Tool> findRelevantTools(String query, Llm llm, List<Tool> availableTools, MCPManager.MatchingOptions options) {
        if (query == null || query.trim().isEmpty() || availableTools.isEmpty()) {
            return Collections.emptyList();
        }

        // Usar sempre matching semântico quando LLM disponível
        if (options.useSemanticMatching && llm != null) {
            try {
                return semanticMatcher.findSemanticMatches(query, availableTools, llm);
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
    public Map<Tool, Map<String, Object>> findRelevantToolsWithParams(String query, Llm llm, List<Tool> availableTools, MCPManager.MatchingOptions options) {
        if (query == null || query.trim().isEmpty() || availableTools.isEmpty()) {
            return Collections.emptyMap();
        }

        // Usar sempre matching semântico quando LLM disponível
        if (options.useSemanticMatching && llm != null) {
            try {
                return semanticMatcher.findSemanticMatchesWithParams(query, availableTools, llm);
            } catch (Exception e) {
                logger.error("Erro durante o matching semântico com parâmetros", e);
                return Collections.emptyMap();
            }
        }

        // Fallback: converter matching básico para Map
        List<Tool> basicMatches = findBasicMatches(query, availableTools);
        Map<Tool, Map<String, Object>> result = new HashMap<>();
        for (Tool tool : basicMatches) {
            result.put(tool, Collections.emptyMap());
        }
        return result;
    }
    
    /**
     * Encontra múltiplas ferramentas relevantes com parâmetros extraídos (para multi-step).
     */
    public Map<Tool, Map<String, Object>> findMultipleToolsWithParams(String query, Llm llm, List<Tool> availableTools, MCPManager.MatchingOptions options) {
        if (query == null || query.trim().isEmpty() || availableTools.isEmpty()) {
            return Collections.emptyMap();
        }

        if (llm != null) {
            try {
                return semanticMatcher.findMultipleSemanticMatchesWithParams(query, availableTools, llm);
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

    private static class ToolMatch {
        final Tool tool;
        final double score;

        ToolMatch(Tool tool, double score) {
            this.tool = tool;
            this.score = score;
        }
    }
}
