cd /d "%~dp0"
mvn compile -q
echo === Testando Multi-Tool Enhanced Implementation ===
java -cp "target/JCli-0.0.1-SNAPSHOT-jar-with-dependencies.jar" com.gazapps.AppMultiToolWeatherFile
pause
