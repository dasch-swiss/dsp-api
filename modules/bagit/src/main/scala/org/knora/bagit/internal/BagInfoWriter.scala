/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.bagit.internal

import org.knora.bagit.domain.BagInfo

object BagInfoWriter {

  def write(bagInfo: BagInfo): String = {
    val lines = List.newBuilder[String]

    bagInfo.sourceOrganization.foreach(v => lines += s"Source-Organization: $v")
    bagInfo.externalDescription.foreach(v => lines += s"External-Description: $v")
    bagInfo.externalIdentifier.foreach(v => lines += s"External-Identifier: $v")
    bagInfo.baggingDate.foreach(v => lines += s"Bagging-Date: $v")
    bagInfo.payloadOxum.foreach(v => lines += s"Payload-Oxum: $v")

    bagInfo.additionalFields.foreach { case (k, v) =>
      lines += s"$k: $v"
    }

    lines.result().mkString("\n")
  }
}
