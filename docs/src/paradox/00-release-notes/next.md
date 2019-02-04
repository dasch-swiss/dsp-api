# Release Notes for Next Release

Write any new release notes between releases into this file. They will be moved to the correct place,
at the time of the release, because only then we will know the next release number.

Also, please change the **HINT** to the appropriate level:
 - MAJOR: the changes introduced warrant a major number increase
 - FEATURE: the changes introduced warrant a minor number increase
 - FIX: the changes introduced warrant a bugfix number increase


## HINT => MAJOR CHANGE

- MAJOR: Fix property names for incoming links (@github[#1144](#1144))
- FIX: Triplestore connection error when using dockerComposeUp (@github[#1122](#1122))
- FEATURE: Update resource metadata in API v2 (@github[#1131](#1131))
- FIX: Reject link value properties in Gravsearch queries in the simple schema (@github[#1145](#1145))
- FIX: Fix error-checking when updating cardinalities in ontology API (@github[#1142](#1142))
- FEATURE: Allow setting resource creation date in bulk import (@github[#1151](#1151))
- FEATURE: The `v2/authentication` route now also initiates cookie creation (the same as `v1/authentication`) (@github[#1159](#1159))
- FIX: Allow hasRepresentation in an ontology used in a bulk import (@github[#1171](#1171))
- MAJOR: Generate and resolve ARK URLs for resources (@github[#1161](#1161)). Projects
  that have resource IRIs that do not conform to the format specified in
  https://docs.knora.org/paradox/03-apis/api-v2/knora-iris.html#iris-for-data
  must update them.
- FIX: Set cookie domain to the value specified in `application.conf` with the setting `cookie-domain` (@github[#1169](#1169))
- FIX: Fix processing of shared property in bulk import (@github[#1182](#1182))
- MAJOR: Use project shortcode in IIIF URLs (@github[#1191](#1191)). If you have file value IRIs containing the substring `/reps/`, you must replace `/reps/` with `/values/`.
