package com.gazapps.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.joran.spi.JoranException;
import com.gazapps.llm.LlmProvider;
import java.util.ArrayList;
import java.util.List;

public class Config {

	private static final Logger logger = LoggerFactory.getLogger(Config.class);
	private static final String CONFIG_FILE = "config/application.properties";
	private Properties properties;

	public Config() {
		loadApplicationProperties();
		loadLlmProperties(); // NOVO - carregar llm.properties
		if ("true".equals(properties.getProperty("log.recreate", "false"))) {
			deleteAllLogFiles();
		}

		logger.info("\ud83d\udccb Logging configured via logback.xml - JavaCLI/log structure");
	}
	
	/**
	 * Creates complete configuration structure required for JCli Chat.
	 */
	public void createConfigStructure() {
		try {
			// Create base directories
			Files.createDirectories(Paths.get("config"));
			Files.createDirectories(Paths.get("config/mcp"));
			Files.createDirectories(Paths.get("log"));
			Files.createDirectories(Paths.get("log/llm"));
			Files.createDirectories(Paths.get("log/inference"));
			Files.createDirectories(Paths.get("documents"));
			
			// Create application.properties if it doesn't exist
			createConfigFileIfNeeded();
			
			// Create basic .mcp.json if it doesn't exist
			createMcpConfigIfNeeded();
			
			logger.info("✅ Config structure created successfully");
			
		} catch (IOException e) {
			logger.error("❌ Error creating config structure: {}", e.getMessage());
		}
	}

