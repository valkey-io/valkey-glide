#!/usr/bin/env bash
# Run from the repository root. Record imports before the RC patch, then validate
# after the patch and workload using the same RUNNER_TEMP and RC_VERSION.
set -eo pipefail

if [[ "${1:-}" == --record-import-paths ]]; then
    # Per-file -exec keeps an empty match list successful with pipefail on macOS.
    find go/integTest -name '*.go' -type f \
        -exec grep -lF 'github.com/valkey-io/valkey-glide/go/v2/internal/interfaces' {} \; \
        | sort > "$RUNNER_TEMP/go-rc-import-paths"
    exit 0
fi

label="${1:?Usage: validate-go-rc-changes.sh <label> | --record-import-paths}"
rc_major="${RC_VERSION#v}"
rc_major="${rc_major%%.*}"
unexpected_paths=()
while IFS= read -r path; do
    case "$path" in
        go/go.mod|go/go.sum|go/benchmarks/go.mod|go/benchmarks/go.sum)
            ;;
        go/integTest/*.go)
            if ! grep -Fxq "$path" "$RUNNER_TEMP/go-rc-import-paths"; then
                unexpected_paths+=("$path")
            fi
            ;;
        utils/cluster_manager.py)
            if (( rc_major >= 2 )); then
                unexpected_paths+=("$path")
            fi
            ;;
        *)
            unexpected_paths+=("$path")
            ;;
    esac
done < <(git diff --name-only --no-renames HEAD; git ls-files --others --exclude-standard)
if (( ${#unexpected_paths[@]} > 0 )); then
    echo "Unexpected worktree changes after $label:"
    echo "Worktree status (first 200 entries):"
    git status --short --untracked-files=all | sed -n '1,200p'
    echo "Unexpected paths (first 200 entries):"
    printf '  %s\n' "${unexpected_paths[@]}" | sed -n '1,200p'
    exit 1
fi
