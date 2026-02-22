# Interpolated Template — Open Items

Items identified during the design review walkthrough that still need to be addressed.

## Design Decisions Pending

### Error handling design
The safe constructors (`Iri.from`, `Variable.from`, `Prefix.from`, `LanguageTag.from`) return `Either[String, T]`. Need a cohesive design:
- Should there be a dedicated error type (e.g., `SparqlBuilderError`) instead of plain strings?
- Should errors compose (e.g., building a fragment from multiple potentially-invalid values)?
- How does this interact with ZIO at the call site in `webapi`?

### PREFIX usage in template bodies
Template style uses `PREFIX $kb` in the header, then bare `knora-base:lastModificationDate` in the body. The library can't verify at construction time that the prefix is declared. Options:
- Accept bare prefixed names as raw template text (current approach) — simple, reads like SPARQL
- Always use interpolated IRIs like `$kbLastMod` in the body — safer, more verbose
- Validate prefix usage at render time — complex, may not be worth it

## Benchmarks Not Yet Reviewed

- **Benchmark 3**: Updated in document but not yet discussed in detail
- **Benchmark 4**: InsertValueQueryBuilder — needs `Iri.unsafeFrom`, prefix-derived IRIs, builder middle-ground variant
- **Benchmark 5**: SearchQueries / Lucene — needs PropertyPath type and Lucene combinator design (per user decision), `Literal.stringEscaped`, prefix-derived IRIs
- **Benchmark 6**: addValueVersion — needs `Iri.unsafeFrom`, `Literal.stringEscaped`, `Literal.typedEscaped`, prefix-derived IRIs, builder middle-ground variant

## Code Updates Still Needed in Document

### Benchmarks 4-6: Apply agreed conventions
- `Iri.trusted(...)` → `Iri.unsafeFrom(...)` or prefix-derived `prefix.unsafeIri("...")`
- `Literal.string(...)` → `Literal.stringEscaped(...)`
- `Literal.typed(...)` → `Literal.typedEscaped(...)`
- `Literal.int(...)` → `Literal.int(...)` (unchanged — type-safe)
- `Literal.bool(...)` → `Literal.bool(...)` (unchanged — type-safe)
- Add builder middle-ground variant (multi-line WHERE fragment) to each benchmark
- Use `Prefix` objects in builder `.prefixes(...)` calls

### Benchmark 5 specifically
- Design and showcase `PropertyPath` type (for `rdfs:subClassOf*`)
- Design and showcase Lucene combinator (for `text#query`)
- Replace `Fragment.raw(s"...")` anti-pattern with typed alternatives

## Notes Section Updates

- Update "When to use which" table if needed after remaining benchmarks
- Update "Known issues" list — some issues are now resolved by design decisions
- Document the `sp"..."` alias convention more prominently

## Items Agreed But Not Yet Applied Everywhere

| Convention | Status |
|-----------|--------|
| `Iri.unsafeFrom` naming | Applied in Benchmarks 1-3, pending 4-6 |
| `Prefix.unsafeFrom` + `prefix.unsafeIri` | Applied in vocabulary + Benchmarks 1-3, pending 4-6 |
| `Literal.stringEscaped` / `Literal.typedEscaped` | Applied in Benchmarks 2-3, pending 4-6 |
| `sp"..."` alias | Documented in Notes, not yet used in examples |
| Builder middle-ground variant | Applied in Benchmarks 1-3, pending 4-6 |
| `Sparql` entry point (not `SparqlQuery`) | Applied in Benchmarks 1-3, pending 4-6 |
| `LanguageTag` opaque type | Documented in Notes, not yet used in examples |
