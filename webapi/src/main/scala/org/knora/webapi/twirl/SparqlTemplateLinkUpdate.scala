/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and Sepideh Alassi.
 *
 * This file is part of Knora.
 *
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.twirl

import org.knora.webapi.IRI

/**
  * Contains instructions that can be given to a SPARQL template for updating direct links and `knora-base:LinkValue`
  * objects representing references to resources.
  *
  * @param linkPropertyIri         the IRI of the direct link property.
  * @param directLinkExists        `true` if a direct link already exists between the source and target resources.
  * @param insertDirectLink        `true` if the direct link should be inserted.
  * @param deleteDirectLink        `true` if the direct link should be deleted (because the reference count is being decremented
  *                                to 0).
  * @param linkValueExists         `true` if a `LinkValue` already exists describing a direct link between the source
  *                                and target resources.
  * @param linkTargetExists        `true` if the link target already exists, `false` if is being created in the same
  *                                SPARQL update operation.
  * @param newLinkValueIri         the IRI of the new `LinkValue` to be created.
  * @param linkTargetIri           the IRI of the target resource.
  * @param currentReferenceCount   the current reference count of the existing `LinkValue`, if any. This will be
  *                                0 if (a) there was previously a direct link but it was deleted (`directLinkExists` is
  *                                `false` and `linkValueExists` is `true`), or (b) there was never a direct link, and
  *                                there is no `LinkValue` (`directLinkExists` and `linkValueExists` will then be `false`).
  * @param newReferenceCount       the new reference count of the `LinkValue`.
  * @param newLinkValueCreator     the creator of the new `LinkValue`.
  * @param newLinkValuePermissions the literal that should be the object of the `hasPermissions` property of
  *                                the new `LinkValue`.
  */
case class SparqlTemplateLinkUpdate(linkPropertyIri: IRI,
                                    directLinkExists: Boolean,
                                    insertDirectLink: Boolean,
                                    deleteDirectLink: Boolean,
                                    linkValueExists: Boolean,
                                    linkTargetExists: Boolean,
                                    newLinkValueIri: IRI,
                                    linkTargetIri: IRI,
                                    currentReferenceCount: Int,
                                    newReferenceCount: Int,
                                    newLinkValueCreator: IRI,
                                    newLinkValuePermissions: String)
