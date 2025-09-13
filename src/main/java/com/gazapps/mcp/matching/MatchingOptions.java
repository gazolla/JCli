package com.gazapps.mcp.matching;

import java.util.Collections;
import java.util.Set;

/**
 * Opções de configuração para matching de ferramentas.
 * 
 * Esta classe foi extraída de MCPManager.MatchingOptions para eliminar
 * referência circular entre MCPManager e ToolMatcher.
 * 
 * Princípios KISS & DRY aplicados:
 * - KISS: Uma responsabilidade clara (configuração de matching)
 * - DRY: Elimina duplicação de dependências circulares
 */
public class MatchingOptions {
    public final boolean useSemanticMatching;
    public final double confidenceThreshold;
    public final int maxResults;
    public final Set<String> includeDomains;
    public final Set<String> excludeDomains;

    public MatchingOptions(boolean useSemanticMatching, double confidenceThreshold, int maxResults,
            Set<String> includeDomains, Set<String> excludeDomains) {
        this.useSemanticMatching = useSemanticMatching;
        this.confidenceThreshold = Math.max(0.0, Math.min(1.0, confidenceThreshold));
        this.maxResults = Math.max(0, maxResults);
        this.includeDomains = includeDomains != null ? Set.copyOf(includeDomains) : Collections.emptySet();
        this.excludeDomains = excludeDomains != null ? Set.copyOf(excludeDomains) : Collections.emptySet();
    }

    public static MatchingOptions defaultOptions() {
        return new MatchingOptions(true, 0.5, 10, Collections.emptySet(), Collections.emptySet());
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (useSemanticMatching ? 1231 : 1237);
        long temp;
        temp = Double.doubleToLongBits(confidenceThreshold);
        result = prime * result + (int) (temp ^ (temp >>> 32));
        result = prime * result + maxResults;
        result = prime * result + ((includeDomains == null) ? 0 : includeDomains.hashCode());
        result = prime * result + ((excludeDomains == null) ? 0 : excludeDomains.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        MatchingOptions other = (MatchingOptions) obj;
        if (useSemanticMatching != other.useSemanticMatching)
            return false;
        if (Double.doubleToLongBits(confidenceThreshold) != Double.doubleToLongBits(other.confidenceThreshold))
            return false;
        if (maxResults != other.maxResults)
            return false;
        if (includeDomains == null) {
            if (other.includeDomains != null)
                return false;
        } else if (!includeDomains.equals(other.includeDomains))
            return false;
        if (excludeDomains == null) {
            if (other.excludeDomains != null)
                return false;
        } else if (!excludeDomains.equals(other.excludeDomains))
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "MatchingOptions{" +
                "useSemanticMatching=" + useSemanticMatching +
                ", confidenceThreshold=" + confidenceThreshold +
                ", maxResults=" + maxResults +
                ", includeDomains=" + includeDomains +
                ", excludeDomains=" + excludeDomains +
                '}';
    }

    /**
     * Builder pattern para construção fluente de MatchingOptions.
     */
    public static class Builder {
        private boolean useSemanticMatching = false;
        private double confidenceThreshold = 0.5;
        private int maxResults = 10;
        private Set<String> includeDomains = Collections.emptySet();
        private Set<String> excludeDomains = Collections.emptySet();

        public Builder useSemanticMatching(boolean use) {
            this.useSemanticMatching = use;
            return this;
        }

        public Builder confidenceThreshold(double threshold) {
            this.confidenceThreshold = threshold;
            return this;
        }

        public Builder maxResults(int max) {
            this.maxResults = max;
            return this;
        }

        public Builder includeDomains(Set<String> domains) {
            this.includeDomains = domains != null ? Set.copyOf(domains) : Collections.emptySet();
            return this;
        }

        public Builder excludeDomains(Set<String> domains) {
            this.excludeDomains = domains != null ? Set.copyOf(domains) : Collections.emptySet();
            return this;
        }

        public MatchingOptions build() {
            return new MatchingOptions(useSemanticMatching, confidenceThreshold, maxResults, includeDomains,
                    excludeDomains);
        }
    }
}
