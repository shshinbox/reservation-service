FROM eclipse-temurin:21-jre

WORKDIR /app

RUN addgroup --system app && adduser --system --ingroup app app

COPY target/reservation-service-*.jar app.jar

USER app

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
