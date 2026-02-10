FROM public.ecr.aws/lambda/java:21-arm64

RUN groupadd -g 10001 appgroup && \
    useradd -u 10001 -g appgroup -m -s /bin/bash appuser

# Copy the application JAR file to Lambda task root
COPY build/libs/lambda-test-1.0-SNAPSHOT.jar ${LAMBDA_TASK_ROOT}/lib/

USER appuser

CMD ["com.company.glide.lambda.ValkeyLambda::handleRequest"]
