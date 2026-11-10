/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v2.ontologies

import org.junit.runner.RunWith
import zio.*
import zio.test.*
import zio.test.Assertion.failsWithA

import dsp.errors.BadRequestException
import dsp.errors.ForbiddenException
import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.IRI
import org.knora.webapi.TestDataFactory
import org.knora.webapi.config.AppConfig
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionsDataADM
import org.knora.webapi.responders.IriService
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.repo.KnoraProjectRepoInMemory
import org.knora.webapi.slice.admin.domain.service.KnoraGroupRepo
import org.knora.webapi.slice.admin.domain.service.KnoraGroupService
import org.knora.webapi.slice.admin.domain.service.KnoraProjectService
import org.knora.webapi.slice.admin.domain.service.KnoraUserService
import org.knora.webapi.slice.admin.domain.service.PasswordService
import org.knora.webapi.slice.admin.repo.LicenseRepo
import org.knora.webapi.slice.admin.repo.service.KnoraGroupRepoInMemory
import org.knora.webapi.slice.admin.repo.service.KnoraUserRepoLive
import org.knora.webapi.slice.api.v2.ontologies.RestCardinalityService
import org.knora.webapi.slice.api.v2.ontologies.RestCardinalityServiceSpec.StubCardinalitiesService
import org.knora.webapi.slice.api.v2.ontologies.RestCardinalityServiceSpec.StubCardinalitiesService.replaceSuccess
import org.knora.webapi.slice.api.v2.ontologies.RestCardinalityServiceSpec.StubCardinalitiesService.setSuccess
import org.knora.webapi.slice.common.KnoraIris.PropertyIri
import org.knora.webapi.slice.common.KnoraIris.ResourceClassIri
import org.knora.webapi.slice.common.api.AuthorizationRestService
import org.knora.webapi.slice.common.api.TestCandidates
import org.knora.webapi.slice.common.service.IriConverter
import org.knora.webapi.slice.infrastructure.CacheManager
import org.knora.webapi.slice.infrastructure.OtelSetup
import org.knora.webapi.slice.ontology.domain.OntologyCacheDataBuilder
import org.knora.webapi.slice.ontology.domain.ReadOntologyV2Builder
import org.knora.webapi.slice.ontology.domain.model.Cardinality
import org.knora.webapi.slice.ontology.domain.service.CardinalityService
import org.knora.webapi.slice.ontology.domain.service.ChangeCardinalityCheckResult
import org.knora.webapi.slice.ontology.domain.service.ChangeCardinalityCheckResult.*
import org.knora.webapi.slice.ontology.domain.service.ChangeCardinalityCheckResult.CanReplaceCardinalityCheckResult.IsInUseCheckFailure
import org.knora.webapi.slice.ontology.repo.service.OntologyCacheFake
import org.knora.webapi.slice.ontology.repo.service.OntologyRepoLive
import org.knora.webapi.slice.resourceinfo.domain.IriTestConstants
import org.knora.webapi.store.triplestore.impl.TriplestoreServiceLive
import org.knora.webapi.util.JsonHelper.StringToJson
import org.knora.webapi.util.JsonHelper.renderResponseJson

@RunWith(classOf[DspZTestJUnitRunner])
class RestCardinalityServiceSpec extends ZIOSpecDefault {
  private val ontology = OntologyCacheDataBuilder.builder
    .addOntology(
      ReadOntologyV2Builder
        .builder(IriTestConstants.Biblio.Ontology)
        .assignToProject(IriTestConstants.Project.TestProject),
    )
    .build
  private val projectIri       = IriTestConstants.Project.TestProject
  private val classIri: IRI    = IriTestConstants.Biblio.Class.Article.value
  private val propertyIri: IRI = IriTestConstants.Biblio.Property.hasTitle.value
  private val normalUser: User =
    User("http://iri", "username", "email@example.com", "given name", "family name", status = true, "lang")
  private val userWithAccess: User =
    normalUser.copy(permissions =
      PermissionsDataADM(Map(projectIri.value -> List(KnoraGroupRepo.builtIn.ProjectAdmin.id.value))),
    )
  private val project     = TestDataFactory.someProject.copy(id = projectIri)
  private val saveProject = ZIO.serviceWithZIO[KnoraProjectRepoInMemory](_.save(project)).orDie

