/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service

import zio.Task

import org.knora.webapi.slice.admin.domain.model.GroupIri
import org.knora.webapi.slice.admin.domain.model.KnoraGroup
import org.knora.webapi.slice.common.repo.service.Repository

trait KnoraGroupRepo extends Repository[KnoraGroup, GroupIri] {

  /**
   * Saves the user group, returns the created data. Updates not supported.
   *
   * @param group The [[KnoraGroup]] to be saved, can be an update or a creation.
   * @return      The saved entity.
   */
  def save(group: KnoraGroup): Task[KnoraGroup]
}
