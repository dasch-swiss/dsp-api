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
import org.knora.webapi.messages.store.triplestoremessages.{StringLiteralSequenceV2, StringLiteralV2}

object SharedListsTestDataADM {


    val otherTreeListInfo: ListInfoADM = ListInfoADM(
        id = "http://rdfh.ch/lists/0001/otherTreeList",
        projectIri = "http://rdfh.ch/projects/0001",
        labels = StringLiteralSequenceV2(Vector(StringLiteralV2("Tree list root", Some("en")))),
        comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
    )

    val bigListInfo: ListInfoADM = ListInfoADM(
        id = "http://rdfh.ch/lists/00FF/73d0ec0302",
        projectIri = "http://rdfh.ch/projects/00FF",
        labels = StringLiteralSequenceV2(Vector(StringLiteralV2("Title", Some("en")), StringLiteralV2("Titel", Some("de")), StringLiteralV2("Titre", Some("fr")))),
        comments = StringLiteralSequenceV2(Vector(StringLiteralV2("Hierarchisches Stichwortverzeichnis / Signatur der Bilder", Some("de"))))
    )

    val summerNodeInfo: ListNodeInfoADM = ListNodeInfoADM(
        id = "http://rdfh.ch/lists/00FF/526f26ed04",
        name = Some("sommer"),
        labels = StringLiteralSequenceV2(Vector(StringLiteralV2("Sommer"))),
        comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2]),
        position = Some(0)
    )

    val seasonListNodes: Seq[ListNodeADM] = Seq(
        ListNodeADM(
            id = "http://rdfh.ch/lists/00FF/526f26ed04",
            name = Some("sommer"),
            labels = StringLiteralSequenceV2(Vector(StringLiteralV2("Sommer"))),
            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2]),
            children = Vector.empty[ListNodeADM],
            position = Some(0)
        ),
        ListNodeADM(
            id = "http://rdfh.ch/lists/00FF/eda2792605",
            name = Some("winter"),
            labels = StringLiteralSequenceV2(Vector(StringLiteralV2("Winter"))),
            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2]),
            children = Seq.empty[ListNodeADM],
            position = Some(1)
        )
    )

    val nodePath: Seq[ListNodeADM] = Seq(
        ListNodeADM(
            id = "http://rdfh.ch/lists/00FF/691eee1cbe",
            name = Some("4KUN"),
            labels = StringLiteralSequenceV2(Vector(StringLiteralV2("KUNST"))),
            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2]),
            children = Seq.empty[ListNodeADM],
            position = None

        ),
        ListNodeADM(
            id = "http://rdfh.ch/lists/00FF/2ebd2706c1",
            name = Some("7"),
            labels = StringLiteralSequenceV2(Vector(StringLiteralV2("FILM UND FOTO"))),
            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2]),
            children = Seq.empty[ListNodeADM],
            position = None
        ),
        ListNodeADM(
            id = "http://rdfh.ch/lists/00FF/c7f07a3fc1",
            name = Some("1"),
            labels = StringLiteralSequenceV2(Vector(StringLiteralV2("Heidi Film"))),
            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2]),
            children = Seq.empty[ListNodeADM],
            position = None
        )
    )

    val bigListNodes = Vector(
        ListNodeADM(
            children = Vector(
                ListNodeADM(
                    children = Nil,
                    name = Some("1"),
                    id = "http://rdfh.ch/lists/00FF/412821d3a6",
                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                        value = "BIBLIOTHEKEN ST. MORITZ",
                        language = None
                    ))),
                    position = Some(0),
                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                ),
                ListNodeADM(
                    children = Nil,
                    name = Some("6"),
                    id = "http://rdfh.ch/lists/00FF/da5b740ca7",
                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                        value = "SAMMELB\u00C4NDE, FOTOALBEN",
                        language = None
                    ))),
                    position = Some(1),
                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                )
            ),
            name = Some("1ALL"),
            id = "http://rdfh.ch/lists/00FF/a8f4cd99a6",
            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                value = "ALLGEMEINES",
                language = None
            ))),
            position = Some(0),
            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
        ),
        ListNodeADM(
            children = Vector(
                ListNodeADM(
                    children = Vector(ListNodeADM(
                        children = Nil,
                        name = Some("1"),
                        id = "http://rdfh.ch/lists/00FF/a5f66db8a7",
                        labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                            value = "Personen",
                            language = None
                        ))),
                        position = Some(0),
                        comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                    )),
                    name = Some("1"),
                    id = "http://rdfh.ch/lists/00FF/0cc31a7fa7",
                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                        value = "SCHWEIZ",
                        language = None
                    ))),
                    position = Some(0),
                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                ),
                ListNodeADM(
                    children = Vector(ListNodeADM(
                        children = Nil,
                        name = Some("1"),
                        id = "http://rdfh.ch/lists/00FF/d75d142ba8",
                        labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                            value = "Personen",
                            language = None
                        ))),
                        position = Some(0),
                        comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                    )),
                    name = Some("2"),
                    id = "http://rdfh.ch/lists/00FF/3e2ac1f1a7",
                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                        value = "GRAUB\u00DCNDEN",
                        language = None
                    ))),
                    position = Some(1),
                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                ),
                ListNodeADM(
                    children = Vector(
                        ListNodeADM(
                            children = Nil,
                            name = Some("6"),
                            id = "http://rdfh.ch/lists/00FF/06c75abca9",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Landschaft Seen",
                                language = None
                            ))),
                            position = Some(5),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("1"),
                            id = "http://rdfh.ch/lists/00FF/09c5ba9da8",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Flugaufnahmen",
                                language = None
                            ))),
                            position = Some(0),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Vector(
                                ListNodeADM(
                                    children = Nil,
                                    name = Some("8"),
                                    id = "http://rdfh.ch/lists/00FF/00cb9af9ab",
                                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                        value = "Samedan",
                                        language = None
                                    ))),
                                    position = Some(7),
                                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                                ),
                                ListNodeADM(
                                    children = Nil,
                                    name = Some("3"),
                                    id = "http://rdfh.ch/lists/00FF/03c9fadaaa",
                                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                        value = "Silvaplana",
                                        language = None
                                    ))),
                                    position = Some(2),
                                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                                ),
                                ListNodeADM(
                                    children = Nil,
                                    name = Some("15"),
                                    id = "http://rdfh.ch/lists/00FF/2f34e18aad",
                                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                        value = "Cinous-chel",
                                        language = None
                                    ))),
                                    position = Some(14),
                                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                                ),
                                ListNodeADM(
                                    children = Nil,
                                    name = Some("10"),
                                    id = "http://rdfh.ch/lists/00FF/3232416cac",
                                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                        value = "La Punt",
                                        language = None
                                    ))),
                                    position = Some(9),
                                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                                ),
                                ListNodeADM(
                                    children = Nil,
                                    name = Some("5"),
                                    id = "http://rdfh.ch/lists/00FF/3530a14dab",
                                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                        value = "Champf\u00E8r",
                                        language = None
                                    ))),
                                    position = Some(4),
                                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                                ),
                                ListNodeADM(
                                    children = Nil,
                                    name = Some("17"),
                                    id = "http://rdfh.ch/lists/00FF/619b87fdad",
                                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                        value = "Unterengadin",
                                        language = None
                                    ))),
                                    position = Some(16),
                                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                                ),
                                ListNodeADM(
                                    children = Nil,
                                    name = Some("12"),
                                    id = "http://rdfh.ch/lists/00FF/6499e7deac",
                                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                        value = "Madulain",
                                        language = None
                                    ))),
                                    position = Some(11),
                                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                                ),
                                ListNodeADM(
                                    children = Nil,
                                    name = Some("7"),
                                    id = "http://rdfh.ch/lists/00FF/679747c0ab",
                                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                        value = "Celerina",
                                        language = None
                                    ))),
                                    position = Some(6),
                                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                                ),
                                ListNodeADM(
                                    children = Nil,
                                    name = Some("2"),
                                    id = "http://rdfh.ch/lists/00FF/6a95a7a1aa",
                                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                        value = "Sils",
                                        language = None
                                    ))),
                                    position = Some(1),
                                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                                ),
                                ListNodeADM(
                                    children = Nil,
                                    name = Some("14"),
                                    id = "http://rdfh.ch/lists/00FF/96008e51ad",
                                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                        value = "S-chanf",
                                        language = None
                                    ))),
                                    position = Some(13),
                                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                                ),
                                ListNodeADM(
                                    children = Nil,
                                    name = Some("9"),
                                    id = "http://rdfh.ch/lists/00FF/99feed32ac",
                                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                        value = "Bever",
                                        language = None
                                    ))),
                                    position = Some(8),
                                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                                ),
                                ListNodeADM(
                                    children = Nil,
                                    name = Some("4"),
                                    id = "http://rdfh.ch/lists/00FF/9cfc4d14ab",
                                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                        value = "Surlej",
                                        language = None
                                    ))),
                                    position = Some(3),
                                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                                ),
                                ListNodeADM(
                                    children = Nil,
                                    name = Some("16"),
                                    id = "http://rdfh.ch/lists/00FF/c86734c4ad",
                                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                        value = "Fex",
                                        language = None
                                    ))),
                                    position = Some(15),
                                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                                ),
                                ListNodeADM(
                                    children = Nil,
                                    name = Some("11"),
                                    id = "http://rdfh.ch/lists/00FF/cb6594a5ac",
                                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                        value = "Chamues-ch",
                                        language = None
                                    ))),
                                    position = Some(10),
                                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                                ),
                                ListNodeADM(
                                    children = Nil,
                                    name = Some("6"),
                                    id = "http://rdfh.ch/lists/00FF/ce63f486ab",
                                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                        value = "Pontresina",
                                        language = None
                                    ))),
                                    position = Some(5),
                                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                                ),
                                ListNodeADM(
                                    children = Nil,
                                    name = Some("1"),
                                    id = "http://rdfh.ch/lists/00FF/d1615468aa",
                                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                        value = "Maloja",
                                        language = None
                                    ))),
                                    position = Some(0),
                                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                                ),
                                ListNodeADM(
                                    children = Nil,
                                    name = Some("18"),
                                    id = "http://rdfh.ch/lists/00FF/faceda36ae",
                                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                        value = "Personen",
                                        language = None
                                    ))),
                                    position = Some(17),
                                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                                ),
                                ListNodeADM(
                                    children = Nil,
                                    name = Some("13"),
                                    id = "http://rdfh.ch/lists/00FF/fdcc3a18ad",
                                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                        value = "Zuoz",
                                        language = None
                                    ))),
                                    position = Some(12),
                                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                                )
                            ),
                            name = Some("8"),
                            id = "http://rdfh.ch/lists/00FF/382e012faa",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Ortschaften Sommer",
                                language = None
                            ))),
                            position = Some(7),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("3"),
                            id = "http://rdfh.ch/lists/00FF/3b2c6110a9",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Landschaft Sommer mit Ortschaften",
                                language = None
                            ))),
                            position = Some(2),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("5"),
                            id = "http://rdfh.ch/lists/00FF/6d930783a9",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Landschaft Winter mit Ortschaften",
                                language = None
                            ))),
                            position = Some(4),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Vector(
                                ListNodeADM(
                                    children = Nil,
                                    name = Some("16"),
                                    id = "http://rdfh.ch/lists/00FF/233c6105b2",
                                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                        value = "Fex",
                                        language = None
                                    ))),
                                    position = Some(15),
                                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                                ),
                                ListNodeADM(
                                    children = Nil,
                                    name = Some("11"),
                                    id = "http://rdfh.ch/lists/00FF/263ac1e6b0",
                                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                        value = "Chamues-ch",
                                        language = None
                                    ))),
                                    position = Some(10),
                                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                                ),
                                ListNodeADM(
                                    children = Nil,
                                    name = Some("6"),
                                    id = "http://rdfh.ch/lists/00FF/293821c8af",
                                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                        value = "Pontresina",
                                        language = None
                                    ))),
                                    position = Some(5),
                                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                                ),
                                ListNodeADM(
                                    children = Nil,
                                    name = Some("1"),
                                    id = "http://rdfh.ch/lists/00FF/2c3681a9ae",
                                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                        value = "Maloja",
                                        language = None
                                    ))),
                                    position = Some(0),
                                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                                ),
                                ListNodeADM(
                                    children = Nil,
                                    name = Some("18"),
                                    id = "http://rdfh.ch/lists/00FF/55a30778b2",
                                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                        value = "Personen",
                                        language = None
                                    ))),
                                    position = Some(17),
                                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                                ),
                                ListNodeADM(
                                    children = Nil,
                                    name = Some("13"),
                                    id = "http://rdfh.ch/lists/00FF/58a16759b1",
                                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                        value = "Zuoz",
                                        language = None
                                    ))),
                                    position = Some(12),
                                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                                ),
                                ListNodeADM(
                                    children = Nil,
                                    name = Some("8"),
                                    id = "http://rdfh.ch/lists/00FF/5b9fc73ab0",
                                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                        value = "Samedan",
                                        language = None
                                    ))),
                                    position = Some(7),
                                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                                ),
                                ListNodeADM(
                                    children = Nil,
                                    name = Some("3"),
                                    id = "http://rdfh.ch/lists/00FF/5e9d271caf",
                                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                        value = "Silvaplana",
                                        language = None
                                    ))),
                                    position = Some(2),
                                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                                ),
                                ListNodeADM(
                                    children = Nil,
                                    name = Some("15"),
                                    id = "http://rdfh.ch/lists/00FF/8a080eccb1",
                                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                        value = "Cinous-chel",
                                        language = None
                                    ))),
                                    position = Some(14),
                                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                                ),
                                ListNodeADM(
                                    children = Nil,
                                    name = Some("10"),
                                    id = "http://rdfh.ch/lists/00FF/8d066eadb0",
                                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                        value = "La Punt",
                                        language = None
                                    ))),
                                    position = Some(9),
                                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                                ),
                                ListNodeADM(
                                    children = Nil,
                                    name = Some("5"),
                                    id = "http://rdfh.ch/lists/00FF/9004ce8eaf",
                                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                        value = "Champf\u00E8r",
                                        language = None
                                    ))),
                                    position = Some(4),
                                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                                ),
                                ListNodeADM(
                                    children = Nil,
                                    name = Some("17"),
                                    id = "http://rdfh.ch/lists/00FF/bc6fb43eb2",
                                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                        value = "Unterengadin",
                                        language = None
                                    ))),
                                    position = Some(16),
                                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                                ),
                                ListNodeADM(
                                    children = Nil,
                                    name = Some("12"),
                                    id = "http://rdfh.ch/lists/00FF/bf6d1420b1",
                                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                        value = "Madulain",
                                        language = None
                                    ))),
                                    position = Some(11),
                                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                                ),
                                ListNodeADM(
                                    children = Nil,
                                    name = Some("7"),
                                    id = "http://rdfh.ch/lists/00FF/c26b7401b0",
                                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                        value = "Celerina",
                                        language = None
                                    ))),
                                    position = Some(6),
                                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                                ),
                                ListNodeADM(
                                    children = Nil,
                                    name = Some("2"),
                                    id = "http://rdfh.ch/lists/00FF/c569d4e2ae",
                                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                        value = "Sils",
                                        language = None
                                    ))),
                                    position = Some(1),
                                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                                ),
                                ListNodeADM(
                                    children = Nil,
                                    name = Some("14"),
                                    id = "http://rdfh.ch/lists/00FF/f1d4ba92b1",
                                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                        value = "S-chanf",
                                        language = None
                                    ))),
                                    position = Some(13),
                                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                                ),
                                ListNodeADM(
                                    children = Nil,
                                    name = Some("9"),
                                    id = "http://rdfh.ch/lists/00FF/f4d21a74b0",
                                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                        value = "Bever",
                                        language = None
                                    ))),
                                    position = Some(8),
                                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                                ),
                                ListNodeADM(
                                    children = Nil,
                                    name = Some("4"),
                                    id = "http://rdfh.ch/lists/00FF/f7d07a55af",
                                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                        value = "Surlej",
                                        language = None
                                    ))),
                                    position = Some(3),
                                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                                )
                            ),
                            name = Some("9"),
                            id = "http://rdfh.ch/lists/00FF/93022e70ae",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Ortschaften Winter",
                                language = None
                            ))),
                            position = Some(8),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("7"),
                            id = "http://rdfh.ch/lists/00FF/9ffaadf5a9",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Landschaft Berge",
                                language = None
                            ))),
                            position = Some(6),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("2"),
                            id = "http://rdfh.ch/lists/00FF/a2f80dd7a8",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Landschaft Sommer ohne Ortschaften",
                                language = None
                            ))),
                            position = Some(1),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("4"),
                            id = "http://rdfh.ch/lists/00FF/d45fb449a9",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Landschaft Winter ohne Ortschaften",
                                language = None
                            ))),
                            position = Some(3),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        )
                    ),
                    name = Some("3"),
                    id = "http://rdfh.ch/lists/00FF/70916764a8",
                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                        value = "ENGADIN",
                        language = None
                    ))),
                    position = Some(2),
                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                ),
                ListNodeADM(
                    children = Vector(
                        ListNodeADM(
                            children = Nil,
                            name = Some("1"),
                            id = "http://rdfh.ch/lists/00FF/1a424161b5",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Landkarten",
                                language = None
                            ))),
                            position = Some(0),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("2"),
                            id = "http://rdfh.ch/lists/00FF/b375949ab5",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Panoramen",
                                language = None
                            ))),
                            position = Some(1),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        )
                    ),
                    name = Some("6"),
                    id = "http://rdfh.ch/lists/00FF/810eee27b5",
                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                        value = "LANDKARTEN UND PANORAMEN",
                        language = None
                    ))),
                    position = Some(5),
                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                ),
                ListNodeADM(
                    children = Vector(
                        ListNodeADM(
                            children = Nil,
                            name = Some("3"),
                            id = "http://rdfh.ch/lists/00FF/45cfa1df0401",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Personen",
                                language = None
                            ))),
                            position = Some(2),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("1"),
                            id = "http://rdfh.ch/lists/00FF/4fa747b5b4",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Ortschaften",
                                language = None
                            ))),
                            position = Some(0),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("2"),
                            id = "http://rdfh.ch/lists/00FF/e8da9aeeb4",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Landschaften",
                                language = None
                            ))),
                            position = Some(1),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        )
                    ),
                    name = Some("5"),
                    id = "http://rdfh.ch/lists/00FF/b673f47bb4",
                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                        value = "SUEDTAELER",
                        language = None
                    ))),
                    position = Some(4),
                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                ),
                ListNodeADM(
                    children = Vector(
                        ListNodeADM(
                            children = Nil,
                            name = Some("7"),
                            id = "http://rdfh.ch/lists/00FF/1d40a142b4",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "St. Moritz Schulh\u00E4user",
                                language = None
                            ))),
                            position = Some(6),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("2"),
                            id = "http://rdfh.ch/lists/00FF/203e0124b3",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "St. Moritz Dorf Sommer",
                                language = None
                            ))),
                            position = Some(1),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("4"),
                            id = "http://rdfh.ch/lists/00FF/52a5a796b3",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "St. Moritz Denkm\u00E4ler",
                                language = None
                            ))),
                            position = Some(3),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("6"),
                            id = "http://rdfh.ch/lists/00FF/840c4e09b4",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "St. Moritz Landschaft Winter",
                                language = None
                            ))),
                            position = Some(5),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("1"),
                            id = "http://rdfh.ch/lists/00FF/870aaeeab2",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "St. Moritz Dorf und Bad Winter",
                                language = None
                            ))),
                            position = Some(0),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("3"),
                            id = "http://rdfh.ch/lists/00FF/b971545db3",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "St. Moritz Bad Sommer",
                                language = None
                            ))),
                            position = Some(2),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("5"),
                            id = "http://rdfh.ch/lists/00FF/ebd8facfb3",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "St. Moritz Landschaft Sommer",
                                language = None
                            ))),
                            position = Some(4),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        )
                    ),
                    name = Some("4"),
                    id = "http://rdfh.ch/lists/00FF/eed65ab1b2",
                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                        value = "ST. MORITZ",
                        language = None
                    ))),
                    position = Some(3),
                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                )
            ),
            name = Some("2GEO"),
            id = "http://rdfh.ch/lists/00FF/738fc745a7",
            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                value = "GEOGRAPHIE",
                language = None
            ))),
            position = Some(1),
            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
        ),
        ListNodeADM(
            children = Vector(
                ListNodeADM(
                    children = Nil,
                    name = Some("3"),
                    id = "http://rdfh.ch/lists/00FF/106a9b8b0501",
                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                        value = "ENGADIN",
                        language = None
                    ))),
                    position = Some(2),
                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                ),
                ListNodeADM(
                    children = Nil,
                    name = Some("7"),
                    id = "http://rdfh.ch/lists/00FF/37b747aabd",
                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                        value = "WAPPEN UND FAHNEN",
                        language = None
                    ))),
                    position = Some(5),
                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                ),
                ListNodeADM(
                    children = Nil,
                    name = Some("2"),
                    id = "http://rdfh.ch/lists/00FF/773648520501",
                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                        value = "GRAUB\u00DCNDEN",
                        language = None
                    ))),
                    position = Some(1),
                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                ),
                ListNodeADM(
                    children = Nil,
                    name = Some("4"),
                    id = "http://rdfh.ch/lists/00FF/a99deec40501",
                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                        value = "ST. MORITZ",
                        language = None
                    ))),
                    position = Some(3),
                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                ),
                ListNodeADM(
                    children = Nil,
                    name = Some("9"),
                    id = "http://rdfh.ch/lists/00FF/d0ea9ae3bd",
                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                        value = "KRIEGE UND MILIT\u00C4R",
                        language = None
                    ))),
                    position = Some(6),
                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                ),
                ListNodeADM(
                    children = Nil,
                    name = Some("1"),
                    id = "http://rdfh.ch/lists/00FF/de02f5180501",
                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                        value = "SCHWEIZ",
                        language = None
                    ))),
                    position = Some(0),
                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                ),
                ListNodeADM(
                    children = Vector(
                        ListNodeADM(
                            children = Nil,
                            name = Some("6"),
                            id = "http://rdfh.ch/lists/00FF/0550a137bd",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Sonnenbadende",
                                language = None
                            ))),
                            position = Some(5),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("3"),
                            id = "http://rdfh.ch/lists/00FF/3ab5a78bbc",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Gruppen Einheimische",
                                language = None
                            ))),
                            position = Some(2),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("5"),
                            id = "http://rdfh.ch/lists/00FF/6c1c4efebc",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Kinder Sommer",
                                language = None
                            ))),
                            position = Some(4),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Vector(
                                ListNodeADM(
                                    children = Nil,
                                    name = Some("26"),
                                    id = "http://rdfh.ch/lists/00FF/084e0119bc",
                                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                        value = "Personen Z",
                                        language = None
                                    ))),
                                    position = Some(25),
                                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                                ),
                                ListNodeADM(
                                    children = Nil,
                                    name = Some("21"),
                                    id = "http://rdfh.ch/lists/00FF/0b4c61faba",
                                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                        value = "Personen U",
                                        language = None
                                    ))),
                                    position = Some(20),
                                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                                ),
                                ListNodeADM(
                                    children = Nil,
                                    name = Some("16"),
                                    id = "http://rdfh.ch/lists/00FF/0e4ac1dbb9",
                                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                        value = "Personen P",
                                        language = None
                                    ))),
                                    position = Some(15),
                                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                                ),
                                ListNodeADM(
                                    children = Nil,
                                    name = Some("11"),
                                    id = "http://rdfh.ch/lists/00FF/114821bdb8",
                                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                        value = "Personen K",
                                        language = None
                                    ))),
                                    position = Some(10),
                                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                                ),
                                ListNodeADM(
                                    children = Nil,
                                    name = Some("6"),
                                    id = "http://rdfh.ch/lists/00FF/1446819eb7",
                                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                        value = "Personen F",
                                        language = None
                                    ))),
                                    position = Some(5),
                                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                                ),
                                ListNodeADM(
                                    children = Nil,
                                    name = Some("1"),
                                    id = "http://rdfh.ch/lists/00FF/1744e17fb6",
                                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                        value = "Personen A",
                                        language = None
                                    ))),
                                    position = Some(0),
                                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                                ),
                                ListNodeADM(
                                    children = Nil,
                                    name = Some("23"),
                                    id = "http://rdfh.ch/lists/00FF/3db3076dbb",
                                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                        value = "Personen W",
                                        language = None
                                    ))),
                                    position = Some(22),
                                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                                ),
                                ListNodeADM(
                                    children = Nil,
                                    name = Some("18"),
                                    id = "http://rdfh.ch/lists/00FF/40b1674eba",
                                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                        value = "Personen R",
                                        language = None
                                    ))),
                                    position = Some(17),
                                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                                ),
                                ListNodeADM(
                                    children = Nil,
                                    name = Some("13"),
                                    id = "http://rdfh.ch/lists/00FF/43afc72fb9",
                                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                        value = "Personen M",
                                        language = None
                                    ))),
                                    position = Some(12),
                                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                                ),
                                ListNodeADM(
                                    children = Nil,
                                    name = Some("8"),
                                    id = "http://rdfh.ch/lists/00FF/46ad2711b8",
                                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                        value = "Personen H",
                                        language = None
                                    ))),
                                    position = Some(7),
                                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                                ),
                                ListNodeADM(
                                    children = Nil,
                                    name = Some("3"),
                                    id = "http://rdfh.ch/lists/00FF/49ab87f2b6",
                                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                        value = "Personen C",
                                        language = None
                                    ))),
                                    position = Some(2),
                                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                                ),
                                ListNodeADM(
                                    children = Nil,
                                    name = Some("25"),
                                    id = "http://rdfh.ch/lists/00FF/6f1aaedfbb",
                                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                        value = "Personen Y",
                                        language = None
                                    ))),
                                    position = Some(24),
                                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                                ),
                                ListNodeADM(
                                    children = Nil,
                                    name = Some("20"),
                                    id = "http://rdfh.ch/lists/00FF/72180ec1ba",
                                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                        value = "Personen T",
                                        language = None
                                    ))),
                                    position = Some(19),
                                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                                ),
                                ListNodeADM(
                                    children = Nil,
                                    name = Some("15"),
                                    id = "http://rdfh.ch/lists/00FF/75166ea2b9",
                                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                        value = "Personen O",
                                        language = None
                                    ))),
                                    position = Some(14),
                                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                                ),
                                ListNodeADM(
                                    children = Nil,
                                    name = Some("10"),
                                    id = "http://rdfh.ch/lists/00FF/7814ce83b8",
                                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                        value = "Personen J",
                                        language = None
                                    ))),
                                    position = Some(9),
                                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                                ),
                                ListNodeADM(
                                    children = Nil,
                                    name = Some("5"),
                                    id = "http://rdfh.ch/lists/00FF/7b122e65b7",
                                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                        value = "Personen E",
                                        language = None
                                    ))),
                                    position = Some(4),
                                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                                ),
                                ListNodeADM(
                                    children = Nil,
                                    name = Some("22"),
                                    id = "http://rdfh.ch/lists/00FF/a47fb433bb",
                                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                        value = "Personen V",
                                        language = None
                                    ))),
                                    position = Some(21),
                                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                                ),
                                ListNodeADM(
                                    children = Nil,
                                    name = Some("17"),
                                    id = "http://rdfh.ch/lists/00FF/a77d1415ba",
                                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                        value = "Personen Q",
                                        language = None
                                    ))),
                                    position = Some(16),
                                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                                ),
                                ListNodeADM(
                                    children = Nil,
                                    name = Some("12"),
                                    id = "http://rdfh.ch/lists/00FF/aa7b74f6b8",
                                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                        value = "Personen L",
                                        language = None
                                    ))),
                                    position = Some(11),
                                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                                ),
                                ListNodeADM(
                                    children = Nil,
                                    name = Some("7"),
                                    id = "http://rdfh.ch/lists/00FF/ad79d4d7b7",
                                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                        value = "Personen G",
                                        language = None
                                    ))),
                                    position = Some(6),
                                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                                ),
                                ListNodeADM(
                                    children = Nil,
                                    name = Some("2"),
                                    id = "http://rdfh.ch/lists/00FF/b07734b9b6",
                                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                        value = "Personen B",
                                        language = None
                                    ))),
                                    position = Some(1),
                                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                                ),
                                ListNodeADM(
                                    children = Nil,
                                    name = Some("24"),
                                    id = "http://rdfh.ch/lists/00FF/d6e65aa6bb",
                                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                        value = "Personen X",
                                        language = None
                                    ))),
                                    position = Some(23),
                                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                                ),
                                ListNodeADM(
                                    children = Nil,
                                    name = Some("19"),
                                    id = "http://rdfh.ch/lists/00FF/d9e4ba87ba",
                                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                        value = "Personen S",
                                        language = None
                                    ))),
                                    position = Some(18),
                                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                                ),
                                ListNodeADM(
                                    children = Nil,
                                    name = Some("14"),
                                    id = "http://rdfh.ch/lists/00FF/dce21a69b9",
                                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                        value = "Personen N",
                                        language = None
                                    ))),
                                    position = Some(13),
                                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                                ),
                                ListNodeADM(
                                    children = Nil,
                                    name = Some("9"),
                                    id = "http://rdfh.ch/lists/00FF/dfe07a4ab8",
                                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                        value = "Personen I",
                                        language = None
                                    ))),
                                    position = Some(8),
                                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                                ),
                                ListNodeADM(
                                    children = Nil,
                                    name = Some("4"),
                                    id = "http://rdfh.ch/lists/00FF/e2deda2bb7",
                                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                        value = "Personen D",
                                        language = None
                                    ))),
                                    position = Some(3),
                                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                                )
                            ),
                            name = Some("1"),
                            id = "http://rdfh.ch/lists/00FF/7e108e46b6",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Personen A-Z",
                                language = None
                            ))),
                            position = Some(0),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("7"),
                            id = "http://rdfh.ch/lists/00FF/9e83f470bd",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Zuschauer",
                                language = None
                            ))),
                            position = Some(6),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("2"),
                            id = "http://rdfh.ch/lists/00FF/a1815452bc",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Personen unbekannt",
                                language = None
                            ))),
                            position = Some(1),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("4"),
                            id = "http://rdfh.ch/lists/00FF/d3e8fac4bc",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Kinder Winter",
                                language = None
                            ))),
                            position = Some(3),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        )
                    ),
                    name = Some("5"),
                    id = "http://rdfh.ch/lists/00FF/e5dc3a0db6",
                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                        value = "BIOGRAPHIEN",
                        language = None
                    ))),
                    position = Some(4),
                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                )
            ),
            name = Some("3GES"),
            id = "http://rdfh.ch/lists/00FF/4ca9e7d3b5",
            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                value = "GESCHICHTE",
                language = None
            ))),
            position = Some(2),
            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
        ),
        ListNodeADM(
            children = Vector(
                ListNodeADM(
                    children = Vector(
                        ListNodeADM(
                            children = Nil,
                            name = Some("2"),
                            id = "http://rdfh.ch/lists/00FF/34b9e7c8be",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Gem\u00E4lde",
                                language = None
                            ))),
                            position = Some(1),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("4"),
                            id = "http://rdfh.ch/lists/00FF/66208e3bbf",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Segantini und Museum",
                                language = None
                            ))),
                            position = Some(3),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("1"),
                            id = "http://rdfh.ch/lists/00FF/9b85948fbe",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Ausstellungen",
                                language = None
                            ))),
                            position = Some(0),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("3"),
                            id = "http://rdfh.ch/lists/00FF/cdec3a02bf",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Karrikaturen und Kritik",
                                language = None
                            ))),
                            position = Some(2),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("5"),
                            id = "http://rdfh.ch/lists/00FF/ff53e174bf",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Sgrafitti",
                                language = None
                            ))),
                            position = Some(4),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        )
                    ),
                    name = Some("5"),
                    id = "http://rdfh.ch/lists/00FF/02524156be",
                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                        value = "MALEREI",
                        language = None
                    ))),
                    position = Some(0),
                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                ),
                ListNodeADM(
                    children = Vector(
                        ListNodeADM(
                            children = Nil,
                            name = Some("2"),
                            id = "http://rdfh.ch/lists/00FF/572aaed4c4",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Pl\u00E4ne",
                                language = None
                            ))),
                            position = Some(1),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("1"),
                            id = "http://rdfh.ch/lists/00FF/bef65a9bc4",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Architektur / Inneneinrichtungen",
                                language = None
                            ))),
                            position = Some(0),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        )
                    ),
                    name = Some("10"),
                    id = "http://rdfh.ch/lists/00FF/25c30762c4",
                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                        value = "ARCHITEKTUR",
                        language = None
                    ))),
                    position = Some(5),
                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                ),
                ListNodeADM(
                    children = Vector(
                        ListNodeADM(
                            children = Nil,
                            name = Some("2"),
                            id = "http://rdfh.ch/lists/00FF/6024ce78c1",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Foto",
                                language = None
                            ))),
                            position = Some(1),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("1"),
                            id = "http://rdfh.ch/lists/00FF/c7f07a3fc1",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Heidi Film",
                                language = None
                            ))),
                            position = Some(0),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("3"),
                            id = "http://rdfh.ch/lists/00FF/f95721b2c1",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Film",
                                language = None
                            ))),
                            position = Some(2),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        )
                    ),
                    name = Some("7"),
                    id = "http://rdfh.ch/lists/00FF/2ebd2706c1",
                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                        value = "FILM UND FOTO",
                        language = None
                    ))),
                    position = Some(2),
                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                ),
                ListNodeADM(
                    children = Vector(
                        ListNodeADM(
                            children = Nil,
                            name = Some("6"),
                            id = "http://rdfh.ch/lists/00FF/28c16743c3",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Kunstgewerbe",
                                language = None
                            ))),
                            position = Some(5),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("1"),
                            id = "http://rdfh.ch/lists/00FF/2bbfc724c2",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Modelle",
                                language = None
                            ))),
                            position = Some(0),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("3"),
                            id = "http://rdfh.ch/lists/00FF/5d266e97c2",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Plastiken",
                                language = None
                            ))),
                            position = Some(2),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("5"),
                            id = "http://rdfh.ch/lists/00FF/8f8d140ac3",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Bildhauerei",
                                language = None
                            ))),
                            position = Some(4),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("2"),
                            id = "http://rdfh.ch/lists/00FF/c4f21a5ec2",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Schneeskulpturen",
                                language = None
                            ))),
                            position = Some(1),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("4"),
                            id = "http://rdfh.ch/lists/00FF/f659c1d0c2",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Stiche",
                                language = None
                            ))),
                            position = Some(3),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        )
                    ),
                    name = Some("8"),
                    id = "http://rdfh.ch/lists/00FF/928b74ebc1",
                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                        value = "BILDHAUEREI UND KUNSTGEWERBE",
                        language = None
                    ))),
                    position = Some(3),
                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                ),
                ListNodeADM(
                    children = Vector(
                        ListNodeADM(
                            children = Nil,
                            name = Some("1"),
                            id = "http://rdfh.ch/lists/00FF/31bb87e7bf",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Kurorchester",
                                language = None
                            ))),
                            position = Some(0),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("3"),
                            id = "http://rdfh.ch/lists/00FF/63222e5ac0",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Zirkus",
                                language = None
                            ))),
                            position = Some(2),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("5"),
                            id = "http://rdfh.ch/lists/00FF/9589d4ccc0",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Tanz",
                                language = None
                            ))),
                            position = Some(4),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("2"),
                            id = "http://rdfh.ch/lists/00FF/caeeda20c0",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Musik",
                                language = None
                            ))),
                            position = Some(1),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("4"),
                            id = "http://rdfh.ch/lists/00FF/fc558193c0",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Theater",
                                language = None
                            ))),
                            position = Some(3),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        )
                    ),
                    name = Some("6"),
                    id = "http://rdfh.ch/lists/00FF/988734aebf",
                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                        value = "MUSIK, THEATER UND RADIO",
                        language = None
                    ))),
                    position = Some(1),
                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                ),
                ListNodeADM(
                    children = Vector(
                        ListNodeADM(
                            children = Nil,
                            name = Some("1"),
                            id = "http://rdfh.ch/lists/00FF/5a280eb6c3",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Grafiken",
                                language = None
                            ))),
                            position = Some(0),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("3"),
                            id = "http://rdfh.ch/lists/00FF/8c8fb428c4",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Plakate",
                                language = None
                            ))),
                            position = Some(2),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("2"),
                            id = "http://rdfh.ch/lists/00FF/f35b61efc3",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Holzschnitte",
                                language = None
                            ))),
                            position = Some(1),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        )
                    ),
                    name = Some("9"),
                    id = "http://rdfh.ch/lists/00FF/c1f4ba7cc3",
                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                        value = "ST. MORITZ GRAFIKEN UND PLAKATE",
                        language = None
                    ))),
                    position = Some(4),
                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                )
            ),
            name = Some("4KUN"),
            id = "http://rdfh.ch/lists/00FF/691eee1cbe",
            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                value = "KUNST",
                language = None
            ))),
            position = Some(3),
            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
        ),
        ListNodeADM(
            children = Vector(
                ListNodeADM(
                    children = Vector(
                        ListNodeADM(
                            children = Nil,
                            name = Some("2"),
                            id = "http://rdfh.ch/lists/00FF/542c4ef3c5",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Heilbad innen",
                                language = None
                            ))),
                            position = Some(1),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("1"),
                            id = "http://rdfh.ch/lists/00FF/bbf8fab9c5",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Heilbad aussen",
                                language = None
                            ))),
                            position = Some(0),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        )
                    ),
                    name = Some("3"),
                    id = "http://rdfh.ch/lists/00FF/22c5a780c5",
                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                        value = "HEILBAD UND QUELLEN",
                        language = None
                    ))),
                    position = Some(1),
                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                ),
                ListNodeADM(
                    children = Nil,
                    name = Some("1"),
                    id = "http://rdfh.ch/lists/00FF/89915447c5",
                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                        value = "MEDIZIN UND NATURHEILKUNDE",
                        language = None
                    ))),
                    position = Some(0),
                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                ),
                ListNodeADM(
                    children = Nil,
                    name = Some("4"),
                    id = "http://rdfh.ch/lists/00FF/ed5fa12cc6",
                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                        value = "SPITAL UND KLINIKEN / KINDERHEIME",
                        language = None
                    ))),
                    position = Some(2),
                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                )
            ),
            name = Some("5MED"),
            id = "http://rdfh.ch/lists/00FF/f05d010ec5",
            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                value = "MEDIZIN",
                language = None
            ))),
            position = Some(4),
            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
        ),
        ListNodeADM(
            children = Vector(
                ListNodeADM(
                    children = Vector(
                        ListNodeADM(
                            children = Nil,
                            name = Some("2"),
                            id = "http://rdfh.ch/lists/00FF/512eee11c7",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Jagen",
                                language = None
                            ))),
                            position = Some(1),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("1"),
                            id = "http://rdfh.ch/lists/00FF/b8fa9ad8c6",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Fischen",
                                language = None
                            ))),
                            position = Some(0),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("3"),
                            id = "http://rdfh.ch/lists/00FF/ea61414bc7",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Tiere",
                                language = None
                            ))),
                            position = Some(2),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        )
                    ),
                    name = Some("2"),
                    id = "http://rdfh.ch/lists/00FF/1fc7479fc6",
                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                        value = "FAUNA",
                        language = None
                    ))),
                    position = Some(0),
                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                ),
                ListNodeADM(
                    children = Nil,
                    name = Some("4"),
                    id = "http://rdfh.ch/lists/00FF/4e308e30c8",
                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                        value = "GEOLOGIE UND MINERALOGIE",
                        language = None
                    ))),
                    position = Some(2),
                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                ),
                ListNodeADM(
                    children = Vector(
                        ListNodeADM(
                            children = Nil,
                            name = Some("1"),
                            id = "http://rdfh.ch/lists/00FF/1cc9e7bdc7",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Blumen",
                                language = None
                            ))),
                            position = Some(0),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("2"),
                            id = "http://rdfh.ch/lists/00FF/b5fc3af7c7",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "B\u00E4ume",
                                language = None
                            ))),
                            position = Some(1),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        )
                    ),
                    name = Some("3"),
                    id = "http://rdfh.ch/lists/00FF/83959484c7",
                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                        value = "FLORA",
                        language = None
                    ))),
                    position = Some(1),
                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                ),
                ListNodeADM(
                    children = Nil,
                    name = Some("6"),
                    id = "http://rdfh.ch/lists/00FF/e4658188c9",
                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                        value = "UMWELT",
                        language = None
                    ))),
                    position = Some(4),
                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                ),
                ListNodeADM(
                    children = Vector(
                        ListNodeADM(
                            children = Nil,
                            name = Some("2"),
                            id = "http://rdfh.ch/lists/00FF/19cb87dcc8",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Gletscher",
                                language = None
                            ))),
                            position = Some(1),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("4"),
                            id = "http://rdfh.ch/lists/00FF/4b322e4fc9",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Schnee, Raureif, Eisblumen",
                                language = None
                            ))),
                            position = Some(3),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("1"),
                            id = "http://rdfh.ch/lists/00FF/809734a3c8",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Gew\u00E4sser und \u00DCberschwemmungen",
                                language = None
                            ))),
                            position = Some(0),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("3"),
                            id = "http://rdfh.ch/lists/00FF/b2feda15c9",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Lawinen",
                                language = None
                            ))),
                            position = Some(2),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        )
                    ),
                    name = Some("5"),
                    id = "http://rdfh.ch/lists/00FF/e763e169c8",
                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                        value = "KLIMATOLOGIE UND METEOROLOGIE",
                        language = None
                    ))),
                    position = Some(3),
                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                )
            ),
            name = Some("6NAT"),
            id = "http://rdfh.ch/lists/00FF/8693f465c6",
            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                value = "NATURKUNDE",
                language = None
            ))),
            position = Some(5),
            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
        ),
        ListNodeADM(
            children = Vector(ListNodeADM(
                children = Vector(ListNodeADM(
                    children = Nil,
                    name = Some("1"),
                    id = "http://rdfh.ch/lists/00FF/af007b34ca",
                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                        value = "St. Moritz Kirchen",
                        language = None
                    ))),
                    position = Some(0),
                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                )),
                name = Some("1"),
                id = "http://rdfh.ch/lists/00FF/16cd27fbc9",
                labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                    value = "RELIGION UND KIRCHEN",
                    language = None
                ))),
                position = Some(0),
                comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
            )),
            name = Some("7REL"),
            id = "http://rdfh.ch/lists/00FF/7d99d4c1c9",
            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                value = "RELIGION",
                language = None
            ))),
            position = Some(6),
            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
        ),
        ListNodeADM(
            children = Vector(
                ListNodeADM(
                    children = Vector(
                        ListNodeADM(
                            children = Nil,
                            name = Some("5"),
                            id = "http://rdfh.ch/lists/00FF/0dd30757cd",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Schlitteda",
                                language = None
                            ))),
                            position = Some(4),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("2"),
                            id = "http://rdfh.ch/lists/00FF/42380eabcc",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Chalandamarz",
                                language = None
                            ))),
                            position = Some(1),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("4"),
                            id = "http://rdfh.ch/lists/00FF/749fb41dcd",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Feste und Umz\u00FCge",
                                language = None
                            ))),
                            position = Some(3),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("6"),
                            id = "http://rdfh.ch/lists/00FF/a6065b90cd",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Trachten",
                                language = None
                            ))),
                            position = Some(5),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("1"),
                            id = "http://rdfh.ch/lists/00FF/a904bb71cc",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "B\u00E4lle und Verkleidungen",
                                language = None
                            ))),
                            position = Some(0),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("3"),
                            id = "http://rdfh.ch/lists/00FF/db6b61e4cc",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Engadiner Museum",
                                language = None
                            ))),
                            position = Some(2),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        )
                    ),
                    name = Some("4"),
                    id = "http://rdfh.ch/lists/00FF/10d16738cc",
                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                        value = "VOLKSKUNDE",
                        language = None
                    ))),
                    position = Some(3),
                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                ),
                ListNodeADM(
                    children = Nil,
                    name = Some("6"),
                    id = "http://rdfh.ch/lists/00FF/3f3aaec9cd",
                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                        value = "PARTEIEN UND GRUPPIERUNGEN",
                        language = None
                    ))),
                    position = Some(4),
                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                ),
                ListNodeADM(
                    children = Nil,
                    name = Some("3"),
                    id = "http://rdfh.ch/lists/00FF/779d14ffcb",
                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                        value = "SCHULWESEN",
                        language = None
                    ))),
                    position = Some(2),
                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                ),
                ListNodeADM(
                    children = Vector(
                        ListNodeADM(
                            children = Nil,
                            name = Some("1"),
                            id = "http://rdfh.ch/lists/00FF/13cfc719cb",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Wasserwirtschaft",
                                language = None
                            ))),
                            position = Some(0),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("3"),
                            id = "http://rdfh.ch/lists/00FF/45366e8ccb",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Polizei und Beh\u00F6rde",
                                language = None
                            ))),
                            position = Some(2),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("2"),
                            id = "http://rdfh.ch/lists/00FF/ac021b53cb",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Feuer und Feuerwehr",
                                language = None
                            ))),
                            position = Some(1),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("4"),
                            id = "http://rdfh.ch/lists/00FF/de69c1c5cb",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Abfallbewirtschaftung",
                                language = None
                            ))),
                            position = Some(3),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        )
                    ),
                    name = Some("2"),
                    id = "http://rdfh.ch/lists/00FF/7a9b74e0ca",
                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                        value = "GEMEINDEWESEN",
                        language = None
                    ))),
                    position = Some(1),
                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                ),
                ListNodeADM(
                    children = Nil,
                    name = Some("7"),
                    id = "http://rdfh.ch/lists/00FF/d86d0103ce",
                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                        value = "SCHWESTERNST\u00C4TDE",
                        language = None
                    ))),
                    position = Some(5),
                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                ),
                ListNodeADM(
                    children = Nil,
                    name = Some("1"),
                    id = "http://rdfh.ch/lists/00FF/e16721a7ca",
                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                        value = "VERFASSUNGEN UND GESETZE",
                        language = None
                    ))),
                    position = Some(0),
                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                )
            ),
            name = Some("8SOZ"),
            id = "http://rdfh.ch/lists/00FF/4834ce6dca",
            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                value = "SOZIALES",
                language = None
            ))),
            position = Some(7),
            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
        ),
        ListNodeADM(
            children = Vector(
                ListNodeADM(
                    children = Vector(
                        ListNodeADM(
                            children = Nil,
                            name = Some("2"),
                            id = "http://rdfh.ch/lists/00FF/33422e44d2",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Schlitteln",
                                language = None
                            ))),
                            position = Some(1),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("4"),
                            id = "http://rdfh.ch/lists/00FF/65a9d4b6d2",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Tailing",
                                language = None
                            ))),
                            position = Some(3),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("1"),
                            id = "http://rdfh.ch/lists/00FF/9a0edb0ad2",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Cricket",
                                language = None
                            ))),
                            position = Some(0),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("3"),
                            id = "http://rdfh.ch/lists/00FF/cc75817dd2",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Schneeschuhlaufen",
                                language = None
                            ))),
                            position = Some(2),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("5"),
                            id = "http://rdfh.ch/lists/00FF/fedc27f0d2",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Wind-, Schlittenhundrennen",
                                language = None
                            ))),
                            position = Some(4),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        )
                    ),
                    name = Some("1"),
                    id = "http://rdfh.ch/lists/00FF/01db87d1d1",
                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                        value = "WINTERSPORT",
                        language = None
                    ))),
                    position = Some(1),
                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                ),
                ListNodeADM(
                    children = Vector(
                        ListNodeADM(
                            children = Nil,
                            name = Some("10"),
                            id = "http://rdfh.ch/lists/00FF/04d9e7b2d0",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Schiessen",
                                language = None
                            ))),
                            position = Some(9),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("5"),
                            id = "http://rdfh.ch/lists/00FF/07d74794cf",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Fitness",
                                language = None
                            ))),
                            position = Some(4),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("12"),
                            id = "http://rdfh.ch/lists/00FF/36408e25d1",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Zeitmessung",
                                language = None
                            ))),
                            position = Some(11),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("7"),
                            id = "http://rdfh.ch/lists/00FF/393eee06d0",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Krafttraining",
                                language = None
                            ))),
                            position = Some(6),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("2"),
                            id = "http://rdfh.ch/lists/00FF/3c3c4ee8ce",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Boxen",
                                language = None
                            ))),
                            position = Some(1),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("14"),
                            id = "http://rdfh.ch/lists/00FF/68a73498d1",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Schwingen",
                                language = None
                            ))),
                            position = Some(13),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("9"),
                            id = "http://rdfh.ch/lists/00FF/6ba59479d0",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Pokale, Preise, Medallien",
                                language = None
                            ))),
                            position = Some(8),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("4"),
                            id = "http://rdfh.ch/lists/00FF/6ea3f45acf",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Fechten",
                                language = None
                            ))),
                            position = Some(3),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("11"),
                            id = "http://rdfh.ch/lists/00FF/9d0c3becd0",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Turnen",
                                language = None
                            ))),
                            position = Some(10),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("6"),
                            id = "http://rdfh.ch/lists/00FF/a00a9bcdcf",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "H\u00F6hentraining",
                                language = None
                            ))),
                            position = Some(5),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("1"),
                            id = "http://rdfh.ch/lists/00FF/a308fbaece",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Bridge",
                                language = None
                            ))),
                            position = Some(0),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("13"),
                            id = "http://rdfh.ch/lists/00FF/cf73e15ed1",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Hornussen",
                                language = None
                            ))),
                            position = Some(12),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("8"),
                            id = "http://rdfh.ch/lists/00FF/d2714140d0",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Leichtathletik",
                                language = None
                            ))),
                            position = Some(7),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("3"),
                            id = "http://rdfh.ch/lists/00FF/d56fa121cf",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Camping",
                                language = None
                            ))),
                            position = Some(2),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        )
                    ),
                    name = Some("0"),
                    id = "http://rdfh.ch/lists/00FF/0ad5a775ce",
                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                        value = "SPORT",
                        language = None
                    ))),
                    position = Some(0),
                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                ),
                ListNodeADM(
                    children = Vector(
                        ListNodeADM(
                            children = Vector(
                                ListNodeADM(
                                    children = Nil,
                                    name = Some("2"),
                                    id = "http://rdfh.ch/lists/00FF/47bd14e9dd",
                                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                        value = "Bau",
                                        language = None
                                    ))),
                                    position = Some(1),
                                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                                ),
                                ListNodeADM(
                                    children = Nil,
                                    name = Some("1"),
                                    id = "http://rdfh.ch/lists/00FF/ae89c1afdd",
                                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                        value = "Personen",
                                        language = None
                                    ))),
                                    position = Some(0),
                                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                                )
                            ),
                            name = Some("2"),
                            id = "http://rdfh.ch/lists/00FF/15566e76dd",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Cresta Run",
                                language = None
                            ))),
                            position = Some(1),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("3"),
                            id = "http://rdfh.ch/lists/00FF/42d141fe0501",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Rodeln",
                                language = None
                            ))),
                            position = Some(2),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Vector(
                                ListNodeADM(
                                    children = Nil,
                                    name = Some("1"),
                                    id = "http://rdfh.ch/lists/00FF/4abb74cadc",
                                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                        value = "Personen",
                                        language = None
                                    ))),
                                    position = Some(0),
                                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                                ),
                                ListNodeADM(
                                    children = Nil,
                                    name = Some("3"),
                                    id = "http://rdfh.ch/lists/00FF/7c221b3ddd",
                                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                        value = "Bau",
                                        language = None
                                    ))),
                                    position = Some(2),
                                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                                ),
                                ListNodeADM(
                                    children = Nil,
                                    name = Some("2"),
                                    id = "http://rdfh.ch/lists/00FF/e3eec703dd",
                                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                        value = "St\u00FCrze",
                                        language = None
                                    ))),
                                    position = Some(1),
                                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                                )
                            ),
                            name = Some("1"),
                            id = "http://rdfh.ch/lists/00FF/b1872191dc",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Bob Run",
                                language = None
                            ))),
                            position = Some(0),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        )
                    ),
                    name = Some("5"),
                    id = "http://rdfh.ch/lists/00FF/1854ce57dc",
                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                        value = "CRESTA RUN UND BOB",
                        language = None
                    ))),
                    position = Some(9),
                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                ),
                ListNodeADM(
                    children = Nil,
                    name = Some("2-5"),
                    id = "http://rdfh.ch/lists/00FF/244c4eddd7",
                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                        value = "SNOWBOARD UND SNOWBOARDSCHULE",
                        language = None
                    ))),
                    position = Some(6),
                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                ),
                ListNodeADM(
                    children = Vector(
                        ListNodeADM(
                            children = Nil,
                            name = Some("5"),
                            id = "http://rdfh.ch/lists/00FF/29d1541be9",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Windsurfen",
                                language = None
                            ))),
                            position = Some(4),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("7"),
                            id = "http://rdfh.ch/lists/00FF/5b38fb8de9",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Rafting",
                                language = None
                            ))),
                            position = Some(6),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("2"),
                            id = "http://rdfh.ch/lists/00FF/5e365b6fe8",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Schwimmen Seen",
                                language = None
                            ))),
                            position = Some(1),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("4"),
                            id = "http://rdfh.ch/lists/00FF/909d01e2e8",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Segeln",
                                language = None
                            ))),
                            position = Some(3),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("6"),
                            id = "http://rdfh.ch/lists/00FF/c204a854e9",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Tauchen",
                                language = None
                            ))),
                            position = Some(5),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("1"),
                            id = "http://rdfh.ch/lists/00FF/c5020836e8",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Schwimmen Hallenb\u00E4der",
                                language = None
                            ))),
                            position = Some(0),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("8"),
                            id = "http://rdfh.ch/lists/00FF/f46b4ec7e9",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Kitesurfen",
                                language = None
                            ))),
                            position = Some(7),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("3"),
                            id = "http://rdfh.ch/lists/00FF/f769aea8e8",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Rudern",
                                language = None
                            ))),
                            position = Some(2),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        )
                    ),
                    name = Some("11"),
                    id = "http://rdfh.ch/lists/00FF/2ccfb4fce7",
                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                        value = "WASSERSPORT",
                        language = None
                    ))),
                    position = Some(15),
                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                ),
                ListNodeADM(
                    children = Vector(
                        ListNodeADM(
                            children = Nil,
                            name = Some("3"),
                            id = "http://rdfh.ch/lists/00FF/06608e0fe3",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Trecking mit Tieren",
                                language = None
                            ))),
                            position = Some(2),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("5"),
                            id = "http://rdfh.ch/lists/00FF/38c73482e3",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Spazieren",
                                language = None
                            ))),
                            position = Some(4),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("2"),
                            id = "http://rdfh.ch/lists/00FF/6d2c3bd6e2",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Bergh\u00FCtten und Restaurants",
                                language = None
                            ))),
                            position = Some(1),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("4"),
                            id = "http://rdfh.ch/lists/00FF/9f93e148e3",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Wandern",
                                language = None
                            ))),
                            position = Some(3),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("1"),
                            id = "http://rdfh.ch/lists/00FF/d4f8e79ce2",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Alpinismus",
                                language = None
                            ))),
                            position = Some(0),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        )
                    ),
                    name = Some("8"),
                    id = "http://rdfh.ch/lists/00FF/3bc59463e2",
                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                        value = "ALPINISMUS",
                        language = None
                    ))),
                    position = Some(12),
                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                ),
                ListNodeADM(
                    children = Vector(
                        ListNodeADM(
                            children = Nil,
                            name = Some("5"),
                            id = "http://rdfh.ch/lists/00FF/095eeef0e1",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Tennis",
                                language = None
                            ))),
                            position = Some(4),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("3"),
                            id = "http://rdfh.ch/lists/00FF/0c5c4ed2e0",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Kegeln",
                                language = None
                            ))),
                            position = Some(2),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("2"),
                            id = "http://rdfh.ch/lists/00FF/7328fb98e0",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Fussball",
                                language = None
                            ))),
                            position = Some(1),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("6"),
                            id = "http://rdfh.ch/lists/00FF/a291412ae2",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Volleyball",
                                language = None
                            ))),
                            position = Some(5),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Vector(
                                ListNodeADM(
                                    children = Nil,
                                    name = Some("1"),
                                    id = "http://rdfh.ch/lists/00FF/3ec3f444e1",
                                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                        value = "Minigolf",
                                        language = None
                                    ))),
                                    position = Some(0),
                                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                                ),
                                ListNodeADM(
                                    children = Nil,
                                    name = Some("3"),
                                    id = "http://rdfh.ch/lists/00FF/702a9bb7e1",
                                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                        value = "Wintergolf",
                                        language = None
                                    ))),
                                    position = Some(2),
                                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                                ),
                                ListNodeADM(
                                    children = Nil,
                                    name = Some("2"),
                                    id = "http://rdfh.ch/lists/00FF/d7f6477ee1",
                                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                        value = "Sommergolf",
                                        language = None
                                    ))),
                                    position = Some(1),
                                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                                )
                            ),
                            name = Some("4"),
                            id = "http://rdfh.ch/lists/00FF/a58fa10be1",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Golf",
                                language = None
                            ))),
                            position = Some(3),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("1"),
                            id = "http://rdfh.ch/lists/00FF/daf4a75fe0",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Billiard",
                                language = None
                            ))),
                            position = Some(0),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        )
                    ),
                    name = Some("7"),
                    id = "http://rdfh.ch/lists/00FF/41c15426e0",
                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                        value = "BALLSPORT",
                        language = None
                    ))),
                    position = Some(11),
                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                ),
                ListNodeADM(
                    children = Vector(
                        ListNodeADM(
                            children = Nil,
                            name = Some("2"),
                            id = "http://rdfh.ch/lists/00FF/8b18fba3d7",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Engadin Skimarathon",
                                language = None
                            ))),
                            position = Some(1),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("1"),
                            id = "http://rdfh.ch/lists/00FF/f2e4a76ad7",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Skilanglauf",
                                language = None
                            ))),
                            position = Some(0),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        )
                    ),
                    name = Some("2-4"),
                    id = "http://rdfh.ch/lists/00FF/59b15431d7",
                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                        value = "SKILANGLAUF UND ENGADIN SKIMARATHON",
                        language = None
                    ))),
                    position = Some(5),
                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                ),
                ListNodeADM(
                    children = Nil,
                    name = Some("2-2"),
                    id = "http://rdfh.ch/lists/00FF/5fad14f4d4",
                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                        value = "SKISCHULE",
                        language = None
                    ))),
                    position = Some(3),
                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                ),
                ListNodeADM(
                    children = Vector(
                        ListNodeADM(
                            children = Vector(
                                ListNodeADM(
                                    children = Nil,
                                    name = Some("2"),
                                    id = "http://rdfh.ch/lists/00FF/32cb74bfe5",
                                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                        value = "Berninarennen",
                                        language = None
                                    ))),
                                    position = Some(1),
                                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                                ),
                                ListNodeADM(
                                    children = Nil,
                                    name = Some("4"),
                                    id = "http://rdfh.ch/lists/00FF/64321b32e6",
                                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                        value = "Personen",
                                        language = None
                                    ))),
                                    position = Some(3),
                                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                                ),
                                ListNodeADM(
                                    children = Nil,
                                    name = Some("1"),
                                    id = "http://rdfh.ch/lists/00FF/99972186e5",
                                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                        value = "Malojarennen",
                                        language = None
                                    ))),
                                    position = Some(0),
                                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                                ),
                                ListNodeADM(
                                    children = Nil,
                                    name = Some("3"),
                                    id = "http://rdfh.ch/lists/00FF/cbfec7f8e5",
                                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                        value = "Shellstrasse",
                                        language = None
                                    ))),
                                    position = Some(2),
                                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                                ),
                                ListNodeADM(
                                    children = Nil,
                                    name = Some("5"),
                                    id = "http://rdfh.ch/lists/00FF/fd656e6be6",
                                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                        value = "Verschiedenes",
                                        language = None
                                    ))),
                                    position = Some(4),
                                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                                )
                            ),
                            name = Some("1"),
                            id = "http://rdfh.ch/lists/00FF/0064ce4ce5",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Autorennen",
                                language = None
                            ))),
                            position = Some(0),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("3"),
                            id = "http://rdfh.ch/lists/00FF/2fcd14dee6",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Sch\u00F6nheitskonkurrenz",
                                language = None
                            ))),
                            position = Some(2),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("5"),
                            id = "http://rdfh.ch/lists/00FF/6134bb50e7",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Montainbiking",
                                language = None
                            ))),
                            position = Some(4),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("7"),
                            id = "http://rdfh.ch/lists/00FF/939b61c3e7",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Motorradfahren",
                                language = None
                            ))),
                            position = Some(6),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("2"),
                            id = "http://rdfh.ch/lists/00FF/9699c1a4e6",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Geschicklichkeitsfahren",
                                language = None
                            ))),
                            position = Some(1),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("4"),
                            id = "http://rdfh.ch/lists/00FF/c8006817e7",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Inline Skating",
                                language = None
                            ))),
                            position = Some(3),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("6"),
                            id = "http://rdfh.ch/lists/00FF/fa670e8ae7",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Radfahren",
                                language = None
                            ))),
                            position = Some(5),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        )
                    ),
                    name = Some("10"),
                    id = "http://rdfh.ch/lists/00FF/67307b13e5",
                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                        value = "RADSPORT",
                        language = None
                    ))),
                    position = Some(14),
                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                ),
                ListNodeADM(
                    children = Vector(
                        ListNodeADM(
                            children = Nil,
                            name = Some("4"),
                            id = "http://rdfh.ch/lists/00FF/1b522e39db",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Eisstadion",
                                language = None
                            ))),
                            position = Some(3),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("1"),
                            id = "http://rdfh.ch/lists/00FF/214eeefbd8",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Eishockey und Bandy",
                                language = None
                            ))),
                            position = Some(0),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("7"),
                            id = "http://rdfh.ch/lists/00FF/7f207b1edc",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Kunsteisbahn Ludains",
                                language = None
                            ))),
                            position = Some(6),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("3"),
                            id = "http://rdfh.ch/lists/00FF/821edbffda",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Eissegeln, -Surfen",
                                language = None
                            ))),
                            position = Some(2),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Vector(ListNodeADM(
                                children = Nil,
                                name = Some("1"),
                                id = "http://rdfh.ch/lists/00FF/4db9d4abdb",
                                labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                    value = "Personen",
                                    language = None
                                ))),
                                position = Some(0),
                                comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                            )),
                            name = Some("5"),
                            id = "http://rdfh.ch/lists/00FF/b4858172db",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Curling",
                                language = None
                            ))),
                            position = Some(4),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Vector(
                                ListNodeADM(
                                    children = Nil,
                                    name = Some("4"),
                                    id = "http://rdfh.ch/lists/00FF/1e508e1ada",
                                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                        value = "Paarlauf",
                                        language = None
                                    ))),
                                    position = Some(3),
                                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                                ),
                                ListNodeADM(
                                    children = Nil,
                                    name = Some("6"),
                                    id = "http://rdfh.ch/lists/00FF/50b7348dda",
                                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                        value = "Kellner auf Eis",
                                        language = None
                                    ))),
                                    position = Some(5),
                                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                                ),
                                ListNodeADM(
                                    children = Nil,
                                    name = Some("1"),
                                    id = "http://rdfh.ch/lists/00FF/53b5946ed9",
                                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                        value = "Gefrorene Seen",
                                        language = None
                                    ))),
                                    position = Some(0),
                                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                                ),
                                ListNodeADM(
                                    children = Nil,
                                    name = Some("3"),
                                    id = "http://rdfh.ch/lists/00FF/851c3be1d9",
                                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                        value = "Eisrevue",
                                        language = None
                                    ))),
                                    position = Some(2),
                                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                                ),
                                ListNodeADM(
                                    children = Nil,
                                    name = Some("5"),
                                    id = "http://rdfh.ch/lists/00FF/b783e153da",
                                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                        value = "Schnellauf",
                                        language = None
                                    ))),
                                    position = Some(4),
                                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                                ),
                                ListNodeADM(
                                    children = Nil,
                                    name = Some("7"),
                                    id = "http://rdfh.ch/lists/00FF/e9ea87c6da",
                                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                        value = "Personen",
                                        language = None
                                    ))),
                                    position = Some(6),
                                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                                ),
                                ListNodeADM(
                                    children = Nil,
                                    name = Some("2"),
                                    id = "http://rdfh.ch/lists/00FF/ece8e7a7d9",
                                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                        value = "Gymkhana",
                                        language = None
                                    ))),
                                    position = Some(1),
                                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                                )
                            ),
                            name = Some("2"),
                            id = "http://rdfh.ch/lists/00FF/ba814135d9",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Eislaufen",
                                language = None
                            ))),
                            position = Some(1),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("6"),
                            id = "http://rdfh.ch/lists/00FF/e6ec27e5db",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Eisstockschiessen",
                                language = None
                            ))),
                            position = Some(5),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        )
                    ),
                    name = Some("4"),
                    id = "http://rdfh.ch/lists/00FF/881a9bc2d8",
                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                        value = "EISSPORT",
                        language = None
                    ))),
                    position = Some(8),
                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                ),
                ListNodeADM(
                    children = Vector(
                        ListNodeADM(
                            children = Nil,
                            name = Some("6"),
                            id = "http://rdfh.ch/lists/00FF/2d466e81d4",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Ski SOS",
                                language = None
                            ))),
                            position = Some(5),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("1"),
                            id = "http://rdfh.ch/lists/00FF/3044ce62d3",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Verschiedenes",
                                language = None
                            ))),
                            position = Some(0),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("3"),
                            id = "http://rdfh.ch/lists/00FF/62ab74d5d3",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Ski Corvatsch",
                                language = None
                            ))),
                            position = Some(2),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("5"),
                            id = "http://rdfh.ch/lists/00FF/94121b48d4",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Ski Kilometer-Lanc\u00E9",
                                language = None
                            ))),
                            position = Some(4),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("7"),
                            id = "http://rdfh.ch/lists/00FF/c679c1bad4",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Skitouren",
                                language = None
                            ))),
                            position = Some(6),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("2"),
                            id = "http://rdfh.ch/lists/00FF/c977219cd3",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Skiakrobatik",
                                language = None
                            ))),
                            position = Some(1),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("4"),
                            id = "http://rdfh.ch/lists/00FF/fbdec70ed4",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Skifahren",
                                language = None
                            ))),
                            position = Some(3),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        )
                    ),
                    name = Some("2"),
                    id = "http://rdfh.ch/lists/00FF/97107b29d3",
                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                        value = "SKI",
                        language = None
                    ))),
                    position = Some(2),
                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                ),
                ListNodeADM(
                    children = Vector(
                        ListNodeADM(
                            children = Nil,
                            name = Some("1"),
                            id = "http://rdfh.ch/lists/00FF/56b3f44fd8",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Olympiade 1928",
                                language = None
                            ))),
                            position = Some(0),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("2"),
                            id = "http://rdfh.ch/lists/00FF/efe64789d8",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Olympiade 1948",
                                language = None
                            ))),
                            position = Some(1),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        )
                    ),
                    name = Some("3"),
                    id = "http://rdfh.ch/lists/00FF/bd7fa116d8",
                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                        value = "OLYMPIADEN",
                        language = None
                    ))),
                    position = Some(7),
                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                ),
                ListNodeADM(
                    children = Vector(
                        ListNodeADM(
                            children = Nil,
                            name = Some("2"),
                            id = "http://rdfh.ch/lists/00FF/03622e2ee4",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Delta",
                                language = None
                            ))),
                            position = Some(1),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("4"),
                            id = "http://rdfh.ch/lists/00FF/35c9d4a0e4",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Helikopter",
                                language = None
                            ))),
                            position = Some(3),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("1"),
                            id = "http://rdfh.ch/lists/00FF/6a2edbf4e3",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Ballon",
                                language = None
                            ))),
                            position = Some(0),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("3"),
                            id = "http://rdfh.ch/lists/00FF/9c958167e4",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Flugzeuge",
                                language = None
                            ))),
                            position = Some(2),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("5"),
                            id = "http://rdfh.ch/lists/00FF/cefc27dae4",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Segelflieger",
                                language = None
                            ))),
                            position = Some(4),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        )
                    ),
                    name = Some("9"),
                    id = "http://rdfh.ch/lists/00FF/d1fa87bbe3",
                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                        value = "FLIEGEN",
                        language = None
                    ))),
                    position = Some(13),
                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                ),
                ListNodeADM(
                    children = Vector(
                        ListNodeADM(
                            children = Nil,
                            name = Some("7"),
                            id = "http://rdfh.ch/lists/00FF/0f5aaeb3df",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Fahrturnier",
                                language = None
                            ))),
                            position = Some(6),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("2"),
                            id = "http://rdfh.ch/lists/00FF/12580e95de",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Pferderennen",
                                language = None
                            ))),
                            position = Some(1),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("4"),
                            id = "http://rdfh.ch/lists/00FF/44bfb407df",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Reiten",
                                language = None
                            ))),
                            position = Some(3),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("6"),
                            id = "http://rdfh.ch/lists/00FF/76265b7adf",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Skikj\u00F6ring",
                                language = None
                            ))),
                            position = Some(5),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("1"),
                            id = "http://rdfh.ch/lists/00FF/7924bb5bde",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Concours Hippique",
                                language = None
                            ))),
                            position = Some(0),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("8"),
                            id = "http://rdfh.ch/lists/00FF/a88d01eddf",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Zuschauer",
                                language = None
                            ))),
                            position = Some(7),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("3"),
                            id = "http://rdfh.ch/lists/00FF/ab8b61cede",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Polo",
                                language = None
                            ))),
                            position = Some(2),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("5"),
                            id = "http://rdfh.ch/lists/00FF/ddf20741df",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Reithalle",
                                language = None
                            ))),
                            position = Some(4),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        )
                    ),
                    name = Some("6"),
                    id = "http://rdfh.ch/lists/00FF/e0f06722de",
                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                        value = "PFERDESPORT",
                        language = None
                    ))),
                    position = Some(10),
                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                ),
                ListNodeADM(
                    children = Vector(
                        ListNodeADM(
                            children = Nil,
                            name = Some("7"),
                            id = "http://rdfh.ch/lists/00FF/274aaebed6",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Ski Weltmeisterschaft 2003",
                                language = None
                            ))),
                            position = Some(6),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("2"),
                            id = "http://rdfh.ch/lists/00FF/2a480ea0d5",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Ski Rennpisten",
                                language = None
                            ))),
                            position = Some(1),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("4"),
                            id = "http://rdfh.ch/lists/00FF/5cafb412d6",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Guardia Grischa",
                                language = None
                            ))),
                            position = Some(3),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("6"),
                            id = "http://rdfh.ch/lists/00FF/8e165b85d6",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Ski Weltmeisterschaft 1974",
                                language = None
                            ))),
                            position = Some(5),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("1"),
                            id = "http://rdfh.ch/lists/00FF/9114bb66d5",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Skirennen",
                                language = None
                            ))),
                            position = Some(0),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("8"),
                            id = "http://rdfh.ch/lists/00FF/c07d01f8d6",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Skispringen",
                                language = None
                            ))),
                            position = Some(7),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("3"),
                            id = "http://rdfh.ch/lists/00FF/c37b61d9d5",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Personen",
                                language = None
                            ))),
                            position = Some(2),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("5"),
                            id = "http://rdfh.ch/lists/00FF/f5e2074cd6",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Ski Vorweltmeisterschaft 1973",
                                language = None
                            ))),
                            position = Some(4),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        )
                    ),
                    name = Some("2-3"),
                    id = "http://rdfh.ch/lists/00FF/f8e0672dd5",
                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                        value = "SKIRENNEN UND SKISPRINGEN",
                        language = None
                    ))),
                    position = Some(4),
                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                )
            ),
            name = Some("9SPO"),
            id = "http://rdfh.ch/lists/00FF/71a1543cce",
            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                value = "SPORT",
                language = None
            ))),
            position = Some(8),
            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
        ),
        ListNodeADM(
            children = Vector(
                ListNodeADM(
                    children = Vector(
                        ListNodeADM(
                            children = Nil,
                            name = Some("2"),
                            id = "http://rdfh.ch/lists/00FF/4c421b27ef",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Essen",
                                language = None
                            ))),
                            position = Some(1),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Vector(
                                ListNodeADM(
                                    children = Nil,
                                    name = Some("hotel_v"),
                                    id = "http://rdfh.ch/lists/00FF/24b01c2abd01",
                                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                        value = "Hotel V",
                                        language = None
                                    ))),
                                    position = Some(21),
                                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                                ),
                                ListNodeADM(
                                    children = Nil,
                                    name = Some("hotel_q"),
                                    id = "http://rdfh.ch/lists/00FF/27ae7c0bbc01",
                                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                        value = "Hotel Q",
                                        language = None
                                    ))),
                                    position = Some(16),
                                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                                ),
                                ListNodeADM(
                                    children = Nil,
                                    name = Some("hotel_l"),
                                    id = "http://rdfh.ch/lists/00FF/2aacdcecba01",
                                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                        value = "Hotel L",
                                        language = None
                                    ))),
                                    position = Some(11),
                                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                                ),
                                ListNodeADM(
                                    children = Nil,
                                    name = Some("hotel_g"),
                                    id = "http://rdfh.ch/lists/00FF/2daa3cceb901",
                                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                        value = "Hotel G",
                                        language = None
                                    ))),
                                    position = Some(6),
                                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                                ),
                                ListNodeADM(
                                    children = Nil,
                                    name = Some("hotel_b"),
                                    id = "http://rdfh.ch/lists/00FF/30a89cafb801",
                                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                        value = "Hotel B",
                                        language = None
                                    ))),
                                    position = Some(1),
                                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                                ),
                                ListNodeADM(
                                    children = Nil,
                                    name = Some("hotel_y"),
                                    id = "http://rdfh.ch/lists/00FF/5617c39cbd01",
                                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                        value = "Hotel Y",
                                        language = None
                                    ))),
                                    position = Some(24),
                                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                                ),
                                ListNodeADM(
                                    children = Nil,
                                    name = Some("hotel_s"),
                                    id = "http://rdfh.ch/lists/00FF/5915237ebc01",
                                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                        value = "Hotel S",
                                        language = None
                                    ))),
                                    position = Some(18),
                                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                                ),
                                ListNodeADM(
                                    children = Nil,
                                    name = Some("hotel_n"),
                                    id = "http://rdfh.ch/lists/00FF/5c13835fbb01",
                                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                        value = "Hotel N",
                                        language = None
                                    ))),
                                    position = Some(13),
                                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                                ),
                                ListNodeADM(
                                    children = Nil,
                                    name = Some("hotel_i"),
                                    id = "http://rdfh.ch/lists/00FF/5f11e340ba01",
                                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                        value = "Hotel I",
                                        language = None
                                    ))),
                                    position = Some(8),
                                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                                ),
                                ListNodeADM(
                                    children = Nil,
                                    name = Some("hotel_d"),
                                    id = "http://rdfh.ch/lists/00FF/620f4322b901",
                                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                        value = "Hotel D",
                                        language = None
                                    ))),
                                    position = Some(3),
                                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                                ),
                                ListNodeADM(
                                    children = Nil,
                                    name = Some("hotel_u"),
                                    id = "http://rdfh.ch/lists/00FF/8b7cc9f0bc01",
                                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                        value = "Hotel U",
                                        language = None
                                    ))),
                                    position = Some(20),
                                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                                ),
                                ListNodeADM(
                                    children = Nil,
                                    name = Some("hotel_p"),
                                    id = "http://rdfh.ch/lists/00FF/8e7a29d2bb01",
                                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                        value = "Hotel P",
                                        language = None
                                    ))),
                                    position = Some(15),
                                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                                ),
                                ListNodeADM(
                                    children = Nil,
                                    name = Some("hotel_k"),
                                    id = "http://rdfh.ch/lists/00FF/917889b3ba01",
                                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                        value = "Hotel K",
                                        language = None
                                    ))),
                                    position = Some(10),
                                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                                ),
                                ListNodeADM(
                                    children = Nil,
                                    name = Some("hotel_f"),
                                    id = "http://rdfh.ch/lists/00FF/9476e994b901",
                                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                        value = "Hotel F",
                                        language = None
                                    ))),
                                    position = Some(5),
                                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                                ),
                                ListNodeADM(
                                    children = Nil,
                                    name = Some("hotel_a"),
                                    id = "http://rdfh.ch/lists/00FF/97744976b801",
                                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                        value = "Hotel A",
                                        language = None
                                    ))),
                                    position = Some(0),
                                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                                ),
                                ListNodeADM(
                                    children = Nil,
                                    name = Some("hotel_w"),
                                    id = "http://rdfh.ch/lists/00FF/9f29173c3b02",
                                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                        value = "Hotel W",
                                        language = None
                                    ))),
                                    position = Some(22),
                                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                                ),
                                ListNodeADM(
                                    children = Nil,
                                    name = Some("hotel_x"),
                                    id = "http://rdfh.ch/lists/00FF/bde36f63bd01",
                                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                        value = "Hotel X",
                                        language = None
                                    ))),
                                    position = Some(23),
                                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                                ),
                                ListNodeADM(
                                    children = Nil,
                                    name = Some("hotel_r"),
                                    id = "http://rdfh.ch/lists/00FF/c0e1cf44bc01",
                                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                        value = "Hotel R",
                                        language = None
                                    ))),
                                    position = Some(17),
                                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                                ),
                                ListNodeADM(
                                    children = Nil,
                                    name = Some("hotel_m"),
                                    id = "http://rdfh.ch/lists/00FF/c3df2f26bb01",
                                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                        value = "Hotel M",
                                        language = None
                                    ))),
                                    position = Some(12),
                                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                                ),
                                ListNodeADM(
                                    children = Nil,
                                    name = Some("hotel_h"),
                                    id = "http://rdfh.ch/lists/00FF/c6dd8f07ba01",
                                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                        value = "Hotel H",
                                        language = None
                                    ))),
                                    position = Some(7),
                                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                                ),
                                ListNodeADM(
                                    children = Nil,
                                    name = Some("hotel_c"),
                                    id = "http://rdfh.ch/lists/00FF/c9dbefe8b801",
                                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                        value = "Hotel C",
                                        language = None
                                    ))),
                                    position = Some(2),
                                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                                ),
                                ListNodeADM(
                                    children = Nil,
                                    name = Some("hotel_z"),
                                    id = "http://rdfh.ch/lists/00FF/ef4a16d6bd01",
                                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                        value = "Hotel Z",
                                        language = None
                                    ))),
                                    position = Some(25),
                                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                                ),
                                ListNodeADM(
                                    children = Nil,
                                    name = Some("hotel_t"),
                                    id = "http://rdfh.ch/lists/00FF/f24876b7bc01",
                                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                        value = "Hotel T",
                                        language = None
                                    ))),
                                    position = Some(19),
                                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                                ),
                                ListNodeADM(
                                    children = Nil,
                                    name = Some("hotel_o"),
                                    id = "http://rdfh.ch/lists/00FF/f546d698bb01",
                                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                        value = "Hotel O",
                                        language = None
                                    ))),
                                    position = Some(14),
                                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                                ),
                                ListNodeADM(
                                    children = Nil,
                                    name = Some("hotel_j"),
                                    id = "http://rdfh.ch/lists/00FF/f844367aba01",
                                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                        value = "Hotel J",
                                        language = None
                                    ))),
                                    position = Some(9),
                                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                                ),
                                ListNodeADM(
                                    children = Nil,
                                    name = Some("hotel_e"),
                                    id = "http://rdfh.ch/lists/00FF/fb42965bb901",
                                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                        value = "Hotel E",
                                        language = None
                                    ))),
                                    position = Some(4),
                                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                                )
                            ),
                            name = Some("1"),
                            id = "http://rdfh.ch/lists/00FF/b30ec8edee",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Hotels und Restaurants A-Z",
                                language = None
                            ))),
                            position = Some(0),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("3"),
                            id = "http://rdfh.ch/lists/00FF/e5756e60ef",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Men\u00FCkarten",
                                language = None
                            ))),
                            position = Some(2),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        )
                    ),
                    name = Some("3"),
                    id = "http://rdfh.ch/lists/00FF/1adb74b4ee",
                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                        value = "HOTELLERIE",
                        language = None
                    ))),
                    position = Some(3),
                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                ),
                ListNodeADM(
                    children = Vector(
                        ListNodeADM(
                            children = Nil,
                            name = Some("10"),
                            id = "http://rdfh.ch/lists/00FF/20d73477ec",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Standseilbahnen und Stationen",
                                language = None
                            ))),
                            position = Some(9),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("5"),
                            id = "http://rdfh.ch/lists/00FF/23d59458eb",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Kutschen und Pferdetransporte",
                                language = None
                            ))),
                            position = Some(4),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("12"),
                            id = "http://rdfh.ch/lists/00FF/523edbe9ec",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Tram",
                                language = None
                            ))),
                            position = Some(11),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("7"),
                            id = "http://rdfh.ch/lists/00FF/553c3bcbeb",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Schneer\u00E4umungs- und Pistenfahrzeuge",
                                language = None
                            ))),
                            position = Some(6),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("2"),
                            id = "http://rdfh.ch/lists/00FF/583a9bacea",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Boote",
                                language = None
                            ))),
                            position = Some(1),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("9"),
                            id = "http://rdfh.ch/lists/00FF/87a3e13dec",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Skilifte",
                                language = None
                            ))),
                            position = Some(8),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("4"),
                            id = "http://rdfh.ch/lists/00FF/8aa1411feb",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Kommunikation",
                                language = None
                            ))),
                            position = Some(3),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("11"),
                            id = "http://rdfh.ch/lists/00FF/b90a88b0ec",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Strassen und P\u00E4sse",
                                language = None
                            ))),
                            position = Some(10),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("6"),
                            id = "http://rdfh.ch/lists/00FF/bc08e891eb",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Luftseilbahnen und Stationen",
                                language = None
                            ))),
                            position = Some(5),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("1"),
                            id = "http://rdfh.ch/lists/00FF/bf064873ea",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Autos, Busse und Postautos",
                                language = None
                            ))),
                            position = Some(0),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("13"),
                            id = "http://rdfh.ch/lists/00FF/eb712e23ed",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Wegweiser",
                                language = None
                            ))),
                            position = Some(12),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("8"),
                            id = "http://rdfh.ch/lists/00FF/ee6f8e04ec",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Schneekanonen",
                                language = None
                            ))),
                            position = Some(7),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("3"),
                            id = "http://rdfh.ch/lists/00FF/f16deee5ea",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Flugplatz Samedan",
                                language = None
                            ))),
                            position = Some(2),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        )
                    ),
                    name = Some("1"),
                    id = "http://rdfh.ch/lists/00FF/26d3f439ea",
                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                        value = "VERKEHR",
                        language = None
                    ))),
                    position = Some(0),
                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                ),
                ListNodeADM(
                    children = Nil,
                    name = Some("9"),
                    id = "http://rdfh.ch/lists/00FF/4348fb82f2",
                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                        value = "WALDWIRTSCHAFT",
                        language = None
                    ))),
                    position = Some(8),
                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                ),
                ListNodeADM(
                    children = Vector(
                        ListNodeADM(
                            children = Nil,
                            name = Some("3"),
                            id = "http://rdfh.ch/lists/00FF/11e15410f2",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Solarenergie",
                                language = None
                            ))),
                            position = Some(2),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("2"),
                            id = "http://rdfh.ch/lists/00FF/78ad01d7f1",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Wasserkraft",
                                language = None
                            ))),
                            position = Some(1),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("1"),
                            id = "http://rdfh.ch/lists/00FF/df79ae9df1",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Elektrizit\u00E4t",
                                language = None
                            ))),
                            position = Some(0),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        )
                    ),
                    name = Some("7"),
                    id = "http://rdfh.ch/lists/00FF/46465b64f1",
                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                        value = "ENERGIEWIRTSCHAFT",
                        language = None
                    ))),
                    position = Some(6),
                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                ),
                ListNodeADM(
                    children = Vector(
                        ListNodeADM(
                            children = Nil,
                            name = Some("1"),
                            id = "http://rdfh.ch/lists/00FF/17dd14d3ef",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Personal und B\u00FCro",
                                language = None
                            ))),
                            position = Some(0),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("3"),
                            id = "http://rdfh.ch/lists/00FF/4944bb45f0",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Markenzeichen St. Moritz",
                                language = None
                            ))),
                            position = Some(2),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("2"),
                            id = "http://rdfh.ch/lists/00FF/b010680cf0",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Anl\u00E4sse und Reisen",
                                language = None
                            ))),
                            position = Some(1),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        )
                    ),
                    name = Some("4"),
                    id = "http://rdfh.ch/lists/00FF/7ea9c199ef",
                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                        value = "KURVEREIN",
                        language = None
                    ))),
                    position = Some(4),
                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                ),
                ListNodeADM(
                    children = Vector(ListNodeADM(
                        children = Nil,
                        name = Some("1"),
                        id = "http://rdfh.ch/lists/00FF/1dd9d495ed",
                        labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                            value = "Eisenbahnen und Bahnh\u00F6fe",
                            language = None
                        ))),
                        position = Some(0),
                        comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                    )),
                    name = Some("1-1"),
                    id = "http://rdfh.ch/lists/00FF/84a5815ced",
                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                        value = "EISENBAHNEN",
                        language = None
                    ))),
                    position = Some(1),
                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                ),
                ListNodeADM(
                    children = Nil,
                    name = Some("8"),
                    id = "http://rdfh.ch/lists/00FF/aa14a849f2",
                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                        value = "AGRARWIRTSCHAFT",
                        language = None
                    ))),
                    position = Some(7),
                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                ),
                ListNodeADM(
                    children = Vector(
                        ListNodeADM(
                            children = Nil,
                            name = Some("1"),
                            id = "http://rdfh.ch/lists/00FF/4f407b08ee",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Casino",
                                language = None
                            ))),
                            position = Some(0),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("3"),
                            id = "http://rdfh.ch/lists/00FF/81a7217bee",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Mode",
                                language = None
                            ))),
                            position = Some(2),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("2"),
                            id = "http://rdfh.ch/lists/00FF/e873ce41ee",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "G\u00E4ste",
                                language = None
                            ))),
                            position = Some(1),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        )
                    ),
                    name = Some("2"),
                    id = "http://rdfh.ch/lists/00FF/b60c28cfed",
                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                        value = "FREMDENVERKEHR",
                        language = None
                    ))),
                    position = Some(2),
                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                ),
                ListNodeADM(
                    children = Vector(
                        ListNodeADM(
                            children = Nil,
                            name = Some("2"),
                            id = "http://rdfh.ch/lists/00FF/14dfb4f1f0",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Reklame",
                                language = None
                            ))),
                            position = Some(1),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("1"),
                            id = "http://rdfh.ch/lists/00FF/7bab61b8f0",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Arbeitswelt",
                                language = None
                            ))),
                            position = Some(0),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        ),
                        ListNodeADM(
                            children = Nil,
                            name = Some("3"),
                            id = "http://rdfh.ch/lists/00FF/ad12082bf1",
                            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                                value = "Bauwesen",
                                language = None
                            ))),
                            position = Some(2),
                            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                        )
                    ),
                    name = Some("6"),
                    id = "http://rdfh.ch/lists/00FF/e2770e7ff0",
                    labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                        value = "GEWERBE",
                        language = None
                    ))),
                    position = Some(5),
                    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
                )
            ),
            name = Some("10WIR"),
            id = "http://rdfh.ch/lists/00FF/8d9fa100ea",
            labels = StringLiteralSequenceV2(stringLiterals = Vector(StringLiteralV2(
                value = "WIRTSCHAFT",
                language = None
            ))),
            position = Some(9),
            comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
        )
    )
}
