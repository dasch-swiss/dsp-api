/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo

import org.junit.runner.RunWith
import zio.test.*

import java.time.Instant
import java.util.UUID

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.GoldenTest
import org.knora.webapi.messages.IriConversions.*
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.slice.common.ValueIri
import org.knora.webapi.slice.resources.repo.model.SparqlTemplateLinkUpdate
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.SparqlTimeout

@RunWith(classOf[DspZTestJUnitRunner])
class StandoffLinkRefCountRepairQuerySpec extends ZIOSpecDefault with GoldenTest {

  private implicit val sf: StringFormatter = StringFormatter.getInitializedTestInstance

  private val graph        = "http://www.knora.org/data/0001/anything"
  private val source       = "http://rdfh.ch/0001/source"
  private val creationDate = Instant.parse("2026-01-02T03:04:05Z")
  private val uuid         = UUID.fromString("00000000-0000-0000-0000-000000000001")

  private def linkUpdate(
    linkValueExists: Boolean,
    currentReferenceCount: Int,
    newReferenceCount: Int,
    insertDirectLink: Boolean,
    deleteDirectLink: Boolean,
  ) = SparqlTemplateLinkUpdate(
    linkPropertyIri = OntologyConstants.KnoraBase.HasStandoffLinkTo.toSmartIri,
    directLinkExists = !insertDirectLink,
    insertDirectLink = insertDirectLink,
    deleteDirectLink = deleteDirectLink,
    linkValueExists = linkValueExists,
    linkTargetExists = true,
    newLinkValueIri = ValueIri.unsafeFrom("http://rdfh.ch/0001/source/values/new"),
    linkTargetIri = "http://rdfh.ch/0001/target",
    currentReferenceCount = currentReferenceCount,
    newReferenceCount = newReferenceCount,
    newLinkValueCreator = "http://www.knora.org/ontology/knora-admin#SystemUser",
    newLinkValuePermissions = "CR knora-admin:Creator|V knora-admin:ProjectMember",
  )

  private def update(u: SparqlTemplateLinkUpdate) =
    StandoffLinkRefCountRepairQuery.build(graph, source, u, creationDate, uuid)

  override def spec: Spec[TestEnvironment & zio.Scope, Any] = suite("StandoffLinkRefCountRepairQuerySpec")(
    test("link value exists, new count above zero") {
      for {
        q <- update(linkUpdate(true, 3, 1, insertDirectLink = true, deleteDirectLink = false))
      } yield assertTrue(q.timeout == SparqlTimeout.Maintenance) && assertGolden(q.sparql, "updateCount")
    },
    test("link value exists, new count zero") {
      for {
        q <- update(linkUpdate(true, 2, 0, insertDirectLink = false, deleteDirectLink = true))
      } yield assertGolden(q.sparql, "updateCountToZero")
    },
    test("no link value, new count above zero") {
      for {
        q <- update(linkUpdate(false, 0, 2, insertDirectLink = true, deleteDirectLink = false))
      } yield assertGolden(q.sparql, "createLinkValue")
    },
    test("no link value, delete the direct link") {
      for {
        q <- update(linkUpdate(false, 0, 0, insertDirectLink = false, deleteDirectLink = true))
      } yield assertGolden(q.sparql, "deleteDirectLink")
    },
    test("no link value, count zero and no direct link to delete is rejected") {
      for {
        exit <- update(linkUpdate(false, 0, 0, insertDirectLink = false, deleteDirectLink = false)).exit
      } yield assertTrue(exit.isFailure)
    },
    test("isApplied asks for the new link value when one is written") {
      val ask = StandoffLinkRefCountRepairQuery.isApplied(
        graph,
        source,
        linkUpdate(false, 0, 2, insertDirectLink = true, deleteDirectLink = false),
      )
      assertGolden(ask.sparql, "isAppliedLinkValue")
    },
    test("isApplied asks for the absence of the direct link when only it is deleted") {
      val ask = StandoffLinkRefCountRepairQuery.isApplied(
        graph,
        source,
        linkUpdate(false, 0, 0, insertDirectLink = false, deleteDirectLink = true),
      )
      assertGolden(ask.sparql, "isAppliedDirectLinkDeleted")
    },
  )
}
