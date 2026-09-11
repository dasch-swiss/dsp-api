/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.search.repo

import zio.IO
import zio.ZIO

import dsp.errors.SparqlGenerationException
import org.knora.sparqlbuilder.*
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.common.KnoraIris.ResourceClassIri
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Select
import org.knora.webapi.util.ApacheLuceneSupport.LuceneQueryString

object SearchFulltextQuery {

  private val luceneHitLimit = Literal.int(OntologyConstants.Fuseki.luceneHitLimit)

  private def failIf(condition: Boolean, message: String): IO[SparqlGenerationException, Unit] =
    ZIO.fail(SparqlGenerationException(message)).when(condition).unit

  // The standoff restriction, mirrored inside the inner Lucene subquery. Shared by build and buildProbe so the
  // breadth probe measures exactly the candidate set the real query starts from and cannot drift from it.
  private def standoffFilterClause(
    searchTerms: LuceneQueryString,
    limitToStandoffClass: Option[SmartIri],
  ): Fragment =
    limitToStandoffClass.whenSome { standoffClassIri =>
      val standoffIri = Iri.unsafeFrom(standoffClassIri.toInternalSchema.toIri)
      // Each individual term enters the REGEX through a typed literal hole, which escapes it.
      val regexFilters = searchTerms.getSingleTerms.map { term =>
        sparql"""FILTER REGEX(?markedup, ${Literal.string(term)}, "i")"""
      }.joinLines

      sparql"""|?matchingSubject a knora-base:TextValue ;
               |    knora-base:valueHasString ?literal ;
               |    knora-base:valueHasStandoff ?standoffNode .
               |?standoffNode a $standoffIri ;
               |    knora-base:standoffTagHasStart ?start ;
               |    knora-base:standoffTagHasEnd ?end .
               |BIND(SUBSTR(?literal, ?start+1, ?end - ?start) AS ?markedup)
               |$regexFilters"""
    }

  /**
   * The breadth probe (DEV-6864, PROBE): a `COUNT(*)` over the exact inner Lucene subquery of [[build]] — the
   * post-Lucene candidate count, before the value/list/resource joins the real query pays. It mirrors only the
   * standoff filter, which lives inside that subquery; `limitToProject` / `limitToResourceClass` live in the outer
   * query and are not mirrored (they filter after the joins and do not reduce the candidate count — see the plan).
   * Run raced against the real query, this measures how broad a term is without paying the joins.
   */
  def buildProbe(searchTerms: LuceneQueryString, limitToStandoffClass: Option[SmartIri]): Select = {
    val searchLiteral = Literal.string(searchTerms.getQueryString)
    Select.searchProbe(
      sparql"""|PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
               |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
               |SELECT (COUNT(*) AS ?count)
               |WHERE {
               |    {
               |        SELECT DISTINCT ?matchingSubject WHERE {
               |            ?matchingSubject <http://jena.apache.org/text#query> ($searchLiteral $luceneHitLimit) .
               |            ${standoffFilterClause(searchTerms, limitToStandoffClass)}
               |        }
               |    }
               |}""".render,
    )
  }

  // The overall query structure (SELECT with GROUP_CONCAT, subqueries, BIND/COALESCE, SUBSTR) is written as a
  // whole-query `sparql"..."` template: the fixed SPARQL stays SPARQL, and only dynamic values and dynamic
  // structure are interpolated. Individual values — especially user-supplied search terms and dynamic IRIs —
  // go through typed holes (Literal, Iri), which escape at construction and guard against SPARQL injection.
  //
  // Resource-ness and value-ness are asserted by the presence of a datatype property rather than by walking
  // rdfs:subClassOf* to knora-base:Resource / knora-base:Value once per Lucene hit. On a prod-mirrored store the
  // per-hit walks dominated the cost: `count/der` was 82.50s and dropped to 13.09s once they were removed (measured
  // on stage, 2026-07-29). The substitutions are exact, not heuristic:
  //
  //  - Resource-ness (1a): knora-base:creationDate declares `subjectClassConstraint :Resource` with cardinality 1 on
  //    :Resource, so it is present on every resource and on nothing else (verified on stage: zero types carry it
  //    outside the :Resource closure, zero resources lack it). `?resource a ?resourceClass` is emitted only when a
  //    class restriction is requested — see the note above build() — so the type join is paid only then.
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
  /*
   * The optional project and resource-class restrictions below are written out here rather than shared with
   * SearchQueries.selectCountByLabel (D6): the two queries are separate and a shared helper would couple them,
   * but they must agree on the load-bearing detail — the class restriction walks the whole `rdfs:subClassOf*`
   * closure, never `subClassOf?`. The subclass closure is not materialised and there is no query-time inference,
   * so zero-or-one silently excludes every class two or more hops below the target (DEV-6833).
   *
   * `?resource a ?resourceClass` sits inside the class-restriction hole rather than in the query body so the type
   * join is only paid when a class restriction is actually requested — the resource-ness probe is unconditional
   * and stands alone. The project restriction is emitted before the class join: `attachedToProject` on the
   * already-bound `?resource` is a cheap, selective probe (engine Fact 1 — order relative to the joins is the plan).
   */
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
  ): IO[SparqlGenerationException, Select] =
    for {
      _ <- failIf(!countQuery && separator.isEmpty, "Separator expected for non count query, but none given")
    } yield {
      val searchLiteral = Literal.string(searchTerms.getQueryString)

      Select.search(
        sparql"""|PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
                 |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                 |${
            if (countQuery) sparql"SELECT (COUNT(DISTINCT ?resource) AS ?count)"
            else
              sparql"""|SELECT ?resource
                   |       (GROUP_CONCAT(IF(BOUND(?valueObject), STR(?valueObject), ""); SEPARATOR=${Literal
                  .string(separator.get.toString)}) AS ?valueObjectConcat)"""
          }
                 |WHERE {
                 |    {
                 |        SELECT DISTINCT ?matchingSubject WHERE {
                 |            ?matchingSubject <http://jena.apache.org/text#query> ($searchLiteral $luceneHitLimit) .
                 |            ${standoffFilterClause(searchTerms, limitToStandoffClass)}
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
                 |    ${List(
            limitToProject.map(project =>
              sparql"?resource knora-base:attachedToProject ${Iri.unsafeFrom(project.value)} .",
            ),
            limitToResourceClass.map(cls => sparql"""|?resource a ?resourceClass .
                     |?resourceClass rdfs:subClassOf* ${Iri.unsafeFrom(cls.smartIri.toInternalSchema.toIri)} ."""),
          ).flatten.joinLines}
                 |    ${sparql"""|OPTIONAL {
                                       |    ?fileValueProp rdfs:subPropertyOf* knora-base:hasFileValue .
                                       |    ?resource ?fileValueProp ?valueObject .
                                       |}""".when(returnFiles)}
                 |    FILTER NOT EXISTS {
                 |        ?resource knora-base:isDeleted true .
                 |    }
                 |}
                 |${sparql"""|GROUP BY ?resource
                             |ORDER BY ?resource
                             |OFFSET ${Literal.int(offset)}""".unless(countQuery)}
                 |LIMIT ${Literal.int(limit)}""".render,
      )
    }
}
