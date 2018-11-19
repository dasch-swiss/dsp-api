<!---
Copyright © 2015-2018 the contributors (see Contributors.md).

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

# JSON-LD Parsing and Formatting

@@toc

## JsonLDUtil

Knora provides a utility object called `JsonLDUtil`, which wraps the
[JSON-LD Java API](https://github.com/jsonld-java/jsonld-java), and parses JSON-LD text to a
Knora data structure called `JsonLDDocument`. These classes provide commonly needed
functionality for extracting and validating data from JSON-LD documents, as well
as for constructing new documents.

## Parsing JSON-LD

A route that expects a JSON-LD request must first parse the JSON-LD using
`JsonLDUtil` . For example, this is how `ValuesRouteV2` parses a JSON-LD request to create a value:

@@snip [ValuesRouteV2.scala]($src$/org/knora/webapi/routing/v2/ValuesRouteV2.scala) { #post-value-parse-jsonld }

The result is a `JsonLDDocument` in which all prefixes have been expanded
to full IRIs, with an empty JSON-LD context.

The next step is to convert the `JsonLDDocument` to a request message that can be
sent to the Knora responder that will handle the request.

@@snip [ValuesRouteV2.scala]($src$/org/knora/webapi/routing/v2/ValuesRouteV2.scala) { #post-value-create-message }

This is done in a `Future`, because the processing of JSON-LD input
could in itself involve sending messages to responders.

Each request message class (in this case `CreateValueRequestV2`) has a companion object
that implements the `KnoraJsonLDRequestReaderV2` trait:

@@snip [KnoraRequestV2.scala]($src$/org/knora/webapi/messages/v2/responder/KnoraRequestV2.scala) { #KnoraJsonLDRequestReaderV2 }

This means that the companion object has a method `fromJsonLD` that takes a
`JsonLDDocument` and returns an instance of the companion object. The `fromJsonLD` method
can use the functionality of the `JsonLDDocument` data structure for extracting
and validating the content of the request. For example, `JsonLDObject.requireStringWithValidation`
gets a required member of a JSON-LD object, and validates it using a function
that is passed as an argument. Here is an example of getting and validating
a `SmartIri`:

@@snip [ValueMessagesV2.scala]($src$/org/knora/webapi/messages/v2/responder/valuemessages/ValueMessagesV2.scala) { #validate-json-ld-iri }

The validation function (in this case `stringFormatter.toSmartIriWithErr`) has to take
two arguments: a string to be validated, and a function that that throws an exception
if the string is invalid. The return value of `requireStringWithValidation` is the
return value of the validation function, which in this case is a `SmartIri`. If
the string is invalid, `requireStringWithValidation` throws `BadRequestException`.

It is also possible to get and validate an optional JSON-LD object member:

@@snip [ValueMessagesV2.scala]($src$/org/knora/webapi/messages/v2/responder/valuemessages/ValueMessagesV2.scala) { #validate-optional-json-ld-string }

Here `JsonLDObject.maybeStringWithValidation` returns an `Option` that contains
the return value of the validation function (`DateEraV2.parse`) if it was given,
otherwise `None`.

## Returning a JSON-LD Response

Each API response is represented by a message class that extends
`KnoraResponseV2`, which has a method `toJsonLDDocument` that specifies
the target ontology schema:

@@snip [KnoraResponseV2.scala]($src$/org/knora/webapi/messages/v2/responder/KnoraResponseV2.scala) { #KnoraResponseV2 }

The implementation of this method constructs a `JsonLDDocument` object,
in which all object keys are full IRIs (no prefixes are used), but in which
the JSON-LD context also specifies the prefixes that will be used when the
document is returned to the client. The function `JsonLDUtil.makeContext`
is a convenient way to construct the JSON-LD context.

Since `toJsonLDDocument` has to return an object that uses the specified
ontology schema, the recommended design is to separate IRI schema conversion from
JSON-LD generation. To handle IRI schema conversion, the response message can implement
`KnoraReadV2`:

@@snip [KnoraReadV2.scala]($src$/org/knora/webapi/messages/v2/responder/KnoraResponseV2.scala) { #KnoraReadV2 }

This means that the response message class has the method `toOntologySchema`, which returns
a copy of the same message, with IRIs adjusted for the target schema (by calling
`SmartIri.toOntologySchema` on each `SmartIri` instance).

The response message class could then have a private method called `generateJsonLD`, which
generates a `JsonLDDocument` that has the correct structure for the target schema, like
this:

@@snip [ResourceMessagesV2.scala]($src$/org/knora/webapi/messages/v2/responder/resourcemessages/ResourceMessagesV2.scala) { #generateJsonLD }

This way, the implementation of `toJsonLDDocument` can call `toOntologySchema`,
then construct a `JsonLDDocument` from the resulting object. For example:

@@snip [ResourceMessagesV2.scala]($src$/org/knora/webapi/messages/v2/responder/resourcemessages/ResourceMessagesV2.scala) { #toJsonLDDocument }

## Selecting the Response Schema

Most routes complete by calling `RouteUtilV2.runRdfRouteWithFuture`, which calls
the response message's `toJsonLDDocument` method. The `runRdfRouteWithFuture` function
has a parameter that enables the route to select the schema that should be used in
the response. It is up to each route to determine what the appropriate response schema
should be. Some routes support only one response schema. Others allow the client
to choose. To use the schema requested by the client, the route can call
`RouteUtilV2.getOntologySchema`:

@@snip [ResourcesRouteV2.scala]($src$/org/knora/webapi/routing/v2/ResourcesRouteV2.scala) { #use-requested-schema }

If the route only supports one schema, it can specify the schema directly instead:

@@snip [ValuesRouteV2.scala]($src$/org/knora/webapi/routing/v2/ValuesRouteV2.scala) { #specify-response-schema }

## Generating Other RDF Formats

`RouteUtilV2.runRdfRoute` implements
@extref[HTTP content negotiation](rfc:7231#section-5.3.2), and converts JSON-LD
responses into [Turtle](https://www.w3.org/TR/turtle/)
or [RDF/XML](https://www.w3.org/TR/rdf-syntax-grammar/) as appropriate.
