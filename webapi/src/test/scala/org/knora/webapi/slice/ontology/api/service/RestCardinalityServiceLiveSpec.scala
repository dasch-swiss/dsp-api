/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.api.service

import zio._
import zio.test._

import org.knora.webapi.IRI
import org.knora.webapi.config.AppConfig
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.util.KnoraSystemInstances.Users.SystemUser
import org.knora.webapi.slice.ontology.api.service.RestCardinalityServiceLiveSpec.StubCardinalitiesService.replaceSuccess
import org.knora.webapi.slice.ontology.api.service.RestCardinalityServiceLiveSpec.StubCardinalitiesService.setSuccess
import org.knora.webapi.slice.ontology.domain.OntologyCacheDataBuilder
import org.knora.webapi.slice.ontology.domain.ReadOntologyV2Builder
import org.knora.webapi.slice.ontology.domain.model.Cardinality
import org.knora.webapi.slice.ontology.domain.service.CardinalityService
import org.knora.webapi.slice.ontology.domain.service.ChangeCardinalityCheckResult
import org.knora.webapi.slice.ontology.domain.service.ChangeCardinalityCheckResult.CanReplaceCardinalityCheckResult.IsInUseCheckFailure
import org.knora.webapi.slice.ontology.domain.service.ChangeCardinalityCheckResult._
import org.knora.webapi.slice.ontology.repo.service.OntologyCacheFake
import org.knora.webapi.slice.ontology.repo.service.OntologyRepoLive
import org.knora.webapi.slice.resourceinfo.domain.InternalIri
import org.knora.webapi.slice.resourceinfo.domain.IriConverter
import org.knora.webapi.slice.resourceinfo.domain.IriTestConstants
import org.knora.webapi.util.JsonHelper.StringToJson
import org.knora.webapi.util.JsonHelper.parseJson
import org.knora.webapi.util.JsonHelper.renderResponseJson

object RestCardinalityServiceLiveSpec extends ZIOSpecDefault {
  private val ontology = OntologyCacheDataBuilder.builder
    .addOntology(
      ReadOntologyV2Builder
        .builder(IriTestConstants.Biblio.Ontology)
        .assignToProject(IriTestConstants.Project.TestProject)
    )
    .build
  private val projectIri: IRI  = IriTestConstants.Project.TestProject
  private val classIri: IRI    = IriTestConstants.Biblio.Class.Article.value
  private val propertyIri: IRI = IriTestConstants.Biblio.Property.hasTitle.value
  private val userWithAccess: UserADM =
    SystemUser.copy(permissions =
      SystemUser.permissions.copy(groupsPerProject = Map(projectIri -> List(OntologyConstants.KnoraAdmin.ProjectAdmin)))
    )

  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("RestCardinalityServiceLive")(
      suite("canSetCardinality")(
        test("should render a success Response") {
          for {
            _        <- StubCardinalitiesService.setSetResponseSuccess()
            response <- RestCardinalityService.canSetCardinality(classIri, propertyIri, "1", userWithAccess)
          } yield assertTrue(response.canDo.value)
        },
        test("should render a fail Response correctly with multiple Reasons and Context") {
          for {
            _ <- StubCardinalitiesService.setSetResponseFailure(
                   List(
                     CanSetCardinalityCheckResult.SuperClassCheckFailure(
                       List(
                         IriTestConstants.Biblio.Instance.SomePublicationInstance,
                         IriTestConstants.Biblio.Instance.SomeArticleInstance
                       )
                     ),
                     CanSetCardinalityCheckResult.SubclassCheckFailure(
                       List(IriTestConstants.Biblio.Instance.SomeJournalArticleInstance)
                     )
                   )
                 )
            response     <- RestCardinalityService.canSetCardinality(classIri, propertyIri, "1", userWithAccess)
            responseJson <- renderResponseJson(response)
          } yield assertTrue(
            responseJson == parseJson(
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
                |""".stripMargin
            )
          )
        },
        test("should render a fail Response correctly with single Reason and Context") {
          for {
            _ <- StubCardinalitiesService.setSetResponseFailure(
                   List(
                     CanSetCardinalityCheckResult.SubclassCheckFailure(
                       List(IriTestConstants.Biblio.Instance.SomePublicationInstance)
                     )
                   )
                 )
            response     <- RestCardinalityService.canSetCardinality(classIri, propertyIri, "1", userWithAccess)
            responseJson <- renderResponseJson(response)
          } yield assertTrue(
            responseJson == parseJson(
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
                |""".stripMargin
            )
          )
        }
      ),
      suite("canReplaceCardinality")(
        test("should render a success Response") {
          for {
            _        <- StubCardinalitiesService.setReplaceResponseSuccess()
            response <- RestCardinalityService.canReplaceCardinality(classIri, userWithAccess)
          } yield assertTrue(response.canDo.value)
        },
        test("should render a fail Response with correct reason") {
          for {
            _            <- StubCardinalitiesService.setReplaceResponseFailure(IsInUseCheckFailure)
            response     <- RestCardinalityService.canReplaceCardinality(classIri, userWithAccess)
            responseJson <- renderResponseJson(response)
          } yield assertTrue(
            responseJson ==
              """
                |{
                |  "knora-api:canDo": false,
                |  "knora-api:cannotDoReason": "Cardinality is in use.",
                |  "@context": { "knora-api": "http://api.knora.org/ontology/knora-api/v2#"}
                |}
                |""".asJson
          )
        }
      )
    ).provide(
      RestCardinalityServiceLive.layer,
      IriConverter.layer,
      StringFormatter.test,
      OntologyRepoLive.layer,
      OntologyCacheFake.withCache(ontology),
      StubCardinalitiesService.layer,
      AppConfig.layer
    )

