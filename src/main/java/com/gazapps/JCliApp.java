package com.gazapps;

import java.io.File;
import java.io.IOException;
import java.util.Scanner;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gazapps.chat.ChatProcessor;
import com.gazapps.config.Config;
import com.gazapps.config.EnvironmentSetup;
import com.gazapps.exceptions.ConfigurationException;
import com.gazapps.llm.Llm;
import com.gazapps.llm.LlmBuilder;
import com.gazapps.llm.LlmProvider;
import com.gazapps.mcp.MCPManager;
import com.gazapps.session.SessionManager;
import com.github.lalyos.jfiglet.FigletFont;

public class JCliApp implements AutoCloseable {
    
    private static final Logger logger = LoggerFactory.getLogger(JCliApp.class);
    
    private static final Set<String> EXIT_WORDS = Set.of(
        "sair", "exit", "bye", "quit", "adeus", "tchau"
    );
    
    private final MCPManager mcpManager;
    private final Llm llm;
    private final SessionManager sessionManager;
    private final ChatProcessor chatProcessor;
    private final Scanner scanner;
    private final Config config;
    private final File configDir = new File("./config"); 
    
    public JCliApp() throws Exception {
    	starting();
    	
    	if (!EnvironmentSetup.ensureConfigurationReady()) {
    		throw new ConfigurationException("Cannot start without proper configuration");
    	}
    	
        this.config = new Config();
        
        LlmProvider preferredProvider = config.getPreferredProvider();
        this.llm = createLlmInstance(preferredProvider);
        
        this.mcpManager = new MCPManager(configDir, llm);
        this.sessionManager = new SessionManager(config);
        this.chatProcessor = new ChatProcessor(mcpManager, llm, sessionManager);
        this.scanner = new Scanner(System.in);
        
        // Create or load default session
        initializeDefaultSession();
        
        showWelcome();
    }
    
    private Llm createLlmInstance(LlmProvider provider) {
        System.out.printf("🤖 Using preferred provider: %s%n", provider.name());
        return switch (provider) {
            case GEMINI -> LlmBuilder.gemini(null);
            case GROQ -> LlmBuilder.groq(null);
            case CLAUDE -> LlmBuilder.claude(null);
            case OPENAI -> LlmBuilder.openai(null);
        };
    }
    
    /**
     * Initializes a default session or loads the most recent one.
     */
    private void initializeDefaultSession() {
        try {
            // Try to load the most recent session
            var sessions = sessionManager.listSessions();
            
            if (!sessions.isEmpty()) {
                // Load the most recently accessed session
                var mostRecent = sessions.get(0); // Sessions are ordered by last access desc
                sessionManager.loadSession(mostRecent.id);
                System.out.printf("📂 Resumed session '%s'%n", mostRecent.name);
            } else {
                // Create a default session
                String defaultName = config.getSessionDefaultName();
                sessionManager.createNewSession(defaultName, llm, 
                                               chatProcessor.getCurrentStrategy(), 
                                               mcpManager);
                System.out.printf("✨ Created default session '%s'%n", defaultName);
            }
        } catch (Exception e) {
            logger.warn("Failed to initialize default session: {}", e.getMessage());
            // Continue without session - user can create one manually
        }
    }
    
    public static void main(String[] args) throws Exception {
        try (JCliApp app = new JCliApp()) {
            app.runChatLoop();
        }
    }
    
    public void runChatLoop() {

          //  var jtp = new JavaTree();
          //  jtp.printTree(configDir, "");

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
        
        // Get current session info
        var currentSession = sessionManager.getCurrentSession();
        String sessionInfo = currentSession.map(s -> 
            String.format("💬 Session: %s (%d messages)", s.getName(), s.getMessages().size())
        ).orElse("⚠️ No active session");
        
        System.out.printf("""
            
            🔧 MCPManager: %d servers connected
            🛠️ Available domains: %d  
            🤖 LLM Provider: %s
            🧠 Current Strategy: %s
            %s
            %s
            """,
            connectedServers,
            availableDomains,
            llm.getProviderName(),
            chatProcessor.getCurrentStrategy().name().toLowerCase(),
            sessionInfo,
            mcpManager.isHealthy() ? "✅ System Ready" : "⚠️ Some servers unavailable"
        );
    }
    
    @Override
    public void close() {
        if (scanner != null) {
            scanner.close();
        }
        
        if (sessionManager != null) {
            sessionManager.close();
        }
        
        if (mcpManager != null) {
            mcpManager.close();
        }
        
        EnvironmentSetup.cleanup();
        logger.info("JCliApp closed successfully");
    }
}
