# NPM Continuous Deployment Workflow

This directory contains modular workflow components used by the main npm-cd workflow for publishing Valkey Glide Node.js packages.

## Overview

The NPM CD workflow has been refactored into modular components that work together to build, package, publish, and test the Valkey Glide Node.js packages. This approach:

- Improves maintainability by separating concerns
- Reduces duplication and improves code reuse
- Makes the workflow more understandable and easier to debug
- Allows for independent updates to specific stages of the process

## Component Workflows

### 1. `determine-version.yml`

Determines the appropriate version number and npm tag (latest/next) based on the workflow trigger:

- For tags: Extracts version from the tag name
- For manual triggers: Uses the user-provided version
- For PRs: Uses a placeholder version (0.0.0-pr)
- Sets npm tag to "next" for release candidates, otherwise "latest"

### 2. `load-platform-matrix.yml`

Generates the build matrix for platform-specific packages:

- Filters entries from build-matrix.json that include "npm" in their PACKAGE_MANAGERS
- Optimizes the build matrix by removing redundant builds (e.g., macOS x64 if ARM64 is present)

### 3. `build-binaries.yml`

Builds the native bindings for each platform in the matrix:

- Sets up the appropriate build environment (glibc, musl, macOS)
- Installs Rust, Zig, and other required dependencies
- Builds native Node.js modules (.node files)
- Handles special cases like universal macOS binaries
- Uploads artifacts for the next stages

### 4. `organize-artifacts.yml`

Prepares artifacts for publishing:

- Downloads platform-specific binary artifacts
- Uses napi-rs to organize binaries into platform-specific package directories
- Copies JavaScript/TypeScript binding files to each package
- Verifies package completeness and generates checksums

### 5. `publish-platform-packages.yml`

Publishes the platform-specific packages to npm:

- Verifies package integrity and completeness
- Updates version in package.json files
- Publishes packages with the appropriate npm tag
- Handles cases of already-published packages
- Tracks publishing success/failure for reporting

### 6. `publish-base-package.yml`

Publishes the main TypeScript package:

- Creates the TypeScript-only package with the compiled JavaScript
- Sets up entry points and package metadata
- Uses napi-rs to add optional dependencies on platform packages
- Publishes the package with the appropriate npm tag

### 7. `test-release.yml`

Tests the published packages across platforms:

- Installs the published package from npm
- Runs platform-specific tests to verify functionality
- Handles test failures by unpublishing broken packages
- Reports test results

## Usage

The main `npm-cd.yml` workflow calls these components in sequence, passing outputs from one to the next:

```yaml
jobs:
  determine-version:
    uses: ./.github/workflows/CD-NPM/determine-version.yml
    with:
      event_name: ${{ github.event_name }}
      version: ${{ github.event.inputs.version }}
      ref: ${{ github.ref }}
  
  load-platform-matrix:
    uses: ./.github/workflows/CD-NPM/load-platform-matrix.yml
  
  # Additional jobs follow the same pattern...
```

## Modifying Components

When modifying a component:

1. Update the relevant workflow file for the specific stage
2. Test changes in a PR to verify compatibility with other components
3. Update inputs/outputs if needed, ensuring they match between dependent jobs

## Adding New Platforms

To add support for a new platform:

1. Update the build matrix in `.github/json_matrices/build-matrix.json`
2. Ensure the new platform has appropriate PACKAGE_MANAGERS entry including "npm"
3. Add platform-specific build logic if needed in `build-binaries.yml`

## Troubleshooting

Each component records detailed information in step summaries during execution. Check the specific component workflow where the failure occurred for detailed logs and error messages.

Common issues:

- Missing artifacts: Check the build-binaries job outputs and artifact uploads
- Publishing failures: Check npm authentication and version conflicts
- Test failures: Check platform-specific test results and environment setup
