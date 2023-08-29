/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages

import org.knora.webapi.messages.v2.responder.standoffmessages.MappingXMLtoStandoff
import org.knora.webapi.messages.v2.responder.standoffmessages.StandoffDataTypeClasses
import org.knora.webapi.messages.v2.responder.standoffmessages.XMLStandoffDataTypeClass
import org.knora.webapi.messages.v2.responder.standoffmessages.XMLTag
import org.knora.webapi.messages.v2.responder.standoffmessages.XMLTagToStandoffClass

import OntologyConstants.Standoff._
import OntologyConstants.KnoraBase._

object StandoffConstants {
  val standardMapping: MappingXMLtoStandoff = MappingXMLtoStandoff(
    Map(
      "noNamespace" -> Map(
        "text" -> Map(
          "noClass" -> XMLTag(
            "text",
            XMLTagToStandoffClass(
              StandoffRootTag,
              Map("noNamespace" -> Map("documentType" -> standoffRootTagHasDocumentType)),
              None
            ),
            false
          )
        ),
        "p"      -> Map("noClass" -> XMLTag("p", XMLTagToStandoffClass(StandoffParagraphTag, Map.empty, None), true)),
        "h1"     -> Map("noClass" -> XMLTag("h1", XMLTagToStandoffClass(StandoffHeader1Tag, Map.empty, None), true)),
        "h2"     -> Map("noClass" -> XMLTag("h2", XMLTagToStandoffClass(StandoffHeader2Tag, Map.empty, None), true)),
        "h3"     -> Map("noClass" -> XMLTag("h3", XMLTagToStandoffClass(StandoffHeader3Tag, Map.empty, None), true)),
        "h4"     -> Map("noClass" -> XMLTag("h4", XMLTagToStandoffClass(StandoffHeader4Tag, Map.empty, None), true)),
        "h5"     -> Map("noClass" -> XMLTag("h5", XMLTagToStandoffClass(StandoffHeader5Tag, Map.empty, None), true)),
        "h6"     -> Map("noClass" -> XMLTag("h6", XMLTagToStandoffClass(StandoffHeader6Tag, Map.empty, None), true)),
        "strong" -> Map("noClass" -> XMLTag("strong", XMLTagToStandoffClass(StandoffBoldTag, Map.empty, None), false)),
        "em"     -> Map("noClass" -> XMLTag("em", XMLTagToStandoffClass(StandoffItalicTag, Map.empty, None), false)),
        "sub"    -> Map("noClass" -> XMLTag("sub", XMLTagToStandoffClass(StandoffSubscriptTag, Map.empty, None), false)),
        "sup"    -> Map("noClass" -> XMLTag("sup", XMLTagToStandoffClass(StandoffSuperscriptTag, Map.empty, None), false)),
        "u"      -> Map("noClass" -> XMLTag("u", XMLTagToStandoffClass(StandoffUnderlineTag, Map.empty, None), false)),
        "ol"     -> Map("noClass" -> XMLTag("ol", XMLTagToStandoffClass(StandoffOrderedListTag, Map.empty, None), true)),
        "ul"     -> Map("noClass" -> XMLTag("ul", XMLTagToStandoffClass(StandoffUnorderedListTag, Map.empty, None), true)),
        "li"     -> Map("noClass" -> XMLTag("li", XMLTagToStandoffClass(StandoffListElementTag, Map.empty, None), true)),
        "blockquote" -> Map(
          "noClass" -> XMLTag("blockquote", XMLTagToStandoffClass(StandoffBlockquoteTag, Map.empty, None), true)
        ),
        "table" -> Map("noClass" -> XMLTag("table", XMLTagToStandoffClass(StandoffTableTag, Map.empty, None), true)),
        "tbody" -> Map(
          "noClass" -> XMLTag("tbody", XMLTagToStandoffClass(StandoffTableBodyTag, Map.empty, None), true)
        ),
        "pre" -> Map("noClass" -> XMLTag("pre", XMLTagToStandoffClass(StandoffPreTag, Map.empty, None), true)),
        "hr"  -> Map("noClass" -> XMLTag("hr", XMLTagToStandoffClass(StandoffLineTag, Map.empty, None), true)),
        "br"  -> Map("noClass" -> XMLTag("br", XMLTagToStandoffClass(StandoffBrTag, Map.empty, None), true)),
        "strike" -> Map(
          "noClass" -> XMLTag("strike", XMLTagToStandoffClass(StandoffStrikethroughTag, Map.empty, None), false)
        ),
        "code" -> Map("noClass" -> XMLTag("code", XMLTagToStandoffClass(StandoffCodeTag, Map.empty, None), true)),
        "td"   -> Map("noClass" -> XMLTag("td", XMLTagToStandoffClass(StandoffTableCellTag, Map.empty, None), true)),
        "tr"   -> Map("noClass" -> XMLTag("tr", XMLTagToStandoffClass(StandoffTableRowTag, Map.empty, None), true)),
        "cite" -> Map("noClass" -> XMLTag("cite", XMLTagToStandoffClass(StandoffCiteTag, Map.empty, None), true)),
        "a" -> Map(
          "noClass" -> XMLTag(
            "a",
            XMLTagToStandoffClass(
              StandoffUriTag,
              Map.empty,
              Some(XMLStandoffDataTypeClass(StandoffDataTypeClasses.StandoffUriTag, "href"))
            ),
            false
          ),
          "salsah-link" -> XMLTag(
            "a",
            XMLTagToStandoffClass(
              StandoffLinkTag,
              Map.empty,
              Some(XMLStandoffDataTypeClass(StandoffDataTypeClasses.StandoffLinkTag, "href"))
            ),
            false
          ),
          "internal-link" -> XMLTag(
            "a",
            XMLTagToStandoffClass(
              StandoffInternalReferenceTag,
              Map.empty,
              Some(XMLStandoffDataTypeClass(StandoffDataTypeClasses.StandoffInternalReferenceTag, "href"))
            ),
            false
          )
        )
      )
    ),
    None
  )
}
