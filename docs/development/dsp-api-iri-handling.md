# IRI Handling

Rules for constructing typed IRI value objects (e.g. `ResourceIri`, `ValueIri`, `ProjectIri`, `UserIri`) from raw strings across the codebase.

These rules are **universal** — they apply to v2, v3, admin, and any other API slice.
For v3-specific endpoint wiring (how SmartIri-backed IRIs cross the HTTP boundary), see
[dsp-api-v3-iri-handling.md](dsp-api-v3-iri-handling.md).

## The rules

A typed IRI value object has a safe constructor `Xxx.from(String): Either[String, Xxx]`
and an unsafe constructor `Xxx.unsafeFrom(String): Xxx` that throws on invalid input.

### 1. Never call `unsafeFrom` in responders or RestServices

Input from the outside world must never bypass validation.
`unsafeFrom` is reserved for cases where the invariant is already guaranteed (tests, static
constants, values round-tripping out of a typed source). In request handling code it is a
latent crash.

```scala
// BAD — calling unsafe direct
ResourceIri.unsafeFrom(request.resourceIri)
ZIO.attempt(ReousrceIri.unsafeFrom(request.resourceIri))

// GOOD
ZIO.fromEither(ResourceIri.from(request.resourceIri)).mapError(BadRequestException.apply)
```

### 2. Make conversion ZIO effectful and map to the right failure type

Convert via `ZIO.fromEither(Xxx.from(...))` and `mapError` to the failure type that fits the layer.

- **RestService** → `BadRequestException` (maps to HTTP 400). For v3 endpoints, map to the v3
  error model, e.g. `.mapError(BadRequest(_))` — see the v3 doc.
- **Responder** → same as RestService when the IRI came from user input: `BadRequestException`.
- **Repo** → `InconsistentRepositoryDataException` (see rule 5).
- **Service** → a domain error that matches the service's error channel (see rule 4).

```scala
// RestService (v2)
ZIO.fromEither(ResourceIri.from(resourceIri.value)).mapError(BadRequestException(_))

// RestService (v3)
ZIO.fromEither(ResourceIri.from(resourceIri.value)).mapError(BadRequest(_))
```

### 3. Never `.die` on a `ResourceIri.from` failure

`ZIO.attempt(ResourceIri.unsafeFrom(s)).orDie` turns a recoverable validation error into a fatal
defect. A malformed IRI from a client is a 400, not a 500. Always `mapError` to a recoverable
failure.

```scala
// BAD
ZIO.fromEither(ResourceIri.from(s)).map(Exception(_)).orDie

// GOOD
ZIO.fromEither(ResourceIri.from(s)).mapError(BadRequestException.apply)
```

### 4. In services, use a suitable domain error — ideally convert earlier

Services should receive typed IRIs, not raw strings. The conversion belongs in the API layer
(RestService / endpoint handler) so the service method signature already expresses the invariant.

```scala
// Preferred — service takes the typed value
def findResource(iri: ResourceIri): IO[ResourceError, Resource]

// If conversion must happen inside the service, map to a domain error — not a raw Throwable
ZIO.fromEither(ResourceIri.from(s)).mapError(msg => InvalidResourceIri(msg))
```

Avoid leaking `BadRequestException` out of a service if the service's error channel is a
domain ADT — pick (or add) an appropriate variant instead.

### 5. In repos, map to `InconsistentRepositoryDataException`

IRIs read back from the triplestore are expected to be valid. If validation fails there, the
repository contents disagree with the schema — that is a data-integrity problem, not a
client error.

```scala
// Repo row parsing
resIri <- ZIO.fromEither(ResourceIri.from(row.getRequired("resource")))
            .mapError(InconsistentRepositoryDataException(_))
```

`unsafeFrom` in repo code is acceptable only when the value was just produced by a
constructor that guarantees the invariant (e.g. `ResourceIri.makeNew(shortcode)`).
Parsing arbitrary triplestore output with `unsafeFrom` hides the data-integrity signal behind a
generic defect — prefer `from` + `InconsistentRepositoryDataException`.

## Summary by layer

| Layer | Preferred | Error mapping |
| --- | --- | --- |
| Endpoint / RestService (v2) | `ResourceIri.from` | `BadRequestException` |
| Endpoint / RestService (v3) | `ResourceIri.from` (or `IriConverter` for SmartIri-backed types) | `BadRequest` (v3 error model) |
| Responder | receive typed IRI; if unavoidable, `from` + `BadRequestException` | `BadRequestException` |
| Service | receive typed IRI; if unavoidable, `from` + domain error | service's domain error ADT |
| Repo (reading triplestore) | `ResourceIri.from` | `InconsistentRepositoryDataException` |
| Repo (value just constructed internally) | `unsafeFrom` acceptable | — |
| Tests / static constants | `unsafeFrom` acceptable | — |

## Reference

- [dsp-api-v3-iri-handling.md](dsp-api-v3-iri-handling.md) — v3 endpoint wiring, `IriDto`, `IriConverter`
- [dsp-api-value-types.md](dsp-api-value-types.md) — `StringValue` / `Value[A]` pattern
- `ResourceIri` — `slice/common/ResourceIri.scala`
- `InconsistentRepositoryDataException` — `dsp/errors/Errors.scala`
