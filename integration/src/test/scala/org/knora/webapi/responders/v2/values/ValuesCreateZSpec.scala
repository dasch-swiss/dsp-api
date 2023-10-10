/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.v2.values

import zio._
import zio.test._
import org.knora.webapi.CoreZioSpec
import org.knora.webapi.core.LayersTest
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.TestDatasetBuilder
import org.knora.webapi.core.MessageRelay
import org.knora.webapi.messages.v2.responder.valuemessages.CreateValueRequestV2
import org.knora.webapi.messages.v2.responder.valuemessages.CreateValueV2
import org.knora.webapi.IRI
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.IriConversions._
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.v2.responder.valuemessages.IntegerValueContentV2
import org.knora.webapi.ApiV2Complex
import java.util.UUID
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.messages.v2.responder.valuemessages.CreateValueResponseV2
import org.knora.webapi.store.triplestore.StandardDataset
import org.knora.webapi.messages.admin.responder.usersmessages.UsersGetRequestADM
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.admin.responder.usersmessages.UsersGetResponseADM

object ValuesCreateZSpec extends CoreZioSpec {

  def smartIri(iri: IRI): ZIO[StringFormatter, Throwable, SmartIri] =
    ZIO.serviceWith[StringFormatter](_.toSmartIri(iri))

  val resourceIri: IRI = "http://rdfh.ch/0001/a-thing"
  val intValue         = 4
  // val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUser1)
  override def spec = suite("ValuesCreateZSpec")(
    test("create an integer value") {
      for {
        relay            <- ZIO.service[MessageRelay]
        _                <- Console.printLine("-" * 120)
        resourceClassIri <- smartIri("http://0.0.0.0:3333/ontology/0001/anything/v2#Thing")
        propertyIri      <- smartIri("http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger")

        msg = CreateValueRequestV2(
                CreateValueV2(
                  resourceIri = resourceIri,
                  resourceClassIri = resourceClassIri,
                  propertyIri = propertyIri,
                  valueContent = IntegerValueContentV2(
                    ontologySchema = ApiV2Complex,
                    valueHasInteger = intValue
                  )
                ),
                requestingUser = SharedTestDataADM.anythingUser1,
                apiRequestID = UUID.randomUUID
              )
        msg2 = UsersGetRequestADM(requestingUser = SharedTestDataADM.rootUser)

        response <- relay.ask[UsersGetResponseADM](msg2).debug("relay response")
        // response <- relay.ask[CreateValueResponseV2](msg)
        _ <- Console.printLine(response)
      } yield assertTrue(true)
    }.provide(
      LayersTest.integrationTestsWithInmemoryTriplestore,
      TestDatasetBuilder.allTestOntologies.debug
        // .withTrig(
        //   """|@prefix rdf:         <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
        //      |@prefix xsd:         <http://www.w3.org/2001/XMLSchema#> .
        //      |@prefix knora-admin: <http://www.knora.org/ontology/knora-admin#> .
        //      |<http://www.knora.org/data/admin> {
        //      |  <http://rdfh.ch/users/root>
        //      |      rdf:type                         knora-admin:User ;
        //      |      knora-admin:username             "root"^^xsd:string ;
        //      |      knora-admin:email                "root@example.com"^^xsd:string ;
        //      |      knora-admin:givenName            "System"^^xsd:string ;
        //      |      knora-admin:familyName           "Administrator"^^xsd:string ;
        //      |      knora-admin:password             "$2a$12$7XEBehimXN1rbhmVgQsyve08.vtDmKK7VMin4AdgCEtE4DWgfQbTK"^^xsd:string ;
        //      |      knora-admin:phone                "123456"^^xsd:string ;
        //      |      knora-admin:preferredLanguage    "de"^^xsd:string ;
        //      |      knora-admin:status               "true"^^xsd:boolean ;
        //      |      knora-admin:isInSystemAdminGroup "true"^^xsd:boolean .
        //      |}
        //      |<graph1> {
        //      |    <http://rdfh.ch/0001/a> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://www.knora.org/ontology/knora-base#Resource> .
        //      |}""".stripMargin
        // )
        // .withAdditional(StandardDataset.AnythingOntology, StandardDataset.AnythingData)
        .toLayer
    )
  )
}
