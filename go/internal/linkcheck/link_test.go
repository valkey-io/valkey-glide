// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

// Package linkcheck holds a regression test that links a program importing only the glide
// package, so the #cgo LDFLAGS in go/base_client.go and go/options/cgo_flags.go must name
// every system framework and library that libglide_ffi.a needs.
//
// Other test binaries cannot catch a missing flag: they import packages such as crypto/x509,
// which add -framework CoreFoundation on darwin themselves. Go up to 1.26 also added it for
// every cgo program on darwin/arm64 (runtime/cgo); 1.27 does not
// (https://github.com/valkey-io/valkey-glide/issues/7784).
package linkcheck

import (
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"testing"
)

// hostTarget returns the go/rustbin directory that the glide package's #cgo LDFLAGS select for
// this test binary's platform and musl tag.
func hostTarget() string {
	libc := "gnu"
	if musl {
		libc = "musl"
	}
	switch runtime.GOOS + "/" + runtime.GOARCH {
	case "darwin/arm64":
		return "aarch64-apple-darwin"
	case "darwin/amd64":
		return "x86_64-apple-darwin"
	case "linux/arm64":
		return "aarch64-unknown-linux-" + libc
	case "linux/amd64":
		return "x86_64-unknown-linux-" + libc
	}
	return ""
}

// TestMinimalProgramLinks builds testdata/minimal with the toolchain that runs the test.
func TestMinimalProgramLinks(t *testing.T) {
	target := hostTarget()
	if target == "" {
		t.Skipf("no prebuilt archive for %s/%s", runtime.GOOS, runtime.GOARCH)
	}
	if _, err := os.Stat(filepath.Join("..", "..", "rustbin", target, "libglide_ffi.a")); err != nil {
		msg := "no go/rustbin/" + target + "/libglide_ffi.a; run `make build` first"
		if os.Getenv("GITHUB_ACTIONS") != "" {
			t.Fatal(msg)
		}
		t.Skip(msg)
	}

	args := []string{"build", "-o", filepath.Join(t.TempDir(), "minimal")}
	if musl {
		// A -tags flag given to `go test` does not reach this child build; only GOFLAGS does.
		args = append(args, "-tags=musl")
	}
	args = append(args, "./testdata/minimal")
	cmd := exec.Command(filepath.Join(runtime.GOROOT(), "bin", "go"), args...)
	// Use the toolchain that runs this test, not one go.mod or GOTOOLCHAIN would switch to.
	cmd.Env = append(os.Environ(), "GOTOOLCHAIN=local")
	if out, err := cmd.CombinedOutput(); err != nil {
		t.Fatalf("a program that imports only the glide package does not link with %s: %v\n%s",
			runtime.Version(), err, out)
	}
}
