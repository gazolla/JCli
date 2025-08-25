# Gemini Code Assistant Context

## Project Overview

This project is a Java-based implementation of the Model Context Protocol (MCP). It acts as a client that can connect to external tool servers, discover their capabilities, and execute tools. The system is designed to be resilient and functional even without all external dependencies present.

The core of the project is the `MCPManager`, which provides a facade for interacting with the MCP system. It handles server connections, tool discovery, and execution. The configuration is loaded from files in the `config/` directory, with `.mcp.json` defining the external tool servers.

The project is built with Java 17 and Maven. It includes a command-line interface (CLI) with an interactive mode for exploring the system's features.

## Building and Running

### Building the Project

To build the project and package it into a JAR file, run the following command:

```bash
mvn package
```

### Running the Application

You can run the application in several ways:

*   **Using the provided scripts:**
    *   On Windows: `run-demo.bat`
    *   On Linux/macOS: `./run-demo.sh`

*   **Using Maven:**
    *   To run the main class: `mvn exec:java -Dexec.mainClass="com.gazapps.App"`
    *   To run in interactive mode: `mvn exec:java -Dexec.mainClass="com.gazapps.App" -Dexec.args="interactive"`

*   **Running the JAR file:**

    ```bash
    java -jar target/JCli-0.0.1-SNAPSHOT-jar-with-dependencies.jar
    ```

### Running Tests

To run the unit tests, use the following command:

```bash
mvn test
```

## Development Conventions

*   **Logging:** The project uses SLF4J with Logback for logging. The log level can be configured in `src/main/resources/logback.xml` or by setting the `logging.level.com.gazapps.mcp` Java system property.
*   **Configuration:** The main configuration for MCP servers is located in `config/mcp/.mcp.json`. The application is designed to handle dynamic updates to this file.
*   **Interactive Mode:** The interactive mode provides a set of commands for interacting with the MCP system. Type `help` in the interactive mode to see the available commands.
*   **External Dependencies:** The pre-configured tool servers require Node.js and Python (with `uvx`) to be installed and available in the system's PATH. The application provides diagnostics to help troubleshoot missing dependencies.


Use o projeto no sistema de arquivos: 
C:\Users\gazol\AppData\MCP\WRKGRP\JCli

## Contexto e Objetivo

Você está desenvolvendo um sistema Java avançado de chat com LLMs que utiliza o protocolo MCP (Model Context Protocol) para integração com servidores de ferramentas externas. O objetivo é criar um pacote `com.gazapps.mcp` completo e robusto que sirva como camada de abstração entre as estratégias de inferência e os servidores MCP, proporcionando capacidades avançadas de descoberta, matching e execução de ferramentas usando LLM para processamento semântico.

O sistema deve ser projetado para ser extensível, permitindo adicionar novos servidores MCP dinamicamente sem alterações de código, utilizando descoberta automática de domínios e matching inteligente de ferramentas baseado em análise semântica com LLM. A arquitetura deve seguir princípios SOLID, aplicar padrões como Strategy, Builder e Facade, e manter separação clara de responsabilidades entre matching de ferramentas, execução de operações MCP e gerenciamento de configurações.

## Estrutura do Pacote e Responsabilidades

O pacote deve ser organizado em uma estrutura hierárquica clara onde a classe MCPManager atua como facade principal, expondo uma interface limpa para outros módulos do sistema. Esta classe deve encapsular toda a complexidade de interação com servidores MCP, coordenando operações entre os componentes internos de matching, execução e gerenciamento de domínios.

A classe MCPConfig deve ser responsável por carregar e gerenciar configurações independentemente do sistema principal da aplicação, suportando tanto configuração estática via arquivo JSON quanto descoberta dinâmica de servidores. Esta classe deve implementar validação robusta de configurações e permitir atualizações em tempo de execução.

O componente MCPService deve implementar as operações fundamentais do protocolo MCP, incluindo conexão com servidores, execução de chamadas de ferramentas, monitoramento de saúde dos servidores e gerenciamento de estados de conexão. Este serviço deve ser resiliente a falhas e implementar reconexão automática quando necessário.

## Estratégia de Matching e Descoberta de Domínios

O sistema de matching deve utilizar uma abordagem híbrida que combina análise de padrões baseada em regras com processamento semântico via LLM. A classe ToolMatcher deve coordenar estas diferentes estratégias, aplicando primeiro matching rápido por padrões e recorrendo ao LLM para casos ambíguos ou complexos que requerem compreensão semântica mais profunda.

