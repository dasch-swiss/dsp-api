/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.models.filemodels

import org.knora.webapi.ApiV2Complex
import org.knora.webapi.messages.IriConversions._
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectADM
import org.knora.webapi.messages.v2.responder.resourcemessages.{CreateResourceV2, CreateValueInNewResourceV2}
import org.knora.webapi.messages.v2.responder.valuemessages.{
  ArchiveFileValueContentV2,
  AudioFileValueContentV2,
  DocumentFileValueContentV2,
  FileValueV2,
  MovingImageFileValueContentV2,
  StillImageFileValueContentV2,
  TextFileValueContentV2
}
import org.knora.webapi.messages.{SmartIri, StringFormatter}
import org.knora.webapi.sharedtestdata.SharedTestDataADM

import java.time.Instant
import java.util.UUID

sealed abstract case class UploadFileRequest private (
  fileType: FileType,
  internalFilename: String,
  className: String,
  ontologyName: String,
  shortcode: String,
  resourceIri: String,
  dimX: Option[Int],
  dimY: Option[Int],
  pageCount: Option[Int],
  comment: Option[String],
  internalMimeType: Option[String],
  originalFilename: Option[String],
  originalMimeType: Option[String],
  customValueIri: Option[SmartIri],
  customValueUUID: Option[UUID],
  customValueCreationDate: Option[Instant],
  valuePermissions: Option[String],
  label: String,
  resourcePermissions: Option[String],
  project: ProjectADM
) {

  /**
   * Create a JSON-LD serialization of the request. This can be used for e2e and integration tests.
   *
   * @return JSON-LD serialization of the request.
   */
  def toJsonLd: String = {
    val fileValuePropertyName = FileModelUtil.getFileValuePropertyName(fileType)
    val fileValueType = FileModelUtil.getFileValueType(fileType)
    val context = FileModelUtil.getJsonLdContext(ontologyName)

    s"""{
       |  "@type" : "$ontologyName:$className",
       |  "$fileValuePropertyName" : {
       |    "@type" : "$fileValueType",
       |    "knora-api:fileValueHasFilename" : "$internalFilename"
       |  },
       |  "knora-api:attachedToProject" : {
       |    "@id" : "http://rdfh.ch/projects/$shortcode"
       |  },
       |  "rdfs:label" : "test label",
       |  $context}""".stripMargin
  }

  def toMessage: CreateResourceV2 = {
    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    val resourceClassIri: SmartIri = FileModelUtil.getFileRepresentationClassIri(fileType)
    val fileValuePropertyIri: SmartIri = FileModelUtil.getFileRepresentationPropertyIri(fileType)
    val valueContent = fileType match {
      case FileType.DocumentFile =>
        DocumentFileValueContentV2(
          ontologySchema = ApiV2Complex,
          fileValue = FileValueV2(
            internalFilename = internalFilename,
            internalMimeType = internalMimeType match {
              case Some(v) => v
              case None    => "application/pdf"
            },
            originalFilename = originalFilename,
            originalMimeType = Some("application/pdf")
          ),
          pageCount = pageCount,
          dimX = dimX,
          dimY = dimY,
          comment = comment
        )
      case FileType.StillImageFile =>
        StillImageFileValueContentV2(
          ontologySchema = ApiV2Complex,
          fileValue = FileValueV2(
            internalFilename = internalFilename,
            internalMimeType = "image/jp2",
            originalFilename = originalFilename,
            originalMimeType = originalMimeType
          ),
          dimX = dimX.get,
          dimY = dimY.get,
          comment = comment
        )
      case FileType.MovingImageFile =>
        MovingImageFileValueContentV2(
          ontologySchema = ApiV2Complex,
          fileValue = FileValueV2(
            internalFilename = internalFilename,
            internalMimeType = internalMimeType.get,
            originalFilename = originalFilename,
            originalMimeType = internalMimeType
          ),
          dimX = dimX.get,
          dimY = dimY.get
        )
      case FileType.TextFile =>
        TextFileValueContentV2(
          ontologySchema = ApiV2Complex,
          fileValue = FileValueV2(
            internalFilename = internalFilename,
            internalMimeType = internalMimeType.get,
            originalFilename = originalFilename,
            originalMimeType = internalMimeType
          )
        )
      case FileType.AudioFile =>
        AudioFileValueContentV2(
          ontologySchema = ApiV2Complex,
          fileValue = FileValueV2(
            internalFilename = internalFilename,
            internalMimeType = internalMimeType.get,
            originalFilename = originalFilename,
            originalMimeType = internalMimeType
          )
        )
      case FileType.ArchiveFile =>
        ArchiveFileValueContentV2(
          ontologySchema = ApiV2Complex,
          fileValue = FileValueV2(
            internalFilename = internalFilename,
            internalMimeType = internalMimeType match {
              case Some(v) => v
              case None    => "application/zip"
            },
            originalFilename = originalFilename,
            originalMimeType = internalMimeType
          ),
          comment = comment
        )
    }

    val values = List(
      CreateValueInNewResourceV2(
        valueContent = valueContent,
        customValueIri = customValueIri,
        customValueUUID = customValueUUID,
        customValueCreationDate = customValueCreationDate,
        permissions = valuePermissions
      )
    )
    val inputValues: Map[SmartIri, Seq[CreateValueInNewResourceV2]] = Map(fileValuePropertyIri -> values)

    CreateResourceV2(
      resourceIri = Some(resourceIri.toSmartIri),
      resourceClassIri = resourceClassIri,
      label = label,
      values = inputValues,
      projectADM = project,
      permissions = resourcePermissions
    )
  }
}

