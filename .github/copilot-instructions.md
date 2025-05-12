# Valkey GLIDE Project Context for GitHub Copilot

## Project Overview

Valkey GLIDE is a polyglot client library for Valkey/Redis servers. The Node.js implementation combines TypeScript code with Rust-based native modules.

## Current Structure - Node.js Client Focus

- **Node.js wrapper:** `node/`
  - TypeScript source: `node/src/`
  - Compiled output: `node/build-ts/`
  - Rust client: `node/rust-client/`
  - Package templates: `node/npm/<platform>/` and `node/npm/glide/`
- **CI/CD Workflows:** 
  - Main workflow: `.github/workflows/npm-cd.yml`
  - Modular components: `.github/workflows/CD-NPM/*.yml`

## Modular Workflow Architecture

The npm-cd.yml workflow has been refactored into modular components in `.github/workflows/CD-NPM/`:

### Components and Flow

1. **`determine-version.yml`**: Sets release version and npm tag (latest/next)
2. **`load-platform-matrix.yml`**: Filters build matrix for npm packages
3. **`build-binaries.yml`**: Builds native modules for each platform
4. **`organize-artifacts.yml`**: Arranges platform-specific packages
5. **`publish-platform-packages.yml`**: Publishes platform-specific npm packages
6. **`publish-base-package.yml`**: Creates and publishes the TypeScript package
7. **`test-release.yml`**: Tests published packages across platforms

The main workflow orchestrates these components in sequence, passing outputs between them.

### Package Structure

- **Native Packages** (`@valkey/valkey-glide-<platform>-<arch>`)
  - Platform-specific .node binary
  - JavaScript/TypeScript bindings (native.js, native.d.ts)
  
- **TypeScript Package** (`@valkey/valkey-glide`)
  - TypeScript code with high-level API
  - Optional dependencies on platform-specific packages

## NAPI-RS CLI Tools

Key tools used in the workflow:
- `napi build`: Creates native modules from Rust
- `napi artifacts`: Organizes binaries by platform
- `napi prepublish`: Adds platform dependencies to main package

## Current Status

- Workflow has been modularized into reusable components
- Each component handles a specific stage independently
- Developer documentation added at `.github/workflows/CD-NPM/DEVELOPER.md`
- Benefits include improved maintainability, debugging, and transparency

## Documentation

When modifying workflow components:
- Update the corresponding section in `DEVELOPER.md`
- Document input/output parameters for workflow components
- Keep CLAUDE.md in sync with any architectural changes