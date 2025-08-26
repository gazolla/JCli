package com.gazapps.mcp;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.gazapps.mcp.domain.DomainDefinition;
import com.gazapps.mcp.domain.Server;

/**
 * Gerenciador de configurações MCP que suporta tanto configuração estática
 * via arquivos JSON quanto descoberta dinâmica de servidores.
 */
public class MCPConfig {
    
    private static final Logger logger = LoggerFactory.getLogger(MCPConfig.class);
    
    private final ObjectMapper objectMapper;
    private final String configDirectory;
    private final Map<String, ServerConfig> servers;
    private final Map<String, DomainDefinition> domains;
    
    // Configurações globais
    private boolean autoDiscoveryEnabled = true;
    private String llmProvider = "groq";
    private long refreshIntervalMs = 300000; // 5 minutos (mais razoável)
    
    public MCPConfig(String configDirectory) {
        this.configDirectory = Objects.requireNonNull(configDirectory, "Config directory cannot be null");
        this.objectMapper = new ObjectMapper();
        this.servers = new ConcurrentHashMap<>();
        this.domains = new ConcurrentHashMap<>();
        
        ensureConfigDirectory();
        loadConfiguration();
    }
    
    /**
     * Carrega servidores da configuração.
     */
    public Map<String, ServerConfig> loadServers() {
        return Map.copyOf(servers);
    }
    
    /**
     * Carrega domínios da configuração.
     */
    public Map<String, DomainDefinition> loadDomains() {
        return Map.copyOf(domains);
    }
    
    /**
     * Salva a configuração atual nos arquivos.
     */
    public void saveConfiguration() {
        try {
            saveServersConfig();
            saveDomainsConfig();
            logger.info("Configuração salva com sucesso");
        } catch (Exception e) {
            logger.error("Erro ao salvar configuração", e);
            throw new MCPConfigException("Falha ao salvar configuração", e);
        }
    }
    
    /**
     * Valida a configuração atual.
     */
    public void validateConfiguration() {
        List<String> errors = new ArrayList<>();
        
        // Validar servidores
        for (Map.Entry<String, ServerConfig> entry : servers.entrySet()) {
            String serverId = entry.getKey();
            ServerConfig config = entry.getValue();
            
            if (config.command == null || config.command.trim().isEmpty()) {
                errors.add("Servidor '" + serverId + "' não possui comando definido");
            }
        }
        
        // Validar domínios
        for (Map.Entry<String, DomainDefinition> entry : domains.entrySet()) {
            String domainName = entry.getKey();
            DomainDefinition domain = entry.getValue();
            
            if (!domainName.equals(domain.getName())) {
                errors.add("Nome do domínio '" + domainName + "' não coincide com definição interna");
            }
        }
        
        if (!errors.isEmpty()) {
            throw new MCPConfigException("Configuração inválida: " + String.join("; ", errors));
        }
    }
    
    // Getters e setters para configurações globais
    
    public boolean isAutoDiscoveryEnabled() {
        return autoDiscoveryEnabled;
    }
    
    public void setAutoDiscoveryEnabled(boolean autoDiscoveryEnabled) {
        this.autoDiscoveryEnabled = autoDiscoveryEnabled;
    }
    
    public String getLLMProvider() {
        return llmProvider;
    }
    
    public void setLLMProvider(String llmProvider) {
        this.llmProvider = Objects.requireNonNull(llmProvider, "LLM provider cannot be null");
    }
    
    public long getRefreshIntervalMs() {
        return refreshIntervalMs;
    }
    
    public void setRefreshIntervalMs(long refreshIntervalMs) {
        if (refreshIntervalMs < 1000) {
            throw new IllegalArgumentException("Refresh interval must be at least 1000ms");
        }
        this.refreshIntervalMs = refreshIntervalMs;
    }
    
    
    public void removeDomain(String domain) {
        if (domains.remove(domain) != null) {
            logger.info("Domínio '{}' removido da configuração", domain);
        }
    }
    
