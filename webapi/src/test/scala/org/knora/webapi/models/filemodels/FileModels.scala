/*
 * Copyright © 2021 Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.models.filemodels

import org.knora.webapi.{ApiV2Complex, IRI}
import org.knora.webapi.messages.{OntologyConstants, SmartIri, StringFormatter}
import org.knora.webapi.messages.v2.responder.resourcemessages.{CreateResourceV2, CreateValueInNewResourceV2}
import org.knora.webapi.messages.v2.responder.valuemessages.{
  ArchiveFileValueContentV2,
  DocumentFileValueContentV2,
  FileValueV2,
  StillImageFileValueContentV2
}
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.messages.IriConversions._
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectADM
import FileModelConstants._

import java.time.Instant
import java.util.UUID

sealed abstract case class UploadFileRequest private (
  fileType: FileType,
  internalFilename: String,
  className: String,
  ontologyName: String,
  shortcode: String
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
       |  $context
       |}""".stripMargin
  }
}

/**
 * Helper object for creating a request to upload a file.
 *
 * Can be instantiated by calling `UploadFileRequest.make()`.
 *
 * To generate a JSON-LD request, call `.toJsonLd`.
 *
 * // TODO: method for message
 */
object UploadFileRequest {

  /**
   * Smart constructor for instantiating a [[UploadFileRequest]].
   *
   * @param fileType         the [[FileType]] of the resource.
   * @param internalFilename the internal file name assigned by SIPI.
   * @param className        the class name of the resource. Optional.
   * @param ontologyName     the name of the ontology to be prefixed to the class name. Defaults to `"knora-api"`
   * @param shortcode        the shortcode of the project to which the resource should be added. Defaults to `"0001"`
   * @return returns a [[UploadFileRequest]] object storing all information needed to generate a Message
   *         or JSON-LD serialization that can be used to generate the respective resource in the API.
   */
  def make(
    fileType: FileType,
    internalFilename: String,
    className: Option[String] = None,
    ontologyName: String = "knora-api",
    shortcode: String = "0001"
  ): UploadFileRequest = {
    val classNameWithDefaults = className match {
      case Some(v) => v
      case None    => FileModelUtil.getDefaultClassName(fileType)
    }
    new UploadFileRequest(
      fileType = fileType,
      internalFilename = internalFilename,
      className = classNameWithDefaults,
      ontologyName = ontologyName,
      shortcode = shortcode
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
       |    "@type" : "${fileValueType}",
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
 *
 * // TODO: method for message
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

///**
// * Models for generating message objects to manipulate file representations.
// */
//object FileMessageModels {
//  private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
//
//  /**
//   * Case class holding a [[CreateResourceV2]] object.
//   *
//   * @param value the [[CreateResourceV2]] message.
//   */
//  sealed abstract case class CreateDocumentMessage private (value: CreateResourceV2)
//
//  /**
//   * Companion object to the [[CreateDocumentMessage]] case class.
//   */
//  object CreateDocumentMessage {
//
//    /**
//     * Smart constructor for instantiating a [[CreateDocumentMessage]]
//     *
//     * @param resourceIri       the IRI of the resource to create.
//     * @param internalFilename  the internal file name assigned by SIPI.
//     * @param resourceClassIri  the IRI of the resource class. Defaults to `knora-api:DocumentRepresentation`
//     * @param originalFilename  the original file name previous to the SIPI upload.
//     *                          Optional. Defaults to `Some("test.pdf")`.
//     * @param pageCount         Number of pages in the document. Optional. Defaults to `Some(1)`.
//     * @param dimX              width of the document. Optional. Defaults to `Some(100)`
//     * @param dimY              height of the document. Optional. Defaults to `Some(100)`
//     * @param label             the `rdfs:label` of the resource. Defaults to `"test document"`
//     * @param comment           comment on the resource. Optional. Defaults to `Some("This is a document")`
//     * @param project           the project to which the resource belongs.
//     *                          Defaults to [[SharedTestDataADM.anythingProject]]
//     * @param permissions       permissions on the resource. Optional. Defaults to `None`.
//     * @param valueIRI          custom IRI for the value. Optional. Defaults to `None`.
//     *                          If `None`, an IRI will be generated.
//     * @param valueUUID         custom UUID for the value. Optional. Defaults to `None`.
//     *                          If `None`, a UUID will be generated.
//     * @param valueCreationDate custom creation date for the value. Optional.
//     *                          Defaults to `None`. If `None`, the current instant will be used.
//     * @param valuePermissions  custom permissions for the value. Optional. Defaults to `None`.
//     *                          If `None`, the default permissions will be used.
//     * @return a [[CreateDocumentMessage]] containing a [[CreateResourceV2]] as specified by the parameters.
//     */
//    def make(
//      resourceIri: IRI,
//      internalFilename: String,
//      resourceClassIri: SmartIri = OntologyConstants.KnoraApiV2Complex.DocumentRepresentation.toSmartIri,
//      originalFilename: Option[String] = Some("test.pdf"),
//      pageCount: Option[Int] = Some(1),
//      dimX: Option[Int] = Some(100),
//      dimY: Option[Int] = Some(100),
//      label: String = "test document",
//      comment: Option[String] = Some("This is a document"),
//      project: ProjectADM = SharedTestDataADM.anythingProject,
//      permissions: Option[String] = None,
//      valueIRI: Option[SmartIri] = None,
//      valueUUID: Option[UUID] = None,
//      valueCreationDate: Option[Instant] = None,
//      valuePermissions: Option[String] = None
//    ): CreateDocumentMessage = {
//      val valuePropertyIri: SmartIri = OntologyConstants.KnoraApiV2Complex.HasDocumentFileValue.toSmartIri
//      val valueContent = DocumentFileValueContentV2(
//        ontologySchema = ApiV2Complex,
//        fileValue = FileValueV2(
//          internalFilename = internalFilename,
//          internalMimeType = "application/pdf",
//          originalFilename = originalFilename,
//          originalMimeType = Some("application/pdf")
//        ),
//        pageCount = pageCount,
//        dimX = dimX,
//        dimY = dimY,
//        comment = comment
//      )
//      val value = CreateResourceMessage
//        .make(
//          resourceIri = resourceIri,
//          resourceClassIri = resourceClassIri,
//          label = label,
//          valuePropertyIris = List(valuePropertyIri),
//          values = List(
//            List(
//              CreateValueInNewResourceV2(
//                valueContent = valueContent,
//                customValueIri = valueIRI,
//                customValueUUID = valueUUID,
//                customValueCreationDate = valueCreationDate,
//                permissions = valuePermissions
//              )
//            )
//          ),
//          project = project,
//          permissions = permissions
//        )
//        .value
//      new CreateDocumentMessage(value) {}
//    }
//  }
//
//  /**
//   * Case class holding a [[CreateResourceV2]] object.
//   *
//   * @param value the [[CreateResourceV2]] message.
//   */
//  sealed abstract case class CreateImageMessage private (value: CreateResourceV2)
//
//  /**
//   * Companion object to the [[CreateImageMessage]] case class.
//   */
//  object CreateImageMessage {
//
//    /**
//     * Smart constructor for instantiating a [[CreateImageMessage]]
//     *
//     * @param resourceIri       the IRI of the resource to create.
//     * @param internalFilename  the internal file name assigned by SIPI.
//     * @param dimX              width of the image.
//     * @param dimY              height of the image.
//     * @param resourceClassIri  the IRI of the resource class. Defaults to `knora-api:StillImageRepresentation`
//     * @param originalFilename  the original file name previous to the SIPI upload.
//     *                          Optional. Defaults to `Some("test.tiff")`.
//     * @param label             the `rdfs:label` of the resource. Defaults to `"test thing picture"`
//     * @param originalMimeType  the document mime type previous to the SIPI upload. Optional.
//     *                          Defaults to `Some("image/tiff")`
//     * @param comment           comment on the resource. Optional. Defaults to `None`
//     * @param project           the project to which the resource belongs.
//     *                          Defaults to [[SharedTestDataADM.anythingProject]]
//     * @param permissions       permissions on the resource. Optional. Defaults to `None`.
//     * @param valueIRI          custom IRI for the value. Optional. Defaults to `None`.
//     *                          If `None`, an IRI will be generated.
//     * @param valueUUID         custom UUID for the value. Optional. Defaults to `None`.
//     *                          If `None`, a UUID will be generated.
//     * @param valueCreationDate custom creation date for the value. Optional.
//     *                          Defaults to `None`. If `None`, the current instant will be used.
//     * @param valuePermissions  custom permissions for the value. Optional. Defaults to `None`.
//     *                          If `None`, the default permissions will be used.
//     * @return a [[CreateImageMessage]] containing a [[CreateResourceV2]] as specified by the parameters.
//     */
//    def make(
//      resourceIri: IRI,
//      internalFilename: String,
//      dimX: Int,
//      dimY: Int,
//      resourceClassIri: SmartIri = OntologyConstants.KnoraApiV2Complex.StillImageRepresentation.toSmartIri,
//      originalFilename: Option[String] = Some("test.tiff"),
//      label: String = "test thing picture",
//      originalMimeType: Option[String] = Some("image/tiff"),
//      comment: Option[String] = None,
//      project: ProjectADM = SharedTestDataADM.anythingProject,
//      permissions: Option[String] = None,
//      valueIRI: Option[SmartIri] = None,
//      valueUUID: Option[UUID] = None,
//      valueCreationDate: Option[Instant] = None,
//      valuePermissions: Option[String] = None
//    ): CreateImageMessage = {
//      val valuePropertyIri: SmartIri = OntologyConstants.KnoraApiV2Complex.HasStillImageFileValue.toSmartIri
//      val valueContent = StillImageFileValueContentV2(
//        ontologySchema = ApiV2Complex,
//        fileValue = FileValueV2(
//          internalFilename = internalFilename,
//          internalMimeType = "image/jp2",
//          originalFilename = originalFilename,
//          originalMimeType = originalMimeType
//        ),
//        dimX = dimX,
//        dimY = dimY,
//        comment = comment
//      )
//      val value = CreateResourceMessage
//        .make(
//          resourceIri = resourceIri,
//          resourceClassIri = resourceClassIri,
//          label = label,
//          valuePropertyIris = List(valuePropertyIri),
//          values = List(
//            List(
//              CreateValueInNewResourceV2(
//                valueContent = valueContent,
//                customValueIri = valueIRI,
//                customValueUUID = valueUUID,
//                customValueCreationDate = valueCreationDate,
//                permissions = valuePermissions
//              )
//            )
//          ),
//          project = project,
//          permissions = permissions
//        )
//        .value
//      new CreateImageMessage(value) {}
//    }
//  }
//
//  /**
//   * Case class holding a [[CreateResourceV2]] object.
//   *
//   * @param value the [[CreateResourceV2]] message.
//   */
//  sealed abstract case class CreateArchiveMessage private (value: CreateResourceV2)
//
//  /**
//   * Companion object to the [[CreateArchiveMessage]] case class.
//   */
//  object CreateArchiveMessage {
//
//    /**
//     * Smart constructor for instantiating a [[CreateArchiveMessage]]
//     *
//     * @param resourceIri       the IRI of the resource to create.
//     * @param internalFilename  the internal file name assigned by SIPI.
//     * @param internalMimeType  the document mimetype as deduced by SIPI.
//     * @param resourceClassIri  the IRI of the resource class. Defaults to `knora-api:StillImageRepresentation`
//     * @param originalFilename  the original file name previous to the SIPI upload.
//     *                          Optional. Defaults to `Some("test.zip")`.
//     * @param label             the `rdfs:label` of the resource. Defaults to `"test archive"`
//     * @param comment           comment on the resource. Optional. Defaults to `Some("This is a zip archive")`
//     * @param project           the project to which the resource belongs.
//     *                          Defaults to [[SharedTestDataADM.anythingProject]]
//     * @param permissions       permissions on the resource. Optional. Defaults to `None`.
//     * @param valueIRI          custom IRI for the value. Optional. Defaults to `None`.
//     *                          If `None`, an IRI will be generated.
//     * @param valueUUID         custom UUID for the value. Optional. Defaults to `None`.
//     *                          If `None`, a UUID will be generated.
//     * @param valueCreationDate custom creation date for the value. Optional.
//     *                          Defaults to `None`. If `None`, the current instant will be used.
//     * @param valuePermissions  custom permissions for the value. Optional. Defaults to `None`.
//     *                          If `None`, the default permissions will be used.
//     * @return a [[CreateArchiveMessage]] containing a [[CreateResourceV2]] as specified by the parameters.
//     */
//    def make(
//      resourceIri: IRI,
//      internalFilename: String,
//      internalMimeType: String,
//      resourceClassIri: SmartIri = OntologyConstants.KnoraApiV2Complex.ArchiveRepresentation.toSmartIri,
//      originalFilename: Option[String] = Some("test.zip"),
//      label: String = "test archive",
//      comment: Option[String] = Some("This is a zip archive"),
//      project: ProjectADM = SharedTestDataADM.anythingProject,
//      permissions: Option[String] = None,
//      valueIRI: Option[SmartIri] = None,
//      valueUUID: Option[UUID] = None,
//      valueCreationDate: Option[Instant] = None,
//      valuePermissions: Option[String] = None
//    ): CreateArchiveMessage = {
//      val valuePropertyIri: SmartIri = OntologyConstants.KnoraApiV2Complex.HasArchiveFileValue.toSmartIri
//      val valueContent = ArchiveFileValueContentV2(
//        ontologySchema = ApiV2Complex,
//        fileValue = FileValueV2(
//          internalFilename = internalFilename,
//          internalMimeType = internalMimeType,
//          originalFilename = originalFilename,
//          originalMimeType = Some(internalMimeType)
//        ),
//        comment = comment
//      )
//      val value = CreateResourceMessage
//        .make(
//          resourceIri = resourceIri,
//          resourceClassIri = resourceClassIri,
//          label = label,
//          valuePropertyIris = List(valuePropertyIri),
//          values = List(
//            List(
//              CreateValueInNewResourceV2(
//                valueContent = valueContent,
//                customValueIri = valueIRI,
//                customValueUUID = valueUUID,
//                customValueCreationDate = valueCreationDate,
//                permissions = valuePermissions
//              )
//            )
//          ),
//          project = project,
//          permissions = permissions
//        )
//        .value
//      new CreateArchiveMessage(value) {}
//    }
//  }
//
//  /**
//   * Case class holding a [[CreateResourceV2]] object.
//   *
//   * @param value the [[CreateResourceV2]] message.
//   */
//  sealed abstract case class CreateResourceMessage private (value: CreateResourceV2)
//
//  /**
//   * Companion object to the [[CreateResourceMessage]] case class.
//   */
//  object CreateResourceMessage {
//
//    /**
//     * Smart constructor for instantiating a [[CreateResourceMessage]].
//     *
//     * '''Note:''' This is a low level model which normally should not be called directly.
//     * Instead one of the following higher level value objects should be used, which internally call this one:
//     *
//     *  - [[CreateDocumentMessage]]
//     *  - [[CreateImageMessage]]
//     *  - [[CreateArchiveMessage]]
//     *
//     * @param resourceIri       the IRI of the resource to create.
//     * @param resourceClassIri  the IRI of the resource class.
//     * @param label             the `rdfs:label` of the resource.
//     * @param valuePropertyIris a list of IRIs of the value properties to be added to the resource.
//     *                          Defaults to `List.empty`.
//     * @param values            a list of lists, containing [[CreateValueInNewResourceV2]] objects. Defaults to `List.empty`.
//     *                          The outer list should be of the same length as the `valuePropertyIris` list.
//     *                          Each IRI in valuePropertyIris will be mapped to a list of [[CreateValueInNewResourceV2]] objects.
//     * @param project           the project to which the resource belongs.
//     *                          Defaults to [[SharedTestDataADM.anythingProject]]
//     * @param permissions       permissions on the resource. Optional. Defaults to `None`.
//     * @return a [[CreateResourceMessage]] containing a [[CreateResourceV2]] as specified by the parameters.
//     */
//    def make(
//      resourceIri: IRI,
//      resourceClassIri: SmartIri,
//      label: String,
//      valuePropertyIris: List[SmartIri] = List.empty,
//      values: List[List[CreateValueInNewResourceV2]] = List.empty,
//      project: ProjectADM = SharedTestDataADM.anythingProject,
//      permissions: Option[String] = None
//    ): CreateResourceMessage = {
//
//      val inputValues: Map[SmartIri, Seq[CreateValueInNewResourceV2]] = valuePropertyIris.zip(values).toMap
//
//      val inputResource = CreateResourceV2(
//        resourceIri = Some(resourceIri.toSmartIri),
//        resourceClassIri = resourceClassIri,
//        label = label,
//        values = inputValues,
//        projectADM = project,
//        permissions = permissions
//      )
//      new CreateResourceMessage(inputResource) {}
//    }
//  }
//}
