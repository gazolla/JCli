package com.gazapps.chat;

import com.gazapps.inference.InferenceStrategy;
import com.gazapps.llm.Llm;
import com.gazapps.llm.LlmBuilder;
import com.gazapps.mcp.MCPManager;

public class CommandHandler {

	private final ChatProcessor chatProcessor;
	private final MCPManager mcpManager;

	public CommandHandler(ChatProcessor chatProcessor, MCPManager mcpManager) {
		this.chatProcessor = chatProcessor;
		this.mcpManager = mcpManager;
	}

	public void handle(String command) {
		String[] parts = command.substring(1).split("\\s+", 2);
		String cmd = parts[0].toLowerCase();
		String args = parts.length > 1 ? parts[1] : "";

		switch (cmd) {
		case "help" -> showHelp();
		case "status" -> showStatus();
		case "tools" -> showTools();
		case "servers" -> showServers();
		case "strategy" -> changeStrategy(args);
		case "llm" -> changeLlm(args);
		case "debug" -> toggleDebug();
		case "clear" -> clearScreen();
		case "quit", "exit" -> System.exit(0);
		default -> showUnknownCommand(cmd);
		}
	}

	private void changeLlm(String providerName) {
		if (providerName.trim().isEmpty()) {
			System.out.println("❌ LLM provider name required. Available: openai, claude, gemini, groq");
			System.out.printf("   Current provider: %s%n", chatProcessor.getCurrentLlm().getProviderName());
			return;
		}

		String provider = providerName.toLowerCase().trim();
		String currentProvider = chatProcessor.getCurrentLlm().getProviderName().toString().toLowerCase();

		if (currentProvider.equals(provider)) {
			System.out.printf("✅ Already using %s%n", provider);
			return;
		}

		try {
			System.out.printf("🔄 Switching from %s to %s...%n", currentProvider, provider);

			Llm newLlm = switch (provider) {
			case "openai" -> LlmBuilder.openai(null);
			case "claude" -> LlmBuilder.claude(null);
			case "gemini" -> LlmBuilder.gemini(null);
			case "groq" -> LlmBuilder.groq(null);
			default -> {
				System.out.printf("❌ Unknown LLM provider: %s%n", provider);
				System.out.println("   Available providers: openai, claude, gemini, groq");
				yield null;
			}
			};

			if (newLlm != null) {
				boolean success = chatProcessor.changeLlm(newLlm);

				if (success) {
					System.out.printf("✅ Successfully switched to %s%n", provider);

					System.out.printf("   Provider: %s%n", newLlm.getProviderName());
					if (newLlm.getCapabilities() != null) {
						System.out.printf("   Capabilities: %s%n", newLlm.getCapabilities());
					}
				} else {
					System.out.println("❌ Failed to switch LLM provider");
					System.out.printf("   Staying with %s%n", currentProvider);
				}
			}

		} catch (Exception e) {
			System.out.printf("❌ Error switching LLM: %s%n", e.getMessage());
			System.out.printf("   Staying with %s%n", currentProvider);
		}
	}

	private void showHelp() {
		System.out.println("""
				🔧 JCli Commands:

				/help                    - Show this help
				/status                  - System status
				/tools                   - List MCP tools
				/servers                 - List MCP servers
				/strategy <name>         - Change inference (simple|react|reflection)
				/llm <provider>          - Change LLM provider (openai|claude|gemini|groq)
				/debug                   - Toggle debug mode
				/clear                   - Clear screen
				/quit                    - Exit

				💡 Just type your questions naturally!
				""");
	}

	private void showStatus() {
		var servers = mcpManager.getConnectedServers();
		var domains = mcpManager.getAvailableDomains();

		System.out.printf("""
				📊 System Status:
				🖥️ Servers: %d connected
				🛠️ Tools: Available across %d domains
				🤖 LLM: %s
				🧠 Strategy: %s
				🔧 Debug: %s
				⚡ Health: %s
				%n""", servers.size(), domains.size(), chatProcessor.getCurrentLlm().getProviderName(),
				chatProcessor.getCurrentStrategy().name().toLowerCase(), chatProcessor.isDebugMode() ? "ON" : "OFF",
				mcpManager.isHealthy() ? "✅ Healthy" : "❌ Issues");
	}

	private void showTools() {
		var domains = mcpManager.getAvailableDomains();

		System.out.println("🔧 Available Tools:");
		for (String domain : domains) {
			var tools = mcpManager.getToolsByDomain(domain);
			if (!tools.isEmpty()) {
				System.out.printf("%n📁 %s Domain:%n", domain);
				tools.forEach(tool -> System.out.printf("  • %s - %s%n", tool.getName(), tool.getDescription()));
			}
		}
		System.out.println();
	}

	private void showServers() {
		var servers = mcpManager.getConnectedServers();

		System.out.println("🖥️ MCP Servers:");
		if (servers.isEmpty()) {
			System.out.println("  No servers connected");
		} else {
			servers.values().forEach(server -> System.out.printf("  • %s (%s) - %d tools%n", server.getId(),
					server.isHealthy() ? "✅" : "❌", server.getTools().size()));
		}
		System.out.println();
	}

	private void changeStrategy(String strategyName) {
		if (strategyName.trim().isEmpty()) {
			System.out.println("❌ Strategy name required. Available: simple, react, reflection");
			return;
		}

		try {
			InferenceStrategy strategy = InferenceStrategy.fromString(strategyName);
			chatProcessor.setCurrentStrategy(strategy);
			System.out.printf("✅ Strategy changed to: %s%n", strategy.name().toLowerCase());
		} catch (IllegalArgumentException e) {
			System.out.println("❌ Invalid strategy. Available: simple, react, reflection");
		}
	}

	private void toggleDebug() {
		boolean oldState = chatProcessor.isDebugMode();
		boolean newState = chatProcessor.toggleDebug();
		System.out.printf("🔧 Debug mode: %s → %s%n", oldState ? "ON" : "OFF", newState ? "ON" : "OFF");
	}

	private void clearScreen() {
		System.out.print("\\033[2J\\033[H");
		System.out.flush();
	}

	private void showUnknownCommand(String cmd) {
		System.out.printf("❌ Unknown command: %s. Type /help for available commands.%n", cmd);
	}
}
