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
 *  - knora-api:BundleRepresentation
 */
sealed trait FileValueType {
  val value: String
}
object FileValueType {

  /**
   * Represents "knora-api:DocumentFileValue"
   */
  case object DocumentFileValue extends FileValueType {
    val value = "knora-api:DocumentFileValue"
  }

  /**
   * Represents "knora-api:StillImageFileValue"
   */
  case object StillImageFileValue extends FileValueType {
    val value = "knora-api:StillImageFileValue"
  }

  /**
   * Represents "knora-api:MovingImageFileValue"
   */
  case object MovingImageFileValue extends FileValueType {
    val value = "knora-api:MovingImageFileValue"
  }

  /**
   * Represents "knora-api:TextFileValue"
   */
  case object TextFileValue extends FileValueType {
    val value = "knora-api:TextFileValue"
  }

  /**
   * Represents "knora-api:AudioFileValue"
   */
  case object AudioFileValue extends FileValueType {
    val value = "knora-api:AudioFileValue"
  }

  /**
   * Represents "knora-api:BundleFileValue"
   */
  case object BundleFileValue extends FileValueType {
    val value = "knora-api:BundleFileValue"
  }
}

sealed abstract case class UploadDocumentFile private (value: String)
object UploadDocumentFile {
  def make(
    internalFilename: String,
    className: String = "DocumentRepresentation",
    shortcode: String = "0001",
    ontologyName: String = "knora-api"
  ): UploadDocumentFile = new UploadDocumentFile(
    UploadFileRequest
      .make(
        className = className,
        internalFilename = internalFilename,
        fileValueType = FileValueType.DocumentFileValue,
        shortcode = shortcode,
        ontologyName = ontologyName
      )
      .value
  ) {}
}

sealed abstract case class UploadTextFile private (value: String)
object UploadTextFile {
  def make(
    internalFilename: String,
    className: String = "TextRepresentation",
    shortcode: String = "0001",
    ontologyName: String = "knora-api"
  ): UploadTextFile = new UploadTextFile(
    UploadFileRequest
      .make(
        className = className,
        internalFilename = internalFilename,
        fileValueType = FileValueType.TextFileValue,
        shortcode = shortcode,
        ontologyName = ontologyName
      )
      .value
  ) {}
}

sealed abstract case class UploadImageFile private (value: String)
object UploadImageFile {
  def make(
    internalFilename: String,
    className: String = "StillImageRepresentation",
    shortcode: String = "0001",
    ontologyName: String = "knora-api"
  ): UploadImageFile = new UploadImageFile(
    UploadFileRequest
      .make(
        className = className,
        internalFilename = internalFilename,
        fileValueType = FileValueType.StillImageFileValue,
        shortcode = shortcode,
        ontologyName = ontologyName
      )
      .value
  ) {}
}

sealed abstract case class UploadAudioFile private (value: String)
object UploadAudioFile {
  def make(
    internalFilename: String,
    className: String = "AudioRepresentation",
    shortcode: String = "0001",
    ontologyName: String = "knora-api"
  ): UploadAudioFile = new UploadAudioFile(
    UploadFileRequest
      .make(
        className = className,
        internalFilename = internalFilename,
        fileValueType = FileValueType.AudioFileValue,
        shortcode = shortcode,
        ontologyName = ontologyName
      )
      .value
  ) {}
}

sealed abstract case class UploadVideoFile private (value: String)
object UploadVideoFile {
  def make(
    internalFilename: String,
    className: String = "MovingImageRepresentation",
    shortcode: String = "0001",
    ontologyName: String = "knora-api"
  ): UploadVideoFile = new UploadVideoFile(
    UploadFileRequest
      .make(
        className = className,
        internalFilename = internalFilename,
        fileValueType = FileValueType.MovingImageFileValue,
        shortcode = shortcode,
        ontologyName = ontologyName
      )
      .value
  ) {}
}

