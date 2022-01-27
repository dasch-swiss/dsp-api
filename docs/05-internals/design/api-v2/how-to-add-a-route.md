<!---
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
-->

# How to Add an API v2 Route

## Write SPARQL templates

Add any SPARQL templates you need to `src/main/twirl/queries/sparql/v2`,
using the [Twirl](https://github.com/playframework/twirl) template
engine.

## Write Responder Request and Response Messages

Add a file to the `org.knora.webapi.messages.v2.responder`
package, containing case classes for your responder's request and
response messages. Add a trait that the responder's request messages
extend. Each request message type should contain a `UserADM`.

Request and response messages should be designed following the patterns described
in [JSON-LD Parsing and Formatting](json-ld.md). Each responder's
request messages should extend a responder-specific trait, so that
`ResponderManager` will know which responder to route those messages to.

## Write a Responder

Write an Akka actor class that extends `org.knora.webapi.responders.Responder`,
and add it to the `org.knora.webapi.responders.v2` package.

Give your responder a `receive(msg: YourCustomType)` method that handles each of your
request message types by generating a `Future` containing a response message.

See [Triplestore Access](../principles/design-overview.md#triplestore-access) for details of how
to access the triplestore in your responder.

Add the path of your responder to the `org.knora.webapi.responders` package object,
and add code to `ResponderManager` to instantiate the new responder. Then add a `case` to
the `receive` method in `ResponderManager`, to match messages that extend your request
message trait, and pass them them to that responder's receive method.
The responder's resulting `Future` must be passed to the `ActorUtil.future2Message`.
See [Futures with Akka](../principles/futures-with-akka.md) and
[Error Handling](../principles/design-overview.md#error-handling) for details.

## Write a Route

Add a class to the `org.knora.webapi.routing.v2` package for your
route, using the Akka HTTP [Routing DSL](https://doc.akka.io/docs/akka-http/current/routing-dsl/index.html).
See the routes in that package for examples. Typically, each route
route will construct a responder request message and pass it to
`RouteUtilV2.runRdfRouteWithFuture` to handle the request.

Finally, add your route's `knoraApiPath` function to the `apiRoutes` member
variable in `KnoraService`. Any exception thrown inside the route will
be handled by the `KnoraExceptionHandler`, so that the correct client
response (including the HTTP status code) will be returned.
