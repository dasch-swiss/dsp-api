# JSON-LD 

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
