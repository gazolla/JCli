package com.gazapps.mcp.rules;

import java.util.List;
import java.util.Map;

public class RuleItem {
    private String name;
    private List<String> triggers;
    private List<String> contentKeywords;
    private Map<String, Object> rules;

    public RuleItem() {}

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public List<String> getTriggers() { return triggers; }
    public void setTriggers(List<String> triggers) { this.triggers = triggers; }

    public List<String> getContentKeywords() { return contentKeywords; }
    public void setContentKeywords(List<String> contentKeywords) { this.contentKeywords = contentKeywords; }

    public Map<String, Object> getRules() { return rules; }
    public void setRules(Map<String, Object> rules) { this.rules = rules; }

    public boolean matchesTrigger(String parameter) {
        return triggers != null && triggers.contains(parameter);
    }

    public boolean matchesContent(String content) {
        if (contentKeywords == null || content == null) return false;
        String lowerContent = content.toLowerCase();
        return contentKeywords.stream().anyMatch(keyword -> lowerContent.contains(keyword.toLowerCase()));
    }
}
