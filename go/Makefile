# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

SHELL:=/bin/bash
GO_DIR := $(dir $(abspath $(lastword $(MAKEFILE_LIST))))
ROOT_FOLDER := $(abspath $(GO_DIR)/..)
GLIDE_CORE_PATH := $(ROOT_FOLDER)/glide-core
GLIDE_FFI_PATH := $(ROOT_FOLDER)/ffi
TARGET_DIR := $(GLIDE_FFI_PATH)/target

# Determine the target folder based on OS and architecture
UNAME := $(shell uname)
ARCH := $(shell uname -m)
IS_ALPINE := $(shell test -f /etc/alpine-release && echo true || echo false)

# Set default values for GLIDE_NAME and GLIDE_VERSION
# GLIDE_VERSION is automatically set during the deployment workflow based on the value defined in go-cd.yml.
# For local builds, you can manually specify the version using `GLIDE_VERSION=<version> make build`
GLIDE_NAME = GlideGo
GLIDE_VERSION ?= unknown

ifndef TARGET_TRIPLET
ifeq ($(UNAME), Darwin)
    TARGET_TRIPLET := $(if $(filter arm64,$(ARCH)),aarch64-apple-darwin,x86_64-apple-darwin)
else ifeq ($(UNAME), Linux)
    ifeq ($(IS_ALPINE), true)
        TARGET_TRIPLET := $(if $(filter arm64 aarch64,$(ARCH)),aarch64-unknown-linux-musl,x86_64-unknown-linux-musl)
    else
        TARGET_TRIPLET := $(if $(filter arm64 aarch64,$(ARCH)),aarch64-unknown-linux-gnu,x86_64-unknown-linux-gnu)
    endif
else
    $(error Unsupported platform: $(UNAME) $(ARCH))
endif
endif

ifeq ($(UNAME), Darwin)
	# https://github.com/rust-lang/rust/issues/51009#issuecomment-2274649980
	BUILD_CMD := rustc --crate-type staticlib
	CARGO_FIX_CMD := : # no-op
	CARGO_POSTFIX_CMD := : # no-op
    STRIP_CMD := strip -x
else ifeq ($(UNAME), Linux)
	# zigbuild https://github.com/rust-cross/cargo-zigbuild
	BUILD_CMD := zigbuild --target $(TARGET_TRIPLET)
	# workaround for https://github.com/rust-cross/cargo-zigbuild/issues/337
	CARGO_FIX_CMD := sed -i 's/crate-type.*/crate-type = ["staticlib"]/g' Cargo.toml && rustup target add $(TARGET_TRIPLET)
	CARGO_POSTFIX_CMD := git restore Cargo.toml
    STRIP_CMD := strip --strip-unneeded
	TARGET_DIR := $(TARGET_DIR)/$(TARGET_TRIPLET)
else
    $(error Unsupported platform: $(UNAME) $(ARCH))
endif

# Path where compiled binary is copied to and therefore used by go
DEST_PATH := rustbin/$(TARGET_TRIPLET)

# Determine if we're using musl
IS_MUSL := $(if $(filter true,$(IS_ALPINE)),true,$(if $(findstring musl,$(TARGET_TRIPLET)),true,false))

# Set GOFLAGS for musl to use musl tag
ifeq ($(IS_MUSL), true)
    export GOFLAGS := -tags=musl
endif


install-build-tools:
	go install google.golang.org/protobuf/cmd/protoc-gen-go@v1.33.0
	cargo install cbindgen

install-dev-tools:
	go install github.com/vakenbolt/go-test-report@v0.9.3
	go install mvdan.cc/gofumpt@v0.6.0
	go install github.com/segmentio/golines@v0.12.2
	go install honnef.co/go/tools/cmd/staticcheck@latest

install-tools: install-build-tools install-dev-tools

build: build-glide-client generate-protobuf
	go build ./...
	cd benchmarks && go build -ldflags="-w" ./...

build-debug: build-glide-client-debug generate-protobuf
	go build -gcflags "-l -N" ./...
	cd benchmarks && go build -gcflags "-l -N" ./...

clean:
	go clean
	rm -f lib.h
	rm -f benchmarks/benchmarks
	rm -rf internal/protobuf
	rm -rf $(GLIDE_FFI_PATH)/target
	rm -rf reports


# Release build
build-glide-client: BUILD_FLAGS=--release
build-glide-client: TARGET_DIR := $(TARGET_DIR)/release
build-glide-client: build-glide-ffi

# Debug build (no --release flag)
build-glide-client-debug: BUILD_FLAGS=
build-glide-client-debug: TARGET_DIR := $(TARGET_DIR)/debug
build-glide-client-debug: build-glide-ffi

libglide_ffi:
	mkdir -p $(DEST_PATH)
	cd $(GLIDE_FFI_PATH) && \
	$(CARGO_FIX_CMD) && \
    GLIDE_NAME=$(GLIDE_NAME) GLIDE_VERSION=$(GLIDE_VERSION) cargo $(BUILD_CMD) $(BUILD_FLAGS) && \
	$(CARGO_POSTFIX_CMD) && \
	$(STRIP_CMD) $(TARGET_DIR)/libglide_ffi.a && \
	cp $(TARGET_DIR)/libglide_ffi.a $(GO_DIR)/$(DEST_PATH)

build-glide-ffi: libglide_ffi gen-c-bindings

gen-c-bindings:
	cd $(GLIDE_FFI_PATH) && \
	cbindgen --config cbindgen.toml --crate glide-ffi --output $(GO_DIR)/lib.h --lang c

