<!---
 * Copyright © 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
-->

# JSON-LD Parsing and Formatting

## JsonLDUtil

Knora provides a utility object called `JsonLDUtil`, which wraps the
[titanium-json-ld Java library](https://github.com/filip26/titanium-json-ld), and parses JSON-LD text to a
Knora data structure called `JsonLDDocument`. These classes provide commonly needed
functionality for extracting and validating data from JSON-LD documents, as well
as for constructing new documents.

## Parsing JSON-LD

A route that expects a JSON-LD request must first parse the JSON-LD using
`JsonLDUtil` . For example, this is how `ValuesRouteV2` parses a JSON-LD request to create a value:

````scala
post {
            entity(as[String]) { jsonRequest =>
                requestContext => {
                    val requestDoc: JsonLDDocument = JsonLDUtil.parseJsonLD(jsonRequest)
````

The result is a `JsonLDDocument` in which all prefixes have been expanded
to full IRIs, with an empty JSON-LD context.

The next step is to convert the `JsonLDDocument` to a request message that can be
sent to the Knora responder that will handle the request.

```scala
val requestMessageFuture: Future[CreateValueRequestV2] = for {
                        requestingUser <- getUserADM(requestContext)
                        requestMessage: CreateValueRequestV2 <- CreateValueRequestV2.fromJsonLD(
                            requestDoc,
                            apiRequestID = UUID.randomUUID,
                            requestingUser = requestingUser,
                            responderManager = responderManager,
                            storeManager = storeManager,
                            settings = settings,
                            log = log
                        )
                    } yield requestMessage
```

This is done in a `Future`, because the processing of JSON-LD input
could in itself involve sending messages to responders.

Each request message case class (in this case `CreateValueRequestV2`) has a companion object
that implements the `KnoraJsonLDRequestReaderV2` trait:

```scala
/**
  * A trait for objects that can generate case class instances based on JSON-LD input.
  *
  * @tparam C the type of the case class that can be generated.
  */
trait KnoraJsonLDRequestReaderV2[C] {
    /**
      * Converts JSON-LD input into a case class instance.
      *
      * @param jsonLDDocument   the JSON-LD input.
      * @param apiRequestID     the UUID of the API request.
      * @param requestingUser   the user making the request.
      * @param responderManager a reference to the responder manager.
      * @param storeManager     a reference to the store manager.
      * @param settings         the application settings.
      * @param log              a logging adapter.
      * @param timeout          a timeout for `ask` messages.
      * @param executionContext an execution context for futures.
      * @return a case class instance representing the input.
      */
    def fromJsonLD(jsonLDDocument: JsonLDDocument,
                   apiRequestID: UUID,
                   requestingUser: UserADM,
                   responderManager: ActorRef,
                   storeManager: ActorRef,
                   settings: KnoraSettingsImpl,
                   log: LoggingAdapter)(implicit timeout: Timeout, executionContext: ExecutionContext): Future[C]
}
```

This means that the companion object has a method `fromJsonLD` that takes a
`JsonLDDocument` and returns an instance of the case class. The `fromJsonLD` method
can use the functionality of the `JsonLDDocument` data structure for extracting
and validating the content of the request. For example, `JsonLDObject.requireStringWithValidation`
gets a required member of a JSON-LD object, and validates it using a function
that is passed as an argument. Here is an example of getting and validating
a `SmartIri`:

```scala
for {
      valueType: SmartIri <- Future(jsonLDObject.requireStringWithValidation(JsonLDConstants.TYPE, stringFormatter.toSmartIriWithErr))
```

The validation function (in this case `stringFormatter.toSmartIriWithErr`) has to take
two arguments: a string to be validated, and a function that that throws an exception
if the string is invalid. The return value of `requireStringWithValidation` is the
return value of the validation function, which in this case is a `SmartIri`. If
the string is invalid, `requireStringWithValidation` throws `BadRequestException`.

It is also possible to get and validate an optional JSON-LD object member:

```scala
val maybeDateValueHasStartEra: Option[DateEraV2] = jsonLDObject.maybeStringWithValidation(OntologyConstants.KnoraApiV2Complex.DateValueHasStartEra, DateEraV2.parse)
```

Here `JsonLDObject.maybeStringWithValidation` returns an `Option` that contains
the return value of the validation function (`DateEraV2.parse`) if it was given,
otherwise `None`.

## Returning a JSON-LD Response

Each API response is represented by a message class that extends
`KnoraJsonLDResponseV2`, which has a method `toJsonLDDocument` that specifies
the target ontology schema. The implementation of this method constructs a `JsonLDDocument`,
in which all object keys are full IRIs (no prefixes are used), but in which
the JSON-LD context also specifies the prefixes that will be used when the
document is returned to the client. The function `JsonLDUtil.makeContext`
is a convenient way to construct the JSON-LD context.

Since `toJsonLDDocument` has to return an object that uses the specified
ontology schema, the recommended design is to separate schema conversion as much
as possible from JSON-LD generation. As a first step, schema conversion (or at the very
least, the conversion of Knora type IRIs to the target schema) can be done via an
implementation of `KnoraReadV2`:

```scala
/**
  * A trait for read wrappers that can convert themselves to external schemas.
  *
  * @tparam C the type of the read wrapper that extends this trait.
  */
trait KnoraReadV2[C <: KnoraReadV2[C]] {
    this: C =>
    def toOntologySchema(targetSchema: ApiV2Schema): C
}
```

This means that the response message class has the method `toOntologySchema`, which returns
a copy of the same message, with Knora type IRIs (and perhaps other content) adjusted
for the target schema. (See [Smart IRIs](smart-iris.md) on how to convert Knora
type IRIs to the target schema.)

The response message class could then have a private method called `generateJsonLD`, which
generates a `JsonLDDocument` that has the correct structure for the target schema, like
this:

````scala
private def generateJsonLD(targetSchema: ApiV2Schema, settings: KnoraSettingsImpl, schemaOptions: Set[SchemaOption]): JsonLDDocument
````

This way, the implementation of `toJsonLDDocument` can call `toOntologySchema`,
then construct a `JsonLDDocument` from the resulting object. For example:

```scala
    override def toJsonLDDocument(targetSchema: ApiV2Schema, settings: KnoraSettingsImpl, schemaOptions: Set[SchemaOption] = Set.empty): JsonLDDocument = {
        toOntologySchema(targetSchema).generateJsonLD(
            targetSchema = targetSchema,
            settings = settings,
            schemaOptions = schemaOptions
        )
    }
```

## Selecting the Response Schema

Most routes complete by calling `RouteUtilV2.runRdfRouteWithFuture`, which calls
the response message's `toJsonLDDocument` method. The `runRdfRouteWithFuture` function
has a parameter that enables the route to select the schema that should be used in
the response. It is up to each route to determine what the appropriate response schema
should be. Some routes support only one response schema. Others allow the client
to choose. To use the schema requested by the client, the route can call
`RouteUtilV2.getOntologySchema`:

```scala
RouteUtilV2.runRdfRouteWithFuture(
    requestMessageF = requestMessageFuture,
    requestContext = requestContext,
    settings = settings,
    responderManager = responderManager,
    log = log,
    targetSchema = targetSchema,
    schemaOptions = schemaOptions
)
```

If the route only supports one schema, it can specify the schema directly instead:

```scala
RouteUtilV2.runRdfRouteWithFuture(
    requestMessageF = requestMessageFuture,
    requestContext = requestContext,
    settings = settings,
    responderManager = responderManager,
    log = log,
    targetSchema = ApiV2Complex,
    schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
)
```

## Generating Other RDF Formats

`RouteUtilV2.runRdfRouteWithFuture` implements
[HTTP content negotiation](https://tools.ietf.org/html/rfc7231#section-5.3.2). After
determining the client's preferred format, it asks the `KnoraResponseV2` to convert
itself into that format. `KnoraResponseV2` has an abstract `format` method, whose implementations
select the most efficient conversion between the response message's internal
representation (which could be JSON-LD or Turtle) and the requested format.
