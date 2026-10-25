# Ir a la carpeta donde está este script
Set-Location $PSScriptRoot

Write-Host "Iniciando servidor WebSocket..."

# Compilar y ejecutar el servidor
mvn -PrunMain compile exec:java "-Dexec.mainClass=com.project.server.Main"