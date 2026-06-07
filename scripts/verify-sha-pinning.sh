#!/bin/bash
# verify-sha-pinning.sh
#
# This script verifies that all external GitHub Actions are pinned to commit SHAs
# instead of version tags. This helps mitigate supply chain attacks by ensuring
# actions reference immutable commits rather than mutable version tags.
#
# SHA pinning is required by Requirements 6.1, 6.2, 6.3
#
# Usage: ./scripts/verify-sha-pinning.sh
# Exit codes:
#   0 - All external actions are properly SHA-pinned
#   1 - One or more external actions use version tags instead of SHAs

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# Track if any violations are found
VIOLATIONS_FOUND=0

# Color codes for output (if terminal supports it)
if [[ -t 1 ]]; then
    RED='\033[0;31m'
    GREEN='\033[0;32m'
    YELLOW='\033[0;33m'
    NC='\033[0m' # No Color
else
    RED=''
    GREEN=''
    YELLOW=''
    NC=''
fi

echo "Checking SHA pinning in GitHub Actions files..."
echo ""

# Function to check a file for version tag references
check_file() {
    local file="$1"
    local relative_path="${file#$REPO_ROOT/}"
    local file_violations=0

    # Read file line by line to report line numbers
    local line_num=0
    while IFS= read -r line || [[ -n "$line" ]]; do
        ((line_num++))

        # Skip local actions (starting with ./ or referencing local paths)
        if echo "$line" | grep -qE 'uses:\s*\./'; then
            continue
        fi

        # Check for external action references with version tags
        # Pattern: uses: owner/repo@v... (version tags like @v4, @v2.3.1, @v1.2.3-beta)
        # This should NOT match SHA references which are 40-character hex strings
        if echo "$line" | grep -qE 'uses:\s+[^@]+@v[0-9]'; then
            echo -e "${RED}ERROR:${NC} $relative_path:$line_num"
            echo "       Uses version tag instead of SHA:"
            echo "       $(echo "$line" | sed 's/^[[:space:]]*/       /')"
            echo ""
            ((file_violations++))
            VIOLATIONS_FOUND=1
        fi

        # Also check for references using branch names or non-SHA refs
        # Pattern: uses: owner/repo@main, @master, @release-*, etc.
        # Exclude SHAs (40 hex chars) and local actions (./)
        if echo "$line" | grep -qE 'uses:\s+[^@]+@(main|master|dev|develop|release)'; then
            echo -e "${YELLOW}WARNING:${NC} $relative_path:$line_num"
            echo "         Uses branch name instead of SHA (potential security risk):"
            echo "         $(echo "$line" | sed 's/^[[:space:]]*/         /')"
            echo ""
            ((file_violations++))
            VIOLATIONS_FOUND=1
        fi

    done < "$file"

    return $file_violations
}

# Find and check all action.yml files in .github/actions/
echo "Scanning composite actions in .github/actions/..."
action_files=$(find "$REPO_ROOT/.github/actions" -name "action.yml" -o -name "action.yaml" 2>/dev/null || true)
action_count=0
for file in $action_files; do
    if [[ -f "$file" ]]; then
        check_file "$file" || true
        ((action_count++))
    fi
done
echo "  Checked $action_count action files in .github/actions/"
echo ""

# Find and check all workflow files in .github/workflows/
# Include both .yml and .yaml extensions, and check action.yml files within workflow subdirectories
echo "Scanning workflows in .github/workflows/..."
workflow_files=$(find "$REPO_ROOT/.github/workflows" \( -name "*.yml" -o -name "*.yaml" \) 2>/dev/null || true)
workflow_count=0
for file in $workflow_files; do
    if [[ -f "$file" ]]; then
        check_file "$file" || true
        ((workflow_count++))
    fi
done
echo "  Checked $workflow_count workflow files in .github/workflows/"
echo ""

# Summary
total_checked=$((action_count + workflow_count))
echo "=========================================="
echo "SHA Pinning Verification Complete"
echo "=========================================="
echo "Total files checked: $total_checked"

if [[ $VIOLATIONS_FOUND -eq 0 ]]; then
    echo -e "${GREEN}✓ All external actions are properly SHA-pinned${NC}"
    exit 0
else
    echo -e "${RED}✗ Found actions using version tags or branch names instead of SHAs${NC}"
    echo ""
    echo "To fix violations:"
    echo "  1. Find the commit SHA for the desired version"
    echo "  2. Replace the version tag with the full 40-character SHA"
    echo "  3. Add a comment with the version for documentation"
    echo ""
    echo "Example:"
    echo "  Before: uses: actions/checkout@v4"
    echo "  After:  uses: actions/checkout@11bd71901bbe5b1630ceea73d27597364c9af683 # v4.2.2"
    exit 1
fi
