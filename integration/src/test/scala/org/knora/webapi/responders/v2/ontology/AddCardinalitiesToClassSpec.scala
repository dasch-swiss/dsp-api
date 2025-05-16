/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.v2.ontology

import zio.ZIO

import java.util.UUID

import org.knora.webapi.ApiV2Complex
import org.knora.webapi.CoreSpec
import org.knora.webapi.messages.IriConversions.*
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.store.triplestoremessages.SmartIriLiteralV2
import org.knora.webapi.messages.v2.responder.ontologymessages.*
import org.knora.webapi.messages.v2.responder.ontologymessages.OwlCardinality.*
import org.knora.webapi.responders.v2.OntologyResponderV2
import org.knora.webapi.routing.UnsafeZioRun
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.slice.common.KnoraIris
import org.knora.webapi.slice.common.KnoraIris.OntologyIri
import org.knora.webapi.slice.common.KnoraIris.PropertyIri
import org.knora.webapi.slice.common.KnoraIris.ResourceClassIri
import org.knora.webapi.slice.ontology.api.AddCardinalitiesToClassRequestV2
import org.knora.webapi.slice.ontology.domain.model.Cardinality.ZeroOrOne
import org.knora.webapi.slice.ontology.domain.service.OntologyRepo
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Select

/**
 * This spec is used to test [[org.knora.webapi.responders.v2.ontology.Cardinalities]].
 */
class AddCardinalitiesToClassSpec extends CoreSpec {

  private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
  private val ontologyResponder                         = ZIO.serviceWithZIO[OntologyResponderV2]
  private val ontologyRepo                              = ZIO.serviceWithZIO[OntologyRepo]

  override lazy val rdfDataObjects: List[RdfDataObject] = List(
    RdfDataObject(
      path = "test_data/project_ontologies/freetest-onto.ttl",
      name = "http://www.knora.org/ontology/0001/freetest",
    ),
    RdfDataObject(
      path = "test_data/project_ontologies/anything-onto.ttl",
      name = "http://www.knora.org/ontology/0001/anything",
    ),
  )

  private def getCardinalityCountFromTriplestore(classIri: ResourceClassIri, propertyIri: PropertyIri) = {
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
    val result = UnsafeZioRun.runOrThrow(ZIO.serviceWithZIO[TriplestoreService](_.query(Select(sparqlCountQuery))))
    result.results.bindings.head.rowMap.values.head
  }

  private val freetestOntologyIri = OntologyIri.unsafeFrom("http://0.0.0.0:3333/ontology/0001/freetest/v2".toSmartIri)

  "The ontology responder" should {
    "add a cardinality to a class without duplicating all existing cardinalities in the triplestore" in {

      val classIri       = freetestOntologyIri.makeClass("Book")
      val propertyIri    = freetestOntologyIri.makeProperty("hasAuthor")
      val newPropertyIri = freetestOntologyIri.makeProperty("hasName")

      val ontologyLastModificationDate = UnsafeZioRun.runOrThrow(
        ontologyRepo(_.findById(freetestOntologyIri))
          .someOrFail(new AssertionError("Ontology not found"))
          .map(_.ontologyMetadata.lastModificationDate)
          .someOrFail(new AssertionError("Ontology does not have a LastModificationDate")),
      )

      // assert that the cardinality for `:hasAuthor` is only once in the triplestore
      val countInitial = getCardinalityCountFromTriplestore(classIri, propertyIri)
      assert(countInitial == "1")

      // add additional cardinality to the class
      val _ = UnsafeZioRun.runOrThrow(
        ontologyResponder(
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
        ),
      )

      // assert that the cardinalities for `:hasAuthor` and `:hasName` is still only once in the triplestore
      val countAfterwards = getCardinalityCountFromTriplestore(classIri, propertyIri)
      assert(countAfterwards == "1")
      val countNewProperty = getCardinalityCountFromTriplestore(classIri, newPropertyIri)
      assert(countNewProperty == "1")
    }
  }
}
