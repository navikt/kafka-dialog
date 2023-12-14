FROM ghcr.io/navikt/baseimages/temurin:17
ENV JAVA_OPTS="-Dlogback.configurationFile=logback-remote.xml -Xms1024M -Xmx2048M"
COPY build/libs/app*.jar app.jar