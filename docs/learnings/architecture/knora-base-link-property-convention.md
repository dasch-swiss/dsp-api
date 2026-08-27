---
title: "Link Value Property Naming Convention in knora-base"
date: 2026-02-22
category: "architecture"
component: "ontology"
module: "knora-base"
problem_type: "architectural_pattern"
severity: "low"
symptoms: "Conditional branches for link vs. non-link properties appear in virtually every code path that touches properties, values, or SPARQL queries. The duality must be considered in every new feature, query, or refactoring that involves properties."
root_cause: "architectural_complexity"
tags: ["knora-base", "link-properties", "link-value-properties", "naming-convention", "sparql", "ontology-design", "reification", "rdf"]
---

# Link Value Property Naming Convention in knora-base

## Problem

The link/non-link property duality is one of the major sources of complexity in the DSP-API codebase. Every code path that touches properties or values — resource creation, value updates, property deletion, ontology validation, permission checks, cardinality management, SPARQL query construction — must account for the possibility that a property is a link property with a paired "Value" variant. This manifests as `Option[PropertyIri]` parameters, `isLinkProp` conditionals, and dual-branch logic throughout the system. It is not a one-time concern to learn and move on from; it is an ongoing architectural constraint that must be actively considered in every new feature, query, or refactoring that involves properties.

## Explanation

### Regular Value Properties vs. Link Properties

In knora-base, resources hold data via properties. There are two fundamentally different kinds:

**Regular value properties** (subproperties of `kb:hasValue`): A single property (e.g., `hasColor`) points from a resource to a reified value object (e.g., a `ColorValue`). The value object carries metadata: creation date, permissions, delete status. There is exactly one property involved.

```
resource1 ex:hasColor colorValue1 .

colorValue1 a kb:ColorValue ;
    kb:valueHasColor "#FF0000" ;
    kb:hasPermissions "CR knora-admin:Creator" .
```

**Link properties** (subproperties of `kb:hasLinkTo`): Two coupled properties exist for every link:

- The **link property** (e.g., `hasOtherThing`) points directly from the source resource to the target resource. This is the actual RDF triple that makes the link queryable.
- The **link value property** (e.g., `hasOtherThingValue`) points from the source resource to a `LinkValue` reification object that carries metadata about the link (creator, permissions, reference count).

```
painting1 ex:isInCollection pompidou .              # direct link
painting1 ex:isInCollectionValue linkValue1 .        # link to reification

linkValue1 a kb:LinkValue ;
    kb:valueHasRefCount 1 ;
    kb:hasPermissions "CR knora-admin:Creator" .
```

### The Naming Convention

The link value property name is **mechanically derived** from the link property name by appending `"Value"`:

| Link property | Link value property |
|---|---|
| `hasOtherThing` | `hasOtherThingValue` |
| `isInCollection` | `isInCollectionValue` |
| `isPartOf` | `isPartOfValue` |
| `hasStandoffLinkTo` | `hasStandoffLinkToValue` |

This convention is **enforced**: non-link value properties must not end in `"Value"`, ensuring the naming rule is unambiguous.

### Why This Is a Major Source of Complexity

The dual-property convention is not isolated to one part of the codebase — it is a pervasive architectural constraint. Virtually every code path that operates on properties or values must branch on whether the property is a link property:

- **Ontology management**: Creating a link property auto-generates the paired link value property. Deleting requires removing both. Cardinalities must match between the pair.
- **Resource creation/update**: When creating a link between resources, both the direct link triple and the `LinkValue` reification must be written.
- **Value versioning**: Updating a link creates a new `LinkValue` version (with updated reference count) and updates the direct link triple.
- **Permission checks**: Both the link property and its value property carry permissions that must be checked.
- **SPARQL queries**: Any query touching link properties typically needs conditional patterns for the paired property. This manifests as `Option[PropertyIri]` parameters and conditional fragment composition.
- **Standoff links**: Text values with standoff markup that reference other resources automatically create/update links with reference counting via `LinkValue`.

The pattern in code is consistently: check `isLinkProp`, derive the paired property IRI (append `"Value"`), handle both. This branching logic is not optional or edge-case — it is load-bearing and must be present wherever properties are manipulated.

## Key Code Locations

- **`SmartIri` conversion**: `StringFormatter.scala` — `fromLinkPropToLinkValueProp` / `fromLinkValuePropToLinkProp` (appends/strips `"Value"`)
- **`PropertyIri` wrapper**: `KnoraIris.scala` — `PropertyIri.fromLinkPropToLinkValueProp` / `fromLinkValuePropToLinkProp`
- **Property metadata flags**: `OntologyMessagesV2.scala` — `ReadPropertyInfoV2.isLinkProp`, `isLinkValueProp`, `linkValueProperty: Option[PropertyIri]`
- **Auto-generation**: `OntologyHelpers.scala` — `linkPropertyDefToLinkValuePropertyDef` (creates the paired property definition when a link property is defined)
- **Ontology validation**: `OntologyHelpers.scala` — enforces "within the cardinalities of a class, there must be a link value property for each link property and vice versa"
- **Example SPARQL query**: `DeletePropertyQuery.scala` — `linkValuePropertyIri: Option[PropertyIri]` conditionally adds patterns for the paired property

## Prevention

- When writing new code that handles properties, always check whether link properties need special handling (look for the `isLinkProp` / `Option[PropertyIri]` pattern in similar existing code).
- When constructing SPARQL queries for property operations, test with both link and non-link properties to ensure the conditional patterns work correctly.
- The `ReadPropertyInfoV2.linkValueProperty` convenience method returns `Some(derivedIri)` for link properties and `None` otherwise — use this rather than manually deriving the paired IRI.

## References

- `docs/02-dsp-ontologies/knora-base.md` (lines 391-521) — canonical documentation of the link property / link value property convention
- `docs/05-internals/design/principles/triplestore-updates.md` (lines 106-181) — how LinkValues are versioned and deleted, including reference counting
