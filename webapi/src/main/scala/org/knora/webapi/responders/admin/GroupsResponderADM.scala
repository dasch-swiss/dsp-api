/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.admin

import com.typesafe.scalalogging.LazyLogging
import zio._

import dsp.errors._
import org.knora.webapi._
import org.knora.webapi.core.MessageHandler
import org.knora.webapi.core.MessageRelay
import org.knora.webapi.messages.IriConversions._
import org.knora.webapi.messages.OntologyConstants.KnoraAdmin._
import org.knora.webapi.messages.ResponderRequest
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.groupsmessages._
import org.knora.webapi.messages.admin.responder.projectsmessages.Project
import org.knora.webapi.messages.store.triplestoremessages.SparqlExtendedConstructResponse.ConstructPredicateObjects
import org.knora.webapi.messages.store.triplestoremessages._
import org.knora.webapi.messages.twirl.queries.sparql
import org.knora.webapi.responders.IriService
import org.knora.webapi.responders.Responder
import org.knora.webapi.slice.admin.domain.model
import org.knora.webapi.slice.admin.domain.model.Group
import org.knora.webapi.slice.admin.domain.service.KnoraUserService
import org.knora.webapi.slice.admin.domain.service.ProjectService
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries._
import org.knora.webapi.util.ZioHelper

final case class GroupsResponderADM(
  triplestore: TriplestoreService,
  messageRelay: MessageRelay,
  iriService: IriService,
  knoraUserService: KnoraUserService,
  projectService: ProjectService,
)(implicit val stringFormatter: StringFormatter)
    extends MessageHandler
    with GroupsADMJsonProtocol
    with LazyLogging {

  override def isResponsibleFor(message: ResponderRequest): Boolean = message.isInstanceOf[GroupsResponderRequestADM]

  /**
   * Receives a message extending [[GroupsResponderRequestADM]], and returns an appropriate response message
   */
  def handle(msg: ResponderRequest): Task[Any] = msg match {
    case r: MultipleGroupsGetRequestADM => multipleGroupsGetRequestADM(r.groupIris)
    case other                          => Responder.handleUnexpectedMessage(other, this.getClass.getName)
  }

  private def convertStatementsToGroupADM(statements: (SubjectV2, ConstructPredicateObjects)): Task[Group] = {
    val groupIri: SubjectV2                      = statements._1
    val propertiesMap: ConstructPredicateObjects = statements._2
    def getOption[A <: LiteralV2](key: IRI): UIO[Option[Seq[A]]] =
      ZIO.succeed(propertiesMap.get(key.toSmartIri).map(_.map(_.asInstanceOf[A])))
    def getOrFail[A <: LiteralV2](key: IRI): Task[Seq[A]] =
      getOption[A](key)
        .flatMap(ZIO.fromOption(_))
        .orElseFail(InconsistentRepositoryDataException(s"Project: $groupIri has no $key defined."))
    def getFirstValueOrFail[A <: LiteralV2](key: IRI): Task[A] = getOrFail[A](key).map(_.head)
    for {
      projectIri <- getFirstValueOrFail[IriLiteralV2](BelongsToProject).map(_.value)
      project <- findProjectByIriOrFail(
                   projectIri,
                   InconsistentRepositoryDataException(
                     s"Project $projectIri was referenced by $groupIri but was not found in the triplestore.",
                   ),
                 )
      name         <- getFirstValueOrFail[StringLiteralV2](GroupName).map(_.value)
      descriptions <- getOrFail[StringLiteralV2](GroupDescriptions)
      status       <- getFirstValueOrFail[BooleanLiteralV2](StatusProp).map(_.value)
      selfjoin     <- getFirstValueOrFail[BooleanLiteralV2](HasSelfJoinEnabled).map(_.value)
    } yield Group(groupIri.toString, name, descriptions, Some(project), status, selfjoin)
  }

  private def findProjectByIriOrFail(iri: String, failReason: Throwable): Task[Project] =
    for {
      id      <- ZIO.fromEither(model.KnoraProject.ProjectIri.from(iri)).mapError(BadRequestException.apply)
      project <- projectService.findById(id).someOrFail(failReason)
    } yield project

  /**
   * Gets the group with the given group IRI and returns the information as a [[Group]].
   *
   * @param groupIri       the IRI of the group requested.
   * @return information about the group as a [[Group]]
   */
  private def groupGetADM(groupIri: IRI): Task[Option[Group]] = {
    val query = Construct(sparql.admin.txt.getGroups(maybeIri = Some(groupIri)))
    for {
      statements <- triplestore.query(query).flatMap(_.asExtended).map(_.statements.headOption)
      maybeGroup <- statements.map(convertStatementsToGroupADM).map(_.map(Some(_))).getOrElse(ZIO.succeed(None))
    } yield maybeGroup
  }

  /**
   * Gets the groups with the given IRIs and returns a set of [[GroupGetResponseADM]] objects.
   *
   * @param groupIris      the IRIs of the groups being requested
   * @return information about the group as a set of [[GroupGetResponseADM]] objects.
   */
  private def multipleGroupsGetRequestADM(groupIris: Set[IRI]): Task[Set[GroupGetResponseADM]] =
    ZioHelper.sequence(groupIris.map { iri =>
      groupGetADM(iri)
        .flatMap(ZIO.fromOption(_))
        .mapBoth(_ => NotFoundException(s"Group <$iri> not found."), GroupGetResponseADM.apply)
    })
}

object GroupsResponderADM {
  val layer = ZLayer.fromZIO {
    for {
      ts      <- ZIO.service[TriplestoreService]
      iris    <- ZIO.service[IriService]
      sf      <- ZIO.service[StringFormatter]
      kus     <- ZIO.service[KnoraUserService]
      ps      <- ZIO.service[ProjectService]
      mr      <- ZIO.service[MessageRelay]
      handler <- mr.subscribe(GroupsResponderADM(ts, mr, iris, kus, ps)(sf))
    } yield handler
  }
}
