/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.search.repo

import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf
import zio.IO

import dsp.errors.SparqlGenerationException
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.common.KnoraIris.ResourceClassIri
import org.knora.webapi.slice.common.QueryBuilderHelper
import org.knora.webapi.util.ApacheLuceneSupport.LuceneQueryString

object SearchFulltextQuery extends QueryBuilderHelper {

  // The overall query structure (SELECT with GROUP_CONCAT, subqueries, BIND/COALESCE, SUBSTR)
  // is assembled via string interpolation because these features are not supported by the
  // rdf4j SparqlBuilder. Individual values — especially user-supplied search terms and dynamic
  // IRIs — are built through rdf4j's Rdf.literalOf / Rdf.iri to ensure proper escaping and
  // guard against SPARQL injection.
  def build(
    searchTerms: LuceneQueryString,
    limitToProject: Option[ProjectIri],
    limitToResourceClass: Option[ResourceClassIri],
    limitToStandoffClass: Option[SmartIri],
    returnFiles: Boolean,
    separator: Option[Char],
    limit: Int,
    offset: Int,
    countQuery: Boolean,
  ): IO[SparqlGenerationException, String] =
    for {
      _ <- failIf(!countQuery && separator.isEmpty, "Separator expected for non count query, but none given")
    } yield {
      val selectClause =
        if (countQuery)
          "SELECT (COUNT(DISTINCT ?resource) AS ?count)"
        else
          s"""SELECT DISTINCT ?resource
             |       (GROUP_CONCAT(IF(BOUND(?valueObject), STR(?valueObject), ""); SEPARATOR="${separator.get}") AS ?valueObjectConcat)""".stripMargin

      // Escape user-supplied search terms via rdf4j to prevent SPARQL injection
      val searchLiteral = Rdf.literalOf(searchTerms.getQueryString).getQueryString

      val standoffFilter = limitToStandoffClass.fold("") { standoffClassIri =>
        val standoffIri = toRdfIri(standoffClassIri).getQueryString
        // Escape each individual term via rdf4j before embedding in REGEX
        val regexFilters = searchTerms.getSingleTerms.map { term =>
          val termLiteral = Rdf.literalOf(term).getQueryString
          s"""    FILTER REGEX(?markedup, $termLiteral, "i")"""
        }.mkString("\n")

        s"""
           |    ?matchingSubject a knora-base:TextValue ;
           |        knora-base:valueHasString ?literal ;
           |        knora-base:valueHasStandoff ?standoffNode .
           |    ?standoffNode a $standoffIri ;
           |        knora-base:standoffTagHasStart ?start ;
           |        knora-base:standoffTagHasEnd ?end .
           |    BIND(SUBSTR(?literal, ?start+1, ?end - ?start) AS ?markedup)
           |$regexFilters""".stripMargin
      }

      val resourceClassFilter = limitToResourceClass.fold("") { rc =>
        val iri = toRdfIri(rc).getQueryString
        s"\n    ?resourceClass rdfs:subClassOf* $iri ."
      }

      val projectFilter = limitToProject.fold("") { p =>
        val iri = toRdfIri(p).getQueryString
        s"\n    ?resource knora-base:attachedToProject $iri ."
      }

      val fileValuesBlock =
        if (returnFiles)
          """
            |    OPTIONAL {
            |        ?fileValueProp rdfs:subPropertyOf* knora-base:hasFileValue .
            |        ?resource ?fileValueProp ?valueObject .
            |    }""".stripMargin
        else ""

      val groupOrderOffset =
        if (countQuery) ""
        else
          s"""
             |GROUP BY ?resource
             |ORDER BY ?resource
             |OFFSET $offset""".stripMargin

      s"""PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
         |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
         |$selectClause
         |WHERE {
         |    {
         |        SELECT DISTINCT ?matchingSubject WHERE {
         |            ?matchingSubject <http://jena.apache.org/text#query> $searchLiteral .$standoffFilter
         |        }
         |    }
         |    OPTIONAL {
         |        ?matchingSubject a ?valueObjectType .
         |        ?valueObjectType rdfs:subClassOf* knora-base:Value .
         |        FILTER(?valueObjectType != knora-base:LinkValue && ?valueObjectType != knora-base:ListValue)
         |        ?containingResource ?property ?matchingSubject .
         |        ?property rdfs:subPropertyOf* knora-base:hasValue .
         |        FILTER NOT EXISTS {
         |            ?matchingSubject knora-base:isDeleted true .
         |        }
         |        BIND(?matchingSubject AS ?valueObject)
         |    }
         |    OPTIONAL {
         |        ?matchingSubject a knora-base:ListNode .
         |        ?matchingSubject knora-base:hasSubListNode* ?subListNode .
         |        ?listValue knora-base:valueHasListNode ?subListNode .
         |        ?subjectWithListValue ?predicate ?listValue .
         |        FILTER NOT EXISTS {
         |            ?matchingSubject knora-base:isDeleted true .
         |        }
         |        BIND(?listValue AS ?valueObject)
         |    }
         |    BIND(COALESCE(?containingResource, ?subjectWithListValue, ?matchingSubject) AS ?resource)
         |    ?resource a ?resourceClass .
         |    ?resourceClass rdfs:subClassOf* knora-base:Resource .$resourceClassFilter$projectFilter$fileValuesBlock
         |    FILTER NOT EXISTS {
         |        ?resource knora-base:isDeleted true .
         |    }
         |}
         |$groupOrderOffset
         |LIMIT $limit
         |""".stripMargin
    }
}
