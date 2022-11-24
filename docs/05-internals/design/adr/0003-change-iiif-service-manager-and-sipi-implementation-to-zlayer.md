# 3. Change IIIF Service Manager and Sipi implementation to zlayer

Date: 2022-04-29

## Status

Accepted

## Context

Both `org.knora.webapi.store.iiif.IIIFServiceManager` and `org.knora.webapi.store.iiif.impl.IIIFServiceSipiImpl`
where implemented as Akka-Actors 

## Decision

As part of the move from `Akka` to `ZIO`, it was decided that the `IIIFServiceManager` and the `IIIFServiceSipiImpl` is refactored using ZIO.

## Consequences

The usage from other actors stays the same. The actor messages and responses don't change.
