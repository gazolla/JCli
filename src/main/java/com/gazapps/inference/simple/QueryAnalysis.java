package com.gazapps.inference.simple;

public class QueryAnalysis {
    public enum ExecutionType { 
        SINGLE_TOOL,     // Needs one tool
        MULTI_TOOL,      // Needs multiple tools  
        DIRECT_ANSWER    // Answer directly with knowledge
    }
    
    public final ExecutionType execution;
    public final String reasoning;
    
    public QueryAnalysis(ExecutionType execution, String reasoning) {
        this.execution = execution;
        this.reasoning = reasoning;
    }
    
    @Override
    public String toString() {
        return String.format("QueryAnalysis{execution=%s, reasoning='%s'}", 
                           execution, reasoning);
    }
}
