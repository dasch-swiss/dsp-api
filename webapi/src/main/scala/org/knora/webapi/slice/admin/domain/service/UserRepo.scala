package org.knora.webapi.slice.admin.domain.service

import zio.Task

import org.knora.webapi.slice.admin.domain.model.KnoraUser
import org.knora.webapi.slice.admin.domain.model.UserIri
import org.knora.webapi.slice.common.repo.service.Repository

trait UserRepo extends Repository[KnoraUser, UserIri] {

  /**
   * Saves a given user. Use the returned instance for further operations as the save operation might have changed the entity instance completely.
   *
   * @param user The [[KnoraUser]] to be saved, can be an update or a creation.
   * @return the saved entity.
   */
  def save(user: KnoraUser): Task[KnoraUser]
}
