/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.testservices
import sttp.client4.*
import zio.*

import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.util.rdf.JsonLDDocument
import org.knora.webapi.models.filemodels.FileType
import org.knora.webapi.models.filemodels.UploadFileRequest
import org.knora.webapi.slice.admin.domain.model.*
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.common.KnoraIris.OntologyIri
import org.knora.webapi.slice.common.KnoraIris.ResourceIri
import org.knora.webapi.testservices.TestDspIngestClient.UploadedFile

final case class TestResourcesApiClient(private val apiClient: TestApiClient) {

  def createStillImageRepresentation(
    shortcode: Shortcode,
    ontologyIri: OntologyIri,
    resourceClassName: String,
    img: UploadedFile,
    user: User,
    legalInfo: LegalInfo,
  ): Task[Response[Either[String, String]]] = {
    val resourceClassIri = ontologyIri.makeClass(resourceClassName)
    val jsonLd = UploadFileRequest
      .make(
        FileType.StillImageFile(),
        img.internalFilename,
        copyrightHolder = legalInfo.copyrightHolder,
        authorship = legalInfo.authorship,
        licenseIri = legalInfo.licenseIri,
      )
      .toJsonLd(
        shortcode = shortcode,
        className = Some(resourceClassIri.name),
        ontologyName = ontologyIri.ontologyName.value,
      )
    apiClient.postJsonLd(uri"/v2/resources", jsonLd, user)
  }

  def getResource(resourceIri: ResourceIri): Task[Response[Either[String, JsonLDDocument]]] =
    apiClient.getJsonLdDocument(uri"/v2/resources/$resourceIri")
}

object TestResourcesApiClient {

  def createStillImageRepresentation(
    shortcode: Shortcode,
    ontologyIri: OntologyIri,
    resourceClassName: String,
    img: UploadedFile,
    user: User,
    legalInfo: LegalInfo,
  ): ZIO[TestResourcesApiClient, Throwable, Response[Either[String, String]]] =
    ZIO.serviceWithZIO[TestResourcesApiClient](
      _.createStillImageRepresentation(
        shortcode,
        ontologyIri,
        resourceClassName,
        img,
        user,
        legalInfo,
      ),
    )

  def getResource(resourceIri: String)(implicit
    sf: StringFormatter,
  ): ZIO[TestResourcesApiClient, Throwable, Response[Either[String, JsonLDDocument]]] =
    ZIO.attempt(ResourceIri.unsafeFrom(sf.toSmartIri(resourceIri))).flatMap(getResource)

  def getResource(
    resourceIri: ResourceIri,
  ): ZIO[TestResourcesApiClient, Throwable, Response[Either[String, JsonLDDocument]]] =
    ZIO.serviceWithZIO[TestResourcesApiClient](_.getResource(resourceIri))

  val layer = ZLayer.derive[TestResourcesApiClient]
}