/**
 * Helper object for creating a request to upload a file.
 *
 * Can be instantiated by calling `UploadFileRequest.make()`.
 *
 * To generate a JSON-LD request, call `.toJsonLd`.
 *
 * To generate a [[CreateResourceV2]] message, call `.toMessage`
 */
object UploadFileRequest {
  implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

  /**
   * Smart constructor for instantiating a [[UploadFileRequest]].
   *
   * @param fileType                the [[FileType]] of the resource.
   * @param internalFilename        the internal file name assigned by SIPI.
   * @param className               the class name of the resource. Optional.
   * @param ontologyName            the name of the ontology to be prefixed to the class name. Defaults to `"knora-api"`
   * @param shortcode               the shortcode of the project to which the resource should be added. Defaults to `"0001"`
   * @param resourceIri             the custom IRI of the resource. Optional. Defaults to None. If None, a random IRI is generated
   * @param dimX                    the width of the file, if applicable. Optional. Defaults to None.
   *                                If None, the file type specific default is used.
   * @param dimY                    the height of the file, if applicable. Optional. Defaults to None.
   *                                If None, the file type specific default is used.
   * @param pageCount               the page count of the file, if applicable. Optional. Defaults to None.
   *                                If None, the file type specific default is used.
   * @param comment                 comment. Optional.
   * @param internalMimeType        internal mime type as determined by SIPI. Optional.
   * @param originalMimeType        original mime type previous to uploading to SIPI. Optional.
   * @param originalFilename        original filename previous to uploading to SIPI. Optional.
   * @param customValueIri          custom IRI for the value. Optional. Defaults to None.
   *                                If None, an IRI will be generated.
   * @param customValueUUID         custom UUID for the value. Optional. Defaults to None.
   *                                If None, a UUID will be generated.
   * @param customValueCreationDate custom creation date for the value. Optional. Defaults to None.
   *                                If None, the current instant will be used.
   * @param valuePermissions        custom permissions for the value. Optional. Defaults to None.
   *                                If `None`, the default permissions will be used.
   * @param label                   the resource label
   * @param resourcePermissions     permissions for the resource. Optional. If none, the default permissions are used.
   * @param project                 the project to which the resource belongs. Optional. Defaults to None.
   *                                If None, [[SharedTestDataADM.anythingProject]] is used.
   * @return returns a [[UploadFileRequest]] object storing all information needed to generate a Message
   *         or JSON-LD serialization that can be used to generate the respective resource in the API.
   */

