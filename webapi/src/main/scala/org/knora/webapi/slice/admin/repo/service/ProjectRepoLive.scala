package org.knora.webapi.slice.admin.repo.service
import play.twirl.api.TxtFormat
import zio.Task
import zio.ZIO
import zio.ZLayer

import dsp.errors.InconsistentRepositoryDataException
import dsp.valueobjects.V2
import org.knora.webapi.IRI
import org.knora.webapi.messages.OntologyConstants.KnoraAdmin.HasSelfJoinEnabled
import org.knora.webapi.messages.OntologyConstants.KnoraAdmin.ProjectDescription
import org.knora.webapi.messages.OntologyConstants.KnoraAdmin.ProjectKeyword
import org.knora.webapi.messages.OntologyConstants.KnoraAdmin.ProjectLogo
import org.knora.webapi.messages.OntologyConstants.KnoraAdmin.ProjectLongname
import org.knora.webapi.messages.OntologyConstants.KnoraAdmin.ProjectShortcode
import org.knora.webapi.messages.OntologyConstants.KnoraAdmin.ProjectShortname
import org.knora.webapi.messages.OntologyConstants.KnoraAdmin.Status
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM
import org.knora.webapi.messages.store.triplestoremessages.BooleanLiteralV2
import org.knora.webapi.messages.store.triplestoremessages.LiteralV2
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.messages.twirl
import org.knora.webapi.slice.admin.domain.service.ProjectRepo
import org.knora.webapi.slice.ontology.domain.service.OntologyRepo
import org.knora.webapi.slice.resourceinfo.domain.InternalIri
import org.knora.webapi.slice.resourceinfo.domain.IriConverter
import org.knora.webapi.store.triplestore.api.TriplestoreService

final case class ProjectRepoLive(
  private val triplestore: TriplestoreService,
  private val ontologyRepo: OntologyRepo,
  private val iriConverter: IriConverter
) extends ProjectRepo {

  override def findById(id: InternalIri): Task[Option[ProjectADM]] =
    findByQuery(twirl.queries.sparql.admin.txt.getProjects(maybeIri = Some(id.toString), None, None))

  override def findByProjectIdentifier(id: ProjectIdentifierADM): Task[Option[ProjectADM]] =
    findByQuery(
      twirl.queries.sparql.admin.txt
        .getProjects(id.asIriIdentifierOption, id.asShortnameIdentifierOption, id.asShortcodeIdentifierOption)
    )

  private def findByQuery(query: TxtFormat.Appendable): Task[Option[ProjectADM]] =
    for {
      construct <- triplestore.sparqlHttpExtendedConstruct(query.toString).map(_.statements.headOption)
      project   <- ZIO.foreach(construct)(it => assembleProjectADM(InternalIri(it._1.toString), it._2))
    } yield project

  override def findAll(): Task[List[ProjectADM]] = {
    val query = twirl.queries.sparql.admin.txt.getProjects(None, None, None)
    for {
      projectsResponse <- triplestore.sparqlHttpExtendedConstruct(query.toString()).map(_.statements.toList)
      projects         <- ZIO.foreach(projectsResponse)(it => assembleProjectADM(InternalIri(it._1.toString), it._2))
    } yield projects.sorted
  }

  private def assembleProjectADM(
    projectIri: InternalIri,
    statements: Map[SmartIri, Seq[LiteralV2]]
  ): Task[ProjectADM] = {
    val iri: IRI                                     = projectIri.value
    val propertiesMap: Map[SmartIri, Seq[LiteralV2]] = statements

    def getOption[A <: LiteralV2](key: IRI): Task[Option[Seq[A]]] =
      for {
        smartIri <- iriConverter.asInternalSmartIri(key)
        props     = propertiesMap.get(smartIri)
      } yield props.map(_.map(_.asInstanceOf[A]))

    def getOrFail[A <: LiteralV2](key: IRI): Task[Seq[A]] =
      getOption[A](key)
        .flatMap(ZIO.fromOption(_))
        .orElseFail(InconsistentRepositoryDataException(s"Project: $iri has no $key defined."))

    for {
      ontologyIris <- ontologyRepo.findByProject(projectIri).map(_.map(_.ontologyMetadata.ontologyIri.toIri))
      shortname    <- getOrFail[StringLiteralV2](ProjectShortname).map(_.head.value)
      shortcode    <- getOrFail[StringLiteralV2](ProjectShortcode).map(_.head.value)
      status       <- getOrFail[BooleanLiteralV2](Status).map(_.head.value)
      keywords     <- getOption[StringLiteralV2](ProjectKeyword).map(_.getOrElse(Seq.empty).map(_.value).sorted)
      logo         <- getOption[StringLiteralV2](ProjectLogo).map(_.map(_.head.value))
      longname     <- getOption[StringLiteralV2](ProjectLongname).map(_.map(_.head.value))
      selfjoin     <- getOrFail[BooleanLiteralV2](HasSelfJoinEnabled).map(_.head.value)
      description <-
        getOrFail[StringLiteralV2](ProjectDescription).map(_.map(desc => V2.StringLiteralV2(desc.value, desc.language)))

      p = ProjectADM(iri, shortname, shortcode, longname, description, keywords, logo, ontologyIris, status, selfjoin)
    } yield p.unescape
  }
}

object ProjectRepoLive {
  val layer = ZLayer.fromFunction(ProjectRepoLive.apply _)
}
