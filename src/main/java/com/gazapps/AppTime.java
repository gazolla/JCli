package com.gazapps;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gazapps.config.Config;
import com.gazapps.inference.Inference;
import com.gazapps.inference.InferenceFactory;
import com.gazapps.inference.InferenceStrategy;
import com.gazapps.llm.Llm;
import com.gazapps.llm.LlmBuilder;

import com.gazapps.mcp.MCPManager;


public class AppTime {
    
    private static final Logger logger = LoggerFactory.getLogger(AppTime.class);
    
    public static void main(String[] args) {
        logger.info("=== Teste LLM Gemini + Inference Simple - JavaCLI/log structure ===");
        

        String configDirectory = "./config";
        new Config();
        try (MCPManager mcpManager = new MCPManager(configDirectory, LlmBuilder.createGemini(null))) {
           
            Llm geminiLlm = mcpManager.getLlm();
            
            logger.info("LLM Gemini inicializado: {}", geminiLlm.getProviderName());
            logger.info("Capacidades: {}", geminiLlm.getCapabilities());
            

            if (!geminiLlm.isHealthy()) {
                logger.warn("⚠️ LLM Gemini não está saudável - verifique configuração de API key");
            }
            
            // Opções para a estratégia Simple
            Map<String, Object> inferenceOptions = new HashMap<>();
            inferenceOptions.put("maxTools", 3);
            inferenceOptions.put("timeout", 30000);
            
            // Criar estratégia de inferência Simple
            logger.info("Criando estratégia de inferência Simple...");
            Inference simpleInference = InferenceFactory.createInference(
                InferenceStrategy.SIMPLE, 
                mcpManager, 
                geminiLlm, 
                inferenceOptions
            );
            
            // Pergunta sobre horário em Brasília
            String query = "Que horas são em Brasília, DF?";
            
            logger.info("Processando pergunta: '{}'", query);
            logger.info("Aguarde enquanto processamos a consulta...");
            
            // Processar query
            String resultado = simpleInference.processQuery(query);
            
            // Exibir resultado
            System.out.println();
            System.out.println("=".repeat(60));
            System.out.println("PERGUNTA: " + query);
            System.out.println("=".repeat(60));
            System.out.println("RESPOSTA:");
            System.out.println(resultado);
            System.out.println("=".repeat(60));
            
            logger.info("Consulta processada com sucesso");
            
            // Fechar recursos
            simpleInference.close();
            
        } catch (Exception e) {
            logger.error("Erro na aplicação de teste", e);
            System.err.println("Erro: " + e.getMessage());
            System.err.println();
            System.err.println("Possíveis causas:");
            System.err.println("1. Variável de ambiente GEMINI_API_KEY não configurada");
            System.err.println("2. API key inválida ou expirada");
            System.err.println("3. Problema de conectividade com o Gemini");
            System.err.println();
            System.err.println("Para configurar o Gemini:");
            System.err.println("1. Obtenha uma API key em: https://makersuite.google.com/app/apikey");
            System.err.println("2. Configure a variável de ambiente Windows:");
            System.err.println("   set GEMINI_API_KEY=sua_api_key_aqui");
            System.err.println("3. Reinicie o terminal/IDE após definir a variável");
            System.err.println();
            System.err.println("Verificação atual da variável:");
            String currentKey = System.getenv("GEMINI_API_KEY");
            if (currentKey != null && !currentKey.trim().isEmpty()) {
                System.err.println("   GEMINI_API_KEY = " + currentKey.substring(0, Math.min(10, currentKey.length())) + "...");
            } else {
                System.err.println("   GEMINI_API_KEY = NÃO DEFINIDA");
            }
            System.exit(1);
        }
        
        logger.info("=== Teste finalizado - logs salvos em JavaCLI/log/ ===");
    }
}
