FROM eclipse-temurin:17-jdk-jammy

WORKDIR /app

COPY target/order-service-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8100

ENTRYPOINT ["java","-jar","/app/app.jar"]
