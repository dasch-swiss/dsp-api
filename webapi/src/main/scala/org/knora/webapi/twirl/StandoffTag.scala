/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and André Fatton.
 *
 * This file is part of Knora.
 *
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.twirl

import java.util.UUID

import org.knora.webapi.IRI
import org.knora.webapi.messages.v1.responder.valuemessages.{KnoraCalendarV1, KnoraPrecisionV1}

/**
  *
  * The following case classes represent standoff classes and attributes
  * that are about to be added to the triplestore.
  *
  */


/**
  * Represents an attribute for a [[StandoffTagV1]].
  *
  * @param key      IRI of the attribute (predicate).
  * @param value    value of the attribute.
  * @param datatype datatype of the attribute.
  */
case class StandoffTagAttributeV1(key: IRI, value: String, datatype: String)

/**
  * A trait representing the required properties for a `knora-base:StandoffTag`
  */
trait StandoffTagV1 {

    def name: IRI

    def uuid: UUID

    def startPosition: Int

    def endPosition: Int

    def startIndex: Int

    def endIndex: Option[Int]

    def startParentIndex: Option[Int]

    def endParentIndex: Option[Int]

    def attributes: Seq[StandoffTagAttributeV1]

}

/**
  * Represents a `knora-base:StandoffTag` or any subclass of it
  * that is not a subclass of a data type standoff tag (e.g. `knora-base:StandoffDateTag`).
  *
  * @param name             the IRI of the standoff class to be created.
  * @param uuid             a [[UUID]] representing this tag and any other tags that
  *                         point to semantically equivalent content in other versions of the same text.
  * @param startPosition    the start position of the range of characters marked up with this tag.
  * @param endPosition      the end position of the range of characters marked up with this tag.
  * @param startIndex       the index of this tag (start index in case of a virtual hierarchy tag that has two parents). Indexes are numbered from 0 within the context of a particular text,
  *                         and make it possible to order tags that share the same position.
  * @param endIndex         the index of the end position (only in case of a virtual hierarchy tag).
  * @param startParentIndex the index of the parent node (start index in case of a virtual hierarchy tag that has two parents), if any, that contains the start position.
  * @param endParentIndex   the index of the the parent node (only in case of a virtual hierarchy tag), if any, that contains the end position.
  * @param attributes       the attributes attached to this tag.
  */
case class StandoffBaseTagV1(name: IRI,
                             uuid: UUID,
                             startPosition: Int,
                             endPosition: Int,
                             startIndex: Int,
                             endIndex: Option[Int],
                             startParentIndex: Option[Int],
                             endParentIndex: Option[Int],
                             attributes: Seq[StandoffTagAttributeV1]) extends StandoffTagV1

/**
  * Represents a `knora-base:StandoffLinkTag` or any subclass of it.
  *
  * @param name               the IRI of the standoff class to be created.
  * @param uuid               a [[UUID]] representing this tag and any other tags that
  *                           point to semantically equivalent content in other versions of the same text.
  * @param startPosition      the start position of the range of characters marked up with this tag.
  * @param endPosition        the end position of the range of characters marked up with this tag.
  * @param startIndex         the index of this tag (start index in case of a virtual hierarchy tag that has two parents). Indexes are numbered from 0 within the context of a particular text,
  *                           and make it possible to order tags that share the same position.
  * @param endIndex           the index of the end position (only in case of a virtual hierarchy tag).
  * @param startParentIndex   the index of the parent node (start index in case of a virtual hierarchy tag that has two parents), if any, that contains the start position.
  * @param endParentIndex     the index of the the parent node (only in case of a virtual hierarchy tag), if any, that contains the end position.
  * @param attributes         the attributes attached to this tag.
  * @param standoffTagHasLink the resource IRI referred to.
  */
case class StandoffLinkTagV1(name: IRI,
                             uuid: UUID,
                             startPosition: Int,
                             endPosition: Int,
                             startIndex: Int,
                             endIndex: Option[Int],
                             startParentIndex: Option[Int],
                             endParentIndex: Option[Int],
                             attributes: Seq[StandoffTagAttributeV1],
                             standoffTagHasLink: IRI) extends StandoffTagV1

/**
  * Represents a `knora-base:StandoffDateTag` or any subclass of it.
  *
  * @param name                   the IRI of the standoff class to be created.
  * @param uuid                   a [[UUID]] representing this tag and any other tags that
  *                               point to semantically equivalent content in other versions of the same text.
  * @param startPosition          the start position of the range of characters marked up with this tag.
  * @param endPosition            the end position of the range of characters marked up with this tag.
  * @param startIndex             the index of this tag (start index in case of a virtual hierarchy tag that has two parents). Indexes are numbered from 0 within the context of a particular text,
  *                               and make it possible to order tags that share the same position.
  * @param endIndex               the index of the end position (only in case of a virtual hierarchy tag).
  * @param startParentIndex       the index of the parent node (start index in case of a virtual hierarchy tag that has two parents), if any, that contains the start position.
  * @param endParentIndex         the index of the the parent node (only in case of a virtual hierarchy tag), if any, that contains the end position.
  * @param attributes             the attributes attached to this tag.
  * @param valueHasCalendar       the calendar used for the given date.
  * @param valueHasStartPrecision the precision of the start data.
  * @param valueHasEndPrecision   the precision of the end date.
  * @param valueHasStartJDC       the Julian Day Count of the start date.
  * @param valueHasEndJDC         the Julian Day Count of the end date.
  */
