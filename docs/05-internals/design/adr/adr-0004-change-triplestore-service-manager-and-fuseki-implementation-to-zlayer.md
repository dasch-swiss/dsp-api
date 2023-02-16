# ADR-0004 Change Triplestore Service Manager and Fuseki implementation to ZLayer

Date: 2022-05-23

## Status

Accepted

## Context

Both `org.knora.webapi.store.triplestore.TriplestoreServiceManager` and `org.knora.webapi.store.triplestore.impl.TriplestoreServiceHttpConnectorImpl`
where implemented as Akka-Actors.

## Decision

As part of the move from `Akka` to `ZIO`, it was decided that the `TriplestoreServiceManager` and the `TriplestoreServiceHttpConnectorImpl` is refactored using ZIO.

## Consequences

The usage from other actors stays the same. The actor messages and responses don't change.
