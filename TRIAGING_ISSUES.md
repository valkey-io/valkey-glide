# Triaging Issues

> This guide describes how maintainers and experienced contributors triage incoming issues in the Valkey GLIDE repository. Triage ensures that issues are complete, relevant, and ready for someone to pick up.

## What Is Triage?

Triage is the process of reviewing new issues to determine whether they contain enough information to act on. A triager reads the issue, checks that it uses the correct template, and decides what happens next:

1. **Review for completeness** — Does the issue include all required fields? For bug reports, are there reproduction steps? For feature requests, is the use case clear?
2. **Validate relevance** — Is this a genuine bug, a reasonable feature request, or a duplicate of an existing issue? Does it fall within the scope of the project?
3. **Determine next steps** — Accept the issue for work, request more information, or close it if it's out of scope or a duplicate.

## Triage Workflow

### Accepting an Issue

When an issue is complete and ready for work:

1. Review the issue content against the template requirements (see [Creating Issues](./CREATING_ISSUES.md) for template details).
2. Verify the issue is not a duplicate of an existing open or recently closed issue.
3. Remove the `Untriaged user issue` label to signal that the issue has been reviewed and accepted.
4. Add any additional labels that help categorize the issue (e.g., language-specific labels, priority, or component labels).
5. Optionally leave a comment acknowledging the issue and providing any initial guidance.

### Requesting Clarification

When an issue is missing information or is unclear:

1. Leave a comment explaining what additional details are needed (e.g., reproduction steps, version information, expected behavior).
2. Keep the `Untriaged user issue` label on the issue. This signals that the issue is still awaiting review and is not ready to be picked up.
3. Monitor the issue for a response from the author. Once the requested information is provided, re-evaluate and either accept or close the issue.

### Closing an Issue

Close an issue when:

- It is a duplicate of an existing issue (link to the original).
- It is out of scope for the project.
- The author has not responded to a request for clarification after a reasonable period.

## Stale Bot Behavior

The repository uses a [stale bot](.github/workflows/stale.yml) (powered by the `actions/stale` GitHub Action) to automatically manage inactive issues and pull requests.

### Issues

- An issue with no activity for **90 days** is marked as stale.
- If there is no further activity within **14 days** after being marked stale, the issue is automatically closed.

### Pull Requests

- A PR with no activity for **60 days** is marked as stale.
- If there is no further activity within **10 days** after being marked stale, the PR is automatically closed.

### Exempt Labels

Issues with any of the following labels are exempt from the stale bot and will not be automatically marked stale or closed:

- `bug`
- `Users Pain`
- `Epic`
- `User issue`
- `Unatriaged user issue`

> **Note:** Issues with milestones assigned are also exempt from the stale bot.

If a stale issue or PR becomes active again, the bot automatically adds the `Unstaled` label to indicate it has been revived.

## Related Resources

- [Creating Issues](./CREATING_ISSUES.md) — Template details and required fields
- [Picking Up Issues](docs/wiki/picking-up-issues.md) — How contributors find triaged issues to work on
- [Stale Bot Configuration](.github/workflows/stale.yml) — The workflow file that configures stale bot behavior
- [Back to Wiki Home](docs/wiki/README.md)
