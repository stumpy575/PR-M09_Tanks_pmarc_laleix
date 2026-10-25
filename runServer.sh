#!/bin/bash

# Ir a la carpeta donde está este script
cd "$(dirname "$0")" || exit 1

echo "Iniciando servidor WebSocket..."

# Compilar y ejecutar el servidor
mvn -PrunMain compile exec:java -Dexec.mainClass=com.project.server.Main