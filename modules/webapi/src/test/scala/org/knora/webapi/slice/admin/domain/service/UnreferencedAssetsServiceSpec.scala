/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service

import org.apache.jena.query.Dataset
import org.junit.runner.RunWith
import zio.*
import zio.nio.file.Path
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.admin.domain.service.ViewRestrictionsTestFixtures.anythingProject
import org.knora.webapi.slice.admin.domain.service.ViewRestrictionsTestFixtures.projectDataset
import org.knora.webapi.slice.admin.repo.AssetReferencesRepo
import org.knora.webapi.slice.api.admin.model.MaintenanceRequests.AssetId
import org.knora.webapi.store.triplestore.api.TriplestoreServiceInMemory

@RunWith(classOf[DspZTestJUnitRunner])
class UnreferencedAssetsServiceSpec extends ZIOSpecDefault {

  private val kb = "http://www.knora.org/ontology/knora-base#"

  private def asset(id: String, ext: String) = IngestAsset(id, s"$id.$ext", s"original-$id.$ext")

  private val orphan      = asset("orphan01", "jp2")
  private val deletedOnly = asset("deleted01", "jp2")
  private val liveImage   = asset("live01", "jp2")
  private val liveVideo   = asset("video01", "mp4")
  private val oldVersion  = asset("old01", "jpx")
  private val both        = asset("both01", "jp2")

  private val ingest = new DspIngestClient {
    def getAssetInfo(shortcode: Shortcode, assetId: AssetId): Task[AssetInfoResponse] = ZIO.dieMessage("not used")
    def listAssets(shortcode: Shortcode): Task[Chunk[IngestAsset]]                    =
      ZIO.succeed(Chunk(orphan, deletedOnly, liveImage, liveVideo, oldVersion, both))
    def exportProject(shortcode: Shortcode, outputFile: Path): Task[Option[Path]] = ZIO.dieMessage("not used")
    def eraseProject(shortcode: Shortcode): Task[Unit]                            = ZIO.dieMessage("not used")
    def importProject(shortcode: Shortcode, fileToImport: Path): Task[Path]       = ZIO.dieMessage("not used")
  }

  private def resource(iri: String, deleted: Boolean, values: String*): String =
    s"""<$iri> <${kb}attachedToProject> <${anythingProject.id.value}> ;
       |  <${kb}isDeleted> $deleted ;
       |  ${values.map(v => s"<${kb}hasStillImageFileValue> <$v>").mkString(" ;\n  ")} .
       |""".stripMargin

  private def fileValue(iri: String, filename: String, previous: Option[String] = None): String =
    s"""<$iri> <${kb}internalFilename> "$filename" ;
       |  ${previous.fold("")(p => s"<${kb}previousValue> <$p> ;")}
       |  <${kb}isDeleted> false .
       |""".stripMargin

  private val turtle: String =
    List(
      resource(
        "http://rdfh.ch/0001/deleted",
        deleted = true,
        "http://rdfh.ch/0001/deleted/v1",
        "http://rdfh.ch/0001/deleted/v2",
      ),
      fileValue("http://rdfh.ch/0001/deleted/v1", "deleted01.jp2"),
      fileValue("http://rdfh.ch/0001/deleted/v2", "both01.jp2"),
      resource(
        "http://rdfh.ch/0001/live",
        deleted = false,
        "http://rdfh.ch/0001/live/image",
        "http://rdfh.ch/0001/live/video",
        "http://rdfh.ch/0001/live/current",
        "http://rdfh.ch/0001/live/both",
      ),
      fileValue("http://rdfh.ch/0001/live/image", "live01.jp2"),
      fileValue("http://rdfh.ch/0001/live/video", "video01.mp4"),
      fileValue("http://rdfh.ch/0001/live/current", "current01.jpx", Some("http://rdfh.ch/0001/live/old")),
      fileValue("http://rdfh.ch/0001/live/old", "old01.jpx"),
      fileValue("http://rdfh.ch/0001/live/both", "both01.jp2"),
    ).mkString("\n")

  private val layers = ZLayer.makeSome[Ref[Dataset], UnreferencedAssetsService](
    AssetReferencesRepo.layer,
    StringFormatter.test,
    TriplestoreServiceInMemory.layer,
    UnreferencedAssetsService.layer,
    ZLayer.succeed(ingest),
  )

  private val find = ZIO.serviceWithZIO[UnreferencedAssetsService](_.find(anythingProject))

  val spec = suite("UnreferencedAssetsService")(
    test("lists an asset without a reference, and one referenced only by a deleted resource with the flag") {
      find.map(result =>
        assertTrue(
          result == Chunk(
            UnreferencedAsset("deleted01", "deleted01.jp2", "original-deleted01.jp2", true),
            UnreferencedAsset("orphan01", "orphan01.jp2", "original-orphan01.jp2", false),
          ),
        ),
      )
    },
    test("does not list an asset with a live reference, whatever its extension or version") {
      find.map { result =>
        val listed = result.map(_.assetId).toSet
        assertTrue(Set("live01", "video01", "old01", "both01").intersect(listed).isEmpty)
      }
    },
  ).provide(layers, projectDataset(turtle))
}
