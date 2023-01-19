/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.admin

import akka.http.scaladsl.util.FastFuture
import akka.pattern._

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import dsp.errors.NotFoundException
import dsp.errors._
import dsp.valueobjects.Iri
import dsp.valueobjects.Project
import dsp.valueobjects.V2
import org.knora.webapi._
import org.knora.webapi.instrumentation.InstrumentationSupport
import org.knora.webapi.messages.IriConversions._
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.admin.responder.permissionsmessages._
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM._
import org.knora.webapi.messages.admin.responder.projectsmessages._
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.admin.responder.usersmessages.UserGetADM
import org.knora.webapi.messages.admin.responder.usersmessages.UserIdentifierADM
import org.knora.webapi.messages.admin.responder.usersmessages.UserInformationTypeADM
import org.knora.webapi.messages.store.cacheservicemessages.CacheServiceFlushDB
import org.knora.webapi.messages.store.cacheservicemessages.CacheServiceGetProjectADM
import org.knora.webapi.messages.store.cacheservicemessages.CacheServicePutProjectADM
import org.knora.webapi.messages.store.triplestoremessages._
import org.knora.webapi.messages.util.KnoraSystemInstances
import org.knora.webapi.messages.util.rdf._
import org.knora.webapi.messages.v2.responder.ontologymessages.OntologyMetadataGetByProjectRequestV2
import org.knora.webapi.messages.v2.responder.ontologymessages.OntologyMetadataV2
import org.knora.webapi.messages.v2.responder.ontologymessages.ReadOntologyMetadataV2
import org.knora.webapi.responders.ActorDeps
import org.knora.webapi.responders.IriLocker
import org.knora.webapi.responders.Responder
import org.knora.webapi.store.cache.settings.CacheServiceSettings

/**
 * Returns information about projects.
 */
