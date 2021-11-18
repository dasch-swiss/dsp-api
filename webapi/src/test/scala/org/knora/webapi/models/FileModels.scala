/*
 * Copyright © 2021 Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.models

import org.knora.webapi.{ApiV2Complex, IRI}
import org.knora.webapi.messages.{OntologyConstants, SmartIri, StringFormatter}
import org.knora.webapi.messages.v2.responder.resourcemessages.{CreateResourceV2, CreateValueInNewResourceV2}
import org.knora.webapi.messages.v2.responder.valuemessages.{
  ArchiveFileValueContentV2,
  DocumentFileValueContentV2,
  FileValueV2,
  StillImageFileValueContentV2,
  ValueContentV2
}
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.messages.IriConversions._
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectADM

import java.time.Instant
import java.util.UUID

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
}

/**
 * Models for generating JSON-LD requests to manipulate file representations.
 */
object FileJsonModels {
  import FileModelConstants._

  /**
   * Trait for FileValue type.
   *
   * Implemented types:
   *
   *  - knora-api:DocumentFileValue
   *  - knora-api:StillImageFileValue
   *  - knora-api:TextFileValue
   *  - knora-api:TextFileValue
   *  - knora-api:ArchiveRepresentation
   */
  sealed trait FileValueType {

    /** String value of the FileValueType */
    val value: String
  }

