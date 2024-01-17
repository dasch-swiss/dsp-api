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
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.KnoraProject.*
import org.knora.webapi.slice.admin.domain.service.KnoraProjectRepo
import org.knora.webapi.slice.admin.repo.service.KnoraProjectQueries.getProjectByIri
import org.knora.webapi.slice.common.repo.rdf.Errors.RdfError
import org.knora.webapi.slice.common.repo.rdf.ImprovedRdfModel
import org.knora.webapi.slice.common.repo.service.PredicateObjectMapper
import org.knora.webapi.slice.resourceinfo.domain.InternalIri
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Construct
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update

object KnoraProjectQueries {
  private[service] def getProjectByIri(iri: ProjectIri): Construct =
    Construct(
      s"""|PREFIX knora-admin: <http://www.knora.org/ontology/knora-admin#>
          |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
          |PREFIX owl: <http://www.w3.org/2002/07/owl#>
          |CONSTRUCT {
          |  ?project ?p ?o .
          |  ?project knora-admin:belongsToOntology ?ontology .
          |} WHERE {
          |  BIND(IRI("${iri.value}") as ?project)
          |  ?project a knora-admin:knoraProject .
          |  OPTIONAL {
          |      ?ontology a owl:Ontology .
          |      ?ontology knora-base:attachedToProject ?project .
          |  }
          |  ?project ?p ?o .
          |}""".stripMargin
    )
}

final case class KnoraProjectRepoLive(
  private val triplestore: TriplestoreService,
  private val mapper: PredicateObjectMapper,
  private implicit val sf: StringFormatter
) extends KnoraProjectRepo {

  private val belongsToOntology = "http://www.knora.org/ontology/knora-admin#belongsToOntology"

  override def findById(id: ProjectIri): Task[Option[KnoraProject]] = findOneByIri(id)

  override def findById(id: ProjectIdentifierADM): Task[Option[KnoraProject]] =
    id.asIriIdentifierOption match {
      case Some(iri) => findOneByIri(ProjectIri.unsafeFrom(iri))
      case None =>
        val maybeShortname = id.asShortnameIdentifierOption
        val maybeShortcode = id.asShortcodeIdentifierOption
        findOneByQuery(
          sparql.admin.txt
            .getProjects(None, maybeShortname = maybeShortname, maybeShortcode = maybeShortcode)
        )
    }

  private def findOneByIri(iri: ProjectIri): Task[Option[KnoraProject]] =
    for {
      ttl      <- triplestore.queryRdf(getProjectByIri(iri))
      newModel <- ImprovedRdfModel.fromTurtle(ttl)
      project  <- toKnoraProjectNew(newModel, iri).option
    } yield project

  private def findOneByQuery(query: TxtFormat.Appendable): Task[Option[KnoraProject]] =
    for {
      construct <- triplestore.query(Construct(query)).flatMap(_.asExtended).map(_.statements.headOption)
      project   <- ZIO.foreach(construct)(toKnoraProject)
    } yield project

  private def toKnoraProjectNew(model: ImprovedRdfModel, iri: ProjectIri): IO[RdfError, KnoraProject] =
    for {
      projectResource <- model.getResource(iri.value)
      shortcode       <- projectResource.getStringLiteralOrFail[Shortcode](ProjectShortcode)
      shortname       <- projectResource.getStringLiteralOrFail[Shortname](ProjectShortname)
      longname        <- projectResource.getStringLiteral[Longname](ProjectLongname)
      description     <- projectResource.getLangStringLiteralsOrFail[Description](ProjectDescription)
      keywords        <- projectResource.getStringLiterals[Keyword](ProjectKeyword)
      logo            <- projectResource.getStringLiteral[Logo](ProjectLogo)
      status          <- projectResource.getBooleanLiteralOrFail[Status](StatusProp)
      selfjoin        <- projectResource.getBooleanLiteralOrFail[SelfJoin](HasSelfJoinEnabled)
      ontologies      <- projectResource.getObjectIris(belongsToOntology)
    } yield KnoraProject(
      id = iri,
      shortcode = shortcode,
      shortname = shortname,
      longname = longname,
      description = description,
      keywords = keywords.toList,
      logo = logo,
      status = status,
      selfjoin = selfjoin,
      ontologies = ontologies.toList
    )

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
