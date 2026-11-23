# Runs the ready-made, tested jar (release/ershak.jar). The web app is already inside it.
FROM eclipse-temurin:21-jre

WORKDIR /app
COPY release/ershak.jar /app/ershak.jar

# Hosts set PORT. The database goes in /data: mount a persistent disk there to keep customers between restarts.
ENV ERSHAK_DB=jdbc:sqlite:/data/ershak.db \
    ERSHAK_DEMO_DATA=true \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 --enable-native-access=ALL-UNNAMED"
RUN mkdir -p /data
EXPOSE 7070

ENTRYPOINT ["java", "-jar", "/app/ershak.jar"]
