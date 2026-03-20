// Docker Bake configuration for integration tests.
// Builds a shared base image (with pre-compiled Rust deps), then language-specific test images.
//
// Usage (from repo root):
//   docker buildx bake node-test --load
//   docker run --rm node-test
//   docker run --rm -e TEST_FILE=GlideClient.test.ts node-test
//   docker run --rm -e TEST_PATTERN="ping" node-test
//
//   docker buildx bake python-test --load
//   docker run --rm python-test
//   docker run --rm -e TEST_ARGS="-k test_ping" python-test
//
//   docker buildx bake glide-core-test --load
//   docker run --rm glide-core-test
//   docker run --rm -e TEST_ARGS="--test test_client" glide-core-test
//
//   docker buildx bake ffi-test --load
//   docker run --rm ffi-test

target "base" {
  dockerfile = "Dockerfile.test"
  context    = "."
}

target "node-test" {
  dockerfile = "node/Dockerfile.test"
  context    = "."
  contexts = {
    base = "target:base"
  }
  tags = ["node-test"]
}

target "python-test" {
  dockerfile = "python/Dockerfile.test"
  context    = "."
  contexts = {
    base = "target:base"
  }
  tags = ["python-test"]
}

target "glide-core-test" {
  dockerfile = "glide-core/Dockerfile.test"
  context    = "."
  contexts = {
    base = "target:base"
  }
  tags = ["glide-core-test"]
}

target "ffi-test" {
  dockerfile = "ffi/Dockerfile.test"
  context    = "."
  contexts = {
    base = "target:base"
  }
  tags = ["ffi-test"]
}
