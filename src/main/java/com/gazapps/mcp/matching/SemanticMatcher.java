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

public class SemanticMatcher {

	private static final Logger logger = LoggerFactory.getLogger(SemanticMatcher.class);
	private final RuleEngine ruleEngine;

	public SemanticMatcher(RuleEngine ruleEngine) {
		this.ruleEngine = ruleEngine;
	}
	
	public RuleEngine getRuleEngine() {
		return ruleEngine;
	}

	// REMOVIDO: findSemanticMatches() - duplicado com ToolMatcher.findRelevantTools()

	// REMOVIDO: createToolMatchingPrompt() - movido para ToolMatcher

	public List<Tool> parseToolSelection(String llmResponse, List<Tool> tools) {
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

	// REMOVIDO: findSemanticMatchesWithParams() - duplicado com ToolMatcher.findRelevantToolsWithParams()

	// REMOVIDO: findMultipleSemanticMatchesWithParams() - duplicado com ToolMatcher.findMultipleToolsWithParams()

	// REMOVIDO: createEnhancedPrompt() - movido para ToolMatcher

	public Map<Tool, Map<String, Object>> parseToolSelectionWithParams(String llmResponse, List<Tool> tools) {
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

	// REMOVIDO: createMultiToolPrompt() - movido para ToolMatcher

	public Map<Tool, Map<String, Object>> parseMultiToolSelection(String llmResponse, List<Tool> tools,
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

	// REMOVIDO: inferServerFromTools() - movido para ToolMatcher
	// REMOVIDO: extractParameterNames() - movido para ToolMatcher

}
