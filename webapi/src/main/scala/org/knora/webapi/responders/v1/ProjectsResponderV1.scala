/*
 * Copyright © 2015-2018 the contributors (see Contributors.md).
 *
 * This file is part of Knora.
 *
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.responders.v1

import akka.actor.Status
import akka.pattern._
import org.knora.webapi._
import org.knora.webapi.messages.store.triplestoremessages._
import org.knora.webapi.messages.v1.responder.ontologymessages.NamedGraphV1
import org.knora.webapi.messages.v1.responder.projectmessages._
import org.knora.webapi.messages.v1.responder.usermessages._
import org.knora.webapi.responders.Responder
import org.knora.webapi.util.ActorUtil._
import org.knora.webapi.util.KnoraIdUtil

import scala.concurrent.Future

/**
  * Returns information about Knora projects.
  */
class ProjectsResponderV1 extends Responder {

    // Creates IRIs for new Knora user objects.
    val knoraIdUtil = new KnoraIdUtil

    // Global lock IRI used for project creation and update
    val PROJECTS_GLOBAL_LOCK_IRI = "http://data.knora.org/projects"

    /**
      * Receives a message extending [[org.knora.webapi.messages.v1.responder.projectmessages.ProjectsResponderRequestV1]], and returns an appropriate response message, or
      * [[Status.Failure]]. If a serious error occurs (i.e. an error that isn't the client's fault), this
      * method first returns `Failure` to the sender, then throws an exception.
      */
    def receive: PartialFunction[Any, Unit] = {
        case ProjectsGetRequestV1(userProfile) => future2Message(sender(), projectsGetRequestV1(userProfile), log)
        case ProjectsGetV1(userProfile) => future2Message(sender(), projectsGetV1(userProfile), log)
        case ProjectInfoByIRIGetRequestV1(iri, userProfile) => future2Message(sender(), projectInfoByIRIGetRequestV1(iri, userProfile), log)
        case ProjectInfoByIRIGetV1(iri, userProfile) => future2Message(sender(), projectInfoByIRIGetV1(iri, userProfile), log)
        case ProjectInfoByShortnameGetRequestV1(shortname, userProfile) => future2Message(sender(), projectInfoByShortnameGetRequestV1(shortname, userProfile), log)
        case ProjectMembersByIRIGetRequestV1(iri, userProfileV1) => future2Message(sender(), projectMembersByIRIGetRequestV1(iri, userProfileV1), log)
        case ProjectMembersByShortnameGetRequestV1(shortname, userProfileV1) => future2Message(sender(), projectMembersByShortnameGetRequestV1(shortname, userProfileV1), log)
        case ProjectAdminMembersByIRIGetRequestV1(iri, userProfileV1) => future2Message(sender(), projectAdminMembersByIRIGetRequestV1(iri, userProfileV1), log)
        case ProjectAdminMembersByShortnameGetRequestV1(shortname, userProfileV1) => future2Message(sender(), projectAdminMembersByShortnameGetRequestV1(shortname, userProfileV1), log)
        case other => handleUnexpectedMessage(sender(), other, log, this.getClass.getName)
    }

    /**
      * Gets all the projects and returns them as a [[ProjectsResponseV1]].
      *
      * @param userProfile the profile of the user that is making the request.
      * @return all the projects as a [[ProjectsResponseV1]].
      * @throws NotFoundException if no projects are found.
      */
    private def projectsGetRequestV1(userProfile: Option[UserProfileV1]): Future[ProjectsResponseV1] = {

        //log.debug("projectsGetRequestV1")

        for {
            projects <- projectsGetV1(userProfile)

            result = if (projects.nonEmpty) {
                ProjectsResponseV1(
                    projects = projects
                )
            } else {
                throw NotFoundException(s"No projects found")
            }

        } yield result
    }

