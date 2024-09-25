/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.shacl
import zio.URLayer

import org.knora.webapi.slice.URModule
import org.knora.webapi.slice.shacl.domain.ShaclValidator

object ShaclModule extends URModule[Any, ShaclValidator] { self =>
  val layer: URLayer[self.Dependencies, self.Provided] = ShaclValidator.layer
}
