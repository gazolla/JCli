#!/bin/bash

echo "Starting JCli Interactive Chat..."

# Compile and run
mvn compile exec:java -Dexec.mainClass="com.gazapps.JCliApp" -q
