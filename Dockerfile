FROM eclipse-temurin:17-jdk-alpine
WORKDIR /app
COPY target/*.jar app.jar
COPY config/ config/
ENTRYPOINT ["java", "-jar", "app.jar"]  
