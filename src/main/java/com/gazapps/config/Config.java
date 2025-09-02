package com.gazapps.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.joran.spi.JoranException;
import com.gazapps.llm.LlmConfig;
import com.gazapps.llm.LlmProvider;
import com.gazapps.mcp.MCPConfig;

public class Config {

	private static final Logger logger = LoggerFactory.getLogger(Config.class);
	private static final String CONFIG_FILE = "config/application.properties";
	
	private final Properties properties;
	private final LlmConfig llmConfig;
	private final MCPConfig mcpConfig;

	public Config() {
		// 1. Inicializar properties
		this.properties = new Properties();
		loadApplicationProperties();
		
		// 2. Inicializar componentes especializados
		this.llmConfig = new LlmConfig();
		File configDir = new File("./config"); 
		this.mcpConfig = new MCPConfig(configDir);
		
		// 3. Setup logging
		if ("true".equals(properties.getProperty("log.recreate", "false"))) {
			deleteAllLogFiles();
		}

		logger.info("📋 Configuration initialized with LlmConfig and MCPConfig");
	}
	
	// ✅ DELEGAR para LlmConfig
	public LlmProvider getPreferredProvider() {
		// Tentar provider configurado no properties
		String providerName = properties.getProperty("llm.provider", "gemini");
		try {
			LlmProvider configuredProvider = LlmProvider.fromString(providerName);
			if (llmConfig.isLlmConfigValid(configuredProvider)) {
				return configuredProvider;
			}
		} catch (IllegalArgumentException e) {
			logger.warn("Invalid provider in config: {}", providerName);
		}
		
		// Usar primeiro disponível
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
	
	// ✅ DELEGAR para LlmConfig
	public List<LlmProvider> getConfiguredProviders() {
		return llmConfig.getAllConfiguredProviders();
	}
	
	// ✅ DELEGAR para LlmConfig
	public boolean isProviderConfigured(LlmProvider provider) {
		return llmConfig.isLlmConfigValid(provider);
	}
	
	// ✅ SIMPLIFICAR - apenas delegar
	public void createConfigStructure() {
		try {
			// MCPConfig já cria toda estrutura necessária
			logger.info("✅ Config structure created successfully");
		} catch (Exception e) {
			logger.error("❌ Error creating config structure: {}", e.getMessage());
		}
	}
	
	// ✅ EXPOR acesso aos componentes se necessário
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
