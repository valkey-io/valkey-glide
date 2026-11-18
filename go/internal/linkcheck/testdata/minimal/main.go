// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

// Command minimal imports only the glide package. Nothing else in the program links a system
// framework or library, so it links only if the glide package's #cgo LDFLAGS name everything
// libglide_ffi.a needs.
package main

import _ "github.com/valkey-io/valkey-glide/go/v2"

func main() {}
