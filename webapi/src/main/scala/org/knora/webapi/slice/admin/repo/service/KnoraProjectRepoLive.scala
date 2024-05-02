/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.repo.service

import org.eclipse.rdf4j.common.net.ParsedIRI
import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.sparqlbuilder.graphpattern.TriplePattern
import org.eclipse.rdf4j.sparqlbuilder.rdf.Iri
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf
import zio._

import org.knora.webapi.messages.OntologyConstants.KnoraAdmin
import org.knora.webapi.messages.OntologyConstants.KnoraAdmin._
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.KnoraProject._
import org.knora.webapi.slice.admin.domain.model.RestrictedView
import org.knora.webapi.slice.admin.domain.service.KnoraProjectRepo
import org.knora.webapi.slice.admin.repo.rdf.RdfConversions._
import org.knora.webapi.slice.admin.repo.rdf.Vocabulary
import org.knora.webapi.slice.common.repo.rdf.Errors.RdfError
import org.knora.webapi.slice.common.repo.rdf.RdfResource
import org.knora.webapi.store.triplestore.api.TriplestoreService

final case class KnoraProjectRepoLive(
  private val triplestore: TriplestoreService,
  private val mapper: RdfEntityMapper[KnoraProject],
  private val cache: EntityCache[ProjectIri, KnoraProject],
) extends CachingEntityRepo[KnoraProject, ProjectIri](triplestore, mapper, cache)
    with KnoraProjectRepo {

  override protected def resourceClass: ParsedIRI = ParsedIRI.create(KnoraAdmin.KnoraProject)
  override protected def namedGraphIri: Iri       = Vocabulary.NamedGraphs.dataAdmin

  override protected def entityProperties: EntityProperties = EntityProperties(
    NonEmptyChunk(
      Vocabulary.KnoraAdmin.hasSelfJoinEnabled,
      Vocabulary.KnoraAdmin.projectDescription,
      Vocabulary.KnoraAdmin.projectShortcode,
      Vocabulary.KnoraAdmin.projectShortname,
      Vocabulary.KnoraAdmin.status,
    ),
    Chunk(
      Vocabulary.KnoraAdmin.projectKeyword,
      Vocabulary.KnoraAdmin.projectLogo,
      Vocabulary.KnoraAdmin.projectLongname,
      Vocabulary.KnoraAdmin.projectRestrictedViewSize,
      Vocabulary.KnoraAdmin.projectRestrictedViewWatermark,
    ),
  )

  override def findById(id: ProjectIri): Task[Option[KnoraProject]] =
    super.findById(id).map(_.orElse(KnoraProjectRepo.builtIn.findOneBy(_.id == id)))

  override def findByShortcode(shortcode: Shortcode): Task[Option[KnoraProject]] =
    findOneByTriplePattern(_.has(Vocabulary.KnoraAdmin.projectShortcode, shortcode.value))
      .map(_.orElse(KnoraProjectRepo.builtIn.findOneBy(_.shortcode == shortcode)))

  override def findByShortname(shortname: Shortname): Task[Option[KnoraProject]] =
    findOneByTriplePattern(_.has(Vocabulary.KnoraAdmin.projectShortname, shortname.value))
      .map(_.orElse(KnoraProjectRepo.builtIn.findOneBy(_.shortname == shortname)))

  override def findAll(): Task[Chunk[KnoraProject]] = super.findAll().map(_ ++ KnoraProjectRepo.builtIn.all)

  override def save(project: KnoraProject): Task[KnoraProject] =
    ZIO
      .die(new IllegalArgumentException("Update not supported for built-in projects"))
      .when(project.id.isBuiltInProjectIri) *>
      super.save(project)
}

object KnoraProjectRepoLive {

  private val mapper = new RdfEntityMapper[KnoraProject] {

    def toEntity(resource: RdfResource): IO[RdfError, KnoraProject] = {
      def getRestrictedView =
        for {
          size <- resource.getStringLiteral[RestrictedView.Size](ProjectRestrictedViewSize)(RestrictedView.Size.from)
          watermark <- resource.getBooleanLiteral[RestrictedView.Watermark](ProjectRestrictedViewWatermark)(b =>
                         Right(RestrictedView.Watermark.from(b)),
                       )
        } yield size.orElse(watermark).getOrElse(RestrictedView.default)

      for {
        iri            <- resource.getSubjectIri
        shortcode      <- resource.getStringLiteralOrFail[Shortcode](ProjectShortcode)
        shortname      <- resource.getStringLiteralOrFail[Shortname](ProjectShortname)
        longname       <- resource.getStringLiteral[Longname](ProjectLongname)
        description    <- resource.getLangStringLiteralsOrFail[Description](ProjectDescription)
        keywords       <- resource.getStringLiterals[Keyword](ProjectKeyword)
        logo           <- resource.getStringLiteral[Logo](ProjectLogo)
        status         <- resource.getBooleanLiteralOrFail[Status](StatusProp)
        selfjoin       <- resource.getBooleanLiteralOrFail[SelfJoin](HasSelfJoinEnabled)
        restrictedView <- getRestrictedView
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
        restrictedView = restrictedView,
      )
    }

    def toTriples(project: KnoraProject): TriplePattern = {
      val pattern = Rdf
        .iri(project.id.value)
        .has(RDF.TYPE, Vocabulary.KnoraAdmin.KnoraProject)
        .andHas(Vocabulary.KnoraAdmin.projectShortname, project.shortname.value)
        .andHas(Vocabulary.KnoraAdmin.projectShortcode, project.shortcode.value)
        .andHas(Vocabulary.KnoraAdmin.status, project.status.value)
        .andHas(Vocabulary.KnoraAdmin.hasSelfJoinEnabled, project.selfjoin.value)
      project.longname.foreach(longname => pattern.andHas(Vocabulary.KnoraAdmin.projectLongname, longname.value))
      project.description.foreach(description =>
        pattern.andHas(Vocabulary.KnoraAdmin.projectDescription, description.value.toRdfLiteral),
      )
      project.keywords.foreach(keyword => pattern.andHas(Vocabulary.KnoraAdmin.projectKeyword, keyword.value))
      project.logo.foreach(logo => pattern.andHas(Vocabulary.KnoraAdmin.projectLogo, logo.value))

      project.restrictedView match {
        case RestrictedView.Size(size) =>
          pattern.andHas(Vocabulary.KnoraAdmin.projectRestrictedViewSize, size)
        case RestrictedView.Watermark(watermark) =>
          pattern.andHas(Vocabulary.KnoraAdmin.projectRestrictedViewWatermark, watermark)
      }
      pattern
    }
  }

  val layer = (ZLayer.succeed(mapper) >+> EntityCache.layer[ProjectIri, KnoraProject]("knoraProject")) >>> ZLayer
    .derive[KnoraProjectRepoLive]
}
