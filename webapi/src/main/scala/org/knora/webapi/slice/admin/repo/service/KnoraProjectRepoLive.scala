/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.repo.service

import play.twirl.api.TxtFormat
import zio.*

import dsp.valueobjects.V2
import org.knora.webapi.messages.OntologyConstants.KnoraAdmin.*
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM
import org.knora.webapi.messages.store.triplestoremessages.BooleanLiteralV2
import org.knora.webapi.messages.store.triplestoremessages.SparqlExtendedConstructResponse.ConstructPredicateObjects
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.messages.store.triplestoremessages.SubjectV2
import org.knora.webapi.messages.twirl.queries.sparql
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.KnoraProject.*
import org.knora.webapi.slice.admin.domain.model.RestrictedView
import org.knora.webapi.slice.admin.domain.model.RestrictedViewSize
import org.knora.webapi.slice.admin.domain.service.KnoraProjectRepo
import org.knora.webapi.slice.admin.repo.rdf.RdfConversions.*
import org.knora.webapi.slice.admin.repo.service.KnoraProjectQueries.getProjectByIri
import org.knora.webapi.slice.common.repo.rdf.Errors.RdfError
import org.knora.webapi.slice.common.repo.rdf.RdfModel
import org.knora.webapi.slice.common.repo.service.PredicateObjectMapper
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Construct
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update
import org.knora.webapi.store.triplestore.errors.TriplestoreResponseException

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
      newModel <- RdfModel.fromTurtle(ttl).mapError(e => TriplestoreResponseException(e.msg))
      project  <- toKnoraProjectNew(newModel, iri).option
    } yield project

  private def findOneByQuery(query: TxtFormat.Appendable): Task[Option[KnoraProject]] =
    for {
      construct <- triplestore.query(Construct(query)).flatMap(_.asExtended).map(_.statements.headOption)
      project   <- ZIO.foreach(construct)(toKnoraProject)
    } yield project

  private def toKnoraProjectNew(model: RdfModel, iri: ProjectIri): IO[RdfError, KnoraProject] =
    for {
      resource    <- model.getResource(iri.value)
      shortcode   <- resource.getStringLiteralOrFail[Shortcode](ProjectShortcode)
      shortname   <- resource.getStringLiteralOrFail[Shortname](ProjectShortname)
      longname    <- resource.getStringLiteral[Longname](ProjectLongname)
      description <- resource.getLangStringLiteralsOrFail[Description](ProjectDescription)
      keywords    <- resource.getStringLiterals[Keyword](ProjectKeyword)
      logo        <- resource.getStringLiteral[Logo](ProjectLogo)
      status      <- resource.getBooleanLiteralOrFail[Status](StatusProp)
      selfjoin    <- resource.getBooleanLiteralOrFail[SelfJoin](HasSelfJoinEnabled)
    } yield KnoraProject(
      id = iri,
      shortcode = shortcode,
      shortname = shortname,
      longname = longname,
      description = description,
      keywords = keywords.toList.sortBy(_.value),
      logo = logo,
      status = status,
      selfjoin = selfjoin
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
      projectIri <- mapper.eitherOrDie(ProjectIri.from(subject.value))
      shortname <- mapper
                     .getSingleOrFail[StringLiteralV2](ProjectShortname, propertiesMap)
                     .flatMap(l => mapper.eitherOrDie(Shortname.from(l.value)))
      shortcode <- mapper
                     .getSingleOrFail[StringLiteralV2](ProjectShortcode, propertiesMap)
                     .flatMap(l => mapper.eitherOrDie(Shortcode.from(l.value)))
      longname <- mapper
                    .getSingleOption[StringLiteralV2](ProjectLongname, propertiesMap)
                    .flatMap(ZIO.foreach(_)(it => mapper.eitherOrDie(Longname.from(it.value))))
      description <- mapper
                       .getNonEmptyChunkOrFail[StringLiteralV2](ProjectDescription, propertiesMap)
                       .map(_.map(l => V2.StringLiteralV2(l.value, l.language)))
                       .flatMap(ZIO.foreach(_)(it => mapper.eitherOrDie(Description.from(it))))
      keywords <- mapper
                    .getList[StringLiteralV2](ProjectKeyword, propertiesMap)
                    .flatMap(l => ZIO.foreach(l.map(_.value).sorted)(it => mapper.eitherOrDie(Keyword.from(it))))
      logo <- mapper
                .getSingleOption[StringLiteralV2](ProjectLogo, propertiesMap)
                .flatMap(ZIO.foreach(_)(it => mapper.eitherOrDie(Logo.from(it.value))))
      status <- mapper
                  .getSingleOrFail[BooleanLiteralV2](StatusProp, propertiesMap)
                  .map(l => Status.from(l.value))
      selfjoin <- mapper
                    .getSingleOrFail[BooleanLiteralV2](HasSelfJoinEnabled, propertiesMap)
                    .map(l => SelfJoin.from(l.value))

    } yield KnoraProject(
      projectIri,
      shortname,
      shortcode,
      longname,
      description,
      keywords,
      logo,
      status,
      selfjoin
    )
  }

  override def setProjectRestrictedView(
    project: KnoraProject,
    settings: RestrictedView
  ): Task[Unit] =
    triplestore.query(Update(Queries.setRestrictedView(project.id, settings.size, settings.watermark)))

  object Queries {
    def setRestrictedView(projectIri: ProjectIri, size: RestrictedViewSize, watermark: Boolean): String =
      s"""
         |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
         |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
         |PREFIX knora-admin: <http://www.knora.org/ontology/knora-admin#>
         |
         |WITH <http://www.knora.org/data/admin>
         |DELETE {
         |	<${projectIri.value}> knora-admin:projectRestrictedViewSize ?prevSize .
         |	<${projectIri.value}> knora-admin:projectRestrictedViewWatermark ?prevWatermark.
         |}
         |INSERT {
         |	<${projectIri.value}> knora-admin:projectRestrictedViewSize "${size.value}"^^xsd:string .
         |	<${projectIri.value}> knora-admin:projectRestrictedViewWatermark "$watermark"^^xsd:boolean.
         |}
         |WHERE {
         |    <${projectIri.value}> a knora-admin:knoraProject .
         |    OPTIONAL {
         |        <${projectIri.value}> knora-admin:projectRestrictedViewSize ?prevSize .
         |    }
         |    OPTIONAL {
         |        <${projectIri.value}> knora-admin:projectRestrictedViewWatermark ?prevWatermark .
         |    }
         |}
         |""".stripMargin
  }
}

object KnoraProjectRepoLive {
  val layer = ZLayer.derive[KnoraProjectRepoLive]
}