  /**
   * Companion object to the [[FileValueType]] trait.
   */
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
     * Represents "knora-api:ArchiveFileValue"
     */
    case object ArchiveFileValue extends FileValueType {
      val value = "knora-api:ArchiveFileValue"
    }
  }

  /**
   * Case class containing the JSON-LD serialization of a request to create a "knora-api:DocumentRepresentation".
   *
   * @param value the JSON-LD serialized request.
   */
  sealed abstract case class UploadDocumentFile private (value: String)

  /**
   * Companion object to the [[UploadDocumentFile]] case class.
   */
  object UploadDocumentFile {

    /**
     * Smart constructor for instantiating a [[UploadDocumentFile]].
     *
     * @param internalFilename the internal file name assigned by SIPI.
     * @param className the class name of the resource. Defaults to "DocumentRepresentation".
     *                  Can be a project ontology class that inherits from "DocumentRepresentation".
     * @param shortcode the shortcode of the project to which the resource should be added.
     *                  Defaults to "0001" (anything project).
     * @param ontologyName the name of the ontology to be prefixed to the class name. Defaults to "knora-api".
     *                     Should only be used, if `className` has a non-default value.
     * @return returns a [[UploadDocumentFile]] holding a JSON-LD serialized request to create the resource as specified by the parameters.
     */
    def make(
      internalFilename: String,
      className: String = documentRepresentation,
      shortcode: String = anythingShortcode,
      ontologyName: String = knoraApiPrefix
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

  /**
   * Case class containing the JSON-LD serialization of a request to create a "knora-api:TextRepresentation".
   *
   * @param value the JSON-LD serialized request.
   */
  sealed abstract case class UploadTextFile private (value: String)

  /**
   * Companion object to the [[UploadTextFile]] case class.
   */
  object UploadTextFile {

    /**
     * Smart constructor for instantiating a [[UploadTextFile]].
     *
     * @param internalFilename the internal file name assigned by SIPI.
     * @param className the class name of the resource. Defaults to "TextRepresentation".
     *                  Can be a project ontology class that inherits from "TextRepresentation".
     * @param shortcode the shortcode of the project to which the resource should be added.
     *                  Defaults to "0001" (anything project).
     * @param ontologyName the name of the ontology to be prefixed to the class name. Defaults to "knora-api".
     *                     Should only be used, if `className` has a non-default value.
     * @return returns a [[UploadTextFile]] holding a JSON-LD serialized request to create the resource as specified by the parameters.
     */
    def make(
      internalFilename: String,
      className: String = textRepresentation,
      shortcode: String = anythingShortcode,
      ontologyName: String = knoraApiPrefix
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

  /**
   * Case class containing the JSON-LD serialization of a request to create a "knora-api:StillImageRepresentation".
   *
   * @param value the JSON-LD serialized request.
   */
  sealed abstract case class UploadImageFile private (value: String)

  /**
   * Companion object to the [[UploadImageFile]] case class.
   */
  object UploadImageFile {

    /**
     * Smart constructor for instantiating a [[UploadImageFile]].
     *
     * @param internalFilename the internal file name assigned by SIPI.
     * @param className the class name of the resource. Defaults to "StillImageRepresentation".
     *                  Can be a project ontology class that inherits from "StillImageRepresentation".
     * @param shortcode the shortcode of the project to which the resource should be added.
     *                  Defaults to "0001" (anything project).
     * @param ontologyName the name of the ontology to be prefixed to the class name. Defaults to "knora-api".
     *                     Should only be used, if `className` has a non-default value.
     * @return returns a [[UploadImageFile]] holding a JSON-LD serialized request to create the resource as specified by the parameters.
     */
    def make(
      internalFilename: String,
      className: String = stillImageRepresentation,
      shortcode: String = anythingShortcode,
      ontologyName: String = knoraApiPrefix
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

  /**
   * Case class containing the JSON-LD serialization of a request to create a "knora-api:AudioRepresentation".
   *
   * @param value the JSON-LD serialized request.
   */
  sealed abstract case class UploadAudioFile private (value: String)

  /**
   * Companion object to the [[UploadAudioFile]] case class.
   */
  object UploadAudioFile {

    /**
     * Smart constructor for instantiating a [[UploadAudioFile]].
     *
     * @param internalFilename the internal file name assigned by SIPI.
     * @param className the class name of the resource. Defaults to "AudioRepresentation".
     *                  Can be a project ontology class that inherits from "AudioRepresentation".
     * @param shortcode the shortcode of the project to which the resource should be added.
     *                  Defaults to "0001" (anything project).
     * @param ontologyName the name of the ontology to be prefixed to the class name. Defaults to "knora-api".
     *                     Should only be used, if `className` has a non-default value.
     * @return returns a [[UploadAudioFile]] holding a JSON-LD serialized request to create the resource as specified by the parameters.
     */
    def make(
      internalFilename: String,
      className: String = audioRepresentation,
      shortcode: String = anythingShortcode,
      ontologyName: String = knoraApiPrefix
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

  /**
   * Case class containing the JSON-LD serialization of a request to create a "knora-api:MovingImageRepresentation".
   *
   * @param value the JSON-LD serialized request.
   */
  sealed abstract case class UploadVideoFile private (value: String)

  /**
   * Companion object to the [[UploadVideoFile]] case class.
   */
  object UploadVideoFile {

    /**
     * Smart constructor for instantiating a [[UploadVideoFile]].
     *
     * @param internalFilename the internal file name assigned by SIPI.
     * @param className the class name of the resource. Defaults to "MovingImageRepresentation".
     *                  Can be a project ontology class that inherits from "MovingImageRepresentation".
     * @param shortcode the shortcode of the project to which the resource should be added.
     *                  Defaults to "0001" (anything project).
     * @param ontologyName the name of the ontology to be prefixed to the class name. Defaults to "knora-api".
     *                     Should only be used, if `className` has a non-default value.
     * @return returns a [[UploadVideoFile]] holding a JSON-LD serialized request to create the resource as specified by the parameters.
     */
    def make(
      internalFilename: String,
      className: String = movingImageRepresentation,
      shortcode: String = anythingShortcode,
      ontologyName: String = knoraApiPrefix
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

  /**
   * Case class containing the JSON-LD serialization of a request to create a "knora-api:ArchiveRepresentation".
   *
   * @param value the JSON-LD serialized request.
   */
  sealed abstract case class UploadArchiveFile private (value: String)

  /**
   * Companion object to the [[UploadArchiveFile]] case class.
   */
  object UploadArchiveFile {

    /**
     * Smart constructor for instantiating a [[UploadArchiveFile]].
     *
     * @param internalFilename the internal file name assigned by SIPI.
     * @param className the class name of the resource. Defaults to "ArchiveRepresentation".
     *                  Can be a project ontology class that inherits from "ArchiveRepresentation".
     * @param shortcode the shortcode of the project to which the resource should be added.
     *                  Defaults to "0001" (anything project).
     * @param ontologyName the name of the ontology to be prefixed to the class name. Defaults to "knora-api".
     *                     Should only be used, if `className` has a non-default value.
     * @return returns a [[UploadArchiveFile]] holding a JSON-LD serialized request to create the resource as specified by the parameters.
     */
    def make(
      internalFilename: String,
      className: String = archiveRepresentation,
      shortcode: String = anythingShortcode,
      ontologyName: String = knoraApiPrefix
    ): UploadArchiveFile = new UploadArchiveFile(
      UploadFileRequest
        .make(
          className = className,
          internalFilename = internalFilename,
          fileValueType = FileValueType.ArchiveFileValue,
          shortcode = shortcode,
          ontologyName = ontologyName
        )
        .value
    ) {}
  }

  /**
   * Case class containing the JSON-LD serialization of a request to create a file representation.
   *
   * @param value the JSON-LD serialized request.
   */
  sealed abstract case class UploadFileRequest private (value: String)

  /**
   * Companion object to the [[UploadFileRequest]] case class.
   */
  object UploadFileRequest {

    /**
     * Smart constructor for instantiating a [[UploadFileRequest]].
     *
     * '''Note:''' This is a low level model which normally should not be called directly.
     * Instead one of the following higher level value objects should be used, which internally call this one:
     *
     *  - [[UploadDocumentFile]]
     *  - [[UploadTextFile]]
     *  - [[UploadImageFile]]
     *  - [[UploadAudioFile]]
     *  - [[UploadVideoFile]]
     *  - [[UploadArchiveFile]]
     *
     * @param className        the class name of the resource.
     * @param internalFilename the internal file name assigned by SIPI.
     * @param fileValueType    the [[FileValueType]] of the resource.
     * @param shortcode        the shortcode of the project to which the resource should be added.
     * @param ontologyName     the name of the ontology to be prefixed to the class name.
     * @return returns a [[UploadFileRequest]] holding a JSON-LD serialized request to create the resource as specified by the parameters.
     */
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
        case FileValueType.ArchiveFileValue     => "knora-api:hasArchiveFileValue"
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

  /**
   * Case class containing the JSON-LD serialization of a request to change a "knora-api:DocumentRepresentation".
   *
   * @param value the JSON-LD serialized request.
   */
  sealed abstract case class ChangeDocumentFileRequest private (value: String)

  /**
   * Companion object to the [[ChangeDocumentFileRequest]] case class.
   */
  object ChangeDocumentFileRequest {

    /**
     * Smart constructor for instantiating a [[ChangeDocumentFileRequest]].
     *
     * @param resourceIRI      the IRI of the resource where a property is to change.
     * @param internalFilename the new internal file name assigned by SIPI.
     * @param valueIRI         the IRI of the value property to change.
     * @param className        the class name of the resource. Defaults to "DocumentRepresentation".
     *                         Can be a project ontology class that inherits from "DocumentRepresentation".
     * @param ontologyName     the name of the ontology to be prefixed to the class name. Defaults to "knora-api".
     *                         Should only be used, if `className` has a non-default value.
     * @return returns a [[ChangeDocumentFileRequest]] holding a JSON-LD serialized request to change the resource as specified by the parameters.
     */
    def make(
      resourceIRI: String,
      internalFilename: String,
      valueIRI: String,
      className: String = documentRepresentation,
      ontologyName: String = knoraApiPrefix
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

  /**
   * Case class containing the JSON-LD serialization of a request to change a "knora-api:StillImageRepresentation".
   *
   * @param value the JSON-LD serialized request.
   */
  sealed abstract case class ChangeImageFileRequest private (value: String)

  /**
   * Companion object to the [[ChangeImageFileRequest]] case class.
   */
  object ChangeImageFileRequest {

    /**
     * Smart constructor for instantiating a [[ChangeImageFileRequest]].
     *
     * @param resourceIRI      the IRI of the resource where a property is to change.
     * @param internalFilename the new internal file name assigned by SIPI.
     * @param valueIRI         the IRI of the value property to change.
     * @param className        the class name of the resource. Defaults to "StillImageRepresentation".
     *                         Can be a project ontology class that inherits from "StillImageRepresentation".
     * @param ontologyName     the name of the ontology to be prefixed to the class name. Defaults to "knora-api".
     *                         Should only be used, if `className` has a non-default value.
     * @return returns a [[ChangeImageFileRequest]] holding a JSON-LD serialized request to change the resource as specified by the parameters.
     */
    def make(
      resourceIRI: String,
      internalFilename: String,
      valueIRI: String,
      className: String = stillImageRepresentation,
      ontologyName: String = knoraApiPrefix
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

  /**
   * Case class containing the JSON-LD serialization of a request to change a "knora-api:MovingImageRepresentation".
   *
   * @param value the JSON-LD serialized request.
   */
  sealed abstract case class ChangeVideoFileRequest private (value: String)

  /**
   * Companion object to the [[ChangeVideoFileRequest]] case class.
   */
  object ChangeVideoFileRequest {

    /**
     * Smart constructor for instantiating a [[ChangeVideoFileRequest]].
     *
     * @param resourceIRI      the IRI of the resource where a property is to change.
     * @param internalFilename the new internal file name assigned by SIPI.
     * @param valueIRI         the IRI of the value property to change.
     * @param className        the class name of the resource. Defaults to "MovingImageRepresentation".
     *                         Can be a project ontology class that inherits from "MovingImageRepresentation".
     * @param ontologyName     the name of the ontology to be prefixed to the class name. Defaults to "knora-api".
     *                         Should only be used, if `className` has a non-default value.
     * @return returns a [[ChangeVideoFileRequest]] holding a JSON-LD serialized request to change the resource as specified by the parameters.
     */
    def make(
      resourceIRI: String,
      internalFilename: String,
      valueIRI: String,
      className: String = movingImageRepresentation,
      ontologyName: String = knoraApiPrefix
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

  /**
   * Case class containing the JSON-LD serialization of a request to change a "knora-api:TextRepresentation".
   *
   * @param value the JSON-LD serialized request.
   */
  sealed abstract case class ChangeTextFileRequest private (value: String)

  /**
   * Companion object to the [[ChangeTextFileRequest]] case class.
   */
  object ChangeTextFileRequest {

    /**
     * Smart constructor for instantiating a [[ChangeTextFileRequest]].
     *
     * @param resourceIRI      the IRI of the resource where a property is to change.
     * @param internalFilename the new internal file name assigned by SIPI.
     * @param valueIRI         the IRI of the value property to change.
     * @param className        the class name of the resource. Defaults to "TextRepresentation".
     *                         Can be a project ontology class that inherits from "DocumentRepresentation".
     * @param ontologyName     the name of the ontology to be prefixed to the class name. Defaults to "knora-api".
     *                         Should only be used, if `className` has a non-default value.
     * @return returns a [[ChangeTextFileRequest]] holding a JSON-LD serialized request to change the resource as specified by the parameters.
     */
    def make(
      resourceIRI: String,
      internalFilename: String,
      valueIRI: String,
      className: String = textRepresentation,
      ontologyName: String = knoraApiPrefix
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

  /**
   * Case class containing the JSON-LD serialization of a request to change a "knora-api:AudioRepresentation".
   *
   * @param value the JSON-LD serialized request.
   */
  sealed abstract case class ChangeAudioFileRequest private (value: String)

  /**
   * Companion object to the [[ChangeAudioFileRequest]] case class.
   */
  object ChangeAudioFileRequest {

    /**
     * Smart constructor for instantiating a [[ChangeAudioFileRequest]].
     *
     * @param resourceIRI      the IRI of the resource where a property is to change.
     * @param internalFilename the new internal file name assigned by SIPI.
     * @param valueIRI         the IRI of the value property to change.
     * @param className        the class name of the resource. Defaults to "AudioRepresentation".
     *                         Can be a project ontology class that inherits from "AudioRepresentation".
     * @param ontologyName     the name of the ontology to be prefixed to the class name. Defaults to "knora-api".
     *                         Should only be used, if `className` has a non-default value.
     * @return returns a [[ChangeAudioFileRequest]] holding a JSON-LD serialized request to change the resource as specified by the parameters.
     */
    def make(
      resourceIRI: String,
      internalFilename: String,
      valueIRI: String,
      className: String = audioRepresentation,
      ontologyName: String = knoraApiPrefix
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

  /**
   * Case class containing the JSON-LD serialization of a request to change a "knora-api:ArchiveRepresentation".
   *
   * @param value the JSON-LD serialized request.
   */
  sealed abstract case class ChangeArchiveFileRequest private (value: String)

  /**
   * Companion object to the [[ChangeArchiveFileRequest]] case class.
   */
  object ChangeArchiveFileRequest {

    /**
     * Smart constructor for instantiating a [[ChangeArchiveFileRequest]].
     *
     * @param resourceIRI      the IRI of the resource where a property is to change.
     * @param internalFilename the new internal file name assigned by SIPI.
     * @param valueIRI         the IRI of the value property to change.
     * @param className        the class name of the resource. Defaults to "ArchiveRepresentation".
     *                         Can be a project ontology class that inherits from "ArchiveRepresentation".
     * @param ontologyName     the name of the ontology to be prefixed to the class name. Defaults to "knora-api".
     *                         Should only be used, if `className` has a non-default value.
     * @return returns a [[ChangeArchiveFileRequest]] holding a JSON-LD serialized request to change the resource as specified by the parameters.
     */
    def make(
      resourceIRI: String,
      internalFilename: String,
      valueIRI: String,
      className: String = archiveRepresentation,
      ontologyName: String = knoraApiPrefix
    ): ChangeArchiveFileRequest = new ChangeArchiveFileRequest(
      ChangeFileRequest
        .make(
          fileValueType = FileValueType.ArchiveFileValue,
          resourceIRI = resourceIRI,
          internalFilename = internalFilename,
          valueIRI = valueIRI,
          className = className,
          ontologyName = ontologyName
        )
        .value
    ) {}
  }

  /**
   * Case class containing the JSON-LD serialization of a request to change a file representation.
   *
   * @param value the JSON-LD serialized request.
   */
  sealed abstract case class ChangeFileRequest private (value: String)

  /**
   * Companion object to the [[ChangeFileRequest]] case class.
   */
  object ChangeFileRequest {

    /**
     * Smart constructor for instantiating a [[ChangeFileRequest]].
     *
     * '''Note:''' This is a low level model which normally should not be called directly.
     * Instead one of the following higher level value objects should be used, which internally call this one:
     *
     *  - [[ChangeDocumentFileRequest]]
     *  - [[ChangeTextFileRequest]]
     *  - [[ChangeImageFileRequest]]
     *  - [[ChangeAudioFileRequest]]
     *  - [[ChangeVideoFileRequest]]
     *  - [[ChangeArchiveFileRequest]]
     *
     * @param fileValueType    the [[FileValueType]] of the resource.
     * @param className        the class name of the resource.
     * @param resourceIRI      the IRI of the resource where the respective fileValue property is to change.
     * @param internalFilename the new internal file name assigned by SIPI.
     * @param valueIRI         the IRI of the value property to change.
     * @param ontologyName     the name of the ontology to be prefixed to the class name.
     * @return returns a [[ChangeFileRequest]] holding a JSON-LD serialized request to change the resource as specified by the parameters.
     */
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
        case FileValueType.ArchiveFileValue     => "knora-api:hasArchiveFileValue"
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

}

/**
 * Models for generating message objects to manipulate file representations.
 */
object FileMessageModels {
  private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

  /**
   * Case class holding a [[CreateResourceV2]] object.
   *
   * @param value the [[CreateResourceV2]] message.
   */
  sealed abstract case class CreateDocumentMessage private (value: CreateResourceV2)

  /**
   * Companion object to the [[CreateDocumentMessage]] case class.
   */
  object CreateDocumentMessage {

    /**
     * Smart constructor for instantiating a [[CreateDocumentMessage]]
     *
     * @param resourceIri       the IRI of the resource to create.
     * @param internalFilename  the internal file name assigned by SIPI.
     * @param resourceClassIri  the IRI of the resource class. Defaults to `knora-api:DocumentRepresentation`
     * @param originalFilename  the original file name previous to the SIPI upload.
     *                          Optional. Defaults to `Some("test.pdf")`.
     * @param pageCount         Number of pages in the document. Optional. Defaults to `Some(1)`.
     * @param dimX              width of the document. Optional. Defaults to `Some(100)`
     * @param dimY              height of the document. Optional. Defaults to `Some(100)`
     * @param label             the `rdfs:label` of the resource. Defaults to `"test document"`
     * @param comment           comment on the resource. Optional. Defaults to `Some("This is a document")`
     * @param project           the project to which the resource belongs.
     *                          Defaults to [[SharedTestDataADM.anythingProject]]
     * @param permissions       permissions on the resource. Optional. Defaults to `None`.
     * @param valueIRI          custom IRI for the value. Optional. Defaults to `None`.
     *                          If `None`, an IRI will be generated.
     * @param valueUUID         custom UUID for the value. Optional. Defaults to `None`.
     *                          If `None`, a UUID will be generated.
     * @param valueCreationDate custom creation date for the value. Optional.
     *                          Defaults to `None`. If `None`, the current instant will be used.
     * @param valuePermissions  custom permissions for the value. Optional. Defaults to `None`.
     *                          If `None`, the default permissions will be used.
     * @return a [[CreateDocumentMessage]] containing a [[CreateResourceV2]] as specified by the parameters.
     */
    def make(
      resourceIri: IRI,
      internalFilename: String,
      resourceClassIri: SmartIri = OntologyConstants.KnoraApiV2Complex.DocumentRepresentation.toSmartIri,
      originalFilename: Option[String] = Some("test.pdf"),
      pageCount: Option[Int] = Some(1),
      dimX: Option[Int] = Some(100),
      dimY: Option[Int] = Some(100),
      label: String = "test document",
      comment: Option[String] = Some("This is a document"),
      project: ProjectADM = SharedTestDataADM.anythingProject,
      permissions: Option[String] = None,
      valueIRI: Option[SmartIri] = None,
      valueUUID: Option[UUID] = None,
      valueCreationDate: Option[Instant] = None,
      valuePermissions: Option[String] = None
    ): CreateDocumentMessage = {
      val valuePropertyIri: SmartIri = OntologyConstants.KnoraApiV2Complex.HasDocumentFileValue.toSmartIri
      val valueContent = DocumentFileValueContentV2(
        ontologySchema = ApiV2Complex,
        fileValue = FileValueV2(
          internalFilename = internalFilename,
          internalMimeType = "application/pdf",
          originalFilename = originalFilename,
          originalMimeType = Some("application/pdf")
        ),
        pageCount = pageCount,
        dimX = dimX,
        dimY = dimY,
        comment = comment
      )
      val value = CreateResourceMessage
        .make(
          resourceIri = resourceIri,
          resourceClassIri = resourceClassIri,
          label = label,
          valuePropertyIris = List(valuePropertyIri),
          values = List(
            List(
              CreateValueInNewResourceV2(
                valueContent = valueContent,
                customValueIri = valueIRI,
                customValueUUID = valueUUID,
                customValueCreationDate = valueCreationDate,
                permissions = valuePermissions
              )
            )
          ),
          project = project,
          permissions = permissions
        )
        .value
      new CreateDocumentMessage(value) {}
    }
  }

  /**
   * Case class holding a [[CreateResourceV2]] object.
   *
   * @param value the [[CreateResourceV2]] message.
   */
  sealed abstract case class CreateImageMessage private (value: CreateResourceV2)

  /**
   * Companion object to the [[CreateImageMessage]] case class.
   */
  object CreateImageMessage {

    /**
     * Smart constructor for instantiating a [[CreateImageMessage]]
     *
     * @param resourceIri       the IRI of the resource to create.
     * @param internalFilename  the internal file name assigned by SIPI.
     * @param dimX              width of the image.
     * @param dimY              height of the image.
     * @param resourceClassIri  the IRI of the resource class. Defaults to `knora-api:StillImageRepresentation`
     * @param originalFilename  the original file name previous to the SIPI upload.
     *                          Optional. Defaults to `Some("test.tiff")`.
     * @param label             the `rdfs:label` of the resource. Defaults to `"test thing picture"`
     * @param originalMimeType  the document mime type previous to the SIPI upload. Optional.
     *                          Defaults to `Some("image/tiff")`
     * @param comment           comment on the resource. Optional. Defaults to `None`
     * @param project           the project to which the resource belongs.
     *                          Defaults to [[SharedTestDataADM.anythingProject]]
     * @param permissions       permissions on the resource. Optional. Defaults to `None`.
     * @param valueIRI          custom IRI for the value. Optional. Defaults to `None`.
     *                          If `None`, an IRI will be generated.
     * @param valueUUID         custom UUID for the value. Optional. Defaults to `None`.
     *                          If `None`, a UUID will be generated.
     * @param valueCreationDate custom creation date for the value. Optional.
     *                          Defaults to `None`. If `None`, the current instant will be used.
     * @param valuePermissions  custom permissions for the value. Optional. Defaults to `None`.
     *                          If `None`, the default permissions will be used.
     * @return a [[CreateImageMessage]] containing a [[CreateResourceV2]] as specified by the parameters.
     */
    def make(
      resourceIri: IRI,
      internalFilename: String,
      dimX: Int,
      dimY: Int,
      resourceClassIri: SmartIri = OntologyConstants.KnoraApiV2Complex.StillImageRepresentation.toSmartIri,
      originalFilename: Option[String] = Some("test.tiff"),
      label: String = "test thing picture",
      originalMimeType: Option[String] = Some("image/tiff"),
      comment: Option[String] = None,
      project: ProjectADM = SharedTestDataADM.anythingProject,
      permissions: Option[String] = None,
      valueIRI: Option[SmartIri] = None,
      valueUUID: Option[UUID] = None,
      valueCreationDate: Option[Instant] = None,
      valuePermissions: Option[String] = None
    ): CreateImageMessage = {
      val valuePropertyIri: SmartIri = OntologyConstants.KnoraApiV2Complex.HasStillImageFileValue.toSmartIri
      val valueContent = StillImageFileValueContentV2(
        ontologySchema = ApiV2Complex,
        fileValue = FileValueV2(
          internalFilename = internalFilename,
          internalMimeType = "image/jp2",
          originalFilename = originalFilename,
          originalMimeType = originalMimeType
        ),
        dimX = dimX,
        dimY = dimY,
        comment = comment
      )
      val value = CreateResourceMessage
        .make(
          resourceIri = resourceIri,
          resourceClassIri = resourceClassIri,
          label = label,
          valuePropertyIris = List(valuePropertyIri),
          values = List(
            List(
              CreateValueInNewResourceV2(
                valueContent = valueContent,
                customValueIri = valueIRI,
                customValueUUID = valueUUID,
                customValueCreationDate = valueCreationDate,
                permissions = valuePermissions
              )
            )
          ),
          project = project,
          permissions = permissions
        )
        .value
      new CreateImageMessage(value) {}
    }
  }

  /**
   * Case class holding a [[CreateResourceV2]] object.
   *
   * @param value the [[CreateResourceV2]] message.
   */
  sealed abstract case class CreateArchiveMessage private (value: CreateResourceV2)

  /**
   * Companion object to the [[CreateArchiveMessage]] case class.
   */
  object CreateArchiveMessage {

    /**
     * Smart constructor for instantiating a [[CreateArchiveMessage]]
     *
     * @param resourceIri       the IRI of the resource to create.
     * @param internalFilename  the internal file name assigned by SIPI.
     * @param internalMimeType  the document mimetype as deduced by SIPI.
     * @param resourceClassIri  the IRI of the resource class. Defaults to `knora-api:StillImageRepresentation`
     * @param originalFilename  the original file name previous to the SIPI upload.
     *                          Optional. Defaults to `Some("test.zip")`.
     * @param label             the `rdfs:label` of the resource. Defaults to `"test archive"`
     * @param comment           comment on the resource. Optional. Defaults to `Some("This is a zip archive")`
     * @param project           the project to which the resource belongs.
     *                          Defaults to [[SharedTestDataADM.anythingProject]]
     * @param permissions       permissions on the resource. Optional. Defaults to `None`.
     * @param valueIRI          custom IRI for the value. Optional. Defaults to `None`.
     *                          If `None`, an IRI will be generated.
     * @param valueUUID         custom UUID for the value. Optional. Defaults to `None`.
     *                          If `None`, a UUID will be generated.
     * @param valueCreationDate custom creation date for the value. Optional.
     *                          Defaults to `None`. If `None`, the current instant will be used.
     * @param valuePermissions  custom permissions for the value. Optional. Defaults to `None`.
     *                          If `None`, the default permissions will be used.
     * @return a [[CreateArchiveMessage]] containing a [[CreateResourceV2]] as specified by the parameters.
     */
    def make(
      resourceIri: IRI,
      internalFilename: String,
      internalMimeType: String,
      resourceClassIri: SmartIri = OntologyConstants.KnoraApiV2Complex.ArchiveRepresentation.toSmartIri,
      originalFilename: Option[String] = Some("test.zip"),
      label: String = "test archive",
      comment: Option[String] = Some("This is a zip archive"),
      project: ProjectADM = SharedTestDataADM.anythingProject,
      permissions: Option[String] = None,
      valueIRI: Option[SmartIri] = None,
      valueUUID: Option[UUID] = None,
      valueCreationDate: Option[Instant] = None,
      valuePermissions: Option[String] = None
    ): CreateArchiveMessage = {
      val valuePropertyIri: SmartIri = OntologyConstants.KnoraApiV2Complex.HasArchiveFileValue.toSmartIri
      val valueContent = ArchiveFileValueContentV2(
        ontologySchema = ApiV2Complex,
        fileValue = FileValueV2(
          internalFilename = internalFilename,
          internalMimeType = internalMimeType,
          originalFilename = originalFilename,
          originalMimeType = Some(internalMimeType)
        ),
        comment = comment
      )
      val value = CreateResourceMessage
        .make(
          resourceIri = resourceIri,
          resourceClassIri = resourceClassIri,
          label = label,
          valuePropertyIris = List(valuePropertyIri),
          values = List(
            List(
              CreateValueInNewResourceV2(
                valueContent = valueContent,
                customValueIri = valueIRI,
                customValueUUID = valueUUID,
                customValueCreationDate = valueCreationDate,
                permissions = valuePermissions
              )
            )
          ),
          project = project,
          permissions = permissions
        )
        .value
      new CreateArchiveMessage(value) {}
    }
  }

  /**
   * Case class holding a [[CreateResourceV2]] object.
   *
   * @param value the [[CreateResourceV2]] message.
   */
  sealed abstract case class CreateResourceMessage private (value: CreateResourceV2)

  /**
   * Companion object to the [[CreateResourceMessage]] case class.
   */
  object CreateResourceMessage {

    /**
     * Smart constructor for instantiating a [[CreateResourceMessage]].
     *
     * '''Note:''' This is a low level model which normally should not be called directly.
     * Instead one of the following higher level value objects should be used, which internally call this one:
     *
     *  - [[CreateDocumentMessage]]
     *  - [[CreateImageMessage]]
     *  - [[CreateArchiveMessage]]
     *
     * @param resourceIri       the IRI of the resource to create.
     * @param resourceClassIri  the IRI of the resource class.
     * @param label             the `rdfs:label` of the resource.
     * @param valuePropertyIris a list of IRIs of the value properties to be added to the resource.
     *                          Defaults to `List.empty`.
     * @param values            a list of lists, containing [[CreateValueInNewResourceV2]] objects. Defaults to `List.empty`.
     *                          The outer list should be of the same length as the `valuePropertyIris` list.
     *                          Each IRI in valuePropertyIris will be mapped to a list of [[CreateValueInNewResourceV2]] objects.
     * @param project           the project to which the resource belongs.
     *                          Defaults to [[SharedTestDataADM.anythingProject]]
     * @param permissions       permissions on the resource. Optional. Defaults to `None`.
     * @return a [[CreateResourceMessage]] containing a [[CreateResourceV2]] as specified by the parameters.
     */
    def make(
      resourceIri: IRI,
      resourceClassIri: SmartIri,
      label: String,
      valuePropertyIris: List[SmartIri] = List.empty,
      values: List[List[CreateValueInNewResourceV2]] = List.empty,
      project: ProjectADM = SharedTestDataADM.anythingProject,
      permissions: Option[String] = None
    ): CreateResourceMessage = {

      val inputValues: Map[SmartIri, Seq[CreateValueInNewResourceV2]] = valuePropertyIris.zip(values).toMap

      val inputResource = CreateResourceV2(
        resourceIri = Some(resourceIri.toSmartIri),
        resourceClassIri = resourceClassIri,
        label = label,
        values = inputValues,
        projectADM = project,
        permissions = permissions
      )
      new CreateResourceMessage(inputResource) {}
    }
  }
}
