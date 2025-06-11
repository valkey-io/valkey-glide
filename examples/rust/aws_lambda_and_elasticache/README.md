# Prereqs:
*This crate was built and tested on Windows 11 using Rust 1.86, and run on x86_64 Linux based Lambdas*

- Make sure you have rust / cargo installed. This package was tested with version 1.86.
- Install `cargo lambda`: https://www.cargo-lambda.info/
- Make sure you have appropriate cross compile toolchains
    - You will need a version of Zig for compiling some of the C dependencies.
        - Version 0.15.0 was used
        - https://ziglang.org/
    - The `ring` crate in particular has issues with cross compilation.
        - However, this example was able to compile fine from Windows to Linux using the same cross compile toolchain that `Strawberry Perl` uses.
        - https://strawberryperl.com/releases.html


## Other Notes
When creating new projects based on this, make sure to do the following:
- Make sure you have a .cargo/config.toml file with Glide environment variables:
    ```
    [env]
    GLIDE_NAME = "valkey-glide"
    GLIDE_VERSION = "1.3.4" # Or the current version you're using
    ```

# AWS Resources
*The examples below use the `aws cli`, and assume you have proper permissions*
*!!! These examples don't necessarily follow proper security / "least privilege" principals. You will need to determine what is right for you and your organization. !!!*

## Elasticache
https://aws.amazon.com/elasticache/

### Create a Valkey Cache
You can create a cache using the following command:
```aws elasticache create-serverless-cache --serverless-cache-name my-awesome-cache --engine valkey```

### Get Cache Connection Info
It can take tens of seconds (potentially minutes) for the cache to spin up.
You can check the status of a cache using this command:
```aws elasticache describe-serverless-cache --serverless-cache-name my-awesome-cache```

Once the cache is ready, the response from the previous command will also give you the connection information.
You can set the host and ip using the `GLIDE_HOST_IP` and `GLIDE_HOST_PORT` environment variables in your lambda.

https://docs.aws.amazon.com/lambda/latest/dg/configuration-envvars.html#:~:text=To%20set%20environment%20variables%20in,Under%20Environment%20variables%2C%20choose%20Edit.


### Delete a Valkey Cache
Once your cache is no longer needed, you can delete it with the following command:
```aws elasticache delete-serverless-cache --serverless-cache-name my-awesome-cache```


### Accessing the Cache
Elasticache resources are only available via AWS VPC.
This means they _ARE NOT_ accesible to the public internet.
They are not even accessible to other AWS rsources (like Lambdas) by default, unless those resources are also in the same VPC.

## Lambda
https://docs.aws.amazon.com/lambda/latest/dg/configuration-vpc.html
https://aws.amazon.com/lambda
https://www.cargo-lambda.info/

As mentioned earlier, elasticache resources are only accessible behind AWS VPCs.
We must make sure our Lambda is on the same VPC as the cache.
In the example below, we'll deploy the lambda and setup all necessary config.

*NOTE: This will remove your Lambdas ability to communicate with the public Internet! You'll need additional setup to restore that functionality if you need it. Your lambda can still be invoked from the public internet.*

```
# Step 0: Create a cache if you haven't already.
aws elasticache create-serverless-cache --serverless-cache-name <ELASTICACHE_NAME> --engine valkey


# Step 1: Build and Deploy the lambda.
# In your output, note the arn value, we'll use it below.
cargo lambda build --release
cargo lambda deploy --binary-name <RUST_CRATE_NAME>


# Step 2: Get the cache information.
# In your output, look for the following:
#    - Endpoint: Only included once your cache is "available"
#    - SubnetIds: These are the IDs of the VPC Subnets that the cache is on. We'll use these below.
#    - SecurityGroupIds: This is the security group that the cache belongs to. We'll use this below
#    - Endpoint: This is the address / port that where your cache will be accessible. We'll use this below.
aws elasticache describe-serverless-caches --serverless-cache-name <ELASTICACHE_NAME>


# Step 3: Get the lambda information
# In your output, look for the following:
#    - Role: This is the IAM Role given to the Lambda by `cargo lambda`. We'll use this below.
aws lambda get-function --function-name <LAMBDA_ARN_FROM_STEP_1>


# Step 4: Give your lambda the correct permissions.
# https://docs.aws.amazon.com/lambda/latest/dg/configuration-vpc.html#configuration-vpc-permissions
# The `AWSLambdaENIManagementAccess` policy is managed by Amazon, and gives permission to configure the VPC connection.
# The "lambda:InvokeFunctionUrl" permission is necessary to allow public access. Otherwise, requests will bounce back with a 403 FORBIDDEN
aws iam attach-role-policy --role-name <LAMBDA_ROLE_FROM_STEP_3> --policy-arn arn:aws:iam::aws:policy/service-role/AWSLambdaENIManagementAccess
aws lambda add-permission --function-name <LAMBDA_ARN_FROM_STEP_1> --statement-id FunctionURLAllowPublicAccess --principal "*" --function-url-auth-type NONE --action lambda:InvokeFunctionUrl


# Step 5: Add the VPC Config and Environment Variables that will allow the Lambda to connect to the cache.
aws lambda update-function-configuration \
    --function-name <LAMBDA_ARN_FROM_STEP_1> \
    --vpc-config SubnetIds=<COMMA_SEPARATED_SUBNETS_FROM_STEP_4>,SecurityGroupIds=<COMMA_SEPARATED_SUBNETS_FROM_STEP_4> \
    --envrironment "Variables={GLIDE_HOST_IP=<ENDPOINT_ADDRESS_FROM_STEP_2>,GLIDE_HOST_PORT=<ENDPOINT_PORT_FROM_STEP_2>}"


# Step 6: Setup your lambda so it can be called via HTTP
# Note: Using AuthType = NONE means ANYONE will be able to access the lambda if they have the URL.
#        For demo purposes that's fine, but you should consider either requiring AWS_IAM or setting up
#        your own form of authentication.
# In your output, look for the following:
#    - FunctionUrl: This is the URL that you will be able to send requests to.
aws lambda create-function-url-config --function-name <LAMBDA_ARN_FROM_STEP_1> --auth-type NONE
```


# Querying your Lambda
The lambda should now be accessible via HTTP. We should be able to query it using tools like `curl` or anything else.

```
curl "<FUNCTION_URL_FROM_STEP_6>" -H "content-type: application/json" -d '{"SetValue": {"key": "SomeKey", "value": {"field1" : 0} }}'
# Returns: {"SetValue": {}}

curl "<FUNCTION_URL_FROM_STEP_6>" -H "content-type: application/json" -d '{"GetValue": {"key": "SomeKey" } }'
# Returns: {"GetValue":{"value":{"field1":0}}}
```
