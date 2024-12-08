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
import zio.http.Body
import zio.http.Response
import zio.test.*
import zio.test.TestAspect

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
import org.knora.webapi.slice.admin.domain.model.KnoraProject.CopyrightAttribution
import org.knora.webapi.slice.admin.domain.model.KnoraProject.LicenseText
import org.knora.webapi.slice.admin.domain.model.KnoraProject.LicenseUri
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
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

  private val projectCopyrightAttribution = CopyrightAttribution.unsafeFrom("2024, On Project")
  private val projectLicenseText          = LicenseText.unsafeFrom("Apache-2.0")
  private val projectLicenseUri           = LicenseUri.unsafeFrom("https://www.apache.org/licenses/LICENSE-2.0")

  private val givenProjectHasNoCopyrightAttributionAndLicenseSuite = suite(
    "given the project does not have a license and does not have a copyright attribution ",
  )(
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
    test(
      "when creating a resource without copyright attribution and license " +
        "and when providing the project with copyright attribution and license " +
        "and then updating the value" +
        "the response when getting the updated value should contain the license and copyright attribution of the project",
    ) {
      for {
        createResourceResponseModel <- createStillImageResource()
        _                           <- addCopyrightAttributionAndLicenseToProject()
        resourceId                  <- resourceId(createResourceResponseModel)
        valueId                     <- valueId(createResourceResponseModel)
        _                           <- updateValue(resourceId, valueId)
        valueGetResponse            <- getValueFromApi(createResourceResponseModel)
        actualCopyright             <- copyrightValue(valueGetResponse)
        actualLicenseText           <- licenseTextValue(valueGetResponse)
        actualLicenseUri            <- licenseUriValue(valueGetResponse)
      } yield assertTrue(
        actualCopyright == projectCopyrightAttribution.value,
        actualLicenseText == projectLicenseText.value,
        actualLicenseUri == projectLicenseUri.value,
      )
    },
  ) @@ TestAspect.before(removeCopyrightAttributionAndLicenseFromProject())

  private val givenProjectHasCopyrightAttributionAndLicenseSuite = suite(
    "given the project has a license and has a copyright attribution",
  )(
    test(
      "when creating a resource without copyright attribution and without license " +
        "then the response when getting the created value should contain the default license and default copyright attribution",
    ) {
      for {
        createResourceResponseModel <- createStillImageResource()
        valueResponseModel          <- getValueFromApi(createResourceResponseModel)
        actualCopyright             <- copyrightValue(valueResponseModel)
        actualLicenseText           <- licenseTextValue(valueResponseModel)
        actualLicenseUri            <- licenseUriValue(valueResponseModel)
      } yield assertTrue(
        actualCopyright == projectCopyrightAttribution.value,
        actualLicenseText == projectLicenseText.value,
        actualLicenseUri == projectLicenseUri.value,
      )
    },
    test(
      "when creating a resource without copyright attribution and without license " +
        "then the create response contain the license and copyright attribution from resource",
    ) {
      for {
        createResourceResponseModel <- createStillImageResource()
        actualCopyright             <- copyrightValue(createResourceResponseModel)
        actualLicenseText           <- licenseTextValue(createResourceResponseModel)
        actualLicenseUri            <- licenseUriValue(createResourceResponseModel)
      } yield assertTrue(
        actualCopyright == projectCopyrightAttribution.value,
        actualLicenseText == projectLicenseText.value,
        actualLicenseUri == projectLicenseUri.value,
      )
    },
    test(
      "when creating a resource with copyright attribution and license " +
        "then the create response contain the license and copyright attribution from resource",
    ) {
      for {
        createResourceResponseModel <-
          createStillImageResource(Some(aCopyrightAttribution), Some(aLicenseText), Some(aLicenseUri))
        actualCopyright   <- copyrightValue(createResourceResponseModel)
        actualLicenseText <- licenseTextValue(createResourceResponseModel)
        actualLicenseUri  <- licenseUriValue(createResourceResponseModel)
      } yield assertTrue(
        actualCopyright == aCopyrightAttribution.value,
        actualLicenseText == aLicenseText.value,
        actualLicenseUri == aLicenseUri.value,
      )
    },
    test(
      "when creating a resource with copyright attribution and without license " +
        "then the response when getting the created value should contain the license and copyright attribution from resource",
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
  ) @@ TestAspect.before(addCopyrightAttributionAndLicenseToProject())

  val e2eSpec: Spec[Scope & env, Any] = suite("Copyright Attribution and Licenses")(
    givenProjectHasNoCopyrightAttributionAndLicenseSuite,
    givenProjectHasCopyrightAttributionAndLicenseSuite,
  )

  private def removeCopyrightAttributionAndLicenseFromProject() =
    setCopyrightAttributionAndLicenseToProject(None, None, None)
  private def addCopyrightAttributionAndLicenseToProject() =
    setCopyrightAttributionAndLicenseToProject(
      Some(projectCopyrightAttribution),
      Some(projectLicenseText),
      Some(projectLicenseUri),
    )
  private def setCopyrightAttributionAndLicenseToProject(
    copyrightAttribution: Option[CopyrightAttribution],
    licenseText: Option[LicenseText],
    licenseUri: Option[LicenseUri],
  ) =
    for {
      projectService <- ZIO.service[KnoraProjectService]
      prj            <- projectService.findByShortcode(Shortcode.unsafeFrom("0001")).someOrFail(new Exception("Project not found"))
      change          = prj.copy(copyrightAttribution = copyrightAttribution, licenseText = licenseText, licenseUri = licenseUri)
      updated        <- projectService.save(change)
    } yield updated

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

  private def updateValue(resourceIri: String, valueId: ValueIri) = {
    val jsonLd =
      s"""
         |{
         |  "@id": "${resourceIri}",
         |  "@type": "anything:ThingPicture",
         |  "knora-api:hasStillImageFileValue": {
         |    "@id" : "${valueId.smartIri.toComplexSchema.toIri}",
         |    "@type": "knora-api:StillImageFileValue",
         |    "knora-api:fileValueHasFilename": "test.jpg"
         |  },
         |  "@context": {
         |    "knora-api": "http://api.knora.org/ontology/knora-api/v2#",
         |    "anything": "http://0.0.0.0:3333/ontology/0001/anything/v2#"
         |  }
         |}
         |""".stripMargin
    for {
      _ <- Console.printLine(jsonLd)
      _ <- ModelOps.fromJsonLd(jsonLd).mapError(Exception(_))
      responseBody <-
        sendPutRequestAsRoot("/v2/values", Body.fromString(jsonLd))
          .filterOrElseWith(_.status.isSuccess)(failResponse(s"Value update failed $valueId resource $resourceIri."))
          .flatMap(_.body.asString)
      model <- ModelOps.fromJsonLd(responseBody).mapError(Exception(_))
    } yield model
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
