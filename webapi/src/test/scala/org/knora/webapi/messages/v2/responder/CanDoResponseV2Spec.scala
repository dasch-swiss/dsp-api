/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.v2.responder
import zio.ZIO
import zio.test._

import org.knora.webapi.ApiV2Complex
import org.knora.webapi.config.AppConfig
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.util.rdf.JsonLD
import org.knora.webapi.messages.util.rdf.JsonLDArray
import org.knora.webapi.messages.util.rdf.JsonLDObject
import org.knora.webapi.messages.util.rdf.JsonLDString
import org.knora.webapi.util.JsonHelper.parseJson

object CanDoResponseV2Spec extends ZIOSpecDefault {

  val spec: Spec[Any, Nothing] = suite("CanDoResponseV2Spec")(
    test(".yes is correctly rendered") {
      for {
        appConfig <- ZIO.service[AppConfig]
        jsonString = CanDoResponseV2.yes.format(JsonLD, ApiV2Complex, Set.empty, appConfig)
        expectedJsonString =
          """
            |{
            |  "knora-api:canDo": true,
            |  "@context": {
            |    "knora-api": "http://api.knora.org/ontology/knora-api/v2#"
            |  }
            |}
            |""".stripMargin
      } yield assertTrue(parseJson(jsonString) == parseJson(expectedJsonString))
    },
    test(".no is correctly rendered") {
      for {
        appConfig <- ZIO.service[AppConfig]
        jsonString = CanDoResponseV2.no.format(JsonLD, ApiV2Complex, Set.empty, appConfig)
        expectedJsonString =
          """
            |{
            |  "knora-api:canDo": false,
            |  "@context": {
            |    "knora-api": "http://api.knora.org/ontology/knora-api/v2#"
            |  }
            |}
            |""".stripMargin
      } yield assertTrue(parseJson(jsonString) == parseJson(expectedJsonString))
    },
    test(".no with reason is correctly rendered") {
      for {
        appConfig <- ZIO.service[AppConfig]
        jsonString = CanDoResponseV2.no(JsonLDString("some reason")).format(JsonLD, ApiV2Complex, Set.empty, appConfig)
        expectedJsonString =
          """
            |{
            |    "knora-api:canDo": false,
            |    "@context": {
            |        "knora-api": "http://api.knora.org/ontology/knora-api/v2#"
            |    },
            |    "knora-api:cannotDoReason": "some reason"
            |}
            |""".stripMargin
      } yield assertTrue(parseJson(jsonString) == parseJson(expectedJsonString))
    },
    test(".no with reason and context is correctly rendered") {
      for {
        _         <- ZIO.service[StringFormatter]
        appConfig <- ZIO.service[AppConfig]
        jsonString =
          CanDoResponseV2
            .no(
              JsonLDString("some reason"),
              JsonLDObject(
                Map(
                  "http://some-other-key" -> JsonLDArray(
                    List(
                      JsonLDObject(Map("@id" -> JsonLDString("http://someIri"))),
                      JsonLDObject(Map("@id" -> JsonLDString("http://anotherIri")))
                    )
                  )
                )
              )
            )
            .format(JsonLD, ApiV2Complex, Set.empty, appConfig)
        expectedJsonString =
          """
            |{
            |    "knora-api:canDo": false,
            |    "@context": {
            |        "knora-api": "http://api.knora.org/ontology/knora-api/v2#"
            |    },
            |    "knora-api:cannotDoReason": "some reason",
            |    "knora-api:cannotDoContext":  {
            |                                     "http://some-other-key"  : [
            |                                                                 {"@id":"http://someIri"},
            |                                                                 {"@id":"http://anotherIri"}
            |                                                                 ]
            |                                  }
            |}
            |""".stripMargin
      } yield assertTrue(parseJson(jsonString) == parseJson(expectedJsonString))
    }
  ).provide(AppConfig.layer, StringFormatter.test)
}
