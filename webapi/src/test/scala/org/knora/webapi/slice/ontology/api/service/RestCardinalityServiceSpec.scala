/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.api.service

import zio.*
import zio.test.*

import org.knora.webapi.IRI
import org.knora.webapi.config.AppConfig
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.util.KnoraSystemInstances.Users.SystemUser
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.service.KnoraGroupRepo
import org.knora.webapi.slice.ontology.api.service.RestCardinalityServiceSpec.StubCardinalitiesService.replaceSuccess
import org.knora.webapi.slice.ontology.api.service.RestCardinalityServiceSpec.StubCardinalitiesService.setSuccess
import org.knora.webapi.slice.ontology.domain.OntologyCacheDataBuilder
import org.knora.webapi.slice.ontology.domain.ReadOntologyV2Builder
import org.knora.webapi.slice.ontology.domain.model.Cardinality
import org.knora.webapi.slice.ontology.domain.service.CardinalityService
import org.knora.webapi.slice.ontology.domain.service.ChangeCardinalityCheckResult
import org.knora.webapi.slice.ontology.domain.service.ChangeCardinalityCheckResult.*
import org.knora.webapi.slice.ontology.domain.service.ChangeCardinalityCheckResult.CanReplaceCardinalityCheckResult.IsInUseCheckFailure
import org.knora.webapi.slice.ontology.domain.service.IriConverter
import org.knora.webapi.slice.ontology.repo.service.OntologyCacheFake
import org.knora.webapi.slice.ontology.repo.service.OntologyRepoLive
import org.knora.webapi.slice.resourceinfo.domain.InternalIri
import org.knora.webapi.slice.resourceinfo.domain.IriTestConstants
import org.knora.webapi.util.JsonHelper.StringToJson
import org.knora.webapi.util.JsonHelper.renderResponseJson

object RestCardinalityServiceSpec extends ZIOSpecDefault {
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
  private val userWithAccess: User =
    SystemUser.copy(permissions =
      SystemUser.permissions
        .copy(groupsPerProject = Map(projectIri.value -> List(KnoraGroupRepo.builtIn.ProjectAdmin.id.value))),
    )

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("RestCardinalityServiceLive")(
      suite("canSetCardinality")(
        test("should render a success Response") {
          for {
            _ <- StubCardinalitiesService.setSetResponseSuccess()
            response <- ZIO.serviceWithZIO[RestCardinalityService](
                          _.canSetCardinality(classIri, propertyIri, "1", userWithAccess),
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
                          _.canSetCardinality(classIri, propertyIri, "1", userWithAccess),
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
                          _.canSetCardinality(classIri, propertyIri, "1", userWithAccess),
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
      suite("canReplaceCardinality")(
        test("should render a success Response") {
          for {
            _        <- StubCardinalitiesService.setReplaceResponseSuccess()
            response <- ZIO.serviceWithZIO[RestCardinalityService](_.canReplaceCardinality(classIri, userWithAccess))
          } yield assertTrue(response.canDo.value)
        },
        test("should render a fail Response with correct reason") {
          for {
            _            <- StubCardinalitiesService.setReplaceResponseFailure(IsInUseCheckFailure)
            response     <- ZIO.serviceWithZIO[RestCardinalityService](_.canReplaceCardinality(classIri, userWithAccess))
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
    ).provide(
      RestCardinalityService.layer,
      IriConverter.layer,
      StringFormatter.test,
      OntologyRepoLive.layer,
      OntologyCacheFake.withCache(ontology),
      StubCardinalitiesService.layer,
      AppConfig.layer,
    )

  final case class StubCardinalitiesService(
    setResponse: Ref[Either[List[CanSetCardinalityCheckResult.Failure], CanSetCardinalityCheckResult.Success.type]],
    replaceResponse: Ref[CanReplaceCardinalityCheckResult.CanReplaceCardinalityCheckResult],
  ) extends CardinalityService {

    def setSetResponseFailure(response: List[CanSetCardinalityCheckResult.Failure]): UIO[Unit] =
      setResponse.set(Left(response))

    def setSetResponseSuccess(): UIO[Unit] = setResponse.set(setSuccess)
    override def canSetCardinality(
      classIri: InternalIri,
      propertyIri: InternalIri,
      newCardinality: Cardinality,
    ): Task[Either[List[CanSetCardinalityCheckResult.Failure], CanSetCardinalityCheckResult.Success.type]] =
      setResponse.get

    def setReplaceFailure(response: CanReplaceCardinalityCheckResult.Failure): UIO[Unit] =
      replaceResponse.set(response)

    def setReplaceResponseSuccess(): UIO[Unit] = replaceResponse.set(replaceSuccess)
    override def canReplaceCardinality(
      classIri: InternalIri,
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
    private val replaceSuccess = CanReplaceCardinalityCheckResult.Success
    private val setSuccess     = Right(CanSetCardinalityCheckResult.Success)
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