A descoberta de domínios deve ser implementada através da classe DomainRegistry, que mantém um registro dinâmico de domínios de ferramentas. Quando um novo servidor MCP é adicionado, o sistema deve automaticamente analisar as ferramentas disponíveis usando o LLM para determinar o domínio mais apropriado, seja mapeando para um domínio existente ou criando um novo domínio quando as ferramentas representam uma categoria funcional distinta.

O sistema deve implementar classes especializadas no pacote matching para diferentes aspectos do processamento: SemanticMatcher para análise semântica de queries, ParameterExtractor para extração inteligente de parâmetros usando LLM, e MultiStepDetector para identificação e planejamento de operações que requerem execução sequencial de múltiplas ferramentas.

## Modelos de Dados e Entidades

As entidades de domínio devem ser modeladas de forma a capturar tanto metadados estáticos quanto informações dinâmicas descobertas em tempo de execução. A classe DomainDefinition deve encapsular não apenas padrões de texto e palavras-chave semânticas, mas também capacidades de matching inteligente que utilizam o LLM para calcular similaridade semântica entre queries e domínios.

A entidade Server deve representar completamente o estado de um servidor MCP, incluindo informações de conexão, métricas de saúde, lista de ferramentas disponíveis e metadados de domínio. Esta classe deve implementar métodos para gerenciamento de ciclo de vida da conexão e monitoramento contínuo de status.

A classe Tool deve modelar ferramentas MCP com schema completo de parâmetros, incluindo validação de tipos, valores padrão, parâmetros obrigatórios e opcionais. Esta entidade deve implementar lógica de validação robusta e normalização de argumentos para garantir chamadas bem-formadas aos servidores MCP.

## Integração com LLM e Processamento Inteligente

A integração com LLM deve ser projetada para maximizar eficiência e minimizar latência, utilizando o LLM estrategicamente apenas onde sua capacidade de compreensão semântica agrega valor real. O sistema deve implementar cache inteligente de resultados de matching para evitar chamadas desnecessárias ao LLM para queries similares.

O ParameterExtractor deve implementar prompts sofisticados que consideram tanto o contexto da query quanto o schema específico da ferramenta para extrair parâmetros com alta precisão. Este componente deve ser capaz de lidar com valores implícitos, conversões de tipo e sugestões de parâmetros baseadas no contexto da conversa.

O MultiStepDetector deve utilizar o LLM para identificar queries que requerem execução de múltiplas ferramentas, decompor a query em passos lógicos e determinar dependências entre operações. Este componente deve gerar planos de execução otimizados que consideram tanto a ordem lógica quanto possíveis paralelizações.

