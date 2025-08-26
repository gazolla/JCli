cd /d "%~dp0"
mvn compile -q
echo === Testando Prompts Padronizados ===
java -cp "target/JCli-0.0.1-SNAPSHOT-jar-with-dependencies.jar" com.gazapps.AppWeather
pause
