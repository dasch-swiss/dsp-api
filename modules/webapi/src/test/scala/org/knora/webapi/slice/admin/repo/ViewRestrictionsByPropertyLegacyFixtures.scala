/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.repo

/**
 * The RDF4J SparqlBuilder output that [[ViewRestrictionsByPropertyRepo]]'s queries were ported from,
 * captured verbatim before the port and kept as fixtures.
 *
 * `ViewRestrictionsByPropertyQuerySpec` compares the ported queries against these canonically — parsed by
 * Jena with the prefix map cleared — so the port is pinned to the same queries rather than to the old
 * builder's whitespace. Keys are `<builder>-<itemType>[-<arguments>]`; the arguments are the ones the spec
 * itself exercises.
 *
 * Do not reformat: these strings are a record of what the previous builder emitted.
 */
object ViewRestrictionsByPropertyLegacyFixtures {

  val byKey: Map[String, String] = Map(
    "drillDownCountQuery-All" ->
      """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
SELECT ( COUNT( DISTINCT ?resource ) AS ?cnt )
WHERE { ?resource knora-base:attachedToProject <http://rdfh.ch/projects/0001> ;
    knora-base:isDeleted false ;
    <http://www.knora.org/ontology/0001/anything#hasText> ?value .
?value knora-base:hasPermissions ?permissions ;
    knora-base:isDeleted false .
FILTER NOT EXISTS { ?value a knora-base:LinkValue . }
FILTER ( !( REGEX( ?permissions, "(^|[|])(V|M|D|CR) [^|]*knora-admin:UnknownUser" ) ) ) }""",
    "drillDownCountQuery-Comment" ->
      """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
SELECT ( COUNT( DISTINCT ?resource ) AS ?cnt )
WHERE { { ?resource knora-base:attachedToProject <http://rdfh.ch/projects/0001> ;
    knora-base:isDeleted false ;
    <http://www.knora.org/ontology/0001/anything#hasText> ?value .
?value knora-base:hasPermissions ?permissions ;
    knora-base:isDeleted false .
FILTER NOT EXISTS { ?value a knora-base:LinkValue . } }
?value knora-base:valueHasComment ?comment .
FILTER ( !( REGEX( ?permissions, "(^|[|])(V|M|D|CR) [^|]*knora-admin:UnknownUser" ) ) ) }""",
    "drillDownCountQuery-File" ->
      """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
SELECT ( COUNT( DISTINCT ?resource ) AS ?cnt )
WHERE { { ?resource knora-base:attachedToProject <http://rdfh.ch/projects/0001> ;
    knora-base:isDeleted false ;
    <http://www.knora.org/ontology/0001/anything#hasText> ?value .
?value knora-base:hasPermissions ?permissions ;
    knora-base:isDeleted false .
FILTER NOT EXISTS { ?value a knora-base:LinkValue . } }
{ ?value a ?fileClass .
?fileClass rdfs:subClassOf* knora-base:FileValue . }
FILTER ( !( REGEX( ?permissions, "(^|[|])(V|M|D|CR) [^|]*knora-admin:UnknownUser" ) ) ) }""",
    "drillDownCountQuery-Value" ->
      """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
SELECT ( COUNT( DISTINCT ?resource ) AS ?cnt )
WHERE { { ?resource knora-base:attachedToProject <http://rdfh.ch/projects/0001> ;
    knora-base:isDeleted false ;
    <http://www.knora.org/ontology/0001/anything#hasText> ?value .
?value knora-base:hasPermissions ?permissions ;
    knora-base:isDeleted false .
FILTER NOT EXISTS { ?value a knora-base:LinkValue . } }
FILTER NOT EXISTS { ?value a ?fileClass .
?fileClass rdfs:subClassOf* knora-base:FileValue . }
FILTER ( !( REGEX( ?permissions, "(^|[|])(V|M|D|CR) [^|]*knora-admin:UnknownUser" ) ) ) }""",
    "drillDownResourcePageQuery-All-0-25" ->
      """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
SELECT DISTINCT ?resource ?labelOrIri
WHERE { { ?resource knora-base:attachedToProject <http://rdfh.ch/projects/0001> ;
    knora-base:isDeleted false ;
    <http://www.knora.org/ontology/0001/anything#hasText> ?value .
?value knora-base:hasPermissions ?permissions ;
    knora-base:isDeleted false .
FILTER NOT EXISTS { ?value a knora-base:LinkValue . }
FILTER ( !( REGEX( ?permissions, "(^|[|])(V|M|D|CR) [^|]*knora-admin:UnknownUser" ) ) ) }
OPTIONAL { ?resource rdfs:label ?label . }
BIND( COALESCE( ?label, STR( ?resource ) ) AS ?labelOrIri ) }
ORDER BY ASC( ?labelOrIri ) ASC( ?resource )
LIMIT 25
OFFSET 0""",
    "drillDownResourcePageQuery-All-50-25" ->
      """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
SELECT DISTINCT ?resource ?labelOrIri
WHERE { { ?resource knora-base:attachedToProject <http://rdfh.ch/projects/0001> ;
    knora-base:isDeleted false ;
    <http://www.knora.org/ontology/0001/anything#hasText> ?value .
?value knora-base:hasPermissions ?permissions ;
    knora-base:isDeleted false .
FILTER NOT EXISTS { ?value a knora-base:LinkValue . }
FILTER ( !( REGEX( ?permissions, "(^|[|])(V|M|D|CR) [^|]*knora-admin:UnknownUser" ) ) ) }
OPTIONAL { ?resource rdfs:label ?label . }
BIND( COALESCE( ?label, STR( ?resource ) ) AS ?labelOrIri ) }
ORDER BY ASC( ?labelOrIri ) ASC( ?resource )
LIMIT 25
OFFSET 50""",
    "drillDownResourcePageQuery-Comment-0-25" ->
      """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
SELECT DISTINCT ?resource ?labelOrIri
WHERE { { { ?resource knora-base:attachedToProject <http://rdfh.ch/projects/0001> ;
    knora-base:isDeleted false ;
    <http://www.knora.org/ontology/0001/anything#hasText> ?value .
?value knora-base:hasPermissions ?permissions ;
    knora-base:isDeleted false .
FILTER NOT EXISTS { ?value a knora-base:LinkValue . } }
?value knora-base:valueHasComment ?comment .
FILTER ( !( REGEX( ?permissions, "(^|[|])(V|M|D|CR) [^|]*knora-admin:UnknownUser" ) ) ) }
OPTIONAL { ?resource rdfs:label ?label . }
BIND( COALESCE( ?label, STR( ?resource ) ) AS ?labelOrIri ) }
ORDER BY ASC( ?labelOrIri ) ASC( ?resource )
LIMIT 25
OFFSET 0""",
    "drillDownResourcePageQuery-Comment-50-25" ->
      """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
SELECT DISTINCT ?resource ?labelOrIri
WHERE { { { ?resource knora-base:attachedToProject <http://rdfh.ch/projects/0001> ;
    knora-base:isDeleted false ;
    <http://www.knora.org/ontology/0001/anything#hasText> ?value .
?value knora-base:hasPermissions ?permissions ;
    knora-base:isDeleted false .
FILTER NOT EXISTS { ?value a knora-base:LinkValue . } }
?value knora-base:valueHasComment ?comment .
FILTER ( !( REGEX( ?permissions, "(^|[|])(V|M|D|CR) [^|]*knora-admin:UnknownUser" ) ) ) }
OPTIONAL { ?resource rdfs:label ?label . }
BIND( COALESCE( ?label, STR( ?resource ) ) AS ?labelOrIri ) }
ORDER BY ASC( ?labelOrIri ) ASC( ?resource )
LIMIT 25
OFFSET 50""",
    "drillDownResourcePageQuery-File-0-25" ->
      """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
SELECT DISTINCT ?resource ?labelOrIri
WHERE { { { ?resource knora-base:attachedToProject <http://rdfh.ch/projects/0001> ;
    knora-base:isDeleted false ;
    <http://www.knora.org/ontology/0001/anything#hasText> ?value .
?value knora-base:hasPermissions ?permissions ;
    knora-base:isDeleted false .
FILTER NOT EXISTS { ?value a knora-base:LinkValue . } }
{ ?value a ?fileClass .
?fileClass rdfs:subClassOf* knora-base:FileValue . }
FILTER ( !( REGEX( ?permissions, "(^|[|])(V|M|D|CR) [^|]*knora-admin:UnknownUser" ) ) ) }
OPTIONAL { ?resource rdfs:label ?label . }
BIND( COALESCE( ?label, STR( ?resource ) ) AS ?labelOrIri ) }
ORDER BY ASC( ?labelOrIri ) ASC( ?resource )
LIMIT 25
OFFSET 0""",
    "drillDownResourcePageQuery-File-50-25" ->
      """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
SELECT DISTINCT ?resource ?labelOrIri
WHERE { { { ?resource knora-base:attachedToProject <http://rdfh.ch/projects/0001> ;
    knora-base:isDeleted false ;
    <http://www.knora.org/ontology/0001/anything#hasText> ?value .
?value knora-base:hasPermissions ?permissions ;
    knora-base:isDeleted false .
FILTER NOT EXISTS { ?value a knora-base:LinkValue . } }
{ ?value a ?fileClass .
?fileClass rdfs:subClassOf* knora-base:FileValue . }
FILTER ( !( REGEX( ?permissions, "(^|[|])(V|M|D|CR) [^|]*knora-admin:UnknownUser" ) ) ) }
OPTIONAL { ?resource rdfs:label ?label . }
BIND( COALESCE( ?label, STR( ?resource ) ) AS ?labelOrIri ) }
ORDER BY ASC( ?labelOrIri ) ASC( ?resource )
LIMIT 25
OFFSET 50""",
    "drillDownResourcePageQuery-Value-0-25" ->
      """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
SELECT DISTINCT ?resource ?labelOrIri
WHERE { { { ?resource knora-base:attachedToProject <http://rdfh.ch/projects/0001> ;
    knora-base:isDeleted false ;
    <http://www.knora.org/ontology/0001/anything#hasText> ?value .
?value knora-base:hasPermissions ?permissions ;
    knora-base:isDeleted false .
FILTER NOT EXISTS { ?value a knora-base:LinkValue . } }
FILTER NOT EXISTS { ?value a ?fileClass .
?fileClass rdfs:subClassOf* knora-base:FileValue . }
FILTER ( !( REGEX( ?permissions, "(^|[|])(V|M|D|CR) [^|]*knora-admin:UnknownUser" ) ) ) }
OPTIONAL { ?resource rdfs:label ?label . }
BIND( COALESCE( ?label, STR( ?resource ) ) AS ?labelOrIri ) }
ORDER BY ASC( ?labelOrIri ) ASC( ?resource )
LIMIT 25
OFFSET 0""",
    "drillDownResourcePageQuery-Value-50-25" ->
      """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
SELECT DISTINCT ?resource ?labelOrIri
WHERE { { { ?resource knora-base:attachedToProject <http://rdfh.ch/projects/0001> ;
    knora-base:isDeleted false ;
    <http://www.knora.org/ontology/0001/anything#hasText> ?value .
?value knora-base:hasPermissions ?permissions ;
    knora-base:isDeleted false .
FILTER NOT EXISTS { ?value a knora-base:LinkValue . } }
FILTER NOT EXISTS { ?value a ?fileClass .
?fileClass rdfs:subClassOf* knora-base:FileValue . }
FILTER ( !( REGEX( ?permissions, "(^|[|])(V|M|D|CR) [^|]*knora-admin:UnknownUser" ) ) ) }
OPTIONAL { ?resource rdfs:label ?label . }
BIND( COALESCE( ?label, STR( ?resource ) ) AS ?labelOrIri ) }
ORDER BY ASC( ?labelOrIri ) ASC( ?resource )
LIMIT 25
OFFSET 50""",
    "drillDownRowsQuery-All-one" ->
      """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
SELECT DISTINCT ?resource ?resClass ?value ?creator ?permissions ?fileClass ?comment ?label
WHERE { ?resource knora-base:attachedToProject <http://rdfh.ch/projects/0001> ;
    knora-base:isDeleted false ;
    <http://www.knora.org/ontology/0001/anything#hasText> ?value .
?value knora-base:hasPermissions ?permissions ;
    knora-base:isDeleted false .
FILTER NOT EXISTS { ?value a knora-base:LinkValue . }
?resource a ?resClass .
?value knora-base:attachedToUser ?creator .
OPTIONAL { ?resource rdfs:label ?label . }
OPTIONAL { ?value a ?fileClass .
?fileClass rdfs:subClassOf* knora-base:FileValue . }
OPTIONAL { ?value knora-base:valueHasComment ?comment . }
FILTER ( !( REGEX( ?permissions, "(^|[|])(V|M|D|CR) [^|]*knora-admin:UnknownUser" ) ) )
FILTER ( ?resource IN ( <http://rdfh.ch/0001/a> ) ) }
ORDER BY ASC( ?label ) ASC( ?resource ) ASC( ?value )""",
    "drillDownRowsQuery-All-two" ->
      """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
SELECT DISTINCT ?resource ?resClass ?value ?creator ?permissions ?fileClass ?comment ?label
WHERE { ?resource knora-base:attachedToProject <http://rdfh.ch/projects/0001> ;
    knora-base:isDeleted false ;
    <http://www.knora.org/ontology/0001/anything#hasText> ?value .
?value knora-base:hasPermissions ?permissions ;
    knora-base:isDeleted false .
FILTER NOT EXISTS { ?value a knora-base:LinkValue . }
?resource a ?resClass .
?value knora-base:attachedToUser ?creator .
OPTIONAL { ?resource rdfs:label ?label . }
OPTIONAL { ?value a ?fileClass .
?fileClass rdfs:subClassOf* knora-base:FileValue . }
OPTIONAL { ?value knora-base:valueHasComment ?comment . }
FILTER ( !( REGEX( ?permissions, "(^|[|])(V|M|D|CR) [^|]*knora-admin:UnknownUser" ) ) )
FILTER ( ?resource IN ( <http://rdfh.ch/0001/a>, <http://rdfh.ch/0001/b> ) ) }
ORDER BY ASC( ?label ) ASC( ?resource ) ASC( ?value )""",
    "drillDownRowsQuery-Comment-one" ->
      """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
SELECT DISTINCT ?resource ?resClass ?value ?creator ?permissions ?fileClass ?comment ?label
WHERE { { ?resource knora-base:attachedToProject <http://rdfh.ch/projects/0001> ;
    knora-base:isDeleted false ;
    <http://www.knora.org/ontology/0001/anything#hasText> ?value .
?value knora-base:hasPermissions ?permissions ;
    knora-base:isDeleted false .
FILTER NOT EXISTS { ?value a knora-base:LinkValue . }
?resource a ?resClass .
?value knora-base:attachedToUser ?creator .
OPTIONAL { ?resource rdfs:label ?label . }
OPTIONAL { ?value a ?fileClass .
?fileClass rdfs:subClassOf* knora-base:FileValue . }
OPTIONAL { ?value knora-base:valueHasComment ?comment . } }
?value knora-base:valueHasComment ?comment .
FILTER ( !( REGEX( ?permissions, "(^|[|])(V|M|D|CR) [^|]*knora-admin:UnknownUser" ) ) )
FILTER ( ?resource IN ( <http://rdfh.ch/0001/a> ) ) }
ORDER BY ASC( ?label ) ASC( ?resource ) ASC( ?value )""",
    "drillDownRowsQuery-Comment-two" ->
      """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
SELECT DISTINCT ?resource ?resClass ?value ?creator ?permissions ?fileClass ?comment ?label
WHERE { { ?resource knora-base:attachedToProject <http://rdfh.ch/projects/0001> ;
    knora-base:isDeleted false ;
    <http://www.knora.org/ontology/0001/anything#hasText> ?value .
?value knora-base:hasPermissions ?permissions ;
    knora-base:isDeleted false .
FILTER NOT EXISTS { ?value a knora-base:LinkValue . }
?resource a ?resClass .
?value knora-base:attachedToUser ?creator .
OPTIONAL { ?resource rdfs:label ?label . }
OPTIONAL { ?value a ?fileClass .
?fileClass rdfs:subClassOf* knora-base:FileValue . }
OPTIONAL { ?value knora-base:valueHasComment ?comment . } }
?value knora-base:valueHasComment ?comment .
FILTER ( !( REGEX( ?permissions, "(^|[|])(V|M|D|CR) [^|]*knora-admin:UnknownUser" ) ) )
FILTER ( ?resource IN ( <http://rdfh.ch/0001/a>, <http://rdfh.ch/0001/b> ) ) }
ORDER BY ASC( ?label ) ASC( ?resource ) ASC( ?value )""",
    "drillDownRowsQuery-File-one" ->
      """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
SELECT DISTINCT ?resource ?resClass ?value ?creator ?permissions ?fileClass ?comment ?label
WHERE { { ?resource knora-base:attachedToProject <http://rdfh.ch/projects/0001> ;
    knora-base:isDeleted false ;
    <http://www.knora.org/ontology/0001/anything#hasText> ?value .
?value knora-base:hasPermissions ?permissions ;
    knora-base:isDeleted false .
FILTER NOT EXISTS { ?value a knora-base:LinkValue . }
?resource a ?resClass .
?value knora-base:attachedToUser ?creator .
OPTIONAL { ?resource rdfs:label ?label . }
OPTIONAL { ?value a ?fileClass .
?fileClass rdfs:subClassOf* knora-base:FileValue . }
OPTIONAL { ?value knora-base:valueHasComment ?comment . } }
{ ?value a ?fileClass .
?fileClass rdfs:subClassOf* knora-base:FileValue . }
FILTER ( !( REGEX( ?permissions, "(^|[|])(V|M|D|CR) [^|]*knora-admin:UnknownUser" ) ) )
FILTER ( ?resource IN ( <http://rdfh.ch/0001/a> ) ) }
ORDER BY ASC( ?label ) ASC( ?resource ) ASC( ?value )""",
    "drillDownRowsQuery-File-two" ->
      """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
SELECT DISTINCT ?resource ?resClass ?value ?creator ?permissions ?fileClass ?comment ?label
WHERE { { ?resource knora-base:attachedToProject <http://rdfh.ch/projects/0001> ;
    knora-base:isDeleted false ;
    <http://www.knora.org/ontology/0001/anything#hasText> ?value .
?value knora-base:hasPermissions ?permissions ;
    knora-base:isDeleted false .
FILTER NOT EXISTS { ?value a knora-base:LinkValue . }
?resource a ?resClass .
?value knora-base:attachedToUser ?creator .
OPTIONAL { ?resource rdfs:label ?label . }
OPTIONAL { ?value a ?fileClass .
?fileClass rdfs:subClassOf* knora-base:FileValue . }
OPTIONAL { ?value knora-base:valueHasComment ?comment . } }
{ ?value a ?fileClass .
?fileClass rdfs:subClassOf* knora-base:FileValue . }
FILTER ( !( REGEX( ?permissions, "(^|[|])(V|M|D|CR) [^|]*knora-admin:UnknownUser" ) ) )
FILTER ( ?resource IN ( <http://rdfh.ch/0001/a>, <http://rdfh.ch/0001/b> ) ) }
ORDER BY ASC( ?label ) ASC( ?resource ) ASC( ?value )""",
    "drillDownRowsQuery-Value-one" ->
      """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
SELECT DISTINCT ?resource ?resClass ?value ?creator ?permissions ?fileClass ?comment ?label
WHERE { { ?resource knora-base:attachedToProject <http://rdfh.ch/projects/0001> ;
    knora-base:isDeleted false ;
    <http://www.knora.org/ontology/0001/anything#hasText> ?value .
?value knora-base:hasPermissions ?permissions ;
    knora-base:isDeleted false .
FILTER NOT EXISTS { ?value a knora-base:LinkValue . }
?resource a ?resClass .
?value knora-base:attachedToUser ?creator .
OPTIONAL { ?resource rdfs:label ?label . }
OPTIONAL { ?value a ?fileClass .
?fileClass rdfs:subClassOf* knora-base:FileValue . }
OPTIONAL { ?value knora-base:valueHasComment ?comment . } }
FILTER NOT EXISTS { ?value a ?fileClass .
?fileClass rdfs:subClassOf* knora-base:FileValue . }
FILTER ( !( REGEX( ?permissions, "(^|[|])(V|M|D|CR) [^|]*knora-admin:UnknownUser" ) ) )
FILTER ( ?resource IN ( <http://rdfh.ch/0001/a> ) ) }
ORDER BY ASC( ?label ) ASC( ?resource ) ASC( ?value )""",
    "drillDownRowsQuery-Value-two" ->
      """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
SELECT DISTINCT ?resource ?resClass ?value ?creator ?permissions ?fileClass ?comment ?label
WHERE { { ?resource knora-base:attachedToProject <http://rdfh.ch/projects/0001> ;
    knora-base:isDeleted false ;
    <http://www.knora.org/ontology/0001/anything#hasText> ?value .
?value knora-base:hasPermissions ?permissions ;
    knora-base:isDeleted false .
FILTER NOT EXISTS { ?value a knora-base:LinkValue . }
?resource a ?resClass .
?value knora-base:attachedToUser ?creator .
OPTIONAL { ?resource rdfs:label ?label . }
OPTIONAL { ?value a ?fileClass .
?fileClass rdfs:subClassOf* knora-base:FileValue . }
OPTIONAL { ?value knora-base:valueHasComment ?comment . } }
FILTER NOT EXISTS { ?value a ?fileClass .
?fileClass rdfs:subClassOf* knora-base:FileValue . }
FILTER ( !( REGEX( ?permissions, "(^|[|])(V|M|D|CR) [^|]*knora-admin:UnknownUser" ) ) )
FILTER ( ?resource IN ( <http://rdfh.ch/0001/a>, <http://rdfh.ch/0001/b> ) ) }
ORDER BY ASC( ?label ) ASC( ?resource ) ASC( ?value )""",
    "valueCountsQuery-All" ->
      """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
SELECT ?permissions ( COUNT( DISTINCT ?value ) AS ?cnt )
WHERE { ?resource knora-base:attachedToProject <http://rdfh.ch/projects/0001> ;
    knora-base:isDeleted false ;
    <http://www.knora.org/ontology/0001/anything#hasText> ?value .
?value knora-base:hasPermissions ?permissions ;
    knora-base:isDeleted false .
FILTER NOT EXISTS { ?value a knora-base:LinkValue . } }
GROUP BY ?permissions""",
    "valueCountsQuery-Comment" ->
      """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
SELECT ?permissions ( COUNT( DISTINCT ?value ) AS ?cnt )
WHERE { { ?resource knora-base:attachedToProject <http://rdfh.ch/projects/0001> ;
    knora-base:isDeleted false ;
    <http://www.knora.org/ontology/0001/anything#hasText> ?value .
?value knora-base:hasPermissions ?permissions ;
    knora-base:isDeleted false .
FILTER NOT EXISTS { ?value a knora-base:LinkValue . } }
?value knora-base:valueHasComment ?comment . }
GROUP BY ?permissions""",
    "valueCountsQuery-File" ->
      """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
SELECT ?permissions ( COUNT( DISTINCT ?value ) AS ?cnt )
WHERE { { ?resource knora-base:attachedToProject <http://rdfh.ch/projects/0001> ;
    knora-base:isDeleted false ;
    <http://www.knora.org/ontology/0001/anything#hasText> ?value .
?value knora-base:hasPermissions ?permissions ;
    knora-base:isDeleted false .
FILTER NOT EXISTS { ?value a knora-base:LinkValue . } }
{ ?value a ?fileClass .
?fileClass rdfs:subClassOf* knora-base:FileValue . } }
GROUP BY ?permissions""",
    "valueCountsQuery-Value" ->
      """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
SELECT ?permissions ( COUNT( DISTINCT ?value ) AS ?cnt )
WHERE { { ?resource knora-base:attachedToProject <http://rdfh.ch/projects/0001> ;
    knora-base:isDeleted false ;
    <http://www.knora.org/ontology/0001/anything#hasText> ?value .
?value knora-base:hasPermissions ?permissions ;
    knora-base:isDeleted false .
FILTER NOT EXISTS { ?value a knora-base:LinkValue . } }
FILTER NOT EXISTS { ?value a ?fileClass .
?fileClass rdfs:subClassOf* knora-base:FileValue . } }
GROUP BY ?permissions""",
  )
}
