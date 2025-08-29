package com.gazapps;

import java.io.IOException;
import java.util.Scanner;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gazapps.chat.ChatProcessor;
import com.gazapps.config.Config;
import com.gazapps.llm.Llm;
import com.gazapps.llm.LlmBuilder;
import com.gazapps.mcp.MCPManager;
import com.github.lalyos.jfiglet.FigletFont;

/**
 * Aplicação principal do JCli com interface de chat interativo.
 */
public class JCliApp implements AutoCloseable {
    
    private static final Logger logger = LoggerFactory.getLogger(JCliApp.class);
    
    private static final Set<String> EXIT_WORDS = Set.of(
        "sair", "exit", "bye", "quit", "adeus", "tchau"
    );
    
    private final MCPManager mcpManager;
    private final Llm llm;
    private final ChatProcessor chatProcessor;
    private final Scanner scanner;
    private final Config config;
    
    public JCliApp() throws Exception {
    	starting();
        this.config = new Config();
        this.llm = LlmBuilder.gemini(null);
        this.mcpManager = new MCPManager("./config", llm);
        this.chatProcessor = new ChatProcessor(mcpManager, llm);
        this.scanner = new Scanner(System.in);
        
        showWelcome();
    }
    
    public static void main(String[] args) throws Exception {
        try (JCliApp app = new JCliApp()) {
            app.runChatLoop();
        }
    }
    
    public void runChatLoop() {
        System.out.println("""
                
                Tips for getting started:
                1. Ask questions, edit files, or run commands.
                2. Be specific for the best results.
                3. /help for more information.
                4. /llm <provider> or /stragety <strategy> to change configuration.
                """);
        
        while (true) {
            System.out.print("you: ");
            String input = scanner.nextLine().trim();
            
            if (shouldExit(input)) {
                System.out.println("👋 Goodbye!");
                break;
            }
            
            if (input.isEmpty()) {
                continue;
            }
            
            if (input.startsWith("/")) {
                chatProcessor.processCommand(input);
            } else {
                chatProcessor.processQuery(input);
            }
        }
    }
    
    private boolean shouldExit(String input) {
        return EXIT_WORDS.contains(input.toLowerCase());
    }
    
    private static void starting() throws IOException {
        System.out.println(FigletFont.convertOneLine("Java CLI"));
        System.out.println("Starting...");
    }

    
    private void showWelcome() {
        var connectedServers = mcpManager.getConnectedServers().size();
        var availableDomains = mcpManager.getAvailableDomains().size();
        
        System.out.printf("""
            
            🔧 MCPManager: %d servers connected
            🛠️ Available domains: %d  
            🤖 LLM Provider: %s
            🧠 Current Strategy: %s
            %s
            """,
            connectedServers,
            availableDomains,
            llm.getProviderName(),
            chatProcessor.getCurrentStrategy().name().toLowerCase(),
            mcpManager.isHealthy() ? "✅ System Ready" : "⚠️ Some servers unavailable"
        );
    }
    
    @Override
    public void close() {
        if (scanner != null) {
            scanner.close();
        }
        
        if (mcpManager != null) {
            mcpManager.close();
        }
        
        logger.info("JCliApp closed successfully");
    }
}
