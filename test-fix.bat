@echo off
echo.
echo ==========================================
echo   TESTE RAPIDO - Sistema MCP Java
echo   Usando a abordagem JavaCLI que funcionava
echo ==========================================
echo.

echo Compilando rapidamente...
call mvn -q compile
if %errorlevel% neq 0 (
    echo ERRO na compilacao!
    exit /b 1
)

echo.
echo Executando teste...
echo (Devera conectar aos servidores MCP agora)
echo.

call mvn -q exec:java -Dexec.mainClass="com.gazapps.App" -Dexec.args="interactive"

pause
