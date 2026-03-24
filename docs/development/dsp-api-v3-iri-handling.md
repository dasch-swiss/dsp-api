# V3 API - IRI Handling

How IRI parameters are represented, validated, and passed in v3 endpoints.

## Two Categories of IRIs

The codebase has two fundamentally different kinds of IRI:

### 1. Simple IRIs - same internal and external representation

These are IRIs whose string form never changes between API schemas and internal storage.
Typed value objects already exist for most of them:

- `ProjectIri` (`http://rdfh.ch/projects/0001`)
- `UserIri` (`http://rdfh.ch/users/...`)
- `GroupIri` (`http://rdfh.ch/groups/...`)
- `PermissionIri` (`http://rdfh.ch/permissions/...`)
- `ListIri`, `ListNodeIri`, etc.

**In endpoint definitions** use the typed value object directly - Tapir picks up the codec
from the companion's `given Codec[String, T, TextPlain]`:

```scala
val getProject = base
  .secured(...)
  .get
  .in("v3" / "projects" / path[ProjectIri].name("projectIri"))
  .out(jsonBody[ProjectResponse])
```

No conversion is needed in the RestService - the value object is used as-is.

### 2. SmartIri-backed IRIs - different internal and external representation

Ontology IRIs, resource class IRIs, resource IRIs, value IRIs  and property IRIs are backed by `SmartIri`.
Their external (API) representation depends on server configuration (complex schema) and differs from the internal representation stored in the triplestore.

Example domain types: `KnoraIris.OntologyIri`, `KnoraIris.ResourceClassIri`, `KnoraIris.PropertyIri`
- these wrap a `SmartIri` and implement the `KnoraIri` trait.

**In v3 endpoints** these IRIs are accepted as `IriDto` (a simple validated-IRI string wrapper).
The conversion from `IriDto` to the domain type happens in the **RestService** layer using `IriConverter`.

#### Endpoint definition - accept as `IriDto`

```scala
private val ontologyIriPath      = path[IriDto].name("ontologyIri")
private val resourceClassIriPath = path[IriDto].name("resourceClassIri")

val getClass = base
  .secured(...)
  .get
  .in(ontologiesBase / ontologyIriPath / "classes" / resourceClassIriPath)
  .out(jsonBody[ClassResponse])
```

`IriDto` validates that the string is a syntactically valid IRI (`Iri.isIri`), but does not resolve schema differences - that is the RestService's job.

#### RestService - convert with `IriConverter`

The v3 API only accepts the **complex** (external) representation of SmartIri-backed IRIs, aka. `ApiV2Complex` schema.
Use the `*ApiV2Complex` variants on `IriConverter` - they reject IRIs that are not in the API v2 complex schema with a descriptive error.

**Example 1: converting and validating a class IRI is complex**

```scala
final case class OntologyMappingRestService(
  private val iriConverter: IriConverter,
  ...
) {
  def getClass(user: User)(ontologyIriDto: IriDto, classIriDto: IriDto): IO[V3ErrorInfo, ClassResponse] =
    for {
      classIri <- iriConverter.asResourceClassIriApiV2Complex(classIriDto.value)
                    .mapError(BadRequest(_))
      ...
    } yield response
}
```

**Example 2: converting and validating an ontology IRI is complex with additional validations**

```scala
def getOntology(user: User)(name: OntologyName, ontologyIriDto: IriDto): IO[V3ErrorInfo, OntologyResponse] =
  for {
    ontologyIri <- iriConverter.asOntologyIriApiV2Complex(ontologyIriDto.value)
    // logging a warning for "error handling", use `ZIO.fail`` if the response should give feedback to the user
    _           <- ZIO.logWarning("The name is not expected.").unless(ontologyIri.ontologyName == ontologyName)
    ...
  } yield response
```

V3 endpoints use the v3 error model (`org.knora.webapi.slice.api.v3.V3ErrorInfo` types like `BadRequest`). 
The `*ApiV2Complex` `IriConverter` methods return `IO[String, T]` - map the `String` error channel to `BadRequest` with `.mapError(BadRequest(_))`.

If a client sends an internal IRI (e.g. `http://www.knora.org/ontology/0001/anything`) instead of the complex form (e.g. `http://0.0.0.0:3333/ontology/0001/anything/v2`), the `*ApiV2Complex` method returns `Left("")` which is mapped to `BadRequest` → HTTP 400.

Key `IriConverter` methods for SmartIri-backed types:

| Method | Returns | Validates complex? | Use for |
|--------|---------|-------------------|---------|
| `asOntologyIriApiV2Complex(iri)` | `IO[String, OntologyIri]` | Yes | Ontology IRIs (v3) |
| `asResourceClassIriApiV2Complex(iri)` | `IO[String, ResourceClassIri]` | Yes | Class IRIs (v3) |
| `asPropertyIriApiV2Complex(iri)` | `IO[String, PropertyIri]` | Yes | Property IRIs (v3) |

For v3 endpoints, **always prefer the `*ApiV2Complex` variants** to reject non-complex IRIs early. 
If necessary add new conversions to the `IriConverter`.

## URL Encoding

As stated in ADR-0010, we use existing IRIs as entity identifiers. 
Since IRIs contain characters that are not valid in URL path segments (`:`, `/`), clients MUST URL-encode IRI values when passing them as path or query parameters. 
Tapir handles decoding automatically.

Document this in the `.description(...)` of every IRI path/query parameter: `"Must be URL-encoded."`

## Anti-patterns

```scala
// BAD: raw String - no validation, no type safety
.in(ontologiesBase / path[String]("ontologyIri"))

// BAD: using a SmartIri-backed domain type directly in endpoint definition
// (the representation depends on server config - use IriDto instead)
.in(ontologiesBase / path[OntologyIri].name("ontologyIri"))

// BAD: converting IriDto in the endpoint definition layer
// (conversion belongs in the RestService)
```

## Summary

| IRI category | Endpoint type | RestService conversion |
|---|---|---|
| Simple (same internal/external) | Typed value object (`ProjectIri`, `UserIri`, ...) | None needed |
| SmartIri-backed (different internal/external) | `IriDto` | `IriConverter.as*` methods |

## Reference

- [ADR-0009: API v3](../05-internals/design/adr/ADR-0009-api-v3.md) - v3 API overview
- [ADR-0010: API v3 basics](../05-internals/design/adr/ADR-0010-api-v3-basics.md) - naming, pagination, errors
- [dsp-api conventions](dsp-api-conventions.md) - `StringValue` pattern, Tapir codecs
- `AdminPathVariables.scala` - reusable path variable definitions for simple IRIs
- `IriDto` - defined in `slice/api/v2/Models.scala`
- `IriConverter` - defined in `slice/common/service/IriConverter.scala`
- `KnoraIris` - SmartIri-backed domain types in `slice/common/KnoraIris.scala`
