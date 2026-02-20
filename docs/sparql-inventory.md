# SPARQL Generation Inventory

Comprehensive inventory of all SPARQL generation sites in dsp-api, categorized by pattern type, query type, and complexity.
Created as part of the SPARQL Builder Library spike (Phase 1, Step 1).

## Summary

| Pattern | File Count | Key Risk |
|---------|-----------|----------|
| Pure RDF4J Builder | 27 | Low — type-safe but verbose Java API |
| Hybrid (string interpolation + `.getQueryString`) | 11 | High — SPARQL injection risk |
| Twirl Templates | 25 | Medium — no systematic injection protection |
| Graph Management (inline string) | 1 | Low — simple `DROP GRAPH <uri>` |
| **Total** | **64** | |

Additionally, **44 files** extend or mix in `QueryBuilderHelper`, and **25 Twirl templates** exist under `webapi/src/main/twirl/`.

---

## 1. Pure RDF4J Builder Files (~27 files)

Files that use RDF4J's `Queries.SELECT()`, `Queries.CONSTRUCT()`, `Queries.MODIFY()` etc. builder API exclusively for query construction. Most extend `QueryBuilderHelper` for type conversion helpers (`toRdfIri()`, `variable()`, etc.) but assemble the query through the builder API, not string interpolation.

### Export Queries

| File | Query Type | Complexity | Description |
|------|-----------|-----------|-------------|
| `slice/export/domain/PermissionDataQuery.scala` | CONSTRUCT | Low | Extracts permission data for a project |
| `slice/export/domain/AdminDataQuery.scala` | CONSTRUCT | Low | Exports admin data (projects, users, groups) |
| `slice/export/domain/ProjectMigrationExportService.scala` | CONSTRUCT (via delegates) | Low | Orchestrates project export to BagIt |
| `slice/api/v3/export_/FindResourcesService.scala` | SELECT DISTINCT | Medium | Finds resources by class in a project; uses property paths |

### Admin Queries

| File | Query Type | Complexity | Description |
|------|-----------|-----------|-------------|
| `slice/admin/repo/FileValuePermissionsQuery.scala` | SELECT | Medium | Retrieves file value permissions by filename; uses `zeroOrMore()` |
| `slice/admin/repo/service/AbstractEntityRepo.scala` | CONSTRUCT, UPDATE | Medium | Generic CRUD base class for RDF entities |
| `slice/admin/repo/service/KnoraProjectRepoLive.scala` | CONSTRUCT (via AbstractEntityRepo) | Low | Project repository with caching |
| `slice/admin/repo/service/KnoraGroupRepoLive.scala` | CONSTRUCT (via AbstractEntityRepo) | Low | Group repository |
| `slice/admin/domain/service/ProjectExportService.scala` | SELECT | Low | Exports project data to TriG format |

### Resource Queries

| File | Query Type | Complexity | Description |
|------|-----------|-----------|-------------|
| `slice/resources/repo/service/ValueRepo.scala` | CONSTRUCT, SELECT, DELETE, UPDATE | Medium | Value CRUD operations |
| `slice/resources/repo/service/ResourcesRepoLive.scala` | CONSTRUCT, SELECT, UPDATE | High | Core resource repository |
| `slice/resources/repo/service/value/queries/InsertValueQueryBuilder.scala` | UPDATE (MODIFY) | High | Complex value insertion with link updates, conditional patterns, BIND |
| `slice/resources/repo/DeleteLinkQuery.scala` | UPDATE (MODIFY) | Medium | Deletes links, creates deleted LinkValue versions |
| `slice/resources/repo/ChangeResourceMetadataQuery.scala` | UPDATE (MODIFY) | Medium | Updates resource label, permissions, last modification date; uses `filterNotExists` |

### List Node Queries

| File | Query Type | Complexity | Description |
|------|-----------|-----------|-------------|
| `slice/resources/repo/CreateListNodeQuery.scala` | UPDATE (INSERT DATA) | Low | Creates root or child list nodes |
| `slice/resources/repo/GetListNodeQuery.scala` | CONSTRUCT | Low | Retrieves list node properties |
| `slice/resources/repo/GetListNodeWithChildrenQuery.scala` | CONSTRUCT | Medium | Recursive retrieval with `zeroOrMore()` property paths |
| `slice/resources/repo/GetParentNodeQuery.scala` | CONSTRUCT | Low | Finds parent of a list node |
| `slice/resources/repo/DeleteNodeQuery.scala` | UPDATE (DELETE WHERE) | Low | Deletes list nodes |
| `slice/resources/repo/DeleteListNodeCommentsQuery.scala` | DELETE WHERE | Low | Removes list node comments |
| `slice/resources/repo/UpdateListInfoQuery.scala` | UPDATE (DELETE/INSERT WHERE) | Medium | Updates labels, names, comments |
| `slice/resources/repo/UpdateNodePositionQuery.scala` | UPDATE (DELETE/INSERT WHERE) | Low | Updates node position |
| `slice/resources/repo/ChangeParentNodeQuery.scala` | UPDATE (DELETE/INSERT WHERE) | Low | Moves node between parents |

