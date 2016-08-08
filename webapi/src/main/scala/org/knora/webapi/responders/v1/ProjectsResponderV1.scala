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

import akka.actor.Status
import akka.pattern._
import org.knora.webapi._
import org.knora.webapi.messages.v1.responder.permissionmessages.{TemplatePermissionsCreateRequestV1, TemplatePermissionsCreateResponseV1}
import org.knora.webapi.messages.v1.responder.projectmessages._
import org.knora.webapi.messages.v1.responder.usermessages.UserProfileV1
import org.knora.webapi.messages.v1.store.triplestoremessages._
import org.knora.webapi.util.ActorUtil._
import org.knora.webapi.util.{KnoraIriUtil, SparqlUtil}

import scala.concurrent.Future


/**
  * Returns information about Knora projects.
  */
class ProjectsResponderV1 extends ResponderV1 {

    // Creates IRIs for new Knora user objects.
    val knoraIriUtil = new KnoraIriUtil

    /**
      * Receives a message extending [[ProjectsResponderRequestV1]], and returns an appropriate response message, or
      * [[Status.Failure]]. If a serious error occurs (i.e. an error that isn't the client's fault), this
      * method first returns `Failure` to the sender, then throws an exception.
      */
    def receive = {
        case ProjectsGetRequestV1(infoType, userProfile) => future2Message(sender(), getProjectsResponseV1(infoType, userProfile), log)
        case ProjectInfoByIRIGetRequest(iri, infoType, userProfile) => future2Message(sender(), getProjectInfoByIRIGetRequest(iri, infoType, userProfile), log)
        case ProjectInfoByShortnameGetRequest(shortname, infoType, userProfile) => future2Message(sender(), getProjectInfoByShortnameGetRequest(shortname, infoType, userProfile), log)
        case ProjectCreateRequestV1(newProjectDataV1: NewProjectDataV1, userProfileV1) => future2Message(sender(), createNewProjectV1(newProjectDataV1, userProfileV1), log)
        case other => sender ! Status.Failure(UnexpectedMessageException(s"Unexpected message $other of type ${other.getClass.getCanonicalName}"))
    }

    /**
      * Gets permissions for the current user on the given project.
      *
      * @param projectIri the Iri of the project.
      * @param propertiesForProject assertions containing permissions on the project.
      * @param userProfile the user that is making the request.
      * @return permission level of the current user on the project.
      */
    private def getUserPermissionV1ForProject(projectIri: IRI, propertiesForProject: Map[IRI, String], userProfile: UserProfileV1): Option[Int] = {

        // propertiesForProject must contain an owner for the project (knora-base:attachedToUser).
        propertiesForProject.get(OntologyConstants.KnoraBase.AttachedToUser) match {
            case Some(user) => // add statement that `PermissionUtil.getUserPermissionV1` requires but is not present in the data for projects.
                val assertionsForProject: Seq[(IRI, IRI)] = (OntologyConstants.KnoraBase.AttachedToProject, projectIri) +: propertiesForProject.toVector
                PermissionUtilV1.getUserPermissionV1(projectIri, assertionsForProject, userProfile)
            case None => None // TODO: this is temporary to prevent PermissionUtil.getUserPermissionV1 from failing because owner id is missing in the data for project. See issue 1.
        }
    }

