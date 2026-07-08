/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.util

import zio.json.*

/**
 * Utility functions for communicating with Sipi.
 */
object SipiUtil {

  final case class SipiErrorResponse(message: String)
  object SipiErrorResponse {
    given JsonDecoder[SipiErrorResponse] = DeriveJsonDecoder.gen[SipiErrorResponse]
  }

  /**
   * Tries to extract an error message from a Sipi HTTP response.
   *
   * @param sipiResponse the response received from Sipi.
   * @return the error message contained in the response, or the same string if it could not be parsed.
   */
  def getSipiErrorMessage(sipiResponse: String): String =
    sipiResponse.fromJson[SipiErrorResponse] match {
      case Right(msg: SipiErrorResponse) => msg.message
      case _                             => sipiResponse
    }
}