case class StandoffDateTagV1(name: IRI,
                             uuid: UUID,
                             startPosition: Int,
                             endPosition: Int,
                             startIndex: Int,
                             endIndex: Option[Int],
                             startParentIndex: Option[Int],
                             endParentIndex: Option[Int],
                             attributes: Seq[StandoffTagAttributeV1],
                             valueHasCalendar: KnoraCalendarV1.Value,
                             valueHasStartPrecision: KnoraPrecisionV1.Value,
                             valueHasEndPrecision: KnoraPrecisionV1.Value,
                             valueHasStartJDC: Int,
                             valueHasEndJDC: Int) extends StandoffTagV1

/**
  * Represents a `knora-base:StandoffUriTag` or any subclass of it.
  *
  * @param name             the IRI of the standoff class to be created.
  * @param uuid             a [[UUID]] representing this tag and any other tags that
  *                         point to semantically equivalent content in other versions of the same text.
  * @param startPosition    the start position of the range of characters marked up with this tag.
  * @param endPosition      the end position of the range of characters marked up with this tag.
  * @param startIndex       the index of this tag (start index in case of a virtual hierarchy tag that has two parents). Indexes are numbered from 0 within the context of a particular text,
  *                         and make it possible to order tags that share the same position.
  * @param endIndex         the index of the end position (only in case of a virtual hierarchy tag).
  * @param startParentIndex the index of the parent node (start index in case of a virtual hierarchy tag that has two parents), if any, that contains the start position.
  * @param endParentIndex   the index of the the parent node (only in case of a virtual hierarchy tag), if any, that contains the end position.
  * @param attributes       the attributes attached to this tag.
  * @param valueHasUri      the URI the standoff class points to.
  */
case class StandoffUriTagV1(name: IRI,
                            uuid: UUID,
                            startPosition: Int,
                            endPosition: Int,
                            startIndex: Int,
                            endIndex: Option[Int],
                            startParentIndex: Option[Int],
                            endParentIndex: Option[Int],
                            attributes: Seq[StandoffTagAttributeV1],
                            valueHasUri: String) extends StandoffTagV1

/**
  * Represents a `knora-base:StandoffColorTag` or any subclass of it.
  *
  * @param name             the IRI of the standoff class to be created.
  * @param uuid             a [[UUID]] representing this tag and any other tags that
  *                         point to semantically equivalent content in other versions of the same text.
  * @param startPosition    the start position of the range of characters marked up with this tag.
  * @param endPosition      the end position of the range of characters marked up with this tag.
  * @param startIndex       the index of this tag (start index in case of a virtual hierarchy tag that has two parents). Indexes are numbered from 0 within the context of a particular text,
  *                         and make it possible to order tags that share the same position.
  * @param endIndex         the index of the end position (only in case of a virtual hierarchy tag).
  * @param startParentIndex the index of the parent node (start index in case of a virtual hierarchy tag that has two parents), if any, that contains the start position.
  * @param endParentIndex   the index of the the parent node (only in case of a virtual hierarchy tag), if any, that contains the end position.
  * @param attributes       the attributes attached to this tag.
  * @param valueHasColor    the color the standoff tag represents.
  */
case class StandoffColorTagV1(name: IRI,
                              uuid: UUID,
                              startPosition: Int,
                              endPosition: Int,
                              startIndex: Int,
                              endIndex: Option[Int],
                              startParentIndex: Option[Int],
                              endParentIndex: Option[Int],
                              attributes: Seq[StandoffTagAttributeV1],
                              valueHasColor: String) extends StandoffTagV1

/**
  * Represents a `knora-base:StandoffIntegerTag` or any subclass of it.
  *
  * @param name             the IRI of the standoff class to be created.
  * @param uuid             a [[UUID]] representing this tag and any other tags that
  *                         point to semantically equivalent content in other versions of the same text.
  * @param startPosition    the start position of the range of characters marked up with this tag.
  * @param endPosition      the end position of the range of characters marked up with this tag.
  * @param startIndex       the index of this tag (start index in case of a virtual hierarchy tag that has two parents). Indexes are numbered from 0 within the context of a particular text,
  *                         and make it possible to order tags that share the same position.
  * @param endIndex         the index of the end position (only in case of a virtual hierarchy tag).
  * @param startParentIndex the index of the parent node (start index in case of a virtual hierarchy tag that has two parents), if any, that contains the start position.
  * @param endParentIndex   the index of the the parent node (only in case of a virtual hierarchy tag), if any, that contains the end position.
  * @param attributes       the attributes attached to this tag.
  * @param valueHasInteger  the integer value the standoff tag represents.
  */
