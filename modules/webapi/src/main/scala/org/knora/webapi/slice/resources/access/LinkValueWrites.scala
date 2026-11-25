/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.access

import zio.*

import dsp.errors.InconsistentRepositoryDataException
import org.knora.webapi.IRI
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.v2.responder.resourcemessages.ReadResourceV2
import org.knora.webapi.messages.v2.responder.valuemessages.ReadLinkValueV2
import org.knora.webapi.slice.common.ResourceIri
import org.knora.webapi.slice.common.ValueIri
import org.knora.webapi.slice.resources.repo.model.SparqlTemplateLinkUpdate

/**
 * The `LinkValue` updates of a write, built from the unfiltered resource: a link's reference count counts references
 * the caller may not see. Only the update leaves the package.
 */
object LinkValueWrites {

  /**
   * Adds one reference of the link from the grant's resource to `target` through `linkProperty`: a new version of the
   * current `LinkValue` with the count incremented, or, without one, the direct link and a new `LinkValue` counting 1.
   *
   * @param linkProperty the link property, not the link value property.
   */
  def increment(
    grant: Grant[?],
    linkProperty: SmartIri,
    target: ResourceIri,
    creator: IRI,
    permissions: String,
    newLinkValueIri: Task[ValueIri],
  ): Task[SparqlTemplateLinkUpdate] =
    newLinkValueIri.map(
      linkUpdate(
        currentLinkValue(grant, linkProperty, target),
        Reference.Added,
        linkProperty,
        target,
        creator,
        permissions,
        _,
      ),
    )

  /**
   * A new version of the current `LinkValue` from the grant's resource to `target` through `linkProperty`, with the
   * same reference count, for a change of its metadata.
   *
   * @param linkProperty the link property, not the link value property.
   */
  def changeMetadata(
    grant: Grant[?],
    linkProperty: SmartIri,
    target: ResourceIri,
    creator: IRI,
    permissions: String,
    newLinkValueIri: Task[ValueIri],
  ): Task[SparqlTemplateLinkUpdate] =
    existing(grant, linkProperty, target).flatMap(current =>
      newLinkValueIri.map(linkUpdate(Some(current), Reference.Kept, linkProperty, target, creator, permissions, _)),
    )

  /**
   * Removes one reference of the link from the grant's resource to `target` through `linkProperty`: a new version of
   * the current `LinkValue` with the count decremented, and the direct link removed when it reaches 0.
   *
   * @param linkProperty    the link property, not the link value property.
   * @param newLinkValueIri runs only when the link value is found.
   */
  def decrement(
    grant: Grant[?],
    linkProperty: SmartIri,
    target: ResourceIri,
    creator: IRI,
    permissions: String,
    newLinkValueIri: Task[ValueIri],
  ): Task[SparqlTemplateLinkUpdate] =
    existing(grant, linkProperty, target).flatMap(current =>
      newLinkValueIri.map(linkUpdate(Some(current), Reference.Removed, linkProperty, target, creator, permissions, _)),
    )

  private enum Reference(val delta: Int) {
    case Added   extends Reference(1)
    case Kept    extends Reference(0)
    case Removed extends Reference(-1)
  }

  /**
   * A new version of `current`, or the first `LinkValue` when there is none. The direct link is inserted with the
   * first reference and deleted when the last one is removed.
   */
  private def linkUpdate(
    current: Option[ReadLinkValueV2],
    reference: Reference,
    linkProperty: SmartIri,
    target: ResourceIri,
    creator: IRI,
    permissions: String,
    newLinkValueIri: ValueIri,
  ): SparqlTemplateLinkUpdate = {
    val currentReferenceCount = current.fold(0)(_.valueHasRefCount)
    val newReferenceCount     = currentReferenceCount + reference.delta
    SparqlTemplateLinkUpdate(
      linkPropertyIri = linkProperty,
      directLinkExists = current.isDefined,
      insertDirectLink = current.isEmpty,
      deleteDirectLink = reference == Reference.Removed && newReferenceCount == 0,
      linkValueExists = current.isDefined,
      linkTargetExists = true,
      newLinkValueIri = newLinkValueIri,
      linkTargetIri = target.value,
      currentReferenceCount = currentReferenceCount,
      newReferenceCount = newReferenceCount,
      newLinkValueCreator = creator,
      newLinkValuePermissions = permissions,
    )
  }

  private def existing(grant: Grant[?], linkProperty: SmartIri, target: ResourceIri): Task[ReadLinkValueV2] =
    ZIO
      .fromOption(currentLinkValue(grant, linkProperty, target))
      .orDieWith(_ =>
        InconsistentRepositoryDataException(
          s"There should be a knora-base:LinkValue describing a direct link to resource <$target> using property <$linkProperty>, but it seems to be missing",
        ),
      )

  private def currentLinkValue(grant: Grant[?], linkProperty: SmartIri, target: ResourceIri) =
    grant.unfiltered.resource.flatMap(currentLinkValueOf(_, linkProperty.fromLinkPropToLinkValueProp, target))

  /** The current, not deleted, `LinkValue` of `linkValueProperty` (internal schema) to `target`, if any. */
  private[access] def currentLinkValueOf(
    resource: ReadResourceV2,
    linkValueProperty: SmartIri,
    target: ResourceIri,
  ): Option[ReadLinkValueV2] =
    resource.values
      .get(linkValueProperty)
      .flatMap(_.collectFirst {
        case link: ReadLinkValueV2 if link.valueContent.referredResourceIri == target && link.deletionInfo.isEmpty =>
          link
      })
}
