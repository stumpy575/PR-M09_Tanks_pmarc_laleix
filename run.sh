#!/bin/bash
set -e

cd "$(dirname "$0")"
mainClass=${1:-com.project.client.Main}

# Compilar proyecto y generar classpath de dependencias
mvn compile dependency:build-classpath \
    -Dmdep.outputFile=target/classpath.txt

# Detectar arquitectura
javaArch=$(java -XshowSettings:properties -version 2>&1 |
    awk '/^[[:space:]]*os.arch = / { print $3 }')

case "$(uname -s)" in
    Darwin) fxPlatform="mac" ;;
    Linux)  fxPlatform="linux" ;;
    *)
        echo "Sistema no compatible."
        exit 1
        ;;
esac

case "$javaArch" in
    aarch64|arm64)
        fxPlatform="${fxPlatform}-aarch64"
        ;;
    amd64|x86_64)
        ;;
    *)
        echo "Arquitectura no compatible: $javaArch"
        exit 1
        ;;
esac

# JavaFX 22
fxPath=""

for module in base controls fxml graphics; do
    jar="$HOME/.m2/repository/org/openjfx/javafx-$module/22/javafx-$module-22-$fxPlatform.jar"

    if [[ ! -f "$jar" ]]; then
        echo "No se encuentra JavaFX: $jar"
        exit 1
    fi

    fxPath="${fxPath:+$fxPath:}$jar"
done

# Obtener todas las dependencias Maven
dependencyPath=$(cat target/classpath.txt)

# Ejecutar aplicación con todas las dependencias
exec java \
    --module-path "$fxPath" \
    --add-modules javafx.controls,javafx.fxml \
    -cp "target/classes:$dependencyPath" \
    "$mainClass"