  override def spec: Spec[TestEnvironment & Scope, Any] =
    (suite("RestCardinalityServiceLive")(
      suite("canSetCardinality")(
        test("should render a success Response") {
          for {
            _        <- StubCardinalitiesService.setSetResponseSuccess()
            response <- ZIO.serviceWithZIO[RestCardinalityService](
                          _.canSetCardinality(classIri, propertyIri, "1", TestCandidates.of(userWithAccess)),
                        )
          } yield assertTrue(response.canDo.value)
        },
        test("should render a fail Response correctly with multiple Reasons and Context") {
          for {
            _ <- StubCardinalitiesService.setSetResponseFailure(
                   List(
                     CanSetCardinalityCheckResult.SuperClassCheckFailure(
                       List(
                         IriTestConstants.Biblio.Instance.SomePublicationInstance,
                         IriTestConstants.Biblio.Instance.SomeArticleInstance,
                       ),
                     ),
                     CanSetCardinalityCheckResult.SubclassCheckFailure(
                       List(IriTestConstants.Biblio.Instance.SomeJournalArticleInstance),
                     ),
                   ),
                 )
            response <- ZIO.serviceWithZIO[RestCardinalityService](
                          _.canSetCardinality(classIri, propertyIri, "1", TestCandidates.of(userWithAccess)),
                        )
            responseJson <- renderResponseJson(response)
          } yield assertTrue(
            responseJson ==
              """
                |{
                |  "knora-api:canDo": false,
                |  "knora-api:cannotDoReason": "The new cardinality is not included in the cardinality of a super-class. Please fix super-classes first: http://0.0.0.0:3333/ontology/0801/biblio/v2#somePublicationInstance,http://0.0.0.0:3333/ontology/0801/biblio/v2#someArticleInstance. The new cardinality does not include the cardinality of a subclass. Please fix subclasses first: http://0.0.0.0:3333/ontology/0801/biblio/v2#someJournalArticleInstance.",
                |  "knora-api:cannotDoContext": {
                |    "knora-api:canSetCardinalityCheckFailure": [
                |      {
                |        "knora-api:canSetCardinalityOntologySuperClassCheckFailed": [
                |          {
                |            "@id": "http://0.0.0.0:3333/ontology/0801/biblio/v2#somePublicationInstance"
                |          },
                |          {
                |            "@id": "http://0.0.0.0:3333/ontology/0801/biblio/v2#someArticleInstance"
                |          }
                |        ]
                |      },
                |      {
                |        "knora-api:canSetCardinalityOntologySubclassCheckFailed": {
                |          "@id": "http://0.0.0.0:3333/ontology/0801/biblio/v2#someJournalArticleInstance"
                |        }
                |      }
                |    ]
                |  },
                |  "@context": {
                |    "knora-api": "http://api.knora.org/ontology/knora-api/v2#"
                |  }
                |}
                |""".asJson,
          )
        },
        test("should render a fail Response correctly with single Reason and Context") {
          for {
            _ <- StubCardinalitiesService.setSetResponseFailure(
                   List(
                     CanSetCardinalityCheckResult.SubclassCheckFailure(
                       List(IriTestConstants.Biblio.Instance.SomePublicationInstance),
                     ),
                   ),
                 )
            response <- ZIO.serviceWithZIO[RestCardinalityService](
                          _.canSetCardinality(classIri, propertyIri, "1", TestCandidates.of(userWithAccess)),
                        )
            responseJson <- renderResponseJson(response)
          } yield assertTrue(
            responseJson ==
              """
                |{
                |  "knora-api:canDo": false,
                |  "knora-api:cannotDoReason": "The new cardinality does not include the cardinality of a subclass. Please fix subclasses first: http://0.0.0.0:3333/ontology/0801/biblio/v2#somePublicationInstance.",
                |  "knora-api:cannotDoContext":
                |  {
                |    "knora-api:canSetCardinalityCheckFailure":
                |    {
                |      "knora-api:canSetCardinalityOntologySubclassCheckFailed":
                |        { "@id": "http://0.0.0.0:3333/ontology/0801/biblio/v2#somePublicationInstance" }
                |    }
                |  },
                |  "@context":{"knora-api":"http://api.knora.org/ontology/knora-api/v2#"}
                |}
                |""".asJson,
          )
        },
      ),
      suite("authorization")(
        test("refuses a user who is not an admin of the ontology's project") {
          for {
            exit <- ZIO
                      .serviceWithZIO[RestCardinalityService](
                        _.canReplaceCardinality(classIri, TestCandidates.of(normalUser)),
                      )
                      .exit
          } yield assert(exit)(failsWithA[ForbiddenException])
        },
        test("refuses a class of a built-in ontology") {
          for {
            exit <- ZIO
                      .serviceWithZIO[RestCardinalityService](
                        _.canReplaceCardinality(
                          "http://www.knora.org/ontology/knora-base#Resource",
                          TestCandidates.of(userWithAccess),
                        ),
                      )
                      .exit
          } yield assert(exit)(failsWithA[BadRequestException])
        },
      ),
      suite("canReplaceCardinality")(
        test("should render a success Response") {
          for {
            _        <- StubCardinalitiesService.setReplaceResponseSuccess()
            response <- ZIO.serviceWithZIO[RestCardinalityService](
                          _.canReplaceCardinality(classIri, TestCandidates.of(userWithAccess)),
                        )
          } yield assertTrue(response.canDo.value)
        },
        test("should render a fail Response with correct reason") {
          for {
            _        <- StubCardinalitiesService.setReplaceResponseFailure(IsInUseCheckFailure)
            response <- ZIO.serviceWithZIO[RestCardinalityService](
                          _.canReplaceCardinality(classIri, TestCandidates.of(userWithAccess)),
                        )
            responseJson <- renderResponseJson(response)
          } yield assertTrue(
            responseJson ==
              """
                |{
                |  "knora-api:canDo": false,
                |  "knora-api:cannotDoReason": "Cardinality is in use.",
                |  "@context": { "knora-api": "http://api.knora.org/ontology/knora-api/v2#"}
                |}
                |""".asJson,
          )
        },
      ),
    ) @@ TestAspect.before(saveProject)).provide(
      RestCardinalityService.layer,
      AuthorizationRestService.layer,
      CacheManager.layer,
      IriConverter.layer,
      IriService.layer,
      KnoraGroupRepoInMemory.layer,
      KnoraGroupService.layer,
      KnoraProjectRepoInMemory.layer,
      KnoraProjectService.layer,
      KnoraUserRepoLive.layer,
      KnoraUserService.layer,
      LicenseRepo.layer,
      PasswordService.layer,
      StringFormatter.test,
      TriplestoreServiceLive.layer,
      OtelSetup.stdOut,
      OntologyRepoLive.layer,
      OntologyCacheFake.withCache(ontology),
      StubCardinalitiesService.layer,
      AppConfig.layer,
    )
}

