package com.gazapps.mcp.domain;

import java.util.*;

/**
 * Entidade que representa uma ferramenta MCP com schema completo de parâmetros,
 * validação e normalização de argumentos.
 */
public class Tool {
    
    private final String name;
    private final String description;
    private final String serverId;
    private final Map<String, Object> schema;
    private final List<String> requiredParams;
    private final List<String> optionalParams;
    private String domain;
    
    public Tool(String name, String description, String serverId, Map<String, Object> schema) {
        this.name = Objects.requireNonNull(name, "Tool name cannot be null");
        this.description = description != null ? description : "";
        this.serverId = Objects.requireNonNull(serverId, "Server ID cannot be null");
        this.schema = schema != null ? Map.copyOf(schema) : Collections.emptyMap();
        this.requiredParams = extractRequiredParams();
        this.optionalParams = extractOptionalParams();
        this.domain = inferDomain();
    }
    
    /**
     * Factory method para converter de ferramenta MCP do SDK.
     */
    public static Tool fromMcp(io.modelcontextprotocol.spec.McpSchema.Tool mcpTool, String serverId) {
        Objects.requireNonNull(mcpTool, "MCP tool cannot be null");
        
        Map<String, Object> schema = Collections.emptyMap();
        if (mcpTool.inputSchema() != null) {
            schema = convertMcpSchema(mcpTool.inputSchema());
        }
        
        return new Tool(
            mcpTool.name(),
            mcpTool.description(),
            serverId,
            schema
        );
    }
    
    /**
     * Builder para criar instâncias Tool de forma fluida.
     */
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String name;
        private String description;
        private String serverId;
        private Map<String, Object> schema = Collections.emptyMap();
        private String domain;
        
        public Builder name(String name) {
            this.name = name;
            return this;
        }
        
        public Builder description(String description) {
            this.description = description;
            return this;
        }
        
        public Builder serverId(String serverId) {
            this.serverId = serverId;
            return this;
        }
        
        public Builder schema(Map<String, Object> schema) {
            this.schema = schema;
            return this;
        }
        
        public Builder domain(String domain) {
            this.domain = domain;
            return this;
        }
        
