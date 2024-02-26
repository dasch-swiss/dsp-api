/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.repo.service

import org.eclipse.rdf4j.model.vocabulary.OWL
import org.eclipse.rdf4j.sparqlbuilder.core.SparqlBuilder.`var` as variable
import org.eclipse.rdf4j.sparqlbuilder.core.query.Queries
import org.eclipse.rdf4j.sparqlbuilder.graphpattern.GraphPatterns.tp
import zio.*

import dsp.errors.InconsistentRepositoryDataException
import org.knora.webapi.messages.OntologyConstants.KnoraAdmin.*
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.KnoraProject.*
import org.knora.webapi.slice.admin.domain.model.RestrictedView
import org.knora.webapi.slice.admin.domain.model.RestrictedViewSize
import org.knora.webapi.slice.admin.domain.service.KnoraProjectRepo
import org.knora.webapi.slice.admin.repo.rdf.RdfConversions.*
import org.knora.webapi.slice.admin.repo.rdf.Vocabulary
import org.knora.webapi.slice.admin.repo.service.KnoraProjectRepoLive.ProjectQueries
import org.knora.webapi.slice.common.repo.rdf.Errors.RdfError
import org.knora.webapi.slice.common.repo.rdf.RdfResource
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Construct
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update

final case class KnoraProjectRepoLive(
  private val triplestore: TriplestoreService
) extends KnoraProjectRepo {

  override def findAll(): Task[List[KnoraProject]] =
    for {
      model     <- triplestore.queryRdfModel(ProjectQueries.findAll)
      resources <- model.getSubjectResources
      projects <- ZIO.foreach(resources)(res =>
                    toKnoraProject(res).orElseFail(
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
      model    <- triplestore.queryRdfModel(ProjectQueries.findOneByIri(iri))
      resource <- model.getResource(iri.value)
      project  <- ZIO.foreach(resource)(toKnoraProject).orElse(ZIO.none)
    } yield project

  private def findOneByShortcode(shortcode: Shortcode): Task[Option[KnoraProject]] =
    for {
      model    <- triplestore.queryRdfModel(ProjectQueries.findOneByShortcode(shortcode))
      resource <- model.getResourceByPropertyStringValue(ProjectShortcode, shortcode.value)
      project  <- ZIO.foreach(resource)(toKnoraProject).orElse(ZIO.none)
    } yield project

  private def findOneByShortname(shortname: Shortname): Task[Option[KnoraProject]] =
    for {
      model    <- triplestore.queryRdfModel(ProjectQueries.findOneByShortname(shortname))
      resource <- model.getResourceByPropertyStringValue(ProjectShortname, shortname.value)
      project  <- ZIO.foreach(resource)(toKnoraProject).orElse(ZIO.none)
    } yield project

  private def toKnoraProject(resource: RdfResource): IO[RdfError, KnoraProject] =
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
    } yield KnoraProject(
      id = ProjectIri.unsafeFrom(iri.value),
      shortcode = shortcode,
      shortname = shortname,
      longname = longname,
      description = description,
      keywords = keywords.toList.sortBy(_.value),
      logo = logo,
      status = status,
      selfjoin = selfjoin
    )

  override def setProjectRestrictedView(
    project: KnoraProject,
    settings: RestrictedView
  ): Task[Unit] =
    triplestore.query(Update(ProjectQueries.setProjectRestrictedView(project.id, settings.size, settings.watermark)))

}

object KnoraProjectRepoLive {

  private object ProjectQueries {

    def findOneByIri(iri: ProjectIri): Construct =
      Construct(
        s"""|PREFIX knora-admin: <http://www.knora.org/ontology/knora-admin#>
            |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
            |PREFIX owl: <http://www.w3.org/2002/07/owl#>
            |CONSTRUCT {
            |  ?project ?p ?o .
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

    def findOneByShortcode(shortcode: Shortcode): Construct =
      Construct(
        s"""|PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
            |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
            |PREFIX knora-admin: <http://www.knora.org/ontology/knora-admin#>
            |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
            |PREFIX owl: <http://www.w3.org/2002/07/owl#>
            |CONSTRUCT {
            |  ?project ?p ?o .
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

    def findOneByShortname(shortname: Shortname): Construct =
      Construct(
        s"""|PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
            |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
            |PREFIX knora-admin: <http://www.knora.org/ontology/knora-admin#>
            |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
            |PREFIX owl: <http://www.w3.org/2002/07/owl#>
            |CONSTRUCT {
            |  ?project ?p ?o .
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

    def findAll: Construct = {
      val (project, p, o, ontology) = (variable("project"), variable("p"), variable("o"), variable("ontology"))
      def projectPo                 = tp(project, p, o)
      val query =
        Queries
          .CONSTRUCT(projectPo)
          .prefix(Vocabulary.KnoraAdmin.NS, Vocabulary.KnoraBase.NS, OWL.NS)
          .where(
            project
              .isA(Vocabulary.KnoraAdmin.KnoraProject)
              .and(ontology.isA(OWL.ONTOLOGY).andHas(Vocabulary.KnoraBase.attachedToProject, project).optional)
              .and(projectPo)
          )
      Construct(query.getQueryString)
    }

    def setProjectRestrictedView(projectIri: ProjectIri, size: RestrictedViewSize, watermark: Boolean): String =
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

  val layer = ZLayer.derive[KnoraProjectRepoLive]
}
