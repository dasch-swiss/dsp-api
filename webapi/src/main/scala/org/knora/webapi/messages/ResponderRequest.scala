/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages

trait ResponderRequest

object ResponderRequest {

  /**
   * A tagging trait for messages that can be sent to Knora API v2 responders.
   */
  trait KnoraRequestV2 extends ResponderRequest

  /**
   * A tagging trait for messages that can be sent to Knora API v1 responders.
   */
  trait KnoraRequestV1 extends ResponderRequest

  /**
   * A tagging trait for messages that can be sent to Knora Admin responders.
   */
  trait KnoraRequestADM extends ResponderRequest
}
