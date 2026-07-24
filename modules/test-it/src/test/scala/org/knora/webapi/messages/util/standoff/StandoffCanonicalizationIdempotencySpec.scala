/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.util.standoff

import org.junit.runner.RunWith
import zio.*
import zio.test.*

import java.nio.file.Paths

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.E2EZSpec
import org.knora.webapi.messages.v2.responder.standoffmessages.GetMappingResponseV2
import org.knora.webapi.slice.common.StandoffMappingIri
import org.knora.webapi.slice.standoff.service.StandoffMappingService
import org.knora.webapi.util.FileUtil

/**
 * Tier-1 idempotency spike for the proposed "canonicalize rich text" feature (standard mapping only).
 *
 * The feature would expose `canonicalize(xml) = render(parse(xml))` so a client can normalise its own
 * source and compare it against the `textValueAsXml` dsp-api returns (which is itself `render(parse(...))`).
 * That comparison is only sound if canonicalisation is a FIXED POINT:
 *
 * {{{
 *     canonicalize(canonicalize(x)) == canonicalize(x)        // byte-identical
 * }}}
 *
 * The existing round-trip specs only assert input/output ISOMORPHISM (the known, expected gap that makes a
 * first pass non-identical to arbitrary input). None assert the second-pass fixed point -- which is exactly
 * what the whole comparison scheme rests on. This spec fills that gap for the standard mapping.
 *
 * Scope note: this exercises only data we control (repo fixtures + hand-built edge cases). Verifying against
 * a customer's real corpus is a separate, later gate.
 */
@RunWith(classOf[DspZTestJUnitRunner])
class StandoffCanonicalizationIdempotencySpec extends E2EZSpec {

  private val getStandardMapping: RIO[StandoffMappingService, GetMappingResponseV2] =
    ZIO.serviceWithZIO[StandoffMappingService](_.getMappingV2(StandoffMappingIri.StandardMapping))

  /** Real standard-mapping fixtures in the repo (name -> path). */
  private val fixtureCorpus: List[(String, String)] = List(
    "StandardHTML"               -> "test_data/test_route/texts/StandardHTML.xml",
    "StandardHTML-internal-link" -> "test_data/test_route/texts/StandardHTML-internal-link.xml",
  )

  /** Hand-built documents that deliberately stress the known normalisations (name -> XML). */
  private val inlineCorpus: List[(String, String)] = List(
    // Highest risk: footnote content is escaped XML stored in an ATTRIBUTE, escaped out again on render.
    "footnote-embedded-markup" ->
      """<text documentType="html"><p>See<footnote content="&lt;p&gt;A &lt;strong&gt;bold&lt;/strong&gt; note&lt;/p&gt;"/> in the margin.</p></text>""",
    // Empty elements: <br></br> should collapse to the same self-closing form as <br/>.
    "empty-vs-self-closing" ->
      """<text documentType="html"><p>line one<br></br>line two<br/>done</p></text>""",
    // Entity normalisation: named vs numeric (decimal/hex) forms.
    "entities" ->
      """<text documentType="html"><p>amp &amp; lt &lt; nbsp&#160;num&#38;done</p></text>""",
    // Deeply nested inline formatting.
    "nested-inline" ->
      """<text documentType="html"><p><strong><em>bold italic</em></strong> then <sub>a<sup>b</sup></sub> and <u>underlined</u></p></text>""",
  )

  /** The XML declaration dsp-api prepends to every rendered text value. */
  private val xmlDeclaration = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"

