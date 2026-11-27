#!/usr/bin/env bash
# Windows packaging pins versions and writes package-specific ignore rules.
# All other non-ignored changes after the clean baseline are unexpected.
set -eo pipefail

unexpected_paths=()
while IFS= read -r path; do
    case "$path" in
        python/glide-async/pyproject.toml|python/glide-sync/pyproject.toml|python/glide-shared/pyproject.toml|python/glide-async/.gitignore|python/glide-sync/.gitignore)
            ;;
        *)
            unexpected_paths+=("$path")
            ;;
    esac
done < <(git diff --name-only --no-renames HEAD; git ls-files --others --exclude-standard)
if (( ${#unexpected_paths[@]} > 0 )); then
    echo "Unexpected worktree changes after Python Windows wheel build and tests:"
    echo "Worktree status (first 200 entries):"
    git status --short --untracked-files=all | sed -n '1,200p'
    echo "Unexpected paths (first 200 entries):"
    printf '  %s\n' "${unexpected_paths[@]}" | sed -n '1,200p'
    exit 1
fi
