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

public class Config {

	private static final Logger logger = LoggerFactory.getLogger(Config.class);
	private static final String CONFIG_FILE = "config/application.properties";
	private Properties properties;

	public Config() {
		loadApplicationProperties();
		if ("true".equals(properties.getProperty("log.recreate", "false"))) {
			deleteAllLogFiles();
		}

		logger.info("\ud83d\udccb Logging configured via logback.xml - JavaCLI/log structure");
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

				# Configuraca£o da aplicacao MCP
				llm.provider=groq
				mcp.auto.discovery=true
				mcp.refresh.interval=30000
				mcp.connection.timeout=30000
				mcp.call.timeout=60000

				# Configuraca£o de regras
				mcp.rules.enabled=true
				mcp.rules.path=config/rules
				mcp.rules.auto.reload=false

				# Configuracao de logging
				logging.level.com.gazapps.mcp=DEBUG
				logging.level.root=INFO
				log.recreate=true
				""";

		try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
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
			logger.error("❌ Erro ao recriar diretórios de log: {}", e.getMessage());
		}

		loggerContext.reset();
		JoranConfigurator configurator = new JoranConfigurator();
		configurator.setContext(loggerContext);

		URL configUrl = getClass().getClassLoader().getResource("logback.xml");

		if (configUrl != null) {
			try {
				configurator.doConfigure(configUrl);
				logger.info("✅ Configuração do Logback recarregada com sucesso!");
			} catch (JoranException e) {
				logger.error("❌ Erro ao recarregar a configuração do Logback: {}", e.getMessage());
			}
		} else {
			logger.error("❌ Arquivo 'logback.xml' não encontrado no classpath.");
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
						logger.info("✅ Arquivo deletado com sucesso: {}", file.getAbsolutePath());
					} else {
						logger.error("❌ Falha ao deletar arquivo: {}", file.getAbsolutePath());
					}
				}
			}
		}

		// AQUI ESTÁ A CHAVE: APAGA O DIRETÓRIO APÓS ELE ESTAR VAZIO
		if (dir.delete()) {
			logger.info("✅ Diretório deletado com sucesso: {}", dir.getAbsolutePath());
		} else {
			logger.error("❌ Falha ao deletar diretório: {}", dir.getAbsolutePath());
		}
	}
}