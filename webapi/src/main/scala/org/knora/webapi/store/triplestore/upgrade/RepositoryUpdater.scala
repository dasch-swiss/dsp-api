/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.triplestore.upgrade

import zio._
import zio.json._

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

import org.knora.webapi.messages.util.rdf._
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Select

final case class RepositoryUpdater(triplestoreService: TriplestoreService) {

  private val tmpDirNamePrefix: String = "knora"

  private case class RepoUpdateMetric(
    triples: Int,
    graphs: Int,
    totalDuration: Double,
    downloadDuration: Double,
    noopMigrationDuration: Double,
    dropGraphsDuration: Double,
    uploadDuration: Double,
  )
  private object RepoUpdateMetric {
    implicit val codec: JsonCodec[RepoUpdateMetric] = DeriveJsonCodec.gen[RepoUpdateMetric]
  }

  def getMigrationMetrics: Task[Unit] =
    for {
      durationState <- Ref.make(List.empty[RepoUpdateMetric])
      _             <- ZIO.logInfo("Starting dummy migration process...")
      _             <- deleteTmpDirectories()
      graphs        <- getDataGraphs.debug("Data graphs")
      metric1       <- doDummieMigration()
      _             <- durationState.update(metrics => metrics :+ metric1)
      _ <- ZIO.foreachDiscard(graphs) { graph =>
             for {
               _               <- ZIO.logInfo(s"Removing graph for next dummy migration: $graph")
               _               <- triplestoreService.compact()
               _               <- triplestoreService.dropGraph(graph)
               metric          <- doDummieMigration()
               _               <- durationState.update(metrics => metrics :+ metric)
               intermedMetrics <- durationState.get
               _ <- ZIO.logInfo(
                      s"""|Intermediate metrics:
                          |${intermedMetrics.toJsonPretty}
                          |""".stripMargin,
                    )
             } yield ()
           }
      metrics    <- durationState.get
      _          <- ZIO.logInfo(s"Metrics: $metrics")
      metricsJson = metrics.toJsonPretty
      _ <- ZIO.logInfo(
             s"""|***********************
                 |Final Metrics JSON:
                 |
                 |$metricsJson
                 |
                 |***********************
                 |""".stripMargin,
           )
    } yield ()

  private def getDataGraphs: Task[Seq[String]] =
    for {
      response <-
        triplestoreService.query(
          Select("SELECT DISTINCT ?g WHERE { GRAPH ?g { ?s ?p ?o } }", isGravsearch = false),
        )
      bindings <- ZIO.succeed(response.results.bindings)
      graphs   <- ZIO.succeed(bindings.map(_.rowMap("g")))
      dataGraphs = graphs.filter { graph =>
                     val pattern = """http://www\.knora\.org/data/(.*)/.*""".r
                     graph match {
                       case pattern(shortcode) =>
                         shortcode != "0000"
                       case _ => false
                     }
                   }
    } yield dataGraphs

  private def doDummieMigration(): Task[RepoUpdateMetric] =
    for {
      _       <- ZIO.logInfo("Starting dummy migration...")
      triples <- getTripleCount
      graphs  <- getGraphCount
      _ <- ZIO.logInfo(s"""|Dummy Migration metrics:
                           |Triples: $triples
                           |Graphs: $graphs
                           |""".stripMargin)
      start                <- Clock.currentTime(TimeUnit.MILLISECONDS)
      dir                  <- ZIO.attempt(Files.createTempDirectory(tmpDirNamePrefix))
      file                 <- createEmptyFile("downloaded-repository.nq", dir)
      _                    <- ZIO.logInfo(s"Downloading repository to file: $file")
      _                    <- triplestoreService.downloadRepository(file, MigrateAllGraphs)
      size                 <- ZIO.attempt(Files.size(file))
      _                    <- ZIO.logInfo(s"Downloaded file size: $size")
      downloadedAt         <- Clock.currentTime(TimeUnit.MILLISECONDS)
      _                    <- doNoopMigration(file)
      noopMigrationDoneAt  <- Clock.currentTime(TimeUnit.MILLISECONDS)
      _                    <- triplestoreService.dropDataGraphByGraph()
      _                    <- ZIO.logInfo("Done dropping graphs. Uploading repository...")
      dropGraphsDoneAt     <- Clock.currentTime(TimeUnit.MILLISECONDS)
      _                    <- triplestoreService.uploadRepository(file)
      _                    <- ZIO.logInfo("Done uploading repository.")
      _                    <- ZIO.attempt(Files.delete(file))
      _                    <- ZIO.logInfo("Deleted downloaded file.")
      end                  <- Clock.currentTime(TimeUnit.MILLISECONDS)
      totalDuration         = (end - start) / 1000.0
      downloadDuration      = (downloadedAt - start) / 1000.0
      noopMigrationDuration = (noopMigrationDoneAt - downloadedAt) / 1000.0
      dropGraphsDuration    = (dropGraphsDoneAt - noopMigrationDoneAt) / 1000.0
      uploadDuration        = (end - dropGraphsDoneAt) / 1000.0
      metric = RepoUpdateMetric(
                 triples,
                 graphs,
                 totalDuration,
                 downloadDuration,
                 noopMigrationDuration,
                 dropGraphsDuration,
                 uploadDuration,
               )
      _ <- ZIO.logInfo(s"Dummy migration done. Metrics: $metric")
    } yield metric

  private def doNoopMigration(file: Path): Task[Unit] =
    for {
      _       <- ZIO.logInfo("Starting noop migration...")
      model    = RdfFormatUtil.fileToRdfModel(file, NQuads)
      _       <- ZIO.logInfo(s"Read ${model.size} statements.")
      _        = model.foreach(_ => ())
      _       <- ZIO.logInfo("Noop migration done.")
      tmpFile <- createEmptyFile("transformed-repository.nq", file.getParent)
      _       <- ZIO.logInfo(s"Writing output file $tmpFile (${model.size} statements)...")
      _        = RdfFormatUtil.rdfModelToFile(model, tmpFile, NQuads)
      _       <- ZIO.logInfo("Wrote output file.")
      _       <- ZIO.attempt(tmpFile.toFile.delete())
      _       <- ZIO.logInfo("Temp file deleted. Noop migration done.")
    } yield ()

  private def createEmptyFile(filename: String, dir: Path) = ZIO.attempt {
    val file = dir.resolve(filename)
    Files.deleteIfExists(file)
    Files.createFile(file)
  }
  private def getTripleCount: Task[Int] =
    for {
      response <-
        triplestoreService.query(Select("SELECT (COUNT(*) AS ?count) WHERE { ?s ?p ?o }", isGravsearch = false))
      bindings <- ZIO.succeed(response.results.bindings)
      count    <- ZIO.succeed(bindings.head.rowMap("count").toInt)
    } yield count

  private def getGraphCount: Task[Int] =
    for {
      response <-
        triplestoreService.query(
          Select("SELECT (COUNT(DISTINCT ?g) AS ?count) WHERE { GRAPH ?g { ?s ?p ?o } }", isGravsearch = false),
        )
      bindings <- ZIO.succeed(response.results.bindings)
      count    <- ZIO.succeed(bindings.head.rowMap("count").toInt)
    } yield count

  /**
   * Deletes directories inside tmp directory starting with `tmpDirNamePrefix`.
   */
  private def deleteTmpDirectories(): UIO[Unit] = {
    val rootDir        = new File("/tmp/")
    val getTmpToDelete = rootDir.listFiles.filter(_.getName.startsWith(tmpDirNamePrefix))
    ZIO.foreach(getTmpToDelete) { dir =>
      zio.nio.file.Files.deleteRecursive(zio.nio.file.Path(dir.getPath))
    }
  }.unit.orDie

}

object RepositoryUpdater {
  val layer = ZLayer.derive[RepositoryUpdater]
}
