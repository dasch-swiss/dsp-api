/*
 * Copyright © 2015-2019 the contributors (see Contributors.md).
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

package org.knora.webapi.responders.v1

import akka.actor.ActorSystem
import org.knora.webapi.Settings
import org.knora.webapi.messages.v1.responder.resourcemessages._
import org.knora.webapi.messages.v1.responder.valuemessages._
import org.knora.webapi.messages.v2.responder.standoffmessages.{MappingXMLtoStandoff, XMLTag}


object ResourcesResponderV1SpecFullData {

    implicit lazy val system = ActorSystem("webapi")

    val settings = Settings(system)

    // The expected response to a "full" resource request for a book.
    val expectedBookResourceFullResponse = ResourceFullResponseV1(
        access = "OK",
        incoming = Vector(
            IncomingV1(
                value = Some("DEMO_"),
                resinfo = ResourceInfoV1(
                    firstproperty = Some("DEMO_"),
                    value_of = 0,
                    lastmod = "0000-00-00 00:00:00",
                    resclass_has_location = false,
                    resclass_name = "object",
                    locdata = None,
                    locations = None,
                    preview = None,
                    restype_iconsrc = Some(settings.salsah1BaseUrl + settings.salsah1ProjectIconsBasePath + "knora-base/link.gif"),
                    restype_description = Some("Verkn\u00FCpfung mehrerer Resourcen"),
                    restype_label = Some("Verkn\u00FCpfungsobjekt"),
                    restype_name = Some("http://www.knora.org/ontology/knora-base#LinkObj"),
                    restype_id = "http://www.knora.org/ontology/knora-base#LinkObj",
                    person_id = "http://rdfh.ch/users/91e19f1e01",
                    project_id = "http://rdfh.ch/projects/0803"
                ),
                ext_res_id = ExternalResourceIDV1(
                    pid = "http://www.knora.org/ontology/knora-base#hasLinkTo",
                    id = "http://rdfh.ch/ab79ffa43935"
                )
            ),
            IncomingV1(
                value = Some("Diese drei Texte sind in einem Band zusammengebunden."),
                resinfo = ResourceInfoV1(
                    firstproperty = Some("Diese drei Texte sind in einem Band zusammengebunden."),
                    value_of = 0,
                    lastmod = "0000-00-00 00:00:00",
                    resclass_has_location = false,
                    resclass_name = "object",
                    locdata = None,
                    locations = None,
                    preview = None,
                    restype_iconsrc = Some(settings.salsah1BaseUrl + settings.salsah1ProjectIconsBasePath + "knora-base/link.gif"),
                    restype_description = Some("Verkn\u00FCpfung mehrerer Resourcen"),
                    restype_label = Some("Verkn\u00FCpfungsobjekt"),
                    restype_name = Some("http://www.knora.org/ontology/knora-base#LinkObj"),
                    restype_id = "http://www.knora.org/ontology/knora-base#LinkObj",
                    person_id = "http://rdfh.ch/users/b83acc5f05",
                    project_id = "http://rdfh.ch/projects/0803"
                ),
                ext_res_id = ExternalResourceIDV1(
                    pid = "http://www.knora.org/ontology/knora-base#hasLinkTo",
                    id = "http://rdfh.ch/cb1a74e3e2f6"
                )
            )
        ),
        props = Some(PropsV1(properties = Vector(
            PropertyV1(
                locations = Nil,
                value_rights = Vector(Some(6)),
                value_firstprops = Vector(None),
                value_iconsrcs = Vector(None),
                value_restype = Vector(None),
                comments = Vector(None),
                value_ids = Vector("http://rdfh.ch/c5058f3a/values/8653a672"),
                values = Vector(TextValueSimpleV1(
                    utf8str = "Berthold, der Bruder"
                )),
                occurrence = Some("0-n"),
                attributes = "maxlength=255;size=60",
                label = Some("Creator"),
                is_annotation = "0",
                guielement = Some("text"),
                guiorder = Some(2),
                valuetype_id = Some("http://www.knora.org/ontology/knora-base#TextValue"),
                regular_property = 1,
                pid = "http://www.knora.org/ontology/0803/incunabula#hasAuthor"
            ),
            PropertyV1(
                locations = Nil,
                value_rights = Vector(Some(6)),
                value_firstprops = Vector(None),
                value_iconsrcs = Vector(None),
                value_restype = Vector(None),
                comments = Vector(None),
                value_ids = Vector("http://rdfh.ch/c5058f3a/values/c3295339"),
                values = Vector(TextValueSimpleV1(
                    utf8str = "Zeitgl\u00F6cklein des Lebens und Leidens Christi"
                )),
                occurrence = Some("1-n"),
                attributes = "size=80;maxlength=255",
                label = Some("Titel"),
                is_annotation = "0",
                guielement = Some("text"),
                guiorder = Some(1),
                valuetype_id = Some("http://www.knora.org/ontology/knora-base#TextValue"),
                regular_property = 1,
                pid = "http://www.knora.org/ontology/0803/incunabula#title"
            ),
            PropertyV1(
                locations = Nil,
                value_rights = Vector(
                    Some(6),
                    Some(6),
                    Some(6)
                ),
                value_firstprops = Vector(
                    None,
                    None,
                    None
                ),
                value_iconsrcs = Vector(
                    None,
                    None,
                    None
                ),
                value_restype = Vector(
                    None,
                    None,
                    None
                ),
                comments = Vector(
                    None,
                    None,
                    None
                ),
                value_ids = Vector(
                    "http://rdfh.ch/c5058f3a/values/184e99ca01",
                    "http://rdfh.ch/c5058f3a/values/db77ec0302",
                    "http://rdfh.ch/c5058f3a/values/9ea13f3d02"
                ),
                values = Vector(
                    TextValueSimpleV1(
                        utf8str = "Schramm Bd. XXI, S. 27"
                    ),
                    TextValueSimpleV1(
                        utf8str = "GW 4168"
                    ),
                    TextValueSimpleV1(
                        utf8str = "ISTC ib00512000"
                    )
                ),
                occurrence = Some("0-n"),
                attributes = "cols=60;wrap=soft;rows=3",
                label = Some("Verweis"),
                is_annotation = "0",
                guielement = Some("textarea"),
                guiorder = Some(5),
                valuetype_id = Some("http://www.knora.org/ontology/knora-base#TextValue"),
                regular_property = 1,
                pid = "http://www.knora.org/ontology/0803/incunabula#citation"
            ),
            PropertyV1(
                locations = Nil,
                value_rights = Vector(Some(7)),
                value_firstprops = Vector(None),
                value_iconsrcs = Vector(None),
                value_restype = Vector(None),
                comments = Vector(None),
                value_ids = Vector("http://rdfh.ch/c5058f3a/values/92faf25701"),
                values = Vector(TextValueSimpleV1(
                    utf8str = "Universit\u00E4ts- und Stadtbibliothek K\u00F6ln, Sign: AD+S167"
                )),
                occurrence = Some("0-1"),
                attributes = "cols=60;rows=4;wrap=soft",
                label = Some("Standort"),
                is_annotation = "0",
                guielement = Some("textarea"),
                guiorder = Some(6),
                valuetype_id = Some("http://www.knora.org/ontology/knora-base#TextValue"),
                regular_property = 1,
                pid = "http://www.knora.org/ontology/0803/incunabula#location"
            ),
            PropertyV1(
                locations = Nil,
                value_rights = Vector(Some(7)),
                value_firstprops = Vector(None),
                value_iconsrcs = Vector(None),
                value_restype = Vector(None),
                comments = Vector(None),
                value_ids = Vector("http://rdfh.ch/c5058f3a/values/10e00c7acc2704"),
                values = Vector(TextValueSimpleV1(
                    utf8str = "http://www.ub.uni-koeln.de/cdm/compoundobject/collection/inkunabeln/id/1878/rec/1"
                )),
                occurrence = Some("0-1"),
                attributes = "size=60;maxlength=200",
                label = Some("URI"),
                is_annotation = "0",
                guielement = Some("text"),
                guiorder = Some(7),
                valuetype_id = Some("http://www.knora.org/ontology/knora-base#TextValue"),
                regular_property = 1,
                pid = "http://www.knora.org/ontology/0803/incunabula#url"
            ),
            PropertyV1(
                locations = Nil,
                value_rights = Vector(Some(6)),
                value_firstprops = Vector(None),
                value_iconsrcs = Vector(None),
                value_restype = Vector(None),
                comments = Vector(None),
                value_ids = Vector("http://rdfh.ch/c5058f3a/values/5524469101"),
                values = Vector(TextValueSimpleV1(
                    utf8str = "Dimension: 8\u00B0"
                )),
                occurrence = Some("0-1"),
                attributes = "cols=60;wrap=soft;rows=3",
                label = Some("Physische Beschreibung"),
                is_annotation = "0",
                guielement = Some("textarea"),
                guiorder = Some(9),
                valuetype_id = Some("http://www.knora.org/ontology/knora-base#TextValue"),
                regular_property = 1,
                pid = "http://www.knora.org/ontology/0803/incunabula#physical_desc"
            ),
            PropertyV1(
                locations = Nil,
                value_rights = Vector(Some(2)),
                value_firstprops = Vector(None),
                value_iconsrcs = Vector(None),
                value_restype = Vector(None),
                comments = Vector(None),
                value_ids = Vector("http://rdfh.ch/c5058f3a/values/0ca74ce5"),
                values = Vector(TextValueSimpleV1(
                    utf8str = "Basel"
                )),
                occurrence = Some("0-1"),
                attributes = "size=60;maxlength=100",
                label = Some("Ort der Herausgabe"),
                is_annotation = "0",
                guielement = Some("text"),
                guiorder = Some(4),
                valuetype_id = Some("http://www.knora.org/ontology/knora-base#TextValue"),
                regular_property = 1,
                pid = "http://www.knora.org/ontology/0803/incunabula#publoc"
            ),
            PropertyV1(
                locations = Nil,
                value_rights = Vector(Some(6)),
                value_firstprops = Vector(None),
                value_iconsrcs = Vector(None),
                value_restype = Vector(None),
                comments = Vector(None),
                value_ids = Vector("http://rdfh.ch/c5058f3a/values/cfd09f1e01"),
                values = Vector(DateValueV1(
                    calendar = KnoraCalendarV1.JULIAN,
                    dateval2 = "1492",
                    dateval1 = "1492",
                    era1="CE",
                    era2="CE"
                )),
                occurrence = Some("0-1"),
                attributes = "",
                label = Some("Datum der Herausgabe"),
                is_annotation = "0",
                guielement = Some("date"),
                guiorder = Some(5),
                valuetype_id = Some("http://www.knora.org/ontology/knora-base#DateValue"),
                regular_property = 1,
                pid = "http://www.knora.org/ontology/0803/incunabula#pubdate"
            ),
            PropertyV1(
                locations = Nil,
                value_rights = Vector(Some(6)),
                value_firstprops = Vector(None),
                value_iconsrcs = Vector(None),
                value_restype = Vector(None),
                comments = Vector(None),
                value_ids = Vector("http://rdfh.ch/c5058f3a/values/497df9ab"),
                values = Vector(TextValueSimpleV1(
                    utf8str = "Johann Amerbach"
                )),
                occurrence = Some("0-n"),
                attributes = "maxlength=255;size=60",
                label = Some("Verleger"),
                is_annotation = "0",
                guielement = Some("text"),
                guiorder = Some(3),
                valuetype_id = Some("http://www.knora.org/ontology/knora-base#TextValue"),
                regular_property = 1,
                pid = "http://www.knora.org/ontology/0803/incunabula#publisher"
            ),
            PropertyV1(
                locations = Nil,
                value_rights = Nil,
                value_firstprops = Nil,
                value_iconsrcs = Nil,
                value_restype = Nil,
                comments = Nil,
                value_ids = Nil,
                values = Nil,
                occurrence = Some("0-n"),
                attributes = "wrap=soft;width=95%;rows=7",
                label = Some("Kommentar"),
                is_annotation = "0",
                guielement = Some("textarea"),
                guiorder = Some(12),
                valuetype_id = Some("http://www.knora.org/ontology/knora-base#TextValue"),
                regular_property = 1,
                pid = "http://www.knora.org/ontology/0803/incunabula#book_comment"
            ),
            PropertyV1(
                locations = Nil,
                value_rights = Nil,
                value_firstprops = Nil,
                value_iconsrcs = Nil,
                value_restype = Nil,
                comments = Nil,
                value_ids = Nil,
                values = Nil,
                occurrence = Some("0-1"),
                attributes = "",
                label = Some("Beschreibung (Richtext)"),
                is_annotation = "0",
                guielement = Some("richtext"),
                guiorder = Some(2),
                valuetype_id = Some("http://www.knora.org/ontology/knora-base#TextValue"),
                regular_property = 1,
                pid = "http://www.knora.org/ontology/0803/incunabula#description"
            ),
            PropertyV1(
                locations = Nil,
                value_rights = Nil,
                value_firstprops = Nil,
                value_iconsrcs = Nil,
                value_restype = Nil,
                comments = Nil,
                value_ids = Nil,
                values = Nil,
                occurrence = Some("0-n"),
                attributes = "cols=60;wrap=soft;rows=3",
                label = Some("Anmerkung"),
                is_annotation = "0",
                guielement = Some("textarea"),
                guiorder = Some(10),
                valuetype_id = Some("http://www.knora.org/ontology/knora-base#TextValue"),
                regular_property = 1,
                pid = "http://www.knora.org/ontology/0803/incunabula#note"
            ),
            PropertyV1(
                locations = Nil,
                value_rights = Nil,
                value_firstprops = Nil,
                value_iconsrcs = Nil,
                value_restype = Nil,
                comments = Nil,
                value_ids = Nil,
                values = Nil,
                occurrence = Some("0-n"),
                attributes = "restypeid=http://www.knora.org/ontology/knora-base#Resource",
                label = Some("hat Standoff Link zu"),
                is_annotation = "0",
                guielement = None,
                guiorder = None,
                valuetype_id = Some("http://www.knora.org/ontology/knora-base#LinkValue"),
                regular_property = 1,
                pid = "http://www.knora.org/ontology/knora-base#hasStandoffLinkTo"
            )

        ))),
        resdata = Some(ResourceDataV1(
            rights = Some(6),
            iconsrc = Some(settings.salsah1BaseUrl + settings.salsah1ProjectIconsBasePath + "incunabula/book.gif"),
            restype_label = Some("Buch"),
            restype_name = "http://www.knora.org/ontology/0803/incunabula#book",
            res_id = "http://rdfh.ch/c5058f3a"
        )),
        resinfo = Some(ResourceInfoV1(
            firstproperty = Some("Zeitgl\u00F6cklein des Lebens und Leidens Christi"),
            value_of = 0,
            lastmod = "0000-00-00 00:00:00",
            resclass_has_location = false,
            resclass_name = "object",
            locdata = None,
            locations = None,
            preview = None,
            restype_iconsrc = Some(settings.salsah1BaseUrl + settings.salsah1ProjectIconsBasePath + "incunabula/book.gif"),
            restype_description = Some("Diese Resource-Klasse beschreibt ein Buch"),
            restype_label = Some("Buch"),
            restype_name = Some("http://www.knora.org/ontology/0803/incunabula#book"),
            restype_id = "http://www.knora.org/ontology/0803/incunabula#book",
            person_id = "http://rdfh.ch/users/91e19f1e01",
            project_id = "http://rdfh.ch/projects/0803"
        ))
    )

    // The expected response to a "full" resource request for a page.
    val expectedPageResourceFullResponse = ResourceFullResponseV1(
        access = "OK",
        incoming = Nil,
        props = Some(PropsV1(properties = Vector(
            PropertyV1(
                locations = Vector(
                    LocationV1(
                        protocol = "file",
                        duration = 0,
                        fps = 0,
                        path = "http://localhost:1024/knora/incunabula_0000000002.jp2/full/95,128/0/default.jpg",
                        ny = Some(128),
                        nx = Some(95),
                        origname = "ad+s167_druck1=0001.tif",
                        format_name = "JPEG2000"
                    ),
                    LocationV1(
                        protocol = "file",
                        duration = 0,
                        fps = 0,
                        path = "http://localhost:1024/knora/incunabula_0000000002.jp2/full/82,110/0/default.jpg",
                        ny = Some(110),
                        nx = Some(82),
                        origname = "ad+s167_druck1=0001.tif",
                        format_name = "JPEG2000"
                    ),
                    LocationV1(
                        protocol = "file",
                        duration = 0,
                        fps = 0,
                        path = "http://localhost:1024/knora/incunabula_0000000002.jp2/full/163,219/0/default.jpg",
                        ny = Some(219),
                        nx = Some(163),
                        origname = "ad+s167_druck1=0001.tif",
                        format_name = "JPEG2000"
                    ),
                    LocationV1(
                        protocol = "file",
                        duration = 0,
                        fps = 0,
                        path = "http://localhost:1024/knora/incunabula_0000000002.jp2/full/327,438/0/default.jpg",
                        ny = Some(438),
                        nx = Some(327),
                        origname = "ad+s167_druck1=0001.tif",
                        format_name = "JPEG2000"
                    ),
                    LocationV1(
                        protocol = "file",
                        duration = 0,
                        fps = 0,
                        path = "http://localhost:1024/knora/incunabula_0000000002.jp2/full/653,876/0/default.jpg",
                        ny = Some(876),
                        nx = Some(653),
                        origname = "ad+s167_druck1=0001.tif",
                        format_name = "JPEG2000"
                    ),
                    LocationV1(
                        protocol = "file",
                        duration = 0,
                        fps = 0,
                        path = "http://localhost:1024/knora/incunabula_0000000002.jp2/full/1307,1753/0/default.jpg",
                        ny = Some(1753),
                        nx = Some(1307),
                        origname = "ad+s167_druck1=0001.tif",
                        format_name = "JPEG2000"
                    ),
                    LocationV1(
                        protocol = "file",
                        duration = 0,
                        fps = 0,
                        path = "http://localhost:1024/knora/incunabula_0000000002.jp2/full/2613,3505/0/default.jpg",
                        ny = Some(3505),
                        nx = Some(2613),
                        origname = "ad+s167_druck1=0001.tif",
                        format_name = "JPEG2000"
                    )
                ),
                value_rights = Nil,
                value_firstprops = Nil,
                value_iconsrcs = Nil,
                value_restype = Nil,
                comments = Vector(None),
                value_ids = Vector("0"),
                values = Vector(IntegerValueV1(ival = 0)),
                occurrence = None,
                attributes = "",
                label = None,
                is_annotation = "0",
                guielement = Some("fileupload"),
                guiorder = Some(2147483647),
                valuetype_id = Some("-1"),
                regular_property = 1,
                pid = "__location__"
            ),
            PropertyV1(
                locations = Nil,
                value_rights = Vector(Some(6)),
                value_firstprops = Vector(None),
                value_iconsrcs = Vector(None),
                value_restype = Vector(None),
                comments = Vector(None),
                value_ids = Vector("http://rdfh.ch/8a0b1e75/values/61cb927602"),
                values = Vector(TextValueSimpleV1(
                    utf8str = "a1r, Titelblatt"
                )),
                occurrence = Some("0-1"),
                attributes = "size=8;maxlength=8",
                label = Some("Seitenbezeichnung"),
                is_annotation = "0",
                guielement = Some("text"),
                guiorder = Some(1),
                valuetype_id = Some("http://www.knora.org/ontology/knora-base#TextValue"),
                regular_property = 1,
                pid = "http://www.knora.org/ontology/0803/incunabula#pagenum"
            ),
            PropertyV1(
                locations = Nil,
                value_rights = Vector(Some(6)),
                value_firstprops = Vector(None),
                value_iconsrcs = Vector(None),
                value_restype = Vector(None),
                comments = Vector(None),
                value_ids = Vector("http://rdfh.ch/8a0b1e75/values/3e3d4dc0e922"),
                values = Vector(TextValueSimpleV1(
                    utf8str = "Titel: \"Das andechtig zitglo(e)gglyn | des lebens vnd lide(n)s christi nach | den xxiiij stunden v\u00DFgeteilt.\"\nHolzschnitt: Schlaguhr mit Zifferblatt f\u00FCr 24 Stunden, auf deren oberem Rand zu beiden Seiten einer Glocke die Verk\u00FCndigungsszene mit Maria (links) und dem Engel (rechts) zu sehen ist.\nBord\u00FCre: Ranken mit Fabelwesen, Holzschnitt.\nKolorierung: Rot, Blau, Gr\u00FCn, Gelb, Braun.\nBeschriftung oben Mitte (Graphitstift) \"B 1\"."
                )),
                occurrence = Some("0-1"),
                attributes = "",
                label = Some("Beschreibung (Richtext)"),
                is_annotation = "0",
                guielement = Some("richtext"),
                guiorder = Some(2),
                valuetype_id = Some("http://www.knora.org/ontology/knora-base#TextValue"),
                regular_property = 1,
                pid = "http://www.knora.org/ontology/0803/incunabula#description"
            ),
            PropertyV1(
                locations = Nil,
                value_rights = Vector(Some(7)),
                value_firstprops = Vector(None),
                value_iconsrcs = Vector(None),
                value_restype = Vector(None),
                comments = Vector(None),
                value_ids = Vector("http://rdfh.ch/8a0b1e75/values/e80b2d895f23"),
                values = Vector(TextValueSimpleV1(
                    utf8str = "Schramm, Bd. 21, Abb. 601."
                )),
                occurrence = Some("0-n"),
                attributes = "wrap=soft;rows=7;width=95%",
                label = Some("Kommentar"),
                is_annotation = "0",
                guielement = Some("textarea"),
                guiorder = Some(6),
                valuetype_id = Some("http://www.knora.org/ontology/knora-base#TextValue"),
                regular_property = 1,
                pid = "http://www.knora.org/ontology/0803/incunabula#page_comment"
            ),
            PropertyV1(
                locations = Nil,
                value_rights = Vector(Some(2)),
                value_firstprops = Vector(None),
                value_iconsrcs = Vector(None),
                value_restype = Vector(None),
                comments = Vector(None),
                value_ids = Vector("http://rdfh.ch/8a0b1e75/values/aa488c2203"),
                values = Vector(TextValueSimpleV1(
                    utf8str = "ad+s167_druck1=0001.tif"
                )),
                occurrence = Some("1"),
                attributes = "maxlength=128;size=54",
                label = Some("Urspr\u00FCnglicher Dateiname"),
                is_annotation = "0",
                guielement = Some("text"),
                guiorder = Some(7),
                valuetype_id = Some("http://www.knora.org/ontology/knora-base#TextValue"),
                regular_property = 1,
                pid = "http://www.knora.org/ontology/0803/incunabula#origname"
            ),
            PropertyV1(
                locations = Nil,
                value_rights = Vector(Some(2)),
                value_firstprops = Vector(None),
                value_iconsrcs = Vector(None),
                value_restype = Vector(None),
                comments = Vector(None),
                value_ids = Vector("http://rdfh.ch/8a0b1e75/values/e71e39e902"),
                values = Vector(IntegerValueV1(ival = 1)),
                occurrence = Some("0-1"),
                attributes = "max=-1;min=0",
                label = Some("Sequenznummer"),
                is_annotation = "0",
                guielement = Some("spinbox"),
                guiorder = Some(3),
                valuetype_id = Some("http://www.knora.org/ontology/knora-base#IntValue"),
                regular_property = 1,
                pid = "http://www.knora.org/ontology/0803/incunabula#seqnum"
            ),
            PropertyV1(
                locations = Nil,
                value_rights = Vector(Some(2)),
                value_firstprops = Vector(Some("Zeitgl\u00F6cklein des Lebens und Leidens Christi")),
                value_iconsrcs = Vector(Some(settings.salsah1BaseUrl + settings.salsah1ProjectIconsBasePath + "incunabula/book.gif")),
                value_restype = Vector(Some("Buch")),
                comments = Vector(None),
                value_ids = Vector("http://rdfh.ch/8a0b1e75/values/ac9ddbf4-62a7-4cdc-b530-16cbbaa265bf"),
                values = Vector(LinkV1(
                    valueResourceClassIcon = Some(settings.salsah1BaseUrl + settings.salsah1ProjectIconsBasePath + "incunabula/book.gif"),
                    valueResourceClassLabel = Some("Buch"),
                    valueResourceClass = Some("http://www.knora.org/ontology/0803/incunabula#book"),
                    valueLabel = Some("Zeitgl\u00F6cklein des Lebens und Leidens Christi"),
                    targetResourceIri = "http://rdfh.ch/c5058f3a"
                )),
                occurrence = Some("1"),
                attributes = "restypeid=http://www.knora.org/ontology/0803/incunabula#book",
                label = Some("ist ein Teil von"),
                is_annotation = "0",
                guielement = Some("searchbox"),
                guiorder = Some(2),
                valuetype_id = Some("http://www.knora.org/ontology/knora-base#LinkValue"),
                regular_property = 1,
                pid = "http://www.knora.org/ontology/0803/incunabula#partOf"
            ),
            PropertyV1(
                locations = Nil,
                value_rights = Nil,
                value_firstprops = Nil,
                value_iconsrcs = Nil,
                value_restype = Nil,
                comments = Nil,
                value_ids = Nil,
                values = Nil,
                occurrence = Some("0-1"),
                attributes = "numprops=1;restypeid=http://www.knora.org/ontology/0803/incunabula#Sideband",
                label = Some("Randleistentyp links"),
                is_annotation = "0",
                guielement = Some("searchbox"),
                guiorder = Some(10),
                valuetype_id = Some("http://www.knora.org/ontology/knora-base#LinkValue"),
                regular_property = 1,
                pid = "http://www.knora.org/ontology/0803/incunabula#hasLeftSideband"
            ),
            PropertyV1(
                locations = Nil,
                value_rights = Nil,
                value_firstprops = Nil,
                value_iconsrcs = Nil,
                value_restype = Nil,
                comments = Nil,
                value_ids = Nil,
                values = Nil,
                occurrence = Some("0-1"),
                attributes = "numprops=1;restypeid=http://www.knora.org/ontology/0803/incunabula#Sideband",
                label = Some("Randleistentyp rechts"),
                is_annotation = "0",
                guielement = Some("searchbox"),
                guiorder = Some(11),
                valuetype_id = Some("http://www.knora.org/ontology/knora-base#LinkValue"),
                regular_property = 1,
                pid = "http://www.knora.org/ontology/0803/incunabula#hasRightSideband"
            ),
            PropertyV1(
                locations = Nil,
                value_rights = Nil,
                value_firstprops = Nil,
                value_iconsrcs = Nil,
                value_restype = Nil,
                comments = Nil,
                value_ids = Nil,
                values = Nil,
                occurrence = Some("0-n"),
                attributes = "cols=60;wrap=soft;rows=3",
                label = Some("Verweis"),
                is_annotation = "0",
                guielement = Some("textarea"),
                guiorder = Some(5),
                valuetype_id = Some("http://www.knora.org/ontology/knora-base#TextValue"),
                regular_property = 1,
                pid = "http://www.knora.org/ontology/0803/incunabula#citation"
            ),
            PropertyV1(
                locations = Nil,
                value_rights = Nil,
                value_firstprops = Nil,
                value_iconsrcs = Nil,
                value_restype = Nil,
                comments = Nil,
                value_ids = Nil,
                values = Nil,
                occurrence = Some("0-n"),
                attributes = "restypeid=http://www.knora.org/ontology/knora-base#Resource",
                label = Some("hat Standoff Link zu"),
                is_annotation = "0",
                guielement = None,
                guiorder = None,
                valuetype_id = Some("http://www.knora.org/ontology/knora-base#LinkValue"),
                regular_property = 1,
                pid = "http://www.knora.org/ontology/knora-base#hasStandoffLinkTo"
            ),
            PropertyV1(
                locations = Nil,
                value_rights = Nil,
                value_firstprops = Nil,
                value_iconsrcs = Nil,
                value_restype = Nil,
                comments = Nil,
                value_ids = Nil,
                values = Nil,
                occurrence = Some("0-n"),
                attributes = "",
                label = Some("Transkription"),
                is_annotation = "0",
                guielement = Some("richtext"),
                guiorder = Some(12),
                valuetype_id = Some("http://www.knora.org/ontology/knora-base#TextValue"),
                regular_property = 1,
                pid = "http://www.knora.org/ontology/0803/incunabula#transcription"
            )
        ))),
        resdata = Some(ResourceDataV1(
            rights = Some(6),
            iconsrc = Some(settings.salsah1BaseUrl + settings.salsah1ProjectIconsBasePath + "incunabula/page.gif"),
            restype_label = Some("Seite"),
            restype_name = "http://www.knora.org/ontology/0803/incunabula#page",
            res_id = "http://rdfh.ch/8a0b1e75"
        )),
        resinfo = Some(ResourceInfoV1(
            firstproperty = Some("a1r, Titelblatt"),
            value_of = 0,
            lastmod = "0000-00-00 00:00:00",
            resclass_has_location = true,
            resclass_name = "object",
            locdata = Some(LocationV1(
                protocol = "file",
                duration = 0,
                fps = 0,
                path = "http://localhost:1024/knora/incunabula_0000000002.jp2/full/2613,3505/0/default.jpg",
                ny = Some(3505),
                nx = Some(2613),
                origname = "ad+s167_druck1=0001.tif",
                format_name = "JPEG2000"
            )),
            locations = Some(Vector(
                LocationV1(
                    protocol = "file",
                    duration = 0,
                    fps = 0,
                    path = "http://localhost:1024/knora/incunabula_0000000002.jp2/full/95,128/0/default.jpg",
                    ny = Some(128),
                    nx = Some(95),
                    origname = "ad+s167_druck1=0001.tif",
                    format_name = "JPEG2000"
                ),
                LocationV1(
                    protocol = "file",
                    duration = 0,
                    fps = 0,
                    path = "http://localhost:1024/knora/incunabula_0000000002.jp2/full/82,110/0/default.jpg",
                    ny = Some(110),
                    nx = Some(82),
                    origname = "ad+s167_druck1=0001.tif",
                    format_name = "JPEG2000"
                ),
                LocationV1(
                    protocol = "file",
                    duration = 0,
                    fps = 0,
                    path = "http://localhost:1024/knora/incunabula_0000000002.jp2/full/163,219/0/default.jpg",
                    ny = Some(219),
                    nx = Some(163),
                    origname = "ad+s167_druck1=0001.tif",
                    format_name = "JPEG2000"
                ),
                LocationV1(
                    protocol = "file",
                    duration = 0,
                    fps = 0,
                    path = "http://localhost:1024/knora/incunabula_0000000002.jp2/full/327,438/0/default.jpg",
                    ny = Some(438),
                    nx = Some(327),
                    origname = "ad+s167_druck1=0001.tif",
                    format_name = "JPEG2000"
                ),
                LocationV1(
                    protocol = "file",
                    duration = 0,
                    fps = 0,
                    path = "http://localhost:1024/knora/incunabula_0000000002.jp2/full/653,876/0/default.jpg",
                    ny = Some(876),
                    nx = Some(653),
                    origname = "ad+s167_druck1=0001.tif",
                    format_name = "JPEG2000"
                ),
                LocationV1(
                    protocol = "file",
                    duration = 0,
                    fps = 0,
                    path = "http://localhost:1024/knora/incunabula_0000000002.jp2/full/1307,1753/0/default.jpg",
                    ny = Some(1753),
                    nx = Some(1307),
                    origname = "ad+s167_druck1=0001.tif",
                    format_name = "JPEG2000"
                ),
                LocationV1(
                    protocol = "file",
                    duration = 0,
                    fps = 0,
                    path = "http://localhost:1024/knora/incunabula_0000000002.jp2/full/2613,3505/0/default.jpg",
                    ny = Some(3505),
                    nx = Some(2613),
                    origname = "ad+s167_druck1=0001.tif",
                    format_name = "JPEG2000"
                )
            )),
            preview = Some(LocationV1(
                protocol = "file",
                duration = 0,
                fps = 0,
                path = "http://localhost:1024/knora/incunabula_0000000002.jp2/full/95,128/0/default.jpg",
                ny = Some(128),
                nx = Some(95),
                origname = "ad+s167_druck1=0001.tif",
                format_name = "JPEG2000"
            )),
            restype_iconsrc = Some(settings.salsah1BaseUrl + settings.salsah1ProjectIconsBasePath + "incunabula/page.gif"),
            restype_description = Some("Eine Seite ist ein Teil eines Buchs"),
            restype_label = Some("Seite"),
            restype_name = Some("http://www.knora.org/ontology/0803/incunabula#page"),
            restype_id = "http://www.knora.org/ontology/0803/incunabula#page",
            person_id = "http://rdfh.ch/users/91e19f1e01",
            project_id = "http://rdfh.ch/projects/0803"
        ))
    )

    val dummyMapping = MappingXMLtoStandoff(
        namespace = Map.empty[String, Map[String, Map[String, XMLTag]]],
        defaultXSLTransformation = None
    )
}