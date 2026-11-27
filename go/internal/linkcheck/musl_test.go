// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

//go:build musl

package linkcheck

// musl reports whether this test binary was built with the musl tag, which also selects the
// musl archive directory in the glide package's #cgo LDFLAGS.
const musl = true
