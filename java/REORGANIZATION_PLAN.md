# Java Directory Reorganization Plan

## Current Mess Analysis

### Problems:
1. **Mixed Concerns**: Core JNI code in `/java/src/main/java/io/valkey/glide/core/` mixed with client API in `/java/client/`
2. **Scattered Rust**: Rust JNI code in `/rust-jni/` separate from Java project
3. **Legacy Code**: `/java-old/` directory with old implementation
4. **Documentation Chaos**: Multiple session docs scattered throughout
5. **Temporary Files**: `temp-excluded-files/` in main directory

### Current Structure:
```
valkey-glide/
├── java/                          # Main Java project (CURRENT)
│   ├── src/main/java/             # Core JNI integration
│   ├── client/                    # Client API module
│   ├── integTest/                 # Integration tests
│   ├── benchmarks/                # Benchmark code
│   ├── temp-excluded-files/       # Legacy code (MESSY)
│   └── docs/                      # Session documentation
├── java-old/                      # Old implementation (WASTE)
├── rust-jni/                      # Rust JNI code (SEPARATE)
└── glide-core/                    # Core Rust library
```

## Proposed Clean Structure

### Target Structure:
```
valkey-glide/
├── java/                          # Clean Java project
│   ├── glide-core/               # Core JNI module
│   │   ├── src/main/java/        # Core JNI classes
│   │   ├── src/main/rust/        # Rust JNI code (INTEGRATED)
│   │   └── build.gradle          # Core build config
│   ├── glide-client/             # Client API module
│   │   ├── src/main/java/        # Client API classes
│   │   └── build.gradle          # Client build config
│   ├── integration-tests/        # Integration tests
│   ├── benchmarks/               # Benchmark code
│   ├── legacy/                   # Archived legacy code
│   └── docs/                     # Project documentation
├── archive/                      # Archived old implementations
└── glide-core/                   # Core Rust library (UNCHANGED)
```

## Reorganization Steps

### Phase 1: Create New Structure
1. Create new module directories
2. Move Rust JNI code into Java project
3. Consolidate documentation

### Phase 2: Migrate Code
1. Move core JNI classes to `glide-core` module
2. Move client API to `glide-client` module
3. Update build configurations

### Phase 3: Archive Legacy
1. Move `java-old/` to `archive/java-old/`
2. Move `temp-excluded-files/` to `legacy/`
3. Clean up documentation

### Phase 4: Update Build System
1. Update settings.gradle
2. Update build.gradle files
3. Update CI/CD configurations

## Implementation Benefits

1. **Clear Separation**: Core JNI vs Client API
2. **Integrated Rust**: Rust JNI code within Java project
3. **Clean Legacy**: Archived but accessible
4. **Unified Build**: Single build system
5. **Better Documentation**: Consolidated docs
