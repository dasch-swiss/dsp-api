/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain;

import zio.*
import zio.nio.file.*
import zio.test.*

object ZipUtilitySpec extends ZIOSpecDefault {

  private val testFolderName  = "test-folder"
  private val testFileName    = "test-file.txt"
  private val testFileContent = List("This is a test file.", "Content is not important.")

  private val createTestDirectory = for {
    tmp <- Files.createTempDirectoryScoped(Some("tmp"), fileAttributes = Nil).logError
    _   <- Files.createDirectory(tmp / testFolderName)
    _   <- Files.createFile(tmp / testFolderName / testFileName)
    _   <- Files.writeLines(tmp / testFolderName / testFileName, testFileContent)
  } yield tmp

  private def verifyUnzipped(path: Path) = for {
    folderExists <- Files.isDirectory(path / testFolderName)
    _            <- ZIO.logError(s"Folder does not exist: ${path / testFolderName}").when(!folderExists)
    expectedFile  = path / testFolderName / testFileName
    fileContentIsCorrect <-
      Files.isRegularFile(expectedFile) && Files.readAllLines(expectedFile).map(_ == testFileContent)
    _ <- ZIO.logError(s"File content $expectedFile is not correct").when(!fileContentIsCorrect)
  } yield folderExists && fileContentIsCorrect

  override val spec = suite("ZipUtility")(
    test("creating the test directory should work") {
      for {
        tmp              <- createTestDirectory
        createdCorrectly <- verifyUnzipped(tmp)
      } yield assertTrue(createdCorrectly)
    },
    test("should zip/unzip a folder with a single file") {
      for {
        zipThis     <- createTestDirectory
        tmpZipped   <- Files.createTempDirectoryScoped(Some("zipped"), fileAttributes = Nil)
        zipped      <- ZipUtility.zipFolder(zipThis, tmpZipped)
        tmpUnzipped <- Files.createTempDirectoryScoped(Some("unzipped"), fileAttributes = Nil)
        unzipped    <- ZipUtility.unzipFile(zipped, tmpUnzipped)
        result      <- verifyUnzipped(unzipped)
      } yield assertTrue(result)
    }
  )
}
