package org.knora.webapi.slice.admin.repo.service

import play.twirl.api.TxtFormat
import zio._

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
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM
import org.knora.webapi.messages.store.triplestoremessages.BooleanLiteralV2
import org.knora.webapi.messages.store.triplestoremessages.LiteralV2
import org.knora.webapi.messages.store.triplestoremessages.SparqlExtendedConstructResponse.ConstructPredicateObjects
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.messages.twirl
import org.knora.webapi.slice.admin.domain.model.DspProject
import org.knora.webapi.slice.admin.domain.service.DspProjectRepo
import org.knora.webapi.slice.ontology.domain.service.OntologyRepo
import org.knora.webapi.slice.resourceinfo.domain.InternalIri
import org.knora.webapi.slice.resourceinfo.domain.IriConverter
import org.knora.webapi.store.triplestore.api.TriplestoreService

final case class DspProjectRepoLive(
  private val triplestore: TriplestoreService,
  private val iriConverter: IriConverter
) extends DspProjectRepo {

  override def findById(id: InternalIri): Task[Option[DspProject]] =
    findOneByQuery(twirl.queries.sparql.admin.txt.getProjects(maybeIri = Some(id.value)))

  override def findByProjectIdentifier(id: ProjectIdentifierADM): Task[Option[DspProject]] =
    findOneByQuery(
      twirl.queries.sparql.admin.txt
        .getProjects(id.asIriIdentifierOption, id.asShortnameIdentifierOption, id.asShortcodeIdentifierOption)
    )

  private def findOneByQuery(query: TxtFormat.Appendable): Task[Option[DspProject]] =
    for {
      construct <- triplestore.sparqlHttpExtendedConstruct(query.toString).map(_.statements.headOption)
      project   <- ZIO.foreach(construct)(it => toDspProject(InternalIri(it._1.toString), it._2))
    } yield project

  override def findAll(): Task[List[DspProject]] = {
    val query = twirl.queries.sparql.admin.txt.getProjects()
    for {
      projectsResponse <- triplestore.sparqlHttpExtendedConstruct(query.toString()).map(_.statements.toList)
      projects         <- ZIO.foreach(projectsResponse)(it => toDspProject(InternalIri(it._1.toString), it._2))
    } yield projects
  }

  private def toDspProject(
    projectIri: InternalIri,
    propertiesMap: ConstructPredicateObjects
  ): Task[DspProject] = {
    def getOption[A <: LiteralV2](key: IRI): Task[Option[Seq[A]]] =
      for {
        smartIri <- iriConverter.asInternalSmartIri(key)
        props     = propertiesMap.get(smartIri)
      } yield props.map(_.map(_.asInstanceOf[A]))

    def getOrFail[A <: LiteralV2](key: IRI): Task[Seq[A]] =
      getOption[A](key)
        .flatMap(ZIO.fromOption(_))
        .orElseFail(InconsistentRepositoryDataException(s"Project: ${projectIri.value} has no $key defined."))

    for {
      shortname <- getOrFail[StringLiteralV2](ProjectShortname).map(_.head.value)
      shortcode <- getOrFail[StringLiteralV2](ProjectShortcode).map(_.head.value)
      status    <- getOrFail[BooleanLiteralV2](Status).map(_.head.value)
      keywords  <- getOption[StringLiteralV2](ProjectKeyword).map(_.getOrElse(Seq.empty).map(_.value).sorted)
      logo      <- getOption[StringLiteralV2](ProjectLogo).map(_.map(_.head.value))
      longname  <- getOption[StringLiteralV2](ProjectLongname).map(_.map(_.head.value))
      selfjoin  <- getOrFail[BooleanLiteralV2](HasSelfJoinEnabled).map(_.head.value)
      description <-
        getOrFail[StringLiteralV2](ProjectDescription).map(_.map(desc => V2.StringLiteralV2(desc.value, desc.language)))
    } yield DspProject(projectIri, shortname, shortcode, longname, description, keywords, logo, status, selfjoin)
  }
}

object DspProjectRepoLive {
  val layer: URLayer[TriplestoreService with OntologyRepo with IriConverter, DspProjectRepoLive] =
    ZLayer.fromFunction(DspProjectRepoLive.apply _)
}
