FROM gcr.io/distroless/java21:nonroot
ARG APP_NAME
WORKDIR /$APP_NAME

# Asume that logback.xml is located in the project/app root dir.
# The unconventional location is a signal to developers to make them aware that we use this file in an unconventional
# way in the ENTRYPOINT command in this Dockerfile.
# COPY logback.xml /

# Copy the prebuilt distribution (run: ./gradlew clean installDist)
COPY build/install/*/lib /app/lib

# Set logback.xml explicitly and with an absolute path, to avoid accidentally using any logback.xml bundled in the JAR-files of the app's dependencies
# Run without the shell script (since we dont have a shell)
ENTRYPOINT ["java", "-Duser.timezone=Europe/Oslo", "-Dlogback.configurationFile=/logback.xml", "-cp", "/app/lib/*", "no.nav.toi.rekrutteringsbistand.bruker.api.AppKt"]

EXPOSE 8080