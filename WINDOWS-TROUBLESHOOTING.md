# Guia de Solução para Windows - Sistema MCP Java

## 🚨 **PROBLEMA IDENTIFICADO: Node.js/npm não detectado corretamente**

Baseado nos logs, você tem Node.js instalado (o servidor weather funciona manualmente), mas o sistema Java não consegue encontrar o `npx`.

## ✅ **SOLUÇÕES IMPLEMENTADAS**

### 1. **Sistema Resiliente Melhorado**
- Detecção inteligente de comandos Windows
- Timeout otimizado para conexões MCP
- Tentativa de conexão direta mesmo se comando não for detectado no PATH

### 2. **Resolução Automática de Caminhos Windows**
O sistema agora tenta encontrar o `npx` em:
- `npx` (PATH padrão)
- `npx.cmd` (versão Windows)
- `%USERPROFILE%\AppData\Roaming\npm\npx.cmd`
- `%ProgramFiles%\nodejs\npx.cmd`
- `%ProgramFiles(x86)%\nodejs\npx.cmd`

### 3. **Comando de Diagnóstico**
```
MCP> debug weather-nws
```
Isso mostrará informações detalhadas sobre por que o servidor não conecta.

## 🛠️ **PASSOS PARA SOLUÇÃO**

### Passo 1: Compilar e Executar
```bat
mvn compile
mvn exec:java -Dexec.mainClass="com.gazapps.App" -Dexec.args="interactive"
```

### Passo 2: Usar Comando de Diagnóstico
```
MCP> debug weather-nws
MCP> debug filesystem
MCP> debug time
```

### Passo 3: Se NPX ainda não for detectado

#### Opção A: Verificar PATH do Node.js
```bat
where node
where npm  
where npx
```

Se `npx` não for encontrado:
```bat
# Adicionar ao PATH (substitua pelo caminho correto)
set PATH=%PATH%;C:\Program Files\nodejs
# ou
set PATH=%PATH%;%USERPROFILE%\AppData\Roaming\npm
```

#### Opção B: Reinstalar Node.js
1. Baixar: https://nodejs.org/
2. Instalar versão LTS
3. **IMPORTANTE**: Reiniciar o terminal/cmd após instalação
4. Verificar: `node --version && npm --version && npx --version`

#### Opção C: Instalar NPX separadamente
```bat
npm install -g npx
```

## 🧪 **TESTE MANUAL DOS SERVIDORES**

### Weather Server
```bat
cd C:\Users\gazol\AppData\MCP\WRKGRP\JCli
npx @h1deya/mcp-server-weather
```

### Filesystem Server  
```bat
cd C:\Users\gazol\AppData\MCP\WRKGRP\JCli
npx -y @modelcontextprotocol/server-filesystem ./documents
```

### Time Server
```bat
pip install uvx
uvx mcp-server-time
```

## 📊 **EXEMPLO DE FUNCIONAMENTO CORRETO**

Após a correção, você deve ver:
```
09:52:15.656 [main] INFO com.gazapps.mcp.MCPService - Conectando ao servidor MCP 'weather-nws'...
09:52:16.200 [main] DEBUG com.gazapps.mcp.MCPService - Usando caminho npx: npx.cmd
09:52:17.500 [main] DEBUG com.gazapps.mcp.MCPService - Carregadas 3 ferramentas para servidor 'weather-nws'
09:52:17.501 [main] INFO com.gazapps.mcp.MCPService - ✅ Servidor 'weather-nws' conectado com sucesso! Ferramentas: 3
```

## 🚀 **COMANDOS ÚTEIS NO MODO INTERATIVO**

```
MCP> stats           # Ver status geral
MCP> debug           # Diagnóstico do sistema
MCP> debug weather-nws # Diagnóstico específico do servidor
MCP> servers         # Ver todos os servidores
MCP> refresh         # Tentar reconectar
MCP> install         # Guia de instalação
```

## 🔧 **SE AINDA NÃO FUNCIONAR**

Execute no modo interativo:
```
MCP> debug weather-nws
```

E compartilhe a saída para análise mais detalhada.

O sistema foi projetado para funcionar mesmo sem servidores MCP externos, então você pode explorar todas as funcionalidades da arquitetura independentemente dos servidores se conectarem ou não.
