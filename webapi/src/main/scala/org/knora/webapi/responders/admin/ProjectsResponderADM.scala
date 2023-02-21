/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.admin
import com.typesafe.scalalogging.LazyLogging
import dsp.errors._
import dsp.valueobjects.Iri
import dsp.valueobjects.V2
import org.knora.webapi._
import org.knora.webapi.config.AppConfig
import org.knora.webapi.core.MessageHandler
import org.knora.webapi.core.MessageRelay
import org.knora.webapi.instrumentation.InstrumentationSupport
import org.knora.webapi.messages.IriConversions._
import org.knora.webapi.messages.OntologyConstants.KnoraAdmin._
import org.knora.webapi.messages._
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
import org.knora.webapi.responders.IriLocker
import org.knora.webapi.responders.IriService
import org.knora.webapi.responders.Responder
import org.knora.webapi.store.cache.settings.CacheServiceSettings
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.util.ZioHelper
import zio._

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import scala.util.Failure
import scala.util.Success
import scala.util.Try

/**
 * Returns information about projects.
 */
trait ProjectsResponderADM {

  /**
   * Gets all the projects and returns them as a [[ProjectADM]].
   *
   * @return all the projects as a [[ProjectADM]].
   *
   *         NotFoundException if no projects are found.
   */
  def projectsGetRequestADM(): Task[ProjectsGetResponseADM]

  /**
   * Gets the project with the given project IRI, shortname, shortcode or UUID and returns the information as a [[ProjectADM]].
   *
   * @param id the IRI, shortname, shortcode or UUID of the project.
   * @param skipCache  if `true`, doesn't check the cache and tries to retrieve the project directly from the triplestore
   * @return information about the project as an optional [[ProjectADM]].
   */
  def getSingleProjectADM(id: ProjectIdentifierADM, skipCache: Boolean = false): Task[Option[ProjectADM]]

  /**
   * Gets the project with the given project IRI, shortname, shortcode or UUID and returns the information
   * as a [[ProjectGetResponseADM]].
   *
   * @param id the IRI, shortname, shortcode or UUID of the project.
   * @return Information about the project as a [[ProjectGetResponseADM]].
   *
   *         [[NotFoundException]] When no project for the given IRI can be found.
   */
  def getSingleProjectADMRequest(id: ProjectIdentifierADM): Task[ProjectGetResponseADM]

  /**
   * Gets the members of a project with the given IRI, shortname, shortcode or UUID. Returns an empty list
   * if none are found.
   *
   * @param id     the IRI, shortname, shortcode or UUID of the project.
   * @param user the user making the request.
   * @return the members of a project as a [[ProjectMembersGetResponseADM]]
   */
  def projectMembersGetRequestADM(id: ProjectIdentifierADM, user: UserADM): Task[ProjectMembersGetResponseADM]

  /**
   * Gets the admin members of a project with the given IRI, shortname, shortcode or UUIDe. Returns an empty list
   * if none are found
   *
   * @param id     the IRI, shortname, shortcode or UUID of the project.
   * @param user the user making the request.
   * @return the members of a project as a [[ProjectMembersGetResponseADM]]
   */
  def projectAdminMembersGetRequestADM(id: ProjectIdentifierADM, user: UserADM): Task[ProjectAdminMembersGetResponseADM]

  /**
   * Gets all unique keywords for all projects and returns them. Returns an empty list if none are found.
   *
   * @return all keywords for all projects as [[ProjectsKeywordsGetResponseADM]]
   */
  def projectsKeywordsGetRequestADM(): Task[ProjectsKeywordsGetResponseADM]

  /**
   * Gets all keywords for a single project and returns them. Returns an empty list if none are found.
   *
   * @param projectIri the IRI of the project.
   * @return keywords for a projects as [[ProjectKeywordsGetResponseADM]]
   */
  def projectKeywordsGetRequestADM(projectIri: Iri.ProjectIri): Task[ProjectKeywordsGetResponseADM]

  /**
   * Get project's restricted view settings.
   *
   * @param id the project's identifier (IRI / shortcode / shortname / UUID)
   * @return [[ProjectRestrictedViewSettingsADM]]
   */
  def projectRestrictedViewSettingsGetADM(id: ProjectIdentifierADM): Task[Option[ProjectRestrictedViewSettingsADM]]

  /**
   * Get project's restricted view settings.
   *
   * @param id the project's identifier (IRI / shortcode / shortname / UUID)
   * @return [[ProjectRestrictedViewSettingsGetResponseADM]]
   */
  def projectRestrictedViewSettingsGetRequestADM(
    id: ProjectIdentifierADM
  ): Task[ProjectRestrictedViewSettingsGetResponseADM]

  /**
   * Creates a project.
   *
   * @param createPayload the new project's information.
   * @param requestingUser       the user that is making the request.
   * @param apiRequestID         the unique api request ID.
   * @return A [[ProjectOperationResponseADM]].
   *
   *         [[ForbiddenException]]      In the case that the user is not allowed to perform the operation.
   *
   *         [[DuplicateValueException]] In the case when either the shortname or shortcode are not unique.
   *
   *         [[BadRequestException]]     In the case when the shortcode is invalid.
   */
  def projectCreateRequestADM(
    createPayload: ProjectCreatePayloadADM,
    requestingUser: UserADM,
    apiRequestID: UUID
  ): Task[ProjectOperationResponseADM]

