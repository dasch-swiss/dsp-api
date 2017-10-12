package org.knora.webapi.responders.v2

import akka.actor.ActorSystem
import org.knora.webapi.Settings
import org.knora.webapi.messages.v1.responder.valuemessages.{KnoraCalendarV1, KnoraPrecisionV1}
import org.knora.webapi.messages.v2.responder._
import org.knora.webapi.util.search._

object SearchResponderV2SpecFullData {

    implicit lazy val system = ActorSystem("webapi")

    val settings = Settings(system)

    val fulltextSearchForNarr = ReadResourcesSequenceV2(
        resources = Vector(
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 39.\nHolzschnitt zu Kap. 39: Nichts f\u00FCr sich behalten k\u00F6nnen.\nEin Narr, der hinter einem Geb\u00FCsch lauert, hat am Boden ein grosses Fangnetz gespannt. Die V\u00F6gel gehen jedoch dem Netz aus dem Weg oder fliegen davon, 11.7 x 8.5 cm.\n"
                    ),
                    valueIri = "http://data.knora.org/7f2296af8803/values/c36a38c64a26"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "g1r",
                resourceIri = "http://data.knora.org/7f2296af8803"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 85.\nHolzschnitt zu Kap. 85: Des Todes nicht eingedenk sein.\nEin Narr, der ein Schellenb\u00FCndel in H\u00E4nden h\u00E4lt, wendet sich erschrocken um, als ihm der Tod in Gestalt eines Skeletts, das eine Bahre auf der Schulter tr\u00E4gt, am Rockzipfel packt und ihm zuruft \u201Ed\u00FC blibst\", 11.7 x 8.4 cm."
                    ),
                    valueIri = "http://data.knora.org/27f8965ae203/values/1a7803437c26"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "m6r",
                resourceIri = "http://data.knora.org/27f8965ae203"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn der handschriftlichen Erg\u00E4nzung der fehlenden ersten Lage; Feder (Braun); Rubrizierung in Rot: \"Ein Vorred in das narr(e)nschiff\"  (Hartl 2001: Narrenschiff II).\nOben links Nummerierung (Graphitstift): \"1.\"."
                    ),
                    valueIri = "http://data.knora.org/3794b4b22703/values/5e1619729426"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "(2)r",
                resourceIri = "http://data.knora.org/3794b4b22703"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 69.\nHolzschnitt Lemmer 1979, S. 117: Variante zu Kap. 69.\nEin Narr, der vor einer Stadtkulisse steht, hat mit seiner Rechten einen Ball in die Luft geworfen und schl\u00E4gt mit seiner Linken einen Mann, der sogleich nach seinem Dolch greift. Ein junger Mann beobachtet das Geschehen.\nDer Bildinhalt stimmt weitgehend mit dem urspr\u00FCnglichen Holzschnitt \u00FCberein.\n11.7 x 8.4 cm.\nUnkoloriert."
                    ),
                    valueIri = "http://data.knora.org/e28579ba4f03/values/a2f1044e7326"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "m1v",
                resourceIri = "http://data.knora.org/e28579ba4f03"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 11.\nHolzschnitt zu Kap. 11: Von Missachtung der Heiligen Schrift.\nEin Narr, der zwei B\u00FCcher mit F\u00FCssen tritt, spricht mit einem in ein Leichentuch geh\u00FCllten, wiedererweckten Toten, der auf seiner Bahre hockt.\n11.6 x 8.5 cm.\nUnkoloriert."
                    ),
                    valueIri = "http://data.knora.org/fe2fcadf2d03/values/a45436933026"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "b7v",
                resourceIri = "http://data.knora.org/fe2fcadf2d03"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = " Beginn Kapitel 31.\nHolzschnitt zu Kap. 31: Vom Hinausschieben auf morgen.\nEin Narr steht mit ausgebreiteten Armen auf einer Strasse. Auf seinen H\u00E4nden sitzen zwei Raben, die beide \u201ECras\u201C \u2013 das lateinische Wort f\u00FCr \u201Emorgen\u201C \u2013 rufen. Auf dem Kopf des Narren sitzt ein Papagei und ahmt den Ruf der Kr\u00E4hen nach. 11.6 x 8.5 cm."
                    ),
                    valueIri = "http://data.knora.org/b9d1c37b8503/values/894d14e43c26"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "f2r",
                resourceIri = "http://data.knora.org/b9d1c37b8503"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 105.\nHolzschnitt identisch mit Kap. 95: In einer Landschaft fasst ein Narr, der ein Zepter in der Linken h\u00E4lt, einem Mann an die Schulter und redet auf ihn ein, er m\u00F6ge die Feiertage missachten, 11.7 x 8.6 cm."
                    ),
                    valueIri = "http://data.knora.org/661e1505ee03/values/916bd4eb8926"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "p7v",
                resourceIri = "http://data.knora.org/661e1505ee03"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 12.\nHolzschnitt zu Kap. 12: Von Unbedachtsamkeit.\nEin Narr f\u00E4llt von seinem Esel, weil er den Sattelgurt nicht geschnallt hat.\n11.7 x 8.5 cm.\nUnkoloriert."
                    ),
                    valueIri = "http://data.knora.org/883be8542e03/values/edd12f3f3126"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "b8v",
                resourceIri = "http://data.knora.org/883be8542e03"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 19.\nHolzschnitt zu Kap. 19: Von \u00FCberfl\u00FCssigen Schw\u00E4tzen.\nEin Narr steht mit herausgestreckter Zunge unter einem Baum. Er erblickt in dessen Krone das Nest eines Spechts, der unten ein Loch in den Stamm h\u00E4mmert.\n11.6 x 8.5 cm.\nUnkoloriert.\n"
                    ),
                    valueIri = "http://data.knora.org/62a3f6723203/values/1d6e62d43426"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "d1v",
                resourceIri = "http://data.knora.org/62a3f6723203"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 19.\nHolzschnitt zu Kap. 19: Von \u00FCberfl\u00FCssigen Schw\u00E4tzen.\nEin Narr steht mit herausgestreckter Zunge unter einem Baum. Er erblickt in dessen Krone das Nest eines Spechts, der unten ein Loch in den Stamm h\u00E4mmert. 11.6 x 8.5 cm.\n"
                    ),
                    valueIri = "http://data.knora.org/41475bfe7f03/values/5a440f9b3426"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "d6r",
                resourceIri = "http://data.knora.org/41475bfe7f03"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 41.\nHolzschnitt zu Kap. 41: Nachrede unbeachtet lassen.\nEin Narr hantiert mit einem Mehlsack. Rechts steht eine grosse Glocke mit einem Fuchsschwanz als Kl\u00F6ppel, 11.7 x 8.4 cm.\n"
                    ),
                    valueIri = "http://data.knora.org/f9078baece03/values/923bd8e44b26"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "g3r",
                resourceIri = "http://data.knora.org/f9078baece03"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 38.\nHolzschnitt zu Kap. 38: Von eigensinnigen Kranken.\nEin kranker Narr, der im Bett liegt, st\u00F6sst mit seinen Beinen einen Tisch um, auf dem seine Arzneien liegen. Zu beiden Seiten des Bettes stehen ein Arzt und eine Frau, 11.7 x 8.4 cm."
                    ),
                    valueIri = "http://data.knora.org/5be5304fcd03/values/b7c3ebe04926"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "f8r",
                resourceIri = "http://data.knora.org/5be5304fcd03"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 101.\nHolzschnitt zu Kap. 101: Von der Ohrenbl\u00E4serei\nVor einer Landschaftskulisse fl\u00FCstert ein Narr, der ein Zepter in seiner Rechten h\u00E4lt, einem anderen Narr, der links neben ihm steht und sich begierig zur Seite neigt, etwas ins Ohr, 11.6 x 8.4 cm."
                    ),
                    valueIri = "http://data.knora.org/4e16c6a6a603/values/6d76ee3b8726"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "p2v",
                resourceIri = "http://data.knora.org/4e16c6a6a603"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 94.\nHolzschnitt zu Kap. 94: Von der Hoffnung, andere zu beerben.\nEin Narr beschl\u00E4gt die Vorderhufe seines Esels auf dessen R\u00FCcken der Tod in Gestalt eines Skeletts rittlings sitzt. Er schwingt einen Knochen, um so die Fr\u00FCchte aus der Krone eines nahen Baumes zu schlagen, 11.7 x 8.4 cm."
                    ),
                    valueIri = "http://data.knora.org/50715228e703/values/abdfc84e8226"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "n8v",
                resourceIri = "http://data.knora.org/50715228e703"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 76.\nHolzschnitt zu Kap. 76: Von Prahlerei und \u00DCberhebung.\nIn einem Innenraum st\u00FCtzt sich ein alter Narr auf einen Stab. Er tr\u00E4gt eine Kette um den Hals, an der ein viergeteilter Wappenschild h\u00E4ngt, auf dem der je zwei Baselst\u00E4be und Affen zu sehen sind. \u00DCber ihm schwebt eine Banderole mit dem Namen \u201ERitter Peter\u201C. Vor ihm sitzt ein Gelehrter im Narrenkost\u00FCm an einem Tisch und beugt sich vor, um den Ritter am Ohr zu ziehen. Auch \u00FCber ihm schwebt eine Banderole, auf der sein Name \u201EDoctor griff\u201C zu lesen ist.\n11.7 x 8.4 cm.\nUnkoloriert.\noben rechts Blattnummerierung (Graphitstift): \"99\".\n"
                    ),
                    valueIri = "http://data.knora.org/81f316135403/values/0f64e4a97626"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "n3r",
                resourceIri = "http://data.knora.org/81f316135403"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 54.\nHolzschnitt zu Kap. 54: Sich nicht bessern wollen.\nEin Narr steht auf einer Strasse und bl\u00E4st auf einem Dudelsack w\u00E4hrend er Laute und Harfe unbeachtet am Boden liegen l\u00E4sst, 11.7 x 8.4 cm."
                    ),
                    valueIri = "http://data.knora.org/e786d5b6d303/values/f93ba6776b26"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "h6r",
                resourceIri = "http://data.knora.org/e786d5b6d303"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 71.\nHolzschnitt zu Kap. 71: Von Streit- und Prozesss\u00FCchtigen.\nIn einem Innenraum steht ein Narr, dem ein Hechelbrett im Hintern steckt. W\u00E4hrend er Gefahr l\u00E4uft, auf weitere derartige Bretter, die am Boden liegen, zu treten, verbindet er der vor ihm sitzenden Personifikation der Justitia die Augen.\n11.7 x 8.5 cm."
                    ),
                    valueIri = "http://data.knora.org/25621068dc03/values/121d6d8e6826"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "l1r",
                resourceIri = "http://data.knora.org/25621068dc03"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 60.\nHolzschnitt zu Kap. 60: Von Selbstgef\u00E4lligkeit.\nEin alter Narr steht am Ofen und r\u00FChrt in einem Topf. Gleichzeitig schaut er sich dabei in einem Handspiegel an.\n11.7 x 8.5 cm.\nUnkoloriert."
                    ),
                    valueIri = "http://data.knora.org/ccd8b6dd4803/values/29d8d80c6f26"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "k2v",
                resourceIri = "http://data.knora.org/ccd8b6dd4803"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 71.\nHolzschnitt zu Kap. 71: Von Streit- und Prozesss\u00FCchtigen.\nIn einem Innenraum steht ein Narr, dem ein Hechelbrett im Hintern steckt. W\u00E4hrend er Gefahr l\u00E4uft, auf weitere derartige Bretter, die am Boden liegen, zu treten, verbindet er der vor ihm sitzenden Personifikation der Justitia die Augen.\n11.7 x 8.5 cm.\nUnkoloriert."
                    ),
                    valueIri = "http://data.knora.org/f69cb5a45003/values/ae9851337426"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "m3v",
                resourceIri = "http://data.knora.org/f69cb5a45003"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 8.\nHolzschnitt zu Kap. 8: Guten Rat verschm\u00E4hen.\nEin Narr, dessen Linke mit einem Falknerhandschuh gesch\u00FCtzt wird, auf dem ein Vogel sitzt, lenkt einen Pflug, den ein zweiter Narr zieht.\n11.6 x 8.6 cm."
                    ),
                    valueIri = "http://data.knora.org/b996c90ac003/values/8fbf25ad2026"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "c3r",
                resourceIri = "http://data.knora.org/b996c90ac003"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 81.\nHolzschnitt zu Kap. 81: Aus K\u00FCche und Keller.\nEin Narr f\u00FChrt von einem Boot aus vier Knechte am Strick, die sich in einer K\u00FCche \u00FCber Spreis und Trank hermachen, w\u00E4hrend eine Frau, die am Herdfeuer sitzt, das Essen zubereitet, 11.7 x 8.5 cm."
                    ),
                    valueIri = "http://data.knora.org/75be0011e003/values/f6821d937926"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "m1r",
                resourceIri = "http://data.knora.org/75be0011e003"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 18.\nHolzschnitt zu Kap. 18: Vom Dienst an zwei Herren.\nEin mit Spiess bewaffneter Narr bl\u00E4st in ein Horn. Sein Hund versucht derweil im Hintergrund zwei Hasen gleichzeitig zu erjagen.\n11.6 x 8.4 cm.\nUnkoloriert.\n"
                    ),
                    valueIri = "http://data.knora.org/d897d8fd3103/values/d4f068283426"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "c8v",
                resourceIri = "http://data.knora.org/d897d8fd3103"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 101.\nHolzschnitt zu Kap. 101: Von der Ohrenbl\u00E4serei\nVor einer Landschaftskulisse fl\u00FCstert ein Narr, der ein Zepter in seiner Rechten h\u00E4lt, einem anderen Narr, der links neben ihm steht und sich begierig zur Seite neigt, etwas ins Ohr.\n11.6 x 8.4 cm.\nUnkoloriert.\nOben rechts Blattnummerierung (Graphitstift): \"136\"."
                    ),
                    valueIri = "http://data.knora.org/739e6e006503/values/30a041758726"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "r8r",
                resourceIri = "http://data.knora.org/739e6e006503"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 111.\nHolzschnitt zu Kap. 111: Entschuldigung des Dichters\nEin Narr hat seine Attribute \u2013 die Kappe und das Zepter - hinter sich abgelegt und kniet betend vor einem Altar. In seinen H\u00E4nden h\u00E4lt er die Kappe des Gelehrten.  Von hinten n\u00E4hert sich eine f\u00FCnfk\u00F6pfige Narrenschar und kommentiert das Geschehen mit erregten Gesten, 11.6 x 8.4 cm.\n"
                    ),
                    valueIri = "http://data.knora.org/2a145bb7b003/values/22d399f78f26"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "r8v",
                resourceIri = "http://data.knora.org/2a145bb7b003"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 93.\nHolzschnitt zu Kap. 93: Von Wucher und Preistreiberei.\nVor einer st\u00E4dtischen Kulisse steht ein Mann, der in seinen Geldbeutel greift. Am rechten Rand verhandelt ein Narr mit ihm. Zwischen den beiden erkennt man Getreides\u00E4cke, links lagern Weinf\u00E4sser, 11.5 x 8.4 cm."
                    ),
                    valueIri = "http://data.knora.org/c66534b3e603/values/6262cfa28126"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "n7v",
                resourceIri = "http://data.knora.org/c66534b3e603"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 89.\nHolzschnitt zu Kap. 89: Von t\u00F6richtem Tausch.\nEin Narr, der in einer Landschaft einem Mann begegnet, tauscht mit diesem sein Maultier gegen einen Dudelsack.\n11.7 x 8.3 cm.\nUnkoloriert.\nOben rechts Blattnummerierung (Graphitstift): \"119\"."
                    ),
                    valueIri = "http://data.knora.org/49da6f395d03/values/c4c08f657f26"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "p7r",
                resourceIri = "http://data.knora.org/49da6f395d03"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 59.\nHolzschnitt zu Kap. 59: Vom Danken und Lohnen.\nEin Narr begleitet einen Gelehrten und ist dabei so ins Gespr\u00E4ch vertieft, dass er einen anderen Mann missachtet. Dieser schl\u00E4gt deshalb den Narren mit einer Pritsche.\n11.7 x 8.5 cm.\nUnkoloriert."
                    ),
                    valueIri = "http://data.knora.org/42cd98684803/values/a384329a6e26"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "k1v",
                resourceIri = "http://data.knora.org/42cd98684803"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 35.\nHolzschnitt zu Kap. 35: Leicht zornig werden.\nEin Esel bleibt st\u00F6rrisch stehen, w\u00E4hrend ein Narr, der auf ihm sitzt, eine Peitsche schwingt und mit seinen Sporen gegen die Ohren tritt. Auch eine Frau, die an seinem Schweif zieht und ein Hund, der ihn anbellt, treiben den Esel so wenig an, dass eine Schnecke ihn am Boden begleiten kann, 11.6 x 8.4 cm.\n"
                    ),
                    valueIri = "http://data.knora.org/47cef464cc03/values/b856192d4526"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "f6r",
                resourceIri = "http://data.knora.org/47cef464cc03"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 29.\nHolzschnitt zu Kap. 29: Von Verkennung der Mitmenschen.\nEin Narr verspottet einen Sterbenden, neben dessen Bett eine Frau betet, w\u00E4hrend sich unter dem Narren die H\u00F6lle in Gestalt eines gefr\u00E4ssigen Drachenkopfs auftut.\n11.7 x 8.5 cm.\nUnkoloriert.\n"
                    ),
                    valueIri = "http://data.knora.org/da2d5ff03703/values/3429ce523b26"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "e5v",
                resourceIri = "http://data.knora.org/da2d5ff03703"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 76.\nHolzschnitt zu Kap. 76: Von Prahlerei und \u00DCberhebung.\nIn einem Innenraum st\u00FCtzt sich ein alter Narr auf einen Stab. Er tr\u00E4gt eine Kette um den Hals, an der ein viergeteilter Wappenschild h\u00E4ngt, auf dem der je zwei Baselst\u00E4be und Affen zu sehen sind. \u00DCber ihm schwebt eine Banderole mit dem Namen \u201ERitter Peter\u201C. Vor ihm sitzt ein Gelehrter im Narrenkost\u00FCm an einem Tisch und beugt sich vor, um den Ritter am Ohr zu ziehen. Auch \u00FCber ihm schwebt eine Banderole, auf der sein Name \u201EDoctor griff\u201C zu lesen ist, 11.7 x 8.4 cm."
                    ),
                    valueIri = "http://data.knora.org/5db6b1b29803/values/4c3a91707626"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "l4r",
                resourceIri = "http://data.knora.org/5db6b1b29803"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 85.\nHolzschnitt zu Kap. 85: Des Todes nicht eingedenk sein.\nEin Narr, der ein Schellenb\u00FCndel in H\u00E4nden h\u00E4lt, wendet sich erschrocken um, als ihm der Tod in Gestalt eines Skeletts, das eine Bahre auf der Schulter tr\u00E4gt, am Rockzipfel packt und ihm zuruft \u201Ed\u00FC blibst\", 11.7 x 8.4 cm."
                    ),
                    valueIri = "http://data.knora.org/c129de459d03/values/dda1567c7c26"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "m6r",
                resourceIri = "http://data.knora.org/c129de459d03"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 23.\nHolzschnitt zu Kap. 23: Vom blinden Vertrauen auf das Gl\u00FCck.\nEin Narr schaut oben aus dem Fenster seines Hauses, das unten lichterloh brennt. Am Himmel erscheint die r\u00E4chende Gotteshand, die mit einen Hammer auf Haus und Narr einschl\u00E4gt. Auf der Fahne \u00FCber dem Erker des Hauses ist der Baselstab zu erkennen.\n11.5 x 8.2 cm.\nUnkoloriert.\n"
                    ),
                    valueIri = "http://data.knora.org/14dd8cbc3403/values/7e39f54a3726"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "d6v",
                resourceIri = "http://data.knora.org/14dd8cbc3403"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 45.\nHolzschnitt zu Kap. 45: Von selbstverschuldetem Ungl\u00FCck.\nIn Gestalt eines Narren springt Empedokles in den lodernden Krater des \u00C4tna. Im Vordergrund l\u00E4sst sich ein anderer Narr in einen Brunnen fallen. Beide werden von drei M\u00E4nnern beobachtet, die das Verhalten mit \u201EJn geschicht recht\u201C  kommentieren, 11.7 x 8.3 cm.\n"
                    ),
                    valueIri = "http://data.knora.org/21360383d003/values/b630be944e26"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "g7r",
                resourceIri = "http://data.knora.org/21360383d003"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 86.\nHolzschnitt zu Kap. 86: Gott nicht ernst nehmen.\nEin Narr zupft dem in einer Landschaft stehenden Erl\u00F6ser, der die bekr\u00F6nte Weltkugel in der Linken h\u00E4lt, die Rechten zum Segen erhebt und dessen Haupt von einer Aureole umgeben wird, am Bart. Er wird daf\u00FCr bestraft, indem ein Schauer von Steinen oder Feuergeschossen vom Himmel herab auf ihn niedergeht.\n11.6 x 8.4 cm.\nUnkoloriert."
                    ),
                    valueIri = "http://data.knora.org/e6b1869f5b03/values/e948a3617d26"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "p3v",
                resourceIri = "http://data.knora.org/e6b1869f5b03"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 50.\nHolzschnitt zu Kap. 50: Von der Wollust.\nDie als n\u00E4rrischen Frau personifizierte Wollust f\u00FChrt einen Vogel, ein Schaf und einen Ochsen an Seilen. Ein Narr packt die beiden grossen Tiere beim Schwanz.\n11.7 x 8.5 cm.\nUnkoloriert."
                    ),
                    valueIri = "http://data.knora.org/544e4e604303/values/987013016926"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "h6v",
                resourceIri = "http://data.knora.org/544e4e604303"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 102.\nHolzschnitt zu Kap. 102: Von Falschheit und Betrug\nEin Narr hantiert mit Ger\u00E4tschaften aus der Alchimistenk\u00FCche an einem Ofen. Ihm assistiert eine Gelehrter, der Hinter ihm steht. Ein zweiter Narr hockt vor einem Weinfass und r\u00FChrt mit einem Knochen in dessen Inhalt herum, 11.6 x 8.4 cm."
                    ),
                    valueIri = "http://data.knora.org/d821e41ba703/values/b6f3e7e78726"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "p3v",
                resourceIri = "http://data.knora.org/d821e41ba703"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 34.\nHolzschnitt zu Kap. 34: Ein Narr sein und es bleiben.\nEin Narr wird von drei G\u00E4nsen umgeben, deren eine von ihm wegfliegt, 11.7 x 8.4 cm.\n"
                    ),
                    valueIri = "http://data.knora.org/bdc2d6efcb03/values/a00880624326"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "f5r",
                resourceIri = "http://data.knora.org/bdc2d6efcb03"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 41.\nHolzschnitt zu Kap. 41: Nachrede unbeachtet lassen.\nEin Narr hantiert mit einem Mehlsack. Rechts steht eine grosse Glocke mit einem Fuchsschwanz als Kl\u00F6ppel, 11.7 x 8.4 cm.\n"
                    ),
                    valueIri = "http://data.knora.org/9339d2998903/values/55652b1e4c26"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "g3r",
                resourceIri = "http://data.knora.org/9339d2998903"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 45.\nHolzschnitt zu Kap. 45: Von selbstverschuldetem Ungl\u00FCck.\nIn Gestalt eines Narren springt Empedokles in den lodernden Krater des \u00C4tna. Im Vordergrund l\u00E4sst sich ein anderer Narr in einen Brunnen fallen. Beide werden von drei M\u00E4nnern beobachtet, die das Verhalten mit \u201EJn geschicht recht\u201C  kommentieren, 11.7 x 8.3 cm.\n"
                    ),
                    valueIri = "http://data.knora.org/bb674a6e8b03/values/795a11ce4e26"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "g7r",
                resourceIri = "http://data.knora.org/bb674a6e8b03"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 50.\nHolzschnitt zu Kap. 50: Von der Wollust.\nDie als n\u00E4rrischen Frau personifizierte Wollust f\u00FChrt einen Vogel, ein Schaf und einen Ochsen an Seilen. Ein Narr packt die beiden grossen Tiere beim Schwanz.\n11.7 x 8.5 cm."
                    ),
                    valueIri = "http://data.knora.org/bf585de2d103/values/fd875ca85826"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "h2r",
                resourceIri = "http://data.knora.org/bf585de2d103"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 74.\nHolzschnitt zu Kap. 74: Von n\u00E4rrischer J\u00E4gerei.\nHolzschnitt identisch mit Kap. 18: Ein mit Spiess bewaffneter Narr bl\u00E4st in ein Horn. Sein Hund versucht derweil im Hintergrund zwei Hasen gleichzeitig zu erjagen.\n11.6 x 8.4 cm.\nUnkoloriert."
                    ),
                    valueIri = "http://data.knora.org/a8d64bee5203/values/03bd97c47526"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "m8v",
                resourceIri = "http://data.knora.org/a8d64bee5203"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 78.\nHolzschnitt zu Kap. 78: Von Narren, die sich selbst Bedr\u00FCckung verschaffen.\nEin Narr, dem ein Esel auf den R\u00FCcken zu springen versucht, st\u00FCrzt zu Boden.\n11.6 x 8.4 cm.\nUnkoloriert.\nOben rechts Blattnummerierung (Graphitstift): \"103\"."
                    ),
                    valueIri = "http://data.knora.org/a9218fe75503/values/a15ed7017826"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "n7r",
                resourceIri = "http://data.knora.org/a9218fe75503"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 95.\nHolzschnitt Lemmer 1979, S. 120: Neue Illustration zu Kap. 95\nEin Narr kniet vor einem Wagen, um ein Rad zu wechseln. Im Wagen tummeln sich zahlreiche Affen. Hinter dem Wagen ist eine Kirche zu sehen.\n11.6 x 8.3 cm.\nUnkoloriert."
                    ),
                    valueIri = "http://data.knora.org/d430d1a76003/values/7ab0686d8326"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "q6v",
                resourceIri = "http://data.knora.org/d430d1a76003"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 1.\nHolzschnitt zu Kap. 81: Aus K\u00FCche und Keller.\nEin Narr f\u00FChrt von einem Boot aus vier Knechte am Strick, die sich in einer K\u00FCche \u00FCber Spreis und Trank hermachen, w\u00E4hrend eine Frau, die am Herdfeuer sitzt, das Essen zubereitet.\n11.7 x 8.5 cm.\nUnkoloriert.\nOben rechts Blattnummerierung (Graphitstift): \"106\"."
                    ),
                    valueIri = "http://data.knora.org/4744e9465703/values/7cd6c3057a26"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "o2r",
                resourceIri = "http://data.knora.org/4744e9465703"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 24.\nHolzschnitt zu Kap. 24: Sich um zu viel bek\u00FCmmern.\nEin Narr beugt sich unter der Last einer Erdkugel, die er auf seinen Schultern tr\u00E4gt.\n11.7 x 8.4 cm.\nUnkoloriert.\n"
                    ),
                    valueIri = "http://data.knora.org/9ee8aa313503/values/8ae041303826"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "d7v",
                resourceIri = "http://data.knora.org/9ee8aa313503"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 32.\nHolzschnitt zu Kap. 32: Vom Bewachen der Frauentugend.\nIm Vordergrund giesst ein Narr Wasser in einen Brunnen, ein zweiter reinigt Ziegelsteine. Dahinter steht ein dritter Narr, der eine Heuschreckenherde h\u00FCtet. Er blickt zu einem Geb\u00E4ude, aus dessen Fenster eine Frau schaut und ihm \u201Ehu(e)t fast\u201C zuruft.\n11.7 x 8.6 cm.\nUnkoloriert."
                    ),
                    valueIri = "http://data.knora.org/7850b94f3903/values/4c77671d3d26"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "e8v",
                resourceIri = "http://data.knora.org/7850b94f3903"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 70.\nHolzschnitt zu Kap. 70: Nicht Vorsorgen in der Zeit.\nEin \u00E4rmlich gekleideter Narr, der einen Strick mit sich tr\u00E4gt zieht umher. Rechts unten kauert ein B\u00E4r, der an seiner Pfote saugt. Hinten sammeln Insekten einen Futtervorrat.\n11.7 x 8.5 cm."
                    ),
                    valueIri = "http://data.knora.org/358839de9603/values/651b58877326"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "k8r",
                resourceIri = "http://data.knora.org/358839de9603"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 72.\nHolzschnitt zu Kap. 72: Von den Grobianern.\nVor einer Landschaftskulisse steht ein Narr, der sich einem Schwein zuwendet, dass eine Krone auf dem Kopf und eine Glocke um den Hals tr\u00E4gt, 11.7 x 8.5 cm.\nUnkoloriert."
                    ),
                    valueIri = "http://data.knora.org/80a8d3195103/values/f7154bdf7426"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "m4v",
                resourceIri = "http://data.knora.org/80a8d3195103"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 4.\nHolzschnitt zu Kap. 4: Von neumodischen Sitten.\nEin alter Narr mit Becher h\u00E4lt einem jungen Mann in modischer Tracht einen Spiegel vor. Zwischen Narr und J\u00FCngling steht der Name \u201E.VLI.\u201C; \u00FCber den beiden schwebt eine Banderole mit der Aufschrift \u201Evly . von . stouffen .  . frisch . vnd vngschaffen\u201C; zwischen den F\u00FCssen des J\u00FCnglings ist die Jahreszahl \u201E.1.4.9.4.\u201C zu lesen.\n11.6 x 8.5 cm."
                    ),
                    valueIri = "http://data.knora.org/075d33c1bd03/values/77718ce21e26"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "b6r",
                resourceIri = "http://data.knora.org/075d33c1bd03"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 69.\nHolzschnitt Lemmer 1979, S. 117: Variante zu Kap. 69.\nEin Narr, der vor einer Stadtkulisse steht, hat mit seiner Rechten einen Ball in die Luft geworfen und schl\u00E4gt mit seiner Linken einen Mann, der sogleich nach seinem Dolch greift. Ein junger Mann beobachtet das Geschehen.\nDer Bildinhalt stimmt weitgehend mit dem urspr\u00FCnglichen Holzschnitt \u00FCberein.\n11.7 x 8.4 cm."
                    ),
                    valueIri = "http://data.knora.org/114bd47ddb03/values/c99f73e26726"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "k7r",
                resourceIri = "http://data.knora.org/114bd47ddb03"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 4.\nHolzschnitt zu Kap. 4: Von neumodischen Sitten.\nEin alter Narr mit Becher h\u00E4lt einem jungen Mann in modischer Tracht einen Spiegel vor. Zwischen Narr und J\u00FCngling steht der Name \u201E.VLI.\u201C; \u00FCber den beiden schwebt eine Banderole mit der Aufschrift \u201Evly . von . stouffen .  . frisch . vnd vngschaffen\u201C; zwischen den F\u00FCssen des J\u00FCnglings ist die Jahreszahl \u201E.1.4.9.4.\u201C zu lesen.\n11.6 x 8.5 cm."
                    ),
                    valueIri = "http://data.knora.org/a18e7aac7803/values/81f27d2f2926"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "b6r",
                resourceIri = "http://data.knora.org/a18e7aac7803"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 111.\nHolzschnitt zu Kap. 111: Entschuldigung des Dichters\nEin Narr hat seine Attribute \u2013 die Kappe und das Zepter - hinter sich abgelegt und kniet betend vor einem Altar. In seinen H\u00E4nden h\u00E4lt er die Kappe des Gelehrten.  Von hinten n\u00E4hert sich eine f\u00FCnfk\u00F6pfige Narrenschar und kommentiert das Geschehen mit erregten Gesten, 11.6 x 8.4 cm.\n"
                    ),
                    valueIri = "http://data.knora.org/90e213ccf503/values/e5fcec309026"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "r8v",
                resourceIri = "http://data.knora.org/90e213ccf503"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 109.\nHolzschnitt zu Kap. 109: Von Verachtung des Ungl\u00FCcks\nEin Narr hat sich in einem Boot zu weit vom Ufer entfernt. Nun birst der Schiffsrumpf, das Segel flattert haltlos umher. Der Narr h\u00E4lt sich an einem Seil der Takelage fest, 11.6 x 8.4 cm."
                    ),
                    valueIri = "http://data.knora.org/04416f64ef03/values/6ce3c0ef8b26"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "q2v",
                resourceIri = "http://data.knora.org/04416f64ef03"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 60.\nHolzschnitt zu Kap. 60: Von Selbstgef\u00E4lligkeit.\nEin alter Narr steht am Ofen und r\u00FChrt in einem Topf. Gleichzeitig schaut er sich dabei in einem Handspiegel an.\n11.7 x 8.5 cm."
                    ),
                    valueIri = "http://data.knora.org/23cc8975d603/values/a63dbb7e6026"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "i4r",
                resourceIri = "http://data.knora.org/23cc8975d603"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 89.\nHolzschnitt zu Kap. 89: Von t\u00F6richtem Tausch.\nEin Narr, der in einer Landschaft einem Mann begegnet, tauscht mit diesem sein Maultier gegen einen Dudelsack, 11.7 x 8.3 cm."
                    ),
                    valueIri = "http://data.knora.org/7363748f9f03/values/01973c2c7f26"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "n3r",
                resourceIri = "http://data.knora.org/7363748f9f03"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 14.\nHolzschnitt zu Kap. 14: Von falschen Vorstellungen \u00FCber Gott.\nEin Narr, der ein Kummet um seinen Hals tr\u00E4gt, geht mit B\u00FCchse und L\u00F6ffel auf einen Trog zu, aus dem Schweine und G\u00E4nse fressen.\n11.7 x 8.5 cm.\nUnkoloriert."
                    ),
                    valueIri = "http://data.knora.org/265e42b42f03/values/39fc0a833e25"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "c3v",
                resourceIri = "http://data.knora.org/265e42b42f03"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 58.\nHolzschnitt zu Kap. 58: Sich um die Angelegenheiten anderer k\u00FCmmern.\nEin Narr versucht mit einem Wassereimer den Brand im Haus des Nachbarn zu l\u00F6schen und wird dabei von einem anderen Narren, der an seinem Mantel zerrt, unterbrochen, den hinter ihm steht auch sein eigenes Haus in Flammen.\n11.6 x 8.5 cm."
                    ),
                    valueIri = "http://data.knora.org/0fb54d8bd503/values/9a966e995f26"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "i2r",
                resourceIri = "http://data.knora.org/0fb54d8bd503"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 66.\nHolzschnitt zu Kap. 66: Von der Erforschung der Welt.\nEin Narr hat ein Schema des Universums auf den Boden Gezeichnet und vermisst es mit einem Zirkel. Von hinten blickt ein zweiter Narr \u00FCber eine Mauer und wendet sich dem ersten mit sp\u00F6ttischen Gesten zu.\n11.6 x 8.4 cm.\nUnkoloriert."
                    ),
                    valueIri = "http://data.knora.org/a640c5fb4c03/values/10f711f67126"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "l3v",
                resourceIri = "http://data.knora.org/a640c5fb4c03"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 45.\nHolzschnitt zu Kap. 45: Von selbstverschuldetem Ungl\u00FCck.\nIn Gestalt eines Narren springt Empedokles in den lodernden Krater des \u00C4tna. Im Vordergrund l\u00E4sst sich ein anderer Narr in einen Brunnen fallen. Beide werden von drei M\u00E4nnern beobachtet, die das Verhalten mit \u201EJn geschicht recht\u201C  kommentieren.\n11.7 x 8.3 cm.\nUnkoloriert."
                    ),
                    valueIri = "http://data.knora.org/8efd7b2c4003/values/f3066b5b4e26"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "g7v",
                resourceIri = "http://data.knora.org/8efd7b2c4003"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 28.\nHolzschnitt zu Kap. 28: Vom N\u00F6rgeln an Gottes Werken.\nEin Narr, der auf einem Berg ein Feuer entfacht hat, h\u00E4lt seine Hand sch\u00FCtzend \u00FCber die Augen, w\u00E4hrend er seinen Blick auf die hell am Himmel strahlende Sonne richtet. 11.7 x 8.5 cm."
                    ),
                    valueIri = "http://data.knora.org/1baf691c8403/values/2882816d3a26"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "e7r",
                resourceIri = "http://data.knora.org/1baf691c8403"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 37.\nHolzschnitt zu Kap. 37: Von der Wandelbarkeit des Gl\u00FCcks.\nHolzschnitt identisch mit Kap. 56. Die Hand Gottes treibt das Gl\u00FCcksrad an, auf dem ein Esel und zwei Mischwesen \u2013 halb Esel, halb Narr \u2013 herumgewirbelt werden. Der von der Abw\u00E4rtsbewegung des Rades Mitgerissene st\u00FCrzt in das unter ihm ausgehobene Grab, dessen Deckel im Vordergrund bereit liegt.\n11.5 x 8.4 cm."
                    ),
                    valueIri = "http://data.knora.org/6b0b5ac58703/values/25c9f8884826"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "f7r",
                resourceIri = "http://data.knora.org/6b0b5ac58703"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 32.\nHolzschnitt zu Kap. 32: Vom Bewachen der Frauentugend.\nIm Vordergrund giesst ein Narr Wasser in einen Brunnen, ein zweiter reinigt Ziegelsteine. Dahinter steht ein dritter Narr, der eine Heuschreckenherde h\u00FCtet. Er blickt zu einem Geb\u00E4ude, aus dessen Fenster eine Frau schaut und ihm \u201Ehu(e)t fast\u201C zuruft. 11.7 x 8.6 cm.\n"
                    ),
                    valueIri = "http://data.knora.org/43dde1f08503/values/0fa1ba563d26"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "f3r",
                resourceIri = "http://data.knora.org/43dde1f08503"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 59.\nHolzschnitt zu Kap. 59: Vom Danken und Lohnen.\nEin Narr begleitet einen Gelehrten und ist dabei so ins Gespr\u00E4ch vertieft, dass er einen anderen Mann missachtet. Dieser schl\u00E4gt deshalb den Narren mit einer Pritsche.\n11.7 x 8.5 cm."
                    ),
                    valueIri = "http://data.knora.org/33f2b2eb9003/values/e05adf606e26"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "i3r",
                resourceIri = "http://data.knora.org/33f2b2eb9003"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 47.\nHolzschnitt zu Kap. 47: Von dem Weg zur ewigen Gl\u00FCckseligkeit.\nEin Narr zieht auf einem beschwerlichen Weg zwei Wagen zwei Wagen bergauf.\n11.6 x 8.5 cm.\nUnkoloriert."
                    ),
                    valueIri = "http://data.knora.org/2c20d68b4103/values/c2d70a7a4f26"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "h2v",
                resourceIri = "http://data.knora.org/2c20d68b4103"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 95.\nHolzschnitt Lemmer 1979, S. 120: Neue Illustration zu Kap. 95\nEin Narr kniet vor einem Wagen, um ein Rad zu wechseln. Im Wagen tummeln sich zahlreiche Affen. Hinter dem Wagen ist eine Kirche zu sehen, 11.6 x 8.3 cm."
                    ),
                    valueIri = "http://data.knora.org/da7c709de703/values/f45cc2fa8226"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "o1v",
                resourceIri = "http://data.knora.org/da7c709de703"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 44.\nHolzschnitt zu Kap. 44: Vom L\u00E4rmen in der Kirche\nEin junger Narr in edler Kleidung, der einen Jagdfalken auf dem Arm h\u00E4lt, von Hunden begleitet wird, und klappernde Schuhsohlen tr\u00E4gt, geht auf ein Portal zu, in dem eine Frau steht und ihm sch\u00F6ne Augen macht, 11.7 x 8.5 cm.\n"
                    ),
                    valueIri = "http://data.knora.org/315c2cf98a03/values/30dd17224e26"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "g6r",
                resourceIri = "http://data.knora.org/315c2cf98a03"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 10.\nHolzschnitt zu Kap. 10: Vom rechten Verhalten gegen\u00FCber dem N\u00E4chsten.\nEin Narr verpr\u00FCgelt einen Mann auf der Strasse. Eine Zuschauergruppe versucht den Narren zu bes\u00E4nftigen.\n11.5 x 8.3 cm.\nUnkoloriert."
                    ),
                    valueIri = "http://data.knora.org/7424ac6a2d03/values/1e0190203026"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "b6v",
                resourceIri = "http://data.knora.org/7424ac6a2d03"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Titelblatt (Hartl 2001: Stultitia Navis I).\nHolzschnitt: Ersatzholzschnitt f\u00FCr Titelblatt, recto:\nEin Schiff voller Narren f\u00E4hrt nach links. Hinten auf der Br\u00FCcke trinkt ein Narr aus einer Flasche, vorne pr\u00FCgeln sich zwei weitere narren so sehr, dass einer von ihnen \u00FCber Bord zu gehen droht. Oben die Inschrift \"Nauis stultoru(m).\"; auf dem Schiffsrumpf die Datierung \"1.4.9.7.\".\n6.5 x 11.5 cm.\noben links die bibliographische Angabe (Graphitstift) \"Hain No. 3746.\".\nunten der getilgte, nur schwach lesbare Besitzeintrag (Feder, braun) \"Sum Basilius Sum Amerbachior(um) | Anno MDXII\"; nur das Wort \"Amerbachiorum\" ist deutlich lesbar.\nrechts daneben Bibliotheksstempel (queroval, schwarz): \"BIBL. PUBL.| BASILEENSIS\".\n\n"
                    ),
                    valueIri = "http://data.knora.org/9ff8f3b97203/values/a97c9709c526"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "a1r; Titelblatt, recto",
                resourceIri = "http://data.knora.org/9ff8f3b97203"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 8.\nHolzschnitt zu Kap. 8: Guten Rat verschm\u00E4hen.\nEin Narr, dessen Linke mit einem Falknerhandschuh gesch\u00FCtzt wird, auf dem ein Vogel sitzt, lenkt einen Pflug, den ein zweiter Narr zieht.\n11.6 x 8.6 cm."
                    ),
                    valueIri = "http://data.knora.org/53c810f67a03/values/125a433b2f26"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "c3r",
                resourceIri = "http://data.knora.org/53c810f67a03"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 96.\nHolzschnitt zu Kap. 96: Schenken und hinterdrein bereuen.\nEin Narr, der vor einem Haus steht, \u00FCberreicht einem b\u00E4rtigen Alten ein Geschenk, kratzt sich dabei aber unschl\u00FCssig am Kopf, 11.6 x 8.3 cm."
                    ),
                    valueIri = "http://data.knora.org/64888e12e803/values/3ddabba68326"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "o2v",
                resourceIri = "http://data.knora.org/64888e12e803"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 20.\nHolzschnitt zu Kap. 20: Vom Finden fremden Eigentums.\nEin Narr streckt seine H\u00E4nde nach einem Schatz im Boden aus. Von Hinten tritt der Teufel in Gestalt eines Mischwesens heran und fl\u00FCstert dem Narr mit einem Blasebalg einen Gedanken ein, 11.6 x 8.5 cm.\n"
                    ),
                    valueIri = "http://data.knora.org/31213288c503/values/e097b50d3526"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "d7r",
                resourceIri = "http://data.knora.org/31213288c503"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 71.\nHolzschnitt zu Kap. 71: Von Streit- und Prozesss\u00FCchtigen.\nIn einem Innenraum steht ein Narr, dem ein Hechelbrett im Hintern steckt. W\u00E4hrend er Gefahr l\u00E4uft, auf weitere derartige Bretter, die am Boden liegen, zu treten, verbindet er der vor ihm sitzenden Personifikation der Justitia die Augen.\n11.7 x 8.5 cm."
                    ),
                    valueIri = "http://data.knora.org/bf9357539703/values/eb6efef97326"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "l1r",
                resourceIri = "http://data.knora.org/bf9357539703"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 57.\nHolzschnitt zu Kap. 57: Von der Gnadenwahl Gottes.\nEin Narr, der auf einem Krebs reitet, st\u00FCtzt sich auf ein brechendes Schildrohr, das ihm die Hand  durchbohrt. Ein Vogel fliegt auf den offenen Mund des Narren zu.\n11.6 x 8.5 cm."
                    ),
                    valueIri = "http://data.knora.org/1fdb76019003/values/118a3f426d26"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "i1r",
                resourceIri = "http://data.knora.org/1fdb76019003"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn \"De Corrupto ordine viuendi | pereu(n)tibus.\"\n5. neuer Holzschnitt der lateinischen Ausgabe: In einer Landschaft treibt unten links ein Narr, der Sporen an seinen Schuhspitzen tr\u00E4gt, mit einer Peitsche zwei Pferde an. Rechts steht ein zweiter Narr in einem Wagen auf dem Kopf. Unterhalb des Wagens sind vier Wappenschilde angeordnet. Oben links ist ein Horoskop f\u00FCr den 2. Oktober 1503 angebracht, 15,1 x 11.9 cm."
                    ),
                    valueIri = "http://data.knora.org/a54493aff903/values/8f4579539326"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "t1r",
                resourceIri = "http://data.knora.org/a54493aff903"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 94.\nHolzschnitt zu Kap. 94: Von der Hoffnung, andere zu beerben.\nEin Narr beschl\u00E4gt die Vorderhufe seines Esels auf dessen R\u00FCcken der Tod in Gestalt eines Skeletts rittlings sitzt. Er schwingt einen Knochen, um so die Fr\u00FCchte aus der Krone eines nahen Baumes zu schlagen.\n11.7 x 8.4 cm.\nUnkoloriert."
                    ),
                    valueIri = "http://data.knora.org/4a25b3326003/values/31336fc18226"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "q5v",
                resourceIri = "http://data.knora.org/4a25b3326003"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 14.\nHolzschnitt zu Kap. 14: Von falschen Vorstellungen \u00FCber Gott.\nEin Narr, der ein Kummet um seinen Hals tr\u00E4gt, geht mit B\u00FCchse und L\u00F6ffel auf einen Trog zu, aus dem Schweine und G\u00E4nse fressen.\n11.7 x 8.5 cm."
                    ),
                    valueIri = "http://data.knora.org/8f0dc5b47d03/values/364f29eb3126"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "d1r",
                resourceIri = "http://data.knora.org/8f0dc5b47d03"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 44.\nHolzschnitt zu Kap. 44: Vom L\u00E4rmen in der Kirche\nEin junger Narr in edler Kleidung, der einen Jagdfalken auf dem Arm h\u00E4lt, von Hunden begleitet wird, und klappernde Schuhsohlen tr\u00E4gt, geht auf ein Portal zu, in dem eine Frau steht und ihm sch\u00F6ne Augen macht, 11.7 x 8.5 cm."
                    ),
                    valueIri = "http://data.knora.org/972ae50dd003/values/6db3c4e84d26"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "g6r",
                resourceIri = "http://data.knora.org/972ae50dd003"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 76.\nHolzschnitt zu Kap. 76: Von Prahlerei und \u00DCberhebung.\nIn einem Innenraum st\u00FCtzt sich ein alter Narr auf einen Stab. Er tr\u00E4gt eine Kette um den Hals, an der ein viergeteilter Wappenschild h\u00E4ngt, auf dem der je zwei Baselst\u00E4be und Affen zu sehen sind. \u00DCber ihm schwebt eine Banderole mit dem Namen \u201ERitter Peter\u201C. Vor ihm sitzt ein Gelehrter im Narrenkost\u00FCm an einem Tisch und beugt sich vor, um den Ritter am Ohr zu ziehen. Auch \u00FCber ihm schwebt eine Banderole, auf der sein Name \u201EDoctor griff\u201C zu lesen ist, 11.7 x 8.4 cm."
                    ),
                    valueIri = "http://data.knora.org/c3846ac7dd03/values/89103e377626"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "l4r",
                resourceIri = "http://data.knora.org/c3846ac7dd03"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 44.\nHolzschnitt zu Kap. 44: Vom L\u00E4rmen in der Kirche\nEin junger Narr in edler Kleidung, der einen Jagdfalken auf dem Arm h\u00E4lt, von Hunden begleitet wird, und klappernde Schuhsohlen tr\u00E4gt, geht auf ein Portal zu, in dem eine Frau steht und ihm sch\u00F6ne Augen macht.\n11.7 x 8.5 cm.\nUnkoloriert."
                    ),
                    valueIri = "http://data.knora.org/04f25db73f03/values/aa8971af4d26"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "g6v",
                resourceIri = "http://data.knora.org/04f25db73f03"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 7.\nHolzschnitt zu Kap. 7: Vom Zwietracht stiften.\nEin Narr wird von zwei M\u00FChlsteinen zerdr\u00FCckt, ein zweiter hat seinen Finger in einer T\u00FCrangel eingeklemmt und wird von einem dritten Narren beobachtet, der so hinter einer Wand verborgen ist, dass die Ohren seiner Narrenkappe sein Versteck verraten.\n11.7 x 8.4 cm."
                    ),
                    valueIri = "http://data.knora.org/2f8bab95bf03/values/096c7f3a2026"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "c2r",
                resourceIri = "http://data.knora.org/2f8bab95bf03"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 83.\nneuer Holzschitt (nicht in Lemmer 1979): Vor einer H\u00E4userkulisse kniet ein Narr mit einem Beutel in der Linken und zwei Keulen in der Rechten vor einem Mann mit Hut und einem j\u00FCngeren Begleiter, 11.6 x 8.6 cm."
                    ),
                    valueIri = "http://data.knora.org/89d53cfbe003/values/0ed1b65d7b26"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "m3r",
                resourceIri = "http://data.knora.org/89d53cfbe003"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 109.\nHolzschnitt zu Kap. 109: Von Verachtung des Ungl\u00FCcks\nEin Narr hat sich in einem Boot zu weit vom Ufer entfernt. Nun birst der Schiffsrumpf, das Segel flattert haltlos umher. Der Narr h\u00E4lt sich an einem Seil der Takelage fest, 11.6 x 8.4 cm."
                    ),
                    valueIri = "http://data.knora.org/9e72b64faa03/values/2f0d14298c26"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "q2v",
                resourceIri = "http://data.knora.org/9e72b64faa03"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 11.\nHolzschnitt zu Kap. 11: Von Missachtung der Heiligen Schrift.\nEin Narr, der zwei B\u00FCcher mit F\u00FCssen tritt, spricht mit einem in ein Leichentuch geh\u00FCllten, wiedererweckten Toten, der auf seiner Bahre hockt.\n11.6 x 8.5 cm."
                    ),
                    valueIri = "http://data.knora.org/f1ea6a557c03/values/677e89cc3026"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "c6r",
                resourceIri = "http://data.knora.org/f1ea6a557c03"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 64.\nHolzschnitt zu Kap. 64: Von b\u00F6sen Weibern.\nHolzschnitt identisch mit Kap. 35: Ein Esel bleibt st\u00F6rrisch stehen, w\u00E4hrend ein Narr, der auf ihm sitzt, eine Peitsche schwingt und mit seinen Sporen gegen die Ohren tritt. Auch eine Frau, die an seinem Schweif zieht und ein Hund, der ihn anbellt, treiben den Esel so wenig an, dass eine Schnecke ihn am Boden begleiten kann.\n11.6 x 8.4 cm."
                    ),
                    valueIri = "http://data.knora.org/4bfa014ad803/values/fb6101106226"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "i8r",
                resourceIri = "http://data.knora.org/4bfa014ad803"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 54.\nHolzschnitt zu Kap. 54: Sich nicht bessern wollen.\nEin Narr steht auf einer Strasse und bl\u00E4st auf einem Dudelsack w\u00E4hrend er Laute und Harfe unbeachtet am Boden liegen l\u00E4sst.\n11.7 x 8.4 cm.\nUnkoloriert."
                    ),
                    valueIri = "http://data.knora.org/7c7cc6344503/values/7f8f4cea6b26"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "i2v",
                resourceIri = "http://data.knora.org/7c7cc6344503"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 104.\nHolzschnitt zu Kap. 104: Die Wahrheit verschweigen\nEin Narr steht auf einer h\u00F6lzernen Kanzel und wendet sich einer Menschenmenge zu, spricht aber nicht zu ihr, sondern h\u00E4lt seinen linken Zeigefinger schweigend vor den Mund. Manche aus dem Publikum drohen ihm mit St\u00F6cken und Schwertern, andere schlafen oder wenden sich vom Geschehen ab, 11.6 x 8.3 cm."
                    ),
                    valueIri = "http://data.knora.org/dc12f78fed03/values/85c487068926"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "p6v",
                resourceIri = "http://data.knora.org/dc12f78fed03"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 72.\nHolzschnitt zu Kap. 72: Von den Grobianern.\nVor einer Landschaftskulisse steht ein Narr, der sich einem Schwein zuwendet, dass eine Krone auf dem Kopf und eine Glocke um den Hals tr\u00E4gt, 11.7 x 8.5 cm."
                    ),
                    valueIri = "http://data.knora.org/499f75c89703/values/34ecf7a57426"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "l2r",
                resourceIri = "http://data.knora.org/499f75c89703"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 104.\nHolzschnitt zu Kap. 104: Die Wahrheit verschweigen\nEin Narr steht auf einer h\u00F6lzernen Kanzel und wendet sich einer Menschenmenge zu, spricht aber nicht zu ihr, sondern h\u00E4lt seinen linken Zeigefinger schweigend vor den Mund. Manche aus dem Publikum drohen ihm mit St\u00F6cken und Schwertern, andere schlafen oder wenden sich vom Geschehen ab.\n11.6 x 8.3 cm.\nUnkoloriert.\nOben rechts Blattnummerierung (Graphitstift): \"142\"."
                    ),
                    valueIri = "http://data.knora.org/afe322bf6703/values/0b182e798926"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "s6r",
                resourceIri = "http://data.knora.org/afe322bf6703"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 66.\nHolzschnitt zu Kap. 66: Von der Erforschung der Welt.\nEin Narr hat ein Schema des Universums auf den Boden Gezeichnet und vermisst es mit einem Zirkel. Von hinten blickt ein zweiter Narr \u00FCber eine Mauer und wendet sich dem ersten mit sp\u00F6ttischen Gesten zu.\n11.6 x 8.4 cm."
                    ),
                    valueIri = "http://data.knora.org/0d5ac1099503/values/4dcdbebc7126"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "k4r",
                resourceIri = "http://data.knora.org/0d5ac1099503"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 83.\nneuer Holzschitt (nicht in Lemmer 1979): Vor einer H\u00E4userkulisse kniet ein Narr mit einem Beutel in der Linken und zwei Keulen in der Rechten vor einem Mann mit Hut und einem j\u00FCngeren Begleiter, 11.6 x 8.6 cm."
                    ),
                    valueIri = "http://data.knora.org/230784e69b03/values/4ba763247b26"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "m3r",
                resourceIri = "http://data.knora.org/230784e69b03"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 70.\nHolzschnitt zu Kap. 70: Nicht Vorsorgen in der Zeit.\nEin \u00E4rmlich gekleideter Narr, der einen Strick mit sich tr\u00E4gt zieht umher. Rechts unten kauert ein B\u00E4r, der an seiner Pfote saugt. Hinten sammeln Insekten einen Futtervorrat.\n11.7 x 8.5 cm.\nUnkoloriert."
                    ),
                    valueIri = "http://data.knora.org/6c91972f5003/values/2845abc07326"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "m2v",
                resourceIri = "http://data.knora.org/6c91972f5003"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 18.\nHolzschnitt zu Kap. 18: Vom Dienst an zwei Herren.\nEin mit Spiess bewaffneter Narr bl\u00E4st in ein Horn. Sein Hund versucht derweil im Hintergrund zwei Hasen gleichzeitig zu erjagen. 11.6 x 8.4 cm.\n"
                    ),
                    valueIri = "http://data.knora.org/b73b3d897f03/values/11c715ef3326"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "d5r",
                resourceIri = "http://data.knora.org/b73b3d897f03"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 109.\nHolzschnitt zu Kap. 109: Von Verachtung des Ungl\u00FCcks\nEin Narr hat sich in einem Boot zu weit vom Ufer entfernt. Nun birst der Schiffsrumpf, das Segel flattert haltlos umher. Der Narr h\u00E4lt sich an einem Seil der Takelage fest.\n11.6 x 8.4 cm.\nUnkoloriert.\nOben rechts Blattnummerierung (Graphitstift): \"151\"."
                    ),
                    valueIri = "http://data.knora.org/894b31dd6b03/values/f23667628c26"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "t7r",
                resourceIri = "http://data.knora.org/894b31dd6b03"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 31.\nHolzschnitt zu Kap. 31: Vom Hinausschieben auf morgen.\nEin Narr steht mit ausgebreiteten Armen auf einer Strasse. Auf seinen H\u00E4nden sitzen zwei Raben, die beide \u201ECras\u201C \u2013 das lateinische Wort f\u00FCr \u201Emorgen\u201C \u2013 rufen. Auf dem Kopf des Narren sitzt ein Papagei und ahmt den Ruf der Kr\u00E4hen nach, 11.6 x 8.5 cm."
                    ),
                    valueIri = "http://data.knora.org/1fa07c90ca03/values/c623c1aa3c26"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "f2r",
                resourceIri = "http://data.knora.org/1fa07c90ca03"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 6.\nHolzschnitt zu Kap. 6: Von mangelhafter Erziehung der Kinder.\nZwei Jungen geraten am Spieltisch \u00FCber Karten und W\u00FCrfen in Streit. W\u00E4hrend der eine einen Dolch z\u00FCckt und der andere nach seinem Schwert greift, sitzt ein \u00E4lterer Narr mit verbundenen Augen ahnungslos neben dem Geschehen.\n11.7 x 8.5 cm."
                    ),
                    valueIri = "http://data.knora.org/b5a5b6967903/values/b18eb0c42c26"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "b8r",
                resourceIri = "http://data.knora.org/b5a5b6967903"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 10.\nHolzschnitt zu Kap. 10: Vom rechten Verhalten gegen\u00FCber dem N\u00E4chsten.\nEin Narr verpr\u00FCgelt einen Mann auf der Strasse. Eine Zuschauergruppe versucht den Narren zu bes\u00E4nftigen.\n"
                    ),
                    valueIri = "http://data.knora.org/cdad05f5c003/values/5bd73ce72f26"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "c5r",
                resourceIri = "http://data.knora.org/cdad05f5c003"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 5.\nHolzschnitt zu Kap. 5: Noch im Alter ein Narr sein.\nEin alter Narr, der mit einem Bein im Grab steht und dem schon das Schindmesser im Hintern steckt, geht auf Kr\u00FCcken weiter seines Weges. \u00DCber ihm am Himmel ist ein leerer Wappenschild und der Name \u201EHaintz Nar\u201C zu sehen. 11.5 x 8.5 cm."
                    ),
                    valueIri = "http://data.knora.org/2b9a98217903/values/d616c4c02a26"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "b7r",
                resourceIri = "http://data.knora.org/2b9a98217903"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 59.\nHolzschnitt zu Kap. 59: Vom Danken und Lohnen.\nEin Narr begleitet einen Gelehrten und ist dabei so ins Gespr\u00E4ch vertieft, dass er einen anderen Mann missachtet. Dieser schl\u00E4gt deshalb den Narren mit einer Pritsche.\n11.7 x 8.5 cm."
                    ),
                    valueIri = "http://data.knora.org/99c06b00d603/values/1d318c276e26"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "i3r",
                resourceIri = "http://data.knora.org/99c06b00d603"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 56.\nHolzschnitt zu Kap. 56: Alle Macht nimmt einmal ein Ende.\nHolzschnitt identisch mit Kap. 37: Die Hand Gottes treibt das Gl\u00FCcksrad an, auf dem ein Esel und zwei Mischwesen \u2013 halb Esel, halb Narr \u2013 herumgewirbelt werden. Der von der Abw\u00E4rtsbewegung des Rades Mitgerissene st\u00FCrzt in das unter ihm ausgehobene Grab, dessen Deckel im Vordergrund bereit liegt.\n11.5 x 8.4 cm."
                    ),
                    valueIri = "http://data.knora.org/fb9d11a1d403/values/e4a695915b26"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "h8r",
                resourceIri = "http://data.knora.org/fb9d11a1d403"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn von Kapitel 6.\nunten zweizeilige handschriftliche Notiz (Feder, grau).\nHolzschnitt zu Kap. 6: Von mangelhafter Erziehung der Kinder.\nZwei Jungen geraten am Spieltisch \u00FCber Karten und W\u00FCrfen in Streit. W\u00E4hrend der eine einen Dolch z\u00FCckt und der andere nach seinem Schwert greift, sitzt ein \u00E4lterer Narr mit verbundenen Augen ahnungslos neben dem Geschehen.\n11.7 x 8.5 cm.\nUnkoloriert."
                    ),
                    valueIri = "http://data.knora.org/c2ea15212b03/values/14809ed32526"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "b1v",
                resourceIri = "http://data.knora.org/c2ea15212b03"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 12.\nHolzschnitt zu Kap. 12: Von Unbedachtsamkeit.\nEin Narr f\u00E4llt von seinem Esel, weil er den Sattelgurt nicht geschnallt hat.\n11.7 x 8.5 cm."
                    ),
                    valueIri = "http://data.knora.org/e1c441dfc103/values/a70dbf772226"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "c7r",
                resourceIri = "http://data.knora.org/e1c441dfc103"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 82.\nHolzschnitt zu Kap. 82: Vom st\u00E4ndischen Ehrgeiz.\nEin Narr, der kostbare Frauenkleider und Schmuck tr\u00E4gt, steht vor dem Tor eines Hauses und versucht einen \u201EDreispitz\u201C in einen Sack zu stecken. Diese Handlung des Narren wird von einem Spruchband mit der Aussage \u201EEr mu(o)\u00DF dryn\u201C begleitet, 11.7 x 8.4 cm."
                    ),
                    valueIri = "http://data.knora.org/99fb65719b03/values/022a6a787a26"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "m2r",
                resourceIri = "http://data.knora.org/99fb65719b03"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 96.\nHolzschnitt zu Kap. 96: Schenken und hinterdrein bereuen.\nEin Narr, der vor einem Haus steht, \u00FCberreicht einem b\u00E4rtigen Alten ein Geschenk, kratzt sich dabei aber unschl\u00FCssig am Kopf.\n11.6 x 8.3 cm.\nUnkoloriert.\nOben rechts Blattnummerierung (Graphitstift): \"128\"."
                    ),
                    valueIri = "http://data.knora.org/23427e576103/values/c32d62198426"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "q8r",
                resourceIri = "http://data.knora.org/23427e576103"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 32.\nHolzschnitt zu Kap. 32: Vom Bewachen der Frauentugend.\nIm Vordergrund giesst ein Narr Wasser in einen Brunnen, ein zweiter reinigt Ziegelsteine. Dahinter steht ein dritter Narr, der eine Heuschreckenherde h\u00FCtet. Er blickt zu einem Geb\u00E4ude, aus dessen Fenster eine Frau schaut und ihm \u201Ehu(e)t fast\u201C zuruft, 11.7 x 8.6 cm.\n"
                    ),
                    valueIri = "http://data.knora.org/a9ab9a05cb03/values/d2ca0d903d26"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "f3r",
                resourceIri = "http://data.knora.org/a9ab9a05cb03"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 87.\nHolzschnitt zu Kap. 87: Von Gottesl\u00E4sterung.\nIn einer Landschaft h\u00E4ngt Christus am Kreuz. Von rechts n\u00E4hert sich ein Narr, der mit einem Dreizack auf den Gekreuzigten einsticht, 11.6 x 8.5 cm."
                    ),
                    valueIri = "http://data.knora.org/c51af1b9e303/values/ac72f69a7d26"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "n1r",
                resourceIri = "http://data.knora.org/c51af1b9e303"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 37.\nHolzschnitt zu Kap. 37: Von der Wandelbarkeit des Gl\u00FCcks.\nHolzschnitt identisch mit Kap. 56. Die Hand Gottes treibt das Gl\u00FCcksrad an, auf dem ein Esel und zwei Mischwesen \u2013 halb Esel, halb Narr \u2013 herumgewirbelt werden. Der von der Abw\u00E4rtsbewegung des Rades Mitgerissene st\u00FCrzt in das unter ihm ausgehobene Grab, dessen Deckel im Vordergrund bereit liegt.\n11.5 x 8.4 cm.\nUnkoloriert."
                    ),
                    valueIri = "http://data.knora.org/b4956d0e3c03/values/d0a4b2f74626"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "f6v",
                resourceIri = "http://data.knora.org/b4956d0e3c03"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 70.\nHolzschnitt zu Kap. 70: Nicht Vorsorgen in der Zeit.\nEin \u00E4rmlich gekleideter Narr, der einen Strick mit sich tr\u00E4gt zieht umher. Rechts unten kauert ein B\u00E4r, der an seiner Pfote saugt. Hinten sammeln Insekten einen Futtervorrat.\n11.7 x 8.5 cm."
                    ),
                    valueIri = "http://data.knora.org/9b56f2f2db03/values/8cc9c61b6826"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "k8r",
                resourceIri = "http://data.knora.org/9b56f2f2db03"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 39.\nHolzschnitt zu Kap. 39: Nichts f\u00FCr sich behalten k\u00F6nnen.\nEin Narr, der hinter einem Geb\u00FCsch lauert, hat am Boden ein grosses Fangnetz gespannt. Die V\u00F6gel gehen jedoch dem Netz aus dem Weg oder fliegen davon, 11.7 x 8.5 cm.\n"
                    ),
                    valueIri = "http://data.knora.org/e5f04ec4cd03/values/0041e58c4a26"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "g1r",
                resourceIri = "http://data.knora.org/e5f04ec4cd03"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 23.\nHolzschnitt zu Kap. 23: Vom blinden Vertrauen auf das Gl\u00FCck.\nEin Narr schaut oben aus dem Fenster seines Hauses, das unten lichterloh brennt. Am Himmel erscheint die r\u00E4chende Gotteshand, die mit einen Hammer auf Haus und Narr einschl\u00E4gt. Auf der Fahne \u00FCber dem Erker des Hauses ist der Baselstab zu erkennen. 11.5 x 8.2 cm."
                    ),
                    valueIri = "http://data.knora.org/6975d3d28103/values/416348843726"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "e2r",
                resourceIri = "http://data.knora.org/6975d3d28103"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 78.\nHolzschnitt zu Kap. 78: Von Narren, die sich selbst Bedr\u00FCckung verschaffen.\nEin Narr, dem ein Esel auf den R\u00FCcken zu springen versucht, st\u00FCrzt zu Boden, 11.6 x 8.4 cm."
                    ),
                    valueIri = "http://data.knora.org/d79ba6b1de03/values/1b0b318f7726"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "l6r",
                resourceIri = "http://data.knora.org/d79ba6b1de03"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 72.\nHolzschnitt zu Kap. 72: Von den Grobianern.\nVor einer Landschaftskulisse steht ein Narr, der sich einem Schwein zuwendet, dass eine Krone auf dem Kopf und eine Glocke um den Hals tr\u00E4gt, 11.7 x 8.5 cm."
                    ),
                    valueIri = "http://data.knora.org/af6d2edddc03/values/71c2a46c7426"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "l2r",
                resourceIri = "http://data.knora.org/af6d2edddc03"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 73.\nHolzschnitt Lemmer 1979, S. 118: Neue Illustration zu Kap. 73.\nVor einer Stadtvedute f\u00FChrt ein Narr zwei Esel an Stricken hinter sich her.\n11.5 x 8.3 cm.\nUnkoloriert."
                    ),
                    valueIri = "http://data.knora.org/94bf0f045203/values/4093448b7526"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "m6v",
                resourceIri = "http://data.knora.org/94bf0f045203"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 41.\nHolzschnitt zu Kap. 41: Nachrede unbeachtet lassen.\nEin Narr hantiert mit einem Mehlsack. Rechts steht eine grosse Glocke mit einem Fuchsschwanz als Kl\u00F6ppel.\n11.7 x 8.4 cm.\nUnkoloriert."
                    ),
                    valueIri = "http://data.knora.org/66cf03583e03/values/cf1185ab4b26"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "g3v",
                resourceIri = "http://data.knora.org/66cf03583e03"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 7.\nIm Bild handschriftliche Notiz \"peter schellenbeg\" und Kritzeleien Holzschnitt zu Kap. 7: Vom Zwietracht stiften.\nEin Narr wird von zwei M\u00FChlsteinen zerdr\u00FCckt, ein zweiter hat seinen Finger in einer T\u00FCrangel eingeklemmt und wird von einem dritten Narren beobachtet, der so hinter einer Wand verborgen ist, dass die Ohren seiner Narrenkappe sein Versteck verraten.\n11.7 x 8.4 cm.\nUnkoloriert."
                    ),
                    valueIri = "http://data.knora.org/d601520b2c03/values/d7a9f10c2626"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "b3v",
                resourceIri = "http://data.knora.org/d601520b2c03"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 84.\nHolzschnitt zu Kap. 84: Vom Festhalten am Guten.\nHolzschnitt identisch mit Kap. 8: Ein Narr, dessen Linke mit einem Falknerhandschuh gesch\u00FCtzt wird, auf dem ein Vogel sitzt, lenkt einen Pflug, den ein zweiter Narr zieht.\n11.6 x 8.6 cm."
                    ),
                    valueIri = "http://data.knora.org/371ec0d09c03/values/94245dd07b26"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "m5r",
                resourceIri = "http://data.knora.org/371ec0d09c03"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 9.\nHolzschnitt Lemmer 1979, S. 115: Variante f\u00FCr Kap. 9.\nEin junger, reich gekleideter Mann zieht seine Narrenkappe an einem Strick hinter sich her. Der Bildinhalt stimmt weitgehend mit dem urspr\u00FCnglichen Holzschnitt \u00FCberein: zus\u00E4tzlich zur Kappe zieht der Narr hier auch ein Zepter hinter sich her.\n11.8 x 8.4 cm.\nUnkoloriert."
                    ),
                    valueIri = "http://data.knora.org/ea188ef52c03/values/d58396742f26"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "b5v",
                resourceIri = "http://data.knora.org/ea188ef52c03"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 21.\nHolzschnitt zu Kap. 21: Andere tadeln und selbst unrecht handeln.\nEin Narr, der mit seinen Beinen im Sumpf steckt, zeigt auf einen nahen Weg, an dem ein Bildstock die Richtung weist.\n11.7 x 8.5 cm.\nUnkoloriert.\n"
                    ),
                    valueIri = "http://data.knora.org/00c650d23303/values/af68552c3626"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "d4v",
                resourceIri = "http://data.knora.org/00c650d23303"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 68.\nHolzschnitt zu Kap. 68: Keinen Scherz verstehen.\nEin Kind, das auf einem Steckenpferd reitet und mit einem Stock als Gerte umher fuchtelt, wird von einem Narren am rechten Rand ausgeschimpft. Ein anderer Narr, der neben dem Kind steht, ist dabei, sein Schwert aus der Scheide zu ziehen.\n11.7 x 8.5 cm.\nUnkoloriert."
                    ),
                    valueIri = "http://data.knora.org/587a5b454f03/values/1c9e5edb7226"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "l8v",
                resourceIri = "http://data.knora.org/587a5b454f03"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 69.\nHolzschnitt Lemmer 1979, S. 117: Variante zu Kap. 69.\nEin Narr, der vor einer Stadtkulisse steht, hat mit seiner Rechten einen Ball in die Luft geworfen und schl\u00E4gt mit seiner Linken einen Mann, der sogleich nach seinem Dolch greift. Ein junger Mann beobachtet das Geschehen.\nDer Bildinhalt stimmt weitgehend mit dem urspr\u00FCnglichen Holzschnitt \u00FCberein.\n11.7 x 8.4 cm."
                    ),
                    valueIri = "http://data.knora.org/ab7c1b699603/values/dfc7b1147326"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "k7r",
                resourceIri = "http://data.knora.org/ab7c1b699603"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 68.\nHolzschnitt zu Kap. 68: Keinen Scherz verstehen.\nEin Kind, das auf einem Steckenpferd reitet und mit einem Stock als Gerte umher fuchtelt, wird von einem Narren am rechten Rand ausgeschimpft. Ein anderer Narr, der neben dem Kind steht, ist dabei, sein Schwert aus der Scheide zu ziehen.\n11.7 x 8.5 cm."
                    ),
                    valueIri = "http://data.knora.org/2171fdf39503/values/59740ba27226"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "k6r",
                resourceIri = "http://data.knora.org/2171fdf39503"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 87.\nHolzschnitt zu Kap. 87: Von Gottesl\u00E4sterung.\nIn einer Landschaft h\u00E4ngt Christus am Kreuz. Von rechts n\u00E4hert sich ein Narr, der mit einem Dreizack auf den Gekreuzigten einsticht, 11.6 x 8.5 cm."
                    ),
                    valueIri = "http://data.knora.org/5f4c38a59e03/values/6f9c49d47d26"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "n1r",
                resourceIri = "http://data.knora.org/5f4c38a59e03"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 85.\nHolzschnitt zu Kap. 85: Des Todes nicht eingedenk sein.\nEin Narr, der ein Schellenb\u00FCndel in H\u00E4nden h\u00E4lt, wendet sich erschrocken um, als ihm der Tod in Gestalt eines Skeletts, das eine Bahre auf der Schulter tr\u00E4gt, am Rockzipfel packt und ihm zuruft \u201Ed\u00FC blibst\u201C.\n11.7 x 8.4 cm.\nUnkoloriert."
                    ),
                    valueIri = "http://data.knora.org/488f2c405a03/values/a0cba9b57c26"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "o8v",
                resourceIri = "http://data.knora.org/488f2c405a03"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 57.\nHolzschnitt zu Kap. 57: Von der Gnadenwahl Gottes.\nEin Narr, der auf einem Krebs reitet, st\u00FCtzt sich auf ein brechendes Schildrohr, das ihm die Hand  durchbohrt. Ein Vogel fliegt auf den offenen Mund des Narren zu.\n11.6 x 8.5 cm."
                    ),
                    valueIri = "http://data.knora.org/85a92f16d503/values/1443c8265f26"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "i1r",
                resourceIri = "http://data.knora.org/85a92f16d503"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 38.\nHolzschnitt zu Kap. 38: Von eigensinnigen Kranken.\nEin kranker Narr, der im Bett liegt, st\u00F6sst mit seinen Beinen einen Tisch um, auf dem seine Arzneien liegen. Zu beiden Seiten des Bettes stehen ein Arzt und eine Frau, 11.7 x 8.4 cm."
                    ),
                    valueIri = "http://data.knora.org/f516783a8803/values/7aed3e1a4a26"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "f8r",
                resourceIri = "http://data.knora.org/f516783a8803"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 7.\nHolzschnitt zu Kap. 7: Vom Zwietracht stiften.\nEin Narr wird von zwei M\u00FChlsteinen zerdr\u00FCckt, ein zweiter hat seinen Finger in einer T\u00FCrangel eingeklemmt und wird von einem dritten Narren beobachtet, der so hinter einer Wand verborgen ist, dass die Ohren seiner Narrenkappe sein Versteck verraten.\n11.7 x 8.4 cm."
                    ),
                    valueIri = "http://data.knora.org/c9bcf2807a03/values/4f30f0012f26"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "c2r",
                resourceIri = "http://data.knora.org/c9bcf2807a03"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 105.\nHolzschnitt identisch mit Kap. 95: In einer Landschaft fasst ein Narr, der ein Zepter in der Linken h\u00E4lt, einem Mann an die Schulter und redet auf ihn ein, er m\u00F6ge die Feiertage missachten, 11.7 x 8.6 cm."
                    ),
                    valueIri = "http://data.knora.org/00505cf0a803/values/549527258a26"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "p7v",
                resourceIri = "http://data.knora.org/00505cf0a803"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 29.\nHolzschnitt zu Kap. 29: Von Verkennung der Mitmenschen.\nEin Narr verspottet einen Sterbenden, neben dessen Bett eine Frau betet, w\u00E4hrend sich unter dem Narren die H\u00F6lle in Gestalt eines gefr\u00E4ssigen Drachenkopfs auftut, 11.7 x 8.5 cm.\n"
                    ),
                    valueIri = "http://data.knora.org/a5ba87918403/values/71ff7a193b26"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "e8r",
                resourceIri = "http://data.knora.org/a5ba87918403"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 86.\nHolzschnitt zu Kap. 86: Gott nicht ernst nehmen.\nEin Narr zupft dem in einer Landschaft stehenden Erl\u00F6ser, der die bekr\u00F6nte Weltkugel in der Linken h\u00E4lt, die Rechten zum Segen erhebt und dessen Haupt von einer Aureole umgeben wird, am Bart. Er wird daf\u00FCr bestraft, indem ein Schauer von Steinen oder Feuergeschossen vom Himmel herab auf ihn niedergeht, 11.6 x 8.4 cm."
                    ),
                    valueIri = "http://data.knora.org/3b0fd344e303/values/63f5fcee7c26"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "m8r",
                resourceIri = "http://data.knora.org/3b0fd344e303"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 31.\nHolzschnitt zu Kap. 31: Vom Hinausschieben auf morgen.\nEin Narr steht mit ausgebreiteten Armen auf einer Strasse. Auf seinen H\u00E4nden sitzen zwei Raben, die beide \u201ECras\u201C \u2013 das lateinische Wort f\u00FCr \u201Emorgen\u201C \u2013 rufen. Auf dem Kopf des Narren sitzt ein Papagei und ahmt den Ruf der Kr\u00E4hen nach.\n11.6 x 8.5 cm.\nUnkoloriert."
                    ),
                    valueIri = "http://data.knora.org/ee449bda3803/values/03fa6d713c26"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "e7v",
                resourceIri = "http://data.knora.org/ee449bda3803"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 86.\nHolzschnitt zu Kap. 86: Gott nicht ernst nehmen.\nEin Narr zupft dem in einer Landschaft stehenden Erl\u00F6ser, der die bekr\u00F6nte Weltkugel in der Linken h\u00E4lt, die Rechten zum Segen erhebt und dessen Haupt von einer Aureole umgeben wird, am Bart. Er wird daf\u00FCr bestraft, indem ein Schauer von Steinen oder Feuergeschossen vom Himmel herab auf ihn niedergeht, 11.6 x 8.4 cm."
                    ),
                    valueIri = "http://data.knora.org/d5401a309e03/values/261f50287d26"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "m8r",
                resourceIri = "http://data.knora.org/d5401a309e03"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 73.\nHolzschnitt Lemmer 1979, S. 118: Neue Illustration zu Kap. 73.\nVor einer Stadtvedute f\u00FChrt ein Narr zwei Esel an Stricken hinter sich her, 11.5 x 8.3 cm."
                    ),
                    valueIri = "http://data.knora.org/39794c52dd03/values/ba3f9e187526"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "l3r",
                resourceIri = "http://data.knora.org/39794c52dd03"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 47.\nHolzschnitt zu Kap. 47: Von dem Weg zur ewigen Gl\u00FCckseligkeit.\nEin Narr zieht auf einem beschwerlichen Weg zwei Wagen zwei Wagen bergauf. 11.6 x 8.5 cm."
                    ),
                    valueIri = "http://data.knora.org/457368e38b03/values/85015eb34f26"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "g8r",
                resourceIri = "http://data.knora.org/457368e38b03"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 99.\nHolzschnitt zu Kap. 99: Von der Einbusse des christlichen Reiches\nAuf einem Hof kniet ein Narr vor den Vertretern der kirchlichen und weltlichen Obrigkeit, die vor ein Portal getreten sind, und bittet darum, sie m\u00F6gen die Narrenkappe verschm\u00E4hen. Im Hintergrund kommentieren zwei weitere Narren \u00FCber die Hofmauer hinweg das Geschehen mit ungl\u00E4ubigen Gesten.\n11.7 x 8.5 cm.\nUnkoloriert.\nOben rechts Blattnummerierung (Graphitstift): \"131\"."
                    ),
                    valueIri = "http://data.knora.org/c164d8b66203/values/9ea54e1d8626"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "r3r",
                resourceIri = "http://data.knora.org/c164d8b66203"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 23.\nHolzschnitt zu Kap. 23: Vom blinden Vertrauen auf das Gl\u00FCck.\nEin Narr schaut oben aus dem Fenster seines Hauses, das unten lichterloh brennt. Am Himmel erscheint die r\u00E4chende Gotteshand, die mit einen Hammer auf Haus und Narr einschl\u00E4gt. Auf der Fahne \u00FCber dem Erker des Hauses ist der Baselstab zu erkennen, 11.5 x 8.2 cm.\n"
                    ),
                    valueIri = "http://data.knora.org/cf438ce7c603/values/bb0fa2113726"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "e2r",
                resourceIri = "http://data.knora.org/cf438ce7c603"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 21.\nHolzschnitt zu Kap. 21: Andere tadeln und selbst unrecht handeln.\nEin Narr, der mit seinen Beinen im Sumpf steckt, zeigt auf einen nahen Weg, an dem ein Bildstock die Richtung weist, 11.7 x 8.5 cm.\n"
                    ),
                    valueIri = "http://data.knora.org/bb2c50fdc503/values/2915afb93526"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "d8r",
                resourceIri = "http://data.knora.org/bb2c50fdc503"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 19.\nHolzschnitt zu Kap. 19: Von \u00FCberfl\u00FCssigen Schw\u00E4tzen.\nEin Narr steht mit herausgestreckter Zunge unter einem Baum. Er erblickt in dessen Krone das Nest eines Spechts, der unten ein Loch in den Stamm h\u00E4mmert, 11.6 x 8.5 cm.\n"
                    ),
                    valueIri = "http://data.knora.org/a7151413c503/values/971abc613426"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "d6r",
                resourceIri = "http://data.knora.org/a7151413c503"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn \"De singularitate quoru(n)da(m) | nouor(um) fatuor(um) additio\" (Hartl 2001: Stultitia navis XV).\nHolzschnitt identisch mit Kap. 36.\nEin Narr, der ein Nest pl\u00FCndern wollte, st\u00FCrzt vom Baum herab. Die aus dem Nest gefallenen V\u00F6gel liegen tot am Boden, 11.5 x 8.4 cm.\n"
                    ),
                    valueIri = "http://data.knora.org/8d3c4451b203/values/6b5093a39026"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "s4r",
                resourceIri = "http://data.knora.org/8d3c4451b203"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 57.\nHolzschnitt zu Kap. 57: Von der Gnadenwahl Gottes.\nEin Narr, der auf einem Krebs reitet, st\u00FCtzt sich auf ein brechendes Schildrohr, das ihm die Hand  durchbohrt. Ein Vogel fliegt auf den offenen Mund des Narren zu.\n11.6 x 8.5 cm.\nUnkoloriert."
                    ),
                    valueIri = "http://data.knora.org/a4aa3e094703/values/d4b3927b6d26"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "i6v",
                resourceIri = "http://data.knora.org/a4aa3e094703"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 34.\nHolzschnitt zu Kap. 34: Ein Narr sein und es bleiben.\nEin Narr wird von drei G\u00E4nsen umgeben, deren eine von ihm wegfliegt.\n11.7 x 8.4 cm.\nUnkoloriert."
                    ),
                    valueIri = "http://data.knora.org/167313af3a03/values/1ab5d9ef4226"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "f3v",
                resourceIri = "http://data.knora.org/167313af3a03"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 47.\nHolzschnitt zu Kap. 47: Von dem Weg zur ewigen Gl\u00FCckseligkeit.\nEin Narr zieht auf einem beschwerlichen Weg zwei Wagen zwei Wagen bergauf, 11.8 x 8.5 cm."
                    ),
                    valueIri = "http://data.knora.org/ab4121f8d003/values/ffadb7404f26"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "g8r",
                resourceIri = "http://data.knora.org/ab4121f8d003"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 43.\nHolzschnitt zu Kap. 43: Missachten der ewigen Seligkeit\nEin Narr steht mit einer grossen Waage in einer Landschaft und wiegt das Himmelsfirmament (links) gegen eine Burg (rechts) auf. Die Zunge der Waage schl\u00E4gt zugunsten der Burg aus.\n11.5 x 8.4 cm.\nUnkoloriert."
                    ),
                    valueIri = "http://data.knora.org/7ae63f423f03/values/2436cb3c4d26"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "g5v",
                resourceIri = "http://data.knora.org/7ae63f423f03"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 43.\nHolzschnitt zu Kap. 43: Missachten der ewigen Seligkeit\nEin Narr steht mit einer grossen Waage in einer Landschaft und wiegt das Himmelsfirmament (links) gegen eine Burg (rechts) auf. Die Zunge der Waage schl\u00E4gt zugunsten der Burg aus, 11.5 x 8.4 cm.\n"
                    ),
                    valueIri = "http://data.knora.org/a7500e848a03/values/610c78034d26"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "g5r",
                resourceIri = "http://data.knora.org/a7500e848a03"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 65.\nHolzschnitt zu Kap. 65: Von Astrologie und anderem Aberglauben.\nEin Narr, an dessen Seite ein Fuchsschwanz h\u00E4ngt, will einen Gelehrten \u00FCberreden, auf die Gestirne und den Flug der V\u00F6gel zu achten. Daf\u00FCr fasst er den Gelehrten bei der Schulter des Gelehrten und weist mit seiner Rechten zum Himmel.\n11.6 x 8.4 cm."
                    ),
                    valueIri = "http://data.knora.org/e91c5ca9d903/values/ee2787de6526"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "k3r",
                resourceIri = "http://data.knora.org/e91c5ca9d903"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 52.\nHolzschnitt zu Kap. 52: Von Geldheiraten.\nEin junger, bekr\u00E4nzter Narr, nimmt mit seiner Linken einen Gelbeutel von einer alten Frau in Empfang. In seiner Rechten h\u00E4lt er einen L\u00F6ffel, den er einem Esel unter dem Schwanz h\u00E4lt, 11.5 x 8.4 cm."
                    ),
                    valueIri = "http://data.knora.org/d36f99ccd203/values/6741b31f6a26"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "h4r",
                resourceIri = "http://data.knora.org/d36f99ccd203"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 68.\nHolzschnitt zu Kap. 68: Keinen Scherz verstehen.\nEin Kind, das auf einem Steckenpferd reitet und mit einem Stock als Gerte umher fuchtelt, wird von einem Narren am rechten Rand ausgeschimpft. Ein anderer Narr, der neben dem Kind steht, ist dabei, sein Schwert aus der Scheide zu ziehen.\n11.7 x 8.5 cm."
                    ),
                    valueIri = "http://data.knora.org/873fb608db03/values/434ccd6f6726"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "k6r",
                resourceIri = "http://data.knora.org/873fb608db03"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 64.\nHolzschnitt zu Kap. 64: Von b\u00F6sen Weibern.\nHolzschnitt identisch mit Kap. 35: Ein Esel bleibt st\u00F6rrisch stehen, w\u00E4hrend ein Narr, der auf ihm sitzt, eine Peitsche schwingt und mit seinen Sporen gegen die Ohren tritt. Auch eine Frau, die an seinem Schweif zieht und ein Hund, der ihn anbellt, treiben den Esel so wenig an, dass eine Schnecke ihn am Boden begleiten kann.\n11.6 x 8.4 cm.\nUnkoloriert."
                    ),
                    valueIri = "http://data.knora.org/7e124d274b03/values/412672d77026"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "k7v",
                resourceIri = "http://data.knora.org/7e124d274b03"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 5.\nHolzschnitt zu Kap. 5: Noch im Alter ein Narr sein.\nEin alter Narr, der mit einem Bein im Grab steht und dem schon das Schindmesser im Hintern steckt, geht auf Kr\u00FCcken weiter seines Weges. \u00DCber ihm am Himmel ist ein leerer Wappenschild und der Name \u201EHaintz Nar\u201C zu sehen."
                    ),
                    valueIri = "http://data.knora.org/91685136be03/values/13ed70872a26"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "b7r",
                resourceIri = "http://data.knora.org/91685136be03"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 102.\nHolzschnitt zu Kap. 102: Von Falschheit und Betrug\nEin Narr hantiert mit Ger\u00E4tschaften aus der Alchimistenk\u00FCche an einem Ofen. Ihm assistiert eine Gelehrter, der Hinter ihm steht. Ein zweiter Narr hockt vor einem Weinfass und r\u00FChrt mit einem Knochen in dessen Inhalt herum.\n11.6 x 8.4 cm.\nUnkoloriert.\nOben rechts Blattnummerierung (Graphitstift): \"137\"."
                    ),
                    valueIri = "http://data.knora.org/fda98c756503/values/791d3b218826"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "s1r",
                resourceIri = "http://data.knora.org/fda98c756503"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 36.\nHolzschnitt zu Kap. 36: Vom Eigensinn.\nEin Narr, der ein Nest pl\u00FCndern wollte, st\u00FCrzt vom Baum herab. Die aus dem Nest gefallenen V\u00F6gel liegen tot am Boden.\n11.5 x 8.4 cm.\nUnkoloriert."
                    ),
                    valueIri = "http://data.knora.org/2a8a4f993b03/values/0d7b5fbe4626"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "f5v",
                resourceIri = "http://data.knora.org/2a8a4f993b03"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 101.\nHolzschnitt zu Kap. 101: Von der Ohrenbl\u00E4serei\nVor einer Landschaftskulisse fl\u00FCstert ein Narr, der ein Zepter in seiner Rechten h\u00E4lt, einem anderen Narr, der links neben ihm steht und sich begierig zur Seite neigt, etwas ins Ohr, 11.6 x 8.4 cm."
                    ),
                    valueIri = "http://data.knora.org/b4e47ebbeb03/values/aa4c9b028726"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "p2v",
                resourceIri = "http://data.knora.org/b4e47ebbeb03"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 65.\nHolzschnitt zu Kap. 65: Von Astrologie und anderem Aberglauben.\nEin Narr, an dessen Seite ein Fuchsschwanz h\u00E4ngt, will einen Gelehrten \u00FCberreden, auf die Gestirne und den Flug der V\u00F6gel zu achten. Daf\u00FCr fasst er den Gelehrten bei der Schulter des Gelehrten und weist mit seiner Rechten zum Himmel.\n11.6 x 8.4 cm.\nUnkoloriert."
                    ),
                    valueIri = "http://data.knora.org/922989114c03/values/8aa36b837126"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "l1v",
                resourceIri = "http://data.knora.org/922989114c03"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 84.\nHolzschnitt zu Kap. 84: Vom Festhalten am Guten.\nHolzschnitt identisch mit Kap. 8: Ein Narr, dessen Linke mit einem Falknerhandschuh gesch\u00FCtzt wird, auf dem ein Vogel sitzt, lenkt einen Pflug, den ein zweiter Narr zieht.\n11.6 x 8.6 cm."
                    ),
                    valueIri = "http://data.knora.org/9dec78e5e103/values/d1fa09977b26"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "m5r",
                resourceIri = "http://data.knora.org/9dec78e5e103"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 24.\nHolzschnitt zu Kap. 24: Sich um zu viel bek\u00FCmmern.\nEin Narr beugt sich unter der Last einer Erdkugel, die er auf seinen Schultern tr\u00E4gt. 11.7 x 8.4 cm.\n"
                    ),
                    valueIri = "http://data.knora.org/f380f1478203/values/c7b6eef63726"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "e3r",
                resourceIri = "http://data.knora.org/f380f1478203"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 38.\nHolzschnitt zu Kap. 38: Nichts f\u00FCr sich behalten k\u00F6nnen.\nEin Narr, der hinter einem Geb\u00FCsch lauert, hat am Boden ein grosses Fangnetz gespannt. Die V\u00F6gel gehen jedoch dem Netz aus dem Weg oder fliegen davon.\n11.7 x 8.4 cm.\nUnkoloriert."
                    ),
                    valueIri = "http://data.knora.org/3ea18b833c03/values/3d1792534a26"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "f7v",
                resourceIri = "http://data.knora.org/3ea18b833c03"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 96.\nHolzschnitt zu Kap. 96: Schenken und hinterdrein bereuen.\nEin Narr, der vor einem Haus steht, \u00FCberreicht einem b\u00E4rtigen Alten ein Geschenk, kratzt sich dabei aber unschl\u00FCssig am Kopf, 11.6 x 8.3 cm."
                    ),
                    valueIri = "http://data.knora.org/feb9d5fda203/values/00040fe08326"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "o2v",
                resourceIri = "http://data.knora.org/feb9d5fda203"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 11.\nHolzschnitt zu Kap. 11: Von Missachtung der Heiligen Schrift.\nEin Narr, der zwei B\u00FCcher mit F\u00FCssen tritt, spricht mit einem in ein Leichentuch geh\u00FCllten, wiedererweckten Toten, der auf seiner Bahre hockt.\n11.6 x 8.5 cm."
                    ),
                    valueIri = "http://data.knora.org/57b9236ac103/values/21ba18052226"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "c6r",
                resourceIri = "http://data.knora.org/57b9236ac103"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 52.\nHolzschnitt zu Kap. 52: Von Geldheiraten.\nEin junger, bekr\u00E4nzter Narr, nimmt mit seiner Linken einen Gelbeutel von einer alten Frau in Empfang. In seiner Rechten h\u00E4lt er einen L\u00F6ffel, den er einem Esel unter dem Schwanz h\u00E4lt.\n11.7 x 8.5 cm.\nUnkoloriert."
                    ),
                    valueIri = "http://data.knora.org/68658a4a4403/values/ed9459926a26"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "h8v",
                resourceIri = "http://data.knora.org/68658a4a4403"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 39.\nHolzschnitt zu Kap. 39: Nichts f\u00FCr sich behalten k\u00F6nnen.\nEin Narr, der hinter einem Geb\u00FCsch lauert, hat am Boden ein grosses Fangnetz gespannt. Die V\u00F6gel gehen jedoch dem Netz aus dem Weg oder fliegen davon.\n11.7 x 8.5 cm.\nUnkoloriert."
                    ),
                    valueIri = "http://data.knora.org/52b8c76d3d03/values/94e78623b526"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "g1v",
                resourceIri = "http://data.knora.org/52b8c76d3d03"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 10.\nHolzschnitt zu Kap. 10: Vom rechten Verhalten gegen\u00FCber dem N\u00E4chsten.\nEin Narr verpr\u00FCgelt einen Mann auf der Strasse. Eine Zuschauergruppe versucht den Narren zu bes\u00E4nftigen. 11.5 x 8.3 cm.\n"
                    ),
                    valueIri = "http://data.knora.org/67df4ce07b03/values/e12ae3593026"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "c5r",
                resourceIri = "http://data.knora.org/67df4ce07b03"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 65.\nHolzschnitt zu Kap. 65: Von Astrologie und anderem Aberglauben.\nEin Narr, an dessen Seite ein Fuchsschwanz h\u00E4ngt, will einen Gelehrten \u00FCberreden, auf die Gestirne und den Flug der V\u00F6gel zu achten. Daf\u00FCr fasst er den Gelehrten bei der Schulter des Gelehrten und weist mit seiner Rechten zum Himmel.\n11.6 x 8.4 cm."
                    ),
                    valueIri = "http://data.knora.org/834ea3949403/values/c779184a7126"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "k3r",
                resourceIri = "http://data.knora.org/834ea3949403"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 28.\nHolzschnitt zu Kap. 28: Vom N\u00F6rgeln an Gottes Werken.\nEin Narr, der auf einem Berg ein Feuer entfacht hat, h\u00E4lt seine Hand sch\u00FCtzend \u00FCber die Augen, w\u00E4hrend er seinen Blick auf die hell am Himmel strahlende Sonne richtet.\n11.7 x 8.5 cm.\nUnkoloriert."
                    ),
                    valueIri = "http://data.knora.org/5022417b3703/values/ebabd4a63a26"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "e4v",
                resourceIri = "http://data.knora.org/5022417b3703"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 73.\nHolzschnitt Lemmer 1979, S. 118: Neue Illustration zu Kap. 73.\nVor einer Stadtvedute f\u00FChrt ein Narr zwei Esel an Stricken hinter sich her, 11.5 x 8.3 cm."
                    ),
                    valueIri = "http://data.knora.org/d3aa933d9803/values/7d69f1517526"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "l3r",
                resourceIri = "http://data.knora.org/d3aa933d9803"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 20.\nHolzschnitt zu Kap. 20: Vom Finden fremden Eigentums.\nEin Narr streckt seine H\u00E4nde nach einem Schatz im Boden aus. Von Hinten tritt der Teufel in Gestalt eines Mischwesens heran und fl\u00FCstert dem Narr mit einem Blasebalg einen Gedanken ein.\n11.6 x 8.5 cm.\nUnkoloriert.\n"
                    ),
                    valueIri = "http://data.knora.org/76ba325d3303/values/66eb5b803526"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "d3v",
                resourceIri = "http://data.knora.org/76ba325d3303"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 89.\nHolzschnitt zu Kap. 89: Von t\u00F6richtem Tausch.\nEin Narr, der in einer Landschaft einem Mann begegnet, tauscht mit diesem sein Maultier gegen einen Dudelsack, 11.7 x 8.3 cm."
                    ),
                    valueIri = "http://data.knora.org/d9312da4e403/values/3e6de9f27e26"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "n3r",
                resourceIri = "http://data.knora.org/d9312da4e403"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 20.\nHolzschnitt zu Kap. 20: Vom Finden fremden Eigentums.\nEin Narr streckt seine H\u00E4nde nach einem Schatz im Boden aus. Von Hinten tritt der Teufel in Gestalt eines Mischwesens heran und fl\u00FCstert dem Narr mit einem Blasebalg einen Gedanken ein. 11.6 x 8.5 cm.\n"
                    ),
                    valueIri = "http://data.knora.org/cb5279738003/values/a3c108473526"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "d7r",
                resourceIri = "http://data.knora.org/cb5279738003"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 99.\nHolzschnitt zu Kap. 99: Von der Einbusse des christlichen Reiches\nAuf einem Hof kniet ein Narr vor den Vertretern der kirchlichen und weltlichen Obrigkeit, die vor ein Portal getreten sind, und bittet darum, sie m\u00F6gen die Narrenkappe verschm\u00E4hen. Im Hintergrund kommentieren zwei weitere Narren \u00FCber die Hofmauer hinweg das Geschehen mit ungl\u00E4ubigen Gesten, 11.7 x 8.5 cm."
                    ),
                    valueIri = "http://data.knora.org/02abe871e903/values/1852a8aa8526"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "o5v",
                resourceIri = "http://data.knora.org/02abe871e903"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 58.\nHolzschnitt zu Kap. 58: Sich um die Angelegenheiten anderer k\u00FCmmern.\nEin Narr versucht mit einem Wassereimer den Brand im Haus des Nachbarn zu l\u00F6schen und wird dabei von einem anderen Narren, der an seinem Mantel zerrt, unterbrochen, den hinter ihm steht auch sein eigenes Haus in Flammen.\n11.6 x 8.5 cm.\nUnkoloriert."
                    ),
                    valueIri = "http://data.knora.org/b8c17af34703/values/5a0739ee6d26"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "i8v",
                resourceIri = "http://data.knora.org/b8c17af34703"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 29.\nHolzschnitt zu Kap. 29: Von Verkennung der Mitmenschen.\nEin Narr verspottet einen Sterbenden, neben dessen Bett eine Frau betet, w\u00E4hrend sich unter dem Narren die H\u00F6lle in Gestalt eines gefr\u00E4ssigen Drachenkopfs auftut, 11.7 x 8.5 cm.\n"
                    ),
                    valueIri = "http://data.knora.org/0b8940a6c903/values/f752218c3b26"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "e8r",
                resourceIri = "http://data.knora.org/0b8940a6c903"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 84.\nHolzschnitt zu Kap. 84: Vom Festhalten am Guten.\nHolzschnitt identisch mit Kap. 8: Ein Narr, dessen Linke mit einem Falknerhandschuh gesch\u00FCtzt wird, auf dem ein Vogel sitzt, lenkt einen Pflug, den ein zweiter Narr zieht.\n11.6 x 8.6 cm.\nUnkoloriert."
                    ),
                    valueIri = "http://data.knora.org/be830ecb5903/values/574eb0097c26"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "o7v",
                resourceIri = "http://data.knora.org/be830ecb5903"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 93.\nHolzschnitt zu Kap. 93: Von Wucher und Preistreiberei.\nVor einer st\u00E4dtischen Kulisse steht ein Mann, der in seinen Geldbeutel greift. Am rechten Rand verhandelt ein Narr mit ihm. Zwischen den beiden erkennt man Getreides\u00E4cke, links lagern Weinf\u00E4sser.\n11.5 x 8.4 cm.\nUnkoloriert."
                    ),
                    valueIri = "http://data.knora.org/c01995bd5f03/values/e8b575158226"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "q4v",
                resourceIri = "http://data.knora.org/c01995bd5f03"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 87.\nHolzschnitt zu Kap. 87: Von Gottesl\u00E4sterung.\nIn einer Landschaft h\u00E4ngt Christus am Kreuz. Von rechts n\u00E4hert sich ein Narr, der mit einem Dreizack auf den Gekreuzigten einsticht.\n11.6 x 8.5 cm.\nUnkoloriert.\nOben rechts Blattnummerierung (Graphitstift): \"117\"."
                    ),
                    valueIri = "http://data.knora.org/35c3334f5c03/values/32c69c0d7e26"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "p5r",
                resourceIri = "http://data.knora.org/35c3334f5c03"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Titelblatt (Hartl 2001: Stultitia Navis I).\nHolzschnitt: \nErsatzholzschnitt f\u00FCr Titelblatt, recto:\nEin Schiff voller Narren f\u00E4hrt nach links. Hinten auf der Br\u00FCcke trinkt ein Narr aus einer Flasche, vorne pr\u00FCgeln sich zwei weitere narren so sehr, dass einer von ihnen \u00FCber Bord zu gehen droht. Oben die Inschrift \"Nauis stultoru(m).\"; auf dem Schiffsrumpf die Datierung \"1.4.9.7.\".\n6.5 x 11.5 cm.\noben rechts die bibliographische Angabe  (Graphitstift) \"Hain 3750\"; unten rechts Bibliotheksstempel (queroval, schwarz): \"BIBL. PUBL.| BASILEENSIS\"."
                    ),
                    valueIri = "http://data.knora.org/05c7acceb703/values/5f23f3171d26"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "a1r; Titelblatt, recto",
                resourceIri = "http://data.knora.org/05c7acceb703"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 14.\nHolzschnitt zu Kap. 14: Von falschen Vorstellungen \u00FCber Gott.\nEin Narr, der ein Kummet um seinen Hals tr\u00E4gt, geht mit B\u00FCchse und L\u00F6ffel auf einen Trog zu, aus dem Schweine und G\u00E4nse fressen.\n11.7 x 8.5 cm."
                    ),
                    valueIri = "http://data.knora.org/f5db7dc9c203/values/f08ab8232326"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "d1r",
                resourceIri = "http://data.knora.org/f5db7dc9c203"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 18.\nHolzschnitt zu Kap. 18: Vom Dienst an zwei Herren.\nEin mit Spiess bewaffneter Narr bl\u00E4st in ein Horn. Sein Hund versucht derweil im Hintergrund zwei Hasen gleichzeitig zu erjagen, 11.6 x 8.4 cm.\n"
                    ),
                    valueIri = "http://data.knora.org/1d0af69dc403/values/4e9dc2b53326"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "d5r",
                resourceIri = "http://data.knora.org/1d0af69dc403"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 111.\nHolzschnitt zu Kap. 111: Entschuldigung des Dichters\nEin Narr hat seine Attribute \u2013 die Kappe und das Zepter - hinter sich abgelegt und kniet betend vor einem Altar. In seinen H\u00E4nden h\u00E4lt er die Kappe des Gelehrten.  Von hinten n\u00E4hert sich eine f\u00FCnfk\u00F6pfige Narrenschar und kommentiert das Geschehen mit erregten Gesten.\n11.6 x 8.4 cm.\nUnkoloriert.\nOben rechts Blattnummerierung (Graphitstift): \"159\".\nUnten einige Notizen (Feder, graubraun)."
                    ),
                    valueIri = "http://data.knora.org/d9a721866f03/values/475badf38d26"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "v1r",
                resourceIri = "http://data.knora.org/d9a721866f03"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 81.\nHolzschnitt zu Kap. 81: Aus K\u00FCche und Keller.\nEin Narr f\u00FChrt von einem Boot aus vier Knechte am Strick, die sich in einer K\u00FCche \u00FCber Spreis und Trank hermachen, w\u00E4hrend eine Frau, die am Herdfeuer sitzt, das Essen zubereitet, 11.7 x 8.5 cm."
                    ),
                    valueIri = "http://data.knora.org/0ff047fc9a03/values/b9ac70cc7926"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "m1r",
                resourceIri = "http://data.knora.org/0ff047fc9a03"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 34.\nHolzschnitt zu Kap. 34: Ein Narr sein und es bleiben.\nEin Narr wird von drei G\u00E4nsen umgeben, deren eine von ihm wegfliegt, 11.7 x 8.4 cm.\n"
                    ),
                    valueIri = "http://data.knora.org/57f41ddb8603/values/ddde2c294326"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "f5r",
                resourceIri = "http://data.knora.org/57f41ddb8603"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 66.\nHolzschnitt zu Kap. 66: Von der Erforschung der Welt.\nEin Narr hat ein Schema des Universums auf den Boden Gezeichnet und vermisst es mit einem Zirkel. Von hinten blickt ein zweiter Narr \u00FCber eine Mauer und wendet sich dem ersten mit sp\u00F6ttischen Gesten zu.\n11.6 x 8.4 cm."
                    ),
                    valueIri = "http://data.knora.org/73287a1eda03/values/747b2d516626"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "k4r",
                resourceIri = "http://data.knora.org/73287a1eda03"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 28.\nHolzschnitt zu Kap. 28: Vom N\u00F6rgeln an Gottes Werken.\nEin Narr, der auf einem Berg ein Feuer entfacht hat, h\u00E4lt seine Hand sch\u00FCtzend \u00FCber die Augen, w\u00E4hrend er seinen Blick auf die hell am Himmel strahlende Sonne richtet, 11.7 x 8.5 cm."
                    ),
                    valueIri = "http://data.knora.org/817d2231c903/values/aed527e03a26"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "e7r",
                resourceIri = "http://data.knora.org/817d2231c903"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 35.\nHolzschnitt zu Kap. 35: Leicht zornig werden.\nEin Esel bleibt st\u00F6rrisch stehen, w\u00E4hrend ein Narr, der auf ihm sitzt, eine Peitsche schwingt und mit seinen Sporen gegen die Ohren tritt. Auch eine Frau, die an seinem Schweif zieht und ein Hund, der ihn anbellt, treiben den Esel so wenig an, dass eine Schnecke ihn am Boden begleiten kann, 11.6 x 8.4 cm. \n"
                    ),
                    valueIri = "http://data.knora.org/e1ff3b508703/values/f52cc6f34426"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "f6r",
                resourceIri = "http://data.knora.org/e1ff3b508703"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 37.\nHolzschnitt zu Kap. 37: Von der Wandelbarkeit des Gl\u00FCcks.\nHolzschnitt identisch mit Kap. 56. Die Hand Gottes treibt das Gl\u00FCcksrad an, auf dem ein Esel und zwei Mischwesen \u2013 halb Esel, halb Narr \u2013 herumgewirbelt werden. Der von der Abw\u00E4rtsbewegung des Rades Mitgerissene st\u00FCrzt in das unter ihm ausgehobene Grab, dessen Deckel im Vordergrund bereit liegt.\n11.5 x 8.4 cm."
                    ),
                    valueIri = "http://data.knora.org/d1d912dacc03/values/93ce05314726"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "f7r",
                resourceIri = "http://data.knora.org/d1d912dacc03"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 9.\nHolzschnitt Lemmer 1979, S. 115: Variante f\u00FCr Kap. 9.\nEin junger, reich gekleideter Mann zieht seine Narrenkappe an einem Strick hinter sich her. Der Bildinhalt stimmt weitgehend mit dem urspr\u00FCnglichen Holzschnitt \u00FCberein: zus\u00E4tzlich zur Kappe zieht der Narr hier auch ein Zepter hinter sich her.\n11.8 x 8.4 cm."
                    ),
                    valueIri = "http://data.knora.org/43a2e77fc003/values/1513cc1f2126"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "c4r",
                resourceIri = "http://data.knora.org/43a2e77fc003"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 30.\nHolzschnitt zu Kap. 30: Von Pfr\u00FCndenj\u00E4gern.\nEin Narr l\u00E4dt so viele S\u00E4cke auf seinen Esel, dass dieser unter der Last zusammenbricht.\n11.5 x 8.2 cm.\nUnkoloriert.\n"
                    ),
                    valueIri = "http://data.knora.org/64397d653803/values/ba7c74c53b26"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "e6v",
                resourceIri = "http://data.knora.org/64397d653803"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 104.\nHolzschnitt zu Kap. 104: Die Wahrheit verschweigen\nEin Narr steht auf einer h\u00F6lzernen Kanzel und wendet sich einer Menschenmenge zu, spricht aber nicht zu ihr, sondern h\u00E4lt seinen linken Zeigefinger schweigend vor den Mund. Manche aus dem Publikum drohen ihm mit St\u00F6cken und Schwertern, andere schlafen oder wenden sich vom Geschehen ab, 11.6 x 8.3 cm."
                    ),
                    valueIri = "http://data.knora.org/76443e7ba803/values/48eeda3f8926"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "p6v",
                resourceIri = "http://data.knora.org/76443e7ba803"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 43.\nHolzschnitt zu Kap. 43: Missachten der ewigen Seligkeit\nEin Narr steht mit einer grossen Waage in einer Landschaft und wiegt das Himmelsfirmament (links) gegen eine Burg (rechts) auf. Die Zunge der Waage schl\u00E4gt zugunsten der Burg aus, 11.5 x 8.4 cm.\n"
                    ),
                    valueIri = "http://data.knora.org/0d1fc798cf03/values/e75f1e764d26"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "g5r",
                resourceIri = "http://data.knora.org/0d1fc798cf03"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 56.\nHolzschnitt zu Kap. 56: Alle Macht nimmt einmal ein Ende.\nHolzschnitt identisch mit Kap. 37: Die Hand Gottes treibt das Gl\u00FCcksrad an, auf dem ein Esel und zwei Mischwesen \u2013 halb Esel, halb Narr \u2013 herumgewirbelt werden. Der von der Abw\u00E4rtsbewegung des Rades Mitgerissene st\u00FCrzt in das unter ihm ausgehobene Grab, dessen Deckel im Vordergrund bereit liegt.\n11.5 x 8.4 cm."
                    ),
                    valueIri = "http://data.knora.org/95cf588c8f03/values/4e60ec086d26"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "h8r",
                resourceIri = "http://data.knora.org/95cf588c8f03"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 30.\nHolzschnitt zu Kap. 30: Von Pfr\u00FCndenj\u00E4gern.\nEin Narr l\u00E4dt so viele S\u00E4cke auf seinen Esel, dass dieser unter der Last zusammenbricht. 11.5 x 8.2 cm.\n"
                    ),
                    valueIri = "http://data.knora.org/2fc6a5068503/values/40d01a383c26"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "f1r",
                resourceIri = "http://data.knora.org/2fc6a5068503"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 102.\nHolzschnitt zu Kap. 102: Von Falschheit und Betrug\nEin Narr hantiert mit Ger\u00E4tschaften aus der Alchimistenk\u00FCche an einem Ofen. Ihm assistiert eine Gelehrter, der Hinter ihm steht. Ein zweiter Narr hockt vor einem Weinfass und r\u00FChrt mit einem Knochen in dessen Inhalt herum, 11.6 x 8.4 cm."
                    ),
                    valueIri = "http://data.knora.org/3ef09c30ec03/values/f3c994ae8726"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "p3v",
                resourceIri = "http://data.knora.org/3ef09c30ec03"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 58.\nHolzschnitt zu Kap. 58: Sich um die Angelegenheiten anderer k\u00FCmmern.\nEin Narr versucht mit einem Wassereimer den Brand im Haus des Nachbarn zu l\u00F6schen und wird dabei von einem anderen Narren, der an seinem Mantel zerrt, unterbrochen, den hinter ihm steht auch sein eigenes Haus in Flammen.\n11.6 x 8.5 cm."
                    ),
                    valueIri = "http://data.knora.org/a9e694769003/values/97dde5b46d26"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "i2r",
                resourceIri = "http://data.knora.org/a9e694769003"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 30.\nHolzschnitt zu Kap. 30: Von Pfr\u00FCndenj\u00E4gern.\nEin Narr l\u00E4dt so viele S\u00E4cke auf seinen Esel, dass dieser unter der Last zusammenbricht, 11.5 x 8.2 cm.\n"
                    ),
                    valueIri = "http://data.knora.org/95945e1bca03/values/7da6c7fe3b26"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "f1r",
                resourceIri = "http://data.knora.org/95945e1bca03"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 8.\nHolzschnitt zu Kap. 8: Guten Rat verschm\u00E4hen.\nEin Narr, dessen Linke mit einem Falknerhandschuh gesch\u00FCtzt wird, auf dem ein Vogel sitzt, lenkt einen Pflug, den ein zweiter Narr zieht.\n11.6 x 8.6 cm.\nUnkoloriert."
                    ),
                    valueIri = "http://data.knora.org/600d70802c03/values/9ad344462626"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "b4v",
                resourceIri = "http://data.knora.org/600d70802c03"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 54.\nHolzschnitt zu Kap. 54: Sich nicht bessern wollen.\nEin Narr steht auf einer Strasse und bl\u00E4st auf einem Dudelsack w\u00E4hrend er Laute und Harfe unbeachtet am Boden liegen l\u00E4sst, 11.7 x 8.4 cm.\n"
                    ),
                    valueIri = "http://data.knora.org/81b81ca28e03/values/bc65f9b06b26"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "h6r",
                resourceIri = "http://data.knora.org/81b81ca28e03"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 82.\nHolzschnitt zu Kap. 82: Vom st\u00E4ndischen Ehrgeiz.\nEin Narr, der kostbare Frauenkleider und Schmuck tr\u00E4gt, steht vor dem Tor eines Hauses und versucht einen \u201EDreispitz\u201C in einen Sack zu stecken. Diese Handlung des Narren wird von einem Spruchband mit der Aussage \u201EEr mu(o)\u00DF dryn\u201C begleitet.\n11.7 x 8.4 cm.\nUnkoloriert."
                    ),
                    valueIri = "http://data.knora.org/965596f65703/values/c553bdb17a26"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "o3v",
                resourceIri = "http://data.knora.org/965596f65703"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 93.\nHolzschnitt zu Kap. 93: Von Wucher und Preistreiberei.\nVor einer st\u00E4dtischen Kulisse steht ein Mann, der in seinen Geldbeutel greift. Am rechten Rand verhandelt ein Narr mit ihm. Zwischen den beiden erkennt man Getreides\u00E4cke, links lagern Weinf\u00E4sser, 11.5 x 8.4 cm."
                    ),
                    valueIri = "http://data.knora.org/60977b9ea103/values/258c22dc8126"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "n7v",
                resourceIri = "http://data.knora.org/60977b9ea103"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 21.\nHolzschnitt zu Kap. 21: Andere tadeln und selbst unrecht handeln.\nEin Narr, der mit seinen Beinen im Sumpf steckt, zeigt auf einen nahen Weg, an dem ein Bildstock die Richtung weist, 11.7 x 8.5 cm.\n"
                    ),
                    valueIri = "http://data.knora.org/555e97e88003/values/ec3e02f33526"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "d8r",
                resourceIri = "http://data.knora.org/555e97e88003"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 35.\nHolzschnitt zu Kap. 35: Leicht zornig werden.\nEin Esel bleibt st\u00F6rrisch stehen, w\u00E4hrend ein Narr, der auf ihm sitzt, eine Peitsche schwingt und mit seinen Sporen gegen die Ohren tritt. Auch eine Frau, die an seinem Schweif zieht und ein Hund, der ihn anbellt, treiben den Esel so wenig an, dass eine Schnecke ihn am Boden begleiten kann.\n11.6 x 8.4 cm.\nUnkoloriert."
                    ),
                    valueIri = "http://data.knora.org/a07e31243b03/values/6332d39b4326"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "f4v",
                resourceIri = "http://data.knora.org/a07e31243b03"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 12.\nHolzschnitt zu Kap. 12: Von Unbedachtsamkeit.\nEin Narr f\u00E4llt von seinem Esel, weil er den Sattelgurt nicht geschnallt hat.\n11.7 x 8.5 cm."
                    ),
                    valueIri = "http://data.knora.org/7bf688ca7c03/values/2aa8dc053126"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "c7r",
                resourceIri = "http://data.knora.org/7bf688ca7c03"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 50.\nHolzschnitt zu Kap. 50: Von der Wollust.\nDie als n\u00E4rrischen Frau personifizierte Wollust f\u00FChrt einen Vogel, ein Schaf und einen Ochsen an Seilen. Ein Narr packt die beiden grossen Tiere beim Schwanz.\n11.7 x 8.5 cm."
                    ),
                    valueIri = "http://data.knora.org/598aa4cd8c03/values/5b9a663a6926"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "h2r",
                resourceIri = "http://data.knora.org/598aa4cd8c03"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 78.\nHolzschnitt zu Kap. 78: Von Narren, die sich selbst Bedr\u00FCckung verschaffen.\nEin Narr, dem ein Esel auf den R\u00FCcken zu springen versucht, st\u00FCrzt zu Boden, 11.6 x 8.4 cm."
                    ),
                    valueIri = "http://data.knora.org/71cded9c9903/values/de3484c87726"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "l6r",
                resourceIri = "http://data.knora.org/71cded9c9903"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 64.\nHolzschnitt zu Kap. 64: Von b\u00F6sen Weibern.\nHolzschnitt identisch mit Kap. 35: Ein Esel bleibt st\u00F6rrisch stehen, w\u00E4hrend ein Narr, der auf ihm sitzt, eine Peitsche schwingt und mit seinen Sporen gegen die Ohren tritt. Auch eine Frau, die an seinem Schweif zieht und ein Hund, der ihn anbellt, treiben den Esel so wenig an, dass eine Schnecke ihn am Boden begleiten kann.\n11.6 x 8.4 cm."
                    ),
                    valueIri = "http://data.knora.org/e52b49359303/values/7efc1e9e7026"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "i8r",
                resourceIri = "http://data.knora.org/e52b49359303"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 94.\nHolzschnitt zu Kap. 94: Von der Hoffnung, andere zu beerben.\nEin Narr beschl\u00E4gt die Vorderhufe seines Esels auf dessen R\u00FCcken der Tod in Gestalt eines Skeletts rittlings sitzt. Er schwingt einen Knochen, um so die Fr\u00FCchte aus der Krone eines nahen Baumes zu schlagen, 11.7 x 8.4 cm."
                    ),
                    valueIri = "http://data.knora.org/eaa29913a203/values/6e091c888226"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "n8v",
                resourceIri = "http://data.knora.org/eaa29913a203"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 6.\nHolzschnitt zu Kap. 6: Von mangelhafter Erziehung der Kinder.\nZwei Jungen geraten am Spieltisch \u00FCber Karten und W\u00FCrfen in Streit. W\u00E4hrend der eine einen Dolch z\u00FCckt und der andere nach seinem Schwert greift, sitzt ein \u00E4lterer Narr mit verbundenen Augen ahnungslos neben dem Geschehen.\n11.7 x 8.5 cm."
                    ),
                    valueIri = "http://data.knora.org/1b746fabbe03/values/8318d9c71f26"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "b8r",
                resourceIri = "http://data.knora.org/1b746fabbe03"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 24.\nHolzschnitt zu Kap. 24: Sich um zu viel bek\u00FCmmern.\nEin Narr beugt sich unter der Last einer Erdkugel, die er auf seinen Schultern tr\u00E4gt, 11.7 x 8.4 cm.\n"
                    ),
                    valueIri = "http://data.knora.org/594faa5cc703/values/048d9bbd3726"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "e3r",
                resourceIri = "http://data.knora.org/594faa5cc703"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 82.\nHolzschnitt zu Kap. 82: Vom st\u00E4ndischen Ehrgeiz.\nEin Narr, der kostbare Frauenkleider und Schmuck tr\u00E4gt, steht vor dem Tor eines Hauses und versucht einen \u201EDreispitz\u201C in einen Sack zu stecken. Diese Handlung des Narren wird von einem Spruchband mit der Aussage \u201EEr mu(o)\u00DF dryn\u201C begleitet, 11.7 x 8.4 cm."
                    ),
                    valueIri = "http://data.knora.org/ffc91e86e003/values/3f00173f7a26"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "m2r",
                resourceIri = "http://data.knora.org/ffc91e86e003"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 95.\nHolzschnitt Lemmer 1979, S. 120: Neue Illustration zu Kap. 95\nEin Narr kniet vor einem Wagen, um ein Rad zu wechseln. Im Wagen tummeln sich zahlreiche Affen. Hinter dem Wagen ist eine Kirche zu sehen, 11.6 x 8.3 cm."
                    ),
                    valueIri = "http://data.knora.org/74aeb788a203/values/b78615348326"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "o1v",
                resourceIri = "http://data.knora.org/74aeb788a203"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 56.\nHolzschnitt zu Kap. 56: Alle Macht nimmt einmal ein Ende.\nHolzschnitt identisch mit Kap. 37: Die Hand Gottes treibt das Gl\u00FCcksrad an, auf dem ein Esel und zwei Mischwesen \u2013 halb Esel, halb Narr \u2013 herumgewirbelt werden. Der von der Abw\u00E4rtsbewegung des Rades Mitgerissene st\u00FCrzt in das unter ihm ausgehobene Grab, dessen Deckel im Vordergrund bereit liegt.\n11.5 x 8.4 cm.\nUnkoloriert."
                    ),
                    valueIri = "http://data.knora.org/9093021f4603/values/8b3699cf6c26"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "i4v",
                resourceIri = "http://data.knora.org/9093021f4603"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 99.\nHolzschnitt zu Kap. 99: Von der Einbusse des christlichen Reiches\nAuf einem Hof kniet ein Narr vor den Vertretern der kirchlichen und weltlichen Obrigkeit, die vor ein Portal getreten sind, und bittet darum, sie m\u00F6gen die Narrenkappe verschm\u00E4hen. Im Hintergrund kommentieren zwei weitere Narren \u00FCber die Hofmauer hinweg das Geschehen mit ungl\u00E4ubigen Gesten, 11.7 x 8.5 cm."
                    ),
                    valueIri = "http://data.knora.org/9cdc2f5da403/values/db7bfbe38526"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "o5v",
                resourceIri = "http://data.knora.org/9cdc2f5da403"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 9.\nHolzschnitt Lemmer 1979, S. 115: Variante f\u00FCr Kap. 9.\nEin junger, reich gekleideter Mann zieht seine Narrenkappe an einem Strick hinter sich her. Der Bildinhalt stimmt weitgehend mit dem urspr\u00FCnglichen Holzschnitt \u00FCberein: zus\u00E4tzlich zur Kappe zieht der Narr hier auch ein Zepter hinter sich her.\n11.8 x 8.4 cm."
                    ),
                    valueIri = "http://data.knora.org/ddd32e6b7b03/values/98ade9ad2f26"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "c4r",
                resourceIri = "http://data.knora.org/ddd32e6b7b03"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 60.\nHolzschnitt zu Kap. 60: Von Selbstgef\u00E4lligkeit.\nEin alter Narr steht am Ofen und r\u00FChrt in einem Topf. Gleichzeitig schaut er sich dabei in einem Handspiegel an.\n11.7 x 8.5 cm."
                    ),
                    valueIri = "http://data.knora.org/bdfdd0609103/values/66ae85d36e26"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "i4r",
                resourceIri = "http://data.knora.org/bdfdd0609103"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map("http://www.knora.org/ontology/incunabula#description" -> Vector(ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Beginn Kapitel 52.\nHolzschnitt zu Kap. 52: Von Geldheiraten.\nEin junger, bekr\u00E4nzter Narr, nimmt mit seiner Linken einen Gelbeutel von einer alten Frau in Empfang. In seiner Rechten h\u00E4lt er einen L\u00F6ffel, den er einem Esel unter dem Schwanz h\u00E4lt, 11.5 x 8.4 cm.\n"
                    ),
                    valueIri = "http://data.knora.org/6da1e0b78d03/values/2a6b06596a26"
                ))),
                resourceClass = "http://www.knora.org/ontology/incunabula#page",
                label = "h4r",
                resourceIri = "http://data.knora.org/6da1e0b78d03"
            )
        ),
        numberOfResources = 210
    )

    val fulltextSearchForDinge = ReadResourcesSequenceV2(
        resources = Vector(ReadResourceV2(
            resourceInfos = Map(),
            values = Map("http://www.knora.org/ontology/anything#hasText" -> Vector(
                ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Ich liebe die Dinge, sie sind alles f\u00FCr mich."
                    ),
                    valueIri = "http://data.knora.org/a-thing-with-text-values/values/1"
                ),
                ReadValueV2(
                    valueContent = TextValueContentV2(
                        comment = None,
                        standoff = None,
                        valueHasString = "Na ja, die Dinge sind OK."
                    ),
                    valueIri = "http://data.knora.org/a-thing-with-text-values/values/2"
                )
            )),
            resourceClass = "http://www.knora.org/ontology/anything#Thing",
            label = "Ein Ding f\u00FCr jemanden, dem die Dinge gefallen",
            resourceIri = "http://data.knora.org/a-thing-with-text-values"
        )),
        numberOfResources = 1
    )

    val constructQueryForBooksWithTitleZeitgloecklein = ConstructQuery(
        whereClause = WhereClause(patterns = Vector(
            StatementPattern(
                namedGraph = None,
                obj = IriRef(
                    iri = "http://0.0.0.0:3333/ontology/incunabula/simple/v2#book"
                ),
                pred = IriRef(
                    iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type"
                ),
                subj = QueryVariable(variableName = "book")
            ),
            StatementPattern(
                namedGraph = None,
                obj = IriRef(
                    iri = "http://api.knora.org/ontology/knora-api/simple/v2#Resource"
                ),
                pred = IriRef(
                    iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type"
                ),
                subj = QueryVariable(variableName = "book")
            ),
            StatementPattern(
                namedGraph = None,
                obj = QueryVariable(variableName = "title"),
                pred = IriRef(
                    iri = "http://0.0.0.0:3333/ontology/incunabula/simple/v2#title"
                ),
                subj = QueryVariable(variableName = "book")
            ),
            StatementPattern(
                namedGraph = None,
                obj = IriRef(
                    iri = "http://www.w3.org/2001/XMLSchema#string"
                ),
                pred = IriRef(
                    iri = "http://api.knora.org/ontology/knora-api/simple/v2#objectType"
                ),
                subj = IriRef(
                    iri = "http://0.0.0.0:3333/ontology/incunabula/simple/v2#title"
                )
            ),
            StatementPattern(
                namedGraph = None,
                obj = IriRef(
                    iri = "http://www.w3.org/2001/XMLSchema#string"
                ),
                pred = IriRef(
                    iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type"
                ),
                subj = QueryVariable(variableName = "title")
            ),
            FilterPattern(expression = CompareExpression(
                rightArg = XsdLiteral(
                    datatype = "http://www.w3.org/2001/XMLSchema#string",
                    value = "Zeitgl\u00F6cklein des Lebens und Leidens Christi"
                ),
                operator = CompareExpressionOperator.EQUALS,
                leftArg = QueryVariable(variableName = "title")
            ))
        )), constructClause = ConstructClause(statements = Vector(
            StatementPattern(
                namedGraph = None,
                obj = XsdLiteral(
                    datatype = "http://www.w3.org/2001/XMLSchema#boolean",
                    value = "true"
                ),
                pred = IriRef(
                    iri = "http://api.knora.org/ontology/knora-api/simple/v2#isMainResource"
                ),
                subj = QueryVariable(variableName = "book")
            ),
            StatementPattern(
                namedGraph = None,
                obj = QueryVariable(variableName = "title"),
                pred = IriRef(
                    iri = "http://0.0.0.0:3333/ontology/incunabula/simple/v2#title"
                ),
                subj = QueryVariable(variableName = "book")
            )
        ))
    )

    val booksWithTitleZeitgloeckleinResponse = ReadResourcesSequenceV2(
        resources = Vector(
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map(
                    "http://www.knora.org/ontology/incunabula#title" -> Vector(ReadValueV2(
                        valueContent = TextValueContentV2(
                            comment = None,
                            standoff = None,
                            valueHasString = "Zeitgl\u00F6cklein des Lebens und Leidens Christi"
                        ),
                        valueIri = "http://data.knora.org/c5058f3a/values/c3295339"
                    )),
                    "http://www.knora.org/ontology/incunabula#citation" -> Vector(
                        ReadValueV2(
                            valueContent = TextValueContentV2(
                                comment = None,
                                standoff = None,
                                valueHasString = "Schramm Bd. XXI, S. 27"
                            ),
                            valueIri = "http://data.knora.org/c5058f3a/values/184e99ca01"
                        ),
                        ReadValueV2(
                            valueContent = TextValueContentV2(
                                comment = None,
                                standoff = None,
                                valueHasString = "GW 4168"
                            ),
                            valueIri = "http://data.knora.org/c5058f3a/values/db77ec0302"
                        ),
                        ReadValueV2(
                            valueContent = TextValueContentV2(
                                comment = None,
                                standoff = None,
                                valueHasString = "ISTC ib00512000"
                            ),
                            valueIri = "http://data.knora.org/c5058f3a/values/9ea13f3d02"
                        )
                    ),
                    "http://www.knora.org/ontology/incunabula#location" -> Vector(ReadValueV2(
                        valueContent = TextValueContentV2(
                            comment = None,
                            standoff = None,
                            valueHasString = "Universit\u00E4ts- und Stadtbibliothek K\u00F6ln, Sign: AD+S167"
                        ),
                        valueIri = "http://data.knora.org/c5058f3a/values/92faf25701"
                    )),
                    "http://www.knora.org/ontology/incunabula#url" -> Vector(ReadValueV2(
                        valueContent = TextValueContentV2(
                            comment = None,
                            standoff = None,
                            valueHasString = "http://www.ub.uni-koeln.de/cdm/compoundobject/collection/inkunabeln/id/1878/rec/1"
                        ),
                        valueIri = "http://data.knora.org/c5058f3a/values/10e00c7acc2704"
                    )),
                    "http://www.knora.org/ontology/incunabula#physical_desc" -> Vector(ReadValueV2(
                        valueContent = TextValueContentV2(
                            comment = None,
                            standoff = None,
                            valueHasString = "Dimension: 8\u00B0"
                        ),
                        valueIri = "http://data.knora.org/c5058f3a/values/5524469101"
                    )),
                    "http://www.knora.org/ontology/incunabula#publoc" -> Vector(ReadValueV2(
                        valueContent = TextValueContentV2(
                            comment = None,
                            standoff = None,
                            valueHasString = "Basel"
                        ),
                        valueIri = "http://data.knora.org/c5058f3a/values/0ca74ce5"
                    )),
                    "http://www.knora.org/ontology/incunabula#pubdate" -> Vector(ReadValueV2(
                        valueContent = DateValueContentV2(
                            comment = None,
                            valueHasCalendar = KnoraCalendarV1.JULIAN,
                            valueHasEndPrecision = KnoraPrecisionV1.YEAR,
                            valueHasStartPrecision = KnoraPrecisionV1.YEAR,
                            valueHasEndJDN = 2266376,
                            valueHasStartJDN = 2266011,
                            valueHasString = "1492-01-01 - 1492-12-31"
                        ),
                        valueIri = "http://data.knora.org/c5058f3a/values/cfd09f1e01"
                    )),
                    "http://www.knora.org/ontology/incunabula#publisher" -> Vector(ReadValueV2(
                        valueContent = TextValueContentV2(
                            comment = None,
                            standoff = None,
                            valueHasString = "Johann Amerbach"
                        ),
                        valueIri = "http://data.knora.org/c5058f3a/values/497df9ab"
                    ))
                ),
                resourceClass = "http://www.knora.org/ontology/incunabula#book",
                label = "Zeitgl\u00F6cklein des Lebens und Leidens Christi",
                resourceIri = "http://data.knora.org/c5058f3a"
            ),
            ReadResourceV2(
                resourceInfos = Map(),
                values = Map(
                    "http://www.knora.org/ontology/incunabula#title" -> Vector(
                        ReadValueV2(
                            valueContent = TextValueContentV2(
                                comment = None,
                                standoff = None,
                                valueHasString = "Zeitgl\u00F6cklein des Lebens und Leidens Christi"
                            ),
                            valueIri = "http://data.knora.org/ff17e5ef9601/values/d9a522845006"
                        ),
                        ReadValueV2(
                            valueContent = TextValueContentV2(
                                comment = None,
                                standoff = None,
                                valueHasString = "Horologium devotionis circa vitam Christi"
                            ),
                            valueIri = "http://data.knora.org/ff17e5ef9601/values/9ccf75bd5006"
                        )
                    ),
                    "http://www.knora.org/ontology/incunabula#citation" -> Vector(
                        ReadValueV2(
                            valueContent = TextValueContentV2(
                                comment = None,
                                standoff = None,
                                valueHasString = "Van der Haegen I: 16,46"
                            ),
                            valueIri = "http://data.knora.org/ff17e5ef9601/values/c0c45b6d5306"
                        ),
                        ReadValueV2(
                            valueContent = TextValueContentV2(
                                comment = None,
                                standoff = None,
                                valueHasString = "Goff B506"
                            ),
                            valueIri = "http://data.knora.org/ff17e5ef9601/values/83eeaea65306"
                        ),
                        ReadValueV2(
                            valueContent = TextValueContentV2(
                                comment = None,
                                standoff = None,
                                valueHasString = "H 2990* (I) = HC 2993* = H 8928*"
                            ),
                            valueIri = "http://data.knora.org/ff17e5ef9601/values/461802e05306"
                        ),
                        ReadValueV2(
                            valueContent = TextValueContentV2(
                                comment = None,
                                standoff = None,
                                valueHasString = "Schr 3442"
                            ),
                            valueIri = "http://data.knora.org/ff17e5ef9601/values/094255195406"
                        ),
                        ReadValueV2(
                            valueContent = TextValueContentV2(
                                comment = None,
                                standoff = None,
                                valueHasString = "Schramm XXI p.27"
                            ),
                            valueIri = "http://data.knora.org/ff17e5ef9601/values/cc6ba8525406"
                        ),
                        ReadValueV2(
                            valueContent = TextValueContentV2(
                                comment = None,
                                standoff = None,
                                valueHasString = "Pell 2247"
                            ),
                            valueIri = "http://data.knora.org/ff17e5ef9601/values/8f95fb8b5406"
                        ),
                        ReadValueV2(
                            valueContent = TextValueContentV2(
                                comment = None,
                                standoff = None,
                                valueHasString = "Polain(B) 629"
                            ),
                            valueIri = "http://data.knora.org/ff17e5ef9601/values/52bf4ec55406"
                        ),
                        ReadValueV2(
                            valueContent = TextValueContentV2(
                                comment = None,
                                standoff = None,
                                valueHasString = "IDL 801"
                            ),
                            valueIri = "http://data.knora.org/ff17e5ef9601/values/15e9a1fe5406"
                        ),
                        ReadValueV2(
                            valueContent = TextValueContentV2(
                                comment = None,
                                standoff = None,
                                valueHasString = "IGI 1617"
                            ),
                            valueIri = "http://data.knora.org/ff17e5ef9601/values/d812f5375506"
                        ),
                        ReadValueV2(
                            valueContent = TextValueContentV2(
                                comment = None,
                                standoff = None,
                                valueHasString = "Voull(B) 477"
                            ),
                            valueIri = "http://data.knora.org/ff17e5ef9601/values/9b3c48715506"
                        ),
                        ReadValueV2(
                            valueContent = TextValueContentV2(
                                comment = None,
                                standoff = None,
                                valueHasString = "Voull(Trier) 184"
                            ),
                            valueIri = "http://data.knora.org/ff17e5ef9601/values/5e669baa5506"
                        ),
                        ReadValueV2(
                            valueContent = TextValueContentV2(
                                comment = None,
                                standoff = None,
                                valueHasString = "Hubay(Augsburg) 351"
                            ),
                            valueIri = "http://data.knora.org/ff17e5ef9601/values/2190eee35506"
                        ),
                        ReadValueV2(
                            valueContent = TextValueContentV2(
                                comment = None,
                                standoff = None,
                                valueHasString = "Sack(Freiburg) 604"
                            ),
                            valueIri = "http://data.knora.org/ff17e5ef9601/values/e4b9411d5606"
                        ),
                        ReadValueV2(
                            valueContent = TextValueContentV2(
                                comment = None,
                                standoff = None,
                                valueHasString = "Finger 177"
                            ),
                            valueIri = "http://data.knora.org/ff17e5ef9601/values/a7e394565606"
                        ),
                        ReadValueV2(
                            valueContent = TextValueContentV2(
                                comment = None,
                                standoff = None,
                                valueHasString = "Oates 2799, 2800"
                            ),
                            valueIri = "http://data.knora.org/ff17e5ef9601/values/6a0de88f5606"
                        ),
                        ReadValueV2(
                            valueContent = TextValueContentV2(
                                comment = None,
                                standoff = None,
                                valueHasString = "Rhodes(Oxford Colleges) 340"
                            ),
                            valueIri = "http://data.knora.org/ff17e5ef9601/values/2d373bc95606"
                        ),
                        ReadValueV2(
                            valueContent = TextValueContentV2(
                                comment = None,
                                standoff = None,
                                valueHasString = "Sheppard 2435"
                            ),
                            valueIri = "http://data.knora.org/ff17e5ef9601/values/f0608e025706"
                        ),
                        ReadValueV2(
                            valueContent = TextValueContentV2(
                                comment = None,
                                standoff = None,
                                valueHasString = "Pr 7635"
                            ),
                            valueIri = "http://data.knora.org/ff17e5ef9601/values/b38ae13b5706"
                        ),
                        ReadValueV2(
                            valueContent = TextValueContentV2(
                                comment = None,
                                standoff = None,
                                valueHasString = "BMC III 753"
                            ),
                            valueIri = "http://data.knora.org/ff17e5ef9601/values/76b434755706"
                        ),
                        ReadValueV2(
                            valueContent = TextValueContentV2(
                                comment = None,
                                standoff = None,
                                valueHasString = "BSB-Ink B-398"
                            ),
                            valueIri = "http://data.knora.org/ff17e5ef9601/values/39de87ae5706"
                        ),
                        ReadValueV2(
                            valueContent = TextValueContentV2(
                                comment = None,
                                standoff = None,
                                valueHasString = "GW 4175"
                            ),
                            valueIri = "http://data.knora.org/ff17e5ef9601/values/fc07dbe75706"
                        )
                    ),
                    "http://www.knora.org/ontology/incunabula#location" -> Vector(ReadValueV2(
                        valueContent = TextValueContentV2(
                            comment = None,
                            standoff = None,
                            valueHasString = "Basel UB, Sign: Aleph E X 18:2"
                        ),
                        valueIri = "http://data.knora.org/ff17e5ef9601/values/6ba015dc5106"
                    )),
                    "http://www.knora.org/ontology/incunabula#url" -> Vector(ReadValueV2(
                        valueContent = TextValueContentV2(
                            comment = None,
                            standoff = None,
                            valueHasString = "http://aleph.unibas.ch/F/?local_base=DSV01&con_lng=GER&func=find-b&find_code=SYS&request=002645085"
                        ),
                        valueIri = "http://data.knora.org/ff17e5ef9601/values/bbbbc6e8ca2704"
                    )),
                    "http://www.knora.org/ontology/incunabula#physical_desc" -> Vector(ReadValueV2(
                        valueContent = TextValueContentV2(
                            comment = None,
                            standoff = None,
                            valueHasString = "Extent: 1 Bd.; Other physical details: Ill.; Dimensions: 8\u00B0"
                        ),
                        valueIri = "http://data.knora.org/ff17e5ef9601/values/2eca68155206"
                    )),
                    "http://www.knora.org/ontology/incunabula#book_comment" -> Vector(ReadValueV2(
                        valueContent = TextValueContentV2(
                            comment = None,
                            standoff = None,
                            valueHasString = "Katalogaufnahme anhand ISTC und v.d.Haegen"
                        ),
                        valueIri = "http://data.knora.org/ff17e5ef9601/values/bf312e215806"
                    )),
                    "http://www.knora.org/ontology/incunabula#publoc" -> Vector(ReadValueV2(
                        valueContent = TextValueContentV2(
                            comment = None,
                            standoff = None,
                            valueHasString = "Basel"
                        ),
                        valueIri = "http://data.knora.org/ff17e5ef9601/values/e54c6f695106"
                    )),
                    "http://www.knora.org/ontology/incunabula#pubdate" -> Vector(ReadValueV2(
                        valueContent = DateValueContentV2(
                            comment = None,
                            valueHasCalendar = KnoraCalendarV1.JULIAN,
                            valueHasEndPrecision = KnoraPrecisionV1.YEAR,
                            valueHasStartPrecision = KnoraPrecisionV1.YEAR,
                            valueHasEndJDN = 2265645,
                            valueHasStartJDN = 2265281,
                            valueHasString = "1490-01-01 - 1490-12-31"
                        ),
                        valueIri = "http://data.knora.org/ff17e5ef9601/values/a876c2a25106"
                    )),
                    "http://www.knora.org/ontology/incunabula#publisher" -> Vector(ReadValueV2(
                        valueContent = TextValueContentV2(
                            comment = None,
                            standoff = None,
                            valueHasString = "Johann Amerbach"
                        ),
                        valueIri = "http://data.knora.org/ff17e5ef9601/values/22231c305106"
                    )),
                    "http://www.knora.org/ontology/incunabula#note" -> Vector(
                        ReadValueV2(
                            valueContent = TextValueContentV2(
                                comment = None,
                                standoff = None,
                                valueHasString = "Datum nach V.d.Haegen: nicht nach 1489"
                            ),
                            valueIri = "http://data.knora.org/ff17e5ef9601/values/f1f3bb4e5206"
                        ),
                        ReadValueV2(
                            valueContent = TextValueContentV2(
                                comment = None,
                                standoff = None,
                                valueHasString = "A copy in the Basel University Library has a rubricator's date 1490. Woodcuts"
                            ),
                            valueIri = "http://data.knora.org/ff17e5ef9601/values/b41d0f885206"
                        ),
                        ReadValueV2(
                            valueContent = TextValueContentV2(
                                comment = None,
                                standoff = None,
                                valueHasString = "Rot und blau rubr. mit Init., Zierinit. Q im Maigl\u00F6ckchen-Stil auf Bl. 2a"
                            ),
                            valueIri = "http://data.knora.org/ff17e5ef9601/values/774762c15206"
                        ),
                        ReadValueV2(
                            valueContent = TextValueContentV2(
                                comment = None,
                                standoff = None,
                                valueHasString = "Holzschnitte koloriert"
                            ),
                            valueIri = "http://data.knora.org/ff17e5ef9601/values/3a71b5fa5206"
                        ),
                        ReadValueV2(
                            valueContent = TextValueContentV2(
                                comment = None,
                                standoff = None,
                                valueHasString = "Zusammengebunden mit 2 weiteren Drucken"
                            ),
                            valueIri = "http://data.knora.org/ff17e5ef9601/values/fd9a08345306"
                        ),
                        ReadValueV2(
                            valueContent = TextValueContentV2(
                                comment = None,
                                standoff = None,
                                valueHasString = "Blattgr\u00F6sse: ca. 155 x 110 mm"
                            ),
                            valueIri = "http://data.knora.org/ff17e5ef9601/values/70ffdaede322"
                        )
                    )
                ),
                resourceClass = "http://www.knora.org/ontology/incunabula#book",
                label = "Zeitgl\u00F6cklein des Lebens und Leidens Christi",
                resourceIri = "http://data.knora.org/ff17e5ef9601"
            )
        ),
        numberOfResources = 2
    )

    val constructQueryForBooksWithoutTitleZeitgloecklein = ConstructQuery(
        whereClause = WhereClause(patterns = Vector(
            StatementPattern(
                namedGraph = None,
                obj = IriRef(
                    iri = "http://0.0.0.0:3333/ontology/incunabula/simple/v2#book"
                ),
                pred = IriRef(
                    iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type"
                ),
                subj = QueryVariable(variableName = "book")
            ),
            StatementPattern(
                namedGraph = None,
                obj = IriRef(
                    iri = "http://api.knora.org/ontology/knora-api/simple/v2#Resource"
                ),
                pred = IriRef(
                    iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type"
                ),
                subj = QueryVariable(variableName = "book")
            ),
            StatementPattern(
                namedGraph = None,
                obj = QueryVariable(variableName = "title"),
                pred = IriRef(
                    iri = "http://0.0.0.0:3333/ontology/incunabula/simple/v2#title"
                ),
                subj = QueryVariable(variableName = "book")
            ),
            StatementPattern(
                namedGraph = None,
                obj = IriRef(
                    iri = "http://www.w3.org/2001/XMLSchema#string"
                ),
                pred = IriRef(
                    iri = "http://api.knora.org/ontology/knora-api/simple/v2#objectType"
                ),
                subj = IriRef(
                    iri = "http://0.0.0.0:3333/ontology/incunabula/simple/v2#title"
                )
            ),
            StatementPattern(
                namedGraph = None,
                obj = IriRef(
                    iri = "http://www.w3.org/2001/XMLSchema#string"
                ),
                pred = IriRef(
                    iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type"
                ),
                subj = QueryVariable(variableName = "title")
            ),
            FilterPattern(expression = CompareExpression(
                rightArg = XsdLiteral(
                    datatype = "http://www.w3.org/2001/XMLSchema#string",
                    value = "Zeitgl\u00F6cklein des Lebens und Leidens Christi"
                ),
                operator = CompareExpressionOperator.NOT_EQUALS,
                leftArg = QueryVariable(variableName = "title")
            ))
        )),
        constructClause = ConstructClause(statements = Vector(
            StatementPattern(
                namedGraph = None,
                obj = XsdLiteral(
                    datatype = "http://www.w3.org/2001/XMLSchema#boolean",
                    value = "true"
                ),
                pred = IriRef(
                    iri = "http://api.knora.org/ontology/knora-api/simple/v2#isMainResource"
                ),
                subj = QueryVariable(variableName = "book")
            ),
            StatementPattern(
                namedGraph = None,
                obj = QueryVariable(variableName = "title"),
                pred = IriRef(
                    iri = "http://0.0.0.0:3333/ontology/incunabula/simple/v2#title"
                ),
                subj = QueryVariable(variableName = "book")
            )
        )
        )
    )
}