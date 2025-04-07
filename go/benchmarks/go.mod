module github.com/valkey-io/valkey-glide/go/benchmarks

go 1.22

replace github.com/valkey-io/valkey-glide/go => ../

require (
	github.com/redis/go-redis/v9 v9.5.5
	github.com/valkey-io/valkey-glide/go v0.0.0
)

require (
	github.com/cespare/xxhash/v2 v2.2.0 // indirect
	github.com/dgryski/go-rendezvous v0.0.0-20200823014737-9f7001d12a5f // indirect
	google.golang.org/protobuf v1.33.0 // indirect
)
