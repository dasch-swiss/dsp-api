/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and André Fatton.
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

import java.util.UUID

import akka.actor.Status
import akka.pattern._
import org.apache.jena.sparql.function.library.leviathan.log
import org.knora.webapi._
import org.knora.webapi.messages.v1.responder.ontologymessages.NamedGraphV1
import org.knora.webapi.messages.v1.responder.projectmessages._
import org.knora.webapi.messages.v1.responder.usermessages.{UserDataV1, UserProfileByIRIGetRequestV1, UserProfileType, UserProfileV1}
import org.knora.webapi.messages.v1.store.triplestoremessages._
import org.knora.webapi.responders.IriLocker
import org.knora.webapi.util.ActorUtil._
import org.knora.webapi.util.{KnoraIdUtil, MessageUtil, PermissionUtilV1}

import scala.concurrent.Future

/**
  * Returns information about Knora projects.
  */
class ProjectsResponderV1 extends ResponderV1 {

    // Creates IRIs for new Knora user objects.
    val knoraIdUtil = new KnoraIdUtil

    // Global lock IRI used for project creation and update
    val PROJECTS_GLOBAL_LOCK_IRI = "http://data.knora.org/projects"

    /**
      * Receives a message extending [[org.knora.webapi.messages.v1.responder.projectmessages.ProjectsResponderRequestV1]], and returns an appropriate response message, or
      * [[Status.Failure]]. If a serious error occurs (i.e. an error that isn't the client's fault), this
      * method first returns `Failure` to the sender, then throws an exception.
      */
    def receive = {
        case ProjectsGetRequestV1(userProfile) => future2Message(sender(), projectsGetRequestV1(userProfile), log)
        case ProjectsGetV1(userProfile) => future2Message(sender(), projectsGetV1(userProfile), log)
        case ProjectsNamedGraphGetV1(userProfile) => future2Message(sender(), projectsNamedGraphGetV1(userProfile), log)
        case ProjectInfoByIRIGetRequestV1(iri, userProfile) => future2Message(sender(), projectInfoByIRIGetRequestV1(iri, userProfile), log)
        case ProjectInfoByIRIGetV1(iri, userProfile) => future2Message(sender(), projectInfoByIRIGetV1(iri, userProfile), log)
        case ProjectInfoByShortnameGetRequestV1(shortname, userProfile) => future2Message(sender(), projectInfoByShortnameGetRequestV1(shortname, userProfile), log)
        case ProjectMembersByIRIGetRequestV1(iri, userProfileV1) => future2Message(sender(), projectMembersByIRIGetRequestV1(iri, userProfileV1), log)
        case ProjectMembersByShortnameGetRequestV1(shortname, userProfileV1) => future2Message(sender(), projectMembersByShortnameGetRequestV1(shortname, userProfileV1), log)
        case ProjectCreateRequestV1(createRequest: CreateProjectApiRequestV1, userProfileV1, apiRequestID) => future2Message(sender(), projectCreateRequestV1(createRequest, userProfileV1, apiRequestID), log)
        case other => handleUnexpectedMessage(sender(), other, log, this.getClass.getName)
    }

    /**
      * Gets permissions for the current user on the given project.
      *
      * @param projectIRI           the Iri of the project.
      * @param propertiesForProject assertions containing permissions on the project.
      * @param userProfile          the user that is making the request.
      * @return permission level of the current user on the project.
      */
    private def getUserPermissionV1ForProject(projectIRI: IRI, propertiesForProject: Map[IRI, String], userProfile: UserProfileV1): Option[Int] = {

        // propertiesForProject must contain an owner for the project (knora-base:attachedToUser).
        propertiesForProject.get(OntologyConstants.KnoraBase.AttachedToUser) match {
            case Some(user) => // add statement that `PermissionUtil.getUserPermissionV1` requires but is not present in the data for projects.
                val assertionsForProject: Seq[(IRI, IRI)] = (OntologyConstants.KnoraBase.AttachedToProject, projectIRI) +: propertiesForProject.toVector
                PermissionUtilV1.getUserPermissionV1FromAssertions(
                    subjectIri = projectIRI,
                    assertions = assertionsForProject,
                    userProfile = userProfile
                )
            case None => None // TODO: this is temporary to prevent PermissionUtil.getUserPermissionV1 from failing because owner id is missing in the data for project. See issue 1.
        }
    }