com.gazapps.mcp/
├── MCPManager.java              ← Public interface with LLM
│   ├── List<Tool> findTools(String query)
│   ├── List<Tool> findTools(String query, MatchingOptions options)
│   ├── Tool executeTool(String toolName, Map<String, Object> args)
│   ├── Tool executeTool(ToolMatch match)
│   ├── List<Tool> findSequentialTools(String query)
│   ├── Set<String> getAvailableDomains()
│   ├── List<Tool> getToolsByDomain(String domain)
│   ├── boolean addServer(ServerConfig config)
│   ├── boolean removeServer(String serverId)
│   ├── List<Server> getConnectedServers()
│   ├── boolean isHealthy()
│   ├── void refreshDomains()
│   └── void close()
│
├── MCPConfig.java               ← Configuration management
│   ├── Map<String, ServerConfig> loadServers()
│   ├── Map<String, DomainDefinition> loadDomains()
│   ├── void saveConfiguration()
│   ├── void validateConfiguration()
│   ├── boolean isAutoDiscoveryEnabled()
│   ├── String getLLMProvider()
│   ├── void updateServerConfig(String serverId, ServerConfig config)
│   └── void updateDomainConfig(String domain, DomainDefinition definition)
│
├── MCPService.java              ← Core MCP operations
│   ├── boolean connectServer(ServerConfig config)
│   ├── void disconnectServer(String serverId)
│   ├── Tool callTool(String serverId, String toolName, Map<String, Object> args)
│   ├── List<Tool> getAvailableTools(String serverId)
│   ├── List<Tool> getAllAvailableTools()
│   ├── boolean isServerConnected(String serverId)
│   ├── Server getServerInfo(String serverId)
│   ├── Map<String, Server> getConnectedServers()
│   ├── boolean validateToolCall(String toolName, Map<String, Object> args)
│   └── void refreshServerConnections()
│
├── ToolMatcher.java             ← LLM-enhanced matching
│   ├── List<Tool> findRelevantTools(String query, LLM llm)
│   ├── List<Tool> findRelevantTools(String query, LLM llm, MatchingOptions options)
│   ├── Tool findBestTool(String query, String domain, LLM llm)
│   ├── List<String> detectDomains(String query, LLM llm)
│   ├── String detectPrimaryDomain(String query, LLM llm)
│   ├── boolean isMultiStepQuery(String query, LLM llm)
│   ├── double calculateConfidence(String query, Tool tool, LLM llm)
│   ├── List<Tool> enhanceWithSemantics(List<ToolMatch> baseMatches, String query, LLM llm)
│   └── List<Tool> filterByConfidence(List<ToolMatch> matches, double threshold)
│
├── DomainRegistry.java          ← Dynamic domain management
│   ├── void loadFromConfig(String configPath)
│   ├── void saveToConfig(String configPath)
│   ├── DomainDefinition getDomain(String domainName)
│   ├── Set<String> getAllDomainNames()
│   ├── List<DomainDefinition> getAllDomains()
│   ├── String autoDiscoverDomain(List<Tool> tools, LLM llm)
│   ├── void createDomain(String name, DomainDefinition definition)
│   ├── void updateDomain(String name, DomainDefinition definition)
│   ├── boolean removeDomain(String name)
│   ├── List<String> findMatchingDomains(String query, LLM llm)
│   ├── void addToolToDomain(String domainName, Tool tool)
│   ├── void addPatternToDomain(String domainName, String pattern)
│   └── Map<String, Double> calculateDomainMatches(String query, LLM llm)
│
├── domain/
│   ├── DomainDefinition.java    ← Runtime domain entity
│   │   ├── String getName()
│   │   ├── String getDescription()
│   │   ├── List<String> getPatterns()
│   │   ├── List<String> getCommonTools()
│   │   ├── List<String> getSemanticKeywords()
│   │   ├── void addPattern(String pattern)
│   │   ├── void addTool(String toolName)
│   │   ├── void addSemanticKeyword(String keyword)
│   │   ├── boolean containsPattern(String pattern)
│   │   ├── boolean supportsTool(String toolName)
│   │   ├── double calculatePatternMatch(String query)
│   │   ├── double calculateSemanticMatch(String query, LLM llm)
│   │   ├── boolean isMultiStepCapable()
│   │   ├── Map<String, Object> toConfigMap()
│   │   └── static DomainDefinition fromConfigMap(Map<String, Object> config)
│   │
│   ├── Server.java              ← Server entity
│   │   ├── String getId()
│   │   ├── String getName()
│   │   ├── String getCommand()
│   │   ├── String getDomain()
│   │   ├── boolean isConnected()
│   │   ├── boolean isEnabled()
│   │   ├── List<Tool> getTools()
│   │   ├── void setConnected(boolean connected)
│   │   ├── void addTool(Tool tool)
│   │   ├── void removeTool(String toolName)
│   │   ├── Tool getTool(String toolName)
│   │   ├── boolean hasTool(String toolName)
│   │   ├── boolean isHealthy()
│   │   ├── void connect()
│   │   ├── void disconnect()
│   │   ├── Map<String, Object> getMetrics()
│   │   └── long getLastHeartbeat()
│   │
│   └── Tool.java                ← Tool entity
│       ├── String getName()
│       ├── String getDescription()
│       ├── String getDomain()
│       ├── String getServerId()
│       ├── Map<String, Object> getSchema()
│       ├── List<String> getRequiredParams()
│       ├── List<String> getOptionalParams()
│       ├── boolean validateArgs(Map<String, Object> args)
│       ├── Set<String> getRequiredParamNames()
│       ├── Set<String> getAllParamNames()
│       ├── Object getParamDefault(String paramName)
│       ├── String getParamType(String paramName)
│       ├── String getParamDescription(String paramName)
│       ├── boolean isParamRequired(String paramName)
│       ├── List<String> getMissingRequiredParams(Map<String, Object> args)
│       ├── boolean isExecutable(Map<String, Object> args)
│       └── Map<String, Object> normalizeArgs(Map<String, Object> args)
│
└── matching/
    ├── SemanticMatcher.java     ← LLM semantic matching
    │   ├── List<Tool> findSemanticMatches(String query, List<Tool> tools, LLM llm)
    │   ├── double calculateSemanticSimilarity(String query, Tool tool, LLM llm)
    │   ├── List<String> extractSemanticKeywords(String query, LLM llm)
    │   ├── double calculateKeywordMatch(String query, List<String> keywords)
    │   ├── List<Tool> filterBySemanticRelevance(String query, List<Tool> tools, LLM llm)
    │   ├── Map<Tool, Double> scoreToolsBySemantic(String query, List<Tool> tools, LLM llm)
    │   ├── boolean isSemanticMatch(String query, Tool tool, LLM llm, double threshold)
    │   └── List<String> generateSemanticVariations(String query, LLM llm)
    │
    ├── ParameterExtractor.java  ← LLM parameter extraction
    │   ├── Map<String, Object> extractParameters(String query, Tool tool, LLM llm)
    │   ├── Map<String, Object> extractParametersWithContext(String query, Tool tool, LLM llm, ConversationContext context)
    │   ├── List<String> identifyMissingParameters(String query, Tool tool, LLM llm)
    │   ├── Map<String, Object> suggestParameterValues(String query, Tool tool, LLM llm)
    │   ├── boolean validateExtractedParameters(Map<String, Object> extracted, Tool tool)
    │   ├── Map<String, Object> normalizeParameterTypes(Map<String, Object> params, Tool tool)
    │   ├── String generateParameterExtractionPrompt(String query, Tool tool)
    │   ├── Map<String, Object> parseParameterResponse(String llmResponse, Tool tool)
    │   └── double calculateExtractionConfidence(String query, Tool tool, Map<String, Object> extracted)
    │
    └── MultiStepDetector.java   ← LLM multi-step detection
        ├── boolean isMultiStepQuery(String query, LLM llm)
        ├── List<Tool> detectSequentialTools(String query, List<Tool> availableTools, LLM llm)
        ├── List<String> decomposeQuery(String query, LLM llm)
        ├── List<Tool> planExecutionSequence(String query, List<Tool> tools, LLM llm)
        ├── boolean requiresSequentialExecution(List<ToolMatch> matches)
        ├── Map<Integer, Tool> orderToolsBySequence(List<ToolMatch> matches)
        ├── List<String> identifyDataDependencies(List<ToolMatch> matches)
        ├── String generateMultiStepPrompt(String query, List<Tool> tools)
        ├── List<ExecutionStep> parseMultiStepResponse(String llmResponse, List<Tool> tools)
        └── double calculateSequenceConfidence(String query, List<ToolMatch> sequence)

