# ADR-0006 Use ZIO HTTP

Date: 2022-12-01

## Status

Accepted

## Context

The current routes use the `Akka Http` framework. Because of Lightbend's decision that `Akka` will no longer be open source and their introduction of a commercial license, we want to get away from `Akka Http`. This also fits the general strategic decision to use ZIO for the backend.

## Decision

In preparation of the move from `Akka` to `ZIO`, it was decided that the routes should be ported to use the `ZIO HTTP` server / library instead of `Akka Http`.

## Consequences

In a first step only the routes are going to be ported, one by one, to use `ZIO HTTP` instead of being routed through `Akka Http`. The `Akka Actor System` still remains and will be dealt with later.

