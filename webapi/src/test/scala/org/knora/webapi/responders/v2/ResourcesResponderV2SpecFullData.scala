package org.knora.webapi.responders.v2

import akka.actor.ActorSystem
import org.knora.webapi.Settings
import org.knora.webapi.messages.v1.responder.valuemessages.{KnoraCalendarV1, KnoraPrecisionV1}
import org.knora.webapi.messages.v2.responder._

object ResourcesResponderV2SpecFullData {

    implicit lazy val system = ActorSystem("webapi")

    val settings = Settings(system)

    val expectedReadResourceV2ForZeitgloecklein = ReadResourceV2(
        resourceInfos = Map(),
        values = Map(
            "http://www.knora.org/ontology/0803/incunabula#hasAuthor" -> Vector(ReadValueV2(
                valueContent = TextValueContentV2(
                    comment = None,
                    standoff = None,
                    valueHasString = "Berthold, der Bruder"
                ),
                valueIri = "http://rdfh.ch/c5058f3a/values/8653a672"
            )),
            "http://www.knora.org/ontology/0803/incunabula#title" -> Vector(ReadValueV2(
                valueContent = TextValueContentV2(
                    comment = None,
                    standoff = None,
                    valueHasString = "Zeitgl\u00F6cklein des Lebens und Leidens Christi"
                ),
                valueIri = "http://rdfh.ch/c5058f3a/values/c3295339"
            )),
            "http://www.knora.org/ontology/0803/incunabula#citation" -> Vector(
                ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Schramm Bd. XXI, S. 27"
                    ),
                    valueIri = "http://rdfh.ch/c5058f3a/values/184e99ca01"
                ),
                ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "GW 4168"
                    ),
                    valueIri = "http://rdfh.ch/c5058f3a/values/db77ec0302"
                ),
                ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "ISTC ib00512000"
                    ),
                    valueIri = "http://rdfh.ch/c5058f3a/values/9ea13f3d02"
                )
            ),
            "http://www.knora.org/ontology/0803/incunabula#location" -> Vector(ReadValueV2(
                valueContent = TextValueContentV2(
                    comment = None,
                    standoff = None,
                    valueHasString = "Universit\u00E4ts- und Stadtbibliothek K\u00F6ln, Sign: AD+S167"
                ),
                valueIri = "http://rdfh.ch/c5058f3a/values/92faf25701"
            )),
            "http://www.knora.org/ontology/0803/incunabula#url" -> Vector(ReadValueV2(
                valueContent = TextValueContentV2(
                    comment = None,
                    standoff = None,
                    valueHasString = "http://www.ub.uni-koeln.de/cdm/compoundobject/collection/inkunabeln/id/1878/rec/1"
                ),
                valueIri = "http://rdfh.ch/c5058f3a/values/10e00c7acc2704"
            )),
            "http://www.knora.org/ontology/0803/incunabula#physical_desc" -> Vector(ReadValueV2(
                valueContent = TextValueContentV2(
                    comment = None,
                    standoff = None,
                    valueHasString = "Dimension: 8\u00B0"
                ),
                valueIri = "http://rdfh.ch/c5058f3a/values/5524469101"
            )),
            "http://www.knora.org/ontology/0803/incunabula#publoc" -> Vector(ReadValueV2(
                valueContent = TextValueContentV2(
                    comment = None,
                    standoff = None,
                    valueHasString = "Basel"
                ),
                valueIri = "http://rdfh.ch/c5058f3a/values/0ca74ce5"
            )),
            "http://www.knora.org/ontology/0803/incunabula#pubdate" -> Vector(ReadValueV2(
                valueContent = DateValueContentV2(
                    comment = None,
                    valueHasCalendar = KnoraCalendarV1.JULIAN,
                    valueHasEndPrecision = KnoraPrecisionV1.YEAR,
                    valueHasStartPrecision = KnoraPrecisionV1.YEAR,
                    valueHasEndJDN = 2266376,
                    valueHasStartJDN = 2266011,
                    valueHasString = "1492-01-01 - 1492-12-31"
                ),
                valueIri = "http://rdfh.ch/c5058f3a/values/cfd09f1e01"
            )),
            "http://www.knora.org/ontology/0803/incunabula#publisher" -> Vector(ReadValueV2(
                valueContent = TextValueContentV2(
                    comment = None,
                    standoff = None,
                    valueHasString = "Johann Amerbach"
                ),
                valueIri = "http://rdfh.ch/c5058f3a/values/497df9ab"
            ))
        ),
        resourceClass = "http://www.knora.org/ontology/0803/incunabula#book",
        label = "Zeitgl\u00F6cklein des Lebens und Leidens Christi",
        resourceIri = "http://rdfh.ch/c5058f3a"
    )

    val expectedReadResourceV2ForReiseInsHeiligeland = ReadResourceV2(
        resourceInfos = Map(),
        values = Map(
            "http://www.knora.org/ontology/0803/incunabula#hasAuthor" -> Vector(ReadValueV2(
                valueContent = TextValueContentV2(
                    comment = None,
                    standoff = None,
                    valueHasString = "Jean Mandeville"
                ),
                valueIri = "http://rdfh.ch/2a6221216701/values/1a7f08829105"
            )),
            "http://www.knora.org/ontology/0803/incunabula#title" -> Vector(
                ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Reise ins Heilige Land"
                    ),
                    valueIri = "http://rdfh.ch/2a6221216701/values/d1010fd69005"
                ),
                ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Reysen und wanderschafften durch das Gelobte Land"
                    ),
                    valueIri = "http://rdfh.ch/2a6221216701/values/942b620f9105"
                ),
                ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Itinerarius"
                    ),
                    valueIri = "http://rdfh.ch/2a6221216701/values/5755b5489105"
                )
            ),
            "http://www.knora.org/ontology/0803/incunabula#citation" -> Vector(
                ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Van der Haegen I: 9,14"
                    ),
                    valueIri = "http://rdfh.ch/2a6221216701/values/7b4a9bf89305"
                ),
                ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Goff M165"
                    ),
                    valueIri = "http://rdfh.ch/2a6221216701/values/3e74ee319405"
                ),
                ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "C 3833"
                    ),
                    valueIri = "http://rdfh.ch/2a6221216701/values/019e416b9405"
                ),
                ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Klebs 651.2"
                    ),
                    valueIri = "http://rdfh.ch/2a6221216701/values/c4c794a49405"
                ),
                ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Schr 4799"
                    ),
                    valueIri = "http://rdfh.ch/2a6221216701/values/87f1e7dd9405"
                ),
                ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Schramm XXI p. 9 & 26"
                    ),
                    valueIri = "http://rdfh.ch/2a6221216701/values/4a1b3b179505"
                ),
                ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "FairMur(G) 283"
                    ),
                    valueIri = "http://rdfh.ch/2a6221216701/values/0d458e509505"
                ),
                ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "IBP 3556"
                    ),
                    valueIri = "http://rdfh.ch/2a6221216701/values/d06ee1899505"
                ),
                ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Borm 1751"
                    ),
                    valueIri = "http://rdfh.ch/2a6221216701/values/939834c39505"
                )
            ),
            "http://www.knora.org/ontology/0803/incunabula#location" -> Vector(ReadValueV2(
                valueContent = TextValueContentV2(
                    comment = None,
                    standoff = None,
                    valueHasString = "Basel UB, Sign: Aleph D III 13:1"
                ),
                valueIri = "http://rdfh.ch/2a6221216701/values/262655679205"
            )),
            "http://www.knora.org/ontology/0803/incunabula#url" -> Vector(ReadValueV2(
                valueContent = TextValueContentV2(
                    comment = None,
                    standoff = None,
                    valueHasString = "http://aleph.unibas.ch/F/?local_base=DSV01&con_lng=GER&func=find-b&find_code=SYS&request=002610320"
                ),
                valueIri = "http://rdfh.ch/2a6221216701/values/f89173afca2704"
            )),
            "http://www.knora.org/ontology/0803/incunabula#physical_desc" -> Vector(ReadValueV2(
                valueContent = TextValueContentV2(
                    comment = None,
                    standoff = None,
                    valueHasString = "Extent: 1 Bd.; Dimensions: f\u00B0"
                ),
                valueIri = "http://rdfh.ch/2a6221216701/values/e94fa8a09205"
            )),
            "http://www.knora.org/ontology/0803/incunabula#book_comment" -> Vector(ReadValueV2(
                valueContent = TextValueContentV2(
                    comment = None,
                    standoff = None,
                    valueHasString = "Katalogaufnahme anhand ISTC und v.d.Haegen"
                ),
                valueIri = "http://rdfh.ch/2a6221216701/values/56c287fc9505"
            )),
            "http://www.knora.org/ontology/0803/incunabula#publoc" -> Vector(ReadValueV2(
                valueContent = TextValueContentV2(
                    comment = None,
                    standoff = None,
                    valueHasString = "Basel"
                ),
                valueIri = "http://rdfh.ch/2a6221216701/values/a0d2aef49105"
            )),
            "http://www.knora.org/ontology/0803/incunabula#pubdate" -> Vector(ReadValueV2(
                valueContent = DateValueContentV2(
                    comment = None,
                    valueHasCalendar = KnoraCalendarV1.JULIAN,
                    valueHasEndPrecision = KnoraPrecisionV1.YEAR,
                    valueHasStartPrecision = KnoraPrecisionV1.YEAR,
                    valueHasEndJDN = 2262358,
                    valueHasStartJDN = 2261994,
                    valueHasString = "1481-01-01 - 1481-12-31"
                ),
                valueIri = "http://rdfh.ch/2a6221216701/values/63fc012e9205"
            )),
            "http://www.knora.org/ontology/0803/incunabula#publisher" -> Vector(ReadValueV2(
                valueContent = TextValueContentV2(
                    comment = None,
                    standoff = None,
                    valueHasString = "Bernhard Richel"
                ),
                valueIri = "http://rdfh.ch/2a6221216701/values/dda85bbb9105"
            )),
            "http://www.knora.org/ontology/0803/incunabula#note" -> Vector(
                ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "deutsch von Otto von Diemeringen"
                    ),
                    valueIri = "http://rdfh.ch/2a6221216701/values/ac79fbd99205"
                ),
                ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Rubr. mit Init. J zu Beginn"
                    ),
                    valueIri = "http://rdfh.ch/2a6221216701/values/6fa34e139305"
                ),
                ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Holzschnitte nicht koloriert"
                    ),
                    valueIri = "http://rdfh.ch/2a6221216701/values/32cda14c9305"
                ),
                ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Besitzervermerke: Kartause, H. Zscheckenb\u00FCrlin"
                    ),
                    valueIri = "http://rdfh.ch/2a6221216701/values/f5f6f4859305"
                ),
                ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Zusammengebunden mit: Die zehen Gebote ; Was und wie man beten soll und Auslegung des hlg. Pater nosters / Hans von Warmont. Strassburg, 1516"
                    ),
                    valueIri = "http://rdfh.ch/2a6221216701/values/b82048bf9305"
                )
            )
        ),
        resourceClass = "http://www.knora.org/ontology/0803/incunabula#book",
        label = "Reise ins Heilige Land",
        resourceIri = "http://rdfh.ch/2a6221216701"
    )

    val expectedFullResourceResponseForZeitgloecklein = ReadResourcesSequenceV2(
        resources = Vector(expectedReadResourceV2ForZeitgloecklein),
        numberOfResources = 1
    )

    val expectedFullResourceResponseForReise = ReadResourcesSequenceV2(
        resources = Vector(expectedReadResourceV2ForReiseInsHeiligeland),
        numberOfResources = 1
    )

    val expectedFullResourceResponseForZeitgloeckleinAndReise = ReadResourcesSequenceV2(
        resources = Vector(expectedReadResourceV2ForZeitgloecklein, expectedReadResourceV2ForReiseInsHeiligeland),
        numberOfResources = 2
    )

    val expectedFullResourceResponseForReiseInversedAndZeitgloeckleinInversedOrder = ReadResourcesSequenceV2(
        resources = Vector(expectedReadResourceV2ForReiseInsHeiligeland, expectedReadResourceV2ForZeitgloecklein),
        numberOfResources = 2
    )

}