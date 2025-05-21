## Note for developers - 
When building a new wrapper using glide-ffi, be sure to specify the appropriate GLIDE_NAME and GLIDE_VERSION environment variables during the build process. This is especially important for production or deployment workflows. These values determine how the client identifies itself to the server and will appear in the `CLIENT INFO` output. If not set, the defaults are `"GlideFFI"` for the client name and `"unknown"` for the version.

For example:
```
GLIDE_NAME=GlideGo GLIDE_VERSION=2.0.0 cargo build
```
