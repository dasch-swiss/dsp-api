# ADR-0006 Use ZIO HTTP

Date: 2022-12-01

## Status

Accepted

## Context

The current routes use the `Akka Http` framework. The current routes use the `Akka Http` library. Because of changes to the licensing of the `Akka` framework, we want to move away from using `Akka Http`. This also fits the general strategic decision to use ZIO for the backend.

## Decision

In preparation of the move from `Akka` to `ZIO`, it was decided that the routes should be ported to use the `ZIO HTTP` server / library instead of `Akka Http`.

## Consequences

In a first step only the routes are going to be ported, one by one, to use `ZIO HTTP` instead of being routed through `Akka Http`. The `Akka Actor System` still remains and will be dealt with later.

