#!/bin/bash

set -x

script=`pwd`/${BASH_SOURCE[0]}
RELATIVE_BUILD_PATH=`dirname ${script}`
export PYTHON_EXAMPLE_FOLDER=`realpath ${RELATIVE_BUILD_PATH}`
export GLIDE_HOME_FOLDER="${PYTHON_EXAMPLE_FOLDER}/../.."
export PYTHON_FOLDER="${GLIDE_HOME_FOLDER}/python"

# Generate protobuf files
protoc -Iprotobuf=${GLIDE_HOME_FOLDER}/glide-core/src/protobuf/ --python_out=${PYTHON_FOLDER}/python/pybushka ${GLIDE_HOME_FOLDER}/glide-core/src/protobuf/*.proto
cd ${PYTHON_EXAMPLE_FOLDER}
# Create a virtual environment 
python3 -m pip install --user virtualenv
python3 -m venv .env
source .env/bin/activate
cd ${PYTHON_FOLDER}
git submodule update --init --recursive
# Build the client
pip install --upgrade --quiet pip
pip install --quiet -r requirements.txt
maturin develop --release
echo "To activate the created virtual environment, run: source ${PYTHON_EXAMPLE_FOLDER}/.env/bin/activate"
