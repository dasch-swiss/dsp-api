/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.util

import spray.json._

import scala.util.Failure
import scala.util.Success
import scala.util.Try

/**
 * Utility functions for communicating with Sipi.
 */
object SipiUtil {

  /**
   * Tries to extract an error message from a Sipi HTTP response.
   *
   * @param sipiResponseStr the response received from Sipi.
   * @return the error message contained in the response, or the same string if it could not be parsed.
   */
  def getSipiErrorMessage(sipiResponseStr: String): String =
    if (sipiResponseStr.isEmpty) {
      sipiResponseStr
    } else {
      Try(sipiResponseStr.parseJson) match {
        case Success(jsValue: JsValue) =>
          jsValue match {
            case jsObject: JsObject =>
              jsObject.fields.get("message") match {
                case Some(JsString(str)) => str
                case _                   => sipiResponseStr
              }

            case _ => sipiResponseStr
          }

        case Failure(_) => sipiResponseStr
      }
    }
}
