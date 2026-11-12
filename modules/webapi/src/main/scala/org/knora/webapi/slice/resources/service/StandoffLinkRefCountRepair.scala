/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.service

import org.knora.webapi.messages.IriConversions.*
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionType
import org.knora.webapi.messages.util.PermissionUtilADM
import org.knora.webapi.slice.admin.domain.service.KnoraUserRepo
import org.knora.webapi.slice.common.ValueIri
import org.knora.webapi.slice.resources.repo.model.SparqlTemplateLinkUpdate
import org.knora.webapi.slice.resources.repo.service.StandoffLinkRefCountMismatch

/** The reason a [[StandoffLinkRefCountMismatch]] is not repaired. */
enum StandoffLinkRefCountSkipReason {
  case TargetMissing, StoredCountMissing, NothingToRepair
}

/** The outcome of deciding how to resolve one [[StandoffLinkRefCountMismatch]]. */
sealed trait StandoffLinkRefCountRepairPlan {
  def mismatch: StandoffLinkRefCountMismatch
}

object StandoffLinkRefCountRepairPlan {
  final case class Repair(mismatch: StandoffLinkRefCountMismatch, update: SparqlTemplateLinkUpdate)
      extends StandoffLinkRefCountRepairPlan
  final case class Skip(mismatch: StandoffLinkRefCountMismatch, reason: StandoffLinkRefCountSkipReason)
      extends StandoffLinkRefCountRepairPlan
}

object StandoffLinkRefCountRepair {
  import StandoffLinkRefCountRepairPlan.*
  import StandoffLinkRefCountSkipReason.*

  private lazy val standoffLinkValuePermissions: String =
    PermissionUtilADM.formatPermissionADMs(PermissionUtilADM.standoffLinkValuePermissions, PermissionType.OAP)

  /**
   * Maps a mismatch to the update that makes the stored reference count and direct link agree with the standoff tags.
   *
   * @param targetExists    whether the target resource exists
   * @param newLinkValueIri the IRI to use if a link value has to be created
   */
  def plan(mismatch: StandoffLinkRefCountMismatch, targetExists: Boolean, newLinkValueIri: ValueIri)(implicit
    sf: StringFormatter,
  ): StandoffLinkRefCountRepairPlan = {
    def update(
      linkValueExists: Boolean,
      currentReferenceCount: Int,
      newReferenceCount: Int,
      insertDirectLink: Boolean,
      deleteDirectLink: Boolean,
    ): StandoffLinkRefCountRepairPlan =
      Repair(
        mismatch,
        SparqlTemplateLinkUpdate(
          linkPropertyIri = OntologyConstants.KnoraBase.HasStandoffLinkTo.toSmartIri,
          directLinkExists = mismatch.directLink,
          insertDirectLink = insertDirectLink,
          deleteDirectLink = deleteDirectLink,
          linkValueExists = linkValueExists,
          linkTargetExists = targetExists,
          newLinkValueIri = newLinkValueIri,
          linkTargetIri = mismatch.target,
          currentReferenceCount = currentReferenceCount,
          newReferenceCount = newReferenceCount,
          newLinkValueCreator = KnoraUserRepo.builtIn.SystemUser.id.value,
          newLinkValuePermissions = standoffLinkValuePermissions,
        ),
      )

    val expected = mismatch.expectedCount
    (mismatch.linkValue, mismatch.storedCount) match {
      case _ if !targetExists                      => Skip(mismatch, TargetMissing)
      case (Some(_), None)                         => Skip(mismatch, StoredCountMissing)
      case (Some(_), Some(stored)) if expected > 0 =>
        update(
          linkValueExists = true,
          currentReferenceCount = stored,
          newReferenceCount = expected,
          insertDirectLink = !mismatch.directLink,
          deleteDirectLink = false,
        )
      case (Some(_), Some(stored)) =>
        update(
          linkValueExists = true,
          currentReferenceCount = stored,
          newReferenceCount = 0,
          insertDirectLink = false,
          deleteDirectLink = mismatch.directLink,
        )
      case (None, _) if expected > 0 =>
        update(
          linkValueExists = false,
          currentReferenceCount = 0,
          newReferenceCount = expected,
          insertDirectLink = !mismatch.directLink,
          deleteDirectLink = false,
        )
      case (None, _) if mismatch.directLink =>
        update(
          linkValueExists = false,
          currentReferenceCount = 0,
          newReferenceCount = 0,
          insertDirectLink = false,
          deleteDirectLink = true,
        )
      case _ => Skip(mismatch, NothingToRepair)
    }
  }
}
