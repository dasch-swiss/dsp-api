/*
 * Copyright © 2015-2019 the contributors (see Contributors.md).
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

import java.io.{BufferedReader, BufferedWriter, File, FileReader, FileWriter}
import java.nio.file.Files
import java.util.UUID

import akka.http.scaladsl.util.FastFuture
import akka.pattern._
import org.eclipse.rdf4j.model.Statement
import org.eclipse.rdf4j.rio.{RDFFormat, RDFHandler, RDFWriter, Rio}
import org.knora.webapi._
import org.knora.webapi.annotation.ApiMayChange
import org.knora.webapi.messages.admin.responder.projectsmessages._
import org.knora.webapi.messages.admin.responder.usersmessages.{UserADM, UserGetADM, UserIdentifierADM, UserInformationTypeADM}
import org.knora.webapi.messages.store.cacheservicemessages.{CacheServiceGetProjectADM, CacheServicePutProjectADM, CacheServiceRemoveValues}
import org.knora.webapi.messages.store.triplestoremessages._
import org.knora.webapi.messages.v1.responder.projectmessages._
import org.knora.webapi.messages.v2.responder.ontologymessages.{OntologyMetadataGetByProjectRequestV2, ReadOntologyMetadataV2}
import org.knora.webapi.responders.Responder.handleUnexpectedMessage
import org.knora.webapi.responders.{IriLocker, Responder, ResponderData}
import org.knora.webapi.util.IriConversions._
import org.knora.webapi.util.{InstrumentationSupport, SmartIri, StringFormatter}

import scala.concurrent.Future

/**
  * Returns information about Knora projects.
  */
class ProjectsResponderADM(responderData: ResponderData) extends Responder(responderData) with InstrumentationSupport {


    // Global lock IRI used for project creation and update
    private val PROJECTS_GLOBAL_LOCK_IRI = "http://rdfh.ch/projects"

    private val ADMIN_DATA_GRAPH = "http://www.knora.org/data/admin"
    private val PERMISSIONS_DATA_GRAPH = "http://www.knora.org/data/permissions"

