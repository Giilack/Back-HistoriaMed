# Análisis de calidad con SonarQube (servidor local en http://localhost:9000).
#
#   1. Levantar SonarQube:   docker start sonarqube   (la primera vez: docker run -d --name sonarqube -p 9000:9000 sonarqube:community)
#   2. Token personal:       $env:SONAR_TOKEN = "..."   (My Account > Security; nunca se guarda en el repositorio)
#   3. Ejecutar:             .\sonar.ps1               (pruebas + cobertura + análisis)
#                            .\sonar.ps1 -SinPruebas   (solo el análisis del código, más rápido, sin cobertura)
param(
    [switch]$SinPruebas,
    [string]$Servidor = "http://localhost:9000"
)

if (-not $env:SONAR_TOKEN) {
    Write-Error 'Falta el token: $env:SONAR_TOKEN = "..." (SonarQube > My Account > Security)'
    exit 1
}

$argumentos = @("clean", "verify", "org.sonarsource.scanner.maven:sonar-maven-plugin:sonar",
    "-Dsonar.host.url=$Servidor")
# El token no se pasa como argumento: el analizador lo lee solo de la variable de entorno SONAR_TOKEN
if ($SinPruebas) { $argumentos += "-DskipTests" }

& "$PSScriptRoot\mvnw.cmd" @argumentos
exit $LASTEXITCODE
