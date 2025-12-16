/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.repo

import zio.test.*

import org.knora.webapi.slice.admin.domain.model.GroupIri
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.Permission
import org.knora.webapi.slice.admin.domain.model.PermissionIri

object CreateAdministrativePermissionQuerySpec extends ZIOSpecDefault {

  private val testPermissionIri = PermissionIri.unsafeFrom("http://rdfh.ch/permissions/0001/test-permission")
  private val testProjectIri    = ProjectIri.unsafeFrom("http://rdfh.ch/projects/0001")
  private val testGroupIri      = GroupIri.unsafeFrom("http://rdfh.ch/groups/0001/thing-searcher")

  override def spec: Spec[TestEnvironment, Any] = suite("CreateAdministrativePermissionQuerySpec")(
    test("should produce correct query with a single permission") {
      val permissions = Set[Permission.Administrative](
        Permission.Administrative.ProjectAdminAll,
      )

      val query = CreateAdministrativePermissionQuery.build(
        permissionIri = testPermissionIri,
        forProjectIri = testProjectIri,
        forGroupIri = testGroupIri,
        permissions = permissions,
      )

      assertTrue(
        query.getQueryString ==
          """PREFIX knora-admin: <http://www.knora.org/ontology/knora-admin#>
            |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
            |INSERT { GRAPH <http://www.knora.org/data/permissions> { <http://rdfh.ch/permissions/0001/test-permission> a knora-admin:AdministrativePermission ;
            |    knora-admin:forProject <http://rdfh.ch/projects/0001> ;
            |    knora-admin:forGroup <http://rdfh.ch/groups/0001/thing-searcher> ;
            |    knora-base:hasPermissions "ProjectAdminAllPermission" . } }
            |WHERE {}""".stripMargin,
      )
    },
    test("should produce correct query with multiple permissions") {
      val permissions = Set[Permission.Administrative](
        Permission.Administrative.ProjectAdminAll,
        Permission.Administrative.ProjectResourceCreateAll,
      )

      val query = CreateAdministrativePermissionQuery.build(
        permissionIri = testPermissionIri,
        forProjectIri = testProjectIri,
        forGroupIri = testGroupIri,
        permissions = permissions,
      )

      assertTrue(
        query.getQueryString ==
          """PREFIX knora-admin: <http://www.knora.org/ontology/knora-admin#>
            |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
            |INSERT { GRAPH <http://www.knora.org/data/permissions> { <http://rdfh.ch/permissions/0001/test-permission> a knora-admin:AdministrativePermission ;
            |    knora-admin:forProject <http://rdfh.ch/projects/0001> ;
            |    knora-admin:forGroup <http://rdfh.ch/groups/0001/thing-searcher> ;
            |    knora-base:hasPermissions "ProjectAdminAllPermission|ProjectResourceCreateAllPermission" . } }
            |WHERE {}""".stripMargin,
      )
    },
    test("should produce correct query with all available administrative permissions") {
      val permissions = Set[Permission.Administrative](
        Permission.Administrative.ProjectAdminAll,
        Permission.Administrative.ProjectAdminGroupAll,
        Permission.Administrative.ProjectAdminGroupRestricted,
        Permission.Administrative.ProjectAdminRightsAll,
        Permission.Administrative.ProjectResourceCreateAll,
        Permission.Administrative.ProjectResourceCreateRestricted,
      )

      val query = CreateAdministrativePermissionQuery.build(
        permissionIri = testPermissionIri,
        forProjectIri = testProjectIri,
        forGroupIri = testGroupIri,
        permissions = permissions,
      )

      assertTrue(
        query.getQueryString ==
          """PREFIX knora-admin: <http://www.knora.org/ontology/knora-admin#>
            |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
            |INSERT { GRAPH <http://www.knora.org/data/permissions> { <http://rdfh.ch/permissions/0001/test-permission> a knora-admin:AdministrativePermission ;
            |    knora-admin:forProject <http://rdfh.ch/projects/0001> ;
            |    knora-admin:forGroup <http://rdfh.ch/groups/0001/thing-searcher> ;
            |    knora-base:hasPermissions "ProjectResourceCreateAllPermission|ProjectResourceCreateRestrictedPermission|ProjectAdminAllPermission|ProjectAdminGroupAllPermission|ProjectAdminGroupRestrictedPermission|ProjectAdminRightsAllPermission" . } }
            |WHERE {}""".stripMargin,
      )
    },
    test("should produce correct query for different project and group") {
      val differentProjectIri = ProjectIri.unsafeFrom("http://rdfh.ch/projects/00FF")
      val differentGroupIri   = GroupIri.unsafeFrom("http://rdfh.ch/groups/00FF/editors")
      val permissions         = Set[Permission.Administrative](
        Permission.Administrative.ProjectResourceCreateAll,
      )

      val query = CreateAdministrativePermissionQuery.build(
        permissionIri = testPermissionIri,
        forProjectIri = differentProjectIri,
        forGroupIri = differentGroupIri,
        permissions = permissions,
      )

      assertTrue(
        query.getQueryString ==
          """PREFIX knora-admin: <http://www.knora.org/ontology/knora-admin#>
            |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
            |INSERT { GRAPH <http://www.knora.org/data/permissions> { <http://rdfh.ch/permissions/0001/test-permission> a knora-admin:AdministrativePermission ;
            |    knora-admin:forProject <http://rdfh.ch/projects/00FF> ;
            |    knora-admin:forGroup <http://rdfh.ch/groups/00FF/editors> ;
            |    knora-base:hasPermissions "ProjectResourceCreateAllPermission" . } }
            |WHERE {}""".stripMargin,
      )
    },
    test("should produce correct query with ProjectAdminRightsAll permission") {
      val permissions = Set[Permission.Administrative](
        Permission.Administrative.ProjectAdminRightsAll,
      )

      val query = CreateAdministrativePermissionQuery.build(
        permissionIri = testPermissionIri,
        forProjectIri = testProjectIri,
        forGroupIri = testGroupIri,
        permissions = permissions,
      )

      assertTrue(
        query.getQueryString ==
          """PREFIX knora-admin: <http://www.knora.org/ontology/knora-admin#>
            |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
            |INSERT { GRAPH <http://www.knora.org/data/permissions> { <http://rdfh.ch/permissions/0001/test-permission> a knora-admin:AdministrativePermission ;
            |    knora-admin:forProject <http://rdfh.ch/projects/0001> ;
            |    knora-admin:forGroup <http://rdfh.ch/groups/0001/thing-searcher> ;
            |    knora-base:hasPermissions "ProjectAdminRightsAllPermission" . } }
            |WHERE {}""".stripMargin,
      )
    },
  )
}