    /**
      * Receives a message extending [[ProjectsResponderRequestV1]], and returns an appropriate response message.
      */
    def receive(msg: ProjectsResponderRequestADM) = msg match {
        case ProjectsGetADM(requestingUser) => projectsGetADM(requestingUser)
        case ProjectsGetRequestADM(requestingUser) => projectsGetRequestADM(requestingUser)
        case ProjectGetADM(identifier, requestingUser) => getSingleProjectADM(identifier, requestingUser)
        case ProjectGetRequestADM(identifier, requestingUser) => getSingleProjectADMRequest(identifier, requestingUser)
        case ProjectMembersGetRequestADM(identifier, requestingUser) => projectMembersGetRequestADM(identifier, requestingUser)
        case ProjectAdminMembersGetRequestADM(identifier, requestingUser) => projectAdminMembersGetRequestADM(identifier, requestingUser)
        case ProjectsKeywordsGetRequestADM(requestingUser) => projectsKeywordsGetRequestADM(requestingUser)
        case ProjectKeywordsGetRequestADM(projectIri, requestingUser) => projectKeywordsGetRequestADM(projectIri, requestingUser)
        case ProjectRestrictedViewSettingsGetADM(identifier, requestingUser) => projectRestrictedViewSettingsGetADM(identifier, requestingUser)
        case ProjectRestrictedViewSettingsGetRequestADM(identifier, requestingUser) => projectRestrictedViewSettingsGetRequestADM(identifier, requestingUser)
        case ProjectCreateRequestADM(createRequest, requestingUser, apiRequestID) => projectCreateRequestADM(createRequest, requestingUser, apiRequestID)
        case ProjectChangeRequestADM(projectIri, changeProjectRequest, requestingUser, apiRequestID) => changeBasicInformationRequestADM(projectIri, changeProjectRequest, requestingUser, apiRequestID)
        case ProjectDataGetRequestADM(projectIdentifier, requestingUser) => projectDataGetRequestADM(projectIdentifier, requestingUser)
        case other => handleUnexpectedMessage(other, log, this.getClass.getName)
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

            statements: List[(SubjectV2, Map[SmartIri, Seq[LiteralV2]])] = projectsResponse.statements.toList
            // _ = log.debug(s"projectsGetADM - statements: $statements")

            projectIris = statements.map {
                case (projectIri: SubjectV2, _) => projectIri.toString
            }.toSet

            ontologiesForProjects: Map[IRI, Seq[IRI]] <- getOntologiesForProjects(projectIris, requestingUser)

            projects: Seq[ProjectADM] = statements.map {
                case (projectIriSubject: SubjectV2, propsMap: Map[SmartIri, Seq[LiteralV2]]) =>
                    val projectOntologies = ontologiesForProjects.getOrElse(projectIriSubject.toString, Seq.empty[IRI])
                    statements2ProjectADM(statements = (projectIriSubject, propsMap), ontologies = projectOntologies)
            }

        } yield projects.sorted
    }

    /**
      * Given a set of project IRIs, gets the ontologies that belong to each project.
      *
      * @param projectIris    a set of project IRIs. If empty, returns the ontologies for all projects.
      * @param requestingUser the requesting user.
      * @return a map of project IRIs to sequences of ontology IRIs.
      */
    private def getOntologiesForProjects(projectIris: Set[IRI], requestingUser: UserADM): Future[Map[IRI, Seq[IRI]]] = {
        for {
            ontologyMetadataResponse: ReadOntologyMetadataV2 <- (responderManager ? OntologyMetadataGetByProjectRequestV2(projectIris = projectIris.map(_.toSmartIri), requestingUser = requestingUser)).mapTo[ReadOntologyMetadataV2]
        } yield ontologyMetadataResponse.ontologies.map {
            ontology =>
                val ontologyIri: IRI = ontology.ontologyIri.toString
                val projectIri: IRI = ontology.projectIri.getOrElse(throw InconsistentTriplestoreDataException(s"Ontology $ontologyIri has no project")).toString
                projectIri -> ontologyIri
        }.groupBy(_._1).map {
            case (projectIri, projectIriAndOntologies: Set[(IRI, IRI)]) =>
                projectIri -> projectIriAndOntologies.map(_._2).toSeq
        }
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
      * @param identifier     the IRI, shortname, or shortcode of the project.
      * @param requestingUser the user making the request.
      * @return information about the project as a [[ProjectInfoV1]].
      */
    private def getSingleProjectADM(identifier: ProjectIdentifierADM,
                                    requestingUser: UserADM,
                                    skipCache: Boolean = false
                                   ): Future[Option[ProjectADM]] = tracedFuture("admin-get-project") {

        // log.debug("getSingleProjectADM - projectIRI: {}", projectIri)

        log.debug(s"getSingleProjectADM - id: {}, requester: {}, skipCache: {}",
            identifier.value,
            requestingUser.username,
            skipCache)

        for {
            maybeProjectADM <- if (skipCache) {
                // getting directly from triplestore
                getProjectFromTriplestore(identifier)
            } else {
                // getting from cache or triplestore
                getProjectFromCacheOrTriplestore(identifier)
            }

            _ = if (maybeProjectADM.nonEmpty) {
                log.debug("getSingleProjectADM - successfully retrieved project: {}", identifier.value)
            } else {
                log.debug("getSingleProjectADM - could not retrieve project: {}", identifier.value)
            }

        } yield maybeProjectADM
    }

    /**
      * Gets the project with the given project IRI, shortname, or shortcode and returns the information
      * as a [[ProjectGetResponseADM]].
      *
      * @param identifier     the IRI, shortname, or shortcode of the project.
      * @param requestingUser the user making the request.
      * @return information about the project as a [[ProjectInfoResponseV1]].
      * @throws NotFoundException when no project for the given IRI can be found
      */
    private def getSingleProjectADMRequest(identifier: ProjectIdentifierADM, requestingUser: UserADM): Future[ProjectGetResponseADM] = {

        // log.debug("getSingleProjectADMRequest - maybeIri: {}, maybeShortname: {}, maybeShortcode: {}", maybeIri, maybeShortname, maybeShortcode)

        for {
            maybeProject: Option[ProjectADM] <- getSingleProjectADM(identifier, requestingUser)
            project = maybeProject match {
                case Some(p) => p
                case None => throw NotFoundException(s"Project '${identifier.value}' not found")
            }
        } yield ProjectGetResponseADM(
            project = project
        )
    }

    /**
      * Gets the members of a project with the given IRI, shortname, oder shortcode. Returns an empty list
      * if none are found.
      *
      * @param identifier     the IRI, shortname, or shortcode of the project.
      * @param requestingUser the user making the request.
      * @return the members of a project as a [[ProjectMembersGetResponseADM]]
      */
    private def projectMembersGetRequestADM(identifier: ProjectIdentifierADM, requestingUser: UserADM): Future[ProjectMembersGetResponseADM] = {

        // log.debug("projectMembersGetRequestADM - maybeIri: {}, maybeShortname: {}, maybeShortcode: {}", maybeIri, maybeShortname, maybeShortcode)

        for {

            /* Get project and verify permissions. */
            project <- getSingleProjectADM(identifier, KnoraSystemInstances.Users.SystemUser)
            _ = if (project.isEmpty) {
                throw NotFoundException(s"Project '${identifier.value}' not found.")
            } else {
                if (!requestingUser.permissions.isSystemAdmin && !requestingUser.permissions.isProjectAdmin(project.get.id) && !requestingUser.isSystemUser) {
                    throw ForbiddenException("SystemAdmin or ProjectAdmin permissions are required.")
                }
            }

            sparqlQueryString <- Future(queries.sparql.admin.txt.getProjectMembers(
                triplestore = settings.triplestoreType,
                maybeIri = identifier.toIriOption,
                maybeShortname = identifier.toShortnameOption,
                maybeShortcode = identifier.toShortcodeOption
            ).toString())
            //_ = log.debug(s"projectMembersGetRequestADM - query: $sparqlQueryString")

            projectMembersResponse <- (storeManager ? SparqlExtendedConstructRequest(sparqlQueryString)).mapTo[SparqlExtendedConstructResponse]

            statements = projectMembersResponse.statements.toList

            // _ = log.debug(s"projectMembersGetRequestADM - statements: {}", MessageUtil.toSource(statements))

            // get project member IRI from results rows
            userIris: Seq[IRI] = if (statements.nonEmpty) {
                statements.map(_._1.toString)
            } else {
                Seq.empty[IRI]
            }

            maybeUserFutures: Seq[Future[Option[UserADM]]] = userIris.map {
                userIri => (responderManager ? UserGetADM(identifier = UserIdentifierADM(maybeIri = Some(userIri)), userInformationTypeADM = UserInformationTypeADM.RESTRICTED, requestingUser = KnoraSystemInstances.Users.SystemUser)).mapTo[Option[UserADM]]
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
      * @param identifier     the IRI, shortname, or shortcode of the project.
      * @param requestingUser the user making the request.
      * @return the members of a project as a [[ProjectMembersGetResponseADM]]
      */
    private def projectAdminMembersGetRequestADM(identifier: ProjectIdentifierADM, requestingUser: UserADM): Future[ProjectAdminMembersGetResponseADM] = {

        // log.debug("projectAdminMembersGetRequestADM - maybeIri: {}, maybeShortname: {}, maybeShortcode: {}", maybeIri, maybeShortname, maybeShortcode)

        for {
            /* Get project and verify permissions. */
            project <- getSingleProjectADM(identifier, KnoraSystemInstances.Users.SystemUser)
            _ = if (project.isEmpty) {
                throw NotFoundException(s"Project '${identifier.value}' not found.")
            } else {
                if (!requestingUser.permissions.isSystemAdmin && !requestingUser.permissions.isProjectAdmin(project.get.id)) {
                    throw ForbiddenException("SystemAdmin or ProjectAdmin permissions are required.")
                }
            }

            sparqlQueryString <- Future(queries.sparql.admin.txt.getProjectAdminMembers(
                triplestore = settings.triplestoreType,
                maybeIri = identifier.toIriOption,
                maybeShortname = identifier.toShortnameOption,
                maybeShortcode = identifier.toShortcodeOption
            ).toString())
            //_ = log.debug(s"projectAdminMembersByIRIGetRequestV1 - query: $sparqlQueryString")

            projectAdminMembersResponse <- (storeManager ? SparqlExtendedConstructRequest(sparqlQueryString)).mapTo[SparqlExtendedConstructResponse]
            //_ = log.debug(s"projectAdminMembersByIRIGetRequestV1 - result: ${MessageUtil.toSource(projectMembersResponse)}")

            statements = projectAdminMembersResponse.statements.toList

            // get project member IRI from results rows
            userIris: Seq[IRI] = if (statements.nonEmpty) {
                statements.map(_._1.toString)
            } else {
                Seq.empty[IRI]
            }

            maybeUserFutures: Seq[Future[Option[UserADM]]] = userIris.map {
                userIri => (responderManager ? UserGetADM(identifier = UserIdentifierADM(maybeIri = Some(userIri)), userInformationTypeADM = UserInformationTypeADM.RESTRICTED, requestingUser = KnoraSystemInstances.Users.SystemUser)).mapTo[Option[UserADM]]
            }
            maybeUsers: Seq[Option[UserADM]] <- Future.sequence(maybeUserFutures)
            users: Seq[UserADM] = maybeUsers.flatten

            //_ = log.debug(s"projectMembersGetRequestADM - users: $users")

        } yield ProjectAdminMembersGetResponseADM(members = users)
    }

    /**
      * Gets all unique keywords for all projects and returns them. Returns an empty list if none are found.
      *
      * @param requestingUser the user making the request.
      * @return all keywords for all projects as [[ProjectsKeywordsGetResponseADM]]
      */
    private def projectsKeywordsGetRequestADM(requestingUser: UserADM): Future[ProjectsKeywordsGetResponseADM] = {

        for {
            projects <- projectsGetADM(KnoraSystemInstances.Users.SystemUser)

            keywords: Seq[String] = projects.flatMap(_.keywords).distinct.sorted

        } yield ProjectsKeywordsGetResponseADM(keywords = keywords)
    }

    /**
      * Gets all keywords for a single project and returns them. Returns an empty list if none are found.
      *
      * @param projectIri     the IRI of the project.
      * @param requestingUser the user making the request.
      * @return keywords for a projects as [[ProjectKeywordsGetResponseADM]]
      */
    private def projectKeywordsGetRequestADM(projectIri: IRI, requestingUser: UserADM): Future[ProjectKeywordsGetResponseADM] = {

        for {
            maybeProject <- getSingleProjectADM(ProjectIdentifierADM(maybeIri = Some(projectIri)), requestingUser = KnoraSystemInstances.Users.SystemUser)

            keywords: Seq[String] = maybeProject match {
                case Some(p) => p.keywords
                case None => throw NotFoundException(s"Project '$projectIri' not found.")
            }

        } yield ProjectKeywordsGetResponseADM(keywords = keywords)
    }

    private def projectDataGetRequestADM(projectIdentifier: ProjectIdentifierADM, requestingUser: UserADM): Future[ProjectDataGetResponseADM] = {
        /**
          * Represents a named graph to be saved to a TriG file.
          *
          * @param graphIri the IRI of the named graph.
          * @param tempDir  the directory in which the file is to be saved.
          */
        case class NamedGraphTrigFile(graphIri: IRI, tempDir: File) {
            lazy val dataFile: File = {
                val filename = graphIri.replaceAll(":", "_").
                    replaceAll("/", "_").
                    replaceAll("""\.""", "_") +
                    ".trig"

                new File(tempDir, filename)
            }
        }

        /**
          * An [[RDFHandler]] for combining several named graphs into one.
          *
          * @param outputWriter an [[RDFWriter]] for writing the combined result.
          */
        class CombiningRdfHandler(outputWriter: RDFWriter) extends RDFHandler {
            private var startedStatements = false

            // Ignore this, since it will be done before the first file is written.
            override def startRDF(): Unit = {}

            // Ignore this, since it will be done after the last file is written.
            override def endRDF(): Unit = {}

            override def handleNamespace(prefix: IRI, uri: IRI): Unit = {
                // Only accept namespaces from the first graph, to prevent conflicts.
                if (!startedStatements) {
                    outputWriter.handleNamespace(prefix, uri)
                }
            }

            override def handleStatement(st: Statement): Unit = {
                startedStatements = true
                outputWriter.handleStatement(st)
            }

            override def handleComment(comment: IRI): Unit = outputWriter.handleComment(comment)
        }

        /**
          * Combines several TriG files into one.
          *
          * @param namedGraphTrigFiles the TriG files to combine.
          * @param resultFile          the output file.
          */
        def combineGraphs(namedGraphTrigFiles: Seq[NamedGraphTrigFile], resultFile: File): Unit = {
            var maybeBufferedFileWriter: Option[BufferedWriter] = None

            try {
                maybeBufferedFileWriter = Some(new BufferedWriter(new FileWriter(resultFile)))
                val trigFileWriter: RDFWriter = Rio.createWriter(RDFFormat.TRIG, maybeBufferedFileWriter.get)
                val combiningRdfHandler = new CombiningRdfHandler(trigFileWriter)
                trigFileWriter.startRDF()

                for (namedGraphTrigFile: NamedGraphTrigFile <- namedGraphTrigFiles) {
                    var maybeBufferedFileReader: Option[BufferedReader] = None

                    try {
                        maybeBufferedFileReader = Some(new BufferedReader(new FileReader(namedGraphTrigFile.dataFile)))
                        val trigFileParser = Rio.createParser(RDFFormat.TRIG)
                        trigFileParser.setRDFHandler(combiningRdfHandler)
                        trigFileParser.parse(maybeBufferedFileReader.get, "")
                        namedGraphTrigFile.dataFile.delete
                    } finally {
                        maybeBufferedFileReader.foreach(_.close)
                    }
                }

                trigFileWriter.endRDF()
            } finally {
                maybeBufferedFileWriter.foreach(_.close)
            }
        }

        for {
            // Get the project info.
            maybeProject: Option[ProjectADM] <- getSingleProjectADM(
                identifier = projectIdentifier,
                requestingUser = requestingUser
            )

            project: ProjectADM = maybeProject.getOrElse(throw NotFoundException(s"Project '${projectIdentifier.value}' not found."))

            // Check that the user has permission to download the data.
            _ = if (!(requestingUser.permissions.isSystemAdmin || requestingUser.permissions.isProjectAdmin(project.id))) {
                throw ForbiddenException(s"You are logged in as ${requestingUser.username}, but only a system administrator or project administrator can request a project's data")
            }

            // Make a temporary directory for the downloaded data.
            tempDir = Files.createTempDirectory(project.shortname).toFile
            _ = log.info("Downloading project data to temporary directory " + tempDir.getAbsolutePath)

            // Download the project's named graphs.

            projectDataNamedGraph: IRI = stringFormatter.projectDataNamedGraphV2(project)
            graphsToDownload: Seq[IRI] = project.ontologies :+ projectDataNamedGraph
            projectSpecificNamedGraphTrigFiles: Seq[NamedGraphTrigFile] = graphsToDownload.map(graphIri => NamedGraphTrigFile(graphIri = graphIri, tempDir = tempDir))

            projectSpecificNamedGraphTrigFileWriteFutures: Seq[Future[FileWrittenResponse]] = projectSpecificNamedGraphTrigFiles.map {
                trigFile =>
                    for {
                        fileWrittenResponse: FileWrittenResponse <- (
                            storeManager ?
                                GraphFileRequest(
                                    graphIri = trigFile.graphIri,
                                    outputFile = trigFile.dataFile
                                )
                            ).mapTo[FileWrittenResponse]
                    } yield fileWrittenResponse
            }

            _: Seq[FileWrittenResponse] <- Future.sequence(projectSpecificNamedGraphTrigFileWriteFutures)

            // Download the project's admin data.

            adminDataNamedGraphTrigFile = NamedGraphTrigFile(graphIri = ADMIN_DATA_GRAPH, tempDir = tempDir)

            adminDataSparql: String = queries.sparql.admin.txt.getProjectAdminData(
                triplestore = settings.triplestoreType,
                projectIri = project.id
            ).toString()

            _: FileWrittenResponse <- (storeManager ? SparqlConstructFileRequest(
                sparql = adminDataSparql,
                graphIri = adminDataNamedGraphTrigFile.graphIri,
                outputFile = adminDataNamedGraphTrigFile.dataFile
            )).mapTo[FileWrittenResponse]

            // Download the project's permission data.

            permissionDataNamedGraphTrigFile = NamedGraphTrigFile(graphIri = PERMISSIONS_DATA_GRAPH, tempDir = tempDir)

            permissionDataSparql: String = queries.sparql.admin.txt.getProjectPermissions(
                triplestore = settings.triplestoreType,
                projectIri = project.id
            ).toString()

            _: FileWrittenResponse <- (storeManager ? SparqlConstructFileRequest(
                sparql = permissionDataSparql,
                graphIri = permissionDataNamedGraphTrigFile.graphIri,
                outputFile = permissionDataNamedGraphTrigFile.dataFile
            )).mapTo[FileWrittenResponse]

            // Stream the combined results into the output file.

            namedGraphTrigFiles: Seq[NamedGraphTrigFile] = projectSpecificNamedGraphTrigFiles :+ adminDataNamedGraphTrigFile :+ permissionDataNamedGraphTrigFile
            resultFile: File = new File(tempDir, project.shortname + ".trig")
            _ = combineGraphs(namedGraphTrigFiles = namedGraphTrigFiles, resultFile = resultFile)
        } yield ProjectDataGetResponseADM(resultFile)
    }

    /**
      * Get project's restricted view settings.
      *
      * @param identifier     the project's identifier (IRI / shortcode / shortname)
      * @param requestingUser the user making the request.
      * @return [[ProjectRestrictedViewSettingsADM]]
      */
    @ApiMayChange
    private def projectRestrictedViewSettingsGetADM(identifier: ProjectIdentifierADM, requestingUser: UserADM): Future[Option[ProjectRestrictedViewSettingsADM]] = {

        // ToDo: We have two possible NotFound scenarios: 1. Project, 2. ProjectRestrictedViewSettings resource. How to send the client the correct NotFound reply?

        for {
            sparqlQuery <- Future(queries.sparql.admin.txt.getProjects(
                triplestore = settings.triplestoreType,
                maybeIri = identifier.toIriOption,
                maybeShortname = identifier.toShortnameOption,
                maybeShortcode = identifier.toShortcodeOption
            ).toString())

            projectResponse <- (storeManager ? SparqlExtendedConstructRequest(sparqlQuery)).mapTo[SparqlExtendedConstructResponse]

            restrictedViewSettings = if (projectResponse.statements.nonEmpty) {

                val (_, propsMap): (SubjectV2, Map[SmartIri, Seq[LiteralV2]]) = projectResponse.statements.head

                val size = propsMap.get(OntologyConstants.KnoraAdmin.ProjectRestrictedViewSize.toSmartIri).map(_.head.asInstanceOf[StringLiteralV2].value)
                val watermark = propsMap.get(OntologyConstants.KnoraAdmin.ProjectRestrictedViewWatermark.toSmartIri).map(_.head.asInstanceOf[StringLiteralV2].value)

                Some(ProjectRestrictedViewSettingsADM(size, watermark))
            } else {
                None
            }

        } yield restrictedViewSettings

    }

    /**
      * Get project's restricted view settings.
      *
      * @param identifier     the project's identifier (IRI / shortcode / shortname)
      * @param requestingUser the user making the request.
      * @return [[ProjectRestrictedViewSettingsGetResponseADM]]
      */
    @ApiMayChange
    private def projectRestrictedViewSettingsGetRequestADM(identifier: ProjectIdentifierADM, requestingUser: UserADM): Future[ProjectRestrictedViewSettingsGetResponseADM] = {

        val maybeIri = identifier.toIriOption
        val maybeShortname = identifier.toShortnameOption
        val maybeShortcode = identifier.toShortcodeOption

        for {
            maybeSettings: Option[ProjectRestrictedViewSettingsADM] <- projectRestrictedViewSettingsGetADM(identifier, requestingUser)

            settings = maybeSettings match {
                case Some(s) => s
                case None => throw NotFoundException(s"Project '${Seq(maybeIri, maybeShortname, maybeShortcode).flatten.head}' not found.")
            }

        } yield ProjectRestrictedViewSettingsGetResponseADM(settings)

    }

    /**
      * Changes project's basic information.
      *
      * @param projectIri           the IRI of the project.
      * @param changeProjectRequest the change payload.
      * @param requestingUser       the user making the request.
      * @param apiRequestID         the unique api request ID.
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
      * Main project update method.
      *
      * @param projectIri           the IRI of the project.
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
            projectUpdatePayload.status,
            projectUpdatePayload.selfjoin).flatten.size

        if (parametersCount == 0) throw BadRequestException("No data would be changed. Aborting update request.")

        for {

            maybeCurrentProject: Option[ProjectADM] <- getSingleProjectADM(identifier = ProjectIdentifierADM(maybeIri = Some(projectIri)), requestingUser = requestingUser, skipCache = true)
            _ = if (maybeCurrentProject.isEmpty) {
                throw NotFoundException(s"Project '$projectIri' not found. Aborting update request.")
            }
            // we are changing the project, so lets get rid of the cached copy
            _ = invalidateCachedProjectADM(maybeCurrentProject)

            /* Update project */
            updateProjectSparqlString <- Future(queries.sparql.admin.txt.updateProject(
                adminNamedGraphIri = "http://www.knora.org/data/admin",
                triplestore = settings.triplestoreType,
                projectIri = projectIri,
                maybeShortname = projectUpdatePayload.shortname,
                maybeLongname = projectUpdatePayload.longname,
                maybeDescriptions = projectUpdatePayload.description,
                maybeKeywords = projectUpdatePayload.keywords,
                maybeLogo = projectUpdatePayload.logo,
                maybeStatus = projectUpdatePayload.status,
                maybeSelfjoin = projectUpdatePayload.selfjoin
            ).toString)

            // _ = log.debug(s"updateProjectADM - update query: {}", updateProjectSparqlString)

            updateProjectResponse <- (storeManager ? SparqlUpdateRequest(updateProjectSparqlString)).mapTo[SparqlUpdateResponse]

            /* Verify that the project was updated. */
            maybeUpdatedProject <- getSingleProjectADM(identifier = ProjectIdentifierADM(maybeIri = Some(projectIri)), requestingUser = KnoraSystemInstances.Users.SystemUser, skipCache = true)
            updatedProject: ProjectADM = maybeUpdatedProject.getOrElse(throw UpdateNotPerformedException("Project was not updated. Please report this as a possible bug."))

            _ = log.debug("updateProjectADM - projectUpdatePayload: {} /  updatedProject: {}", projectUpdatePayload, updatedProject)

            _ = if (projectUpdatePayload.shortname.isDefined) {
                if (updatedProject.shortname != projectUpdatePayload.shortname.get) throw UpdateNotPerformedException("Project's 'shortname' was not updated. Please report this as a possible bug.")
            }

            _ = if (projectUpdatePayload.longname.isDefined) {
                if (updatedProject.longname != projectUpdatePayload.longname) throw UpdateNotPerformedException("Project's 'longname' was not updated. Please report this as a possible bug.")
            }

            _ = if (projectUpdatePayload.description.isDefined) {
                if (updatedProject.description.sorted != projectUpdatePayload.description.get.sorted) throw UpdateNotPerformedException("Project's 'description' was not updated. Please report this as a possible bug.")
            }

            _ = if (projectUpdatePayload.keywords.isDefined) {
                if (updatedProject.keywords.sorted != projectUpdatePayload.keywords.get.sorted) throw UpdateNotPerformedException("Project's 'keywords' was not updated. Please report this as a possible bug.")
            }

            _ = if (projectUpdatePayload.logo.isDefined) {
                if (updatedProject.logo != projectUpdatePayload.logo) throw UpdateNotPerformedException("Project's 'logo' was not updated. Please report this as a possible bug.")
            }

            _ = if (projectUpdatePayload.status.isDefined) {
                if (updatedProject.status != projectUpdatePayload.status.get) throw UpdateNotPerformedException("Project's 'status' was not updated. Please report this as a possible bug.")
            }

            _ = if (projectUpdatePayload.selfjoin.isDefined) {
                if (updatedProject.selfjoin != projectUpdatePayload.selfjoin.get) throw UpdateNotPerformedException("Project's 'selfjoin' status was not updated. Please report this as a possible bug.")
            }

        } yield ProjectOperationResponseADM(project = updatedProject)

    }

    /**
      * Creates a project.
      *
      * @param createRequest  the new project's information.
      * @param requestingUser the user that is making the request.
      * @param apiRequestID   the unique api request ID.
      * @return a [[ProjectOperationResponseADM]].
      * @throws ForbiddenException      in the case that the user is not allowed to perform the operation.
      * @throws DuplicateValueException in the case when either the shortname or shortcode are not unique.
      * @throws BadRequestException     in the case when the shortcode is invalid.
      */
    private def projectCreateRequestADM(createRequest: CreateProjectApiRequestADM, requestingUser: UserADM, apiRequestID: UUID): Future[ProjectOperationResponseADM] = {

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

            validatedShortcode = StringFormatter.getGeneralInstance.validateProjectShortcode(
                createRequest.shortcode,
                errorFun = throw BadRequestException(s"The supplied short code: '${createRequest.shortcode}' is not valid.")
            )

            // check if the optionally supplied shortcode is valid and unique
            shortcodeExists <- {
                projectByShortcodeExists(validatedShortcode)
            }
            _ = if (shortcodeExists) {
                throw DuplicateValueException(s"Project with the shortcode: '${createRequest.shortcode}' already exists")
            }

            newProjectIRI = stringFormatter.makeRandomProjectIri(validatedShortcode)

            // Create the new project.
            createNewProjectSparqlString = queries.sparql.admin.txt.createNewProject(
                adminNamedGraphIri = OntologyConstants.NamedGraphs.AdminNamedGraph,
                triplestore = settings.triplestoreType,
                projectIri = newProjectIRI,
                projectClassIri = OntologyConstants.KnoraAdmin.KnoraProject,
                shortname = createRequest.shortname,
                shortcode = validatedShortcode,
                maybeLongname = createRequest.longname,
                maybeDescriptions = if (createRequest.description.nonEmpty) {
                    Some(createRequest.description)
                } else None,
                maybeKeywords = if (createRequest.keywords.nonEmpty) {
                    Some(createRequest.keywords)
                } else None,
                maybeLogo = createRequest.logo,
                status = createRequest.status,
                hasSelfJoinEnabled = createRequest.selfjoin
            ).toString
            // _ = log.debug("projectCreateRequestADM - create query: {}", createNewProjectSparqlString)

            createProjectResponse <- (storeManager ? SparqlUpdateRequest(createNewProjectSparqlString)).mapTo[SparqlUpdateResponse]

            // try to retrieve newly created project (will also add to cache)
            maybeNewProjectADM
            <- getSingleProjectADM(
                identifier = ProjectIdentifierADM(maybeIri = Some(newProjectIRI)),
                requestingUser = KnoraSystemInstances.Users.SystemUser,
                skipCache = true
            )

            // check to see if we could retrieve the new project
            newProjectADM = maybeNewProjectADM.getOrElse(
                throw UpdateNotPerformedException(s"Project $newProjectIRI was not created. Please report this as a possible bug.")
            )

        } yield ProjectOperationResponseADM(project = newProjectADM)

        for {
            // run user creation with an global IRI lock
            taskResult <- IriLocker.runWithIriLock(
                apiRequestID,
                PROJECTS_GLOBAL_LOCK_IRI,
                () => projectCreateTask(createRequest, requestingUser)
            )
        } yield taskResult
    }


    ////////////////////
    // Helper Methods //
    ////////////////////

    /**
      * Tries to retrieve a [[ProjectADM]] either from triplestore or cache if caching is enabled.
      * If project is not found in cache but in triplestore, then project is written to cache.
      */
    private def getProjectFromCacheOrTriplestore(identifier: ProjectIdentifierADM): Future[Option[ProjectADM]] = {
        if (settings.cacheServiceEnabled) {
            // caching enabled
            getProjectFromCache(identifier)
              .flatMap {
                  case None =>
                      // none found in cache. getting from triplestore.
                      getProjectFromTriplestore(identifier)
                        .flatMap {
                            case None =>
                                // also none found in triplestore. finally returning none.
                                log.debug("getProjectFromCacheOrTriplestore - not found in cache and in triplestore")
                                FastFuture.successful(None)
                            case Some(project) =>
                                // found a project in the triplestore. need to write to cache.
                                log.debug("getProjectFromCacheOrTriplestore - not found in cache but found in triplestore. need to write to cache.")
                                // writing project to cache and afterwards returning the project found in the triplestore
                                writeProjectADMToCache(project).map(res => Some(project))
                        }
                  case Some(user) =>
                      log.debug("getProjectFromCacheOrTriplestore - found in cache. returning user.")
                      FastFuture.successful(Some(user))
              }
        } else {
            // caching disabled
            log.debug("getProjectFromCacheOrTriplestore - caching disabled. getting from triplestore.")
            getProjectFromTriplestore(identifier)
        }
    }

    /**
      * Tries to retrieve a [[ProjectADM]] from the triplestore.
      */
    private def getProjectFromTriplestore(identifier: ProjectIdentifierADM): Future[Option[ProjectADM]] = for {

        sparqlQuery <- Future(queries.sparql.admin.txt.getProjects(
            triplestore = settings.triplestoreType,
            maybeIri = identifier.toIriOption,
            maybeShortname = identifier.toShortnameOption,
            maybeShortcode = identifier.toShortcodeOption
        ).toString())

        projectResponse <- (storeManager ? SparqlExtendedConstructRequest(sparqlQuery)).mapTo[SparqlExtendedConstructResponse]

        projectIris = projectResponse.statements.keySet.map(_.toString)

        ontologies <- if (projectResponse.statements.nonEmpty) {
            getOntologiesForProjects(projectIris, KnoraSystemInstances.Users.SystemUser)
        } else {
            FastFuture.successful(Map.empty[IRI, Seq[IRI]])
        }

        maybeProjectADM: Option[ProjectADM] = if (projectResponse.statements.nonEmpty) {
            log.debug("getProjectFromTriplestore - triplestore hit for: {}", identifier)
            val projectOntologies = ontologies.getOrElse(projectIris.head, Seq.empty[IRI])
            Some(statements2ProjectADM(statements = projectResponse.statements.head, ontologies = projectOntologies))
        } else {
            log.debug("getProjectFromTriplestore - no triplestore hit for: {}", identifier)
            None
        }

    } yield maybeProjectADM

    /**
      * Helper method that turns SPARQL result rows into a [[ProjectInfoV1]].
      *
      * @param statements results from the SPARQL query representing information about the project.
      * @param ontologies the ontologies in the project.
      * @return a [[ProjectADM]] representing information about project.
      */
    private def statements2ProjectADM(statements: (SubjectV2, Map[SmartIri, Seq[LiteralV2]]), ontologies: Seq[IRI]): ProjectADM = {

        // log.debug("statements2ProjectADM - statements: {}", statements)

        val projectIri: IRI = statements._1.toString
        val propsMap: Map[SmartIri, Seq[LiteralV2]] = statements._2

        ProjectADM(
            id = projectIri,
            shortname = propsMap.getOrElse(OntologyConstants.KnoraAdmin.ProjectShortname.toSmartIri, throw InconsistentTriplestoreDataException(s"Project: $projectIri has no shortname defined.")).head.asInstanceOf[StringLiteralV2].value,
            shortcode = propsMap.getOrElse(OntologyConstants.KnoraAdmin.ProjectShortcode.toSmartIri, throw InconsistentTriplestoreDataException(s"Project: $projectIri has no shortcode defined.")).head.asInstanceOf[StringLiteralV2].value,
            longname = propsMap.get(OntologyConstants.KnoraAdmin.ProjectLongname.toSmartIri).map(_.head.asInstanceOf[StringLiteralV2].value),
            description = propsMap.getOrElse(OntologyConstants.KnoraAdmin.ProjectDescription.toSmartIri, throw InconsistentTriplestoreDataException(s"Project: $projectIri has no description defined.")).map(_.asInstanceOf[StringLiteralV2].requireLanguage(throw InconsistentTriplestoreDataException((s"The description of project $projectIri has no language tag")))),
            keywords = propsMap.getOrElse(OntologyConstants.KnoraAdmin.ProjectKeyword.toSmartIri, Seq.empty[String]).map(_.asInstanceOf[StringLiteralV2].value).sorted,
            logo = propsMap.get(OntologyConstants.KnoraAdmin.ProjectLogo.toSmartIri).map(_.head.asInstanceOf[StringLiteralV2].value),
            ontologies = ontologies,
            status = propsMap.getOrElse(OntologyConstants.KnoraAdmin.Status.toSmartIri, throw InconsistentTriplestoreDataException(s"Project: $projectIri has no status defined.")).head.asInstanceOf[BooleanLiteralV2].value,
            selfjoin = propsMap.getOrElse(OntologyConstants.KnoraAdmin.HasSelfJoinEnabled.toSmartIri, throw InconsistentTriplestoreDataException(s"Project: $projectIri has no hasSelfJoinEnabled defined.")).head.asInstanceOf[BooleanLiteralV2].value
        )
    }

    /**
      * Helper method for checking if a project exists.
      *
      * @param maybeIri       the IRI of the project.
      * @param maybeShortname the shortname of the project.
      * @param maybeShortcode the shortcode of the project.
      * @return a [[Boolean]].
      */
    private def projectExists(maybeIri: Option[IRI], maybeShortname: Option[String], maybeShortcode: Option[String]): Future[Boolean] = {

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
    private def projectByIriExists(projectIri: IRI): Future[Boolean] = {
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
    private def projectByShortnameExists(shortname: String): Future[Boolean] = {
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
    private def projectByShortcodeExists(shortcode: String): Future[Boolean] = {
        for {
            askString <- Future(queries.sparql.admin.txt.checkProjectExistsByShortcode(shortcode = shortcode).toString)
            //_ = log.debug("projectExists - query: {}", askString)

            checkProjectExistsResponse <- (storeManager ? SparqlAskRequest(askString)).mapTo[SparqlAskResponse]
            result = checkProjectExistsResponse.result

        } yield result
    }

    /**
      * Tries to retrieve a [[ProjectADM]] from the cache.
      */
    private def getProjectFromCache(identifier: ProjectIdentifierADM): Future[Option[ProjectADM]] = {
        val result = (storeManager ? CacheServiceGetProjectADM(identifier)).mapTo[Option[ProjectADM]]
        result.map {
            case Some(project) =>
                log.debug("getProjectFromCache - cache hit for: {}", identifier)
                Some(project)
            case None =>
                log.debug("getUserProjectCache - no cache hit for: {}", identifier)
                None
        }
    }

    /**
      * Writes the project to cache.
      *
      * @param project a [[ProjectADM]].
      * @return true if writing was successful.
      */
    private def writeProjectADMToCache(project: ProjectADM): Future[Boolean] = {
        val result = (storeManager ? CacheServicePutProjectADM(project)).mapTo[Boolean]
        result.map { res =>
            log.debug("writeProjectADMToCache - result: {}", result)
            res
        }
    }

    /**
      * Removes the project from cache.
      */
    private def invalidateCachedProjectADM(maybeProject: Option[ProjectADM]): Future[Boolean] = {
        if (settings.cacheServiceEnabled) {
            val keys: Set[String] = Seq(maybeProject.map(_.id), maybeProject.map(_.shortname), maybeProject.map(_.shortcode)).flatten.toSet
            // only send to Redis if keys are not empty
            if (keys.nonEmpty) {
                val result = (storeManager ? CacheServiceRemoveValues(keys)).mapTo[Boolean]
                result.map { res =>
                    log.debug("invalidateCachedProjectADM - result: {}", res)
                    res
                }
            } else {
                // since there was nothing to remove, we can immediately return
                FastFuture.successful(true)
            }
        } else {
            // caching is turned off, so nothing to do.
            FastFuture.successful(true)
        }

    }
}
