## Pre-requirements (for Beta)
-   GCC
-   pkg-config
-   protobuf-compiler (protoc)
-   openssl
-   libssl-dev
-   python3
-   python3-venv

Installation for ubuntu:
`sudo apt install -y gcc pkg-config protobuf-compiler openssl libssl-dev python3 python3-venv`

## Build
To build GLIDE's Python client, run (on unix based systems):
```
cd examples/python
sudo chmod +x build_client.sh
./build_client.sh
```

## Run
To run the example or any other Python application utilizing GLIDE for Redis, activate the virtual environment that created by the 'Build' stage:
```
cd examples/python
source .env/bin/activate
python3 client_example.py
```
