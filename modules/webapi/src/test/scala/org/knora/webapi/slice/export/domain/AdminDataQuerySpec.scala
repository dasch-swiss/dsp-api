/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.`export`.domain

import org.apache.jena.query.QueryFactory
import org.junit.runner.RunWith
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.UserIri

@RunWith(classOf[DspZTestJUnitRunner])
class AdminDataQuerySpec extends ZIOSpecDefault {

  private def canonical(query: String): String = {
    val q = QueryFactory.create(query)
    q.getPrefixMapping.clearNsPrefixMap()
    q.toString
  }

  private val testProjectIri = ProjectIri.unsafeFrom("http://rdfh.ch/projects/0001")
  private val testUser1      = UserIri.unsafeFrom("http://rdfh.ch/users/user001")
  private val testUser2      = UserIri.unsafeFrom("http://rdfh.ch/users/user002")

  override def spec: Spec[TestEnvironment, Any] = suite("AdminDataQuerySpec")(
    suite("build (project members)")(
      test("renders the project, project member and group branches scoped to the admin data graph") {
        val expected =
          """PREFIX knora-admin: <http://www.knora.org/ontology/knora-admin#>
            |CONSTRUCT { <http://rdfh.ch/projects/0001> ?projectPred ?projectObj .
            |?user ?userPred ?userObj .
            |?group ?groupPred ?groupObj . }
            |WHERE { GRAPH <http://www.knora.org/data/admin> { { <http://rdfh.ch/projects/0001> a knora-admin:knoraProject ;
            |    ?projectPred ?projectObj . } UNION { ?user a knora-admin:User ;
            |    ?userPred ?userObj ;
            |    knora-admin:isInProject <http://rdfh.ch/projects/0001> . } UNION { ?group a knora-admin:UserGroup ;
            |    ?groupPred ?groupObj ;
            |    knora-admin:belongsToProject <http://rdfh.ch/projects/0001> . } } }
            |""".stripMargin
        assertTrue(canonical(AdminDataQuery.build(testProjectIri).sparql) == canonical(expected))
      },
      test("has no system admin filter") {
        val queryString = AdminDataQuery.build(testProjectIri).sparql
        assertTrue(
          !queryString.contains("FILTER NOT EXISTS"),
          !queryString.contains("isInSystemAdminGroup"),
        )
      },
    ),
    suite("buildWithReferencedUsers")(
      test("with empty set returns same as build") {
        val base    = AdminDataQuery.build(testProjectIri).sparql
        val withRef = AdminDataQuery.buildWithReferencedUsers(testProjectIri, Set.empty).sparql
        assertTrue(base == withRef)
      },
      test("adds a VALUES branch for the referenced user IRIs") {
        val expected =
          """PREFIX knora-admin: <http://www.knora.org/ontology/knora-admin#>
            |CONSTRUCT {
            |  <http://rdfh.ch/projects/0001> ?projectPred ?projectObj .
            |  ?user ?userPred ?userObj .
            |  ?group ?groupPred ?groupObj .
            |}
            |WHERE {
            |  GRAPH <http://www.knora.org/data/admin> {
            |    {
            |      <http://rdfh.ch/projects/0001> a knora-admin:knoraProject ;
            |        ?projectPred ?projectObj .
            |    } UNION {
            |      ?user a knora-admin:User ;
            |        ?userPred ?userObj ;
            |        knora-admin:isInProject <http://rdfh.ch/projects/0001> .
            |    } UNION {
            |      ?user a knora-admin:User ;
            |        ?userPred ?userObj .
            |      VALUES ?user { <http://rdfh.ch/users/user001> <http://rdfh.ch/users/user002> }
            |    } UNION {
            |      ?group a knora-admin:UserGroup ;
            |        ?groupPred ?groupObj ;
            |        knora-admin:belongsToProject <http://rdfh.ch/projects/0001> .
            |    }
            |  }
            |}""".stripMargin
        val actual = AdminDataQuery.buildWithReferencedUsers(testProjectIri, Set(testUser1, testUser2))
        assertTrue(canonical(actual.sparql) == canonical(expected))
      },
      test("has no system admin filter on any branch") {
        val queryStr = AdminDataQuery.buildWithReferencedUsers(testProjectIri, Set(testUser1)).sparql
        assertTrue(
          !queryStr.contains("FILTER NOT EXISTS"),
          !queryStr.contains("isInSystemAdminGroup"),
        )
      },
    ),
  )
}
