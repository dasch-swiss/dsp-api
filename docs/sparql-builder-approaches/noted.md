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

## Benchmarks Review Status

All 6 benchmarks have been updated in the document with agreed conventions:

| Benchmark | Status | Notes |
|-----------|--------|-------|
| 1: Simple SELECT | Updated, reviewed | All conventions applied |
| 2: ASK with UNION | Updated, reviewed | All conventions applied |
| 3: DeletePropertyQuery | Updated, reviewed | Intro text, full IRIs, prefix-derived IRIs, builder multi-line variant |
| 4: InsertValueQueryBuilder | Updated, reviewed | Intro text, `Iri.unsafeFrom`, prefix-derived IRIs, builder multi-line variant |
| 5: SearchQueries/Lucene | Updated, reviewed | Major rewrite: PropertyPath type, Fragments.jenaTextQuery combinator |
| 6: addValueVersion | Updated, reviewed | Intro text, `Iri.unsafeFrom`, `Literal.stringEscaped`/`typedEscaped`, prefix-derived IRIs, builder multi-line variant |

## Notes Section Updates

- [x] Updated "Known issues" list — `Fragment.raw` issue marked resolved via PropertyPath/jenaTextQuery
- [ ] Verify "When to use which" table still accurate after all benchmarks reviewed

## Items Agreed and Applied

| Convention | Status |
|-----------|--------|
| `Iri.unsafeFrom` naming | Applied in all benchmarks |
| `Prefix.unsafeFrom` + `prefix.unsafeIri` | Applied in vocabulary + all benchmarks |
| `Literal.stringEscaped` / `Literal.typedEscaped` | Applied in all benchmarks |
| `sp"..."` alias | Documented in Notes, not yet used in examples |
| Builder middle-ground variant | Applied in all benchmarks |
| `Sparql` entry point (not `SparqlQuery`) | Applied in all benchmarks |
| `LanguageTag` opaque type | Documented in Notes, not yet used in examples |
| `PropertyPath` type | Applied in Benchmark 5, documented in Notes |
| `Fragments.jenaTextQuery` combinator | Applied in Benchmark 5, documented in Notes |