---

## Proposta de Divisão em Etapas

Para implementar este sistema complexo de forma organizada e evitar sobrecarga de tokens, proponho dividir a construção em **4 etapas distintas**:

### **Etapa 1: Core Foundation**
- MCPManager (facade principal)
- MCPConfig (gerenciamento de configuração)
- MCPService (operações MCP básicas)
- Entidades básicas (Server, Tool, DomainDefinition)

### **Etapa 2: Domain Management**
- DomainRegistry (gerenciamento dinâmico de domínios)
- Lógica de auto-descoberta de domínios
- Configuração JSON e persistência

### **Etapa 3: Advanced Matching**
- ToolMatcher (coordenador de matching)
- SemanticMatcher (matching semântico com LLM)
- ParameterExtractor (extração inteligente de parâmetros)

### **Etapa 4: Multi-Step Processing**
- MultiStepDetector (detecção e planejamento multi-step)
- Orquestração de execução sequencial
- Integração final e testes

Cada etapa será implementada de forma incremental, permitindo testes e validação antes de prosseguir para a próxima fase. Esta abordagem garante que cada componente seja bem testado e integrado adequadamente ao sistema geral.

USE METODO KISS E DRY. NÃO ESCREVA CÓDIGO DESNECESSÁRIO E NÃO SOLICITADO. FAÇA PERGUNTAS INICIAS SOBRE QUALQUER QUESTÃO DÚBIA. VERIFIQUE A ETAPA 1 E A ETAPA 2. INICIE A ETAPA 3.
I just add all instructions in the gemini.md file. Can you read it?
