package org.knora.webapi.slice.ontology

import zio.IO
import zio.UIO
import zio.ZLayer

import org.knora.webapi.config.AppConfig

final case class TransformerError(message: String)

final class OntologyTransformer() { self =>

  private val extPrefix: UIO[String] = AppConfig.config(_.knoraApi.externalOntologyIriHostAndPort)
  private val intPrefix: String      = "http://www.knora.org"

  def toKnoraBase(rdf: String): IO[TransformerError, String] =
    extPrefix.map(prefix => rdf.replaceAll(prefix, intPrefix))
}

object OntologyTransformer {
  val layer = ZLayer.derive[OntologyTransformer]
}
