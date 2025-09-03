package com.gazapps.inference.react;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReActLogger {
    private static final Logger logger = LoggerFactory.getLogger(ReAct.class);
    private static final Logger conversationLogger = LoggerFactory.getLogger("com.gazapps.inference.react.ReAct.conversations");

    public void logDebug(String message, Object... args) {
        logger.debug(message, args);
    }

    public void logInferenceStart(String query, int maxIterations) {
        if (conversationLogger.isInfoEnabled()) {
            conversationLogger.info("=== REACT INFERENCE START ===");
            conversationLogger.info("Query: {}", query);
            conversationLogger.info("Max iterations: {}", maxIterations);
        }
    }

    public void logInferenceEnd(String finalAnswer, int iterationCount) {
        if (conversationLogger.isInfoEnabled()) {
            conversationLogger.info("=== REACT INFERENCE END ===");
            conversationLogger.info("Iterations completed: {}", iterationCount);
            conversationLogger.info("Final result: {}", finalAnswer);
            conversationLogger.info("===============================");
        }
    }

    public void logError(Exception e) {
        logger.error("Error processing query with ReAct", e);
        if (conversationLogger.isErrorEnabled()) {
            conversationLogger.error("=== REACT INFERENCE ERROR ===");
            conversationLogger.error("Error: {}", e.getMessage());
            conversationLogger.error("==============================");
        }
    }

    public void logIterationStart(int iteration) {
        if (conversationLogger.isInfoEnabled()) {
            conversationLogger.info("--- Iteration {} ---", iteration);
        }
    }

    public void logThought(String thought) {
        if (conversationLogger.isInfoEnabled()) {
            conversationLogger.info("Thought: {}", thought);
        }
    }

    public void logActionDecision(String actionType) {
        if (conversationLogger.isInfoEnabled()) {
            conversationLogger.info("Action Decision: {}", actionType);
        }
    }

    public void logObservation(String observation) {
        if (conversationLogger.isInfoEnabled()) {
            conversationLogger.info("Observation: {}", observation);
        }
    }

    public void logParseActionError(String message) {
        logger.debug("Error parsing action decision: {}", message);
    }

    public void logClassifyObservationError(String message) {
        logger.debug("Erro na classificação semântica, usando fallback: {}", message);
    }

    public void logClose() {
        logger.debug("[REACT] Inference strategy closed - logs salvos em JavaCLI/log/inference/");
    }
}