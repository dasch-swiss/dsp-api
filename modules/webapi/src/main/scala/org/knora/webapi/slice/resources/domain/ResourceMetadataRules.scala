/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.domain

import org.knora.webapi.messages.v2.responder.resourcemessages.ReadResourceV2
import org.knora.webapi.messages.v2.responder.valuemessages.AudioFileValueContentV2
import org.knora.webapi.messages.v2.responder.valuemessages.FileValueContentV2
import org.knora.webapi.messages.v2.responder.valuemessages.MovingImageFileValueContentV2
import org.knora.webapi.messages.v2.responder.valuemessages.ReadValueV2
import org.knora.webapi.messages.v2.responder.valuemessages.StillImageExternalFileValueContentV2
import org.knora.webapi.messages.v2.responder.valuemessages.StillImageFileValueContentV2
import org.knora.webapi.messages.v2.responder.valuemessages.TextFileValueContentV2
import org.knora.webapi.messages.v2.responder.valuemessages.TextValueContentV2
import org.knora.webapi.messages.v2.responder.valuemessages.ValueContentV2
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode

/**
 * Pure rules for deriving resource metadata (file, type of data, description), shared by every producer of it.
 */
object ResourceMetadataRules {

  /**
   * The first file value of the resource, if it has one. A resource has at most one file value.
   */
  def selectFileValue(r: ReadResourceV2): Option[(ReadValueV2, FileValueContentV2)] =
    r.values.values.flatten.flatMap { v =>
      v.valueContent match {
        case fc: FileValueContentV2 => Some((v, fc))
        case _                      => None
      }
    }.headOption

  /**
   * The label for the kind of data a single value content holds, if it is one that identifies a resource's type.
   */
  def typeOfData(content: ValueContentV2): Option[String] = content match {
    case _: StillImageFileValueContentV2 | _: StillImageExternalFileValueContentV2 => Some("Image")
    case _: MovingImageFileValueContentV2                                          => Some("Audiovisual")
    case _: AudioFileValueContentV2                                                => Some("Sound")
    case _: TextFileValueContentV2 | _: TextValueContentV2                         => Some("Text")
    case _                                                                         => None
  }

  /**
   * The type of data of the first value of the resource that maps to one.
   */
  def typeOfDataOf(r: ReadResourceV2): Option[String] =
    r.values.values.flatten.map(_.valueContent).flatMap(typeOfData).headOption

  /**
   * The asset id of a file value: its internal filename without extension.
   */
  def assetIdOf(file: FileValueContentV2): String =
    file.fileValue.internalFilename.takeWhile(_ != '.')

  /**
   * The direct URL of the original file on the ingest service.
   */
  def originalFileUrl(ingestExternalBaseUrl: String, shortcode: Shortcode, assetId: String): String =
    s"$ingestExternalBaseUrl/projects/${shortcode.value}/assets/$assetId/original"

  /**
   * The IRI of the property holding a project's resource description, for projects that have one configured.
   */
  def descriptionPropertyIri(shortcode: Shortcode): Option[String] =
    shortcode.value match {
      case "0803" => Some("http://www.knora.org/ontology/0803/incunabula#description")
      case "081C" => Some("http://www.knora.org/ontology/081C/hdm#hasDescription")
      case "0868" => Some("http://www.knora.org/ontology/0868/SolarEclipses#hasDescription")
      case "1612" => Some("http://www.knora.org/ontology/1612/Data#TextShort")
      case _      => None
    }
}
