/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.common

import org.apache.tika.Tika
import org.apache.tika.metadata.Metadata
import org.apache.tika.metadata.TikaCoreProperties
import org.junit.runner.RunWith
import zio.test.*

import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path

import org.knora.testrunner.DspZTestJUnitRunner

/**
 * Feasibility spike for mime-type backfill: what `tika-core` alone (no parser packages) detects
 * from the files in ./test-mimes, by filename, by content, and by both.
 */
@RunWith(classOf[DspZTestJUnitRunner])
class TikaMimeTypeDetectionSpec extends ZIOSpecDefault {

  private val tika = new Tika()

  // filename -> (byName, byContent, byBoth)
  private val cases = Seq(
    ("Screenshot 2026-06-29 at 22.25.15.png", "image/png", "image/png", "image/png"),
    ("Screen Recording 2026-09-04 at 12.53.35.mov", "video/quicktime", "video/quicktime", "video/quicktime"),
    ("Wieniawski-Legende.pdf", "application/pdf", "application/pdf", "application/pdf"),
    ("out.pdf", "application/pdf", "application/pdf", "application/pdf"),
    ("out.midi", "audio/midi", "audio/midi", "audio/midi"),
    // LilyPond source: unknown extension, so only the content says it is text.
    ("out.ly", "application/octet-stream", "text/plain", "text/plain"),
  )

  private def detectBoth(file: Path): String = {
    val metadata = new Metadata()
    metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, file.getFileName.toString)
    tika.detect(new ByteArrayInputStream(Files.readAllBytes(file)), metadata)
  }

  override val spec: Spec[Any, Any] = suite("Tika mime-type detection (tika-core, no parsers)")(
    cases.map { case (name, byName, byContent, byBoth) =>
      test(s"$name :: $byBoth") {
        val file = Path.of("test-mimes", name)
        assertTrue(
          tika.detect(name) == byName,
          tika.detect(Files.readAllBytes(file)) == byContent,
          detectBoth(file) == byBoth,
        )
      }
    },
  )
}
