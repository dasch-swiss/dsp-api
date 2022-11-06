package dsp.schema.api

import zio._
import zhttp.http._
import zhttp.service.Server

object App extends ZIOAppDefault {
  val app = Http.collect[Request] { case Method.GET -> !! / "text" =>
    Response.text("Hello World!")
  }

  def run = Server.start(8090, app)
}
