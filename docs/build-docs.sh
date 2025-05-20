#!/bin/bash -ex

TARGET=$@
if [ -z "$TARGET" ]; then
    TARGET="build"
fi

BASE_DIR=$(readlink -f $(dirname $(readlink -f $0))/..)

# For building the docs, we require mkdocs + mkdocstrings-python
function install_mkdocs() {
    if ! command -v mkdocs >/dev/null 2>&1; then
        echo "-- Installing mkdocs ..."
        pip3 install --upgrade pip
        pip3 install                           \
            mkdocs                             \
            mkdocstrings-python==1.13.0        \
            pymdown-extensions                 \
            mkdocs-breadcrumbs-plugin          \
            mkdocs-material
        echo "-- Done"
    fi
    command -v mkdocs
}

function build_docs() {
    # NodeJS
    (cd ${BASE_DIR}/node && npm run docs)

    # Python - should be last, since Python docs are generated using mkdocs plugin
    # Set PYTHONPATH so python classes are found
    export PYTHONPATH=${BASE_DIR}/python/python:$PYTHONPATH
    (cd ${BASE_DIR}/docs && python3 -m mkdocs ${TARGET})
}

install_mkdocs
build_docs $@
