/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.bagit.internal

import java.time.LocalDate
import scala.util.Try

import org.knora.bagit.BagItError
import org.knora.bagit.domain.BagInfo
import org.knora.bagit.domain.PayloadOxum

object BagInfoParser {

  def parse(lines: List[String]): Either[BagItError, BagInfo] = {
    // Merge continuation lines (lines starting with whitespace) with their predecessor
    val merged = lines
      .foldLeft(List.empty[(String, String)]) { (acc, line) =>
        if (line.nonEmpty && (line.head == ' ' || line.head == '\t')) {
          // continuation line
          acc match {
            case Nil          => acc // skip orphan continuation
            case head :: tail => (head._1, head._2 + " " + line.trim) :: tail
          }
        } else {
          val idx = line.indexOf(':')
          if (idx > 0) {
            val label = line.substring(0, idx).trim
            val value = line.substring(idx + 1).trim
            (label, value) :: acc
          } else {
            acc // skip malformed lines
          }
        }
      }
      .reverse

    var baggingDate         = Option.empty[LocalDate]
    var payloadOxum         = Option.empty[PayloadOxum]
    var sourceOrganization  = Option.empty[String]
    var externalDescription = Option.empty[String]
    var externalIdentifier  = Option.empty[String]
    val additionalFields    = List.newBuilder[(String, String)]

    merged.foreach { case (label, value) =>
      label match {
        case "Bagging-Date" if baggingDate.isEmpty =>
          baggingDate = Try(LocalDate.parse(value)).toOption
        case "Payload-Oxum" if payloadOxum.isEmpty =>
          payloadOxum = PayloadOxum.parse(value).toOption
        case "Source-Organization" if sourceOrganization.isEmpty =>
          sourceOrganization = Some(value)
        case "External-Description" if externalDescription.isEmpty =>
          externalDescription = Some(value)
        case "External-Identifier" if externalIdentifier.isEmpty =>
          externalIdentifier = Some(value)
        case _ =>
          additionalFields += ((label, value))
      }
    }

    Right(
      BagInfo(
        baggingDate = baggingDate,
        payloadOxum = payloadOxum,
        sourceOrganization = sourceOrganization,
        externalDescription = externalDescription,
        externalIdentifier = externalIdentifier,
        additionalFields = additionalFields.result(),
      ),
    )
  }
}
