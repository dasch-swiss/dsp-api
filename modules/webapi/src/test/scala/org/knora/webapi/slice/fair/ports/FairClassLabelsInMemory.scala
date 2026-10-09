/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.fair.ports

import zio.*

final case class FairClassLabelsInMemory(byClass: Map[String, Map[String, String]] = Map.empty)
    extends FairClassLabels {
  override def labels(classIri: String): Task[Map[String, String]] =
    ZIO.succeed(byClass.getOrElse(classIri, Map.empty))
}