object RestCardinalityServiceSpec {
  final class StubCardinalitiesService(
    setResponse: Ref[Either[List[CanSetCardinalityCheckResult.Failure], CanSetCardinalityCheckResult.Success.type]],
    replaceResponse: Ref[CanReplaceCardinalityCheckResult.CanReplaceCardinalityCheckResult],
  ) extends CardinalityService {

    def setSetResponseFailure(response: List[CanSetCardinalityCheckResult.Failure]): UIO[Unit] =
      setResponse.set(Left(response))

    def setSetResponseSuccess(): UIO[Unit] = setResponse.set(setSuccess)
    override def canSetCardinality(
      classIri: ResourceClassIri,
      propertyIri: PropertyIri,
      newCardinality: Cardinality,
    ): Task[Either[List[CanSetCardinalityCheckResult.Failure], CanSetCardinalityCheckResult.Success.type]] =
      setResponse.get

    def setReplaceFailure(response: CanReplaceCardinalityCheckResult.Failure): UIO[Unit] =
      replaceResponse.set(response)

    def setReplaceResponseSuccess(): UIO[Unit] = replaceResponse.set(replaceSuccess)
    override def canReplaceCardinality(
      classIri: SmartIri,
    ): Task[CanReplaceCardinalityCheckResult.CanReplaceCardinalityCheckResult] = replaceResponse.get
  }
  object StubCardinalitiesService {
    def setSetResponseSuccess(): ZIO[StubCardinalitiesService, Nothing, Unit] =
      ZIO.serviceWithZIO[StubCardinalitiesService](_.setSetResponseSuccess())
    def setSetResponseFailure(
      failure: List[CanSetCardinalityCheckResult.Failure],
    ): ZIO[StubCardinalitiesService, Nothing, Unit] =
      ZIO.serviceWithZIO[StubCardinalitiesService](_.setSetResponseFailure(failure))
    def setReplaceResponseSuccess(): ZIO[StubCardinalitiesService, Nothing, Unit] =
      ZIO.serviceWithZIO[StubCardinalitiesService](_.setReplaceResponseSuccess())
    def setReplaceResponseFailure(
      failure: ChangeCardinalityCheckResult.CanReplaceCardinalityCheckResult.Failure,
    ): ZIO[StubCardinalitiesService, Nothing, Unit] =
      ZIO.serviceWithZIO[StubCardinalitiesService](_.setReplaceFailure(failure))
    private val replaceSuccess                  = CanReplaceCardinalityCheckResult.Success
    private val setSuccess                      = Right(CanSetCardinalityCheckResult.Success)
    val layer: ULayer[StubCardinalitiesService] = ZLayer.fromZIO {
      for {
        refSet <-
          Ref.make[Either[List[CanSetCardinalityCheckResult.Failure], CanSetCardinalityCheckResult.Success.type]](
            setSuccess,
          )
        refRepl <- Ref.make[CanReplaceCardinalityCheckResult.CanReplaceCardinalityCheckResult](replaceSuccess)
      } yield StubCardinalitiesService(refSet, refRepl)
    }
  }
}
