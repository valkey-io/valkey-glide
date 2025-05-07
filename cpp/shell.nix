{
  pkgs ? import <nixpkgs> { },
}:
with pkgs;
mkShell {
  nativeBuildInputs = [ pkg-config ];
  buildInputs = [
    gcc
    rust-cbindgen
    cmake
    openssl
    protobuf
    abseil-cpp
  ];

  shellHook = ''
    export GLIDE_VERSION="dev"
    export GLIDE_NAME="glide"
  '';
}
