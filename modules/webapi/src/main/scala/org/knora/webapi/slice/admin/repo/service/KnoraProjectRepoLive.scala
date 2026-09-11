/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.repo.service

import zio.*

import org.knora.sparqlbuilder.*
import org.knora.webapi.messages.OntologyConstants.KnoraAdmin
import org.knora.webapi.messages.OntologyConstants.KnoraAdmin.*
import org.knora.webapi.messages.store.triplestoremessages.LanguageTaggedStringLiteralV2
import org.knora.webapi.messages.store.triplestoremessages.PlainStringLiteralV2
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.slice.admin.AdminConstants.adminDataNamedGraph
import org.knora.webapi.slice.admin.domain.model.Authorship
import org.knora.webapi.slice.admin.domain.model.CopyrightHolder
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.KnoraProject.*
import org.knora.webapi.slice.admin.domain.model.LicenseIri
import org.knora.webapi.slice.admin.domain.model.RestrictedView
import org.knora.webapi.slice.admin.domain.service.KnoraProjectRepo
import org.knora.webapi.slice.admin.repo.rdf.RdfConversions.*
import org.knora.webapi.slice.common.repo.rdf.Errors.RdfError
import org.knora.webapi.slice.common.repo.rdf.RdfResource
import org.knora.webapi.store.triplestore.api.TriplestoreService

final case class KnoraProjectRepoLive(
  private val triplestore: TriplestoreService,
  private val mapper: RdfEntityMapper[KnoraProject],
  private val cache: EntityCache[ProjectIri, KnoraProject],
) extends CachingEntityRepo[KnoraProject, ProjectIri](triplestore, mapper, cache)
    with KnoraProjectRepo {

  override protected def resourceClass: Iri = Iri.unsafeFrom(KnoraAdmin.KnoraProject)
  override protected def namedGraphIri: Iri = Iri.unsafeFrom(adminDataNamedGraph.value)

  override protected def entityProperties: EntityProperties = EntityProperties(
    NonEmptyChunk(
      Iri.unsafeFrom(HasSelfJoinEnabled),
      Iri.unsafeFrom(ProjectDescription),
      Iri.unsafeFrom(ProjectShortcode),
      Iri.unsafeFrom(ProjectShortname),
    ),
    Chunk(
      Iri.unsafeFrom(ProjectKeyword),
      Iri.unsafeFrom(ProjectLogo),
      Iri.unsafeFrom(ProjectLongname),
      Iri.unsafeFrom(ProjectRestrictedViewSize),
      Iri.unsafeFrom(ProjectRestrictedViewWatermark),
      Iri.unsafeFrom(hasAllowedCopyrightHolder),
      Iri.unsafeFrom(hasEnabledLicense),
      Iri.unsafeFrom(hasDataLicense),
      Iri.unsafeFrom(hasDataCopyrightHolder),
      Iri.unsafeFrom(hasDefaultDataAuthorship),
    ),
  )

  override def findById(id: ProjectIri): Task[Option[KnoraProject]] =
    super.findById(id).map(_.orElse(KnoraProjectRepo.builtIn.findOneBy(_.id == id)))

  override def findByShortcode(shortcode: Shortcode): Task[Option[KnoraProject]] =
    findOneByPattern(sparql"$s knora-admin:projectShortcode ${Literal.string(shortcode.value)} .")
      .map(_.orElse(KnoraProjectRepo.builtIn.findOneBy(_.shortcode == shortcode)))

  override def findByShortname(shortname: Shortname): Task[Option[KnoraProject]] =
    findOneByPattern(sparql"$s knora-admin:projectShortname ${Literal.string(shortname.value)} .")
      .map(_.orElse(KnoraProjectRepo.builtIn.findOneBy(_.shortname == shortname)))

  override def findAll(): Task[Chunk[KnoraProject]] = super.findAll().map(_ ++ KnoraProjectRepo.builtIn.all)

  override def save(project: KnoraProject): Task[KnoraProject] =
    ZIO
      .die(new IllegalArgumentException("Update not supported for built-in projects"))
      .when(project.id.isBuiltInProjectIri) *>
      super.save(project)

  override def delete(project: KnoraProject): Task[Unit] =
    ZIO
      .die(new IllegalArgumentException("Erase not supported for built-in projects"))
      .when(project.id.isBuiltInProjectIri) *>
      super.delete(project)
}

object KnoraProjectRepoLive {

  private def toLiteral(literal: StringLiteralV2): Literal = literal match {
    case LanguageTaggedStringLiteralV2(value, lang) => Literal.langString(value, lang.value)
    case PlainStringLiteralV2(value)                => Literal.string(value)
  }

