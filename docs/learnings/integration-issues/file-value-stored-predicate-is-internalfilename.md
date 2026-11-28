---
title: "A file value's stored filename predicate is knora-base:internalFilename"
date: 2026-11-28
category: integration-issues
component: webapi-admin
module: modules/webapi/src/main/scala/org/knora/webapi/slice/admin/repo/AssetReferencesRepo.scala
problem_type: integration-issue
severity: medium
symptoms:
  - "A SPARQL query for file values by knora-api:fileValueHasFilename matches nothing in the triplestore"
  - "An asset that only an older file value version names is reported as unreferenced"
root_cause: "fileValueHasFilename is the knora-api (external) name; the triplestore stores knora-base:internalFilename, and older value versions are reached only through knora-base:previousValue."
tags: [sparql, file-value, internal-filename, previous-value, assets, triplestore]
issue: "DEV-8096"
---

# A file value's stored filename predicate is knora-base:internalFilename

## Problem

To find which ingest assets a project's data still references, a query must read the filenames of its file values.
The knora-api name `fileValueHasFilename` does not exist in the stored data, so a query using it returns nothing.

## Root Cause

The API schema and the stored schema differ. The stored predicate is `knora-base:internalFilename`. A newer value
version also points at the older one through `knora-base:previousValue`, so a pattern on file values alone also
matches old versions as if they were separate values.

## Solution

`AssetReferencesRepo` reads `knora-base:internalFilename` over `previousValue*`, keeps `?resource` a resource with
`knora-base:attachedToProject`, and reads the resource's `isDeleted`. The asset ID is the filename before its first
`.`, as in `AssetId.fromFilename`, so `.jp2`, `.jpx`, `.mp4` and derivatives all match.

## Prevention

- Query the stored (knora-base) predicates; check a predicate against the test data before writing the query.
- Count previous value versions as references when the question is "may this asset be deleted": a false negative
  is safe, a false orphan is not.
