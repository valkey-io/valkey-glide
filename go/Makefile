# Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

SHELL:=/bin/bash

install-build-tools:
	go install google.golang.org/protobuf/cmd/protoc-gen-go@v1.33.0
	cargo install cbindgen

install-dev-tools-go1.20:
	go install github.com/vakenbolt/go-test-report@v0.9.3
	go install mvdan.cc/gofumpt@v0.4.0
	go install github.com/segmentio/golines@v0.11.0
	go install honnef.co/go/tools/cmd/staticcheck@v0.3.3

install-dev-tools-go1.20.0: install-dev-tools-go1.20

install-dev-tools-go1.22:
	go install github.com/vakenbolt/go-test-report@v0.9.3
	go install mvdan.cc/gofumpt@v0.6.0
	go install github.com/segmentio/golines@v0.12.2
	go install honnef.co/go/tools/cmd/staticcheck@latest

install-dev-tools-go1.22.0: install-dev-tools-go1.22

install-dev-tools: install-dev-tools-go1.22.0

install-tools-go1.20.0: install-build-tools install-dev-tools-go1.20.0

install-tools-go1.22.0: install-build-tools install-dev-tools-go1.22.0

install-tools: install-tools-go1.22.0

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
	rm -rf protobuf
	rm -rf target
	rm -rf reports

build-glide-client:
	cargo build --release
	cbindgen --config cbindgen.toml --crate glide-rs --output lib.h --lang c
	@if [ "$$(uname)" = "Darwin" ] && [ "$$(arch)" = "arm64" ]; then \
		echo "Platform: aarch64-apple-darwin"; \
		strip -x target/release/libglide_rs.a; \
		mkdir -p rustbin/aarch64-apple-darwin; \
		cp target/release/libglide_rs.a rustbin/aarch64-apple-darwin/; \
	elif [ "$$(uname)" = "Linux" ]; then \
		strip --strip-unneeded target/release/libglide_rs.a; \
		if [ "$$(arch)" = "arm64" ] || [ "$$(arch)" = "aarch64" ]; then \
			echo "Platform: aarch64-unknown-linux-gnu"; \
			mkdir -p rustbin/aarch64-unknown-linux-gnu; \
			cp target/release/libglide_rs.a rustbin/aarch64-unknown-linux-gnu/; \
		elif [ "$$(arch)" = "x86_64" ]; then \
			echo "Platform: x86_64-unknown-linux-gnu"; \
			mkdir -p rustbin/x86_64-unknown-linux-gnu; \
			cp target/release/libglide_rs.a rustbin/x86_64-unknown-linux-gnu/; \
		fi; \
	else \
		echo "Unsupported platform: $(shell uname), $(shell arch)"; \
		exit 1; \
	fi


build-glide-client-debug:
	cargo build
	cbindgen --config cbindgen.toml --crate glide-rs --output lib.h --lang c

generate-protobuf:
	rm -rf protobuf
	mkdir -p protobuf
	protoc --proto_path=../glide-core/src/protobuf \
		--go_opt=Mconnection_request.proto=github.com/valkey-io/valkey-glide/go/protobuf \
		--go_opt=Mcommand_request.proto=github.com/valkey-io/valkey-glide/go/protobuf \
		--go_opt=Mresponse.proto=github.com/valkey-io/valkey-glide/go/protobuf \
		--go_out=./protobuf \
		--go_opt=paths=source_relative \
		../glide-core/src/protobuf/*.proto

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
# due to a bug in go 1.20 and 1.21, we need to use -run flag to get -skip flag to work
unit-test:
	mkdir -p reports
	set -o pipefail; \
	LD_LIBRARY_PATH=$(shell find . -name libglide_rs.a|grep -w release|tail -1|xargs dirname|xargs readlink -f):${LD_LIBRARY_PATH} \
	go test -v ./... -skip 'Example|TestGlideTestSuite' -run Test $(if $(test-filter), -run $(test-filter)) \
	| tee >(go tool test2json -t -p github.com/valkey-io/valkey-glide/go/utils | go-test-report -o reports/unit-tests.html -t unit-test > /dev/null)

# example tests - skip complete IT suite (including MT)
example-test:
	mkdir -p reports
	set -o pipefail; \
	../utils/cluster_manager.py start -p 6379 -r 0
	@OUTPUT=$$(python3 ../utils/cluster_manager.py start --cluster-mode); \
	GLIDE_CLUSTER_NODES=$$(echo "$$OUTPUT" | grep 'CLUSTER_NODES=' | cut -d'=' -f2); \
	LD_LIBRARY_PATH=$(shell find . -name libglide_rs.a|grep -w release|tail -1|xargs dirname|xargs readlink -f):${LD_LIBRARY_PATH} \
	go test -v ./api -skip Test $(if $(test-filter), -run $(test-filter)) -clusternodes $$GLIDE_CLUSTER_NODES \
	| tee >(go tool test2json -t -p github.com/valkey-io/valkey-glide/go/api \
	| go-test-report -o reports/example-tests.html -t example-test > /dev/null)

	../utils/cluster_manager.py stop --prefix cluster

# integration tests - run subtask with skipping modules tests
integ-test: export TEST_FILTER = -skip TestGlideTestSuite/TestModule $(if $(test-filter), -testify.m $(test-filter))
integ-test: __it

# modules tests - run substask with default filter
modules-test: export TEST_FILTER = $(if $(test-filter), -run $(test-filter), -testify.m TestGlideTestSuite/TestModule)
modules-test: __it

__it:
	mkdir -p reports
	set -o pipefail; \
	LD_LIBRARY_PATH=$(shell find . -name libglide_rs.a|grep -w release|tail -1|xargs dirname|xargs readlink -f):${LD_LIBRARY_PATH} \
	go test -v ./integTest/... \
	$(TEST_FILTER) \
	$(if $(filter true, $(tls)), --tls,) \
	$(if $(standalone-endpoints), --standalone-endpoints=$(standalone-endpoints)) \
	$(if $(cluster-endpoints), --cluster-endpoints=$(cluster-endpoints)) \
	| tee >(go tool test2json -t -p github.com/valkey-io/valkey-glide/go/integTest | go-test-report -o reports/integ-tests.html -t integ-test > /dev/null)
# code above ^ is similar to `go test .... -json | go-test-report ....`, but it also prints plain text output to stdout
# `go test` prints plain text, tee duplicates it to stdout and to `test2json` which is coupled with `go-test-report` to generate the report
