/*
 * Copyright © 2015-2018 the contributors (see Contributors.md).
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

package org.knora.webapi

import org.knora.webapi.messages.admin.responder.listsmessages.{ListInfoADM, ListNodeADM, ListNodeInfoADM}
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2

object SharedListsTestDataADM {


    val otherTreeListInfo: ListInfoADM = ListInfoADM (
        id = "http://data.knora.org/anything/otherTreeList",
        projectIri = "http://rdfh.ch/projects/anything",
        labels = Seq(StringLiteralV2("Tree list root", Some("en"))),
        comments = Seq.empty[StringLiteralV2]
    )

    val bigListInfo: ListInfoADM = ListInfoADM(
        id = "http://rdfh.ch/lists/00FF/73d0ec0302",
        projectIri = "http://rdfh.ch/projects/00FF",
        labels = Seq(StringLiteralV2("Title", Some("en")), StringLiteralV2("Titel", Some("de")), StringLiteralV2("Titre", Some("fr"))),
        comments = Seq(StringLiteralV2("Hierarchisches Stichwortverzeichnis / Signatur der Bilder", Some("de")))
    )

    val summerNodeInfo: ListNodeInfoADM = ListNodeInfoADM (
        id = "http://rdfh.ch/lists/00FF/526f26ed04",
        name = Some("sommer"),
        labels = Seq(StringLiteralV2("Sommer")),
        comments = Seq.empty[StringLiteralV2],
        position = Some(0)
    )

    val seasonListNodes: Seq[ListNodeADM] = Seq(
        ListNodeADM(
            id = "http://rdfh.ch/lists/00FF/526f26ed04",
            name = Some("sommer"),
            labels = Seq(StringLiteralV2("Sommer")),
            comments = Seq.empty[StringLiteralV2],
            children = Seq.empty[ListNodeADM],
            position = Some(0)
        ),
        ListNodeADM(
            id = "http://rdfh.ch/lists/00FF/eda2792605",
            name = Some("winter"),
            labels = Seq(StringLiteralV2("Winter")),
            comments = Seq.empty[StringLiteralV2],
            children = Seq.empty[ListNodeADM],
            position = Some(1)
        )
    )

    val nodePath: Seq[ListNodeADM] = Seq(
        ListNodeADM(
            id = "http://rdfh.ch/lists/00FF/691eee1cbe",
            name = Some("4KUN"),
            labels = Seq(StringLiteralV2("KUNST")),
            comments = Seq.empty[StringLiteralV2],
            children = Seq.empty[ListNodeADM],
            position = None

        ),
        ListNodeADM(
            id = "http://rdfh.ch/lists/00FF/2ebd2706c1",
            name = Some("7"),
            labels = Seq(StringLiteralV2("FILM UND FOTO")),
            comments = Seq.empty[StringLiteralV2],
            children = Seq.empty[ListNodeADM],
            position = None
        ),
        ListNodeADM(
            id = "http://rdfh.ch/lists/00FF/c7f07a3fc1",
            name = Some("1"),
            labels = Seq(StringLiteralV2("Heidi Film")),
            comments = Seq.empty[StringLiteralV2],
            children = Seq.empty[ListNodeADM],
            position = None
        )
    )

    val bigListNodes: Seq[ListNodeADM] = Vector(
            ListNodeADM(
                position = Some(0),
                children = Vector(
                    ListNodeADM(
                        position = Some(0),
                        children = Nil,
                        comments = Nil,
                        labels = Vector(StringLiteralV2(
                            language = None,
                            value = "BIBLIOTHEKEN ST. MORITZ"
                        )),
                        name = Some("1"),
                        id = "http://rdfh.ch/lists/00FF/412821d3a6"
                    ),
                    ListNodeADM(
                        position = Some(1),
                        children = Nil,
                        comments = Nil,
                        labels = Vector(StringLiteralV2(
                            language = None,
                            value = "SAMMELB\u00C4NDE, FOTOALBEN"
                        )),
                        name = Some("6"),
                        id = "http://rdfh.ch/lists/00FF/da5b740ca7"
                    )
                ),
                comments = Nil,
                labels = Vector(StringLiteralV2(
                    language = None,
                    value = "ALLGEMEINES"
                )),
                name = Some("1ALL"),
                id = "http://rdfh.ch/lists/00FF/a8f4cd99a6"
            ),
            ListNodeADM(
                position = Some(1),
                children = Vector(
                    ListNodeADM(
                        position = Some(0),
                        children = Vector(ListNodeADM(
                            position = Some(0),
                            children = Nil,
                            comments = Nil,
                            labels = Vector(StringLiteralV2(
                                language = None,
                                value = "Personen"
                            )),
                            name = Some("1"),
                            id = "http://rdfh.ch/lists/00FF/a5f66db8a7"
                        )),
                        comments = Nil,
                        labels = Vector(StringLiteralV2(
                            language = None,
                            value = "SCHWEIZ"
                        )),
                        name = Some("1"),
                        id = "http://rdfh.ch/lists/00FF/0cc31a7fa7"
                    ),
                    ListNodeADM(
                        position = Some(1),
                        children = Vector(ListNodeADM(
                            position = Some(0),
                            children = Nil,
                            comments = Nil,
                            labels = Vector(StringLiteralV2(
                                language = None,
                                value = "Personen"
                            )),
                            name = Some("1"),
                            id = "http://rdfh.ch/lists/00FF/d75d142ba8"
                        )),
                        comments = Nil,
                        labels = Vector(StringLiteralV2(
                            language = None,
                            value = "GRAUB\u00DCNDEN"
                        )),
                        name = Some("2"),
                        id = "http://rdfh.ch/lists/00FF/3e2ac1f1a7"
                    ),
                    ListNodeADM(
                        position = Some(2),
                        children = Vector(
                            ListNodeADM(
                                position = Some(0),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Flugaufnahmen"
                                )),
                                name = Some("1"),
                                id = "http://rdfh.ch/lists/00FF/09c5ba9da8"
                            ),
                            ListNodeADM(
                                position = Some(1),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Landschaft Sommer ohne Ortschaften"
                                )),
                                name = Some("2"),
                                id = "http://rdfh.ch/lists/00FF/a2f80dd7a8"
                            ),
                            ListNodeADM(
                                position = Some(2),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Landschaft Sommer mit Ortschaften"
                                )),
                                name = Some("3"),
                                id = "http://rdfh.ch/lists/00FF/3b2c6110a9"
                            ),
                            ListNodeADM(
                                position = Some(3),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Landschaft Winter ohne Ortschaften"
                                )),
                                name = Some("4"),
                                id = "http://rdfh.ch/lists/00FF/d45fb449a9"
                            ),
                            ListNodeADM(
                                position = Some(4),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Landschaft Winter mit Ortschaften"
                                )),
                                name = Some("5"),
                                id = "http://rdfh.ch/lists/00FF/6d930783a9"
                            ),
                            ListNodeADM(
                                position = Some(5),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Landschaft Seen"
                                )),
                                name = Some("6"),
                                id = "http://rdfh.ch/lists/00FF/06c75abca9"
                            ),
                            ListNodeADM(
                                position = Some(6),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Landschaft Berge"
                                )),
                                name = Some("7"),
                                id = "http://rdfh.ch/lists/00FF/9ffaadf5a9"
                            ),
                            ListNodeADM(
                                position = Some(7),
                                children = Vector(
                                    ListNodeADM(
                                        position = Some(0),
                                        children = Nil,
                                        comments = Nil,
                                        labels = Vector(StringLiteralV2(
                                            language = None,
                                            value = "Maloja"
                                        )),
                                        name = Some("1"),
                                        id = "http://rdfh.ch/lists/00FF/d1615468aa"
                                    ),
                                    ListNodeADM(
                                        position = Some(1),
                                        children = Nil,
                                        comments = Nil,
                                        labels = Vector(StringLiteralV2(
                                            language = None,
                                            value = "Sils"
                                        )),
                                        name = Some("2"),
                                        id = "http://rdfh.ch/lists/00FF/6a95a7a1aa"
                                    ),
                                    ListNodeADM(
                                        position = Some(2),
                                        children = Nil,
                                        comments = Nil,
                                        labels = Vector(StringLiteralV2(
                                            language = None,
                                            value = "Silvaplana"
                                        )),
                                        name = Some("3"),
                                        id = "http://rdfh.ch/lists/00FF/03c9fadaaa"
                                    ),
                                    ListNodeADM(
                                        position = Some(3),
                                        children = Nil,
                                        comments = Nil,
                                        labels = Vector(StringLiteralV2(
                                            language = None,
                                            value = "Surlej"
                                        )),
                                        name = Some("4"),
                                        id = "http://rdfh.ch/lists/00FF/9cfc4d14ab"
                                    ),
                                    ListNodeADM(
                                        position = Some(4),
                                        children = Nil,
                                        comments = Nil,
                                        labels = Vector(StringLiteralV2(
                                            language = None,
                                            value = "Champf\u00E8r"
                                        )),
                                        name = Some("5"),
                                        id = "http://rdfh.ch/lists/00FF/3530a14dab"
                                    ),
                                    ListNodeADM(
                                        position = Some(5),
                                        children = Nil,
                                        comments = Nil,
                                        labels = Vector(StringLiteralV2(
                                            language = None,
                                            value = "Pontresina"
                                        )),
                                        name = Some("6"),
                                        id = "http://rdfh.ch/lists/00FF/ce63f486ab"
                                    ),
                                    ListNodeADM(
                                        position = Some(6),
                                        children = Nil,
                                        comments = Nil,
                                        labels = Vector(StringLiteralV2(
                                            language = None,
                                            value = "Celerina"
                                        )),
                                        name = Some("7"),
                                        id = "http://rdfh.ch/lists/00FF/679747c0ab"
                                    ),
                                    ListNodeADM(
                                        position = Some(7),
                                        children = Nil,
                                        comments = Nil,
                                        labels = Vector(StringLiteralV2(
                                            language = None,
                                            value = "Samedan"
                                        )),
                                        name = Some("8"),
                                        id = "http://rdfh.ch/lists/00FF/00cb9af9ab"
                                    ),
                                    ListNodeADM(
                                        position = Some(8),
                                        children = Nil,
                                        comments = Nil,
                                        labels = Vector(StringLiteralV2(
                                            language = None,
                                            value = "Bever"
                                        )),
                                        name = Some("9"),
                                        id = "http://rdfh.ch/lists/00FF/99feed32ac"
                                    ),
                                    ListNodeADM(
                                        position = Some(9),
                                        children = Nil,
                                        comments = Nil,
                                        labels = Vector(StringLiteralV2(
                                            language = None,
                                            value = "La Punt"
                                        )),
                                        name = Some("10"),
                                        id = "http://rdfh.ch/lists/00FF/3232416cac"
                                    ),
                                    ListNodeADM(
                                        position = Some(10),
                                        children = Nil,
                                        comments = Nil,
                                        labels = Vector(StringLiteralV2(
                                            language = None,
                                            value = "Chamues-ch"
                                        )),
                                        name = Some("11"),
                                        id = "http://rdfh.ch/lists/00FF/cb6594a5ac"
                                    ),
                                    ListNodeADM(
                                        position = Some(11),
                                        children = Nil,
                                        comments = Nil,
                                        labels = Vector(StringLiteralV2(
                                            language = None,
                                            value = "Madulain"
                                        )),
                                        name = Some("12"),
                                        id = "http://rdfh.ch/lists/00FF/6499e7deac"
                                    ),
                                    ListNodeADM(
                                        position = Some(12),
                                        children = Nil,
                                        comments = Nil,
                                        labels = Vector(StringLiteralV2(
                                            language = None,
                                            value = "Zuoz"
                                        )),
                                        name = Some("13"),
                                        id = "http://rdfh.ch/lists/00FF/fdcc3a18ad"
                                    ),
                                    ListNodeADM(
                                        position = Some(13),
                                        children = Nil,
                                        comments = Nil,
                                        labels = Vector(StringLiteralV2(
                                            language = None,
                                            value = "S-chanf"
                                        )),
                                        name = Some("14"),
                                        id = "http://rdfh.ch/lists/00FF/96008e51ad"
                                    ),
                                    ListNodeADM(
                                        position = Some(14),
                                        children = Nil,
                                        comments = Nil,
                                        labels = Vector(StringLiteralV2(
                                            language = None,
                                            value = "Cinous-chel"
                                        )),
                                        name = Some("15"),
                                        id = "http://rdfh.ch/lists/00FF/2f34e18aad"
                                    ),
                                    ListNodeADM(
                                        position = Some(15),
                                        children = Nil,
                                        comments = Nil,
                                        labels = Vector(StringLiteralV2(
                                            language = None,
                                            value = "Fex"
                                        )),
                                        name = Some("16"),
                                        id = "http://rdfh.ch/lists/00FF/c86734c4ad"
                                    ),
                                    ListNodeADM(
                                        position = Some(16),
                                        children = Nil,
                                        comments = Nil,
                                        labels = Vector(StringLiteralV2(
                                            language = None,
                                            value = "Unterengadin"
                                        )),
                                        name = Some("17"),
                                        id = "http://rdfh.ch/lists/00FF/619b87fdad"
                                    ),
                                    ListNodeADM(
                                        position = Some(17),
                                        children = Nil,
                                        comments = Nil,
                                        labels = Vector(StringLiteralV2(
                                            language = None,
                                            value = "Personen"
                                        )),
                                        name = Some("18"),
                                        id = "http://rdfh.ch/lists/00FF/faceda36ae"
                                    )
                                ),
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Ortschaften Sommer"
                                )),
                                name = Some("8"),
                                id = "http://rdfh.ch/lists/00FF/382e012faa"
                            ),
                            ListNodeADM(
                                position = Some(8),
                                children = Vector(
                                    ListNodeADM(
                                        position = Some(0),
                                        children = Nil,
                                        comments = Nil,
                                        labels = Vector(StringLiteralV2(
                                            language = None,
                                            value = "Maloja"
                                        )),
                                        name = Some("1"),
                                        id = "http://rdfh.ch/lists/00FF/2c3681a9ae"
                                    ),
                                    ListNodeADM(
                                        position = Some(1),
                                        children = Nil,
                                        comments = Nil,
                                        labels = Vector(StringLiteralV2(
                                            language = None,
                                            value = "Sils"
                                        )),
                                        name = Some("2"),
                                        id = "http://rdfh.ch/lists/00FF/c569d4e2ae"
                                    ),
                                    ListNodeADM(
                                        position = Some(2),
                                        children = Nil,
                                        comments = Nil,
                                        labels = Vector(StringLiteralV2(
                                            language = None,
                                            value = "Silvaplana"
                                        )),
                                        name = Some("3"),
                                        id = "http://rdfh.ch/lists/00FF/5e9d271caf"
                                    ),
                                    ListNodeADM(
                                        position = Some(3),
                                        children = Nil,
                                        comments = Nil,
                                        labels = Vector(StringLiteralV2(
                                            language = None,
                                            value = "Surlej"
                                        )),
                                        name = Some("4"),
                                        id = "http://rdfh.ch/lists/00FF/f7d07a55af"
                                    ),
                                    ListNodeADM(
                                        position = Some(4),
                                        children = Nil,
                                        comments = Nil,
                                        labels = Vector(StringLiteralV2(
                                            language = None,
                                            value = "Champf\u00E8r"
                                        )),
                                        name = Some("5"),
                                        id = "http://rdfh.ch/lists/00FF/9004ce8eaf"
                                    ),
                                    ListNodeADM(
                                        position = Some(5),
                                        children = Nil,
                                        comments = Nil,
                                        labels = Vector(StringLiteralV2(
                                            language = None,
                                            value = "Pontresina"
                                        )),
                                        name = Some("6"),
                                        id = "http://rdfh.ch/lists/00FF/293821c8af"
                                    ),
                                    ListNodeADM(
                                        position = Some(6),
                                        children = Nil,
                                        comments = Nil,
                                        labels = Vector(StringLiteralV2(
                                            language = None,
                                            value = "Celerina"
                                        )),
                                        name = Some("7"),
                                        id = "http://rdfh.ch/lists/00FF/c26b7401b0"
                                    ),
                                    ListNodeADM(
                                        position = Some(7),
                                        children = Nil,
                                        comments = Nil,
                                        labels = Vector(StringLiteralV2(
                                            language = None,
                                            value = "Samedan"
                                        )),
                                        name = Some("8"),
                                        id = "http://rdfh.ch/lists/00FF/5b9fc73ab0"
                                    ),
                                    ListNodeADM(
                                        position = Some(8),
                                        children = Nil,
                                        comments = Nil,
                                        labels = Vector(StringLiteralV2(
                                            language = None,
                                            value = "Bever"
                                        )),
                                        name = Some("9"),
                                        id = "http://rdfh.ch/lists/00FF/f4d21a74b0"
                                    ),
                                    ListNodeADM(
                                        position = Some(9),
                                        children = Nil,
                                        comments = Nil,
                                        labels = Vector(StringLiteralV2(
                                            language = None,
                                            value = "La Punt"
                                        )),
                                        name = Some("10"),
                                        id = "http://rdfh.ch/lists/00FF/8d066eadb0"
                                    ),
                                    ListNodeADM(
                                        position = Some(10),
                                        children = Nil,
                                        comments = Nil,
                                        labels = Vector(StringLiteralV2(
                                            language = None,
                                            value = "Chamues-ch"
                                        )),
                                        name = Some("11"),
                                        id = "http://rdfh.ch/lists/00FF/263ac1e6b0"
                                    ),
                                    ListNodeADM(
                                        position = Some(11),
                                        children = Nil,
                                        comments = Nil,
                                        labels = Vector(StringLiteralV2(
                                            language = None,
                                            value = "Madulain"
                                        )),
                                        name = Some("12"),
                                        id = "http://rdfh.ch/lists/00FF/bf6d1420b1"
                                    ),
                                    ListNodeADM(
                                        position = Some(12),
                                        children = Nil,
                                        comments = Nil,
                                        labels = Vector(StringLiteralV2(
                                            language = None,
                                            value = "Zuoz"
                                        )),
                                        name = Some("13"),
                                        id = "http://rdfh.ch/lists/00FF/58a16759b1"
                                    ),
                                    ListNodeADM(
                                        position = Some(13),
                                        children = Nil,
                                        comments = Nil,
                                        labels = Vector(StringLiteralV2(
                                            language = None,
                                            value = "S-chanf"
                                        )),
                                        name = Some("14"),
                                        id = "http://rdfh.ch/lists/00FF/f1d4ba92b1"
                                    ),
                                    ListNodeADM(
                                        position = Some(14),
                                        children = Nil,
                                        comments = Nil,
                                        labels = Vector(StringLiteralV2(
                                            language = None,
                                            value = "Cinous-chel"
                                        )),
                                        name = Some("15"),
                                        id = "http://rdfh.ch/lists/00FF/8a080eccb1"
                                    ),
                                    ListNodeADM(
                                        position = Some(15),
                                        children = Nil,
                                        comments = Nil,
                                        labels = Vector(StringLiteralV2(
                                            language = None,
                                            value = "Fex"
                                        )),
                                        name = Some("16"),
                                        id = "http://rdfh.ch/lists/00FF/233c6105b2"
                                    ),
                                    ListNodeADM(
                                        position = Some(16),
                                        children = Nil,
                                        comments = Nil,
                                        labels = Vector(StringLiteralV2(
                                            language = None,
                                            value = "Unterengadin"
                                        )),
                                        name = Some("17"),
                                        id = "http://rdfh.ch/lists/00FF/bc6fb43eb2"
                                    ),
                                    ListNodeADM(
                                        position = Some(17),
                                        children = Nil,
                                        comments = Nil,
                                        labels = Vector(StringLiteralV2(
                                            language = None,
                                            value = "Personen"
                                        )),
                                        name = Some("18"),
                                        id = "http://rdfh.ch/lists/00FF/55a30778b2"
                                    )
                                ),
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Ortschaften Winter"
                                )),
                                name = Some("9"),
                                id = "http://rdfh.ch/lists/00FF/93022e70ae"
                            )
                        ),
                        comments = Nil,
                        labels = Vector(StringLiteralV2(
                            language = None,
                            value = "ENGADIN"
                        )),
                        name = Some("3"),
                        id = "http://rdfh.ch/lists/00FF/70916764a8"
                    ),
                    ListNodeADM(
                        position = Some(3),
                        children = Vector(
                            ListNodeADM(
                                position = Some(0),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "St. Moritz Dorf und Bad Winter"
                                )),
                                name = Some("1"),
                                id = "http://rdfh.ch/lists/00FF/870aaeeab2"
                            ),
                            ListNodeADM(
                                position = Some(1),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "St. Moritz Dorf Sommer"
                                )),
                                name = Some("2"),
                                id = "http://rdfh.ch/lists/00FF/203e0124b3"
                            ),
                            ListNodeADM(
                                position = Some(2),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "St. Moritz Bad Sommer"
                                )),
                                name = Some("3"),
                                id = "http://rdfh.ch/lists/00FF/b971545db3"
                            ),
                            ListNodeADM(
                                position = Some(3),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "St. Moritz Denkm\u00E4ler"
                                )),
                                name = Some("4"),
                                id = "http://rdfh.ch/lists/00FF/52a5a796b3"
                            ),
                            ListNodeADM(
                                position = Some(4),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "St. Moritz Landschaft Sommer"
                                )),
                                name = Some("5"),
                                id = "http://rdfh.ch/lists/00FF/ebd8facfb3"
                            ),
                            ListNodeADM(
                                position = Some(5),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "St. Moritz Landschaft Winter"
                                )),
                                name = Some("6"),
                                id = "http://rdfh.ch/lists/00FF/840c4e09b4"
                            ),
                            ListNodeADM(
                                position = Some(6),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "St. Moritz Schulh\u00E4user"
                                )),
                                name = Some("7"),
                                id = "http://rdfh.ch/lists/00FF/1d40a142b4"
                            )
                        ),
                        comments = Nil,
                        labels = Vector(StringLiteralV2(
                            language = None,
                            value = "ST. MORITZ"
                        )),
                        name = Some("4"),
                        id = "http://rdfh.ch/lists/00FF/eed65ab1b2"
                    ),
                    ListNodeADM(
                        position = Some(4),
                        children = Vector(
                            ListNodeADM(
                                position = Some(0),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Ortschaften"
                                )),
                                name = Some("1"),
                                id = "http://rdfh.ch/lists/00FF/4fa747b5b4"
                            ),
                            ListNodeADM(
                                position = Some(1),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Landschaften"
                                )),
                                name = Some("2"),
                                id = "http://rdfh.ch/lists/00FF/e8da9aeeb4"
                            ),
                            ListNodeADM(
                                position = Some(2),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Personen"
                                )),
                                name = Some("3"),
                                id = "http://rdfh.ch/lists/00FF/45cfa1df0401"
                            )
                        ),
                        comments = Nil,
                        labels = Vector(StringLiteralV2(
                            language = None,
                            value = "SUEDTAELER"
                        )),
                        name = Some("5"),
                        id = "http://rdfh.ch/lists/00FF/b673f47bb4"
                    ),
                    ListNodeADM(
                        position = Some(5),
                        children = Vector(
                            ListNodeADM(
                                position = Some(0),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Landkarten"
                                )),
                                name = Some("1"),
                                id = "http://rdfh.ch/lists/00FF/1a424161b5"
                            ),
                            ListNodeADM(
                                position = Some(1),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Panoramen"
                                )),
                                name = Some("2"),
                                id = "http://rdfh.ch/lists/00FF/b375949ab5"
                            )
                        ),
                        comments = Nil,
                        labels = Vector(StringLiteralV2(
                            language = None,
                            value = "LANDKARTEN UND PANORAMEN"
                        )),
                        name = Some("6"),
                        id = "http://rdfh.ch/lists/00FF/810eee27b5"
                    )
                ),
                comments = Nil,
                labels = Vector(StringLiteralV2(
                    language = None,
                    value = "GEOGRAPHIE"
                )),
                name = Some("2GEO"),
                id = "http://rdfh.ch/lists/00FF/738fc745a7"
            ),
            ListNodeADM(
                position = Some(2),
                children = Vector(
                    ListNodeADM(
                        position = Some(0),
                        children = Nil,
                        comments = Nil,
                        labels = Vector(StringLiteralV2(
                            language = None,
                            value = "SCHWEIZ"
                        )),
                        name = Some("1"),
                        id = "http://rdfh.ch/lists/00FF/de02f5180501"
                    ),
                    ListNodeADM(
                        position = Some(1),
                        children = Nil,
                        comments = Nil,
                        labels = Vector(StringLiteralV2(
                            language = None,
                            value = "GRAUB\u00DCNDEN"
                        )),
                        name = Some("2"),
                        id = "http://rdfh.ch/lists/00FF/773648520501"
                    ),
                    ListNodeADM(
                        position = Some(2),
                        children = Nil,
                        comments = Nil,
                        labels = Vector(StringLiteralV2(
                            language = None,
                            value = "ENGADIN"
                        )),
                        name = Some("3"),
                        id = "http://rdfh.ch/lists/00FF/106a9b8b0501"
                    ),
                    ListNodeADM(
                        position = Some(3),
                        children = Nil,
                        comments = Nil,
                        labels = Vector(StringLiteralV2(
                            language = None,
                            value = "ST. MORITZ"
                        )),
                        name = Some("4"),
                        id = "http://rdfh.ch/lists/00FF/a99deec40501"
                    ),
                    ListNodeADM(
                        position = Some(4),
                        children = Vector(
                            ListNodeADM(
                                position = Some(0),
                                children = Vector(
                                    ListNodeADM(
                                        position = Some(0),
                                        children = Nil,
                                        comments = Nil,
                                        labels = Vector(StringLiteralV2(
                                            language = None,
                                            value = "Personen A"
                                        )),
                                        name = Some("1"),
                                        id = "http://rdfh.ch/lists/00FF/1744e17fb6"
                                    ),
                                    ListNodeADM(
                                        position = Some(1),
                                        children = Nil,
                                        comments = Nil,
                                        labels = Vector(StringLiteralV2(
                                            language = None,
                                            value = "Personen B"
                                        )),
                                        name = Some("2"),
                                        id = "http://rdfh.ch/lists/00FF/b07734b9b6"
                                    ),
                                    ListNodeADM(
                                        position = Some(2),
                                        children = Nil,
                                        comments = Nil,
                                        labels = Vector(StringLiteralV2(
                                            language = None,
                                            value = "Personen C"
                                        )),
                                        name = Some("3"),
                                        id = "http://rdfh.ch/lists/00FF/49ab87f2b6"
                                    ),
                                    ListNodeADM(
                                        position = Some(3),
                                        children = Nil,
                                        comments = Nil,
                                        labels = Vector(StringLiteralV2(
                                            language = None,
                                            value = "Personen D"
                                        )),
                                        name = Some("4"),
                                        id = "http://rdfh.ch/lists/00FF/e2deda2bb7"
                                    ),
                                    ListNodeADM(
                                        position = Some(4),
                                        children = Nil,
                                        comments = Nil,
                                        labels = Vector(StringLiteralV2(
                                            language = None,
                                            value = "Personen E"
                                        )),
                                        name = Some("5"),
                                        id = "http://rdfh.ch/lists/00FF/7b122e65b7"
                                    ),
                                    ListNodeADM(
                                        position = Some(5),
                                        children = Nil,
                                        comments = Nil,
                                        labels = Vector(StringLiteralV2(
                                            language = None,
                                            value = "Personen F"
                                        )),
                                        name = Some("6"),
                                        id = "http://rdfh.ch/lists/00FF/1446819eb7"
                                    ),
                                    ListNodeADM(
                                        position = Some(6),
                                        children = Nil,
                                        comments = Nil,
                                        labels = Vector(StringLiteralV2(
                                            language = None,
                                            value = "Personen G"
                                        )),
                                        name = Some("7"),
                                        id = "http://rdfh.ch/lists/00FF/ad79d4d7b7"
                                    ),
                                    ListNodeADM(
                                        position = Some(7),
                                        children = Nil,
                                        comments = Nil,
                                        labels = Vector(StringLiteralV2(
                                            language = None,
                                            value = "Personen H"
                                        )),
                                        name = Some("8"),
                                        id = "http://rdfh.ch/lists/00FF/46ad2711b8"
                                    ),
                                    ListNodeADM(
                                        position = Some(8),
                                        children = Nil,
                                        comments = Nil,
                                        labels = Vector(StringLiteralV2(
                                            language = None,
                                            value = "Personen I"
                                        )),
                                        name = Some("9"),
                                        id = "http://rdfh.ch/lists/00FF/dfe07a4ab8"
                                    ),
                                    ListNodeADM(
                                        position = Some(9),
                                        children = Nil,
                                        comments = Nil,
                                        labels = Vector(StringLiteralV2(
                                            language = None,
                                            value = "Personen J"
                                        )),
                                        name = Some("10"),
                                        id = "http://rdfh.ch/lists/00FF/7814ce83b8"
                                    ),
                                    ListNodeADM(
                                        position = Some(10),
                                        children = Nil,
                                        comments = Nil,
                                        labels = Vector(StringLiteralV2(
                                            language = None,
                                            value = "Personen K"
                                        )),
                                        name = Some("11"),
                                        id = "http://rdfh.ch/lists/00FF/114821bdb8"
                                    ),
                                    ListNodeADM(
                                        position = Some(11),
                                        children = Nil,
                                        comments = Nil,
                                        labels = Vector(StringLiteralV2(
                                            language = None,
                                            value = "Personen L"
                                        )),
                                        name = Some("12"),
                                        id = "http://rdfh.ch/lists/00FF/aa7b74f6b8"
                                    ),
                                    ListNodeADM(
                                        position = Some(12),
                                        children = Nil,
                                        comments = Nil,
                                        labels = Vector(StringLiteralV2(
                                            language = None,
                                            value = "Personen M"
                                        )),
                                        name = Some("13"),
                                        id = "http://rdfh.ch/lists/00FF/43afc72fb9"
                                    ),
                                    ListNodeADM(
                                        position = Some(13),
                                        children = Nil,
                                        comments = Nil,
                                        labels = Vector(StringLiteralV2(
                                            language = None,
                                            value = "Personen N"
                                        )),
                                        name = Some("14"),
                                        id = "http://rdfh.ch/lists/00FF/dce21a69b9"
                                    ),
                                    ListNodeADM(
                                        position = Some(14),
                                        children = Nil,
                                        comments = Nil,
                                        labels = Vector(StringLiteralV2(
                                            language = None,
                                            value = "Personen O"
                                        )),
                                        name = Some("15"),
                                        id = "http://rdfh.ch/lists/00FF/75166ea2b9"
                                    ),
                                    ListNodeADM(
                                        position = Some(15),
                                        children = Nil,
                                        comments = Nil,
                                        labels = Vector(StringLiteralV2(
                                            language = None,
                                            value = "Personen P"
                                        )),
                                        name = Some("16"),
                                        id = "http://rdfh.ch/lists/00FF/0e4ac1dbb9"
                                    ),
                                    ListNodeADM(
                                        position = Some(16),
                                        children = Nil,
                                        comments = Nil,
                                        labels = Vector(StringLiteralV2(
                                            language = None,
                                            value = "Personen Q"
                                        )),
                                        name = Some("17"),
                                        id = "http://rdfh.ch/lists/00FF/a77d1415ba"
                                    ),
                                    ListNodeADM(
                                        position = Some(17),
                                        children = Nil,
                                        comments = Nil,
                                        labels = Vector(StringLiteralV2(
                                            language = None,
                                            value = "Personen R"
                                        )),
                                        name = Some("18"),
                                        id = "http://rdfh.ch/lists/00FF/40b1674eba"
                                    ),
                                    ListNodeADM(
                                        position = Some(18),
                                        children = Nil,
                                        comments = Nil,
                                        labels = Vector(StringLiteralV2(
                                            language = None,
                                            value = "Personen S"
                                        )),
                                        name = Some("19"),
                                        id = "http://rdfh.ch/lists/00FF/d9e4ba87ba"
                                    ),
                                    ListNodeADM(
                                        position = Some(19),
                                        children = Nil,
                                        comments = Nil,
                                        labels = Vector(StringLiteralV2(
                                            language = None,
                                            value = "Personen T"
                                        )),
                                        name = Some("20"),
                                        id = "http://rdfh.ch/lists/00FF/72180ec1ba"
                                    ),
                                    ListNodeADM(
                                        position = Some(20),
                                        children = Nil,
                                        comments = Nil,
                                        labels = Vector(StringLiteralV2(
                                            language = None,
                                            value = "Personen U"
                                        )),
                                        name = Some("21"),
                                        id = "http://rdfh.ch/lists/00FF/0b4c61faba"
                                    ),
                                    ListNodeADM(
                                        position = Some(21),
                                        children = Nil,
                                        comments = Nil,
                                        labels = Vector(StringLiteralV2(
                                            language = None,
                                            value = "Personen V"
                                        )),
                                        name = Some("22"),
                                        id = "http://rdfh.ch/lists/00FF/a47fb433bb"
                                    ),
                                    ListNodeADM(
                                        position = Some(22),
                                        children = Nil,
                                        comments = Nil,
                                        labels = Vector(StringLiteralV2(
                                            language = None,
                                            value = "Personen W"
                                        )),
                                        name = Some("23"),
                                        id = "http://rdfh.ch/lists/00FF/3db3076dbb"
                                    ),
                                    ListNodeADM(
                                        position = Some(23),
                                        children = Nil,
                                        comments = Nil,
                                        labels = Vector(StringLiteralV2(
                                            language = None,
                                            value = "Personen X"
                                        )),
                                        name = Some("24"),
                                        id = "http://rdfh.ch/lists/00FF/d6e65aa6bb"
                                    ),
                                    ListNodeADM(
                                        position = Some(24),
                                        children = Nil,
                                        comments = Nil,
                                        labels = Vector(StringLiteralV2(
                                            language = None,
                                            value = "Personen Y"
                                        )),
                                        name = Some("25"),
                                        id = "http://rdfh.ch/lists/00FF/6f1aaedfbb"
                                    ),
                                    ListNodeADM(
                                        position = Some(25),
                                        children = Nil,
                                        comments = Nil,
                                        labels = Vector(StringLiteralV2(
                                            language = None,
                                            value = "Personen Z"
                                        )),
                                        name = Some("26"),
                                        id = "http://rdfh.ch/lists/00FF/084e0119bc"
                                    )
                                ),
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Personen A-Z"
                                )),
                                name = Some("1"),
                                id = "http://rdfh.ch/lists/00FF/7e108e46b6"
                            ),
                            ListNodeADM(
                                position = Some(1),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Personen unbekannt"
                                )),
                                name = Some("2"),
                                id = "http://rdfh.ch/lists/00FF/a1815452bc"
                            ),
                            ListNodeADM(
                                position = Some(2),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Gruppen Einheimische"
                                )),
                                name = Some("3"),
                                id = "http://rdfh.ch/lists/00FF/3ab5a78bbc"
                            ),
                            ListNodeADM(
                                position = Some(3),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Kinder Winter"
                                )),
                                name = Some("4"),
                                id = "http://rdfh.ch/lists/00FF/d3e8fac4bc"
                            ),
                            ListNodeADM(
                                position = Some(4),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Kinder Sommer"
                                )),
                                name = Some("5"),
                                id = "http://rdfh.ch/lists/00FF/6c1c4efebc"
                            ),
                            ListNodeADM(
                                position = Some(5),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Sonnenbadende"
                                )),
                                name = Some("6"),
                                id = "http://rdfh.ch/lists/00FF/0550a137bd"
                            ),
                            ListNodeADM(
                                position = Some(6),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Zuschauer"
                                )),
                                name = Some("7"),
                                id = "http://rdfh.ch/lists/00FF/9e83f470bd"
                            )
                        ),
                        comments = Nil,
                        labels = Vector(StringLiteralV2(
                            language = None,
                            value = "BIOGRAPHIEN"
                        )),
                        name = Some("5"),
                        id = "http://rdfh.ch/lists/00FF/e5dc3a0db6"
                    ),
                    ListNodeADM(
                        position = Some(5),
                        children = Nil,
                        comments = Nil,
                        labels = Vector(StringLiteralV2(
                            language = None,
                            value = "WAPPEN UND FAHNEN"
                        )),
                        name = Some("7"),
                        id = "http://rdfh.ch/lists/00FF/37b747aabd"
                    ),
                    ListNodeADM(
                        position = Some(6),
                        children = Nil,
                        comments = Nil,
                        labels = Vector(StringLiteralV2(
                            language = None,
                            value = "KRIEGE UND MILIT\u00C4R"
                        )),
                        name = Some("9"),
                        id = "http://rdfh.ch/lists/00FF/d0ea9ae3bd"
                    )
                ),
                comments = Nil,
                labels = Vector(StringLiteralV2(
                    language = None,
                    value = "GESCHICHTE"
                )),
                name = Some("3GES"),
                id = "http://rdfh.ch/lists/00FF/4ca9e7d3b5"
            ),
            ListNodeADM(
                position = Some(3),
                children = Vector(
                    ListNodeADM(
                        position = Some(0),
                        children = Vector(
                            ListNodeADM(
                                position = Some(0),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Ausstellungen"
                                )),
                                name = Some("1"),
                                id = "http://rdfh.ch/lists/00FF/9b85948fbe"
                            ),
                            ListNodeADM(
                                position = Some(1),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Gem\u00E4lde"
                                )),
                                name = Some("2"),
                                id = "http://rdfh.ch/lists/00FF/34b9e7c8be"
                            ),
                            ListNodeADM(
                                position = Some(2),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Karrikaturen und Kritik"
                                )),
                                name = Some("3"),
                                id = "http://rdfh.ch/lists/00FF/cdec3a02bf"
                            ),
                            ListNodeADM(
                                position = Some(3),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Segantini und Museum"
                                )),
                                name = Some("4"),
                                id = "http://rdfh.ch/lists/00FF/66208e3bbf"
                            ),
                            ListNodeADM(
                                position = Some(4),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Sgrafitti"
                                )),
                                name = Some("5"),
                                id = "http://rdfh.ch/lists/00FF/ff53e174bf"
                            )
                        ),
                        comments = Nil,
                        labels = Vector(StringLiteralV2(
                            language = None,
                            value = "MALEREI"
                        )),
                        name = Some("5"),
                        id = "http://rdfh.ch/lists/00FF/02524156be"
                    ),
                    ListNodeADM(
                        position = Some(1),
                        children = Vector(
                            ListNodeADM(
                                position = Some(0),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Kurorchester"
                                )),
                                name = Some("1"),
                                id = "http://rdfh.ch/lists/00FF/31bb87e7bf"
                            ),
                            ListNodeADM(
                                position = Some(1),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Musik"
                                )),
                                name = Some("2"),
                                id = "http://rdfh.ch/lists/00FF/caeeda20c0"
                            ),
                            ListNodeADM(
                                position = Some(2),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Zirkus"
                                )),
                                name = Some("3"),
                                id = "http://rdfh.ch/lists/00FF/63222e5ac0"
                            ),
                            ListNodeADM(
                                position = Some(3),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Theater"
                                )),
                                name = Some("4"),
                                id = "http://rdfh.ch/lists/00FF/fc558193c0"
                            ),
                            ListNodeADM(
                                position = Some(4),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Tanz"
                                )),
                                name = Some("5"),
                                id = "http://rdfh.ch/lists/00FF/9589d4ccc0"
                            )
                        ),
                        comments = Nil,
                        labels = Vector(StringLiteralV2(
                            language = None,
                            value = "MUSIK, THEATER UND RADIO"
                        )),
                        name = Some("6"),
                        id = "http://rdfh.ch/lists/00FF/988734aebf"
                    ),
                    ListNodeADM(
                        position = Some(2),
                        children = Vector(
                            ListNodeADM(
                                position = Some(0),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Heidi Film"
                                )),
                                name = Some("1"),
                                id = "http://rdfh.ch/lists/00FF/c7f07a3fc1"
                            ),
                            ListNodeADM(
                                position = Some(1),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Foto"
                                )),
                                name = Some("2"),
                                id = "http://rdfh.ch/lists/00FF/6024ce78c1"
                            ),
                            ListNodeADM(
                                position = Some(2),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Film"
                                )),
                                name = Some("3"),
                                id = "http://rdfh.ch/lists/00FF/f95721b2c1"
                            )
                        ),
                        comments = Nil,
                        labels = Vector(StringLiteralV2(
                            language = None,
                            value = "FILM UND FOTO"
                        )),
                        name = Some("7"),
                        id = "http://rdfh.ch/lists/00FF/2ebd2706c1"
                    ),
                    ListNodeADM(
                        position = Some(3),
                        children = Vector(
                            ListNodeADM(
                                position = Some(0),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Modelle"
                                )),
                                name = Some("1"),
                                id = "http://rdfh.ch/lists/00FF/2bbfc724c2"
                            ),
                            ListNodeADM(
                                position = Some(1),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Schneeskulpturen"
                                )),
                                name = Some("2"),
                                id = "http://rdfh.ch/lists/00FF/c4f21a5ec2"
                            ),
                            ListNodeADM(
                                position = Some(2),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Plastiken"
                                )),
                                name = Some("3"),
                                id = "http://rdfh.ch/lists/00FF/5d266e97c2"
                            ),
                            ListNodeADM(
                                position = Some(3),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Stiche"
                                )),
                                name = Some("4"),
                                id = "http://rdfh.ch/lists/00FF/f659c1d0c2"
                            ),
                            ListNodeADM(
                                position = Some(4),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Bildhauerei"
                                )),
                                name = Some("5"),
                                id = "http://rdfh.ch/lists/00FF/8f8d140ac3"
                            ),
                            ListNodeADM(
                                position = Some(5),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Kunstgewerbe"
                                )),
                                name = Some("6"),
                                id = "http://rdfh.ch/lists/00FF/28c16743c3"
                            )
                        ),
                        comments = Nil,
                        labels = Vector(StringLiteralV2(
                            language = None,
                            value = "BILDHAUEREI UND KUNSTGEWERBE"
                        )),
                        name = Some("8"),
                        id = "http://rdfh.ch/lists/00FF/928b74ebc1"
                    ),
                    ListNodeADM(
                        position = Some(4),
                        children = Vector(
                            ListNodeADM(
                                position = Some(0),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Grafiken"
                                )),
                                name = Some("1"),
                                id = "http://rdfh.ch/lists/00FF/5a280eb6c3"
                            ),
                            ListNodeADM(
                                position = Some(1),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Holzschnitte"
                                )),
                                name = Some("2"),
                                id = "http://rdfh.ch/lists/00FF/f35b61efc3"
                            ),
                            ListNodeADM(
                                position = Some(2),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Plakate"
                                )),
                                name = Some("3"),
                                id = "http://rdfh.ch/lists/00FF/8c8fb428c4"
                            )
                        ),
                        comments = Nil,
                        labels = Vector(StringLiteralV2(
                            language = None,
                            value = "ST. MORITZ GRAFIKEN UND PLAKATE"
                        )),
                        name = Some("9"),
                        id = "http://rdfh.ch/lists/00FF/c1f4ba7cc3"
                    ),
                    ListNodeADM(
                        position = Some(5),
                        children = Vector(
                            ListNodeADM(
                                position = Some(0),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Architektur / Inneneinrichtungen"
                                )),
                                name = Some("1"),
                                id = "http://rdfh.ch/lists/00FF/bef65a9bc4"
                            ),
                            ListNodeADM(
                                position = Some(1),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Pl\u00E4ne"
                                )),
                                name = Some("2"),
                                id = "http://rdfh.ch/lists/00FF/572aaed4c4"
                            )
                        ),
                        comments = Nil,
                        labels = Vector(StringLiteralV2(
                            language = None,
                            value = "ARCHITEKTUR"
                        )),
                        name = Some("10"),
                        id = "http://rdfh.ch/lists/00FF/25c30762c4"
                    )
                ),
                comments = Nil,
                labels = Vector(StringLiteralV2(
                    language = None,
                    value = "KUNST"
                )),
                name = Some("4KUN"),
                id = "http://rdfh.ch/lists/00FF/691eee1cbe"
            ),
            ListNodeADM(
                position = Some(4),
                children = Vector(
                    ListNodeADM(
                        position = Some(0),
                        children = Nil,
                        comments = Nil,
                        labels = Vector(StringLiteralV2(
                            language = None,
                            value = "MEDIZIN UND NATURHEILKUNDE"
                        )),
                        name = Some("1"),
                        id = "http://rdfh.ch/lists/00FF/89915447c5"
                    ),
                    ListNodeADM(
                        position = Some(1),
                        children = Vector(
                            ListNodeADM(
                                position = Some(0),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Heilbad aussen"
                                )),
                                name = Some("1"),
                                id = "http://rdfh.ch/lists/00FF/bbf8fab9c5"
                            ),
                            ListNodeADM(
                                position = Some(1),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Heilbad innen"
                                )),
                                name = Some("2"),
                                id = "http://rdfh.ch/lists/00FF/542c4ef3c5"
                            )
                        ),
                        comments = Nil,
                        labels = Vector(StringLiteralV2(
                            language = None,
                            value = "HEILBAD UND QUELLEN"
                        )),
                        name = Some("3"),
                        id = "http://rdfh.ch/lists/00FF/22c5a780c5"
                    ),
                    ListNodeADM(
                        position = Some(2),
                        children = Nil,
                        comments = Nil,
                        labels = Vector(StringLiteralV2(
                            language = None,
                            value = "SPITAL UND KLINIKEN / KINDERHEIME"
                        )),
                        name = Some("4"),
                        id = "http://rdfh.ch/lists/00FF/ed5fa12cc6"
                    )
                ),
                comments = Nil,
                labels = Vector(StringLiteralV2(
                    language = None,
                    value = "MEDIZIN"
                )),
                name = Some("5MED"),
                id = "http://rdfh.ch/lists/00FF/f05d010ec5"
            ),
            ListNodeADM(
                position = Some(5),
                children = Vector(
                    ListNodeADM(
                        position = Some(0),
                        children = Vector(
                            ListNodeADM(
                                position = Some(0),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Fischen"
                                )),
                                name = Some("1"),
                                id = "http://rdfh.ch/lists/00FF/b8fa9ad8c6"
                            ),
                            ListNodeADM(
                                position = Some(1),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Jagen"
                                )),
                                name = Some("2"),
                                id = "http://rdfh.ch/lists/00FF/512eee11c7"
                            ),
                            ListNodeADM(
                                position = Some(2),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Tiere"
                                )),
                                name = Some("3"),
                                id = "http://rdfh.ch/lists/00FF/ea61414bc7"
                            )
                        ),
                        comments = Nil,
                        labels = Vector(StringLiteralV2(
                            language = None,
                            value = "FAUNA"
                        )),
                        name = Some("2"),
                        id = "http://rdfh.ch/lists/00FF/1fc7479fc6"
                    ),
                    ListNodeADM(
                        position = Some(1),
                        children = Vector(
                            ListNodeADM(
                                position = Some(0),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Blumen"
                                )),
                                name = Some("1"),
                                id = "http://rdfh.ch/lists/00FF/1cc9e7bdc7"
                            ),
                            ListNodeADM(
                                position = Some(1),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "B\u00E4ume"
                                )),
                                name = Some("2"),
                                id = "http://rdfh.ch/lists/00FF/b5fc3af7c7"
                            )
                        ),
                        comments = Nil,
                        labels = Vector(StringLiteralV2(
                            language = None,
                            value = "FLORA"
                        )),
                        name = Some("3"),
                        id = "http://rdfh.ch/lists/00FF/83959484c7"
                    ),
                    ListNodeADM(
                        position = Some(2),
                        children = Nil,
                        comments = Nil,
                        labels = Vector(StringLiteralV2(
                            language = None,
                            value = "GEOLOGIE UND MINERALOGIE"
                        )),
                        name = Some("4"),
                        id = "http://rdfh.ch/lists/00FF/4e308e30c8"
                    ),
                    ListNodeADM(
                        position = Some(3),
                        children = Vector(
                            ListNodeADM(
                                position = Some(0),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Gew\u00E4sser und \u00DCberschwemmungen"
                                )),
                                name = Some("1"),
                                id = "http://rdfh.ch/lists/00FF/809734a3c8"
                            ),
                            ListNodeADM(
                                position = Some(1),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Gletscher"
                                )),
                                name = Some("2"),
                                id = "http://rdfh.ch/lists/00FF/19cb87dcc8"
                            ),
                            ListNodeADM(
                                position = Some(2),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Lawinen"
                                )),
                                name = Some("3"),
                                id = "http://rdfh.ch/lists/00FF/b2feda15c9"
                            ),
                            ListNodeADM(
                                position = Some(3),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Schnee, Raureif, Eisblumen"
                                )),
                                name = Some("4"),
                                id = "http://rdfh.ch/lists/00FF/4b322e4fc9"
                            )
                        ),
                        comments = Nil,
                        labels = Vector(StringLiteralV2(
                            language = None,
                            value = "KLIMATOLOGIE UND METEOROLOGIE"
                        )),
                        name = Some("5"),
                        id = "http://rdfh.ch/lists/00FF/e763e169c8"
                    ),
                    ListNodeADM(
                        position = Some(4),
                        children = Nil,
                        comments = Nil,
                        labels = Vector(StringLiteralV2(
                            language = None,
                            value = "UMWELT"
                        )),
                        name = Some("6"),
                        id = "http://rdfh.ch/lists/00FF/e4658188c9"
                    )
                ),
                comments = Nil,
                labels = Vector(StringLiteralV2(
                    language = None,
                    value = "NATURKUNDE"
                )),
                name = Some("6NAT"),
                id = "http://rdfh.ch/lists/00FF/8693f465c6"
            ),
            ListNodeADM(
                position = Some(6),
                children = Vector(ListNodeADM(
                    position = Some(0),
                    children = Vector(ListNodeADM(
                        position = Some(0),
                        children = Nil,
                        comments = Nil,
                        labels = Vector(StringLiteralV2(
                            language = None,
                            value = "St. Moritz Kirchen"
                        )),
                        name = Some("1"),
                        id = "http://rdfh.ch/lists/00FF/af007b34ca"
                    )),
                    comments = Nil,
                    labels = Vector(StringLiteralV2(
                        language = None,
                        value = "RELIGION UND KIRCHEN"
                    )),
                    name = Some("1"),
                    id = "http://rdfh.ch/lists/00FF/16cd27fbc9"
                )),
                comments = Nil,
                labels = Vector(StringLiteralV2(
                    language = None,
                    value = "RELIGION"
                )),
                name = Some("7REL"),
                id = "http://rdfh.ch/lists/00FF/7d99d4c1c9"
            ),
            ListNodeADM(
                position = Some(7),
                children = Vector(
                    ListNodeADM(
                        position = Some(0),
                        children = Nil,
                        comments = Nil,
                        labels = Vector(StringLiteralV2(
                            language = None,
                            value = "VERFASSUNGEN UND GESETZE"
                        )),
                        name = Some("1"),
                        id = "http://rdfh.ch/lists/00FF/e16721a7ca"
                    ),
                    ListNodeADM(
                        position = Some(1),
                        children = Vector(
                            ListNodeADM(
                                position = Some(0),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Wasserwirtschaft"
                                )),
                                name = Some("1"),
                                id = "http://rdfh.ch/lists/00FF/13cfc719cb"
                            ),
                            ListNodeADM(
                                position = Some(1),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Feuer und Feuerwehr"
                                )),
                                name = Some("2"),
                                id = "http://rdfh.ch/lists/00FF/ac021b53cb"
                            ),
                            ListNodeADM(
                                position = Some(2),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Polizei und Beh\u00F6rde"
                                )),
                                name = Some("3"),
                                id = "http://rdfh.ch/lists/00FF/45366e8ccb"
                            ),
                            ListNodeADM(
                                position = Some(3),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Abfallbewirtschaftung"
                                )),
                                name = Some("4"),
                                id = "http://rdfh.ch/lists/00FF/de69c1c5cb"
                            )
                        ),
                        comments = Nil,
                        labels = Vector(StringLiteralV2(
                            language = None,
                            value = "GEMEINDEWESEN"
                        )),
                        name = Some("2"),
                        id = "http://rdfh.ch/lists/00FF/7a9b74e0ca"
                    ),
                    ListNodeADM(
                        position = Some(2),
                        children = Nil,
                        comments = Nil,
                        labels = Vector(StringLiteralV2(
                            language = None,
                            value = "SCHULWESEN"
                        )),
                        name = Some("3"),
                        id = "http://rdfh.ch/lists/00FF/779d14ffcb"
                    ),
                    ListNodeADM(
                        position = Some(3),
                        children = Vector(
                            ListNodeADM(
                                position = Some(0),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "B\u00E4lle und Verkleidungen"
                                )),
                                name = Some("1"),
                                id = "http://rdfh.ch/lists/00FF/a904bb71cc"
                            ),
                            ListNodeADM(
                                position = Some(1),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Chalandamarz"
                                )),
                                name = Some("2"),
                                id = "http://rdfh.ch/lists/00FF/42380eabcc"
                            ),
                            ListNodeADM(
                                position = Some(2),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Engadiner Museum"
                                )),
                                name = Some("3"),
                                id = "http://rdfh.ch/lists/00FF/db6b61e4cc"
                            ),
                            ListNodeADM(
                                position = Some(3),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Feste und Umz\u00FCge"
                                )),
                                name = Some("4"),
                                id = "http://rdfh.ch/lists/00FF/749fb41dcd"
                            ),
                            ListNodeADM(
                                position = Some(4),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Schlitteda"
                                )),
                                name = Some("5"),
                                id = "http://rdfh.ch/lists/00FF/0dd30757cd"
                            ),
                            ListNodeADM(
                                position = Some(5),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Trachten"
                                )),
                                name = Some("6"),
                                id = "http://rdfh.ch/lists/00FF/a6065b90cd"
                            )
                        ),
                        comments = Nil,
                        labels = Vector(StringLiteralV2(
                            language = None,
                            value = "VOLKSKUNDE"
                        )),
                        name = Some("4"),
                        id = "http://rdfh.ch/lists/00FF/10d16738cc"
                    ),
                    ListNodeADM(
                        position = Some(4),
                        children = Nil,
                        comments = Nil,
                        labels = Vector(StringLiteralV2(
                            language = None,
                            value = "PARTEIEN UND GRUPPIERUNGEN"
                        )),
                        name = Some("6"),
                        id = "http://rdfh.ch/lists/00FF/3f3aaec9cd"
                    ),
                    ListNodeADM(
                        position = Some(5),
                        children = Nil,
                        comments = Nil,
                        labels = Vector(StringLiteralV2(
                            language = None,
                            value = "SCHWESTERNST\u00C4TDE"
                        )),
                        name = Some("7"),
                        id = "http://rdfh.ch/lists/00FF/d86d0103ce"
                    )
                ),
                comments = Nil,
                labels = Vector(StringLiteralV2(
                    language = None,
                    value = "SOZIALES"
                )),
                name = Some("8SOZ"),
                id = "http://rdfh.ch/lists/00FF/4834ce6dca"
            ),
            ListNodeADM(
                position = Some(8),
                children = Vector(
                    ListNodeADM(
                        position = Some(0),
                        children = Vector(
                            ListNodeADM(
                                position = Some(0),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Bridge"
                                )),
                                name = Some("1"),
                                id = "http://rdfh.ch/lists/00FF/a308fbaece"
                            ),
                            ListNodeADM(
                                position = Some(1),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Boxen"
                                )),
                                name = Some("2"),
                                id = "http://rdfh.ch/lists/00FF/3c3c4ee8ce"
                            ),
                            ListNodeADM(
                                position = Some(2),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Camping"
                                )),
                                name = Some("3"),
                                id = "http://rdfh.ch/lists/00FF/d56fa121cf"
                            ),
                            ListNodeADM(
                                position = Some(3),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Fechten"
                                )),
                                name = Some("4"),
                                id = "http://rdfh.ch/lists/00FF/6ea3f45acf"
                            ),
                            ListNodeADM(
                                position = Some(4),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Fitness"
                                )),
                                name = Some("5"),
                                id = "http://rdfh.ch/lists/00FF/07d74794cf"
                            ),
                            ListNodeADM(
                                position = Some(5),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "H\u00F6hentraining"
                                )),
                                name = Some("6"),
                                id = "http://rdfh.ch/lists/00FF/a00a9bcdcf"
                            ),
                            ListNodeADM(
                                position = Some(6),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Krafttraining"
                                )),
                                name = Some("7"),
                                id = "http://rdfh.ch/lists/00FF/393eee06d0"
                            ),
                            ListNodeADM(
                                position = Some(7),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Leichtathletik"
                                )),
                                name = Some("8"),
                                id = "http://rdfh.ch/lists/00FF/d2714140d0"
                            ),
                            ListNodeADM(
                                position = Some(8),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Pokale, Preise, Medallien"
                                )),
                                name = Some("9"),
                                id = "http://rdfh.ch/lists/00FF/6ba59479d0"
                            ),
                            ListNodeADM(
                                position = Some(9),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Schiessen"
                                )),
                                name = Some("10"),
                                id = "http://rdfh.ch/lists/00FF/04d9e7b2d0"
                            ),
                            ListNodeADM(
                                position = Some(10),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Turnen"
                                )),
                                name = Some("11"),
                                id = "http://rdfh.ch/lists/00FF/9d0c3becd0"
                            ),
                            ListNodeADM(
                                position = Some(11),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Zeitmessung"
                                )),
                                name = Some("12"),
                                id = "http://rdfh.ch/lists/00FF/36408e25d1"
                            ),
                            ListNodeADM(
                                position = Some(12),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Hornussen"
                                )),
                                name = Some("13"),
                                id = "http://rdfh.ch/lists/00FF/cf73e15ed1"
                            ),
                            ListNodeADM(
                                position = Some(13),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Schwingen"
                                )),
                                name = Some("14"),
                                id = "http://rdfh.ch/lists/00FF/68a73498d1"
                            )
                        ),
                        comments = Nil,
                        labels = Vector(StringLiteralV2(
                            language = None,
                            value = "SPORT"
                        )),
                        name = Some("0"),
                        id = "http://rdfh.ch/lists/00FF/0ad5a775ce"
                    ),
                    ListNodeADM(
                        position = Some(1),
                        children = Vector(
                            ListNodeADM(
                                position = Some(0),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Cricket"
                                )),
                                name = Some("1"),
                                id = "http://rdfh.ch/lists/00FF/9a0edb0ad2"
                            ),
                            ListNodeADM(
                                position = Some(1),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Schlitteln"
                                )),
                                name = Some("2"),
                                id = "http://rdfh.ch/lists/00FF/33422e44d2"
                            ),
                            ListNodeADM(
                                position = Some(2),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Schneeschuhlaufen"
                                )),
                                name = Some("3"),
                                id = "http://rdfh.ch/lists/00FF/cc75817dd2"
                            ),
                            ListNodeADM(
                                position = Some(3),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Tailing"
                                )),
                                name = Some("4"),
                                id = "http://rdfh.ch/lists/00FF/65a9d4b6d2"
                            ),
                            ListNodeADM(
                                position = Some(4),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Wind-, Schlittenhundrennen"
                                )),
                                name = Some("5"),
                                id = "http://rdfh.ch/lists/00FF/fedc27f0d2"
                            )
                        ),
                        comments = Nil,
                        labels = Vector(StringLiteralV2(
                            language = None,
                            value = "WINTERSPORT"
                        )),
                        name = Some("1"),
                        id = "http://rdfh.ch/lists/00FF/01db87d1d1"
                    ),
                    ListNodeADM(
                        position = Some(2),
                        children = Vector(
                            ListNodeADM(
                                position = Some(0),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Verschiedenes"
                                )),
                                name = Some("1"),
                                id = "http://rdfh.ch/lists/00FF/3044ce62d3"
                            ),
                            ListNodeADM(
                                position = Some(1),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Skiakrobatik"
                                )),
                                name = Some("2"),
                                id = "http://rdfh.ch/lists/00FF/c977219cd3"
                            ),
                            ListNodeADM(
                                position = Some(2),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Ski Corvatsch"
                                )),
                                name = Some("3"),
                                id = "http://rdfh.ch/lists/00FF/62ab74d5d3"
                            ),
                            ListNodeADM(
                                position = Some(3),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Skifahren"
                                )),
                                name = Some("4"),
                                id = "http://rdfh.ch/lists/00FF/fbdec70ed4"
                            ),
                            ListNodeADM(
                                position = Some(4),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Ski Kilometer-Lanc\u00E9"
                                )),
                                name = Some("5"),
                                id = "http://rdfh.ch/lists/00FF/94121b48d4"
                            ),
                            ListNodeADM(
                                position = Some(5),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Ski SOS"
                                )),
                                name = Some("6"),
                                id = "http://rdfh.ch/lists/00FF/2d466e81d4"
                            ),
                            ListNodeADM(
                                position = Some(6),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Skitouren"
                                )),
                                name = Some("7"),
                                id = "http://rdfh.ch/lists/00FF/c679c1bad4"
                            )
                        ),
                        comments = Nil,
                        labels = Vector(StringLiteralV2(
                            language = None,
                            value = "SKI"
                        )),
                        name = Some("2"),
                        id = "http://rdfh.ch/lists/00FF/97107b29d3"
                    ),
                    ListNodeADM(
                        position = Some(3),
                        children = Nil,
                        comments = Nil,
                        labels = Vector(StringLiteralV2(
                            language = None,
                            value = "SKISCHULE"
                        )),
                        name = Some("2-2"),
                        id = "http://rdfh.ch/lists/00FF/5fad14f4d4"
                    ),
                    ListNodeADM(
                        position = Some(4),
                        children = Vector(
                            ListNodeADM(
                                position = Some(0),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Skirennen"
                                )),
                                name = Some("1"),
                                id = "http://rdfh.ch/lists/00FF/9114bb66d5"
                            ),
                            ListNodeADM(
                                position = Some(1),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Ski Rennpisten"
                                )),
                                name = Some("2"),
                                id = "http://rdfh.ch/lists/00FF/2a480ea0d5"
                            ),
                            ListNodeADM(
                                position = Some(2),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Personen"
                                )),
                                name = Some("3"),
                                id = "http://rdfh.ch/lists/00FF/c37b61d9d5"
                            ),
                            ListNodeADM(
                                position = Some(3),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Guardia Grischa"
                                )),
                                name = Some("4"),
                                id = "http://rdfh.ch/lists/00FF/5cafb412d6"
                            ),
                            ListNodeADM(
                                position = Some(4),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Ski Vorweltmeisterschaft 1973"
                                )),
                                name = Some("5"),
                                id = "http://rdfh.ch/lists/00FF/f5e2074cd6"
                            ),
                            ListNodeADM(
                                position = Some(5),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Ski Weltmeisterschaft 1974"
                                )),
                                name = Some("6"),
                                id = "http://rdfh.ch/lists/00FF/8e165b85d6"
                            ),
                            ListNodeADM(
                                position = Some(6),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Ski Weltmeisterschaft 2003"
                                )),
                                name = Some("7"),
                                id = "http://rdfh.ch/lists/00FF/274aaebed6"
                            ),
                            ListNodeADM(
                                position = Some(7),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Skispringen"
                                )),
                                name = Some("8"),
                                id = "http://rdfh.ch/lists/00FF/c07d01f8d6"
                            )
                        ),
                        comments = Nil,
                        labels = Vector(StringLiteralV2(
                            language = None,
                            value = "SKIRENNEN UND SKISPRINGEN"
                        )),
                        name = Some("2-3"),
                        id = "http://rdfh.ch/lists/00FF/f8e0672dd5"
                    ),
                    ListNodeADM(
                        position = Some(5),
                        children = Vector(
                            ListNodeADM(
                                position = Some(0),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Skilanglauf"
                                )),
                                name = Some("1"),
                                id = "http://rdfh.ch/lists/00FF/f2e4a76ad7"
                            ),
                            ListNodeADM(
                                position = Some(1),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Engadin Skimarathon"
                                )),
                                name = Some("2"),
                                id = "http://rdfh.ch/lists/00FF/8b18fba3d7"
                            )
                        ),
                        comments = Nil,
                        labels = Vector(StringLiteralV2(
                            language = None,
                            value = "SKILANGLAUF UND ENGADIN SKIMARATHON"
                        )),
                        name = Some("2-4"),
                        id = "http://rdfh.ch/lists/00FF/59b15431d7"
                    ),
                    ListNodeADM(
                        position = Some(6),
                        children = Nil,
                        comments = Nil,
                        labels = Vector(StringLiteralV2(
                            language = None,
                            value = "SNOWBOARD UND SNOWBOARDSCHULE"
                        )),
                        name = Some("2-5"),
                        id = "http://rdfh.ch/lists/00FF/244c4eddd7"
                    ),
                    ListNodeADM(
                        position = Some(7),
                        children = Vector(
                            ListNodeADM(
                                position = Some(0),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Olympiade 1928"
                                )),
                                name = Some("1"),
                                id = "http://rdfh.ch/lists/00FF/56b3f44fd8"
                            ),
                            ListNodeADM(
                                position = Some(1),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Olympiade 1948"
                                )),
                                name = Some("2"),
                                id = "http://rdfh.ch/lists/00FF/efe64789d8"
                            )
                        ),
                        comments = Nil,
                        labels = Vector(StringLiteralV2(
                            language = None,
                            value = "OLYMPIADEN"
                        )),
                        name = Some("3"),
                        id = "http://rdfh.ch/lists/00FF/bd7fa116d8"
                    ),
                    ListNodeADM(
                        position = Some(8),
                        children = Vector(
                            ListNodeADM(
                                position = Some(0),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Eishockey und Bandy"
                                )),
                                name = Some("1"),
                                id = "http://rdfh.ch/lists/00FF/214eeefbd8"
                            ),
                            ListNodeADM(
                                position = Some(1),
                                children = Vector(
                                    ListNodeADM(
                                        position = Some(0),
                                        children = Nil,
                                        comments = Nil,
                                        labels = Vector(StringLiteralV2(
                                            language = None,
                                            value = "Gefrorene Seen"
                                        )),
                                        name = Some("1"),
                                        id = "http://rdfh.ch/lists/00FF/53b5946ed9"
                                    ),
                                    ListNodeADM(
                                        position = Some(1),
                                        children = Nil,
                                        comments = Nil,
                                        labels = Vector(StringLiteralV2(
                                            language = None,
                                            value = "Gymkhana"
                                        )),
                                        name = Some("2"),
                                        id = "http://rdfh.ch/lists/00FF/ece8e7a7d9"
                                    ),
                                    ListNodeADM(
                                        position = Some(2),
                                        children = Nil,
                                        comments = Nil,
                                        labels = Vector(StringLiteralV2(
                                            language = None,
                                            value = "Eisrevue"
                                        )),
                                        name = Some("3"),
                                        id = "http://rdfh.ch/lists/00FF/851c3be1d9"
                                    ),
                                    ListNodeADM(
                                        position = Some(3),
                                        children = Nil,
                                        comments = Nil,
                                        labels = Vector(StringLiteralV2(
                                            language = None,
                                            value = "Paarlauf"
                                        )),
                                        name = Some("4"),
                                        id = "http://rdfh.ch/lists/00FF/1e508e1ada"
                                    ),
                                    ListNodeADM(
                                        position = Some(4),
                                        children = Nil,
                                        comments = Nil,
                                        labels = Vector(StringLiteralV2(
                                            language = None,
                                            value = "Schnellauf"
                                        )),
                                        name = Some("5"),
                                        id = "http://rdfh.ch/lists/00FF/b783e153da"
                                    ),
                                    ListNodeADM(
                                        position = Some(5),
                                        children = Nil,
                                        comments = Nil,
                                        labels = Vector(StringLiteralV2(
                                            language = None,
                                            value = "Kellner auf Eis"
                                        )),
                                        name = Some("6"),
                                        id = "http://rdfh.ch/lists/00FF/50b7348dda"
                                    ),
                                    ListNodeADM(
                                        position = Some(6),
                                        children = Nil,
                                        comments = Nil,
                                        labels = Vector(StringLiteralV2(
                                            language = None,
                                            value = "Personen"
                                        )),
                                        name = Some("7"),
                                        id = "http://rdfh.ch/lists/00FF/e9ea87c6da"
                                    )
                                ),
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Eislaufen"
                                )),
                                name = Some("2"),
                                id = "http://rdfh.ch/lists/00FF/ba814135d9"
                            ),
                            ListNodeADM(
                                position = Some(2),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Eissegeln, -Surfen"
                                )),
                                name = Some("3"),
                                id = "http://rdfh.ch/lists/00FF/821edbffda"
                            ),
                            ListNodeADM(
                                position = Some(3),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Eisstadion"
                                )),
                                name = Some("4"),
                                id = "http://rdfh.ch/lists/00FF/1b522e39db"
                            ),
                            ListNodeADM(
                                position = Some(4),
                                children = Vector(ListNodeADM(
                                    position = Some(0),
                                    children = Nil,
                                    comments = Nil,
                                    labels = Vector(StringLiteralV2(
                                        language = None,
                                        value = "Personen"
                                    )),
                                    name = Some("1"),
                                    id = "http://rdfh.ch/lists/00FF/4db9d4abdb"
                                )),
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Curling"
                                )),
                                name = Some("5"),
                                id = "http://rdfh.ch/lists/00FF/b4858172db"
                            ),
                            ListNodeADM(
                                position = Some(5),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Eisstockschiessen"
                                )),
                                name = Some("6"),
                                id = "http://rdfh.ch/lists/00FF/e6ec27e5db"
                            ),
                            ListNodeADM(
                                position = Some(6),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Kunsteisbahn Ludains"
                                )),
                                name = Some("7"),
                                id = "http://rdfh.ch/lists/00FF/7f207b1edc"
                            )
                        ),
                        comments = Nil,
                        labels = Vector(StringLiteralV2(
                            language = None,
                            value = "EISSPORT"
                        )),
                        name = Some("4"),
                        id = "http://rdfh.ch/lists/00FF/881a9bc2d8"
                    ),
                    ListNodeADM(
                        position = Some(9),
                        children = Vector(
                            ListNodeADM(
                                position = Some(0),
                                children = Vector(
                                    ListNodeADM(
                                        position = Some(0),
                                        children = Nil,
                                        comments = Nil,
                                        labels = Vector(StringLiteralV2(
                                            language = None,
                                            value = "Personen"
                                        )),
                                        name = Some("1"),
                                        id = "http://rdfh.ch/lists/00FF/4abb74cadc"
                                    ),
                                    ListNodeADM(
                                        position = Some(1),
                                        children = Nil,
                                        comments = Nil,
                                        labels = Vector(StringLiteralV2(
                                            language = None,
                                            value = "St\u00FCrze"
                                        )),
                                        name = Some("2"),
                                        id = "http://rdfh.ch/lists/00FF/e3eec703dd"
                                    ),
                                    ListNodeADM(
                                        position = Some(2),
                                        children = Nil,
                                        comments = Nil,
                                        labels = Vector(StringLiteralV2(
                                            language = None,
                                            value = "Bau"
                                        )),
                                        name = Some("3"),
                                        id = "http://rdfh.ch/lists/00FF/7c221b3ddd"
                                    )
                                ),
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Bob Run"
                                )),
                                name = Some("1"),
                                id = "http://rdfh.ch/lists/00FF/b1872191dc"
                            ),
                            ListNodeADM(
                                position = Some(1),
                                children = Vector(
                                    ListNodeADM(
                                        position = Some(0),
                                        children = Nil,
                                        comments = Nil,
                                        labels = Vector(StringLiteralV2(
                                            language = None,
                                            value = "Personen"
                                        )),
                                        name = Some("1"),
                                        id = "http://rdfh.ch/lists/00FF/ae89c1afdd"
                                    ),
                                    ListNodeADM(
                                        position = Some(1),
                                        children = Nil,
                                        comments = Nil,
                                        labels = Vector(StringLiteralV2(
                                            language = None,
                                            value = "Bau"
                                        )),
                                        name = Some("2"),
                                        id = "http://rdfh.ch/lists/00FF/47bd14e9dd"
                                    )
                                ),
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Cresta Run"
                                )),
                                name = Some("2"),
                                id = "http://rdfh.ch/lists/00FF/15566e76dd"
                            ),
                            ListNodeADM(
                                position = Some(2),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Rodeln"
                                )),
                                name = Some("3"),
                                id = "http://rdfh.ch/lists/00FF/42d141fe0501"
                            )
                        ),
                        comments = Nil,
                        labels = Vector(StringLiteralV2(
                            language = None,
                            value = "CRESTA RUN UND BOB"
                        )),
                        name = Some("5"),
                        id = "http://rdfh.ch/lists/00FF/1854ce57dc"
                    ),
                    ListNodeADM(
                        position = Some(10),
                        children = Vector(
                            ListNodeADM(
                                position = Some(0),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Concours Hippique"
                                )),
                                name = Some("1"),
                                id = "http://rdfh.ch/lists/00FF/7924bb5bde"
                            ),
                            ListNodeADM(
                                position = Some(1),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Pferderennen"
                                )),
                                name = Some("2"),
                                id = "http://rdfh.ch/lists/00FF/12580e95de"
                            ),
                            ListNodeADM(
                                position = Some(2),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Polo"
                                )),
                                name = Some("3"),
                                id = "http://rdfh.ch/lists/00FF/ab8b61cede"
                            ),
                            ListNodeADM(
                                position = Some(3),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Reiten"
                                )),
                                name = Some("4"),
                                id = "http://rdfh.ch/lists/00FF/44bfb407df"
                            ),
                            ListNodeADM(
                                position = Some(4),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Reithalle"
                                )),
                                name = Some("5"),
                                id = "http://rdfh.ch/lists/00FF/ddf20741df"
                            ),
                            ListNodeADM(
                                position = Some(5),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Skikj\u00F6ring"
                                )),
                                name = Some("6"),
                                id = "http://rdfh.ch/lists/00FF/76265b7adf"
                            ),
                            ListNodeADM(
                                position = Some(6),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Fahrturnier"
                                )),
                                name = Some("7"),
                                id = "http://rdfh.ch/lists/00FF/0f5aaeb3df"
                            ),
                            ListNodeADM(
                                position = Some(7),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Zuschauer"
                                )),
                                name = Some("8"),
                                id = "http://rdfh.ch/lists/00FF/a88d01eddf"
                            )
                        ),
                        comments = Nil,
                        labels = Vector(StringLiteralV2(
                            language = None,
                            value = "PFERDESPORT"
                        )),
                        name = Some("6"),
                        id = "http://rdfh.ch/lists/00FF/e0f06722de"
                    ),
                    ListNodeADM(
                        position = Some(11),
                        children = Vector(
                            ListNodeADM(
                                position = Some(0),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Billiard"
                                )),
                                name = Some("1"),
                                id = "http://rdfh.ch/lists/00FF/daf4a75fe0"
                            ),
                            ListNodeADM(
                                position = Some(1),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Fussball"
                                )),
                                name = Some("2"),
                                id = "http://rdfh.ch/lists/00FF/7328fb98e0"
                            ),
                            ListNodeADM(
                                position = Some(2),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Kegeln"
                                )),
                                name = Some("3"),
                                id = "http://rdfh.ch/lists/00FF/0c5c4ed2e0"
                            ),
                            ListNodeADM(
                                position = Some(3),
                                children = Vector(
                                    ListNodeADM(
                                        position = Some(0),
                                        children = Nil,
                                        comments = Nil,
                                        labels = Vector(StringLiteralV2(
                                            language = None,
                                            value = "Minigolf"
                                        )),
                                        name = Some("1"),
                                        id = "http://rdfh.ch/lists/00FF/3ec3f444e1"
                                    ),
                                    ListNodeADM(
                                        position = Some(1),
                                        children = Nil,
                                        comments = Nil,
                                        labels = Vector(StringLiteralV2(
                                            language = None,
                                            value = "Sommergolf"
                                        )),
                                        name = Some("2"),
                                        id = "http://rdfh.ch/lists/00FF/d7f6477ee1"
                                    ),
                                    ListNodeADM(
                                        position = Some(2),
                                        children = Nil,
                                        comments = Nil,
                                        labels = Vector(StringLiteralV2(
                                            language = None,
                                            value = "Wintergolf"
                                        )),
                                        name = Some("3"),
                                        id = "http://rdfh.ch/lists/00FF/702a9bb7e1"
                                    )
                                ),
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Golf"
                                )),
                                name = Some("4"),
                                id = "http://rdfh.ch/lists/00FF/a58fa10be1"
                            ),
                            ListNodeADM(
                                position = Some(4),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Tennis"
                                )),
                                name = Some("5"),
                                id = "http://rdfh.ch/lists/00FF/095eeef0e1"
                            ),
                            ListNodeADM(
                                position = Some(5),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Volleyball"
                                )),
                                name = Some("6"),
                                id = "http://rdfh.ch/lists/00FF/a291412ae2"
                            )
                        ),
                        comments = Nil,
                        labels = Vector(StringLiteralV2(
                            language = None,
                            value = "BALLSPORT"
                        )),
                        name = Some("7"),
                        id = "http://rdfh.ch/lists/00FF/41c15426e0"
                    ),
                    ListNodeADM(
                        position = Some(12),
                        children = Vector(
                            ListNodeADM(
                                position = Some(0),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Alpinismus"
                                )),
                                name = Some("1"),
                                id = "http://rdfh.ch/lists/00FF/d4f8e79ce2"
                            ),
                            ListNodeADM(
                                position = Some(1),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Bergh\u00FCtten und Restaurants"
                                )),
                                name = Some("2"),
                                id = "http://rdfh.ch/lists/00FF/6d2c3bd6e2"
                            ),
                            ListNodeADM(
                                position = Some(2),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Trecking mit Tieren"
                                )),
                                name = Some("3"),
                                id = "http://rdfh.ch/lists/00FF/06608e0fe3"
                            ),
                            ListNodeADM(
                                position = Some(3),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Wandern"
                                )),
                                name = Some("4"),
                                id = "http://rdfh.ch/lists/00FF/9f93e148e3"
                            ),
                            ListNodeADM(
                                position = Some(4),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Spazieren"
                                )),
                                name = Some("5"),
                                id = "http://rdfh.ch/lists/00FF/38c73482e3"
                            )
                        ),
                        comments = Nil,
                        labels = Vector(StringLiteralV2(
                            language = None,
                            value = "ALPINISMUS"
                        )),
                        name = Some("8"),
                        id = "http://rdfh.ch/lists/00FF/3bc59463e2"
                    ),
                    ListNodeADM(
                        position = Some(13),
                        children = Vector(
                            ListNodeADM(
                                position = Some(0),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Ballon"
                                )),
                                name = Some("1"),
                                id = "http://rdfh.ch/lists/00FF/6a2edbf4e3"
                            ),
                            ListNodeADM(
                                position = Some(1),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Delta"
                                )),
                                name = Some("2"),
                                id = "http://rdfh.ch/lists/00FF/03622e2ee4"
                            ),
                            ListNodeADM(
                                position = Some(2),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Flugzeuge"
                                )),
                                name = Some("3"),
                                id = "http://rdfh.ch/lists/00FF/9c958167e4"
                            ),
                            ListNodeADM(
                                position = Some(3),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Helikopter"
                                )),
                                name = Some("4"),
                                id = "http://rdfh.ch/lists/00FF/35c9d4a0e4"
                            ),
                            ListNodeADM(
                                position = Some(4),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Segelflieger"
                                )),
                                name = Some("5"),
                                id = "http://rdfh.ch/lists/00FF/cefc27dae4"
                            )
                        ),
                        comments = Nil,
                        labels = Vector(StringLiteralV2(
                            language = None,
                            value = "FLIEGEN"
                        )),
                        name = Some("9"),
                        id = "http://rdfh.ch/lists/00FF/d1fa87bbe3"
                    ),
                    ListNodeADM(
                        position = Some(14),
                        children = Vector(
                            ListNodeADM(
                                position = Some(0),
                                children = Vector(
                                    ListNodeADM(
                                        position = Some(0),
                                        children = Nil,
                                        comments = Nil,
                                        labels = Vector(StringLiteralV2(
                                            language = None,
                                            value = "Malojarennen"
                                        )),
                                        name = Some("1"),
                                        id = "http://rdfh.ch/lists/00FF/99972186e5"
                                    ),
                                    ListNodeADM(
                                        position = Some(1),
                                        children = Nil,
                                        comments = Nil,
                                        labels = Vector(StringLiteralV2(
                                            language = None,
                                            value = "Berninarennen"
                                        )),
                                        name = Some("2"),
                                        id = "http://rdfh.ch/lists/00FF/32cb74bfe5"
                                    ),
                                    ListNodeADM(
                                        position = Some(2),
                                        children = Nil,
                                        comments = Nil,
                                        labels = Vector(StringLiteralV2(
                                            language = None,
                                            value = "Shellstrasse"
                                        )),
                                        name = Some("3"),
                                        id = "http://rdfh.ch/lists/00FF/cbfec7f8e5"
                                    ),
                                    ListNodeADM(
                                        position = Some(3),
                                        children = Nil,
                                        comments = Nil,
                                        labels = Vector(StringLiteralV2(
                                            language = None,
                                            value = "Personen"
                                        )),
                                        name = Some("4"),
                                        id = "http://rdfh.ch/lists/00FF/64321b32e6"
                                    ),
                                    ListNodeADM(
                                        position = Some(4),
                                        children = Nil,
                                        comments = Nil,
                                        labels = Vector(StringLiteralV2(
                                            language = None,
                                            value = "Verschiedenes"
                                        )),
                                        name = Some("5"),
                                        id = "http://rdfh.ch/lists/00FF/fd656e6be6"
                                    )
                                ),
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Autorennen"
                                )),
                                name = Some("1"),
                                id = "http://rdfh.ch/lists/00FF/0064ce4ce5"
                            ),
                            ListNodeADM(
                                position = Some(1),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Geschicklichkeitsfahren"
                                )),
                                name = Some("2"),
                                id = "http://rdfh.ch/lists/00FF/9699c1a4e6"
                            ),
                            ListNodeADM(
                                position = Some(2),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Sch\u00F6nheitskonkurrenz"
                                )),
                                name = Some("3"),
                                id = "http://rdfh.ch/lists/00FF/2fcd14dee6"
                            ),
                            ListNodeADM(
                                position = Some(3),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Inline Skating"
                                )),
                                name = Some("4"),
                                id = "http://rdfh.ch/lists/00FF/c8006817e7"
                            ),
                            ListNodeADM(
                                position = Some(4),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Montainbiking"
                                )),
                                name = Some("5"),
                                id = "http://rdfh.ch/lists/00FF/6134bb50e7"
                            ),
                            ListNodeADM(
                                position = Some(5),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Radfahren"
                                )),
                                name = Some("6"),
                                id = "http://rdfh.ch/lists/00FF/fa670e8ae7"
                            ),
                            ListNodeADM(
                                position = Some(6),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Motorradfahren"
                                )),
                                name = Some("7"),
                                id = "http://rdfh.ch/lists/00FF/939b61c3e7"
                            )
                        ),
                        comments = Nil,
                        labels = Vector(StringLiteralV2(
                            language = None,
                            value = "RADSPORT"
                        )),
                        name = Some("10"),
                        id = "http://rdfh.ch/lists/00FF/67307b13e5"
                    ),
                    ListNodeADM(
                        position = Some(15),
                        children = Vector(
                            ListNodeADM(
                                position = Some(0),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Schwimmen Hallenb\u00E4der"
                                )),
                                name = Some("1"),
                                id = "http://rdfh.ch/lists/00FF/c5020836e8"
                            ),
                            ListNodeADM(
                                position = Some(1),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Schwimmen Seen"
                                )),
                                name = Some("2"),
                                id = "http://rdfh.ch/lists/00FF/5e365b6fe8"
                            ),
                            ListNodeADM(
                                position = Some(2),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Rudern"
                                )),
                                name = Some("3"),
                                id = "http://rdfh.ch/lists/00FF/f769aea8e8"
                            ),
                            ListNodeADM(
                                position = Some(3),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Segeln"
                                )),
                                name = Some("4"),
                                id = "http://rdfh.ch/lists/00FF/909d01e2e8"
                            ),
                            ListNodeADM(
                                position = Some(4),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Windsurfen"
                                )),
                                name = Some("5"),
                                id = "http://rdfh.ch/lists/00FF/29d1541be9"
                            ),
                            ListNodeADM(
                                position = Some(5),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Tauchen"
                                )),
                                name = Some("6"),
                                id = "http://rdfh.ch/lists/00FF/c204a854e9"
                            ),
                            ListNodeADM(
                                position = Some(6),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Rafting"
                                )),
                                name = Some("7"),
                                id = "http://rdfh.ch/lists/00FF/5b38fb8de9"
                            ),
                            ListNodeADM(
                                position = Some(7),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Kitesurfen"
                                )),
                                name = Some("8"),
                                id = "http://rdfh.ch/lists/00FF/f46b4ec7e9"
                            )
                        ),
                        comments = Nil,
                        labels = Vector(StringLiteralV2(
                            language = None,
                            value = "WASSERSPORT"
                        )),
                        name = Some("11"),
                        id = "http://rdfh.ch/lists/00FF/2ccfb4fce7"
                    )
                ),
                comments = Nil,
                labels = Vector(StringLiteralV2(
                    language = None,
                    value = "SPORT"
                )),
                name = Some("9SPO"),
                id = "http://rdfh.ch/lists/00FF/71a1543cce"
            ),
            ListNodeADM(
                position = Some(9),
                children = Vector(
                    ListNodeADM(
                        position = Some(0),
                        children = Vector(
                            ListNodeADM(
                                position = Some(0),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Autos, Busse und Postautos"
                                )),
                                name = Some("1"),
                                id = "http://rdfh.ch/lists/00FF/bf064873ea"
                            ),
                            ListNodeADM(
                                position = Some(1),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Boote"
                                )),
                                name = Some("2"),
                                id = "http://rdfh.ch/lists/00FF/583a9bacea"
                            ),
                            ListNodeADM(
                                position = Some(2),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Flugplatz Samedan"
                                )),
                                name = Some("3"),
                                id = "http://rdfh.ch/lists/00FF/f16deee5ea"
                            ),
                            ListNodeADM(
                                position = Some(3),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Kommunikation"
                                )),
                                name = Some("4"),
                                id = "http://rdfh.ch/lists/00FF/8aa1411feb"
                            ),
                            ListNodeADM(
                                position = Some(4),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Kutschen und Pferdetransporte"
                                )),
                                name = Some("5"),
                                id = "http://rdfh.ch/lists/00FF/23d59458eb"
                            ),
                            ListNodeADM(
                                position = Some(5),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Luftseilbahnen und Stationen"
                                )),
                                name = Some("6"),
                                id = "http://rdfh.ch/lists/00FF/bc08e891eb"
                            ),
                            ListNodeADM(
                                position = Some(6),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Schneer\u00E4umungs- und Pistenfahrzeuge"
                                )),
                                name = Some("7"),
                                id = "http://rdfh.ch/lists/00FF/553c3bcbeb"
                            ),
                            ListNodeADM(
                                position = Some(7),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Schneekanonen"
                                )),
                                name = Some("8"),
                                id = "http://rdfh.ch/lists/00FF/ee6f8e04ec"
                            ),
                            ListNodeADM(
                                position = Some(8),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Skilifte"
                                )),
                                name = Some("9"),
                                id = "http://rdfh.ch/lists/00FF/87a3e13dec"
                            ),
                            ListNodeADM(
                                position = Some(9),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Standseilbahnen und Stationen"
                                )),
                                name = Some("10"),
                                id = "http://rdfh.ch/lists/00FF/20d73477ec"
                            ),
                            ListNodeADM(
                                position = Some(10),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Strassen und P\u00E4sse"
                                )),
                                name = Some("11"),
                                id = "http://rdfh.ch/lists/00FF/b90a88b0ec"
                            ),
                            ListNodeADM(
                                position = Some(11),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Tram"
                                )),
                                name = Some("12"),
                                id = "http://rdfh.ch/lists/00FF/523edbe9ec"
                            ),
                            ListNodeADM(
                                position = Some(12),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Wegweiser"
                                )),
                                name = Some("13"),
                                id = "http://rdfh.ch/lists/00FF/eb712e23ed"
                            )
                        ),
                        comments = Nil,
                        labels = Vector(StringLiteralV2(
                            language = None,
                            value = "VERKEHR"
                        )),
                        name = Some("1"),
                        id = "http://rdfh.ch/lists/00FF/26d3f439ea"
                    ),
                    ListNodeADM(
                        position = Some(1),
                        children = Vector(ListNodeADM(
                            position = Some(0),
                            children = Nil,
                            comments = Nil,
                            labels = Vector(StringLiteralV2(
                                language = None,
                                value = "Eisenbahnen und Bahnh\u00F6fe"
                            )),
                            name = Some("1"),
                            id = "http://rdfh.ch/lists/00FF/1dd9d495ed"
                        )),
                        comments = Nil,
                        labels = Vector(StringLiteralV2(
                            language = None,
                            value = "EISENBAHNEN"
                        )),
                        name = Some("1-1"),
                        id = "http://rdfh.ch/lists/00FF/84a5815ced"
                    ),
                    ListNodeADM(
                        position = Some(2),
                        children = Vector(
                            ListNodeADM(
                                position = Some(0),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Casino"
                                )),
                                name = Some("1"),
                                id = "http://rdfh.ch/lists/00FF/4f407b08ee"
                            ),
                            ListNodeADM(
                                position = Some(1),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "G\u00E4ste"
                                )),
                                name = Some("2"),
                                id = "http://rdfh.ch/lists/00FF/e873ce41ee"
                            ),
                            ListNodeADM(
                                position = Some(2),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Mode"
                                )),
                                name = Some("3"),
                                id = "http://rdfh.ch/lists/00FF/81a7217bee"
                            )
                        ),
                        comments = Nil,
                        labels = Vector(StringLiteralV2(
                            language = None,
                            value = "FREMDENVERKEHR"
                        )),
                        name = Some("2"),
                        id = "http://rdfh.ch/lists/00FF/b60c28cfed"
                    ),
                    ListNodeADM(
                        position = Some(3),
                        children = Vector(
                            ListNodeADM(
                                position = Some(0),
                                children = Vector(
                                    ListNodeADM(
                                        position = Some(0),
                                        children = Nil,
                                        comments = Nil,
                                        labels = Vector(StringLiteralV2(
                                            language = None,
                                            value = "Hotel A"
                                        )),
                                        name = Some("hotel_a"),
                                        id = "http://rdfh.ch/lists/00FF/97744976b801"
                                    ),
                                    ListNodeADM(
                                        position = Some(1),
                                        children = Nil,
                                        comments = Nil,
                                        labels = Vector(StringLiteralV2(
                                            language = None,
                                            value = "Hotel B"
                                        )),
                                        name = Some("hotel_b"),
                                        id = "http://rdfh.ch/lists/00FF/30a89cafb801"
                                    ),
                                    ListNodeADM(
                                        position = Some(2),
                                        children = Nil,
                                        comments = Nil,
                                        labels = Vector(StringLiteralV2(
                                            language = None,
                                            value = "Hotel C"
                                        )),
                                        name = Some("hotel_c"),
                                        id = "http://rdfh.ch/lists/00FF/c9dbefe8b801"
                                    ),
                                    ListNodeADM(
                                        position = Some(3),
                                        children = Nil,
                                        comments = Nil,
                                        labels = Vector(StringLiteralV2(
                                            language = None,
                                            value = "Hotel D"
                                        )),
                                        name = Some("hotel_d"),
                                        id = "http://rdfh.ch/lists/00FF/620f4322b901"
                                    ),
                                    ListNodeADM(
                                        position = Some(4),
                                        children = Nil,
                                        comments = Nil,
                                        labels = Vector(StringLiteralV2(
                                            language = None,
                                            value = "Hotel E"
                                        )),
                                        name = Some("hotel_e"),
                                        id = "http://rdfh.ch/lists/00FF/fb42965bb901"
                                    ),
                                    ListNodeADM(
                                        position = Some(5),
                                        children = Nil,
                                        comments = Nil,
                                        labels = Vector(StringLiteralV2(
                                            language = None,
                                            value = "Hotel F"
                                        )),
                                        name = Some("hotel_f"),
                                        id = "http://rdfh.ch/lists/00FF/9476e994b901"
                                    ),
                                    ListNodeADM(
                                        position = Some(6),
                                        children = Nil,
                                        comments = Nil,
                                        labels = Vector(StringLiteralV2(
                                            language = None,
                                            value = "Hotel G"
                                        )),
                                        name = Some("hotel_g"),
                                        id = "http://rdfh.ch/lists/00FF/2daa3cceb901"
                                    ),
                                    ListNodeADM(
                                        position = Some(7),
                                        children = Nil,
                                        comments = Nil,
                                        labels = Vector(StringLiteralV2(
                                            language = None,
                                            value = "Hotel H"
                                        )),
                                        name = Some("hotel_h"),
                                        id = "http://rdfh.ch/lists/00FF/c6dd8f07ba01"
                                    ),
                                    ListNodeADM(
                                        position = Some(8),
                                        children = Nil,
                                        comments = Nil,
                                        labels = Vector(StringLiteralV2(
                                            language = None,
                                            value = "Hotel I"
                                        )),
                                        name = Some("hotel_i"),
                                        id = "http://rdfh.ch/lists/00FF/5f11e340ba01"
                                    ),
                                    ListNodeADM(
                                        position = Some(9),
                                        children = Nil,
                                        comments = Nil,
                                        labels = Vector(StringLiteralV2(
                                            language = None,
                                            value = "Hotel J"
                                        )),
                                        name = Some("hotel_j"),
                                        id = "http://rdfh.ch/lists/00FF/f844367aba01"
                                    ),
                                    ListNodeADM(
                                        position = Some(10),
                                        children = Nil,
                                        comments = Nil,
                                        labels = Vector(StringLiteralV2(
                                            language = None,
                                            value = "Hotel K"
                                        )),
                                        name = Some("hotel_k"),
                                        id = "http://rdfh.ch/lists/00FF/917889b3ba01"
                                    ),
                                    ListNodeADM(
                                        position = Some(11),
                                        children = Nil,
                                        comments = Nil,
                                        labels = Vector(StringLiteralV2(
                                            language = None,
                                            value = "Hotel L"
                                        )),
                                        name = Some("hotel_l"),
                                        id = "http://rdfh.ch/lists/00FF/2aacdcecba01"
                                    ),
                                    ListNodeADM(
                                        position = Some(12),
                                        children = Nil,
                                        comments = Nil,
                                        labels = Vector(StringLiteralV2(
                                            language = None,
                                            value = "Hotel M"
                                        )),
                                        name = Some("hotel_m"),
                                        id = "http://rdfh.ch/lists/00FF/c3df2f26bb01"
                                    ),
                                    ListNodeADM(
                                        position = Some(13),
                                        children = Nil,
                                        comments = Nil,
                                        labels = Vector(StringLiteralV2(
                                            language = None,
                                            value = "Hotel N"
                                        )),
                                        name = Some("hotel_n"),
                                        id = "http://rdfh.ch/lists/00FF/5c13835fbb01"
                                    ),
                                    ListNodeADM(
                                        position = Some(14),
                                        children = Nil,
                                        comments = Nil,
                                        labels = Vector(StringLiteralV2(
                                            language = None,
                                            value = "Hotel O"
                                        )),
                                        name = Some("hotel_o"),
                                        id = "http://rdfh.ch/lists/00FF/f546d698bb01"
                                    ),
                                    ListNodeADM(
                                        position = Some(15),
                                        children = Nil,
                                        comments = Nil,
                                        labels = Vector(StringLiteralV2(
                                            language = None,
                                            value = "Hotel P"
                                        )),
                                        name = Some("hotel_p"),
                                        id = "http://rdfh.ch/lists/00FF/8e7a29d2bb01"
                                    ),
                                    ListNodeADM(
                                        position = Some(16),
                                        children = Nil,
                                        comments = Nil,
                                        labels = Vector(StringLiteralV2(
                                            language = None,
                                            value = "Hotel Q"
                                        )),
                                        name = Some("hotel_q"),
                                        id = "http://rdfh.ch/lists/00FF/27ae7c0bbc01"
                                    ),
                                    ListNodeADM(
                                        position = Some(17),
                                        children = Nil,
                                        comments = Nil,
                                        labels = Vector(StringLiteralV2(
                                            language = None,
                                            value = "Hotel R"
                                        )),
                                        name = Some("hotel_r"),
                                        id = "http://rdfh.ch/lists/00FF/c0e1cf44bc01"
                                    ),
                                    ListNodeADM(
                                        position = Some(18),
                                        children = Nil,
                                        comments = Nil,
                                        labels = Vector(StringLiteralV2(
                                            language = None,
                                            value = "Hotel S"
                                        )),
                                        name = Some("hotel_s"),
                                        id = "http://rdfh.ch/lists/00FF/5915237ebc01"
                                    ),
                                    ListNodeADM(
                                        position = Some(19),
                                        children = Nil,
                                        comments = Nil,
                                        labels = Vector(StringLiteralV2(
                                            language = None,
                                            value = "Hotel T"
                                        )),
                                        name = Some("hotel_t"),
                                        id = "http://rdfh.ch/lists/00FF/f24876b7bc01"
                                    ),
                                    ListNodeADM(
                                        position = Some(20),
                                        children = Nil,
                                        comments = Nil,
                                        labels = Vector(StringLiteralV2(
                                            language = None,
                                            value = "Hotel U"
                                        )),
                                        name = Some("hotel_u"),
                                        id = "http://rdfh.ch/lists/00FF/8b7cc9f0bc01"
                                    ),
                                    ListNodeADM(
                                        position = Some(21),
                                        children = Nil,
                                        comments = Nil,
                                        labels = Vector(StringLiteralV2(
                                            language = None,
                                            value = "Hotel V"
                                        )),
                                        name = Some("hotel_v"),
                                        id = "http://rdfh.ch/lists/00FF/24b01c2abd01"
                                    ),
                                    ListNodeADM(
                                        position = Some(22),
                                        children = Nil,
                                        comments = Nil,
                                        labels = Vector(StringLiteralV2(
                                            language = None,
                                            value = "Hotel W"
                                        )),
                                        name = Some("hotel_w"),
                                        id = "http://rdfh.ch/lists/00FF/9f29173c3b02"
                                    ),
                                    ListNodeADM(
                                        position = Some(23),
                                        children = Nil,
                                        comments = Nil,
                                        labels = Vector(StringLiteralV2(
                                            language = None,
                                            value = "Hotel X"
                                        )),
                                        name = Some("hotel_x"),
                                        id = "http://rdfh.ch/lists/00FF/bde36f63bd01"
                                    ),
                                    ListNodeADM(
                                        position = Some(24),
                                        children = Nil,
                                        comments = Nil,
                                        labels = Vector(StringLiteralV2(
                                            language = None,
                                            value = "Hotel Y"
                                        )),
                                        name = Some("hotel_y"),
                                        id = "http://rdfh.ch/lists/00FF/5617c39cbd01"
                                    ),
                                    ListNodeADM(
                                        position = Some(25),
                                        children = Nil,
                                        comments = Nil,
                                        labels = Vector(StringLiteralV2(
                                            language = None,
                                            value = "Hotel Z"
                                        )),
                                        name = Some("hotel_z"),
                                        id = "http://rdfh.ch/lists/00FF/ef4a16d6bd01"
                                    )
                                ),
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Hotels und Restaurants A-Z"
                                )),
                                name = Some("1"),
                                id = "http://rdfh.ch/lists/00FF/b30ec8edee"
                            ),
                            ListNodeADM(
                                position = Some(1),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Essen"
                                )),
                                name = Some("2"),
                                id = "http://rdfh.ch/lists/00FF/4c421b27ef"
                            ),
                            ListNodeADM(
                                position = Some(2),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Men\u00FCkarten"
                                )),
                                name = Some("3"),
                                id = "http://rdfh.ch/lists/00FF/e5756e60ef"
                            )
                        ),
                        comments = Nil,
                        labels = Vector(StringLiteralV2(
                            language = None,
                            value = "HOTELLERIE"
                        )),
                        name = Some("3"),
                        id = "http://rdfh.ch/lists/00FF/1adb74b4ee"
                    ),
                    ListNodeADM(
                        position = Some(4),
                        children = Vector(
                            ListNodeADM(
                                position = Some(0),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Personal und B\u00FCro"
                                )),
                                name = Some("1"),
                                id = "http://rdfh.ch/lists/00FF/17dd14d3ef"
                            ),
                            ListNodeADM(
                                position = Some(1),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Anl\u00E4sse und Reisen"
                                )),
                                name = Some("2"),
                                id = "http://rdfh.ch/lists/00FF/b010680cf0"
                            ),
                            ListNodeADM(
                                position = Some(2),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Markenzeichen St. Moritz"
                                )),
                                name = Some("3"),
                                id = "http://rdfh.ch/lists/00FF/4944bb45f0"
                            )
                        ),
                        comments = Nil,
                        labels = Vector(StringLiteralV2(
                            language = None,
                            value = "KURVEREIN"
                        )),
                        name = Some("4"),
                        id = "http://rdfh.ch/lists/00FF/7ea9c199ef"
                    ),
                    ListNodeADM(
                        position = Some(5),
                        children = Vector(
                            ListNodeADM(
                                position = Some(0),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Arbeitswelt"
                                )),
                                name = Some("1"),
                                id = "http://rdfh.ch/lists/00FF/7bab61b8f0"
                            ),
                            ListNodeADM(
                                position = Some(1),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Reklame"
                                )),
                                name = Some("2"),
                                id = "http://rdfh.ch/lists/00FF/14dfb4f1f0"
                            ),
                            ListNodeADM(
                                position = Some(2),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Bauwesen"
                                )),
                                name = Some("3"),
                                id = "http://rdfh.ch/lists/00FF/ad12082bf1"
                            )
                        ),
                        comments = Nil,
                        labels = Vector(StringLiteralV2(
                            language = None,
                            value = "GEWERBE"
                        )),
                        name = Some("6"),
                        id = "http://rdfh.ch/lists/00FF/e2770e7ff0"
                    ),
                    ListNodeADM(
                        position = Some(6),
                        children = Vector(
                            ListNodeADM(
                                position = Some(0),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Elektrizit\u00E4t"
                                )),
                                name = Some("1"),
                                id = "http://rdfh.ch/lists/00FF/df79ae9df1"
                            ),
                            ListNodeADM(
                                position = Some(1),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Wasserkraft"
                                )),
                                name = Some("2"),
                                id = "http://rdfh.ch/lists/00FF/78ad01d7f1"
                            ),
                            ListNodeADM(
                                position = Some(2),
                                children = Nil,
                                comments = Nil,
                                labels = Vector(StringLiteralV2(
                                    language = None,
                                    value = "Solarenergie"
                                )),
                                name = Some("3"),
                                id = "http://rdfh.ch/lists/00FF/11e15410f2"
                            )
                        ),
                        comments = Nil,
                        labels = Vector(StringLiteralV2(
                            language = None,
                            value = "ENERGIEWIRTSCHAFT"
                        )),
                        name = Some("7"),
                        id = "http://rdfh.ch/lists/00FF/46465b64f1"
                    ),
                    ListNodeADM(
                        position = Some(7),
                        children = Nil,
                        comments = Nil,
                        labels = Vector(StringLiteralV2(
                            language = None,
                            value = "AGRARWIRTSCHAFT"
                        )),
                        name = Some("8"),
                        id = "http://rdfh.ch/lists/00FF/aa14a849f2"
                    ),
                    ListNodeADM(
                        position = Some(8),
                        children = Nil,
                        comments = Nil,
                        labels = Vector(StringLiteralV2(
                            language = None,
                            value = "WALDWIRTSCHAFT"
                        )),
                        name = Some("9"),
                        id = "http://rdfh.ch/lists/00FF/4348fb82f2"
                    )
                ),
                comments = Nil,
                labels = Vector(StringLiteralV2(
                    language = None,
                    value = "WIRTSCHAFT"
                )),
                name = Some("10WIR"),
                id = "http://rdfh.ch/lists/00FF/8d9fa100ea"
            )
        )



}
