# Release Notes for Next Release

Write any new release notes between releases into this file. They will be moved to the correct place,
at the time of the release, because only then we will know the next release number.

Also, please change the **HINT** to the appropriate level:

- MAJOR: the changes introduced warrant a major number increase

- FEATURE: the changes introduced warrant a minor number increase

- FIX: the changes introduced warrant a bug fix number increase


## HINT => MAJOR CHANGE

- FIX: Unescape standoff string attributes when verifying text value update (@github[#1242](#1242))

- FEATURE: Remove persistent map code (@github[#1254](#1254))

- FEATURE: Return user's permission on resources and values (@github[#1257](#1257))

- FEATURE: Get resources in a particular class from a project (@github[#1251](#1251))

- MAJOR: Separate the `knora-admin` ontology from `knora-api` (@github[#1263](#1263)).
  Existing repositories must be updated; see `upgrade/1263-knora-admin` for instructions.
  
- FEATURE: Add support for searching for specific list values in Gravsearch for both the simple and complex schema (@github[#1314](#1314)).  

- REFACTOR: List value responses contain the list node's label in the simple schema only (@github[#1321](#1321))
