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

# Pensado para 512 MB de RAM (capa gratuita): la mitad para el montón de Java y el resto para clases, hilos y
# el hash Argon2id de las contraseñas (19 MB por inicio de sesión).
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=50 -XX:+UseSerialGC -Xss512k"

# El puerto lo indica la plataforma con la variable PORT (por defecto 8080)
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
