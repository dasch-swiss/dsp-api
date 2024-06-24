/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.repo.service

import org.eclipse.rdf4j.sparqlbuilder.core.SparqlBuilder.`var` as variable
import org.eclipse.rdf4j.sparqlbuilder.core.query.Queries
import org.eclipse.rdf4j.sparqlbuilder.core.query.SelectQuery
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf
import zio.Chunk
import zio.ZIO
import zio.test.*

import org.knora.webapi.messages.OntologyConstants.KnoraAdmin.KnoraAdminPrefixExpansion
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.slice.admin.domain.model.AdministrativePermission
import org.knora.webapi.slice.admin.domain.model.AdministrativePermissionPart
import org.knora.webapi.slice.admin.domain.model.AdministrativePermissionRepo
import org.knora.webapi.slice.admin.domain.model.GroupIri
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.admin.domain.model.Permission.Administrative.*
import org.knora.webapi.slice.admin.domain.model.PermissionIri
import org.knora.webapi.slice.admin.repo.rdf.Vocabulary
import org.knora.webapi.slice.resourceinfo.domain.InternalIri
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreServiceInMemory

object AdministrativePermissionRepoLiveSpec extends ZIOSpecDefault {
  private val repo       = ZIO.serviceWithZIO[AdministrativePermissionRepo]
  private val db         = ZIO.serviceWithZIO[TriplestoreService]
  private val shortcode  = Shortcode.unsafeFrom("0001")
  private val groupIri   = GroupIri.makeNew(shortcode)
  private val projectIri = ProjectIri.makeNew
  private def permission(permissions: Chunk[AdministrativePermissionPart]) =
    AdministrativePermission(PermissionIri.makeNew(shortcode), groupIri, projectIri, permissions)

  private val simpleAdminPermission = permission(
    Chunk(AdministrativePermissionPart.Simple.unsafeFrom(ProjectResourceCreateAll)),
  )
  private val complexAdminPermission = permission(
    Chunk(
      AdministrativePermissionPart.ProjectAdminGroupRestricted(Chunk(groupIri, GroupIri.makeNew(shortcode))),
      AdministrativePermissionPart.ResourceCreateRestricted(
        Chunk(InternalIri("https://example.org/1"), InternalIri("https://example.org/2")),
      ),
    ),
  )

  val spec = suite("AdministrativePermissionRepoLive")(
    test("should save and find") {
      for {
        saved <- repo(_.save(simpleAdminPermission))
        found <- repo(_.findById(saved.id))
      } yield assertTrue(found.contains(saved), saved == simpleAdminPermission)
    },
    test("should handle complex permission parts") {
      val expected = complexAdminPermission
      for {
        saved <- repo(_.save(expected))
        found <- repo(_.findById(saved.id))
      } yield assertTrue(found.contains(saved), saved == expected)
    },
    test("should delete") {
      val expected = complexAdminPermission
      for {
        saved               <- repo(_.save(expected))
        foundAfterSave      <- repo(_.findById(saved.id)).map(_.nonEmpty)
        _                   <- repo(_.delete(expected))
        notfoundAfterDelete <- repo(_.findById(saved.id)).map(_.isEmpty)
      } yield assertTrue(foundAfterSave, notfoundAfterDelete)
    },
    test("should write valid permission literal with the 'knora-admin:' prefix") {
      val expected = permission(
        Chunk(
          AdministrativePermissionPart.Simple.unsafeFrom(ProjectResourceCreateAll),
          AdministrativePermissionPart.ProjectAdminGroupRestricted(
            Chunk(
              GroupIri.unsafeFrom(KnoraAdminPrefixExpansion + "Creator"),
              GroupIri.unsafeFrom(KnoraAdminPrefixExpansion + "UnknownUser"),
              groupIri,
            ),
          ),
        ),
      )
      def query(id: PermissionIri): SelectQuery = {
        val hasPermissionsLiteral = variable("lit")
        Queries
          .SELECT()
          .select(hasPermissionsLiteral)
          .where(Rdf.iri(id.value).has(Vocabulary.KnoraBase.hasPermissions, hasPermissionsLiteral))
      }
      for {
        saved <- repo(_.save(expected))
        res   <- db(_.select(query(saved.id)))
      } yield assertTrue(
        res
          .getFirst("lit")
          .head == s"ProjectResourceCreateAllPermission|ProjectAdminGroupRestrictedPermission knora-admin:Creator,knora-admin:UnknownUser,${groupIri.value}",
      )
    },
  ).provide(AdministrativePermissionRepoLive.layer, TriplestoreServiceInMemory.emptyLayer, StringFormatter.test)
}