    /**
      * Gets all the projects and returns them as a sequence containing [[ProjectInfoV1]].
      *
      * @param userProfile the profile of the user that is making the request.
      * @return all the projects as a sequence containing [[ProjectInfoV1]].
      */
    private def projectsGetV1(userProfile: Option[UserProfileV1]): Future[Seq[ProjectInfoV1]] = {

        for {
            sparqlQueryString <- Future(queries.sparql.v1.txt.getProjects(
                triplestore = settings.triplestoreType
            ).toString())
            //_ = log.debug(s"getProjectsResponseV1 - query: $sparqlQueryString")

            projectsResponse <- (storeManager ? SparqlSelectRequest(sparqlQueryString)).mapTo[SparqlSelectResponse]
            //_ = log.debug(s"getProjectsResponseV1 - result: $projectsResponse")

            projectsResponseRows: Seq[VariableResultsRow] = projectsResponse.results.bindings

            projectsWithProperties: Map[IRI, Map[IRI, Seq[String]]] = projectsResponseRows.groupBy(_.rowMap("s")).map {
                case (projIri: String, rows: Seq[VariableResultsRow]) => (projIri, rows.groupBy(_.rowMap("p")).map {
                    case (predicate: IRI, literals: Seq[VariableResultsRow]) => predicate -> literals.map(_.rowMap("o"))
                })
            }
            //_ = log.debug(s"getProjectsResponseV1 - projectsWithProperties: $projectsWithProperties")

            projects = projectsWithProperties.map {
                case (projectIri: String, propsMap: Map[String, Seq[String]]) =>

                    val keywordsSeq: Seq[String] = propsMap.getOrElse(OntologyConstants.KnoraBase.ProjectKeyword, Seq.empty[String]).sorted

                    val maybeKeywords: Option[String] = if (keywordsSeq.nonEmpty) {
                        Some(keywordsSeq.mkString(", "))
                    } else {
                        None
                    }

                    ProjectInfoV1(
                        id = projectIri,
                        shortname = propsMap.getOrElse(OntologyConstants.KnoraBase.ProjectShortname, throw InconsistentTriplestoreDataException(s"Project: $projectIri has no shortname defined.")).head,
                        shortcode = propsMap.get(OntologyConstants.KnoraBase.ProjectShortcode).map(_.head),
                        longname = propsMap.get(OntologyConstants.KnoraBase.ProjectLongname).map(_.head),
                        description = propsMap.get(OntologyConstants.KnoraBase.ProjectDescription).map(_.head),
                        keywords = maybeKeywords,
                        logo = propsMap.get(OntologyConstants.KnoraBase.ProjectLogo).map(_.head),
                        institution = propsMap.get(OntologyConstants.KnoraBase.BelongsToInstitution).map(_.head),
                        status = propsMap.getOrElse(OntologyConstants.KnoraBase.Status, throw InconsistentTriplestoreDataException(s"Project: $projectIri has no status defined.")).head.toBoolean,
                        selfjoin = propsMap.getOrElse(OntologyConstants.KnoraBase.HasSelfJoinEnabled, throw InconsistentTriplestoreDataException(s"Project: $projectIri has no hasSelfJoinEnabled defined.")).head.toBoolean
                    )
            }.toSeq

        } yield projects
    }

    /**
      * Gets the project with the given project IRI and returns the information as a [[ProjectInfoResponseV1]].
      *
      * @param projectIri  the IRI of the project requested.
      * @param userProfile the profile of user that is making the request.
      * @return information about the project as a [[ProjectInfoResponseV1]].
      * @throws NotFoundException when no project for the given IRI can be found
      */
    private def projectInfoByIRIGetRequestV1(projectIri: IRI, userProfile: Option[UserProfileV1] = None): Future[ProjectInfoResponseV1] = {

        //log.debug("projectInfoByIRIGetRequestV1 - projectIRI: {}", projectIRI)

        for {
            maybeProjectInfo: Option[ProjectInfoV1] <- projectInfoByIRIGetV1(projectIri, userProfile)
            projectInfo = maybeProjectInfo match {
                case Some(pi) => pi
                case None => throw NotFoundException(s"Project '$projectIri' not found")
            }
        } yield ProjectInfoResponseV1(
            project_info = projectInfo
        )
    }

    /**
      * Gets the project with the given project IRI and returns the information as a [[ProjectInfoV1]].
      *
      * @param projectIri  the IRI of the project requested.
      * @param userProfile the profile of user that is making the request.
      * @return information about the project as a [[ProjectInfoV1]].
      */
    private def projectInfoByIRIGetV1(projectIri: IRI, userProfile: Option[UserProfileV1] = None): Future[Option[ProjectInfoV1]] = {

        //log.debug("projectInfoByIRIGetV1 - projectIRI: {}", projectIri)

        for {
            sparqlQuery <- Future(queries.sparql.v1.txt.getProjectByIri(
                triplestore = settings.triplestoreType,
                projectIri = projectIri
            ).toString())
            projectResponse <- (storeManager ? SparqlSelectRequest(sparqlQuery)).mapTo[SparqlSelectResponse]

            projectInfo = if (projectResponse.results.bindings.nonEmpty) {
                Some(createProjectInfoV1(projectResponse = projectResponse.results.bindings, projectIri = projectIri, userProfile))
            } else {
                None
            }

            //_ = log.debug("projectInfoByIRIGetV1 - projectInfo: {}", projectInfo)

        } yield projectInfo
    }

