package org.knora.webapi.slice.admin.domain.service

import org.knora.webapi.slice.admin.domain.model.Email
import zio.Task
import org.knora.webapi.slice.admin.domain.model.KnoraUser
import org.knora.webapi.slice.admin.domain.model.UserIri
import org.knora.webapi.slice.common.repo.service.Repository

trait KnoraUserRepo extends Repository[KnoraUser, UserIri] {

  /**
   * Retrieves an entity by its id.
   *
   * @param id The identifier of type [[Id]].
   * @return the entity with the given id or [[None]] if none found.
   */
  def findByEmail(id: Email): Task[Option[KnoraUser]]

  /**
   * Saves a given user. Use the returned instance for further operations as the save operation might have changed the entity instance completely.
   *
   * @param user The [[KnoraUser]] to be saved, can be an update or a creation.
   * @return the saved entity.
   */
  def save(user: KnoraUser): Task[KnoraUser]
}