    /**
      * Gets all the projects and returns them as a [[ProjectsResponseV1]].
      *
      * @param userProfile the profile of the user that is making the request.
      * @return all the projects as a [[ProjectsResponseV1]].
      */
    private def projectsGetRequestV1(userProfile: Option[UserProfileV1]): Future[ProjectsResponseV1] = {

        for {
            projects <- projectsGetV1(userProfile)

            result = if (projects.nonEmpty) {
                ProjectsResponseV1(
                    projects = projects,
                    userdata = userProfile match {
                        case Some(profile) => Some(profile.userData)
                        case None => None
                    }
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
            //_ = log.debug(s"getProjectsResponseV1 - result: ${MessageUtil.toSource(projectsResponse)}")

            projectsResponseRows: Seq[VariableResultsRow] = projectsResponse.results.bindings

            projectsWithProperties: Map[String, Map[String, String]] = projectsResponseRows.groupBy(_.rowMap("s")).map {
                case (projIri: String, rows: Seq[VariableResultsRow]) => (projIri, rows.map(row => (row.rowMap("p"), row.rowMap("o"))).toMap)
            }
            //_ = log.debug(s"getProjectsResponseV1 - projectsWithProperties: ${MessageUtil.toSource(projectsWithProperties)}")

            projects = projectsWithProperties.map {
                case (projectIri: String, propsMap: Map[String, String]) =>

                    ProjectInfoV1(
                        id = projectIri,
                        shortname = propsMap.getOrElse(OntologyConstants.KnoraBase.ProjectShortname, throw InconsistentTriplestoreDataException(s"Project: $projectIri has no shortname defined.")),
                        longname = propsMap.get(OntologyConstants.KnoraBase.ProjectLongname),
                        description = propsMap.get(OntologyConstants.KnoraBase.ProjectDescription),
                        keywords = propsMap.get(OntologyConstants.KnoraBase.ProjectKeywords),
                        logo = propsMap.get(OntologyConstants.KnoraBase.ProjectLogo),
                        belongsToInstitution = propsMap.get(OntologyConstants.KnoraBase.BelongsToProject),
                        basepath = propsMap.getOrElse(OntologyConstants.KnoraBase.ProjectBasepath, throw InconsistentTriplestoreDataException(s"Project: $projectIri has no basepath defined.")),
                        ontologyNamedGraph = propsMap.getOrElse(OntologyConstants.KnoraBase.ProjectOntologyGraph, throw InconsistentTriplestoreDataException(s"Project: $projectIri has no projectOntologyGraph defined.")),
                        dataNamedGraph = propsMap.getOrElse(OntologyConstants.KnoraBase.ProjectDataGraph, throw InconsistentTriplestoreDataException(s"Project: $projectIri has no projectDataGraph defined.")),
                        status = propsMap.getOrElse(OntologyConstants.KnoraBase.Status, throw InconsistentTriplestoreDataException(s"Project: $projectIri has no status defined.")).toBoolean,
                        hasSelfJoinEnabled = propsMap.getOrElse(OntologyConstants.KnoraBase.HasSelfJoinEnabled, throw InconsistentTriplestoreDataException(s"Project: $projectIri has no hasSelfJoinEnabled defined.")).toBoolean
                    )
            }.toSeq

        } yield projects
    }

    /**
      * Gets all the named graphs from all projects and returns them as a sequence of [[NamedGraphV1]]
      * @param userProfile
      * @return a sequence of [[NamedGraphV1]]
      */
    private def projectsNamedGraphGetV1(userProfile: UserProfileV1): Future[Seq[NamedGraphV1]] = {

        for {
            sparqlQueryString <- Future(queries.sparql.v1.txt.getProjects(
                triplestore = settings.triplestoreType
            ).toString())
            //_ = log.debug(s"getProjectsResponseV1 - query: $sparqlQueryString")

            projectsResponse <- (storeManager ? SparqlSelectRequest(sparqlQueryString)).mapTo[SparqlSelectResponse]
            //_ = log.debug(s"getProjectsResponseV1 - result: ${MessageUtil.toSource(projectsResponse)}")

            projectsResponseRows: Seq[VariableResultsRow] = projectsResponse.results.bindings

            projectsWithProperties: Map[String, Map[String, String]] = projectsResponseRows.groupBy(_.rowMap("s")).map {
                case (projIri: String, rows: Seq[VariableResultsRow]) => (projIri, rows.map(row => (row.rowMap("p"), row.rowMap("o"))).toMap)
            }
            //_ = log.debug(s"getProjectsResponseV1 - projectsWithProperties: ${MessageUtil.toSource(projectsWithProperties)}")

            namedGraphs: Seq[NamedGraphV1] = projectsWithProperties.map {
                case (projectIri: String, propsMap: Map[String, String]) =>

                    NamedGraphV1(
                        id = propsMap.getOrElse(OntologyConstants.KnoraBase.ProjectOntologyGraph, throw InconsistentTriplestoreDataException(s"Project: $projectIri has no projectOntologyGraph defined.")),
                        shortname = propsMap.getOrElse(OntologyConstants.KnoraBase.ProjectShortname, throw InconsistentTriplestoreDataException(s"Project: $projectIri has no basepath defined.")),
                        longname = propsMap.getOrElse(OntologyConstants.KnoraBase.ProjectLongname, throw InconsistentTriplestoreDataException(s"Project: $projectIri has no longname defined.")),
                        description = propsMap.getOrElse(OntologyConstants.KnoraBase.ProjectDescription, throw InconsistentTriplestoreDataException(s"Project: $projectIri has no description defined.")),
                        project_id = projectIri,
                        uri = propsMap.getOrElse(OntologyConstants.KnoraBase.ProjectOntologyGraph, throw InconsistentTriplestoreDataException(s"Project: $projectIri has no projectOntologyGraph defined.")),
                        active = propsMap.getOrElse(OntologyConstants.KnoraBase.Status, throw InconsistentTriplestoreDataException(s"Project: $projectIri has no status defined.")).toBoolean
                    )
            }.toSeq
        } yield namedGraphs
    }

    /**
      * Gets the project with the given project Iri and returns the information as a [[ProjectInfoResponseV1]].
      *
      * @param projectIRI the Iri of the project requested.
      * @param userProfile the profile of user that is making the request.
      * @return information about the project as a [[ProjectInfoResponseV1]].
      */
    private def projectInfoByIRIGetRequestV1(projectIRI: IRI, userProfile: Option[UserProfileV1] = None): Future[ProjectInfoResponseV1] = {

        log.debug(s"projectInfoByIRIGetRequestV1 - projectIRI: $projectIRI")

        for {
            projectInfo <- projectInfoByIRIGetV1(projectIRI, userProfile)
        } yield ProjectInfoResponseV1(
            project_info = projectInfo,
            userdata = userProfile match {
                case Some(profile) => Some(profile.userData)
                case None => None
            }
        )
    }

    /**
      * Gets the project with the given project Iri and returns the information as a [[ProjectInfoV1]].
      *
      * @param projectIri the Iri of the project requested.
      * @param userProfile the profile of user that is making the request.
      * @return information about the project as a [[ProjectInfoResponseV1]].
      */
    private def projectInfoByIRIGetV1(projectIri: IRI, userProfile: Option[UserProfileV1] = None): Future[ProjectInfoV1] = {

        log.debug(s"projectInfoByIRIGetV1 - projectIRI: $projectIri")

        for {
            sparqlQuery <- Future(queries.sparql.v1.txt.getProjectByIri(
                triplestore = settings.triplestoreType,
                projectIri = projectIri
            ).toString())
            projectResponse <- (storeManager ? SparqlSelectRequest(sparqlQuery)).mapTo[SparqlSelectResponse]

            _ = if (projectResponse.results.bindings.isEmpty) {
                throw NotFoundException(s"Project '$projectIri' not found")
            }

            projectInfo = createProjectInfoV1(projectResponse = projectResponse.results.bindings, projectIri = projectIri, userProfile)

            _ = log.debug(s"projectInfoByIRIGetV1 - projectInfo: $projectInfo")

        } yield projectInfo
    }

    /**
      * Gets the project with the given shortname and returns the information as a [[ProjectInfoResponseV1]].
      *
      * @param shortName   the shortname of the project requested.
      * @param userProfile the profile of user that is making the request.
      * @return information about the project as a [[ProjectInfoResponseV1]].
      */
    private def projectInfoByShortnameGetRequestV1(shortName: String, userProfile: Option[UserProfileV1]): Future[ProjectInfoResponseV1] = {
        for {
            sparqlQueryString <- Future(queries.sparql.v1.txt.getProjectByShortname(
                triplestore = settings.triplestoreType,
                shortname = shortName
            ).toString())
            //_ = log.debug(s"getProjectInfoByShortnameGetRequest - query: $sparqlQueryString")

            projectResponse <- (storeManager ? SparqlSelectRequest(sparqlQueryString)).mapTo[SparqlSelectResponse]
            //_ = log.debug(s"getProjectInfoByShortnameGetRequest - result: ${MessageUtil.toSource(projectResponse)}")


            // get project Iri from results rows
            projectIri: IRI = if (projectResponse.results.bindings.nonEmpty) {
                projectResponse.results.bindings.head.rowMap("s")
            } else {
                throw NotFoundException(s"Project '$shortName' not found")
            }

            projectInfo = createProjectInfoV1(projectResponse = projectResponse.results.bindings, projectIri = projectIri, userProfile)

        } yield ProjectInfoResponseV1(
            project_info = projectInfo,
            userdata = userProfile match {
                case Some(profile) => Some(profile.userData)
                case None => None
            }
        )
    }

    /**
      * Gets the members of a project with the given IRI.
      *
      * @param projectIri the IRI of the project.
      * @param userProfileV1 the profile of the user that is making the request.
      * @return the members of a project as a [[ProjectMembersGetResponseV1]]
      */
    private def projectMembersByIRIGetRequestV1(projectIri: IRI, userProfileV1: UserProfileV1): Future[ProjectMembersGetResponseV1] = {

        log.debug(s"projectMembersByIRIGetRequestV1 - projectIRI: $projectIri")

        def getUserData(userIri: IRI, userProfileV1: UserProfileV1): Future[UserDataV1] = {
            for {
                userProfile <- (responderManager ? UserProfileByIRIGetRequestV1(userIri, UserProfileType.SHORT, userProfileV1)).mapTo[Option[UserProfileV1]]

                result = userProfile match {
                    case Some(up) => up.userData
                    case None => throw InconsistentTriplestoreDataException(s"User $userIri was not found but is member of project. Please report this as a possible bug.")
                }
            } yield result
        }

        val projectMemberIrisFuture: Future[Seq[IRI]] = for {
            sparqlQueryString <- Future(queries.sparql.v1.txt.getProjectMembersByIri(
                triplestore = settings.triplestoreType,
                projectIri = projectIri
            ).toString())
            //_ = log.debug(s"projectMembersByIRIGetRequestV1 - query: $sparqlQueryString")

            projectMembersResponse <- (storeManager ? SparqlSelectRequest(sparqlQueryString)).mapTo[SparqlSelectResponse]
            //_ = log.debug(s"projectMembersByIRIGetRequestV1 - result: ${MessageUtil.toSource(projectMembersResponse)}")

            // get project member Iri from results rows
            projectMemberIris: Seq[IRI] = if (projectMembersResponse.results.bindings.nonEmpty) {
                projectMembersResponse.results.bindings.map(_.rowMap("s"))
            } else {
                throw NotFoundException(s"Project '$projectIri' either not found or has no members")
            }
            _ = log.debug(s"projectMembersByIRIGetRequestV1 - projectMemberIris: $projectMemberIris")

            //_ = log.debug(s"projectMembersByIRIGetRequestV1 - projectMembers: $projectMembers")

        } yield projectMemberIris

        val userDatasFuture: Future[Seq[Future[UserDataV1]]] = for {
            memberIris: Seq[IRI] <- projectMemberIrisFuture
            userDatas: Seq[Future[UserDataV1]] = memberIris.map{ case userIri: IRI =>
                getUserData(userIri, userProfileV1)
            }
        } yield userDatas

        val userDatas: Future[Seq[Future[UserDataV1]]] = projectMemberIrisFuture map {
            case userIris: Seq[IRI] => {
                userIris map {
                    case userIri: IRI => {
                        val userData = getUserData(userIri, userProfileV1)
                        userData
                    }
                }
            }

        }
    }


    /**
      * Gets the members of a project with the given shortname.
      *
      * @param shortname the IRI of the project.
      * @param userProfileV1 the profile of the user that is making the request.
      * @return the members of a project as a [[ProjectMembersGetResponseV1]]
      */
    private def projectMembersByShortnameGetRequestV1(shortname: String, userProfileV1: UserProfileV1): Future[ProjectMembersGetResponseV1] = {

        for {
            sparqlQueryString <- Future(queries.sparql.v1.txt.getProjectMembersByShortname(
                triplestore = settings.triplestoreType,
                shortname = shortname
            ).toString())
            //_ = log.debug(s"projectMembersByShortnameGetRequestV1 - query: $sparqlQueryString")

            projectMembersResponse <- (storeManager ? SparqlSelectRequest(sparqlQueryString)).mapTo[SparqlSelectResponse]
            //_ = log.debug(s"projectMembersByShortnameGetRequestV1 - result: ${MessageUtil.toSource(projectMembersResponse)}")


            // get project member Iri from results rows
            projectMemberIris: Seq[IRI] = if (projectMembersResponse.results.bindings.nonEmpty) {
                projectMembersResponse.results.bindings.map(_.rowMap("s"))
            } else {
                throw NotFoundException(s"Project '$shortname' either not found or has no members")
            }
            _ = log.debug(s"projectMembersByShortnameGetRequestV1 - projectMemberIris: $projectMemberIris")

            projectMembers = Seq.empty[UserDataV1]

            //_ = log.debug(s"projectMembersByShortnameGetRequestV1 - projectMembers: $projectMembers")

        } yield ProjectMembersGetResponseV1(projectMembers, userProfileV1.userData)
    }

    private def projectCreateRequestV1(createRequest: CreateProjectApiRequestV1, userProfile: UserProfileV1, apiRequestID: UUID): Future[ProjectOperationResponseV1] = {

        def projectCreateTask(createRequest: CreateProjectApiRequestV1, userProfile: UserProfileV1, apiRequestID: UUID): Future[ProjectOperationResponseV1] = for {
            // check if required properties are not empty
            _ <- Future(if (createRequest.shortname.isEmpty) throw BadRequestException("'Shortname' cannot be empty"))

            // check if the supplied 'shortname' for the new project is unique, i.e. not already registered
            sparqlQueryString = queries.sparql.v1.txt.getProjectByShortname(
                triplestore = settings.triplestoreType,
                shortname = createRequest.shortname
            ).toString()
            //_ = log.debug(s"createNewProjectV1 - check duplicate shortname query: $sparqlQueryString")

            projectInfoQueryResponse <- (storeManager ? SparqlSelectRequest(sparqlQueryString)).mapTo[SparqlSelectResponse]
            projectResponse = projectInfoQueryResponse.results.bindings
            //_ = log.debug(s"createNewProjectV1 - check duplicate shortname response:  ${MessageUtil.toSource(projectInfoQueryResponse)}")

            _ = if (projectResponse.nonEmpty) {
                throw DuplicateValueException(s"Project with the shortname: '${createRequest.shortname}' already exists")
            }

            newProjectIRI = knoraIdUtil.makeRandomProjectIri
            projectOntologyGraphString = "http://www.knora.org/ontology/" + createRequest.shortname
            projectDataGraphString = "http://www.knora.org/data/" + createRequest.shortname

            // Create the new project.
            createNewProjectSparqlString = queries.sparql.v1.txt.createNewProject(
                adminNamedGraphIri = "http://www.knora.org/data/admin",
                triplestore = settings.triplestoreType,
                projectIri = newProjectIRI,
                projectClassIri = OntologyConstants.KnoraBase.KnoraProject,
                shortname = createRequest.shortname,
                maybeLongname = createRequest.longname,
                maybeDescription = createRequest.description,
                maybeKeywords = createRequest.keywords,
                maybeLogo = createRequest.logo,
                basepath = createRequest.basepath,
                status = createRequest.status,
                hasSelfJoinEnabled = createRequest.hasSelfJoinEnabled,
                projectOntologyGraph = projectOntologyGraphString,
                projectDataGraph = projectDataGraphString
            ).toString
            //_ = log.debug(s"createNewProjectV1 - update query: $createNewProjectSparqlString")

            createResourceResponse <- (storeManager ? SparqlUpdateRequest(createNewProjectSparqlString)).mapTo[SparqlUpdateResponse]


            // Verify that the project was created.
            sparqlQuery = queries.sparql.v1.txt.getProjectByIri(
                triplestore = settings.triplestoreType,
                projectIri = newProjectIRI
            ).toString
            projectInfoQueryResponse <- (storeManager ? SparqlSelectRequest(sparqlQuery)).mapTo[SparqlSelectResponse]
            projectResponse = projectInfoQueryResponse.results.bindings
            //_ = log.debug(s"createNewProjectV1 - verify query response: ${MessageUtil.toSource(projectResponse)}")

            _ = if (projectResponse.isEmpty) {
                throw UpdateNotPerformedException(s"Project $newProjectIRI was not created. Please report this as a possible bug.")
            }

            // create the project info
            newProjectInfo = createProjectInfoV1(projectResponse, newProjectIRI, Some(userProfile))

            // create the project operation response
            projectOperationResponseV1 = ProjectOperationResponseV1(newProjectInfo, userProfile.userData)

        } yield projectOperationResponseV1

        for {
        // run user creation with an global IRI lock
            taskResult <- IriLocker.runWithIriLock(
                apiRequestID,
                PROJECTS_GLOBAL_LOCK_IRI,
                () => projectCreateTask(createRequest, userProfile, apiRequestID)
            )
        } yield taskResult
    }

    private def projectUpdateRequestV1(userProfileV1: UserProfileV1): Future[ProjectInfoResponseV1] = ???

    ////////////////////
    // Helper Methods //
    ////////////////////

    /**
      * Helper method that turns SPARQL result rows into a [[ProjectInfoV1]].
      *
      * @param projectResponse results from the SPARQL query representing information about the project.
      * @param projectIri the Iri of the project the querid information belong to.
      * @param userProfile the profile of user that is making the request.
      * @return a [[ProjectInfoV1]] representing information about project.
      */
    private def createProjectInfoV1(projectResponse: Seq[VariableResultsRow], projectIri: IRI, userProfile: Option[UserProfileV1]): ProjectInfoV1 = {

        log.debug(s"createProjectInfoV1FromProjectResponse - projectResponse: ${MessageUtil.toSource(projectResponse)}")

        if (projectResponse.nonEmpty) {

            val projectProperties = projectResponse.foldLeft(Map.empty[IRI, String]) {
                case (acc, row: VariableResultsRow) =>
                    acc + (row.rowMap("p") -> row.rowMap("o"))
            }
            log.debug(s"createProjectInfoV1FromProjectResponse - projectProperties: ${MessageUtil.toSource(projectProperties)}")

            /* create and return the project info */
            ProjectInfoV1(
                id = projectIri,
                shortname = projectProperties.getOrElse(OntologyConstants.KnoraBase.ProjectShortname, throw InconsistentTriplestoreDataException(s"Project: $projectIri has no shortname defined.")),
                longname = projectProperties.get(OntologyConstants.KnoraBase.ProjectLongname),
                description = projectProperties.get(OntologyConstants.KnoraBase.ProjectDescription),
                keywords = projectProperties.get(OntologyConstants.KnoraBase.ProjectKeywords),
                logo = projectProperties.get(OntologyConstants.KnoraBase.ProjectLogo),
                belongsToInstitution = projectProperties.get(OntologyConstants.KnoraBase.BelongsToProject),
                basepath = projectProperties.getOrElse(OntologyConstants.KnoraBase.ProjectBasepath, throw InconsistentTriplestoreDataException(s"Project: $projectIri has no basepath defined.")),
                ontologyNamedGraph = projectProperties.getOrElse(OntologyConstants.KnoraBase.ProjectOntologyGraph, throw InconsistentTriplestoreDataException(s"Project: $projectIri has no projectOntologyGraph defined.")),
                dataNamedGraph = projectProperties.getOrElse(OntologyConstants.KnoraBase.ProjectDataGraph, throw InconsistentTriplestoreDataException(s"Project: $projectIri has no projectDataGraph defined.")),
                status = projectProperties.getOrElse(OntologyConstants.KnoraBase.Status, throw InconsistentTriplestoreDataException(s"Project: $projectIri has no status defined.")).toBoolean,
                hasSelfJoinEnabled = projectProperties.getOrElse(OntologyConstants.KnoraBase.HasSelfJoinEnabled, throw InconsistentTriplestoreDataException(s"Project: $projectIri has no hasSelfJoinEnabled defined.")).toBoolean
            )

        } else {
            // no information was found for the given project Iri
            throw NotFoundException(s"For the given project Iri $projectIri no information was found")
        }

    }
}
