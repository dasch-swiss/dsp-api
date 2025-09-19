/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.iiif.errors

import dsp.errors.InternalServerException

/**
 * Indicates that an error occurred with Sipi not relating to the user's request (it is not the user's fault).
 *
 * @param message a description of the error.
 */
final case class SipiException(message: String, cause: Option[Throwable] = None)
    extends InternalServerException(message, cause)

object SipiException {
  def apply(message: String, e: Throwable): SipiException =
    SipiException(message, Some(e))
}
