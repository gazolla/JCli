package com.gazapps.inference;

import java.util.HashMap;
import java.util.Map;

import com.gazapps.inference.react.ReAct;
import com.gazapps.inference.reflection.Reflection;
import com.gazapps.inference.simple.Simple;
import com.gazapps.llm.Llm;
import com.gazapps.mcp.MCPManager;

public final class InferenceFactory {
    
    public static Inference createReAct(MCPManager mcpManager, Llm llm, int maxIterations) {
        if (llm == null) {
            throw new IllegalArgumentException("Llm é obrigatório");
        }
        
        if (mcpManager == null) {
            throw new IllegalArgumentException("MCPManager é obrigatório");
        }
        
        return new ReAct(mcpManager, llm, Map.of("maxIterations", maxIterations, "debug", false));
    }
    
    public static Inference createReAct(MCPManager mcpManager, Llm llm, Map<String, Object> options) {
        if (llm == null) {
            throw new IllegalArgumentException("Llm é obrigatório");
        }
        
        if (mcpManager == null) {
            throw new IllegalArgumentException("MCPManager é obrigatório");
        }
        
        return new ReAct(mcpManager, llm, options);
    }
    
    public static Inference createReflection(MCPManager mcpManager, Llm llm, int maxIterations, boolean debug) {
        if (llm == null) {
            throw new IllegalArgumentException("Llm é obrigatório");
        }
        
        if (mcpManager == null) {
            throw new IllegalArgumentException("MCPManager é obrigatório");
        }
        
        return new Reflection(mcpManager, llm, maxIterations, debug);
    }
    
    public static Inference createReflection(MCPManager mcpManager, Llm llm, Map<String, Object> params) {
        if (llm == null) {
            throw new IllegalArgumentException("Llm é obrigatório");
        }
        
        if (mcpManager == null) {
            throw new IllegalArgumentException("MCPManager é obrigatório");
        }
        
        return new Reflection(mcpManager, llm, params);
    }
    
    public static Inference createSimple(MCPManager mcpManager, Llm llm) {
        if (llm == null) {
            throw new IllegalArgumentException("Llm é obrigatório");
        }
        
        if (mcpManager == null) {
            throw new IllegalArgumentException("MCPManager é obrigatório");
        }
        
        return new Simple(mcpManager, llm, new HashMap<>());
    }
    
    public static Inference createSimple(MCPManager mcpManager, Llm llm, Map<String, Object> options) {
        if (llm == null) {
            throw new IllegalArgumentException("Llm é obrigatório");
        }
        
        if (mcpManager == null) {
            throw new IllegalArgumentException("MCPManager é obrigatório");
        }
        
        return new Simple(mcpManager, llm, options);
    }

    public static Inference createInference(InferenceStrategy strategy, MCPManager mcpManager, Llm llm, Map<String, Object> defaultOptions) {
        return switch (strategy) {
            case REACT -> createReAct(mcpManager, llm, defaultOptions);
            case REFLECTION -> createReflection(mcpManager, llm, defaultOptions);
            case SIMPLE -> createSimple(mcpManager, llm, defaultOptions);
            default -> throw new IllegalArgumentException("Inference strategy '" + strategy + "' não encontrada.");
        };
    }
}
