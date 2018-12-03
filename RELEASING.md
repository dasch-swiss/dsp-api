## Releasing

 1. Communicate that a release is about to be released in the [DaSCH Github Channel](https://github.com/orgs/dhlab-basel/teams/dasch), so that no new Pull Requests are merged
 1. Create a new branch, e.g., `releasing-vX.X.X`
 1. Move the release notes from `next` to either a new page for a major release or to the corresponding minor release notes page.
 1. On Github - Create new milestone
 1. On Github - Move any open issues from current release milestone to the next release milestone and so on.
 1. On Github - Close current milestone.
 1. Run `GenerateContributorsFile`, passing it a GitHub API token (`runMain org.knora.webapi.util.GenerateContributorsFile [ -t TOKEN ]`), to generate `Contributors.md`, then commit that file.
 1. Push and merge PR to `develop`.
 1. Travis CI will start a [CI build](https://travis-ci.org/dhlab-basel/Knora/builds) for the new tag and publish
    artifacts to Docker Hub.
 1. On Github - Tag the commit with the version string, e.g., `vX.X.X` and create a release.
 1. On Github - Copy the release notes from the docs to the release.
 1. Publish documentation.

-> in general, releases should be cut at least once per month and on the last working day of the month.


## Under the Travis hood

Here is what happens in detail when Travis CI is building a git tagged commit. According to the `.travis.yml` file,
the `publish` stage runs the following task:
   
 1. Credentials are read from the `DOCKER_USER` and `DOCKER_PASS` environment vaiables which are stored encrypted on
    the `.travis.yml` file.
 1. The `webapi` docker image is build, tagged, and published.
 1. The `salsah1` docker image is build, tagged, and published.
 1. The `sipi` docker image is additionally tagged and published.
