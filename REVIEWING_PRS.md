# Reviewing and Merging Pull Requests

> This guide covers what happens after a pull request is submitted — how maintainers review PRs, what contributors should expect during the review process, and how PRs get merged or closed.

## What Maintainers Look For

When a maintainer picks up your PR for review, they evaluate it against several criteria:

| Area | What's checked |
|------|---------------|
| **Code quality** | Readability, structure, naming conventions, and adherence to the project's coding style |
| **Test coverage** | New or changed functionality has corresponding tests that pass |
| **Contribution guidelines** | DCO signoff is present, conventional commit format is used, PR template is filled out |
| **CI status** | All automated checks (build, lint, tests) are passing |

Maintainers may request changes, ask clarifying questions, or suggest alternative approaches. This is a normal part of the process — it's collaborative, not adversarial.

## Staying Involved

Once you open a PR, stay engaged:

- **Respond to feedback promptly.** Maintainers set aside time to review, and a timely response keeps the momentum going.
- **Address all review comments.** If you disagree with a suggestion, explain your reasoning — discussion is welcome.
- **Push follow-up commits** to address requested changes rather than force-pushing over the review history, unless asked otherwise.
- **Monitor CI checks.** If a check fails after you push updates, investigate and fix it before pinging reviewers again.

Letting a PR go quiet for too long can result in it being marked stale (see below).

## Keep PRs Focused

Each pull request should address a single change or issue. Focused PRs are:

- Easier for maintainers to review
- Less likely to introduce unrelated regressions
- Faster to get approved and merged

If your work touches multiple areas, consider splitting it into separate PRs that can be reviewed independently.

## Merge Strategies

The repository uses two merge strategies depending on the branch context:

| Scenario | Strategy | Why |
|----------|----------|-----|
| Feature branch → `main` | **Squash and merge** | Combines all commits into one clean commit on main |
| Release branch → `main` | **Merge commit** | Preserves the full commit history from the release branch |

Most contributor PRs target `main` from a feature branch, so squash and merge is the typical path. The maintainer merging the PR will select the appropriate strategy.

## Resolving Review Comments

The original commenter is responsible for resolving their own review comments. This ensures the person who raised the feedback can verify that it has been adequately addressed or responded to before the conversation is marked as resolved.

- **Do not resolve someone else's comment.** Only the person who left the feedback should mark it as resolved.
- **If the feedback has been addressed,** the original commenter should verify the change and resolve the thread.
- **If the contributor disagrees,** the original commenter should resolve the thread once the discussion has reached a conclusion — whether that means the suggestion was adopted, an alternative was agreed upon, or the commenter is satisfied with the explanation provided.

This practice keeps the review process accountable and prevents feedback from being dismissed without acknowledgment.

**Exception — stale threads:** If the original commenter has not responded after a reasonable period of time, a maintainer or the PR author may resolve the thread to keep the review moving forward.

**Linking fixes to feedback:** When addressing review feedback, the PR author should reply with a link to the commit or code change that resolves the comment. This helps the reviewer quickly verify the fix without searching through the diff. This is especially important for larger or more complex PRs that may require multiple iterations of feedback.

> **Note:** A reply like "Addressed it" or "Fixed" without context is not sufficient. Include a direct link or reference so the reviewer can locate the change without effort.
>
> Good examples:
> - "Fixed in [abc1234](link-to-commit) — renamed the variable as suggested."
> - "Addressed here: [link to updated line]. I went with option B since it avoids the extra allocation."
>
> Poor examples:
> - "Done."
> - "Addressed it."
> - "Fixed, PTAL."

## Stale PR Policy

The repository uses an automated [stale bot](.github/workflows/stale.yml) to manage inactive pull requests:

- **After 60 days** of inactivity, the bot marks the PR as stale with a comment.
- **After 10 more days** with no activity, the bot automatically closes the PR.

If your PR is marked stale but you're still working on it, just push an update or leave a comment to reset the timer. If a closed PR is still relevant, you can reopen it or open a new one.

## Maintainer Takeover of PRs

As maintainers, we may sometimes take over a PR to get it across the finish line. This depends on a number of criteria, but most often it happens when the original contributor has become unresponsive or the PR has gone stale for too long.

Before taking over, a maintainer will leave a comment on the PR indicating their intent and allow the original contributor a reasonable period of time to respond. If there is no response, maintainers reserve the right to take over the PR and complete it, or close it if they deem it no longer necessary.

## Related Resources

- [Submitting PRs](./SUBMITTING_PRS.md) — How to prepare and open a pull request
- [PR Template](.github/pull_request_template.md) — The template used for all pull requests
- [Contributing Guide](CONTRIBUTING.md) — General contribution guidelines and policies
- [Back to Wiki Home](docs/wiki/README.md)
