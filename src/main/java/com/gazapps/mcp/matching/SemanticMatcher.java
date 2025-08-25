package com.gazapps.mcp.matching;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gazapps.llm.Llm;
import com.gazapps.llm.LlmResponse;
import com.gazapps.mcp.domain.Tool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Implementa a lógica de matching semântico de ferramentas usando um LLM.
 * Esta classe é responsável por analisar a intenção de uma query e encontrar as
 * ferramentas mais relevantes com base em seu significado, não apenas em palavras-chave.
 */
public class SemanticMatcher {
    
    private static final Logger logger = LoggerFactory.getLogger(SemanticMatcher.class);

    /**
     * Encontra correspondências semânticas para uma query dentro de uma lista de ferramentas.
     *
     * @param query A query do usuário
     * @param tools A lista de ferramentas para analisar
     * @param llm A instância do LLM
     * @return Uma lista de ferramentas que correspondem semanticamente à query
     */
    public List<Tool> findSemanticMatches(String query, List<Tool> tools, Llm llm) {
        if (query == null || query.trim().isEmpty() || tools.isEmpty() || llm == null) {
            return Collections.emptyList();
        }

        try {
            String prompt = createToolMatchingPrompt(query, tools);
            LlmResponse response = llm.generateResponse(prompt);
            
            if (response.isSuccess()) {
                return parseToolSelection(response.getContent(), tools);
            }
            
        } catch (Exception e) {
            logger.error("Erro no matching semântico", e);
        }
        
        return Collections.emptyList();
    }
    
    private String createToolMatchingPrompt(String query, List<Tool> tools) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Analise a query e selecione as ferramentas mais relevantes:\n\n");
        prompt.append("Query: \"").append(query).append("\"\n\n");
        prompt.append("Ferramentas disponíveis:\n");
        
        for (int i = 0; i < tools.size(); i++) {
            Tool tool = tools.get(i);
            prompt.append(i + 1).append(". ").append(tool.getName())
                  .append(" - ").append(tool.getDescription())
                  .append(" (domain: ").append(tool.getDomain()).append(")\n");
        }
        
        prompt.append("\nResponda apenas com os números das ferramentas relevantes, separados por vírgula.");
        prompt.append("\nExemplo: 1,3");
        
        return prompt.toString();
    }
    
    private List<Tool> parseToolSelection(String llmResponse, List<Tool> tools) {
        if (llmResponse == null || llmResponse.trim().isEmpty()) {
            return Collections.emptyList();
        }
        
        List<Tool> selectedTools = new ArrayList<>();
        String[] indices = llmResponse.trim().split("[,\\s]+");
        
        for (String indexStr : indices) {
            try {
                int index = Integer.parseInt(indexStr.trim()) - 1;
                if (index >= 0 && index < tools.size()) {
                    selectedTools.add(tools.get(index));
                }
            } catch (NumberFormatException e) {
                // Ignore invalid numbers
            }
        }
        
        return selectedTools;
    }
    
    /**
     * Encontra correspondências semânticas com extração de parâmetros.
     */
    public Map<Tool, Map<String, Object>> findSemanticMatchesWithParams(String query, List<Tool> tools, Llm llm) {
        if (query == null || query.trim().isEmpty() || tools.isEmpty() || llm == null) {
            return Collections.emptyMap();
        }

        try {
            String prompt = createEnhancedPrompt(query, tools);
            LlmResponse response = llm.generateResponse(prompt);
            
            if (response.isSuccess()) {
                return parseToolSelectionWithParams(response.getContent(), tools);
            }
            
        } catch (Exception e) {
            logger.error("Erro no matching semântico com parâmetros", e);
        }
        
        return Collections.emptyMap();
    }
    
    private String createEnhancedPrompt(String query, List<Tool> tools) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Analise a query e selecione a ferramenta mais relevante com seus parâmetros:\n\n");
        prompt.append("Query: \"").append(query).append("\"\n\n");
        
        for (int i = 0; i < tools.size(); i++) {
            Tool tool = tools.get(i);
            prompt.append(i + 1).append(". ").append(tool.getName())
                  .append(" - ").append(tool.getDescription()).append("\n");
            
            // Usar schema da Tool existente
            if (!tool.getRequiredParams().isEmpty() || !tool.getOptionalParams().isEmpty()) {
                prompt.append("   Parâmetros:\n");
                
                for (String param : tool.getRequiredParams()) {
                    prompt.append("   - ").append(param)
                          .append(" (obrigatório): ");
                    Object paramSchema = tool.getSchema().get(param);
                    if (paramSchema != null) {
                        prompt.append(paramSchema.toString());
                    }
                    prompt.append("\n");
                }
                
                for (String param : tool.getOptionalParams()) {
                    prompt.append("   - ").append(param)
                          .append(" (opcional): ");
                    Object paramSchema = tool.getSchema().get(param);
                    if (paramSchema != null) {
                        prompt.append(paramSchema.toString());
                    }
                    prompt.append("\n");
                }
            }
            prompt.append("\n");
        }
        
        prompt.append("Responda em JSON:\n");
        prompt.append("{\n  \"tool_number\": 1,\n  \"parameters\": {\"param\": \"value\"}\n}");
        
        return prompt.toString();
    }
    
    private Map<Tool, Map<String, Object>> parseToolSelectionWithParams(String llmResponse, List<Tool> tools) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            String jsonString = llmResponse.replaceAll("```json|```", "").trim();
            JsonNode json = mapper.readTree(jsonString);
            
            int toolNumber = json.get("tool_number").asInt();
            JsonNode paramsNode = json.get("parameters");
            
            if (toolNumber > 0 && toolNumber <= tools.size()) {
                Tool selectedTool = tools.get(toolNumber - 1);
                Map<String, Object> parameters = parseJsonToMap(paramsNode);
                
                return Map.of(selectedTool, parameters);
            }
            
        } catch (Exception e) {
            logger.error("Erro ao fazer parse da seleção JSON", e);
        }
        
        return Collections.emptyMap();
    }
    
    private Map<String, Object> parseJsonToMap(JsonNode paramsNode) {
        Map<String, Object> parameters = new HashMap<>();
        if (paramsNode != null && paramsNode.isObject()) {
            paramsNode.fields().forEachRemaining(entry -> {
                parameters.put(entry.getKey(), entry.getValue().asText());
            });
        }
        return parameters;
    }
}
