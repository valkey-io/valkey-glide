//go:build tools

package glide

// This file ensures rustbin platform directories are included when using 'go mod vendor'.
//
// This file is AUTO-GENERATED during releases. Do not edit manually.
// It forces vendoring to include all platform-specific rustbin packages.
//
// See: https://github.com/valkey-io/valkey-glide/issues/4721
import (
	_ "github.com/valkey-io/valkey-glide/go/v2/rustbin/aarch64-apple-darwin"
	_ "github.com/valkey-io/valkey-glide/go/v2/rustbin/aarch64-unknown-linux-gnu"
	_ "github.com/valkey-io/valkey-glide/go/v2/rustbin/aarch64-unknown-linux-musl"
	_ "github.com/valkey-io/valkey-glide/go/v2/rustbin/x86_64-apple-darwin"
	_ "github.com/valkey-io/valkey-glide/go/v2/rustbin/x86_64-unknown-linux-gnu"
	_ "github.com/valkey-io/valkey-glide/go/v2/rustbin/x86_64-unknown-linux-musl"
)
