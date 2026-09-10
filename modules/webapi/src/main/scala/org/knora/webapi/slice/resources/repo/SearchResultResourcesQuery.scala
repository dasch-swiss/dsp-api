/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo

import org.knora.sparqlbuilder.*
import org.knora.webapi.IRI
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Construct

/**
 * Builds the CONSTRUCT query that loads the main resources of a search result with all of their
 * values, their link targets and — optionally — the standoff markup of their text values.
 *
 * Deleted resources and values are never returned, and there is no property, UUID or version
 * filtering: a search result is always the current, undeleted state of the matched resources.
 */
object SearchResultResourcesQuery {

  /**
   * @param resourceIris  the resources to load; must not be empty.
   * @param queryStandoff when true, the standoff markup of text values is loaded as well.
   */
  def build(resourceIris: Seq[IRI], queryStandoff: Boolean): Construct =
    Construct(
      sparql"""|PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
               |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
               |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
               |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
               |
               |CONSTRUCT {
               |  ?resource a knora-base:Resource ;
               |    knora-base:isMainResource true ;
               |    knora-base:attachedToProject ?resourceProject ;
               |    rdfs:label ?label ;
               |    rdf:type ?resourceType ;
               |    knora-base:attachedToUser ?resourceCreator ;
               |    knora-base:hasPermissions ?resourcePermissions ;
               |    knora-base:creationDate ?creationDate ;
               |    knora-base:lastModificationDate ?lastModificationDate ;
               |    knora-base:hasResourceAuthorship ?resourceAuthorship .
               |  ?resource knora-base:isDeleted false .
               |  ?resource knora-base:hasValue ?valueObject ;
               |    ?resourceValueProperty ?valueObject .
               |  ?valueObject ?valueObjectProperty ?valueObjectValue ;
               |    knora-base:valueHasUUID ?currentValueUUID ;
               |    knora-base:hasPermissions ?currentValuePermissions .
               |  ${sparql"""|?valueObject knora-base:valueHasStandoff ?standoffNode .
                             |?standoffNode ?standoffProperty ?standoffValue ;
                             |  knora-base:targetHasOriginalXMLID ?targetOriginalXMLID .""".when(queryStandoff)}
               |  ?resource knora-base:hasLinkTo ?referredResource ;
               |    ?resourceLinkProperty ?referredResource .
               |  ?referredResource a knora-base:Resource ;
               |    ?referredResourcePred ?referredResourceObj .
               |} WHERE {
               |  ${Fragments.values(Variable("resource"), resourceIris.map(Iri.unsafeFrom))}
               |  {
               |    ?resource rdf:type ?resourceType .
               |    ?resourceType rdfs:subClassOf* knora-base:Resource .
               |  }
               |  ?resource knora-base:attachedToProject ?resourceProject ;
               |    knora-base:attachedToUser ?resourceCreator ;
               |    knora-base:hasPermissions ?resourcePermissions ;
               |    knora-base:creationDate ?creationDate ;
               |    rdfs:label ?label .
               |  ?resource knora-base:isDeleted false .
               |  OPTIONAL { ?resource knora-base:lastModificationDate ?lastModificationDate . }
               |  OPTIONAL { ?resource knora-base:hasResourceAuthorship ?resourceAuthorship . }
               |  OPTIONAL {
               |    ?resource ?resourceValueProperty ?valueObject .
               |    ?resourceValueProperty rdfs:subPropertyOf* knora-base:hasValue .
               |    ?valueObject knora-base:hasPermissions ?currentValuePermissions .
               |    ${
          // The UNION nesting is load-bearing: with standoff the standoff branch joins the value branch in
          // their own UNION, and that whole UNION is in turn one branch of the link UNION, as the previous
          // builder emitted it.
          if (queryStandoff)
            sparql"""|{
                   |  {
                   |    ?valueObject a ?valueObjectType ;
                   |      ?valueObjectProperty ?valueObjectValue .
                   |    FILTER(?valueObjectProperty != knora-base:valueHasStandoff && ?valueObjectProperty != knora-base:hasPermissions)
                   |  } UNION {
                   |    ?valueObject knora-base:valueHasStandoff ?standoffNode .
                   |    ?standoffNode ?standoffProperty ?standoffValue ;
                   |      knora-base:standoffTagHasStartIndex ?startIndex .
                   |    OPTIONAL {
                   |      ?standoffTag knora-base:standoffTagHasInternalReference ?targetStandoffTag .
                   |      ?targetStandoffTag knora-base:standoffTagHasOriginalXMLID ?targetOriginalXMLID .
                   |    }
                   |    FILTER(?startIndex >= 0)
                   |  }
                   |} UNION {
                   |  ?valueObject a knora-base:LinkValue ;
                   |    rdf:predicate ?resourceLinkProperty ;
                   |    rdf:object ?referredResource .
                   |  ?referredResource ?referredResourcePred ?referredResourceObj ;
                   |    knora-base:isDeleted false .
                   |}"""
          else
            sparql"""|{
                   |  ?valueObject a ?valueObjectType ;
                   |    ?valueObjectProperty ?valueObjectValue .
                   |  FILTER(?valueObjectProperty != knora-base:valueHasStandoff && ?valueObjectProperty != knora-base:hasPermissions)
                   |} UNION {
                   |  ?valueObject a knora-base:LinkValue ;
                   |    rdf:predicate ?resourceLinkProperty ;
                   |    rdf:object ?referredResource .
                   |  ?referredResource ?referredResourcePred ?referredResourceObj ;
                   |    knora-base:isDeleted false .
                   |}"""
        }
               |  }
               |}""".render,
    )
}
