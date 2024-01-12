/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import swiss.dasch.config.Configuration.StorageConfig
import swiss.dasch.test.SpecConfigurations
import swiss.dasch.test.SpecConstants.*
import swiss.dasch.test.SpecConstants.Projects.*
import zio.nio.file.Path
import zio.test.{Spec, TestEnvironment, ZIOSpecDefault, assertTrue}
import zio.{Chunk, Scope, ZIO, ZLayer}

object ProjectServiceSpec extends ZIOSpecDefault {

  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("ProjectService")(
      suite("listAllProjects")(
        test("should list all projects which contain assets in the asset directory") {
          for {
            projects <- ProjectService.listAllProjects()
          } yield assertTrue(projects.map(_.shortcode) == Chunk(existingProject))
        }
      ),
      suite("findProject path")(
        test("should find existing projects which contain at least one non hidden regular file") {
          for {
            project <- ProjectService.findProject(existingProject)
          } yield assertTrue(project.map(_.shortcode).contains(existingProject))
        },
        test("should not find not existing projects") {
          for {
            project <- ProjectService.findProject(nonExistentProject)
          } yield assertTrue(project.isEmpty)
        }
      ),
      suite("findAssetsOfProject path")(
        test("should find asset infos from existing project") {
          for {
            infos <- ProjectService.findAssetInfosOfProject(existingProject).runCollect
          } yield assertTrue(infos.nonEmpty)
        },
        test("should not find non existing projects") {
          for {
            infos <- ProjectService.findAssetInfosOfProject(nonExistentProject).runCollect
          } yield assertTrue(infos.isEmpty)
        }
      ),
      suite("zipping a project")(
        test("given it does not exist, should return None") {
          for {
            zip <- ProjectService.zipProject(nonExistentProject)
          } yield assertTrue(zip.isEmpty)
        },
        test("given it does exists, should zip and return file path") {
          for {
            tempDir <- ZIO.serviceWith[StorageConfig](_.tempPath)
            zip     <- ProjectService.zipProject(existingProject)
          } yield assertTrue(zip.contains(tempDir / "zipped" / "0001.zip"))
        }
      )
    ).provide(
      AssetInfoServiceLive.layer,
      FileChecksumServiceLive.layer,
      ProjectService.layer,
      SpecConfigurations.storageConfigLayer,
      StorageServiceLive.layer
    )
}
