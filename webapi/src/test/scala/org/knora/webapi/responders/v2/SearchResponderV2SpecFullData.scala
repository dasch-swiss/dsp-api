package org.knora.webapi.responders.v2

import akka.actor.ActorSystem
import org.knora.webapi.{ApiV2Simple, OntologyConstants, Settings}
import org.knora.webapi.messages.v2.responder.resourcemessages._
import org.knora.webapi.messages.v2.responder.valuemessages._
import org.knora.webapi.messages.v2.responder.standoffmessages._
import org.knora.webapi.twirl.{StandoffTagIriAttributeV2, StandoffTagV2}
import org.knora.webapi.util.IriConversions._
import org.knora.webapi.util.StringFormatter
import org.knora.webapi.util.search._

class SearchResponderV2SpecFullData(implicit stringFormatter: StringFormatter) {

    implicit lazy val system: ActorSystem = ActorSystem("webapi")

    val settings = Settings(system)
    val fulltextSearchForNarr = ReadResourcesSequenceV2(
        resources = Vector(
            ReadResourceV2(
                values = Map("http://www.knora.org/ontology/0803/incunabula#description".toSmartIri -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        valueType = OntologyConstants.KnoraBase.TextValue.toSmartIri,
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 105.\nHolzschnitt identisch mit Kap. 95: In einer Landschaft fasst ein Narr, der ein Zepter in der Linken h\u00E4lt, einem Mann an die Schulter und redet auf ihn ein, er m\u00F6ge die Feiertage missachten, 11.7 x 8.6 cm."
                    ),
                    valueIri = "http://rdfh.ch/00505cf0a803/values/549527258a26"
                ))),
                resourceClass = "http://www.knora.org/ontology/0803/incunabula#page".toSmartIri,
                label = "p7v",
                resourceIri = "http://rdfh.ch/00505cf0a803"
            ),
            ReadResourceV2(
                values = Map("http://www.knora.org/ontology/0803/incunabula#description".toSmartIri -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        valueType = OntologyConstants.KnoraBase.TextValue.toSmartIri,
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 21.\nHolzschnitt zu Kap. 21: Andere tadeln und selbst unrecht handeln.\nEin Narr, der mit seinen Beinen im Sumpf steckt, zeigt auf einen nahen Weg, an dem ein Bildstock die Richtung weist.\n11.7 x 8.5 cm.\nUnkoloriert.\n"
                    ),
                    valueIri = "http://rdfh.ch/00c650d23303/values/af68552c3626"
                ))),
                resourceClass = "http://www.knora.org/ontology/0803/incunabula#page".toSmartIri,
                label = "d4v",
                resourceIri = "http://rdfh.ch/00c650d23303"
            ),
            ReadResourceV2(
                values = Map("http://www.knora.org/ontology/0803/incunabula#description".toSmartIri -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        valueType = OntologyConstants.KnoraBase.TextValue.toSmartIri,
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 99.\nHolzschnitt zu Kap. 99: Von der Einbusse des christlichen Reiches\nAuf einem Hof kniet ein Narr vor den Vertretern der kirchlichen und weltlichen Obrigkeit, die vor ein Portal getreten sind, und bittet darum, sie m\u00F6gen die Narrenkappe verschm\u00E4hen. Im Hintergrund kommentieren zwei weitere Narren \u00FCber die Hofmauer hinweg das Geschehen mit ungl\u00E4ubigen Gesten, 11.7 x 8.5 cm."
                    ),
                    valueIri = "http://rdfh.ch/02abe871e903/values/1852a8aa8526"
                ))),
                resourceClass = "http://www.knora.org/ontology/0803/incunabula#page".toSmartIri,
                label = "o5v",
                resourceIri = "http://rdfh.ch/02abe871e903"
            ),
            ReadResourceV2(
                values = Map("http://www.knora.org/ontology/0803/incunabula#description".toSmartIri -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        valueType = OntologyConstants.KnoraBase.TextValue.toSmartIri,
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 109.\nHolzschnitt zu Kap. 109: Von Verachtung des Ungl\u00FCcks\nEin Narr hat sich in einem Boot zu weit vom Ufer entfernt. Nun birst der Schiffsrumpf, das Segel flattert haltlos umher. Der Narr h\u00E4lt sich an einem Seil der Takelage fest, 11.6 x 8.4 cm."
                    ),
                    valueIri = "http://rdfh.ch/04416f64ef03/values/6ce3c0ef8b26"
                ))),
                resourceClass = "http://www.knora.org/ontology/0803/incunabula#page".toSmartIri,
                label = "q2v",
                resourceIri = "http://rdfh.ch/04416f64ef03"
            ),
            ReadResourceV2(
                values = Map("http://www.knora.org/ontology/0803/incunabula#description".toSmartIri -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        valueType = OntologyConstants.KnoraBase.TextValue.toSmartIri,
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 44.\nHolzschnitt zu Kap. 44: Vom L\u00E4rmen in der Kirche\nEin junger Narr in edler Kleidung, der einen Jagdfalken auf dem Arm h\u00E4lt, von Hunden begleitet wird, und klappernde Schuhsohlen tr\u00E4gt, geht auf ein Portal zu, in dem eine Frau steht und ihm sch\u00F6ne Augen macht.\n11.7 x 8.5 cm.\nUnkoloriert."
                    ),
                    valueIri = "http://rdfh.ch/04f25db73f03/values/aa8971af4d26"
                ))),
                resourceClass = "http://www.knora.org/ontology/0803/incunabula#page".toSmartIri,
                label = "g6v",
                resourceIri = "http://rdfh.ch/04f25db73f03"
            ),
            ReadResourceV2(
                values = Map("http://www.knora.org/ontology/0803/incunabula#description".toSmartIri -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        valueType = OntologyConstants.KnoraBase.TextValue.toSmartIri,
                        comment = None,
                        standoff = None,
                        valueHasString = "Titelblatt (Hartl 2001: Stultitia Navis I).\nHolzschnitt: \nErsatzholzschnitt f\u00FCr Titelblatt, recto:\nEin Schiff voller Narren f\u00E4hrt nach links. Hinten auf der Br\u00FCcke trinkt ein Narr aus einer Flasche, vorne pr\u00FCgeln sich zwei weitere narren so sehr, dass einer von ihnen \u00FCber Bord zu gehen droht. Oben die Inschrift \"Nauis stultoru(m).\"; auf dem Schiffsrumpf die Datierung \"1.4.9.7.\".\n6.5 x 11.5 cm.\noben rechts die bibliographische Angabe  (Graphitstift) \"Hain 3750\"; unten rechts Bibliotheksstempel (queroval, schwarz): \"BIBL. PUBL.| BASILEENSIS\"."
                    ),
                    valueIri = "http://rdfh.ch/05c7acceb703/values/5f23f3171d26"
                ))),
                resourceClass = "http://www.knora.org/ontology/0803/incunabula#page".toSmartIri,
                label = "a1r; Titelblatt, recto",
                resourceIri = "http://rdfh.ch/05c7acceb703"
            ),
            ReadResourceV2(
                values = Map("http://www.knora.org/ontology/0803/incunabula#description".toSmartIri -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        valueType = OntologyConstants.KnoraBase.TextValue.toSmartIri,
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 4.\nHolzschnitt zu Kap. 4: Von neumodischen Sitten.\nEin alter Narr mit Becher h\u00E4lt einem jungen Mann in modischer Tracht einen Spiegel vor. Zwischen Narr und J\u00FCngling steht der Name \u201E.VLI.\u201C; \u00FCber den beiden schwebt eine Banderole mit der Aufschrift \u201Evly . von . stouffen .  . frisch . vnd vngschaffen\u201C; zwischen den F\u00FCssen des J\u00FCnglings ist die Jahreszahl \u201E.1.4.9.4.\u201C zu lesen.\n11.6 x 8.5 cm."
                    ),
                    valueIri = "http://rdfh.ch/075d33c1bd03/values/77718ce21e26"
                ))),
                resourceClass = "http://www.knora.org/ontology/0803/incunabula#page".toSmartIri,
                label = "b6r",
                resourceIri = "http://rdfh.ch/075d33c1bd03"
            ),
            ReadResourceV2(
                values = Map("http://www.knora.org/ontology/0803/incunabula#description".toSmartIri -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        valueType = OntologyConstants.KnoraBase.TextValue.toSmartIri,
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 29.\nHolzschnitt zu Kap. 29: Von Verkennung der Mitmenschen.\nEin Narr verspottet einen Sterbenden, neben dessen Bett eine Frau betet, w\u00E4hrend sich unter dem Narren die H\u00F6lle in Gestalt eines gefr\u00E4ssigen Drachenkopfs auftut, 11.7 x 8.5 cm.\n"
                    ),
                    valueIri = "http://rdfh.ch/0b8940a6c903/values/f752218c3b26"
                ))),
                resourceClass = "http://www.knora.org/ontology/0803/incunabula#page".toSmartIri,
                label = "e8r",
                resourceIri = "http://rdfh.ch/0b8940a6c903"
            ),
            ReadResourceV2(
                values = Map("http://www.knora.org/ontology/0803/incunabula#description".toSmartIri -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        valueType = OntologyConstants.KnoraBase.TextValue.toSmartIri,
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 43.\nHolzschnitt zu Kap. 43: Missachten der ewigen Seligkeit\nEin Narr steht mit einer grossen Waage in einer Landschaft und wiegt das Himmelsfirmament (links) gegen eine Burg (rechts) auf. Die Zunge der Waage schl\u00E4gt zugunsten der Burg aus, 11.5 x 8.4 cm.\n"
                    ),
                    valueIri = "http://rdfh.ch/0d1fc798cf03/values/e75f1e764d26"
                ))),
                resourceClass = "http://www.knora.org/ontology/0803/incunabula#page".toSmartIri,
                label = "g5r",
                resourceIri = "http://rdfh.ch/0d1fc798cf03"
            ),
            ReadResourceV2(
                values = Map("http://www.knora.org/ontology/0803/incunabula#description".toSmartIri -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        valueType = OntologyConstants.KnoraBase.TextValue.toSmartIri,
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 66.\nHolzschnitt zu Kap. 66: Von der Erforschung der Welt.\nEin Narr hat ein Schema des Universums auf den Boden Gezeichnet und vermisst es mit einem Zirkel. Von hinten blickt ein zweiter Narr \u00FCber eine Mauer und wendet sich dem ersten mit sp\u00F6ttischen Gesten zu.\n11.6 x 8.4 cm."
                    ),
                    valueIri = "http://rdfh.ch/0d5ac1099503/values/4dcdbebc7126"
                ))),
                resourceClass = "http://www.knora.org/ontology/0803/incunabula#page".toSmartIri,
                label = "k4r",
                resourceIri = "http://rdfh.ch/0d5ac1099503"
            ),
            ReadResourceV2(
                values = Map("http://www.knora.org/ontology/0803/incunabula#description".toSmartIri -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        valueType = OntologyConstants.KnoraBase.TextValue.toSmartIri,
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 58.\nHolzschnitt zu Kap. 58: Sich um die Angelegenheiten anderer k\u00FCmmern.\nEin Narr versucht mit einem Wassereimer den Brand im Haus des Nachbarn zu l\u00F6schen und wird dabei von einem anderen Narren, der an seinem Mantel zerrt, unterbrochen, den hinter ihm steht auch sein eigenes Haus in Flammen.\n11.6 x 8.5 cm."
                    ),
                    valueIri = "http://rdfh.ch/0fb54d8bd503/values/9a966e995f26"
                ))),
                resourceClass = "http://www.knora.org/ontology/0803/incunabula#page".toSmartIri,
                label = "i2r",
                resourceIri = "http://rdfh.ch/0fb54d8bd503"
            ),
            ReadResourceV2(
                values = Map("http://www.knora.org/ontology/0803/incunabula#description".toSmartIri -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        valueType = OntologyConstants.KnoraBase.TextValue.toSmartIri,
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 81.\nHolzschnitt zu Kap. 81: Aus K\u00FCche und Keller.\nEin Narr f\u00FChrt von einem Boot aus vier Knechte am Strick, die sich in einer K\u00FCche \u00FCber Spreis und Trank hermachen, w\u00E4hrend eine Frau, die am Herdfeuer sitzt, das Essen zubereitet, 11.7 x 8.5 cm."
                    ),
                    valueIri = "http://rdfh.ch/0ff047fc9a03/values/b9ac70cc7926"
                ))),
                resourceClass = "http://www.knora.org/ontology/0803/incunabula#page".toSmartIri,
                label = "m1r",
                resourceIri = "http://rdfh.ch/0ff047fc9a03"
            ),
            ReadResourceV2(
                values = Map("http://www.knora.org/ontology/0803/incunabula#description".toSmartIri -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        valueType = OntologyConstants.KnoraBase.TextValue.toSmartIri,
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 69.\nHolzschnitt Lemmer 1979, S. 117: Variante zu Kap. 69.\nEin Narr, der vor einer Stadtkulisse steht, hat mit seiner Rechten einen Ball in die Luft geworfen und schl\u00E4gt mit seiner Linken einen Mann, der sogleich nach seinem Dolch greift. Ein junger Mann beobachtet das Geschehen.\nDer Bildinhalt stimmt weitgehend mit dem urspr\u00FCnglichen Holzschnitt \u00FCberein.\n11.7 x 8.4 cm."
                    ),
                    valueIri = "http://rdfh.ch/114bd47ddb03/values/c99f73e26726"
                ))),
                resourceClass = "http://www.knora.org/ontology/0803/incunabula#page".toSmartIri,
                label = "k7r",
                resourceIri = "http://rdfh.ch/114bd47ddb03"
            ),
            ReadResourceV2(
                values = Map("http://www.knora.org/ontology/0803/incunabula#description".toSmartIri -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        valueType = OntologyConstants.KnoraBase.TextValue.toSmartIri,
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 23.\nHolzschnitt zu Kap. 23: Vom blinden Vertrauen auf das Gl\u00FCck.\nEin Narr schaut oben aus dem Fenster seines Hauses, das unten lichterloh brennt. Am Himmel erscheint die r\u00E4chende Gotteshand, die mit einen Hammer auf Haus und Narr einschl\u00E4gt. Auf der Fahne \u00FCber dem Erker des Hauses ist der Baselstab zu erkennen.\n11.5 x 8.2 cm.\nUnkoloriert.\n"
                    ),
                    valueIri = "http://rdfh.ch/14dd8cbc3403/values/7e39f54a3726"
                ))),
                resourceClass = "http://www.knora.org/ontology/0803/incunabula#page".toSmartIri,
                label = "d6v",
                resourceIri = "http://rdfh.ch/14dd8cbc3403"
            ),
            ReadResourceV2(
                values = Map("http://www.knora.org/ontology/0803/incunabula#description".toSmartIri -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        valueType = OntologyConstants.KnoraBase.TextValue.toSmartIri,
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 34.\nHolzschnitt zu Kap. 34: Ein Narr sein und es bleiben.\nEin Narr wird von drei G\u00E4nsen umgeben, deren eine von ihm wegfliegt.\n11.7 x 8.4 cm.\nUnkoloriert."
                    ),
                    valueIri = "http://rdfh.ch/167313af3a03/values/1ab5d9ef4226"
                ))),
                resourceClass = "http://www.knora.org/ontology/0803/incunabula#page".toSmartIri,
                label = "f3v",
                resourceIri = "http://rdfh.ch/167313af3a03"
            ),
            ReadResourceV2(
                values = Map("http://www.knora.org/ontology/0803/incunabula#description".toSmartIri -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        valueType = OntologyConstants.KnoraBase.TextValue.toSmartIri,
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 6.\nHolzschnitt zu Kap. 6: Von mangelhafter Erziehung der Kinder.\nZwei Jungen geraten am Spieltisch \u00FCber Karten und W\u00FCrfen in Streit. W\u00E4hrend der eine einen Dolch z\u00FCckt und der andere nach seinem Schwert greift, sitzt ein \u00E4lterer Narr mit verbundenen Augen ahnungslos neben dem Geschehen.\n11.7 x 8.5 cm."
                    ),
                    valueIri = "http://rdfh.ch/1b746fabbe03/values/8318d9c71f26"
                ))),
                resourceClass = "http://www.knora.org/ontology/0803/incunabula#page".toSmartIri,
                label = "b8r",
                resourceIri = "http://rdfh.ch/1b746fabbe03"
            ),
            ReadResourceV2(
                values = Map("http://www.knora.org/ontology/0803/incunabula#description".toSmartIri -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        valueType = OntologyConstants.KnoraBase.TextValue.toSmartIri,
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 28.\nHolzschnitt zu Kap. 28: Vom N\u00F6rgeln an Gottes Werken.\nEin Narr, der auf einem Berg ein Feuer entfacht hat, h\u00E4lt seine Hand sch\u00FCtzend \u00FCber die Augen, w\u00E4hrend er seinen Blick auf die hell am Himmel strahlende Sonne richtet. 11.7 x 8.5 cm."
                    ),
                    valueIri = "http://rdfh.ch/1baf691c8403/values/2882816d3a26"
                ))),
                resourceClass = "http://www.knora.org/ontology/0803/incunabula#page".toSmartIri,
                label = "e7r",
                resourceIri = "http://rdfh.ch/1baf691c8403"
            ),
            ReadResourceV2(
                values = Map("http://www.knora.org/ontology/0803/incunabula#description".toSmartIri -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        valueType = OntologyConstants.KnoraBase.TextValue.toSmartIri,
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 18.\nHolzschnitt zu Kap. 18: Vom Dienst an zwei Herren.\nEin mit Spiess bewaffneter Narr bl\u00E4st in ein Horn. Sein Hund versucht derweil im Hintergrund zwei Hasen gleichzeitig zu erjagen, 11.6 x 8.4 cm.\n"
                    ),
                    valueIri = "http://rdfh.ch/1d0af69dc403/values/4e9dc2b53326"
                ))),
                resourceClass = "http://www.knora.org/ontology/0803/incunabula#page".toSmartIri,
                label = "d5r",
                resourceIri = "http://rdfh.ch/1d0af69dc403"
            ),
            ReadResourceV2(
                values = Map("http://www.knora.org/ontology/0803/incunabula#description".toSmartIri -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        valueType = OntologyConstants.KnoraBase.TextValue.toSmartIri,
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 31.\nHolzschnitt zu Kap. 31: Vom Hinausschieben auf morgen.\nEin Narr steht mit ausgebreiteten Armen auf einer Strasse. Auf seinen H\u00E4nden sitzen zwei Raben, die beide \u201ECras\u201C \u2013 das lateinische Wort f\u00FCr \u201Emorgen\u201C \u2013 rufen. Auf dem Kopf des Narren sitzt ein Papagei und ahmt den Ruf der Kr\u00E4hen nach, 11.6 x 8.5 cm."
                    ),
                    valueIri = "http://rdfh.ch/1fa07c90ca03/values/c623c1aa3c26"
                ))),
                resourceClass = "http://www.knora.org/ontology/0803/incunabula#page".toSmartIri,
                label = "f2r",
                resourceIri = "http://rdfh.ch/1fa07c90ca03"
            ),
            ReadResourceV2(
                values = Map("http://www.knora.org/ontology/0803/incunabula#description".toSmartIri -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        valueType = OntologyConstants.KnoraBase.TextValue.toSmartIri,
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 57.\nHolzschnitt zu Kap. 57: Von der Gnadenwahl Gottes.\nEin Narr, der auf einem Krebs reitet, st\u00FCtzt sich auf ein brechendes Schildrohr, das ihm die Hand  durchbohrt. Ein Vogel fliegt auf den offenen Mund des Narren zu.\n11.6 x 8.5 cm."
                    ),
                    valueIri = "http://rdfh.ch/1fdb76019003/values/118a3f426d26"
                ))),
                resourceClass = "http://www.knora.org/ontology/0803/incunabula#page".toSmartIri,
                label = "i1r",
                resourceIri = "http://rdfh.ch/1fdb76019003"
            ),
            ReadResourceV2(
                values = Map("http://www.knora.org/ontology/0803/incunabula#description".toSmartIri -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        valueType = OntologyConstants.KnoraBase.TextValue.toSmartIri,
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 45.\nHolzschnitt zu Kap. 45: Von selbstverschuldetem Ungl\u00FCck.\nIn Gestalt eines Narren springt Empedokles in den lodernden Krater des \u00C4tna. Im Vordergrund l\u00E4sst sich ein anderer Narr in einen Brunnen fallen. Beide werden von drei M\u00E4nnern beobachtet, die das Verhalten mit \u201EJn geschicht recht\u201C  kommentieren, 11.7 x 8.3 cm.\n"
                    ),
                    valueIri = "http://rdfh.ch/21360383d003/values/b630be944e26"
                ))),
                resourceClass = "http://www.knora.org/ontology/0803/incunabula#page".toSmartIri,
                label = "g7r",
                resourceIri = "http://rdfh.ch/21360383d003"
            ),
            ReadResourceV2(
                values = Map("http://www.knora.org/ontology/0803/incunabula#description".toSmartIri -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        valueType = OntologyConstants.KnoraBase.TextValue.toSmartIri,
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 68.\nHolzschnitt zu Kap. 68: Keinen Scherz verstehen.\nEin Kind, das auf einem Steckenpferd reitet und mit einem Stock als Gerte umher fuchtelt, wird von einem Narren am rechten Rand ausgeschimpft. Ein anderer Narr, der neben dem Kind steht, ist dabei, sein Schwert aus der Scheide zu ziehen.\n11.7 x 8.5 cm."
                    ),
                    valueIri = "http://rdfh.ch/2171fdf39503/values/59740ba27226"
                ))),
                resourceClass = "http://www.knora.org/ontology/0803/incunabula#page".toSmartIri,
                label = "k6r",
                resourceIri = "http://rdfh.ch/2171fdf39503"
            ),
            ReadResourceV2(
                values = Map("http://www.knora.org/ontology/0803/incunabula#description".toSmartIri -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        valueType = OntologyConstants.KnoraBase.TextValue.toSmartIri,
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 83.\nneuer Holzschitt (nicht in Lemmer 1979): Vor einer H\u00E4userkulisse kniet ein Narr mit einem Beutel in der Linken und zwei Keulen in der Rechten vor einem Mann mit Hut und einem j\u00FCngeren Begleiter, 11.6 x 8.6 cm."
                    ),
                    valueIri = "http://rdfh.ch/230784e69b03/values/4ba763247b26"
                ))),
                resourceClass = "http://www.knora.org/ontology/0803/incunabula#page".toSmartIri,
                label = "m3r",
                resourceIri = "http://rdfh.ch/230784e69b03"
            ),
            ReadResourceV2(
                values = Map("http://www.knora.org/ontology/0803/incunabula#description".toSmartIri -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        valueType = OntologyConstants.KnoraBase.TextValue.toSmartIri,
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 96.\nHolzschnitt zu Kap. 96: Schenken und hinterdrein bereuen.\nEin Narr, der vor einem Haus steht, \u00FCberreicht einem b\u00E4rtigen Alten ein Geschenk, kratzt sich dabei aber unschl\u00FCssig am Kopf.\n11.6 x 8.3 cm.\nUnkoloriert.\nOben rechts Blattnummerierung (Graphitstift): \"128\"."
                    ),
                    valueIri = "http://rdfh.ch/23427e576103/values/c32d62198426"
                ))),
                resourceClass = "http://www.knora.org/ontology/0803/incunabula#page".toSmartIri,
                label = "q8r",
                resourceIri = "http://rdfh.ch/23427e576103"
            ),
            ReadResourceV2(
                values = Map("http://www.knora.org/ontology/0803/incunabula#description".toSmartIri -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        valueType = OntologyConstants.KnoraBase.TextValue.toSmartIri,
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 60.\nHolzschnitt zu Kap. 60: Von Selbstgef\u00E4lligkeit.\nEin alter Narr steht am Ofen und r\u00FChrt in einem Topf. Gleichzeitig schaut er sich dabei in einem Handspiegel an.\n11.7 x 8.5 cm."
                    ),
                    valueIri = "http://rdfh.ch/23cc8975d603/values/a63dbb7e6026"
                ))),
                resourceClass = "http://www.knora.org/ontology/0803/incunabula#page".toSmartIri,
                label = "i4r",
                resourceIri = "http://rdfh.ch/23cc8975d603"
            )
        ),
        numberOfResources = 25
    )


    val fulltextSearchForDinge = ReadResourcesSequenceV2(
        resources = Vector(ReadResourceV2(
            values = Map("http://www.knora.org/ontology/0001/anything#hasText".toSmartIri -> Vector(
                ReadValueV2(
                    valueContent = TextValueContentV2(
                        valueType = OntologyConstants.KnoraBase.TextValue.toSmartIri,
                        comment = None,
                        standoff = Some(StandoffAndMapping(
                            XSLT = None,
                            mapping = MappingXMLtoStandoff(
                                defaultXSLTransformation = None,
                                namespace = Map("noNamespace" -> Map(
                                    "tbody" -> Map("noClass" -> XMLTag(
                                        separatorRequired = true,
                                        mapping = XMLTagToStandoffClass(
                                            dataType = None,
                                            attributesToProps = Map(),
                                            standoffClassIri = "http://www.knora.org/ontology/standoff#StandoffTableBodyTag"
                                        ),
                                        name = "tbody"
                                    )),
                                    "pre" -> Map("noClass" -> XMLTag(
                                        separatorRequired = true,
                                        mapping = XMLTagToStandoffClass(
                                            dataType = None,
                                            attributesToProps = Map(),
                                            standoffClassIri = "http://www.knora.org/ontology/standoff#StandoffPreTag"
                                        ),
                                        name = "pre"
                                    )),
                                    "ol" -> Map("noClass" -> XMLTag(
                                        separatorRequired = true,
                                        mapping = XMLTagToStandoffClass(
                                            dataType = None,
                                            attributesToProps = Map(),
                                            standoffClassIri = "http://www.knora.org/ontology/standoff#StandoffOrderedListTag"
                                        ),
                                        name = "ol"
                                    )),
                                    "hr" -> Map("noClass" -> XMLTag(
                                        separatorRequired = true,
                                        mapping = XMLTagToStandoffClass(
                                            dataType = None,
                                            attributesToProps = Map(),
                                            standoffClassIri = "http://www.knora.org/ontology/standoff#StandoffLineTag"
                                        ),
                                        name = "hr"
                                    )),
                                    "h4" -> Map("noClass" -> XMLTag(
                                        separatorRequired = true,
                                        mapping = XMLTagToStandoffClass(
                                            dataType = None,
                                            attributesToProps = Map(),
                                            standoffClassIri = "http://www.knora.org/ontology/standoff#StandoffHeader4Tag"
                                        ),
                                        name = "h4"
                                    )),
                                    "h3" -> Map("noClass" -> XMLTag(
                                        separatorRequired = true,
                                        mapping = XMLTagToStandoffClass(
                                            dataType = None,
                                            attributesToProps = Map(),
                                            standoffClassIri = "http://www.knora.org/ontology/standoff#StandoffHeader3Tag"
                                        ),
                                        name = "h3"
                                    )),
                                    "li" -> Map("noClass" -> XMLTag(
                                        separatorRequired = true,
                                        mapping = XMLTagToStandoffClass(
                                            dataType = None,
                                            attributesToProps = Map(),
                                            standoffClassIri = "http://www.knora.org/ontology/standoff#StandoffListElementTag"
                                        ),
                                        name = "li"
                                    )),
                                    "br" -> Map("noClass" -> XMLTag(
                                        separatorRequired = true,
                                        mapping = XMLTagToStandoffClass(
                                            dataType = None,
                                            attributesToProps = Map(),
                                            standoffClassIri = "http://www.knora.org/ontology/standoff#StandoffBrTag"
                                        ),
                                        name = "br"
                                    )),
                                    "u" -> Map("noClass" -> XMLTag(
                                        separatorRequired = false,
                                        mapping = XMLTagToStandoffClass(
                                            dataType = None,
                                            attributesToProps = Map(),
                                            standoffClassIri = "http://www.knora.org/ontology/standoff#StandoffUnderlineTag"
                                        ),
                                        name = "u"
                                    )),
                                    "strike" -> Map("noClass" -> XMLTag(
                                        separatorRequired = false,
                                        mapping = XMLTagToStandoffClass(
                                            dataType = None,
                                            attributesToProps = Map(),
                                            standoffClassIri = "http://www.knora.org/ontology/standoff#StandoffStrikethroughTag"
                                        ),
                                        name = "strike"
                                    )),
                                    "ul" -> Map("noClass" -> XMLTag(
                                        separatorRequired = true,
                                        mapping = XMLTagToStandoffClass(
                                            dataType = None,
                                            attributesToProps = Map(),
                                            standoffClassIri = "http://www.knora.org/ontology/standoff#StandoffUnorderedListTag"
                                        ),
                                        name = "ul"
                                    )),
                                    "a" -> Map(
                                        "noClass" -> XMLTag(
                                            separatorRequired = false,
                                            mapping = XMLTagToStandoffClass(
                                                dataType = Some(XMLStandoffDataTypeClass(
                                                    dataTypeXMLAttribute = "href",
                                                    standoffDataTypeClass = StandoffDataTypeClasses.StandoffUriTag
                                                )),
                                                attributesToProps = Map(),
                                                standoffClassIri = "http://www.knora.org/ontology/knora-base#StandoffUriTag"
                                            ),
                                            name = "a"
                                        ),
                                        "salsah-link" -> XMLTag(
                                            separatorRequired = false,
                                            mapping = XMLTagToStandoffClass(
                                                dataType = Some(XMLStandoffDataTypeClass(
                                                    dataTypeXMLAttribute = "href",
                                                    standoffDataTypeClass = StandoffDataTypeClasses.StandoffLinkTag
                                                )),
                                                attributesToProps = Map(),
                                                standoffClassIri = "http://www.knora.org/ontology/knora-base#StandoffLinkTag"
                                            ),
                                            name = "a"
                                        )
                                    ),
                                    "text" -> Map("noClass" -> XMLTag(
                                        separatorRequired = false,
                                        mapping = XMLTagToStandoffClass(
                                            dataType = None,
                                            attributesToProps = Map("noNamespace" -> Map("documentType" -> "http://www.knora.org/ontology/standoff#standoffRootTagHasDocumentType")),
                                            standoffClassIri = "http://www.knora.org/ontology/standoff#StandoffRootTag"
                                        ),
                                        name = "text"
                                    )),
                                    "strong" -> Map("noClass" -> XMLTag(
                                        separatorRequired = false,
                                        mapping = XMLTagToStandoffClass(
                                            dataType = None,
                                            attributesToProps = Map(),
                                            standoffClassIri = "http://www.knora.org/ontology/standoff#StandoffBoldTag"
                                        ),
                                        name = "strong"
                                    )),
                                    "code" -> Map("noClass" -> XMLTag(
                                        separatorRequired = true,
                                        mapping = XMLTagToStandoffClass(
                                            dataType = None,
                                            attributesToProps = Map(),
                                            standoffClassIri = "http://www.knora.org/ontology/standoff#StandoffCodeTag"
                                        ),
                                        name = "code"
                                    )),
                                    "h2" -> Map("noClass" -> XMLTag(
                                        separatorRequired = true,
                                        mapping = XMLTagToStandoffClass(
                                            dataType = None,
                                            attributesToProps = Map(),
                                            standoffClassIri = "http://www.knora.org/ontology/standoff#StandoffHeader2Tag"
                                        ),
                                        name = "h2"
                                    )),
                                    "td" -> Map("noClass" -> XMLTag(
                                        separatorRequired = true,
                                        mapping = XMLTagToStandoffClass(
                                            dataType = None,
                                            attributesToProps = Map(),
                                            standoffClassIri = "http://www.knora.org/ontology/standoff#StandoffTableCellTag"
                                        ),
                                        name = "td"
                                    )),
                                    "em" -> Map("noClass" -> XMLTag(
                                        separatorRequired = false,
                                        mapping = XMLTagToStandoffClass(
                                            dataType = None,
                                            attributesToProps = Map(),
                                            standoffClassIri = "http://www.knora.org/ontology/standoff#StandoffItalicTag"
                                        ),
                                        name = "em"
                                    )),
                                    "tr" -> Map("noClass" -> XMLTag(
                                        separatorRequired = true,
                                        mapping = XMLTagToStandoffClass(
                                            dataType = None,
                                            attributesToProps = Map(),
                                            standoffClassIri = "http://www.knora.org/ontology/standoff#StandoffTableRowTag"
                                        ),
                                        name = "tr"
                                    )),
                                    "cite" -> Map("noClass" -> XMLTag(
                                        separatorRequired = true,
                                        mapping = XMLTagToStandoffClass(
                                            dataType = None,
                                            attributesToProps = Map(),
                                            standoffClassIri = "http://www.knora.org/ontology/standoff#StandoffCiteTag"
                                        ),
                                        name = "cite"
                                    )),
                                    "blockquote" -> Map("noClass" -> XMLTag(
                                        separatorRequired = true,
                                        mapping = XMLTagToStandoffClass(
                                            dataType = None,
                                            attributesToProps = Map(),
                                            standoffClassIri = "http://www.knora.org/ontology/standoff#StandoffBlockquoteTag"
                                        ),
                                        name = "blockquote"
                                    )),
                                    "p" -> Map("noClass" -> XMLTag(
                                        separatorRequired = true,
                                        mapping = XMLTagToStandoffClass(
                                            dataType = None,
                                            attributesToProps = Map(),
                                            standoffClassIri = "http://www.knora.org/ontology/standoff#StandoffParagraphTag"
                                        ),
                                        name = "p"
                                    )),
                                    "h6" -> Map("noClass" -> XMLTag(
                                        separatorRequired = true,
                                        mapping = XMLTagToStandoffClass(
                                            dataType = None,
                                            attributesToProps = Map(),
                                            standoffClassIri = "http://www.knora.org/ontology/standoff#StandoffHeader6Tag"
                                        ),
                                        name = "h6"
                                    )),
                                    "h1" -> Map("noClass" -> XMLTag(
                                        separatorRequired = true,
                                        mapping = XMLTagToStandoffClass(
                                            dataType = None,
                                            attributesToProps = Map(),
                                            standoffClassIri = "http://www.knora.org/ontology/standoff#StandoffHeader1Tag"
                                        ),
                                        name = "h1"
                                    )),
                                    "sub" -> Map("noClass" -> XMLTag(
                                        separatorRequired = false,
                                        mapping = XMLTagToStandoffClass(
                                            dataType = None,
                                            attributesToProps = Map(),
                                            standoffClassIri = "http://www.knora.org/ontology/standoff#StandoffSubscriptTag"
                                        ),
                                        name = "sub"
                                    )),
                                    "sup" -> Map("noClass" -> XMLTag(
                                        separatorRequired = false,
                                        mapping = XMLTagToStandoffClass(
                                            dataType = None,
                                            attributesToProps = Map(),
                                            standoffClassIri = "http://www.knora.org/ontology/standoff#StandoffSuperscriptTag"
                                        ),
                                        name = "sup"
                                    )),
                                    "h5" -> Map("noClass" -> XMLTag(
                                        separatorRequired = true,
                                        mapping = XMLTagToStandoffClass(
                                            dataType = None,
                                            attributesToProps = Map(),
                                            standoffClassIri = "http://www.knora.org/ontology/standoff#StandoffHeader5Tag"
                                        ),
                                        name = "h5"
                                    )),
                                    "table" -> Map("noClass" -> XMLTag(
                                        separatorRequired = true,
                                        mapping = XMLTagToStandoffClass(
                                            dataType = None,
                                            attributesToProps = Map(),
                                            standoffClassIri = "http://www.knora.org/ontology/standoff#StandoffTableTag"
                                        ),
                                        name = "table"
                                    ))
                                ))
                            ),
                            mappingIri = "http://rdfh.ch/standoff/mappings/StandardMapping",
                            standoff = Vector(
                                StandoffTagV2(
                                    attributes = Nil,
                                    endParentIndex = None,
                                    startParentIndex = None,
                                    endIndex = None,
                                    startIndex = 0,
                                    endPosition = 25,
                                    startPosition = 0,
                                    originalXMLID = None,
                                    uuid = "fd583868-2a3c-4941-a330-990f5a972f71",
                                    dataType = None,
                                    standoffTagClassIri = "http://www.knora.org/ontology/standoff#StandoffRootTag"
                                ),
                                StandoffTagV2(
                                    attributes = Vector(StandoffTagIriAttributeV2(
                                        value = "http://rdfh.ch/0001/a-thing",
                                        standoffPropertyIri = "http://www.knora.org/ontology/knora-base#standoffTagHasLink"
                                    )),
                                    endParentIndex = None,
                                    startParentIndex = Some(0),
                                    endIndex = None,
                                    startIndex = 1,
                                    endPosition = 16,
                                    startPosition = 11,
                                    originalXMLID = None,
                                    uuid = "59a36237-95a9-4acc-8361-7c8fac311063",
                                    dataType = Some(StandoffDataTypeClasses.StandoffLinkTag),
                                    standoffTagClassIri = "http://www.knora.org/ontology/knora-base#StandoffLinkTag"
                                )
                            )
                        )),
                        valueHasString = "Na ja, die Dinge sind OK."
                    ),
                    valueIri = "http://rdfh.ch/0001/a-thing-with-text-values/values/2"
                ),
                ReadValueV2(
                    valueContent = TextValueContentV2(
                        valueType = OntologyConstants.KnoraBase.TextValue.toSmartIri,
                        comment = None,
                        standoff = Some(StandoffAndMapping(
                            XSLT = None,
                            mapping = MappingXMLtoStandoff(
                                defaultXSLTransformation = None,
                                namespace = Map("noNamespace" -> Map(
                                    "tbody" -> Map("noClass" -> XMLTag(
                                        separatorRequired = true,
                                        mapping = XMLTagToStandoffClass(
                                            dataType = None,
                                            attributesToProps = Map(),
                                            standoffClassIri = "http://www.knora.org/ontology/standoff#StandoffTableBodyTag"
                                        ),
                                        name = "tbody"
                                    )),
                                    "pre" -> Map("noClass" -> XMLTag(
                                        separatorRequired = true,
                                        mapping = XMLTagToStandoffClass(
                                            dataType = None,
                                            attributesToProps = Map(),
                                            standoffClassIri = "http://www.knora.org/ontology/standoff#StandoffPreTag"
                                        ),
                                        name = "pre"
                                    )),
                                    "ol" -> Map("noClass" -> XMLTag(
                                        separatorRequired = true,
                                        mapping = XMLTagToStandoffClass(
                                            dataType = None,
                                            attributesToProps = Map(),
                                            standoffClassIri = "http://www.knora.org/ontology/standoff#StandoffOrderedListTag"
                                        ),
                                        name = "ol"
                                    )),
                                    "hr" -> Map("noClass" -> XMLTag(
                                        separatorRequired = true,
                                        mapping = XMLTagToStandoffClass(
                                            dataType = None,
                                            attributesToProps = Map(),
                                            standoffClassIri = "http://www.knora.org/ontology/standoff#StandoffLineTag"
                                        ),
                                        name = "hr"
                                    )),
                                    "h4" -> Map("noClass" -> XMLTag(
                                        separatorRequired = true,
                                        mapping = XMLTagToStandoffClass(
                                            dataType = None,
                                            attributesToProps = Map(),
                                            standoffClassIri = "http://www.knora.org/ontology/standoff#StandoffHeader4Tag"
                                        ),
                                        name = "h4"
                                    )),
                                    "h3" -> Map("noClass" -> XMLTag(
                                        separatorRequired = true,
                                        mapping = XMLTagToStandoffClass(
                                            dataType = None,
                                            attributesToProps = Map(),
                                            standoffClassIri = "http://www.knora.org/ontology/standoff#StandoffHeader3Tag"
                                        ),
                                        name = "h3"
                                    )),
                                    "li" -> Map("noClass" -> XMLTag(
                                        separatorRequired = true,
                                        mapping = XMLTagToStandoffClass(
                                            dataType = None,
                                            attributesToProps = Map(),
                                            standoffClassIri = "http://www.knora.org/ontology/standoff#StandoffListElementTag"
                                        ),
                                        name = "li"
                                    )),
                                    "br" -> Map("noClass" -> XMLTag(
                                        separatorRequired = true,
                                        mapping = XMLTagToStandoffClass(
                                            dataType = None,
                                            attributesToProps = Map(),
                                            standoffClassIri = "http://www.knora.org/ontology/standoff#StandoffBrTag"
                                        ),
                                        name = "br"
                                    )),
                                    "u" -> Map("noClass" -> XMLTag(
                                        separatorRequired = false,
                                        mapping = XMLTagToStandoffClass(
                                            dataType = None,
                                            attributesToProps = Map(),
                                            standoffClassIri = "http://www.knora.org/ontology/standoff#StandoffUnderlineTag"
                                        ),
                                        name = "u"
                                    )),
                                    "strike" -> Map("noClass" -> XMLTag(
                                        separatorRequired = false,
                                        mapping = XMLTagToStandoffClass(
                                            dataType = None,
                                            attributesToProps = Map(),
                                            standoffClassIri = "http://www.knora.org/ontology/standoff#StandoffStrikethroughTag"
                                        ),
                                        name = "strike"
                                    )),
                                    "ul" -> Map("noClass" -> XMLTag(
                                        separatorRequired = true,
                                        mapping = XMLTagToStandoffClass(
                                            dataType = None,
                                            attributesToProps = Map(),
                                            standoffClassIri = "http://www.knora.org/ontology/standoff#StandoffUnorderedListTag"
                                        ),
                                        name = "ul"
                                    )),
                                    "a" -> Map(
                                        "noClass" -> XMLTag(
                                            separatorRequired = false,
                                            mapping = XMLTagToStandoffClass(
                                                dataType = Some(XMLStandoffDataTypeClass(
                                                    dataTypeXMLAttribute = "href",
                                                    standoffDataTypeClass = StandoffDataTypeClasses.StandoffUriTag
                                                )),
                                                attributesToProps = Map(),
                                                standoffClassIri = "http://www.knora.org/ontology/knora-base#StandoffUriTag"
                                            ),
                                            name = "a"
                                        ),
                                        "salsah-link" -> XMLTag(
                                            separatorRequired = false,
                                            mapping = XMLTagToStandoffClass(
                                                dataType = Some(XMLStandoffDataTypeClass(
                                                    dataTypeXMLAttribute = "href",
                                                    standoffDataTypeClass = StandoffDataTypeClasses.StandoffLinkTag
                                                )),
                                                attributesToProps = Map(),
                                                standoffClassIri = "http://www.knora.org/ontology/knora-base#StandoffLinkTag"
                                            ),
                                            name = "a"
                                        )
                                    ),
                                    "text" -> Map("noClass" -> XMLTag(
                                        separatorRequired = false,
                                        mapping = XMLTagToStandoffClass(
                                            dataType = None,
                                            attributesToProps = Map("noNamespace" -> Map("documentType" -> "http://www.knora.org/ontology/standoff#standoffRootTagHasDocumentType")),
                                            standoffClassIri = "http://www.knora.org/ontology/standoff#StandoffRootTag"
                                        ),
                                        name = "text"
                                    )),
                                    "strong" -> Map("noClass" -> XMLTag(
                                        separatorRequired = false,
                                        mapping = XMLTagToStandoffClass(
                                            dataType = None,
                                            attributesToProps = Map(),
                                            standoffClassIri = "http://www.knora.org/ontology/standoff#StandoffBoldTag"
                                        ),
                                        name = "strong"
                                    )),
                                    "code" -> Map("noClass" -> XMLTag(
                                        separatorRequired = true,
                                        mapping = XMLTagToStandoffClass(
                                            dataType = None,
                                            attributesToProps = Map(),
                                            standoffClassIri = "http://www.knora.org/ontology/standoff#StandoffCodeTag"
                                        ),
                                        name = "code"
                                    )),
                                    "h2" -> Map("noClass" -> XMLTag(
                                        separatorRequired = true,
                                        mapping = XMLTagToStandoffClass(
                                            dataType = None,
                                            attributesToProps = Map(),
                                            standoffClassIri = "http://www.knora.org/ontology/standoff#StandoffHeader2Tag"
                                        ),
                                        name = "h2"
                                    )),
                                    "td" -> Map("noClass" -> XMLTag(
                                        separatorRequired = true,
                                        mapping = XMLTagToStandoffClass(
                                            dataType = None,
                                            attributesToProps = Map(),
                                            standoffClassIri = "http://www.knora.org/ontology/standoff#StandoffTableCellTag"
                                        ),
                                        name = "td"
                                    )),
                                    "em" -> Map("noClass" -> XMLTag(
                                        separatorRequired = false,
                                        mapping = XMLTagToStandoffClass(
                                            dataType = None,
                                            attributesToProps = Map(),
                                            standoffClassIri = "http://www.knora.org/ontology/standoff#StandoffItalicTag"
                                        ),
                                        name = "em"
                                    )),
                                    "tr" -> Map("noClass" -> XMLTag(
                                        separatorRequired = true,
                                        mapping = XMLTagToStandoffClass(
                                            dataType = None,
                                            attributesToProps = Map(),
                                            standoffClassIri = "http://www.knora.org/ontology/standoff#StandoffTableRowTag"
                                        ),
                                        name = "tr"
                                    )),
                                    "cite" -> Map("noClass" -> XMLTag(
                                        separatorRequired = true,
                                        mapping = XMLTagToStandoffClass(
                                            dataType = None,
                                            attributesToProps = Map(),
                                            standoffClassIri = "http://www.knora.org/ontology/standoff#StandoffCiteTag"
                                        ),
                                        name = "cite"
                                    )),
                                    "blockquote" -> Map("noClass" -> XMLTag(
                                        separatorRequired = true,
                                        mapping = XMLTagToStandoffClass(
                                            dataType = None,
                                            attributesToProps = Map(),
                                            standoffClassIri = "http://www.knora.org/ontology/standoff#StandoffBlockquoteTag"
                                        ),
                                        name = "blockquote"
                                    )),
                                    "p" -> Map("noClass" -> XMLTag(
                                        separatorRequired = true,
                                        mapping = XMLTagToStandoffClass(
                                            dataType = None,
                                            attributesToProps = Map(),
                                            standoffClassIri = "http://www.knora.org/ontology/standoff#StandoffParagraphTag"
                                        ),
                                        name = "p"
                                    )),
                                    "h6" -> Map("noClass" -> XMLTag(
                                        separatorRequired = true,
                                        mapping = XMLTagToStandoffClass(
                                            dataType = None,
                                            attributesToProps = Map(),
                                            standoffClassIri = "http://www.knora.org/ontology/standoff#StandoffHeader6Tag"
                                        ),
                                        name = "h6"
                                    )),
                                    "h1" -> Map("noClass" -> XMLTag(
                                        separatorRequired = true,
                                        mapping = XMLTagToStandoffClass(
                                            dataType = None,
                                            attributesToProps = Map(),
                                            standoffClassIri = "http://www.knora.org/ontology/standoff#StandoffHeader1Tag"
                                        ),
                                        name = "h1"
                                    )),
                                    "sub" -> Map("noClass" -> XMLTag(
                                        separatorRequired = false,
                                        mapping = XMLTagToStandoffClass(
                                            dataType = None,
                                            attributesToProps = Map(),
                                            standoffClassIri = "http://www.knora.org/ontology/standoff#StandoffSubscriptTag"
                                        ),
                                        name = "sub"
                                    )),
                                    "sup" -> Map("noClass" -> XMLTag(
                                        separatorRequired = false,
                                        mapping = XMLTagToStandoffClass(
                                            dataType = None,
                                            attributesToProps = Map(),
                                            standoffClassIri = "http://www.knora.org/ontology/standoff#StandoffSuperscriptTag"
                                        ),
                                        name = "sup"
                                    )),
                                    "h5" -> Map("noClass" -> XMLTag(
                                        separatorRequired = true,
                                        mapping = XMLTagToStandoffClass(
                                            dataType = None,
                                            attributesToProps = Map(),
                                            standoffClassIri = "http://www.knora.org/ontology/standoff#StandoffHeader5Tag"
                                        ),
                                        name = "h5"
                                    )),
                                    "table" -> Map("noClass" -> XMLTag(
                                        separatorRequired = true,
                                        mapping = XMLTagToStandoffClass(
                                            dataType = None,
                                            attributesToProps = Map(),
                                            standoffClassIri = "http://www.knora.org/ontology/standoff#StandoffTableTag"
                                        ),
                                        name = "table"
                                    ))
                                ))
                            ),
                            mappingIri = "http://rdfh.ch/standoff/mappings/StandardMapping",
                            standoff = Vector(
                                StandoffTagV2(
                                    attributes = Vector(StandoffTagIriAttributeV2(
                                        value = "http://rdfh.ch/0001/a-thing",
                                        standoffPropertyIri = "http://www.knora.org/ontology/knora-base#standoffTagHasLink"
                                    )),
                                    endParentIndex = None,
                                    startParentIndex = Some(0),
                                    endIndex = None,
                                    startIndex = 1,
                                    endPosition = 19,
                                    startPosition = 14,
                                    originalXMLID = None,
                                    uuid = "80133696-26a1-4941-967b-6bf210d7d5e1",
                                    dataType = Some(StandoffDataTypeClasses.StandoffLinkTag),
                                    standoffTagClassIri = "http://www.knora.org/ontology/knora-base#StandoffLinkTag"
                                ),
                                StandoffTagV2(
                                    attributes = Nil,
                                    endParentIndex = None,
                                    startParentIndex = None,
                                    endIndex = None,
                                    startIndex = 0,
                                    endPosition = 45,
                                    startPosition = 0,
                                    originalXMLID = None,
                                    uuid = "2e136103-2a4b-4e59-ac8f-79a53f54b496",
                                    dataType = None,
                                    standoffTagClassIri = "http://www.knora.org/ontology/standoff#StandoffRootTag"
                                )
                            )
                        )),
                        valueHasString = "Ich liebe die Dinge, sie sind alles f\u00FCr mich."
                    ),
                    valueIri = "http://rdfh.ch/0001/a-thing-with-text-values/values/1"
                )
            )),
            resourceClass = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
            label = "Ein Ding f\u00FCr jemanden, dem die Dinge gefallen",
            resourceIri = "http://rdfh.ch/0001/a-thing-with-text-values"
        )),
        numberOfResources = 1
    )

    // Dear Ben: I am aware of the fact that this code is not formatted properly and I know that this deeply disturbs you. But please leave it like this since otherwise I cannot possibly read and understand this query.
    val constructQueryForBooksWithTitleZeitgloecklein = ConstructQuery(
        constructClause = ConstructClause(
            statements = Vector(
                StatementPattern(QueryVariable("book"), IriRef("http://api.knora.org/ontology/knora-api/simple/v2#isMainResource".toSmartIri, None), XsdLiteral("true", "http://www.w3.org/2001/XMLSchema#boolean".toSmartIri), None),
                StatementPattern(QueryVariable("book"), IriRef("http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#title".toSmartIri, None), QueryVariable("title"), None)),
            querySchema = Some(ApiV2Simple)
        ),
        whereClause = WhereClause(
            patterns = Vector(
                StatementPattern(QueryVariable("book"), IriRef("http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri, None), IriRef("http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#book".toSmartIri, None), None),
                StatementPattern(QueryVariable("book"), IriRef("http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri, None), IriRef("http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri, None), None),
                StatementPattern(QueryVariable("book"), IriRef("http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#title".toSmartIri, None), QueryVariable("title"), None),
                StatementPattern(IriRef("http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#title".toSmartIri, None), IriRef("http://api.knora.org/ontology/knora-api/simple/v2#objectType".toSmartIri, None), IriRef("http://www.w3.org/2001/XMLSchema#string".toSmartIri, None), None),
                StatementPattern(QueryVariable("title"), IriRef("http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri, None), IriRef("http://www.w3.org/2001/XMLSchema#string".toSmartIri, None), None),
                FilterPattern(CompareExpression(QueryVariable("title"), CompareExpressionOperator.EQUALS, XsdLiteral("Zeitglöcklein des Lebens und Leidens Christi", "http://www.w3.org/2001/XMLSchema#string".toSmartIri)))
            ),
            positiveEntities = Set(IriRef("http://api.knora.org/ontology/knora-api/simple/v2#isMainResource".toSmartIri, None),
                IriRef("http://api.knora.org/ontology/knora-api/simple/v2#objectType".toSmartIri, None),
                IriRef("http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#book".toSmartIri, None),
                IriRef("http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#title".toSmartIri, None),
                IriRef("http://www.w3.org/2001/XMLSchema#string".toSmartIri, None),
                IriRef("http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri, None),
                IriRef("http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri, None),
                QueryVariable("book"),
                QueryVariable("title")
            ),
            querySchema = Some(ApiV2Simple)
        ),
        querySchema = Some(ApiV2Simple)
    )

    val booksWithTitleZeitgloeckleinResponse = ReadResourcesSequenceV2(
        resources = Vector(
            ReadResourceV2(
                values = Map("http://www.knora.org/ontology/0803/incunabula#title".toSmartIri -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        valueType = OntologyConstants.KnoraBase.TextValue.toSmartIri,
                        comment = None,
                        standoff = None,
                        valueHasString = "Zeitgl\u00F6cklein des Lebens und Leidens Christi"
                    ),
                    valueIri = "http://rdfh.ch/c5058f3a/values/c3295339"
                ))),
                resourceClass = "http://www.knora.org/ontology/0803/incunabula#book".toSmartIri,
                label = "Zeitgl\u00F6cklein des Lebens und Leidens Christi",
                resourceIri = "http://rdfh.ch/c5058f3a"
            ),
            ReadResourceV2(
                values = Map("http://www.knora.org/ontology/0803/incunabula#title".toSmartIri -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        valueType = OntologyConstants.KnoraBase.TextValue.toSmartIri,
                        comment = None,
                        standoff = None,
                        valueHasString = "Zeitgl\u00F6cklein des Lebens und Leidens Christi"
                    ),
                    valueIri = "http://rdfh.ch/ff17e5ef9601/values/d9a522845006"
                ))),
                resourceClass = "http://www.knora.org/ontology/0803/incunabula#book".toSmartIri,
                label = "Zeitgl\u00F6cklein des Lebens und Leidens Christi",
                resourceIri = "http://rdfh.ch/ff17e5ef9601"
            )
        ),
        numberOfResources = 2
    )

    // Dear Ben: please see my comment above
    val constructQueryForBooksWithoutTitleZeitgloecklein = ConstructQuery(
        constructClause = ConstructClause(
            statements = Vector(
                StatementPattern(QueryVariable("book"), IriRef("http://api.knora.org/ontology/knora-api/simple/v2#isMainResource".toSmartIri, None), XsdLiteral("true", "http://www.w3.org/2001/XMLSchema#boolean".toSmartIri), None),
                StatementPattern(QueryVariable("book"), IriRef("http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#title".toSmartIri, None), QueryVariable("title"), None)),
            querySchema = Some(ApiV2Simple)
        ),
        whereClause = WhereClause(
            patterns = Vector(
                StatementPattern(QueryVariable("book"), IriRef("http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri, None), IriRef("http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#book".toSmartIri, None), None),
                StatementPattern(QueryVariable("book"), IriRef("http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri, None), IriRef("http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri, None), None),
                StatementPattern(QueryVariable("book"), IriRef("http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#title".toSmartIri, None), QueryVariable("title"), None),
                StatementPattern(IriRef("http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#title".toSmartIri, None), IriRef("http://api.knora.org/ontology/knora-api/simple/v2#objectType".toSmartIri, None), IriRef("http://www.w3.org/2001/XMLSchema#string".toSmartIri, None), None),
                StatementPattern(QueryVariable("title"), IriRef("http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri, None), IriRef("http://www.w3.org/2001/XMLSchema#string".toSmartIri, None), None),
                FilterPattern(CompareExpression(QueryVariable("title"), CompareExpressionOperator.NOT_EQUALS, XsdLiteral("Zeitglöcklein des Lebens und Leidens Christi", "http://www.w3.org/2001/XMLSchema#string".toSmartIri)))
            ),
            positiveEntities = Set(IriRef("http://api.knora.org/ontology/knora-api/simple/v2#isMainResource".toSmartIri, None),
                IriRef("http://api.knora.org/ontology/knora-api/simple/v2#objectType".toSmartIri, None),
                IriRef("http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#book".toSmartIri, None),
                IriRef("http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#title".toSmartIri, None),
                IriRef("http://www.w3.org/2001/XMLSchema#string".toSmartIri, None),
                IriRef("http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri, None),
                IriRef("http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri, None),
                QueryVariable("book"),
                QueryVariable("title")
            ),
            querySchema = Some(ApiV2Simple)
        ),
        querySchema = Some(ApiV2Simple)
    )
}