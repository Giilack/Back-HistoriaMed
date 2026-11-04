# Imagen del backend para la nube (Render). Dos etapas: compilar con Maven y ejecutar solo con el JRE.
# Construir en local (si tiene Docker):  docker build -t historiamed-backend .

# --- Etapa 1: compilar ---------------------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS compilacion
WORKDIR /app

# Primero solo el pom: las dependencias quedan en una capa que se reutiliza mientras el pom no cambie
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src src
# Las pruebas se ejecutan en la integración continua (GitHub Actions), no al construir la imagen
RUN mvn -B -q -DskipTests package

# --- Etapa 2: ejecutar ---------------------------------------------------------------------------------------
FROM eclipse-temurin:21-jre
WORKDIR /app

# El proceso no corre como root
RUN useradd --system --no-create-home historiamed
COPY --from=compilacion /app/target/backend-*.jar app.jar
USER historiamed

# Pensado para 512 MB de RAM (capa gratuita). Render reinicia la instancia si el proceso entero pasa de 512 MB,
# no solo el montón: por eso cada zona de memoria de la JVM tiene un tope y la suma queda por debajo del límite.
#   montón 40 % (~205 MB) + clases 160 MB + código compilado 64 MB + búferes directos 32 MB + hilos (~20 MB)
# TieredStopAtLevel=1 compila solo con C1: usa menos memoria y arranca más rápido (mejor arranque en frío).
# MALLOC_ARENA_MAX=2 evita que la biblioteca C reserve una zona de memoria por cada hilo.
ENV MALLOC_ARENA_MAX=2
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=40 -XX:MaxMetaspaceSize=160m -XX:ReservedCodeCacheSize=64m -XX:MaxDirectMemorySize=32m -XX:TieredStopAtLevel=1 -XX:+UseSerialGC -Xss512k -XX:+ExitOnOutOfMemoryError"

# El puerto lo indica la plataforma con la variable PORT (por defecto 8080)
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
