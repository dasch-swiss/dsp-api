package dsp.user.route

import zhttp.http.Response
import zio._
import zio.json._

import java.util.UUID

import dsp.user.handler.UserHandler
import dsp.valueobjects.Id

object GetUser {

  /**
   * The route to get a user by UUID
   *
   * @param uuid         the UUID of the user
   * @param userHandler  the userHandler that handles user actions
   * @return             a response with the user as json
   */
  def route(uuid: String, userHandler: UserHandler): Task[Response] = {
    val userUuid = UUID.fromString(uuid)
    for {
      userId <- Id.UserId.make(userUuid).toZIO
      user   <- userHandler.getUserById(userId)
    } yield Response.json(user.toJson)
  }

}
