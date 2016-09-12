.. Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
   Tobias Schweizer, André Kilchenmann, and André Fatton.

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

.. _how-to-add-a-route:

How to Add an API Route
=======================

Write SPARQL templates
-----------------------

Add any SPARQL templates you need to ``src/main/twirl/queries/sparql/v1``, using the `Twirl`_ template engine.

Write Responder Request and Response Messages
----------------------------------------------

Add a file to the ``org.knora.webapi.messages.v1respondermessages``
package, containing case classes for your responder's request and
response messages. Add a trait that the responder's request messages
extend. Each request message type should contain a ``UserProfileV1``.

Response message classes that represent a complete API response must
extend ``KnoraResponseV1``, and must therefore have a ``toJsValue``
method that converts the response message to a JSON AST using
`spray-json <https://github.com/spray/spray-json>`__.

Write a Responder
-----------------

Write an Akka actor class that extends ``ResponderV1``, and add it to
the ``org.knora.webapi.responders.v1`` package.

Give your responder a ``receive()`` method that handles each of your
request message types by generating a ``Future`` containing a response
message, and passing the ``Future`` to ``ActorUtils.futureToMessage()``. See
:ref:`futures-with-akka` and :ref:`error-handling` for details.

See :ref:`triplestore-access` for details of how to access the triplestore
in your responder.

Add an actor pool for your responder to ``application.conf``, under
``actor.deployment``.

In ``ResponderManagerV1``, add a reference to your actor pool. Then add
a ``case`` to the ``receive()`` method in ``ResponderManagerV1``, to
match messages that extend your request message trait, and forward them
to that pool.

Write a Route
--------------

Add an object to the ``org.knora.webapi.routing.v1`` package for your
route. Your object should look something like this:

::

    import akka.actor.ActorSystem
    import akka.event.LoggingAdapter
    import org.knora.webapi.SettingsImpl
    import org.knora.webapi.messages.v1respondermessages.SampleGetRequestV1
    import org.knora.webapi.routing.RouteUtils
    import spray.routing.Directives._
    import spray.routing._
    import org.knora.webapi.util.StringConversions
    import org.knora.webapi.BadRequestException

    object SampleRouteV1 extends Authenticator {

        def knoraApiPath(_system: ActorSystem, settings: SettingsImpl, log: LoggingAdapter): Route = {
            implicit val system: ActorSystem = _system
            implicit val executionContext = system.dispatcher
            implicit val timeout = settings.defaultTimeout
            val responderManager = system.actorSelection("/user/responderManager")

            path("sample" / Segment) { iri =>
                get { requestContext =>
                    val requestMessageTry = Try {
                        val userProfile = getUserProfileV1(requestContext)
                        makeRequestMessage(iri, userProfile)
                    }

                    RouteUtils.runJsonRoute(
                        requestMessageTry,
                        requestContext,
                        settings,
                        responderManager,
                        log
                    )
                }
            }
        }

        private def makeRequestMessage(iriStr: String, userProfile: UserProfileV1): SampleGetRequestV1 = {
            val iri = StringConversions.toIri(iriStr, () => throw BadRequestException(s"Invalid IRI: $iriStr"))
            SampleGetRequestV1(iri, userProfile)
        }
    }

It's important that inside the ``get`` (or ``post`` or whatever),
everything before the call to ``RouteUtils.runJsonRoute`` is wrapped in a
``Try`` constructor. This allows ``RouteUtils.runJsonRoute`` to handle input
validation errors.

Finally, add your ``rapierPath()`` function to the ``routes`` member
variable in ``KnoraHttpService``.

.. _Twirl: https://github.com/playframework/twirl
