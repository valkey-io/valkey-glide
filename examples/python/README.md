## Pre-requirements (for Beta)
-   GCC
-   pkg-config
-   protobuf-compiler (protoc)
-   openssl
-   libssl-dev
-   python3

Installation for ubuntu:
`sudo apt install -y gcc pkg-config protobuf-compiler openssl libssl-dev python3`

## Build
To build Babushka's Python client, run (on unix based systems):
```
cd examples/python
sudo chmod +x build_client.sh
./build_client.sh
```

## Run
To run the example or any other Python application utilizing Babushka, activate the virtual environment that created by the 'Build' stage:
```
cd examples/python
source .env/bin/activate
python3 client_example.py
```
