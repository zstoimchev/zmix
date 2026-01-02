FROM eclipse-temurin:21-jdk
WORKDIR /app
COPY target/*.jar app.jar
#COPY src/main/resources/node.bootstrap.properties application.properties
CMD ["java", "-jar", "app.jar"]