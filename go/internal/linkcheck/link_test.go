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

// hostTarget returns the go/rustbin directory name for the platform the test runs on.
func hostTarget() string {
	switch runtime.GOOS + "/" + runtime.GOARCH {
	case "darwin/arm64":
		return "aarch64-apple-darwin"
	case "darwin/amd64":
		return "x86_64-apple-darwin"
	case "linux/arm64":
		return "aarch64-unknown-linux-*"
	case "linux/amd64":
		return "x86_64-unknown-linux-*"
	}
	return ""
}

// TestMinimalProgramLinks builds testdata/minimal with the toolchain that runs the test.
func TestMinimalProgramLinks(t *testing.T) {
	target := hostTarget()
	if target == "" {
		t.Skipf("no prebuilt archive for %s/%s", runtime.GOOS, runtime.GOARCH)
	}
	archives, err := filepath.Glob(filepath.Join("..", "..", "rustbin", target, "libglide_ffi.a"))
	if err != nil {
		t.Fatal(err)
	}
	if len(archives) == 0 {
		const msg = "no go/rustbin/<target>/libglide_ffi.a for this host; run `make build` first"
		if os.Getenv("GITHUB_ACTIONS") != "" {
			t.Fatal(msg)
		}
		t.Skip(msg)
	}

	goBin := filepath.Join(runtime.GOROOT(), "bin", "go")
	cmd := exec.Command(goBin, "build", "-o", filepath.Join(t.TempDir(), "minimal"), "./testdata/minimal")
	// Use the toolchain that runs this test, not one go.mod or GOTOOLCHAIN would switch to.
	cmd.Env = append(os.Environ(), "GOTOOLCHAIN=local")
	if out, err := cmd.CombinedOutput(); err != nil {
		t.Fatalf("a program that imports only the glide package does not link with %s: %v\n%s",
			runtime.Version(), err, out)
	}
}
