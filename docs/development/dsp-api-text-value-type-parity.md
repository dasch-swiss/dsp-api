# Text-value-type parity contract

Three write paths persist `knora-base:hasTextValueType` on every text value. They derive the
IRI independently from different inputs, but they must agree for the same logical value. A change
to one path must change the other two, or the paths diverge silently.

## The three sites

1. **v2 resource create** — `ResourcesRepoLive.buildFormattedTextValuePatterns`
   (`modules/webapi/src/main/scala/org/knora/webapi/slice/resources/repo/service/ResourcesRepoLive.scala`).
   Keys on `FormattedTextValueType`.
2. **v2 add value** — `InsertValueQueryBuilder.textValueTypeIri`
   (`modules/webapi/src/main/scala/org/knora/webapi/slice/resources/repo/service/value/queries/InsertValueQueryBuilder.scala`).
   Keys on `TextValueType`.
3. **v3 bulk import** — `OntologyTransformer.addTextValueType`
   (`modules/webapi/src/main/scala/org/knora/webapi/slice/ontology/OntologyTransformer.scala`).
   Keys on the presence of `textValueAsXml`.

## The contract

The three sites map to the same target IRIs: `knora-base:UnformattedText`,
`knora-base:FormattedText`, `knora-base:CustomFormattedText`.

- Add or change a text-value-type mapping. Then update all three sites.
- Update the tests and goldens of all three sites at the same time.
- Fail loud on an unmapped case. Do not drop the triple. A silent drop diverges from the other
  two paths.

## Related

- `dsp-api-value-types.md` — value object construction.
- The same parity rule holds for scalar-literal datatypes. `OntologyTransformer.canonicalizeScalarLiterals`
  re-types the bulk-import payload to match the create path (`InsertValueQueryBuilder`).
