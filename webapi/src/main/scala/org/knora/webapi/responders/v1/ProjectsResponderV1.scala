/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.v1

import akka.http.scaladsl.util.FastFuture
import akka.pattern._

import scala.concurrent.Future

import dsp.errors.InconsistentRepositoryDataException
import dsp.errors.NotFoundException
import org.knora.webapi._
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.admin.responder.usersmessages.UserGetRequestADM
import org.knora.webapi.messages.admin.responder.usersmessages.UserIdentifierADM
import org.knora.webapi.messages.admin.responder.usersmessages.UserResponseADM
import org.knora.webapi.messages.store.triplestoremessages._
import org.knora.webapi.messages.util.KnoraSystemInstances
import org.knora.webapi.messages.util.rdf.SparqlSelectResult
import org.knora.webapi.messages.util.rdf.VariableResultsRow
import org.knora.webapi.messages.v1.responder.ontologymessages.NamedGraphV1
import org.knora.webapi.messages.v1.responder.ontologymessages.NamedGraphsGetRequestV1
import org.knora.webapi.messages.v1.responder.ontologymessages.NamedGraphsResponseV1
import org.knora.webapi.messages.v1.responder.projectmessages._
import org.knora.webapi.messages.v1.responder.usermessages._
import org.knora.webapi.responders.ActorDeps
import org.knora.webapi.responders.Responder

/**
 * Returns information about Knora projects.
 */
