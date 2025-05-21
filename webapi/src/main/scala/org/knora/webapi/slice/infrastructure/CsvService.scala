package org.knora.webapi.slice.infrastructure

import com.github.tototoshi.csv.CSVWriter
import zio.Task
import zio.ZIO
import zio.ZLayer
import zio.nio.file.Path

case class CsvService() {
  private def createWriter(path: Path) = {
    val acquire = ZIO.succeed(CSVWriter.open(path.toFile))
    val release = (w: CSVWriter) => ZIO.succeed(w.close())
    ZIO.acquireRelease(acquire)(release)
  }

  def writeReportToCsv[A](report: Seq[A], path: Path)(using rowBuilder: CsvRowBuilder[A]): Task[Path] =
    ZIO.scoped {
      for {
        w <- createWriter(path)
        _ <- ZIO.succeed(w.writeRow(rowBuilder.headerRow))
        _ <- ZIO.foreachDiscard(report)(rep => ZIO.succeed(w.writeRow(rowBuilder.valueRow(rep))))
      } yield path
    }
}
object CsvService {
  val layer = ZLayer.derive[CsvService]
}

trait CsvRowBuilder[A] {
  def headerRow: Seq[String]
  def valueRow(rowItem: A): List[String | Number]
}
