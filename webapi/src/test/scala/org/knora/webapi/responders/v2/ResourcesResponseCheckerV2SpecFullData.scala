package org.knora.webapi.responders.v2

import org.knora.webapi.messages.v1.responder.valuemessages.{KnoraCalendarV1, KnoraPrecisionV1}
import org.knora.webapi.messages.v2.responder.resourcemessages._
import org.knora.webapi.messages.v2.responder.valuemessages._
import org.knora.webapi.util.IriConversions._
import org.knora.webapi.util.StringFormatter

class ResourcesResponseCheckerV2SpecFullData(implicit stringFormatter: StringFormatter) {

    // one title is missing
    val expectedReadResourceV2ForReiseInsHeiligelandWrong = ReadResourceV2(
        label = "Reise ins Heilige Land",
        resourceIri = "http://rdfh.ch/2a6221216701",
        permissions = "CR knora-base:Creator|M knora-base:ProjectMember|V knora-base:KnownUser|RV knora-base:UnknownUser",
        attachedToUser = "http://rdfh.ch/users/91e19f1e01",
        resourceClass = "http://www.knora.org/ontology/0803/incunabula#book".toSmartIri,
        attachedToProject = "http://rdfh.ch/projects/0803",
        values = Map(
            "http://www.knora.org/ontology/0803/incunabula#physical_desc".toSmartIri -> Vector(ReadValueV2(
                valueContent = TextValueContentV2(
                    standoffAndMapping = None,
                    valueType = "http://www.knora.org/ontology/knora-base#TextValue".toSmartIri,
                    valueHasLanguage = None,
                    comment = None,
                    valueHasString = "Extent: 1 Bd.; Dimensions: f\u00B0"
                ),
                valueIri = "http://rdfh.ch/2a6221216701/values/e94fa8a09205",
                permissions = "CR knora-base:Creator|M knora-base:ProjectMember|V knora-base:KnownUser,knora-base:UnknownUser",
                attachedToUser = "http://rdfh.ch/users/91e19f1e01",
                attachedToProject = "http://rdfh.ch/projects/0803"
            )),
            "http://www.knora.org/ontology/0803/incunabula#citation".toSmartIri -> Vector(
                ReadValueV2(
                    valueContent = TextValueContentV2(
                        standoffAndMapping = None,
                        valueType = "http://www.knora.org/ontology/knora-base#TextValue".toSmartIri,
                        valueHasLanguage = None,
                        comment = None,
                        valueHasString = "Van der Haegen I: 9,14"
                    ),
                    valueIri = "http://rdfh.ch/2a6221216701/values/7b4a9bf89305",
                    permissions = "CR knora-base:Creator|M knora-base:ProjectMember|V knora-base:KnownUser,knora-base:UnknownUser",
                    attachedToUser = "http://rdfh.ch/users/91e19f1e01",
                    attachedToProject = "http://rdfh.ch/projects/0803"
                ),
                ReadValueV2(
                    valueContent = TextValueContentV2(
                        standoffAndMapping = None,
                        valueType = "http://www.knora.org/ontology/knora-base#TextValue".toSmartIri,
                        valueHasLanguage = None,
                        comment = None,
                        valueHasString = "Goff M165"
                    ),
                    valueIri = "http://rdfh.ch/2a6221216701/values/3e74ee319405",
                    permissions = "CR knora-base:Creator|M knora-base:ProjectMember|V knora-base:KnownUser,knora-base:UnknownUser",
                    attachedToUser = "http://rdfh.ch/users/91e19f1e01",
                    attachedToProject = "http://rdfh.ch/projects/0803"
                ),
                ReadValueV2(
                    valueContent = TextValueContentV2(
                        standoffAndMapping = None,
                        valueType = "http://www.knora.org/ontology/knora-base#TextValue".toSmartIri,
                        valueHasLanguage = None,
                        comment = None,
                        valueHasString = "C 3833"
                    ),
                    valueIri = "http://rdfh.ch/2a6221216701/values/019e416b9405",
                    permissions = "CR knora-base:Creator|M knora-base:ProjectMember|V knora-base:KnownUser,knora-base:UnknownUser",
                    attachedToUser = "http://rdfh.ch/users/91e19f1e01",
                    attachedToProject = "http://rdfh.ch/projects/0803"
                ),
                ReadValueV2(
                    valueContent = TextValueContentV2(
                        standoffAndMapping = None,
                        valueType = "http://www.knora.org/ontology/knora-base#TextValue".toSmartIri,
                        valueHasLanguage = None,
                        comment = None,
                        valueHasString = "Klebs 651.2"
                    ),
                    valueIri = "http://rdfh.ch/2a6221216701/values/c4c794a49405",
                    permissions = "CR knora-base:Creator|M knora-base:ProjectMember|V knora-base:KnownUser,knora-base:UnknownUser",
                    attachedToUser = "http://rdfh.ch/users/91e19f1e01",
                    attachedToProject = "http://rdfh.ch/projects/0803"
                ),
                ReadValueV2(
                    valueContent = TextValueContentV2(
                        standoffAndMapping = None,
                        valueType = "http://www.knora.org/ontology/knora-base#TextValue".toSmartIri,
                        valueHasLanguage = None,
                        comment = None,
                        valueHasString = "Schr 4799"
                    ),
                    valueIri = "http://rdfh.ch/2a6221216701/values/87f1e7dd9405",
                    permissions = "CR knora-base:Creator|M knora-base:ProjectMember|V knora-base:KnownUser,knora-base:UnknownUser",
                    attachedToUser = "http://rdfh.ch/users/91e19f1e01",
                    attachedToProject = "http://rdfh.ch/projects/0803"
                ),
                ReadValueV2(
                    valueContent = TextValueContentV2(
                        standoffAndMapping = None,
                        valueType = "http://www.knora.org/ontology/knora-base#TextValue".toSmartIri,
                        valueHasLanguage = None,
                        comment = None,
                        valueHasString = "Schramm XXI p. 9 & 26"
                    ),
                    valueIri = "http://rdfh.ch/2a6221216701/values/4a1b3b179505",
                    permissions = "CR knora-base:Creator|M knora-base:ProjectMember|V knora-base:KnownUser,knora-base:UnknownUser",
                    attachedToUser = "http://rdfh.ch/users/91e19f1e01",
                    attachedToProject = "http://rdfh.ch/projects/0803"
                ),
                ReadValueV2(
                    valueContent = TextValueContentV2(
                        standoffAndMapping = None,
                        valueType = "http://www.knora.org/ontology/knora-base#TextValue".toSmartIri,
                        valueHasLanguage = None,
                        comment = None,
                        valueHasString = "FairMur(G) 283"
                    ),
                    valueIri = "http://rdfh.ch/2a6221216701/values/0d458e509505",
                    permissions = "CR knora-base:Creator|M knora-base:ProjectMember|V knora-base:KnownUser,knora-base:UnknownUser",
                    attachedToUser = "http://rdfh.ch/users/91e19f1e01",
                    attachedToProject = "http://rdfh.ch/projects/0803"
                ),
                ReadValueV2(
                    valueContent = TextValueContentV2(
                        standoffAndMapping = None,
                        valueType = "http://www.knora.org/ontology/knora-base#TextValue".toSmartIri,
                        valueHasLanguage = None,
                        comment = None,
                        valueHasString = "IBP 3556"
                    ),
                    valueIri = "http://rdfh.ch/2a6221216701/values/d06ee1899505",
                    permissions = "CR knora-base:Creator|M knora-base:ProjectMember|V knora-base:KnownUser,knora-base:UnknownUser",
                    attachedToUser = "http://rdfh.ch/users/91e19f1e01",
                    attachedToProject = "http://rdfh.ch/projects/0803"
                ),
                ReadValueV2(
                    valueContent = TextValueContentV2(
                        standoffAndMapping = None,
                        valueType = "http://www.knora.org/ontology/knora-base#TextValue".toSmartIri,
                        valueHasLanguage = None,
                        comment = None,
                        valueHasString = "Borm 1751"
                    ),
                    valueIri = "http://rdfh.ch/2a6221216701/values/939834c39505",
                    permissions = "CR knora-base:Creator|M knora-base:ProjectMember|V knora-base:KnownUser,knora-base:UnknownUser",
                    attachedToUser = "http://rdfh.ch/users/91e19f1e01",
                    attachedToProject = "http://rdfh.ch/projects/0803"
                )
            ),
            "http://www.knora.org/ontology/0803/incunabula#publisher".toSmartIri -> Vector(ReadValueV2(
                valueContent = TextValueContentV2(
                    standoffAndMapping = None,
                    valueType = "http://www.knora.org/ontology/knora-base#TextValue".toSmartIri,
                    valueHasLanguage = None,
                    comment = None,
                    valueHasString = "Bernhard Richel"
                ),
                valueIri = "http://rdfh.ch/2a6221216701/values/dda85bbb9105",
                permissions = "CR knora-base:Creator|M knora-base:ProjectMember|V knora-base:KnownUser,knora-base:UnknownUser",
                attachedToUser = "http://rdfh.ch/users/91e19f1e01",
                attachedToProject = "http://rdfh.ch/projects/0803"
            )),
            "http://www.knora.org/ontology/0803/incunabula#hasAuthor".toSmartIri -> Vector(ReadValueV2(
                valueContent = TextValueContentV2(
                    standoffAndMapping = None,
                    valueType = "http://www.knora.org/ontology/knora-base#TextValue".toSmartIri,
                    valueHasLanguage = None,
                    comment = None,
                    valueHasString = "Jean Mandeville"
                ),
                valueIri = "http://rdfh.ch/2a6221216701/values/1a7f08829105",
                permissions = "CR knora-base:Creator|M knora-base:ProjectMember|V knora-base:KnownUser",
                attachedToUser = "http://rdfh.ch/users/91e19f1e01",
                attachedToProject = "http://rdfh.ch/projects/0803"
            )),
            "http://www.knora.org/ontology/0803/incunabula#book_comment".toSmartIri -> Vector(ReadValueV2(
                valueContent = TextValueContentV2(
                    standoffAndMapping = None,
                    valueType = "http://www.knora.org/ontology/knora-base#TextValue".toSmartIri,
                    valueHasLanguage = None,
                    comment = None,
                    valueHasString = "Katalogaufnahme anhand ISTC und v.d.Haegen"
                ),
                valueIri = "http://rdfh.ch/2a6221216701/values/56c287fc9505",
                permissions = "CR knora-base:Creator|M knora-base:ProjectMember|V knora-base:KnownUser,knora-base:UnknownUser",
                attachedToUser = "http://rdfh.ch/users/91e19f1e01",
                attachedToProject = "http://rdfh.ch/projects/0803"
            )),
            "http://www.knora.org/ontology/0803/incunabula#url".toSmartIri -> Vector(ReadValueV2(
                valueContent = TextValueContentV2(
                    standoffAndMapping = None,
                    valueType = "http://www.knora.org/ontology/knora-base#TextValue".toSmartIri,
                    valueHasLanguage = None,
                    comment = None,
                    valueHasString = "http://aleph.unibas.ch/F/?local_base=DSV01&con_lng=GER&func=find-b&find_code=SYS&request=002610320"
                ),
                valueIri = "http://rdfh.ch/2a6221216701/values/f89173afca2704",
                permissions = "CR knora-base:Creator|D knora-base:ProjectMember|V knora-base:KnownUser,knora-base:UnknownUser",
                attachedToUser = "http://rdfh.ch/users/91e19f1e01",
                attachedToProject = "http://rdfh.ch/projects/0803"
            )),
            "http://www.knora.org/ontology/0803/incunabula#note".toSmartIri -> Vector(
                ReadValueV2(
                    valueContent = TextValueContentV2(
                        standoffAndMapping = None,
                        valueType = "http://www.knora.org/ontology/knora-base#TextValue".toSmartIri,
                        valueHasLanguage = None,
                        comment = None,
                        valueHasString = "deutsch von Otto von Diemeringen"
                    ),
                    valueIri = "http://rdfh.ch/2a6221216701/values/ac79fbd99205",
                    permissions = "CR knora-base:Creator|M knora-base:ProjectMember|V knora-base:KnownUser,knora-base:UnknownUser",
                    attachedToUser = "http://rdfh.ch/users/91e19f1e01",
                    attachedToProject = "http://rdfh.ch/projects/0803"
                ),
                ReadValueV2(
                    valueContent = TextValueContentV2(
                        standoffAndMapping = None,
                        valueType = "http://www.knora.org/ontology/knora-base#TextValue".toSmartIri,
                        valueHasLanguage = None,
                        comment = None,
                        valueHasString = "Rubr. mit Init. J zu Beginn"
                    ),
                    valueIri = "http://rdfh.ch/2a6221216701/values/6fa34e139305",
                    permissions = "CR knora-base:Creator|M knora-base:ProjectMember|V knora-base:KnownUser,knora-base:UnknownUser",
                    attachedToUser = "http://rdfh.ch/users/91e19f1e01",
                    attachedToProject = "http://rdfh.ch/projects/0803"
                ),
                ReadValueV2(
                    valueContent = TextValueContentV2(
                        standoffAndMapping = None,
                        valueType = "http://www.knora.org/ontology/knora-base#TextValue".toSmartIri,
                        valueHasLanguage = None,
                        comment = None,
                        valueHasString = "Holzschnitte nicht koloriert"
                    ),
                    valueIri = "http://rdfh.ch/2a6221216701/values/32cda14c9305",
                    permissions = "CR knora-base:Creator|M knora-base:ProjectMember|V knora-base:KnownUser,knora-base:UnknownUser",
                    attachedToUser = "http://rdfh.ch/users/91e19f1e01",
                    attachedToProject = "http://rdfh.ch/projects/0803"
                ),
                ReadValueV2(
                    valueContent = TextValueContentV2(
                        standoffAndMapping = None,
                        valueType = "http://www.knora.org/ontology/knora-base#TextValue".toSmartIri,
                        valueHasLanguage = None,
                        comment = None,
                        valueHasString = "Besitzervermerke: Kartause, H. Zscheckenb\u00FCrlin"
                    ),
                    valueIri = "http://rdfh.ch/2a6221216701/values/f5f6f4859305",
                    permissions = "CR knora-base:Creator|M knora-base:ProjectMember|V knora-base:KnownUser,knora-base:UnknownUser",
                    attachedToUser = "http://rdfh.ch/users/91e19f1e01",
                    attachedToProject = "http://rdfh.ch/projects/0803"
                ),
                ReadValueV2(
                    valueContent = TextValueContentV2(
                        standoffAndMapping = None,
                        valueType = "http://www.knora.org/ontology/knora-base#TextValue".toSmartIri,
                        valueHasLanguage = None,
                        comment = None,
                        valueHasString = "Zusammengebunden mit: Die zehen Gebote ; Was und wie man beten soll und Auslegung des hlg. Pater nosters / Hans von Warmont. Strassburg, 1516"
                    ),
                    valueIri = "http://rdfh.ch/2a6221216701/values/b82048bf9305",
                    permissions = "CR knora-base:Creator|M knora-base:ProjectMember|V knora-base:KnownUser,knora-base:UnknownUser",
                    attachedToUser = "http://rdfh.ch/users/91e19f1e01",
                    attachedToProject = "http://rdfh.ch/projects/0803"
                )
            ),
            "http://www.knora.org/ontology/0803/incunabula#location".toSmartIri -> Vector(ReadValueV2(
                valueContent = TextValueContentV2(
                    standoffAndMapping = None,
                    valueType = "http://www.knora.org/ontology/knora-base#TextValue".toSmartIri,
                    valueHasLanguage = None,
                    comment = None,
                    valueHasString = "Basel UB, Sign: Aleph D III 13:1"
                ),
                valueIri = "http://rdfh.ch/2a6221216701/values/262655679205",
                permissions = "CR knora-base:Creator|D knora-base:ProjectMember|V knora-base:KnownUser,knora-base:UnknownUser",
                attachedToUser = "http://rdfh.ch/users/91e19f1e01",
                attachedToProject = "http://rdfh.ch/projects/0803"
            )),
            "http://www.knora.org/ontology/0803/incunabula#publoc".toSmartIri -> Vector(ReadValueV2(
                valueContent = TextValueContentV2(
                    standoffAndMapping = None,
                    valueType = "http://www.knora.org/ontology/knora-base#TextValue".toSmartIri,
                    valueHasLanguage = None,
                    comment = None,
                    valueHasString = "Basel"
                ),
                valueIri = "http://rdfh.ch/2a6221216701/values/a0d2aef49105",
                permissions = "CR knora-base:Creator|V knora-base:ProjectMember,knora-base:KnownUser,knora-base:UnknownUser",
                attachedToUser = "http://rdfh.ch/users/91e19f1e01",
                attachedToProject = "http://rdfh.ch/projects/0803"
            )),
            "http://www.knora.org/ontology/0803/incunabula#pubdate".toSmartIri -> Vector(ReadValueV2(
                valueContent = DateValueContentV2(
                    valueHasEndJDN = 2262358,
                    valueHasStartJDN = 2261994,
                    valueType = "http://www.knora.org/ontology/knora-base#DateValue".toSmartIri,
                    valueHasStartPrecision = KnoraPrecisionV1.YEAR,
                    valueHasCalendar = KnoraCalendarV1.JULIAN,
                    comment = None,
                    valueHasEndPrecision = KnoraPrecisionV1.YEAR
                ),
                valueIri = "http://rdfh.ch/2a6221216701/values/63fc012e9205",
                permissions = "CR knora-base:Creator|M knora-base:ProjectMember|V knora-base:KnownUser,knora-base:UnknownUser",
                attachedToUser = "http://rdfh.ch/users/91e19f1e01",
                attachedToProject = "http://rdfh.ch/projects/0803"
            )),
            "http://www.knora.org/ontology/0803/incunabula#title".toSmartIri -> Vector(
                ReadValueV2(
                    valueContent = TextValueContentV2(
                        standoffAndMapping = None,
                        valueType = "http://www.knora.org/ontology/knora-base#TextValue".toSmartIri,
                        valueHasLanguage = None,
                        comment = None,
                        valueHasString = "Reise ins Heilige Land"
                    ),
                    valueIri = "http://rdfh.ch/2a6221216701/values/d1010fd69005",
                    permissions = "CR knora-base:Creator|M knora-base:ProjectMember|V knora-base:KnownUser,knora-base:UnknownUser",
                    attachedToUser = "http://rdfh.ch/users/91e19f1e01",
                    attachedToProject = "http://rdfh.ch/projects/0803"
                ),
                ReadValueV2(
                    valueContent = TextValueContentV2(
                        standoffAndMapping = None,
                        valueType = "http://www.knora.org/ontology/knora-base#TextValue".toSmartIri,
                        valueHasLanguage = None,
                        comment = None,
                        valueHasString = "Reysen und wanderschafften durch das Gelobte Land"
                    ),
                    valueIri = "http://rdfh.ch/2a6221216701/values/942b620f9105",
                    permissions = "CR knora-base:Creator|M knora-base:ProjectMember|V knora-base:KnownUser,knora-base:UnknownUser",
                    attachedToUser = "http://rdfh.ch/users/91e19f1e01",
                    attachedToProject = "http://rdfh.ch/projects/0803"
                )
            )
        )
    )

    val expectedFullResourceResponseForReiseWrong = ReadResourcesSequenceV2(
        resources = Vector(expectedReadResourceV2ForReiseInsHeiligelandWrong),
        numberOfResources = 1
    )

}
