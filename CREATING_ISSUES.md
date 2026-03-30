# Creating Issues

> This guide explains how to create issues in the Valkey GLIDE repository. It covers the available issue templates, required fields, labeling conventions, and best practices for writing clear, actionable issues.

## Before You Create an Issue

Search the existing [open issues](https://github.com/valkey-io/valkey-glide/issues) and recently closed issues to check whether your bug, feature request, or question has already been reported. Duplicate issues slow down triage and fragment discussion. If you find a related issue, add a comment or reaction to it instead of opening a new one.

## Issue Templates

The repository provides five issue templates. When you create a new issue, GitHub will prompt you to select one. Each template pre-populates fields and automatically applies labels to help maintainers categorize the issue.

### Bug Report

Use this template to report something that isn't working as expected.

**Title format:** `(topic): (short issue description)`

**Required fields:**

| Field | Description |
|-------|-------------|
| Describe the bug | A clear and concise description of the problem |
| Expected Behavior | What you expected to happen |
| Current Behavior | What actually happened, including errors, stack traces, and relevant logs |
| Reproduction Steps | A self-contained code snippet that reproduces the issue (see [SSCCE](#sscce-principle) below) |
| Client version used | The Valkey GLIDE client version |
| Engine type and version | e.g., Valkey 7.2, Redis 7.0 |
| Engine Environment | e.g., Local, ElastiCache Serverless, Docker |
| OS | Your operating system |
| Language | The language binding(s) affected (TypeScript, Python, Java, Rust, Go, .Net) |
| Language Version | e.g., Python 3.11, Java 17 |

**Optional fields:** Possible Solution, Additional Information/Context, Cluster information, Logs, Other information.


#### SSCCE Principle

Reproduction steps should follow the [SSCCE](http://sscce.org/) principle — Short, Self Contained, Correct Example. Provide a minimal code sample that someone can copy, paste, and run to reproduce the issue. Avoid including business logic or unrelated code, as it makes diagnosis more difficult. For complex issues, consider providing a small repository with the minimal setup needed to reproduce the bug.

### Feature Request

Use this template to propose a new feature or enhancement.

**Title format:** `(topic): (short issue description)`

**Required fields:**

| Field | Description |
|-------|-------------|
| Describe the feature | A clear and concise description of the feature you are proposing |
| Use Case | Why you need this feature |
| Client version used | The Valkey GLIDE client version |
| Environment details | OS name and version, etc. |

**Optional fields:** Proposed Solution, Other Information, Acknowledgements (whether you can implement it, whether it might be a breaking change).

### Flaky CI Test Issue

Use this template to report a test that passes and fails intermittently in the CI pipeline.

**Title format:** `[<language>][Flaky Test] <test-name>`

**Key fields:** Language, Test Name, Test Location (file and line number), Failure Permlink (link to the CI failure), Frequency (e.g., "1 in 10 runs"), Steps to Reproduce, System Information, Language and Version, Engine Version, Logs, Expected Behavior, Actual Behavior, Glide Version, Possible Fixes, Add in Language Label.

### Inquiry

Use this template to ask a question about the project, its usage, or its behavior.

**Title format:** `[Inquiry] <your question summary>`

**Key fields:** Inquiry (detailed description of your question). Optional fields include Language, Language Version, Engine Version, Operating System, and Additional Technical Information.

### Task

Use this template for internal work items and task tracking.

**Title format:** `[Task] <task summary>`

**Key fields:** Description (detailed task description), Checklist (items to be completed), Additional Notes.


## Auto-Labels Reference

Each template automatically applies labels when the issue is created. You do not need to add these manually.

| Template | Auto Labels |
|----------|-------------|
| Bug Report | `bug 🐞`, `Untriaged user issue` |
| Feature Request | `Feature ✨`, `Untriaged user issue` |
| Flaky CI Test Issue | `Flaky-tests 🐦`, `CI/CD ⚒️` |
| Inquiry | `Inquiry ❓` |
| Task | `Task 🔧` |

Bug Report and Feature Request issues are additionally labeled with `Untriaged user issue`, which signals to maintainers that the issue has not yet been reviewed. This label is removed during [triage](./TRIAGING_ISSUES.md).

## Language-Specific Labels

If your issue is specific to a particular language binding, add the corresponding label:

| Language Binding | Label |
|-----------------|-------|
| Java | `Java` |
| Python | `Python` |
| Node.js / TypeScript | `Node` |
| Go | `Go` |

> **Note:** External contributors may not have permission to add labels. If you cannot add a label, mention the affected language in your issue description and a maintainer will add the appropriate label during triage.

## Template Files

The issue templates are defined as YAML files in the repository. For reference:

- [`bug-report.yml`](.github/ISSUE_TEMPLATE/bug-report.yml)
- [`feature-request.yml`](.github/ISSUE_TEMPLATE/feature-request.yml)
- [`flaky-ci-test-issue.yml`](.github/ISSUE_TEMPLATE/flaky-ci-test-issue.yml)
- [`inquiry.yml`](.github/ISSUE_TEMPLATE/inquiry.yml)
- [`task.yml`](.github/ISSUE_TEMPLATE/task.yml)

## Related Resources

- [Triaging Issues](./TRIAGING_ISSUES.md) — What happens after you create an issue
- [Contributing Guide](CONTRIBUTING.md) — General contribution guidelines
- [Back to Wiki Home](docs/wiki/README.md)
