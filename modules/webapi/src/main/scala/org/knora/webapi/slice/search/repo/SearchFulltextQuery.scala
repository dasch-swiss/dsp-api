/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.search.repo

import org.eclipse.rdf4j.model.vocabulary.RDFS
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf
import zio.IO

import dsp.errors.SparqlGenerationException
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.common.KnoraIris.ResourceClassIri
import org.knora.webapi.slice.common.QueryBuilderHelper
import org.knora.webapi.slice.common.repo.rdf.Vocabulary.KnoraBase
import org.knora.webapi.util.ApacheLuceneSupport.LuceneQueryString

object SearchFulltextQuery extends QueryBuilderHelper {

  private val luceneHitLimit = OntologyConstants.Fuseki.luceneHitLimit

  // The overall query structure (SELECT with GROUP_CONCAT, subqueries, BIND/COALESCE, SUBSTR)
  // is assembled via string interpolation because these features are not supported by the
  // rdf4j SparqlBuilder. Individual values — especially user-supplied search terms and dynamic
  // IRIs — are built through rdf4j's Rdf.literalOf / Rdf.iri to ensure proper escaping and
  // guard against SPARQL injection.
  //
  // Resource-ness and value-ness are asserted by the presence of a datatype property rather than by walking
  // rdfs:subClassOf* to knora-base:Resource / knora-base:Value once per Lucene hit. On a prod-mirrored store the
  // per-hit walks dominated the cost: `count/der` was 82.50s and dropped to 13.09s once they were removed (measured
  // on stage, 2026-07-29). The substitutions are exact, not heuristic:
  //
  //  - Resource-ness (1a): knora-base:creationDate declares `subjectClassConstraint :Resource` with cardinality 1 on
  //    :Resource, so it is present on every resource and on nothing else (verified on stage: zero types carry it
  //    outside the :Resource closure, zero resources lack it). `?resource a ?resourceClass` is emitted only when a
  //    class restriction is requested — see filterByProjectAndResourceClass — so the type join is paid only then.
  //  - Value-ness (1c): knora-base:valueCreationDate declares `subjectClassConstraint :Value`, the value-side analogue
  //    (verified: zero indexed values lack it). The direct-type FILTER NOT EXISTS on LinkValue/ListValue is equivalent
  //    to the old `!=` on a walked type — the two can only diverge for a value carrying more than one asserted
  //    rdf:type, and no LinkValue/ListValue on stage does — and it closes the multi-type leak the walked form had.
  //  - The value branch no longer walks `?property rdfs:subPropertyOf* knora-base:hasValue` (1b): its only job was to
  //    reject non-resource subjects, which the resource-ness probe (1a) already does — the subjects it excluded
  //    (previousValue links, standoff references) have no creationDate.
  //  - There is no outer SELECT DISTINCT (1d): `GROUP BY ?resource` already deduplicates the page query, and the count
  //    branch keeps COUNT(DISTINCT ?resource). The inner SELECT DISTINCT ?matchingSubject stays.
  //
  // TODO(DEV-6850): the creationDate / valueCreationDate probes are a stopgap. They mean "when was this made", not
  // "this is a resource / value"; DEV-6850 materialises the entailed `a knora-base:Resource` / `a knora-base:Value`
  // and introduces one shared guard for all the sites that ask this question. Replace these probes with that guard
  // when it lands. See also SearchQueries.selectCountByLabel, which carries the same stopgap.
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
          s"""SELECT ?resource
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
         |            ?matchingSubject <http://jena.apache.org/text#query> ($searchLiteral $luceneHitLimit) .$standoffFilter
         |        }
         |    }
         |    OPTIONAL {
         |        ?matchingSubject knora-base:valueCreationDate ?valueCreationDate .
         |        FILTER NOT EXISTS { ?matchingSubject a knora-base:LinkValue . }
         |        FILTER NOT EXISTS { ?matchingSubject a knora-base:ListValue . }
         |        ?containingResource ?property ?matchingSubject .
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
         |    ?resource knora-base:creationDate ?resourceCreationDate .
         |    ${filterByProjectAndResourceClass(limitToProject, limitToResourceClass)}$fileValuesBlock
         |    FILTER NOT EXISTS {
         |        ?resource knora-base:isDeleted true .
         |    }
         |}
         |$groupOrderOffset
         |LIMIT $limit
         |""".stripMargin
    }

  /**
   * Emits the optional project and resource-class restrictions. Duplicated from
   * SearchQueries.filterByProjectAndResourceClass (D6): the two queries are separate and the shared helper would
   * couple them, but they must agree on the load-bearing detail — the class restriction walks the whole
   * `rdfs:subClassOf*` closure, never `subClassOf?`. The subclass closure is not materialised and there is no
   * query-time inference, so zero-or-one silently excludes every class two or more hops below the target (DEV-6833).
   *
   * `?resource a ?resourceClass` is emitted here rather than in the query body so the type join is only paid when a
   * class restriction is actually requested — the resource-ness probe in build() is unconditional and stands alone.
   * Project restriction is emitted before the class join: `attachedToProject` on the already-bound `?resource` is a
   * cheap, selective probe (engine Fact 1 — order relative to the joins is the plan).
   */
  private def filterByProjectAndResourceClass(
    limitToProject: Option[ProjectIri],
    limitToResourceClass: Option[ResourceClassIri],
  ): String = {
    val projectPattern = limitToProject
      .map(toRdfIri)
      .map(prj => variable("resource").has(KnoraBase.attachedToProject, prj).getQueryString)

    val resourceClassPatterns = limitToResourceClass.map(toRdfIri).map { cls =>
      List(
        variable("resource").isA(variable("resourceClass")).getQueryString,
        variable("resourceClass").has(zeroOrMore(RDFS.SUBCLASSOF), cls).getQueryString,
      ).mkString("\n")
    }

    List(projectPattern, resourceClassPatterns).flatten.mkString("\n")
  }
}
