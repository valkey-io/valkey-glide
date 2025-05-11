use lambda_http::{run, service_fn, Error as LambdaError, Response};

use serde::{Deserialize, Serialize};


///////////////////////////////////////////////////////////////////////////////
/// Utilities
///////////////////////////////////////////////////////////////////////////////
type LambdaResult<T> = Result<T, LambdaError>;

struct StringErr {
    value: String
}

impl StringErr {
    pub fn boxed<S:ToString>(value: S) -> Box<StringErr> {
        Box::new(StringErr {
            value: value.to_string()
        })
    }
}

impl std::fmt::Debug for StringErr {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        write!(f, "{}", self.value)
    }
}

impl std::fmt::Display for StringErr {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        write!(f, "{}", self.value)
    }
}

impl std::error::Error for StringErr {}

///////////////////////////////////////////////////////////////////////////////
/// Request / Result types
///////////////////////////////////////////////////////////////////////////////

/// These are the different types of requests that can come in.
/// The JSON format will be something like this:
/// { "<REQUEST_TYPE_NAME>": { ... }}
///
/// E.G.
/// {
///    "SetValue": {
///         "key": "SomeKey",
///         "value": "SomeValue"
///     }
/// }
#[derive(Deserialize, Serialize, Debug)]
enum RequestType {
    SetValue(SetValueRequest),
    GetValue(GetValueRequest)
}

/// These are the different types of responses that we may return
/// The JSON format will be something like this:
/// { "<REQUEST_TYPE_NAME>": { ... }}
///
/// E.G.
/// {
///    "GetValue": {
///         "value": "SomeValue"
///     }
/// }
#[derive(Serialize, Deserialize)]
enum ResultType {
    SetValue(SetValueResponse),
    GetValue(GetValueResponse)
}

#[derive(Deserialize, Serialize, Debug)]
struct SetValueRequest {
    key: String,
    value: serde_json::Value
}

#[derive(Deserialize, Serialize, Debug)]
struct SetValueResponse {}

#[derive(Deserialize, Serialize, Debug)]
struct GetValueRequest {
    key: String
}

#[derive(Deserialize, Serialize, Debug)]
struct GetValueResponse {
    value: serde_json::Value
}

///////////////////////////////////////////////////////////////////////////////
/// Handler Trait + Implementations
///////////////////////////////////////////////////////////////////////////////
trait RequestHandler {
    async fn handle(self, shared_resources: &SharedResources) -> LambdaResult<ResultType>;
}

impl RequestHandler for RequestType {
     async fn handle(self, shared_resources: &SharedResources) -> LambdaResult<ResultType> {
        match self {
            Self::SetValue(request) => request.handle(shared_resources).await,
            Self::GetValue(request) => request.handle(shared_resources).await
        }
    }
}

impl RequestHandler for SetValueRequest {
    async fn handle(self, shared_resources: &SharedResources) -> LambdaResult<ResultType> {
        let mut cmd = redis::Cmd::new();
        cmd.arg("SET")
            .arg(self.key)
            .arg(serde_json::to_string(&self.value).map_err(Box::new)?);

        let _value = shared_resources.glide_client
            .clone()
            .send_command(&cmd, None)
            .await
            .map_err(Box::new)?;

        Ok(ResultType::SetValue(SetValueResponse{}))
    }
}

impl RequestHandler for GetValueRequest {
    async fn handle(self, shared_resources: &SharedResources) -> LambdaResult<ResultType> {
        let mut cmd = redis::Cmd::new();
        cmd.arg("GET")
            .arg(self.key);

        let value = shared_resources.glide_client
            .clone()
            .send_command(&cmd, None)
            .await
            .map_err(Box::new)?;

        let value : serde_json::Value = match value {
            redis::Value::SimpleString(value) => serde_json::from_str(&value).map_err(Box::new)?,
            redis::Value::BulkString(value) => serde_json::from_slice(&value).map_err(Box::new)?,
            val => Err(StringErr::boxed(format!("Invalid value type returned from valkey! {val:?}")))?
        };

        Ok(ResultType::GetValue(GetValueResponse{value}))
    }
}

///////////////////////////////////////////////////////////////////////////////
/// Main
///////////////////////////////////////////////////////////////////////////////

/// Lambda functions can actually process multiple requests.
/// We can save some compute time by shared resources between invocations
/// You can put other shared resources here as well, like other AWS SDK types.
struct SharedResources {
    _sdk_config: aws_config::SdkConfig,
    glide_client: glide_core::client::Client
}

#[tokio::main]
async fn main() -> LambdaResult<()> {
    lambda_http::tracing::init_default_subscriber();

    let glide_client = {
        // These variables can be set with the lambda deployments.
        // https://docs.aws.amazon.com/lambda/latest/dg/configuration-envvars.html#:~:text=To%20set%20environment%20variables%20in,Under%20Environment%20variables%2C%20choose%20Edit.
        let address_info = glide_core::client::NodeAddress {
            host: std::env::var("GLIDE_HOST_IP").map_err(Box::new)?,
            port: std::env::var("GLIDE_HOST_PORT").map_err(Box::new)?.parse().map_err(Box::new)?
        };

        // elasticache uses Clusters and TLS by default.
        let connection_request = glide_core::client::ConnectionRequest {
            addresses: vec![address_info],
            cluster_mode_enabled: true,
            request_timeout: std::env::var("GLIDE_REQUEST_TIMEOUT").ok().and_then(|v| v.parse::<u32>().ok()),
            tls_mode: Some(glide_core::client::TlsMode::SecureTls),
            ..Default::default()
        };

        glide_core::client::Client::new(connection_request, None)
            .await
            .map_err(Box::new)?
    };

    let sdk_config = aws_config::load_defaults(aws_config::BehaviorVersion::latest()).await;
    let shared_resources = SharedResources{
        _sdk_config: sdk_config,
        glide_client
    };

    // If we tried to use "shared_resources" directly in the closure we'd
    // get a compiler warning that "shared_resources" was moved.
    // Putting it into an explicit ref variable is fine, however, as the
    // reference "shared_resources_ref" is captured, but not "shared_resources"
    let shared_resources_ref = &shared_resources;

    let handler = move |event: lambda_http::Request| async move {
        handle(shared_resources_ref, event).await
    };

    run(service_fn(handler)).await
}

async fn handle(shared_resources: &SharedResources, event: lambda_http::Request) -> LambdaResult<Response<String>> {
    let request : RequestType = match event.body() {
        lambda_http::Body::Empty => Err(StringErr::boxed("Requests cannot be empty!"))?,
        lambda_http::Body::Text(val) => serde_json::from_str(&val)?,
        lambda_http::Body::Binary(val) => serde_json::from_slice(&val)?
    };

    let response = request.handle(shared_resources).await?;
    let response_body : String = serde_json::to_string(&response).map_err(Box::new)?;

    Ok(Response::builder()
        .status(200)
        .header("content-type", "application/json")
        .body(response_body)
        .map_err(Box::new)?)
}
