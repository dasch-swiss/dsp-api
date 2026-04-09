/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.search.repo

import zio.IO

import dsp.errors.SparqlGenerationException
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.common.KnoraIris.ResourceClassIri
import org.knora.webapi.slice.common.QueryBuilderHelper
import org.knora.webapi.util.ApacheLuceneSupport.LuceneQueryString

object SearchFulltextQuery extends QueryBuilderHelper {

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

      val standoffFilter = limitToStandoffClass.fold("") { standoffClassIri =>
        val regexFilters = searchTerms.getSingleTerms.map { term =>
          s"""    FILTER REGEX(?markedup, '$term', "i")"""
        }.mkString("\n")

        s"""
           |    ?matchingSubject a knora-base:TextValue ;
           |        knora-base:valueHasString ?literal ;
           |        knora-base:valueHasStandoff ?standoffNode .
           |    ?standoffNode a <$standoffClassIri> ;
           |        knora-base:standoffTagHasStart ?start ;
           |        knora-base:standoffTagHasEnd ?end .
           |    BIND(SUBSTR(?literal, ?start+1, ?end - ?start) AS ?markedup)
           |$regexFilters""".stripMargin
      }

      val resourceClassFilter = limitToResourceClass.fold("") { rc =>
        val iri = rc.smartIri.toInternalSchema.toIri
        s"\n    ?resourceClass rdfs:subClassOf* <$iri> ."
      }

      val projectFilter = limitToProject.fold("") { p =>
        s"\n    ?resource knora-base:attachedToProject <${p.value}> ."
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
         |            ?matchingSubject <http://jena.apache.org/text#query> '${searchTerms.getQueryString}' .$standoffFilter
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
