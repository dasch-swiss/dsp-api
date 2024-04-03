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

With the introduction of the new `Segment` concept in v31,
the previously existing properties `knora-base:isSequenceOf` and `knora-base:hasSequenceBounds`
have been deprecated and will be removed in a future version.

If you are creating a new ontology,
please do not use these properties anymore.
Instead, use the newly introduced Segment type.

More information on Segments can be found [here](../02-dsp-ontologies/knora-base.md#segment).


