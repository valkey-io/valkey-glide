module github.com/aws/glide-for-redis/go/glide/benchmarks

go 1.18

replace github.com/aws/glide-for-redis/go/glide => ../

require (
	github.com/aws/glide-for-redis/go/glide v0.0.0
	github.com/redis/go-redis/v9 v9.5.1
)

require (
	github.com/cespare/xxhash/v2 v2.2.0 // indirect
	github.com/dgryski/go-rendezvous v0.0.0-20200823014737-9f7001d12a5f // indirect
	google.golang.org/protobuf v1.32.0 // indirect
)
