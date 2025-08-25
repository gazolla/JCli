# Instruções para Configurar e Executar o Teste do Gemini

## Pré-requisitos

1. **API Key do Google Gemini**
   - Acesse: https://makersuite.google.com/app/apikey
   - Crie uma nova API key
   - Copie a chave gerada

2. **Java 17+** instalado
3. **Maven** instalado

## Configuração da Variável de Ambiente

### Windows (Command Prompt)
```cmd
set GEMINI_API_KEY=sua_api_key_aqui
```

### Windows (PowerShell)
```powershell
$env:GEMINI_API_KEY="sua_api_key_aqui"
```

### Windows (Permanente - via System Properties)
1. Pressione Win + X e selecione "System"
2. Clique em "Advanced system settings"
3. Clique em "Environment Variables"
4. Em "User variables", clique "New"
5. Nome: `GEMINI_API_KEY`
6. Valor: sua_api_key_aqui

## Execução

### Método 1: Script Automático
```cmd
teste-gemini.bat
```

### Método 2: Maven Direto
```cmd
mvn exec:java -Dexec.mainClass="com.gazapps.AppTeste"
```

### Método 3: Compilar e Executar JAR
```cmd
mvn package
java -jar target/JCli-0.0.1-SNAPSHOT-jar-with-dependencies.jar
```

## Verificação

O teste deve:
1. ✅ Detectar a variável de ambiente GEMINI_API_KEY
2. ✅ Inicializar o LLM Gemini
3. ✅ Criar a estratégia de inferência Simple
4. ✅ Processar a pergunta "Que horas são em Brasília, DF?"
5. ✅ Exibir a resposta formatada

## Possíveis Problemas

### Erro: "GEMINI_API_KEY não configurada"
- **Causa**: Variável de ambiente não definida
- **Solução**: Configure a variável conforme instruções acima

### Erro: "API Key inválida"
- **Causa**: API key incorreta ou expirada
- **Solução**: Gere uma nova API key no Google AI Studio

### Erro: "Timeout" ou "Conectividade"
- **Causa**: Problemas de rede ou bloqueio de firewall
- **Solução**: Verifique conexão com internet e proxy

## Logs

O sistema gera logs detalhados mostrando:
- Status da variável de ambiente
- Inicialização do LLM
- Processamento da query
- Resultado final

Para mais detalhes, verifique os logs no console.
