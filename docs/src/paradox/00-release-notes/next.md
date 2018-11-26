# Release Notes for Next Release

Write any new release notes between releases into this file. They will be moved to the correct place,
at the time of the release, because only then we will know the next release number.

Also, please change the "HINT" to the appropriate level:
 - MAJOR CHANGE, the changes introduced warrant a major number increase
 - MINOR CHANGE, the changes introduced warrant a minor number increase
 - BUGFIX CHANGE, the changes introduced warrant a bugfix number increase


## HINT => MAJOR CHANGE

- [BREAKING ONTOLOGY CHANGE] The property `knora-base:username` was added and is required for `knora-base:User`. (@github[#1047](#1047))
- [BREAKING API CHANGE] The `/admin/user` API has changed due to adding the `username` property. (@github[#1047](#1047))
- [FIX] Incorrect standoff to XML conversion if empty tag has empty child tag (@github[#1054](#1054))
- [FEATURE] Add default permission caching (@github[#1062](#1062))
- [FIX] Fix unescaping in update check and reading standoff URL (@github[#1074](#1074))