package com.gazapps.llm;

import java.util.List;

import com.gazapps.llm.tool.ToolDefinition;

import io.modelcontextprotocol.spec.McpSchema.Tool;

public interface Llm {

	LlmResponse generateResponse(String prompt);

	LlmResponse generateWithTools(String prompt, List<ToolDefinition> tools);

	List<ToolDefinition> convertMcpTools(List<Tool> mcpTools);

	LlmProvider getProviderName();

	LlmCapabilities getCapabilities();

	boolean isHealthy();

}
