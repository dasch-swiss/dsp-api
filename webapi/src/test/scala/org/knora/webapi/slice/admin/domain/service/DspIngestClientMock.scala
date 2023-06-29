package org.knora.webapi.slice.admin.domain.service

import dsp.valueobjects.Project
import zio.nio.file.Path
import zio.{Scope, Task, ZIO, ZLayer}

object DspIngestClientMock {
  final case class MockDspIngestClient() extends DspIngestClient {
    override def exportProject(shortCode: Project.ShortCode): ZIO[Scope, Throwable, Path]    = ???
    override def importProject(shortCode: Project.ShortCode, fileToImport: Path): Task[Path] = ???
  }
  val layer = ZLayer.fromFunction(MockDspIngestClient.apply _)
}
