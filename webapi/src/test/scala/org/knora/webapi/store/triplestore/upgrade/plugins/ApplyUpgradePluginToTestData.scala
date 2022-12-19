/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.triplestore.upgrade.plugins

import org.apache.jena.riot.RDFDataMgr
import org.apache.jena.riot.RDFFormat
import zio.Scope
import zio.ZIO
import zio.ZIOAppDefault

import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import scala.jdk.CollectionConverters.IteratorHasAsScala

import org.knora.webapi.messages.util.rdf.RdfFeatureFactory
import org.knora.webapi.messages.util.rdf.RdfModel
import org.knora.webapi.messages.util.rdf.Turtle
import org.knora.webapi.messages.util.rdf.jenaimpl.JenaConversions._
import org.knora.webapi.store.triplestore.upgrade.UpgradePlugin

/**
 * This class may be run if you wish to apply your upgrade plugin on our test data.
 * For this you must first provide an instance of your plugin as `val upgradePlugin`
 * and also specify the absolut path to the "test_data" folder in `val testDataPath`.
 */
object ApplyUpgradePluginToTestData extends ZIOAppDefault {

  val upgradePlugin = new NoopPlugin()
  val testDataPath  = "/test_data/all_data"

  def discoverFiles(dirPath: String) =
    Files.list(Path.of(dirPath)).filter(Files.isRegularFile(_)).iterator.asScala.toArray

  def applyUpgradePluginTo(plugin: UpgradePlugin, path: Path): ZIO[Any with Scope, Throwable, Unit] = for {
    model            <- parseRdfModelFromFile(path)
    transformedModel <- applyPluginToModel(plugin, model)
    _                <- writeModelToFile(transformedModel, path)
  } yield ()

  def parseRdfModelFromFile(path: Path) =
    for {
      fis   <- fileInputStreamFor(path)
      model <- parseRdfModel(fis)
    } yield model

  def applyPluginToModel(plugin: UpgradePlugin, model: RdfModel) = ZIO.succeed {
    plugin.transform(model);
    model
  }

  def writeModelToFile(rdfModel: RdfModel, path: Path) = {
    val defaultGraph = rdfModel.asJenaDataset.asDatasetGraph.getDefaultGraph
    for {
      fos <- fileOutputStreamFor(path)
      _   <- ZIO.attempt(RDFDataMgr.write(fos, defaultGraph, RDFFormat.TURTLE_PRETTY))
    } yield ()
  }

  def fileInputStreamFor(path: Path) =
    ZIO.acquireRelease(ZIO.attemptBlockingIO(new FileInputStream(path.toFile)))(fis => ZIO.succeedBlocking(fis.close()))

  def parseRdfModel(is: InputStream) =
    ZIO.succeed(RdfFeatureFactory.getRdfFormatUtil().inputStreamToRdfModel(is, Turtle))

  def fileOutputStreamFor(path: Path) =
    ZIO.acquireRelease(ZIO.attemptBlockingIO(new FileOutputStream(path.toFile)))(fos => ZIO.succeedBlocking(fos))

  def run =
    ZIO.foreach(discoverFiles(testDataPath))(pathToFile =>
      ZIO.debug(s"applying to $pathToFile") *> applyUpgradePluginTo(upgradePlugin, pathToFile)
    )
}
