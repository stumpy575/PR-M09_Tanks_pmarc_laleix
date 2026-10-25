#!/bin/bash

# Ir a la carpeta donde está este script
cd "$(dirname "$0")" || exit 1

echo "Iniciando cliente JavaFX..."

# Ejecutar el cliente
mvn javafx:run