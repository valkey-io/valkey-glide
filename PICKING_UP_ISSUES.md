# Picking Up Issues

> This guide explains how external contributors can find, claim, and start working on issues in the Valkey GLIDE repository. It covers how to identify good issues to work on, how to signal your intent, and how to set up your local environment.

## Finding Issues to Work On

Not every open issue is ready for contribution. Look for issues that meet one of these criteria:

- **Labeled `help wanted`** — These issues have been explicitly flagged by maintainers as good candidates for community contributions.
- **Triaged issues** — Issues where the `Untriaged user issue` label has been removed. This means a maintainer has reviewed the issue, confirmed it is valid, and accepted it for work. See [Triaging Issues](./TRIAGING_ISSUES.md) for details on how triage works.

Avoid picking up issues that still carry the `Untriaged user issue` label. These have not been reviewed yet and may be duplicates, out of scope, or missing critical information.

## Before You Start

### Comment on the Issue

Before you begin working on an issue, leave a comment expressing your intent to work on it. This lets maintainers and other contributors know the issue is being actively worked on and prevents duplicate effort.

A simple comment like _"I'd like to work on this"_ is enough.

### Discuss Significant Changes First

If the change you're planning is significant — a new feature, a large refactor, or anything that touches multiple components — open an issue to discuss it before starting work. This avoids spending time on an approach that may not align with the project's direction. As noted in the [Contributing Guide](./CONTRIBUTING.md):

> You open an issue to discuss any significant changes before starting the work — we would hate for your time to be wasted.

## Setting Up Your Environment

### Fork and Clone

1. [Fork the repository](https://help.github.com/articles/fork-a-repo/) to your GitHub account.
2. Clone your fork locally.
3. Make sure you are working against the latest source on the `main` branch.

```bash
git clone https://github.com/<your-username>/valkey-glide.git
cd valkey-glide
git checkout main
git pull origin main
```

### Language-Specific Setup

Each language binding has its own build, test, and lint instructions. Refer to the developer guide for the language you're contributing to:

| Language Binding | Developer Guide |
|-----------------|-----------------|
| Java | [java/DEVELOPER.md](./java/DEVELOPER.md) |
| Python | [python/DEVELOPER.md](./python/DEVELOPER.md) |
| Node.js / TypeScript | [node/DEVELOPER.md](./node/DEVELOPER.md) |
| Go | [go/DEVELOPER.md](./go/DEVELOPER.md) |

These guides cover prerequisites, build commands, running tests, and linting for each language.

## Workflow Summary

1. Browse open issues and look for the `help wanted` label or triaged issues (no `Untriaged user issue` label).
2. Comment on the issue to signal your intent to work on it.
3. If the change is significant, open an issue to discuss the approach first.
4. Fork the repository and work against the latest `main` branch.
5. Follow the language-specific developer guide to set up your environment.
6. When your changes are ready, submit a pull request — see the [Contributing Guide](./CONTRIBUTING.md) for the PR workflow.

## Related Resources

- [Creating Issues](./CREATING_ISSUES.md) — How to report bugs and request features
- [Triaging Issues](./TRIAGING_ISSUES.md) — How issues are reviewed and categorized
- [Contributing Guide](./CONTRIBUTING.md) — General contribution guidelines and policies
