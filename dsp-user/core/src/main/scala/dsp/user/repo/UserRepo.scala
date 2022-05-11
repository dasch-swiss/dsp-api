package dsp.user.repo

import dsp.user.domain.User
import dsp.user.domain.UserId
import zio._
import zio.macros.accessible

/**
 * The trait (interface) for the user repository. The user repository is responsible for storing and retrieving users.
 * Needs to be used by the user repository implementations.
 */
@accessible // with this annotation we don't have to write the companion object ourselves
trait UserRepo {

  /**
   * Writes a user to the repository (used for both create and update).
   * If this fails (e.g. the triplestore is not available), it's a non-recovable error. That's why we need UIO.
   *   When used, we should do it like: ...store(...).orDie
   *
   * @param user the user to write
   * @return     Unit
   */
  def store(user: User): UIO[Unit]

  /**
   * Gets a user from the repository by its id.
   *
   * @param id the user ID
   * @return   a user or None if not found
   */
  def lookup(id: UserId): UIO[Option[User]]

  /**
   * Gets all users from the repository
   *
   * @return   a list of users
   */
  def getAll(): UIO[List[User]]

  /**
   * Deletes a user from the repository by its id.
   *
   * @param id the user ID
   * @return   Unit or None if not found
   */
  def deleteUser(id: UserId): ZIO[Any, Option[Nothing], Unit]
}
