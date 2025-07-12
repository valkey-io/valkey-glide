# Dependabot Automated Dependency Management

This repository uses Dependabot to automatically manage dependency updates across all supported languages.

## Overview

The Dependabot setup consists of two main components:

1. **`.github/dependabot.yml`** - Basic Dependabot configuration
2. **`.github/workflows/dependabot-management.yml`** - Enhanced workflow for timing constraints

## Languages Supported

- **Java**: Gradle dependencies (`build.gradle` files)
- **Rust**: Cargo dependencies (`Cargo.toml` files)
- **TypeScript/Node.js**: npm dependencies (`package.json` files)
- **Go**: Go modules (`go.mod` files)
- **Python**: pip dependencies (`pyproject.toml`, `requirements.txt`)
- **C#**: NuGet dependencies (`.csproj` files)
- **GitHub Actions**: Action dependencies in workflow files

## Update Schedule

- **Weekly**: Every Monday at 09:00 UTC
- **Pull Request Limit**: 5-10 PRs per ecosystem to avoid spam

## Timing Constraints

The enhanced workflow implements specific timing constraints for different update types:

### Patch Updates
- **Auto-approved**: After 1 hour (allowing CI to complete)
- **Rationale**: Patch updates are typically safe bug fixes

### Minor Updates
- **Auto-approved**: After 1 week
- **Rationale**: Allow time for the community to identify potential issues

### Major Updates
- **Auto-approved**: After 3 weeks
- **Rationale**: Major updates may introduce breaking changes requiring more stability time

## Features

### Automatic Changelog Addition
- Each dependabot PR gets a changelog section added to the description
- Includes basic update information and encourages checking detailed release notes

### Labeling
- All dependency PRs are labeled with:
  - `dependencies` (general dependency label)
  - `dependency-patch`, `dependency-minor`, or `dependency-major` (update type)
  - Ecosystem-specific labels (e.g., `npm`, `cargo`, `gradle`)

### Grouping
- Patch and minor updates are grouped separately to reduce noise
- Major updates are handled individually for better visibility

## Manual Control

### Dry Run Mode
The enhanced workflow can be run in dry-run mode for testing:
```bash
# Via GitHub Actions UI
# Go to Actions → Enhanced Dependabot Management → Run workflow
# Check "Run in dry-run mode"
```

### Workflow Dispatch
The workflow can be triggered manually at any time through the GitHub Actions interface.

## Configuration Details

### Dependabot Configuration
The `.github/dependabot.yml` file configures:
- Update schedules for each ecosystem
- Directory locations for dependency files
- Pull request limits and grouping
- Labels for organization

### Enhanced Workflow
The `.github/workflows/dependabot-management.yml` file provides:
- Timing constraint enforcement
- Automatic changelog addition
- Intelligent labeling
- Auto-approval for aged PRs

## Security Considerations

- The workflow only operates on dependabot-created PRs
- All PRs must pass CI checks before auto-approval
- Major updates require the longest waiting period
- Manual review is always possible before the timing constraints are met

## Troubleshooting

### Common Issues

1. **PRs not being auto-approved**: Check that CI checks are passing
2. **Missing changelogs**: The workflow adds basic changelogs; detailed information should be checked in package repositories
3. **Too many PRs**: Adjust the `open-pull-requests-limit` in dependabot.yml

### Monitoring

- Check the Actions tab for workflow runs
- Review dependabot PRs for proper labeling and changelog addition
- Monitor for any failed auto-approvals in the workflow logs

## Customization

To modify the timing constraints:

1. Edit the `shouldAutoApprovePR` function in `dependabot-management.yml`
2. Adjust the age thresholds:
   - Patch: Currently 1 hour (`0.042` days)
   - Minor: Currently 1 week (`7` days)
   - Major: Currently 3 weeks (`21` days)

To add more ecosystems:

1. Add new entries to `.github/dependabot.yml`
2. Ensure the directory paths are correct
3. Add appropriate labels for organization