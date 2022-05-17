/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.v2.responder.ontologymessages

import akka.util.Timeout
import org.knora.webapi.AsyncCoreSpec
import org.knora.webapi.feature.KnoraSettingsFeatureFactoryConfig
import org.knora.webapi.messages.util.rdf.JsonLDDocument
import org.knora.webapi.messages.util.rdf.JsonLDUtil
import org.knora.webapi.sharedtestdata.SharedOntologyTestDataADM
import org.knora.webapi.sharedtestdata.SharedTestDataADM

import java.util.UUID
import scala.concurrent.Future

/**
 * Tests [[ChangePropertyGuiElementRequestV2Spec]].
 */
class ChangePropertyGuiElementRequestV2Spec extends AsyncCoreSpec {

  "ChangePropertyGuiElementRequest" should {
    "should parse the request message correctly" in {
      val jsonRequest =
        s"""{
           |  "@id" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}",
           |  "@type" : "owl:Ontology",
           |  "knora-api:lastModificationDate" : {
           |    "@type" : "xsd:dateTimeStamp",
           |    "@value" : "2021-08-17T12:04:29.756311Z"
           |  },
           |  "@graph" : [ {
           |    "@id" : "anything:hasName",
           |    "@type" : "owl:ObjectProperty",
           |    "salsah-gui:guiElement" : {
           |      "@id" : "salsah-gui:Textarea"
           |    },
           |    "salsah-gui:guiAttribute" : [ "cols=80", "rows=24" ]
           |  } ],
           |  "@context" : {
           |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "salsah-gui" : "http://api.knora.org/ontology/salsah-gui/v2#",
           |    "owl" : "http://www.w3.org/2002/07/owl#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "anything" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}#"
           |  }
           |}""".stripMargin

      implicit val timeout: Timeout = settings.defaultTimeout

      val featureFactoryConfig: FeatureFactoryConfig = new KnoraSettingsFeatureFactoryConfig(settings)

      val requestingUser = SharedTestDataADM.anythingUser1

      val requestDoc: JsonLDDocument = JsonLDUtil.parseJsonLD(jsonRequest)

      val requestMessageFuture: Future[ChangePropertyGuiElementRequest] = for {

        requestMessage: ChangePropertyGuiElementRequest <- ChangePropertyGuiElementRequest
                                                             .fromJsonLD(
                                                               jsonLDDocument = requestDoc,
                                                               apiRequestID = UUID.randomUUID,
                                                               requestingUser = requestingUser,
                                                               responderManager = responderManager,
                                                               storeManager = storeManager,
                                                               featureFactoryConfig = featureFactoryConfig,
                                                               settings = settings,
                                                               log = log
                                                             )
      } yield requestMessage

      requestMessageFuture map { changePropertyGuiElementRequestMessage =>
        changePropertyGuiElementRequestMessage.propertyIri.toString should equal(
          "http://0.0.0.0:3333/ontology/0001/anything/v2#hasName"
        )
        changePropertyGuiElementRequestMessage.newGuiElement.get.toString should equal(
          "http://www.knora.org/ontology/salsah-gui#Textarea"
        )
        changePropertyGuiElementRequestMessage.newGuiAttributes should equal(Set("cols=80", "rows=24"))
        changePropertyGuiElementRequestMessage.lastModificationDate.toString should equal("2021-08-17T12:04:29.756311Z")
        changePropertyGuiElementRequestMessage.requestingUser.username should equal("anything.user01")
      }

    }
  }

}
