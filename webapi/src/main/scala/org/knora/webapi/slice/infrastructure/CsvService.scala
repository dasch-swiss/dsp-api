package org.knora.webapi.slice.infrastructure

import com.github.tototoshi.csv.CSVFormat
import com.github.tototoshi.csv.CSVWriter
import zio.Scope
import zio.Task
import zio.ZIO
import zio.ZLayer
import zio.nio.file.Path

import java.io.OutputStream
import java.io.StringWriter
import java.io.Writer

case class CsvService() {

  def writeToPath[A](items: Seq[A], path: Path)(using
    rowBuilder: CsvRowBuilder[A],
    CSVFormat: CSVFormat,
  ): ZIO[Scope, Throwable, Path] =
    ZIO.fromAutoCloseable(ZIO.succeed(CSVWriter.open(path.toFile))).flatMap(write(_, items)).as(path)

  def writeToWriter[A](items: Seq[A], w: Writer)(using
    rowBuilder: CsvRowBuilder[A],
    CSVFormat: CSVFormat,
  ): ZIO[Scope, Throwable, Writer] =
    ZIO.fromAutoCloseable(ZIO.succeed(CSVWriter.open(w))).flatMap(write(_, items).as(w))

  def writeToStream[A](items: Seq[A], os: OutputStream)(using
    rowBuilder: CsvRowBuilder[A],
    CSVFormat: CSVFormat,
  ): ZIO[Scope, Throwable, OutputStream] =
    ZIO.fromAutoCloseable(ZIO.succeed(CSVWriter.open(os))).flatMap(write(_, items).as(os))

  def writeToString[A](
    items: Seq[A],
  )(using rowBuilder: CsvRowBuilder[A], CSVFormat: CSVFormat): ZIO[Scope, Throwable, String] = {
    val sw = new StringWriter()
    writeToWriter(items, sw).as(sw.toString)
  }

  private def write[A](w: CSVWriter, items: Seq[A])(using
    rowBuilder: CsvRowBuilder[A],
  ): Task[Unit] = ZIO.attempt(w.writeRow(rowBuilder.header)) *>
    ZIO.foreachDiscard(items)(item => ZIO.attempt(w.writeRow(rowBuilder.values(item))))
}
object CsvService {
  val layer = ZLayer.derive[CsvService]
}

trait CsvRowBuilder[A] {
  def header: Seq[String]
  def values(rowItem: A): Seq[Any]
}
object CsvRowBuilder {
  def fromColumnDefs[A](defs: ColumnDef[A]*): CsvRowBuilder[A] =
    new CsvRowBuilder[A] {
      override def header: Seq[String]          = defs.map(_.headerValue)
      override def values(rowItem: A): Seq[Any] = defs.map(_.rowValue(rowItem))
    }
}
trait ColumnDef[A] {
  def headerValue: String
  def rowValue(item: A): Any
}
object ColumnDef {
  def apply[A](name: String, mapper: A => Any): ColumnDef[A] = new ColumnDef[A] {
    override def headerValue: String    = name
    override def rowValue(item: A): Any = mapper.apply(item)
  }
}
