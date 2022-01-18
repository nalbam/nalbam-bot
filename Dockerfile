# Dockerfile

FROM openjdk:8-jre-alpine

EXPOSE 8080
EXPOSE 8081

WORKDIR /data

ENTRYPOINT ["/bin/sh", "/data/entrypoint.sh"]

COPY ./entrypoint.sh /data/entrypoint.sh

COPY target/*.jar /data/ROOT.jar