    /**
      * Gets all the projects and returns them as a [[ProjectsResponseV1]].
      *
      * @param userProfile the profile of the user that is making the request.
      * @return all the projects as a [[ProjectsResponseV1]].
      */
    private def getProjectsResponseV1(infoType: ProjectInfoType.Value, userProfile: Option[UserProfileV1]): Future[ProjectsResponseV1] = {

        for {
            sparqlQueryString <- Future(queries.sparql.v1.txt.getProjects(
                triplestore = settings.triplestoreType
            ).toString())
            //_ = log.debug(s"getProjectsResponseV1 - query: $sparqlQueryString")

            projectsResponse <- (storeManager ? SparqlSelectRequest(sparqlQueryString)).mapTo[SparqlSelectResponse]
            //_ = log.debug(s"getProjectsResponseV1 - result: ${MessageUtil.toSource(projectsResponse)}")

            projectsResponseRows: Seq[VariableResultsRow] = projectsResponse.results.bindings

            projectsWithProperties: Map[String, Map[String, String]] = projectsResponseRows.groupBy(_.rowMap("s")).map {
                case (projIri: String, rows: Seq[VariableResultsRow]) => (projIri, rows.map {
                    case row => (row.rowMap("p"), row.rowMap("o"))
                }.toMap)
            }
            //_ = log.debug(s"getProjectsResponseV1 - projectsWithProperties: ${MessageUtil.toSource(projectsWithProperties)}")

            projects = projectsWithProperties.map {
                case (projIri: String, propsMap: Map[String, String]) =>

                    val rightsInProject = userProfile match {
                        case Some(profile) => getUserPermissionV1ForProject(projectIri = projIri, propertiesForProject = propsMap, profile)
                        case None => None
                    }

                    ProjectInfoV1(
                        id = projIri,
                        shortname = propsMap.get(OntologyConstants.KnoraBase.ProjectShortname).get,
                        longname = propsMap.get(OntologyConstants.KnoraBase.ProjectLongname),
                        description = propsMap.get(OntologyConstants.KnoraBase.ProjectDescription),
                        keywords = propsMap.get(OntologyConstants.KnoraBase.ProjectKeywords),
                        projectOntologyGraph = propsMap.get(OntologyConstants.KnoraBase.ProjectOntologyGraph).get,
                        projectDataGraph = propsMap.get(OntologyConstants.KnoraBase.ProjectDataGraph).get,
                        logo = propsMap.get(OntologyConstants.KnoraBase.ProjectLogo),
                        basepath = propsMap.get(OntologyConstants.KnoraBase.ProjectBasepath),
                        isActiveProject = propsMap.get(OntologyConstants.KnoraBase.IsActiveProject).map(_.toBoolean),
                        hasSelfJoinEnabled = propsMap.get(OntologyConstants.KnoraBase.HasSelfJoinEnabled).map(_.toBoolean),
                        rights = None
                    )
            }.toVector
        } yield ProjectsResponseV1(
            projects = projects,
            userdata = userProfile match {
                case Some(profile) => Some(profile.userData)
                case None => None
            }
        )
    }

    /**
      * Gets the project with the given project Iri and returns the information as a [[ProjectInfoResponseV1]].
      *
      * @param projectIRI the Iri of the project requested.
      * @param infoType type request: either short or full.
      * @param userProfile the profile of user that is making the request.
      * @return information about the project as a [[ProjectInfoResponseV1]].
      */
    private def getProjectInfoByIRIGetRequest(projectIRI: IRI, infoType: ProjectInfoType.Value, userProfile: Option[UserProfileV1] = None): Future[ProjectInfoResponseV1] = {
        for {
            sparqlQuery <- Future(queries.sparql.v1.txt.getProjectByIri(
                triplestore = settings.triplestoreType,
                projectIri = projectIRI
            ).toString())
            projectResponse <- (storeManager ? SparqlSelectRequest(sparqlQuery)).mapTo[SparqlSelectResponse]

            _ = if (projectResponse.results.bindings.isEmpty) {
                throw NotFoundException(s"Project '$projectIRI' not found")
            }

            projectInfo = createProjectInfoV1FromProjectResponse(projectResponse = projectResponse.results.bindings, projectIri = projectIRI, infoType = infoType, userProfile)

        } yield ProjectInfoResponseV1(
            project_info = projectInfo,
            userdata = userProfile match {
                case Some(profile) => Some(profile.userData)
                case None => None
            }
        )
    }

