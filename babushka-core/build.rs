fn main() {
    protobuf_codegen::Codegen::new()
        .cargo_out_dir("protobuf")
        .include("src")
        .input("src/protobuf/redis_request.proto")
        .input("src/protobuf/response.proto")
        .input("src/protobuf/connection_request.proto")
        .run_from_script();
}
