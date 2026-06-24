FROM europe-north1-docker.pkg.dev/cgr-nav/pull-through/nav.no/jre:openjdk-25
ADD build/distributions/app.tar /

# Asume that logback.xml is located in the project/app root dir.
# The unconventional location is a signal to developers to make them aware that we use this file in an unconventional
# way in the ENTRYPOINT command in this Dockerfile.
COPY logback.xml /

# Set logback.xml explicitly and with an absolute path, to avoid accidentally using any logback.xml bundled in the JAR-files of the app's dependencies
ENTRYPOINT ["java", "-Duser.timezone=Europe/Oslo", "-Dlogback.configurationFile=/logback.xml", "-cp", "/app/lib/*", "no.nav.toi.rekrutteringsbistand.bruker.api.AppKt"]

EXPOSE 8080