    /**
      * Gets the project with the given shortname and returns the information as a [[ProjectInfoResponseV1]].
      *
      * @param shortName   the shortname of the project requested.
      * @param userProfile the profile of user that is making the request.
      * @return information about the project as a [[ProjectInfoResponseV1]].
      * @throws NotFoundException in the case that no project for the given shortname can be found.
      */
    private def projectInfoByShortnameGetRequestV1(shortName: String, userProfile: Option[UserProfileV1]): Future[ProjectInfoResponseV1] = {

        //log.debug("projectInfoByShortnameGetRequestV1 - shortName: {}", shortName)

        for {
            sparqlQueryString <- Future(queries.sparql.v1.txt.getProjectByShortname(
                triplestore = settings.triplestoreType,
                shortname = shortName
            ).toString())
            //_ = log.debug(s"getProjectInfoByShortnameGetRequest - query: $sparqlQueryString")

            projectResponse <- (storeManager ? SparqlSelectRequest(sparqlQueryString)).mapTo[SparqlSelectResponse]
            //_ = log.debug(s"getProjectInfoByShortnameGetRequest - result: $projectResponse")


            // get project IRI from results rows
            projectIri: IRI = if (projectResponse.results.bindings.nonEmpty) {
                projectResponse.results.bindings.head.rowMap("s")
            } else {
                throw NotFoundException(s"Project '$shortName' not found")
            }

            projectInfo = createProjectInfoV1(projectResponse = projectResponse.results.bindings, projectIri = projectIri, userProfile)

        } yield ProjectInfoResponseV1(
            project_info = projectInfo
        )
    }

    /**
      * Gets the project that contains the specified ontology, and returns its information as a [[ProjectInfoResponseV1]].
      *
      * @param ontologyIri the ontology IRI.
      * @param userProfile the profile of user that is making the request.
      * @return information about the project as a [[ProjectInfoResponseV1]].
      * @throws NotFoundException in the case that no project for the given ontology can be found.
      */
    private def projectInfoByOntologyGetRequestV1(ontologyIri: IRI, userProfile: Option[UserProfileV1]): Future[ProjectInfoResponseV1] = {
        for {
            sparqlQueryString <- Future(queries.sparql.v1.txt.getProjectByOntology(
                triplestore = settings.triplestoreType,
                ontologyIri = ontologyIri
            ).toString())

            projectResponse <- (storeManager ? SparqlSelectRequest(sparqlQueryString)).mapTo[SparqlSelectResponse]

            // get project IRI from results rows
            projectIri: IRI = if (projectResponse.results.bindings.nonEmpty) {
                projectResponse.results.bindings.head.rowMap("s")
            } else {
                throw NotFoundException(s"No project found with ontology $ontologyIri")
            }

            projectInfo = createProjectInfoV1(projectResponse = projectResponse.results.bindings, projectIri = projectIri, userProfile)

        } yield ProjectInfoResponseV1(
            project_info = projectInfo
        )
    }

    /**
      * Gets the members of a project with the given IRI.
      *
      * @param projectIri    the IRI of the project.
      * @param userProfileV1 the profile of the user that is making the request.
      * @return the members of a project as a [[ProjectMembersGetResponseV1]]
      * @throws NotFoundException in the case that the project's IRI is not found.
      */
    private def projectMembersByIRIGetRequestV1(projectIri: IRI, userProfileV1: UserProfileV1): Future[ProjectMembersGetResponseV1] = {

        //log.debug("projectMembersByIRIGetRequestV1 - projectIRI: {}", projectIri)

        for {
            projectExists: Boolean <- projectByIriExists(projectIri)

            _ = if (!projectExists) {
                throw NotFoundException(s"Project '$projectIri' not found.")
            }

            sparqlQueryString <- Future(queries.sparql.v1.txt.getProjectMembersByIri(
                triplestore = settings.triplestoreType,
                projectIri = projectIri
            ).toString())
            //_ = log.debug(s"projectMembersByIRIGetRequestV1 - query: $sparqlQueryString")

            projectMembersResponse <- (storeManager ? SparqlSelectRequest(sparqlQueryString)).mapTo[SparqlSelectResponse]
            //_ = log.debug(s"projectMembersByIRIGetRequestV1 - result: ${MessageUtil.toSource(projectMembersResponse)}")

            // get project member IRI from results rows
            projectMemberIris: Seq[IRI] = if (projectMembersResponse.results.bindings.nonEmpty) {
                projectMembersResponse.results.bindings.map(_.rowMap("s"))
            } else {
                Seq.empty[IRI]
            }
            //_ = log.debug(s"projectMembersByIRIGetRequestV1 - projectMemberIris: $projectMemberIris")

            response <- createProjectMembersGetResponse(projectMemberIris, userProfileV1)

        } yield response
    }


