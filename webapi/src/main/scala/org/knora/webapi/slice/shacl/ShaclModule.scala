/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.shacl
import zio.ULayer

import org.knora.webapi.slice.shacl.domain.ShaclValidator

object ShaclModule { self =>
  type Provided = ShaclValidator
  val layer: ULayer[self.Provided] = ShaclValidator.layer
}
