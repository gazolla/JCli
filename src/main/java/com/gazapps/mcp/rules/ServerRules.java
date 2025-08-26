package com.gazapps.mcp.rules;

import java.util.ArrayList;
import java.util.List;

public class ServerRules {
    private String name;
    private String description;
    private String version;
    private List<RuleItem> items = new ArrayList<>();

    public ServerRules() {}

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public List<RuleItem> getItems() { return items; }
    public void setItems(List<RuleItem> items) { this.items = items != null ? items : new ArrayList<>(); }
}