  private val mapper = new RdfEntityMapper[KnoraProject] {

    def toEntity(resource: RdfResource): IO[RdfError, KnoraProject] = {
      def getRestrictedView =
        for {
          size <-
            resource.getStringLiteral[RestrictedView.Size](ProjectRestrictedViewSize)(using RestrictedView.Size.from)
          watermark <- resource.getBooleanLiteral[RestrictedView.Watermark](ProjectRestrictedViewWatermark)(using
                         b => Right(RestrictedView.Watermark.from(b)),
                       )
        } yield size.orElse(watermark).getOrElse(RestrictedView.default)

      for {
        iri                     <- resource.getSubjectIri
        shortcode               <- resource.getStringLiteralOrFail[Shortcode](ProjectShortcode)
        shortname               <- resource.getStringLiteralOrFail[Shortname](ProjectShortname)
        longname                <- resource.getStringLiteral[Longname](ProjectLongname)
        description             <- resource.getLangStringLiteralsOrFail[Description](ProjectDescription)
        keywords                <- resource.getStringLiterals[Keyword](ProjectKeyword)
        logo                    <- resource.getStringLiteral[Logo](ProjectLogo)
        selfjoin                <- resource.getBooleanLiteralOrFail[SelfJoin](HasSelfJoinEnabled)
        allowedCopyrightHolders <-
          resource.getStringLiterals(hasAllowedCopyrightHolder)(using CopyrightHolder.from).map(_.toSet)
        enabledLicenses     <- resource.getObjectIrisConvert(hasEnabledLicense)(using LicenseIri.from).map(_.toSet)
        dataLicense         <- resource.getObjectIrisConvert(hasDataLicense)(using LicenseIri.from).map(_.headOption)
        dataCopyrightHolder <-
          resource.getStringLiterals(hasDataCopyrightHolder)(using CopyrightHolder.from).map(_.headOption)
        defaultDataAuthorship <-
          resource.getStringLiterals(hasDefaultDataAuthorship)(using Authorship.from).map(_.toList.sortBy(_.value))
        restrictedView <- getRestrictedView
      } yield KnoraProject(
        id = ProjectIri.unsafeFrom(iri.value),
        shortcode = shortcode,
        shortname = shortname,
        longname = longname,
        description = description,
        keywords = keywords.toList.sortBy(_.value),
        logo = logo,
        selfjoin = selfjoin,
        restrictedView = restrictedView,
        allowedCopyrightHolders = allowedCopyrightHolders,
        enabledLicenses = enabledLicenses,
        dataLicense = dataLicense,
        dataCopyrightHolder = dataCopyrightHolder,
        defaultDataAuthorship = defaultDataAuthorship,
      )
    }

    def toTriples(project: KnoraProject): Fragment = {
      val id = Iri.unsafeFrom(project.id.value)
      sparql"""|$id a knora-admin:knoraProject ;
               |  knora-admin:projectShortname ${Literal.string(project.shortname.value)} ;
               |  knora-admin:projectShortcode ${Literal.string(project.shortcode.value)} ;
               |  knora-admin:hasSelfJoinEnabled ${Literal.bool(project.selfjoin.value)} .
               |${project.longname.whenSome(longname =>
          sparql"$id knora-admin:projectLongname ${Literal.string(longname.value)} .",
        )}
               |${project.description.toChunk
          .map(d => sparql"$id knora-admin:projectDescription ${toLiteral(d.value)} .")
          .joinLines}
               |${project.keywords
          .map(keyword => sparql"$id knora-admin:projectKeyword ${Literal.string(keyword.value)} .")
          .joinLines}
               |${project.logo.whenSome(logo => sparql"$id knora-admin:projectLogo ${Literal.string(logo.value)} .")}
               |${project.restrictedView match {
          case RestrictedView.Size(size) =>
            sparql"$id knora-admin:projectRestrictedViewSize ${Literal.string(size)} ."
          case RestrictedView.Watermark(watermark) =>
            sparql"$id knora-admin:projectRestrictedViewWatermark ${Literal.bool(watermark)} ."
        }}
               |${project.allowedCopyrightHolders
          .map(holder => sparql"$id knora-admin:hasAllowedCopyrightHolder ${Literal.string(holder.value)} .")
          .joinLines}
               |${project.enabledLicenses
          .map(license => sparql"$id knora-admin:hasEnabledLicense ${Iri.unsafeFrom(license.value)} .")
          .joinLines}
               |${project.dataLicense.whenSome(license =>
          sparql"$id knora-admin:hasDataLicense ${Iri.unsafeFrom(license.value)} .",
        )}
               |${project.dataCopyrightHolder.whenSome(holder =>
          sparql"$id knora-admin:hasDataCopyrightHolder ${Literal.string(holder.value)} .",
        )}
               |${project.defaultDataAuthorship
          .map(authorship => sparql"$id knora-admin:hasDefaultDataAuthorship ${Literal.string(authorship.value)} .")
          .joinLines}"""
    }
  }

  val layer = (ZLayer.succeed(mapper) >+> EntityCache.layer[ProjectIri, KnoraProject]("knoraProject")) >>> ZLayer
    .derive[KnoraProjectRepoLive]
}
