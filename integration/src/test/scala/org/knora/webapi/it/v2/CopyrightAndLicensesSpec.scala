/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.it.v2

import org.apache.jena.rdf.model.Model
import org.apache.jena.rdf.model.Property
import org.apache.jena.rdf.model.Resource
import org.apache.jena.vocabulary.RDF
import zio.*
import zio.test.*

import java.net.URLEncoder
import scala.jdk.CollectionConverters.IteratorHasAsScala
import scala.language.implicitConversions

import org.knora.webapi.E2EZSpec
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.OntologyConstants.KnoraApiV2Complex.HasCopyrightAttribution
import org.knora.webapi.messages.OntologyConstants.KnoraApiV2Complex.HasLicense
import org.knora.webapi.messages.OntologyConstants.KnoraApiV2Complex.StillImageFileValue
import org.knora.webapi.models.filemodels.FileType
import org.knora.webapi.models.filemodels.UploadFileRequest
import org.knora.webapi.slice.admin.domain.model.KnoraProject.CopyrightAttribution
import org.knora.webapi.slice.admin.domain.model.KnoraProject.License
import org.knora.webapi.slice.common.KnoraIris.ValueIri
import org.knora.webapi.slice.common.jena.JenaConversions.given
import org.knora.webapi.slice.common.jena.ModelOps
import org.knora.webapi.slice.common.jena.ModelOps.*
import org.knora.webapi.slice.common.jena.ResourceOps.*
import org.knora.webapi.slice.resourceinfo.domain.IriConverter

object CopyrightAndLicensesSpec extends E2EZSpec {

  private val copyrightAttribution = CopyrightAttribution.unsafeFrom("2020, Example")
  private val license              = License.unsafeFrom("CC BY-SA 4.0")

  val e2eSpec: Spec[Scope & env, Any] = suite("Copyright Attribution and Licenses")(
    test(
      "when creating a resource with copyright attribution and license " +
        "the creation response should contain the license and copyright attribution",
    ) {
      for {
        createResourceResponseModel <- createImageWithCopyrightAndLicense
        actualCreatedCopyright      <- copyrightValue(createResourceResponseModel)
        actualCreatedLicense        <- licenseValue(createResourceResponseModel)
      } yield assertTrue(
        actualCreatedCopyright == copyrightAttribution.value,
        actualCreatedLicense == license.value,
      )
    },
    test(
      "when creating a resource with copyright attribution and license " +
        "the response when getting the created resource should contain the license and copyright attribution",
    ) {
      for {
        createResourceResponseModel <- createImageWithCopyrightAndLicense
        resourceId                  <- resourceId(createResourceResponseModel)
        getResponseModel            <- getResourceFromApi(resourceId)
        actualCopyright             <- copyrightValue(getResponseModel)
        actualLicense               <- licenseValue(getResponseModel)
      } yield assertTrue(
        actualCopyright == copyrightAttribution.value,
        actualLicense == license.value,
      )
    },
    test(
      "when creating a resource with copyright attribution and license " +
        "the response when getting the created value should contain the license and copyright attribution",
    ) {
      for {
        createResourceResponseModel <- createImageWithCopyrightAndLicense
        resourceId                  <- resourceId(createResourceResponseModel)
        valueId                     <- valueId(createResourceResponseModel)
        valueResponseModel          <- getValueFromApi(valueId, resourceId)
        actualCopyright             <- copyrightValue(valueResponseModel)
        actualLicense               <- licenseValue(valueResponseModel)
      } yield assertTrue(
        actualCopyright == copyrightAttribution.value,
        actualLicense == license.value,
      )
    },
  )

  private def createImageWithCopyrightAndLicense: ZIO[env, Throwable, Model] = {
    val jsonLd = UploadFileRequest
      .make(
        FileType.StillImageFile(),
        "internalFilename",
        copyrightAttribution = Some(copyrightAttribution),
        license = Some(license),
      )
      .toJsonLd(
        className = Some("ThingPicture"),
        ontologyName = "anything",
      )

    for {
      responseBody <- sendPostRequestAsRoot("/v2/resources", jsonLd)
                        .filterOrFail(_.status.isSuccess)(s"Failed to create resource")
                        .mapError(Exception(_))
                        .flatMap(_.body.asString)
      createResourceResponseModel <- ModelOps.fromJsonLd(responseBody).mapError(Exception(_))
    } yield createResourceResponseModel
  }

  private def getResourceFromApi(resourceId: String) = for {
    responseBody <- sendGetRequest(s"/v2/resources/${URLEncoder.encode(resourceId, "UTF-8")}")
                      .filterOrFail(_.status.isSuccess)(s"Failed to get resource $resourceId")
                      .flatMap(_.body.asString)
    model <- ModelOps.fromJsonLd(responseBody).mapError(Exception(_))
  } yield model

  private def getValueFromApi(valueIri: ValueIri, resourceIri: String) = for {
    responseBody <- sendGetRequest(s"/v2/values/${URLEncoder.encode(resourceIri, "UTF-8")}/${valueIri.valueId}")
                      .filterOrFail(_.status.isSuccess)(s"Failed to get resource $valueId")
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

  private def copyrightValue(model: Model) = singleStringValue(model, HasCopyrightAttribution)
  private def licenseValue(model: Model)   = singleStringValue(model, HasLicense)
  private def singleStringValue(model: Model, property: Property) =
    ZIO.fromEither(model.singleSubjectWithProperty(property).flatMap(_.objectString(property))).mapError(Exception(_))
}