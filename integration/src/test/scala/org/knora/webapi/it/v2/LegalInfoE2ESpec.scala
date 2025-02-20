/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.it.v2

import cats.syntax.traverse.*
import com.apicatalog.jsonld.JsonLd
import com.apicatalog.jsonld.document.JsonDocument
import org.apache.jena.rdf.model.Model
import org.apache.jena.rdf.model.Property
import org.apache.jena.rdf.model.Resource
import org.apache.jena.vocabulary.RDF
import zio.*
import zio.http.Body
import zio.http.Response
import zio.http.Status
import zio.json.ast.Json
import zio.test.*
import zio.test.Assertion.*

import java.io.ByteArrayInputStream
import java.net.URLEncoder
import scala.jdk.CollectionConverters.IteratorHasAsScala
import scala.language.implicitConversions

import org.knora.webapi.E2EZSpec
import org.knora.webapi.messages.IriConversions.ConvertibleIri
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.OntologyConstants.KnoraApiV2Complex as KA
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.models.filemodels.FileType
import org.knora.webapi.models.filemodels.UploadFileRequest
import org.knora.webapi.slice.admin.api.model.PageAndSize
import org.knora.webapi.slice.admin.api.model.PagedResponse
import org.knora.webapi.slice.admin.domain.model.*
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.admin.domain.service.KnoraProjectService
import org.knora.webapi.slice.common.KnoraIris.ResourceClassIri
import org.knora.webapi.slice.common.KnoraIris.ResourceIri
import org.knora.webapi.slice.common.KnoraIris.ValueIri
import org.knora.webapi.slice.common.jena.JenaConversions.given
import org.knora.webapi.slice.common.jena.ModelOps
import org.knora.webapi.slice.common.jena.ModelOps.*
import org.knora.webapi.slice.common.jena.ResourceOps.*
import org.knora.webapi.slice.resourceinfo.domain.IriConverter

object LegalInfoE2ESpec extends E2EZSpec {

  private implicit val sf: StringFormatter = StringFormatter.getInitializedTestInstance

  private val projectService = ZIO.serviceWithZIO[KnoraProjectService]

  private val aCopyrightHolder  = CopyrightHolder.unsafeFrom("Universität Basel")
  private val someAuthorship    = List("Hans Müller", "Gigi DAgostino").map(Authorship.unsafeFrom)
  private val anotherAuthorship = List("Lotte Reiniger").map(Authorship.unsafeFrom)
  private val aLicenseIri       = LicenseIri.PUBLIC_DOMAIN
  private val shortcode         = Shortcode.unsafeFrom("0001")

  private val allowCopyrightHolder = for {
    prj     <- projectService(_.findByShortcode(shortcode)).someOrFail(Exception("Project not found"))
    updated <- projectService(_.addCopyrightHolders(prj.id, Set(aCopyrightHolder)))
  } yield updated

  private val disallowCopyrightHolder = for {
    prj <- projectService(_.findByShortcode(shortcode)).someOrFail(Exception("Project not found"))
    _   <- projectService(_.removeAllCopyrightHolder(prj.id))
  } yield ()

