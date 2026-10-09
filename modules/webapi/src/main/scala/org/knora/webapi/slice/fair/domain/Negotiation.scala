/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.fair.domain

import java.nio.charset.StandardCharsets
import java.util.regex.Pattern

/** A machine-readable representation a landing page can redirect to. */
final case class Candidate(mediaType: String, url: String)

/** What a landing page answers with: the page itself, or a redirect to a representation. */
enum Decision {
  case Html
  case Redirect(url: String)
}

/**
 * The content negotiation decision for a landing page: an `Accept` header and the representations on offer in, HTML
 * or a redirect out. Pure; it never throws and never yields a client error.
 *
 * Rules:
 *  - Media types compare ASCII case-insensitively; a candidate matches only an exact type. A wildcard range counts
 *    towards HTML and never towards a candidate.
 *  - Parameters other than `q` are ignored. A missing `q` counts as 1; a repeated `q` in one entry is read left to
 *    right, so the last one wins. A `q` that is not a number in 0..1, or an entry without a media range, skips that
 *    entry; the others are still considered.
 *  - HTML's quality is the maximum over every entry matching `text/html`, `text/` plus a wildcard subtype, or the
 *    all-types wildcard, and 0 when none does.
 *  - A candidate wins only with a `q` strictly greater than HTML's. Among candidates at the same `q` the one listed
 *    first in the header wins; the order of the candidate list does not matter.
 *  - At most 20 raw entries are read, and a header longer than 2048 bytes counts as absent.
 */
object Negotiation {

  private val MaxRanges      = 20
  private val MaxHeaderBytes = 2048
  private val HtmlRanges     = List("text/html", "text/*", "*/*")
  private val Number         = Pattern.compile("[+-]?(\\d+\\.?\\d*|\\.\\d+)([eE][+-]?\\d+)?")

  def decide(accept: Option[String], candidates: List[Candidate]): Decision =
    accept.filter(_.getBytes(StandardCharsets.UTF_8).length <= MaxHeaderBytes) match {
      case None         => Decision.Html
      case Some(header) =>
        val entries     = header.split(",", -1).take(MaxRanges).toList.flatMap(parseEntry)
        val htmlQuality = entries.collect { case (range, q) if HtmlRanges.exists(asciiEqualsIgnoreCase(range, _)) => q }
          .foldLeft(0.0)(math.max)
        bestCandidate(entries, htmlQuality, candidates)
    }

  private def bestCandidate(entries: List[(String, Double)], htmlQuality: Double, candidates: List[Candidate]) = {
    var best: Option[(Double, String)] = None
    for {
      (range, q) <- entries
      if q > htmlQuality
      candidate <- candidates.find(c => asciiEqualsIgnoreCase(range, c.mediaType))
      if best.forall { case (bestQ, _) => q > bestQ }
    } best = Some((q, candidate.url))
    best.fold[Decision](Decision.Html) { case (_, url) => Decision.Redirect(url) }
  }

  private def parseEntry(entry: String): Option[(String, Double)] = {
    val parts = entry.split(";", -1).toList.map(trim)
    parts.headOption.filter(_.nonEmpty).flatMap { range =>
      val params = parts.tail.flatMap { p =>
        p.indexOf('=') match {
          case -1 => None
          case i  =>
            Some((p.substring(0, i), p.substring(i + 1))).filter { case (name, _) => asciiEqualsIgnoreCase(name, "q") }
        }
      }
      params
        .foldLeft(Option(1.0)) { case (acc, (_, value)) =>
          acc.flatMap(_ => parseQuality(trim(value)))
        }
        .map(range -> _)
    }
  }

  private def parseQuality(value: String): Option[Double] =
    Option.when(Number.matcher(value).matches())(value.toDouble).filter(q => q >= 0.0 && q <= 1.0)

  // Unicode White_Space, as Rust's str::trim uses; Java's String.trim only strips control characters.
  private def isSpace(c: Char): Boolean =
    (c >= '\t' && c <= '\r') || c == ' ' || c == '\u0085' || c == ' ' || c == ' ' ||
      (c >= ' ' && c <= ' ') || c == ' ' || c == ' ' || c == ' ' || c == ' ' ||
      c == '　'

  private def trim(s: String): String = s.dropWhile(isSpace).reverse.dropWhile(isSpace).reverse

  private def asciiLower(c: Char): Char = if (c >= 'A' && c <= 'Z') (c + 32).toChar else c

  private def asciiEqualsIgnoreCase(a: String, b: String): Boolean =
    a.length == b.length && a.indices.forall(i => asciiLower(a.charAt(i)) == asciiLower(b.charAt(i)))
}
