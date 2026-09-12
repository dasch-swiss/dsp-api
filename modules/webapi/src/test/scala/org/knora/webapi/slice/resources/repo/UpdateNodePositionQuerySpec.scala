/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo

import org.apache.jena.update.UpdateFactory
import org.junit.runner.RunWith
import zio.NonEmptyChunk
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.KnoraProject.*
import org.knora.webapi.slice.admin.domain.model.ListProperties.ListIri
import org.knora.webapi.slice.admin.domain.model.ListProperties.Position

@RunWith(classOf[DspZTestJUnitRunner])
class UpdateNodePositionQuerySpec extends ZIOSpecDefault {

  private def canonical(query: String): String = {
    val update = UpdateFactory.create(query)
    update.getPrefixMapping.clearNsPrefixMap()
    update.toString
  }

  private val testProject = KnoraProject(
    ProjectIri.unsafeFrom("http://rdfh.ch/projects/0001"),
    Shortname.unsafeFrom("anything"),
    Shortcode.unsafeFrom("0001"),
    None,
    NonEmptyChunk(Description.unsafeFrom(StringLiteralV2.from("Test project"))),
    List.empty,
    None,
    SelfJoin.CannotJoin,
    None,
    Set.empty,
    Set.empty,
  )

  override def spec: Spec[TestEnvironment, Any] = suite("UpdateNodePositionQuerySpec")(
    test("should produce the correct query for a positive position") {
      val actual = UpdateNodePositionQuery
        .build(testProject, ListIri.unsafeFrom("http://rdfh.ch/lists/0001/node"), Position.unsafeFrom(3))
        .sparql
      assertTrue(
        canonical(actual) == canonical(
          """PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
            |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
            |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
            |WITH <http://www.knora.org/data/0001/anything>
            |DELETE { <http://rdfh.ch/lists/0001/node> knora-base:listNodePosition ?o . }
            |INSERT { <http://rdfh.ch/lists/0001/node> knora-base:listNodePosition 3 . }
            |WHERE { <http://rdfh.ch/lists/0001/node> a knora-base:ListNode ;
            |    knora-base:listNodePosition ?o . }""".stripMargin,
        ),
      )
    },
    test("should produce the correct query for position zero and another node") {
      val actual = UpdateNodePositionQuery
        .build(testProject, ListIri.unsafeFrom("http://rdfh.ch/lists/0001/other"), Position.unsafeFrom(0))
        .sparql
      assertTrue(
        canonical(actual) == canonical(
          """PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
            |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
            |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
            |WITH <http://www.knora.org/data/0001/anything>
            |DELETE { <http://rdfh.ch/lists/0001/other> knora-base:listNodePosition ?o . }
            |INSERT { <http://rdfh.ch/lists/0001/other> knora-base:listNodePosition 0 . }
            |WHERE { <http://rdfh.ch/lists/0001/other> a knora-base:ListNode ;
            |    knora-base:listNodePosition ?o . }""".stripMargin,
        ),
      )
    },
  )
}
