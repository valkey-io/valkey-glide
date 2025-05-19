## Note for developers - 
When building a new wrapper using glide-ffi, be sure to specify the appropriate GLIDE_NAME and GLIDE_VERSION environment variables. These values define the client's name and version in the FFI crate.

For example:

```
GLIDE_NAME=<your_client_name> GLIDE_VERSION=<your_client_version> cargo build
```