  private val createResourceSuite = suite("Creating Resources")(
    test("without legal info should succeed and the creation response should not contain legal info") {
      for {
        createResourceResponseModel <- createStillImageResource()
        info                        <- copyrightAndLicenseInfo(createResourceResponseModel)
      } yield assertTrue(
        info.copyrightHolder.isEmpty,
        info.authorship.isEmpty,
        info.licenseIri.isEmpty,
      )
    },
    suite("when creating a resource with legal info")(
      suite("given the copyright holder is allowed on the project")(
        test("then creation response should contain it") {
          for {
            createResourceResponseModel <- createStillImageResourceWithInfos()
            info                        <- copyrightAndLicenseInfo(createResourceResponseModel)
          } yield assertTrue(
            info.copyrightHolder.contains(aCopyrightHolder),
            info.licenseIri.contains(aLicenseIri),
          ) && assert(info.authorship.getOrElse(List.empty))(hasSameElements(someAuthorship))
        },
        test("when getting the created resource the response should contain it") {
          for {
            createResourceResponseModel <- createStillImageResourceWithInfos()
            resourceId                  <- resourceId(createResourceResponseModel)
            getResponseModel            <- getResourceFromApi(resourceId)
            info                        <- copyrightAndLicenseInfo(getResponseModel)
          } yield assertTrue(
            info.copyrightHolder.contains(aCopyrightHolder),
            info.licenseIri.contains(aLicenseIri),
          ) && assert(info.authorship.getOrElse(List.empty))(hasSameElements(someAuthorship))
        },
        test("when getting the created value the response should contain it") {
          for {
            createResourceResponseModel <- createStillImageResourceWithInfos()
            valueResponseModel          <- getValueFromApi(createResourceResponseModel)
            info                        <- copyrightAndLicenseInfo(valueResponseModel)
          } yield assertTrue(
            info.copyrightHolder.contains(aCopyrightHolder),
            info.licenseIri.contains(aLicenseIri),
          ) && assert(info.authorship.getOrElse(List.empty))(hasSameElements(someAuthorship))
        },
        test(
          "and creating another resource with different authorship, " +
            "when getting the authorships from the admin api it should contain all authorships",
        ) {
          val expected = (someAuthorship ++ anotherAuthorship).sortBy(_.value)
          for {
            _ <- createStillImageResourceWithInfos(authorship = Some(someAuthorship))
            _ <- createStillImageResourceWithInfos(authorship = Some(anotherAuthorship))
            authorships <- sendGetRequestAsRootDecode[PagedResponse[Authorship]](
                             s"/admin/projects/shortcode/$shortcode/legal-info/authorships",
                           )
          } yield assertTrue(authorships == PagedResponse.from(expected, expected.size, PageAndSize.Default))
        },
      ),
    ) @@ TestAspect.before(allowCopyrightHolder),
    suite("given the copyright holder is NOT allowed on the project")(
      test("creating with a copyright holder should fail") {
        for {
          response <- postCreateResource(Some(aCopyrightHolder))
        } yield assertTrue(response.status == Status.BadRequest)
      },
      test("creating with an invalid LicenseIri should fail") {
        for {
          response <- postCreateResource(licenseIri = Some(LicenseIri.makeNew))
        } yield assertTrue(response.status == Status.BadRequest)
      },
    ) @@ TestAspect.before(disallowCopyrightHolder),
  )

  private val createValueSuite = suite("Values with legal info")(
    suite("given the copyright holder is allowed")(
      test("when updating a value with valid legal info the created value should contain the update") {
        val newLicenseIri = LicenseIri.AI_GENERATED
        for {
          resourceCreated <- createStillImageResource()
          resourceId      <- resourceId(resourceCreated)
          valueIdOld      <- valueId(resourceCreated)
          _ <- putUpdateValue(resourceId, valueIdOld, aCopyrightHolder, someAuthorship, newLicenseIri)
                 .filterOrElseWith(_.status.isSuccess)(failResponse(s"Failed to create value"))
          createdResourceModel <- getResourceFromApi(resourceId)
          info                 <- copyrightAndLicenseInfo(createdResourceModel)
        } yield assertTrue(
          info.licenseIri.contains(newLicenseIri),
        ) && assert(info.authorship.getOrElse(List.empty))(hasSameElements(someAuthorship))
      },
      test("when updating a value with invalid LicenseIri the update should fail") {
        val invalidLicenseIri = LicenseIri.makeNew
        for {
          resourceCreated <- createStillImageResource()
          resourceId      <- resourceId(resourceCreated)
          valueIdOld      <- valueId(resourceCreated)
          response        <- putUpdateValue(resourceId, valueIdOld, aCopyrightHolder, someAuthorship, invalidLicenseIri)
        } yield assertTrue(response.status.isClientError)
      },
    ) @@ TestAspect.before(allowCopyrightHolder),
    suite("given the copyright holder is NOT allowed")(
      test("when updating a value with valid legal info the created value should contain the update") {
        val disallowedCopyrightHolder = CopyrightHolder.unsafeFrom("disallowed-copyright-holder")
        for {
          resourceCreated <- createStillImageResource()
          resourceId      <- resourceId(resourceCreated)
          valueIdOld      <- valueId(resourceCreated)
          response        <- putUpdateValue(resourceId, valueIdOld, disallowedCopyrightHolder, someAuthorship, aLicenseIri)
        } yield assertTrue(response.status.isClientError)
      },
    ) @@ TestAspect.before(disallowCopyrightHolder),
  )

