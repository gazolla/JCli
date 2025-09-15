package com.gazapps.mcp.rules;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

public class RuleEngine {
    private static final Logger logger = LoggerFactory.getLogger(RuleEngine.class);
    
    private final Map<String, ServerRules> serverRules = new HashMap<>();
    private final String rulesPath;
    private final ObjectMapper mapper = new ObjectMapper();
    private boolean enabled = true;

    public RuleEngine(String rulesPath) {
        this(rulesPath, true);
    }
    
    public RuleEngine(String rulesPath, boolean enabled) {
        this.rulesPath = rulesPath;
        this.enabled = enabled;
        
        // FASE 2: Criar regras padrão se necessário
        initializeDefaultRules();
        loadRules();
    }
    
    // FASE 2: Métodos movidos de MCPConfig para centralizar rule management
    
    /**
     * Inicializa regras padrão se ainda não existem.
     * Método movido de MCPConfig.createDefaultRuleFiles().
     */
    private void initializeDefaultRules() {
        try {
            File rulesDir = new File(rulesPath);
            if (!rulesDir.exists()) {
                rulesDir.mkdirs();
            }
            
            createFilesystemRules();
            createTimeRules();
            logger.info("Default rule files initialized at: {}", rulesPath);
            
        } catch (Exception e) {
            logger.warn("Error creating default rule files", e);
        }
    }
    
    /**
     * Cria regras padrão para sistema de arquivos.
     * Método movido de MCPConfig.createFilesystemRules().
     */
    private void createFilesystemRules() {
        try {
            File filesystemRulesFile = new File(rulesPath, "server-filesystem.json");
            
            if (!filesystemRulesFile.exists()) {
                Map<String, Object> filesystemRules = new HashMap<>();
                filesystemRules.put("name", "server-filesystem");
                filesystemRules.put("description", "Regras para sistema de arquivos");
                filesystemRules.put("version", "1.0");

                Map<String, Object> relativePathsItem = new HashMap<>();
                relativePathsItem.put("name", "relative-paths");
                relativePathsItem.put("triggers", List.of("path", "filename"));
                relativePathsItem.put("contentKeywords",
                        List.of("arquivo", "salvar", "criar", "documents", "pasta", "diretório", "path", "filesystem",
                                "file", "folder", "directory", "caminho", "sistema de arquivos", "leitura", "escrita",
                                "writing", "reading", "storage", "armazenamento"));

                Map<String, Object> relativePathsRules = new HashMap<>();
                relativePathsRules.put("context_add",
                        "\n\nIMPORTANTE: Use sempre caminhos relativos simples (documents/arquivo.txt, não /documents/arquivo.txt).");
                relativePathsItem.put("rules", relativePathsRules);

                filesystemRules.put("items", List.of(relativePathsItem));

                mapper.writerWithDefaultPrettyPrinter().writeValue(filesystemRulesFile, filesystemRules);
                logger.debug("Criado arquivo de regras: {}", filesystemRulesFile);
            }
        } catch (Exception e) {
            logger.warn("Error creating filesystem rules", e);
        }
    }
    
    /**
     * Cria regras padrão para servidor de tempo.
     * Método movido de MCPConfig.createTimeRules().
     */
    private void createTimeRules() {
        try {
            File timeRulesFile = new File(rulesPath, "mcp-server-time.json");
            
            if (!timeRulesFile.exists()) {
                Map<String, Object> timeRules = new HashMap<>();
                timeRules.put("name", "mcp-server-time");
                timeRules.put("description", "Regras para servidor de tempo");
                timeRules.put("version", "1.0");

                Map<String, Object> timezoneItem = new HashMap<>();
                timezoneItem.put("name", "timezone-inference");
                timezoneItem.put("triggers", List.of("timezone", "tz"));
                timezoneItem.put("contentKeywords",
                        List.of("hora", "tempo", "data", "fuso horário", "datahora", "marcotemporal", "agendamento",
                                "calendário", "duração", "intervalo", "recorrência", "time", "date", "timezone", "datetime",
                                "timestamp", "UTC", "scheduling", "calendar", "duration", "interval", "recurrence"));

                Map<String, Object> timezoneRules = new HashMap<>();
                Map<String, Object> parameterReplace = new HashMap<>();
                Map<String, Object> timezoneReplace = new HashMap<>();
                timezoneReplace.put("pattern", "Use '[^']*' as local timezone[^.]*\\.");
                timezoneReplace.put("replacement",
                        "Busque infromações sobre timezone IANA. Use apenas timezones IANA válidas existentes. Não invente timezones.");
                parameterReplace.put("timezone", timezoneReplace);
                timezoneRules.put("parameter_replace", parameterReplace);

                timezoneItem.put("rules", timezoneRules);

                timeRules.put("items", List.of(timezoneItem));

                mapper.writerWithDefaultPrettyPrinter().writeValue(timeRulesFile, timeRules);
                logger.debug("Criado arquivo de regras: {}", timeRulesFile);
            }
        } catch (Exception e) {
            logger.warn("Error creating time rules", e);
        }
    }

    public String enhancePrompt(String prompt, String serverName, List<String> parameters) {
        if (!enabled || prompt == null || serverName == null) {
            return prompt;
        }

        ServerRules rules = serverRules.get(serverName);
        if (rules == null) {
            return prompt;
        }

        String enhancedPrompt = prompt;
        for (RuleItem item : rules.getItems()) {
            if (shouldApplyRule(item, parameters, prompt)) {
                enhancedPrompt = applyRule(enhancedPrompt, item);
            }
        }

        return enhancedPrompt;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void reload() {
        serverRules.clear();
        loadRules();
    }

    private void loadRules() {
        File rulesDir = new File(rulesPath);
        if (!rulesDir.exists()) {
            logger.info("Rules directory not found: {}", rulesPath);
            return;
        }

        File[] jsonFiles = rulesDir.listFiles((dir, name) -> name.endsWith(".json"));
        if (jsonFiles == null) {
            return;
        }

        for (File file : jsonFiles) {
            try {
                ServerRules rules = mapper.readValue(file, ServerRules.class);
                serverRules.put(rules.getName(), rules);
                logger.debug("Loaded rules for server: {}", rules.getName());
            } catch (Exception e) {
                logger.warn("Failed to load rules from: {}", file.getName(), e);
            }
        }

        logger.info("Loaded rules for {} servers", serverRules.size());
    }

    private boolean shouldApplyRule(RuleItem item, List<String> parameters, String prompt) {
        if (parameters != null) {
            for (String param : parameters) {
                if (item.matchesTrigger(param)) {
                    return true;
                }
            }
        }
        return item.matchesContent(prompt);
    }

    private String applyRule(String prompt, RuleItem item) {
        Map<String, Object> rules = item.getRules();
        if (rules == null) {
            return prompt;
        }

        // Context injection
        if (rules.containsKey("context_add")) {
            String context = (String) rules.get("context_add");
            return prompt + context;
        }

        // Parameter replacement
        if (rules.containsKey("parameter_replace")) {
            @SuppressWarnings("unchecked")
            Map<String, Map<String, String>> replaceRules = (Map<String, Map<String, String>>) rules.get("parameter_replace");
            
            String result = prompt;
            for (Map.Entry<String, Map<String, String>> entry : replaceRules.entrySet()) {
                Map<String, String> replaceRule = entry.getValue();
                String pattern = replaceRule.get("pattern");
                String replacement = replaceRule.get("replacement");
                
                if (pattern != null && replacement != null) {
                    result = result.replaceAll(pattern, replacement);
                }
            }
            return result;
        }

        return prompt;
    }
}
