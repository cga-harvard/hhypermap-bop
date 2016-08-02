FROM java:openjdk-8-jre-alpine

MAINTAINER David Smiley <dsmiley@apache.org>

WORKDIR /opt/bop-ws

COPY target/*-shaded.jar .

COPY bop-ws.yml .

EXPOSE 8080, 8081

ENTRYPOINT ["java", "-jar", "bop-ws-shaded.jar"]
CMD ["server", "bop-ws.yml"]

#TODO HEALTHCHECK would be nice; curl http but need 'curl' in the distro