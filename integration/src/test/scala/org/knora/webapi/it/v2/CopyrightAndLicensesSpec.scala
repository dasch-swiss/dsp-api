/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.it.v2

import cats.syntax.traverse.*
import org.apache.jena.rdf.model.Model
import org.apache.jena.rdf.model.Property
import org.apache.jena.rdf.model.Resource
import org.apache.jena.vocabulary.RDF
import zio.*
import zio.http.Body
import zio.http.Response
import zio.test.*
import zio.test.Assertion.*

import java.net.URLEncoder
import scala.jdk.CollectionConverters.IteratorHasAsScala
import scala.language.implicitConversions

import org.knora.webapi.E2EZSpec
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.OntologyConstants.KnoraApiV2Complex as KA
import org.knora.webapi.models.filemodels.FileType
import org.knora.webapi.models.filemodels.UploadFileRequest
import org.knora.webapi.slice.admin.domain.model.*
import org.knora.webapi.slice.admin.domain.service.KnoraProjectService
import org.knora.webapi.slice.common.KnoraIris.ValueIri
import org.knora.webapi.slice.common.jena.JenaConversions.given
import org.knora.webapi.slice.common.jena.ModelOps
import org.knora.webapi.slice.common.jena.ModelOps.*
import org.knora.webapi.slice.common.jena.ResourceOps.*
import org.knora.webapi.slice.resourceinfo.domain.IriConverter

object CopyrightAndLicensesSpec extends E2EZSpec {

  private val aCopyrightHolder = CopyrightHolder.unsafeFrom("Universität Basel")
  private val someAuthorship   = List("Hans Müller", "Gigi DAgostino").map(Authorship.unsafeFrom)
  private val aLicenseText     = LicenseText.unsafeFrom("CC BY-SA 4.0")
  private val aLicenseUri      = LicenseUri.unsafeFrom("https://creativecommons.org/licenses/by-sa/4.0/")

  private val createResourceSuite = suite("Creating Resources")(
    test(
      "when creating a resource without copyright and license information" +
        "the creation response should not contain it",
    ) {
      for {
        createResourceResponseModel <- createStillImageResource()
        info                        <- copyrightAndLicenseInfo(createResourceResponseModel)
      } yield assertTrue(
        info.copyrightHolder.isEmpty,
        info.authorship.isEmpty,
        info.licenseText.isEmpty,
        info.licenseUri.isEmpty,
        info.licenseDate.isDefined,
      )
    },
    test(
      "when creating a resource with copyright and license information" +
        "the creation response should not contain it",
    ) {
      for {
        createResourceResponseModel <- createStillImageResourceWithInfos
        info                        <- copyrightAndLicenseInfo(createResourceResponseModel)
      } yield assertTrue(
        info.copyrightHolder.contains(aCopyrightHolder),
        info.licenseText.contains(aLicenseText),
        info.licenseUri.contains(aLicenseUri),
        info.licenseDate.isDefined,
      ) && assert(info.authorship.getOrElse(List.empty))(hasSameElements(someAuthorship))
    },
    test(
      "when creating a resource with copyright and license information " +
        "the response when getting the created resource should contain it",
    ) {
      for {
        createResourceResponseModel <- createStillImageResourceWithInfos
        resourceId                  <- resourceId(createResourceResponseModel)
        getResponseModel            <- getResourceFromApi(resourceId)
        info                        <- copyrightAndLicenseInfo(getResponseModel)
      } yield assertTrue(
        info.copyrightHolder.contains(aCopyrightHolder),
        info.licenseText.contains(aLicenseText),
        info.licenseUri.contains(aLicenseUri),
        info.licenseDate.isDefined,
      ) && assert(info.authorship.getOrElse(List.empty))(hasSameElements(someAuthorship))
    },
    test(
      "when creating a resource with copyright and license information " +
        "the response when getting the created value should contain it",
    ) {
      for {
        createResourceResponseModel <- createStillImageResourceWithInfos
        valueResponseModel          <- getValueFromApi(createResourceResponseModel)
        info                        <- copyrightAndLicenseInfo(valueResponseModel)
      } yield assertTrue(
        info.copyrightHolder.contains(aCopyrightHolder),
        info.licenseText.contains(aLicenseText),
        info.licenseUri.contains(aLicenseUri),
        info.licenseDate.isDefined,
      ) && assert(info.authorship.getOrElse(List.empty))(hasSameElements(someAuthorship))
    },
  )

