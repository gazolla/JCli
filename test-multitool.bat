@echo off
cd /d "%~dp0"

echo === Testando Multi-Tool Implementation ===
java -cp "target/JCli-0.0.1-SNAPSHOT-jar-with-dependencies.jar" com.gazapps.AppMultiToolWeatherFile

pause
