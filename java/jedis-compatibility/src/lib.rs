// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

// This is the jedis-compatibility wrapper that uses the same FFI code as the main client
// but compiles with GLIDE_NAME=GlideJedisAdapter

// Include the main Java FFI implementation
include!("../../src/lib.rs");
