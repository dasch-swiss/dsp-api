/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.repo

/**
 * The RDF4J SparqlBuilder output that [[ViewRestrictionsRepo]]'s queries were ported from, captured
 * verbatim before the port and kept as fixtures.
 *
 * Each string is what the repo actually sent to the triplestore: the built query with the project's
 * `VALUES ?resClass { … }` already spliced into its `WHERE` block, which is where the old string
 * fallback put it. `ViewRestrictionsQuerySpec` compares the ported queries against these canonically —
 * parsed by Jena with the prefix map cleared — so the port is pinned to the same queries rather than to
 * the old builder's whitespace, and to the same algebra, so a change in grouping, join order or filter
 * placement cannot slip through.
 *
 * Keys are `<builder>[-<itemType>][-<classes>][-<arguments>]`, where `<classes>` names the
 * [[ViewRestrictionsRepo.ProjectClasses]] shape the spec builds: `single` (one class, not multi-typed),
 * `multi` (one class, multi-typed), `two` (two classes), `none` (no class discovered) and `noneMulti`
 * (no class discovered, multi-typed) — the four combinations of "is there a `VALUES` clause" and "is the
 * most-specific-class filter needed", plus a two-IRI `VALUES` clause.
 *
 * Do not reformat: these strings are a record of what the previous builder emitted.
 */
object ViewRestrictionsLegacyFixtures {

