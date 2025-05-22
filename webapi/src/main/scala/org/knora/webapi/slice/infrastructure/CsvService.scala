package org.knora.webapi.slice.infrastructure

import com.github.tototoshi.csv.CSVWriter
import zio.Scope
import zio.Task
import zio.ZIO
import zio.ZLayer
import zio.nio.file.Path

import java.io.OutputStream

case class CsvService() {

  def writeToPath[A](items: Seq[A], path: Path)(using
    rowBuilder: CsvRowBuilder[A],
  ): ZIO[Scope, Throwable, Path] =
    ZIO.fromAutoCloseable(ZIO.succeed(CSVWriter.open(path.toFile))).flatMap(write(_, items)).as(path)

  def writeToStream[A](items: Seq[A], os: OutputStream)(using
    rowBuilder: CsvRowBuilder[A],
  ): ZIO[Scope, Throwable, OutputStream] =
    ZIO.fromAutoCloseable(ZIO.succeed(CSVWriter.open(os))).flatMap(write(_, items).as(os))

  private def write[A](w: CSVWriter, items: Seq[A])(using
    rowBuilder: CsvRowBuilder[A],
  ): Task[Unit] = ZIO.attempt(w.writeRow(rowBuilder.headerRow)) *>
    ZIO.foreachDiscard(items)(item => ZIO.attempt(w.writeRow(rowBuilder.valueRow(item))))
}
object CsvService {
  val layer = ZLayer.derive[CsvService]
}

trait CsvRowBuilder[A] {
  def headerRow: Seq[String]
  def valueRow(rowItem: A): List[String | Number]
}
