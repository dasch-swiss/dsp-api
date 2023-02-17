package org.knora.webapi.responders

import dsp.errors.{InconsistentRepositoryDataException, NotFoundException}
import dsp.valueobjects.V2
import org.knora.webapi.IRI
import org.knora.webapi.core.{MessageHandler, MessageRelay, RelayedMessage}
import org.knora.webapi.messages.IriConversions._
import org.knora.webapi.messages.OntologyConstants.KnoraAdmin._
import org.knora.webapi.messages.admin.responder.projectsmessages.{
  ProjectADM,
  ProjectsGetADM,
  ProjectsGetRequestADM,
  ProjectsGetResponseADM
}
import org.knora.webapi.messages.store.triplestoremessages.{BooleanLiteralV2, LiteralV2, StringLiteralV2, SubjectV2}
import org.knora.webapi.messages.util.KnoraSystemInstances
import org.knora.webapi.messages.v2.responder.ontologymessages.{
  OntologyMetadataGetByProjectRequestV2,
  OntologyMetadataV2,
  ReadOntologyMetadataV2
}
import org.knora.webapi.messages.{ResponderRequest, SmartIri, StringFormatter}
import org.knora.webapi.store.triplestore.api.TriplestoreService
import zio._

trait ProjectsMessageHandler {

  /**
   * Gets all the projects and returns them as a [[ProjectADM]].
   *
   * @return all the projects as a [[ProjectADM]].
   *
   *         [[NotFoundException]] if no projects are found.
   */
  def projectsGetRequestADM(): Task[ProjectsGetResponseADM]

  /**
   * Gets all the projects and returns them as a sequence containing [[ProjectADM]].
   *
   * @return all the projects as a sequence containing [[ProjectADM]].
   */
  def projectsGetADM(): Task[Seq[ProjectADM]]
}

final case class ProjectsMessageHandlerLive(
  triplestoreService: TriplestoreService,
  messageRelay: MessageRelay,
  implicit val sf: StringFormatter
) extends ProjectsMessageHandler
    with MessageHandler {

  override def handle(message: ResponderRequest): Task[Any] = message match {
    case _: ProjectsGetRequestADM => projectsGetRequestADM()
    case _: ProjectsGetADM        => projectsGetADM()
  }

  private val supportedMessageTypes: List[Class[_ <: RelayedMessage]] =
    List(classOf[ProjectsGetRequestADM], classOf[ProjectsGetADM])
  override def isResponsibleFor(message: ResponderRequest): Boolean = supportedMessageTypes.contains(message.getClass)

  /**
   * Gets all the projects and returns them as a [[ProjectADM]].
   *
   * @return all the projects as a [[ProjectADM]].
   *
   *         [[NotFoundException]] if no projects are found.
   */
  override def projectsGetRequestADM(): Task[ProjectsGetResponseADM] =
    for {
      projects <- projectsGetADM()
      result = if (projects.nonEmpty) {
                 ProjectsGetResponseADM(projects = projects)
               } else {
                 throw NotFoundException(s"No projects found")
               }
    } yield result

  /**
   * Gets all the projects and returns them as a sequence containing [[ProjectADM]].
   *
   * @return all the projects as a sequence containing [[ProjectADM]].
   */
  override def projectsGetADM(): Task[Seq[ProjectADM]] = {
    val query =
      org.knora.webapi.messages.twirl.queries.sparql.admin.txt
        .getProjects(maybeIri = None, maybeShortname = None, maybeShortcode = None)
        .toString()
    for {
      projectsResponse      <- triplestoreService.sparqlHttpExtendedConstruct(query)
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
   * Helper method that turns SPARQL result rows into a [[ProjectADM]].
   *
   * @param statements results from the SPARQL query representing information about the project.
   * @param ontologies the ontologies in the project.
   * @return a [[ProjectADM]] representing information about project.
   */
  @throws[InconsistentRepositoryDataException]("if the statements do not contain expected keys")
  private def convertStatementsToProjectADM(
    statements: (SubjectV2, Map[SmartIri, Seq[LiteralV2]]),
    ontologies: Seq[IRI]
  ): ProjectADM = {
    val projectIri: IRI = statements._1.toString

    def getOption[A <: LiteralV2](key: IRI): Option[Seq[A]] =
      statements._2.get(key.toSmartIri).map(_.map(_.asInstanceOf[A]))

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
   * Given a set of project IRIs, gets the ontologies that belong to each project.
   *
   * @param projectIris a set of project IRIs. If empty, returns the ontologies for all projects.
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
}

object ProjectsMessageHandlerLive {
  val layer: URLayer[StringFormatter with MessageRelay with TriplestoreService, ProjectsMessageHandlerLive] =
    ZLayer.fromZIO {
      for {
        triplestoreService <- ZIO.service[TriplestoreService]
        messageRelay       <- ZIO.service[MessageRelay]
        stringFormatter    <- ZIO.service[StringFormatter]
        handler            <- messageRelay.subscribe(ProjectsMessageHandlerLive(triplestoreService, messageRelay, stringFormatter))
      } yield handler
    }
}