  def make(
    fileType: FileType,
    internalFilename: String,
    className: Option[String] = None,
    ontologyName: String = "knora-api",
    shortcode: String = "0001",
    resourceIri: Option[String] = None,
    dimX: Option[Int] = None,
    dimY: Option[Int] = None,
    pageCount: Option[Int] = None,
    comment: Option[String] = None,
    internalMimeType: Option[String] = None,
    originalFilename: Option[String] = None,
    originalMimeType: Option[String] = None,
    customValueIri: Option[SmartIri] = None,
    customValueUUID: Option[UUID] = None,
    customValueCreationDate: Option[Instant] = None,
    valuePermissions: Option[String] = None,
    label: String = "test label",
    resourcePermissions: Option[String] = None,
    project: Option[ProjectADM] = None
  ): UploadFileRequest = {
    val classNameWithDefaults = className match {
      case Some(v) => v
      case None    => FileModelUtil.getDefaultClassName(fileType)
    }
    val iri = resourceIri match {
      case Some(value) => value
      case None        => stringFormatter.makeRandomResourceIri(shortcode)
    }
    val (dimXWithDefaults, dimYWithDefaults) = fileType match {
      case FileType.DocumentFile | FileType.StillImageFile | FileType.MovingImageFile =>
        (dimX, dimY) match {
          case (Some(x), Some(y)) => (Some(x), Some(y))
          case _                  => (Some(100), Some(100))
        }
      case FileType.TextFile | FileType.AudioFile | FileType.ArchiveFile => (None, None)
    }
    val pageCountWithDefaults = fileType match {
      case FileType.DocumentFile =>
        pageCount match {
          case Some(value) => Some(value)
          case None        => Some(1)
        }
      case _ => None
    }
    new UploadFileRequest(
      fileType = fileType,
      internalFilename = internalFilename,
      className = classNameWithDefaults,
      ontologyName = ontologyName,
      shortcode = shortcode,
      resourceIri = iri,
      dimX = dimXWithDefaults,
      dimY = dimYWithDefaults,
      pageCount = pageCountWithDefaults,
      comment = comment,
      internalMimeType = internalMimeType,
      originalFilename = originalFilename,
      originalMimeType = originalMimeType,
      customValueIri = customValueIri,
      customValueUUID = customValueUUID,
      customValueCreationDate = customValueCreationDate,
      valuePermissions = valuePermissions,
      label = label,
      resourcePermissions = resourcePermissions,
      project = project match {
        case Some(p) => p
        case None    => SharedTestDataADM.anythingProject
      }
    ) {}
  }
}

sealed abstract case class ChangeFileRequest private (
  fileType: FileType,
  internalFilename: String,
  resourceIRI: String,
  valueIRI: String,
  className: String,
  ontologyName: String
) {

  /**
   * Create a JSON-LD serialization of the request. This can be used for e2e and integration tests.
   *
   * @return JSON-LD serialization of the request.
   */
  def toJsonLd: String = {
    val fileValuePropertyName = FileModelUtil.getFileValuePropertyName(fileType)
    val fileValueType = FileModelUtil.getFileValueType(fileType)
    val context = FileModelUtil.getJsonLdContext(ontologyName)

    s"""{
       |  "@id" : "$resourceIRI",
       |  "@type" : "$ontologyName:$className",
       |  "$fileValuePropertyName" : {
       |    "@id" : "$valueIRI",
       |    "@type" : "$fileValueType",
       |    "knora-api:fileValueHasFilename" : "$internalFilename"
       |  },
       |  $context
       |}""".stripMargin
  }
}

/**
 * Helper object for creating a request to change a file representation.
 *
 * Can be instantiated by calling `ChangeFileRequest.make()`.
 *
 * To generate a JSON-LD request, call `.toJsonLd`.
 */
object ChangeFileRequest {

  /**
   * Smart constructor for instantiating a [[ChangeFileRequest]].
   *
   * @param fileType         the [[FileType]] of the resource.
   * @param internalFilename the internal file name assigned by SIPI.
   * @param resourceIri      the IRI of the resource where a property is to change.
   * @param valueIri         the IRI of the value property to change.
   * @param className        the class name of the resource. Optional.
   * @param ontologyName     the name of the ontology to be prefixed to the class name. Defaults to `"knora-api"`
   * @return returns a [[ChangeFileRequest]] object storing all information needed to generate a Message
   *         or JSON-LD serialization that can be used to change the respective resource in the API.
   */
  def make(
    fileType: FileType,
    internalFilename: String,
    resourceIri: String,
    valueIri: String,
    className: Option[String] = None,
    ontologyName: String = "knora-api"
  ): ChangeFileRequest = {
    val classNameWithDefaults = className match {
      case Some(v) => v
      case None    => FileModelUtil.getDefaultClassName(fileType)
    }
    new ChangeFileRequest(
      fileType = fileType,
      internalFilename = internalFilename,
      resourceIRI = resourceIri,
      valueIRI = valueIri,
      className = classNameWithDefaults,
      ontologyName = ontologyName
    ) {}
  }
}
