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

- MAJOR: Change API v1 file uploads to work like API v2 (@github[#1233](#1233)). To enable
  Knora and Sipi to work without sharing a filesystem, the procedure
  for uploading files in API v1 has changed; see
  @ref:[Adding Resources with Image Files](../03-apis/api-v1/adding-resources.md#adding-resources-with-image-files)
  and @ref:[Bulk Import with Image Files](../03-apis/api-v1/adding-resources.md#bulk-import-with-image-files).
  Also, for consistency with API v2 responses, the `temporaryBaseIIIFUrl` returned by the Sipi 
  `/upload` route no longer includes the image filename; see
  @ref:[Upload Files to Sipi](../03-apis/api-v2/editing-values.md#upload-files-to-sipi).
