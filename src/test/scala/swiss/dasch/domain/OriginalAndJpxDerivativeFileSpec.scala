/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import zio.nio.file.Path
import zio.test.{Gen, ZIOSpecDefault, assertTrue, check}

import DerivativeFile.JpxDerivativeFile

object OriginalAndJpxDerivativeFileSpec extends ZIOSpecDefault {

  private val derivativeFileSuite = suite("DerivativeFile")(
    test("can be created from a Path which is a derivative file jpx") {
      val gen = Gen.fromIterable(List("jpx", "JPX", "jp2", "JP2"))
      check(gen) { extension =>
        val path: Path = Path(s"/tmp/hello.$extension")
        assertTrue(JpxDerivativeFile.from(path).map(_.toPath).contains(path))
      }
    },
    test("cannot be created if filename is not a valid AssetId") {
      val path: Path = Path("/tmp/Name of this file is not an AssetId.jpx")
      assertTrue(JpxDerivativeFile.from(path).isEmpty)
    },
    test("cannot be created from original file") {
      val path: Path = Path(s"/tmp/hello.orig")
      assertTrue(JpxDerivativeFile.from(path).isEmpty)
    },
    test("cannot be created from directory") {
      val path: Path = Path(s"/tmp/hello/")
      assertTrue(JpxDerivativeFile.from(path).isEmpty)
    },
    test("cannot be created from hidden file") {
      val hiddenFiles = Gen.fromIterable(List(".hello.txt", ".hello.jpx"))
      check(hiddenFiles) { filename =>
        val path: Path = Path(s"/tmp/$filename")
        assertTrue(JpxDerivativeFile.from(path).isEmpty)
      }
    }
  )

  private val originalFileSpec = suite("OriginalFile")(
    test("can be created if Path ends with .orig") {
      val path: Path = Path("/tmp/test.orig")
      assertTrue(OriginalFile.from(path).map(_.toPath).contains(path))
    },
    test("cannot be created if filename is not a valid AssetId") {
      val path: Path = Path("/tmp/Name of this file is not an AssetId.orig")
      assertTrue(OriginalFile.from(path).isEmpty)
    },
    test("cannot be created if file is not an .orig file, e.g. directory or other extension") {
      val invalidFileExtensions = Gen.fromIterable(List(".png", ".orig.tiff", "/", ".txt", ""))
      check(invalidFileExtensions) { extension =>
        val path: Path = Path(s"/tmp/test$extension")
        assertTrue(OriginalFile.from(path).isEmpty)
      }
    },
    test("cannot be created from hidden file") {
      val hiddenFiles = Gen.fromIterable(List(".hello.txt", ".hello.orig", ".hello.tiff.orig"))
      check(hiddenFiles) { filename =>
        val path: Path = Path(s"/tmp/$filename")
        assertTrue(OriginalFile.from(path).isEmpty)
      }
    }
  )

  val spec = suite("OriginalAndDerivativeFileSpec")(derivativeFileSuite, originalFileSpec)
}
