/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.it.v2

import cats.syntax.traverse.*
import com.apicatalog.jsonld.JsonLd
import com.apicatalog.jsonld.document.JsonDocument
import org.apache.jena.rdf.model.Model
import org.apache.jena.rdf.model.Property
import org.apache.jena.vocabulary.RDF
import sttp.client4.*
import sttp.model.*
import zio.*
import zio.json.ast.Json
import zio.test.*
import zio.test.Assertion.*

import java.io.ByteArrayInputStream
import scala.jdk.CollectionConverters.IteratorHasAsScala
import scala.language.implicitConversions

import org.knora.webapi.E2EZSpec
import org.knora.webapi.it.v2.LegalInfoE2ESpec.suite
import org.knora.webapi.messages.IriConversions.ConvertibleIri
import org.knora.webapi.messages.OntologyConstants.KnoraApiV2Complex as KA
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.sharedtestdata.SharedTestDataADM.*
import org.knora.webapi.slice.admin.domain.model.*
import org.knora.webapi.slice.admin.domain.service.KnoraProjectService
import org.knora.webapi.slice.api.PageAndSize
import org.knora.webapi.slice.api.PagedResponse
import org.knora.webapi.slice.common.KnoraIris.ResourceClassIri
import org.knora.webapi.slice.common.KnoraIris.ResourceIri
import org.knora.webapi.slice.common.KnoraIris.ValueIri
import org.knora.webapi.slice.common.jena.JenaConversions.given
import org.knora.webapi.slice.common.jena.ModelOps
import org.knora.webapi.slice.common.jena.ModelOps.*
import org.knora.webapi.slice.common.jena.ResourceOps.*
import org.knora.webapi.slice.common.service.IriConverter
import org.knora.webapi.testservices.ResponseOps.assert200
import org.knora.webapi.testservices.ResponseOps.assert400
import org.knora.webapi.testservices.TestApiClient
import org.knora.webapi.testservices.TestDspIngestClient
import org.knora.webapi.testservices.TestDspIngestClient.UploadedFile
import org.knora.webapi.testservices.TestResourcesApiClient

object LegalInfoE2ESpec extends E2EZSpec {

  private implicit val sf: StringFormatter = StringFormatter.getInitializedTestInstance

  override def rdfDataObjects: List[RdfDataObject] = List(anythingRdfData)

  private val projectService = ZIO.serviceWithZIO[KnoraProjectService]

  private val aCopyrightHolder         = CopyrightHolder.unsafeFrom("Universität Basel")
  private val someAuthorship           = List("Hans Müller", "Gigi DAgostino").map(Authorship.unsafeFrom)
  private val anotherAuthorship        = List("Lotte Reiniger").map(Authorship.unsafeFrom)
  private val enabledLicenseIri        = LicenseIri.PUBLIC_DOMAIN
  private val anotherEnabledLicenseIri = LicenseIri.CC_BY_4_0

  private val allowCopyrightHolder = for {
    prj     <- projectService(_.findByShortcode(anythingShortcode)).someOrFail(Exception("Project not found"))
    updated <- projectService(_.addCopyrightHolders(prj.id, Set(aCopyrightHolder)))
  } yield updated

  private val disallowCopyrightHolder = for {
    prj <- projectService(_.findByShortcode(anythingShortcode)).someOrFail(Exception("Project not found"))
    _   <- projectService(_.removeAllCopyrightHolder(prj.id))
  } yield ()

  private val enableLicenses = {
    val licensesToEnable = Set(enabledLicenseIri, anotherEnabledLicenseIri)
    for {
      prj <- projectService(_.findByShortcode(anythingShortcode)).someOrFail(Exception("Project not found"))
      _   <- ZIO.foreachDiscard(licensesToEnable)(iri => projectService(_.enableLicense(iri, prj)))
    } yield ()
  }

  private val disableAllLicenses =
    for {
      prj <- projectService(_.findByShortcode(anythingShortcode)).someOrFail(Exception("Project not found"))
      _   <- ZIO.foreachDiscard(prj.enabledLicenses)(iri => projectService(_.disableLicense(iri, prj)))
    } yield ()