    // Métodos privados auxiliares
    
    private void ensureConfigDirectory() {
        try {
            Path configPath = Paths.get(configDirectory);
            Files.createDirectories(configPath);
            
            Path mcpPath = configPath.resolve("mcp");
            Files.createDirectories(mcpPath);
            
        } catch (IOException e) {
            throw new MCPConfigException("Não foi possível criar diretório de configuração: " + configDirectory, e);
        }
    }
    
    private void loadConfiguration() {
        loadServersFromFile();
        loadDomainsFromFile();
        loadDefaultDomains(); 
    }
    
    private void loadServersFromFile() {
        Path serversFile = Paths.get(configDirectory, "mcp", "mcp.json");
        
        if (!Files.exists(serversFile)) {
            logger.info("Arquivo de configuração de servidores não encontrado, criando configuração padrão");
            createDefaultServersConfig();
            return;
        }
        
        try {
            String content = Files.readString(serversFile);
            TypeFactory typeFactory = objectMapper.getTypeFactory();
            
            @SuppressWarnings("unchecked")
            Map<String, Object> rootConfig = objectMapper.readValue(content, Map.class);
            
            if (rootConfig.containsKey("mcpServers")) {
                @SuppressWarnings("unchecked")
                Map<String, Map<String, Object>> serversData = 
                    (Map<String, Map<String, Object>>) rootConfig.get("mcpServers");
                
                for (Map.Entry<String, Map<String, Object>> entry : serversData.entrySet()) {
                    String serverId = entry.getKey();
                    Map<String, Object> serverData = entry.getValue();
                    
                    ServerConfig config = ServerConfig.fromMap(serverId, serverData);
                    servers.put(serverId, config);
                }
            }
            
            logger.info("Carregados {} servidores da configuração", servers.size());
            
        } catch (Exception e) {
            logger.error("Erro ao carregar configuração de servidores", e);
            createDefaultServersConfig();
        }
    }
    
    private void loadDomainsFromFile() {
        Path domainsFile = Paths.get(configDirectory, "mcp", "domains.json");
        
        if (!Files.exists(domainsFile)) {
            logger.info("Arquivo de configuração de domínios não encontrado");
            return;
        }
        
        try {
            String content = Files.readString(domainsFile);
            
            @SuppressWarnings("unchecked")
            Map<String, Map<String, Object>> domainsData = objectMapper.readValue(content, Map.class);
            
            for (Map.Entry<String, Map<String, Object>> entry : domainsData.entrySet()) {
                String domainName = entry.getKey();
                Map<String, Object> domainData = entry.getValue();
                
                DomainDefinition domain = DomainDefinition.fromConfigMap(domainData);
                domains.put(domainName, domain);
            }
            
            logger.info("Carregados {} domínios da configuração", domains.size());
            
        } catch (Exception e) {
            logger.error("Erro ao carregar configuração de domínios", e);
        }
    }
    
