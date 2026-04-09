/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.search.repo

import zio.test.*

import dsp.errors.SparqlGenerationException
import org.knora.webapi.messages.IriConversions.ConvertibleIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.common.KnoraIris.ResourceClassIri
import org.knora.webapi.util.ApacheLuceneSupport.LuceneQueryString

object SearchFulltextQuerySpec extends ZIOSpecDefault {

  implicit val sf: StringFormatter = StringFormatter.getInitializedTestInstance

  private val searchTerms     = LuceneQueryString("test")
  private val testProjectIri  = ProjectIri.unsafeFrom("http://rdfh.ch/projects/0001")
  private val testResourceIri =
    ResourceClassIri.unsafeFrom("http://www.knora.org/ontology/0001/anything#Thing".toSmartIri)
  private val testStandoffIri = "http://www.knora.org/ontology/standoff#StandoffBoldTag".toSmartIri

  override def spec: Spec[TestEnvironment, Any] = suite("SearchFulltextQuery")(
    suite("count query")(
      test("minimal count query") {
        val actual = SearchFulltextQuery.build(
          searchTerms = searchTerms,
          limitToProject = None,
          limitToResourceClass = None,
          limitToStandoffClass = None,
          returnFiles = false,
          separator = None,
          limit = 1,
          offset = 0,
          countQuery = true,
        )
        assertZIO(actual)(
          Assertion.equalTo(
            """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
              |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
              |SELECT (COUNT(DISTINCT ?resource) AS ?count)
              |WHERE {
              |    {
              |        SELECT DISTINCT ?matchingSubject WHERE {
              |            ?matchingSubject <http://jena.apache.org/text#query> 'test' .
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
              |    ?resourceClass rdfs:subClassOf* knora-base:Resource .
              |    FILTER NOT EXISTS {
              |        ?resource knora-base:isDeleted true .
              |    }
              |}
              |
              |LIMIT 1
              |""".stripMargin,
          ),
        )
      },
      test("count query with project and resource class limit") {
        val actual = SearchFulltextQuery.build(
          searchTerms = searchTerms,
          limitToProject = Some(testProjectIri),
          limitToResourceClass = Some(testResourceIri),
          limitToStandoffClass = None,
          returnFiles = false,
          separator = None,
          limit = 1,
          offset = 0,
          countQuery = true,
        )
        assertZIO(actual)(
          Assertion.equalTo(
            """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
              |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
              |SELECT (COUNT(DISTINCT ?resource) AS ?count)
              |WHERE {
              |    {
              |        SELECT DISTINCT ?matchingSubject WHERE {
              |            ?matchingSubject <http://jena.apache.org/text#query> 'test' .
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
              |    ?resourceClass rdfs:subClassOf* knora-base:Resource .
              |    ?resourceClass rdfs:subClassOf* <http://www.knora.org/ontology/0001/anything#Thing> .
              |    ?resource knora-base:attachedToProject <http://rdfh.ch/projects/0001> .
              |    FILTER NOT EXISTS {
              |        ?resource knora-base:isDeleted true .
              |    }
              |}
              |
              |LIMIT 1
              |""".stripMargin,
          ),
        )
      },
    ),
    suite("regular query")(
      test("minimal regular query") {
        val actual = SearchFulltextQuery.build(
          searchTerms = searchTerms,
          limitToProject = None,
          limitToResourceClass = None,
          limitToStandoffClass = None,
          returnFiles = false,
          separator = Some('\u001F'),
          limit = 25,
          offset = 0,
          countQuery = false,
        )
        assertZIO(actual)(
          Assertion.equalTo(
            s"""PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
               |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
               |SELECT DISTINCT ?resource
               |       (GROUP_CONCAT(IF(BOUND(?valueObject), STR(?valueObject), ""); SEPARATOR="\u001F") AS ?valueObjectConcat)
               |WHERE {
               |    {
               |        SELECT DISTINCT ?matchingSubject WHERE {
               |            ?matchingSubject <http://jena.apache.org/text#query> 'test' .
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
               |    ?resourceClass rdfs:subClassOf* knora-base:Resource .
               |    FILTER NOT EXISTS {
               |        ?resource knora-base:isDeleted true .
               |    }
               |}
               |
               |GROUP BY ?resource
               |ORDER BY ?resource
               |OFFSET 0
               |LIMIT 25
               |""".stripMargin,
          ),
        )
      },
      test("regular query with all filters") {
        val actual = SearchFulltextQuery.build(
          searchTerms = LuceneQueryString("test search"),
          limitToProject = Some(testProjectIri),
          limitToResourceClass = Some(testResourceIri),
          limitToStandoffClass = Some(testStandoffIri),
          returnFiles = true,
          separator = Some('\u001F'),
          limit = 25,
          offset = 50,
          countQuery = false,
        )
        assertZIO(actual)(
          Assertion.equalTo(
            s"""PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
               |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
               |SELECT DISTINCT ?resource
               |       (GROUP_CONCAT(IF(BOUND(?valueObject), STR(?valueObject), ""); SEPARATOR="\u001F") AS ?valueObjectConcat)
               |WHERE {
               |    {
               |        SELECT DISTINCT ?matchingSubject WHERE {
               |            ?matchingSubject <http://jena.apache.org/text#query> 'test search' .
               |    ?matchingSubject a knora-base:TextValue ;
               |        knora-base:valueHasString ?literal ;
               |        knora-base:valueHasStandoff ?standoffNode .
               |    ?standoffNode a <http://www.knora.org/ontology/standoff#StandoffBoldTag> ;
               |        knora-base:standoffTagHasStart ?start ;
               |        knora-base:standoffTagHasEnd ?end .
               |    BIND(SUBSTR(?literal, ?start+1, ?end - ?start) AS ?markedup)
               |    FILTER REGEX(?markedup, 'test', "i")
               |    FILTER REGEX(?markedup, 'search', "i")
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
               |    ?resourceClass rdfs:subClassOf* knora-base:Resource .
               |    ?resourceClass rdfs:subClassOf* <http://www.knora.org/ontology/0001/anything#Thing> .
               |    ?resource knora-base:attachedToProject <http://rdfh.ch/projects/0001> .
               |    OPTIONAL {
               |        ?fileValueProp rdfs:subPropertyOf* knora-base:hasFileValue .
               |        ?resource ?fileValueProp ?valueObject .
               |    }
               |    FILTER NOT EXISTS {
               |        ?resource knora-base:isDeleted true .
               |    }
               |}
               |
               |GROUP BY ?resource
               |ORDER BY ?resource
               |OFFSET 50
               |LIMIT 25
               |""".stripMargin,
          ),
        )
      },
    ),
    suite("validation")(
      test("should fail when separator is missing for non-count query") {
        val effect = SearchFulltextQuery.build(
          searchTerms = searchTerms,
          limitToProject = None,
          limitToResourceClass = None,
          limitToStandoffClass = None,
          returnFiles = false,
          separator = None,
          limit = 25,
          offset = 0,
          countQuery = false,
        )
        assertZIO(effect.exit)(
          Assertion.failsWithA[SparqlGenerationException] &&
            Assertion.fails(Assertion.hasMessage(Assertion.containsString("Separator expected for non count query"))),
        )
      },
    ),
  )
}
