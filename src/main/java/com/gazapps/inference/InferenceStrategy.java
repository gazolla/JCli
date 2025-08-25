package com.gazapps.inference;
   public enum InferenceStrategy {
        SIMPLE,
        REACT,
        REFLECTION;
    	
        public static InferenceStrategy fromString(String value) {
            if (value == null) return null;
            for (InferenceStrategy strategy : InferenceStrategy.values()) {
                if (strategy.name().equalsIgnoreCase(value.trim())) {
                    return strategy;
                }
            }
            throw new IllegalArgumentException("Invalid Inference Strategy: " + value);
        }
    }