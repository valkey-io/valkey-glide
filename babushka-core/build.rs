fn main() {
    protobuf_codegen::Codegen::new()
        .cargo_out_dir("protobuf")
        .include("src")
        .input("src/protobuf/pb_message.proto")
        .run_from_script();
}
