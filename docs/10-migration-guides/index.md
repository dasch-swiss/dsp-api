# Overview

Generally, DSP-API is designed to be backward compatible.
Whenever a new major version of DSP-API is released,
the existing data is migrated to the new version automatically.
The public Rest API is also stable and should remain backward compatible.

However, when a feature appears not to be used,
or if there are urgent technical reasons to change the API,
we may decide to release breaking changes.
In these instances, we try to provide a migration guide,
in case some project or application is affected by the change.

If you experience any issues with the migration,
please contact us via the [DaSCH Help Center](https://www.dasch.swiss/help-center).

## Migration Guides

<!--- 
For the following list, I propose the structure `vXX to vYY: Title` 
or if the version is not yet known: `Planned: Title`
--->

### Planned: Removal of `knora-base:isSequenceOf` and `knora-base:hasSequenceBounds`

If you have used `knora-base:isSequenceOf` and `knora-base:hasSequenceBounds` in your data,
this should be replaced by `knora-base:isAudioSegmentOf` or `knora-base:isVideoSegmentOf` respectively,
and `knora-base:hasSegmentBounds`.

The issue with that is that these properties are only allowed
on resources of type `knora-base:AudioSegment` and `knora-base:VideoSegment`,
whereas previously `knora-base:isSequenceOf` could be added to any `knora-base:Resource`.
This means that you will have to change the type of the resources that you have been using
to be of type `knora-base:AudioSegment` or `knora-base:VideoSegment`.

## Deprecation Warnings

<!---
These items should be removed, once the feature has been removed from the codebase.
Then, only the migration guides should be kept.
--->

### `isSequenceOf` and `hasSequenceBounds`

With the introduction of the new `Segment` concept in DSP-API v30.11.0,
the previously existing properties `knora-base:isSequenceOf` and `knora-base:hasSequenceBounds`
have been deprecated and will be removed in a future version.

If you are creating a new ontology,
please do not use these properties anymore.
Instead, use the newly introduced Segment type.

More information on Segments can be found [here](../02-dsp-ontologies/knora-base.md#segment).

### Updating DOAPs

For updating DOAPs, using the general-purpose route `/admin/permissions/{permissionIri}/group|hasPermissions`
is deprecated and will be removed in a future version.

Insteads use `/admin/permissions/doap/{permissionIri}`
as described [here](../03-endpoints/api-admin/permissions.md#updating-an-existing-default-object-access-permission).

### Retrieving List Information

For retrieving list infos, the routes `/admin/lists/infos/{listIri}` and `/admin/lists/nodes/{listIri}`
are deprecated and will be removed in a future version.  
Instead, use `/admin/lists/{listIri}/info` 
as described [here](../03-endpoints/api-admin/lists.md#get-list-info). 

### Authentication

All authentication methods other than the bearer token are deprecated.
This includes DSP-API and Sipi.
For more information, see the [Authentication](../03-endpoints/api-v2/authentication.md) page
and the [SIPI documentation](../06-sipi/sipi-and-dsp-api.md#authentication-of-users-with-sipi).

### Cardinality Replacement Check

The unparametrised check if it is possible to replace a cardinality of a property on a resource class is deprecated.
Instead of `/v2/ontologies/canreplacecardinalities/{classIRI}`,
use `/v2/ontologies/canreplacecardinalities/{classIRI}?propertyIri={propertyIRI}&newCardinality=[0-1|1|1-n|0-n]`.

For more information, see [here](../03-endpoints/api-v2/ontology-information/#pre-update-checks).

### `fileValueHasExternalUrl`

For still image file values, the property `knora-base:fileValueHasExternalUrl` is deprecated.
Instead, use `knora-base:stillImageFileValueExternalFileValue`.  
Additionally, this property should no longer be typed as a string literal,
but as a `xsd:anyURI` instead.

For more information, see [here](../03-endpoints/api-v2/editing-values.md#images-stored-in-an-external-iiif-server).

