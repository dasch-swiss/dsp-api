/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.it.v2

import org.apache.jena.rdf.model.Model
import org.apache.jena.rdf.model.Property
import org.apache.jena.rdf.model.Resource
import org.apache.jena.vocabulary.RDF
import zio.*
import zio.http.Body
import zio.http.Response
import zio.test.*

import java.net.URLEncoder
import scala.jdk.CollectionConverters.IteratorHasAsScala
import scala.language.implicitConversions

import org.knora.webapi.E2EZSpec
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.OntologyConstants.KnoraApiV2Complex.HasCopyrightAttribution
import org.knora.webapi.messages.OntologyConstants.KnoraApiV2Complex.HasLicenseText
import org.knora.webapi.messages.OntologyConstants.KnoraApiV2Complex.HasLicenseUri
import org.knora.webapi.messages.OntologyConstants.KnoraApiV2Complex.StillImageFileValue
import org.knora.webapi.models.filemodels.FileType
import org.knora.webapi.models.filemodels.UploadFileRequest
import org.knora.webapi.slice.admin.domain.model.CopyrightAttribution
import org.knora.webapi.slice.admin.domain.model.LicenseText
import org.knora.webapi.slice.admin.domain.model.LicenseUri
import org.knora.webapi.slice.admin.domain.service.KnoraProjectService
import org.knora.webapi.slice.common.KnoraIris.ValueIri
import org.knora.webapi.slice.common.jena.JenaConversions.given
import org.knora.webapi.slice.common.jena.ModelOps
import org.knora.webapi.slice.common.jena.ModelOps.*
import org.knora.webapi.slice.common.jena.ResourceOps.*
import org.knora.webapi.slice.resourceinfo.domain.IriConverter

object CopyrightAndLicensesSpec extends E2EZSpec {

  private val aCopyrightAttribution = CopyrightAttribution.unsafeFrom("2020, On FileValue")
  private val aLicenseText          = LicenseText.unsafeFrom("CC BY-SA 4.0")
  private val aLicenseUri           = LicenseUri.unsafeFrom("https://creativecommons.org/licenses/by-sa/4.0/")

  private val givenProjectHasNoCopyrightAttributionAndLicenseSuite = suite("Creating Resources")(
    test(
      "when creating a resource without copyright attribution and license" +
        "the creation response should not contain the license and copyright attribution",
    ) {
      for {
        createResourceResponseModel <- createStillImageResource()
        actualCreatedCopyright      <- copyrightValueOption(createResourceResponseModel)
        actualCreatedLicenseText    <- licenseTextValueOption(createResourceResponseModel)
        actualCreatedLicenseUri     <- licenseUriValueOption(createResourceResponseModel)
      } yield assertTrue(
        actualCreatedCopyright.isEmpty,
        actualCreatedLicenseText.isEmpty,
        actualCreatedLicenseUri.isEmpty,
      )
    },
    test(
      "when creating a resource with copyright attribution and license " +
        "the creation response should contain the license and copyright attribution",
    ) {
      for {
        createResourceResponseModel <-
          createStillImageResource(Some(aCopyrightAttribution), Some(aLicenseText), Some(aLicenseUri))
        actualCreatedCopyright   <- copyrightValue(createResourceResponseModel)
        actualCreatedLicenseText <- licenseTextValue(createResourceResponseModel)
        actualCreatedLicenseUri  <- licenseUriValue(createResourceResponseModel)
      } yield assertTrue(
        actualCreatedCopyright == aCopyrightAttribution.value,
        actualCreatedLicenseText == aLicenseText.value,
        actualCreatedLicenseUri == aLicenseUri.value,
      )
    },
    test(
      "when creating a resource with copyright attribution and license " +
        "the response when getting the created resource should contain the license and copyright attribution",
    ) {
      for {
        createResourceResponseModel <-
          createStillImageResource(Some(aCopyrightAttribution), Some(aLicenseText), Some(aLicenseUri))
        resourceId        <- resourceId(createResourceResponseModel)
        getResponseModel  <- getResourceFromApi(resourceId)
        actualCopyright   <- copyrightValue(getResponseModel)
        actualLicenseText <- licenseTextValue(getResponseModel)
        actualLicenseUri  <- licenseUriValue(getResponseModel)
      } yield assertTrue(
        actualCopyright == aCopyrightAttribution.value,
        actualLicenseText == aLicenseText.value,
        actualLicenseUri == aLicenseUri.value,
      )
    },
    test(
      "when creating a resource with copyright attribution and license " +
        "the response when getting the created value should contain the license and copyright attribution",
    ) {
      for {
        createResourceResponseModel <-
          createStillImageResource(Some(aCopyrightAttribution), Some(aLicenseText), Some(aLicenseUri))
        valueResponseModel <- getValueFromApi(createResourceResponseModel)
        actualCopyright    <- copyrightValue(valueResponseModel)
        actualLicenseText  <- licenseTextValue(valueResponseModel)
        actualLicenseUri   <- licenseUriValue(valueResponseModel)
      } yield assertTrue(
        actualCopyright == aCopyrightAttribution.value,
        actualLicenseText == aLicenseText.value,
        actualLicenseUri == aLicenseUri.value,
      )
    },
  )

