/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.models.filemodels

import org.knora.webapi.messages.{OntologyConstants, SmartIri, StringFormatter}
import org.knora.webapi.messages.IriConversions._

object FileModelUtil {
  private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

  def getFileRepresentationClassIri(fileType: FileType): SmartIri = fileType match {
    case FileType.DocumentFile    => OntologyConstants.KnoraApiV2Complex.DocumentRepresentation.toSmartIri
    case FileType.StillImageFile  => OntologyConstants.KnoraApiV2Complex.StillImageRepresentation.toSmartIri
    case FileType.MovingImageFile => OntologyConstants.KnoraApiV2Complex.MovingImageRepresentation.toSmartIri
    case FileType.TextFile        => OntologyConstants.KnoraApiV2Complex.TextRepresentation.toSmartIri
    case FileType.AudioFile       => OntologyConstants.KnoraApiV2Complex.AudioRepresentation.toSmartIri
    case FileType.ArchiveFile     => OntologyConstants.KnoraApiV2Complex.ArchiveRepresentation.toSmartIri
  }

  def getFileRepresentationPropertyIri(fileType: FileType): SmartIri = fileType match {
    case FileType.DocumentFile    => OntologyConstants.KnoraApiV2Complex.HasDocumentFileValue.toSmartIri
    case FileType.StillImageFile  => OntologyConstants.KnoraApiV2Complex.HasStillImageFileValue.toSmartIri
    case FileType.MovingImageFile => OntologyConstants.KnoraApiV2Complex.HasMovingImageFileValue.toSmartIri
    case FileType.TextFile        => OntologyConstants.KnoraApiV2Complex.HasTextFileValue.toSmartIri
    case FileType.AudioFile       => OntologyConstants.KnoraApiV2Complex.HasAudioFileValue.toSmartIri
    case FileType.ArchiveFile     => OntologyConstants.KnoraApiV2Complex.HasArchiveFileValue.toSmartIri
  }

  def getFileValuePropertyName(fileType: FileType): String = fileType match {
    case FileType.DocumentFile    => "knora-api:hasDocumentFileValue"
    case FileType.StillImageFile  => "knora-api:hasStillImageFileValue"
    case FileType.MovingImageFile => "knora-api:hasMovingImageFileValue"
    case FileType.TextFile        => "knora-api:hasTextFileValue"
    case FileType.AudioFile       => "knora-api:hasAudioFileValue"
    case FileType.ArchiveFile     => "knora-api:hasArchiveFileValue"
  }

  def getDefaultClassName(fileType: FileType): String = fileType match {
    case FileType.DocumentFile    => "DocumentRepresentation"
    case FileType.StillImageFile  => "StillImageRepresentation"
    case FileType.MovingImageFile => "MovingImageRepresentation"
    case FileType.TextFile        => "TextRepresentation"
    case FileType.AudioFile       => "AudioRepresentation"
    case FileType.ArchiveFile     => "ArchiveRepresentation"
  }

  def getFileValueType(fileType: FileType): String = fileType match {
    case FileType.DocumentFile    => "knora-api:DocumentFileValue"
    case FileType.StillImageFile  => "knora-api:StillImageFileValue"
    case FileType.MovingImageFile => "knora-api:MovingImageFileValue"
    case FileType.TextFile        => "knora-api:TextFileValue"
    case FileType.AudioFile       => "knora-api:AudioFileValue"
    case FileType.ArchiveFile     => "knora-api:ArchiveFileValue"
  }

  def getJsonLdContext(ontology: String): String = {
    val ontologies = ontology match {
      case "anything" =>
        FileModelConstants.defaultJsonLdContextMap + ("anything" -> "http://0.0.0.0:3333/ontology/0001/anything/v2#")
      case _ => FileModelConstants.defaultJsonLdContextMap
    }
    val lines = ontologies.toList
      .map(x => s"""    "${x._1}": "${x._2}\"""")
      .reduce({ (a, b) => a + ",\n" + b })
    s"""|"@context" : {
        |$lines
        |  }
        |""".stripMargin
  }
}

/**
 * Constants for use in FileModels.
 */
object FileModelConstants {
  val documentRepresentation = "DocumentRepresentation"
  val textRepresentation = "TextRepresentation"
  val stillImageRepresentation = "StillImageRepresentation"
  val movingImageRepresentation = "MovingImageRepresentation"
  val audioRepresentation = "AudioRepresentation"
  val archiveRepresentation = "ArchiveRepresentation"
  val knoraApiPrefix = "knora-api"
  val anythingShortcode = "0001"
  val defaultJsonLdContextMap = Map(
    "rdf" -> "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
    "knora-api" -> "http://api.knora.org/ontology/knora-api/v2#",
    "rdfs" -> "http://www.w3.org/2000/01/rdf-schema#",
    "xsd" -> "http://www.w3.org/2001/XMLSchema#"
  )
}

sealed trait FileType
object FileType {
  case object DocumentFile extends FileType
  case object StillImageFile extends FileType
  case object MovingImageFile extends FileType
  case object TextFile extends FileType
  case object AudioFile extends FileType
  case object ArchiveFile extends FileType
}
