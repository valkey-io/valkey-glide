# Submitting Pull Requests

> This guide walks you through the complete pull request workflow for the Valkey GLIDE repository — from forking and branching to committing, opening a PR, and passing CI checks. It covers the DCO signoff requirement, conventional commit format, and the PR template you'll need to fill out.

## PR Workflow Overview

1. Fork the repository and clone your fork locally.
2. Create a feature branch from the latest `main` branch.
3. Make focused changes — keep each PR scoped to a single issue or change.
4. Commit with a DCO signoff and conventional commit message.
5. Push your branch and open a pull request.
6. Fill out the PR template, link the related issue, and complete the checklist.
7. Ensure all CI checks pass before requesting review.

## Fork and Branch

If you haven't already, [fork the repository](https://help.github.com/articles/fork-a-repo/) and clone it:

```bash
git clone https://github.com/<your-username>/valkey-glide.git
cd valkey-glide
git checkout main
git pull origin main
```

Create a feature branch for your work:

```bash
git checkout -b feat/my-change
```

Keep your changes focused. If you also reformat unrelated code, it will be harder for reviewers to focus on your actual change.

## Committing Your Changes

### DCO Signoff (Required)

All commits must include a `Signed-off-by` line to certify you have the right to submit the code under the project's license. This is the Developer Certificate of Origin (DCO) requirement.

Add the signoff when you commit:

```bash
git commit -s -m "feat(java): add cluster scan support"
```

If you forgot the signoff on an existing commit, amend it:

```bash
git commit --amend --signoff --no-edit
```

The resulting commit will include a line like:

```
Signed-off-by: Your Name <your.email@example.com>
```

### Conventional Commit Format

The repository uses the [Conventional Commits](https://www.conventionalcommits.org/) format for commit messages:

```
<type>(<scope>): <description>
```

**Common types:**

| Type | When to use |
|------|-------------|
| `feat` | A new feature |
| `fix` | A bug fix |
| `docs` | Documentation changes only |
| `style` | Formatting, missing semicolons, etc. (no code change) |
| `refactor` | Code change that neither fixes a bug nor adds a feature |
| `test` | Adding or updating tests |
| `chore` | Maintenance tasks, CI changes, etc. |

**Scope** is typically the language binding or component: `java`, `python`, `node`, `go`, `core`, `ffi`.

**Examples:**

```
feat(java): add cluster scan support
fix(python): resolve connection timeout on reconnect
docs(node): update README with new API examples
test(go): add integration tests for standalone client
chore(ci): update Node.js version in workflow
```

## Opening a Pull Request

Push your branch to your fork and open a pull request against the `main` branch (or the appropriate release branch).

### Fill Out the PR Template

The repository provides a [PR template](.github/pull_request_template.md) with sections you need to complete:

| Section | What to include |
|---------|----------------|
| **Summary** | A clear description of what changed and why |
| **Issue link** | The URL of the related issue — replace the `[REPLACE ME]` placeholder with the issue URL |
| **Features / Behaviour Changes** | What feature support or behaviour changes are included |
| **Implementation** | Key code changes and areas where reviewers should pay extra attention |
| **Limitations** | Any features or use cases not implemented or only partially supported |
| **Testing** | What tests were conducted and relevant test results |

Always link your PR to the related issue by pasting the issue URL in the "Issue link" section. This helps maintainers track which issues are being addressed.

### Complete the PR Checklist

Before submitting, work through the checklist in the PR template:

- [ ] This Pull Request is related to one issue.
- [ ] Commit message has a detailed description of what changed and why.
- [ ] Tests are added or updated.
- [ ] CHANGELOG.md and documentation files are updated.
- [ ] Linters have been run (`make *-lint` targets) and Prettier has been run (`make prettier-fix`).
- [ ] Destination branch is correct — `main` or `release`.
- [ ] Create merge commit if merging release branch into main, squash otherwise.

## Merge Strategies

The repository uses two merge strategies depending on the context:

| Scenario | Merge Strategy |
|----------|---------------|
| Feature branch into `main` | **Squash and merge** — combines all commits into one clean commit |
| Release branch into `main` | **Merge commit** — preserves the full commit history from the release branch |

When opening your PR, make sure you select the correct merge strategy. Most contributor PRs will use squash and merge.

## CI Checks

After opening your PR, automated CI checks will run. Before requesting a review:

1. Monitor the CI status on your PR page.
2. If any checks fail, investigate and fix the failures promptly.
3. All CI checks must pass before a maintainer will review your PR.

Stay involved in the PR conversation — respond to automated CI feedback and reviewer comments promptly.

## Quick Reference

```bash
# Full workflow
git checkout main && git pull origin main
git checkout -b feat/my-change
# ... make your changes ...
git add .
git commit -s -m "feat(python): add new command support"
git push origin feat/my-change
# Open PR on GitHub
```

## Related Resources

- [PR Template](.github/pull_request_template.md) — The template you'll fill out when opening a PR
- [Contributing Guide](./CONTRIBUTING.md) — General contribution guidelines and policies
- [Picking Up Issues](./PICKING_UP_ISSUES.md) — How to find and claim issues to work on
- [Reviewing PRs](./REVIEWING_PRS.md) — What happens after you submit your PR
