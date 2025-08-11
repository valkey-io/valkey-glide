# CI Troubleshooting Guide

This document contains common CI failures and their solutions to help with debugging and future comparisons.

## Rust Build Failures

### Cargo-zigbuild Target Triple Issues

**Problem**: CI fails with malformed target triple errors during Rust compilation.

**Example Error Logs**:
```
error: linking with `/home/runner/.cache/cargo-zigbuild/0.20.1/zigcc-x86_64-unknown-linux-gnu.2.17-d94d.sh` failed: exit status: 1
note: LLVM failed to parse 'x86_64-unknown-linux4.19.0-gnu2.17.0': No available targets are compatible with triple "x86_64-unknown-linux4.19.0-gnu2.17.0"
error: sub-compilation of compiler_rt failed
error: sub-compilation of libubsan failed
error: could not compile `glide-rs` (lib) due to 1 previous error
```

**Root Cause**: 
cargo-zigbuild auto-detects system information and incorporates kernel version (4.19.0) and glibc version (2.17.0) into the target triple, creating malformed targets like `x86_64-unknown-linux4.19.0-gnu2.17.0` instead of the correct `x86_64-unknown-linux-gnu`.

**Solution**: 
The install-zig action has been updated to explicitly set target environment variables:
- `CARGO_BUILD_TARGET` - Set to the correct target triple
- `CARGO_ZIGBUILD_TARGET` - Set to the correct target triple

This prevents cargo-zigbuild from auto-detecting and creating malformed target triples.

**Related Files**:
- `.github/workflows/install-zig/action.yml` - Fixed to set explicit target configuration
- `.github/workflows/install-shared-dependencies/action.yml` - Updated to pass target parameter

### Other Common Rust Build Issues

**Gradle Cache Restoration Failures**:
```
warning: Gradle cache restoration failed due to missing files.
```

This is typically a non-critical warning that doesn't affect the build outcome.

## Log Quote References for Future Comparisons

When encountering CI failures, include relevant log quotes in issues to enable pattern matching and comparison with historical failures. 

**Standard Log Quotes to Include**:
1. Error messages with specific file paths and exit codes
2. LLVM/compiler error messages with target information
3. Sub-compilation failure messages
4. Cache restoration warnings or failures

**Example Format**:
```
- error: linking with `<path>` failed: exit status: 1
- note: LLVM failed to parse '<target-triple>': <specific error>
- error: sub-compilation of <component> failed
- error: could not compile `<crate>` (lib) due to <count> previous error
- warning: <component> cache restoration failed due to <reason>
```

This documentation helps identify recurring patterns and improves troubleshooting efficiency for similar CI issues in the future.