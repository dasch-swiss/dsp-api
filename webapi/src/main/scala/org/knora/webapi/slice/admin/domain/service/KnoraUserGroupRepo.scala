/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service

import zio.Task

import org.knora.webapi.slice.admin.domain.model.GroupIri
import org.knora.webapi.slice.admin.domain.model.KnoraUserGroup
import org.knora.webapi.slice.common.repo.service.Repository

trait KnoraUserGroupRepo extends Repository[KnoraUserGroup, GroupIri] {

  /**
   * Saves the user group, returns the created data. Updates not supported.
   *
   * @param user The [[KnoraUserGroup]] to be saved, can be an update or a creation.
   * @return the saved entity.
   */
  def save(userGroup: KnoraUserGroup): Task[KnoraUserGroup]
}
