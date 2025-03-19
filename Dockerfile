FROM openjdk:17-jdk-slim-buster
WORKDIR /app
COPY /target/catalog-service-0.0.1-SNAPSHOT.jar /app/catalog-service.jar
ENTRYPOINT ["java", "-jar", "catalog-service.jar"]
EXPOSE 8080