  final case class UpdateStillImageFileValueRequest(
    resourceId: ResourceIri,
    valueId: ValueIri,
    resourceClass: ResourceClassIri,
    copyrightHolder: CopyrightHolder,
    authorship: List[Authorship],
    licenseIri: LicenseIri,
  ) {
    def jsonLd: String =
      JsonLd.expand(JsonDocument.of(new ByteArrayInputStream(toJson.toString().getBytes))).get.toString

    private def toJson: Json = {
      def ldType(typ: Any) = ("@type", Json.Str(typ.toString))
      def ldId(id: Any)    = ("@id", Json.Str(id.toString))
      Json.Obj(
        ldId(resourceId),
        ldType(resourceClass),
        (
          KA.HasStillImageFileValue,
          Json.Obj(
            ldId(valueId),
            ldType(KA.StillImageFileValue),
            (KA.FileValueHasFilename, Json.Str("test.jpx")),
            (KA.HasCopyrightHolder, Json.Str(copyrightHolder.value)),
            (KA.HasAuthorship, Json.Arr(authorship.map(_.value).map(Json.Str.apply): _*)),
            (KA.HasLicense, Json.Obj(ldId(licenseIri.value))),
          ),
        ),
      )
    }
  }

  val e2eSpec: Spec[Scope & env, Any] =
    suite("Copyright Attribution and Licenses")(createResourceSuite, createValueSuite)

  private def failResponse(msg: String)(response: Response) =
    response.body.asString.flatMap(bodyStr => ZIO.fail(Exception(s"$msg\nstatus: ${response.status}\nbody: $bodyStr")))

  private def createStillImageResourceWithInfos(
    copyrightHolder: Option[CopyrightHolder] = Some(aCopyrightHolder),
    authorship: Option[List[Authorship]] = Some(someAuthorship),
    licenseIri: Option[LicenseIri] = Some(aLicenseIri),
  ) = createStillImageResource(copyrightHolder, authorship, licenseIri)

  private def createStillImageResource(
    copyrightHolder: Option[CopyrightHolder] = None,
    authorship: Option[List[Authorship]] = None,
    licenseIri: Option[LicenseIri] = None,
  ): ZIO[env, Throwable, Model] =
    for {
      responseBody <- postCreateResource(copyrightHolder, authorship, licenseIri)
                        .filterOrElseWith(_.status.isSuccess)(failResponse(s"Failed to create resource"))
                        .flatMap(_.body.asString)
      createResourceResponseModel <- ModelOps.fromJsonLd(responseBody).mapError(Exception(_))
    } yield createResourceResponseModel

  private def postCreateResource(
    copyrightHolder: Option[CopyrightHolder] = None,
    authorship: Option[List[Authorship]] = None,
    licenseIri: Option[LicenseIri] = None,
  ): URIO[env, Response] = {
    val jsonLd = UploadFileRequest
      .make(
        FileType.StillImageFile(),
        "internalFilename.jpg",
        copyrightHolder = copyrightHolder,
        authorship = authorship,
        licenseIri = licenseIri,
      )
      .toJsonLd(shortcode = shortcode, className = Some("ThingPicture"), ontologyName = "anything")
    sendPostRequestAsRoot("/v2/resources", jsonLd).mapError(Exception(_)).orDie
  }

