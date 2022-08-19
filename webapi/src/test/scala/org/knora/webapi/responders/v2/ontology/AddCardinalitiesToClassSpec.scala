/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.v2.ontology

import java.util.UUID
import scala.concurrent.duration._

import org.knora.webapi.ApiV2Complex
import org.knora.webapi.CoreSpec
import org.knora.webapi.messages.IriConversions._
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.store.triplestoremessages.SmartIriLiteralV2
import org.knora.webapi.messages.store.triplestoremessages.SparqlSelectRequest
import org.knora.webapi.messages.util.rdf.SparqlSelectResult
import org.knora.webapi.messages.v2.responder.ontologymessages.AddCardinalitiesToClassRequestV2
import org.knora.webapi.messages.v2.responder.ontologymessages.Cardinality
import org.knora.webapi.messages.v2.responder.ontologymessages.ClassInfoContentV2
import org.knora.webapi.messages.v2.responder.ontologymessages.OntologyMetadataGetByIriRequestV2
import org.knora.webapi.messages.v2.responder.ontologymessages.PredicateInfoV2
import org.knora.webapi.messages.v2.responder.ontologymessages.ReadOntologyMetadataV2
import org.knora.webapi.messages.v2.responder.ontologymessages.ReadOntologyV2
import org.knora.webapi.sharedtestdata.SharedTestDataADM

/**
 * This spec is used to test [[org.knora.webapi.responders.v2.ontology.Cardinalities]].
 */
class AddCardinalitiesToClassSpec extends CoreSpec {

  private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
  
  private val freeTestOntology = RdfDataObject(
    path = "test_data/ontologies/freetest-onto.ttl",
    name = "http://www.knora.org/ontology/0001/freetest"
  )
  override lazy val rdfDataObjects: List[RdfDataObject] = List(freeTestOntology)

  private def getCardinalityCountFromTriplestore(classIri: SmartIri, propertyIri: SmartIri) = {
    val sparqlCountQuery =
      s"""|PREFIX owl: <http://www.w3.org/2002/07/owl#>
          |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
          |
          |SELECT (count(?blanknode) as ?count)
          |WHERE {
          |  <$classIri> rdfs:subClassOf ?blanknode 
          |  FILTER isBlank(?blanknode)
          |  ?blanknode owl:onProperty <$propertyIri>
          |}
          |""".stripMargin
    appActor ! SparqlSelectRequest(sparqlCountQuery)
    val sparqlCountResponseInitial = expectMsgType[SparqlSelectResult]
    sparqlCountResponseInitial.results.bindings.head.rowMap.values.head
  }

  val freetestOntologyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/freetest/v2".toSmartIri

  "The ontology responder" should {
    "add a cardinality to a class without duplicating all existing cardinalities in the triplestore" in {

      val user           = SharedTestDataADM.anythingAdminUser
      val classIri       = "http://www.knora.org/ontology/0001/freetest#Book".toSmartIri
      val propertyIri    = "http://www.knora.org/ontology/0001/freetest#hasAuthor".toSmartIri
      val newPropertyIri = "http://www.knora.org/ontology/0001/freetest#hasName".toSmartIri

      // check ontology first
      appActor ! OntologyMetadataGetByIriRequestV2(ontologyIris = Set(freetestOntologyIri), requestingUser = user)
      val ontologyMetadata = expectMsgType[ReadOntologyMetadataV2]
      val ontologyLastModificationDate = ontologyMetadata.ontologies.head.lastModificationDate
        .getOrElse(throw new AssertionError("Ontology does not have a LastModificationDate"))

      // assert that the cardinality for `:hasAuthor` is only once in the triplestore
      val countInitial = getCardinalityCountFromTriplestore(classIri, propertyIri)
      assert(countInitial == "1")

      // add an additional cardinality to the class
      val addCardinalitiesRequest = AddCardinalitiesToClassRequestV2(
        classInfoContent = ClassInfoContentV2(
          classIri = classIri.toOntologySchema(ApiV2Complex),
          ontologySchema = ApiV2Complex,
          predicates = Map(
            OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
              predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
              objects = Vector(SmartIriLiteralV2(value = OntologyConstants.Owl.Class.toSmartIri))
            )
          ),
          directCardinalities =
            Map(newPropertyIri -> Cardinality.KnoraCardinalityInfo(cardinality = Cardinality.MayHaveOne))
        ),
        lastModificationDate = ontologyLastModificationDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = user
      )
      appActor ! addCardinalitiesRequest
      expectMsgType[ReadOntologyV2]

      // assert that the cardinalities for `:hasAuthor` and `:hasName` is still only once in the triplestore
      val countAfterwards = getCardinalityCountFromTriplestore(classIri, propertyIri)
      assert(countAfterwards == "1")
      val countNewProperty = getCardinalityCountFromTriplestore(classIri, newPropertyIri)
      assert(countNewProperty == "1")
    }
  }
}
