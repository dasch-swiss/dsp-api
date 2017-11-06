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
import akka.http.scaladsl.util.FastFuture
import akka.pattern._
import org.knora.webapi._
import org.knora.webapi.messages.v1.responder.ontologymessages.NamedGraphV1
import org.knora.webapi.messages.v1.responder.projectmessages._
import org.knora.webapi.messages.v1.responder.usermessages._
import org.knora.webapi.messages.store.triplestoremessages._
import org.knora.webapi.responders.{IriLocker, Responder}
import org.knora.webapi.util.ActorUtil._
import org.knora.webapi.util.{KnoraIdUtil, StringFormatter}

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
        case ProjectOntologyAddV1(projectIri, ontologyIri, apiRequestID) => future2Message(sender(), projectOntologyAddV1(projectIri, ontologyIri, apiRequestID), log)
        case ProjectOntologyRemoveV1(projectIri, ontologyIri, apiRequestID) => future2Message(sender(), projectOntologyRemoveV1(projectIri, ontologyIri, apiRequestID), log)
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

                    ProjectInfoV1(
                        id = projectIri,
                        shortname = propsMap.getOrElse(OntologyConstants.KnoraBase.ProjectShortname, throw InconsistentTriplestoreDataException(s"Project: $projectIri has no shortname defined.")).head,
                        shortcode = propsMap.get(OntologyConstants.KnoraBase.ProjectShortcode).map(_.head),
                        longname = propsMap.get(OntologyConstants.KnoraBase.ProjectLongname).map(_.head),
                        description = propsMap.get(OntologyConstants.KnoraBase.ProjectDescription).map(_.head),
                        keywords = propsMap.get(OntologyConstants.KnoraBase.ProjectKeywords).map(_.head),
                        logo = propsMap.get(OntologyConstants.KnoraBase.ProjectLogo).map(_.head),
                        institution = propsMap.get(OntologyConstants.KnoraBase.BelongsToProject).map(_.head),
                        ontologies = propsMap.getOrElse(OntologyConstants.KnoraBase.ProjectOntology, Seq.empty[IRI]),
                        status = propsMap.getOrElse(OntologyConstants.KnoraBase.Status, throw InconsistentTriplestoreDataException(s"Project: $projectIri has no status defined.")).head.toBoolean,
                        selfjoin = propsMap.getOrElse(OntologyConstants.KnoraBase.HasSelfJoinEnabled, throw InconsistentTriplestoreDataException(s"Project: $projectIri has no hasSelfJoinEnabled defined.")).head.toBoolean
                    )
            }.toSeq

        } yield projects
    }

    /**
      * Gets all the named graphs from all projects and returns them as a sequence of [[NamedGraphV1]]
      *
      * @param userProfile
      * @return a sequence of [[NamedGraphV1]]
      * @throws InconsistentTriplestoreDataException whenever a expected/required peace of data is not found.
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

            projectsWithProperties: Map[IRI, Map[IRI, Seq[String]]] = projectsResponseRows.groupBy(_.rowMap("s")).map {
                case (projIri: String, rows: Seq[VariableResultsRow]) => (projIri, rows.groupBy(_.rowMap("p")).map {
                    case (predicate: IRI, literals: Seq[VariableResultsRow]) => predicate -> literals.map(_.rowMap("o"))
                })
            }
            //_ = log.debug(s"getProjectsResponseV1 - projectsWithProperties: $projectsWithProperties")

            namedGraphs: Seq[NamedGraphV1] = projectsWithProperties.flatMap {
                case (projectIri: String, propsMap: Map[String, Seq[String]]) =>

                    val maybeOntologies = propsMap.get(OntologyConstants.KnoraBase.ProjectOntology)
                    maybeOntologies match {
                        case Some(ontologies) => ontologies.map( ontologyIri =>
                            NamedGraphV1(
                                id = ontologyIri,
                                shortname = propsMap.getOrElse(OntologyConstants.KnoraBase.ProjectShortname, throw InconsistentTriplestoreDataException(s"Project: $projectIri has no basepath defined.")).head,
                                longname = propsMap.getOrElse(OntologyConstants.KnoraBase.ProjectLongname, throw InconsistentTriplestoreDataException(s"Project: $projectIri has no longname defined.")).head,
                                description = propsMap.getOrElse(OntologyConstants.KnoraBase.ProjectDescription, throw InconsistentTriplestoreDataException(s"Project: $projectIri has no description defined.")).head,
                                project_id = projectIri,
                                uri = ontologyIri,
                                active = propsMap.getOrElse(OntologyConstants.KnoraBase.Status, throw InconsistentTriplestoreDataException(s"Project: $projectIri has no status defined.")).head.toBoolean
                            )
                        )
                        case None => Seq.empty[NamedGraphV1]
                    }
            }.toSeq

            // _ = log.debug("projectsNamedGraphGetV1 - namedGraphs: {}", namedGraphs)
        } yield namedGraphs
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

    /**
      * Creates a project.
      *
      * @param createRequest the new project's information.
      * @param userProfile the profile of the user that is making the request.
      * @param apiRequestID the unique api request ID.
      * @return a [[ProjectOperationResponseV1]].
      * @throws ForbiddenException in the case that the user is not allowed to perform the operation.
      * @throws DuplicateValueException in the case when either the shortname or shortcode are not unique.
      * @throws BadRequestException in the case when the shortcode is invalid.
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

            // check if the supplied shortname is unique
            shortnameExists <- projectByShortnameExists(createRequest.shortname)
            _ = if (shortnameExists) {
                throw DuplicateValueException(s"Project with the shortname: '${createRequest.shortname}' already exists")
            }

            // check if the optionally supplied shortcode is valid and unique
            shortcodeExists <- if (createRequest.shortcode.isDefined) {
                val shortcode = createRequest.shortcode.get
                if (StringFormatter.getInstance.isValidShortcode(shortcode)) {
                    projectByShortcodeExists(shortcode)
                } else {
                    throw BadRequestException(s"The supplied short code: '$shortcode' is not valid.")
                }
            } else {
                FastFuture.successful(false)
            }
            _ = if (shortcodeExists) {
                throw DuplicateValueException(s"Project with the shortcode: '${createRequest.shortcode.get}' already exists")
            }

            newProjectIRI = knoraIdUtil.makeRandomProjectIri(createRequest.shortcode)
            projectOntologyGraphString = "http://www.knora.org/ontology/" + createRequest.shortname
            projectDataGraphString = "http://www.knora.org/data/" + createRequest.shortname

            // Create the new project.
            createNewProjectSparqlString = queries.sparql.v1.txt.createNewProject(
                adminNamedGraphIri = OntologyConstants.NamedGraphs.AdminNamedGraph,
                triplestore = settings.triplestoreType,
                projectIri = newProjectIRI,
                projectClassIri = OntologyConstants.KnoraBase.KnoraProject,
                shortname = createRequest.shortname,
                maybeShortcode = createRequest.shortcode,
                maybeLongname = createRequest.longname,
                maybeDescription = createRequest.description,
                maybeKeywords = createRequest.keywords,
                maybeLogo = createRequest.logo,
                status = createRequest.status,
                hasSelfJoinEnabled = createRequest.selfjoin
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
      * @param projectIri the IRI of the project.
      * @param changeProjectRequest the change payload.
      * @param userProfileV1 the profile of the user making the request.
      * @param apiRequestID the unique api request ID.
      * @return a [[ProjectOperationResponseV1]].
      * @throws ForbiddenException in the case that the user is not allowed to perform the operation.
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

            result <- updateProjectV1(projectIri, projectUpdatePayload)

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
      * Add a ontology to the project.
      *
      * @param projectIri the IRI of the project.
      * @param ontologyIri the IRI of the ontology that is added to the project.
      * @param apiRequestID the unique api request ID.
      * @return a [[ProjectInfoV1]]
      * @throws NotFoundException in the case that the project's IRI is not found.
      */
    private def projectOntologyAddV1(projectIri: IRI, ontologyIri: IRI, apiRequestID: UUID): Future[ProjectInfoV1] = {

        // log.debug("projectOntologyAddV1 - projectIri: {}, ontologyIri: {}", projectIri, ontologyIri)

        /**
          * The actual ontology add task run with an IRI lock.
          */
        def ontologyAddTask(projectIri: IRI, ontologyIri: IRI): Future[ProjectInfoV1] = for {

            _ <- Future(
                // check if necessary information is present
                if (projectIri.isEmpty) throw BadRequestException("Project IRI cannot be empty")
            )

            maybeProjectInfo <- projectInfoByIRIGetV1(projectIri, None)

            // _ = log.debug("projectOntologyAddV1 - ontologyAddTask - maybeProjectInfo: {}", maybeProjectInfo)

            ontologies: Seq[IRI] = maybeProjectInfo match {
                case Some(pi) => pi.ontologies :+ ontologyIri
                case None => throw NotFoundException(s"Project '$projectIri' not found. Aborting update request.")
            }

            projectUpdatePayload = ProjectUpdatePayloadV1(ontologies = Some(ontologies))

            result <- updateProjectV1(projectIri, projectUpdatePayload)
        } yield result.project_info

        for {
            // run the change status task with an IRI lock
            taskResult <- IriLocker.runWithIriLock(
                apiRequestID,
                projectIri,
                () => ontologyAddTask(projectIri, ontologyIri)
            )
        } yield taskResult
    }

    /**
      * Remove a ontology from the project.
      *
      * @param projectIri the IRI of the project.
      * @param ontologyIri the IRI of the ontology that is added to the project.
      * @param apiRequestID the unique api request ID.
      * @return a [[ProjectInfoV1]]
      * @throws NotFoundException in the case that the project's IRI is not found.
      */
    private def projectOntologyRemoveV1(projectIri: IRI, ontologyIri: IRI, apiRequestID: UUID): Future[ProjectInfoV1] = {

        // log.debug("projectOntologyRemoveV1 - projectIri: {}, ontologyIri: {}", projectIri, ontologyIri)

        /**
          * The actual ontology remove task run with an IRI lock.
          */
        def ontologyRemoveTask(projectIri: IRI, ontologyIri: IRI): Future[ProjectInfoV1] = for {

            _ <- Future(
                // check if necessary information is present
                if (projectIri.isEmpty) throw BadRequestException("Project IRI cannot be empty")
            )

            maybeProjectInfo <- projectInfoByIRIGetV1(projectIri, None)

            // _ = log.debug("projectOntologyRemoveV1 - ontologyRemoveTask - maybeProjectInfo: {}", maybeProjectInfo)

            ontologies: Seq[IRI] = maybeProjectInfo match {
                case Some(pi) => pi.ontologies.filterNot(_.equals(ontologyIri))
                case None => throw NotFoundException(s"Project '$projectIri' not found. Aborting update request.")
            }

            projectUpdatePayload = ProjectUpdatePayloadV1(ontologies = Some(ontologies))

            result <- updateProjectV1(projectIri, projectUpdatePayload)
        } yield result.project_info

        for {
            // run the change status task with an IRI lock
            taskResult <- IriLocker.runWithIriLock(
                apiRequestID,
                projectIri,
                () => ontologyRemoveTask(projectIri, ontologyIri)
            )
        } yield taskResult

    }

    /**
      * Main project update method.
      *
      * @param projectIri the IRI of the project.
      * @param projectUpdatePayload the data to be updated. Update means exchanging what is in the triplestore with
      *                             this data. If only some parts of the data need to be changed, then this needs to
      *                             be prepared in the step before this one.
      * @return a [[ProjectOperationResponseV1]].
      * @throws NotFoundException in the case that the project's IRI is not found.
      */
    private def updateProjectV1(projectIri: IRI, projectUpdatePayload: ProjectUpdatePayloadV1): Future[ProjectOperationResponseV1] = {

        log.debug("updateProjectV1 - projectIri: {}, projectUpdatePayload: {}", projectIri, projectUpdatePayload)

        val parametersCount = List(
            projectUpdatePayload.shortname,
            projectUpdatePayload.longname,
            projectUpdatePayload.description,
            projectUpdatePayload.keywords,
            projectUpdatePayload.logo,
            projectUpdatePayload.institution,
            projectUpdatePayload.ontologies,
            projectUpdatePayload.status,
            projectUpdatePayload.selfjoin).flatten.size

        if (parametersCount == 0) throw BadRequestException("No data would be changed. Aborting update request.")

        for {
            /* Verify that the project exists. */
            projectExists <- projectByIriExists(projectIri)
            _ = if (!projectExists) {
                throw NotFoundException(s"Project '$projectIri' not found. Aborting update request.")
            }

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
                maybeOntologies = projectUpdatePayload.ontologies,
                maybeStatus = projectUpdatePayload.status,
                maybeSelfjoin = projectUpdatePayload.selfjoin
            ).toString)
            // _ = log.debug(s"updateProjectV1 - query: {}",updateProjectSparqlString)

            updateProjectResponse <- (storeManager ? SparqlUpdateRequest(updateProjectSparqlString)).mapTo[SparqlUpdateResponse]

            /* Verify that the project was updated. */
            maybeUpdatedProject <- projectInfoByIRIGetV1(projectIri, None)
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

            _ = if (projectUpdatePayload.ontologies.isDefined) {
                if (updatedProject.ontologies != projectUpdatePayload.ontologies.get) throw UpdateNotPerformedException("Project's 'ontologies' where not updated. Please report this as a possible bug.")
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

            val projectProperties: Map[String, Seq[String]] = projectResponse.groupBy(_.rowMap("p")).map {
                case (predicate, rows) => predicate -> rows.map(_.rowMap("o"))
            }

            // log.debug(s"createProjectInfoV1 - projectProperties: $projectProperties")

            /* create and return the project info */
            ProjectInfoV1(
                id = projectIri,
                shortname = projectProperties.getOrElse(OntologyConstants.KnoraBase.ProjectShortname, throw InconsistentTriplestoreDataException(s"Project: $projectIri has no shortname defined.")).head,
                shortcode = projectProperties.get(OntologyConstants.KnoraBase.ProjectShortcode).map(_.head),
                longname = projectProperties.get(OntologyConstants.KnoraBase.ProjectLongname).map(_.head),
                description = projectProperties.get(OntologyConstants.KnoraBase.ProjectDescription).map(_.head),
                keywords = projectProperties.get(OntologyConstants.KnoraBase.ProjectKeywords).map(_.head),
                logo = projectProperties.get(OntologyConstants.KnoraBase.ProjectLogo).map(_.head),
                institution = projectProperties.get(OntologyConstants.KnoraBase.BelongsToInstitution).map(_.head),
                ontologies = projectProperties.getOrElse(OntologyConstants.KnoraBase.ProjectOntology, Seq.empty[IRI]),
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
            askString <- Future(queries.sparql.v1.txt.checkProjectExistsByShortname(shortname = shortname).toString)
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
            askString <- Future(queries.sparql.v1.txt.checkProjectExistsByShortcode(shortcode = shortcode).toString)
            //_ = log.debug("projectExists - query: {}", askString)

            checkProjectExistsResponse <- (storeManager ? SparqlAskRequest(askString)).mapTo[SparqlAskResponse]
            result = checkProjectExistsResponse.result

        } yield result
    }
}
