/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.repo.service

import org.eclipse.rdf4j.model.vocabulary.OWL
import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.sparqlbuilder.core.query.Queries
import zio.*

import dsp.errors.InconsistentRepositoryDataException
import org.knora.webapi.messages.OntologyConstants.KnoraAdmin.*
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.KnoraProject.*
import org.knora.webapi.slice.admin.domain.model.RestrictedView
import org.knora.webapi.slice.admin.domain.model.RestrictedViewSize
import org.knora.webapi.slice.admin.domain.service.KnoraProjectRepo
import org.knora.webapi.slice.admin.repo.rdf.RdfConversions.*
import org.knora.webapi.slice.admin.repo.service.KnoraProjectQueries.getProjectByIri
import org.knora.webapi.slice.admin.repo.service.KnoraProjectQueries.getProjectByShortcode
import org.knora.webapi.slice.admin.repo.service.KnoraProjectQueries.getProjectByShortname
import org.knora.webapi.slice.common.repo.rdf.Errors.RdfError
import org.knora.webapi.slice.common.repo.rdf.RdfResource
import org.knora.webapi.slice.common.repo.service.PredicateObjectMapper
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Construct
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update
import org.eclipse.rdf4j.sparqlbuilder.core.SparqlBuilder.`var` as variable
import org.eclipse.rdf4j.sparqlbuilder.graphpattern.GraphPatterns.tp

import org.knora.webapi.slice.admin.repo.rdf.Vocabulary

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

  private[service] def getProjectByShortcode(shortcode: Shortcode): Construct =
    Construct(
      s"""|PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
          |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
          |PREFIX knora-admin: <http://www.knora.org/ontology/knora-admin#>
          |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
          |PREFIX owl: <http://www.w3.org/2002/07/owl#>
          |CONSTRUCT {
          |  ?project ?p ?o .
          |  ?project knora-admin:belongsToOntology ?ontology .
          |} WHERE {
          |  ?project knora-admin:projectShortcode "${shortcode.value}"^^xsd:string .
          |  ?project a knora-admin:knoraProject .
          |    OPTIONAL{
          |        ?ontology a owl:Ontology .
          |        ?ontology knora-base:attachedToProject ?project .
          |    }
          |    ?project ?p ?o .
          |}""".stripMargin
    )

  private[service] def getProjectByShortname(shortname: Shortname): Construct =
    Construct(
      s"""|PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
          |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
          |PREFIX knora-admin: <http://www.knora.org/ontology/knora-admin#>
          |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
          |PREFIX owl: <http://www.w3.org/2002/07/owl#>
          |CONSTRUCT {
          |  ?project ?p ?o .
          |  ?project knora-admin:belongsToOntology ?ontology .
          |} WHERE {
          |  ?project knora-admin:projectShortname "${shortname.value}"^^xsd:string .
          |  ?project a knora-admin:knoraProject .
          |    OPTIONAL{
          |        ?ontology a owl:Ontology .
          |        ?ontology knora-base:attachedToProject ?project .
          |    }
          |    ?project ?p ?o .
          |}""".stripMargin
    )

  private[service] def getAllProjects: Construct = {
    val (project, p, o, ontology) = (variable("project"), variable("p"), variable("o"), variable("ontology"))
    def spo                       = tp(project, p, o)
    val query =
      Queries
        .CONSTRUCT(spo.andHas(Vocabulary.KnoraAdmin.belongsToProject, ontology))
        .prefix(Vocabulary.KnoraAdmin.NS, Vocabulary.KnoraBase.NS, OWL.NS)
        .where(
          project
            .isA(Vocabulary.KnoraAdmin.KnoraProject)
            .and(
              ontology.isA(OWL.ONTOLOGY).andHas(Vocabulary.KnoraBase.attachedToProject, project).optional
            )
            .and(spo)
        )
    Construct(query.getQueryString)
  }
}

final case class KnoraProjectRepoLive(
  private val triplestore: TriplestoreService,
  private val mapper: PredicateObjectMapper,
  private implicit val sf: StringFormatter
) extends KnoraProjectRepo {

  private val belongsToOntology = "http://www.knora.org/ontology/knora-admin#belongsToOntology"

  override def findAll(): Task[List[KnoraProject]] =
    for {
      model     <- triplestore.queryRdfModel(KnoraProjectQueries.getAllProjects)
      resources <- model.getSubjectResources
      projects <- ZIO.foreach(resources)(res =>
                    toKnoraProjectNew(res).orElseFail(
                      InconsistentRepositoryDataException(s"Failed to convert $res to KnoraProject")
                    )
                  )
    } yield projects.toList

  override def findById(id: ProjectIri): Task[Option[KnoraProject]] = findOneByIri(id)

  override def findById(id: ProjectIdentifierADM): Task[Option[KnoraProject]] =
    id match {
      case ProjectIdentifierADM.IriIdentifier(iri)             => findOneByIri(iri)
      case ProjectIdentifierADM.ShortcodeIdentifier(shortcode) => findOneByShortcode(shortcode)
      case ProjectIdentifierADM.ShortnameIdentifier(shortname) => findOneByShortname(shortname)
    }

  private def findOneByIri(iri: ProjectIri): Task[Option[KnoraProject]] =
    for {
      model    <- triplestore.queryRdfModel(getProjectByIri(iri))
      resource <- model.getResource(iri.value)
      project  <- ZIO.foreach(resource)(toKnoraProjectNew).orElse(ZIO.none)
    } yield project

  private def findOneByShortcode(shortcode: Shortcode): Task[Option[KnoraProject]] =
    for {
      model    <- triplestore.queryRdfModel(getProjectByShortcode(shortcode))
      resource <- model.getResourceByPropertyStringValue(ProjectShortcode, shortcode.value)
      project  <- ZIO.foreach(resource)(toKnoraProjectNew).orElse(ZIO.none)
    } yield project

  private def findOneByShortname(shortname: Shortname): Task[Option[KnoraProject]] =
    for {
      model    <- triplestore.queryRdfModel(getProjectByShortname(shortname))
      resource <- model.getResourceByPropertyStringValue(ProjectShortname, shortname.value)
      project  <- ZIO.foreach(resource)(toKnoraProjectNew).orElse(ZIO.none)
    } yield project

  private def toKnoraProjectNew(resource: RdfResource): IO[RdfError, KnoraProject] =
    for {
      iri         <- resource.getSubjectIri
      shortcode   <- resource.getStringLiteralOrFail[Shortcode](ProjectShortcode)
      shortname   <- resource.getStringLiteralOrFail[Shortname](ProjectShortname)
      longname    <- resource.getStringLiteral[Longname](ProjectLongname)
      description <- resource.getLangStringLiteralsOrFail[Description](ProjectDescription)
      keywords    <- resource.getStringLiterals[Keyword](ProjectKeyword)
      logo        <- resource.getStringLiteral[Logo](ProjectLogo)
      status      <- resource.getBooleanLiteralOrFail[Status](StatusProp)
      selfjoin    <- resource.getBooleanLiteralOrFail[SelfJoin](HasSelfJoinEnabled)
      ontologies  <- resource.getObjectIris(belongsToOntology)
    } yield KnoraProject(
      id = ProjectIri.unsafeFrom(iri.value),
      shortcode = shortcode,
      shortname = shortname,
      longname = longname,
      description = description,
      keywords = keywords.toList.sortBy(_.value),
      logo = logo,
      status = status,
      selfjoin = selfjoin,
      ontologies = ontologies.toList
    )

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
