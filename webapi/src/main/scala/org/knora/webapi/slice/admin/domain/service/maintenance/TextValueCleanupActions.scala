/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service.maintenance

import zio.Task

import org.knora.webapi.slice.admin.domain.model.MaintenanceAction
import org.knora.webapi.store.triplestore.api.TriplestoreService

final case class TextValueCleanupSimpleTextInOntoAction(
  triplestoreService: TriplestoreService,
) extends MaintenanceAction[Unit]() {

  override def execute(params: Unit): Task[Unit] = ???
}

final case class TextValueCleanupSimpleTextInDataAction() {
  throw new NotImplementedError("TextValueCleanupSimpleTextInDataAction is not implemented")
}

final case class TextValueCleanupRichtextInOntoAction() {
  throw new NotImplementedError("TextValueCleanupRichtextInOntoAction is not implemented")
}

final case class TextValueCleanupRichtextInDataAction() {
  throw new NotImplementedError("TextValueCleanupRichtextInDataAction is not implemented")
}

final case class TextValueCleanupCustomMarkupAction() {
  throw new NotImplementedError("TextValueCleanupCustomMarkupAction is not implemented")
}
