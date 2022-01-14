/*
 * Copyright © 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.traits

/**
 * A request message that knows the name of the sender.
 */
trait RequestWithSender {
  val senderName: String
}
