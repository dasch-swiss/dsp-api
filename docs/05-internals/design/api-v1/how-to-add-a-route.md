<!---
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
-->

# How to Add an API v1 Route

## Write SPARQL templates

Add any SPARQL templates you need to `src/main/twirl/queries/sparql/v1`,
using the [Twirl](https://github.com/playframework/twirl) template
engine.

## Write Responder Request and Response Messages

Add a file to the `org.knora.webapi.messages.v2.responder`
package, containing case classes for your responder's request and
response messages. Add a trait that the responder's request messages
extend. Each request message type should contain a `UserADM`.

Response message classes that represent a complete API response must
extend `KnoraResponseV1`, and must therefore have a `toJsValue` method
that converts the response message to a JSON AST using
[spray-json](https://github.com/spray/spray-json).

## Write a Responder

Write a class that extends `org.knora.webapi.responders.Responder`,
and add it to the `org.knora.webapi.responders.v1` package.

Give your responder a `receive(msg: YourCustomType)` method that handles each of your
request message types by generating a `Future` containing a response message.

Add the path of your responder to the `org.knora.webapi.responders` package object,
and add code to `ResponderManager` to instantiate an object for your responder class.
Then add a `case` to the `receive` method in `ResponderManager`, to match
messages that extend your request message trait, and pass them to the responder's
receive method. The responder's resulting `Future` must be passed to the `ActorUtil.future2Message`.
See [Futures with Akka](../principles/futures-with-akka.md) and
[Error Handling](../principles/design-overview.md#error-handling) for details.

## Write a Route

Add a class to the `org.knora.webapi.routing.v1` package for your
route, using the Akka HTTP [Routing DSL](https://doc.akka.io/docs/akka-http/current/routing-dsl/index.html).
See the routes in that package for examples. Typically, each route
route will construct a responder request message and pass it to
`RouteUtilV1.runRdfRouteWithFuture` to handle the request.

Finally, add your `knoraApiPath` function to the `apiRoutes` member
variable in `KnoraService`. Any exception thrown inside the route will
be handled by the `KnoraExceptionHandler`, so that the correct client
response (including the HTTP status code) will be returned.
