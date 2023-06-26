package org.knora.webapi.store.triplestore.upgrade.plugins

import zio.test.ZIOSpecDefault
import org.knora.webapi.messages.util.rdf.RdfFeatureFactory
import org.knora.webapi.messages.util.rdf.RdfFormatUtil
import org.knora.webapi.messages.util.rdf.RdfModel

import zio._
import java.io.BufferedInputStream
import java.io.FileInputStream
import org.knora.webapi.messages.util.rdf.TriG

abstract class UpgradePluginZSpec extends ZIOSpecDefault {

  protected val rdfFormatUtil: RdfFormatUtil = RdfFeatureFactory.getRdfFormatUtil()

  /**
   * Parses a TriG file and returns it as an [[RdfModel]].
   *
   * @param path the file path of the TriG file.
   * @return an [[RdfModel]].
   */
  def trigFileToModel(path: String): Task[RdfModel] =
    ZIO.scoped {
      for {
        bis   <- ZIO.acquireRelease(acquire(path))(release(_))
        model <- ZIO.attempt(rdfFormatUtil.inputStreamToRdfModel(bis, TriG))
      } yield model
    }

  private def acquire(path: String): Task[BufferedInputStream] =
    ZIO.attemptBlockingIO(new BufferedInputStream(new FileInputStream(path)))
  private def release(bis: BufferedInputStream): UIO[Unit] = ZIO.attemptBlockingIO(bis.close()).orDie
}
