#!/usr/bin/env bash

# This script updates the documentation on knora.org by pushing a commit to the gh-pages branch
# of the Knora repository on GitHub.

# Before you run this script, you must be on the develop branch, having committed any changes
# you made there.

# If you don't want git to ask you for your username and password, use SSH instad of HTTPS
# to clone the Knora repository.

set -e

TMP_HTML="/tmp/knora-html" # The temporary directory where we store built HTML.
CURRENT_BRANCH=$(git rev-parse --abbrev-ref HEAD) # The current git branch.

if [ "$CURRENT_BRANCH" != "develop" ]
then
    echo "Current branch is $CURRENT_BRANCH, not develop"
    exit 1
fi

# Build the HTML docs.

sbt makeSite
make jsonformat

# Copy the built HTML docs to a temporary directory.

rm -rf $TMP_HTML
mkdir $TMP_HTML
cp -R target/site/sphinx $TMP_HTML/manual
cp -R _format_docu $TMP_HTML/api

# Switch to the gh-pages branch and remove the existing HTML docs from it.

git checkout gh-pages
git rm -rf ../documentation/manual
git rm -rf ../documentation/api

# Move the new docs from the temporary directory into the git repository tree.

mv $TMP_HTML/manual $TMP_HTML/api ../documentation

# Commit the changes to the gh-pages branch, and push to origin.

git add ../documentation/manual
git add ../documentation/api
git commit -m "Update gh-pages." || true
git push origin gh-pages

# Switch back to the develop branch, and remove the leftover documentation directory.

git checkout develop
rm -rf ../documentation