    /**
      * Gets the members of a project with the given shortname.
      *
      * @param shortname     the IRI of the project.
      * @param userProfileV1 the profile of the user that is making the request.
      * @return the members of a project as a [[ProjectMembersGetResponseV1]]
      * @throws NotFoundException in the case that the project with the given shortname can not be found.
      */
    private def projectMembersByShortnameGetRequestV1(shortname: String, userProfileV1: UserProfileV1): Future[ProjectMembersGetResponseV1] = {

        //log.debug("projectMembersByShortnameGetRequestV1 - shortname: {}", shortname)

        for {
            projectExists: Boolean <- projectByShortnameExists(shortname)

            _ = if (!projectExists) {
                throw NotFoundException(s"Project '$shortname' not found.")
            }

            sparqlQueryString <- Future(queries.sparql.v1.txt.getProjectMembersByShortname(
                triplestore = settings.triplestoreType,
                shortname = shortname
            ).toString())
            //_ = log.debug(s"projectMembersByShortnameGetRequestV1 - query: $sparqlQueryString")

            projectMembersResponse <- (storeManager ? SparqlSelectRequest(sparqlQueryString)).mapTo[SparqlSelectResponse]
            //_ = log.debug(s"projectMembersByShortnameGetRequestV1 - result: ${MessageUtil.toSource(projectMembersResponse)}")


            // get project member IRI from results rows
            projectMemberIris: Seq[IRI] = if (projectMembersResponse.results.bindings.nonEmpty) {
                projectMembersResponse.results.bindings.map(_.rowMap("s"))
            } else {
                Seq.empty[IRI]
            }
            //_ = log.debug(s"projectMembersByShortnameGetRequestV1 - projectMemberIris: $projectMemberIris")

            response <- createProjectMembersGetResponse(projectMemberIris, userProfileV1)

        } yield response
    }

    /**
      * Gets the admin members of a project with the given IRI.
      *
      * @param projectIri    the IRI of the project.
      * @param userProfileV1 the profile of the user that is making the request.
      * @return the members of a project as a [[ProjectMembersGetResponseV1]]
      * @throws NotFoundException in the case that the project's IRI is not found.
      */
    private def projectAdminMembersByIRIGetRequestV1(projectIri: IRI, userProfileV1: UserProfileV1): Future[ProjectAdminMembersGetResponseV1] = {

        //log.debug("projectAdminMembersByIRIGetRequestV1 - projectIRI: {}", projectIri)

        for {
            projectExists: Boolean <- projectByIriExists(projectIri)

            _ = if (!projectExists) {
                throw NotFoundException(s"Project '$projectIri' not found.")
            }

            sparqlQueryString <- Future(queries.sparql.v1.txt.getProjectAdminMembersByIri(
                triplestore = settings.triplestoreType,
                projectIri = projectIri
            ).toString())
            //_ = log.debug(s"projectAdminMembersByIRIGetRequestV1 - query: $sparqlQueryString")

            projectMembersResponse <- (storeManager ? SparqlSelectRequest(sparqlQueryString)).mapTo[SparqlSelectResponse]
            //_ = log.debug(s"projectAdminMembersByIRIGetRequestV1 - result: ${MessageUtil.toSource(projectMembersResponse)}")

            // get project member IRI from results rows
            projectMemberIris: Seq[IRI] = if (projectMembersResponse.results.bindings.nonEmpty) {
                projectMembersResponse.results.bindings.map(_.rowMap("s"))
            } else {
                Seq.empty[IRI]
            }
            //_ = log.debug(s"projectAdminMembersByIRIGetRequestV1 - projectMemberIris: $projectMemberIris")

            response <- createProjectAdminMembersGetResponse(projectMemberIris, userProfileV1)

        } yield response
    }