final case class ProjectsResponderV1(actorDeps: ActorDeps) extends Responder(actorDeps) {

  /**
   * Receives a message extending [[ProjectsResponderRequestV1]], and returns an appropriate response message.
   */
  def receive(msg: ProjectsResponderRequestV1) = msg match {
    case ProjectsGetRequestV1(userProfile) =>
      projectsGetRequestV1(userProfile)
    case ProjectsGetV1(userProfile) => projectsGetV1(userProfile)
    case ProjectInfoByIRIGetRequestV1(iri, userProfile) =>
      projectInfoByIRIGetRequestV1(iri, userProfile)
    case ProjectInfoByIRIGetV1(iri, userProfile) =>
      projectInfoByIRIGetV1(iri, userProfile)
    case ProjectInfoByShortnameGetRequestV1(shortname, userProfile) =>
      projectInfoByShortnameGetRequestV1(shortname, userProfile)
    case other => handleUnexpectedMessage(other, log, this.getClass.getName)
  }

  /**
   * Gets all the projects and returns them as a [[ProjectsResponseV1]].
   *
   * @param userProfile          the profile of the user that is making the request.
   * @return all the projects as a [[ProjectsResponseV1]].
   * @throws NotFoundException if no projects are found.
   */
  private def projectsGetRequestV1(
    userProfile: Option[UserProfileV1]
  ): Future[ProjectsResponseV1] =
    // log.debug("projectsGetRequestV1")
    for {
      projects <- projectsGetV1(
                    userProfile = userProfile
                  )

      result =
        if (projects.nonEmpty) {
          ProjectsResponseV1(
            projects = projects
          )
        } else {
          throw NotFoundException(s"No projects found")
        }

    } yield result

  /**
   * Gets all the projects and returns them as a sequence containing [[ProjectInfoV1]].
   *
   * @param userProfile          the profile of the user that is making the request.
   * @return all the projects as a sequence containing [[ProjectInfoV1]].
   */
  private def projectsGetV1(
    userProfile: Option[UserProfileV1]
  ): Future[Seq[ProjectInfoV1]] =
    for {
      sparqlQueryString <- Future(
                             org.knora.webapi.messages.twirl.queries.sparql.v1.txt
                               .getProjects()
                               .toString()
                           )
      // _ = log.debug(s"getProjectsResponseV1 - query: $sparqlQueryString")

      projectsResponse <- appActor.ask(SparqlSelectRequest(sparqlQueryString)).mapTo[SparqlSelectResult]
      // _ = log.debug(s"getProjectsResponseV1 - result: $projectsResponse")

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
      // _ = log.debug(s"getProjectsResponseV1 - projectsWithProperties: $projectsWithProperties")

      ontologiesForProjects <- getOntologiesForProjects(
                                 userProfile = userProfile
                               )

      projects = projectsWithProperties.map { case (projectIri: String, propsMap: Map[String, Seq[String]]) =>
                   val keywordsSeq: Seq[String] =
                     propsMap.getOrElse(OntologyConstants.KnoraAdmin.ProjectKeyword, Seq.empty[String]).sorted

                   val maybeKeywords: Option[String] = if (keywordsSeq.nonEmpty) {
                     Some(keywordsSeq.mkString(", "))
                   } else {
                     None
                   }

                   val ontologies = ontologiesForProjects.getOrElse(projectIri, Seq.empty[IRI])

                   ProjectInfoV1(
                     id = projectIri,
                     shortname = propsMap
                       .getOrElse(
                         OntologyConstants.KnoraAdmin.ProjectShortname,
                         throw InconsistentRepositoryDataException(s"Project: $projectIri has no shortname defined.")
                       )
                       .head,
                     shortcode = propsMap
                       .getOrElse(
                         OntologyConstants.KnoraAdmin.ProjectShortcode,
                         throw InconsistentRepositoryDataException(s"Project: $projectIri has no shortcode defined.")
                       )
                       .head,
                     longname = propsMap.get(OntologyConstants.KnoraAdmin.ProjectLongname).map(_.head),
                     description = propsMap.get(OntologyConstants.KnoraAdmin.ProjectDescription).map(_.head),
                     keywords = maybeKeywords,
                     logo = propsMap.get(OntologyConstants.KnoraAdmin.ProjectLogo).map(_.head),
                     institution = propsMap.get(OntologyConstants.KnoraAdmin.BelongsToInstitution).map(_.head),
                     ontologies = ontologies,
                     status = propsMap
                       .getOrElse(
                         OntologyConstants.KnoraAdmin.Status,
                         throw InconsistentRepositoryDataException(s"Project: $projectIri has no status defined.")
                       )
                       .head
                       .toBoolean,
                     selfjoin = propsMap
                       .getOrElse(
                         OntologyConstants.KnoraAdmin.HasSelfJoinEnabled,
                         throw InconsistentRepositoryDataException(
                           s"Project: $projectIri has no hasSelfJoinEnabled defined."
                         )
                       )
                       .head
                       .toBoolean
                   )
                 }.toSeq

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
  ): Future[Map[IRI, Seq[IRI]]] =
    for {
      // Get a UserADM for the UserProfileV1, because we need it to send a message to OntologyResponderV1.
      userADM: UserADM <- userProfile match {
                            case Some(profile) =>
                              profile.userData.user_id match {
                                case Some(user_iri) =>
                                  appActor
                                    .ask(
                                      UserGetRequestADM(
                                        identifier = UserIdentifierADM(maybeIri = Some(user_iri)),
                                        requestingUser = KnoraSystemInstances.Users.SystemUser
                                      )
                                    )
                                    .mapTo[UserResponseADM]
                                    .map(_.user)

                                case None => FastFuture.successful(KnoraSystemInstances.Users.AnonymousUser)
                              }

                            case None => FastFuture.successful(KnoraSystemInstances.Users.AnonymousUser)
                          }

      // Get the ontologies per project.

      namedGraphsResponse <- appActor
                               .ask(
                                 NamedGraphsGetRequestV1(
                                   projectIris = projectIris,
                                   userADM = userADM
                                 )
                               )
                               .mapTo[NamedGraphsResponseV1]

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
   *
   * @param userProfile          the profile of user that is making the request.
   * @return information about the project as a [[ProjectInfoResponseV1]].
   * @throws NotFoundException when no project for the given IRI can be found
   */
  private def projectInfoByIRIGetRequestV1(
    projectIri: IRI,
    userProfile: Option[UserProfileV1] = None
  ): Future[ProjectInfoResponseV1] =
    // log.debug("projectInfoByIRIGetRequestV1 - projectIRI: {}", projectIRI)
    for {
      maybeProjectInfo: Option[ProjectInfoV1] <- projectInfoByIRIGetV1(
                                                   projectIri = projectIri,
                                                   userProfile = userProfile
                                                 )

      projectInfo = maybeProjectInfo match {
                      case Some(pi) => pi
                      case None     => throw NotFoundException(s"Project '$projectIri' not found")
                    }
    } yield ProjectInfoResponseV1(
      project_info = projectInfo
    )

  /**
   * Gets the project with the given project IRI and returns the information as a [[ProjectInfoV1]].
   *
   * @param projectIri           the IRI of the project requested.
   *
   * @param userProfile          the profile of user that is making the request.
   * @return information about the project as a [[ProjectInfoV1]].
   */
  private def projectInfoByIRIGetV1(
    projectIri: IRI,
    userProfile: Option[UserProfileV1] = None
  ): Future[Option[ProjectInfoV1]] =
    // log.debug("projectInfoByIRIGetV1 - projectIRI: {}", projectIri)
    for {
      sparqlQuery <- Future(
                       org.knora.webapi.messages.twirl.queries.sparql.v1.txt
                         .getProjectByIri(
                           projectIri = projectIri
                         )
                         .toString()
                     )

      projectResponse <- appActor.ask(SparqlSelectRequest(sparqlQuery)).mapTo[SparqlSelectResult]

      ontologiesForProjects: Map[IRI, Seq[IRI]] <- getOntologiesForProjects(
                                                     projectIris = Set(projectIri),
                                                     userProfile = userProfile
                                                   )

      projectOntologies = ontologiesForProjects.getOrElse(projectIri, Seq.empty[IRI])

      projectInfo =
        if (projectResponse.results.bindings.nonEmpty) {
          Some(
            createProjectInfoV1(
              projectResponse = projectResponse.results.bindings,
              projectIri = projectIri,
              ontologies = projectOntologies,
              userProfile
            )
          )
        } else {
          None
        }

      // _ = log.debug("projectInfoByIRIGetV1 - projectInfo: {}", projectInfo)

    } yield projectInfo

  /**
   * Gets the project with the given shortname and returns the information as a [[ProjectInfoResponseV1]].
   *
   * @param shortName            the shortname of the project requested.
   *
   * @param userProfile          the profile of user that is making the request.
   * @return information about the project as a [[ProjectInfoResponseV1]].
   * @throws NotFoundException in the case that no project for the given shortname can be found.
   */
  private def projectInfoByShortnameGetRequestV1(
    shortName: String,
    userProfile: Option[UserProfileV1]
  ): Future[ProjectInfoResponseV1] =
    // log.debug("projectInfoByShortnameGetRequestV1 - shortName: {}", shortName)
    for {
      sparqlQueryString <- Future(
                             org.knora.webapi.messages.twirl.queries.sparql.v1.txt
                               .getProjectByShortname(
                                 shortname = shortName
                               )
                               .toString()
                           )
      // _ = log.debug(s"getProjectInfoByShortnameGetRequest - query: $sparqlQueryString")

      projectResponse <- appActor.ask(SparqlSelectRequest(sparqlQueryString)).mapTo[SparqlSelectResult]
      // _ = log.debug(s"getProjectInfoByShortnameGetRequest - result: $projectResponse")

      // get project IRI from results rows
      projectIri: IRI =
        if (projectResponse.results.bindings.nonEmpty) {
          projectResponse.results.bindings.head.rowMap("s")
        } else {
          throw NotFoundException(s"Project '$shortName' not found")
        }

      ontologiesForProjects: Map[IRI, Seq[IRI]] <- getOntologiesForProjects(
                                                     projectIris = Set(projectIri),
                                                     userProfile = userProfile
                                                   )

      projectOntologies = ontologiesForProjects(projectIri)

      projectInfo = createProjectInfoV1(
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
  ): ProjectInfoV1 =
    // log.debug("createProjectInfoV1 - projectResponse: {}", projectResponse)
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

      // log.debug(s"createProjectInfoV1 - projectProperties: $projectProperties")

      /* create and return the project info */
      ProjectInfoV1(
        id = projectIri,
        shortname = projectProperties
          .getOrElse(
            OntologyConstants.KnoraAdmin.ProjectShortname,
            throw InconsistentRepositoryDataException(s"Project: $projectIri has no shortname defined.")
          )
          .head,
        shortcode = projectProperties
          .getOrElse(
            OntologyConstants.KnoraAdmin.ProjectShortcode,
            throw InconsistentRepositoryDataException(s"Project: $projectIri has no shortcode defined.")
          )
          .head,
        longname = projectProperties.get(OntologyConstants.KnoraAdmin.ProjectLongname).map(_.head),
        description = projectProperties.get(OntologyConstants.KnoraAdmin.ProjectDescription).map(_.head),
        keywords = maybeKeywords,
        logo = projectProperties.get(OntologyConstants.KnoraAdmin.ProjectLogo).map(_.head),
        institution = projectProperties.get(OntologyConstants.KnoraAdmin.BelongsToInstitution).map(_.head),
        ontologies = ontologies,
        status = projectProperties
          .getOrElse(
            OntologyConstants.KnoraAdmin.Status,
            throw InconsistentRepositoryDataException(s"Project: $projectIri has no status defined.")
          )
          .head
          .toBoolean,
        selfjoin = projectProperties
          .getOrElse(
            OntologyConstants.KnoraAdmin.HasSelfJoinEnabled,
            throw InconsistentRepositoryDataException(s"Project: $projectIri has no hasSelfJoinEnabled defined.")
          )
          .head
          .toBoolean
      )

    } else {
      // no information was found for the given project IRI
      throw NotFoundException(s"For the given project IRI $projectIri no information was found")
    }

  /**
   * Helper method for checking if a project identified by IRI exists.
   *
   * @param projectIri the IRI of the project.
   * @return a [[Boolean]].
   */
  def projectByIriExists(projectIri: IRI): Future[Boolean] =
    for {
      askString <- Future(
                     org.knora.webapi.messages.twirl.queries.sparql.admin.txt
                       .checkProjectExistsByIri(projectIri = projectIri)
                       .toString
                   )
      // _ = log.debug("projectExists - query: {}", askString)

      checkProjectExistsResponse <- appActor.ask(SparqlAskRequest(askString)).mapTo[SparqlAskResponse]
      result                      = checkProjectExistsResponse.result

    } yield result

  /**
   * Helper method for checking if a project identified by shortname exists.
   *
   * @param shortname the shortname of the project.
   * @return a [[Boolean]].
   */
  def projectByShortnameExists(shortname: String): Future[Boolean] =
    for {
      askString <- Future(
                     org.knora.webapi.messages.twirl.queries.sparql.admin.txt
                       .checkProjectExistsByShortname(shortname = shortname)
                       .toString
                   )
      // _ = log.debug("projectExists - query: {}", askString)

      checkProjectExistsResponse <- appActor.ask(SparqlAskRequest(askString)).mapTo[SparqlAskResponse]
      result                      = checkProjectExistsResponse.result

    } yield result

  /**
   * Helper method for checking if a project identified by shortcode exists.
   *
   * @param shortcode the shortcode of the project.
   * @return a [[Boolean]].
   */
  def projectByShortcodeExists(shortcode: String): Future[Boolean] =
    for {
      askString <- Future(
                     org.knora.webapi.messages.twirl.queries.sparql.admin.txt
                       .checkProjectExistsByShortcode(shortcode = shortcode)
                       .toString
                   )
      // _ = log.debug("projectExists - query: {}", askString)

      checkProjectExistsResponse <- appActor.ask(SparqlAskRequest(askString)).mapTo[SparqlAskResponse]
      result                      = checkProjectExistsResponse.result

    } yield result
}
