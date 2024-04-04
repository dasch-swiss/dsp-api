/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.model

import zio.Chunk
import zio.ZIO
import zio.test.ZIOSpecDefault
import zio.test.assertTrue

import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.slice.admin.AdminConstants.permissionsDataNamedGraph
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.service.KnoraGroupRepo
import org.knora.webapi.slice.admin.repo.service.AdministrativePermissionRepoLive
import org.knora.webapi.slice.resourceinfo.domain.InternalIri
import org.knora.webapi.store.triplestore.api.TestTripleStore
import org.knora.webapi.store.triplestore.api.TriplestoreServiceInMemory

object AdministrativePermissionRepoSpec extends ZIOSpecDefault {

  private val permissionIri: PermissionIri  = PermissionIri.unsafeFrom("http://rdfh.ch/permissions/0001/1234567890")
  private val permissionIri2: PermissionIri = PermissionIri.unsafeFrom("http://rdfh.ch/permissions/0001/0987654321")
  private val AdministrativePermissionRepo  = ZIO.serviceWithZIO[AdministrativePermissionRepo]

  private val findById = suite("findById")(
    test("should return None if the permission does not exist") {
      for {
        nonFound <- AdministrativePermissionRepo(_.findById(permissionIri))
      } yield assertTrue(nonFound.isEmpty)
    },
    test("should find simple AdministrativePermission if it exists") {
      for {
        _ <- TestTripleStore
               .setDatasetFromTriG(
                 s"""
                    | @prefix knora-base: <http://www.knora.org/ontology/knora-base#> .
                    | @prefix knora-admin: <http://www.knora.org/ontology/knora-admin#> .
                    | @prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
                    | @prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
                    |
                    | <${permissionsDataNamedGraph.value}> {
                    |   <${permissionIri.value}> a knora-admin:AdministrativePermission ;
                    |     knora-admin:forGroup <knora-admin:ProjectMember> ;
                    |     knora-admin:forProject <http://rdfh.ch/projects/0001> ;
                    |     knora-base:hasPermissions "ProjectAdminGroupAllPermission|ProjectResourceCreateAllPermission" .
                    |  }
                    |""".stripMargin,
               )
        found <- AdministrativePermissionRepo(_.findById(permissionIri))
      } yield assertTrue(
        found.contains(
          AdministrativePermission(
            permissionIri,
            KnoraGroupRepo.builtIn.ProjectMember.id,
            ProjectIri.unsafeFrom("http://rdfh.ch/projects/0001"),
            Chunk(
              AdministrativePermissionPart.Simple.unsafeFrom(Permission.Administrative.ProjectAdminGroupAll),
              AdministrativePermissionPart.Simple.unsafeFrom(Permission.Administrative.ProjectResourceCreateAll),
            ),
          ),
        ),
      )
    },
    test("should find simple and restricted AdministrativePermission if the permission exists") {
      for {
        _ <-
          TestTripleStore
            .setDatasetFromTriG(
              s"""
                 | @prefix knora-base: <http://www.knora.org/ontology/knora-base#> .
                 | @prefix knora-admin: <http://www.knora.org/ontology/knora-admin#> .
                 | @prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
                 | @prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
                 |
                 | <${permissionsDataNamedGraph.value}> {
                 |   <${permissionIri.value}> a knora-admin:AdministrativePermission ;
                 |     knora-admin:forGroup <knora-admin:ProjectMember> ;
                 |     knora-admin:forProject <http://rdfh.ch/projects/0001> ;
                 |     knora-base:hasPermissions "ProjectAdminGroupAllPermission|ProjectResourceCreateRestrictedPermission http://www.knora.org/ontology/0001/example#person,http://www.knora.org/ontology/0001/example#event" .
                 |  }
                 |""".stripMargin,
            )
        found <- AdministrativePermissionRepo(_.findById(permissionIri))
      } yield assertTrue(
        found.contains(
          AdministrativePermission(
            permissionIri,
            KnoraGroupRepo.builtIn.ProjectMember.id,
            ProjectIri.unsafeFrom("http://rdfh.ch/projects/0001"),
            Chunk(
              AdministrativePermissionPart.Simple.unsafeFrom(Permission.Administrative.ProjectAdminGroupAll),
              AdministrativePermissionPart.ResourceCreateRestricted(
                Chunk(
                  InternalIri("http://www.knora.org/ontology/0001/example#person"),
                  InternalIri("http://www.knora.org/ontology/0001/example#event"),
                ),
              ),
            ),
          ),
        ),
      )
    },
  )

