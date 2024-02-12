/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.infrastructure

import zio.{IO, Ref, UIO, ZIO, ZLayer}

import java.io.IOException

final case class CommandExecutorMock(out: Ref[ProcessOutput]) extends CommandExecutor {
  override def buildCommand(command: String, params: String*): UIO[Command] =
    ZIO.succeed(Command(command +: params.toList))

  override def execute(command: Command): IO[IOException, ProcessOutput] = out.get

  def setOutput(output: ProcessOutput): UIO[Unit] = out.set(output)
}

object CommandExecutorMock {
  def setOutput(output: ProcessOutput): ZIO[CommandExecutorMock, Nothing, Unit] =
    ZIO.serviceWithZIO[CommandExecutorMock](_.setOutput(output))

  val layer = ZLayer.fromZIO(Ref.make(ProcessOutput("", "", 0))) >>> ZLayer.derive[CommandExecutorMock]
}
