# Text-value-type parity contract

Three write paths persist `knora-base:hasTextValueType` on every text value. They derive the IRI from
one shared mapping, so the paths cannot diverge. A change to the mapping changes every path at once.

## The shared mapping

`TextValueType.hasTextValueTypeIri`
(`modules/webapi/src/main/scala/org/knora/webapi/messages/v2/responder/valuemessages/ValueMessagesV2.scala`)
maps a `TextValueType` to the internal `knora-base` IRI. It maps `UnformattedText`, `FormattedText`,
and `CustomFormattedText`. It fails loud on `UndefinedTextType`, which no write path can produce.
`TextValueTypeSpec` pins the mapping.

## The three write sites

Each site converts its own input to a `TextValueType`, then calls the shared mapping:

1. **v2 resource create** — `ResourcesRepoLive`: `buildFormattedTextValuePatterns` (converts
   `FormattedTextValueType`) and the `UnformattedTextValueInfo` case in `buildTypeSpecificValuePattern`.
2. **v2 add value** — `InsertValueQueryBuilder.textValueTypeIri` (holds a `TextValueType`).
3. **v3 bulk import** — `OntologyTransformer.addTextValueType` (maps the presence of `textValueAsXml`
   to `FormattedText` or `UnformattedText`).

The bulk-import site never produces `CustomFormattedText`: `OntologyTransformer.rejectCustomMapping`
rejects a non-standard mapping before write (REQ-6.2).

## The read path is separate

`ValueMessagesV2` (the v2 JSON-LD response) maps to external `knora-api` IRIs, not the internal
`knora-base` IRIs of the write paths. It is the only path that reaches `UndefinedTextType`, so it maps
that case rather than failing. It does not use the shared mapping.

## Changing the mapping

- Change `TextValueType.hasTextValueTypeIri` once. Every write path follows.
- Add a case to the shared mapping for a new `TextValueType`. Fail loud on an unmapped case. Do not
  drop the triple. A silent drop diverges from the read path and breaks the round trip.
- Update `TextValueTypeSpec` and the affected write-path goldens at the same time.

## Related

- `dsp-api-value-types.md` — value object construction.
- The same parity rule holds for scalar-literal datatypes. `OntologyTransformer.canonicalizeScalarLiterals`
  re-types the bulk-import payload to match the create path, and every write path formats a decimal
  through `ValuesValidator.canonicalDecimal`.
