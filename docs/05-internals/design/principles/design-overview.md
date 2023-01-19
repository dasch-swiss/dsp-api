<!---
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
-->

# DSP-API Server Design Overview

## Introduction

DSP-API's responsibilites are:

- Querying, creating, updating, and deleting data
- Creating, updating and deleting data models (ontologies)
- Managing projects and users
- Authentication of clients
- Authorisation of clients' requests

DSP-API is developed with [Scala](http://www.scala-lang.org/) and uses the
[Akka](http://akka.io/) framework for message-based concurrency. It is
designed to work with the [Apache Jena Fuseki](https://jena.apache.org) triplestore
which is compliant to the [SPARQL 1.1 Protocol](http://www.w3.org/TR/sparql11-protocol/).
For file storage, it uses [Sipi](http://sipi.io).

## DSP-API Versions

There are two versions of DSP-API:

- DSP-API v2, the latest DSP-API that should be used
- DSP-API v1, legacy API compatibile with applications that used the prototype software.

There is also an Admin API for administrating DSP projects.

Internally, DSP-API v1 and v2 both use functionality in the admin API. DSP-API v1 uses 
some functionality from API v2, but API v2 does not depend on API v1.

## Error Handling

The error-handling design has these aims:

1.  Simplify the error-handling code in actors as much as possible.
2.  Produce error messages that clearly indicate the context in which
    the error occurred (i.e. what the application was trying to do).
3.  Ensure that clients receive an appropriate error message when an
    error occurs.
4.  Ensure that `ask` requests are properly terminated with an
    `akka.actor.Status.Failure` message in the event of an error,
    without which they will simply time out (see
    [Ask: Send and Receive Future](https://doc.akka.io/docs/akka/current/actors.html?language=scala#ask-send-and-receive-future)).
5.  When a actor encounters an error that isn't the client's fault (e.g.
    a triplestore failure), log it, but don't do this with errors caused
    by bad input.
6.  When logging errors, include the full JVM stack trace.

A hierarchy of exception classes is defined in `Exceptions.scala`,
representing different sorts of errors that could occur. The hierarchy
has two main branches:

  - `RequestRejectedException`, an abstract class for errors that are
    the client's fault. These errors are not logged.
  - `InternalServerException`, an abstract class for errors that are not
    the client's fault. These errors are logged.

Exception classes in this hierarchy can be defined to include a wrapped
`cause` exception. When an exception is logged, its stack trace will be
logged along with the stack trace of its `cause`. It is therefore
recommended that low-level code should catch low-level exceptions, and
wrap them in one of our higher-level exceptions, in order to clarify the
context in which the error occurred.

To simplify error-handling in responders, a utility method called
`future2Message` is provided in `ActorUtils`. It is intended to be used
in an actor's `receive` method to respond to messages in the `ask`
pattern. If the responder's computation is successful, it is sent to the
requesting actor as a response to the `ask`. If the computation fails,
the exception representing the failure is wrapped in a `Status.Failure`,
which is sent as a response to the `ask`. If the error is a subclass of
`RequestRejectedException`, only the sender is notified of the error;
otherwise, the error is also logged and rethrown (so that the
`KnoraExceptionHandler` can handle the exception).

In many cases, we transform data from the triplestore into a `Map`
object. To simplify checking for required values in these collections,
the class `ErrorHandlingMap` is provided. You can wrap any `Map` in an
`ErrorHandlingMap`. You must provide a function that will generate an
error message when a required value is missing, and optionally a
function that throws a particular exception. Rows of SPARQL query
results are already returned in `ErrorHandlingMap` objects.

If you want to add a new exception class, see the comments in
`Exceptions.scala` for instructions.

### Transformation of Exception to Client Responses

The `org.knora.webapi.KnoraExceptionHandler` is brought implicitly into
scope of `akka-http`, and by doing so registered and used to handle the
transformation of all `KnoraExceptions` into `HttpResponses`. This
handler handles only exceptions thrown inside the route and not the
actors. However, the design of reply message passing from actors (by
using `future2Message`), makes sure that any exceptions thrown inside
actors, will reach the route, where they will be handled.

See also [Futures with Akka](futures-with-akka.md).

## API Routing

The API routes in the `routing` package are defined using the DSL
provided by the
[akka-http](http://doc.akka.io/docs/akka/current/scala/http/routing-dsl/index.html)
library. A routing function has to do the following:

1.  Authenticate the client.
2.  Figure out what the client is asking for.
3.  Construct an appropriate request message and send it to
    `ResponderManagerV1`, using the `ask` pattern.
4.  Return a result to the client.

To simplify the coding of routing functions, they are contained in
objects that extend `org.knora.webapi.routing.Authenticator`. Each
routing function performs the following operations:

1.  `Authenticator.getUserADM` is called to authenticate the user.
2.  The request parameters are interpreted and validated, and a request
    message is constructed to send to the responder. If the request is
    invalid, `BadRequestException` is thrown. If the request message is
    requesting an update operation, it must include a UUID generated by
    `UUID.randomUUID`, so the responder can obtain a write lock on the
    resource being updated.

The routing function then passes the message to a function in an API-specific
routing utility: `RouteUtilV1`, `RouteUtilV2`, or `RouteUtilADM`.
This utility function sends the message to `ResponderManager` (which
forwards it to the relevant responder), returns a response to the client
in the appropriate format, and handles any errors.

## Logging

Logging in DSP-API is configurable through `logback.xml`, allowing fine
grain configuration of what classes / objects should be logged from which level.

The Akka Actors use [Akka Logging](https://doc.akka.io/docs/akka/current/logging.html)
while logging inside plain Scala Objects and Classes is done through
[Scala Logging](https://github.com/lightbend/scala-logging).
