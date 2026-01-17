FROM eclipse-temurin:25-jre-alpine

WORKDIR /app

COPY build/libs/*SNAPSHOT.jar app.jar

RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]