#!/bin/bash
set -e

# Start by patching
cd ../
git apply -v go/scripts/rc-testing/gha.patch
cd go

# Check if version parameter is provided
if [ $# -ne 1 ]; then
    echo "Usage: $0 <version>"
    echo "Example: $0 v1.3.4-rc2"
    exit 1
fi

VERSION=$1

# Validate version format
if ! [[ $VERSION =~ ^v[0-9]+\.[0-9]+\.[0-9]+(-rc[0-9]+)?$ ]]; then
    echo "Error: Version must be in format vX.Y.Z or vX.Y.Z-rcN (e.g., v1.3.4-rc2)"
    exit 1
else
    MAJOR=${BASH_REMATCH[1]}
    MINOR=${BASH_REMATCH[2]}
    BRANCH="release-${MAJOR}.${MINOR}"

    echo "Testing release candidate: $VERSION"
    echo "Determined branch: $BRANCH"

    # Checkout release branch
    echo "Checking out $BRANCH branch..."
    git fetch origin $BRANCH
    git checkout $BRANCH
fi

# Modify go.mod file
echo "Modifying go.mod file..."
# Use sed to modify the module name and add the dependency after testify depending on MacOS or Linux
os="$(uname -s)"
if [ "$os" = "Linux" ]; then
        # Linux
        sed -i -e 's|module github.com/valkey-io/valkey-glide/go|module github.com/valkey-io/valkey-glide/go-test-rc|' go.mod
        sed -i -e '/github.com\/stretchr\/testify/a\\tgithub.com/valkey-io/valkey-glide/go '"$VERSION"'' go.mod
        if [ $MAJOR -lt "2" ]; then # Fix for release version 1 branches
            sed -i -e 's|"redis-cli",|CLI_COMMAND,|g' ../utils/cluster_manager.py
        fi
else
        # MacOS
        sed -i '' -e 's|module github.com/valkey-io/valkey-glide/go|module github.com/valkey-io/valkey-glide/go-test-rc|' go.mod
        sed -i '' -e '/github.com\/stretchr\/testify/ a\
        github.com\/valkey-io\/valkey-glide\/go '"$VERSION"'' go.mod
        if [ $MAJOR -lt "2" ]; then # Fix for release version 1 branches
            sed -i '' -e 's|"redis-cli",|CLI_COMMAND,|g' ../utils/cluster_manager.py 
        fi
fi

echo "go.mod has been updated"
go mod tidy
