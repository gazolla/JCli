@echo off

echo Starting JCli Interactive Chat...

REM Compile and run
mvn compile exec:java -Dexec.mainClass="com.gazapps.JCliApp" -q

pause
