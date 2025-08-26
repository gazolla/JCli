cd /d "%~dp0"
mvn compile -q
echo === Testando Novo Multi-Step Detection ===
echo.
echo Teste 1: Single-tool query
java -cp "target/JCli-0.0.1-SNAPSHOT-jar-with-dependencies.jar" com.gazapps.AppWeather
echo.
echo ================================
echo.
echo Teste 2: Multi-tool query
java -cp "target/JCli-0.0.1-SNAPSHOT-jar-with-dependencies.jar" com.gazapps.AppMultiToolWeatherFile
pause
