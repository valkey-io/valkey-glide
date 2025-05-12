# CI/CD Workflow Guide

### Overview

Our CI/CD pipeline tests and builds our project across multiple languages, versions, and environments. This guide outlines the key components and processes of our workflow.

### Workflow Triggers

-   Pull requests
-   Pushes to `main` or release branches (PR merges)
-   Scheduled runs (daily) - starts CI pipelines for all clients
-   Manual trigger (`workflow_dispatch`) - a developer can start a client's pipeline or the scheduled one to run all pipelines on demand

<img width="437" alt="Job triggers" src="https://github.com/user-attachments/assets/58bf2b76-d778-4e43-8891-5dcbf0ff9b72">

### Test coverage

There are two levels of testing: the basic one and full (_aka_ `full-matrix`).
Basic amount of testing is executed on every open and merged PR. The full set of tests is executed by the scheduled job.
A developer can select the level when starting a job, either scheduled or client's pipeline.

<img width="264" alt="Matrices" src="https://github.com/user-attachments/assets/72857f80-078c-4e11-bcc6-75beb0125a9d">

### Language-Specific Workflows

Each language has its own workflow file with similar structure but language-specific steps, for example python.yml for Python, or java.yml for Java.

### Shared Components

#### Matrix Files

While workflows are language-specific, the matrix files are shared across all workflows.
Workflows are starting by loading the matrix files from the `.github/json_matrices` directory.

-   `engine-matrix.json`: Defines the versions of the engine to test against.
-   `build-matrix.json`: Defines the host environments for testing.
-   `supported-languages-versions.json`: Defines the supported versions of languages.

All matrices have a `run` like field which specifies if the configuration should be tested on every workflow run.
This allows for flexible control over which configurations are tested in different scenarios, optimizing CI/CD performance and resource usage.

#### Engine Matrix (engine-matrix.json)

Defines the versions of Valkey engine to test against:

```json
[
    { "type": "valkey", "version": "7.2.5", "run": "always" }
    // ... other configurations
]
```

-   `type`: The type of engine (e.g., Valkey, Redis).
-   `version`: Specifies the engine version that the workflow should checkout.
    For example, "7.2.5" represents a release tag, while "7.0" denotes a branch name. The workflow should use this parameter to checkout the specific release version or branch to build the engine with the appropriate version.
-   `run`: Specifies if the engine version should be tested on every workflow.

#### Build Matrix (build-matrix.json)

Defines the host environments for testing:

```json
[
    {
        "OS": "ubuntu",
        "RUNNER": "ubuntu-latest",
        "TARGET": "x86_64-unknown-linux-gnu",
        "run": ["always", "python", "node", "java"]
    }
    // ... other configurations
]
```

-   `OS`: The operating system of the host.
-   `RUNNER`: The GitHub runner to use.
-   `TARGET`: The target environment as defined in Rust. To see a list of available targets, run `rustup target list`.
-   `run`: Specifies which language workflows should use this host configuration. The value `always` indicates that the configuration should be used for every workflow trigger.

#### Supported Languages Version (supported-languages-version.json)

Defines the supported versions of languages:

```json
[
    {
        "language": "java",
        "versions": ["11", "17"],
        "always-run-versions": ["17"]
    }
    // ... other configurations
]
```

-   `language`: The language for which the version is supported.
-   `versions`: The full versions supported of the language which will test against scheduled.
-   `always-run-versions`: The versions that will always be tested, regardless of the workflow trigger.

#### Triggering Workflows

-   Push to `main` by merging a PR or create a new pull request to run workflows automatically.
-   Use `workflow_dispatch` for manual triggers, accepting a boolean configuration parameter to run all configurations.
-   Scheduled runs are triggered daily to ensure regular testing of all configurations.

### Mutual vs. Language-Specific Components

#### Mutual

-   Matrix files - `.github/json_matrices/`
-   Shared dependencies installation - `.github/workflows/install-shared-dependencies/action.yml`
-   Rust linters - `.github/workflows/lint-rust/action.yml`

#### Language-Specific

-   Package manager commands
-   Testing frameworks
-   Build processes

### Customizing Workflows

Modify `<language>.yml` files to adjust language-specific steps.
Update matrix files to change tested versions or environments.
Adjust cron schedules in workflow files for different timing of scheduled runs.

