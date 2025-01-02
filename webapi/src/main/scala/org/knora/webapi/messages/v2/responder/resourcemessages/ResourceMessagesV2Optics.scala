/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.v2.responder.resourcemessages

import monocle.*
import monocle.macros.*

import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.v2.responder.valuemessages.FileValueContentV2
import org.knora.webapi.messages.v2.responder.valuemessages.FileValueV2
import org.knora.webapi.messages.v2.responder.valuemessages.ValueContentV2
import org.knora.webapi.messages.v2.responder.valuemessages.ValueMessagesV2Optics.FileValueContentV2Optics

object ResourceMessagesV2Optics {

  object CreateResourceV2Optics {
    type CreateResourceV2Values = Map[SmartIri, Seq[CreateValueInNewResourceV2]]

    val values: Lens[CreateResourceV2, CreateResourceV2Values] = GenLens[CreateResourceV2](_.values)

    private def inValues(predicate: Seq[CreateValueInNewResourceV2] => Boolean) =
      Optional[CreateResourceV2Values, Seq[CreateValueInNewResourceV2]](_.values.find(predicate))(newValue =>
        values =>
          values.map {
            case (k, v) if predicate(v) => (k, newValue)
            case other                  => other
          },
      )

    def values(
      predicate: Seq[CreateValueInNewResourceV2] => Boolean,
    ): Optional[CreateResourceV2, Seq[CreateValueInNewResourceV2]] =
      values.andThen(inValues(predicate))
  }

  object CreateValueInNewResourceV2Optics {

    val valueContent: Lens[CreateValueInNewResourceV2, ValueContentV2] =
      GenLens[CreateValueInNewResourceV2](_.valueContent)

    val fileValueContentV2: Optional[CreateValueInNewResourceV2, FileValueContentV2] =
      Optional[CreateValueInNewResourceV2, FileValueContentV2](_.valueContent.asOpt[FileValueContentV2])(fc =>
        _.copy(valueContent = fc),
      )

    val fileValue: Optional[CreateValueInNewResourceV2, FileValueV2] =
      CreateValueInNewResourceV2Optics.fileValueContentV2.andThen(FileValueContentV2Optics.fileValueV2)

    def elements(
      predicate: CreateValueInNewResourceV2 => Boolean,
    ): Optional[Seq[CreateValueInNewResourceV2], CreateValueInNewResourceV2] =
      Optional[Seq[CreateValueInNewResourceV2], CreateValueInNewResourceV2](_.find(predicate))(newValue =>
        values =>
          values.map {
            case v if predicate(v) => newValue
            case other             => other
          },
      )
  }
}
