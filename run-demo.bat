@echo off
echo.
echo ================================
echo   Sistema MCP Java - Etapa 1
echo   Core Foundation - Demonstracao
echo ================================
echo.

echo Compilando o projeto...
call mvn -q compile
if %errorlevel% neq 0 (
    echo Erro na compilacao!
    pause
    exit /b 1
)

echo.
echo Executando sistema MCP...
echo (O sistema funcionara mesmo sem servidores MCP externos)
echo.
echo Pressione CTRL+C para sair
echo.

call mvn -q exec:java -Dexec.mainClass="com.gazapps.App" -Dexec.args="interactive"

echo.
echo Demonstracao concluida.
pause