    private void createDefaultServersConfig() {
        // Criar configuração padrão baseada no JSON fornecido
        Map<String, Object> rootConfig = new HashMap<>();
        Map<String, Object> serversConfig = new HashMap<>();
        
        // Weather NWS
        Map<String, Object> weatherConfig = new HashMap<>();
        weatherConfig.put("description", "Previsões meteorológicas via NWS");
        weatherConfig.put("command", "npx @h1deya/mcp-server-weather");
        weatherConfig.put("priority", 1);
        weatherConfig.put("enabled", true);
        weatherConfig.put("env", Map.of("REQUIRES_NODEJS", "true", "REQUIRES_ONLINE", "true"));
        weatherConfig.put("args", Collections.emptyList());
        serversConfig.put("weather-nws", weatherConfig);
        
        // Filesystem
        Map<String, Object> filesystemConfig = new HashMap<>();
        filesystemConfig.put("description", "Sistema de arquivos - Documents");
        filesystemConfig.put("command", "npx -y @modelcontextprotocol/server-filesystem ./documents");
        filesystemConfig.put("priority", 3);
        filesystemConfig.put("enabled", true);
        filesystemConfig.put("env", Map.of("REQUIRES_NODEJS", "true"));
        filesystemConfig.put("args", Collections.emptyList());
        serversConfig.put("filesystem", filesystemConfig);
        
        // Time
        Map<String, Object> timeConfig = new HashMap<>();
        timeConfig.put("description", "Servidor para ferramentas de tempo e fuso horário");
        timeConfig.put("command", "uvx mcp-server-time");
        timeConfig.put("priority", 1);
        timeConfig.put("enabled", true);
        timeConfig.put("env", Map.of("REQUIRES_UVX", "true"));
        timeConfig.put("args", Collections.emptyList());
        serversConfig.put("time", timeConfig);
        
        rootConfig.put("mcpServers", serversConfig);
        
        // Salvar arquivo
        try {
            Path serversFile = Paths.get(configDirectory, "mcp", "mcp.json");
            String jsonContent = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(rootConfig);
            Files.writeString(serversFile, jsonContent);
            
            // Recarregar do arquivo
            loadServersFromFile();
            
        } catch (Exception e) {
            logger.error("Erro ao criar configuração padrão de servidores", e);
        }
    }
    
    private void loadDefaultDomains() {
        if (domains.isEmpty()) {
            // Domínios básicos
            domains.put("weather", DomainDefinition.builder()
                .name("weather")
                .description("Informações meteorológicas e previsões do tempo")
                .addPattern("weather")
                .addPattern("clima")
                .addPattern("previsão")
                .addPattern("temperatura")
                .addSemanticKeyword("meteorologia")
                .addSemanticKeyword("forecast")
                .build());
            
            domains.put("filesystem", DomainDefinition.builder()
                .name("filesystem")
                .description("Operações com sistema de arquivos")
                .addPattern("arquivo")
                .addPattern("file")
                .addPattern("diretório")
                .addPattern("pasta")
                .addSemanticKeyword("read")
                .addSemanticKeyword("write")
                .addSemanticKeyword("create")
                .build());
            
            domains.put("time", DomainDefinition.builder()
                .name("time")
                .description("Informações de tempo, data e fuso horário")
                .addPattern("time")
                .addPattern("tempo")
                .addPattern("data")
                .addPattern("hora")
                .addSemanticKeyword("timezone")
                .addSemanticKeyword("calendar")
                .build());
        }
    }
    
    private void saveServersConfig() throws IOException {
        Map<String, Object> rootConfig = new HashMap<>();
        Map<String, Object> serversConfig = new HashMap<>();
        
        for (Map.Entry<String, ServerConfig> entry : servers.entrySet()) {
            String serverId = entry.getKey();
            ServerConfig config = entry.getValue();
            serversConfig.put(serverId, config.toMap());
        }
        
        rootConfig.put("mcpServers", serversConfig);
        
        Path serversFile = Paths.get(configDirectory, "mcp", "mcp.json");
        String jsonContent = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(rootConfig);
        Files.writeString(serversFile, jsonContent);
    }
    
    private void saveDomainsConfig() throws IOException {
        Map<String, Object> domainsConfig = new HashMap<>();
        
        for (Map.Entry<String, DomainDefinition> entry : domains.entrySet()) {
            String domainName = entry.getKey();
            DomainDefinition domain = entry.getValue();
            domainsConfig.put(domainName, domain.toConfigMap());
        }
        
        Path domainsFile = Paths.get(configDirectory, "mcp", "domains.json");
        String jsonContent = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(domainsConfig);
        Files.writeString(domainsFile, jsonContent);
    }
    
    /**
     * Atualiza configuração de um servidor específico.
     */
    public void updateServerConfig(String serverId, ServerConfig config) {
        Objects.requireNonNull(serverId, "Server ID cannot be null");
        Objects.requireNonNull(config, "Server config cannot be null");
        
        servers.put(serverId, config);
        
        try {
            saveConfiguration();
            logger.info("Configuração do servidor '{}' atualizada", serverId);
        } catch (Exception e) {
            logger.error("Erro ao salvar configuração atualizada do servidor '{}'", serverId, e);
            throw new MCPConfigException("Erro ao atualizar configuração do servidor: " + e.getMessage(), e);
        }
    }
    
