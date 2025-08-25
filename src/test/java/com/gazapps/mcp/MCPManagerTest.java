package com.gazapps.mcp;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.gazapps.mcp.domain.DomainDefinition;
import com.gazapps.mcp.domain.Server;
import com.gazapps.mcp.domain.Tool;

/**
 * Testes básicos para validar a implementação da Etapa 1 - Core Foundation.
 */
class MCPManagerTest {
    
    private Path tempConfigDir;
    private MCPManager mcpManager;
    
    @BeforeEach
    void setUp() throws IOException {
        // Criar diretório temporário para testes
        tempConfigDir = Files.createTempDirectory("mcp-test-");
        
        // Por enquanto, não inicializar MCPManager pois requer servidores MCP reais
        // mcpManager = new MCPManager(tempConfigDir.toString());
    }
    
    @AfterEach
    void tearDown() throws IOException {
        if (mcpManager != null) {
            mcpManager.close();
        }
        
        // Limpar diretório temporário
        deleteRecursively(tempConfigDir);
    }
    
  /*  @Test
    void testMCPConfigCreation() {
        // Testar criação de configuração
        MCPConfig config = new MCPConfig(tempConfigDir.toString());
        
        assertNotNull(config);
        assertTrue(config.isAutoDiscoveryEnabled());
        assertEquals("groq", config.getLLMProvider());
        assertEquals(30000, config.getRefreshIntervalMs());
    }*/
    
     @Test
   void testServerConfigCreation() {
        // Testar criação de configuração de servidor
        Map<String, Object> serverData = new HashMap<>();
        serverData.put("description", "Test server");
        serverData.put("command", "test-command");
        serverData.put("priority", 1);
        serverData.put("enabled", true);
        serverData.put("args", List.of("arg1", "arg2"));
        serverData.put("env", Map.of("KEY1", "value1"));
        
        MCPConfig.ServerConfig serverConfig = MCPConfig.ServerConfig.fromMap("test-server", serverData);
        
        assertEquals("test-server", serverConfig.id);
        assertEquals("Test server", serverConfig.description);
        assertEquals("test-command", serverConfig.command);
        assertEquals(1, serverConfig.priority);
        assertTrue(serverConfig.enabled);
        assertEquals(2, serverConfig.args.size());
        assertEquals(1, serverConfig.env.size());
    }
    
    @Test
    void testServerEntity() {
        // Testar entidade Server
        Server server = Server.builder()
                .id("test-server")
                .name("Test Server")
                .description("A test server")
                .command("test-command")
                .args(List.of("arg1"))
                .env(Map.of("KEY1", "value1"))
                .priority(1)
                .enabled(true)
                .build();
        
        assertNotNull(server);
        assertEquals("test-server", server.getId());
        assertEquals("Test Server", server.getName());
        assertFalse(server.isConnected());
        assertFalse(server.isHealthy());
        assertEquals(0, server.getTools().size());
        
        // Testar conexão
        server.connect();
        assertTrue(server.isConnected());
    }
    
    @Test
    void testToolEntity() {
        // Testar entidade Tool
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        
        Map<String, Object> properties = new HashMap<>();
        Map<String, Object> paramDef = new HashMap<>();
        paramDef.put("type", "string");
        paramDef.put("description", "Test parameter");
        properties.put("testParam", paramDef);
        schema.put("properties", properties);
        schema.put("required", List.of("testParam"));
        
        Tool tool = Tool.builder()
                .name("test-tool")
                .description("A test tool")
                .serverId("test-server")
                .schema(schema)
                .build();
        
        assertNotNull(tool);
        assertEquals("test-tool", tool.getName());
        assertEquals("A test tool", tool.getDescription());
        assertEquals("test-server", tool.getServerId());
        assertEquals(1, tool.getRequiredParams().size());
        assertTrue(tool.isParamRequired("testParam"));
        
        // Testar validação de argumentos
        Map<String, Object> validArgs = Map.of("testParam", "test-value");
        assertTrue(tool.validateArgs(validArgs));
        
        Map<String, Object> invalidArgs = Map.of("wrongParam", "test-value");
        assertFalse(tool.validateArgs(invalidArgs));
    }
    
    @Test
    void testDomainDefinition() {
        // Testar definição de domínio
        DomainDefinition domain = DomainDefinition.builder()
                .name("test-domain")
                .description("A test domain")
                .addPattern("test")
                .addPattern("example")
                .addTool("test-tool")
                .addSemanticKeyword("testing")
                .multiStepCapable(true)
                .build();
        
        assertNotNull(domain);
        assertEquals("test-domain", domain.getName());
        assertEquals(2, domain.getPatterns().size());
        assertEquals(1, domain.getCommonTools().size());
        assertEquals(1, domain.getSemanticKeywords().size());
        assertTrue(domain.isMultiStepCapable());
        
        // Testar matching de padrões
        assertTrue(domain.containsPattern("test"));
        assertTrue(domain.supportsTool("test-tool"));
        
        double score = domain.calculatePatternMatch("This is a test query");
        assertTrue(score > 0.0);
        
        // Testar serialização
        Map<String, Object> configMap = domain.toConfigMap();
        assertNotNull(configMap);
        assertEquals("test-domain", configMap.get("name"));
        
        DomainDefinition restored = DomainDefinition.fromConfigMap(configMap);
        assertEquals(domain.getName(), restored.getName());
        assertEquals(domain.getPatterns().size(), restored.getPatterns().size());
    }
    
 /*   @Test
    void testMatchingOptions() {
        // Testar opções de matching
        MCPManager.MatchingOptions defaultOptions = MCPManager.MatchingOptions.defaultOptions();
        
        assertFalse(defaultOptions.useSemanticMatching);
        assertEquals(0.5, defaultOptions.confidenceThreshold);
        assertEquals(10, defaultOptions.maxResults);
        
        MCPManager.MatchingOptions customOptions = MCPManager.MatchingOptions.builder()
                .useSemanticMatching(true)
                .confidenceThreshold(0.8)
                .maxResults(5)
                .includeDomains(Set.of("weather", "time"))
                .build();
        
        assertTrue(customOptions.useSemanticMatching);
        assertEquals(0.8, customOptions.confidenceThreshold);
        assertEquals(5, customOptions.maxResults);
        assertEquals(2, customOptions.includeDomains.size());
    }*/
    
    @Test
    void testToolExecutionResult() {
        // Testar resultado de execução de ferramenta
        Tool mockTool = Tool.builder()
                .name("mock-tool")
                .description("Mock tool")
                .serverId("mock-server")
                .build();
        
        MCPService.ToolExecutionResult successResult = 
            MCPService.ToolExecutionResult.success(mockTool, "Execution successful");
        
        assertTrue(successResult.success);
        assertNotNull(successResult.tool);
        assertEquals("Execution successful", successResult.content);
        assertNull(successResult.error);
        
        MCPService.ToolExecutionResult errorResult = 
            MCPService.ToolExecutionResult.error("Execution failed");
        
        assertFalse(errorResult.success);
        assertNull(errorResult.tool);
        assertEquals("Execution failed", errorResult.message);
    }
    
    // Método auxiliar para limpeza
    private void deleteRecursively(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (var stream = Files.list(path)) {
                stream.forEach(child -> {
                    try {
                        deleteRecursively(child);
                    } catch (IOException e) {
                        // Ignorar erros de limpeza
                    }
                });
            }
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            // Ignorar erros de limpeza
        }
    }
}