  /**
   * Update project's basic information.
   *
   * @param projectIri           the IRI of the project.
   * @param updatePayload the update payload.
   * @param user       the user making the request.
   * @param apiRequestID         the unique api request ID.
   * @return A [[ProjectOperationResponseADM]].
   *
   *         [[ForbiddenException]] In the case that the user is not allowed to perform the operation.
   */
  def changeBasicInformationRequestADM(
    projectIri: Iri.ProjectIri,
    updatePayload: ProjectUpdatePayloadADM,
    user: UserADM,
    apiRequestID: UUID
  ): Task[ProjectOperationResponseADM]

  def projectDataGetRequestADM(id: ProjectIdentifierADM, user: UserADM): Task[ProjectDataGetResponseADM]
}

final case class ProjectsResponderADMLive(
  triplestoreService: TriplestoreService,
  messageRelay: MessageRelay,
  iriService: IriService,
  cacheServiceSettings: CacheServiceSettings,
  implicit val stringFormatter: StringFormatter
) extends ProjectsResponderADM
    with MessageHandler
    with LazyLogging
    with InstrumentationSupport {

  // Global lock IRI used for project creation and update
  private val PROJECTS_GLOBAL_LOCK_IRI = "http://rdfh.ch/projects"

  private val ADMIN_DATA_GRAPH       = "http://www.knora.org/data/admin"
  private val PERMISSIONS_DATA_GRAPH = "http://www.knora.org/data/permissions"

  override def isResponsibleFor(message: ResponderRequest): Boolean = message.isInstanceOf[ProjectsResponderRequestADM]

  /**
   * Receives a message extending [[ProjectsResponderRequestADM]], and returns an appropriate response message.
   */
  override def handle(msg: ResponderRequest): Task[Any] = msg match {
    case ProjectsGetRequestADM()          => projectsGetRequestADM()
    case ProjectGetADM(identifier)        => getSingleProjectADM(identifier)
    case ProjectGetRequestADM(identifier) => getSingleProjectADMRequest(identifier)
    case ProjectMembersGetRequestADM(identifier, requestingUser) =>
      projectMembersGetRequestADM(identifier, requestingUser)
    case ProjectAdminMembersGetRequestADM(identifier, requestingUser) =>
      projectAdminMembersGetRequestADM(identifier, requestingUser)
    case ProjectsKeywordsGetRequestADM() => projectsKeywordsGetRequestADM()
    case ProjectKeywordsGetRequestADM(projectIri) =>
      projectKeywordsGetRequestADM(projectIri)
    case ProjectRestrictedViewSettingsGetADM(identifier) =>
      projectRestrictedViewSettingsGetADM(identifier)
    case ProjectRestrictedViewSettingsGetRequestADM(identifier) =>
      projectRestrictedViewSettingsGetRequestADM(identifier)
    case ProjectCreateRequestADM(createRequest, requestingUser, apiRequestID) =>
      projectCreateRequestADM(createRequest, requestingUser, apiRequestID)
    case ProjectChangeRequestADM(
          projectIri,
          projectUpdatePayload,
          requestingUser,
          apiRequestID
        ) =>
      changeBasicInformationRequestADM(
        projectIri,
        projectUpdatePayload,
        requestingUser,
        apiRequestID
      )
    case ProjectDataGetRequestADM(projectIdentifier, requestingUser) =>
      projectDataGetRequestADM(projectIdentifier, requestingUser)
    case other => Responder.handleUnexpectedMessage(other, this.getClass.getName)
  }

  /**
   * Gets all the projects and returns them as a sequence containing [[ProjectADM]].
   *
   * @return all the projects as a sequence containing [[ProjectADM]].
   */
  private def projectsGetADM(): Task[Seq[ProjectADM]] = {
    val query = twirl.queries.sparql.admin.txt
      .getProjects(
        maybeIri = None,
        maybeShortname = None,
        maybeShortcode = None
      )
    for {
      projectsResponse      <- triplestoreService.sparqlHttpExtendedConstruct(query.toString())
      projectIris            = projectsResponse.statements.keySet.map(_.toString)
      ontologiesForProjects <- getOntologiesForProjects(projectIris)
      projects =
        projectsResponse.statements.toList.map {
          case (projectIriSubject: SubjectV2, propsMap: Map[SmartIri, Seq[LiteralV2]]) =>
            val projectOntologies =
              ontologiesForProjects.getOrElse(projectIriSubject.toString, Seq.empty[IRI])
            convertStatementsToProjectADM(
              statements = (projectIriSubject, propsMap),
              ontologies = projectOntologies
            )
        }

    } yield projects.sorted
  }

  /**
   * Given a set of project IRIs, gets the ontologies that belong to each project.
   *
   * @param projectIris    a set of project IRIs. If empty, returns the ontologies for all projects.
   * @return a map of project IRIs to sequences of ontology IRIs.
   */
  private def getOntologiesForProjects(projectIris: Set[IRI]): Task[Map[IRI, Seq[IRI]]] = {
    def getIriPair(ontology: OntologyMetadataV2) =
      ontology.projectIri.fold(
        throw InconsistentRepositoryDataException(s"Ontology ${ontology.ontologyIri} has no project")
      )(project => (project.toString, ontology.ontologyIri.toString))

    val request = OntologyMetadataGetByProjectRequestV2(
      projectIris = projectIris.map(_.toSmartIri),
      requestingUser = KnoraSystemInstances.Users.SystemUser
    )

    for {
      ontologyMetadataResponse <- messageRelay.ask[ReadOntologyMetadataV2](request)
      ontologies                = ontologyMetadataResponse.ontologies.toList
      iriPairs                  = ontologies.map(getIriPair)
      projectToOntologyMap      = iriPairs.groupMap { case (project, _) => project } { case (_, onto) => onto }
    } yield projectToOntologyMap
  }

  /**
   * Gets all the projects and returns them as a [[ProjectADM]].
   *
   * @return all the projects as a [[ProjectADM]].
   *
   *         NotFoundException if no projects are found.
   */
  override def projectsGetRequestADM(): Task[ProjectsGetResponseADM] =
    for {
      projects <- projectsGetADM()
      result <- if (projects.nonEmpty) { ZIO.succeed(ProjectsGetResponseADM(projects)) }
                else { ZIO.fail(NotFoundException(s"No projects found")) }
    } yield result

  /**
   * Gets the project with the given project IRI, shortname, shortcode or UUID and returns the information as a [[ProjectADM]].
   *
   * @param id           the IRI, shortname, shortcode or UUID of the project.
   * @param skipCache            if `true`, doesn't check the cache and tries to retrieve the project directly from the triplestore
   * @return information about the project as an optional [[ProjectADM]].
   */
  override def getSingleProjectADM(
    id: ProjectIdentifierADM,
    skipCache: Boolean = false
  ): Task[Option[ProjectADM]] = {

    logger.debug(
      s"getSingleProjectADM - id: {}, skipCache: {}",
      getId(id),
      skipCache
    )

    for {
      maybeProjectADM <-
        if (skipCache) {
          getProjectFromTriplestore(identifier = id)
        } else {
          // getting from cache or triplestore
          getProjectFromCacheOrTriplestore(identifier = id)
        }
    } yield maybeProjectADM
  }

  /**
   * Gets the project with the given project IRI, shortname, shortcode or UUID and returns the information
   * as a [[ProjectGetResponseADM]].
   *
   * @param id           the IRI, shortname, shortcode or UUID of the project.
   * @return Information about the project as a [[ProjectGetResponseADM]].
   *
   *         [[NotFoundException]] When no project for the given IRI can be found.
   */
  override def getSingleProjectADMRequest(id: ProjectIdentifierADM): Task[ProjectGetResponseADM] =
    getSingleProjectADM(id)
      .flatMap(ZIO.fromOption(_))
      .mapBoth(_ => NotFoundException(s"Project '${getId(id)}' not found"), ProjectGetResponseADM)

  /**
   * Gets the members of a project with the given IRI, shortname, shortcode or UUID. Returns an empty list
   * if none are found.
   *
   * @param id           the IRI, shortname, shortcode or UUID of the project.
   * @param user       the user making the request.
   * @return the members of a project as a [[ProjectMembersGetResponseADM]]
   */
  override def projectMembersGetRequestADM(
    id: ProjectIdentifierADM,
    user: UserADM
  ): Task[ProjectMembersGetResponseADM] =
    for {
      /* Get project and verify permissions. */
      project <- getSingleProjectADM(id)
                   .flatMap(ZIO.fromOption(_))
                   .orElseFail(NotFoundException(s"Project '${getId(id)}' not found."))
      _ <- ZIO
             .fail(ForbiddenException("SystemAdmin or ProjectAdmin permissions are required."))
             .when {
               val userPermissions = user.permissions
               !userPermissions.isSystemAdmin &&
               !userPermissions.isProjectAdmin(project.id) &&
               !user.isSystemUser
             }

      query = twirl.queries.sparql.admin.txt
                .getProjectMembers(
                  maybeIri = id.asIriIdentifierOption,
                  maybeShortname = id.asShortnameIdentifierOption,
                  maybeShortcode = id.asShortcodeIdentifierOption
                )

      projectMembersResponse <- triplestoreService.sparqlHttpExtendedConstruct(query.toString())
      statements              = projectMembersResponse.statements.toList

      // get project member IRI from results rows
      userIris: Seq[IRI] =
        if (statements.nonEmpty) {
          statements.map(_._1.toString)
        } else {
          Seq.empty[IRI]
        }

      maybeUserFutures: Seq[Task[Option[UserADM]]] =
        userIris.map { userIri =>
          messageRelay
            .ask[Option[UserADM]](
              UserGetADM(
                identifier = UserIdentifierADM(maybeIri = Some(userIri)),
                userInformationTypeADM = UserInformationTypeADM.Restricted,
                requestingUser = KnoraSystemInstances.Users.SystemUser
              )
            )
        }
      maybeUsers         <- ZioHelper.sequence(maybeUserFutures)
      users: Seq[UserADM] = maybeUsers.flatten

    } yield ProjectMembersGetResponseADM(members = users)

  /**
   * Gets the admin members of a project with the given IRI, shortname, shortcode or UUIDe. Returns an empty list
   * if none are found
   *
   * @param id           the IRI, shortname, shortcode or UUID of the project.
   * @param user       the user making the request.
   * @return the members of a project as a [[ProjectMembersGetResponseADM]]
   */
  override def projectAdminMembersGetRequestADM(
    id: ProjectIdentifierADM,
    user: UserADM
  ): Task[ProjectAdminMembersGetResponseADM] =
    for {
      /* Get project and verify permissions. */
      project <- getSingleProjectADM(id)
                   .flatMap(ZIO.fromOption(_))
                   .orElseFail(NotFoundException(s"Project '${getId(id)}' not found."))
      _ <- ZIO
             .fail(ForbiddenException("SystemAdmin or ProjectAdmin permissions are required."))
             .when {
               !user.permissions.isSystemAdmin &&
               !user.permissions.isProjectAdmin(project.id)
             }

      query = twirl.queries.sparql.admin.txt
                .getProjectAdminMembers(
                  maybeIri = id.asIriIdentifierOption,
                  maybeShortname = id.asShortnameIdentifierOption,
                  maybeShortcode = id.asShortcodeIdentifierOption
                )

      projectAdminMembersResponse <- triplestoreService.sparqlHttpExtendedConstruct(query.toString())

      statements = projectAdminMembersResponse.statements.toList

      // get project member IRI from results rows
      userIris: Seq[IRI] =
        if (statements.nonEmpty) {
          statements.map(_._1.toString)
        } else {
          Seq.empty[IRI]
        }

      maybeUserTasks: Seq[Task[Option[UserADM]]] = userIris.map { userIri =>
                                                     messageRelay
                                                       .ask[Option[UserADM]](
                                                         UserGetADM(
                                                           identifier = UserIdentifierADM(maybeIri = Some(userIri)),
                                                           userInformationTypeADM = UserInformationTypeADM.Restricted,
                                                           requestingUser = KnoraSystemInstances.Users.SystemUser
                                                         )
                                                       )
                                                   }
      maybeUsers         <- ZioHelper.sequence(maybeUserTasks)
      users: Seq[UserADM] = maybeUsers.flatten

    } yield ProjectAdminMembersGetResponseADM(members = users)

  /**
   * Gets all unique keywords for all projects and returns them. Returns an empty list if none are found.
   *
   * @return all keywords for all projects as [[ProjectsKeywordsGetResponseADM]]
   */
  override def projectsKeywordsGetRequestADM(): Task[ProjectsKeywordsGetResponseADM] =
    for {
      projects <- projectsGetADM()

      keywords: Seq[String] = projects.flatMap(_.keywords).distinct.sorted

    } yield ProjectsKeywordsGetResponseADM(keywords = keywords)

  /**
   * Gets all keywords for a single project and returns them. Returns an empty list if none are found.
   *
   * @param projectIri           the IRI of the project.
   * @return keywords for a projects as [[ProjectKeywordsGetResponseADM]]
   */
  override def projectKeywordsGetRequestADM(projectIri: Iri.ProjectIri): Task[ProjectKeywordsGetResponseADM] =
    for {
      id <- IriIdentifier
              .fromString(projectIri.value)
              .toZIO
              .mapError(e => BadRequestException(e.getMessage))
      keywords <- getSingleProjectADM(id)
                    .flatMap(ZIO.fromOption(_))
                    .mapBoth(_ => NotFoundException(s"Project '${projectIri.value}' not found."), _.keywords)
    } yield ProjectKeywordsGetResponseADM(keywords)

  override def projectDataGetRequestADM(
    id: ProjectIdentifierADM,
    user: UserADM
  ): Task[ProjectDataGetResponseADM] = {

    /**
     * Represents a named graph to be saved to a TriG file.
     *
     * @param graphIri the IRI of the named graph.
     * @param tempDir  the directory in which the file is to be saved.
     */
    case class NamedGraphTrigFile(graphIri: IRI, tempDir: Path) {
      lazy val dataFile: Path = {
        val filename = graphIri.replaceAll("[.:/]", "_") + ".trig"
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
      project <- getSingleProjectADM(id)
                   .flatMap(ZIO.fromOption(_))
                   .orElseFail(NotFoundException(s"Project '${getId(id)}' not found."))

      // Check that the user has permission to download the data.
      _ <-
        ZIO
          .fail(
            ForbiddenException(
              s"You are logged in as ${user.username}, but only a system administrator or project administrator can request a project's data."
            )
          )
          .when(!(user.permissions.isSystemAdmin || user.permissions.isProjectAdmin(project.id)))

      // Make a temporary directory for the downloaded data.
      tempDir = Files.createTempDirectory(project.shortname)
      _      <- ZIO.logInfo("Downloading project data to temporary directory " + tempDir.toAbsolutePath)

      // Download the project's named graphs.

      projectDataNamedGraph: IRI = stringFormatter.projectDataNamedGraphV2(project)
      graphsToDownload: Seq[IRI] = project.ontologies :+ projectDataNamedGraph
      projectSpecificNamedGraphTrigFiles: Seq[NamedGraphTrigFile] =
        graphsToDownload.map(graphIri => NamedGraphTrigFile(graphIri = graphIri, tempDir = tempDir))

      projectSpecificNamedGraphTrigFileWriteFutures: Seq[Task[FileWrittenResponse]] =
        projectSpecificNamedGraphTrigFiles.map { trigFile =>
          for {
            fileWrittenResponse <- messageRelay.ask[FileWrittenResponse](
                                     NamedGraphFileRequest(
                                       graphIri = trigFile.graphIri,
                                       outputFile = trigFile.dataFile,
                                       outputFormat = TriG
                                     )
                                   )
          } yield fileWrittenResponse
        }

      _ <- ZioHelper.sequence(projectSpecificNamedGraphTrigFileWriteFutures)

      // Download the project's admin data.

      adminDataNamedGraphTrigFile = NamedGraphTrigFile(graphIri = ADMIN_DATA_GRAPH, tempDir = tempDir)

      adminDataSparql = twirl.queries.sparql.admin.txt.getProjectAdminData(project.id)
      _ <- triplestoreService.sparqlHttpConstructFile(
             sparql = adminDataSparql.toString(),
             graphIri = adminDataNamedGraphTrigFile.graphIri,
             outputFile = adminDataNamedGraphTrigFile.dataFile,
             outputFormat = TriG
           )

      // Download the project's permission data.

      permissionDataNamedGraphTrigFile = NamedGraphTrigFile(graphIri = PERMISSIONS_DATA_GRAPH, tempDir = tempDir)

      permissionDataSparql = twirl.queries.sparql.admin.txt.getProjectPermissions(project.id)
      _ <- triplestoreService.sparqlHttpConstructFile(
             sparql = permissionDataSparql.toString(),
             graphIri = permissionDataNamedGraphTrigFile.graphIri,
             outputFile = permissionDataNamedGraphTrigFile.dataFile,
             outputFormat = TriG
           )

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
   * @param id  the project's identifier (IRI / shortcode / shortname / UUID)
   *
   * @return [[ProjectRestrictedViewSettingsADM]]
   */
  override def projectRestrictedViewSettingsGetADM(
    id: ProjectIdentifierADM
  ): Task[Option[ProjectRestrictedViewSettingsADM]] = {
    val query = twirl.queries.sparql.admin.txt
      .getProjects(
        maybeIri = id.asIriIdentifierOption,
        maybeShortname = id.asShortnameIdentifierOption,
        maybeShortcode = id.asShortcodeIdentifierOption
      )
    for {
      projectResponse <- triplestoreService.sparqlHttpExtendedConstruct(query.toString)
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
  }

  /**
   * Get project's restricted view settings.
   *
   * @param id  the project's identifier (IRI / shortcode / shortname / UUID)
   *
   * @return [[ProjectRestrictedViewSettingsGetResponseADM]]
   */
  override def projectRestrictedViewSettingsGetRequestADM(
    id: ProjectIdentifierADM
  ): Task[ProjectRestrictedViewSettingsGetResponseADM] =
    projectRestrictedViewSettingsGetADM(id)
      .flatMap(ZIO.fromOption(_))
      .mapBoth(
        _ => NotFoundException(s"Project '${getId(id)}' not found."),
        ProjectRestrictedViewSettingsGetResponseADM
      )

  /**
   * Update project's basic information.
   *
   * @param projectIri           the IRI of the project.
   * @param updatePayload the update payload.
   * @param user       the user making the request.
   * @param apiRequestID         the unique api request ID.
   * @return A [[ProjectOperationResponseADM]].
   *
   *         [[ForbiddenException]] In the case that the user is not allowed to perform the operation.
   */
  override def changeBasicInformationRequestADM(
    projectIri: Iri.ProjectIri,
    updatePayload: ProjectUpdatePayloadADM,
    user: UserADM,
    apiRequestID: UUID
  ): Task[ProjectOperationResponseADM] = {

    /**
     * The actual change project task run with an IRI lock.
     */
    def changeProjectTask(
      projectIri: Iri.ProjectIri,
      projectUpdatePayload: ProjectUpdatePayloadADM,
      requestingUser: UserADM
    ): Task[ProjectOperationResponseADM] =
      // check if the requesting user is allowed to perform updates
      if (!requestingUser.permissions.isProjectAdmin(projectIri.value) && !requestingUser.permissions.isSystemAdmin) {
        ZIO.fail(ForbiddenException("Project's information can only be changed by a project or system admin."))
      } else {
        for {
          result <- updateProjectADM(
                      projectIri = projectIri,
                      projectUpdatePayload = projectUpdatePayload,
                      requestingUser = KnoraSystemInstances.Users.SystemUser
                    )

        } yield result
      }

    val task = changeProjectTask(projectIri, updatePayload, user)
    IriLocker.runWithIriLock(apiRequestID, projectIri.value, task)
  }

  /**
   * Main project update method.
   *
   * @param projectIri           the IRI of the project.
   * @param projectUpdatePayload the data to be updated. Update means exchanging what is in the triplestore with
   *                             this data. If only some parts of the data need to be changed, then this needs to
   *                             be prepared in the step before this one.
   *
   * @return A [[ProjectOperationResponseADM]].
   *
   *         [[NotFoundException]] In the case that the project's IRI is not found.
   */
  private def updateProjectADM(
    projectIri: Iri.ProjectIri,
    projectUpdatePayload: ProjectUpdatePayloadADM,
    requestingUser: UserADM
  ): Task[ProjectOperationResponseADM] = {

    val areAllParamsNone: Boolean = projectUpdatePayload.productIterator.forall {
      case param: Option[Any] => param.isEmpty
      case _                  => false
    }

    if (areAllParamsNone) { ZIO.fail(BadRequestException("No data would be changed. Aborting update request.")) }
    else {
      val projectId = IriIdentifier(projectIri)
      for {
        maybeCurrentProject <- getSingleProjectADM(projectId, skipCache = true)
        _ <- ZIO
               .fromOption(maybeCurrentProject)
               .orElseFail(NotFoundException(s"Project '${projectIri.value}' not found. Aborting update request."))

        // we are changing the project, so lets get rid of the cached copy
        _ <- messageRelay.ask[Any](CacheServiceFlushDB(KnoraSystemInstances.Users.SystemUser))

        /* Update project */
        updateQuery = twirl.queries.sparql.admin.txt
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

        _ <- triplestoreService.sparqlHttpUpdate(updateQuery.toString)

        /* Verify that the project was updated. */
        maybeUpdatedProject <- getSingleProjectADM(projectId, skipCache = true)

        updatedProject <-
          ZIO
            .fromOption(maybeUpdatedProject)
            .orElseFail(UpdateNotPerformedException("Project was not updated. Please report this as a possible bug."))

        _ <- ZIO.logDebug(
               s"updateProjectADM - projectUpdatePayload: $projectUpdatePayload /  updatedProject: $updatedProject"
             )

        _ = checkProjectUpdate(updatedProject, projectUpdatePayload)

      } yield ProjectOperationResponseADM(project = updatedProject)
    }
  }

  /**
   * Checks if all fields of a projectUpdatePayload are represented in the updated [[ProjectADM]]. If so, the
   * update is considered successful.
   *
   * @param updatedProject       The updated project against which the projectUpdatePayload is compared.
   * @param projectUpdatePayload The payload which defines what should have been updated.
   *
   *         [[UpdateNotPerformedException]] If one of the fields was not updated.
   */
  private def checkProjectUpdate(
    updatedProject: ProjectADM,
    projectUpdatePayload: ProjectUpdatePayloadADM
  ): Unit = {
    if (projectUpdatePayload.shortname.nonEmpty) {
      projectUpdatePayload.shortname
        .map(_.value)
        .map(stringFormatter.fromSparqlEncodedString)
        .filter(_ == updatedProject.shortname)
        .getOrElse(
          throw UpdateNotPerformedException(
            "Project's 'shortname' was not updated. Please report this as a possible bug."
          )
        )
    }

    if (projectUpdatePayload.shortname.nonEmpty) {
      projectUpdatePayload.longname
        .map(_.value)
        .map(stringFormatter.fromSparqlEncodedString)
        .filter(updatedProject.longname.contains(_))
        .getOrElse(
          throw UpdateNotPerformedException(
            "Project's 'longname' was not updated. Please report this as a possible bug."
          )
        )
    }

    if (projectUpdatePayload.description.nonEmpty) {
      projectUpdatePayload.description
        .map(_.value)
        .map(_.map(d => V2.StringLiteralV2(stringFormatter.fromSparqlEncodedString(d.value), d.language)))
        .filter(updatedProject.description.diff(_).isEmpty)
        .getOrElse(
          throw UpdateNotPerformedException(
            "Project's 'description' was not updated. Please report this as a possible bug."
          )
        )
    }

    if (projectUpdatePayload.keywords.nonEmpty) {
      projectUpdatePayload.keywords
        .map(_.value)
        .map(_.map(key => stringFormatter.fromSparqlEncodedString(key)))
        .filter(_.sorted == updatedProject.keywords.sorted)
        .getOrElse(
          throw UpdateNotPerformedException(
            "Project's 'keywords' was not updated. Please report this as a possible bug."
          )
        )
    }

    if (projectUpdatePayload.logo.nonEmpty) {
      projectUpdatePayload.logo
        .map(_.value)
        .map(stringFormatter.fromSparqlEncodedString)
        .filter(updatedProject.logo.contains(_))
        .getOrElse(
          throw UpdateNotPerformedException(
            "Project's 'logo' was not updated. Please report this as a possible bug."
          )
        )
    }

    if (projectUpdatePayload.status.nonEmpty) {
      projectUpdatePayload.status
        .map(_.value)
        .filter(_ == updatedProject.status)
        .getOrElse(
          throw UpdateNotPerformedException(
            "Project's 'status' was not updated. Please report this as a possible bug."
          )
        )
    }

    if (projectUpdatePayload.selfjoin.nonEmpty) {
      projectUpdatePayload.selfjoin
        .map(_.value)
        .filter(_ == updatedProject.selfjoin)
        .getOrElse(
          throw UpdateNotPerformedException(
            "Project's 'selfjoin' was not updated. Please report this as a possible bug."
          )
        )
    }

  }

  /**
   * Creates a project.
   *
   * @param createPayload the new project's information.
   *
   * @param requestingUser       the user that is making the request.
   * @param apiRequestID         the unique api request ID.
   * @return A [[ProjectOperationResponseADM]].
   *
   *         [[ForbiddenException]]      In the case that the user is not allowed to perform the operation.
   *
   *         [[DuplicateValueException]] In the case when either the shortname or shortcode are not unique.
   *
   *         [[BadRequestException]]     In the case when the shortcode is invalid.
   */
  override def projectCreateRequestADM(
    createPayload: ProjectCreatePayloadADM,
    requestingUser: UserADM,
    apiRequestID: UUID
  ): Task[ProjectOperationResponseADM] = {

    /**
     * Creates following permissions for the new project
     * 1. Permissions for project admins to do all operations on project level and to create, modify, delete, change rights,
     * view, and restricted view of all new resources and values that belong to this project.
     * 2. Permissions for project members to create, modify, view and restricted view of all new resources and values that belong to this project.
     *
     * @param projectIri The IRI of the new project.
     *
     *         [[BadRequestException]] If a permission is not created.
     */
    def createPermissionsForAdminsAndMembersOfNewProject(projectIri: IRI): Task[Unit] =
      for {
        // Give the admins of the new project rights for any operation in project level, and rights to create resources.
        _ <- messageRelay
               .ask[AdministrativePermissionCreateResponseADM](
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

        // Give the members of the new project rights to create resources.
        _ <- messageRelay
               .ask[AdministrativePermissionCreateResponseADM](
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

        // Give the admins of the new project rights to change rights, modify, delete, view,
        // and restricted view of all resources and values that belong to the project.
        _ <- messageRelay
               .ask[DefaultObjectAccessPermissionCreateResponseADM](
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

        // Give the members of the new project rights to modify, view, and restricted view of all resources and values
        // that belong to the project.
        _ <- messageRelay
               .ask[DefaultObjectAccessPermissionCreateResponseADM](
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
      } yield ()

    def projectCreateTask(
      createProjectRequest: ProjectCreatePayloadADM,
      requestingUser: UserADM
    ): Task[ProjectOperationResponseADM] =
      for {
        // check if the supplied shortname is unique
        _ <- ZIO
               .fail(
                 DuplicateValueException(
                   s"Project with the shortname: '${createProjectRequest.shortname.value}' already exists"
                 )
               )
               .whenZIO(projectByShortnameExists(createProjectRequest.shortname.value))

        // check if the optionally supplied shortcode is valid and unique
        _ <- ZIO
               .fail(
                 DuplicateValueException(
                   s"Project with the shortcode: '${createProjectRequest.shortcode.value}' already exists"
                 )
               )
               .whenZIO(projectByShortcodeExists(createProjectRequest.shortcode.value))

        // check if the requesting user is allowed to create project
        _ <- ZIO
               .fail(ForbiddenException("A new project can only be created by a system admin."))
               .when(!requestingUser.permissions.isSystemAdmin)

        // check the custom IRI; if not given, create an unused IRI
        customProjectIri: Option[SmartIri] = createProjectRequest.id.map(_.value).map(_.toSmartIri)
        newProjectIRI <- iriService.checkOrCreateEntityIriTask(
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

        createNewProjectSparqlString = twirl.queries.sparql.admin.txt
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

        _ <- triplestoreService.sparqlHttpUpdate(createNewProjectSparqlString)

        // try to retrieve newly created project (will also add to cache)
        id <- IriIdentifier.fromString(newProjectIRI).toZIO.mapError(e => BadRequestException(e.getMessage))
        // check to see if we could retrieve the new project
        newProjectADM <- getSingleProjectADM(id, skipCache = true)
                           .flatMap(ZIO.fromOption(_))
                           .orElseFail(
                             UpdateNotPerformedException(
                               s"Project $newProjectIRI was not created. Please report this as a possible bug."
                             )
                           )
        // create permissions for admins and members of the new group
        _ <- createPermissionsForAdminsAndMembersOfNewProject(newProjectIRI)

      } yield ProjectOperationResponseADM(project = newProjectADM.unescape)

    val task = projectCreateTask(createPayload, requestingUser)
    IriLocker.runWithIriLock(apiRequestID, PROJECTS_GLOBAL_LOCK_IRI, task)
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
  ): Task[Option[ProjectADM]] =
    if (cacheServiceSettings.cacheServiceEnabled) {
      // caching enabled
      getProjectFromCache(identifier).flatMap {
        case None =>
          // none found in cache. getting from triplestore.
          getProjectFromTriplestore(identifier = identifier).flatMap {
            case None =>
              // also none found in triplestore. finally returning none.
              logger.debug("getProjectFromCacheOrTriplestore - not found in cache and in triplestore")
              ZIO.succeed(None)
            case Some(project) =>
              // found a project in the triplestore. need to write to cache.
              logger.debug(
                "getProjectFromCacheOrTriplestore - not found in cache but found in triplestore. need to write to cache."
              )
              // writing project to cache and afterwards returning the project found in the triplestore
              messageRelay
                .ask[Unit](CacheServicePutProjectADM(project))
                .as(Some(project))
          }
        case Some(project) =>
          logger.debug("getProjectFromCacheOrTriplestore - found in cache. returning project.")
          ZIO.succeed(Some(project))
      }
    } else {
      // caching disabled
      logger.debug("getProjectFromCacheOrTriplestore - caching disabled. getting from triplestore.")
      getProjectFromTriplestore(identifier = identifier)
    }

  /**
   * Tries to retrieve a [[ProjectADM]] from the triplestore.
   */
  private def getProjectFromTriplestore(
    identifier: ProjectIdentifierADM
  ): Task[Option[ProjectADM]] = {
    val query = twirl.queries.sparql.admin.txt
      .getProjects(
        maybeIri = identifier.asIriIdentifierOption,
        maybeShortname = identifier.asShortnameIdentifierOption,
        maybeShortcode = identifier.asShortcodeIdentifierOption
      )
    for {
      projectResponse <- triplestoreService.sparqlHttpExtendedConstruct(query.toString())

      projectIris = projectResponse.statements.keySet.map(_.toString)
      ontologies <- if (projectResponse.statements.nonEmpty) {
                      getOntologiesForProjects(projectIris)
                    } else {
                      ZIO.succeed(Map.empty[IRI, Seq[IRI]])
                    }

      maybeProjectADM: Option[ProjectADM] =
        if (projectResponse.statements.nonEmpty) {
          logger.debug("getProjectFromTriplestore - triplestore hit for: {}", getId(identifier))
          val projectOntologies = ontologies.getOrElse(projectIris.head, Seq.empty[IRI])
          Some(convertStatementsToProjectADM(projectResponse.statements.head, projectOntologies))
        } else {
          logger.debug("getProjectFromTriplestore - no triplestore hit for: {}", getId(identifier))
          None
        }
    } yield maybeProjectADM
  }

  /**
   * Helper method that turns SPARQL result rows into a [[ProjectADM]].
   *
   * @param statements results from the SPARQL query representing information about the project.
   * @param ontologies the ontologies in the project.
   * @return a [[ProjectADM]] representing information about project.
   */
  @throws[InconsistentRepositoryDataException]("If the statements do not contain expected keys.")
  private def convertStatementsToProjectADM(
    statements: (SubjectV2, Map[SmartIri, Seq[LiteralV2]]),
    ontologies: Seq[IRI]
  ): ProjectADM = {
    val projectIri: IRI                              = statements._1.toString
    val propertiesMap: Map[SmartIri, Seq[LiteralV2]] = statements._2

    def getOption[A <: LiteralV2](key: IRI): Option[Seq[A]] =
      propertiesMap.get(key.toSmartIri).map(_.map(_.asInstanceOf[A]))

    def getOrThrow[A <: LiteralV2](
      key: IRI
    ): Seq[A] =
      getOption[A](key).getOrElse(
        throw InconsistentRepositoryDataException(s"Project: $projectIri has no $key defined.")
      )

    val shortname = getOrThrow[StringLiteralV2](ProjectShortname).head.value
    val shortcode = getOrThrow[StringLiteralV2](ProjectShortcode).head.value
    val longname  = getOption[StringLiteralV2](ProjectLongname).map(_.head.value)
    val description = getOrThrow[StringLiteralV2](ProjectDescription)
      .map(desc => V2.StringLiteralV2(desc.value, desc.language))
    val keywords = getOption[StringLiteralV2](ProjectKeyword).getOrElse(Seq.empty).map(_.value).sorted
    val logo     = getOption[StringLiteralV2](ProjectLogo).map(_.head.value)
    val status   = getOrThrow[BooleanLiteralV2](Status).head.value
    val selfjoin = getOrThrow[BooleanLiteralV2](HasSelfJoinEnabled).head.value

    val project =
      ProjectADM(projectIri, shortname, shortcode, longname, description, keywords, logo, ontologies, status, selfjoin)
    project.unescape
  }

  /**
   * Helper method for checking if a project identified by shortname exists.
   *
   * @param shortname the shortname of the project.
   * @return a [[Boolean]].
   */
  private def projectByShortnameExists(shortname: String): Task[Boolean] = {
    val query = twirl.queries.sparql.admin.txt.checkProjectExistsByShortname(shortname)
    triplestoreService.sparqlHttpAsk(query.toString()).map(_.result)
  }

  /**
   * Helper method for checking if a project identified by shortcode exists.
   *
   * @param shortcode the shortcode of the project.
   * @return a [[Boolean]].
   */
  private def projectByShortcodeExists(shortcode: String): Task[Boolean] = {
    val query = twirl.queries.sparql.admin.txt.checkProjectExistsByShortcode(shortcode)
    triplestoreService.sparqlHttpAsk(query.toString()).map(_.result)
  }

  private def getProjectFromCache(identifier: ProjectIdentifierADM): Task[Option[ProjectADM]] =
    messageRelay.ask[Option[ProjectADM]](CacheServiceGetProjectADM(identifier)).map(_.map(_.unescape))
}

object ProjectsResponderADMLive {
  val layer: ZLayer[
    MessageRelay with TriplestoreService with StringFormatter with IriService with AppConfig,
    Nothing,
    ProjectsResponderADM
  ] = ZLayer.fromZIO {
    for {
      c       <- ZIO.service[AppConfig].map(new CacheServiceSettings(_))
      iris    <- ZIO.service[IriService]
      sf      <- ZIO.service[StringFormatter]
      ts      <- ZIO.service[TriplestoreService]
      mr      <- ZIO.service[MessageRelay]
      handler <- mr.subscribe(ProjectsResponderADMLive(ts, mr, iris, c, sf))
    } yield handler
  }
}