  /**
   * Whitespace-shaped inputs. The round-trip preserves whitespace verbatim (it only prepends the XML
   * declaration), so canonicalisation is whitespace-SENSITIVE: these documents are already canonical
   * except for that declaration, which is exactly what [[whitespacePreservationTest]] asserts.
   */
  private val whitespaceCorpus: List[(String, String)] = List(
    "ws-leading-trailing" -> "<text documentType=\"html\"><p>  hello  </p></text>",
    "ws-inner-runs"       -> "<text documentType=\"html\"><p>a    b</p></text>",
    "ws-newline-in-text"  -> "<text documentType=\"html\"><p>line1\nline2</p></text>",
    // indentation / newlines between block elements (the "pretty-printed source" case)
    "ws-inter-block-indent" -> "<text documentType=\"html\">\n  <p>a</p>\n  <p>b</p>\n</text>",
    // whitespace between two separator-inserting <p> elements (the separator char must not leak into XML)
    "ws-separator-adjacent" -> "<text documentType=\"html\"><p>a</p>   <p>b</p></text>",
    // significant whitespace inside <pre> must be preserved exactly
    "ws-pre-significant" -> "<text documentType=\"html\"><pre>line1\n    indented\nline3</pre></text>",
  )

  /** Human-readable pointer to the first character where two renders diverge (only used on failure). */
  private def firstDivergence(a: String, b: String): String = {
    val idx                      = a.zip(b).indexWhere { case (x, y) => x != y }
    val at                       = if (idx < 0) math.min(a.length, b.length) else idx
    val window                   = 60
    val from                     = math.max(0, at - window)
    def slice(s: String): String = s.substring(from, math.min(s.length, at + window)).replace("\n", "\\n")
    s"    first divergence at index $at\n    pass1: ...${slice(a)}...\n    pass2: ...${slice(b)}..."
  }

  private def idempotencyTest(name: String, loadXml: Task[String]) =
    test(name) {
      for {
        mapping <- getStandardMapping
        input   <- loadXml
        pass1   <- ZIO.attempt(StandoffTagUtilV2.canonicalize(input, mapping))
        pass2   <- ZIO.attempt(StandoffTagUtilV2.canonicalize(pass1, mapping))
        idem     = pass1 == pass2
        _       <- ZIO.debug(
               s"[$name] input==pass1: ${input == pass1}  |  idempotent(pass1==pass2): $idem  " +
                 s"(len in=${input.length}, pass1=${pass1.length})",
             )
        _ <- ZIO.when(!idem)(ZIO.debug(firstDivergence(pass1, pass2)))
      } yield assertTrue(pass1 == pass2)
    }

  /**
   * Asserts that a document which is already canonical (apart from the XML declaration) round-trips to
   * exactly itself with the declaration prepended -- i.e. whitespace and structure are preserved verbatim,
   * with no collapsing, trimming or normalisation.
   */
  private def whitespacePreservationTest(name: String, xml: String) =
    test(name) {
      for {
        mapping   <- getStandardMapping
        canonical <- ZIO.attempt(StandoffTagUtilV2.canonicalize(xml, mapping))
      } yield assertTrue(canonical == xmlDeclaration + xml)
    }

  override val e2eSpec =
    suite("Standard-mapping canonicalisation idempotency (canonicalize(canonicalize(x)) == canonicalize(x))")(
      suite("repo fixtures + hand-built edge cases")(
        (
          fixtureCorpus.map { case (n, p) => idempotencyTest(n, ZIO.attempt(FileUtil.readTextFile(Paths.get(p)))) } ++
            inlineCorpus.map { case (n, x) => idempotencyTest(n, ZIO.succeed(x)) } ++
            whitespaceCorpus.map { case (n, x) => idempotencyTest(n, ZIO.succeed(x)) }
        )*,
      ),
      suite("whitespace is preserved verbatim (only the XML declaration is added)")(
        whitespaceCorpus.map { case (n, x) => whitespacePreservationTest(n, x) }*,
      ),
      // hamlet.xml is very large; the existing hamlet round-trip specs are @@ ignore for memory reasons.
      // Kept here as an opt-in stress case: remove the @@ TestAspect.ignore to run it manually.
      suite("large document (opt-in; memory-heavy, ignored by default)")(
        idempotencyTest(
          "hamlet",
          ZIO.attempt(FileUtil.readTextFile(Paths.get("test_data/generated_test_data/resourcesR2RV2/hamlet.xml"))),
        ),
      ) @@ TestAspect.ignore,
    )
}
