FROM public.ecr.aws/lambda/java:21-arm64

# Copy the application JAR file to Lambda task root
COPY build/libs/lambda-test-1.0-SNAPSHOT.jar ${LAMBDA_TASK_ROOT}/lib/

# Run as non-root user
RUN useradd --system non-root
USER non-root
CMD ["com.company.glide.lambda.ValkeyLambda::handleRequest"]
