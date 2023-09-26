/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.repo.service

import play.twirl.api.TxtFormat
import zio._

import dsp.valueobjects.Project
import dsp.valueobjects.RestrictedViewSize
import dsp.valueobjects.V2
import org.knora.webapi.messages.OntologyConstants.KnoraAdmin.HasSelfJoinEnabled
import org.knora.webapi.messages.OntologyConstants.KnoraAdmin.ProjectDescription
import org.knora.webapi.messages.OntologyConstants.KnoraAdmin.ProjectKeyword
import org.knora.webapi.messages.OntologyConstants.KnoraAdmin.ProjectLogo
import org.knora.webapi.messages.OntologyConstants.KnoraAdmin.ProjectLongname
import org.knora.webapi.messages.OntologyConstants.KnoraAdmin.ProjectShortcode
import org.knora.webapi.messages.OntologyConstants.KnoraAdmin.ProjectShortname
import org.knora.webapi.messages.OntologyConstants.KnoraAdmin.Status
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM
import org.knora.webapi.messages.store.triplestoremessages.BooleanLiteralV2
import org.knora.webapi.messages.store.triplestoremessages.SparqlExtendedConstructResponse.ConstructPredicateObjects
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.messages.store.triplestoremessages.SubjectV2
import org.knora.webapi.messages.twirl.queries.sparql
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.service.KnoraProjectRepo
import org.knora.webapi.slice.common.repo.service.PredicateObjectMapper
import org.knora.webapi.slice.ontology.domain.service.OntologyRepo
import org.knora.webapi.slice.resourceinfo.domain.InternalIri
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Construct
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Select
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update

final case class KnoraProjectRepoLive(
  private val triplestore: TriplestoreService,
  private val mapper: PredicateObjectMapper
) extends KnoraProjectRepo {
  implicit val sf: StringFormatter = StringFormatter.getGeneralInstance

  override def findById(id: InternalIri): Task[Option[KnoraProject]] =
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
      construct <- triplestore.query(Construct(query)).flatMap(_.asExtended).map(_.statements.headOption)
      project   <- ZIO.foreach(construct)(toKnoraProject)
    } yield project

  override def findAll(): Task[List[KnoraProject]] = {
    val query = sparql.admin.txt.getProjects(None, None, None)
    for {
      projectsResponse <- triplestore.query(Construct(query)).flatMap(_.asExtended).map(_.statements.toList)
      projects         <- ZIO.foreach(projectsResponse)(toKnoraProject)
    } yield projects
  }

  private def toKnoraProject(subjectPropsTuple: (SubjectV2, ConstructPredicateObjects)): Task[KnoraProject] = {
    val projectIri = InternalIri(subjectPropsTuple._1.toString)
    val propsMap   = subjectPropsTuple._2
    for {
      shortname <- mapper.getSingleOrFail[StringLiteralV2](ProjectShortname, propsMap).map(_.value)
      shortcode <- mapper
                     .getSingleOrFail[StringLiteralV2](ProjectShortcode, propsMap)
                     .flatMap(it => Project.Shortcode.make(it.value).toZIO)
      longname <- mapper.getSingleOption[StringLiteralV2](ProjectLongname, propsMap).map(_.map(_.value))
      description <- mapper
                       .getNonEmptyChunkOrFail[StringLiteralV2](ProjectDescription, propsMap)
                       .map(_.map(it => V2.StringLiteralV2(it.value, it.language)))
      keywords <- mapper.getList[StringLiteralV2](ProjectKeyword, propsMap).map(_.map(_.value).sorted)
      logo     <- mapper.getSingleOption[StringLiteralV2](ProjectLogo, propsMap).map(_.map(_.value))
      status   <- mapper.getSingleOrFail[BooleanLiteralV2](Status, propsMap).map(_.value)
      selfjoin <- mapper.getSingleOrFail[BooleanLiteralV2](HasSelfJoinEnabled, propsMap).map(_.value)
    } yield KnoraProject(projectIri, shortname, shortcode, longname, description, keywords, logo, status, selfjoin)
  }

  override def findOntologies(project: KnoraProject): Task[List[InternalIri]] = {
    val query =
      s"""
         |PREFIX owl: <http://www.w3.org/2002/07/owl#>
         |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
         |PREFIX knora-admin: <http://www.knora.org/ontology/knora-admin#>  
         |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>  
         |
         |SELECT ?ontologyIri  WHERE {
         |  BIND(<${project.id.value}> AS ?projectIri)
         |  ?ontologyIri a owl:Ontology .
         |  ?ontologyIri knora-base:attachedToProject ?projectIri .
         |  ?projectIri  a knora-admin:knoraProject .
         |} order by ?projectIri""".stripMargin
    triplestore
      .query(Select(query))
      .map(_.results.bindings.flatMap(_.rowMap.get("ontologyIri")).map(InternalIri).toList)
  }

  override def setProjectRestrictedViewSize(
    project: KnoraProject,
    size: RestrictedViewSize
  ): Task[Unit] = {
    val query = sparql.admin.txt
      .setProjectRestrictedViewSettings(project.id.value, size.value, None)
    triplestore.query(Update(query.toString))
  }
}

object KnoraProjectRepoLive {
  val layer: URLayer[TriplestoreService with OntologyRepo with PredicateObjectMapper, KnoraProjectRepoLive] =
    ZLayer.fromFunction(KnoraProjectRepoLive.apply _)
}
