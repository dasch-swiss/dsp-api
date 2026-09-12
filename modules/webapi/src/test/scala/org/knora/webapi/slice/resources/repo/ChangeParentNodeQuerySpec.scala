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

@RunWith(classOf[DspZTestJUnitRunner])
class ChangeParentNodeQuerySpec extends ZIOSpecDefault {

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

  override def spec: Spec[TestEnvironment, Any] = suite("ChangeParentNodeQuerySpec")(
    test("should produce the correct query for changing the parent node") {
      val actual = ChangeParentNodeQuery
        .build(
          testProject,
          ListIri.unsafeFrom("http://rdfh.ch/lists/0001/node"),
          ListIri.unsafeFrom("http://rdfh.ch/lists/0001/old-parent"),
          ListIri.unsafeFrom("http://rdfh.ch/lists/0001/new-parent"),
        )
        .sparql
      assertTrue(
        canonical(actual) == canonical(
          """WITH <http://www.knora.org/data/0001/anything>
            |DELETE { <http://rdfh.ch/lists/0001/old-parent> <http://www.knora.org/ontology/knora-base#hasSubListNode> <http://rdfh.ch/lists/0001/node> . }
            |INSERT { <http://rdfh.ch/lists/0001/new-parent> <http://www.knora.org/ontology/knora-base#hasSubListNode> <http://rdfh.ch/lists/0001/node> . }
            |WHERE { <http://rdfh.ch/lists/0001/node> a <http://www.knora.org/ontology/knora-base#ListNode> .
            |<http://rdfh.ch/lists/0001/old-parent> a <http://www.knora.org/ontology/knora-base#ListNode> ;
            |    <http://www.knora.org/ontology/knora-base#hasSubListNode> <http://rdfh.ch/lists/0001/node> .
            |<http://rdfh.ch/lists/0001/new-parent> a <http://www.knora.org/ontology/knora-base#ListNode> . }""".stripMargin,
        ),
      )
    },
    test("should produce the correct query for different parent IRIs and another project") {
      val otherProject = KnoraProject(
        ProjectIri.unsafeFrom("http://rdfh.ch/projects/0803"),
        Shortname.unsafeFrom("incunabula"),
        Shortcode.unsafeFrom("0803"),
        None,
        NonEmptyChunk(Description.unsafeFrom(StringLiteralV2.from("Incunabula project"))),
        List.empty,
        None,
        SelfJoin.CannotJoin,
        None,
        Set.empty,
        Set.empty,
      )
      val actual = ChangeParentNodeQuery
        .build(
          otherProject,
          ListIri.unsafeFrom("http://rdfh.ch/lists/0803/book-list-node"),
          ListIri.unsafeFrom("http://rdfh.ch/lists/0803/root"),
          ListIri.unsafeFrom("http://rdfh.ch/lists/0803/other-root"),
        )
        .sparql
      assertTrue(
        canonical(actual) == canonical(
          """WITH <http://www.knora.org/data/0803/incunabula>
            |DELETE { <http://rdfh.ch/lists/0803/root> <http://www.knora.org/ontology/knora-base#hasSubListNode> <http://rdfh.ch/lists/0803/book-list-node> . }
            |INSERT { <http://rdfh.ch/lists/0803/other-root> <http://www.knora.org/ontology/knora-base#hasSubListNode> <http://rdfh.ch/lists/0803/book-list-node> . }
            |WHERE { <http://rdfh.ch/lists/0803/book-list-node> a <http://www.knora.org/ontology/knora-base#ListNode> .
            |<http://rdfh.ch/lists/0803/root> a <http://www.knora.org/ontology/knora-base#ListNode> ;
            |    <http://www.knora.org/ontology/knora-base#hasSubListNode> <http://rdfh.ch/lists/0803/book-list-node> .
            |<http://rdfh.ch/lists/0803/other-root> a <http://www.knora.org/ontology/knora-base#ListNode> . }""".stripMargin,
        ),
      )
    },
  )
}
