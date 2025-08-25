#!/bin/bash

echo ""
echo "================================"
echo "   Sistema MCP Java - Etapa 1"
echo "   Core Foundation - Demonstração"
echo "================================"
echo ""

echo "Compilando o projeto..."
mvn -q compile
if [ $? -ne 0 ]; then
    echo "Erro na compilação!"
    exit 1
fi

echo ""
echo "Executando sistema MCP..."
echo "(O sistema funcionará mesmo sem servidores MCP externos)"
echo ""
echo "Pressione CTRL+C para sair"
echo ""

mvn -q exec:java -Dexec.mainClass="com.gazapps.App"

echo ""
echo "Demonstração concluída."