  private val createResourceSuite = suite("Creating Resources")(
    test("without legal info should succeed and the creation response should not contain legal info") {
      for {
        created             <- createStillImageResource()
        (resourceCreated, _) = created
        info                <- copyrightAndLicenseInfo(resourceCreated)
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
            created             <- createStillImageResourceWithInfos()
            (resourceCreated, _) = created
            info                <- copyrightAndLicenseInfo(resourceCreated)
          } yield assertTrue(
            info.copyrightHolder.contains(aCopyrightHolder),
            info.licenseIri.contains(enabledLicenseIri),
          ) && assert(info.authorship.getOrElse(List.empty))(hasSameElements(someAuthorship))
        },
        test("when getting the created resource the response should contain it") {
          for {
            created             <- createStillImageResourceWithInfos()
            (resourceCreated, _) = created
            resourceId          <- resourceId(resourceCreated)
            getResponseModel    <- getResourceFromApi(resourceId)
            info                <- copyrightAndLicenseInfo(getResponseModel)
          } yield assertTrue(
            info.copyrightHolder.contains(aCopyrightHolder),
            info.licenseIri.contains(enabledLicenseIri),
          ) && assert(info.authorship.getOrElse(List.empty))(hasSameElements(someAuthorship))
        },
        test("when getting the created value the response should contain it") {
          for {
            created             <- createStillImageResourceWithInfos()
            (resourceCreated, _) = created
            valueResponseModel  <- getValueFromApi(resourceCreated)
            info                <- copyrightAndLicenseInfo(valueResponseModel)
          } yield assertTrue(
            info.copyrightHolder.contains(aCopyrightHolder),
            info.licenseIri.contains(enabledLicenseIri),
          ) && assert(info.authorship.getOrElse(List.empty))(hasSameElements(someAuthorship))
        },
        test(
          "and creating another resource with different authorship, " +
            "when getting the authorships from the admin api it should contain all authorships",
        ) {
          val expected = (someAuthorship ++ anotherAuthorship).sortBy(_.value)
          for {
            _           <- createStillImageResourceWithInfos(authorship = Some(someAuthorship))
            _           <- createStillImageResourceWithInfos(authorship = Some(anotherAuthorship))
            authorships <- TestApiClient
                             .getJson[PagedResponse[Authorship]](
                               uri"/admin/projects/shortcode/$anythingShortcode/legal-info/authorships",
                               rootUser,
                             )
                             .flatMap(_.assert200)
          } yield assertTrue(authorships == PagedResponse.from(expected, expected.size, PageAndSize.Default))
        },
      ),
    ) @@ TestAspect.before(allowCopyrightHolder),
    suite("given the copyright holder is NOT allowed on the project")(
      test("creating with a copyright holder should fail") {
        for {
          asset    <- TestDspIngestClient.createImageAsset(anythingShortcode)
          response <- postCreateResource(asset, copyrightHolder = Some(aCopyrightHolder))
        } yield assertTrue(response.code == StatusCode.BadRequest)
      },
      test("creating with a not enabled LicenseIri should fail") {
        for {
          asset    <- TestDspIngestClient.createImageAsset(anythingShortcode)
          response <- postCreateResource(asset, licenseIri = Some(LicenseIri.AI_GENERATED))
        } yield assertTrue(response.code == StatusCode.BadRequest)
      },
    ) @@ TestAspect.before(disallowCopyrightHolder),
  )

  private val createValueSuite = suite("Values with legal info")(
    suite("given the license is not enabled")(
      test("when creating a value with legal info the creation should fail") {
        for {
          created                 <- createStillImageResource()
          (resourceCreated, asset) = created
          resourceId              <- resourceId(resourceCreated)
          valueIdOld              <- valueId(resourceCreated)
          response                <- putUpdateValue(asset, resourceId, valueIdOld, aCopyrightHolder, someAuthorship, enabledLicenseIri)
          errorBody               <- response.assert400
        } yield assertTrue(
          errorBody.contains("License http://rdfh.ch/licenses/public-domain is not allowed in project 0001"),
        )
      },
    ) @@ TestAspect.before(disableAllLicenses) @@ TestAspect.before(allowCopyrightHolder),
    suite("given the license is enabled")(
      suite("given the copyright holder is allowed")(
        test("when updating a value with valid legal info the created value should contain the update") {
          val newLicenseIri = LicenseIri.CC_BY_4_0
          for {
            created                 <- createStillImageResource()
            (resourceCreated, asset) = created
            resourceId              <- resourceId(resourceCreated)
            valueIdOld              <- valueId(resourceCreated)
            _                       <- putUpdateValue(asset, resourceId, valueIdOld, aCopyrightHolder, someAuthorship, newLicenseIri)
                   .flatMap(_.assert200)
            createdResourceModel <- getResourceFromApi(resourceId)
            info                 <- copyrightAndLicenseInfo(createdResourceModel)
          } yield assertTrue(
            info.licenseIri.contains(newLicenseIri),
          ) && assert(info.authorship.getOrElse(List.empty))(hasSameElements(someAuthorship))
        },
        test("when updating a value with invalid LicenseIri the update should fail") {
          val invalidLicenseIri = LicenseIri.makeNew
          for {
            created                 <- createStillImageResource()
            (resourceCreated, asset) = created
            resourceId              <- resourceId(resourceCreated)
            valueIdOld              <- valueId(resourceCreated)
            response                <-
              putUpdateValue(asset, resourceId, valueIdOld, aCopyrightHolder, someAuthorship, invalidLicenseIri)
          } yield assertTrue(response.code == StatusCode.BadRequest)
        },
      ) @@ TestAspect.before(allowCopyrightHolder),
      suite("given the copyright holder is NOT allowed")(
        test("when updating a value with valid legal info the created value should contain the update") {
          val disallowedCopyrightHolder = CopyrightHolder.unsafeFrom("disallowed-copyright-holder")
          for {
            created                 <- createStillImageResource()
            (resourceCreated, asset) = created
            resourceId              <- resourceId(resourceCreated)
            valueIdOld              <- valueId(resourceCreated)
            response                <- putUpdateValue(
                          asset,
                          resourceId,
                          valueIdOld,
                          disallowedCopyrightHolder,
                          someAuthorship,
                          enabledLicenseIri,
                        )
          } yield assertTrue(response.code == StatusCode.BadRequest)
        },
      ) @@ TestAspect.before(disallowCopyrightHolder),
    ) @@ TestAspect.before(enableLicenses),
  )

  final case class UpdateStillImageFileValueRequest(
    resourceId: ResourceIri,
    valueId: ValueIri,
    resourceClass: ResourceClassIri,
    copyrightHolder: CopyrightHolder,
    authorship: List[Authorship],
    licenseIri: LicenseIri,
    filename: String,
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
            (KA.FileValueHasFilename, Json.Str(filename)),
            (KA.HasCopyrightHolder, Json.Str(copyrightHolder.value)),
            (KA.HasAuthorship, Json.Arr(authorship.map(_.value).map(Json.Str.apply): _*)),
            (KA.HasLicense, Json.Obj(ldId(licenseIri.value))),
          ),
        ),
      )
    }
  }

  val e2eSpec = suite("Copyright Attribution and Licenses")(createResourceSuite, createValueSuite) @@ TestAspect.before(
    enableLicenses,
  )

  private def createStillImageResourceWithInfos(
    copyrightHolder: Option[CopyrightHolder] = Some(aCopyrightHolder),
    authorship: Option[List[Authorship]] = Some(someAuthorship),
    licenseIri: Option[LicenseIri] = Some(enabledLicenseIri),
  ) = createStillImageResource(copyrightHolder, authorship, licenseIri)

  private def createStillImageResource(
    copyrightHolder: Option[CopyrightHolder] = None,
    authorship: Option[List[Authorship]] = None,
    licenseIri: Option[LicenseIri] = None,
  ) =
    for {
      asset         <- TestDspIngestClient.createImageAsset(anythingShortcode)
      responseBody  <- postCreateResource(asset, copyrightHolder, authorship, licenseIri).flatMap(_.assert200)
      responseModel <- ModelOps.fromJsonLd(responseBody).mapError(Exception(_))
    } yield (responseModel, asset)

  private def postCreateResource(
    img: UploadedFile,
    copyrightHolder: Option[CopyrightHolder] = None,
    authorship: Option[List[Authorship]] = None,
    licenseIri: Option[LicenseIri] = None,
  ) = TestResourcesApiClient.createStillImageRepresentation(
    anythingShortcode,
    anythingOntologyIri,
    "ThingPicture",
    img,
    rootUser,
    LegalInfo(copyrightHolder, authorship, licenseIri),
  )

  private def putUpdateValue(
    uploadedFile: UploadedFile,
    resourceIri: ResourceIri,
    valueIdOld: ValueIri,
    copyrightHolder: CopyrightHolder,
    authorship: List[Authorship],
    licenseIri: LicenseIri,
  ): ZIO[TestApiClient, Throwable, Response[Either[String, String]]] = {
    val request = UpdateStillImageFileValueRequest(
      resourceIri,
      valueIdOld,
      ResourceClassIri.unsafeFrom("http://0.0.0.0:3333/ontology/0001/anything/v2#ThingPicture"),
      copyrightHolder,
      authorship,
      licenseIri,
      uploadedFile.internalFilename,
    )
    TestApiClient.putJsonLd(uri"/v2/values", request.jsonLd, rootUser)
  }

  private def getResourceFromApi(resourceId: ResourceIri) = for {
    responseBody <- TestApiClient.getJsonLd(uri"/v2/resources/${resourceId.toComplexSchema}").flatMap(_.assert200)
    model        <- ModelOps.fromJsonLd(responseBody).mapError(Exception(_))
  } yield model

  private def getValueFromApi(createResourceResponse: Model): ZIO[env, Throwable, Model] = for {
    valueId    <- valueId(createResourceResponse)
    resourceId <- resourceId(createResourceResponse)
    model      <- getValueFromApi(valueId, resourceId)
  } yield model

  private def getValueFromApi(valueId: ValueIri, resourceId: ResourceIri): ZIO[env, Throwable, Model] = for {
    responseBody <-
      TestApiClient.getJsonLd(uri"/v2/values/${resourceId.toComplexSchema}/${valueId.valueId}").flatMap(_.assert200)
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