    /**
      * Gets the admin members of a project with the given shortname.
      *
      * @param shortname     the IRI of the project.
      * @param userProfileV1 the profile of the user that is making the request.
      * @return the members of a project as a [[ProjectMembersGetResponseV1]]
      * @throws NotFoundException in the case that the project with the given shortname can not be found.
      */
    private def projectAdminMembersByShortnameGetRequestV1(shortname: String, userProfileV1: UserProfileV1): Future[ProjectAdminMembersGetResponseV1] = {

        //log.debug("projectAdminMembersByShortnameGetRequestV1 - shortname: {}", shortname)

        for {
            projectExists: Boolean <- projectByShortnameExists(shortname)

            _ = if (!projectExists) {
                throw NotFoundException(s"Project '$shortname' not found.")
            }

            sparqlQueryString <- Future(queries.sparql.v1.txt.getProjectAdminMembersByShortname(
                triplestore = settings.triplestoreType,
                shortname = shortname
            ).toString())
            //_ = log.debug(s"projectAdminMembersByShortnameGetRequestV1 - query: $sparqlQueryString")

            projectMembersResponse <- (storeManager ? SparqlSelectRequest(sparqlQueryString)).mapTo[SparqlSelectResponse]
            //_ = log.debug(s"projectAdminMembersByShortnameGetRequestV1 - result: ${MessageUtil.toSource(projectMembersResponse)}")


            // get project member IRI from results rows
            projectMemberIris: Seq[IRI] = if (projectMembersResponse.results.bindings.nonEmpty) {
                projectMembersResponse.results.bindings.map(_.rowMap("s"))
            } else {
                Seq.empty[IRI]
            }
            //_ = log.debug(s"projectAdminMembersByShortnameGetRequestV1 - projectMemberIris: $projectMemberIris")

            response <- createProjectAdminMembersGetResponse(projectMemberIris, userProfileV1)

        } yield response
    }

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
    private def createProjectInfoV1(projectResponse: Seq[VariableResultsRow], projectIri: IRI, userProfile: Option[UserProfileV1]): ProjectInfoV1 = {

        //log.debug("createProjectInfoV1 - projectResponse: {}", projectResponse)

        if (projectResponse.nonEmpty) {

            val projectProperties: Map[String, Seq[String]] = projectResponse.groupBy(_.rowMap("p")).map {
                case (predicate, rows) => predicate -> rows.map(_.rowMap("o"))
            }

            val keywordsSeq: Seq[String] = projectProperties.getOrElse(OntologyConstants.KnoraBase.ProjectKeyword, Seq.empty[String]).sorted

            val maybeKeywords: Option[String] = if (keywordsSeq.nonEmpty) {
                Some(keywordsSeq.mkString(", "))
            } else {
                None
            }

            // log.debug(s"createProjectInfoV1 - projectProperties: $projectProperties")

            /* create and return the project info */
            ProjectInfoV1(
                id = projectIri,
                shortname = projectProperties.getOrElse(OntologyConstants.KnoraBase.ProjectShortname, throw InconsistentTriplestoreDataException(s"Project: $projectIri has no shortname defined.")).head,
                shortcode = projectProperties.get(OntologyConstants.KnoraBase.ProjectShortcode).map(_.head),
                longname = projectProperties.get(OntologyConstants.KnoraBase.ProjectLongname).map(_.head),
                description = projectProperties.get(OntologyConstants.KnoraBase.ProjectDescription).map(_.head),
                keywords = maybeKeywords,
                logo = projectProperties.get(OntologyConstants.KnoraBase.ProjectLogo).map(_.head),
                institution = projectProperties.get(OntologyConstants.KnoraBase.BelongsToInstitution).map(_.head),
                status = projectProperties.getOrElse(OntologyConstants.KnoraBase.Status, throw InconsistentTriplestoreDataException(s"Project: $projectIri has no status defined.")).head.toBoolean,
                selfjoin = projectProperties.getOrElse(OntologyConstants.KnoraBase.HasSelfJoinEnabled, throw InconsistentTriplestoreDataException(s"Project: $projectIri has no hasSelfJoinEnabled defined.")).head.toBoolean
            )

        } else {
            // no information was found for the given project IRI
            throw NotFoundException(s"For the given project IRI $projectIri no information was found")
        }

    }

