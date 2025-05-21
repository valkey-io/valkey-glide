# NPM Continuous Deployment Workflow

This document describes the Continuous Deployment (CD) workflow for publishing Valkey Glide Node.js packages to npm.

## Overview

The NPM CD workflow uses a comprehensive, multi-stage approach to build, package, publish, and test the Valkey Glide Node.js packages. The workflow is defined in a single file (.github/workflows/npm-cd.yml) and includes the following benefits:

- Comprehensive platform support (Linux with both glibc and musl, macOS)
- Cross-compilation using Zig for Linux targets (with ABI version 2.17 for maximum compatibility)
- Universal binary support for macOS (ARM64 + x86_64)
- Smart version management for different types of releases
- Thorough testing of published packages
- Optimized TypeScript build process preserving documentation
- Automatic deprecation of packages if tests fail after publishing

## Workflow Structure

The workflow consists of the following jobs:

### 1. Parameter Determination (get-build-parameters)

Determines the appropriate version number, npm tag, and platform matrix based on the workflow trigger:

- For tags: Extracts version from the tag name (e.g., `v1.2.3` → `1.2.3`)
- For manual triggers: Uses the user-provided version
- For PRs: Uses a placeholder version (255.255.255)
- Sets npm tag based on release type:
  - "latest" for stable releases (1.0.0)
  - "next" for release candidates (1.0.0-rc1)
- Also determines whether packages should be published based on trigger type

### 2. Native Module Building (build-native-modules)

Builds the native bindings for each platform in the matrix:

- Uses a matrix strategy to build for each supported platform
- Sets up the appropriate build environment (glibc, musl, macOS)
- Installs Rust, Zig, and other required dependencies
- Builds native Node.js modules (.node files) with napi-rs
- Uploads the native modules as artifacts for later assembly
- Uploads JS interface files (native.js/native.d.ts) as artifacts

### 3. Package Preparation and Publishing (prepare-and-publish)

Prepares artifacts and publishes packages (when enabled):

- Downloads native modules and JS interface files
- Uses the NAPI artifacts command to distribute native modules to the correct package folders
- Builds TypeScript source code with optimized settings (--stripInternal --pretty --declaration)
- Sets correct versions in package.json file of the base package.
- Prepares the base package with all TypeScript code
- Copies necessary files (LICENSE, README) to each package
- Publishes the packages with single command, this single command call to prepublishOnly which is a script in package.json, napi prepublish.
- napi prepublish is a script that handles the preparation of the package for publishing, including:
  - Aligning the package.json files versions with the base package version.
  - Adding the native file to optionalDependencies in package.json.
- Uses npm tag based on version type (latest for stable, next for RC versions)

### 4. Release Testing (test-published-release)

Tests the published packages across platforms:

- Runs a matrix strategy to test on each supported platform
- Installs Valkey (or Redis as fallback) as a prerequisite for testing
- Installs the published package from npm
- Runs utils/node tests to verify functionality
- Reports test results

### 5. Node Tag Creation (create-node-tag)

Creates a Node.js-specific git tag when requested:

- Creates and pushes a tag in the format `vX.Y.Z-node`
- Only runs when explicitly requested via workflow input
- Helps track Node.js-specific releases

### 6. Cleanup Artifacts (cleanup-artifacts)

Cleans up artifacts after successful testing:

- Runs after test-published-release job completes
- Removes all artifacts created during the build process to save storage space
- Deletes native module artifacts, JS files, and package artifacts

### 7. Failure Handling (deprecate-on-failure)

Handles failures in the publish or test process:

- Triggers only if publishing was enabled and previous jobs failed
- Sets up Node.js and NPM with authentication
- Deprecates (not unpublishes, as valkey-glide token don't have such permissions) the base package and all platform packages to warn users
- Uses retry mechanism to handle transient errors with npm registry
- Uses robust JSON parsing to handle potential errors in package inspection
- Marks failed packages with clear deprecation notices explaining the failure

## TypeScript Build Process

The workflow includes an optimized TypeScript build process:

1. Uses npm caching for faster builds
2. Installs only the necessary dependencies for building
3. Compiles TypeScript with the following optimizations:
    - `--stripInternal`: Removes @internal marked items from declarations while preserving public documentation
    - `--pretty`: Formats error messages for better readability
    - `--declaration`: Ensures type declaration files are generated with full JSDoc/TSDoc documentation
4. Reports build statistics for monitoring output size

## Triggers

The workflow triggers on:

1. Pull requests that modify:
    - Workflow file itself (.github/workflows/npm-cd.yml)
    - JSON matrices (.github/json_matrices/**)
    - Package configuration (node/package.json)
    - NPM directory structure (node/npm/**)
    - Rust client files (node/rust-client/Cargo.toml, node/rust-client/src/**)
    - TypeScript sources (node/src/**/*.ts)
2. Pushing tags that match the pattern "v*.*.\*" (e.g., v1.2.3, v1.2.3-rc1)

3. Manual workflow dispatch with:
    - Version input (required)
    - Publish option (boolean)
    - Create node tag option (boolean)
