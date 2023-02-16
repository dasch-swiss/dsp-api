package org.knora.webapi.responders

import zio._

import dsp.errors.InconsistentRepositoryDataException
import dsp.errors.NotFoundException
import dsp.valueobjects.V2
import org.knora.webapi.IRI
import org.knora.webapi.core.MessageHandler
import org.knora.webapi.core.MessageRelay
import org.knora.webapi.core.RelayedMessage
import org.knora.webapi.messages.IriConversions._
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.ResponderRequest
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectsGetADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectsGetRequestADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectsGetResponseADM
import org.knora.webapi.messages.store.triplestoremessages.BooleanLiteralV2
import org.knora.webapi.messages.store.triplestoremessages.LiteralV2
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.messages.store.triplestoremessages.SubjectV2
import org.knora.webapi.messages.util.KnoraSystemInstances
import org.knora.webapi.messages.v2.responder.ontologymessages.OntologyMetadataGetByProjectRequestV2
import org.knora.webapi.messages.v2.responder.ontologymessages.OntologyMetadataV2
import org.knora.webapi.messages.v2.responder.ontologymessages.ReadOntologyMetadataV2
import org.knora.webapi.store.triplestore.api.TriplestoreService

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
            statements2ProjectADM(
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
