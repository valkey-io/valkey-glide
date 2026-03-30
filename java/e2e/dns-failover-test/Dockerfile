FROM public.ecr.aws/amazoncorretto/amazoncorretto:21

WORKDIR /app

# Install Docker CLI
RUN amazon-linux-extras install docker -y && yum clean all

# Copy the application JAR
COPY build/libs/dns-failover-test-1.0-SNAPSHOT.jar /app/app.jar

# Run the application (requires root to access Docker socket)
CMD ["java", "-jar", "app.jar"]
