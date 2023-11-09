/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.repo.service

import play.twirl.api.TxtFormat
import zio._

import dsp.valueobjects.RestrictedViewSize
import dsp.valueobjects.V2
import org.knora.webapi.messages.OntologyConstants.KnoraAdmin._
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM
import org.knora.webapi.messages.store.triplestoremessages.BooleanLiteralV2
import org.knora.webapi.messages.store.triplestoremessages.IriLiteralV2
import org.knora.webapi.messages.store.triplestoremessages.SparqlExtendedConstructResponse.ConstructPredicateObjects
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.messages.store.triplestoremessages.SubjectV2
import org.knora.webapi.messages.twirl.queries.sparql
import org.knora.webapi.slice.admin.domain.model.KnoraProject
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
      shortname <- mapper
                     .getSingleOrFail[StringLiteralV2](ProjectShortname, propsMap)
                     .flatMap(it => KnoraProject.Shortname.make(it.value).toZIO)
      shortcode <- mapper
                     .getSingleOrFail[StringLiteralV2](ProjectShortcode, propsMap)
                     .flatMap(it => KnoraProject.Shortcode.make(it.value).toZIO)
      longname <- mapper
                    .getSingleOption[StringLiteralV2](ProjectLongname, propsMap)
                    .flatMap(it => ZIO.foreach(it)(it => KnoraProject.Longname.make(it.value).toZIO))
      description <- mapper
                       .getNonEmptyChunkOrFail[StringLiteralV2](ProjectDescription, propsMap)
                       .map(_.map(it => V2.StringLiteralV2(it.value, it.language)))
      keywords <- mapper.getList[StringLiteralV2](ProjectKeyword, propsMap).map(_.map(_.value).sorted)
      logo     <- mapper.getSingleOption[StringLiteralV2](ProjectLogo, propsMap).map(_.map(_.value))
      status   <- mapper.getSingleOrFail[BooleanLiteralV2](Status, propsMap).map(_.value)
      selfjoin <- mapper.getSingleOrFail[BooleanLiteralV2](HasSelfJoinEnabled, propsMap).map(_.value)
      ontologies <-
        mapper
          .getListOption[IriLiteralV2]("http://www.knora.org/ontology/knora-admin#belongsToOntology", propsMap)
          .map(_.getOrElse(List.empty).map(literal => InternalIri(literal.value)))
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