generate-protobuf:
	rm -rf internal/protobuf
	mkdir -p internal/protobuf
	protoc --proto_path=$(GLIDE_CORE_PATH)/src/protobuf \
		--go_opt=Mconnection_request.proto=github.com/valkey-io/valkey-glide/go/v2/internal/protobuf \
		--go_opt=Mcommand_request.proto=github.com/valkey-io/valkey-glide/go/v2/internal/protobuf \
		--go_opt=Mresponse.proto=github.com/valkey-io/valkey-glide/go/v2/internal/protobuf \
		--go_out=./internal/protobuf \
		--go_opt=paths=source_relative \
		$(GLIDE_CORE_PATH)/src/protobuf/*.proto

lint:
	go vet ./...
	staticcheck ./...
	gofumpt -d .
	golines --dry-run --shorten-comments -m 127 .
	declare -i MISSING_HEADERS=0; \
	for file in `find . -type f -name '*.go' | grep -v protobuf`; do \
		head -1 $$file | grep '// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0' > /dev/null ; \
		if [[ $$? > 0 ]]; then \
			echo Missing licence header in $$file ; \
			((++MISSING_HEADERS)) ; \
		fi; \
	done; \
	exit $$MISSING_HEADERS

lint-ci: lint
	if [ "$$(gofumpt -l . | wc -l)" -gt 0 ]; then exit 1; fi
	if [ "$$(golines -l --shorten-comments -m 127 . | wc -l)" -gt 0 ]; then exit 1; fi

format:
	gofumpt -w .
	golines -w --shorten-comments -m 127 .

# unit tests - skip complete IT suite (including MT), and examples
unit-test:
	mkdir -p reports
	set -o pipefail; \
	go test -v ./... -skip 'Example|TestGlideTestSuite' $(if $(test-filter), -run $(test-filter)) \
	| tee >(go tool test2json -t -p github.com/valkey-io/valkey-glide/go/v2/internal/utils | go-test-report -o reports/unit-tests.html -t unit-test > /dev/null)

# example tests - skip complete IT suite (including MT)
example-test:
	mkdir -p reports
	set -o pipefail; \
    trap '../utils/cluster_manager.py stop --prefix cluster' EXIT; \
	NODE_OUTPUT=$$(python3 ../utils/cluster_manager.py start); \
	GLIDE_NODE=$$(echo "$$NODE_OUTPUT" | grep 'CLUSTER_NODES=' | cut -d'=' -f2); \
	echo "GLIDE_NODE=$$GLIDE_NODE"; \
	CLUSTER_OUTPUT=$$(python3 ../utils/cluster_manager.py start --cluster-mode); \
	GLIDE_CLUSTER_NODES=$$(echo "$$CLUSTER_OUTPUT" | grep 'CLUSTER_NODES=' | cut -d'=' -f2); \
	echo "GLIDE_CLUSTER_NODES=$$GLIDE_CLUSTER_NODES"; \
	go test -v . -skip Test $(if $(test-filter), -run $(test-filter)) -clusternodes $$GLIDE_CLUSTER_NODES -standalonenode $$GLIDE_NODE \
	| tee >(go tool test2json -t -p github.com/valkey-io/valkey-glide/go/v2 \
	| go-test-report -o reports/example-tests.html -t example-test > /dev/null)

# integration tests - run subtask with skipping modules tests
integ-test: export TEST_FILTER = -skip "TestGlideTestSuite/TestModule" $(if $(test-filter), -testify.m $(test-filter))
integ-test: __it

# modules tests - run substask with default filter
modules-test: export TEST_FILTER = $(if $(test-filter), -run $(test-filter), -testify.m TestGlideTestSuite/TestModule)
modules-test: __it

# pubsub tests - run pubsub tests using testify filter
pubsub-test: export TEST_FILTER = -skip "TestGlideTestSuite/TestModule" -testify.m TestPubSub
pubsub-test: __it

# long timeout tests - run subtask with the long timeout flag enabled
long-timeout-test: export TEST_FILTER = -skip "TestGlideTestSuite/TestModule" -long-timeout-tests $(if $(test-filter), -testify.m $(test-filter))
long-timeout-test: __it

# opentelemetry tests - run subtask with the opentelemetry flag enabled
opentelemetry-test: export TEST_FILTER = -skip "TestGlideTestSuite/TestModule" -otel-test $(if $(test-filter), -testify.m $(test-filter), -testify.m TestOpenTelemetry)
opentelemetry-test: __it

# Test command for integration tests
ifeq ($(IS_MUSL), true)
# musl gcc does not support -fsanitize=address, so we use clang instead
# TODO - Investigate musl + address sanitizer issue - https://github.com/valkey-io/valkey-glide/issues/4671
# Disabled for now
# MUSL_TEST_CMD := CC=clang go test -asan -v ./integTest/...
    TEST_CMD := CC="gcc" go test -v ./integTest/...
else
    TEST_CMD := CC="gcc -fsanitize=address" go test -v ./integTest/...
endif

__it:
	mkdir -p reports
	set -o pipefail; \
	$(TEST_CMD) \
	$(TEST_FILTER) \
	$(if $(filter true, $(tls)), --tls,) \
	$(if $(standalone-endpoints), --standalone-endpoints=$(standalone-endpoints)) \
	$(if $(cluster-endpoints), --cluster-endpoints=$(cluster-endpoints)) \
	| tee >(go tool test2json -t -p github.com/valkey-io/valkey-glide/go/v2/integTest | go-test-report -o reports/integ-tests.html -t integ-test > /dev/null)
# code above ^ is similar to `go test .... -json | go-test-report ....`, but it also prints plain text output to stdout
# `go test` prints plain text, tee duplicates it to stdout and to `test2json` which is coupled with `go-test-report` to generate the report
