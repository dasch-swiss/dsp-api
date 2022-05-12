package dsp.user.handler

import dsp.user.domain.User
import dsp.user.repo.UserRepo
import zio._
import dsp.user.domain.UserId

/**
 * The user handler.
 *
 * @param repo  the user repository (interface)
 */
final case class UserHandler(repo: UserRepo) {
  def getAll(): ZIO[Any, Nothing, List[User]] = repo.getAll()
  def getUserById(id: UserId): ZIO[Any, Nothing, Option[User]] =
    repo.lookup(id) // used for V3, needs to be able to handle both IRIs and UUIDs
  def getUserByIri(): User         = ??? // used for V2, has only IRIs
  def getUserByUsername(): User    = ???
  def getUserByEmail(): User       = ???
  def createUser(user: User): Unit = repo.store(user)
  def updateUser(): User           = ???
  def deleteUser(): Unit           = ???
}

/**
 * Companion object providing the layer with an initialized implementation
 */
object UserHandler {
  val layer: ZLayer[UserRepo, Nothing, UserHandler] = {
    ZLayer {
      for {
        repo <- ZIO.service[UserRepo]
      } yield UserHandler(repo)
    }.tap(_ => ZIO.debug(">>> User handler initialized <<<"))
  }
}
