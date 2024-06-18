package org.knora.webapi.slice.admin.repo.service

import org.eclipse.rdf4j.sparqlbuilder.core.SparqlBuilder.`var` as variable
import org.eclipse.rdf4j.sparqlbuilder.core.query.Queries
import org.eclipse.rdf4j.sparqlbuilder.core.query.SelectQuery
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf
import zio.Chunk
import zio.NonEmptyChunk
import zio.ZIO
import zio.test.*

import org.knora.webapi.messages.OntologyConstants.KnoraAdmin.KnoraAdminPrefixExpansion
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.slice.admin.domain.model.DefaultObjectAccessPermission
import org.knora.webapi.slice.admin.domain.model.DefaultObjectAccessPermission.DefaultObjectAccessPermissionPart
import org.knora.webapi.slice.admin.domain.model.DefaultObjectAccessPermission.ForWhat
import org.knora.webapi.slice.admin.domain.model.DefaultObjectAccessPermissionRepo
import org.knora.webapi.slice.admin.domain.model.GroupIri
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.admin.domain.model.Permission.ObjectAccess.*
import org.knora.webapi.slice.admin.domain.model.PermissionIri
import org.knora.webapi.slice.admin.repo.rdf.Vocabulary
import org.knora.webapi.slice.resourceinfo.domain.InternalIri
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreServiceInMemory

object DefaultObjectAccessPermissionRepoLiveSpec extends ZIOSpecDefault {
  private val repo       = ZIO.serviceWithZIO[DefaultObjectAccessPermissionRepo]
  private val db         = ZIO.serviceWithZIO[TriplestoreService]
  private val shortcode  = Shortcode.unsafeFrom("0001")
  private val groupIri   = GroupIri.makeNew(shortcode)
  private val projectIri = ProjectIri.makeNew

  private def permission(forWhat: ForWhat, permissions: Chunk[DefaultObjectAccessPermissionPart]) =
    DefaultObjectAccessPermission(PermissionIri.makeNew(shortcode), projectIri, forWhat, permissions)

  private val expected = permission(
    ForWhat.ResourceClassAndProperty(InternalIri("https://example.com/rc"), InternalIri("https://example.com/p")),
    Chunk(
      DefaultObjectAccessPermissionPart(RestrictedView, NonEmptyChunk(groupIri)),
      DefaultObjectAccessPermissionPart(View, NonEmptyChunk(GroupIri.makeNew(shortcode), GroupIri.makeNew(shortcode))),
    ),
  )

  val spec = suite("AdministrativePermissionRepoLive")(
    test("should save and find") {
      for {
        saved <- repo(_.save(expected))
        found <- repo(_.findById(saved.id))
      } yield assertTrue(found.contains(saved), saved == expected)
    },
    test("should delete") {
      for {
        saved               <- repo(_.save(expected))
        foundAfterSave      <- repo(_.findById(saved.id)).map(_.nonEmpty)
        _                   <- repo(_.delete(expected))
        notfoundAfterDelete <- repo(_.findById(saved.id)).map(_.isEmpty)
      } yield assertTrue(foundAfterSave, notfoundAfterDelete)
    },
    test("should write valid permission literal with the 'knora-admin:' prefix") {
      val expected = permission(
        ForWhat.Group(groupIri),
        Chunk(
          DefaultObjectAccessPermissionPart(RestrictedView, NonEmptyChunk(groupIri)),
          DefaultObjectAccessPermissionPart(
            View,
            NonEmptyChunk(
              GroupIri.unsafeFrom(KnoraAdminPrefixExpansion + "Creator"),
              GroupIri.unsafeFrom(KnoraAdminPrefixExpansion + "UnknownUser"),
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
          .head == s"RV ${groupIri.value}|V knora-admin:Creator,knora-admin:UnknownUser",
      )
    },
  ).provide(DefaultObjectAccessPermissionRepoLive.layer, TriplestoreServiceInMemory.emptyLayer, StringFormatter.test)
}