  private val findAll = suite("findAll")(
    test("should find none if no permissions exist") {
      for {
        found <- AdministrativePermissionRepo(_.findAll())
      } yield assertTrue(found.isEmpty)
    },
    test("should find all") {
      for {
        _ <-
          TestTripleStore
            .setDatasetFromTriG(
              s"""
                 | @prefix knora-base: <http://www.knora.org/ontology/knora-base#> .
                 | @prefix knora-admin: <http://www.knora.org/ontology/knora-admin#> .
                 | @prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
                 | @prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
                 |
                 | <${permissionsDataNamedGraph.value}> {
                 |   <${permissionIri.value}> a knora-admin:AdministrativePermission ;
                 |     knora-admin:forGroup <knora-admin:ProjectMember> ;
                 |     knora-admin:forProject <http://rdfh.ch/projects/0001> ;
                 |     knora-base:hasPermissions "ProjectAdminGroupAllPermission|ProjectResourceCreateAllPermission" .
                 |
                 |   <${permissionIri2.value}> a knora-admin:AdministrativePermission ;
                 |     knora-admin:forGroup <knora-admin:ProjectMember> ;
                 |     knora-admin:forProject <http://rdfh.ch/projects/0001> ;
                 |     knora-base:hasPermissions "ProjectAdminGroupAllPermission|ProjectResourceCreateRestrictedPermission http://www.knora.org/ontology/0001/example#person,http://www.knora.org/ontology/0001/example#event" .
                 |  }
                 |""".stripMargin,
            )
        found <- AdministrativePermissionRepo(_.findAll())
      } yield assertTrue(
        found.sortBy(_.id.value) ==
          List(
            AdministrativePermission(
              permissionIri,
              KnoraGroupRepo.builtIn.ProjectMember.id,
              ProjectIri.unsafeFrom("http://rdfh.ch/projects/0001"),
              Chunk(
                AdministrativePermissionPart.Simple.unsafeFrom(Permission.Administrative.ProjectAdminGroupAll),
                AdministrativePermissionPart.Simple.unsafeFrom(Permission.Administrative.ProjectResourceCreateAll),
              ),
            ),
            AdministrativePermission(
              permissionIri2,
              KnoraGroupRepo.builtIn.ProjectMember.id,
              ProjectIri.unsafeFrom("http://rdfh.ch/projects/0001"),
              Chunk(
                AdministrativePermissionPart.Simple.unsafeFrom(Permission.Administrative.ProjectAdminGroupAll),
                AdministrativePermissionPart.ResourceCreateRestricted(
                  Chunk(
                    InternalIri("http://www.knora.org/ontology/0001/example#person"),
                    InternalIri("http://www.knora.org/ontology/0001/example#event"),
                  ),
                ),
              ),
            ),
          ).sortBy(_.id.value),
      )
    },
  )

  private val save = suite("save")(
    test("should save a new permission") {
      val id =
        PermissionIri.unsafeFrom("http://rdfh.ch/permissions/0001/1234567890")
      val permission = AdministrativePermission(
        id,
        KnoraGroupRepo.builtIn.ProjectMember.id,
        ProjectIri.unsafeFrom("http://rdfh.ch/projects/0001"),
        Chunk(
          AdministrativePermissionPart.Simple.unsafeFrom(Permission.Administrative.ProjectAdminGroupAll),
          AdministrativePermissionPart.ResourceCreateRestricted(
            Chunk(
              InternalIri("http://www.knora.org/ontology/0001/example#person"),
              InternalIri("http://www.knora.org/ontology/0001/example#event"),
            ),
          ),
        ),
      )
      for {
        _     <- AdministrativePermissionRepo(_.save(permission))
        found <- AdministrativePermissionRepo(_.findById(id))
      } yield assertTrue(found.contains(permission))
    },
  )

  val spec = suite("ProjectAdminPermissionRepo")(findById, findAll, save)
    .provide(AdministrativePermissionRepoLive.layer, TriplestoreServiceInMemory.emptyLayer, StringFormatter.test)
}
