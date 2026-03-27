/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.`export`.domain

import zio.test.*

import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.UserIri

object AdminDataQuerySpec extends ZIOSpecDefault {

  private val testProjectIri = ProjectIri.unsafeFrom("http://rdfh.ch/projects/0001")
  private val testUser1      = UserIri.unsafeFrom("http://rdfh.ch/users/user001")
  private val testUser2      = UserIri.unsafeFrom("http://rdfh.ch/users/user002")

  override def spec: Spec[TestEnvironment, Any] = suite("AdminDataQuerySpec")(
    suite("build (project members only)")(
      test("should exclude system admin users with FILTER NOT EXISTS") {
        val query       = AdminDataQuery.build(testProjectIri)
        val queryString = query.getQueryString
        assertTrue(
          queryString.contains("FILTER NOT EXISTS"),
          queryString.contains("isInSystemAdminGroup"),
        )
      },
      test("should still include non-admin users in the project") {
        val query       = AdminDataQuery.build(testProjectIri)
        val queryString = query.getQueryString
        assertTrue(
          queryString.contains("?user a knora-admin:User"),
          queryString.contains("knora-admin:isInProject"),
        )
      },
      test("should include project and group patterns") {
        val query       = AdminDataQuery.build(testProjectIri)
        val queryString = query.getQueryString
        assertTrue(
          queryString.contains("knora-admin:knoraProject"),
          queryString.contains("knora-admin:UserGroup"),
          queryString.contains("knora-admin:belongsToProject"),
        )
      },
    ),
    suite("buildWithReferencedUsers")(
      test("with empty set returns same as build") {
        val base   = AdminDataQuery.build(testProjectIri).getQueryString
        val withRef = AdminDataQuery.buildWithReferencedUsers(testProjectIri, Set.empty)
        assertTrue(base == withRef)
      },
      test("includes VALUES block for referenced user IRIs") {
        val queryStr = AdminDataQuery.buildWithReferencedUsers(testProjectIri, Set(testUser1, testUser2))
        assertTrue(
          queryStr.contains("VALUES ?user"),
          queryStr.contains(testUser1.value),
          queryStr.contains(testUser2.value),
        )
      },
      test("referenced users branch has no SystemAdmin FILTER NOT EXISTS") {
        val queryStr = AdminDataQuery.buildWithReferencedUsers(testProjectIri, Set(testUser1))
        // The query has two UNION branches for users:
        // 1. Project members with FILTER NOT EXISTS for SystemAdmin
        // 2. Referenced users with VALUES but no SystemAdmin filter
        // The referenced users branch should NOT have a second FILTER NOT EXISTS
        val branchesWithFilterNotExists = "FILTER NOT EXISTS".r.findAllIn(queryStr).length
        assertTrue(branchesWithFilterNotExists == 1)
      },
      test("includes project member pattern alongside referenced users") {
        val queryStr = AdminDataQuery.buildWithReferencedUsers(testProjectIri, Set(testUser1))
        assertTrue(
          queryStr.contains("knora-admin:isInProject"),
          queryStr.contains("FILTER NOT EXISTS"),
          queryStr.contains("VALUES ?user"),
        )
      },
      test("includes group pattern") {
        val queryStr = AdminDataQuery.buildWithReferencedUsers(testProjectIri, Set(testUser1))
        assertTrue(
          queryStr.contains("knora-admin:UserGroup"),
          queryStr.contains("knora-admin:belongsToProject"),
        )
      },
    ),
  )
}