  private def putUpdateValue(
    resourceIri: ResourceIri,
    valueIdOld: ValueIri,
    copyrightHolder: CopyrightHolder = aCopyrightHolder,
    authorship: List[Authorship] = someAuthorship,
    licenseIri: LicenseIri = aLicenseIri,
  ): ZIO[env, String, Response] = {
    val request = UpdateStillImageFileValueRequest(
      resourceIri,
      valueIdOld,
      ResourceClassIri.unsafeFrom(
        "http://0.0.0.0:3333/ontology/0001/anything/v2#ThingPicture".toSmartIri,
      ),
      copyrightHolder,
      authorship,
      licenseIri,
    )
    sendPutRequestAsRoot(s"/v2/values", request.jsonLd)
  }

  private def getResourceFromApi(resourceId: ResourceIri) = for {
    responseBody <- sendGetRequest(s"/v2/resources/${URLEncoder.encode(resourceId.toString, "UTF-8")}")
                      .filterOrElseWith(_.status.isSuccess)(failResponse(s"Failed to get resource $resourceId."))
                      .flatMap(_.body.asString)
    model <- ModelOps.fromJsonLd(responseBody).mapError(Exception(_))
  } yield model

  private def getValueFromApi(createResourceResponse: Model): ZIO[env, Throwable, Model] = for {
    valueId    <- valueId(createResourceResponse)
    resourceId <- resourceId(createResourceResponse)
    model      <- getValueFromApi(valueId, resourceId)
  } yield model

  private def getValueFromApi(valueId: ValueIri, resourceId: ResourceIri): ZIO[env, Throwable, Model] = for {
    responseBody <- sendGetRequest(s"/v2/values/${URLEncoder.encode(resourceId.toString, "UTF-8")}/${valueId.valueId}")
                      .filterOrElseWith(_.status.isSuccess)(failResponse(s"Failed to get value $resourceId."))
                      .flatMap(_.body.asString)
    model <- ModelOps.fromJsonLd(responseBody).mapError(Exception(_))
  } yield model

  private def resourceId(model: Model): Task[ResourceIri] =
    ZIO
      .fromEither(
        for {
          root <- model.singleRootResource
          id   <- root.uri.toRight("No URI found for root resource")
        } yield id,
      )
      .map(_.toSmartIri)
      .mapBoth(Exception(_), ResourceIri.unsafeFrom)

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
    licenseIri: Option[LicenseIri],
  )

  private def copyrightAndLicenseInfo(model: Model) =
    for {
      copyright  <- copyrightValueOption(model).map(_.map(CopyrightHolder.unsafeFrom))
      authorship <- authorshipValuesOption(model).map(_.map(_.map(Authorship.unsafeFrom)))
      licenseIri <- licenseIriValueOption(model).map(_.map(LicenseIri.unsafeFrom))
    } yield CopyrightAndLicenseInfo(copyright, authorship, licenseIri)

  private def copyrightValueOption(model: Model) =
    singleStringValueOption(model, KA.HasCopyrightHolder)

  private def singleStringValueOption(model: Model, property: Property): Task[Option[String]] =
    ZIO
      .fromEither(
        model
          .singleSubjectWithPropertyOption(property)
          .flatMap(_.map(_.objectStringOption(property)).fold(Right(None))(identity)),
      )
      .mapError(Exception(_))

  private def authorshipValuesOption(model: Model): Task[Option[List[String]]] =
    ZIO
      .fromEither(
        model
          .singleSubjectWithPropertyOption(KA.HasAuthorship)
          .flatMap(_.traverse(_.objectStringList(KA.HasAuthorship))),
      )
      .mapError(Exception(_))

  private def licenseIriValueOption(model: Model) =
    singleObjectUriOption(model, KA.HasLicense)

  private def singleObjectUriOption(model: Model, property: Property): Task[Option[String]] =
    ZIO
      .fromEither(
        model
          .singleSubjectWithPropertyOption(property)
          .flatMap(_.map(_.objectUriOption(property)).fold(Right(None))(identity)),
      )
      .mapError(Exception(_))
}
