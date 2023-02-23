package org.knora.webapi.util
import zio.URIO

import org.knora.webapi.messages.v2.responder.KnoraJsonLDResponseV2
import org.knora.webapi.ApiV2Complex
import org.knora.webapi.messages.util.rdf.JsonLD
import zio.ZIO
import zio.json.ast.Json
import zio.json.DecoderOps

import org.knora.webapi.config.AppConfig

object JsonHelper {
  def parseJson(jsonString: String): Json = jsonString.fromJson[Json].getOrElse(throw new IllegalStateException)

  def renderResponseJson(response: KnoraJsonLDResponseV2): URIO[AppConfig, Json] =
    ZIO.service[AppConfig].map(appConfig => parseJson(response.format(JsonLD, ApiV2Complex, Set.empty, appConfig)))

  implicit class StringToJson(jsonStr: String) {
    def asJson: Json = parseJson(jsonStr.stripMargin)
  }
}