  final case class StubCardinalitiesService(
    setResponse: Ref[Either[List[CanSetCardinalityCheckResult.Failure], CanSetCardinalityCheckResult.Success.type]],
    replaceResponse: Ref[CanReplaceCardinalityCheckResult.CanReplaceCardinalityCheckResult]
  ) extends CardinalityService {

    def setSetResponseFailure(response: List[CanSetCardinalityCheckResult.Failure]): UIO[Unit] =
      setResponse.set(Left(response))

    def setSetResponseSuccess(): UIO[Unit] = setResponse.set(setSuccess)
    override def canSetCardinality(
      classIri: InternalIri,
      propertyIri: InternalIri,
      newCardinality: Cardinality
    ): Task[Either[List[CanSetCardinalityCheckResult.Failure], CanSetCardinalityCheckResult.Success.type]] =
      setResponse.get

    def setReplaceFailure(response: CanReplaceCardinalityCheckResult.Failure): UIO[Unit] =
      replaceResponse.set(response)

    def setReplaceResponseSuccess(): UIO[Unit] = replaceResponse.set(replaceSuccess)
    override def canReplaceCardinality(
      classIri: InternalIri
    ): Task[CanReplaceCardinalityCheckResult.CanReplaceCardinalityCheckResult] = replaceResponse.get
  }
  object StubCardinalitiesService {
    def setSetResponseSuccess(): ZIO[StubCardinalitiesService, Nothing, Unit] =
      ZIO.serviceWithZIO[StubCardinalitiesService](_.setSetResponseSuccess())
    def setSetResponseFailure(
      failure: List[CanSetCardinalityCheckResult.Failure]
    ): ZIO[StubCardinalitiesService, Nothing, Unit] =
      ZIO.serviceWithZIO[StubCardinalitiesService](_.setSetResponseFailure(failure))
    def setReplaceResponseSuccess(): ZIO[StubCardinalitiesService, Nothing, Unit] =
      ZIO.serviceWithZIO[StubCardinalitiesService](_.setReplaceResponseSuccess())
    def setReplaceResponseFailure(
      failure: ChangeCardinalityCheckResult.CanReplaceCardinalityCheckResult.Failure
    ): ZIO[StubCardinalitiesService, Nothing, Unit] =
      ZIO.serviceWithZIO[StubCardinalitiesService](_.setReplaceFailure(failure))
    private val replaceSuccess = CanReplaceCardinalityCheckResult.Success
    private val setSuccess     = Right(CanSetCardinalityCheckResult.Success)
    val layer: ULayer[StubCardinalitiesService] = ZLayer.fromZIO {
      for {
        refSet <-
          Ref.make[Either[List[CanSetCardinalityCheckResult.Failure], CanSetCardinalityCheckResult.Success.type]](
            setSuccess
          )
        refRepl <- Ref.make[CanReplaceCardinalityCheckResult.CanReplaceCardinalityCheckResult](replaceSuccess)
      } yield StubCardinalitiesService(refSet, refRepl)
    }
  }
}