  val e2eSpec: Spec[Scope & env, Any] = suite("Copyright Attribution and Licenses")(
    givenProjectHasNoCopyrightAttributionAndLicenseSuite,
  )

  private def failResponse(msg: String)(response: Response) =
    response.body.asString.flatMap(bodyStr => ZIO.fail(Exception(s"$msg\nstatus: ${response.status}\nbody: $bodyStr")))

  private def createStillImageResource(
    copyrightAttribution: Option[CopyrightAttribution] = None,
    licenseText: Option[LicenseText] = None,
    licenseUri: Option[LicenseUri] = None,
  ): ZIO[env, Throwable, Model] = {
    val jsonLd = UploadFileRequest
      .make(
        FileType.StillImageFile(),
        "internalFilename.jpg",
        copyrightAttribution = copyrightAttribution,
        licenseText = licenseText,
        licenseUri = licenseUri,
      )
      .toJsonLd(className = Some("ThingPicture"), ontologyName = "anything")
    for {
      responseBody <- sendPostRequestAsRoot("/v2/resources", jsonLd)
                        .mapError(Exception(_))
                        .filterOrElseWith(_.status.isSuccess)(failResponse(s"Failed to create resource"))
                        .flatMap(_.body.asString)
      createResourceResponseModel <- ModelOps.fromJsonLd(responseBody).mapError(Exception(_))
    } yield createResourceResponseModel
  }

  private def getResourceFromApi(resourceId: String) = for {
    responseBody <- sendGetRequest(s"/v2/resources/${URLEncoder.encode(resourceId, "UTF-8")}")
                      .filterOrElseWith(_.status.isSuccess)(failResponse(s"Failed to get resource $resourceId."))
                      .flatMap(_.body.asString)
    model <- ModelOps.fromJsonLd(responseBody).mapError(Exception(_))
  } yield model

  private def getValueFromApi(createResourceResponse: Model) = for {
    valueId    <- valueId(createResourceResponse)
    resourceId <- resourceId(createResourceResponse)
    responseBody <- sendGetRequest(s"/v2/values/${URLEncoder.encode(resourceId, "UTF-8")}/${valueId.valueId}")
                      .filterOrElseWith(_.status.isSuccess)(failResponse(s"Failed to get value $resourceId."))
                      .flatMap(_.body.asString)
    model <- ModelOps.fromJsonLd(responseBody).mapError(Exception(_))
  } yield model

  private def resourceId(model: Model): Task[String] =
    ZIO
      .fromEither(
        for {
          root <- model.singleRootResource
          id   <- root.uri.toRight("No URI found for root resource")
        } yield id,
      )
      .mapError(Exception(_))

  private def valueId(model: Model): ZIO[IriConverter, Throwable, ValueIri] = {
    val subs = model
      .listSubjectsWithProperty(RDF.`type`)
      .asScala
      .filter(_.getProperty(RDF.`type`).getObject.asResource().hasURI(StillImageFileValue))
      .toList
    subs match
      case s :: Nil =>
        ZIO
          .fromEither(s.uri.toRight("No URI found for value"))
          .mapError(Exception(_))
          .flatMap(str => ZIO.serviceWithZIO[IriConverter](_.asSmartIri(str)))
          .flatMap(iri => ZIO.fromEither(ValueIri.from(iri)).mapError(Exception(_)))
      case Nil => ZIO.fail(Exception("No value found"))
      case _   => ZIO.fail(Exception("Multiple values found"))
  }

  private def copyrightValue(model: Model) =
    singleStringValueOption(model, HasCopyrightAttribution).someOrFail(new Exception("No copyright found"))
  private def copyrightValueOption(model: Model) =
    singleStringValueOption(model, HasCopyrightAttribution)
  private def licenseTextValue(model: Model) =
    singleStringValueOption(model, HasLicenseText).someOrFail(new Exception("No license text found"))
  private def licenseTextValueOption(model: Model) =
    singleStringValueOption(model, HasLicenseText)
  private def licenseUriValue(model: Model) =
    singleStringValueOption(model, HasLicenseUri).someOrFail(new Exception("No license uri found"))
  private def licenseUriValueOption(model: Model) =
    singleStringValueOption(model, HasLicenseUri)
  private def singleStringValueOption(model: Model, property: Property): Task[Option[String]] =
    ZIO
      .fromEither(
        model
          .singleSubjectWithPropertyOption(property)
          .flatMap(_.map(_.objectStringOption(property)).fold(Right(None))(identity)),
      )
      .mapError(Exception(_))
}