    /**
      * Gets the project with the given shortname and returns the information as a [[ProjectInfoResponseV1]].
      *
      * @param shortName the shortname of the project requested.
      * @param infoType type request: either short or full.
      * @param userProfile the profile of user that is making the request.
      * @return information about the project as a [[ProjectInfoResponseV1]].
      */
    private def getProjectInfoByShortnameGetRequest(shortName: String, infoType: ProjectInfoType.Value, userProfile: Option[UserProfileV1]): Future[ProjectInfoResponseV1] = {
        for {
            sparqlQueryString <- Future(queries.sparql.v1.txt.getProjectByShortname(
                triplestore = settings.triplestoreType,
                shortname = SparqlUtil.any2SparqlLiteral(shortName)
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

            projectInfo = createProjectInfoV1FromProjectResponse(projectResponse = projectResponse.results.bindings, projectIri = projectIri, infoType = infoType, userProfile)

        } yield ProjectInfoResponseV1(
            project_info = projectInfo,
            userdata = userProfile match {
                case Some(profile) => Some(profile.userData)
                case None => None
            }
        )
    }

    private def createNewProjectV1(newProjectDataV1: NewProjectDataV1, userProfileV1: UserProfileV1): Future[ProjectOperationResponseV1] = {

        for {
            a <- Future("")

            // check if required properties are not empty
            _ = if (newProjectDataV1.shortname.isEmpty) throw BadRequestException("'Shortname' cannot be empty")

            // check if the supplied 'shortname' for the new project is unique, i.e. not already registered
            sparqlQueryString = queries.sparql.v1.txt.getProjectByShortname(
                triplestore = settings.triplestoreType,
                shortname = SparqlUtil.any2SparqlLiteral(newProjectDataV1.shortname)
            ).toString()
            //_ = log.debug(s"createNewProjectV1 - check duplicate shortname query: $sparqlQueryString")

            projectInfoQueryResponse <- (storeManager ? SparqlSelectRequest(sparqlQueryString)).mapTo[SparqlSelectResponse]
            projectResponse = projectInfoQueryResponse.results.bindings
            //_ = log.debug(s"createNewProjectV1 - check duplicate shortname response:  ${MessageUtil.toSource(projectInfoQueryResponse)}")

            _ = if (projectResponse.nonEmpty) {
                throw DuplicateValueException(s"Project with the shortname: '${newProjectDataV1.shortname}' already exists")
            }

            projectIRI = knoraIriUtil.makeRandomProjectIri
            projectOntologyGraphString = "http://www.knora.org/ontology/" + newProjectDataV1.shortname
            projectDataGraphString = "http://www.knora.org/data/" + newProjectDataV1.shortname

            // Create the new project.
            createNewProjectSparqlString = queries.sparql.v1.txt.createNewProject(
                adminNamedGraphIri = "http://www.knora.org/data/admin",
                triplestore = settings.triplestoreType,
                projectIri = projectIRI,
                projectClassIri = OntologyConstants.KnoraBase.KnoraProject,
                shortname = SparqlUtil.any2SparqlLiteral(newProjectDataV1.shortname),
                longname = SparqlUtil.any2SparqlLiteral(newProjectDataV1.longname),
                keywords = SparqlUtil.any2SparqlLiteral(newProjectDataV1.keywords),
                logo = SparqlUtil.any2SparqlLiteral(newProjectDataV1.logo),
                basepath = SparqlUtil.any2SparqlLiteral(newProjectDataV1.basepath),
                isActiveProject = SparqlUtil.any2SparqlLiteral(newProjectDataV1.isActiveProject),
                hasSelfJoinEnabled = SparqlUtil.any2SparqlLiteral(newProjectDataV1.hasSelfJoinEnabled),
                projectOntologyGraph = SparqlUtil.any2SparqlLiteral(projectOntologyGraphString),
                projectDataGraph = SparqlUtil.any2SparqlLiteral(projectDataGraphString)
            ).toString
            //_ = log.debug(s"createNewProjectV1 - update query: $createNewProjectSparqlString")

            createResourceResponse <- (storeManager ? SparqlUpdateRequest(createNewProjectSparqlString)).mapTo[SparqlUpdateResponse]


            // Verify that the project was created.
            sparqlQuery = queries.sparql.v1.txt.getProjectByIri(
                triplestore = settings.triplestoreType,
                projectIri = projectIRI
            ).toString
            projectInfoQueryResponse <- (storeManager ? SparqlSelectRequest(sparqlQuery)).mapTo[SparqlSelectResponse]
            projectResponse = projectInfoQueryResponse.results.bindings
            //_ = log.debug(s"createNewProjectV1 - verify query response: ${MessageUtil.toSource(projectResponse)}")

            _ = if (projectResponse.isEmpty) {
                throw UpdateNotPerformedException(s"Project $projectIRI was not created. Please report this as a possible bug.")
            }

            // create template permissions
            templatePermissionsCreateResponse <- (responderManager ? TemplatePermissionsCreateRequestV1(projectIRI, newProjectDataV1.permissionsTemplate, userProfileV1)).mapTo[TemplatePermissionsCreateResponseV1]

            _ = if (!templatePermissionsCreateResponse.success) {
                //Todo: Handle permission creation problem
                log.error(templatePermissionsCreateResponse.msg)
            }

            // create the project info
            newProjectInfo = createProjectInfoV1FromProjectResponse(projectResponse, projectIRI, ProjectInfoType.FULL, Some(userProfileV1))

            // create the project operation response
            projectOperationResponseV1 = ProjectOperationResponseV1(newProjectInfo, userProfileV1.userData)

        } yield projectOperationResponseV1
    }

    private def updateProjectV1(userProfileV1: UserProfileV1): Future[ProjectInfoResponseV1] = ???

    ////////////////////
    // Helper Methods //
    ////////////////////

    /**
      * Helper method that turns SPARQL result rows into a [[ProjectInfoV1]].
      *
      * @param projectResponse results from the SPARQL query representing information about the project.
      * @param projectIri the Iri of the project the querid information belong to.
      * @param infoType type request: either short or full.
      * @param userProfile the profile of user that is making the request.
      * @return a [[ProjectInfoV1]] representing information about project.
      */
    private def createProjectInfoV1FromProjectResponse(projectResponse: Seq[VariableResultsRow], projectIri: IRI, infoType: ProjectInfoType.Value, userProfile: Option[UserProfileV1]): ProjectInfoV1 = {

        if (projectResponse.nonEmpty) {

            val projectProperties = projectResponse.foldLeft(Map.empty[IRI, String]) {
                case (acc, row: VariableResultsRow) =>
                    acc + (row.rowMap("p") -> row.rowMap("o"))
            }

            val rightsInProject = userProfile match {
                case Some(profile) => getUserPermissionV1ForProject(projectIri = projectIri, propertiesForProject = projectProperties, profile)
                case None => None
            }

            infoType match {
                case ProjectInfoType.FULL =>
                    ProjectInfoV1(
                        id = projectIri,
                        shortname = projectProperties.get(OntologyConstants.KnoraBase.ProjectShortname).get,
                        longname = projectProperties.get(OntologyConstants.KnoraBase.ProjectLongname),
                        description = projectProperties.get(OntologyConstants.KnoraBase.ProjectDescription),
                        keywords = projectProperties.get(OntologyConstants.KnoraBase.ProjectKeywords),
                        projectOntologyGraph = projectProperties.get(OntologyConstants.KnoraBase.ProjectOntologyGraph).get,
                        projectDataGraph = projectProperties.get(OntologyConstants.KnoraBase.ProjectDataGraph).get,
                        logo = projectProperties.get(OntologyConstants.KnoraBase.ProjectLogo),
                        basepath = projectProperties.get(OntologyConstants.KnoraBase.ProjectBasepath),
                        isActiveProject = projectProperties.get(OntologyConstants.KnoraBase.IsActiveProject).map(_.toBoolean),
                        hasSelfJoinEnabled = projectProperties.get(OntologyConstants.KnoraBase.HasSelfJoinEnabled).map(_.toBoolean),
                        rights = rightsInProject
                    )
                case ProjectInfoType.SHORT | _ =>
                    ProjectInfoV1(
                        id = projectIri,
                        shortname = projectProperties.getOrElse(OntologyConstants.KnoraBase.ProjectShortname, ""),
                        longname = projectProperties.get(OntologyConstants.KnoraBase.ProjectLongname),
                        description = projectProperties.get(OntologyConstants.KnoraBase.ProjectDescription),
                        projectOntologyGraph = projectProperties.get(OntologyConstants.KnoraBase.ProjectOntologyGraph).get,
                        projectDataGraph = projectProperties.get(OntologyConstants.KnoraBase.ProjectDataGraph).get
                    )
            }
        } else {
            // no information was found for the given project Iri
            throw NotFoundException(s"For the given project Iri $projectIri no information was found")

        }

    }
}