final case class ProjectsResponderADM(actorDeps: ActorDeps, cacheServiceSettings: CacheServiceSettings)
    extends Responder(actorDeps)
    with InstrumentationSupport {

  // Global lock IRI used for project creation and update
  private val PROJECTS_GLOBAL_LOCK_IRI = "http://rdfh.ch/projects"

  private val ADMIN_DATA_GRAPH       = "http://www.knora.org/data/admin"
  private val PERMISSIONS_DATA_GRAPH = "http://www.knora.org/data/permissions"

  /**
   * Receives a message extending [[ProjectsResponderRequestADM]], and returns an appropriate response message.
   */
  def receive(msg: ProjectsResponderRequestADM) = msg match {
    case ProjectsGetRequestADM()          => projectsGetRequestADM()
    case ProjectGetADM(identifier)        => getSingleProjectADM(identifier)
    case ProjectGetRequestADM(identifier) => getSingleProjectADMRequest(identifier)
    case ProjectMembersGetRequestADM(identifier, requestingUser) =>
      projectMembersGetRequestADM(identifier, requestingUser)
    case ProjectAdminMembersGetRequestADM(identifier, requestingUser) =>
      projectAdminMembersGetRequestADM(identifier, requestingUser)
    case ProjectsKeywordsGetRequestADM() => projectsKeywordsGetRequestADM()
    case ProjectKeywordsGetRequestADM(projectIri, requestingUser) =>
      projectKeywordsGetRequestADM(projectIri, requestingUser)
    case ProjectRestrictedViewSettingsGetADM(identifier, requestingUser) =>
      projectRestrictedViewSettingsGetADM(identifier, requestingUser)
    case ProjectRestrictedViewSettingsGetRequestADM(identifier, requestingUser) =>
      projectRestrictedViewSettingsGetRequestADM(identifier, requestingUser)
    case ProjectCreateRequestADM(createRequest, requestingUser, apiRequestID) =>
      projectCreateRequestADM(createRequest, requestingUser, apiRequestID)
    case ProjectChangeRequestADM(
          projectIri,
          changeProjectPayload,
          requestingUser,
          apiRequestID
        ) =>
      changeBasicInformationRequestADM(
        projectIri,
        changeProjectPayload,
        requestingUser,
        apiRequestID
      )
    case ProjectDataGetRequestADM(projectIdentifier, requestingUser) =>
      projectDataGetRequestADM(projectIdentifier, requestingUser)
    case other => handleUnexpectedMessage(other, log, this.getClass.getName)
  }

  /**
   * Gets all the projects and returns them as a sequence containing [[ProjectADM]].
   *
   * @return all the projects as a sequence containing [[ProjectADM]].
   */
  private def projectsGetADM(): Future[Seq[ProjectADM]] =
    for {
      sparqlQueryString <-
        Future(
          org.knora.webapi.messages.twirl.queries.sparql.admin.txt
            .getProjects(
              maybeIri = None,
              maybeShortname = None,
              maybeShortcode = None
            )
            .toString()
        )
      request                                    = SparqlExtendedConstructRequest(sparql = sparqlQueryString)
      projectsResponse                          <- appActor.ask(request).mapTo[SparqlExtendedConstructResponse]
      projectIris                                = projectsResponse.statements.keySet.map(_.toString)
      ontologiesForProjects: Map[IRI, Seq[IRI]] <- getOntologiesForProjects(projectIris)
      projects =
        projectsResponse.statements.toList.map {
          case (projectIriSubject: SubjectV2, propsMap: Map[SmartIri, Seq[LiteralV2]]) =>
            val projectOntologies =
              ontologiesForProjects.getOrElse(projectIriSubject.toString, Seq.empty[IRI])
            statements2ProjectADM(
              statements = (projectIriSubject, propsMap),
              ontologies = projectOntologies
            )
        }

    } yield projects.sorted

  /**
   * Given a set of project IRIs, gets the ontologies that belong to each project.
   *
   * @param projectIris    a set of project IRIs. If empty, returns the ontologies for all projects.
   * @return a map of project IRIs to sequences of ontology IRIs.
   */
  private def getOntologiesForProjects(projectIris: Set[IRI]): Future[Map[IRI, Seq[IRI]]] = {
    def getIriPair(ontology: OntologyMetadataV2) =
      ontology.projectIri.fold(
        throw InconsistentRepositoryDataException(s"Ontology ${ontology.ontologyIri} has no project")
      )(project => (project.toString, ontology.ontologyIri.toString))

    val request = OntologyMetadataGetByProjectRequestV2(
      projectIris = projectIris.map(_.toSmartIri),
      requestingUser = KnoraSystemInstances.Users.SystemUser
    )

    for {
      ontologyMetadataResponse <- appActor.ask(request).mapTo[ReadOntologyMetadataV2]
      ontologies                = ontologyMetadataResponse.ontologies.toList
      iriPairs                  = ontologies.map(getIriPair(_))
      projectToOntologyMap      = iriPairs.groupMap { case (project, _) => project } { case (_, onto) => onto }
    } yield projectToOntologyMap
  }

  /**
   * Gets all the projects and returns them as a [[ProjectADM]].
   *
   * @return all the projects as a [[ProjectADM]].
   * @throws NotFoundException if no projects are found.
   */
  private def projectsGetRequestADM(): Future[ProjectsGetResponseADM] =
    for {
      projects <- projectsGetADM()
      result = if (projects.nonEmpty) { ProjectsGetResponseADM(projects = projects) }
               else { throw NotFoundException(s"No projects found") }
    } yield result

  /**
   * Gets the project with the given project IRI, shortname, shortcode or UUID and returns the information as a [[ProjectADM]].
   *
   * @param identifier           the IRI, shortname, shortcode or UUID of the project.
   * @param skipCache            if `true`, doesn't check the cache and tries to retrieve the project directly from the triplestore
   * @return information about the project as an optional [[ProjectADM]].
   */
  def getSingleProjectADM(
    identifier: ProjectIdentifierADM,
    skipCache: Boolean = false
  ): Future[Option[ProjectADM]] =
    tracedFuture("admin-get-project") {

      log.debug(
        s"getSingleProjectADM - id: {}, skipCache: {}",
        getId(identifier),
        skipCache
      )

      for {
        maybeProjectADM <-
          if (skipCache) {
            // getting directly from triplestore
            getProjectFromTriplestore(identifier = identifier)
          } else {
            // getting from cache or triplestore
            getProjectFromCacheOrTriplestore(identifier = identifier)
          }

        _ =
          if (maybeProjectADM.nonEmpty) {
            log.debug("getSingleProjectADM - successfully retrieved project: {}", getId(identifier))
          } else {
            log.debug("getSingleProjectADM - could not retrieve project: {}", getId(identifier))
          }

      } yield maybeProjectADM
    }

  /**
   * Gets the project with the given project IRI, shortname, shortcode or UUID and returns the information
   * as a [[ProjectGetResponseADM]].
   *
   * @param identifier           the IRI, shortname, shortcode or UUID of the project.
   * @return information about the project as a [[ProjectGetResponseADM]].
   * @throws NotFoundException when no project for the given IRI can be found
   */
  def getSingleProjectADMRequest(identifier: ProjectIdentifierADM): Future[ProjectGetResponseADM] = for {
    maybeProject <- getSingleProjectADM(identifier)
    project       = maybeProject.getOrElse(throw NotFoundException(s"Project '${getId(identifier)}' not found"))
  } yield ProjectGetResponseADM(project)

  /**
   * Gets the members of a project with the given IRI, shortname, shortcode or UUID. Returns an empty list
   * if none are found.
   *
   * @param identifier           the IRI, shortname, shortcode or UUID of the project.
   * @param requestingUser       the user making the request.
   * @return the members of a project as a [[ProjectMembersGetResponseADM]]
   */
  private def projectMembersGetRequestADM(
    identifier: ProjectIdentifierADM,
    requestingUser: UserADM
  ): Future[ProjectMembersGetResponseADM] =
    for {

      /* Get project and verify permissions. */
      project <- getSingleProjectADM(
                   identifier = identifier
                 )

      _ =
        if (project.isEmpty) {
          throw NotFoundException(s"Project '${getId(identifier)}' not found.")
        } else {
          if (
            !requestingUser.permissions.isSystemAdmin && !requestingUser.permissions.isProjectAdmin(
              project.get.id
            ) && !requestingUser.isSystemUser
          ) {
            throw ForbiddenException("SystemAdmin or ProjectAdmin permissions are required.")
          }
        }

      sparqlQueryString <- Future(
                             org.knora.webapi.messages.twirl.queries.sparql.admin.txt
                               .getProjectMembers(
                                 maybeIri = identifier.asIriIdentifierOption,
                                 maybeShortname = identifier.asShortnameIdentifierOption,
                                 maybeShortcode = identifier.asShortcodeIdentifierOption
                               )
                               .toString()
                           )

      projectMembersResponse <- appActor
                                  .ask(
                                    SparqlExtendedConstructRequest(
                                      sparql = sparqlQueryString
                                    )
                                  )
                                  .mapTo[SparqlExtendedConstructResponse]

      statements = projectMembersResponse.statements.toList

      // get project member IRI from results rows
      userIris: Seq[IRI] =
        if (statements.nonEmpty) {
          statements.map(_._1.toString)
        } else {
          Seq.empty[IRI]
        }

      maybeUserFutures: Seq[Future[Option[UserADM]]] =
        userIris.map { userIri =>
          appActor
            .ask(
              UserGetADM(
                identifier = UserIdentifierADM(maybeIri = Some(userIri)),
                userInformationTypeADM = UserInformationTypeADM.Restricted,
                requestingUser = KnoraSystemInstances.Users.SystemUser
              )
            )
            .mapTo[Option[UserADM]]
        }
      maybeUsers: Seq[Option[UserADM]] <- Future.sequence(maybeUserFutures)
      users: Seq[UserADM]               = maybeUsers.flatten

    } yield ProjectMembersGetResponseADM(members = users)

  /**
   * Gets the admin members of a project with the given IRI, shortname, shortcode or UUIDe. Returns an empty list
   * if none are found
   *
   * @param identifier           the IRI, shortname, shortcode or UUID of the project.
   * @param requestingUser       the user making the request.
   * @return the members of a project as a [[ProjectMembersGetResponseADM]]
   */
  private def projectAdminMembersGetRequestADM(
    identifier: ProjectIdentifierADM,
    requestingUser: UserADM
  ): Future[ProjectAdminMembersGetResponseADM] =
    for {
      /* Get project and verify permissions. */
      project <- getSingleProjectADM(
                   identifier = identifier
                 )

      _ =
        if (project.isEmpty) {
          throw NotFoundException(s"Project '${getId(identifier)}' not found.")
        } else {
          if (!requestingUser.permissions.isSystemAdmin && !requestingUser.permissions.isProjectAdmin(project.get.id)) {
            throw ForbiddenException("SystemAdmin or ProjectAdmin permissions are required.")
          }
        }

      sparqlQueryString <- Future(
                             org.knora.webapi.messages.twirl.queries.sparql.admin.txt
                               .getProjectAdminMembers(
                                 maybeIri = identifier.asIriIdentifierOption,
                                 maybeShortname = identifier.asShortnameIdentifierOption,
                                 maybeShortcode = identifier.asShortcodeIdentifierOption
                               )
                               .toString()
                           )

      projectAdminMembersResponse <- appActor
                                       .ask(
                                         SparqlExtendedConstructRequest(
                                           sparql = sparqlQueryString
                                         )
                                       )
                                       .mapTo[SparqlExtendedConstructResponse]

      statements = projectAdminMembersResponse.statements.toList

      // get project member IRI from results rows
      userIris: Seq[IRI] =
        if (statements.nonEmpty) {
          statements.map(_._1.toString)
        } else {
          Seq.empty[IRI]
        }

      maybeUserFutures: Seq[Future[Option[UserADM]]] = userIris.map { userIri =>
                                                         appActor
                                                           .ask(
                                                             UserGetADM(
                                                               identifier = UserIdentifierADM(maybeIri = Some(userIri)),
                                                               userInformationTypeADM =
                                                                 UserInformationTypeADM.Restricted,
                                                               requestingUser = KnoraSystemInstances.Users.SystemUser
                                                             )
                                                           )
                                                           .mapTo[Option[UserADM]]
                                                       }
      maybeUsers: Seq[Option[UserADM]] <- Future.sequence(maybeUserFutures)
      users: Seq[UserADM]               = maybeUsers.flatten

    } yield ProjectAdminMembersGetResponseADM(members = users)

  /**
   * Gets all unique keywords for all projects and returns them. Returns an empty list if none are found.
   *
   * @return all keywords for all projects as [[ProjectsKeywordsGetResponseADM]]
   */
  private def projectsKeywordsGetRequestADM(): Future[ProjectsKeywordsGetResponseADM] =
    for {
      projects <- projectsGetADM()

      keywords: Seq[String] = projects.flatMap(_.keywords).distinct.sorted

    } yield ProjectsKeywordsGetResponseADM(keywords = keywords)

  /**
   * Gets all keywords for a single project and returns them. Returns an empty list if none are found.
   *
   * @param projectIri           the IRI of the project.
   * @param requestingUser       the user making the request.
   * @return keywords for a projects as [[ProjectKeywordsGetResponseADM]]
   */
  private def projectKeywordsGetRequestADM(
    projectIri: IRI,
    requestingUser: UserADM
  ): Future[ProjectKeywordsGetResponseADM] =
    for {
      maybeProject <- getSingleProjectADM(
                        identifier = IriIdentifier
                          .fromString(projectIri)
                          .getOrElseWith(e => throw BadRequestException(e.head.getMessage))
                      )

      keywords: Seq[String] = maybeProject match {
                                case Some(p) => p.keywords
                                case None    => throw NotFoundException(s"Project '$projectIri' not found.")
                              }

    } yield ProjectKeywordsGetResponseADM(keywords = keywords)

  private def projectDataGetRequestADM(
    projectIdentifier: ProjectIdentifierADM,
    requestingUser: UserADM
  ): Future[ProjectDataGetResponseADM] = {

    /**
     * Represents a named graph to be saved to a TriG file.
     *
     * @param graphIri the IRI of the named graph.
     * @param tempDir  the directory in which the file is to be saved.
     */
    case class NamedGraphTrigFile(graphIri: IRI, tempDir: Path) {
      lazy val dataFile: Path = {
        val filename = graphIri.replaceAll(":", "_").replaceAll("/", "_").replaceAll("""\.""", "_") +
          ".trig"

        tempDir.resolve(filename)
      }
    }

    /**
     * An [[RdfStreamProcessor]] for combining several named graphs into one.
     *
     * @param formattingStreamProcessor an [[RdfStreamProcessor]] for writing the combined result.
     */
    class CombiningRdfProcessor(formattingStreamProcessor: RdfStreamProcessor) extends RdfStreamProcessor {
      private var startedStatements = false

      // Ignore this, since it will be done before the first file is written.
      override def start(): Unit = {}

      // Ignore this, since it will be done after the last file is written.
      override def finish(): Unit = {}

      override def processNamespace(prefix: IRI, namespace: IRI): Unit =
        // Only accept namespaces from the first graph, to prevent conflicts.
        if (!startedStatements) {
          formattingStreamProcessor.processNamespace(prefix, namespace)
        }

      override def processStatement(statement: Statement): Unit = {
        startedStatements = true
        formattingStreamProcessor.processStatement(statement)
      }
    }

    /**
     * Combines several TriG files into one.
     *
     * @param namedGraphTrigFiles the TriG files to combine.
     * @param resultFile          the output file.
     */
    def combineGraphs(namedGraphTrigFiles: Seq[NamedGraphTrigFile], resultFile: Path): Unit = {
      val rdfFormatUtil: RdfFormatUtil                                = RdfFeatureFactory.getRdfFormatUtil()
      var maybeBufferedFileOutputStream: Option[BufferedOutputStream] = None

      val trigFileTry: Try[Unit] = Try {
        maybeBufferedFileOutputStream = Some(new BufferedOutputStream(Files.newOutputStream(resultFile)))

        val formattingStreamProcessor: RdfStreamProcessor = rdfFormatUtil.makeFormattingStreamProcessor(
          outputStream = maybeBufferedFileOutputStream.get,
          rdfFormat = TriG
        )

        val combiningRdfProcessor = new CombiningRdfProcessor(formattingStreamProcessor)
        formattingStreamProcessor.start()

        for (namedGraphTrigFile: NamedGraphTrigFile <- namedGraphTrigFiles) {
          val namedGraphTry: Try[Unit] = Try {
            rdfFormatUtil.parseWithStreamProcessor(
              rdfSource =
                RdfInputStreamSource(new BufferedInputStream(Files.newInputStream(namedGraphTrigFile.dataFile))),
              rdfFormat = TriG,
              rdfStreamProcessor = combiningRdfProcessor
            )
          }

          Files.delete(namedGraphTrigFile.dataFile)

          namedGraphTry match {
            case Success(_)  => ()
            case Failure(ex) => throw ex
          }
        }

        formattingStreamProcessor.finish()
      }

      maybeBufferedFileOutputStream.foreach(_.close)

      trigFileTry match {
        case Success(_)  => ()
        case Failure(ex) => throw ex
      }
    }

    for {
      // Get the project info.
      maybeProject: Option[ProjectADM] <- getSingleProjectADM(
                                            identifier = projectIdentifier
                                          )

      project: ProjectADM = maybeProject.getOrElse(
                              throw NotFoundException(s"Project '${getId(projectIdentifier)}' not found.")
                            )

      // Check that the user has permission to download the data.
      _ = if (!(requestingUser.permissions.isSystemAdmin || requestingUser.permissions.isProjectAdmin(project.id))) {
            throw ForbiddenException(
              s"You are logged in as ${requestingUser.username}, but only a system administrator or project administrator can request a project's data"
            )
          }

      // Make a temporary directory for the downloaded data.
      tempDir = Files.createTempDirectory(project.shortname)
      _       = log.info("Downloading project data to temporary directory " + tempDir.toAbsolutePath)

      // Download the project's named graphs.

      projectDataNamedGraph: IRI = stringFormatter.projectDataNamedGraphV2(project)
      graphsToDownload: Seq[IRI] = project.ontologies :+ projectDataNamedGraph
      projectSpecificNamedGraphTrigFiles: Seq[NamedGraphTrigFile] =
        graphsToDownload.map(graphIri => NamedGraphTrigFile(graphIri = graphIri, tempDir = tempDir))

      projectSpecificNamedGraphTrigFileWriteFutures: Seq[Future[FileWrittenResponse]] =
        projectSpecificNamedGraphTrigFiles.map { trigFile =>
          for {
            fileWrittenResponse: FileWrittenResponse <-
              appActor
                .ask(
                  NamedGraphFileRequest(
                    graphIri = trigFile.graphIri,
                    outputFile = trigFile.dataFile,
                    outputFormat = TriG
                  )
                )
                .mapTo[FileWrittenResponse]
          } yield fileWrittenResponse
        }

      _: Seq[FileWrittenResponse] <- Future.sequence(projectSpecificNamedGraphTrigFileWriteFutures)

      // Download the project's admin data.

      adminDataNamedGraphTrigFile = NamedGraphTrigFile(graphIri = ADMIN_DATA_GRAPH, tempDir = tempDir)

      adminDataSparql: String = org.knora.webapi.messages.twirl.queries.sparql.admin.txt
                                  .getProjectAdminData(
                                    projectIri = project.id
                                  )
                                  .toString()

      _: FileWrittenResponse <- appActor
                                  .ask(
                                    SparqlConstructFileRequest(
                                      sparql = adminDataSparql,
                                      graphIri = adminDataNamedGraphTrigFile.graphIri,
                                      outputFile = adminDataNamedGraphTrigFile.dataFile,
                                      outputFormat = TriG
                                    )
                                  )
                                  .mapTo[FileWrittenResponse]

      // Download the project's permission data.

      permissionDataNamedGraphTrigFile = NamedGraphTrigFile(graphIri = PERMISSIONS_DATA_GRAPH, tempDir = tempDir)

      permissionDataSparql: String = org.knora.webapi.messages.twirl.queries.sparql.admin.txt
                                       .getProjectPermissions(
                                         projectIri = project.id
                                       )
                                       .toString()

      _: FileWrittenResponse <- appActor
                                  .ask(
                                    SparqlConstructFileRequest(
                                      sparql = permissionDataSparql,
                                      graphIri = permissionDataNamedGraphTrigFile.graphIri,
                                      outputFile = permissionDataNamedGraphTrigFile.dataFile,
                                      outputFormat = TriG
                                    )
                                  )
                                  .mapTo[FileWrittenResponse]

      // Stream the combined results into the output file.

      namedGraphTrigFiles: Seq[NamedGraphTrigFile] =
        projectSpecificNamedGraphTrigFiles :+ adminDataNamedGraphTrigFile :+ permissionDataNamedGraphTrigFile
      resultFile: Path = tempDir.resolve(project.shortname + ".trig")
      _                = combineGraphs(namedGraphTrigFiles = namedGraphTrigFiles, resultFile = resultFile)
    } yield ProjectDataGetResponseADM(resultFile)
  }

  /**
   * Get project's restricted view settings.
   *
   * @param identifier           the project's identifier (IRI / shortcode / shortname / UUID)
   *
   * @param requestingUser       the user making the request.
   * @return [[ProjectRestrictedViewSettingsADM]]
   */
  private def projectRestrictedViewSettingsGetADM(
    identifier: ProjectIdentifierADM,
    requestingUser: UserADM
  ): Future[Option[ProjectRestrictedViewSettingsADM]] =
    // ToDo: We have two possible NotFound scenarios: 1. Project, 2. ProjectRestrictedViewSettings resource. How to send the client the correct NotFound reply?
    for {
      sparqlQuery <- Future(
                       org.knora.webapi.messages.twirl.queries.sparql.admin.txt
                         .getProjects(
                           maybeIri = identifier.asIriIdentifierOption,
                           maybeShortname = identifier.asShortnameIdentifierOption,
                           maybeShortcode = identifier.asShortcodeIdentifierOption
                         )
                         .toString()
                     )

      projectResponse <- appActor
                           .ask(
                             SparqlExtendedConstructRequest(
                               sparql = sparqlQuery
                             )
                           )
                           .mapTo[SparqlExtendedConstructResponse]

      restrictedViewSettings =
        if (projectResponse.statements.nonEmpty) {

          val (_, propsMap): (SubjectV2, Map[SmartIri, Seq[LiteralV2]]) = projectResponse.statements.head

          val size = propsMap
            .get(OntologyConstants.KnoraAdmin.ProjectRestrictedViewSize.toSmartIri)
            .map(_.head.asInstanceOf[StringLiteralV2].value)
          val watermark = propsMap
            .get(OntologyConstants.KnoraAdmin.ProjectRestrictedViewWatermark.toSmartIri)
            .map(_.head.asInstanceOf[StringLiteralV2].value)

          Some(ProjectRestrictedViewSettingsADM(size, watermark))
        } else {
          None
        }

    } yield restrictedViewSettings

  /**
   * Get project's restricted view settings.
   *
   * @param identifier     the project's identifier (IRI / shortcode / shortname / UUID)
   *
   * @param requestingUser the user making the request.
   * @return [[ProjectRestrictedViewSettingsGetResponseADM]]
   */
  private def projectRestrictedViewSettingsGetRequestADM(
    identifier: ProjectIdentifierADM,
    requestingUser: UserADM
  ): Future[ProjectRestrictedViewSettingsGetResponseADM] =
    for {
      maybeSettings: Option[ProjectRestrictedViewSettingsADM] <-
        projectRestrictedViewSettingsGetADM(
          identifier = identifier,
          requestingUser = requestingUser
        )

      settings = maybeSettings match {
                   case Some(s) => s
                   case None =>
                     throw NotFoundException(
                       s"Project '${getId(identifier)}' not found."
                     )
                 }

    } yield ProjectRestrictedViewSettingsGetResponseADM(settings)

  /**
   * Update project's basic information.
   *
   * @param projectIri           the IRI of the project.
   * @param projectUpdatePayload the update payload.
   * @param requestingUser       the user making the request.
   * @param apiRequestID         the unique api request ID.
   * @return a [[ProjectOperationResponseADM]].
   * @throws ForbiddenException in the case that the user is not allowed to perform the operation.
   */
  private def changeBasicInformationRequestADM(
    projectIri: Iri.ProjectIri,
    projectUpdatePayload: ProjectUpdatePayloadADM,
    requestingUser: UserADM,
    apiRequestID: UUID
  ): Future[ProjectOperationResponseADM] = {

    /**
     * The actual change project task run with an IRI lock.
     */
    def changeProjectTask(
      projectIri: Iri.ProjectIri,
      projectUpdatePayload: ProjectUpdatePayloadADM,
      requestingUser: UserADM
    ): Future[ProjectOperationResponseADM] = {
      // check if the requesting user is allowed to perform updates
      if (!requestingUser.permissions.isProjectAdmin(projectIri.value) && !requestingUser.permissions.isSystemAdmin) {
        throw ForbiddenException("Project's information can only be changed by a project or system admin.")
      }

      for {
        result <- updateProjectADM(
                    projectIri = projectIri,
                    projectUpdatePayload = projectUpdatePayload,
                    requestingUser = KnoraSystemInstances.Users.SystemUser
                  )

      } yield result
    }

    for {
      // run the change status task with an IRI lock
      taskResult <- IriLocker.runWithIriLock(
                      apiRequestID,
                      projectIri.value,
                      () => changeProjectTask(projectIri, projectUpdatePayload, requestingUser)
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
   *
   * @return a [[ProjectOperationResponseADM]].
   * @throws NotFoundException in the case that the project's IRI is not found.
   */
  private def updateProjectADM(
    projectIri: Iri.ProjectIri,
    projectUpdatePayload: ProjectUpdatePayloadADM,
    requestingUser: UserADM
  ): Future[ProjectOperationResponseADM] = {

    val parametersCount = List(
      projectUpdatePayload.shortname,
      projectUpdatePayload.longname,
      projectUpdatePayload.description,
      projectUpdatePayload.keywords,
      projectUpdatePayload.logo,
      projectUpdatePayload.status,
      projectUpdatePayload.selfjoin
    ).flatten.size

    if (parametersCount == 0) throw BadRequestException("No data would be changed. Aborting update request.")

    for {
      maybeCurrentProject: Option[ProjectADM] <-
        getSingleProjectADM(
          identifier = IriIdentifier
            .fromString(projectIri.value)
            .getOrElseWith(e => throw BadRequestException(e.head.getMessage)),
          skipCache = true
        )

      _ = if (maybeCurrentProject.isEmpty) {
            throw NotFoundException(s"Project '${projectIri.value}' not found. Aborting update request.")
          }
      // we are changing the project, so lets get rid of the cached copy
      _ = appActor.ask(CacheServiceFlushDB(KnoraSystemInstances.Users.SystemUser))

      /* Update project */
      updateProjectSparqlString <- Future(
                                     org.knora.webapi.messages.twirl.queries.sparql.admin.txt
                                       .updateProject(
                                         adminNamedGraphIri = "http://www.knora.org/data/admin",
                                         projectIri = projectIri.value,
                                         maybeShortname = projectUpdatePayload.shortname.map(_.value),
                                         maybeLongname = projectUpdatePayload.longname.map(_.value),
                                         maybeDescriptions = projectUpdatePayload.description.map(_.value),
                                         maybeKeywords = projectUpdatePayload.keywords.map(_.value),
                                         maybeLogo = projectUpdatePayload.logo.map(_.value),
                                         maybeStatus = projectUpdatePayload.status.map(_.value),
                                         maybeSelfjoin = projectUpdatePayload.selfjoin.map(_.value)
                                       )
                                       .toString
                                   )

      _ <- appActor
             .ask(SparqlUpdateRequest(updateProjectSparqlString))
             .mapTo[SparqlUpdateResponse]

      /* Verify that the project was updated. */
      maybeUpdatedProject <-
        getSingleProjectADM(
          identifier = IriIdentifier
            .fromString(projectIri.value)
            .getOrElseWith(e => throw BadRequestException(e.head.getMessage)),
          skipCache = true
        )

      updatedProject: ProjectADM =
        maybeUpdatedProject.getOrElse(
          throw UpdateNotPerformedException("Project was not updated. Please report this as a possible bug.")
        )

      _ = log.debug(
            "updateProjectADM - projectUpdatePayload: {} /  updatedProject: {}",
            projectUpdatePayload,
            updatedProject
          )

      _ = projectUpdatePayload.shortname.map { shortname =>
            val unescapedShortName: String = stringFormatter.fromSparqlEncodedString(shortname.value)
            if (updatedProject.shortname != unescapedShortName)
              throw UpdateNotPerformedException(
                "Project's 'shortname' was not updated. Please report this as a possible bug."
              )
          }

      _ = projectUpdatePayload.longname.map { longname: Project.Name =>
            val unescapedLongname: String = stringFormatter.fromSparqlEncodedString(longname.value)
            updatedProject.longname.map { value =>
              if (value != unescapedLongname)
                throw UpdateNotPerformedException(
                  s"Project's 'longname' was not updated. Please report this as a possible bug."
                )
            }
          }

      _ = projectUpdatePayload.description.map { descriptions =>
            val unescapedDescriptions: Seq[V2.StringLiteralV2] = descriptions.value.map(desc =>
              V2.StringLiteralV2(stringFormatter.fromSparqlEncodedString(desc.value), desc.language)
            )
            if (updatedProject.description.diff(unescapedDescriptions).nonEmpty)
              throw UpdateNotPerformedException(
                "Project's 'description' was not updated. Please report this as a possible bug."
              )
          }

      _ = projectUpdatePayload.keywords.map { keywords =>
            val unescapedKeywords: Seq[String] = keywords.value.map(key => stringFormatter.fromSparqlEncodedString(key))
            if (updatedProject.keywords.sorted != unescapedKeywords.sorted)
              throw UpdateNotPerformedException(
                "Project's 'keywords' was not updated. Please report this as a possible bug."
              )
          }

      _ = projectUpdatePayload.logo.map { logo: Project.Logo =>
            updatedProject.logo.map { value =>
              val unescapedLogo: String = stringFormatter.fromSparqlEncodedString(logo.value)
              if (value != unescapedLogo)
                throw UpdateNotPerformedException(
                  s"Project's 'longname' was not updated. Please report this as a possible bug."
                )
            }
          }

      _ = projectUpdatePayload.status.map { projectStatus: Project.ProjectStatus =>
            if (updatedProject.status != projectStatus.value)
              throw UpdateNotPerformedException(
                "Project's 'status' was not updated. Please report this as a possible bug."
              )
          }

      _ = projectUpdatePayload.selfjoin.map { selfjoin =>
            if (updatedProject.selfjoin != selfjoin.value)
              throw UpdateNotPerformedException(
                "Project's 'selfjoin' status was not updated. Please report this as a possible bug."
              )
          }

    } yield ProjectOperationResponseADM(project = updatedProject)
  }

  /**
   * Creates a project.
   *
   * @param createProjectRequest the new project's information.
   *
   * @param requestingUser       the user that is making the request.
   * @param apiRequestID         the unique api request ID.
   * @return a [[ProjectOperationResponseADM]].
   * @throws ForbiddenException      in the case that the user is not allowed to perform the operation.
   * @throws DuplicateValueException in the case when either the shortname or shortcode are not unique.
   * @throws BadRequestException     in the case when the shortcode is invalid.
   */
  private def projectCreateRequestADM(
    createProjectRequest: ProjectCreatePayloadADM,
    requestingUser: UserADM,
    apiRequestID: UUID
  ): Future[ProjectOperationResponseADM] = {

    /**
     * Creates following permissions for the new project
     * 1. Permissions for project admins to do all operations on project level and to create, modify, delete, change rights,
     * view, and restricted view of all new resources and values that belong to this project.
     * 2. Permissions for project members to create, modify, view and restricted view of all new resources and values that belong to this project.
     *
     * @param projectIri the IRI of the new project.
     * @throws BadRequestException if a permission is not created.
     */
    def createPermissionsForAdminsAndMembersOfNewProject(projectIri: IRI, projectShortCode: String): Future[Unit] =
      for {
        // Give the admins of the new project rights for any operation in project level, and rights to create resources.
        _ <- appActor
               .ask(
                 AdministrativePermissionCreateRequestADM(
                   createRequest = CreateAdministrativePermissionAPIRequestADM(
                     forProject = projectIri,
                     forGroup = OntologyConstants.KnoraAdmin.ProjectAdmin,
                     hasPermissions =
                       Set(PermissionADM.ProjectAdminAllPermission, PermissionADM.ProjectResourceCreateAllPermission)
                   ).prepareHasPermissions,
                   requestingUser = requestingUser,
                   apiRequestID = UUID.randomUUID()
                 )
               )
               .mapTo[AdministrativePermissionCreateResponseADM]

        // Give the members of the new project rights to create resources.
        _ <- appActor
               .ask(
                 AdministrativePermissionCreateRequestADM(
                   createRequest = CreateAdministrativePermissionAPIRequestADM(
                     forProject = projectIri,
                     forGroup = OntologyConstants.KnoraAdmin.ProjectMember,
                     hasPermissions = Set(PermissionADM.ProjectResourceCreateAllPermission)
                   ).prepareHasPermissions,
                   requestingUser = requestingUser,
                   apiRequestID = UUID.randomUUID()
                 )
               )
               .mapTo[AdministrativePermissionCreateResponseADM]

        // Give the admins of the new project rights to change rights, modify, delete, view,
        // and restricted view of all resources and values that belong to the project.
        _ <- appActor
               .ask(
                 DefaultObjectAccessPermissionCreateRequestADM(
                   createRequest = CreateDefaultObjectAccessPermissionAPIRequestADM(
                     forProject = projectIri,
                     forGroup = Some(OntologyConstants.KnoraAdmin.ProjectAdmin),
                     hasPermissions = Set(
                       PermissionADM.changeRightsPermission(OntologyConstants.KnoraAdmin.ProjectAdmin),
                       PermissionADM.modifyPermission(OntologyConstants.KnoraAdmin.ProjectMember)
                     )
                   ).prepareHasPermissions,
                   requestingUser = requestingUser,
                   apiRequestID = UUID.randomUUID()
                 )
               )
               .mapTo[DefaultObjectAccessPermissionCreateResponseADM]

        // Give the members of the new project rights to modify, view, and restricted view of all resources and values
        // that belong to the project.
        _ <- appActor
               .ask(
                 DefaultObjectAccessPermissionCreateRequestADM(
                   createRequest = CreateDefaultObjectAccessPermissionAPIRequestADM(
                     forProject = projectIri,
                     forGroup = Some(OntologyConstants.KnoraAdmin.ProjectMember),
                     hasPermissions = Set(
                       PermissionADM.changeRightsPermission(OntologyConstants.KnoraAdmin.ProjectAdmin),
                       PermissionADM.modifyPermission(OntologyConstants.KnoraAdmin.ProjectMember)
                     )
                   ).prepareHasPermissions,
                   requestingUser = requestingUser,
                   apiRequestID = UUID.randomUUID()
                 )
               )
               .mapTo[DefaultObjectAccessPermissionCreateResponseADM]
      } yield ()

    def projectCreateTask(
      createProjectRequest: ProjectCreatePayloadADM,
      requestingUser: UserADM
    ): Future[ProjectOperationResponseADM] =
      for {
        // check if the supplied shortname is unique
        shortnameExists <- projectByShortnameExists(createProjectRequest.shortname.value)
        _ = if (shortnameExists) {
              throw DuplicateValueException(
                s"Project with the shortname: '${createProjectRequest.shortname.value}' already exists"
              )
            }

        // check if the optionally supplied shortcode is valid and unique
        shortcodeExists <- projectByShortcodeExists(createProjectRequest.shortcode.value)

        _ = if (shortcodeExists) {
              throw DuplicateValueException(
                s"Project with the shortcode: '${createProjectRequest.shortcode.value}' already exists"
              )
            }

        // check if the requesting user is allowed to create project
        _ = if (!requestingUser.permissions.isSystemAdmin) {
              // not a system admin
              throw ForbiddenException("A new project can only be created by a system admin.")
            }

        // check the custom IRI; if not given, create an unused IRI
        customProjectIri: Option[SmartIri] = createProjectRequest.id.map(_.value).map(_.toSmartIri)
        newProjectIRI: IRI <- iriService.checkOrCreateEntityIri(
                                customProjectIri,
                                stringFormatter.makeRandomProjectIri
                              )

        maybeLongname = createProjectRequest.longname match {
                          case Some(value) => Some(value.value)
                          case None        => None
                        }

        maybeLogo = createProjectRequest.logo match {
                      case Some(value) => Some(value.value)
                      case None        => None
                    }

        createNewProjectSparqlString = org.knora.webapi.messages.twirl.queries.sparql.admin.txt
                                         .createNewProject(
                                           adminNamedGraphIri = OntologyConstants.NamedGraphs.AdminNamedGraph,
                                           projectIri = newProjectIRI,
                                           projectClassIri = OntologyConstants.KnoraAdmin.KnoraProject,
                                           shortname = createProjectRequest.shortname.value,
                                           shortcode = createProjectRequest.shortcode.value,
                                           maybeLongname = maybeLongname,
                                           maybeDescriptions = if (createProjectRequest.description.value.nonEmpty) {
                                             Some(createProjectRequest.description.value)
                                           } else None,
                                           maybeKeywords = if (createProjectRequest.keywords.value.nonEmpty) {
                                             Some(createProjectRequest.keywords.value)
                                           } else None,
                                           maybeLogo = maybeLogo,
                                           status = createProjectRequest.status.value,
                                           hasSelfJoinEnabled = createProjectRequest.selfjoin.value
                                         )
                                         .toString

        _ <- appActor
               .ask(SparqlUpdateRequest(createNewProjectSparqlString))
               .mapTo[SparqlUpdateResponse]

        // try to retrieve newly created project (will also add to cache)
        maybeNewProjectADM <-
          getSingleProjectADM(
            identifier = IriIdentifier
              .fromString(newProjectIRI)
              .getOrElseWith(e => throw BadRequestException(e.head.getMessage)),
            skipCache = true
          )

        // check to see if we could retrieve the new project
        newProjectADM = maybeNewProjectADM.getOrElse(
                          throw UpdateNotPerformedException(
                            s"Project $newProjectIRI was not created. Please report this as a possible bug."
                          )
                        )
        // create permissions for admins and members of the new group
        _ <- createPermissionsForAdminsAndMembersOfNewProject(newProjectIRI, newProjectADM.shortcode)

      } yield ProjectOperationResponseADM(project = newProjectADM.unescape)

    for {
      // run user creation with an global IRI lock
      taskResult <- IriLocker.runWithIriLock(
                      apiRequestID,
                      PROJECTS_GLOBAL_LOCK_IRI,
                      () => projectCreateTask(createProjectRequest, requestingUser)
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
  private def getProjectFromCacheOrTriplestore(
    identifier: ProjectIdentifierADM
  ): Future[Option[ProjectADM]] =
    if (cacheServiceSettings.cacheServiceEnabled) {
      // caching enabled
      getProjectFromCache(identifier).flatMap {
        case None =>
          // none found in cache. getting from triplestore.
          getProjectFromTriplestore(identifier = identifier).flatMap {
            case None =>
              // also none found in triplestore. finally returning none.
              log.debug("getProjectFromCacheOrTriplestore - not found in cache and in triplestore")
              FastFuture.successful(None)
            case Some(project) =>
              // found a project in the triplestore. need to write to cache.
              log.debug(
                "getProjectFromCacheOrTriplestore - not found in cache but found in triplestore. need to write to cache."
              )
              // writing project to cache and afterwards returning the project found in the triplestore
              writeProjectADMToCache(project).map(_ => Some(project))
          }
        case Some(project) =>
          log.debug("getProjectFromCacheOrTriplestore - found in cache. returning project.")
          FastFuture.successful(Some(project))
      }
    } else {
      // caching disabled
      log.debug("getProjectFromCacheOrTriplestore - caching disabled. getting from triplestore.")
      getProjectFromTriplestore(identifier = identifier)
    }

  /**
   * Tries to retrieve a [[ProjectADM]] from the triplestore.
   */
  private def getProjectFromTriplestore(
    identifier: ProjectIdentifierADM
  ): Future[Option[ProjectADM]] =
    for {
      sparqlQuery <- Future(
                       org.knora.webapi.messages.twirl.queries.sparql.admin.txt
                         .getProjects(
                           maybeIri = identifier.asIriIdentifierOption,
                           maybeShortname = identifier.asShortnameIdentifierOption,
                           maybeShortcode = identifier.asShortcodeIdentifierOption
                         )
                         .toString()
                     )

      projectResponse <- appActor
                           .ask(
                             SparqlExtendedConstructRequest(
                               sparql = sparqlQuery
                             )
                           )
                           .mapTo[SparqlExtendedConstructResponse]

      projectIris = projectResponse.statements.keySet.map(_.toString)

      ontologies <-
        if (projectResponse.statements.nonEmpty) {
          getOntologiesForProjects(projectIris)
        } else {
          FastFuture.successful(Map.empty[IRI, Seq[IRI]])
        }

      maybeProjectADM: Option[ProjectADM] =
        if (projectResponse.statements.nonEmpty) {
          log.debug("getProjectFromTriplestore - triplestore hit for: {}", getId(identifier))
          val projectOntologies = ontologies.getOrElse(projectIris.head, Seq.empty[IRI])
          Some(statements2ProjectADM(statements = projectResponse.statements.head, ontologies = projectOntologies))
        } else {
          log.debug("getProjectFromTriplestore - no triplestore hit for: {}", getId(identifier))
          None
        }
    } yield maybeProjectADM

  /**
   * Helper method that turns SPARQL result rows into a [[ProjectADM]].
   *
   * @param statements results from the SPARQL query representing information about the project.
   * @param ontologies the ontologies in the project.
   * @return a [[ProjectADM]] representing information about project.
   */
  private def statements2ProjectADM(
    statements: (SubjectV2, Map[SmartIri, Seq[LiteralV2]]),
    ontologies: Seq[IRI]
  ): ProjectADM = {

    val projectIri: IRI                         = statements._1.toString
    val propsMap: Map[SmartIri, Seq[LiteralV2]] = statements._2

    // transformation from StringLiteralV2 to V2.StringLiteralV2 for project description
    val descriptionsStringLiteralV2: Seq[StringLiteralV2] = propsMap
      .getOrElse(
        OntologyConstants.KnoraAdmin.ProjectDescription.toSmartIri,
        throw InconsistentRepositoryDataException(s"Project: $projectIri has no description defined.")
      )
      .map(_.asInstanceOf[StringLiteralV2])
    val descriptionsV2StringLiteralV2: Seq[V2.StringLiteralV2] =
      descriptionsStringLiteralV2.map(desc => V2.StringLiteralV2(desc.value, desc.language))

    ProjectADM(
      id = projectIri,
      shortname = propsMap
        .getOrElse(
          OntologyConstants.KnoraAdmin.ProjectShortname.toSmartIri,
          throw InconsistentRepositoryDataException(s"Project: $projectIri has no shortname defined.")
        )
        .head
        .asInstanceOf[StringLiteralV2]
        .value,
      shortcode = propsMap
        .getOrElse(
          OntologyConstants.KnoraAdmin.ProjectShortcode.toSmartIri,
          throw InconsistentRepositoryDataException(s"Project: $projectIri has no shortcode defined.")
        )
        .head
        .asInstanceOf[StringLiteralV2]
        .value,
      longname = propsMap
        .get(OntologyConstants.KnoraAdmin.ProjectLongname.toSmartIri)
        .map(_.head.asInstanceOf[StringLiteralV2].value),
      description = descriptionsV2StringLiteralV2,
      keywords = propsMap
        .getOrElse(OntologyConstants.KnoraAdmin.ProjectKeyword.toSmartIri, Seq.empty[String])
        .map(_.asInstanceOf[StringLiteralV2].value)
        .sorted,
      logo = propsMap
        .get(OntologyConstants.KnoraAdmin.ProjectLogo.toSmartIri)
        .map(_.head.asInstanceOf[StringLiteralV2].value),
      ontologies = ontologies,
      status = propsMap
        .getOrElse(
          OntologyConstants.KnoraAdmin.Status.toSmartIri,
          throw InconsistentRepositoryDataException(s"Project: $projectIri has no status defined.")
        )
        .head
        .asInstanceOf[BooleanLiteralV2]
        .value,
      selfjoin = propsMap
        .getOrElse(
          OntologyConstants.KnoraAdmin.HasSelfJoinEnabled.toSmartIri,
          throw InconsistentRepositoryDataException(s"Project: $projectIri has no hasSelfJoinEnabled defined.")
        )
        .head
        .asInstanceOf[BooleanLiteralV2]
        .value
    ).unescape
  }

  /**
   * Helper method for checking if a project identified by shortname exists.
   *
   * @param shortname the shortname of the project.
   * @return a [[Boolean]].
   */
  private def projectByShortnameExists(shortname: String): Future[Boolean] =
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
  private def projectByShortcodeExists(shortcode: String): Future[Boolean] =
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

  /**
   * Tries to retrieve a [[ProjectADM]] from the cache.
   */
  private def getProjectFromCache(identifier: ProjectIdentifierADM): Future[Option[ProjectADM]] = {
    val result = appActor.ask(CacheServiceGetProjectADM(identifier)).mapTo[Option[ProjectADM]]
    result.map {
      case Some(project) =>
        log.debug("getProjectFromCache - cache hit for: {}", getId(identifier))
        Some(project.unescape)
      case None =>
        log.debug("getUserProjectCache - no cache hit for: {}", getId(identifier))
        None
    }
  }

  /**
   * Writes the project to cache.
   *
   * @param project a [[ProjectADM]].
   * @return true if writing was successful.
   */
  private def writeProjectADMToCache(project: ProjectADM): Future[Unit] = {
    val result = appActor.ask(CacheServicePutProjectADM(project)).mapTo[Unit]
    result.map { res =>
      log.debug("writeProjectADMToCache - result: {}", result)
      res
    }
  }
}
