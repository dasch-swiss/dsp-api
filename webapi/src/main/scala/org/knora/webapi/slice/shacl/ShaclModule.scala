package org.knora.webapi.slice.shacl
import zio.URLayer

import org.knora.webapi.slice.URModule
import org.knora.webapi.slice.shacl.domain.ShaclValidator

object ShaclModule extends URModule[Any, ShaclValidator] { self =>
  val layer: URLayer[self.Dependencies, self.Provided] = ShaclValidator.layer
}
