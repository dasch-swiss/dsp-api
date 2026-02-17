/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.bagit.domain

import java.time.LocalDate

final case class PayloadOxum(octetCount: Long, streamCount: Long) {
  override def toString: String = s"$octetCount.$streamCount"
}

object PayloadOxum {
  def parse(s: String): Either[String, PayloadOxum] =
    s.split('.') match {
      case Array(octets, streams) =>
        for {
          o <- octets.toLongOption.toRight(s"Invalid octet count: $octets")
          s <- streams.toLongOption.toRight(s"Invalid stream count: $streams")
        } yield PayloadOxum(o, s)
      case _ => Left(s"Invalid Payload-Oxum format: $s")
    }
}

final case class BagInfo(
  baggingDate: Option[LocalDate] = None,
  payloadOxum: Option[PayloadOxum] = None,
  sourceOrganization: Option[String] = None,
  externalDescription: Option[String] = None,
  externalIdentifier: Option[String] = None,
  additionalFields: List[(String, String)] = List.empty,
)
