package com.gazapps.mcp.matching;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gazapps.llm.Llm;
import com.gazapps.llm.LlmResponse;
import com.gazapps.mcp.domain.Tool;
import com.gazapps.mcp.rules.RuleEngine;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Implementa a lógica de matching semântico de ferramentas usando um LLM. Esta
 * classe é responsável por analisar a intenção de uma query e encontrar as
 * ferramentas mais relevantes com base em seu significado, não apenas em
 * palavras-chave.
 */
public class SemanticMatcher {

	private static final Logger logger = LoggerFactory.getLogger(SemanticMatcher.class);
	private final RuleEngine ruleEngine;

	public SemanticMatcher(RuleEngine ruleEngine) {
		this.ruleEngine = ruleEngine;
	}

	/**
	 * Encontra correspondências semânticas para uma query dentro de uma lista de
	 * ferramentas.
	 *
	 * @param query A query do usuário
	 * @param tools A lista de ferramentas para analisar
	 * @param llm   A instância do LLM
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
			prompt.append(i + 1).append(". ").append(tool.getName()).append(" - ").append(tool.getDescription())
					.append(" (domain: ").append(tool.getDomain()).append(")\n");
		}

		prompt.append("\nResponda apenas com os números das ferramentas relevantes, separados por vírgula.");
		prompt.append("\nExemplo: 1,3");

		// Apply rules if available
		if (ruleEngine != null && ruleEngine.isEnabled()) {
			String serverName = inferServerFromTools(tools);
			List<String> parameters = extractParameterNames(tools);
			return ruleEngine.enhancePrompt(prompt.toString(), serverName, parameters);
		}

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

	/**
	 * Encontra múltiplas correspondências semânticas com extração de parâmetros
	 * (multi-tool).
	 */
	public Map<Tool, Map<String, Object>> findMultipleSemanticMatchesWithParams(String query, List<Tool> tools,
			Llm llm) {
		if (query == null || query.trim().isEmpty() || tools.isEmpty() || llm == null) {
			return Collections.emptyMap();
		}

		try {
			String prompt = createMultiToolPrompt(query, tools);
			LlmResponse response = llm.generateResponse(prompt);

			if (response.isSuccess()) {
				return parseMultiToolSelection(response.getContent(), tools, query);
			}

		} catch (Exception e) {
			logger.error("Erro no matching multi-tool", e);
		}

		return Collections.emptyMap();
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
		if (ruleEngine != null && ruleEngine.isEnabled()) {
			String serverName = inferServerFromTools(tools);
			List<String> parameters = extractParameterNames(tools);
			return ruleEngine.enhancePrompt(basePrompt, serverName, parameters);
		}

		return basePrompt;
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
				Map<String, Object> parameters = parseJsonToMap(paramsNode, selectedTool);

				return Map.of(selectedTool, parameters);
			}

		} catch (Exception e) {
			logger.error("Erro ao fazer parse da seleção JSON", e);
		}

		return Collections.emptyMap();
	}

	private Map<String, Object> parseJsonToMap(JsonNode paramsNode, Tool tool) {
		Map<String, Object> parameters = new HashMap<>();
		if (paramsNode != null && paramsNode.isObject()) {
			Map<String, Object> properties = tool.getProperties();

			paramsNode.fields().forEachRemaining(entry -> {
				String paramName = entry.getKey();
				JsonNode valueNode = entry.getValue();

				// Type casting baseado no schema da ferramenta
				String expectedType = getParamTypeFromProperties(paramName, properties);
				Object typedValue = convertJsonValueToType(valueNode, expectedType);

				parameters.put(paramName, typedValue);
			});
		}
		return parameters;
	}

	private String getParamTypeFromProperties(String paramName, Map<String, Object> properties) {
		if (properties.containsKey(paramName)) {
			@SuppressWarnings("unchecked")
			Map<String, Object> paramDef = (Map<String, Object>) properties.get(paramName);
			String type = (String) paramDef.get("type");
			return type != null ? type : "string";
		}
		return "string"; // default type
	}

	private Object convertJsonValueToType(JsonNode valueNode, String expectedType) {
		if (valueNode == null || valueNode.isNull()) {
			return null;
		}

		try {
			switch (expectedType) {
			case "number":
				return valueNode.isNumber() ? valueNode.asDouble() : Double.parseDouble(valueNode.asText());
			case "integer":
				return valueNode.isInt() ? valueNode.asInt() : Integer.parseInt(valueNode.asText());
			case "boolean":
				return valueNode.isBoolean() ? valueNode.asBoolean() : Boolean.parseBoolean(valueNode.asText());
			case "array":
				if (valueNode.isArray()) {
					List<Object> list = new ArrayList<>();
					valueNode.forEach(node -> list.add(node.asText()));
					return list;
				} else {
					// Fallback: split string por vírgula
					return Arrays.asList(valueNode.asText().split(","));
				}
			default:
				return valueNode.asText(); // String ou tipo desconhecido
			}
		} catch (Exception e) {
			// Fallback para String se conversão falhar
			return valueNode.asText();
		}
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
		if (ruleEngine != null && ruleEngine.isEnabled()) {
			String serverName = inferServerFromTools(tools);
			List<String> parameters = extractParameterNames(tools);
			return ruleEngine.enhancePrompt(basePrompt, serverName, parameters);
		}

		return basePrompt;
	}

	private Map<Tool, Map<String, Object>> parseMultiToolSelection(String llmResponse, List<Tool> tools,
			String originalQuery) {
		try {
			ObjectMapper mapper = new ObjectMapper();
			String jsonString = llmResponse.replaceAll("```json|```", "").trim();
			JsonNode json = mapper.readTree(jsonString);
			JsonNode toolsArray = json.get("tools");

			Map<Tool, Map<String, Object>> result = new LinkedHashMap<>(); // Manter ordem

			if (toolsArray.isArray()) {
				for (JsonNode toolNode : toolsArray) {
					int toolNumber = toolNode.get("tool_number").asInt();
					JsonNode paramsNode = toolNode.get("parameters");

					if (toolNumber > 0 && toolNumber <= tools.size()) {
						Tool selectedTool = tools.get(toolNumber - 1);
						Map<String, Object> parameters = parseJsonToMap(paramsNode, selectedTool);

						result.put(selectedTool, parameters);
					}
				}
			}

			return result;

		} catch (Exception e) {
			logger.error("Erro ao fazer parse da seleção multi-tool JSON", e);
		}

		return Collections.emptyMap();
	}

	private String inferServerFromTools(List<Tool> tools) {
		if (tools == null || tools.isEmpty()) {
			return null;
		}
		// Usa o domínio da primeira ferramenta como aproximação do servidor
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

}
