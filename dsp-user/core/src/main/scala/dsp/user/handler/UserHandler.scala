package dsp.user.handler

import dsp.user.domain.User
import dsp.user.repo.UserRepo
import zio._

/**
 * The user handler.
 *
 * @param repo  the user repository (interface)
 */
final case class UserHandler(repo: UserRepo) {
  def getAll(): ZIO[Any, Nothing, List[User]] = repo.getAll()
  def getUserById(): User                     = ??? // used for V3, needs to be able to handle both IRIs and UUIDs
  def getUserByIri(): User                    = ??? // used for V2, has only IRIs
  def getUserByUsername(): User               = ???
  def getUserByEmail(): User                  = ???
  def createUser(): User                      = ???
  def updateUser(): User                      = ???
  def deleteUser(): Unit                      = ???
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
