# JCli Interactive Chat

Chat interativo implementado com observer pattern para feedback em tempo real.

## Execução

```bash
# Windows
./run-chat.bat

# Linux/Mac
./run-chat.sh

# Maven direto
mvn compile exec:java -Dexec.mainClass="com.gazapps.JCliApp"
```

## Comandos Disponíveis

- `/help` - Mostra ajuda completa
- `/status` - Status do sistema
- `/tools` - Lista ferramentas MCP
- `/servers` - Lista servidores MCP
- `/strategy <simple|react|reflection>` - Muda estratégia de inferência
- `/debug` - Toggle modo debug
- `/clear` - Limpa tela
- `/quit` - Sair

## Funcionalidades

✅ Observer pattern com feedback em tempo real
✅ Três estratégias de inferência (Simple, ReAct, Reflection) 
✅ Sistema de comandos avançado
✅ Integração completa com MCPManager existente
✅ Reutilização 100% do sistema MCP

## Exemplo de Uso

```
JCli> what's the weather in brasilia?
🧠 Using Simple inference...
🔧 Using: get_forecast
   Args: latitude=-15.7939, longitude=-47.8828
🤖 Current weather in Brasília: 28°C, partly cloudy...

JCli> /strategy react
✅ Strategy changed to: react

JCli> /help
[Lista completa de comandos]
```
