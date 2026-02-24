# Contributing Guidelines

Thank you for your interest in contributing to our project. Whether it's a bug report, new feature, correction, or additional
documentation, we greatly value feedback and contributions from our community.

Please read through this document before submitting any issues or pull requests to ensure we have all the necessary
information to effectively respond to your bug report or contribution.


## Reporting Bugs, Feature Requests, and Other Issues

We welcome you to use the GitHub issue tracker to report bugs, suggest features, ask questions, or track tasks. The repository provides issue templates for each of these — Bug Report, Feature Request, Flaky CI Test Issue, Inquiry, and Task — that pre-populate the required fields and apply the appropriate labels automatically.

Before creating a new issue, please search existing open and recently closed issues to avoid duplicates.

For step-by-step guidance on choosing the right template, filling out required fields, and labeling conventions, see the [Creating Issues Guide](./CREATING_ISSUES.md).

## Triaging Issues

Maintainers and experienced contributors review incoming issues to ensure they are complete, relevant, and ready to be worked on. The triage process involves checking that the correct template was used, all required fields are filled in, and the issue is not a duplicate.

- Once an issue is accepted for work, the `Untriaged user issue` label is removed.
- If more information is needed, a comment is left requesting clarification and the label stays until the author responds.
- Issues inactive for 90 days are automatically marked stale and closed after 14 additional days. Issues labeled `bug`, `Users Pain`, `Epic`, `User issue`, or `Unatriaged user issue` are exempt.

For the full triage workflow, stale bot behavior, and exempt labels, see the [Triaging Issues Guide](./TRIAGING_ISSUES.md).

## Contributing via Pull Requests
Contributions via pull requests are much appreciated. Before sending us a pull request, please ensure that:

1. You are working against the latest source on the *main* branch.
2. You check existing open, and recently merged, pull requests to make sure someone else hasn't addressed the problem already.
3. You open an issue to discuss any significant changes before starting the work - we would hate for your time to be wasted.
   - A significant change can include management commands, which most of the time will require core changes, or any modification that spans multiple components or language bindings.

To send us a pull request, please:

1. Fork the repository.
2. Modify the source; please focus on the specific change you are contributing. If you also reformat all the code, it will be hard for us to focus on your change.
3. Ensure local tests pass.
4. Commit to your fork using clear commit messages, merge or squash commits as necessary.
   - All commits require a DCO signoff (`git commit -s -m "message"`) and should follow the [Conventional Commits](https://www.conventionalcommits.org/) format: `<type>(<scope>): <description>`. To add a signoff to an existing commit: `git commit --amend --signoff --no-edit`.
   - All commits must be cryptographically signed so they show as "Verified" on GitHub. Use GPG or SSH signing by configuring `git config commit.gpgsign true`. To sign a commit: `git commit -S -s -m "message"`. See [GitHub's guide on signing commits](https://docs.github.com/en/authentication/managing-commit-signature-verification/signing-commits) for setup instructions.
5. Send us a pull request, answering any default questions in the pull request interface.
6. Pay attention to any automated CI failures reported in the pull request, and stay involved in the conversation.

GitHub provides additional document on [forking a repository](https://help.github.com/articles/fork-a-repo/) and
[creating a pull request](https://help.github.com/articles/creating-a-pull-request/).

For the complete PR workflow including the PR template, checklist, merge strategies, and CI checks, see the [Submitting PRs Guide](./SUBMITTING_PRS.md).


## Finding Contributions to Work On

Not every open issue is ready for contribution. Look for issues that meet one of these criteria:

- **Labeled `good first issue`** — These issues have been explicitly flagged by maintainers as good first issues candidates for community contributions.
- **Triaged issues** — Issues where the `Untriaged user issue` label has been removed. This means a maintainer has reviewed the issue, confirmed it is valid, and accepted it for work. See the [Triaging Issues Guide](./TRIAGING_ISSUES.md) for details.

Avoid picking up issues that still carry the `Untriaged user issue` label. These have not been reviewed yet and may be duplicates, out of scope, or missing critical information.

You can filter issues by language-specific labels to find work relevant to your expertise: `Java`, `Python`, `Node`, or `Go`. Use GitHub's search syntax on the [issues page](https://github.com/valkey-io/valkey-glide/issues) — for example, `label:"java ☕"` to find Java-specific issues ready for contribution.

### Before You Start

Before you begin working on an issue, leave a comment expressing your intent to work on it. This lets maintainers and other contributors know the issue is being actively worked on and prevents duplicate effort. A simple comment like _"I'd like to work on this"_ is enough.

For detailed instructions on forking, cloning, and setting up your local environment, see the [Picking Up Issues Guide](./PICKING_UP_ISSUES.md).

## Code of Conduct
This project has adopted the [Amazon Open Source Code of Conduct](https://aws.github.io/code-of-conduct).
For more information see the [Code of Conduct FAQ](https://aws.github.io/code-of-conduct-faq) or contact
opensource-codeofconduct@amazon.com with any additional questions or comments.


## Security issue notifications
See [SECURITY.md](./SECURITY.md)


## Licensing

See the [LICENSE](LICENSE) file for our project's licensing. We will ask you to confirm the licensing of your contribution.

## Community Support and Feedback

We encourage you to join our community to support, share feedback, and ask questions. You can approach us for anything on our Valkey Slack: [Join Valkey Slack](https://join.slack.com/t/valkey-oss-developer/shared_invite/zt-2nxs51chx-EB9hu9Qdch3GMfRcztTSkQ).
