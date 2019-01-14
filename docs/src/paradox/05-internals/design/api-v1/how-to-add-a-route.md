<!---
Copyright © 2015-2019 the contributors (see Contributors.md).

This file is part of Knora.

Knora is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published
by the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

Knora is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public
License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
-->

# How to Add an API v1 Route

@@toc

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

Write an Akka actor class that extends `org.knora.webapi.responders.Responder`,
and add it to the `org.knora.webapi.responders.v1` package.

Give your responder a `receive()` method that handles each of your
request message types by generating a `Future` containing a response
message, and passing the `Future` to `ActorUtil.future2Message`. See
@ref:[Futures with Akka](../principles/futures-with-akka.md) and
@ref:[Error Handling](../principles/design-overview.md#error-handling) for details.

See @ref:[Triplestore Access](../principles/design-overview.md#triplestore-access) for details of how
to access the triplestore in your responder.

Add the path of your responder to the `org.knora.webapi.responders` package object,
and add code to `ResponderManager` to instantiate an Akka router for your
actor. Then add a `case` to the `receive` method in `ResponderManager`, to match
messages that extend your request message trait, and forward them to
that responder's router.

## Write a Route

Add an object to the `org.knora.webapi.routing.v1` package for your
route, using the Akka HTTP [Routing DSL](https://doc.akka.io/docs/akka-http/current/routing-dsl/index.html).
See the routes in that package for examples. Typically, each route
route will construct a responder request message and pass it to
`RouteUtilV1.runRdfRouteWithFuture` to handle the request.

Finally, add your `knoraApiPath` function to the `apiRoutes` member
variable in `KnoraService`. Any exception thrown inside the route will
be handled by the `KnoraExceptionHandler`, so that the correct client
response (including the HTTP status code) will be returned.
