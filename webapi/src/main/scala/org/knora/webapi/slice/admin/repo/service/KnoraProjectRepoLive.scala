/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.repo.service

import play.twirl.api.TxtFormat
import zio.*

import dsp.valueobjects.RestrictedViewSize
import dsp.valueobjects.V2
import org.knora.webapi.messages.OntologyConstants.KnoraAdmin.*
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM
import org.knora.webapi.messages.store.triplestoremessages.BooleanLiteralV2
import org.knora.webapi.messages.store.triplestoremessages.IriLiteralV2
import org.knora.webapi.messages.store.triplestoremessages.SparqlExtendedConstructResponse.ConstructPredicateObjects
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.messages.store.triplestoremessages.SubjectV2
import org.knora.webapi.messages.twirl.queries.sparql
import org.knora.webapi.messages.util.rdf.NewRdfModel
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.KnoraProject.*
import org.knora.webapi.slice.admin.domain.service.KnoraProjectRepo
import org.knora.webapi.slice.common.repo.service.PredicateObjectMapper
import org.knora.webapi.slice.resourceinfo.domain.InternalIri
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Construct
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update

final case class KnoraProjectRepoLive(
  private val triplestore: TriplestoreService,
  private val mapper: PredicateObjectMapper,
  private implicit val sf: StringFormatter
) extends KnoraProjectRepo {

  private val belongsToOntology = "http://www.knora.org/ontology/knora-admin#belongsToOntology"

  override def findById(id: ProjectIri): Task[Option[KnoraProject]] =
    findOneByQuery(sparql.admin.txt.getProjects(maybeIri = Some(id.value), None, None))

  override def findById(id: ProjectIdentifierADM): Task[Option[KnoraProject]] = {
    val maybeIri       = id.asIriIdentifierOption
    val maybeShortname = id.asShortnameIdentifierOption
    val maybeShortcode = id.asShortcodeIdentifierOption
    findOneByQuery(
      sparql.admin.txt
        .getProjects(maybeIri = maybeIri, maybeShortname = maybeShortname, maybeShortcode = maybeShortcode)
    )
  }

  private def findOneByQuery(query: TxtFormat.Appendable): Task[Option[KnoraProject]] =
    for {
      model   <- triplestore.queryRdf(Construct(query))
      newModel = NewRdfModel(model)
      x       <- ZIO.fromOption(findOneNew(newModel)).orElseFail(new Exception("not found"))
    } yield None // TODO: this is of course not working!

  private def findOneNew(model: NewRdfModel) =
    for {
      projectResource <- model.getResource("http://rdfh.ch/projects/0001")
      shortcode       <- projectResource.getStringLiteralByProperty(ProjectShortcode)
      shortname       <- projectResource.getStringLiteralByProperty(ProjectShortname)
      longname        <- projectResource.getStringLiteralByProperty(ProjectLongname)
      description      = projectResource.getStringLiteralsByProperty(ProjectDescription)
      keywords         = projectResource.getStringLiteralsByProperty(ProjectKeyword)
      logo             = projectResource.getStringLiteralByProperty(ProjectLogo)
      status          <- projectResource.getBooleanLiteralByProperty(StatusProp)
      selfjoin        <- projectResource.getBooleanLiteralByProperty(HasSelfJoinEnabled)
      ontologies       = projectResource.getObjectIrisByProperty(belongsToOntology)
      _                = println(s"shortcode: $shortcode")
      _                = println(s"shortname: $shortname")
      _                = println(s"longname: $longname")
      _                = println(s"description: $description")
      _                = println(s"keywords: $keywords")
      _                = println(s"logo: $logo")
      _                = println(s"status: $status")
      _                = println(s"selfjoin: $selfjoin")
      _                = println(s"ontos: $ontologies")
    } yield ()

  override def findAll(): Task[List[KnoraProject]] = {
    val query = sparql.admin.txt.getProjects(None, None, None)
    for {
      projectsResponse <- triplestore.query(Construct(query)).flatMap(_.asExtended).map(_.statements.toList)
      projects         <- ZIO.foreach(projectsResponse)(toKnoraProject)
    } yield projects
  }

  private def toKnoraProject(subjectPropsTuple: (SubjectV2, ConstructPredicateObjects)): Task[KnoraProject] = {
    val (subject, propertiesMap) = subjectPropsTuple
    for {
      projectIri <- ProjectIri.from(subject.value).toZIO
      shortname <- mapper
                     .getSingleOrFail[StringLiteralV2](ProjectShortname, propertiesMap)
                     .flatMap(l => Shortname.from(l.value).toZIO)
      shortcode <- mapper
                     .getSingleOrFail[StringLiteralV2](ProjectShortcode, propertiesMap)
                     .flatMap(l => Shortcode.from(l.value).toZIO)
      longname <- mapper
                    .getSingleOption[StringLiteralV2](ProjectLongname, propertiesMap)
                    .flatMap(optLit => ZIO.foreach(optLit)(l => Longname.from(l.value).toZIO))
      description <- mapper
                       .getNonEmptyChunkOrFail[StringLiteralV2](ProjectDescription, propertiesMap)
                       .map(_.map(l => V2.StringLiteralV2(l.value, l.language)))
                       .flatMap(ZIO.foreach(_)(Description.from(_).toZIO))
      keywords <- mapper
                    .getList[StringLiteralV2](ProjectKeyword, propertiesMap)
                    .flatMap(l => ZIO.foreach(l.map(_.value).sorted)(Keyword.from(_).toZIO))
      logo <- mapper
                .getSingleOption[StringLiteralV2](ProjectLogo, propertiesMap)
                .flatMap(optLit => ZIO.foreach(optLit)(l => Logo.from(l.value).toZIO))
      status <- mapper
                  .getSingleOrFail[BooleanLiteralV2](StatusProp, propertiesMap)
                  .map(l => Status.from(l.value))
      selfjoin <- mapper
                    .getSingleOrFail[BooleanLiteralV2](HasSelfJoinEnabled, propertiesMap)
                    .map(l => SelfJoin.from(l.value))
      ontologies <-
        mapper
          .getList[IriLiteralV2]("http://www.knora.org/ontology/knora-admin#belongsToOntology", propertiesMap)
          .map(_.map(l => InternalIri(l.value)))
    } yield KnoraProject(
      projectIri,
      shortname,
      shortcode,
      longname,
      description,
      keywords,
      logo,
      status,
      selfjoin,
      ontologies
    )
  }

  override def setProjectRestrictedViewSize(
    project: KnoraProject,
    size: RestrictedViewSize
  ): Task[Unit] = {
    val query = sparql.admin.txt
      .setProjectRestrictedViewSettings(project.id.value, size.value)
    triplestore.query(Update(query.toString))
  }
}

object KnoraProjectRepoLive {
  val layer = ZLayer.derive[KnoraProjectRepoLive]
}