  private val createValueSuite = suite("Values with Copyright and License Information") {
    test("when updating a value with copyright and license information, the created value should contain it") {
      for {
        resourceCreated <- createStillImageResource()
        resourceId      <- resourceId(resourceCreated)
        valueIdOld      <- valueId(resourceCreated)
        authorshipArray  = someAuthorship.map(_.value).mkString("[ \"", "\", \"", "\" ]")
        body = s"""|{
                   |  "@type": "anything:ThingPicture",
                   |  "@id": "$resourceId",
                   |  "ka:hasStillImageFileValue": {
                   |    "@type": "ka:StillImageFileValue",
                   |    "@id": "$valueIdOld",
                   |    "ka:fileValueHasFilename": "filename.jpx",
                   |    "ka:hasCopyrightHolder" : "${aCopyrightHolder.value}",
                   |    "ka:hasAuthorship" : $authorshipArray,
                   |    "ka:hasLicenseText" : "${aLicenseText.value}",
                   |    "ka:hasLicenseUri" : {
                   |      "@value" : "${aLicenseUri.value}",
                   |      "@type" : "http://www.w3.org/2001/XMLSchema#anyURI"
                   |    }
                   |  },
                   |  "@context": {
                   |    "ka": "http://api.knora.org/ontology/knora-api/v2#",
                   |    "anything": "http://0.0.0.0:3333/ontology/0001/anything/v2#"
                   |  }
                   |}""".stripMargin
        _ <- sendPutRequestAsRoot(s"/v2/values", body)
               .filterOrElseWith(_.status.isSuccess)(failResponse(s"Failed to create value"))
               .flatMap(_.body.asString)
        createdResourceModel <- getResourceFromApi(resourceId)
        info                 <- copyrightAndLicenseInfo(createdResourceModel)
      } yield assertTrue(
        info.licenseText.contains(aLicenseText),
        info.licenseUri.contains(aLicenseUri),
        info.licenseDate.isDefined,
      ) && assert(info.authorship.getOrElse(List.empty))(hasSameElements(someAuthorship))
    }
  }

  val e2eSpec: Spec[Scope & env, Any] =
    suite("Copyright Attribution and Licenses")(createResourceSuite, createValueSuite)

  private def failResponse(msg: String)(response: Response) =
    response.body.asString.flatMap(bodyStr => ZIO.fail(Exception(s"$msg\nstatus: ${response.status}\nbody: $bodyStr")))

  private def createStillImageResourceWithInfos =
    createStillImageResource(
      Some(aCopyrightHolder),
      Some(someAuthorship),
      Some(aLicenseText),
      Some(aLicenseUri),
    )

  private def createStillImageResource(
    copyrightHolder: Option[CopyrightHolder] = None,
    authorship: Option[List[Authorship]] = None,
    licenseText: Option[LicenseText] = None,
    licenseUri: Option[LicenseUri] = None,
  ): ZIO[env, Throwable, Model] = {
    val jsonLd = UploadFileRequest
      .make(
        FileType.StillImageFile(),
        "internalFilename.jpg",
        copyrightHolder = copyrightHolder,
        authorship = authorship,
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

  private def getValueFromApi(createResourceResponse: Model): ZIO[env, Throwable, Model] = for {
    valueId    <- valueId(createResourceResponse)
    resourceId <- resourceId(createResourceResponse)
    model      <- getValueFromApi(valueId, resourceId)
  } yield model

  private def getValueFromApi(valueId: ValueIri, resourceId: String): ZIO[env, Throwable, Model] = for {
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
      .filter(_.getProperty(RDF.`type`).getObject.asResource().hasURI(KA.StillImageFileValue))
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

  final case class CopyrightAndLicenseInfo(
    copyrightHolder: Option[CopyrightHolder],
    authorship: Option[List[Authorship]],
    licenseText: Option[LicenseText],
    licenseUri: Option[LicenseUri],
    licenseDate: Option[LicenseDate],
  )

  private def copyrightAndLicenseInfo(model: Model) =
    for {
      copyright   <- copyrightValueOption(model).map(_.map(CopyrightHolder.unsafeFrom))
      authorship  <- authorshipValuesOption(model).map(_.map(_.map(Authorship.unsafeFrom)))
      licenseText <- licenseTextValueOption(model).map(_.map(LicenseText.unsafeFrom))
      licenseUri  <- licenseUriValueOption(model).map(_.map(LicenseUri.unsafeFrom))
      licenseDate <- licenseDateValueOption(model).map(_.map(LicenseDate.unsafeFrom))
    } yield CopyrightAndLicenseInfo(copyright, authorship, licenseText, licenseUri, licenseDate)

  private def copyrightValueOption(model: Model) =
    singleStringValueOption(model, KA.HasCopyrightHolder)

  private def authorshipValuesOption(model: Model): Task[Option[List[String]]] =
    ZIO
      .fromEither(
        model
          .singleSubjectWithPropertyOption(KA.HasAuthorship)
          .flatMap(_.traverse(_.objectStringList(KA.HasAuthorship))),
      )
      .mapError(Exception(_))

  private def licenseTextValueOption(model: Model) =
    singleStringValueOption(model, KA.HasLicenseText)

  private def licenseUriValueOption(model: Model) =
    singleStringValueOption(model, KA.HasLicenseUri)

  private def licenseDateValueOption(model: Model) =
    singleStringValueOption(model, KA.HasLicenseDate)

  private def singleStringValueOption(model: Model, property: Property): Task[Option[String]] =
    ZIO
      .fromEither(
        model
          .singleSubjectWithPropertyOption(property)
          .flatMap(_.map(_.objectStringOption(property)).fold(Right(None))(identity)),
      )
      .mapError(Exception(_))
}
