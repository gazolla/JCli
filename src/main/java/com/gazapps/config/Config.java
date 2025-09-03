package com.gazapps.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gazapps.llm.LlmConfig;
import com.gazapps.llm.LlmProvider;
import com.gazapps.mcp.MCPConfig;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.joran.spi.JoranException;

public class Config {

	private static final Logger logger = LoggerFactory.getLogger(Config.class);
	private static final String CONFIG_FILE = "config/application.properties";
	
	private final Properties properties;
	private final LlmConfig llmConfig;
	private final MCPConfig mcpConfig;

	public Config() {
		this.properties = new Properties();
		loadApplicationProperties();
		
		this.llmConfig = new LlmConfig();
		File configDir = new File("./config"); 
		this.mcpConfig = new MCPConfig(configDir);
		
		if ("true".equals(properties.getProperty("log.recreate", "false"))) {
			deleteAllLogFiles();
		}

		logger.info("📋 Configuration initialized with LlmConfig and MCPConfig");
	}
	
	public LlmProvider getPreferredProvider() {
		String providerName = properties.getProperty("llm.provider", "gemini");
		try {
			LlmProvider configuredProvider = LlmProvider.fromString(providerName);
			if (llmConfig.isLlmConfigValid(configuredProvider)) {
				return configuredProvider;
			}
		} catch (IllegalArgumentException e) {
			logger.warn("Invalid provider in config: {}", providerName);
		}
		
		List<LlmProvider> configured = llmConfig.getAllConfiguredProviders();
		if (!configured.isEmpty()) {
			LlmProvider firstAvailable = configured.get(0);
			logger.info("Using first available provider: {} (preferred {} not configured)", 
					   firstAvailable, providerName);
			return firstAvailable;
		}
		
		logger.warn("No providers configured, defaulting to GEMINI");
		return LlmProvider.GEMINI;
	}
	
	public List<LlmProvider> getConfiguredProviders() {
		return llmConfig.getAllConfiguredProviders();
	}
	
	public boolean isProviderConfigured(LlmProvider provider) {
		return llmConfig.isLlmConfigValid(provider);
	}
	
	
	public LlmConfig getLlmConfig() {
		return llmConfig;
	}
	
	public MCPConfig getMcpConfig() {
		return mcpConfig;
	}

	private void loadApplicationProperties() {
		try {
			if (Files.exists(Paths.get(CONFIG_FILE))) {
				try (InputStream input = new FileInputStream(CONFIG_FILE)) {
					properties.load(input);
					logger.info("📋 Application properties loaded: {}", new File(CONFIG_FILE).getAbsolutePath());
				}
			} else {
				logger.warn("Configuration file not found: {}", CONFIG_FILE);
				createConfigFileIfNeeded();
			}
		} catch (IOException e) {
			logger.error("Error loading configuration: {}", e.getMessage());
		}
	}

	public void createConfigFileIfNeeded() {
		try {
			Path configPath = Paths.get(CONFIG_FILE);
			if (!Files.exists(configPath)) {
				Files.createDirectories(configPath.getParent());
				createDefaultApplicationProperties();
				logger.info("✅ Default application.properties created");
			}
		} catch (IOException e) {
			logger.error("Error creating config file: {}", e.getMessage());
		}
	}

	private void createDefaultApplicationProperties() throws IOException {
		String defaultContent = """
				# JCLI Application Configuration
				# Application Info
				app.name=JCLI
				app.version=0.0.1

				# MCP Application Configuration
				llm.provider=groq
				mcp.auto.discovery=true
				mcp.refresh.interval=30000
				mcp.connection.timeout=30000
				mcp.call.timeout=60000

				# Rules Configuration
				mcp.rules.enabled=true
				mcp.rules.path=config/rules
				mcp.rules.auto.reload=false

				# Logging Configuration
				logging.level.com.gazapps.mcp=DEBUG
				logging.level.root=INFO
				log.recreate=true
				""";

		try (java.io.FileWriter writer = new java.io.FileWriter(CONFIG_FILE)) {
			writer.write(defaultContent);
		}
	}

	private void deleteAllLogFiles() {
	    List<String> logMessages = new ArrayList<>(); // Buffer log messages
	    List<String> errorMessages = new ArrayList<>(); // Buffer error messages

	    LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
	    logMessages.add("Stopping LoggerContext to release log file handles");
	    loggerContext.stop();

	    Path logDir = Paths.get("log");
	    logMessages.add("Attempting to delete log directory: " + logDir.toAbsolutePath());

	    try {
	        // Delete log directory and contents
	        if (Files.exists(logDir)) {
	            Files.walk(logDir)
	                .sorted((p1, p2) -> -p1.compareTo(p2)) // Delete files before directories
	                .forEach(path -> {
	                    int retries = 3;
	                    while (retries > 0) {
	                        try {
	                            Files.delete(path);
	                            logMessages.add("✅ Deleted: " + path.toAbsolutePath());
	                            break;
	                        } catch (IOException e) {
	                            retries--;
	                            if (retries == 0) {
	                                errorMessages.add("❌ Failed to delete: " + path.toAbsolutePath() + ". Reason: " + e.getMessage());
	                            } else {
	                                try {
	                                    Thread.sleep(100); // Wait before retry
	                                } catch (InterruptedException ie) {
	                                    Thread.currentThread().interrupt();
	                                }
	                            }
	                        }
	                    }
	                });
	        }

	        // Recreate log directories
	        Files.createDirectories(logDir);
	        Files.createDirectories(logDir.resolve("llm"));
	        Files.createDirectories(logDir.resolve("inference"));
	        logMessages.add("✅ Log directories recreated: log, log/llm, log/inference");

	        // Reload Logback configuration
	        loggerContext.reset();
	        JoranConfigurator configurator = new JoranConfigurator();
	        configurator.setContext(loggerContext);

	        URL configUrl = getClass().getClassLoader().getResource("logback.xml");
	        if (configUrl != null) {
	            configurator.doConfigure(configUrl);
	            logMessages.add("✅ Logback configuration reloaded from: " + configUrl);
	        } else {
	            errorMessages.add("❌ 'logback.xml' not found in classpath. Logging may not work as expected.");
	        }

	    } catch (IOException | JoranException e) {
	        errorMessages.add("❌ Error during log file recreation: " + e.getMessage());
	    }

	    // Write buffered messages after LoggerContext is reset
	    Logger postResetLogger = LoggerFactory.getLogger(Config.class);
	    logMessages.forEach(msg -> postResetLogger.info(msg));
	    errorMessages.forEach(msg -> postResetLogger.error(msg));
	}
}
