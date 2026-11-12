/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.service

import org.junit.runner.RunWith
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.messages.IriConversions.*
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionType
import org.knora.webapi.messages.util.PermissionUtilADM
import org.knora.webapi.slice.admin.domain.service.KnoraUserRepo
import org.knora.webapi.slice.common.ValueIri
import org.knora.webapi.slice.resources.repo.model.SparqlTemplateLinkUpdate
import org.knora.webapi.slice.resources.repo.service.StandoffLinkRefCountMismatch
import org.knora.webapi.slice.resources.service.StandoffLinkRefCountRepairPlan.*
import org.knora.webapi.slice.resources.service.StandoffLinkRefCountSkipReason.*

@RunWith(classOf[DspZTestJUnitRunner])
class StandoffLinkRefCountRepairSpec extends ZIOSpecDefault {

  private implicit val sf: StringFormatter = StringFormatter.getInitializedTestInstance

  private val newLinkValueIri = ValueIri.unsafeFrom("http://rdfh.ch/0001/source/values/new")
  private val linkValueIri    = "http://rdfh.ch/0001/source/values/link"

  private def mismatch(
    linkValue: Option[String] = Some(linkValueIri),
    storedCount: Option[Int] = Some(3),
    expectedCount: Int = 1,
    directLink: Boolean = true,
  ) = StandoffLinkRefCountMismatch(
    source = "http://rdfh.ch/0001/source",
    target = "http://rdfh.ch/0001/target",
    linkValue = linkValue,
    storedCount = storedCount,
    expectedCount = expectedCount,
    directLink = directLink,
  )

  private def plan(m: StandoffLinkRefCountMismatch, targetExists: Boolean = true) =
    StandoffLinkRefCountRepair.plan(m, targetExists, newLinkValueIri)

  private def expectedUpdate(
    directLinkExists: Boolean,
    insertDirectLink: Boolean,
    deleteDirectLink: Boolean,
    linkValueExists: Boolean,
    currentReferenceCount: Int,
    newReferenceCount: Int,
    linkTargetExists: Boolean = true,
  ) = SparqlTemplateLinkUpdate(
    linkPropertyIri = OntologyConstants.KnoraBase.HasStandoffLinkTo.toSmartIri,
    directLinkExists = directLinkExists,
    insertDirectLink = insertDirectLink,
    deleteDirectLink = deleteDirectLink,
    linkValueExists = linkValueExists,
    linkTargetExists = linkTargetExists,
    newLinkValueIri = newLinkValueIri,
    linkTargetIri = "http://rdfh.ch/0001/target",
    currentReferenceCount = currentReferenceCount,
    newReferenceCount = newReferenceCount,
    newLinkValueCreator = KnoraUserRepo.builtIn.SystemUser.id.value,
    newLinkValuePermissions =
      PermissionUtilADM.formatPermissionADMs(PermissionUtilADM.standoffLinkValuePermissions, PermissionType.OAP),
  )

  override val spec: Spec[Any, Nothing] = suite("StandoffLinkRefCountRepair")(
    test("skips when the target is missing") {
      val m = mismatch()
      assertTrue(plan(m, targetExists = false) == Skip(m, TargetMissing))
    },
    test("skips when a link value has no stored count") {
      val m = mismatch(storedCount = None)
      assertTrue(plan(m) == Skip(m, StoredCountMissing))
    },
    test("corrects the count of an existing link value and inserts a missing direct link") {
      val m = mismatch(expectedCount = 2, directLink = false)
      assertTrue(
        plan(m) == Repair(m, expectedUpdate(false, true, false, true, 3, 2)),
      )
    },
    test("corrects the count of an existing link value and keeps an existing direct link") {
      val m = mismatch(expectedCount = 2, directLink = true)
      assertTrue(
        plan(m) == Repair(m, expectedUpdate(true, false, false, true, 3, 2)),
      )
    },
    test("sets the count to 0 and deletes the direct link when nothing references the target") {
      val m = mismatch(expectedCount = 0, directLink = true)
      assertTrue(
        plan(m) == Repair(m, expectedUpdate(true, false, true, true, 3, 0)),
      )
    },
    test("sets the count to 0 without deleting an absent direct link") {
      val m = mismatch(expectedCount = 0, directLink = false)
      assertTrue(
        plan(m) == Repair(m, expectedUpdate(false, false, false, true, 3, 0)),
      )
    },
    test("creates a link value and the direct link when neither exists") {
      val m = mismatch(linkValue = None, storedCount = None, expectedCount = 2, directLink = false)
      assertTrue(
        plan(m) == Repair(m, expectedUpdate(false, true, false, false, 0, 2)),
      )
    },
    test("creates a link value and keeps an existing direct link") {
      val m = mismatch(linkValue = None, storedCount = None, expectedCount = 2, directLink = true)
      assertTrue(
        plan(m) == Repair(m, expectedUpdate(true, false, false, false, 0, 2)),
      )
    },
    test("deletes a dangling direct link when there is no link value and no reference") {
      val m = mismatch(linkValue = None, storedCount = None, expectedCount = 0, directLink = true)
      assertTrue(
        plan(m) == Repair(m, expectedUpdate(true, false, true, false, 0, 0)),
      )
    },
    test("skips when there is nothing to repair") {
      val m = mismatch(linkValue = None, storedCount = None, expectedCount = 0, directLink = false)
      assertTrue(plan(m) == Skip(m, NothingToRepair))
    },
  )
}