### Workflow Matrices

We use dynamic matrices for our CI/CD workflows, which are created using the `create-test-matrices` action. This action is defined in `.github/workflows/create-test-matrices/action.yml`.

#### How it works

1. The action is called with a `language-name` input and `dispatch-run-full-matrix` input.
2. It reads the `engine-matrix.json`, `build-matrix.json`, and `supported-languages-version.json` files.
3. It filters the matrices based on the inputs and the event type.
4. It generates three matrices:
    - Engine matrix: Defines the types and versions of the engine to test against, for example Valkey 7.2.5.
    - Host matrix: Defines the host platforms to run the tests on, for example Ubuntu on ARM64.
    - Language-version matrix: Defines the supported versions of languages, for example python 3.9.

#### Outputs

-   `engine-matrix-output`: The generated engine matrix.
-   `host-matrix-output`: The generated host matrix.
-   `language-version-matrix-output`: The generated language version matrix.

This dynamic matrix generation allows for flexible and efficient CI/CD workflows, adapting the test configurations based on the type of change and the specific language being tested.

### Node.js CD Workflow Structure

The Node.js CD workflow (`npm-cd.yml`) uses a multi-stage approach to build and publish platform-specific native modules and a TypeScript package:

#### 1. Version Determination (`determine-version`)

- Determines the `release_version` and `npm_tag` for the build.
- `release_version` logic:
  - For pull requests: defaults to `"0.0.0-pr"`.
  - For manual `workflow_dispatch`: uses the version provided in the input.
  - For pushes to tags (e.g., `v1.2.3`): extracts the version from the tag (e.g., `1.2.3`).
- `npm_tag` logic:
  - Set to `"next"` if the `release_version` contains `"rc"` (e.g., for release candidates).
  - Otherwise, set to `"latest"`.

#### 2. Platform Matrix Generation (`load-platform-matrix`)

- Filters entries from `build-matrix.json` that include "npm" in their `PACKAGE_MANAGERS` field
- Creates a platform matrix for building native modules

#### 3. Native Binary Building (`build-binaries`)

- Runs on multiple platforms defined by the matrix
- Sets up environment-specific configurations (GNU vs musl)
- Builds native modules with NAPI-RS, **using Zig for cross-compilation capabilities.**
- **For macOS ARM64 (`aarch64-apple-darwin`) targets, the workflow also triggers a build for macOS x86_64 (`x86_64-apple-darwin`) to ensure binaries are available for both architectures.**
- Uploads native binary artifacts with unique platform identifiers

#### 4. Artifact Organization (`organize-artifacts`)

- Downloads all platform-specific binary artifacts
- Uses NAPI-RS CLI to organize artifacts into platform-specific directories
- Organizes artifacts under `node/npm/` directory for publishing

#### 5. Platform Package Publishing (`publish-platform-packages`)

- Publishes each platform-specific package
- Sets version and tag (latest/next) based on release version (determined in the `determine-version` job).
- Handles errors gracefully, allowing for idempotent publishing.
- Skips already published packages, **ensuring idempotency (i.e., the workflow can be re-run without causing issues if packages are already published).**
- **Packages are named using the convention: `@valkey/valkey-glide-<os>-<arch>[-<libc>]`. For example, `@valkey/valkey-glide-linux-x64-gnu` or `@valkey/valkey-glide-linux-arm64-musl`. The `[-<libc>]` part is specific to Linux, indicating `gnu` (glibc) or `musl`.**

#### 6. Base TypeScript Package Publishing (`publish-base-to-npm`)

- Creates and configures the main TypeScript-only package
- Copies required files from build outputs
- Runs `napi prepublish` to add platform-specific optional dependencies
- Publishes the main package with proper version and tag

#### 7. Release Testing (`test-release`)

- Tests the published packages on each supported platform
- Verifies packages work correctly in real-world scenarios.
- **If tests fail, it triggers a deprecation process for the published packages. This includes deprecating the base TypeScript package (`@valkey/valkey-glide@<version>`) and all associated platform-specific native packages (`@valkey/valkey-glide-<platform>-<arch>[-<libc>]@<version>`) for that release version on npm.**
