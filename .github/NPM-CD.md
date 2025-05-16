# NPM Continuous Deployment Workflow

This document describes the Continuous Deployment (CD) workflow for publishing Valkey Glide Node.js packages to npm.

## Overview

The NPM CD workflow uses a comprehensive, multi-stage approach to build, package, publish, and test the Valkey Glide Node.js packages. The workflow is defined in a single file (.github/workflows/npm-cd.yml) and includes the following benefits:

- Comprehensive platform support (Linux with both glibc and musl, macOS)
- Cross-compilation using Zig for Linux targets
- Universal binary support for macOS (ARM64 + x86_64)
- Smart version management for different types of releases
- Thorough testing of published packages

## Workflow Structure

The workflow consists of several sequential jobs:

### 1. Parameter Determination (get-build-parameters)

Determines the appropriate version number, npm tag, and platform matrix based on the workflow trigger:

- For tags: Extracts version from the tag name (e.g., `v1.2.3` → `1.2.3`)
- For manual triggers: Uses the user-provided version
- For PRs: Uses a placeholder version (255.255.255)
- Sets npm tag to "next" for release candidates, otherwise "latest"
- Also determines whether packages should be published based on trigger type

### 2. Native Module Building (build-native-modules)

Builds the native bindings for each platform in the matrix:

- Uses a matrix strategy to build for each supported platform
- Sets up the appropriate build environment (glibc, musl, macOS)
- Installs Rust, Zig, and other required dependencies
- Builds native Node.js modules (.node files) with napi-rs
- Copies native modules directly to the npm/<platform> directories
- Uploads only the JS interface files (native.js/native.d.ts) as artifacts

### 3. Package Preparation (prepare-and-version-packages)

Prepares artifacts for publishing:

- Downloads only the generated JS interface files (native.js and native.d.ts)
- Builds TypeScript source code for the base package
- Uses files already placed into platform-specific directories during the build step
- Sets up the package.json files with correct version information
- Uses napi-rs prepublish to configure optional dependencies between packages and align versions
- Organizes artifacts under the `node/npm/` directory for publishing

### 4. Platform Package Publishing (publish-platform-packages)

Publishes the platform-specific packages to npm:

- Runs only when should_publish is true (tags or explicit manual trigger)
- Sets up Node.js and NPM with authentication
- Publishes each platform package with the appropriate npm tag
- Packages are named using the convention: `@valkey/valkey-glide-<os>-<arch>[-<libc>]`

### 5. Base Package Publishing (publish-base-package)

Publishes the main TypeScript package:

- Runs after platform packages are published to ensure optional dependencies are available
- Sets up Node.js and NPM with authentication
- Publishes the base package with the appropriate npm tag
- Reports success or failure of the publish operation

### 6. Release Testing (test-published-release)

Tests the published packages across platforms:

- Runs a matrix strategy to test on each supported platform
- Installs Valkey as a prerequisite for testing
- Installs the published package from npm
- Runs utils/node tests to verify functionality
- Reports test results

### 7. Failure Handling (unpublish-on-failure)

Handles failures in the publish or test process:

- Triggers only if publishing was enabled and previous jobs failed
- Sets up Node.js and NPM with authentication
- Unpublishes the base package and all platform packages to prevent broken releases
- Uses the --force flag to ensure unpublish works even if packages were just published

## Adding New Platforms

To add support for a new platform:

1. Update the build matrix in `.github/json_matrices/build-matrix.json`
2. Ensure the new platform has appropriate PACKAGE_MANAGERS entry including "npm"
3. Add platform-specific build logic if needed in the workflow

## Triggers

The workflow can be triggered by:

1. Pull requests that modify relevant workflow files or source code
2. Pushing tags that match the pattern "v*.*.*" (e.g., v1.2.3, v1.2.3-rc1)
3. Manual workflow dispatch with version input and publish option
