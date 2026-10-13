/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.fair.domain.writers

import org.knora.webapi.slice.fair.domain.ResourceFacts

final case class MetaTag(name: String, content: String)

/** The `<meta name="DC.*">` tags of a resource landing page, in a fixed order, one tag per value. */
object DublinCoreMeta {

  /** `DC.description` is cut to this many Unicode code points. */
  private val DescriptionLimit = 1000

  def render(facts: ResourceFacts): List[MetaTag] = {
    val tags = List.newBuilder[MetaTag]
    tags += MetaTag("DC.title", facts.title)
    WriterFacts.personCreators(facts).foreach(c => tags += MetaTag("DC.creator", c))
    tags += MetaTag("DC.publisher", WriterFacts.PublisherName)
    tags += MetaTag("DC.identifier", facts.ark)
    tags += MetaTag("DC.type", WriterFacts.generalType(facts))
    tags += MetaTag("DC.date", facts.dateCreated.toString)
    facts.description.foreach(d => tags += MetaTag("DC.description", cut(d)))
    facts.file.flatMap(_.mimeType).foreach(m => tags += MetaTag("DC.format", m))
    tags += MetaTag("DC.relation", facts.projectArk)
    facts.licence.foreach { l =>
      tags += MetaTag("DC.rights", l.label)
      l.uri.foreach(u => tags += MetaTag("DC.rights", u))
    }
    tags += MetaTag("DC.accessRights", facts.accessLevel.coarUri)
    tags.result()
  }

  /** Cuts on a code point boundary, so a surrogate pair is never split. */
  private def cut(text: String): String =
    if (text.codePointCount(0, text.length) <= DescriptionLimit) text
    else text.substring(0, text.offsetByCodePoints(0, DescriptionLimit)) + "\u2026"
}
