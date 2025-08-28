package com.gazapps;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gazapps.config.Config;
import com.gazapps.inference.Inference;
import com.gazapps.inference.InferenceFactory;
import com.gazapps.llm.Llm;
import com.gazapps.llm.LlmBuilder;
import com.gazapps.mcp.MCPManager;

public class AppMultiToolWeatherFile {
	
	private static final Logger logger = LoggerFactory.getLogger(AppFile.class);

	public static void main(String[] args) {
		logger.info("=== Teste LLM Gemini + Inference Simple - JavaCLI/log structure ===");
		
        String configDirectory = "./config";
        new Config();
        try (MCPManager mcpManager = new MCPManager(configDirectory, LlmBuilder.createGemini(null))) {
        	
            Llm llm = mcpManager.getLlm();

            Map<String, Object> inferenceOptions = Map.of("maxIterations", 10, "debug", false);
            Inference inference = InferenceFactory.createReAct(mcpManager, 
            													llm, 
            													inferenceOptions);
            
            String userInput = "verifique o clima em NYC e salve no arquivo nyc.txt em documents";
            
            String response = inference.processQuery(userInput);
            
            System.out.println();
            System.out.println("=".repeat(60));
            System.out.println("PERGUNTA: " + userInput);
            System.out.println("=".repeat(60));
            System.out.println("RESPOSTA:");
            System.out.println(response);
            System.out.println("=".repeat(60));
            
            logger.info("Consulta processada com sucesso");
        }
	}
}
