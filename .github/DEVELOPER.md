# CI/CD Workflow Guide

TODO: Add a description of the CI/CD workflow and its components.

### Overview

Our CI/CD pipeline tests and builds our project across multiple languages, versions, and environments. This guide outlines the key components and processes of our workflow.

### Workflow Triggers

*   Push to `main` branch
*   Pull requests
*   Scheduled runs (daily)
*   Manual trigger (workflow_dispatch)

### Language-Specific Workflows

Each language has its own workflow file with similar structure but language-specific steps, for example python.yml for Python, or java.yml for Java.

### Shared Components

#### Matrix Files

While workflows are language-specific, the matrix files are shared across all workflows. 
Workflows are starting by loading the matrix files from the `.github/json_matrices` directory.

*  `engine-matrix.json`: Defines the versions of Valkey engine to test against.
*  `build-matrix.json`: Defines the host environments for testing.
* `supported-languages-version.json`: Defines the supported versions of languages.

All matrices have a `run` like field which specifies if the configuration should be tested on every workflow run.
This allows for flexible control over which configurations are tested in different scenarios, optimizing CI/CD performance and resource usage.

#### Engine Matrix (engine-matrix.json)

Defines the versions of Valkey engine to test against:

```json
[
    { "type": "valkey", "version": "7.2.5", "run": "always" },
]
```
*  `type`: The type of engine (e.g., Valkey, Redis).
*  `version`: The version of the engine.
*  `run`: Specifies if the engine version should be tested on every workflow.

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
*  `OS`: The operating system of the host.
*  `RUNNER`: The GitHub runner to use.
*  `TARGET`: The target environment.
*  `run`: Specifies which language workflows should use this host configuration, always means run on each workflow trigger.

#### Supported Languages Version (supported-languages-version.json)

Defines the supported versions of languages:

```json
[
    {
        "language": "java",
        "versions": ["11", "17"],
        "always-run-versions": ["17"]
    },
    // ... other configurations
]
```
*  `language`: The language for which the version is supported.
*  `versions`: The full versions supported of the language which will test against scheduled.
*  `always-run-versions`: The versions which will be tested in every workflow run.

#### Triggering Workflows

Push to main or create a pull request to run workflows automatically.
Use workflow_dispatch for manual triggers, accepting inputs of full-matrix which is a boolean value to run all configurations.
Scheduled runs are triggered daily to ensure regular testing of all configurations.

### Mutual vs. Language-Specific Components

#### Mutual

`Matrix files` - `.github/json_matrices`
`Shared dependencies installation` - `.github/workflows/install-shared-dependencies/action.yml`
`Linting Rust` - `.github/workflows/lint-rust/action.yml`

#### Language-Specific

`Package manager commands`
`Testing frameworks`
`Build processes`

### Customizing Workflows

Modify `[language].yml` files to adjust language-specific steps.
Update matrix files to change tested versions or environments.
Adjust cron schedules in workflow files for different timing of scheduled runs.

### Workflow Matrices

We use dynamic matrices for our CI/CD workflows, which are created using the `create-test-matrices` action. This action is defined in `.github/workflows/create-test-matrices/action.yml`.

#### How it works

1. The action is called with a `language-name` input and `dispatch-run-full-matrix` input.
2. It reads the `engine-matrix.json`, `build-matrix.json`, and `supported-languages-version.json` files.
3. It filters the matrices based on the inputs and the event type.
2. It generates three matrices:
   - Engine matrix: Defines the types and versions of the engine to test against, for example Valkey 7.2.5.
   - Host matrix: Defines the host platforms to run the tests on, for example Ubuntu on ARM64.
   - Language-version matrix: Defines the supported versions of languages, for example python 3.8.

#### Outputs
- `engine-matrix-output`: The generated engine matrix.
- `host-matrix-output`: The generated host matrix.
- `language-version-matrix-output`: The generated language version matrix.

This dynamic matrix generation allows for flexible and efficient CI/CD workflows, adapting the test configurations based on the type of change and the specific language being tested.
