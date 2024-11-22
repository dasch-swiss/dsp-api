/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.v2.responder.resourcemessages

import monocle.*
import monocle.macros.*

import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.v2.responder.valuemessages.ReadValueV2

object ResourceMessagesV2Optics {

  type ReadResourceV2Values = Map[SmartIri, Seq[ReadValueV2]]

  object ReadResourceV2Optics {

    val values: Lens[ReadResourceV2, ReadResourceV2Values] = GenLens[ReadResourceV2](_.values)

    private def inValues(predicate: Seq[ReadValueV2] => Boolean): Optional[ReadResourceV2Values, Seq[ReadValueV2]] =
      Optional[ReadResourceV2Values, Seq[ReadValueV2]](_.values.find(predicate))(newValue =>
        values =>
          values.map {
            case (k, v) if predicate(v) => (k, newValue)
            case other                  => other
          },
      )

    def values(predicate: Seq[ReadValueV2] => Boolean): Optional[ReadResourceV2, Seq[ReadValueV2]] =
      values.andThen(inValues(predicate))
  }
}
