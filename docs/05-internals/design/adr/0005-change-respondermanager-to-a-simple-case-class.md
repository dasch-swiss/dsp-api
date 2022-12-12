# 5. Change ResponderManager to a simple case class

Date: 2022-06-06

## Status

Accepted

## Context

The `org.knora.webapi.responders.ResponderManager` was implemented as an Akka-Actor.

## Decision

In preparation of the move from `Akka` to `ZIO`, it was decided that the `ResponderManager` is refactored using plain `case` classes.

## Consequences

The actor messages and responses don't change. All calls made previously to the `ResponderManager` and the `StorageManager` are now changed to the `ApplicationActor` which will route the calls to either the `ResponderManager` or the `StorageManager` based on the message type. The `ApplicationActor` is the only actor that is allowed to make calls to either the `ResponderManager` or the `StorageManager`. All requests from routes are now routed to the `ApplicationActor`.
