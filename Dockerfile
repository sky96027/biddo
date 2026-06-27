FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY biddo-api/build/libs/*.jar app.jar
EXPOSE 9090
ENTRYPOINT ["java", "-jar", "app.jar"]