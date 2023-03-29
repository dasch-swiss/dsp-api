/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.repo.service

import play.twirl.api.TxtFormat
import zio._

import dsp.valueobjects.V2
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
import org.knora.webapi.messages.store.triplestoremessages.SparqlExtendedConstructResponse.ConstructPredicateObjects
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.messages.store.triplestoremessages.SubjectV2
import org.knora.webapi.messages.twirl.queries.sparql.admin.txt.getProjects
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.service.KnoraProjectRepo
import org.knora.webapi.slice.common.service.PredicateObjectMapper
import org.knora.webapi.slice.ontology.domain.service.OntologyRepo
import org.knora.webapi.slice.resourceinfo.domain.InternalIri
import org.knora.webapi.store.triplestore.api.TriplestoreService

final case class KnoraProjectRepoLive(
  private val triplestore: TriplestoreService,
  private val mapper: PredicateObjectMapper
) extends KnoraProjectRepo {

  override def findById(id: InternalIri): Task[Option[KnoraProject]] =
    findOneByQuery(getProjects(maybeIri = Some(id.value), None, None))

  override def findById(id: ProjectIdentifierADM): Task[Option[KnoraProject]] = {
    val maybeIri       = id.asIriIdentifierOption
    val maybeShortname = id.asShortnameIdentifierOption
    val maybeShortCode = id.asShortcodeIdentifierOption
    findOneByQuery(getProjects(maybeIri = maybeIri, maybeShortname = maybeShortname, maybeShortcode = maybeShortCode))
  }

  private def findOneByQuery(query: TxtFormat.Appendable): Task[Option[KnoraProject]] =
    for {
      construct <- triplestore.sparqlHttpExtendedConstruct(query).map(_.statements.headOption)
      project   <- ZIO.foreach(construct)(toKnoraProject)
    } yield project

  override def findAll(): Task[List[KnoraProject]] = {
    val query = getProjects(None, None, None)
    for {
      projectsResponse <- triplestore.sparqlHttpExtendedConstruct(query).map(_.statements.toList)
      projects         <- ZIO.foreach(projectsResponse)(toKnoraProject)
    } yield projects
  }

  private def toKnoraProject(subjectPropsTuple: (SubjectV2, ConstructPredicateObjects)): Task[KnoraProject] = {
    val projectIri = InternalIri(subjectPropsTuple._1.toString)
    val propsMap   = subjectPropsTuple._2
    for {
      shortname <- mapper.getSingleOrFail[StringLiteralV2](ProjectShortname, propsMap).map(_.value)
      shortcode <- mapper.getSingleOrFail[StringLiteralV2](ProjectShortcode, propsMap).map(_.value)
      longname  <- mapper.getSingleOption[StringLiteralV2](ProjectLongname, propsMap).map(_.map(_.value))
      description <- mapper
                       .getListOrFail[StringLiteralV2](ProjectDescription, propsMap)
                       .map(_.map(desc => V2.StringLiteralV2(desc.value, desc.language)))
      keywords <- mapper.getList[StringLiteralV2](ProjectKeyword, propsMap).map(_.map(_.value).sorted)
      logo     <- mapper.getSingleOption[StringLiteralV2](ProjectLogo, propsMap).map(_.map(_.value))
      status   <- mapper.getSingleOrFail[BooleanLiteralV2](Status, propsMap).map(_.value)
      selfjoin <- mapper.getSingleOrFail[BooleanLiteralV2](HasSelfJoinEnabled, propsMap).map(_.value)
    } yield KnoraProject(projectIri, shortname, shortcode, longname, description, keywords, logo, status, selfjoin)
  }
}

object KnoraProjectRepoLive {
  val layer: URLayer[TriplestoreService with OntologyRepo with PredicateObjectMapper, KnoraProjectRepoLive] =
    ZLayer.fromFunction(KnoraProjectRepoLive.apply _)
}
