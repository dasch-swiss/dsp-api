package org.knora.webapi.slice.ontology.api
import org.knora.webapi.slice.URModule
import zio.*

object OntologyApiModule extends URModule[Any, OntologyV2RequestParser] { self =>

  val layer: URLayer[self.Dependencies, self.Provided] =
    ZLayer.makeSome[self.Dependencies, self.Provided](OntologyV2RequestParser.layer)
}
