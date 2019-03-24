## Releasing

 1. (optional) Run `GenerateContributorsFile`, passing it a GitHub API token (`runMain org.knora.webapi.util.GenerateContributorsFile [ -t TOKEN ]`), to generate `Contributors.md`, then commit that file.
 1. (optional) Push and merge PR to `develop`.
 1. Travis CI will start a [CI build](https://travis-ci.org/dhlab-basel/Knora/builds) for the new tag and publish
    artifacts to Docker Hub.
 1. On Github - Tag the commit with the version string, e.g., `vX.X.X` and create a release.
 1. Run [gren](https://github.com/github-tools/github-release-notes)
 1. Publish documentation.

-> in general, releases should be cut at least once per month and on the last working day of the month.
-> before a major release, create a minor one if not already present.

## Under the Travis hood

Here is what happens in detail when Travis CI is building a git tagged commit. According to the `.travis.yml` file,
the `publish` stage runs the following task:
   
 1. Credentials are read from the `DOCKER_USER` and `DOCKER_PASS` environment vaiables which are stored encrypted on
    the `.travis.yml` file.
 1. The `webapi` docker image is build, tagged, and published.
 1. The `salsah1` docker image is build, tagged, and published.
 1. The `sipi` docker image is additionally tagged and published.

[1] 