sealed abstract case class UploadBundleFile private (value: String)
object UploadBundleFile {
  def make(
    internalFilename: String,
    className: String = "BundleRepresentation",
    shortcode: String = "0001",
    ontologyName: String = "knora-api"
  ): UploadBundleFile = new UploadBundleFile(
    UploadFileRequest
      .make(
        className = className,
        internalFilename = internalFilename,
        fileValueType = FileValueType.BundleFileValue,
        shortcode = shortcode,
        ontologyName = ontologyName
      )
      .value
  ) {}
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
    val context = ontologyName match {
      case "anything"  => ",\n    \"anything\": \"http://0.0.0.0:3333/ontology/0001/anything/v2#\""
      case "knora-api" => ""
      case _           => ""
    }
    val propName = fileValueType match {
      case FileValueType.DocumentFileValue    => "knora-api:hasDocumentFileValue"
      case FileValueType.StillImageFileValue  => "knora-api:hasStillImageFileValue"
      case FileValueType.MovingImageFileValue => "knora-api:hasMovingImageFileValue"
      case FileValueType.TextFileValue        => "knora-api:hasTextFileValue"
      case FileValueType.AudioFileValue       => "knora-api:hasAudioFileValue"
      case FileValueType.BundleFileValue      => "knora-api:hasBundleFileValue"
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

sealed abstract case class ChangeDocumentFileRequest private (value: String)
object ChangeDocumentFileRequest {
  def make(
    resourceIRI: String,
    internalFilename: String,
    valueIRI: String,
    className: String = "DocumentRepresentation",
    ontologyName: String = "knora-api"
  ): ChangeDocumentFileRequest = new ChangeDocumentFileRequest(
    ChangeFileRequest
      .make(
        fileValueType = FileValueType.DocumentFileValue,
        resourceIRI = resourceIRI,
        internalFilename = internalFilename,
        valueIRI = valueIRI,
        className = className,
        ontologyName = ontologyName
      )
      .value
  ) {}
}

sealed abstract case class ChangeImageFileRequest private (value: String)
object ChangeImageFileRequest {
  def make(
    resourceIRI: String,
    internalFilename: String,
    valueIRI: String,
    className: String = "StillImageRepresentation",
    ontologyName: String = "knora-api"
  ): ChangeImageFileRequest = new ChangeImageFileRequest(
    ChangeFileRequest
      .make(
        fileValueType = FileValueType.StillImageFileValue,
        resourceIRI = resourceIRI,
        internalFilename = internalFilename,
        valueIRI = valueIRI,
        className = className,
        ontologyName = ontologyName
      )
      .value
  ) {}
}

sealed abstract case class ChangeVideoFileRequest private (value: String)
object ChangeVideoFileRequest {
  def make(
    resourceIRI: String,
    internalFilename: String,
    valueIRI: String,
    className: String = "MovingImageRepresentation",
    ontologyName: String = "knora-api"
  ): ChangeVideoFileRequest = new ChangeVideoFileRequest(
    ChangeFileRequest
      .make(
        fileValueType = FileValueType.MovingImageFileValue,
        resourceIRI = resourceIRI,
        internalFilename = internalFilename,
        valueIRI = valueIRI,
        className = className,
        ontologyName = ontologyName
      )
      .value
  ) {}
}

sealed abstract case class ChangeTextFileRequest private (value: String)
object ChangeTextFileRequest {
  def make(
    resourceIRI: String,
    internalFilename: String,
    valueIRI: String,
    className: String = "TextRepresentation",
    ontologyName: String = "knora-api"
  ): ChangeTextFileRequest = new ChangeTextFileRequest(
    ChangeFileRequest
      .make(
        fileValueType = FileValueType.TextFileValue,
        resourceIRI = resourceIRI,
        internalFilename = internalFilename,
        valueIRI = valueIRI,
        className = className,
        ontologyName = ontologyName
      )
      .value
  ) {}
}

sealed abstract case class ChangeAudioFileRequest private (value: String)
object ChangeAudioFileRequest {
  def make(
    resourceIRI: String,
    internalFilename: String,
    valueIRI: String,
    className: String = "AudioRepresentation",
    ontologyName: String = "knora-api"
  ): ChangeAudioFileRequest = new ChangeAudioFileRequest(
    ChangeFileRequest
      .make(
        fileValueType = FileValueType.AudioFileValue,
        resourceIRI = resourceIRI,
        internalFilename = internalFilename,
        valueIRI = valueIRI,
        className = className,
        ontologyName = ontologyName
      )
      .value
  ) {}
}

sealed abstract case class ChangeBundleFileRequest private (value: String)
object ChangeBundleFileRequest {
  def make(
    resourceIRI: String,
    internalFilename: String,
    valueIRI: String,
    className: String = "BundleRepresentation",
    ontologyName: String = "knora-api"
  ): ChangeBundleFileRequest = new ChangeBundleFileRequest(
    ChangeFileRequest
      .make(
        fileValueType = FileValueType.BundleFileValue,
        resourceIRI = resourceIRI,
        internalFilename = internalFilename,
        valueIRI = valueIRI,
        className = className,
        ontologyName = ontologyName
      )
      .value
  ) {}
}

sealed abstract case class ChangeFileRequest private (value: String)
object ChangeFileRequest {
  def make(
    fileValueType: FileValueType,
    className: String,
    resourceIRI: String,
    internalFilename: String,
    valueIRI: String,
    ontologyName: String
  ): ChangeFileRequest = {

    val context = ontologyName match {
      case "anything"  => ",\n    \"anything\": \"http://0.0.0.0:3333/ontology/0001/anything/v2#\""
      case "knora-api" => ""
      case _           => ""
    }
    val propName = fileValueType match {
      case FileValueType.DocumentFileValue    => "knora-api:hasDocumentFileValue"
      case FileValueType.StillImageFileValue  => "knora-api:hasStillImageFileValue"
      case FileValueType.MovingImageFileValue => "knora-api:hasMovingImageFileValue"
      case FileValueType.TextFileValue        => "knora-api:hasTextFileValue"
      case FileValueType.AudioFileValue       => "knora-api:hasAudioFileValue"
      case FileValueType.BundleFileValue      => "knora-api:hasBundleFileValue"
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
         |    "xsd" : "http://www.w3.org/2001/XMLSchema#"$context
         |  }
         |}""".stripMargin
    new ChangeFileRequest(value) {}
  }
}
