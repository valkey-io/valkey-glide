// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package options

// FlushMode represents the database flush operation mode
type FlushMode string

const (
	// SYNC flushes synchronously.
	// Since Valkey 6.2 and above.
	SYNC FlushMode = "SYNC"

	// ASYNC flushes asynchronously.
	ASYNC FlushMode = "ASYNC"
)
