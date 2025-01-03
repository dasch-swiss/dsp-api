/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import zio.test.{Gen, ZIOSpecDefault, assertTrue, check}

object AssetFilenameSpec extends ZIOSpecDefault {
  val spec = suite("AssetFilenameSpec")(
    test("should allow valid filenames") {
      val validFilenames = Seq(
        "0.jpg",
        "Foo.tif",
        "Foo Bar.csv",
        "Foo-Bar.mp4",
        "808205.tif",
        "leoo_html.xsl",
        "02_Robson_Introduction to IIIF.mp4",
        "Raemy_Gautschy_Elaboration d'un processus pour les images 3D reposant sur IIIF_Humanistica2023.pdf",
        "70. Oratorio di Giovanni  Frammento con Gesù dell'ingresso a Gerusalemme .jpg",
        "disegni e copie, Vat.lat.9849, f. 66r. (cf emerick nota 92)jpg.jpg",
        "öäüßÖÄÜ.jpg",                          // German alphabet
        "éèêëàâæçîïôœùûüÿÉÈÊËÀÂÆÇÎÏÔŒÙÛÜŸ.png", // Other Latin characters
        "= `'+?!<>|.jpg",                       // Special characters
        "漢字.jpg",                               // Kanji
        "ひらがな.jpg",                             // Hiragana
        "カタカナ.jpg",                             // Katakana
        "한글.jpg",                               // Hangul
        "አማርኛ.jpg",                             // Amharic
        "العربية.jpg",                          // Arabic
        "հայերեն.jpg",                          // Armenian
        "български.jpg",                        // Bulgarian
        "中文.jpg",                               // Chinese
      )
      check(Gen.fromIterable(validFilenames)) { str =>
        val actual: Either[String, String] = AssetFilename.from(str).map(_.value)
        assertTrue(actual == Right(str))
      }
    },
    test("should not allow invalid filenames missing a valid extension") {
      val filenamesWithoutValidExtension = Seq(
        "Foo",
        "Foo.",
        "Foo.bar",
        "Foo.png.bar",
      )
      check(Gen.fromIterable(filenamesWithoutValidExtension)) { str =>
        val actual: Either[String, String] = AssetFilename.from(str).map(_.value)
        assertTrue(actual == Left("Filename must have a valid file extension"))
      }
    },
    test("should not allow invalid filenames containing path traversals") {
      val filenamesWithoutValidExtension = Seq(
        "./Foo.png",
        "Foo/../../bar.jpg",
        "Foo//foo.jpg",
      )
      check(Gen.fromIterable(filenamesWithoutValidExtension)) { str =>
        val actual: Either[String, String] = AssetFilename.from(str).map(_.value)
        assertTrue(actual == Left("Filename must not contain any path information"))
      }
    },
    test("should not allow invalid characters ") {
      val filenamesWithInvalidCharacters = Seq(
        "Foo#.png",
        "Foo$.png",
        "\uD83D\uDC4D.png", // Thumbs up emoji
      )
      check(Gen.fromIterable(filenamesWithInvalidCharacters)) { str =>
        val actual: Either[String, String] = AssetFilename.from(str).map(_.value)
        assertTrue(actual == Left("Filename contains invalid characters"))
      }
    },
  )
}
