/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.bagit.domain

import org.knora.bagit.BagItError
import org.knora.bagit.internal.PathSecurity

opaque type PayloadPath = String

object PayloadPath {

  def apply(raw: String): Either[BagItError.PathTraversalDetected, PayloadPath] =
    PathSecurity.validateEntryName(raw).map(v => v: PayloadPath)

  extension (p: PayloadPath) {
    def value: String = p
  }
}
