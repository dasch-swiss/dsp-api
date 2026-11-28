---
title: "Iri.validateAndEscapeIri rejects URLs with // in the path, such as Wayback Machine links"
date: 2026-11-28
category: integration-issues
component: webapi-standoff
module: modules/webapi/src/main/scala/org/knora/webapi/messages/util/standoff/StandoffLinkUri.scala
problem_type: integration-issue
severity: medium
symptoms:
  - "A standoff link to https://web.archive.org/web/2020/https://example.org/ is rejected as an invalid IRI"
root_cause: "The UrlValidator behind Iri.validateAndEscapeIri is built without ALLOW_2_SLASHES, and every SmartIri relies on that strictness."
tags: [standoff, iri, url-validator, wayback-machine, smartiri]
issue: "DEV-8095"
---

# Iri.validateAndEscapeIri rejects URLs with // in the path, such as Wayback Machine links

## Problem

A Wayback Machine URL embeds the archived URL in its path, so the path holds `//`. Standoff links to such URLs were
rejected.

## Root Cause

`Iri.validateAndEscapeIri` uses a `UrlValidator` without `ALLOW_2_SLASHES`. It validates every `SmartIri`, so
loosening it would accept `//` in ontology, class and property IRIs too.

## Solution

`StandoffLinkUri.validate` accepts http and https URLs with `//` in the path and escapes them as before.
`StandoffTagUtilV2` uses it for `valueHasUri` only.

## Prevention

`StandoffLinkUriSpec` pins both sides: Wayback URLs pass `StandoffLinkUri` and still fail
`Iri.validateAndEscapeIri`. Scope a relaxed validation to the one field that needs it.
