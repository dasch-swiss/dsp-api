## Releasing

 1. (optional) Run `GenerateContributorsFile`, passing it a GitHub API token
    (`bazel run //webapi:GenerateContributorsFile -- [ -t TOKEN ]`), to generate
    `Contributors.md`, then commit that file. The file can be found under:
    `/bazel-bin/webapi/GenerateContributorsFile.runfiles/io_dasch_knora_api/Contributors.md`
 1. (optional) Push and merge PR to `develop`.
 1. Github CI will start a [CI build](https://github.com/dasch-swiss/knora-api/actions) for the new tag and publish
    artifacts to Docker Hub.
 1. On Github - Tag the commit with the version string, e.g., `vX.X.X` and create a release.
 1. Run [gren](https://github.com/github-tools/github-release-notes) (see the instruction at the end)
 1. Publish documentation.

-> in general, releases should be cut at least once per month and on the last working day of the month.
-> before a major release, create a minor one if not already present.

## Installing and running 'gren'

```
$ npm install github-release-notes -g
```

Generate a GitHub token, with repo scope, at this link. Then add this line to `~/.bash_profile` (or `~/.zshrc`)

```
export GREN_GITHUB_TOKEN=your_token_here
```

To generate the release notes for the latest release, go to Github and make a release by giving it a tag in the form of `vX.X.X`.

Then:

```bash
# Navigate to your project directory
cd ~/Path/to/repo
# Run the task
gren release --override --milestone-match="xxxx-xx"
```