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

package org.knora.webapi.responders.admin

import java.util.UUID

import akka.actor.Status
import akka.http.scaladsl.util.FastFuture
import akka.pattern._
import org.knora.webapi._
import org.knora.webapi.messages.admin.responder.ontologiesmessages.OntologyInfoShortADM
import org.knora.webapi.messages.admin.responder.projectsmessages._
import org.knora.webapi.messages.admin.responder.usersmessages.{UserADM, UserGetADM, UserInformationTypeADM}
import org.knora.webapi.messages.store.triplestoremessages._
import org.knora.webapi.messages.v1.responder.projectmessages._
import org.knora.webapi.responders.{IriLocker, Responder}
import org.knora.webapi.util.ActorUtil._
import org.knora.webapi.util.{KnoraIdUtil, SmartIri, StringFormatter}

import scala.concurrent.Future

/**
  * Returns information about Knora projects.
  */
class ProjectsResponderADM extends Responder {

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
        case ProjectsGetADM(requestingUser) => future2Message(sender(), projectsGetADM(requestingUser), log)
        case ProjectsGetRequestADM(requestingUser) => future2Message(sender(), projectsGetRequestADM(requestingUser), log)
        case ProjectGetADM(maybeIri, maybeShortname, maybeShortcode, requestingUser) => future2Message(sender(), projectGetADM(maybeIri, maybeShortname, maybeShortcode, requestingUser), log)
        case ProjectGetRequestADM(maybeIri, maybeShortname, maybeShortcode, requestingUser) => future2Message(sender(), projectGetRequestADM(maybeIri, maybeShortname, maybeShortcode, requestingUser), log)
        case ProjectMembersGetRequestADM(maybeIri, maybeShortname, maybeShortcode, requestingUser) => future2Message(sender(), projectMembersGetRequestADM(maybeIri, maybeShortname, maybeShortcode, requestingUser), log)
        case ProjectAdminMembersGetRequestADM(maybeIri, maybeShortname, maybeShortcode, requestingUser) => future2Message(sender(), projectAdminMembersGetRequestADM(maybeIri, maybeShortname, maybeShortcode, requestingUser), log)
        case ProjectCreateRequestADM(createRequest, requestingUser, apiRequestID) => future2Message(sender(), projectCreateRequestV1(createRequest, requestingUser, apiRequestID), log)
        case ProjectChangeRequestADM(projectIri, changeProjectRequest, requestingUser, apiRequestID) => future2Message(sender(), changeBasicInformationRequestADM(projectIri, changeProjectRequest, requestingUser, apiRequestID), log)
        case ProjectOntologyAddADM(projectIri, ontologyIri, requestingUser, apiRequestID) => future2Message(sender(), projectOntologyAddADM(projectIri, ontologyIri, requestingUser, apiRequestID), log)
        case ProjectOntologyRemoveADM(projectIri, ontologyIri, requestingUser, apiRequestID) => future2Message(sender(), projectOntologyRemoveADM(projectIri, ontologyIri, requestingUser, apiRequestID), log)
        case other => handleUnexpectedMessage(sender(), other, log, this.getClass.getName)
    }

    /**
      * Gets all the projects and returns them as a sequence containing [[ProjectADM]].
      *
      * @param requestingUser the user making the request.
      * @return all the projects as a sequence containing [[ProjectADM]].
      */
    private def projectsGetADM(requestingUser: UserADM): Future[Seq[ProjectADM]] = {

        for {
            sparqlQueryString <- Future(queries.sparql.admin.txt.getProjects(
                triplestore = settings.triplestoreType,
                maybeIri = None,
                maybeShortname = None,
                maybeShortcode = None
            ).toString())
            // _ = log.debug(s"getProjectsResponseV1 - query: $sparqlQueryString")

            projectsResponse <- (storeManager ? SparqlExtendedConstructRequest(sparqlQueryString)).mapTo[SparqlExtendedConstructResponse]
            // _ = log.debug(s"projectsGetADM - projectsResponse: $projectsResponse")

            statements = projectsResponse.statements.toList
            // _ = log.debug(s"projectsGetADM - statements: $statements")

            projects: Seq[ProjectADM] = statements.map {
                case (projectIri: IRI, propsMap: Map[IRI, Seq[LiteralV2]]) =>

                    val ontologyIris = propsMap.getOrElse(OntologyConstants.KnoraBase.ProjectOntology, Seq.empty[IRI]).map(_.asInstanceOf[IriLiteralV2].value)

                    val ontologyInfos: Seq[OntologyInfoShortADM] = ontologyIris.map { ontologyIri =>
                        OntologyInfoShortADM(
                            ontologyIri = ontologyIri,
                            ontologyName = SmartIri(ontologyIri).getOntologyName
                        )
                    }

                    ProjectADM(
                        id = projectIri,
                        shortname = propsMap.getOrElse(OntologyConstants.KnoraBase.ProjectShortname, throw InconsistentTriplestoreDataException(s"Project: $projectIri has no shortname defined.")).head.asInstanceOf[StringLiteralV2].value,
                        shortcode = propsMap.get(OntologyConstants.KnoraBase.ProjectShortcode).map(_.head.asInstanceOf[StringLiteralV2].value),
                        longname = propsMap.get(OntologyConstants.KnoraBase.ProjectLongname).map(_.head.asInstanceOf[StringLiteralV2].value),
                        description = propsMap.get(OntologyConstants.KnoraBase.ProjectDescription).map(_.head.asInstanceOf[StringLiteralV2].value),
                        keywords = propsMap.get(OntologyConstants.KnoraBase.ProjectKeywords).map(_.head.asInstanceOf[StringLiteralV2].value),
                        logo = propsMap.get(OntologyConstants.KnoraBase.ProjectLogo).map(_.head.asInstanceOf[StringLiteralV2].value),
                        institution = propsMap.get(OntologyConstants.KnoraBase.BelongsToInstitution).map(_.head.asInstanceOf[IriLiteralV2].value),
                        ontologies = ontologyInfos,
                        status = propsMap.getOrElse(OntologyConstants.KnoraBase.Status, throw InconsistentTriplestoreDataException(s"Project: $projectIri has no status defined.")).head.asInstanceOf[BooleanLiteralV2].value,
                        selfjoin = propsMap.getOrElse(OntologyConstants.KnoraBase.HasSelfJoinEnabled, throw InconsistentTriplestoreDataException(s"Project: $projectIri has no hasSelfJoinEnabled defined.")).head.asInstanceOf[BooleanLiteralV2].value
                    )
            }

        } yield projects
    }

    /**
      * Gets all the projects and returns them as a [[ProjectsResponseV1]].
      *
      * @param requestingUser the user that is making the request.
      * @return all the projects as a [[ProjectsResponseV1]].
      * @throws NotFoundException if no projects are found.
      */
    private def projectsGetRequestADM(requestingUser: UserADM): Future[ProjectsGetResponseADM] = {

        // log.debug("projectsGetRequestADM")

        // ToDo: What permissions should be required, if any?

        for {
            projects <- projectsGetADM(requestingUser = requestingUser)

            result = if (projects.nonEmpty) {
                ProjectsGetResponseADM(
                    projects = projects
                )
            } else {
                throw NotFoundException(s"No projects found")
            }

        } yield result
    }



    /**
      * Gets the project with the given project IRI, shortname, or shortcode and returns the information as a [[ProjectADM]].
      *
      * @param maybeIri           the IRI of the project.
      * @param maybeShortname the project's short name.
      * @param maybeShortcode the project's shortcode.
      * @param requestingUser the user making the request.
      * @return information about the project as a [[ProjectInfoV1]].
      */
    private def projectGetADM(maybeIri: Option[IRI], maybeShortname: Option[String], maybeShortcode: Option[String], requestingUser: UserADM): Future[Option[ProjectADM]] = {

        // log.debug("projectGetADM - projectIRI: {}", projectIri)

        for {
            sparqlQuery <- Future(queries.sparql.admin.txt.getProjects(
                triplestore = settings.triplestoreType,
                maybeIri = maybeIri,
                maybeShortname = maybeShortname,
                maybeShortcode = maybeShortcode
            ).toString())
            projectResponse <- (storeManager ? SparqlExtendedConstructRequest(sparqlQuery)).mapTo[SparqlExtendedConstructResponse]

            projectADM = if (projectResponse.statements.nonEmpty) {
                Some(statements2ProjectADM(statements = projectResponse.statements.head, requestingUser))
            } else {
                None
            }

            // _ = log.debug("projectGetADM - projectADM: {}", projectADM)

        } yield projectADM
    }

    /**
      * Gets the project with the given project IRI, shortname, or shortcode and returns the information
      * as a [[ProjectGetResponseADM]].
      *
      * @param maybeIri       the IRI of the project.
      * @param maybeShortname the project's short name.
      * @param maybeShortcode the project's shortcode.
      * @param requestingUser the user making the request.
      * @return information about the project as a [[ProjectInfoResponseV1]].
      * @throws NotFoundException when no project for the given IRI can be found
      */
    private def projectGetRequestADM(maybeIri: Option[IRI], maybeShortname: Option[String], maybeShortcode: Option[String], requestingUser: UserADM): Future[ProjectGetResponseADM] = {

        // log.debug("projectGetRequestADM - maybeIri: {}, maybeShortname: {}, maybeShortcode: {}", maybeIri, maybeShortname, maybeShortcode)

        for {
            maybeProject: Option[ProjectADM] <- projectGetADM(maybeIri, maybeShortname, maybeShortcode, requestingUser)
            project = maybeProject match {
                case Some(p) => p
                case None => throw NotFoundException(s"Project '${Seq(maybeIri, maybeShortname, maybeShortcode).flatten.head}' not found")
            }
        } yield ProjectGetResponseADM(
            project = project
        )
    }

    /**
      * Gets the members of a project with the given IRI, shortname, oder shortcode. Returns an empty list
      * if none are found.
      *
      * @param maybeIri       the IRI of the project.
      * @param maybeShortname the project's short name.
      * @param maybeShortcode the project's shortcode.
      * @param requestingUser the user making the request.
      * @return the members of a project as a [[ProjectMembersGetResponseV1]]
      */
    private def projectMembersGetRequestADM(maybeIri: Option[IRI], maybeShortname: Option[String], maybeShortcode: Option[String], requestingUser: UserADM): Future[ProjectMembersGetResponseADM] = {

        // log.debug("projectMembersGetRequestADM - maybeIri: {}, maybeShortname: {}, maybeShortcode: {}", maybeIri, maybeShortname, maybeShortcode)

        for {

            /* Verify that the project exists. */
            exists <- projectExists(maybeIri, maybeShortname, maybeShortcode)
            _ = if (!exists) {
                throw NotFoundException(s"Project '${Seq(maybeIri, maybeShortname, maybeShortcode).flatten.head}' not found.")
            }

            sparqlQueryString <- Future(queries.sparql.admin.txt.getProjectMembers(
                triplestore = settings.triplestoreType,
                maybeIri = maybeIri,
                maybeShortname = maybeShortname,
                maybeShortcode = maybeShortcode
            ).toString())
            //_ = log.debug(s"projectMembersGetRequestADM - query: $sparqlQueryString")

            projectMembersResponse <- (storeManager ? SparqlExtendedConstructRequest(sparqlQueryString)).mapTo[SparqlExtendedConstructResponse]

            statements = projectMembersResponse.statements.toList

            // _ = log.debug(s"projectMembersGetRequestADM - statements: {}", MessageUtil.toSource(statements))

            // get project member IRI from results rows
            userIris: Seq[IRI] = if (statements.nonEmpty) {
                statements.map(_._1)
            } else {
                Seq.empty[IRI]
            }

            maybeUserFutures: Seq[Future[Option[UserADM]]] = userIris.map {
                userIri => (responderManager ? UserGetADM(maybeIri = Some(userIri), maybeEmail = None, userInformationTypeADM = UserInformationTypeADM.RESTRICTED, requestingUser = KnoraSystemInstances.Users.SystemUser)).mapTo[Option[UserADM]]
            }
            maybeUsers: Seq[Option[UserADM]] <- Future.sequence(maybeUserFutures)
            users: Seq[UserADM] = maybeUsers.flatten

            // _ = log.debug(s"projectMembersGetRequestADM - users: {}", users)

        } yield ProjectMembersGetResponseADM(members = users)
    }

    /**
      * Gets the admin members of a project with the given IRI, shortname, or shortcode. Returns an empty list
      * if none are found
      *
      * @param maybeIri       the IRI of the project.
      * @param maybeShortname the project's short name.
      * @param maybeShortcode the project's shortcode.
      * @param requestingUser the user making the request.
      * @return the members of a project as a [[ProjectMembersGetResponseADM]]
      */
    private def projectAdminMembersGetRequestADM(maybeIri: Option[IRI], maybeShortname: Option[String], maybeShortcode: Option[String], requestingUser: UserADM): Future[ProjectAdminMembersGetResponseADM] = {

        // log.debug("projectAdminMembersGetRequestADM - maybeIri: {}, maybeShortname: {}, maybeShortcode: {}", maybeIri, maybeShortname, maybeShortcode)

        for {

            /* Verify that the project exists. */
            exists <- projectExists(maybeIri, maybeShortname, maybeShortcode)
            _ = if (!exists) {
                throw NotFoundException(s"Project '${Seq(maybeIri, maybeShortname, maybeShortcode).flatten.head}' not found.")
            }

            sparqlQueryString <- Future(queries.sparql.admin.txt.getProjectAdminMembers(
                triplestore = settings.triplestoreType,
                maybeIri = maybeIri,
                maybeShortname = maybeShortname,
                maybeShortcode = maybeShortcode
            ).toString())
            //_ = log.debug(s"projectAdminMembersByIRIGetRequestV1 - query: $sparqlQueryString")

            projectMembersResponse <- (storeManager ? SparqlExtendedConstructRequest(sparqlQueryString)).mapTo[SparqlExtendedConstructResponse]
            //_ = log.debug(s"projectAdminMembersByIRIGetRequestV1 - result: ${MessageUtil.toSource(projectMembersResponse)}")

            statements = projectMembersResponse.statements.toList

            // get project member IRI from results rows
            userIris: Seq[IRI] = if (statements.nonEmpty) {
                statements.map(_._1)
            } else {
                Seq.empty[IRI]
            }

            maybeUserFutures: Seq[Future[Option[UserADM]]] = userIris.map {
                userIri => (responderManager ? UserGetADM(maybeIri = Some(userIri), maybeEmail = None, userInformationTypeADM = UserInformationTypeADM.RESTRICTED, requestingUser = KnoraSystemInstances.Users.SystemUser)).mapTo[Option[UserADM]]
            }
            maybeUsers: Seq[Option[UserADM]] <- Future.sequence(maybeUserFutures)
            users: Seq[UserADM] = maybeUsers.flatten

            //_ = log.debug(s"projectMembersGetRequestADM - users: $users")

        } yield ProjectAdminMembersGetResponseADM(members = users)
    }

    /**
      * Creates a project.
      *
      * @param createRequest  the new project's information.
      * @param requestingUser the user that is making the request.
      * @param apiRequestID   the unique api request ID.
      * @return a [[ProjectOperationResponseADM]].
      * @throws ForbiddenException in the case that the user is not allowed to perform the operation.
      * @throws DuplicateValueException in the case when either the shortname or shortcode are not unique.
      * @throws BadRequestException in the case when the shortcode is invalid.
      */
    private def projectCreateRequestV1(createRequest: CreateProjectApiRequestADM, requestingUser: UserADM, apiRequestID: UUID): Future[ProjectOperationResponseADM] = {

        // log.debug("projectCreateRequestV1 - createRequest: {}", createRequest)

        def projectCreateTask(createRequest: CreateProjectApiRequestADM, requestingUser: UserADM): Future[ProjectOperationResponseADM] = for {
            // check if required properties are not empty
            _ <- Future(if (createRequest.shortname.isEmpty) throw BadRequestException("'Shortname' cannot be empty"))

            // check if the requesting user is allowed to create project
            _ = if (!requestingUser.permissions.isSystemAdmin) {
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
                val shortcode = StringFormatter.getGeneralInstance.validateProjectShortcode(
                    createRequest.shortcode.get,
                    errorFun = throw BadRequestException(s"The supplied short code: '${createRequest.shortcode.get}' is not valid.")
                )
                projectByShortcodeExists(shortcode)
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
            createNewProjectSparqlString = queries.sparql.admin.txt.createNewProject(
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
            sparqlQuery = queries.sparql.admin.txt.getProjects(
                triplestore = settings.triplestoreType,
                maybeIri = Some(newProjectIRI),
                maybeShortcode = None,
                maybeShortname = None
            ).toString
            projectResponse <- (storeManager ? SparqlExtendedConstructRequest(sparqlQuery)).mapTo[SparqlExtendedConstructResponse]

            projectADM = if (projectResponse.statements.nonEmpty) {
                statements2ProjectADM(statements = projectResponse.statements.head, requestingUser)
            } else {
                throw UpdateNotPerformedException(s"Project $newProjectIRI was not created. Please report this as a possible bug.")
            }

        } yield ProjectOperationResponseADM(project = projectADM)

        for {
            // run user creation with an global IRI lock
            taskResult <- IriLocker.runWithIriLock(
                apiRequestID,
                PROJECTS_GLOBAL_LOCK_IRI,
                () => projectCreateTask(createRequest, requestingUser)
            )
        } yield taskResult
    }

    /**
      * Changes project's basic information.
      *
      * @param projectIri the IRI of the project.
      * @param changeProjectRequest the change payload.
      * @param requestingUser the user making the request.
      * @param apiRequestID the unique api request ID.
      * @return a [[ProjectOperationResponseADM]].
      * @throws ForbiddenException in the case that the user is not allowed to perform the operation.
      */
    private def changeBasicInformationRequestADM(projectIri: IRI, changeProjectRequest: ChangeProjectApiRequestADM, requestingUser: UserADM, apiRequestID: UUID): Future[ProjectOperationResponseADM] = {

        //log.debug(s"changeBasicInformationRequestV1: changeProjectRequest: {}", changeProjectRequest)

        /**
          * The actual change project task run with an IRI lock.
          */
        def changeProjectTask(projectIri: IRI, changeProjectRequest: ChangeProjectApiRequestADM, requestingUser: UserADM): Future[ProjectOperationResponseADM] = for {

            _ <- Future(
                // check if necessary information is present
                if (projectIri.isEmpty) throw BadRequestException("Project IRI cannot be empty")
            )

            // check if the requesting user is allowed to perform updates
            _ = if (!requestingUser.permissions.isProjectAdmin(projectIri) && !requestingUser.permissions.isSystemAdmin) {
                // not a project admin and not a system admin
                throw ForbiddenException("Project's information can only be changed by a project or system admin.")
            }

            // create the update request
            projectUpdatePayload = ProjectUpdatePayloadADM(
                longname = changeProjectRequest.longname,
                description = changeProjectRequest.description,
                keywords = changeProjectRequest.keywords,
                logo = changeProjectRequest.logo,
                institution = changeProjectRequest.institution,
                status = changeProjectRequest.status,
                selfjoin = changeProjectRequest.selfjoin
            )

            result <- updateProjectADM(projectIri, projectUpdatePayload, requestingUser = KnoraSystemInstances.Users.SystemUser)

        } yield result

        for {
            // run the change status task with an IRI lock
            taskResult <- IriLocker.runWithIriLock(
                apiRequestID,
                projectIri,
                () => changeProjectTask(projectIri, changeProjectRequest, requestingUser)
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
    private def projectOntologyAddADM(projectIri: IRI, ontologyIri: IRI, requestingUser: UserADM, apiRequestID: UUID): Future[ProjectADM] = {

        // log.debug("projectOntologyAddV1 - projectIri: {}, ontologyIri: {}", projectIri, ontologyIri)

        /**
          * The actual ontology add task run with an IRI lock.
          */
        def ontologyAddTask(projectIri: IRI, ontologyIri: IRI): Future[ProjectADM] = for {

            _ <- Future(
                // check if necessary information is present
                if (projectIri.isEmpty) throw BadRequestException("Project IRI cannot be empty")
            )

            maybeProject <- projectGetADM(maybeIri = Some(projectIri), maybeShortname = None, maybeShortcode = None, requestingUser = KnoraSystemInstances.Users.SystemUser)

            // _ = log.debug("projectOntologyAddV1 - ontologyAddTask - maybeProjectInfo: {}", maybeProjectInfo)

            ontologies: Seq[IRI] = maybeProject match {
                case Some(project) => project.ontologies.map(_.ontologyIri.toString) :+ ontologyIri
                case None => throw NotFoundException(s"Project '$projectIri' not found. Aborting update request.")
            }

            projectUpdatePayload = ProjectUpdatePayloadADM(ontologies = Some(ontologies))

            result <- updateProjectADM(projectIri, projectUpdatePayload, requestingUser = KnoraSystemInstances.Users.SystemUser)
        } yield result.project

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
    private def projectOntologyRemoveADM(projectIri: IRI, ontologyIri: IRI, requestingUser: UserADM, apiRequestID: UUID): Future[ProjectADM] = {

        // log.debug("projectOntologyRemoveV1 - projectIri: {}, ontologyIri: {}", projectIri, ontologyIri)

        /**
          * The actual ontology remove task run with an IRI lock.
          */
        def ontologyRemoveTask(projectIri: IRI, ontologyIri: IRI): Future[ProjectADM] = for {

            _ <- Future(
                // check if necessary information is present
                if (projectIri.isEmpty) throw BadRequestException("Project IRI cannot be empty")
            )

            maybeProjectADM <- projectGetADM(maybeIri = Some(projectIri), maybeShortname = None, maybeShortcode = None, requestingUser = KnoraSystemInstances.Users.SystemUser)

            // _ = log.debug("projectOntologyRemoveV1 - ontologyRemoveTask - maybeProjectInfo: {}", maybeProjectInfo)

            ontologies: Seq[IRI] = maybeProjectADM match {
                case Some(pi) => pi.ontologies.map(_.ontologyIri.toString).filterNot(_.equals(ontologyIri))
                case None => throw NotFoundException(s"Project '$projectIri' not found. Aborting update request.")
            }

            projectUpdatePayload = ProjectUpdatePayloadADM(ontologies = Some(ontologies))

            result <- updateProjectADM(projectIri, projectUpdatePayload, requestingUser = KnoraSystemInstances.Users.SystemUser)
        } yield result.project

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
      * @return a [[ProjectOperationResponseADM]].
      * @throws NotFoundException in the case that the project's IRI is not found.
      */
    private def updateProjectADM(projectIri: IRI, projectUpdatePayload: ProjectUpdatePayloadADM, requestingUser: UserADM): Future[ProjectOperationResponseADM] = {

        // log.debug("updateProjectADM - projectIri: {}, projectUpdatePayload: {}", projectIri, projectUpdatePayload)

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
            updateProjectSparqlString <- Future(queries.sparql.admin.txt.updateProject(
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

            // _ = log.debug(s"updateProjectADM - query: {}", updateProjectSparqlString)

            updateProjectResponse <- (storeManager ? SparqlUpdateRequest(updateProjectSparqlString)).mapTo[SparqlUpdateResponse]

            /* Verify that the project was updated. */
            maybeUpdatedProject <- projectGetADM(maybeIri = Some(projectIri), maybeShortname = None, maybeShortcode = None, requestingUser = KnoraSystemInstances.Users.SystemUser)
            updatedProject: ProjectADM = maybeUpdatedProject.getOrElse(throw UpdateNotPerformedException("Project was not updated. Please report this as a possible bug."))

            // _ = log.debug("updateProjectADM - projectUpdatePayload: {} /  updatedProject: {}", projectUpdatePayload, updatedProject)

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
                if (updatedProject.ontologies.map(_.ontologyIri.toString) != projectUpdatePayload.ontologies.get) throw UpdateNotPerformedException("Project's 'ontologies' where not updated. Please report this as a possible bug.")
            }

            _ = if (projectUpdatePayload.status.isDefined) {
                if (updatedProject.status != projectUpdatePayload.status.get) throw UpdateNotPerformedException("Project's 'status' was not updated. Please report this as a possible bug.")
            }

            _ = if (projectUpdatePayload.selfjoin.isDefined) {
                if (updatedProject.selfjoin != projectUpdatePayload.selfjoin.get) throw UpdateNotPerformedException("Project's 'selfjoin' status was not updated. Please report this as a possible bug.")
            }

            // create the project operation response
            projectOperationResponseV1 = ProjectOperationResponseADM(project = updatedProject)
        } yield projectOperationResponseV1

    }


    ////////////////////
    // Helper Methods //
    ////////////////////

    /**
      * Helper method that turns SPARQL result rows into a [[ProjectInfoV1]].
      *
      * @param statements results from the SPARQL query representing information about the project.
      * @param requestingUser     the user making the request.
      * @return a [[ProjectADM]] representing information about project.
      */
    private def statements2ProjectADM(statements: (IRI, Map[IRI, Seq[LiteralV2]]), requestingUser: UserADM): ProjectADM = {

        // log.debug("statements2ProjectADM - statements: {}", statements)

        val projectIri: IRI = statements._1
        val propsMap: Map[IRI, Seq[LiteralV2]] = statements._2

        val ontologyIris = propsMap.getOrElse(OntologyConstants.KnoraBase.ProjectOntology, Seq.empty[IRI]).map(_.asInstanceOf[IriLiteralV2].value)

        val ontologyInfos: Seq[OntologyInfoShortADM] = ontologyIris.map { ontologyIri =>
            OntologyInfoShortADM(
                ontologyIri = ontologyIri,
                ontologyName = SmartIri(ontologyIri).getOntologyName
            )
        }

        ProjectADM(
            id = projectIri,
            shortname = propsMap.getOrElse(OntologyConstants.KnoraBase.ProjectShortname, throw InconsistentTriplestoreDataException(s"Project: $projectIri has no shortname defined.")).head.asInstanceOf[StringLiteralV2].value,
            shortcode = propsMap.get(OntologyConstants.KnoraBase.ProjectShortcode).map(_.head.asInstanceOf[StringLiteralV2].value),
            longname = propsMap.get(OntologyConstants.KnoraBase.ProjectLongname).map(_.head.asInstanceOf[StringLiteralV2].value),
            description = propsMap.get(OntologyConstants.KnoraBase.ProjectDescription).map(_.head.asInstanceOf[StringLiteralV2].value),
            keywords = propsMap.get(OntologyConstants.KnoraBase.ProjectKeywords).map(_.head.asInstanceOf[StringLiteralV2].value),
            logo = propsMap.get(OntologyConstants.KnoraBase.ProjectLogo).map(_.head.asInstanceOf[StringLiteralV2].value),
            institution = propsMap.get(OntologyConstants.KnoraBase.BelongsToInstitution).map(_.head.asInstanceOf[IriLiteralV2].value),
            ontologies = ontologyInfos,
            status = propsMap.getOrElse(OntologyConstants.KnoraBase.Status, throw InconsistentTriplestoreDataException(s"Project: $projectIri has no status defined.")).head.asInstanceOf[BooleanLiteralV2].value,
            selfjoin = propsMap.getOrElse(OntologyConstants.KnoraBase.HasSelfJoinEnabled, throw InconsistentTriplestoreDataException(s"Project: $projectIri has no hasSelfJoinEnabled defined.")).head.asInstanceOf[BooleanLiteralV2].value
        )
    }

    /**
      * Helper method for checking if a project exists.
      *
      * @param maybeIri the IRI of the project.
      * @param maybeShortname the shortname of the project.
      * @param maybeShortcode the shortcode of the project.
      * @return a [[Boolean]].
      */
    def projectExists(maybeIri: Option[IRI], maybeShortname: Option[String], maybeShortcode: Option[String]): Future[Boolean] = {

        if (maybeIri.nonEmpty) {
            projectByIriExists(maybeIri.get)
        } else if (maybeShortname.nonEmpty) {
            projectByShortnameExists(maybeShortname.get)
        } else if (maybeShortcode.nonEmpty) {
            projectByShortcodeExists(maybeShortcode.get)
        } else {
            FastFuture.successful(false)
        }
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
