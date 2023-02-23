/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.util
import zio.URIO
import zio.ZIO
import zio.json.DecoderOps
import zio.json.ast.Json

import org.knora.webapi.ApiV2Complex
import org.knora.webapi.config.AppConfig
import org.knora.webapi.messages.util.rdf.JsonLD
import org.knora.webapi.messages.v2.responder.KnoraJsonLDResponseV2

object JsonHelper {
  def parseJson(jsonString: String): Json = jsonString.fromJson[Json].getOrElse(throw new IllegalStateException)

  def renderResponseJson(response: KnoraJsonLDResponseV2): URIO[AppConfig, Json] =
    ZIO.service[AppConfig].map(appConfig => parseJson(response.format(JsonLD, ApiV2Complex, Set.empty, appConfig)))

  implicit class StringToJson(jsonStr: String) {
    def asJson: Json = parseJson(jsonStr.stripMargin)
  }
}
