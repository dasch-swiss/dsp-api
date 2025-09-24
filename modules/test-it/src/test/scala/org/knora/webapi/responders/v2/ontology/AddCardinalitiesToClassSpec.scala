/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.v2.ontology

import zio.*
import zio.test.*

import java.util.UUID

import org.knora.webapi.ApiV2Complex
import org.knora.webapi.E2EZSpec
import org.knora.webapi.messages.IriConversions.*
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.store.triplestoremessages.SmartIriLiteralV2
import org.knora.webapi.messages.v2.responder.ontologymessages.*
import org.knora.webapi.messages.v2.responder.ontologymessages.OwlCardinality.*
import org.knora.webapi.responders.v2.OntologyResponderV2
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.sharedtestdata.SharedTestDataADM.*
import org.knora.webapi.slice.common.KnoraIris
import org.knora.webapi.slice.common.KnoraIris.PropertyIri
import org.knora.webapi.slice.common.KnoraIris.ResourceClassIri
import org.knora.webapi.slice.ontology.api.AddCardinalitiesToClassRequestV2
import org.knora.webapi.slice.ontology.domain.model.Cardinality.ZeroOrOne
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Select
import org.knora.webapi.testservices.TestOntologyApiClient

/**
 * This spec is used to test [[org.knora.webapi.responders.v2.ontology.Cardinalities]].
 */
object AddCardinalitiesToClassSpec extends E2EZSpec {

  private val ontologyResponder = ZIO.serviceWithZIO[OntologyResponderV2]

  override val rdfDataObjects: List[RdfDataObject] = List(freetestRdfOntology)

  private def getCardinalityCountFromTriplestore(
    classIri: ResourceClassIri,
    propertyIri: PropertyIri,
  ): ZIO[TriplestoreService, Throwable, String] = {
    val sparqlCountQuery =
      s"""|PREFIX owl: <http://www.w3.org/2002/07/owl#>
          |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
          |
          |SELECT (count(?blanknode) as ?count)
          |WHERE {
          |  <${classIri.toInternalSchema}> rdfs:subClassOf ?blanknode
          |  FILTER isBlank(?blanknode)
          |  ?blanknode owl:onProperty <${propertyIri.toInternalSchema}>
          |}
          |""".stripMargin
    ZIO
      .serviceWithZIO[TriplestoreService](_.query(Select(sparqlCountQuery)))
      .map(_.results.bindings.head.rowMap.values.head)
  }

  val e2eSpec: Spec[env, Any] = suite("The ontology responder")(
    test("add a cardinality to a class without duplicating all existing cardinalities in the triplestore") {
      val classIri       = freetestOntologyIri.makeClass("Book")
      val propertyIri    = freetestOntologyIri.makeProperty("hasAuthor")
      val newPropertyIri = freetestOntologyIri.makeProperty("hasName")

      for {
        ontologyLastModificationDate <- TestOntologyApiClient.getLastModificationDate(freetestOntologyIri)

        // assert that the cardinality for `:hasAuthor` is only once in the triplestore
        countInitial <- getCardinalityCountFromTriplestore(classIri, propertyIri)

        // add additional cardinality to the class
        _ <- ontologyResponder(
               _.addCardinalitiesToClass(
                 AddCardinalitiesToClassRequestV2(
                   classInfoContent = ClassInfoContentV2(
                     classIri = classIri.toComplexSchema,
                     ontologySchema = ApiV2Complex,
                     predicates = Map(
                       OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
                         predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
                         objects = Vector(SmartIriLiteralV2(value = OntologyConstants.Owl.Class.toSmartIri)),
                       ),
                     ),
                     directCardinalities =
                       Map(newPropertyIri.toInternalSchema -> KnoraCardinalityInfo(cardinality = ZeroOrOne)),
                   ),
                   lastModificationDate = ontologyLastModificationDate,
                   apiRequestID = UUID.randomUUID,
                   requestingUser = SharedTestDataADM.anythingAdminUser,
                 ),
               ),
             )

        // assert that the cardinalities for `:hasAuthor` and `:hasName` is still only once in the triplestore
        countAfterwards  <- getCardinalityCountFromTriplestore(classIri, propertyIri)
        countNewProperty <- getCardinalityCountFromTriplestore(classIri, newPropertyIri)
      } yield assertTrue(
        countInitial == "1",
        countAfterwards == "1",
        countNewProperty == "1",
      )
    },
  )
}