### Ontology Queries

| File | Query Type | Complexity | Description |
|------|-----------|-----------|-------------|
| `slice/ontology/repo/CreateClassQuery.scala` | UPDATE (MODIFY) | Medium | Creates resource classes with cardinality constraints |
| `slice/ontology/repo/ChangeClassLabelsOrCommentsQuery.scala` | UPDATE (MODIFY) | Medium | Updates class labels/comments in multiple languages |
| `slice/ontology/repo/DeleteClassCommentsQuery.scala` | UPDATE (DELETE WHERE) | Low | Removes class comments |
| `slice/ontology/repo/CreatePropertyQuery.scala` | UPDATE (MODIFY) | Medium | Creates properties with sub-property relationships |
| `slice/ontology/repo/ChangePropertyLabelsOrCommentsQuery.scala` | UPDATE (MODIFY) | Medium | Updates property labels/comments |
| `slice/ontology/repo/DeletePropertyQuery.scala` | UPDATE (DELETE WHERE) | Medium | Deletes properties (with usage check via `filterNotExists`) |
| `slice/ontology/repo/DeletePropertyCommentsQuery.scala` | UPDATE (DELETE WHERE) | Low | Removes property comments |
| `slice/ontology/repo/CountPropertyUsedWithClassQuery.scala` | SELECT | Medium | Counts property usage; uses MINUS, COUNT, GROUP BY |
| `slice/ontology/repo/GetOntologyGraphQuery.scala` | CONSTRUCT | Low | Retrieves entire ontology graph |
| `slice/ontology/repo/GetAllOntologiesMetadataQuery.scala` | SELECT | Low | Lists all ontologies and metadata |
| `slice/ontology/repo/UpdateOntologyMetadataQuery.scala` | UPDATE (MODIFY) | Medium | Updates ontology labels/comments |
| `slice/ontology/repo/DeleteOntologyCommentQuery.scala` | UPDATE (DELETE WHERE) | Low | Removes ontology comment |
| `slice/ontology/repo/DeleteOntologyQuery.scala` | UPDATE (DELETE WHERE) | Low | Deletes entire ontology graph |

---

## 2. Hybrid String Interpolation Files (~11 files)

Files that mix RDF4J builder calls (`.getQueryString()`) with string interpolation (`s"""..."""`). This is the most injection-prone pattern because SPARQL structure is assembled through string concatenation.

| File | Query Type | Complexity | Description | Injection Risk |
|------|-----------|-----------|-------------|---------------|
| `slice/common/QueryBuilderHelper.scala` | ASK (via `askWhere()`) | Low | Foundation trait; `askWhere()` wraps triple patterns with `s"""ASK WHERE { ... }"""` | High — defines the hybrid pattern |
| `slice/ontology/repo/CheckIriExistsQuery.scala` | ASK | Low | `s"""ASK { ${triplePattern.getQueryString} }"""` | High |
| `slice/ontology/repo/IsClassUsedInDataQuery.scala` | ASK | Medium | Embeds class instance patterns in string template | High |
| `slice/ontology/repo/IsEntityUsedQuery.scala` | ASK | Medium | Embeds triple patterns + conditional FILTER strings | High |
| `slice/ontology/repo/IsPropertyUsedInResourcesQuery.scala` | ASK | Low | `s"ASK WHERE { ${union.getQueryString} }"` | High |
| `slice/resources/repo/AskListNameInProjectExistsQuery.scala` | ASK | Low | `s"ASK ${askPattern.getQueryString}"` | High |
| `slice/resources/repo/IsListInUseQuery.scala` | ASK | Low | Embeds list of triple patterns in string | High |
| `slice/resources/repo/IsNodeUsedQuery.scala` | ASK | Low | Embeds two patterns in UNION via string | High |
| `slice/resources/repo/ListNodeExistsQuery.scala` | ASK (via `askWhere()`) | Low | Uses inherited `askWhere()` from QueryBuilderHelper | High |
| `responders/v2/SearchQueries.scala` | SELECT, CONSTRUCT | High | Lucene queries + filter generation; most complex hybrid file | Critical |
| `slice/admin/domain/service/LegalInfoService.scala` | SELECT | Medium | Embeds graph IRI and filter in `s"""..."""` | High |
| `store/triplestore/api/TriplestoreService.scala` | ASK | Low | `isIriInObjectPosition` embeds triple pattern in string | High |

