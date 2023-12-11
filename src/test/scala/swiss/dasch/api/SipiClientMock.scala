/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.api

import swiss.dasch.api.SipiClientMockMethodInvocation.*
import swiss.dasch.domain.*
import swiss.dasch.domain.Exif.Image.OrientationValue
import swiss.dasch.infrastructure.ProcessOutput
import zio.*
import zio.nio.file.*

import java.nio.file.StandardCopyOption

sealed trait SipiClientMockMethodInvocation
object SipiClientMockMethodInvocation {
  final case class TranscodeImageFile(
    fileIn: Path,
    fileOut: Path,
    outputFormat: SipiImageFormat
  ) extends SipiClientMockMethodInvocation
  final case class ApplyTopLeftCorrection(fileIn: Path, fileOut: Path) extends SipiClientMockMethodInvocation
  final case class QueryImageFile(file: Path)                          extends SipiClientMockMethodInvocation
}

final case class SipiClientMock(
  invocations: Ref[List[SipiClientMockMethodInvocation]],
  queryImageFileReturnValue: Ref[ProcessOutput],
  dontTranscode: Ref[Boolean]
) extends SipiClient {

  override def transcodeImageFile(
    fileIn: Path,
    fileOut: Path,
    outputFormat: SipiImageFormat
  ): UIO[ProcessOutput] =
    ZIO.ifZIO(dontTranscode.get)(
      onTrue = ZIO.succeed(ProcessOutput("", "", 0)),
      onFalse = Files.copy(fileIn, fileOut, StandardCopyOption.REPLACE_EXISTING).orDie *>
        invocations
          .update(_.appended(TranscodeImageFile(fileIn, fileOut, outputFormat)))
          .as(ProcessOutput("", "", 0))
    )

  override def applyTopLeftCorrection(fileIn: Path, fileOut: Path): UIO[ProcessOutput] =
    Files.copy(fileIn, fileOut, StandardCopyOption.REPLACE_EXISTING).orDie *>
      invocations
        .update(_.appended(ApplyTopLeftCorrection(fileIn, fileOut)))
        .as(ProcessOutput("", "", 0))

  override def queryImageFile(file: Path): UIO[ProcessOutput] = for {
    response <- queryImageFileReturnValue.get
    _        <- invocations.update(_.appended(QueryImageFile(file)))
  } yield response

  def getInvocations(): UIO[List[SipiClientMockMethodInvocation]] = invocations.get

  def wasInvoked(invocation: SipiClientMockMethodInvocation): UIO[Boolean] =
    getInvocations().map(_.contains(invocation))

  def noInteractions(): UIO[Boolean] = getInvocations().map(_.isEmpty)

  def setQueryImageFileOrientation(orientation: OrientationValue): UIO[Unit] = queryImageFileReturnValue.set(
    ProcessOutput(s"Exif.Image.Orientation                       0x0112 Short       ${orientation.value}", "", 0)
  )

  def setQueryImageDimensions(dimension: Dimensions): UIO[Unit] = queryImageFileReturnValue.set(
    ProcessOutput(
      s"""
         |
         |SipiImage with the following parameters:
         |nx    = ${dimension.width} 
         |ny    = ${dimension.height}
         |""".stripMargin,
      "",
      0
    )
  )

  def setDontTranscode(newState: Boolean): UIO[Unit] = this.dontTranscode.set(newState)
}

object SipiClientMock {
  def transcodeImageFile(
    fileIn: Path,
    fileOut: Path,
    outputFormat: SipiImageFormat
  ): RIO[SipiClientMock, ProcessOutput] =
    ZIO.serviceWithZIO[SipiClientMock](_.transcodeImageFile(fileIn, fileOut, outputFormat))

  def applyTopLeftCorrection(fileIn: Path, fileOut: Path): RIO[SipiClientMock, ProcessOutput] =
    ZIO.serviceWithZIO[SipiClientMock](_.applyTopLeftCorrection(fileIn, fileOut))

  def queryImageFile(file: Path): RIO[SipiClientMock, ProcessOutput] =
    ZIO.serviceWithZIO[SipiClientMock](_.queryImageFile(file))

  def getInvocations(): RIO[SipiClientMock, List[SipiClientMockMethodInvocation]] =
    ZIO.serviceWithZIO[SipiClientMock](_.getInvocations())

  def wasInvoked(invocation: SipiClientMockMethodInvocation): RIO[SipiClientMock, Boolean] =
    ZIO.serviceWithZIO[SipiClientMock](_.wasInvoked(invocation))

  def noInteractions(): RIO[SipiClientMock, Boolean] =
    ZIO.serviceWithZIO[SipiClientMock](_.noInteractions())

  def setOrientation(orientation: OrientationValue): RIO[SipiClientMock, Unit] =
    ZIO.serviceWithZIO[SipiClientMock](_.setQueryImageFileOrientation(orientation))

  def setQueryImageDimensions(dimension: Dimensions): RIO[SipiClientMock, Unit] =
    ZIO.serviceWithZIO[SipiClientMock](_.setQueryImageDimensions(dimension))

  def dontTranscode(): RIO[SipiClientMock, Unit] =
    ZIO.serviceWithZIO[SipiClientMock](_.setDontTranscode(true))

  val layer: ULayer[SipiClientMock] = ZLayer.fromZIO(for {
    invocations  <- Ref.make(List.empty[SipiClientMockMethodInvocation])
    querySipiOut <- Ref.make[ProcessOutput](ProcessOutput("", "", 0))
    failSilently <- Ref.make[Boolean](false)
  } yield SipiClientMock(invocations, querySipiOut, failSilently))
}
