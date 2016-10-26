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

package org.knora.webapi.responders.v1

import akka.actor.ActorSystem
import org.knora.webapi.messages.v1.responder.resourcemessages._
import org.knora.webapi.messages.v1.responder.usermessages.UserDataV1
import org.knora.webapi.messages.v1.responder.valuemessages._
import org.knora.webapi.{IRI, Settings}

object ResourcesResponderV1SpecFullData {

    implicit lazy val system = ActorSystem("webapi")

    val settings = Settings(system)

    // The expected response to a "full" resource request for a book.
    val expectedBookResourceFullResponse = ResourceFullResponseV1(
        userdata = UserDataV1(
            password = None,
            email = Some("test@test.ch"),
            lastname = Some("Test"),
            firstname = Some("User"),
            username = Some("testuser"),
            token = None,
            user_id = Some("http://data.knora.org/users/b83acc5f05"),
            lang = "de"
        ),
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
                    restype_iconsrc = Some(settings.baseSALSAHUrl + settings.projectIconsBasePath + "knora-base/link.gif"),
                    restype_description = Some("Verkn\u00FCpfung mehrerer Resourcen"),
                    restype_label = Some("Verkn\u00FCpfungsobjekt"),
                    restype_name = Some("http://www.knora.org/ontology/knora-base#LinkObj"),
                    restype_id = "http://www.knora.org/ontology/knora-base#LinkObj",
                    person_id = "http://data.knora.org/users/91e19f1e01",
                    project_id = "http://data.knora.org/projects/77275339"
                ),
                ext_res_id = ExternalResourceIDV1(
                    pid = "http://www.knora.org/ontology/knora-base#hasLinkTo",
                    id = "http://data.knora.org/ab79ffa43935"
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
                    restype_iconsrc = Some(settings.baseSALSAHUrl + settings.projectIconsBasePath + "knora-base/link.gif"),
                    restype_description = Some("Verkn\u00FCpfung mehrerer Resourcen"),
                    restype_label = Some("Verkn\u00FCpfungsobjekt"),
                    restype_name = Some("http://www.knora.org/ontology/knora-base#LinkObj"),
                    restype_id = "http://www.knora.org/ontology/knora-base#LinkObj",
                    person_id = "http://data.knora.org/users/b83acc5f05",
                    project_id = "http://data.knora.org/projects/77275339"
                ),
                ext_res_id = ExternalResourceIDV1(
                    pid = "http://www.knora.org/ontology/knora-base#hasLinkTo",
                    id = "http://data.knora.org/cb1a74e3e2f6"
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
                value_ids = Vector("http://data.knora.org/c5058f3a/values/8653a672"),
                values = Vector(TextValueV1(
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
                pid = "http://www.knora.org/ontology/incunabula#hasAuthor"
            ),
            PropertyV1(
                locations = Nil,
                value_rights = Vector(Some(6)),
                value_firstprops = Vector(None),
                value_iconsrcs = Vector(None),
                value_restype = Vector(None),
                comments = Vector(None),
                value_ids = Vector("http://data.knora.org/c5058f3a/values/c3295339"),
                values = Vector(TextValueV1(
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
                pid = "http://www.knora.org/ontology/incunabula#title"
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
                    "http://data.knora.org/c5058f3a/values/184e99ca01",
                    "http://data.knora.org/c5058f3a/values/db77ec0302",
                    "http://data.knora.org/c5058f3a/values/9ea13f3d02"
                ),
                values = Vector(
                    TextValueV1(
                        utf8str = "Schramm Bd. XXI, S. 27"
                    ),
                    TextValueV1(
                        utf8str = "GW 4168"
                    ),
                    TextValueV1(
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
                pid = "http://www.knora.org/ontology/incunabula#citation"
            ),
            PropertyV1(
                locations = Nil,
                value_rights = Vector(Some(7)),
                value_firstprops = Vector(None),
                value_iconsrcs = Vector(None),
                value_restype = Vector(None),
                comments = Vector(None),
                value_ids = Vector("http://data.knora.org/c5058f3a/values/92faf25701"),
                values = Vector(TextValueV1(
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
                pid = "http://www.knora.org/ontology/incunabula#location"
            ),
            PropertyV1(
                locations = Nil,
                value_rights = Vector(Some(7)),
                value_firstprops = Vector(None),
                value_iconsrcs = Vector(None),
                value_restype = Vector(None),
                comments = Vector(None),
                value_ids = Vector("http://data.knora.org/c5058f3a/values/10e00c7acc2704"),
                values = Vector(TextValueV1(
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
                pid = "http://www.knora.org/ontology/incunabula#url"
            ),
            PropertyV1(
                locations = Nil,
                value_rights = Vector(Some(6)),
                value_firstprops = Vector(None),
                value_iconsrcs = Vector(None),
                value_restype = Vector(None),
                comments = Vector(None),
                value_ids = Vector("http://data.knora.org/c5058f3a/values/5524469101"),
                values = Vector(TextValueV1(
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
                pid = "http://www.knora.org/ontology/incunabula#physical_desc"
            ),
            PropertyV1(
                locations = Nil,
                value_rights = Vector(Some(2)),
                value_firstprops = Vector(None),
                value_iconsrcs = Vector(None),
                value_restype = Vector(None),
                comments = Vector(None),
                value_ids = Vector("http://data.knora.org/c5058f3a/values/0ca74ce5"),
                values = Vector(TextValueV1(
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
                pid = "http://www.knora.org/ontology/incunabula#publoc"
            ),
            PropertyV1(
                locations = Nil,
                value_rights = Vector(Some(6)),
                value_firstprops = Vector(None),
                value_iconsrcs = Vector(None),
                value_restype = Vector(None),
                comments = Vector(None),
                value_ids = Vector("http://data.knora.org/c5058f3a/values/cfd09f1e01"),
                values = Vector(DateValueV1(
                    calendar = KnoraCalendarV1.JULIAN,
                    dateval2 = "1492",
                    dateval1 = "1492"
                )),
                occurrence = Some("0-1"),
                attributes = "size=16;maxlength=32",
                label = Some("Datum der Herausgabe"),
                is_annotation = "0",
                guielement = Some("date"),
                guiorder = Some(5),
                valuetype_id = Some("http://www.knora.org/ontology/knora-base#DateValue"),
                regular_property = 1,
                pid = "http://www.knora.org/ontology/incunabula#pubdate"
            ),
            PropertyV1(
                locations = Nil,
                value_rights = Vector(Some(6)),
                value_firstprops = Vector(None),
                value_iconsrcs = Vector(None),
                value_restype = Vector(None),
                comments = Vector(None),
                value_ids = Vector("http://data.knora.org/c5058f3a/values/497df9ab"),
                values = Vector(TextValueV1(
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
                pid = "http://www.knora.org/ontology/incunabula#publisher"
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
                pid = "http://www.knora.org/ontology/incunabula#book_comment"
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
                pid = "http://www.knora.org/ontology/incunabula#description"
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
                pid = "http://www.knora.org/ontology/incunabula#note"
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
            iconsrc = Some(settings.baseSALSAHUrl + settings.projectIconsBasePath + "incunabula/book.gif"),
            restype_label = Some("Buch"),
            restype_name = "http://www.knora.org/ontology/incunabula#book",
            res_id = "http://data.knora.org/c5058f3a"
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
            restype_iconsrc = Some(settings.baseSALSAHUrl + settings.projectIconsBasePath + "incunabula/book.gif"),
            restype_description = Some("Diese Resource-Klasse beschreibt ein Buch"),
            restype_label = Some("Buch"),
            restype_name = Some("http://www.knora.org/ontology/incunabula#book"),
            restype_id = "http://www.knora.org/ontology/incunabula#book",
            person_id = "http://data.knora.org/users/91e19f1e01",
            project_id = "http://data.knora.org/projects/77275339"
        ))
    )

    // The expected response to a "full" resource request for a page.
    val expectedPageResourceFullResponse = ResourceFullResponseV1(
        userdata = UserDataV1(
            projects_info = Nil,
            projects = None,
            active_project = None,
            password = None,
            email = Some("test@test.ch"),
            lastname = Some("Test"),
            firstname = Some("User"),
            username = Some("testuser"),
            token = None,
            user_id = Some("http://data.knora.org/users/b83acc5f05"),
            lang = "de"
        ),
        access = "OK",
        incoming = Nil,
        props = Some(PropsV1(properties = Vector(
            PropertyV1(
                locations = Vector(
                    LocationV1(
                        protocol = "file",
                        duration = 0,
                        fps = 0,
                        path = "http://localhost:1024/knora/incunabula_0000000002.jpg/full/full/0/default.jpg",
                        ny = Some(128),
                        nx = Some(95),
                        origname = "ad+s167_druck1=0001.tif",
                        format_name = "JPEG"
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
                value_ids = Vector("http://data.knora.org/8a0b1e75/values/61cb927602"),
                values = Vector(TextValueV1(
                    utf8str = "a1r, Titelblatt"
                )),
                occurrence = Some("0-1"),
                attributes = "min=4;max=8",
                label = Some("Seitenbezeichnung"),
                is_annotation = "0",
                guielement = Some("text"),
                guiorder = Some(1),
                valuetype_id = Some("http://www.knora.org/ontology/knora-base#TextValue"),
                regular_property = 1,
                pid = "http://www.knora.org/ontology/incunabula#pagenum"
            ),
            PropertyV1(
                locations = Nil,
                value_rights = Vector(Some(8)),
                value_firstprops = Vector(None),
                value_iconsrcs = Vector(None),
                value_restype = Vector(None),
                comments = Vector(None),
                value_ids = Vector("http://data.knora.org/8a0b1e75/values/3e3d4dc0e922"),
                values = Vector(TextValueV1(
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
                pid = "http://www.knora.org/ontology/incunabula#description"
            ),
            PropertyV1(
                locations = Nil,
                value_rights = Vector(Some(8)),
                value_firstprops = Vector(None),
                value_iconsrcs = Vector(None),
                value_restype = Vector(None),
                comments = Vector(None),
                value_ids = Vector("http://data.knora.org/8a0b1e75/values/e80b2d895f23"),
                values = Vector(TextValueV1(
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
                pid = "http://www.knora.org/ontology/incunabula#page_comment"
            ),
            PropertyV1(
                locations = Nil,
                value_rights = Vector(Some(2)),
                value_firstprops = Vector(None),
                value_iconsrcs = Vector(None),
                value_restype = Vector(None),
                comments = Vector(None),
                value_ids = Vector("http://data.knora.org/8a0b1e75/values/aa488c2203"),
                values = Vector(TextValueV1(
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
                pid = "http://www.knora.org/ontology/incunabula#origname"
            ),
            PropertyV1(
                locations = Nil,
                value_rights = Vector(Some(2)),
                value_firstprops = Vector(None),
                value_iconsrcs = Vector(None),
                value_restype = Vector(None),
                comments = Vector(None),
                value_ids = Vector("http://data.knora.org/8a0b1e75/values/e71e39e902"),
                values = Vector(IntegerValueV1(ival = 1)),
                occurrence = Some("0-1"),
                attributes = "max=-1;min=0",
                label = Some("Sequenznummer"),
                is_annotation = "0",
                guielement = Some("spinbox"),
                guiorder = Some(3),
                valuetype_id = Some("http://www.knora.org/ontology/knora-base#IntValue"),
                regular_property = 1,
                pid = "http://www.knora.org/ontology/incunabula#seqnum"
            ),
            PropertyV1(
                locations = Nil,
                value_rights = Vector(Some(2)),
                value_firstprops = Vector(Some("Zeitgl\u00F6cklein des Lebens und Leidens Christi")),
                value_iconsrcs = Vector(Some(settings.baseSALSAHUrl + settings.projectIconsBasePath + "incunabula/book.gif")),
                value_restype = Vector(Some("Buch")),
                comments = Vector(None),
                value_ids = Vector("http://data.knora.org/8a0b1e75/values/ac9ddbf4-62a7-4cdc-b530-16cbbaa265bf"),
                values = Vector(LinkV1(
                    valueResourceClassIcon = Some(settings.baseSALSAHUrl + settings.projectIconsBasePath + "incunabula/book.gif"),
                    valueResourceClassLabel = Some("Buch"),
                    valueResourceClass = Some("http://www.knora.org/ontology/incunabula#book"),
                    valueLabel = Some("Zeitgl\u00F6cklein des Lebens und Leidens Christi"),
                    targetResourceIri = "http://data.knora.org/c5058f3a"
                )),
                occurrence = Some("1"),
                attributes = "restypeid=http://www.knora.org/ontology/incunabula#book",
                label = Some("ist ein Teil von"),
                is_annotation = "0",
                guielement = Some("searchbox"),
                guiorder = Some(2),
                valuetype_id = Some("http://www.knora.org/ontology/knora-base#LinkValue"),
                regular_property = 1,
                pid = "http://www.knora.org/ontology/incunabula#partOf"
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
                attributes = "numprops=1;restypeid=http://www.knora.org/ontology/incunabula#Sideband",
                label = Some("Randleistentyp links"),
                is_annotation = "0",
                guielement = Some("searchbox"),
                guiorder = Some(10),
                valuetype_id = Some("http://www.knora.org/ontology/knora-base#LinkValue"),
                regular_property = 1,
                pid = "http://www.knora.org/ontology/incunabula#hasLeftSideband"
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
                attributes = "numprops=1;restypeid=http://www.knora.org/ontology/incunabula#Sideband",
                label = Some("Randleistentyp rechts"),
                is_annotation = "0",
                guielement = Some("searchbox"),
                guiorder = Some(11),
                valuetype_id = Some("http://www.knora.org/ontology/knora-base#LinkValue"),
                regular_property = 1,
                pid = "http://www.knora.org/ontology/incunabula#hasRightSideband"
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
                pid = "http://www.knora.org/ontology/incunabula#citation"
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
                attributes = "hlist=<http://data.knora.org/lists/4b6d86ce03>",
                label = Some("Transkription"),
                is_annotation = "0",
                guielement = Some("pulldown"),
                guiorder = Some(12),
                valuetype_id = Some("http://www.knora.org/ontology/knora-base#TextValue"),
                regular_property = 1,
                pid = "http://www.knora.org/ontology/incunabula#transcription"
            )
        ))),
        resdata = Some(ResourceDataV1(
            rights = Some(6),
            iconsrc = Some(settings.baseSALSAHUrl + settings.projectIconsBasePath + "incunabula/page.gif"),
            restype_label = Some("Seite"),
            restype_name = "http://www.knora.org/ontology/incunabula#page",
            res_id = "http://data.knora.org/8a0b1e75"
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
                    path = "http://localhost:1024/knora/incunabula_0000000002.jpg/full/full/0/default.jpg",
                    ny = Some(128),
                    nx = Some(95),
                    origname = "ad+s167_druck1=0001.tif",
                    format_name = "JPEG"
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
                path = "http://localhost:1024/knora/incunabula_0000000002.jpg/full/full/0/default.jpg",
                ny = Some(128),
                nx = Some(95),
                origname = "ad+s167_druck1=0001.tif",
                format_name = "JPEG"
            )),
            restype_iconsrc = Some(settings.baseSALSAHUrl + settings.projectIconsBasePath + "incunabula/page.gif"),
            restype_description = Some("Eine Seite ist ein Teil eines Buchs"),
            restype_label = Some("Seite"),
            restype_name = Some("http://www.knora.org/ontology/incunabula#page"),
            restype_id = "http://www.knora.org/ontology/incunabula#page",
            person_id = "http://data.knora.org/users/91e19f1e01",
            project_id = "http://data.knora.org/projects/77275339"
        ))
    )

    val expectedRegionFullResource = ResourceFullResponseV1(
        userdata = UserDataV1(
            password = None,
            email = Some("test@test.ch"),
            lastname = Some("Test"),
            firstname = Some("User"),
            username = Some("testuser"),
            token = None,
            user_id = Some("http://data.knora.org/users/b83acc5f05"),
            lang = "de"
        ),
        access = "OK",
        incoming = Vector(
            IncomingV1(
                value = Some("Gleicher Holzschnitt"),
                resinfo = ResourceInfoV1(
                    firstproperty = Some("Gleicher Holzschnitt"),
                    value_of = 0,
                    lastmod = "0000-00-00 00:00:00",
                    resclass_has_location = false,
                    resclass_name = "object",
                    locdata = None,
                    locations = None,
                    preview = None,
                    restype_iconsrc = Some(settings.baseSALSAHUrl + settings.projectIconsBasePath + "knora-base/link.gif"),
                    restype_description = Some("Verknüpfung mehrerer Resourcen (Systemobject)"),
                    restype_label = Some("Verknüpfungsobjekt"),
                    restype_name = Some("http://www.knora.org/ontology/knora-base#LinkObj"),
                    restype_id = "http://www.knora.org/ontology/knora-base#LinkObj",
                    person_id = "http://data.knora.org/users/91e19f1e01",
                    project_id = "http://data.knora.org/projects/77275339"
                ),
                ext_res_id = ExternalResourceIDV1(
                    pid = "http://www.knora.org/ontology/knora-base#hasLinkTo",
                    id = "http://data.knora.org/faa4d435a9f7"
                )
            ),
            IncomingV1(
                value = Some("Gleicher Holzschnitt wie in deutscher Ausgabe Seite b8v"),
                resinfo = ResourceInfoV1(
                    firstproperty = Some("Gleicher Holzschnitt wie in deutscher Ausgabe Seite b8v"),
                    value_of = 0,
                    lastmod = "0000-00-00 00:00:00",
                    resclass_has_location = false,
                    resclass_name = "object",
                    locdata = None,
                    locations = None,
                    preview = None,
                    restype_iconsrc = Some(settings.baseSALSAHUrl + settings.projectIconsBasePath + "knora-base/region.gif"),
                    restype_description = Some("This Resource represents a geometric region of a resource. The geometry is represented currently as JSON string."),
                    restype_label = Some("Region"),
                    restype_name = Some("http://www.knora.org/ontology/knora-base#Region"),
                    restype_id = "http://www.knora.org/ontology/knora-base#Region",
                    person_id = "http://data.knora.org/users/91e19f1e01",
                    project_id = "http://data.knora.org/projects/77275339"
                ),
                ext_res_id = ExternalResourceIDV1(
                    pid = "http://www.knora.org/ontology/knora-base#hasStandoffLinkTo",
                    id = "http://data.knora.org/c5cf5a2bc6be"
                )
            ),
            IncomingV1(
                value = Some("Derselbe Holzschnitt wird auch auf Seite b8v der deutschen Ausgabe des Narrenschiffs verwendet."),
                resinfo = ResourceInfoV1(
                    firstproperty = Some("Derselbe Holzschnitt wird auch auf Seite b8v der deutschen Ausgabe des Narrenschiffs verwendet."),
                    value_of = 0,
                    lastmod = "0000-00-00 00:00:00",
                    resclass_has_location = false,
                    resclass_name = "object",
                    locdata = None,
                    locations = None,
                    preview = None,
                    restype_iconsrc = Some(settings.baseSALSAHUrl + settings.projectIconsBasePath + "knora-base/region.gif"),
                    restype_description = Some("This Resource represents a geometric region of a resource. The geometry is represented currently as JSON string."),
                    restype_label = Some("Region"),
                    restype_name = Some("http://www.knora.org/ontology/knora-base#Region"),
                    restype_id = "http://www.knora.org/ontology/knora-base#Region",
                    person_id = "http://data.knora.org/users/1458b20f08",
                    project_id = "http://data.knora.org/projects/77275339"
                ),
                ext_res_id = ExternalResourceIDV1(
                    pid = "http://www.knora.org/ontology/knora-base#hasStandoffLinkTo",
                    id = "http://data.knora.org/c9824353ae06"
                )
            ),
            IncomingV1(
                value = Some("Der Holzschnitt wird auf Seite b8v der deutschen Ausgabe des Narrenschiffs und c7r der lateinischen Ausgabe des Narrenschiffs verwendet."),
                resinfo = ResourceInfoV1(
                    firstproperty = Some("Der Holzschnitt wird auf Seite b8v der deutschen Ausgabe des Narrenschiffs und c7r der lateinischen Ausgabe des Narrenschiffs verwendet."),
                    value_of = 0,
                    lastmod = "0000-00-00 00:00:00",
                    resclass_has_location = false,
                    resclass_name = "object",
                    locdata = None,
                    locations = None,
                    preview = None,
                    restype_iconsrc = Some(settings.baseSALSAHUrl + settings.projectIconsBasePath + "knora-base/link.gif"),
                    restype_description = Some("Verknüpfung mehrerer Resourcen (Systemobject)"),
                    restype_label = Some("Verknüpfungsobjekt"),
                    restype_name = Some("http://www.knora.org/ontology/knora-base#LinkObj"),
                    restype_id = "http://www.knora.org/ontology/knora-base#LinkObj",
                    person_id = "http://data.knora.org/users/1458b20f08",
                    project_id = "http://data.knora.org/projects/77275339"
                ),
                ext_res_id = ExternalResourceIDV1(
                    pid = "http://www.knora.org/ontology/knora-base#hasLinkTo",
                    id = "http://data.knora.org/8e88d28dae06"
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
                value_ids = Vector("http://data.knora.org/047db418ae06/values/7331b94196a104"),
                values = Vector(TextValueV1(
                    resource_reference = Set("http://data.knora.org/047db418ae06/values/2428fc96-1383-4457-9704-077b37256103"), // TODO: Why is this a Value onject IRI?
                    textattr = Map(
                        StandoffTagV1.paragraph -> Vector(StandoffPositionV1(
                            href = None,
                            resid = None,
                            end = 94,
                            start = 0
                        )),
                        StandoffTagV1.link -> Vector(StandoffPositionV1(
                            href = Some("http://localhost:3333/v1/resources/http%3A%2F%2Fdata.knora.org%2F047db418ae06%2Fvalues%2F2428fc96-1383-4457-9704-077b37256103"),
                            resid = Some("http://data.knora.org/047db418ae06/values/2428fc96-1383-4457-9704-077b37256103"),
                            end = 39,
                            start = 36
                        ))
                    ),
                    utf8str = "Derselbe Holzschnitt wird auf Seite c7r der lateinischen Ausgabe des Narrenschiffs verwendet.\r"
                )),
                occurrence = Some("1-n"),
                attributes = "",
                label = Some("Kommentar"),
                is_annotation = "0",
                guielement = None,
                guiorder = None,
                valuetype_id = Some("http://www.knora.org/ontology/knora-base#TextValue"),
                regular_property = 1,
                pid = "http://www.knora.org/ontology/knora-base#hasComment"
            ),
            PropertyV1(
                locations = Nil,
                value_rights = Vector(Some(6)),
                value_firstprops = Vector(None),
                value_iconsrcs = Vector(None),
                value_restype = Vector(None),
                comments = Vector(None),
                value_ids = Vector("http://data.knora.org/047db418ae06/values/cca179c00527"),
                values = Vector(ColorValueV1(color = "#ff3333")),
                occurrence = Some("1"),
                attributes = "ncolors=8",
                label = Some("Farbe"),
                is_annotation = "0",
                guielement = Some("colorpicker"),
                guiorder = None,
                valuetype_id = Some("http://www.knora.org/ontology/knora-base#ColorValue"),
                regular_property = 1,
                pid = "http://www.knora.org/ontology/knora-base#hasColor"
            ),
            PropertyV1(
                locations = Nil,
                value_rights = Vector(Some(6)),
                value_firstprops = Vector(None),
                value_iconsrcs = Vector(None),
                value_restype = Vector(None),
                comments = Vector(None),
                value_ids = Vector("http://data.knora.org/047db418ae06/values/097826870527"),
                values = Vector(GeomValueV1(geom = "{\"status\":\"active\",\"lineColor\":\"#ff3333\",\"lineWidth\":2,\"points\":[{\"x\":0.24285714285714285,\"y\":0.1712962962962963},{\"x\":0.8678571428571429,\"y\":0.16666666666666666},{\"x\":0.8892857142857142,\"y\":0.7222222222222222},{\"x\":0.25,\"y\":0.7361111111111112},{\"x\":0.2392857142857143,\"y\":0.16898148148148148}],\"type\":\"polygon\"}")),
                occurrence = Some("1-n"),
                attributes = "width=95%;rows=4;wrap=soft",
                label = Some("Geometrie"),
                is_annotation = "0",
                guielement = Some("geometry"),
                guiorder = None,
                valuetype_id = Some("http://www.knora.org/ontology/knora-base#GeomValue"),
                regular_property = 1,
                pid = "http://www.knora.org/ontology/knora-base#hasGeometry"
            ),
            PropertyV1(
                locations = Nil,
                value_rights = Vector(Some(6)),
                value_firstprops = Vector(Some("Derselbe Holzschnitt wird auch auf Seite b8v der deutschen Ausgabe des Narrenschiffs verwendet.")),
                value_iconsrcs = Vector(Some(settings.baseSALSAHUrl + settings.projectIconsBasePath + "knora-base/region.gif")),
                value_restype = Vector(Some("Region")),
                comments = Vector(None),
                value_ids = Vector("http://data.knora.org/047db418ae06/values/2428fc96-1383-4457-9704-077b37256103"),
                values = Vector(LinkV1(
                    valueResourceClassIcon = Some(settings.baseSALSAHUrl + settings.projectIconsBasePath + "knora-base/region.gif"),
                    valueResourceClassLabel = Some("Region"),
                    valueResourceClass = Some("http://www.knora.org/ontology/knora-base#Region"),
                    valueLabel = Some("Derselbe Holzschnitt wird auch auf Seite b8v der deutschen Ausgabe des Narrenschiffs verwendet."),
                    targetResourceIri = "http://data.knora.org/c9824353ae06"
                )),
                occurrence = None,
                attributes = "",
                label = None,
                is_annotation = "0",
                guielement = None,
                guiorder = None,
                valuetype_id = Some("http://www.knora.org/ontology/knora-base#Resource"),
                regular_property = 1,
                pid = "http://www.knora.org/ontology/knora-base#hasStandoffLinkTo"
            ),
            PropertyV1(
                locations = Nil,
                value_rights = Vector(Some(6)),
                value_firstprops = Vector(Some("b8v")),
                value_iconsrcs = Vector(Some(settings.baseSALSAHUrl + settings.projectIconsBasePath + "incunabula/page.gif")),
                value_restype = Vector(Some("Seite")),
                comments = Vector(None),
                value_ids = Vector("http://data.knora.org/047db418ae06/values/2335c869-b649-4dd8-b4b5-e82c88449d62"),
                values = Vector(LinkV1(
                    valueResourceClassIcon = Some(settings.baseSALSAHUrl + settings.projectIconsBasePath + "incunabula/page.gif"),
                    valueResourceClassLabel = Some("Seite"),
                    valueResourceClass = Some("http://www.knora.org/ontology/incunabula#page"),
                    valueLabel = Some("b8v"),
                    targetResourceIri = "http://data.knora.org/883be8542e03"
                )),
                occurrence = Some("1"),
                attributes = "",
                label = None,
                is_annotation = "0",
                guielement = None,
                guiorder = None,
                valuetype_id = Some("http://www.knora.org/ontology/knora-base#Representation"),
                regular_property = 1,
                pid = "http://www.knora.org/ontology/knora-base#isRegionOf"
            )
        ))),
        resdata = Some(ResourceDataV1(
            rights = Some(6),
            iconsrc = Some(settings.baseSALSAHUrl + settings.projectIconsBasePath + "knora-base/region.gif"),
            restype_label = Some("Region"),
            restype_name = "http://www.knora.org/ontology/knora-base#Region",
            res_id = "http://data.knora.org/047db418ae06"
        )),
        resinfo = Some(ResourceInfoV1(
            firstproperty = Some("Derselbe Holzschnitt wird auf Seite c7r der lateinischen Ausgabe des Narrenschiffs verwendet."),
            value_of = 0,
            lastmod = "0000-00-00 00:00:00",
            resclass_has_location = false,
            resclass_name = "object",
            locdata = None,
            locations = None,
            preview = None,
            restype_iconsrc = Some(settings.baseSALSAHUrl + settings.projectIconsBasePath + "knora-base/region.gif"),
            restype_description = Some("This Resource represents a geometric region of a resource. The geometry is represented currently as JSON string."),
            restype_label = Some("Region"),
            restype_name = Some("http://www.knora.org/ontology/knora-base#Region"),
            restype_id = "http://www.knora.org/ontology/knora-base#Region",
            person_id = "http://data.knora.org/users/1458b20f08",
            project_id = "http://data.knora.org/projects/77275339"
        ))
    )
}