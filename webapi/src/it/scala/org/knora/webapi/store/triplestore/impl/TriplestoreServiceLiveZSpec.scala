/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.triplestore.impl

import zio._
import zio.test.Assertion._
import zio.test._

import org.knora.webapi.config.AppConfig
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.store.triplestore.api.TriplestoreService

/**
 * This spec is used to test [[org.knora.webapi.store.triplestore.impl.TriplestoreServiceLive]].
 */
object TriplestoreServiceLiveZSpec extends ZIOSpecDefault {

  /**
   * Defines a layer which encompases all dependencies that are needed for
   * running the tests. `bootstrap` overrides the base layer of ZIOApp.
   */
  val testLayer: ULayer[TriplestoreService] =
    ZLayer.make[TriplestoreService](
      TriplestoreServiceLive.layer,
      AppConfig.layer,
      StringFormatter.test
    )

  def spec =
    suite("TriplestoreServiceHttpConnectorImplSpec")(
      test("successfully call a request that triggers a TriplestoreResponseException") {

        val searchStringOfDeath =
          """
             PREFIX knora-base: <http://www.knora.org/ontology/knora-base#> PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>   SELECT DISTINCT ?resource                 (GROUP_CONCAT(IF(BOUND(?valueObject), STR(?valueObject), "");                     separator="")                     AS ?valueObjectConcat)  WHERE {      {         SELECT DISTINCT ?matchingSubject WHERE {              ?matchingSubject <http://jena.apache.org/text#query> 'fiche_CLSR AND GR AND MS AND 6 AND E129b_[Le AND sommeil AND et AND la AND mort…]*' .                                    }     }      OPTIONAL {         ?matchingSubject a ?valueObjectType .          ?valueObjectType rdfs:subClassOf *knora-base:Value .          FILTER(?valueObjectType != knora-base:LinkValue && ?valueObjectType != knora-base:ListValue)          ?containingResource ?property ?matchingSubject .          ?property rdfs:subPropertyOf* knora-base:hasValue .          FILTER NOT EXISTS {             ?matchingSubject knora-base:isDeleted true         }          # this variable will only be bound if the search matched a value object         BIND(?matchingSubject AS ?valueObject)     }      OPTIONAL {         # get all list nodes that match the search term         ?matchingSubject a knora-base:ListNode .          # get sub-node(s) of that node(s) (recursively)         ?matchingSubject knora-base:hasSubListNode* ?subListNode .          # get all values that point to the node(s) and sub-node(s)         ?listValue knora-base:valueHasListNode ?subListNode .          # get all resources that have that values         ?subjectWithListValue ?predicate ?listValue .          FILTER NOT EXISTS {             ?matchingSubject knora-base:isDeleted true         }          # this variable will only be bound if the search matched a list node         BIND(?listValue AS ?valueObject)     }      # If the first OPTIONAL clause was executed, ?matchingSubject is a value object, and ?containingResource will be set as ?valueObject.     # If the second OPTIONAL clause was executed, ?matchingSubject is a list node, and ?listValue will be set as ?valueObject.     # Otherwise, ?matchingSubject is a resource (its rdfs:label matched the search pattern).     BIND(         COALESCE(             ?containingResource,             ?subjectWithListValue,             ?matchingSubject)         AS ?resource)      ?resource a ?resourceClass .      ?resourceClass rdfs:subClassOf* knora-base:Resource .                        FILTER NOT EXISTS {         ?resource knora-base:isDeleted true .     } }  GROUP BY ?resource ORDER BY ?resource OFFSET 0  LIMIT 25
            """

        for {
          // TODO: Need to first load testdata. Only then this query should trigger a 500 error in Fuseki.
          // _      <- TriplestoreService.sparqlHttpSelect(searchStringOfDeath, false).exit.repeatN(100)
          // _      <- Clock.ClockLive.sleep(10.seconds)
          result <- TriplestoreService.sparqlHttpSelect(searchStringOfDeath, false).exit
        } yield assert(result)(
          diesWithA[dsp.errors.NotFoundException]
        )
      }
    ).provideLayer(testLayer) @@ TestAspect.sequential
}
