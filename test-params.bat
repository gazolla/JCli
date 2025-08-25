@echo off
echo.
echo ===============================================
echo   TESTE DE PARAMETROS DAS FERRAMENTAS MCP
echo ===============================================
echo.

echo Compilando...
call mvn -q compile test-compile
if %errorlevel% neq 0 (
    echo ERRO na compilacao!
    exit /b 1
)

echo.
echo Executando teste de parametros...
echo.

call mvn -q exec:java -Dexec.mainClass="com.gazapps.mcp.TestToolParams" -Dexec.classpathScope="test"

echo.
echo Teste concluido.
pause