	private void loadApplicationProperties() {
		properties = new Properties();
		try {
			// Try to load from file
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
	
	private void loadLlmProperties() {
		String llmConfigFile = "config/llm.properties";
		try {
			if (Files.exists(Paths.get(llmConfigFile))) {
				try (InputStream input = new FileInputStream(llmConfigFile)) {
					properties.load(input);
					logger.info("📋 LLM properties loaded: {}", new File(llmConfigFile).getAbsolutePath());
				}
			} else {
				logger.info("LLM properties file not found, using defaults");
			}
		} catch (IOException e) {
			logger.error("Error loading LLM configuration: {}", e.getMessage());
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

		try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
			writer.write(defaultContent);
		}
	}
	
	/**
	 * Creates basic .mcp.json file if it doesn't exist.
	 */
	private void createMcpConfigIfNeeded() throws IOException {
		Path mcpConfigPath = Paths.get("config/mcp/.mcp.json");
		
		if (!Files.exists(mcpConfigPath)) {
			String mcpConfig = """
					{
					  "mcpServers" : {
					    "time" : {
					      "args" : [ ],
					      "domain" : "time",
					      "description" : "Server for time and timezone tools",
					      "env" : {
					        "REQUIRES_UVX" : "true"
					      },
					      "priority" : 1,
					      "command" : "uvx mcp-server-time",
					      "enabled" : true
					    },
					    "weather-nws" : {
					      "args" : [ ],
					      "domain" : "weather",
					      "description" : "Weather forecasts via NWS",
					      "env" : {
					        "REQUIRES_ONLINE" : "true",
					        "REQUIRES_NODEJS" : "true"
					      },
					      "priority" : 1,
					      "command" : "npx @h1deya/mcp-server-weather",
					      "enabled" : true
					    },
					    "filesystem" : {
					      "args" : [ ],
					      "domain" : "filesystem",
					      "description" : "File system - Documents",
					      "env" : {
					        "REQUIRES_NODEJS" : "true"
					      },
					      "priority" : 3,
					      "command" : "npx -y @modelcontextprotocol/server-filesystem ./documents",
					      "enabled" : true
					    }
					  }
					}
				""";
			
			try (FileWriter writer = new FileWriter(mcpConfigPath.toFile())) {
				writer.write(mcpConfig);
			}
			
			logger.info("✅ Default .mcp.json created");
		}
	}

	private void deleteAllLogFiles() {
		LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
		loggerContext.stop();
		File logDir = new File("log");
		if (logDir.exists() && logDir.isDirectory()) {
			deleteDirectory(logDir);
		}

		try {
			Thread.sleep(500);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}

		try {
			Files.createDirectories(Paths.get("log"));
			Files.createDirectories(Paths.get("log/llm"));
			Files.createDirectories(Paths.get("log/inference"));
		} catch (IOException e) {
			logger.error("❌ Error recreating log directories: {}", e.getMessage());
		}

		loggerContext.reset();
		JoranConfigurator configurator = new JoranConfigurator();
		configurator.setContext(loggerContext);

		URL configUrl = getClass().getClassLoader().getResource("logback.xml");

		if (configUrl != null) {
			try {
				configurator.doConfigure(configUrl);
				logger.info("✅ Logback configuration reloaded successfully!");
			} catch (JoranException e) {
				logger.error("❌ Error reloading Logback configuration: {}", e.getMessage());
			}
		} else {
			logger.error("❌ 'logback.xml' file not found in classpath.");
		}

	}

	public LlmProvider getPreferredProvider() {
		// Primeiro, tentar o provider configurado no properties
		String providerName = properties.getProperty("llm.provider", "gemini");
		try {
			LlmProvider configuredProvider = LlmProvider.fromString(providerName);
			if (isProviderConfigured(configuredProvider)) {
				return configuredProvider;
			}
		} catch (IllegalArgumentException e) {
			logger.warn("Invalid provider in config: {}", providerName);
		}
		
		// Se o provider do properties não está configurado, usar o primeiro disponível
		List<LlmProvider> configured = getConfiguredProviders();
		if (!configured.isEmpty()) {
			LlmProvider firstAvailable = configured.get(0);
			logger.info("Using first available provider: {} (preferred {} not configured)", 
					   firstAvailable, providerName);
			return firstAvailable;
		}
		
		// Fallback para GEMINI se nada estiver configurado (não deveria acontecer)
		logger.warn("No providers configured, defaulting to GEMINI");
		return LlmProvider.GEMINI;
	}
	
	public List<LlmProvider> getConfiguredProviders() {
		List<LlmProvider> configured = new ArrayList<>();
		for (LlmProvider provider : LlmProvider.values()) {
			if (isProviderConfigured(provider)) {
				configured.add(provider);
			}
		}
		return configured;
	}
	
	public boolean isProviderConfigured(LlmProvider provider) {
		String envKey = getEnvironmentKeyName(provider);
		String value = getApiKeyFromEnvironment(envKey);
		return value != null && !value.trim().isEmpty() && !value.startsWith("your_");
	}
	
	private String getEnvironmentKeyName(LlmProvider provider) {
		return switch (provider) {
			case GEMINI -> "GEMINI_API_KEY";
			case GROQ -> "GROQ_API_KEY";
			case CLAUDE -> "ANTHROPIC_API_KEY";
			case OPENAI -> "OPENAI_API_KEY";
		};
	}
	
	private String getApiKeyFromEnvironment(String envKey) {
		// 1. System property
		String value = System.getProperty(envKey);
		if (value != null && !value.trim().isEmpty()) {
			return value;
		}
		
		// 2. Environment variable  
		value = System.getenv(envKey);
		if (value != null && !value.trim().isEmpty()) {
			return value;
		}
		
		// 3. Properties file (groq.api.key, etc)
		String propKey = envKey.toLowerCase().replace("_", ".");
		value = properties.getProperty(propKey);
		if (value != null && !value.trim().isEmpty()) {
			return value;
		}
		
		return null;
	}

	private void deleteDirectory(File dir) {
		if (dir == null) {
			return;
		}

		File[] files = dir.listFiles();
		if (files != null) {
			for (File file : files) {
				if (file.isDirectory()) {
					deleteDirectory(file);
				} else {
					if (file.delete()) {
						logger.info("✅ File deleted successfully: {}", file.getAbsolutePath());
					} else {
						logger.error("❌ Failed to delete file: {}", file.getAbsolutePath());
					}
				}
			}
		}

		if (dir.delete()) {
			logger.info("✅ Directory deleted successfully: {}", dir.getAbsolutePath());
		} else {
			logger.error("❌ Failed to delete directory: {}", dir.getAbsolutePath());
		}
	}
}