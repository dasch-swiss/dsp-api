/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.v1

import zio._

import dsp.errors.InconsistentRepositoryDataException
import dsp.errors.NotFoundException
import org.knora.webapi._
import org.knora.webapi.core.MessageHandler
import org.knora.webapi.core.MessageRelay
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.ResponderRequest
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.usersmessages.UserGetRequestADM
import org.knora.webapi.messages.admin.responder.usersmessages.UserIdentifierADM
import org.knora.webapi.messages.admin.responder.usersmessages.UserResponseADM
import org.knora.webapi.messages.util.KnoraSystemInstances
import org.knora.webapi.messages.util.rdf.VariableResultsRow
import org.knora.webapi.messages.v1.responder.ontologymessages.NamedGraphV1
import org.knora.webapi.messages.v1.responder.ontologymessages.NamedGraphsGetRequestV1
import org.knora.webapi.messages.v1.responder.ontologymessages.NamedGraphsResponseV1
import org.knora.webapi.messages.v1.responder.projectmessages._
import org.knora.webapi.messages.v1.responder.usermessages._
import org.knora.webapi.responders.Responder
import org.knora.webapi.store.triplestore.api.TriplestoreService

/**
 * Returns information about Knora projects.
 */
trait ProjectsResponderV1

final case class ProjectsResponderV1Live(
  messageRelay: MessageRelay,
  triplestoreService: TriplestoreService,
  implicit val stringFormatter: StringFormatter
) extends ProjectsResponderV1
    with MessageHandler {

  override def isResponsibleFor(message: ResponderRequest): Boolean =
    message.isInstanceOf[ProjectsResponderRequestV1]

  override def handle(msg: ResponderRequest): Task[Any] = msg match {
    case ProjectsGetRequestV1(userProfile) =>
      projectsGetRequestV1(userProfile)
    case ProjectsGetV1(userProfile) => projectsGetV1(userProfile)
    case ProjectInfoByIRIGetRequestV1(iri, userProfile) =>
      projectInfoByIRIGetRequestV1(iri, userProfile)
    case ProjectInfoByIRIGetV1(iri, userProfile) =>
      projectInfoByIRIGetV1(iri, userProfile)
    case ProjectInfoByShortnameGetRequestV1(shortname, userProfile) =>
      projectInfoByShortnameGetRequestV1(shortname, userProfile)
    case other => Responder.handleUnexpectedMessage(other, this.getClass.getName)
  }

  private val notFoundException = (iri: String, property: String) =>
    InconsistentRepositoryDataException(s"Project: $iri has no $property defined.")

  /**
   * Gets all the projects and returns them as a [[ProjectsResponseV1]].
   *
   * @param userProfile          the profile of the user that is making the request.
   * @return all the projects as a [[ProjectsResponseV1]] or [[NotFoundException]] if no projects are found.
   */
  private def projectsGetRequestV1(
    userProfile: Option[UserProfileV1]
  ): Task[ProjectsResponseV1] =
    for {
      projects <- projectsGetV1(userProfile)
      result <-
        if (projects.nonEmpty) ZIO.succeed(ProjectsResponseV1(projects))
        else ZIO.fail(NotFoundException(s"No projects found"))
    } yield result

  /**
   * Gets all the projects and returns them as a sequence containing [[ProjectInfoV1]].
   *
   * @param userProfile          the profile of the user that is making the request.
   * @return all the projects as a sequence containing [[ProjectInfoV1]].
   */
  private def projectsGetV1(
    userProfile: Option[UserProfileV1]
  ): Task[Seq[ProjectInfoV1]] =
    for {
      sparqlQueryString <- ZIO.attempt(
                             org.knora.webapi.messages.twirl.queries.sparql.v1.txt
                               .getProjects()
                               .toString()
                           )
      projectsResponse <- triplestoreService.sparqlHttpSelect(sparqlQueryString)

      projectsResponseRows: Seq[VariableResultsRow] = projectsResponse.results.bindings

      projectsWithProperties: Map[IRI, Map[IRI, Seq[String]]] =
        projectsResponseRows.groupBy(_.rowMap("s")).map { case (projIri: String, rows: Seq[VariableResultsRow]) =>
          (
            projIri,
            rows.groupBy(_.rowMap("p")).map { case (predicate: IRI, literals: Seq[VariableResultsRow]) =>
              predicate -> literals.map(_.rowMap("o"))
            }
          )
        }

      ontologiesForProjects <- getOntologiesForProjects(
                                 userProfile = userProfile
                               )

      projects <-
        ZIO.collectAll(projectsWithProperties.map { case (projectIri: String, propsMap: Map[String, Seq[String]]) =>
          val keywordsSeq: Seq[String] =
            propsMap.getOrElse(OntologyConstants.KnoraAdmin.ProjectKeyword, Seq.empty[String]).sorted

          val maybeKeywords: Option[String] =
            if (keywordsSeq.nonEmpty) Some(keywordsSeq.mkString(", "))
            else None

          val ontologies = ontologiesForProjects.getOrElse(projectIri, Seq.empty[IRI])

          for {
            sn <- ZIO
                    .fromOption(propsMap.get(OntologyConstants.KnoraAdmin.ProjectShortname))
                    .orElseFail(notFoundException(projectIri, "shortname"))
            sc <- ZIO
                    .fromOption(propsMap.get(OntologyConstants.KnoraAdmin.ProjectShortcode))
                    .orElseFail(notFoundException(projectIri, "shortcode"))
            status <- ZIO
                        .fromOption(propsMap.get(OntologyConstants.KnoraAdmin.Status))
                        .orElseFail(notFoundException(projectIri, "status"))
            sj <- ZIO
                    .fromOption(propsMap.get(OntologyConstants.KnoraAdmin.HasSelfJoinEnabled))
                    .orElseFail(notFoundException(projectIri, "hasSelfJoinEnabled"))
          } yield ProjectInfoV1(
            id = projectIri,
            shortname = sn.head,
            shortcode = sc.head,
            longname = propsMap.get(OntologyConstants.KnoraAdmin.ProjectLongname).map(_.head),
            description = propsMap.get(OntologyConstants.KnoraAdmin.ProjectDescription).map(_.head),
            keywords = maybeKeywords,
            logo = propsMap.get(OntologyConstants.KnoraAdmin.ProjectLogo).map(_.head),
            institution = propsMap.get(OntologyConstants.KnoraAdmin.BelongsToInstitution).map(_.head),
            ontologies = ontologies,
            status = status.head.toBoolean,
            selfjoin = sj.head.toBoolean
          )
        }.toSeq)
    } yield projects

  /**
   * Gets the ontologies that belong to each project.
   *
   * @param projectIris the IRIs of the projects whose ontologies should be requested. If empty, returns the ontologies
   *                    for all projects.
   * @param userProfile the requesting user.
   * @return a map of project IRIs to sequences of ontology IRIs.
   */
  private def getOntologiesForProjects(
    projectIris: Set[IRI] = Set.empty[IRI],
    userProfile: Option[UserProfileV1]
  ): Task[Map[IRI, Seq[IRI]]] =
    for {
      // Get a UserADM for the UserProfileV1, because we need it to send a message to OntologyResponderV1.
      userADM <- userProfile match {
                   case Some(profile) =>
                     profile.userData.user_id match {
                       case Some(user_iri) =>
                         messageRelay
                           .ask[UserResponseADM](
                             UserGetRequestADM(
                               identifier = UserIdentifierADM(maybeIri = Some(user_iri)),
                               requestingUser = KnoraSystemInstances.Users.SystemUser
                             )
                           )
                           .map(_.user)

                       case None => ZIO.succeed(KnoraSystemInstances.Users.AnonymousUser)
                     }

                   case None => ZIO.succeed(KnoraSystemInstances.Users.AnonymousUser)
                 }

      // Get the ontologies per project.

      namedGraphsResponse <- messageRelay
                               .ask[NamedGraphsResponseV1](
                                 NamedGraphsGetRequestV1(
                                   projectIris = projectIris,
                                   userADM = userADM
                                 )
                               )

    } yield namedGraphsResponse.vocabularies.map { namedGraph: NamedGraphV1 =>
      namedGraph.project_id -> namedGraph.id
    }
      .groupBy(_._1)
      .map { case (projectIri, projectIriAndOntologies: Seq[(IRI, IRI)]) =>
        projectIri -> projectIriAndOntologies.map(_._2)
      }

  /**
   * Gets the project with the given project IRI and returns the information as a [[ProjectInfoResponseV1]].
   *
   * @param projectIri           the IRI of the project requested.
   * @param userProfile          the profile of user that is making the request.
   * @return information about the project as a [[ProjectInfoResponseV1]] or [[NotFoundException]]
   *         when no project for the given IRI can be found
   */
  private def projectInfoByIRIGetRequestV1(
    projectIri: IRI,
    userProfile: Option[UserProfileV1] = None
  ): Task[ProjectInfoResponseV1] =
    for {
      maybeProjectInfo <- projectInfoByIRIGetV1(projectIri, userProfile)
      projectInfo <-
        maybeProjectInfo match {
          case Some(pi) => ZIO.succeed(pi)
          case None     => ZIO.fail(NotFoundException(s"Project '$projectIri' not found"))
        }
    } yield ProjectInfoResponseV1(project_info = projectInfo)

  /**
   * Gets the project with the given project IRI and returns the information as a [[ProjectInfoV1]].
   *
   * @param projectIri           the IRI of the project requested.
   * @param userProfile          the profile of user that is making the request.
   * @return information about the project as a [[ProjectInfoV1]].
   */
  private def projectInfoByIRIGetV1(
    projectIri: IRI,
    userProfile: Option[UserProfileV1] = None
  ): Task[Option[ProjectInfoV1]] =
    for {
      sparqlQuery <- ZIO.attempt(
                       org.knora.webapi.messages.twirl.queries.sparql.v1.txt
                         .getProjectByIri(
                           projectIri = projectIri
                         )
                         .toString()
                     )

      projectResponse <- triplestoreService.sparqlHttpSelect(sparqlQuery)

      ontologiesForProjects <- getOntologiesForProjects(
                                 projectIris = Set(projectIri),
                                 userProfile = userProfile
                               )

      projectOntologies = ontologiesForProjects.getOrElse(projectIri, Seq.empty[IRI])

      result <-
        if (projectResponse.results.bindings.nonEmpty)
          createProjectInfoV1(
            projectResponse = projectResponse.results.bindings,
            projectIri = projectIri,
            ontologies = projectOntologies,
            userProfile
          ).map(Some(_))
        else ZIO.none
    } yield result

  /**
   * Gets the project with the given shortname and returns the information as a [[ProjectInfoResponseV1]].
   *
   * @param shortName            the shortname of the project requested.
   *
   * @param userProfile          the profile of user that is making the request.
   * @return information about the project as a [[ProjectInfoResponseV1]].
   *
   *         [[NotFoundException]] in the case that no project for the given shortname can be found.
   */
  private def projectInfoByShortnameGetRequestV1(
    shortName: String,
    userProfile: Option[UserProfileV1]
  ): Task[ProjectInfoResponseV1] =
    for {
      sparqlQueryString <- ZIO.attempt(
                             org.knora.webapi.messages.twirl.queries.sparql.v1.txt
                               .getProjectByShortname(
                                 shortname = shortName
                               )
                               .toString()
                           )
      projectResponse <- triplestoreService.sparqlHttpSelect(sparqlQueryString)

      // get project IRI from results rows
      projectIri <-
        if (projectResponse.results.bindings.nonEmpty) ZIO.succeed(projectResponse.results.bindings.head.rowMap("s"))
        else ZIO.fail(NotFoundException(s"Project '$shortName' not found"))

      ontologiesForProjects <- getOntologiesForProjects(
                                 projectIris = Set(projectIri),
                                 userProfile = userProfile
                               )

      projectOntologies = ontologiesForProjects(projectIri)

      projectInfo <-
        createProjectInfoV1(
          projectResponse = projectResponse.results.bindings,
          projectIri = projectIri,
          ontologies = projectOntologies,
          userProfile
        )

    } yield ProjectInfoResponseV1(
      project_info = projectInfo
    )

  ////////////////////
  // Helper Methods //
  ////////////////////

  /**
   * Helper method that turns SPARQL result rows into a [[ProjectInfoV1]].
   *
   * @param projectResponse results from the SPARQL query representing information about the project.
   * @param projectIri      the IRI of the project the querid information belong to.
   * @param userProfile     the profile of user that is making the request.
   * @return a [[ProjectInfoV1]] representing information about project.
   */
  private def createProjectInfoV1(
    projectResponse: Seq[VariableResultsRow],
    projectIri: IRI,
    ontologies: Seq[IRI],
    userProfile: Option[UserProfileV1]
  ): Task[ProjectInfoV1] =
    if (projectResponse.nonEmpty) {

      val projectProperties: Map[String, Seq[String]] = projectResponse.groupBy(_.rowMap("p")).map {
        case (predicate, rows) => predicate -> rows.map(_.rowMap("o"))
      }

      val keywordsSeq: Seq[String] =
        projectProperties.getOrElse(OntologyConstants.KnoraAdmin.ProjectKeyword, Seq.empty[String]).sorted

      val maybeKeywords: Option[String] = if (keywordsSeq.nonEmpty) {
        Some(keywordsSeq.mkString(", "))
      } else {
        None
      }

      for {
        sn <- ZIO
                .fromOption(projectProperties.get(OntologyConstants.KnoraAdmin.ProjectShortname))
                .orElseFail(notFoundException(projectIri, "shortname"))
        sc <- ZIO
                .fromOption(projectProperties.get(OntologyConstants.KnoraAdmin.ProjectShortcode))
                .orElseFail(notFoundException(projectIri, "shortcode"))
        status <- ZIO
                    .fromOption(projectProperties.get(OntologyConstants.KnoraAdmin.Status))
                    .orElseFail(notFoundException(projectIri, "status"))
        sj <- ZIO
                .fromOption(projectProperties.get(OntologyConstants.KnoraAdmin.HasSelfJoinEnabled))
                .orElseFail(notFoundException(projectIri, "hasSelfJoinEnabled"))
      } yield ProjectInfoV1(
        id = projectIri,
        shortname = sn.head,
        shortcode = sc.head,
        longname = projectProperties.get(OntologyConstants.KnoraAdmin.ProjectLongname).map(_.head),
        description = projectProperties.get(OntologyConstants.KnoraAdmin.ProjectDescription).map(_.head),
        keywords = maybeKeywords,
        logo = projectProperties.get(OntologyConstants.KnoraAdmin.ProjectLogo).map(_.head),
        institution = projectProperties.get(OntologyConstants.KnoraAdmin.BelongsToInstitution).map(_.head),
        ontologies = ontologies,
        status = status.head.toBoolean,
        selfjoin = sj.head.toBoolean
      )
    } else
      ZIO.fail(NotFoundException(s"For the given project IRI $projectIri no information was found"))

  /**
   * Helper method for checking if a project identified by shortname exists.
   *
   * @param shortname the shortname of the project.
   * @return a [[Boolean]].
   */
  def projectByShortnameExists(shortname: String): Task[Boolean] =
    for {
      askString <- ZIO.attempt(
                     org.knora.webapi.messages.twirl.queries.sparql.admin.txt
                       .checkProjectExistsByShortname(shortname = shortname)
                       .toString
                   )
      checkProjectExistsResponse <- triplestoreService.sparqlHttpAsk(askString)
      result                      = checkProjectExistsResponse.result

    } yield result

  /**
   * Helper method for checking if a project identified by shortcode exists.
   *
   * @param shortcode the shortcode of the project.
   * @return a [[Boolean]].
   */
  def projectByShortcodeExists(shortcode: String): Task[Boolean] =
    for {
      askString <- ZIO.attempt(
                     org.knora.webapi.messages.twirl.queries.sparql.admin.txt
                       .checkProjectExistsByShortcode(shortcode = shortcode)
                       .toString
                   )
      checkProjectExistsResponse <- triplestoreService.sparqlHttpAsk(askString)
      result                      = checkProjectExistsResponse.result

    } yield result
}
object ProjectsResponderV1Live {
  val layer: URLayer[
    StringFormatter with TriplestoreService with MessageRelay,
    ProjectsResponderV1
  ] = ZLayer.fromZIO {
    for {
      mr      <- ZIO.service[MessageRelay]
      ts      <- ZIO.service[TriplestoreService]
      sf      <- ZIO.service[StringFormatter]
      handler <- mr.subscribe(ProjectsResponderV1Live(mr, ts, sf))
    } yield handler
  }
}