  val byKey: Map[String, String] = Map(
    "anyMultiTypedResourceQuery" ->
      """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
SELECT ?resource
WHERE { ?resource a ?c1 ;
    knora-base:attachedToProject <http://rdfh.ch/projects/0001> ;
    knora-base:isDeleted false ;
    a ?c2 .
FILTER ( ?c1 != ?c2 ) }
LIMIT 1""",
    "multiTypedQuery" ->
      """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
SELECT ?resource
WHERE { ?resource a ?resClass ;
    knora-base:attachedToProject <http://rdfh.ch/projects/0001> ;
    knora-base:isDeleted false ;
    a ?subClass .
?subClass rdfs:subClassOf+ ?resClass . }
LIMIT 1""",
    "projectClassesQuery" ->
      """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
SELECT DISTINCT ?resClass
WHERE { { SELECT DISTINCT ?resClass
WHERE { ?resource knora-base:attachedToProject <http://rdfh.ch/projects/0001> ;
    a ?resClass . }
 }
?resClass rdfs:subClassOf* knora-base:Resource . }""",
    "resourceCountForDrillDownQuery-All-multi" ->
      """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
SELECT ( COUNT( DISTINCT ?resource ) AS ?cnt )
WHERE {
VALUES ?resClass { <http://www.knora.org/ontology/0001/anything#Thing> } { ?resource a ?resClass ;
    knora-base:attachedToProject <http://rdfh.ch/projects/0001> ;
    knora-base:attachedToUser ?creator ;
    knora-base:hasPermissions ?permissions ;
    knora-base:isDeleted false .
FILTER NOT EXISTS { ?resource a ?subClass .
?subClass rdfs:subClassOf+ ?resClass . }
FILTER ( !( REGEX( ?permissions, "(^|[|])(V|M|D|CR) [^|]*knora-admin:UnknownUser" ) ) )
FILTER ( ?resClass = <http://www.knora.org/ontology/0001/anything#Thing> ) } UNION { ?resource a ?resClass ;
    knora-base:attachedToProject <http://rdfh.ch/projects/0001> ;
    knora-base:isDeleted false .
FILTER NOT EXISTS { ?resource a ?subClass .
?subClass rdfs:subClassOf+ ?resClass . }
{ ?resource ?prop ?value .
?prop rdfs:subPropertyOf* knora-base:hasValue . }
?value knora-base:attachedToUser ?creator ;
    knora-base:hasPermissions ?permissions ;
    knora-base:isDeleted false .
FILTER NOT EXISTS { ?value a knora-base:LinkValue . }
FILTER ( !( REGEX( ?permissions, "(^|[|])(V|M|D|CR) [^|]*knora-admin:UnknownUser" ) ) )
FILTER ( ?resClass = <http://www.knora.org/ontology/0001/anything#Thing> ) } }""",
    "resourceCountForDrillDownQuery-All-none" ->
      """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
SELECT ( COUNT( DISTINCT ?resource ) AS ?cnt )
WHERE { { ?resource a ?resClass ;
    knora-base:attachedToProject <http://rdfh.ch/projects/0001> ;
    knora-base:attachedToUser ?creator ;
    knora-base:hasPermissions ?permissions ;
    knora-base:isDeleted false .
?resClass rdfs:subClassOf* knora-base:Resource .
FILTER ( !( REGEX( ?permissions, "(^|[|])(V|M|D|CR) [^|]*knora-admin:UnknownUser" ) ) )
FILTER ( ?resClass = <http://www.knora.org/ontology/0001/anything#Thing> ) } UNION { ?resource a ?resClass ;
    knora-base:attachedToProject <http://rdfh.ch/projects/0001> ;
    knora-base:isDeleted false .
?resClass rdfs:subClassOf* knora-base:Resource .
{ ?resource ?prop ?value .
?prop rdfs:subPropertyOf* knora-base:hasValue . }
?value knora-base:attachedToUser ?creator ;
    knora-base:hasPermissions ?permissions ;
    knora-base:isDeleted false .
FILTER NOT EXISTS { ?value a knora-base:LinkValue . }
FILTER ( !( REGEX( ?permissions, "(^|[|])(V|M|D|CR) [^|]*knora-admin:UnknownUser" ) ) )
FILTER ( ?resClass = <http://www.knora.org/ontology/0001/anything#Thing> ) } }""",
    "resourceCountForDrillDownQuery-All-noneMulti" ->
      """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
SELECT ( COUNT( DISTINCT ?resource ) AS ?cnt )
WHERE { { ?resource a ?resClass ;
    knora-base:attachedToProject <http://rdfh.ch/projects/0001> ;
    knora-base:attachedToUser ?creator ;
    knora-base:hasPermissions ?permissions ;
    knora-base:isDeleted false .
?resClass rdfs:subClassOf* knora-base:Resource .
FILTER NOT EXISTS { ?resource a ?subClass .
?subClass rdfs:subClassOf+ ?resClass . }
FILTER ( !( REGEX( ?permissions, "(^|[|])(V|M|D|CR) [^|]*knora-admin:UnknownUser" ) ) )
FILTER ( ?resClass = <http://www.knora.org/ontology/0001/anything#Thing> ) } UNION { ?resource a ?resClass ;
    knora-base:attachedToProject <http://rdfh.ch/projects/0001> ;
    knora-base:isDeleted false .
?resClass rdfs:subClassOf* knora-base:Resource .
FILTER NOT EXISTS { ?resource a ?subClass .
?subClass rdfs:subClassOf+ ?resClass . }
{ ?resource ?prop ?value .
?prop rdfs:subPropertyOf* knora-base:hasValue . }
?value knora-base:attachedToUser ?creator ;
    knora-base:hasPermissions ?permissions ;
    knora-base:isDeleted false .
FILTER NOT EXISTS { ?value a knora-base:LinkValue . }
FILTER ( !( REGEX( ?permissions, "(^|[|])(V|M|D|CR) [^|]*knora-admin:UnknownUser" ) ) )
FILTER ( ?resClass = <http://www.knora.org/ontology/0001/anything#Thing> ) } }""",
    "resourceCountForDrillDownQuery-All-single" ->
      """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
SELECT ( COUNT( DISTINCT ?resource ) AS ?cnt )
WHERE {
VALUES ?resClass { <http://www.knora.org/ontology/0001/anything#Thing> } { ?resource a ?resClass ;
    knora-base:attachedToProject <http://rdfh.ch/projects/0001> ;
    knora-base:attachedToUser ?creator ;
    knora-base:hasPermissions ?permissions ;
    knora-base:isDeleted false .
FILTER ( !( REGEX( ?permissions, "(^|[|])(V|M|D|CR) [^|]*knora-admin:UnknownUser" ) ) )
FILTER ( ?resClass = <http://www.knora.org/ontology/0001/anything#Thing> ) } UNION { ?resource a ?resClass ;
    knora-base:attachedToProject <http://rdfh.ch/projects/0001> ;
    knora-base:isDeleted false .
{ ?resource ?prop ?value .
?prop rdfs:subPropertyOf* knora-base:hasValue . }
?value knora-base:attachedToUser ?creator ;
    knora-base:hasPermissions ?permissions ;
    knora-base:isDeleted false .
FILTER NOT EXISTS { ?value a knora-base:LinkValue . }
FILTER ( !( REGEX( ?permissions, "(^|[|])(V|M|D|CR) [^|]*knora-admin:UnknownUser" ) ) )
FILTER ( ?resClass = <http://www.knora.org/ontology/0001/anything#Thing> ) } }""",
    "resourceCountForDrillDownQuery-All-two" ->
      """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
SELECT ( COUNT( DISTINCT ?resource ) AS ?cnt )
WHERE {
VALUES ?resClass { <http://www.knora.org/ontology/0001/anything#Thing> <http://www.knora.org/ontology/0001/anything#Book> } { ?resource a ?resClass ;
    knora-base:attachedToProject <http://rdfh.ch/projects/0001> ;
    knora-base:attachedToUser ?creator ;
    knora-base:hasPermissions ?permissions ;
    knora-base:isDeleted false .
FILTER ( !( REGEX( ?permissions, "(^|[|])(V|M|D|CR) [^|]*knora-admin:UnknownUser" ) ) )
FILTER ( ?resClass = <http://www.knora.org/ontology/0001/anything#Thing> ) } UNION { ?resource a ?resClass ;
    knora-base:attachedToProject <http://rdfh.ch/projects/0001> ;
    knora-base:isDeleted false .
{ ?resource ?prop ?value .
?prop rdfs:subPropertyOf* knora-base:hasValue . }
?value knora-base:attachedToUser ?creator ;
    knora-base:hasPermissions ?permissions ;
    knora-base:isDeleted false .
FILTER NOT EXISTS { ?value a knora-base:LinkValue . }
FILTER ( !( REGEX( ?permissions, "(^|[|])(V|M|D|CR) [^|]*knora-admin:UnknownUser" ) ) )
FILTER ( ?resClass = <http://www.knora.org/ontology/0001/anything#Thing> ) } }""",
    "resourceCountForDrillDownQuery-Comment-single" ->
      """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
SELECT ( COUNT( DISTINCT ?resource ) AS ?cnt )
WHERE {
VALUES ?resClass { <http://www.knora.org/ontology/0001/anything#Thing> } { ?resource a ?resClass ;
    knora-base:attachedToProject <http://rdfh.ch/projects/0001> ;
    knora-base:isDeleted false .
{ ?resource ?prop ?value .
?prop rdfs:subPropertyOf* knora-base:hasValue . }
?value knora-base:attachedToUser ?creator ;
    knora-base:hasPermissions ?permissions ;
    knora-base:isDeleted false .
FILTER NOT EXISTS { ?value a knora-base:LinkValue . } }
?value knora-base:valueHasComment ?comment .
FILTER ( !( REGEX( ?permissions, "(^|[|])(V|M|D|CR) [^|]*knora-admin:UnknownUser" ) ) )
FILTER ( ?resClass = <http://www.knora.org/ontology/0001/anything#Thing> ) }""",
    "resourceCountForDrillDownQuery-File-single" ->
      """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
SELECT ( COUNT( DISTINCT ?resource ) AS ?cnt )
WHERE {
VALUES ?resClass { <http://www.knora.org/ontology/0001/anything#Thing> } { ?resource a ?resClass ;
    knora-base:attachedToProject <http://rdfh.ch/projects/0001> ;
    knora-base:isDeleted false .
{ ?resource ?prop ?value .
?prop rdfs:subPropertyOf* knora-base:hasValue . }
?value knora-base:attachedToUser ?creator ;
    knora-base:hasPermissions ?permissions ;
    knora-base:isDeleted false .
FILTER NOT EXISTS { ?value a knora-base:LinkValue . } }
{ ?value a ?fileClass .
?fileClass rdfs:subClassOf* knora-base:FileValue . }
FILTER ( !( REGEX( ?permissions, "(^|[|])(V|M|D|CR) [^|]*knora-admin:UnknownUser" ) ) )
FILTER ( ?resClass = <http://www.knora.org/ontology/0001/anything#Thing> ) }""",
    "resourceCountForDrillDownQuery-Resource-single" ->
      """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
SELECT ( COUNT( DISTINCT ?resource ) AS ?cnt )
WHERE {
VALUES ?resClass { <http://www.knora.org/ontology/0001/anything#Thing> } ?resource a ?resClass ;
    knora-base:attachedToProject <http://rdfh.ch/projects/0001> ;
    knora-base:attachedToUser ?creator ;
    knora-base:hasPermissions ?permissions ;
    knora-base:isDeleted false .
FILTER ( !( REGEX( ?permissions, "(^|[|])(V|M|D|CR) [^|]*knora-admin:UnknownUser" ) ) )
FILTER ( ?resClass = <http://www.knora.org/ontology/0001/anything#Thing> ) }""",
    "resourceCountForDrillDownQuery-Value-single" ->
      """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
SELECT ( COUNT( DISTINCT ?resource ) AS ?cnt )
WHERE {
VALUES ?resClass { <http://www.knora.org/ontology/0001/anything#Thing> } { ?resource a ?resClass ;
    knora-base:attachedToProject <http://rdfh.ch/projects/0001> ;
    knora-base:isDeleted false .
{ ?resource ?prop ?value .
?prop rdfs:subPropertyOf* knora-base:hasValue . }
?value knora-base:attachedToUser ?creator ;
    knora-base:hasPermissions ?permissions ;
    knora-base:isDeleted false .
FILTER NOT EXISTS { ?value a knora-base:LinkValue . } }
FILTER NOT EXISTS { ?value a ?fileClass .
?fileClass rdfs:subClassOf* knora-base:FileValue . }
FILTER ( !( REGEX( ?permissions, "(^|[|])(V|M|D|CR) [^|]*knora-admin:UnknownUser" ) ) )
FILTER ( ?resClass = <http://www.knora.org/ontology/0001/anything#Thing> ) }""",
    "resourceCountsByClassAndPermissionQuery-multi" ->
      """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
SELECT ?resClass ?permissions ( COUNT( DISTINCT ?resource ) AS ?cnt )
WHERE {
VALUES ?resClass { <http://www.knora.org/ontology/0001/anything#Thing> } ?resource a ?resClass ;
    knora-base:attachedToProject <http://rdfh.ch/projects/0001> ;
    knora-base:hasPermissions ?permissions ;
    knora-base:isDeleted false .
FILTER NOT EXISTS { ?resource a ?subClass .
?subClass rdfs:subClassOf+ ?resClass . } }
GROUP BY ?resClass ?permissions""",
    "resourceCountsByClassAndPermissionQuery-none" ->
      """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
SELECT ?resClass ?permissions ( COUNT( DISTINCT ?resource ) AS ?cnt )
WHERE { ?resource a ?resClass ;
    knora-base:attachedToProject <http://rdfh.ch/projects/0001> ;
    knora-base:hasPermissions ?permissions ;
    knora-base:isDeleted false .
?resClass rdfs:subClassOf* knora-base:Resource . }
GROUP BY ?resClass ?permissions""",
    "resourceCountsByClassAndPermissionQuery-noneMulti" ->
      """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
SELECT ?resClass ?permissions ( COUNT( DISTINCT ?resource ) AS ?cnt )
WHERE { ?resource a ?resClass ;
    knora-base:attachedToProject <http://rdfh.ch/projects/0001> ;
    knora-base:hasPermissions ?permissions ;
    knora-base:isDeleted false .
?resClass rdfs:subClassOf* knora-base:Resource .
FILTER NOT EXISTS { ?resource a ?subClass .
?subClass rdfs:subClassOf+ ?resClass . } }
GROUP BY ?resClass ?permissions""",
    "resourceCountsByClassAndPermissionQuery-single" ->
      """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
SELECT ?resClass ?permissions ( COUNT( DISTINCT ?resource ) AS ?cnt )
WHERE {
VALUES ?resClass { <http://www.knora.org/ontology/0001/anything#Thing> } ?resource a ?resClass ;
    knora-base:attachedToProject <http://rdfh.ch/projects/0001> ;
    knora-base:hasPermissions ?permissions ;
    knora-base:isDeleted false . }
GROUP BY ?resClass ?permissions""",
    "resourceCountsByClassAndPermissionQuery-two" ->
      """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
SELECT ?resClass ?permissions ( COUNT( DISTINCT ?resource ) AS ?cnt )
WHERE {
VALUES ?resClass { <http://www.knora.org/ontology/0001/anything#Thing> <http://www.knora.org/ontology/0001/anything#Book> } ?resource a ?resClass ;
    knora-base:attachedToProject <http://rdfh.ch/projects/0001> ;
    knora-base:hasPermissions ?permissions ;
    knora-base:isDeleted false . }
GROUP BY ?resClass ?permissions""",
    "resourcePageQuery-All-multi-50-25" ->
      """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
SELECT DISTINCT ?resource ?labelOrIri
WHERE {
VALUES ?resClass { <http://www.knora.org/ontology/0001/anything#Thing> } { ?resource a ?resClass ;
    knora-base:attachedToProject <http://rdfh.ch/projects/0001> ;
    knora-base:attachedToUser ?creator ;
    knora-base:hasPermissions ?permissions ;
    knora-base:isDeleted false .
FILTER NOT EXISTS { ?resource a ?subClass .
?subClass rdfs:subClassOf+ ?resClass . }
FILTER ( !( REGEX( ?permissions, "(^|[|])(V|M|D|CR) [^|]*knora-admin:UnknownUser" ) ) )
FILTER ( ?resClass = <http://www.knora.org/ontology/0001/anything#Thing> ) } UNION { ?resource a ?resClass ;
    knora-base:attachedToProject <http://rdfh.ch/projects/0001> ;
    knora-base:isDeleted false .
FILTER NOT EXISTS { ?resource a ?subClass .
?subClass rdfs:subClassOf+ ?resClass . }
{ ?resource ?prop ?value .
?prop rdfs:subPropertyOf* knora-base:hasValue . }
?value knora-base:attachedToUser ?creator ;
    knora-base:hasPermissions ?permissions ;
    knora-base:isDeleted false .
FILTER NOT EXISTS { ?value a knora-base:LinkValue . }
FILTER ( !( REGEX( ?permissions, "(^|[|])(V|M|D|CR) [^|]*knora-admin:UnknownUser" ) ) )
FILTER ( ?resClass = <http://www.knora.org/ontology/0001/anything#Thing> ) }
OPTIONAL { ?resource rdfs:label ?label . }
BIND( COALESCE( ?label, STR( ?resource ) ) AS ?labelOrIri ) }
ORDER BY ASC( ?labelOrIri ) ASC( ?resource )
LIMIT 25
OFFSET 50""",
    "resourcePageQuery-All-none-50-25" ->
      """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
SELECT DISTINCT ?resource ?labelOrIri
WHERE { { ?resource a ?resClass ;
    knora-base:attachedToProject <http://rdfh.ch/projects/0001> ;
    knora-base:attachedToUser ?creator ;
    knora-base:hasPermissions ?permissions ;
    knora-base:isDeleted false .
?resClass rdfs:subClassOf* knora-base:Resource .
FILTER ( !( REGEX( ?permissions, "(^|[|])(V|M|D|CR) [^|]*knora-admin:UnknownUser" ) ) )
FILTER ( ?resClass = <http://www.knora.org/ontology/0001/anything#Thing> ) } UNION { ?resource a ?resClass ;
    knora-base:attachedToProject <http://rdfh.ch/projects/0001> ;
    knora-base:isDeleted false .
?resClass rdfs:subClassOf* knora-base:Resource .
{ ?resource ?prop ?value .
?prop rdfs:subPropertyOf* knora-base:hasValue . }
?value knora-base:attachedToUser ?creator ;
    knora-base:hasPermissions ?permissions ;
    knora-base:isDeleted false .
FILTER NOT EXISTS { ?value a knora-base:LinkValue . }
FILTER ( !( REGEX( ?permissions, "(^|[|])(V|M|D|CR) [^|]*knora-admin:UnknownUser" ) ) )
FILTER ( ?resClass = <http://www.knora.org/ontology/0001/anything#Thing> ) }
OPTIONAL { ?resource rdfs:label ?label . }
BIND( COALESCE( ?label, STR( ?resource ) ) AS ?labelOrIri ) }
ORDER BY ASC( ?labelOrIri ) ASC( ?resource )
LIMIT 25
OFFSET 50""",
    "resourcePageQuery-All-noneMulti-50-25" ->
      """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
SELECT DISTINCT ?resource ?labelOrIri
WHERE { { ?resource a ?resClass ;
    knora-base:attachedToProject <http://rdfh.ch/projects/0001> ;
    knora-base:attachedToUser ?creator ;
    knora-base:hasPermissions ?permissions ;
    knora-base:isDeleted false .
?resClass rdfs:subClassOf* knora-base:Resource .
FILTER NOT EXISTS { ?resource a ?subClass .
?subClass rdfs:subClassOf+ ?resClass . }
FILTER ( !( REGEX( ?permissions, "(^|[|])(V|M|D|CR) [^|]*knora-admin:UnknownUser" ) ) )
FILTER ( ?resClass = <http://www.knora.org/ontology/0001/anything#Thing> ) } UNION { ?resource a ?resClass ;
    knora-base:attachedToProject <http://rdfh.ch/projects/0001> ;
    knora-base:isDeleted false .
?resClass rdfs:subClassOf* knora-base:Resource .
FILTER NOT EXISTS { ?resource a ?subClass .
?subClass rdfs:subClassOf+ ?resClass . }
{ ?resource ?prop ?value .
?prop rdfs:subPropertyOf* knora-base:hasValue . }
?value knora-base:attachedToUser ?creator ;
    knora-base:hasPermissions ?permissions ;
    knora-base:isDeleted false .
FILTER NOT EXISTS { ?value a knora-base:LinkValue . }
FILTER ( !( REGEX( ?permissions, "(^|[|])(V|M|D|CR) [^|]*knora-admin:UnknownUser" ) ) )
FILTER ( ?resClass = <http://www.knora.org/ontology/0001/anything#Thing> ) }
OPTIONAL { ?resource rdfs:label ?label . }
BIND( COALESCE( ?label, STR( ?resource ) ) AS ?labelOrIri ) }
ORDER BY ASC( ?labelOrIri ) ASC( ?resource )
LIMIT 25
OFFSET 50""",
    "resourcePageQuery-All-single-0-25" ->
      """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
SELECT DISTINCT ?resource ?labelOrIri
WHERE {
VALUES ?resClass { <http://www.knora.org/ontology/0001/anything#Thing> } { ?resource a ?resClass ;
    knora-base:attachedToProject <http://rdfh.ch/projects/0001> ;
    knora-base:attachedToUser ?creator ;
    knora-base:hasPermissions ?permissions ;
    knora-base:isDeleted false .
FILTER ( !( REGEX( ?permissions, "(^|[|])(V|M|D|CR) [^|]*knora-admin:UnknownUser" ) ) )
FILTER ( ?resClass = <http://www.knora.org/ontology/0001/anything#Thing> ) } UNION { ?resource a ?resClass ;
    knora-base:attachedToProject <http://rdfh.ch/projects/0001> ;
    knora-base:isDeleted false .
{ ?resource ?prop ?value .
?prop rdfs:subPropertyOf* knora-base:hasValue . }
?value knora-base:attachedToUser ?creator ;
    knora-base:hasPermissions ?permissions ;
    knora-base:isDeleted false .
FILTER NOT EXISTS { ?value a knora-base:LinkValue . }
FILTER ( !( REGEX( ?permissions, "(^|[|])(V|M|D|CR) [^|]*knora-admin:UnknownUser" ) ) )
FILTER ( ?resClass = <http://www.knora.org/ontology/0001/anything#Thing> ) }
OPTIONAL { ?resource rdfs:label ?label . }
BIND( COALESCE( ?label, STR( ?resource ) ) AS ?labelOrIri ) }
ORDER BY ASC( ?labelOrIri ) ASC( ?resource )
LIMIT 25
OFFSET 0""",
    "resourcePageQuery-All-single-50-25" ->
      """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
SELECT DISTINCT ?resource ?labelOrIri
WHERE {
VALUES ?resClass { <http://www.knora.org/ontology/0001/anything#Thing> } { ?resource a ?resClass ;
    knora-base:attachedToProject <http://rdfh.ch/projects/0001> ;
    knora-base:attachedToUser ?creator ;
    knora-base:hasPermissions ?permissions ;
    knora-base:isDeleted false .
FILTER ( !( REGEX( ?permissions, "(^|[|])(V|M|D|CR) [^|]*knora-admin:UnknownUser" ) ) )
FILTER ( ?resClass = <http://www.knora.org/ontology/0001/anything#Thing> ) } UNION { ?resource a ?resClass ;
    knora-base:attachedToProject <http://rdfh.ch/projects/0001> ;
    knora-base:isDeleted false .
{ ?resource ?prop ?value .
?prop rdfs:subPropertyOf* knora-base:hasValue . }
?value knora-base:attachedToUser ?creator ;
    knora-base:hasPermissions ?permissions ;
    knora-base:isDeleted false .
FILTER NOT EXISTS { ?value a knora-base:LinkValue . }
FILTER ( !( REGEX( ?permissions, "(^|[|])(V|M|D|CR) [^|]*knora-admin:UnknownUser" ) ) )
FILTER ( ?resClass = <http://www.knora.org/ontology/0001/anything#Thing> ) }
OPTIONAL { ?resource rdfs:label ?label . }
BIND( COALESCE( ?label, STR( ?resource ) ) AS ?labelOrIri ) }
ORDER BY ASC( ?labelOrIri ) ASC( ?resource )
LIMIT 25
OFFSET 50""",
    "resourcePageQuery-All-two-50-25" ->
      """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
SELECT DISTINCT ?resource ?labelOrIri
WHERE {
VALUES ?resClass { <http://www.knora.org/ontology/0001/anything#Thing> <http://www.knora.org/ontology/0001/anything#Book> } { ?resource a ?resClass ;
    knora-base:attachedToProject <http://rdfh.ch/projects/0001> ;
    knora-base:attachedToUser ?creator ;
    knora-base:hasPermissions ?permissions ;
    knora-base:isDeleted false .
FILTER ( !( REGEX( ?permissions, "(^|[|])(V|M|D|CR) [^|]*knora-admin:UnknownUser" ) ) )
FILTER ( ?resClass = <http://www.knora.org/ontology/0001/anything#Thing> ) } UNION { ?resource a ?resClass ;
    knora-base:attachedToProject <http://rdfh.ch/projects/0001> ;
    knora-base:isDeleted false .
{ ?resource ?prop ?value .
?prop rdfs:subPropertyOf* knora-base:hasValue . }
?value knora-base:attachedToUser ?creator ;
    knora-base:hasPermissions ?permissions ;
    knora-base:isDeleted false .
FILTER NOT EXISTS { ?value a knora-base:LinkValue . }
FILTER ( !( REGEX( ?permissions, "(^|[|])(V|M|D|CR) [^|]*knora-admin:UnknownUser" ) ) )
FILTER ( ?resClass = <http://www.knora.org/ontology/0001/anything#Thing> ) }
OPTIONAL { ?resource rdfs:label ?label . }
BIND( COALESCE( ?label, STR( ?resource ) ) AS ?labelOrIri ) }
ORDER BY ASC( ?labelOrIri ) ASC( ?resource )
LIMIT 25
OFFSET 50""",
    "resourcePageQuery-Comment-single-50-25" ->
      """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
SELECT DISTINCT ?resource ?labelOrIri
WHERE {
VALUES ?resClass { <http://www.knora.org/ontology/0001/anything#Thing> } { { ?resource a ?resClass ;
    knora-base:attachedToProject <http://rdfh.ch/projects/0001> ;
    knora-base:isDeleted false .
{ ?resource ?prop ?value .
?prop rdfs:subPropertyOf* knora-base:hasValue . }
?value knora-base:attachedToUser ?creator ;
    knora-base:hasPermissions ?permissions ;
    knora-base:isDeleted false .
FILTER NOT EXISTS { ?value a knora-base:LinkValue . } }
?value knora-base:valueHasComment ?comment .
FILTER ( !( REGEX( ?permissions, "(^|[|])(V|M|D|CR) [^|]*knora-admin:UnknownUser" ) ) )
FILTER ( ?resClass = <http://www.knora.org/ontology/0001/anything#Thing> ) }
OPTIONAL { ?resource rdfs:label ?label . }
BIND( COALESCE( ?label, STR( ?resource ) ) AS ?labelOrIri ) }
ORDER BY ASC( ?labelOrIri ) ASC( ?resource )
LIMIT 25
OFFSET 50""",
    "resourcePageQuery-File-single-50-25" ->
      """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
SELECT DISTINCT ?resource ?labelOrIri
WHERE {
VALUES ?resClass { <http://www.knora.org/ontology/0001/anything#Thing> } { { ?resource a ?resClass ;
    knora-base:attachedToProject <http://rdfh.ch/projects/0001> ;
    knora-base:isDeleted false .
{ ?resource ?prop ?value .
?prop rdfs:subPropertyOf* knora-base:hasValue . }
?value knora-base:attachedToUser ?creator ;
    knora-base:hasPermissions ?permissions ;
    knora-base:isDeleted false .
FILTER NOT EXISTS { ?value a knora-base:LinkValue . } }
{ ?value a ?fileClass .
?fileClass rdfs:subClassOf* knora-base:FileValue . }
FILTER ( !( REGEX( ?permissions, "(^|[|])(V|M|D|CR) [^|]*knora-admin:UnknownUser" ) ) )
FILTER ( ?resClass = <http://www.knora.org/ontology/0001/anything#Thing> ) }
OPTIONAL { ?resource rdfs:label ?label . }
BIND( COALESCE( ?label, STR( ?resource ) ) AS ?labelOrIri ) }
ORDER BY ASC( ?labelOrIri ) ASC( ?resource )
LIMIT 25
OFFSET 50""",
    "resourcePageQuery-Resource-single-50-25" ->
      """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
SELECT DISTINCT ?resource ?labelOrIri
WHERE {
VALUES ?resClass { <http://www.knora.org/ontology/0001/anything#Thing> } { ?resource a ?resClass ;
    knora-base:attachedToProject <http://rdfh.ch/projects/0001> ;
    knora-base:attachedToUser ?creator ;
    knora-base:hasPermissions ?permissions ;
    knora-base:isDeleted false .
FILTER ( !( REGEX( ?permissions, "(^|[|])(V|M|D|CR) [^|]*knora-admin:UnknownUser" ) ) )
FILTER ( ?resClass = <http://www.knora.org/ontology/0001/anything#Thing> ) }
OPTIONAL { ?resource rdfs:label ?label . }
BIND( COALESCE( ?label, STR( ?resource ) ) AS ?labelOrIri ) }
ORDER BY ASC( ?labelOrIri ) ASC( ?resource )
LIMIT 25
OFFSET 50""",
    "resourcePageQuery-Value-single-50-25" ->
      """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
SELECT DISTINCT ?resource ?labelOrIri
WHERE {
VALUES ?resClass { <http://www.knora.org/ontology/0001/anything#Thing> } { { ?resource a ?resClass ;
    knora-base:attachedToProject <http://rdfh.ch/projects/0001> ;
    knora-base:isDeleted false .
{ ?resource ?prop ?value .
?prop rdfs:subPropertyOf* knora-base:hasValue . }
?value knora-base:attachedToUser ?creator ;
    knora-base:hasPermissions ?permissions ;
    knora-base:isDeleted false .
FILTER NOT EXISTS { ?value a knora-base:LinkValue . } }
FILTER NOT EXISTS { ?value a ?fileClass .
?fileClass rdfs:subClassOf* knora-base:FileValue . }
FILTER ( !( REGEX( ?permissions, "(^|[|])(V|M|D|CR) [^|]*knora-admin:UnknownUser" ) ) )
FILTER ( ?resClass = <http://www.knora.org/ontology/0001/anything#Thing> ) }
OPTIONAL { ?resource rdfs:label ?label . }
BIND( COALESCE( ?label, STR( ?resource ) ) AS ?labelOrIri ) }
ORDER BY ASC( ?labelOrIri ) ASC( ?resource )
LIMIT 25
OFFSET 50""",
    "resourceQuery-group-multi-two" ->
      """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
SELECT DISTINCT ?resource ?resClass ?label ?creator ?permissions
WHERE {
VALUES ?resClass { <http://www.knora.org/ontology/0001/anything#Thing> } ?resource a ?resClass ;
    knora-base:attachedToProject <http://rdfh.ch/projects/0001> ;
    knora-base:attachedToUser ?creator ;
    knora-base:hasPermissions ?permissions ;
    knora-base:isDeleted false .
FILTER NOT EXISTS { ?resource a ?subClass .
?subClass rdfs:subClassOf+ ?resClass . }
OPTIONAL { ?resource rdfs:label ?label . }
FILTER ( !( REGEX( ?permissions, "(^|[|])(V|M|D|CR) [^|]*knora-admin:UnknownUser" ) ) )
FILTER ( ?resource IN ( <http://rdfh.ch/0001/a>, <http://rdfh.ch/0001/b> ) )
FILTER ( ?resClass = <http://www.knora.org/ontology/0001/anything#Thing> ) }""",
    "resourceQuery-group-none-two" ->
      """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
SELECT DISTINCT ?resource ?resClass ?label ?creator ?permissions
WHERE { ?resource a ?resClass ;
    knora-base:attachedToProject <http://rdfh.ch/projects/0001> ;
    knora-base:attachedToUser ?creator ;
    knora-base:hasPermissions ?permissions ;
    knora-base:isDeleted false .
?resClass rdfs:subClassOf* knora-base:Resource .
OPTIONAL { ?resource rdfs:label ?label . }
FILTER ( !( REGEX( ?permissions, "(^|[|])(V|M|D|CR) [^|]*knora-admin:UnknownUser" ) ) )
FILTER ( ?resource IN ( <http://rdfh.ch/0001/a>, <http://rdfh.ch/0001/b> ) )
FILTER ( ?resClass = <http://www.knora.org/ontology/0001/anything#Thing> ) }""",
    "resourceQuery-group-noneMulti-two" ->
      """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
SELECT DISTINCT ?resource ?resClass ?label ?creator ?permissions
WHERE { ?resource a ?resClass ;
    knora-base:attachedToProject <http://rdfh.ch/projects/0001> ;
    knora-base:attachedToUser ?creator ;
    knora-base:hasPermissions ?permissions ;
    knora-base:isDeleted false .
?resClass rdfs:subClassOf* knora-base:Resource .
FILTER NOT EXISTS { ?resource a ?subClass .
?subClass rdfs:subClassOf+ ?resClass . }
OPTIONAL { ?resource rdfs:label ?label . }
FILTER ( !( REGEX( ?permissions, "(^|[|])(V|M|D|CR) [^|]*knora-admin:UnknownUser" ) ) )
FILTER ( ?resource IN ( <http://rdfh.ch/0001/a>, <http://rdfh.ch/0001/b> ) )
FILTER ( ?resClass = <http://www.knora.org/ontology/0001/anything#Thing> ) }""",
    "resourceQuery-group-single-one" ->
      """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
SELECT DISTINCT ?resource ?resClass ?label ?creator ?permissions
WHERE {
VALUES ?resClass { <http://www.knora.org/ontology/0001/anything#Thing> } ?resource a ?resClass ;
    knora-base:attachedToProject <http://rdfh.ch/projects/0001> ;
    knora-base:attachedToUser ?creator ;
    knora-base:hasPermissions ?permissions ;
    knora-base:isDeleted false .
OPTIONAL { ?resource rdfs:label ?label . }
FILTER ( !( REGEX( ?permissions, "(^|[|])(V|M|D|CR) [^|]*knora-admin:UnknownUser" ) ) )
FILTER ( ?resource IN ( <http://rdfh.ch/0001/a> ) )
FILTER ( ?resClass = <http://www.knora.org/ontology/0001/anything#Thing> ) }""",
    "resourceQuery-group-single-two" ->
      """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
SELECT DISTINCT ?resource ?resClass ?label ?creator ?permissions
WHERE {
VALUES ?resClass { <http://www.knora.org/ontology/0001/anything#Thing> } ?resource a ?resClass ;
    knora-base:attachedToProject <http://rdfh.ch/projects/0001> ;
    knora-base:attachedToUser ?creator ;
    knora-base:hasPermissions ?permissions ;
    knora-base:isDeleted false .
OPTIONAL { ?resource rdfs:label ?label . }
FILTER ( !( REGEX( ?permissions, "(^|[|])(V|M|D|CR) [^|]*knora-admin:UnknownUser" ) ) )
FILTER ( ?resource IN ( <http://rdfh.ch/0001/a>, <http://rdfh.ch/0001/b> ) )
FILTER ( ?resClass = <http://www.knora.org/ontology/0001/anything#Thing> ) }""",
    "resourceQuery-group-two-two" ->
      """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
SELECT DISTINCT ?resource ?resClass ?label ?creator ?permissions
WHERE {
VALUES ?resClass { <http://www.knora.org/ontology/0001/anything#Thing> <http://www.knora.org/ontology/0001/anything#Book> } ?resource a ?resClass ;
    knora-base:attachedToProject <http://rdfh.ch/projects/0001> ;
    knora-base:attachedToUser ?creator ;
    knora-base:hasPermissions ?permissions ;
    knora-base:isDeleted false .
OPTIONAL { ?resource rdfs:label ?label . }
FILTER ( !( REGEX( ?permissions, "(^|[|])(V|M|D|CR) [^|]*knora-admin:UnknownUser" ) ) )
FILTER ( ?resource IN ( <http://rdfh.ch/0001/a>, <http://rdfh.ch/0001/b> ) )
FILTER ( ?resClass = <http://www.knora.org/ontology/0001/anything#Thing> ) }""",
    "resourceQuery-noGroup-single-two" ->
      """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
SELECT DISTINCT ?resource ?resClass ?label ?creator ?permissions
WHERE {
VALUES ?resClass { <http://www.knora.org/ontology/0001/anything#Thing> } ?resource a ?resClass ;
    knora-base:attachedToProject <http://rdfh.ch/projects/0001> ;
    knora-base:attachedToUser ?creator ;
    knora-base:hasPermissions ?permissions ;
    knora-base:isDeleted false .
OPTIONAL { ?resource rdfs:label ?label . }
FILTER ( !( REGEX( ?permissions, "(^|[|])(V|M|D|CR) [^|]*knora-admin:UnknownUser" ) ) )
FILTER ( ?resource IN ( <http://rdfh.ch/0001/a>, <http://rdfh.ch/0001/b> ) ) }""",
    "valueCountsByPermissionQuery-All-multi" ->
      """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
SELECT ?permissions ( COUNT( DISTINCT ?value ) AS ?cnt )
WHERE {
VALUES ?resClass { <http://www.knora.org/ontology/0001/anything#Thing> } ?resource a ?resClass ;
    knora-base:attachedToProject <http://rdfh.ch/projects/0001> ;
    knora-base:isDeleted false .
FILTER NOT EXISTS { ?resource a ?subClass .
?subClass rdfs:subClassOf+ ?resClass . }
{ ?resource ?prop ?value .
?prop rdfs:subPropertyOf* knora-base:hasValue . }
?value knora-base:hasPermissions ?permissions ;
    knora-base:isDeleted false .
FILTER NOT EXISTS { ?value a knora-base:LinkValue . }
FILTER ( ?resClass = <http://www.knora.org/ontology/0001/anything#Thing> ) }
GROUP BY ?permissions""",
    "valueCountsByPermissionQuery-All-none" ->
      """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
SELECT ?permissions ( COUNT( DISTINCT ?value ) AS ?cnt )
WHERE { ?resource a ?resClass ;
    knora-base:attachedToProject <http://rdfh.ch/projects/0001> ;
    knora-base:isDeleted false .
?resClass rdfs:subClassOf* knora-base:Resource .
{ ?resource ?prop ?value .
?prop rdfs:subPropertyOf* knora-base:hasValue . }
?value knora-base:hasPermissions ?permissions ;
    knora-base:isDeleted false .
FILTER NOT EXISTS { ?value a knora-base:LinkValue . }
FILTER ( ?resClass = <http://www.knora.org/ontology/0001/anything#Thing> ) }
GROUP BY ?permissions""",
    "valueCountsByPermissionQuery-All-noneMulti" ->
      """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
SELECT ?permissions ( COUNT( DISTINCT ?value ) AS ?cnt )
WHERE { ?resource a ?resClass ;
    knora-base:attachedToProject <http://rdfh.ch/projects/0001> ;
    knora-base:isDeleted false .
?resClass rdfs:subClassOf* knora-base:Resource .
FILTER NOT EXISTS { ?resource a ?subClass .
?subClass rdfs:subClassOf+ ?resClass . }
{ ?resource ?prop ?value .
?prop rdfs:subPropertyOf* knora-base:hasValue . }
?value knora-base:hasPermissions ?permissions ;
    knora-base:isDeleted false .
FILTER NOT EXISTS { ?value a knora-base:LinkValue . }
FILTER ( ?resClass = <http://www.knora.org/ontology/0001/anything#Thing> ) }
GROUP BY ?permissions""",
    "valueCountsByPermissionQuery-All-single" ->
      """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
SELECT ?permissions ( COUNT( DISTINCT ?value ) AS ?cnt )
WHERE {
VALUES ?resClass { <http://www.knora.org/ontology/0001/anything#Thing> } ?resource a ?resClass ;
    knora-base:attachedToProject <http://rdfh.ch/projects/0001> ;
    knora-base:isDeleted false .
{ ?resource ?prop ?value .
?prop rdfs:subPropertyOf* knora-base:hasValue . }
?value knora-base:hasPermissions ?permissions ;
    knora-base:isDeleted false .
FILTER NOT EXISTS { ?value a knora-base:LinkValue . }
FILTER ( ?resClass = <http://www.knora.org/ontology/0001/anything#Thing> ) }
GROUP BY ?permissions""",
    "valueCountsByPermissionQuery-All-two" ->
      """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
SELECT ?permissions ( COUNT( DISTINCT ?value ) AS ?cnt )
WHERE {
VALUES ?resClass { <http://www.knora.org/ontology/0001/anything#Thing> <http://www.knora.org/ontology/0001/anything#Book> } ?resource a ?resClass ;
    knora-base:attachedToProject <http://rdfh.ch/projects/0001> ;
    knora-base:isDeleted false .
{ ?resource ?prop ?value .
?prop rdfs:subPropertyOf* knora-base:hasValue . }
?value knora-base:hasPermissions ?permissions ;
    knora-base:isDeleted false .
FILTER NOT EXISTS { ?value a knora-base:LinkValue . }
FILTER ( ?resClass = <http://www.knora.org/ontology/0001/anything#Thing> ) }
GROUP BY ?permissions""",
    "valueCountsByPermissionQuery-Comment-single" ->
      """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
SELECT ?permissions ( COUNT( DISTINCT ?value ) AS ?cnt )
WHERE {
VALUES ?resClass { <http://www.knora.org/ontology/0001/anything#Thing> } { ?resource a ?resClass ;
    knora-base:attachedToProject <http://rdfh.ch/projects/0001> ;
    knora-base:isDeleted false .
{ ?resource ?prop ?value .
?prop rdfs:subPropertyOf* knora-base:hasValue . }
?value knora-base:hasPermissions ?permissions ;
    knora-base:isDeleted false .
FILTER NOT EXISTS { ?value a knora-base:LinkValue . } }
?value knora-base:valueHasComment ?comment .
FILTER ( ?resClass = <http://www.knora.org/ontology/0001/anything#Thing> ) }
GROUP BY ?permissions""",
    "valueCountsByPermissionQuery-File-single" ->
      """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
SELECT ?permissions ( COUNT( DISTINCT ?value ) AS ?cnt )
WHERE {
VALUES ?resClass { <http://www.knora.org/ontology/0001/anything#Thing> } { ?resource a ?resClass ;
    knora-base:attachedToProject <http://rdfh.ch/projects/0001> ;
    knora-base:isDeleted false .
{ ?resource ?prop ?value .
?prop rdfs:subPropertyOf* knora-base:hasValue . }
?value knora-base:hasPermissions ?permissions ;
    knora-base:isDeleted false .
FILTER NOT EXISTS { ?value a knora-base:LinkValue . } }
{ ?value a ?fileClass .
?fileClass rdfs:subClassOf* knora-base:FileValue . }
FILTER ( ?resClass = <http://www.knora.org/ontology/0001/anything#Thing> ) }
GROUP BY ?permissions""",
    "valueCountsByPermissionQuery-Resource-single" ->
      """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
SELECT ?permissions ( COUNT( DISTINCT ?value ) AS ?cnt )
WHERE {
VALUES ?resClass { <http://www.knora.org/ontology/0001/anything#Thing> } ?resource a ?resClass ;
    knora-base:attachedToProject <http://rdfh.ch/projects/0001> ;
    knora-base:isDeleted false .
{ ?resource ?prop ?value .
?prop rdfs:subPropertyOf* knora-base:hasValue . }
?value knora-base:hasPermissions ?permissions ;
    knora-base:isDeleted false .
FILTER NOT EXISTS { ?value a knora-base:LinkValue . }
FILTER ( ?resClass = <http://www.knora.org/ontology/0001/anything#Thing> ) }
GROUP BY ?permissions""",
    "valueCountsByPermissionQuery-Value-single" ->
      """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
SELECT ?permissions ( COUNT( DISTINCT ?value ) AS ?cnt )
WHERE {
VALUES ?resClass { <http://www.knora.org/ontology/0001/anything#Thing> } { ?resource a ?resClass ;
    knora-base:attachedToProject <http://rdfh.ch/projects/0001> ;
    knora-base:isDeleted false .
{ ?resource ?prop ?value .
?prop rdfs:subPropertyOf* knora-base:hasValue . }
?value knora-base:hasPermissions ?permissions ;
    knora-base:isDeleted false .
FILTER NOT EXISTS { ?value a knora-base:LinkValue . } }
FILTER NOT EXISTS { ?value a ?fileClass .
?fileClass rdfs:subClassOf* knora-base:FileValue . }
FILTER ( ?resClass = <http://www.knora.org/ontology/0001/anything#Thing> ) }
GROUP BY ?permissions""",
    "valueQuery-group-multi-two" ->
      """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
SELECT DISTINCT ?resource ?resClass ?label ?prop ?value ?fileClass ?comment ?creator ?permissions
WHERE {
VALUES ?resClass { <http://www.knora.org/ontology/0001/anything#Thing> } ?resource a ?resClass ;
    knora-base:attachedToProject <http://rdfh.ch/projects/0001> ;
    knora-base:isDeleted false .
FILTER NOT EXISTS { ?resource a ?subClass .
?subClass rdfs:subClassOf+ ?resClass . }
{ ?resource ?prop ?value .
?prop rdfs:subPropertyOf* knora-base:hasValue . }
?value knora-base:attachedToUser ?creator ;
    knora-base:hasPermissions ?permissions ;
    knora-base:isDeleted false .
FILTER NOT EXISTS { ?value a knora-base:LinkValue . }
OPTIONAL { ?resource rdfs:label ?label . }
OPTIONAL { ?value a ?fileClass .
?fileClass rdfs:subClassOf* knora-base:FileValue . }
OPTIONAL { ?value knora-base:valueHasComment ?comment . }
FILTER ( !( REGEX( ?permissions, "(^|[|])(V|M|D|CR) [^|]*knora-admin:UnknownUser" ) ) )
FILTER ( ?resource IN ( <http://rdfh.ch/0001/a>, <http://rdfh.ch/0001/b> ) )
FILTER ( ?resClass = <http://www.knora.org/ontology/0001/anything#hasPicture> ) }""",
    "valueQuery-group-none-two" ->
      """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
SELECT DISTINCT ?resource ?resClass ?label ?prop ?value ?fileClass ?comment ?creator ?permissions
WHERE { ?resource a ?resClass ;
    knora-base:attachedToProject <http://rdfh.ch/projects/0001> ;
    knora-base:isDeleted false .
?resClass rdfs:subClassOf* knora-base:Resource .
{ ?resource ?prop ?value .
?prop rdfs:subPropertyOf* knora-base:hasValue . }
?value knora-base:attachedToUser ?creator ;
    knora-base:hasPermissions ?permissions ;
    knora-base:isDeleted false .
FILTER NOT EXISTS { ?value a knora-base:LinkValue . }
OPTIONAL { ?resource rdfs:label ?label . }
OPTIONAL { ?value a ?fileClass .
?fileClass rdfs:subClassOf* knora-base:FileValue . }
OPTIONAL { ?value knora-base:valueHasComment ?comment . }
FILTER ( !( REGEX( ?permissions, "(^|[|])(V|M|D|CR) [^|]*knora-admin:UnknownUser" ) ) )
FILTER ( ?resource IN ( <http://rdfh.ch/0001/a>, <http://rdfh.ch/0001/b> ) )
FILTER ( ?resClass = <http://www.knora.org/ontology/0001/anything#hasPicture> ) }""",
    "valueQuery-group-noneMulti-two" ->
      """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
SELECT DISTINCT ?resource ?resClass ?label ?prop ?value ?fileClass ?comment ?creator ?permissions
WHERE { ?resource a ?resClass ;
    knora-base:attachedToProject <http://rdfh.ch/projects/0001> ;
    knora-base:isDeleted false .
?resClass rdfs:subClassOf* knora-base:Resource .
FILTER NOT EXISTS { ?resource a ?subClass .
?subClass rdfs:subClassOf+ ?resClass . }
{ ?resource ?prop ?value .
?prop rdfs:subPropertyOf* knora-base:hasValue . }
?value knora-base:attachedToUser ?creator ;
    knora-base:hasPermissions ?permissions ;
    knora-base:isDeleted false .
FILTER NOT EXISTS { ?value a knora-base:LinkValue . }
OPTIONAL { ?resource rdfs:label ?label . }
OPTIONAL { ?value a ?fileClass .
?fileClass rdfs:subClassOf* knora-base:FileValue . }
OPTIONAL { ?value knora-base:valueHasComment ?comment . }
FILTER ( !( REGEX( ?permissions, "(^|[|])(V|M|D|CR) [^|]*knora-admin:UnknownUser" ) ) )
FILTER ( ?resource IN ( <http://rdfh.ch/0001/a>, <http://rdfh.ch/0001/b> ) )
FILTER ( ?resClass = <http://www.knora.org/ontology/0001/anything#hasPicture> ) }""",
    "valueQuery-group-single-one" ->
      """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
SELECT DISTINCT ?resource ?resClass ?label ?prop ?value ?fileClass ?comment ?creator ?permissions
WHERE {
VALUES ?resClass { <http://www.knora.org/ontology/0001/anything#Thing> } ?resource a ?resClass ;
    knora-base:attachedToProject <http://rdfh.ch/projects/0001> ;
    knora-base:isDeleted false .
{ ?resource ?prop ?value .
?prop rdfs:subPropertyOf* knora-base:hasValue . }
?value knora-base:attachedToUser ?creator ;
    knora-base:hasPermissions ?permissions ;
    knora-base:isDeleted false .
FILTER NOT EXISTS { ?value a knora-base:LinkValue . }
OPTIONAL { ?resource rdfs:label ?label . }
OPTIONAL { ?value a ?fileClass .
?fileClass rdfs:subClassOf* knora-base:FileValue . }
OPTIONAL { ?value knora-base:valueHasComment ?comment . }
FILTER ( !( REGEX( ?permissions, "(^|[|])(V|M|D|CR) [^|]*knora-admin:UnknownUser" ) ) )
FILTER ( ?resource IN ( <http://rdfh.ch/0001/a> ) )
FILTER ( ?resClass = <http://www.knora.org/ontology/0001/anything#hasPicture> ) }""",
    "valueQuery-group-single-two" ->
      """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
SELECT DISTINCT ?resource ?resClass ?label ?prop ?value ?fileClass ?comment ?creator ?permissions
WHERE {
VALUES ?resClass { <http://www.knora.org/ontology/0001/anything#Thing> } ?resource a ?resClass ;
    knora-base:attachedToProject <http://rdfh.ch/projects/0001> ;
    knora-base:isDeleted false .
{ ?resource ?prop ?value .
?prop rdfs:subPropertyOf* knora-base:hasValue . }
?value knora-base:attachedToUser ?creator ;
    knora-base:hasPermissions ?permissions ;
    knora-base:isDeleted false .
FILTER NOT EXISTS { ?value a knora-base:LinkValue . }
OPTIONAL { ?resource rdfs:label ?label . }
OPTIONAL { ?value a ?fileClass .
?fileClass rdfs:subClassOf* knora-base:FileValue . }
OPTIONAL { ?value knora-base:valueHasComment ?comment . }
FILTER ( !( REGEX( ?permissions, "(^|[|])(V|M|D|CR) [^|]*knora-admin:UnknownUser" ) ) )
FILTER ( ?resource IN ( <http://rdfh.ch/0001/a>, <http://rdfh.ch/0001/b> ) )
FILTER ( ?resClass = <http://www.knora.org/ontology/0001/anything#hasPicture> ) }""",
    "valueQuery-group-two-two" ->
      """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
SELECT DISTINCT ?resource ?resClass ?label ?prop ?value ?fileClass ?comment ?creator ?permissions
WHERE {
VALUES ?resClass { <http://www.knora.org/ontology/0001/anything#Thing> <http://www.knora.org/ontology/0001/anything#Book> } ?resource a ?resClass ;
    knora-base:attachedToProject <http://rdfh.ch/projects/0001> ;
    knora-base:isDeleted false .
{ ?resource ?prop ?value .
?prop rdfs:subPropertyOf* knora-base:hasValue . }
?value knora-base:attachedToUser ?creator ;
    knora-base:hasPermissions ?permissions ;
    knora-base:isDeleted false .
FILTER NOT EXISTS { ?value a knora-base:LinkValue . }
OPTIONAL { ?resource rdfs:label ?label . }
OPTIONAL { ?value a ?fileClass .
?fileClass rdfs:subClassOf* knora-base:FileValue . }
OPTIONAL { ?value knora-base:valueHasComment ?comment . }
FILTER ( !( REGEX( ?permissions, "(^|[|])(V|M|D|CR) [^|]*knora-admin:UnknownUser" ) ) )
FILTER ( ?resource IN ( <http://rdfh.ch/0001/a>, <http://rdfh.ch/0001/b> ) )
FILTER ( ?resClass = <http://www.knora.org/ontology/0001/anything#hasPicture> ) }""",
    "valueQuery-noGroup-single-two" ->
      """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
SELECT DISTINCT ?resource ?resClass ?label ?prop ?value ?fileClass ?comment ?creator ?permissions
WHERE {
VALUES ?resClass { <http://www.knora.org/ontology/0001/anything#Thing> } ?resource a ?resClass ;
    knora-base:attachedToProject <http://rdfh.ch/projects/0001> ;
    knora-base:isDeleted false .
{ ?resource ?prop ?value .
?prop rdfs:subPropertyOf* knora-base:hasValue . }
?value knora-base:attachedToUser ?creator ;
    knora-base:hasPermissions ?permissions ;
    knora-base:isDeleted false .
FILTER NOT EXISTS { ?value a knora-base:LinkValue . }
OPTIONAL { ?resource rdfs:label ?label . }
OPTIONAL { ?value a ?fileClass .
?fileClass rdfs:subClassOf* knora-base:FileValue . }
OPTIONAL { ?value knora-base:valueHasComment ?comment . }
FILTER ( !( REGEX( ?permissions, "(^|[|])(V|M|D|CR) [^|]*knora-admin:UnknownUser" ) ) )
FILTER ( ?resource IN ( <http://rdfh.ch/0001/a>, <http://rdfh.ch/0001/b> ) ) }""",
  )
}