**Note:** `SearchQueries.scala` is the most complex hybrid file — it builds Lucene queries, conditional filter expressions, and subqueries all through string interpolation. This file also has a secondary Lucene injection risk via `FusekiLuceneQuery`.

---

## 3. Twirl Templates (25 files)

All located under `webapi/src/main/twirl/org/knora/webapi/messages/twirl/queries/`.

### Gravsearch Templates (2 files)

| File | Query Type | Complexity | Lines | Control Structures |
|------|-----------|-----------|-------|-------------------|
| `gravsearch/getIncomingImageLinks.scala.txt` | CONSTRUCT | Low | 32 | None |
| `gravsearch/getResourceWithSpecifiedProperties.scala.txt` | CONSTRUCT | Medium | 38 | `@for` |

### SPARQL v2 Templates — Simple (8 files)

Templates with no or minimal control structures.

| File | Query Type | Complexity | Lines | Control Structures |
|------|-----------|-----------|-------|-------------------|
| `sparql/v2/checkValueDeletion.scala.txt` | SELECT | Low | 28 | None |
| `sparql/v2/getAllResourcesInProjectPrequery.scala.txt` | SELECT | Low | 28 | None |
| `sparql/v2/getStandoffTagByUUID.scala.txt` | SELECT | Low | 27 | None |
| `sparql/v2/createOntology.scala.txt` | INSERT WHERE | Low | 55 | `@if` (optional comment) |
| `sparql/v2/eraseResource.scala.txt` | DELETE WHERE | Medium | 58 | UNION patterns |
| `sparql/v2/deleteClass.scala.txt` | UPDATE | Medium | 68 | UNION patterns |
| `sparql/v2/getResourceValueVersionHistory.scala.txt` | SELECT | Medium | 65 | `@if`, `@match` (date filters) |
| `sparql/v2/getResourcesByClassInProjectPrequery.scala.txt` | SELECT | Medium | 63 | `@match` (ordering) |

### SPARQL v2 Templates — Moderate (8 files)

Templates with conditional logic and/or iteration.

| File | Query Type | Complexity | Lines | Control Structures |
|------|-----------|-----------|-------|-------------------|
| `sparql/v2/addCardinalitiesToClass.scala.txt` | UPDATE | Medium | 78 | `@for`, `@match`, `@defining` |
| `sparql/v2/createNewMapping.scala.txt` | INSERT WHERE | Medium | 86 | `@if`, `@for` |
| `sparql/v2/getMapping.scala.txt` | CONSTRUCT | Medium | 85 | `@()` inline, OPTIONALs |
| `sparql/v2/getGraphData.scala.txt` | SELECT | Medium | 110 | `@if`, `@match` |
| `sparql/v2/replaceClassCardinalities.scala.txt` | UPDATE (3 queries) | Medium | 111 | `@for`, `@match`, `@defining` |
| `sparql/v2/changeLinkMetadata.scala.txt` | UPDATE | Medium | 129 | `@if`, `@match` |
| `sparql/v2/changePropertyGuiElement.scala.txt` | UPDATE (multi-query) | Medium | 138 | `@match`, `@for` |
| `sparql/v2/isOntologyUsed.scala.txt` | SELECT | Medium | 110 | `@for`, `@if`, UNION construction |

### SPARQL v2 Templates — Complex (7 files)

Templates with extensive conditional logic, iteration, polymorphic matching, or Lucene integration.

| File | Query Type | Complexity | Lines | Control Structures |
|------|-----------|-----------|-------|-------------------|
| `sparql/v2/createLink.scala.txt` | UPDATE | High | 164 | `@if`, `@match` (validation + optional comments) |
| `sparql/v2/searchFulltext.scala.txt` | SELECT | High | 168 | `@if`, `@for`, `@match`, Jena `text#query` |
| `sparql/v2/deleteValue.scala.txt` | UPDATE | High | 178 | `@if`, `@for` (LinkValue management) |
| `sparql/v2/deleteResource.scala.txt` | UPDATE | Medium | 77 | `@if`, `@match` (delete comment) |
| `sparql/v2/changeLinkTarget.scala.txt` | UPDATE | High | 233 | `@if` (multiple validations), complex link state |
| `sparql/v2/getResourcePropertiesAndValues.scala.txt` | CONSTRUCT | High | 275 | `@if`, `@match` (version history, preview, standoff, property/UUID/date filters) |
| `sparql/v2/addValueVersion.scala.txt` | UPDATE | Very High | 518 | `@match` (polymorphic value types), `@for`, `@if`, indexed variables |

---

## 4. Graph Management (1 file)

