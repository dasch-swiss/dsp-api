# Release Notes for Next Release

Write any new release notes between releases into this file. They will be moved to the correct place,
at the time of the release, because only then we will know the next release number.

Also, please change the **HINT** to the appropriate level:
 - MAJOR: the changes introduced warrant a major number increase
 - FEATURE: the changes introduced warrant a minor number increase
 - FIX: the changes introduced warrant a bugfix number increase


## HINT => MAJOR CHANGE

- MAJOR: Use HTTP POST to mark resources and values as deleted (@github[#1203](#1203))
- MAJOR: Reorganize user and project routes (@github[#1209](#1209))
- FEATURE: Secure routes returning user informations (@github[#961](#961))
- MAJOR: Change all `xsd:dateTimeStamp` to `xsd:dateTime` in the triplestore (@github[#1211](#1211)). Existing data must be updated.
