package com.gazapps.mcp;

import java.util.List;
import com.gazapps.mcp.domain.Tool;

/**
 * Teste simples para verificar se os parâmetros das ferramentas MCP
 * estão sendo extraídos corretamente.
 */
public class TestToolParams {
    
    public static void main(String[] args) {
        System.out.println("=== TESTE DE EXTRAÇÃO DE PARÂMETROS ===");
        
        try (MCPManager mcpManager = new MCPManager("./config")) {
            
            System.out.println("Sistema iniciado. Servidores conectados: " + mcpManager.getConnectedServers().size());
            
            MCPService mcpService = mcpManager.getMcpService();
            List<Tool> allTools = mcpService.getAllAvailableTools();
            
            System.out.println("Total de ferramentas: " + allTools.size());
            System.out.println("");
            
            // Testar algumas ferramentas específicas
            String[] testTools = {"get_current_time", "get-forecast", "read_file", "convert_time"};
            
            for (String toolName : testTools) {
                Tool tool = findTool(allTools, toolName);
                if (tool != null) {
                    analyzeToolParams(tool);
                } else {
                    System.out.println("❌ Ferramenta não encontrada: " + toolName);
                }
                System.out.println("---");
            }
            
        } catch (Exception e) {
            System.err.println("Erro: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static Tool findTool(List<Tool> tools, String toolName) {
        return tools.stream()
            .filter(t -> toolName.equals(t.getName()))
            .findFirst()
            .orElse(null);
    }
    
    private static void analyzeToolParams(Tool tool) {
        System.out.println("🔍 ANÁLISE: " + tool.getName());
        System.out.println("Servidor: " + tool.getServerId());
        System.out.println("Descrição: " + tool.getDescription());
        
        System.out.println("Schema completo: " + tool.getSchema());
        System.out.println("Schema keys: " + tool.getSchema().keySet());
        
        System.out.println("Parâmetros obrigatórios (" + tool.getRequiredParams().size() + "): " + tool.getRequiredParams());
        System.out.println("Parâmetros opcionais (" + tool.getOptionalParams().size() + "): " + tool.getOptionalParams());
        System.out.println("Todos os parâmetros: " + tool.getAllParamNames());
        
        // Verificar se properties existe
        Object properties = tool.getSchema().get("properties");
        System.out.println("Properties no schema: " + (properties != null ? properties.getClass().getSimpleName() + " " + properties : "null"));
        
        // Verificar required
        Object required = tool.getSchema().get("required");
        System.out.println("Required no schema: " + (required != null ? required.getClass().getSimpleName() + " " + required : "null"));
        
        System.out.println("");
    }
}