        public Tool build() {
            Tool tool = new Tool(name, description, serverId, schema);
            if (domain != null) {
                tool.domain = domain;
            }
            return tool;
        }
    }
    
    // Getters básicos
    
    public String getName() {
        return name;
    }
    
    public String getDescription() {
        return description;
    }
    
    public String getDomain() {
        return domain;
    }
    
    public String getServerId() {
        return serverId;
    }
    
    public Map<String, Object> getSchema() {
        return schema;
    }
    
    public List<String> getRequiredParams() {
        return requiredParams;
    }
    
    public List<String> getOptionalParams() {
        return optionalParams;
    }
    
    // Métodos de validação e utilitários
    
    public boolean validateArgs(Map<String, Object> args) {
        if (args == null) args = Collections.emptyMap();
        
        // Verificar parâmetros obrigatórios
        for (String required : requiredParams) {
            if (!args.containsKey(required) || args.get(required) == null) {
                return false;
            }
        }
        
        // Verificar tipos básicos se disponível no schema
        Map<String, Object> properties = getProperties();
        for (Map.Entry<String, Object> entry : args.entrySet()) {
            String paramName = entry.getKey();
            if (properties.containsKey(paramName)) {
                if (!validateParameterType(paramName, entry.getValue(), properties)) {
                    return false;
                }
            }
        }
        
        return true;
    }
    
    public Set<String> getRequiredParamNames() {
        return Set.copyOf(requiredParams);
    }
    
    public Set<String> getAllParamNames() {
        Set<String> allParams = new HashSet<>(requiredParams);
        allParams.addAll(optionalParams);
        return allParams;
    }
    
    public Object getParamDefault(String paramName) {
        Map<String, Object> properties = getProperties();
        if (properties.containsKey(paramName)) {
            @SuppressWarnings("unchecked")
            Map<String, Object> paramDef = (Map<String, Object>) properties.get(paramName);
            return paramDef.get("default");
        }
        return null;
    }
    
    public String getParamType(String paramName) {
        Map<String, Object> properties = getProperties();
        if (properties.containsKey(paramName)) {
            @SuppressWarnings("unchecked")
            Map<String, Object> paramDef = (Map<String, Object>) properties.get(paramName);
            return (String) paramDef.get("type");
        }
        return "string"; // default type
    }
    
    public String getParamDescription(String paramName) {
        Map<String, Object> properties = getProperties();
        if (properties.containsKey(paramName)) {
            @SuppressWarnings("unchecked")
            Map<String, Object> paramDef = (Map<String, Object>) properties.get(paramName);
            return (String) paramDef.get("description");
        }
        return "";
    }
    
    public boolean isParamRequired(String paramName) {
        return requiredParams.contains(paramName);
    }
    
    public List<String> getMissingRequiredParams(Map<String, Object> args) {
        if (args == null) args = Collections.emptyMap();
        
        List<String> missing = new ArrayList<>();
        for (String required : requiredParams) {
            if (!args.containsKey(required) || args.get(required) == null) {
                missing.add(required);
            }
        }
        return missing;
    }
    
    public boolean isExecutable(Map<String, Object> args) {
        return getMissingRequiredParams(args).isEmpty();
    }
    
    public Map<String, Object> normalizeArgs(Map<String, Object> args) {
        if (args == null) return Collections.emptyMap();
        
        Map<String, Object> normalized = new HashMap<>(args);
        Map<String, Object> properties = getProperties();
        
        // Aplicar valores padrão para parâmetros opcionais ausentes
        for (String optionalParam : optionalParams) {
            if (!normalized.containsKey(optionalParam)) {
                Object defaultValue = getParamDefault(optionalParam);
                if (defaultValue != null) {
                    normalized.put(optionalParam, defaultValue);
                }
            }
        }
        
        // Normalizar tipos se necessário
        for (Map.Entry<String, Object> entry : normalized.entrySet()) {
            String paramName = entry.getKey();
            Object value = entry.getValue();
            String expectedType = getParamType(paramName);
            
            Object normalizedValue = normalizeValue(value, expectedType);
            if (normalizedValue != value) {
                normalized.put(paramName, normalizedValue);
            }
        }
        
        return normalized;
    }
    
    // Métodos privados auxiliares
    
    @SuppressWarnings("unchecked")
    private List<String> extractRequiredParams() {
        if (schema.containsKey("required")) {
            try {
                return List.copyOf((List<String>) schema.get("required"));
            } catch (ClassCastException e) {
                return Collections.emptyList();
            }
        }
        return Collections.emptyList();
    }
    
    @SuppressWarnings("unchecked")
    private List<String> extractOptionalParams() {
        Map<String, Object> properties = getProperties();
        if (properties.isEmpty()) return Collections.emptyList();
        
        List<String> optional = new ArrayList<>();
        for (String paramName : properties.keySet()) {
            if (!requiredParams.contains(paramName)) {
                optional.add(paramName);
            }
        }
        return optional;
    }
    
    @SuppressWarnings("unchecked")
    private Map<String, Object> getProperties() {
        if (schema.containsKey("properties")) {
            try {
                return (Map<String, Object>) schema.get("properties");
            } catch (ClassCastException e) {
                return Collections.emptyMap();
            }
        }
        return Collections.emptyMap();
    }
    
    private String inferDomain() {
        // Heurística simples para inferir domínio baseado no nome da ferramenta
        String toolName = name.toLowerCase();
        
        if (toolName.contains("weather") || toolName.contains("clima")) return "weather";
        if (toolName.contains("file") || toolName.contains("filesystem") || toolName.contains("read") || toolName.contains("write")) return "filesystem";
        if (toolName.contains("time") || toolName.contains("date") || toolName.contains("calendar")) return "time";
        if (toolName.contains("http") || toolName.contains("request") || toolName.contains("api")) return "web";
        if (toolName.contains("calc") || toolName.contains("math")) return "math";
        
        return "general";
    }
    
    private boolean validateParameterType(String paramName, Object value, Map<String, Object> properties) {
        if (value == null) return true; // null values são validados em outro lugar
        
        @SuppressWarnings("unchecked")
        Map<String, Object> paramDef = (Map<String, Object>) properties.get(paramName);
        if (paramDef == null) return true;
        
        String expectedType = (String) paramDef.get("type");
        if (expectedType == null) return true;
        
        switch (expectedType) {
            case "string":
                return value instanceof String;
            case "integer":
                return value instanceof Integer || value instanceof Long;
            case "number":
                return value instanceof Number;
            case "boolean":
                return value instanceof Boolean;
            case "array":
                return value instanceof List;
            case "object":
                return value instanceof Map;
            default:
                return true; // tipo desconhecido, assumir válido
        }
    }
    
    private Object normalizeValue(Object value, String expectedType) {
        if (value == null || expectedType == null) return value;
        
        try {
            switch (expectedType) {
                case "string":
                    return value instanceof String ? value : String.valueOf(value);
                case "integer":
                    if (value instanceof Number) {
                        return ((Number) value).intValue();
                    } else if (value instanceof String) {
                        return Integer.parseInt((String) value);
                    }
                    break;
                case "number":
                    if (value instanceof Number) {
                        return value;
                    } else if (value instanceof String) {
                        return Double.parseDouble((String) value);
                    }
                    break;
                case "boolean":
                    if (value instanceof Boolean) {
                        return value;
                    } else if (value instanceof String) {
                        return Boolean.parseBoolean((String) value);
                    }
                    break;
            }
        } catch (Exception e) {
            // Se a conversão falhar, retorna o valor original
        }
        
        return value;
    }
    
    @SuppressWarnings("unchecked")
    private static Map<String, Object> convertMcpSchema(Object inputSchema) {
        if (inputSchema == null) {
            return Collections.emptyMap();
        }
        
        try {
            // KISS: Acesso direto às propriedades do JsonSchema
            if (inputSchema instanceof io.modelcontextprotocol.spec.McpSchema.JsonSchema) {
                io.modelcontextprotocol.spec.McpSchema.JsonSchema jsonSchema = 
                    (io.modelcontextprotocol.spec.McpSchema.JsonSchema) inputSchema;
                
                Map<String, Object> result = new HashMap<>();
                
                // Copiar properties diretamente
                if (jsonSchema.properties() != null && !jsonSchema.properties().isEmpty()) {
                    result.put("properties", new HashMap<>(jsonSchema.properties()));
                }
                
                // Copiar required diretamente
                if (jsonSchema.required() != null && !jsonSchema.required().isEmpty()) {
                    result.put("required", new ArrayList<>(jsonSchema.required()));
                }
                
                // Copiar type se existir
                if (jsonSchema.type() != null) {
                    result.put("type", jsonSchema.type());
                }
                
                return result;
            }
            
            // Fallback para Map genérico
            if (inputSchema instanceof Map) {
                return new HashMap<>((Map<String, Object>) inputSchema);
            }
            
        } catch (Exception e) {
            System.err.println("Erro ao converter schema MCP: " + e.getMessage());
            e.printStackTrace();
        }
        
        return Collections.emptyMap();
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        Tool tool = (Tool) obj;
        return Objects.equals(name, tool.name) && Objects.equals(serverId, tool.serverId);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(name, serverId);
    }
    
    @Override
    public String toString() {
        return String.format("Tool{name='%s', domain='%s', server='%s', requiredParams=%d, optionalParams=%d}",
                           name, domain, serverId, requiredParams.size(), optionalParams.size());
    }
}
