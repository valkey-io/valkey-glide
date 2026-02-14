FROM public.ecr.aws/lambda/java:21-arm64

# Copy the application JAR file to Lambda task root
COPY build/libs/lambda-test-1.0-SNAPSHOT.jar ${LAMBDA_TASK_ROOT}/lib/

USER 1000

CMD ["com.company.glide.lambda.ValkeyLambda::handleRequest"]
