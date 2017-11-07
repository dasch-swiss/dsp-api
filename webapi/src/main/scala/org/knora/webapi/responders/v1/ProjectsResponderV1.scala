/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and Sepideh Alassi.
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
import org.knora.webapi._
import org.knora.webapi.messages.v1.responder.ontologymessages.NamedGraphV1
import org.knora.webapi.messages.v1.responder.projectmessages._
import org.knora.webapi.messages.v1.responder.usermessages._
import org.knora.webapi.messages.store.triplestoremessages._
import org.knora.webapi.responders.{IriLocker, Responder}
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
    def receive = {
        case ProjectsGetRequestV1(userProfile) => future2Message(sender(), projectsGetRequestV1(userProfile), log)
        case ProjectsGetV1(userProfile) => future2Message(sender(), projectsGetV1(userProfile), log)
        case ProjectsNamedGraphGetV1(userProfile) => future2Message(sender(), projectsNamedGraphGetV1(userProfile), log)
        case ProjectInfoByIRIGetRequestV1(iri, userProfile) => future2Message(sender(), projectInfoByIRIGetRequestV1(iri, userProfile), log)
        case ProjectInfoByIRIGetV1(iri, userProfile) => future2Message(sender(), projectInfoByIRIGetV1(iri, userProfile), log)
        case ProjectInfoByShortnameGetRequestV1(shortname, userProfile) => future2Message(sender(), projectInfoByShortnameGetRequestV1(shortname, userProfile), log)
        case ProjectMembersByIRIGetRequestV1(iri, userProfileV1) => future2Message(sender(), projectMembersByIRIGetRequestV1(iri, userProfileV1), log)
        case ProjectMembersByShortnameGetRequestV1(shortname, userProfileV1) => future2Message(sender(), projectMembersByShortnameGetRequestV1(shortname, userProfileV1), log)
        case ProjectAdminMembersByIRIGetRequestV1(iri, userProfileV1) => future2Message(sender(), projectAdminMembersByIRIGetRequestV1(iri, userProfileV1), log)
        case ProjectAdminMembersByShortnameGetRequestV1(shortname, userProfileV1) => future2Message(sender(), projectAdminMembersByShortnameGetRequestV1(shortname, userProfileV1), log)
        case ProjectCreateRequestV1(createRequest, userProfileV1, apiRequestID) => future2Message(sender(), projectCreateRequestV1(createRequest, userProfileV1, apiRequestID), log)
        case ProjectChangeRequestV1(projectIri, changeProjectRequest, userProfileV1, apiRequestID) => future2Message(sender(), changeBasicInformationRequestV1(projectIri, changeProjectRequest, userProfileV1, apiRequestID), log)
        case other => handleUnexpectedMessage(sender(), other, log, this.getClass.getName)
    }

    /**
      * Gets all the projects and returns them as a [[ProjectsResponseV1]].
      *
      * @param userProfile the profile of the user that is making the request.
      * @return all the projects as a [[ProjectsResponseV1]].
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

            projectsWithProperties: Map[String, Map[String, String]] = projectsResponseRows.groupBy(_.rowMap("s")).map {
                case (projIri: String, rows: Seq[VariableResultsRow]) => (projIri, rows.map(row => (row.rowMap("p"), row.rowMap("o"))).toMap)
            }
            //_ = log.debug(s"getProjectsResponseV1 - projectsWithProperties: $projectsWithProperties")

            projects = projectsWithProperties.map {
                case (projectIri: String, propsMap: Map[String, String]) =>

                    ProjectInfoV1(
                        id = projectIri,
                        shortname = propsMap.getOrElse(OntologyConstants.KnoraBase.ProjectShortname, throw InconsistentTriplestoreDataException(s"Project: $projectIri has no shortname defined.")),
                        longname = propsMap.get(OntologyConstants.KnoraBase.ProjectLongname),
                        description = propsMap.get(OntologyConstants.KnoraBase.ProjectDescription),
                        keywords = propsMap.get(OntologyConstants.KnoraBase.ProjectKeywords),
                        logo = propsMap.get(OntologyConstants.KnoraBase.ProjectLogo),
                        institution = propsMap.get(OntologyConstants.KnoraBase.BelongsToProject),
                        ontologyNamedGraph = propsMap.getOrElse(OntologyConstants.KnoraBase.ProjectOntologyGraph, throw InconsistentTriplestoreDataException(s"Project: $projectIri has no projectOntologyGraph defined.")),
                        dataNamedGraph = propsMap.getOrElse(OntologyConstants.KnoraBase.ProjectDataGraph, throw InconsistentTriplestoreDataException(s"Project: $projectIri has no projectDataGraph defined.")),
                        status = propsMap.getOrElse(OntologyConstants.KnoraBase.Status, throw InconsistentTriplestoreDataException(s"Project: $projectIri has no status defined.")).toBoolean,
                        selfjoin = propsMap.getOrElse(OntologyConstants.KnoraBase.HasSelfJoinEnabled, throw InconsistentTriplestoreDataException(s"Project: $projectIri has no hasSelfJoinEnabled defined.")).toBoolean
                    )
            }.toSeq

        } yield projects
    }

    /**
      * Gets all the named graphs from all projects and returns them as a sequence of [[NamedGraphV1]]
      *
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
            //_ = log.debug(s"getProjectsResponseV1 - result: $projectsResponse")

            projectsResponseRows: Seq[VariableResultsRow] = projectsResponse.results.bindings

            projectsWithProperties: Map[String, Map[String, String]] = projectsResponseRows.groupBy(_.rowMap("s")).map {
                case (projIri: String, rows: Seq[VariableResultsRow]) => (projIri, rows.map(row => (row.rowMap("p"), row.rowMap("o"))).toMap)
            }
            //_ = log.debug(s"getProjectsResponseV1 - projectsWithProperties: $projectsWithProperties")

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
      * Gets the project with the given project IRI and returns the information as a [[ProjectInfoResponseV1]].
      *
      * @param projectIri  the IRI of the project requested.
      * @param userProfile the profile of user that is making the request.
      * @return information about the project as a [[ProjectInfoResponseV1]].
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
      * Gets the members of a project with the given IRI.
      *
      * @param projectIri    the IRI of the project.
      * @param userProfileV1 the profile of the user that is making the request.
      * @return the members of a project as a [[ProjectMembersGetResponseV1]]
      */
    private def projectMembersByIRIGetRequestV1(projectIri: IRI, userProfileV1: UserProfileV1): Future[ProjectMembersGetResponseV1] = {

        //log.debug("projectMembersByIRIGetRequestV1 - projectIRI: {}", projectIri)

        for {
            projectExists: Boolean <- projectByIriExists(projectIri)

            _ = if (!projectExists) throw NotFoundException(s"Project '$projectIri' not found.")

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
      */
    private def projectMembersByShortnameGetRequestV1(shortname: String, userProfileV1: UserProfileV1): Future[ProjectMembersGetResponseV1] = {

        //log.debug("projectMembersByShortnameGetRequestV1 - shortname: {}", shortname)

        for {
            projectExists: Boolean <- projectByShortnameExists(shortname)

            _ = if (!projectExists) throw NotFoundException(s"Project '$shortname' not found.")

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
      */
    private def projectAdminMembersByIRIGetRequestV1(projectIri: IRI, userProfileV1: UserProfileV1): Future[ProjectAdminMembersGetResponseV1] = {

        //log.debug("projectAdminMembersByIRIGetRequestV1 - projectIRI: {}", projectIri)

        for {
            projectExists: Boolean <- projectByIriExists(projectIri)

            _ = if (!projectExists) throw NotFoundException(s"Project '$projectIri' not found.")

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
      */
    private def projectAdminMembersByShortnameGetRequestV1(shortname: String, userProfileV1: UserProfileV1): Future[ProjectAdminMembersGetResponseV1] = {

        //log.debug("projectAdminMembersByShortnameGetRequestV1 - shortname: {}", shortname)

        for {
            projectExists: Boolean <- projectByShortnameExists(shortname)

            _ = if (!projectExists) throw NotFoundException(s"Project '$shortname' not found.")

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

    /**
      * Creates a project.
      *
      * @param createRequest
      * @param userProfile
      * @param apiRequestID
      * @return
      */
    private def projectCreateRequestV1(createRequest: CreateProjectApiRequestV1, userProfile: UserProfileV1, apiRequestID: UUID): Future[ProjectOperationResponseV1] = {

        //log.debug("projectCreateRequestV1 - createRequest: {}", createRequest)

        def projectCreateTask(createRequest: CreateProjectApiRequestV1, userProfile: UserProfileV1): Future[ProjectOperationResponseV1] = for {
            // check if required properties are not empty
            _ <- Future(if (createRequest.shortname.isEmpty) throw BadRequestException("'Shortname' cannot be empty"))

            // check if the requesting user is allowed to create project
            _ = if (!userProfile.permissionData.isSystemAdmin) {
                // not a system admin
                throw ForbiddenException("A new project can only be created by a system admin.")
            }

            // check if the supplied 'shortname' for the new project is unique, i.e. not already registered
            sparqlQueryString = queries.sparql.v1.txt.getProjectByShortname(
                triplestore = settings.triplestoreType,
                shortname = createRequest.shortname
            ).toString()
            //_ = log.debug(s"createNewProjectV1 - check duplicate shortname query: $sparqlQueryString")

            projectInfoQueryResponse <- (storeManager ? SparqlSelectRequest(sparqlQueryString)).mapTo[SparqlSelectResponse]
            projectResponse = projectInfoQueryResponse.results.bindings
            //_ = log.debug(s"createNewProjectV1 - check duplicate shortname response: $projectInfoQueryResponse")

            _ = if (projectResponse.nonEmpty) {
                throw DuplicateValueException(s"Project with the shortname: '${createRequest.shortname}' already exists")
            }

            newProjectIRI = knoraIdUtil.makeRandomProjectIri
            projectOntologyGraphString = "http://www.knora.org/ontology/" + createRequest.shortname
            projectDataGraphString = "http://www.knora.org/data/" + createRequest.shortname

            // Create the new project.
            createNewProjectSparqlString = queries.sparql.v1.txt.createNewProject(
                adminNamedGraphIri = OntologyConstants.NamedGraphs.AdminNamedGraph,
                triplestore = settings.triplestoreType,
                projectIri = newProjectIRI,
                projectClassIri = OntologyConstants.KnoraBase.KnoraProject,
                shortname = createRequest.shortname,
                maybeLongname = createRequest.longname,
                maybeDescription = createRequest.description,
                maybeKeywords = createRequest.keywords,
                maybeLogo = createRequest.logo,
                status = createRequest.status,
                hasSelfJoinEnabled = createRequest.selfjoin,
                projectOntologyGraph = projectOntologyGraphString,
                projectDataGraph = projectDataGraphString
            ).toString
            //_ = log.debug("createNewProjectV1 - update query: {}", createNewProjectSparqlString)

            createProjectResponse <- (storeManager ? SparqlUpdateRequest(createNewProjectSparqlString)).mapTo[SparqlUpdateResponse]


            // Verify that the project was created.
            sparqlQuery = queries.sparql.v1.txt.getProjectByIri(
                triplestore = settings.triplestoreType,
                projectIri = newProjectIRI
            ).toString
            projectInfoQueryResponse <- (storeManager ? SparqlSelectRequest(sparqlQuery)).mapTo[SparqlSelectResponse]
            projectResponse = projectInfoQueryResponse.results.bindings
            //_ = log.debug("createNewProjectV1 - verify query response: {}", projectResponse)

            _ = if (projectResponse.isEmpty) {
                throw UpdateNotPerformedException(s"Project $newProjectIRI was not created. Please report this as a possible bug.")
            }

            // create the project info
            newProjectInfo = createProjectInfoV1(projectResponse, newProjectIRI, Some(userProfile))

            // create the project operation response
            projectOperationResponseV1 = ProjectOperationResponseV1(newProjectInfo)

        } yield projectOperationResponseV1

        for {
            // run user creation with an global IRI lock
            taskResult <- IriLocker.runWithIriLock(
                apiRequestID,
                PROJECTS_GLOBAL_LOCK_IRI,
                () => projectCreateTask(createRequest, userProfile)
            )
        } yield taskResult
    }

    /**
      * Changes project's basic information.
      *
      * @param projectIri
      * @param changeProjectRequest
      * @param userProfileV1
      * @param apiRequestID
      * @return
      */
    private def changeBasicInformationRequestV1(projectIri: IRI, changeProjectRequest: ChangeProjectApiRequestV1, userProfileV1: UserProfileV1, apiRequestID: UUID): Future[ProjectOperationResponseV1] = {

        //log.debug(s"changeBasicInformationRequestV1: changeProjectRequest: {}", changeProjectRequest)

        /**
          * The actual change project task run with an IRI lock.
          */
        def changeProjectTask(projectIri: IRI, changeProjectRequest: ChangeProjectApiRequestV1, userProfileV1: UserProfileV1): Future[ProjectOperationResponseV1] = for {

            _ <- Future(
                // check if necessary information is present
                if (projectIri.isEmpty) throw BadRequestException("Project IRI cannot be empty")
            )

            // check if the requesting user is allowed to perform updates
            _ = if (!userProfileV1.permissionData.isProjectAdmin(projectIri) && !userProfileV1.permissionData.isSystemAdmin) {
                // not a project admin and not a system admin
                throw ForbiddenException("Project's information can only be changed by a project or system admin.")
            }

            // create the update request
            projectUpdatePayload = ProjectUpdatePayloadV1(
                longname = changeProjectRequest.longname,
                description = changeProjectRequest.description,
                keywords = changeProjectRequest.keywords,
                logo = changeProjectRequest.logo,
                institution = changeProjectRequest.institution,
                status = changeProjectRequest.status,
                selfjoin = changeProjectRequest.selfjoin
            )

            result <- updateProjectV1(projectIri, projectUpdatePayload, userProfileV1)

        } yield result

        for {
            // run the change status task with an IRI lock
            taskResult <- IriLocker.runWithIriLock(
                apiRequestID,
                projectIri,
                () => changeProjectTask(projectIri, changeProjectRequest, userProfileV1)
            )
        } yield taskResult

    }

    /**
      * Main project update method.
      *
      * @param projectIri
      * @param projectUpdatePayload
      * @param userProfile
      * @return
      */
    private def updateProjectV1(projectIri: IRI, projectUpdatePayload: ProjectUpdatePayloadV1, userProfile: UserProfileV1): Future[ProjectOperationResponseV1] = {

        // log.debug("updateProjectV1 - projectUpdatePayload: {}", projectUpdatePayload)

        val parametersCount = List(
            projectUpdatePayload.shortname,
            projectUpdatePayload.longname,
            projectUpdatePayload.description,
            projectUpdatePayload.keywords,
            projectUpdatePayload.logo,
            projectUpdatePayload.institution,
            projectUpdatePayload.ontologyNamedGraph,
            projectUpdatePayload.dataNamedGraph,
            projectUpdatePayload.status,
            projectUpdatePayload.selfjoin).flatten.size

        if (parametersCount == 0) throw BadRequestException("No data would be changed. Aborting update request.")

        for {
            /* Verify that the project exists. */
            maybeProjectInfo <- projectInfoByIRIGetV1(projectIri, Some(userProfile))
            projectInfo: ProjectInfoV1 = maybeProjectInfo.getOrElse(throw NotFoundException(s"Project '$projectIri' not found. Aborting update request."))

            /* Update project */
            updateProjectSparqlString <- Future(queries.sparql.v1.txt.updateProject(
                adminNamedGraphIri = "http://www.knora.org/data/admin",
                triplestore = settings.triplestoreType,
                projectIri = projectIri,
                maybeShortname = projectUpdatePayload.shortname,
                maybeLongname = projectUpdatePayload.longname,
                maybeDescription = projectUpdatePayload.description,
                maybeKeywords = projectUpdatePayload.keywords,
                maybeLogo = projectUpdatePayload.logo,
                maybeInstitution = projectUpdatePayload.institution,
                maybeOntologyGraph = projectUpdatePayload.ontologyNamedGraph,
                maybeDataGraph = projectUpdatePayload.ontologyNamedGraph,
                maybeStatus = projectUpdatePayload.status,
                maybeSelfjoin = projectUpdatePayload.selfjoin
            ).toString)
            //_ = log.debug(s"updateProjectV1 - query: {}",updateProjectSparqlString)

            updateProjectResponse <- (storeManager ? SparqlUpdateRequest(updateProjectSparqlString)).mapTo[SparqlUpdateResponse]

            /* Verify that the project was updated. */
            maybeUpdatedProject <- projectInfoByIRIGetV1(projectIri, Some(userProfile))
            updatedProject: ProjectInfoV1 = maybeUpdatedProject.getOrElse(throw UpdateNotPerformedException("Project was not updated. Please report this as a possible bug."))

            //_ = log.debug("updateProjectV1 - projectUpdatePayload: {} /  updatedProject: {}", projectUpdatePayload, updatedProject)

            _ = if (projectUpdatePayload.shortname.isDefined) {
                if (updatedProject.shortname != projectUpdatePayload.shortname.get) throw UpdateNotPerformedException("Project's 'shortname' was not updated. Please report this as a possible bug.")
            }

            _ = if (projectUpdatePayload.longname.isDefined) {
                if (updatedProject.longname != projectUpdatePayload.longname) throw UpdateNotPerformedException("Project's 'longname' was not updated. Please report this as a possible bug.")
            }

            _ = if (projectUpdatePayload.description.isDefined) {
                if (updatedProject.description != projectUpdatePayload.description) throw UpdateNotPerformedException("Project's 'description' was not updated. Please report this as a possible bug.")
            }

            _ = if (projectUpdatePayload.keywords.isDefined) {
                if (updatedProject.keywords != projectUpdatePayload.keywords) throw UpdateNotPerformedException("Project's 'keywords' was not updated. Please report this as a possible bug.")
            }

            _ = if (projectUpdatePayload.logo.isDefined) {
                if (updatedProject.logo != projectUpdatePayload.logo) throw UpdateNotPerformedException("Project's 'logo' was not updated. Please report this as a possible bug.")
            }

            _ = if (projectUpdatePayload.institution.isDefined) {
                if (updatedProject.institution != projectUpdatePayload.institution) throw UpdateNotPerformedException("Project's 'institution' was not updated. Please report this as a possible bug.")
            }

            _ = if (projectUpdatePayload.ontologyNamedGraph.isDefined) {
                if (updatedProject.ontologyNamedGraph != projectUpdatePayload.ontologyNamedGraph.get) throw UpdateNotPerformedException("Project's 'ontologyGraph' was not updated. Please report this as a possible bug.")
            }

            _ = if (projectUpdatePayload.dataNamedGraph.isDefined) {
                if (updatedProject.dataNamedGraph != projectUpdatePayload.dataNamedGraph.get) throw UpdateNotPerformedException("Project's 'dataGraph' was not updated. Please report this as a possible bug.")
            }

            _ = if (projectUpdatePayload.status.isDefined) {
                if (updatedProject.status != projectUpdatePayload.status.get) throw UpdateNotPerformedException("Project's 'status' was not updated. Please report this as a possible bug.")
            }

            _ = if (projectUpdatePayload.selfjoin.isDefined) {
                if (updatedProject.selfjoin != projectUpdatePayload.selfjoin.get) throw UpdateNotPerformedException("Project's 'selfjoin' status was not updated. Please report this as a possible bug.")
            }

            // create the project operation response
            projectOperationResponseV1 = ProjectOperationResponseV1(project_info = updatedProject)
        } yield projectOperationResponseV1

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

            val projectProperties = projectResponse.foldLeft(Map.empty[IRI, String]) {
                case (acc, row: VariableResultsRow) =>
                    acc + (row.rowMap("p") -> row.rowMap("o"))
            }

            // log.debug(s"createProjectInfoV1 - projectProperties: $projectProperties")

            /* create and return the project info */
            ProjectInfoV1(
                id = projectIri,
                shortname = projectProperties.getOrElse(OntologyConstants.KnoraBase.ProjectShortname, throw InconsistentTriplestoreDataException(s"Project: $projectIri has no shortname defined.")),
                longname = projectProperties.get(OntologyConstants.KnoraBase.ProjectLongname),
                description = projectProperties.get(OntologyConstants.KnoraBase.ProjectDescription),
                keywords = projectProperties.get(OntologyConstants.KnoraBase.ProjectKeywords),
                logo = projectProperties.get(OntologyConstants.KnoraBase.ProjectLogo),
                institution = projectProperties.get(OntologyConstants.KnoraBase.BelongsToInstitution),
                ontologyNamedGraph = projectProperties.getOrElse(OntologyConstants.KnoraBase.ProjectOntologyGraph, throw InconsistentTriplestoreDataException(s"Project: $projectIri has no projectOntologyGraph defined.")),
                dataNamedGraph = projectProperties.getOrElse(OntologyConstants.KnoraBase.ProjectDataGraph, throw InconsistentTriplestoreDataException(s"Project: $projectIri has no projectDataGraph defined.")),
                status = projectProperties.getOrElse(OntologyConstants.KnoraBase.Status, throw InconsistentTriplestoreDataException(s"Project: $projectIri has no status defined.")).toBoolean,
                selfjoin = projectProperties.getOrElse(OntologyConstants.KnoraBase.HasSelfJoinEnabled, throw InconsistentTriplestoreDataException(s"Project: $projectIri has no hasSelfJoinEnabled defined.")).toBoolean
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
                userProfile <- (responderManager ? UserDataByIriGetV1(userIri, true)).mapTo[Option[UserDataV1]]

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
                userProfile <- (responderManager ? UserDataByIriGetV1(userIri, true)).mapTo[Option[UserDataV1]]

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
            askString <- Future(queries.sparql.v1.txt.checkProjectExistsByIri(projectIri = projectIri).toString)
            //_ = log.debug("projectExists - query: {}", askString)

            checkUserExistsResponse <- (storeManager ? SparqlAskRequest(askString)).mapTo[SparqlAskResponse]
            result = checkUserExistsResponse.result

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
            askString <- Future(queries.sparql.v1.txt.checkProjectExistsByShortname(shortname = shortname).toString)
            //_ = log.debug("projectExists - query: {}", askString)

            checkUserExistsResponse <- (storeManager ? SparqlAskRequest(askString)).mapTo[SparqlAskResponse]
            result = checkUserExistsResponse.result

        } yield result
    }
}
