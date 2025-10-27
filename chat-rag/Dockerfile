# Use a imagem oficial do OpenJDK 17
FROM eclipse-temurin:17-jdk-jammy as build

# Define o diretório de trabalho
WORKDIR /workspace/app

# Copia o arquivo POM e os arquivos fonte
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
COPY src src

# Compila e empacota a aplicação
RUN chmod +x ./mvnw
RUN ./mvnw package -DskipTests

# Imagem final
FROM eclipse-temurin:17-jre-jammy

# Configuração de variáveis de ambiente para a JVM
ENV JAVA_OPTS="-Xms512m -Xmx512m"

WORKDIR /app

# Copia o JAR da fase de build
COPY --from=build /workspace/app/target/*.jar app.jar

# Expõe a porta 8080
EXPOSE 8080

# Comando para executar a aplicação
ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -jar app.jar"]