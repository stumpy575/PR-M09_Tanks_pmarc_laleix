$ErrorActionPreference = "Stop"

Set-Location $PSScriptRoot

$mainClass = if ($args.Count -gt 0) {
    $args[0]
} else {
    "com.project.client.Main"
}

# Compilar y generar classpath
mvn compile dependency:build-classpath "-Dmdep.outputFile=target/classpath.txt"

if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

# Detectar arquitectura
$javaSettings = cmd /c "java -XshowSettings:properties -version 2>&1"

$javaArch = (
    $javaSettings |
    Select-String '^\s*os.arch = (.*)$'
).Matches.Groups[1].Value

if ($javaArch -notin @("amd64", "x86_64")) {
    Write-Error "Arquitectura Java no compatible: $javaArch"
    exit 1
}

# JavaFX 22 para Windows
$fxJars = foreach ($module in @("base", "controls", "fxml", "graphics")) {

    $jar = "$env:USERPROFILE\.m2\repository\org\openjfx\javafx-$module\22\javafx-$module-22-win.jar"

    if (-not (Test-Path $jar)) {
        Write-Error "No se encuentra JavaFX: $jar"
        exit 1
    }

    $jar
}

$fxPath = $fxJars -join ";"

# Obtener dependencias Maven
$dependencyPath = (Get-Content "target/classpath.txt" -Raw).Trim()

# Construir classpath completo
$classPath = "target/classes;$dependencyPath"

# Ejecutar
java `
    --module-path $fxPath `
    --add-modules javafx.controls,javafx.fxml `
    -cp $classPath `
    $mainClass

exit $LASTEXITCODE