| File | Query Type | Complexity | Description |
|------|-----------|-----------|-------------|
| `store/triplestore/impl/TriplestoreServiceLive.scala` | DROP GRAPH | Low | `s"DROP GRAPH <$graphName>"` — simple string interpolation |

---

## 5. Excluded Files

### Upgrade Plugins (~17 files)

Files under `store/triplestore/upgrade/plugins/` extend `AbstractSparqlUpdatePlugin` (which extends `QueryBuilderHelper`). These are one-time-use data migration scripts that run once and remain as historical artifacts. They use string interpolation for SPARQL but are **excluded from migration scope**.

### Gravsearch Pipeline

The `SparqlQuery.scala` AST case classes (`ConstructQuery`, `SelectQuery`, `StatementPattern`, etc.) and all Gravsearch pipeline consumers (`FullTextMainQueryGenerator`, `NonTriplestoreSpecificGravsearchToPrequeryTransformer`, etc.) are **out of scope**.

---

## Query Type Summary

| Query Type | Pure RDF4J | Hybrid | Twirl | Graph Mgmt | Total |
|-----------|-----------|--------|-------|------------|-------|
| SELECT | 5 | 1 | 7 | — | 13 |
| CONSTRUCT | 8 | — | 4 | — | 12 |
| ASK | — | 9 | — | — | 9 |
| UPDATE (DELETE/INSERT WHERE) | 13 | — | 13 | — | 26 |
| DELETE WHERE | 1 | — | 1 | — | 2 |
| INSERT DATA / INSERT WHERE | 1 | — | 1 | — | 2 |
| DROP GRAPH | — | — | — | 1 | 1 |
| **Total** | **28** | **10** | **26** | **1** | **65** |

Note: Some files produce multiple query types; counts reflect the primary query type per file. `SearchQueries.scala` appears once under Hybrid/SELECT though it also produces CONSTRUCT queries.

## Complexity Distribution

| Complexity | Pure RDF4J | Hybrid | Twirl | Total |
|-----------|-----------|--------|-------|-------|
| Low | 15 | 7 | 5 | 27 |
| Medium | 11 | 2 | 10 | 23 |
| High | 2 | 2 | 7 | 11 |
| **Total** | **28** | **11** | **22** | **61** |

## SPARQL Features Used

For library implementation planning, these are the SPARQL features actively used across all patterns:

| Feature | Used In | Priority |
|---------|---------|----------|
| Triple patterns | All | P1 |
| OPTIONAL | RDF4J, Twirl | P1 |
| UNION | Hybrid (ASK), Twirl | P1 |
| FILTER | Hybrid, Twirl | P1 |
| FILTER NOT EXISTS | RDF4J (DeletePropertyQuery, ChangeResourceMetadataQuery) | P1 |
| MINUS | RDF4J (CountPropertyUsedWithClassQuery) | P1 |
| Property paths (`zeroOrMore`, `oneOrMore`) | RDF4J (FileValuePermissionsQuery, GetListNodeWithChildrenQuery, FindResourcesService) | P1 |
| Named graphs (GRAPH) | RDF4J, Twirl, Hybrid | P1 |
| BIND | RDF4J (InsertValueQueryBuilder) | P1 |
| ORDER BY | RDF4J, Twirl | P1 |
| LIMIT / OFFSET | Twirl (searchFulltext, getResourcesByClass) | P1 |
| DISTINCT | RDF4J (FindResourcesService) | P1 |
| VALUES | Twirl (not confirmed — need to check) | P2 |
| GROUP BY | RDF4J (CountPropertyUsedWithClassQuery) | P2 |
| COUNT / aggregate functions | RDF4J (CountPropertyUsedWithClassQuery), Twirl (searchFulltext) | P2 |
| Subqueries | Twirl (searchFulltext) | P2 |
| Jena `text#query` | Twirl (searchFulltext), Hybrid (SearchQueries) | P2 |
| DROP GRAPH | TriplestoreServiceLive | P3 |

## Migration Effort Estimate

Based on complexity distribution:

| Migration Group | Files | Est. Effort | Notes |
|----------------|-------|-------------|-------|
| Simple hybrid (ASK queries) | 9 | Low | Mechanical rewrite |
| Pure RDF4J — Low complexity | 15 | Low | Direct translation |
| Pure RDF4J — Medium complexity | 11 | Medium | May need new combinators |
| Pure RDF4J — High complexity | 2 | High | InsertValueQueryBuilder, ResourcesRepoLive |
| Hybrid — Complex (SearchQueries, LegalInfoService) | 2 | High | Lucene integration |
| Twirl — Simple | 5 | Low | Direct translation |
| Twirl — Moderate | 10 | Medium | Conditional/iteration patterns |
| Twirl — Complex | 7 | High | Polymorphic matching, 500+ line templates |
| Graph management | 1 | Low | Convenience methods |