    /**
      * Helper method that turns a sequence of user IRIs into a [[ProjectMembersGetResponseV1]]
      *
      * @param memberIris  the user IRIs
      * @param userProfile the profile of the user that is making the request
      * @return a [[ProjectMembersGetResponseV1]]
      */
    private def createProjectMembersGetResponse(memberIris: Seq[IRI], userProfile: UserProfileV1): Future[ProjectMembersGetResponseV1] = {

        def getUserData(userIri: IRI): Future[UserDataV1] = {
            for {
                userProfile <- (responderManager ? UserDataByIriGetV1(userIri)).mapTo[Option[UserDataV1]]

                result = userProfile match {
                    case Some(ud) => ud
                    case None => throw InconsistentTriplestoreDataException(s"User $userIri was not found but is member of project. Please report this as a possible bug.")
                }
            } yield result
        }

        val userDatasFuture: Future[Seq[Future[UserDataV1]]] = for {
            memberIris: Seq[IRI] <- Future(memberIris)
            userDatas: Seq[Future[UserDataV1]] = memberIris.map(userIri => getUserData(userIri))
        } yield userDatas

        for {
            userDatas <- userDatasFuture
            result: Seq[UserDataV1] <- Future.sequence(userDatas)
        } yield ProjectMembersGetResponseV1(result, userProfile.ofType(UserProfileTypeV1.SHORT).userData)

    }

    /**
      * Helper method that turns a sequence of user IRIs into a [[ProjectAdminMembersGetResponseV1]]
      *
      * @param memberIris  the user IRIs
      * @param userProfile the profile of the user that is making the request
      * @return a [[ProjectAdminMembersGetResponseV1]]
      */
    private def createProjectAdminMembersGetResponse(memberIris: Seq[IRI], userProfile: UserProfileV1): Future[ProjectAdminMembersGetResponseV1] = {

        def getUserData(userIri: IRI): Future[UserDataV1] = {
            for {
                userProfile <- (responderManager ? UserDataByIriGetV1(userIri)).mapTo[Option[UserDataV1]]

                result = userProfile match {
                    case Some(ud) => ud
                    case None => throw InconsistentTriplestoreDataException(s"User $userIri was not found but is member of project. Please report this as a possible bug.")
                }
            } yield result
        }

        val userDatasFuture: Future[Seq[Future[UserDataV1]]] = for {
            memberIris: Seq[IRI] <- Future(memberIris)
            userDatas: Seq[Future[UserDataV1]] = memberIris.map(userIri => getUserData(userIri))
        } yield userDatas

        for {
            userDatas <- userDatasFuture
            result: Seq[UserDataV1] <- Future.sequence(userDatas)
        } yield ProjectAdminMembersGetResponseV1(result, userProfile.ofType(UserProfileTypeV1.SHORT).userData)
    }

    /**
      * Helper method for checking if a project identified by IRI exists.
      *
      * @param projectIri the IRI of the project.
      * @return a [[Boolean]].
      */
    def projectByIriExists(projectIri: IRI): Future[Boolean] = {
        for {
            askString <- Future(queries.sparql.admin.txt.checkProjectExistsByIri(projectIri = projectIri).toString)
            //_ = log.debug("projectExists - query: {}", askString)

            checkProjectExistsResponse <- (storeManager ? SparqlAskRequest(askString)).mapTo[SparqlAskResponse]
            result = checkProjectExistsResponse.result

        } yield result
    }

    /**
      * Helper method for checking if a project identified by shortname exists.
      *
      * @param shortname the shortname of the project.
      * @return a [[Boolean]].
      */
    def projectByShortnameExists(shortname: String): Future[Boolean] = {
        for {
            askString <- Future(queries.sparql.admin.txt.checkProjectExistsByShortname(shortname = shortname).toString)
            //_ = log.debug("projectExists - query: {}", askString)

            checkProjectExistsResponse <- (storeManager ? SparqlAskRequest(askString)).mapTo[SparqlAskResponse]
            result = checkProjectExistsResponse.result

        } yield result
    }

    /**
      * Helper method for checking if a project identified by shortcode exists.
      *
      * @param shortcode the shortcode of the project.
      * @return a [[Boolean]].
      */
    def projectByShortcodeExists(shortcode: String): Future[Boolean] = {
        for {
            askString <- Future(queries.sparql.admin.txt.checkProjectExistsByShortcode(shortcode = shortcode).toString)
            //_ = log.debug("projectExists - query: {}", askString)

            checkProjectExistsResponse <- (storeManager ? SparqlAskRequest(askString)).mapTo[SparqlAskResponse]
            result = checkProjectExistsResponse.result

        } yield result
    }
}
