FROM amazoncorretto:21

WORKDIR /app

# Install Docker CLI for container manipulation (exec DEBUG SLEEP).
# curl is already provided by the base image's curl-minimal package; installing the
# full curl package conflicts with it, so we only add tar/gzip here.
RUN yum install -y tar gzip && \
    curl -fsSL https://download.docker.com/linux/static/stable/x86_64/docker-27.5.1.tgz | \
    tar xz --strip-components=1 -C /usr/local/bin docker/docker && \
    yum clean all

COPY build/libs/circuit-breaker-test-1.0-SNAPSHOT.jar /app/app.jar

CMD ["java", "-jar", "app.jar"]
