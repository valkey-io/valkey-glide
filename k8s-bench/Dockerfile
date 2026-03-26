FROM eclipse-temurin:11-jre-jammy

WORKDIR /app

# Copy benchmark distribution
COPY benchmarks/ /app/benchmarks/

# Copy native JNI library
COPY libglide_rs.so /app/lib/

# Make the start script executable
RUN chmod +x /app/benchmarks/bin/benchmarks

ENV JAVA_LIBRARY_PATH=/app/lib
ENV APP_HOME=/app/benchmarks

ENTRYPOINT ["/app/benchmarks/bin/benchmarks"]
