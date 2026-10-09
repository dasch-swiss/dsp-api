/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.fair.ports

import zio.*

final case class FairClassesInMemory(byClass: Map[String, FairClass] = Map.empty) extends FairClasses {
  override def describe(classIri: String): Task[FairClass] =
    ZIO.succeed(byClass.getOrElse(classIri, FairClass(Map.empty, requiresFile = false)))
}
