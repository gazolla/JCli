@echo off
echo Compilando projeto...
call mvn -q clean compile test-compile
if %errorlevel% neq 0 (
    echo ERRO na compilacao!
    pause
    exit /b 1
)

echo.
echo Executando App em modo interativo...
echo Quando iniciar, digite: params get_current_time
echo Depois: params read_file
echo Depois: quit
echo.
pause

java -cp "target/classes;target/test-classes;target/dependency/*" com.gazapps.App interactive
