// Docker Bake configuration for integration tests.
// Builds a shared base image, then language-specific test images on top.
//
// Usage (from repo root):
//   docker buildx bake node-test              # build only
//   docker buildx bake node-test --load       # build + load into docker
//   docker run --rm node-test                 # run all tests
//   docker run --rm -e TEST_FILE=GlideClient.test.ts node-test
//   docker run --rm -e TEST_PATTERN="ping" node-test

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
