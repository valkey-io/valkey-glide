// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package options

// #cgo LDFLAGS: -lglide_ffi
// #cgo !windows LDFLAGS: -lm
// #cgo darwin LDFLAGS: -framework Security
// #cgo darwin,amd64 LDFLAGS: -framework CoreFoundation
// #cgo linux,amd64,!musl LDFLAGS: -L${SRCDIR}/../rustbin/x86_64-unknown-linux-gnu
// #cgo linux,amd64,musl LDFLAGS: -L${SRCDIR}/../rustbin/x86_64-unknown-linux-musl
// #cgo linux,arm64,!musl LDFLAGS: -L${SRCDIR}/../rustbin/aarch64-unknown-linux-gnu
// #cgo linux,arm64,musl LDFLAGS: -L${SRCDIR}/../rustbin/aarch64-unknown-linux-musl
// #cgo darwin,arm64 LDFLAGS: -L${SRCDIR}/../rustbin/aarch64-apple-darwin
// #cgo darwin,amd64 LDFLAGS: -L${SRCDIR}/../rustbin/x86_64-apple-darwin
import "C"
