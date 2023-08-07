/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.models.filemodels

import java.time.Instant
import java.util.UUID

import org.knora.webapi.messages.IriConversions._
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectADM
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.v2.responder.resourcemessages.CreateResourceV2
import org.knora.webapi.messages.v2.responder.resourcemessages.CreateValueInNewResourceV2
import org.knora.webapi.messages.v2.responder.valuemessages.UpdateValueContentV2
import org.knora.webapi.messages.v2.responder.valuemessages.UpdateValueRequestV2
import org.knora.webapi.sharedtestdata.SharedTestDataADM

sealed abstract case class UploadFileRequest private (
  fileType: FileType,
  internalFilename: String,
  label: String,
  resourceIRI: Option[String] = None
) {

  /**
   * Create a JSON-LD serialization of the request. This can be used for e2e and integration tests.
   *
   * @param className    the class name of the resource. Optional.
   * @param ontologyName the name of the ontology to be prefixed to the class name. Defaults to `"knora-api"`
   * @param uuid         the uuid of the project to which the resource should be added. Defaults to `"0001"`
   * @param ontologyIRI  IRI of the ontology, to which the prefix should resolve. Optional.
   * @return JSON-LD serialization of the request.
   */
  def toJsonLd(
    shortcode: String = "0001",
    ontologyName: String = "knora-api",
    className: Option[String] = None,
    ontologyIRI: Option[String] = None
  ): String = {
    val fileValuePropertyName = FileModelUtil.getFileValuePropertyName(fileType)
    val fileValueType         = FileModelUtil.getFileValueType(fileType)
    val context               = FileModelUtil.getJsonLdContext(ontologyName, ontologyIRI)
    val classNameWithDefaults = className match {
      case Some(v) => v
      case None    => FileModelUtil.getDefaultClassName(fileType)
    }
    val resorceIRIOrEmptyString = resourceIRI match {
      case Some(v) => s""" "@id": "$v",\n  """
      case None    => ""
    }

    s"""{
       |  $resorceIRIOrEmptyString"@type" : "$ontologyName:$classNameWithDefaults",
       |  "$fileValuePropertyName" : {
       |    "@type" : "$fileValueType",
       |    "knora-api:fileValueHasFilename" : "$internalFilename"
       |  },
       |  "knora-api:attachedToProject" : {
       |    "@id" : "http://rdfh.ch/projects/$shortcode"
       |  },
       |  "rdfs:label" : "$label",
       |  $context}""".stripMargin
  }

  /**
   * Represents the present [[UploadFileRequest]] as a [[CreateResourceV2]].
   *
   * Various custom values can be supplied. If not, reasonable default values for testing purposes will be used.
   *
   * @param resourceIri             the custom IRI of the resource. Optional. Defaults to None. If None, a random IRI is generated
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
   * @param resourcePermissions     permissions for the resource. Optional. If none, the default permissions are used.
   * @param resourceCreationDate    custom creation date of the resource. Optional.
   * @param valuePropertyIRI        property IRI of the value. Optional.
   * @param resourceClassIRI        resource class IRI. Optional.
   * @param project                 the project to which the resource belongs. Optional. Defaults to None.
   *                                If None, [[SharedTestDataADM.anythingProject]] is used.
   * @return a [[CreateResourceV2]] representation of the [[UploadFileRequest]]
   */
  def toMessage(
    resourceIri: Option[String] = None,
    internalMimeType: Option[String] = None,
    originalFilename: Option[String] = None,
    originalMimeType: Option[String] = None,
    comment: Option[String] = None,
    customValueIri: Option[SmartIri] = None,
    customValueUUID: Option[UUID] = None,
    customValueCreationDate: Option[Instant] = None,
    valuePermissions: Option[String] = None,
    resourcePermissions: Option[String] = None,
    resourceCreationDate: Option[Instant] = None,
    resourceClassIRI: Option[SmartIri] = None,
    valuePropertyIRI: Option[SmartIri] = None,
    project: Option[ProjectADM] = None
  ): CreateResourceV2 = {
    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    val projectOrDefault = project match {
      case Some(p) => p
      case None    => SharedTestDataADM.anythingProject
    }
    val resourceIRIOrDefault = resourceIri match {
      case Some(value) => value
      case None        => stringFormatter.makeRandomResourceIri(projectOrDefault.shortcode)
    }
    val resourceClassIRIOrDefault: SmartIri = resourceClassIRI match {
      case Some(iri) => iri
      case None      => FileModelUtil.getFileRepresentationClassIri(fileType)
    }
    val fileValuePropertyIRIOrDefault: SmartIri = valuePropertyIRI match {
      case Some(iri) => iri
      case None      => FileModelUtil.getFileRepresentationPropertyIri(fileType)
    }
    val valueContent = FileModelUtil.getFileValueContent(
      fileType = fileType,
      internalFilename = internalFilename,
      internalMimeType = internalMimeType,
      originalFilename = originalFilename,
      originalMimeType = originalMimeType,
      comment = comment
    )

    val values = List(
      CreateValueInNewResourceV2(
        valueContent = valueContent,
        customValueIri = customValueIri,
        customValueUUID = customValueUUID,
        customValueCreationDate = customValueCreationDate,
        permissions = valuePermissions
      )
    )
    val inputValues: Map[SmartIri, Seq[CreateValueInNewResourceV2]] = Map(fileValuePropertyIRIOrDefault -> values)

    CreateResourceV2(
      resourceIri = Some(resourceIRIOrDefault.toSmartIri),
      resourceClassIri = resourceClassIRIOrDefault,
      label = label,
      values = inputValues,
      projectADM = projectOrDefault,
      permissions = resourcePermissions,
      creationDate = resourceCreationDate
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
   * @param fileType         the [[FileType]] of the resource.
   * @param internalFilename the internal file name assigned by SIPI.
   * @param label            the rdf:label
   * @return returns a [[UploadFileRequest]] object storing all information needed to generate a Message
   *         or JSON-LD serialization that can be used to generate the respective resource in the API.
   */
  def make(
    fileType: FileType,
    internalFilename: String,
    label: String = "test label",
    resourceIRI: Option[String] = None
  ): UploadFileRequest =
    new UploadFileRequest(
      fileType = fileType,
      internalFilename = internalFilename,
      label = label,
      resourceIRI = resourceIRI
    ) {}
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
    val fileValueType         = FileModelUtil.getFileValueType(fileType)
    val context               = FileModelUtil.getJsonLdContext(ontologyName)

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

  /**
   * @param internalMimeType     internal mimetype, as provided by SIPI. Optional.
   * @param originalFilename     original filename before the upload. Optional.
   * @param originalMimeType     file mimetype before the upload. Optional.
   * @param comment              rdfs:comment to the change. Optional.
   * @param requestingUser       the user issuing the request. Optional.
   * @param permissions          permissions of the updated file value. Optional.
   * @param valueCreationDate    custom creation date of the updated value. Optional.
   * @param newValueVersionIri   custom IRI of the new version of the value. Optional.
   * @param resourceClassIRI     the resource class IRI. Optional.
   * @return
   */
  def toMessage(
    internalMimeType: Option[String] = None,
    originalFilename: Option[String] = None,
    originalMimeType: Option[String] = None,
    comment: Option[String] = None,
    requestingUser: UserADM = SharedTestDataADM.rootUser,
    permissions: Option[String] = None,
    valueCreationDate: Option[Instant] = None,
    newValueVersionIri: Option[SmartIri] = None,
    resourceClassIRI: Option[SmartIri] = None
  ): UpdateValueRequestV2 = {
    val propertyIRI = FileModelUtil.getFileRepresentationPropertyIri(fileType)
    val resourceClassIRIWithDefault = resourceClassIRI match {
      case Some(value) => value
      case None        => FileModelUtil.getFileValueTypeIRI(fileType)
    }
    val valueContent = FileModelUtil.getFileValueContent(
      fileType = fileType,
      internalFilename = internalFilename,
      internalMimeType = internalMimeType,
      originalFilename = originalFilename,
      originalMimeType = originalMimeType,
      comment = comment
    )
    UpdateValueRequestV2(
      updateValue = UpdateValueContentV2(
        resourceIri = resourceIRI,
        resourceClassIri = resourceClassIRIWithDefault,
        propertyIri = propertyIRI,
        valueIri = valueIRI,
        valueContent = valueContent,
        permissions = permissions,
        valueCreationDate = valueCreationDate,
        newValueVersionIri = newValueVersionIri
      ),
      requestingUser = requestingUser,
      apiRequestID = UUID.randomUUID
    )
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
