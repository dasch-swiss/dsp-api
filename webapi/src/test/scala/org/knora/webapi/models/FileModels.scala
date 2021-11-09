/*
 * Copyright © 2021 Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.models

/**
 * Trait for FileValue type.
 *
 * Known types:
 *
 *  - knora-api:DocumentFileValue
 *  - knora-api:StillImageFileValue
 *  - knora-api:TextFileValue
 *  - knora-api:TextFileValue
 */
sealed trait FileValueType {
  val value: String
}
object FileValueType {
  case object DocumentFileValue extends FileValueType {
    val value = "knora-api:DocumentFileValue"
  }
  case object StillImageFileValue extends FileValueType {
    val value = "knora-api:StillImageFileValue"
  }
  case object MovingImageFileValue extends FileValueType {
    val value = "knora-api:MovingImageFileValue"
  }
  case object TextFileValue extends FileValueType {
    val value = "knora-api:TextFileValue"
  }
  case object AudioFileValue extends FileValueType {
    val value = "knora-api:AudioFileValue"
  }
}

sealed abstract case class UploadDocumentFile private (value: String)
object UploadDocumentFile {
  def make(
    internalFilename: String,
    className: String = "DocumentRepresentation",
    shortcode: String = "0001",
    ontologyName: String = "knora-api"
  ): UploadFileRequest =
    UploadFileRequest.make(
      className = className,
      internalFilename = internalFilename,
      fileValueType = FileValueType.DocumentFileValue,
      shortcode = shortcode,
      ontologyName = ontologyName
    )
}

sealed abstract case class UploadTextFile private (value: String)
object UploadTextFile {
  def make(
    internalFilename: String,
    className: String = "TextRepresentation",
    shortcode: String = "0001",
    ontologyName: String = "knora-api"
  ): UploadFileRequest =
    UploadFileRequest.make(
      className = className,
      internalFilename = internalFilename,
      fileValueType = FileValueType.TextFileValue,
      shortcode = shortcode,
      ontologyName = ontologyName
    )
}

sealed abstract case class UploadImageFile private (value: String)
object UploadImageFile {
  def make(
    internalFilename: String,
    className: String = "StillImageRepresentation",
    shortcode: String = "0001",
    ontologyName: String = "knora-api"
  ): UploadFileRequest =
    UploadFileRequest.make(
      className = className,
      internalFilename = internalFilename,
      fileValueType = FileValueType.StillImageFileValue,
      shortcode = shortcode,
      ontologyName = ontologyName
    )
}

sealed abstract case class UploadAudioFile private (value: String)
object UploadAudioFile {
  def make(
    internalFilename: String,
    className: String = "AudioRepresentation",
    shortcode: String = "0001",
    ontologyName: String = "knora-api"
  ): UploadFileRequest =
    UploadFileRequest.make(
      className = className,
      internalFilename = internalFilename,
      fileValueType = FileValueType.AudioFileValue,
      shortcode = shortcode,
      ontologyName = ontologyName
    )
}

sealed abstract case class UploadVideoFile private (value: String)
object UploadVideoFile {
  def make(
    internalFilename: String,
    className: String = "MovingImageRepresentation",
    shortcode: String = "0001",
    ontologyName: String = "knora-api"
  ): UploadFileRequest =
    UploadFileRequest.make(
      className = className,
      internalFilename = internalFilename,
      fileValueType = FileValueType.MovingImageFileValue,
      shortcode = shortcode,
      ontologyName = ontologyName
    )
}

sealed abstract case class UploadFileRequest private (value: String)
object UploadFileRequest {
  def make(
    className: String,
    internalFilename: String,
    fileValueType: FileValueType,
    shortcode: String,
    ontologyName: String
  ): UploadFileRequest = {
    val anythingContext =
      """
        |,
        |    "anything": "http://0.0.0.0:3333/ontology/0001/anything/v2#"
        |""".stripMargin

    val context = ontologyName match {
      case "anything"  => anythingContext
      case "knora-api" => ""
      case _           => ""
    }
    val propName = fileValueType match {
      case FileValueType.DocumentFileValue    => "knora-api:hasDocumentFileValue"
      case FileValueType.StillImageFileValue  => "knora-api:hasStillImageFileValue"
      case FileValueType.MovingImageFileValue => "knora-api:hasMovingImageFileValue"
      case FileValueType.TextFileValue        => "knora-api:hasTextFileValue"
      case FileValueType.AudioFileValue       => "knora-api:hasAudioFileValue"
    }
    val value = s"""{
                   |  "@type" : "$ontologyName:$className",
                   |  "$propName" : {
                   |    "@type" : "${fileValueType.value}",
                   |    "knora-api:fileValueHasFilename" : "$internalFilename"
                   |  },
                   |  "knora-api:attachedToProject" : {
                   |    "@id" : "http://rdfh.ch/projects/$shortcode"
                   |  },
                   |  "rdfs:label" : "test label",
                   |  "@context" : {
                   |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
                   |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                   |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
                   |    "xsd" : "http://www.w3.org/2001/XMLSchema#"$context
                   |  }
                   |}""".stripMargin
    new UploadFileRequest(value) {}
  }
}

sealed abstract case class ChangeFileRequest private (value: String)
object ChangeFileRequest {
  def make(
    className: String,
    fileValueType: FileValueType,
    resourceIRI: String,
    internalFilename: String,
    valueIRI: String,
    ontologyName: String = "anything"
  ): ChangeFileRequest = {
    val context = ontologyName match {
      case "anything" => ""","anything": "http://0.0.0.0:3333/ontology/0001/anything/v2#" """
      case _          => ""
    }
    val propName = fileValueType match {
      case FileValueType.DocumentFileValue   => "knora-api:hasDocumentFileValue"
      case FileValueType.StillImageFileValue => "knora-api:hasStillImageFileValue"
      case FileValueType.TextFileValue       => "knora-api:hasTextFileValue"
    }
    val value =
      s"""{
         |  "@id" : "$resourceIRI",
         |  "@type" : "$ontologyName:$className",
         |  "$propName" : {
         |    "@id" : "$valueIRI",
         |    "@type" : "${fileValueType.value}",
         |    "knora-api:fileValueHasFilename" : "$internalFilename"
         |  },
         |  "@context" : {
         |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
         |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
         |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
         |    "xsd" : "http://www.w3.org/2001/XMLSchema#"
         |    $context
         |  }
         |}""".stripMargin
    new ChangeFileRequest(value) {}
  }
}
