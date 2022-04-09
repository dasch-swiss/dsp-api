# 2. Change Cache Service Manager from Akka-Actor to ZLayer

Date: 2022-04-06

## Status

Accepted

## Context

The `org.knora.webapi.store.cacheservice.CacheServiceManager` was implemented as an `Akka-Actor`.

## Decision

As part of the move from `Akka` to `ZIO`, it was decided that the `CacheServiceManager` and the whole implementation of the in-memory and Redis backed cache is refactored using ZIO.

## Consequences

The usage from other actors stays the same. The actor messages and responses don't change.
