#!/bin/bash
set -e # Allow script to immediately exit if any command has a non-zero exit status.

# Check if version parameter is provided
if [ $# -ne 1 ]; then
    echo "Usage: $0 <version>"
    echo "Example: $0 v1.3.4-rc2"
    exit 1
fi

VERSION=$1

# Validate version format
if ! [[ $VERSION =~ ^v([0-9]+)\.([0-9]+)\.([0-9]+)(-rc[0-9]+)?$ ]]; then
    echo "Error: Version must be in format vX.Y.Z or vX.Y.Z-rcN (e.g., v1.3.4-rc2)"
    exit 1
else
    MAJOR=${BASH_REMATCH[1]}
    echo "Testing release candidate: $VERSION"
fi

# Create testutils directory and copy internal interfaces to prevent errors in the test files
echo "Setting up testutils to handle internal package imports..."
mkdir -p testutils
cp -r internal/interfaces/* testutils/

# Fix imports in test files to use testutils instead of internal/interfaces
echo "Updating test file imports..."
find integTest/ -name "*.go" -type f -exec grep -l "github.com/valkey-io/valkey-glide/go/v2/internal/interfaces" {} \; | while read -r file; do
    echo "Updating imports in $file"
    os="$(uname -s)"
    if [ "$os" = "Linux" ]; then
        sed -i 's|github.com/valkey-io/valkey-glide/go/v2/internal/interfaces|github.com/valkey-io/valkey-glide/go-test-rc/testutils|g' "$file"
    else
        # MacOS
        sed -i '' 's|github.com/valkey-io/valkey-glide/go/v2/internal/interfaces|github.com/valkey-io/valkey-glide/go-test-rc/testutils|g' "$file"
    fi
done

# Modify go.mod file
echo "Modifying go.mod file..."
# Use sed to modify the module name and add the dependency after testify depending on MacOS or Linux
os="$(uname -s)"
if [ "$os" = "Linux" ]; then
        # Linux
        sed -i -e 's|module github.com/valkey-io/valkey-glide/go/v2|module github.com/valkey-io/valkey-glide/go-test-rc|' go.mod
        sed -i -e '/github.com\/stretchr\/testify/a\\tgithub.com/valkey-io/valkey-glide/go/v2 '"$VERSION" go.mod
        if [ $MAJOR -lt "2" ]; then # Fix for release version 1 branches
            sed -i -e 's|"redis-cli",|CLI_COMMAND,|g' ../utils/cluster_manager.py
        fi
else
        # MacOS
        sed -i '' -e 's|module github.com/valkey-io/valkey-glide/go/v2|module github.com/valkey-io/valkey-glide/go-test-rc|' go.mod
        sed -i '' -e '/github.com\/stretchr\/testify/a\
        github.com/valkey-io/valkey-glide/go/v2 '"$VERSION" go.mod
        if [ $MAJOR -lt "2" ]; then # Fix for release version 1 branches
            sed -i '' -e 's|"redis-cli",|CLI_COMMAND,|g' ../utils/cluster_manager.py 
        fi
fi

echo "go.mod has been updated"
go mod tidy
