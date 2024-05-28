#!/bin/bash -e

ROOT_DIR=$(dirname $(readlink -f $0))
ROOT_DIR=$(readlink -f ${ROOT_DIR}/..)

RUN_TESTS=0
GENERATE_PROTO=0
RUN_LINTERS=0

function find_binary() {
    EXE=$(which $1)
    if [ -z ${EXE} ]; then
        echo "ERROR: please install $1"
        exit 1
    fi
    echo $EXE
}

function print_help() {
    echo "Usage: python-build.sh [--release] [--test] [--proto]"
    echo ""
    echo "Build GLIDE for python"
    echo ""
    echo "Optional flags:"
    echo " --release: build in release mode (default: debug)"
    echo " --proto: regenerate protobuf files before building (default: NO)"
    echo " --test: execute the tests after the build (default: NO)"
    echo " --lint: run linters (default: NO)"
    echo " --help: print this help message"
    exit 0
}

# parse command line
for i in "$@"
do
    case $i in
    --release=*)
        RELEASE_FLAGS="--release --strip"
        shift
        ;;
    --test)
        RUN_TESTS=1
        shift
        ;;
    --proto)
        GENERATE_PROTO=1
        shift
        ;;
    --lint)
        RUN_LINTERS=1
        shift
        ;;
    --help)
        print_help
        exit 0
    esac
done

# Check if we need to re-generate the protobuf files before we build
if [ ${GENERATE_PROTO} -eq 1 ]; then
    echo "🟢 Generating protobuf files"
    PROTOC=$(find_binary protoc)
    ${PROTOC} -Iprotobuf=${ROOT_DIR}/glide-core/src/protobuf/   \
        --python_out=${ROOT_DIR}/python/python/glide            \
        ${ROOT_DIR}/glide-core/src/protobuf/*.proto
fi

cd ${ROOT_DIR}/python

# build the environment and install requirements if needed
if [ ! -d .env ]; then
    python3 -m venv .env
    source .env/bin/activate
    pip install -r requirements.txt
    pip install -r dev_requirements.txt
fi


# Build the python wrapper
source .env/bin/activate
maturin develop ${RELEASE_FLAGS}

if [ ${RUN_TESTS} -eq 1 ]; then
    # Check for redis-server
    REDIS_SERVER=$(find_binary redis-server)
    echo "🟢 Running tests ..."
    pytest --asyncio-mode=auto
fi

if [ ${RUN_LINTERS} -eq 1 ]; then
    echo "🟢 Running linters ..."
    isort . --profile black --skip-glob python/glide/protobuf --skip-glob .env
    black . --exclude python/glide/protobuf --exclude .env
    flake8 . --count --select=E9,F63,F7,F82 --show-source --statistics --exclude=python/glide/protobuf,.env/* --extend-ignore=E230
fi