    /**
     * Remove um servidor da configuração.
     */
    public boolean removeServer(String serverId) {
        Objects.requireNonNull(serverId, "Server ID cannot be null");
        
        boolean removed = servers.remove(serverId) != null;
        
        if (removed) {
            try {
                saveConfiguration();
                logger.info("Servidor '{}' removido da configuração", serverId);
            } catch (Exception e) {
                logger.error("Erro ao salvar configuração após remoção do servidor '{}'", serverId, e);
                throw new MCPConfigException("Erro ao remover servidor da configuração: " + e.getMessage(), e);
            }
        }
        
        return removed;
    }
    
    /**
     * Atualiza configuração de um domínio específico.
     */
    public void updateDomainConfig(String domain, DomainDefinition definition) {
        Objects.requireNonNull(domain, "Domain cannot be null");
        Objects.requireNonNull(definition, "Domain definition cannot be null");
        
        domains.put(domain, definition);
        
        try {
            saveConfiguration();
            logger.info("Configuração do domínio '{}' atualizada", domain);
        } catch (Exception e) {
            logger.error("Erro ao salvar configuração atualizada do domínio '{}'", domain, e);
            throw new MCPConfigException("Erro ao atualizar configuração do domínio: " + e.getMessage(), e);
        }
    }
    
    /**
     * Classe para representar configuração de servidor.
     */
    public static class ServerConfig {
        public final String id;
        public final String description;
        public final String command;
        public final List<String> args;
        public final Map<String, String> env;
        public final int priority;
        public final boolean enabled;
        public final String domain;
        
        public ServerConfig(String id, String description, String command, List<String> args,
                           Map<String, String> env, int priority, boolean enabled, String domain) {
            this.id = Objects.requireNonNull(id, "Server ID cannot be null");
            this.description = description != null ? description : "";
            this.command = Objects.requireNonNull(command, "Server command cannot be null");
            this.args = args != null ? List.copyOf(args) : Collections.emptyList();
            this.env = env != null ? Map.copyOf(env) : Collections.emptyMap();
            this.priority = priority;
            this.enabled = enabled;
            this.domain = domain; // Pode ser null
        }
        
        @SuppressWarnings("unchecked")
        public static ServerConfig fromMap(String id, Map<String, Object> data) {
            String description = (String) data.get("description");
            String command = (String) data.get("command");
            List<String> args = (List<String>) data.getOrDefault("args", Collections.emptyList());
            Map<String, String> env = (Map<String, String>) data.getOrDefault("env", Collections.emptyMap());
            Integer priority = (Integer) data.getOrDefault("priority", 1);
            Boolean enabled = (Boolean) data.getOrDefault("enabled", true);
            String domain = (String) data.get("domain"); // Pode ser null
            
            return new ServerConfig(id, description, command, args, env, priority, enabled, domain);
        }
        
        public Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("description", description);
            map.put("command", command);
            map.put("args", args);
            map.put("env", env);
            map.put("priority", priority);
            map.put("enabled", enabled);
            if (domain != null) {
                map.put("domain", domain);
            }
            return map;
        }
        
        public Server toServer() {
            Server server = Server.builder()
                .id(id)
                .name(id) // Por padrão, usar ID como nome
                .description(description)
                .command(command)
                .args(args)
                .env(env)
                .priority(priority)
                .enabled(enabled)
                .build();
            
            if (domain != null) {
                server.setDomain(domain);
            }
            
            return server;
        }
    }
    
    /**
     * Exceção específica para erros de configuração MCP.
     */
    public static class MCPConfigException extends RuntimeException {
        public MCPConfigException(String message) {
            super(message);
        }
        
        public MCPConfigException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
