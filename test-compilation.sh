#!/bin/bash

echo "🔨 Testing JCli Chat Compilation..."
echo ""

cd "C:\Users\gazol\AppData\MCP\WRKGRP\JCli"

# Compile
echo "📦 Compiling..."
mvn compile -q

if [ $? -eq 0 ]; then
    echo "✅ Compilation successful!"
    echo ""
    echo "🚀 Ready to run:"
    echo "  ./run-chat.bat"
    echo ""
    echo "💡 Test commands to try:"
    echo "  JCli> hello"
    echo "  JCli> /help"
    echo "  JCli> /status"
    echo "  JCli> /strategy react"
    echo "  JCli> what's the weather?"
    echo "  JCli> /quit"
else
    echo "❌ Compilation failed!"
    echo ""
    echo "🔍 Check for:"
    echo "  - Missing imports"
    echo "  - Syntax errors"
    echo "  - Missing dependencies"
fi
