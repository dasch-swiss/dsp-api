/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.errors

package object exceptions {
  def deserializationError(msg: String, cause: Throwable = null, fieldNames: List[String] = Nil) =
    throw InvalidJsonLDException(msg, cause)
}
