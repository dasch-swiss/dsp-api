# SHACL shapes in dsp-api

The SHACL shape files validate RDF data and ontologies. They live in
`modules/webapi/src/main/resources/shacl/`. `ProjectMigrationImportValidator` runs them
for project export and import through the in-JVM `ShaclValidator` (TopBraid over Jena, no
network). These conventions keep the shapes readable, correctly placed, and testable.

## Shape files and layering

- `data-shapes.ttl` holds constraints that always apply. It loads in every validation mode.
- `migration-shapes.ttl` and `bulk-import-shapes.ttl` layer mode-specific constraints on
  top. The two never load together. Each reopens a node shape by IRI and adds constraints.

Placement is a decision, not a version step. When you add or change a knora-base value
predicate, decide where its constraint belongs. Put it in `data-shapes.ttl` when it always
holds. Put it in a layer when it is mode-specific. This decision is independent of the
knora-base version bump.

## Documenting shapes

- A complex-constraint shape carries an `sh:description`. Complex means it uses `sh:or`,
  `sh:not`, `sh:xone`, a conditional (`sh:hasValue`-keyed) constraint, or `sh:sparql`.
- A plain shape needs no description. Plain means one `sh:path` with `sh:datatype`,
  `sh:class`, or a cardinality.
- Document shapes with `sh:description`. Do not use `#` comments for shape documentation.

## Writing constraints

### Declarative first

Prefer `sh:property`, `sh:or`, and `sh:not`. Reserve `sh:sparql` for rules that declarative
SHACL cannot express. `data-shapes.ttl` uses no SPARQL today.

### Forbid one case with sh:not

To disallow a predicate for a single case, forbid the pair with `sh:not`. Do not allowlist
the permitted cases with `sh:or`, and leave unmarked or legacy values valid. For example, an
`UnformattedText` value must not carry `knora-base:valueHasXml`:

```turtle
sh:not [
  a sh:NodeShape ;
  sh:description "An UnformattedText value carries no markup, so it must not have valueHasXml." ;
  sh:property [ sh:path knora-base:hasTextValueType ; sh:hasValue knora-base:UnformattedText ] ;
  sh:property [ sh:path knora-base:valueHasXml ; sh:minCount 1 ;
    sh:message "An UnformattedText TextValue instance must not carry knora-base:valueHasXml" ] ;
] .
```

The `TextValue-Shape` in `bulk-import-shapes.ttl` shows the `sh:or` form, where each branch
pins a type with `sh:hasValue`. Use `sh:or` when several distinct cases are each valid, not
to express a single prohibition.

### Do not require presence prematurely

Do not add `sh:minCount 1` for a predicate the write path does not yet always populate.
Restrict invalid combinations only. Add the presence requirement once the write path
guarantees the value and a backfill covers existing data.

### Constraint granularity and messages

- Combine several simple constraints in one `sh:property` when one `sh:message` still names
  the problem, for example a cardinality plus `sh:datatype`, or a cardinality plus
  `sh:class`.
- Split complex validation into separate property shapes, each with its own message.
- Put `sh:message` on the specific property or constraint shape that fails. Never put it on
  the node shape. A node-level message bleeds onto every sibling constraint.

The test for all three: a reader identifies the violation from the message and the data
alone, without opening the shapes file.

## SPARQL-based shapes

Where a shape needs SPARQL, for example in `ontology-shapes.ttl`:

- Project `?this`, not `$this`.
- Declare prefixes through `sh:prefixes`. Do not write inline `PREFIX` lines.

## data-shapes.ttl is not closed

Do not use `sh:closed` on `data-shapes.ttl` shapes. Values carry shared and inferred
predicates, which a closed shape would reject.

## Testing shapes

Every new or changed constraint gets a `ShaclValidator` regression spec. Cover positive and
negative cases. A negative case must be self-verifying: it fails the assertion when the
shape is absent. The pattern is `ValueHasXmlShaclRegressionSpec`
(`modules/webapi/src/test/scala/org/knora/webapi/slice/export/domain/`). It loads the real
`knora-base.ttl` and `shacl/data-shapes.ttl` from the classpath and runs the same engine as
the import validator.

## Keep internal predicates out of the API

An internal value predicate must not surface in the client-facing `knora-api` ontology. Add
it to `internalPropertiesToRemove` in both `KnoraBaseToApiV2ComplexTransformationRules` and
`KnoraBaseToApiV2SimpleTransformationRules`. `OntologyFormatsE2ESpec` must stay green.

## Related

- `docs/development/dsp-api-conventions.md` § Ontology Conventions
- `docs/05-internals/development/updating-repositories.md` § Changing the Built-in Ontologies
- Shape files: `modules/webapi/src/main/resources/shacl/`
