/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.`export`.domain

import zio.test.*

import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri

object AdminDataQuerySpec extends ZIOSpecDefault {

  private val testProjectIri = ProjectIri.unsafeFrom("http://rdfh.ch/projects/0001")

  override def spec: Spec[TestEnvironment, Any] = suite("AdminDataQuerySpec")(
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
  )
}
