# Stage de build: usa imagem Maven com JDK 17 para compilar o projeto
FROM maven:3-eclipse-temurin-17 AS build

# Diretório de trabalho no container
WORKDIR /workspace/app

# Copia apenas o pom primeiro para aproveitar cache do Docker
COPY pom.xml ./

# Copia o código-fonte
COPY src ./src

# Compila e empacota a aplicação (skip tests para acelerar build de imagem)
RUN mvn -B -DskipTests package

# Stage final: imagem runtime mais leve com JRE 17
FROM eclipse-temurin:17-jre-jammy

# Configuração de variáveis de ambiente para a JVM (ajuste conforme necessário)
ENV JAVA_OPTS="-Xms512m -Xmx512m"

WORKDIR /app

# Copia o JAR construído da fase de build
COPY --from=build /workspace/app/target/*.jar app.jar

# Porta padrão do Spring Boot
EXPOSE 8080

# Argumento para definir o profile (default é vazio, executando como API)
ARG SPRING_PROFILES_ACTIVE=""
ENV SPRING_PROFILES_ACTIVE=${SPRING_PROFILES_ACTIVE}

# Executa a aplicação permitindo sobrescrever os profiles via variável de ambiente
ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -jar app.jar ${SPRING_PROFILES_ACTIVE:+--spring.profiles.active=${SPRING_PROFILES_ACTIVE}}"]