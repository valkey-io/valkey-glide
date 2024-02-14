install-tools:
	@cat tools.go | grep _ | awk -F'"' '{print $$2}' | xargs -tI % go install %

build: build-glide-core build-glide-client generate-protobuf
	go build ./...

build-glide-core:
	cd ../glide-core; cargo build --release

build-glide-client:
	cargo build --release

generate-protobuf:
	mkdir -p protobuf
	protoc --proto_path=../glide-core/src/protobuf \
		--go_opt=Mconnection_request.proto=github.com/aws/glide-for-redis/go/protobuf \
		--go_opt=Mredis_request.proto=github.com/aws/glide-for-redis/go/protobuf \
		--go_opt=Mresponse.proto=github.com/aws/glide-for-redis/go/protobuf \
		--go_out=./protobuf \
		--go_opt=paths=source_relative \
		../glide-core/src/protobuf/*.proto

lint:
	go vet ./...
	staticcheck ./...

unit-test-report:
	mkdir -p reports
	go test -race ./... -json | go-test-report -o reports/unit-test-report.html
