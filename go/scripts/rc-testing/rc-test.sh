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
    echo "Testing release candidate: $VERSION"
fi

# Modify go.mod file
echo "Modifying go.mod file..."
# Use sed to modify the module name and add the dependency after testify depending on MacOS or Linux
os="$(uname -s)"
if [ "$os" = "Linux" ]; then
        # Linux
        sed -i -e 's|module github.com/valkey-io/valkey-glide/go/v2|module github.com/valkey-io/valkey-glide/go-test-rc|' go.mod
        sed -i -e '/github.com\/stretchr\/testify/a\\tgithub.com/valkey-io/valkey-glide/go/v2 '"$VERSION" go.mod
else
        # MacOS
        sed -i '' -e 's|module github.com/valkey-io/valkey-glide/go/v2|module github.com/valkey-io/valkey-glide/go-test-rc|' go.mod
        sed -i '' -e '/github.com\/stretchr\/testify/a\
        github.com/valkey-io/valkey-glide/go/v2 '"$VERSION" go.mod
fi

echo "go.mod has been updated"
go mod tidy