case class StandoffIntegerTagV1(name: IRI,
                                uuid: UUID,
                                startPosition: Int,
                                endPosition: Int,
                                startIndex: Int,
                                endIndex: Option[Int],
                                startParentIndex: Option[Int],
                                endParentIndex: Option[Int],
                                attributes: Seq[StandoffTagAttributeV1],
                                valueHasInteger: String) extends StandoffTagV1

/**
  * Represents a `knora-base:StandoffDecimalTag` or any subclass of it.
  *
  * @param name             the IRI of the standoff class to be created.
  * @param uuid             a [[UUID]] representing this tag and any other tags that
  *                         point to semantically equivalent content in other versions of the same text.
  * @param startPosition    the start position of the range of characters marked up with this tag.
  * @param endPosition      the end position of the range of characters marked up with this tag.
  * @param startIndex       the index of this tag (start index in case of a virtual hierarchy tag that has two parents). Indexes are numbered from 0 within the context of a particular text,
  *                         and make it possible to order tags that share the same position.
  * @param endIndex         the index of the end position (only in case of a virtual hierarchy tag).
  * @param startParentIndex the index of the parent node (start index in case of a virtual hierarchy tag that has two parents), if any, that contains the start position.
  * @param endParentIndex   the index of the the parent node (only in case of a virtual hierarchy tag), if any, that contains the end position.
  * @param attributes       the attributes attached to this tag.
  * @param valueHasDecimal  the decimal (floating point) value the standoff tag represents.
  */
case class StandoffDecimalTagV1(name: IRI,
                                uuid: UUID,
                                startPosition: Int,
                                endPosition: Int,
                                startIndex: Int,
                                endIndex: Option[Int],
                                startParentIndex: Option[Int],
                                endParentIndex: Option[Int],
                                attributes: Seq[StandoffTagAttributeV1],
                                valueHasDecimal: BigDecimal) extends StandoffTagV1

/**
  * Represents a `knora-base:StandoffIntervalTag` or any subclass of it.
  *
  * @param name                  the IRI of the standoff class to be created.
  * @param uuid                  a [[UUID]] representing this tag and any other tags that
  *                              point to semantically equivalent content in other versions of the same text.
  * @param startPosition         the start position of the range of characters marked up with this tag.
  * @param endPosition           the end position of the range of characters marked up with this tag.
  * @param startIndex            the index of this tag (start index in case of a virtual hierarchy tag that has two parents). Indexes are numbered from 0 within the context of a particular text,
  *                              and make it possible to order tags that share the same position.
  * @param endIndex              the index of the end position (only in case of a virtual hierarchy tag).
  * @param startParentIndex      the index of the parent node (start index in case of a virtual hierarchy tag that has two parents), if any, that contains the start position.
  * @param endParentIndex        the index of the the parent node (only in case of a virtual hierarchy tag), if any, that contains the end position.
  * @param attributes            the attributes attached to this tag.
  * @param valueHasIntervalStart the start of the interval the standoff tag represents.
  * @param valueHasIntervalEnd   the start of the interval the standoff tag represents.
  */
case class StandoffIntervalTagV1(name: IRI,
                                 uuid: UUID,
                                 startPosition: Int,
                                 endPosition: Int,
                                 startIndex: Int,
                                 endIndex: Option[Int],
                                 startParentIndex: Option[Int],
                                 endParentIndex: Option[Int],
                                 attributes: Seq[StandoffTagAttributeV1],
                                 valueHasIntervalStart: BigDecimal,
                                 valueHasIntervalEnd: BigDecimal) extends StandoffTagV1

/**
  * Represents a `knora-base:StandoffBooleanTag` or any subclass of it.
  *
  * @param name             the IRI of the standoff class to be created.
  * @param uuid             a [[UUID]] representing this tag and any other tags that
  *                         point to semantically equivalent content in other versions of the same text.
  * @param startPosition    the start position of the range of characters marked up with this tag.
  * @param endPosition      the end position of the range of characters marked up with this tag.
  * @param startIndex       the index of this tag (start index in case of a virtual hierarchy tag that has two parents). Indexes are numbered from 0 within the context of a particular text,
  *                         and make it possible to order tags that share the same position.
  * @param endIndex         the index of the end position (only in case of a virtual hierarchy tag).
  * @param startParentIndex the index of the parent node (start index in case of a virtual hierarchy tag that has two parents), if any, that contains the start position.
  * @param endParentIndex   the index of the the parent node (only in case of a virtual hierarchy tag), if any, that contains the end position.
  * @param attributes       the attributes attached to this tag.
  * @param valueHasBoolean  the Boolean value that the standoff tag represents.
  */
case class StandoffBooleanTagV1(name: IRI,
                                uuid: UUID,
                                startPosition: Int,
                                endPosition: Int,
                                startIndex: Int,
                                endIndex: Option[Int],
                                startParentIndex: Option[Int],
                                endParentIndex: Option[Int],
                                attributes: Seq[StandoffTagAttributeV1],
                                valueHasBoolean: Boolean) extends StandoffTagV1


