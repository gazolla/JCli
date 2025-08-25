@echo off
echo === Teste LLM Gemini + Inference Simple ===
echo.

:: Verificar variavel de ambiente
echo Verificando variavel de ambiente GEMINI_API_KEY...
if "%GEMINI_API_KEY%"=="" (
    echo [ERRO] GEMINI_API_KEY nao definida!
    echo.
    echo Para configurar:
    echo 1. Obtenha uma API key em: https://makersuite.google.com/app/apikey
    echo 2. Execute: set GEMINI_API_KEY=sua_api_key_aqui
    echo 3. Execute este script novamente
    echo.
    pause
    exit /b 1
) else (
    echo [OK] GEMINI_API_KEY configurada
)
echo.

:: Verificar se o projeto foi compilado
if not exist "target\classes" (
    echo Compilando projeto...
    mvn compile -q
    if errorlevel 1 (
        echo Erro na compilacao do projeto
        pause
        exit /b 1
    )
)

:: Executar o teste
echo Executando teste...
echo.
mvn exec:java -Dexec.mainClass="com.gazapps.AppTeste" -q

echo.
echo === Teste finalizado ===
pause
