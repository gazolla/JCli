package com.gazapps.inference.reflection;

import java.util.Map;

import com.gazapps.inference.Inference;
import com.gazapps.inference.InferenceStrategy;
import com.gazapps.llm.Llm;
import com.gazapps.mcp.MCPManager;

public class Reflection implements Inference {

    private final MCPManager mcpManager;
    private final Llm llm;
    private final Map<String, Object> options;

    public Reflection(MCPManager mcpManager, Llm llm, int maxIterations, boolean debug) {
        this.mcpManager = mcpManager;
        this.llm = llm;
        this.options = Map.of("maxIterations", maxIterations, "debug", debug);
    }

    public Reflection(MCPManager mcpManager, Llm llm, Map<String, Object> options) {
        this.mcpManager = mcpManager;
        this.llm = llm;
        this.options = options;
    }

    @Override
    public String processQuery(String query) {
        // TODO: Implementar lógica Reflection
        return null;
    }

    @Override
    public String buildSystemPrompt() {
        // TODO: Implementar lógica
        return null;
    }

    @Override
    public InferenceStrategy getStrategyName() {
        return InferenceStrategy.REFLECTION;
    }
}
