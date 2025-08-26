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
        loadRules();
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
