/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.traits

import spray.json.JsValue

/**
 * A trait for classes that can convert themselves into JSON using the spray-json library.
 */
trait Jsonable {

  /**
   * Converts this [[Jsonable]] into a [[JsValue]].
   *
   * @return a [[JsValue]].
   */
  def toJsValue: JsValue
}
