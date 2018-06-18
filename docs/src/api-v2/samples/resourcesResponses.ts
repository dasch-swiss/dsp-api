import {ResourcesResponse} from "../ResourcesResponse";
import ApiV2WithValueObjects = ResourcesResponse.ApiV2WithValueObjects;
import ApiV2Simple = ResourcesResponse.ApiV2Simple

// http://localhost:3333/v2/resources/http%3A%2F%2Frdfh.ch%2Fc5058f3a
const Zeitgloecklein: ApiV2WithValueObjects.Resource = {
    "@id": "http://rdfh.ch/c5058f3a",
    "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book",
    "http://0.0.0.0:3333/ontology/0803/incunabula/v2#citation": [
        {
            "@id": "http://rdfh.ch/c5058f3a/values/184e99ca01",
            "@type": "http://api.knora.org/ontology/knora-api/v2#TextValue",
            "http://api.knora.org/ontology/knora-api/v2#valueAsString": "Schramm Bd. XXI, S. 27"
        },
        {
            "@id": "http://rdfh.ch/c5058f3a/values/db77ec0302",
            "@type": "http://api.knora.org/ontology/knora-api/v2#TextValue",
            "http://api.knora.org/ontology/knora-api/v2#valueAsString": "GW 4168"
        },
        {
            "@id": "http://rdfh.ch/c5058f3a/values/9ea13f3d02",
            "@type": "http://api.knora.org/ontology/knora-api/v2#TextValue",
            "http://api.knora.org/ontology/knora-api/v2#valueAsString": "ISTC ib00512000"
        }
    ],
    "http://0.0.0.0:3333/ontology/0803/incunabula/v2#location": {
        "@id": "http://rdfh.ch/c5058f3a/values/92faf25701",
        "@type": "http://api.knora.org/ontology/knora-api/v2#TextValue",
        "http://api.knora.org/ontology/knora-api/v2#valueAsString": "Universitäts- und Stadtbibliothek Köln, Sign: AD+S167"
    },
    "http://0.0.0.0:3333/ontology/0803/incunabula/v2#physical_desc": {
        "@id": "http://rdfh.ch/c5058f3a/values/5524469101",
        "@type": "http://api.knora.org/ontology/knora-api/v2#TextValue",
        "http://api.knora.org/ontology/knora-api/v2#valueAsString": "Dimension: 8°"
    },
    "http://0.0.0.0:3333/ontology/0803/incunabula/v2#pubdate": {
        "@id": "http://rdfh.ch/c5058f3a/values/cfd09f1e01",
        "@type": "http://api.knora.org/ontology/knora-api/v2#DateValue",
        "http://api.knora.org/ontology/knora-api/v2#dateValueHasCalendar": "JULIAN",
        "http://api.knora.org/ontology/knora-api/v2#dateValueHasEndEra": "CE",
        "http://api.knora.org/ontology/knora-api/v2#dateValueHasEndYear": 1492,
        "http://api.knora.org/ontology/knora-api/v2#dateValueHasStartEra": "CE",
        "http://api.knora.org/ontology/knora-api/v2#dateValueHasStartYear": 1492,
        "http://api.knora.org/ontology/knora-api/v2#valueAsString": "JULIAN:1492 CE"
    },
    "http://0.0.0.0:3333/ontology/0803/incunabula/v2#publisher": {
        "@id": "http://rdfh.ch/c5058f3a/values/497df9ab",
        "@type": "http://api.knora.org/ontology/knora-api/v2#TextValue",
        "http://api.knora.org/ontology/knora-api/v2#valueAsString": "Johann Amerbach"
    },
    "http://0.0.0.0:3333/ontology/0803/incunabula/v2#publoc": {
        "@id": "http://rdfh.ch/c5058f3a/values/0ca74ce5",
        "@type": "http://api.knora.org/ontology/knora-api/v2#TextValue",
        "http://api.knora.org/ontology/knora-api/v2#valueAsString": "Basel"
    },
    "http://0.0.0.0:3333/ontology/0803/incunabula/v2#title": {
        "@id": "http://rdfh.ch/c5058f3a/values/c3295339",
        "@type": "http://api.knora.org/ontology/knora-api/v2#TextValue",
        "http://api.knora.org/ontology/knora-api/v2#valueAsString": "Zeitglöcklein des Lebens und Leidens Christi"
    },
    "http://0.0.0.0:3333/ontology/0803/incunabula/v2#url": {
        "@id": "http://rdfh.ch/c5058f3a/values/10e00c7acc2704",
        "@type": "http://api.knora.org/ontology/knora-api/v2#TextValue",
        "http://api.knora.org/ontology/knora-api/v2#valueAsString": "http://www.ub.uni-koeln.de/cdm/compoundobject/collection/inkunabeln/id/1878/rec/1"
    },
    "http://www.w3.org/2000/01/rdf-schema#label": "Zeitglöcklein des Lebens und Leidens Christi"
};


// http://localhost:3333/v2/resources/http%3A%2F%2Frdfh.ch%2Fc5058f3a?schema=simple
const ZeitgloeckleinSimple: ApiV2Simple.Resource = {
    "@id": "http://rdfh.ch/c5058f3a",
    "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#book",
    "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#citation": [
        "Schramm Bd. XXI, S. 27",
        "GW 4168",
        "ISTC ib00512000"
    ],
    "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#location": "Universitäts- und Stadtbibliothek Köln, Sign: AD+S167",
    "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#physical_desc": "Dimension: 8°",
    "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#pubdate": {
        "@type": "http://api.knora.org/ontology/knora-api/simple/v2#Date",
        "@value": "JULIAN:1492 CE"
    },
    "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#publisher": "Johann Amerbach",
    "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#publoc": "Basel",
    "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#title": "Zeitglöcklein des Lebens und Leidens Christi",
    "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#url": "http://www.ub.uni-koeln.de/cdm/compoundobject/collection/inkunabeln/id/1878/rec/1",
    "http://www.w3.org/2000/01/rdf-schema#label": "Zeitglöcklein des Lebens und Leidens Christi"
};

// http://localhost:3333/v2/search/Narr
let fulltextSearchForNarr: ApiV2WithValueObjects.ResourcesSequence = {
    "@graph": [
        {
            "@id": "http://rdfh.ch/00505cf0a803",
            "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#page",
            "http://0.0.0.0:3333/ontology/0803/incunabula/v2#description": {
                "@id": "http://rdfh.ch/00505cf0a803/values/549527258a26",
                "@type": "http://api.knora.org/ontology/knora-api/v2#TextValue",
                "http://api.knora.org/ontology/knora-api/v2#valueAsString": "Beginn Kapitel 105.\nHolzschnitt identisch mit Kap. 95: In einer Landschaft fasst ein Narr, der ein Zepter in der Linken hält, einem Mann an die Schulter und redet auf ihn ein, er möge die Feiertage missachten, 11.7 x 8.6 cm."
            },
            "http://www.w3.org/2000/01/rdf-schema#label": "p7v"
        },
        {
            "@id": "http://rdfh.ch/00c650d23303",
            "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#page",
            "http://0.0.0.0:3333/ontology/0803/incunabula/v2#description": {
                "@id": "http://rdfh.ch/00c650d23303/values/af68552c3626",
                "@type": "http://api.knora.org/ontology/knora-api/v2#TextValue",
                "http://api.knora.org/ontology/knora-api/v2#valueAsString": "Beginn Kapitel 21.\nHolzschnitt zu Kap. 21: Andere tadeln und selbst unrecht handeln.\nEin Narr, der mit seinen Beinen im Sumpf steckt, zeigt auf einen nahen Weg, an dem ein Bildstock die Richtung weist.\n11.7 x 8.5 cm.\nUnkoloriert.\n"
            },
            "http://www.w3.org/2000/01/rdf-schema#label": "d4v"
        },
        {
            "@id": "http://rdfh.ch/02abe871e903",
            "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#page",
            "http://0.0.0.0:3333/ontology/0803/incunabula/v2#description": {
                "@id": "http://rdfh.ch/02abe871e903/values/1852a8aa8526",
                "@type": "http://api.knora.org/ontology/knora-api/v2#TextValue",
                "http://api.knora.org/ontology/knora-api/v2#valueAsString": "Beginn Kapitel 99.\nHolzschnitt zu Kap. 99: Von der Einbusse des christlichen Reiches\nAuf einem Hof kniet ein Narr vor den Vertretern der kirchlichen und weltlichen Obrigkeit, die vor ein Portal getreten sind, und bittet darum, sie mögen die Narrenkappe verschmähen. Im Hintergrund kommentieren zwei weitere Narren über die Hofmauer hinweg das Geschehen mit ungläubigen Gesten, 11.7 x 8.5 cm."
            },
            "http://www.w3.org/2000/01/rdf-schema#label": "o5v"
        },
        {
            "@id": "http://rdfh.ch/04416f64ef03",
            "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#page",
            "http://0.0.0.0:3333/ontology/0803/incunabula/v2#description": {
                "@id": "http://rdfh.ch/04416f64ef03/values/6ce3c0ef8b26",
                "@type": "http://api.knora.org/ontology/knora-api/v2#TextValue",
                "http://api.knora.org/ontology/knora-api/v2#valueAsString": "Beginn Kapitel 109.\nHolzschnitt zu Kap. 109: Von Verachtung des Unglücks\nEin Narr hat sich in einem Boot zu weit vom Ufer entfernt. Nun birst der Schiffsrumpf, das Segel flattert haltlos umher. Der Narr hält sich an einem Seil der Takelage fest, 11.6 x 8.4 cm."
            },
            "http://www.w3.org/2000/01/rdf-schema#label": "q2v"
        },
        {
            "@id": "http://rdfh.ch/04f25db73f03",
            "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#page",
            "http://0.0.0.0:3333/ontology/0803/incunabula/v2#description": {
                "@id": "http://rdfh.ch/04f25db73f03/values/aa8971af4d26",
                "@type": "http://api.knora.org/ontology/knora-api/v2#TextValue",
                "http://api.knora.org/ontology/knora-api/v2#valueAsString": "Beginn Kapitel 44.\nHolzschnitt zu Kap. 44: Vom Lärmen in der Kirche\nEin junger Narr in edler Kleidung, der einen Jagdfalken auf dem Arm hält, von Hunden begleitet wird, und klappernde Schuhsohlen trägt, geht auf ein Portal zu, in dem eine Frau steht und ihm schöne Augen macht.\n11.7 x 8.5 cm.\nUnkoloriert."
            },
            "http://www.w3.org/2000/01/rdf-schema#label": "g6v"
        },
        {
            "@id": "http://rdfh.ch/05c7acceb703",
            "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#page",
            "http://0.0.0.0:3333/ontology/0803/incunabula/v2#description": {
                "@id": "http://rdfh.ch/05c7acceb703/values/5f23f3171d26",
                "@type": "http://api.knora.org/ontology/knora-api/v2#TextValue",
                "http://api.knora.org/ontology/knora-api/v2#valueAsString": "Titelblatt (Hartl 2001: Stultitia Navis I).\nHolzschnitt: \nErsatzholzschnitt für Titelblatt, recto:\nEin Schiff voller Narren fährt nach links. Hinten auf der Brücke trinkt ein Narr aus einer Flasche, vorne prügeln sich zwei weitere narren so sehr, dass einer von ihnen über Bord zu gehen droht. Oben die Inschrift \"Nauis stultoru(m).\"; auf dem Schiffsrumpf die Datierung \"1.4.9.7.\".\n6.5 x 11.5 cm.\noben rechts die bibliographische Angabe  (Graphitstift) \"Hain 3750\"; unten rechts Bibliotheksstempel (queroval, schwarz): \"BIBL. PUBL.| BASILEENSIS\"."
            },
            "http://www.w3.org/2000/01/rdf-schema#label": "a1r; Titelblatt, recto"
        },
        {
            "@id": "http://rdfh.ch/075d33c1bd03",
            "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#page",
            "http://0.0.0.0:3333/ontology/0803/incunabula/v2#description": {
                "@id": "http://rdfh.ch/075d33c1bd03/values/77718ce21e26",
                "@type": "http://api.knora.org/ontology/knora-api/v2#TextValue",
                "http://api.knora.org/ontology/knora-api/v2#valueAsString": "Beginn Kapitel 4.\nHolzschnitt zu Kap. 4: Von neumodischen Sitten.\nEin alter Narr mit Becher hält einem jungen Mann in modischer Tracht einen Spiegel vor. Zwischen Narr und Jüngling steht der Name „.VLI.“; über den beiden schwebt eine Banderole mit der Aufschrift „vly . von . stouffen .  . frisch . vnd vngschaffen“; zwischen den Füssen des Jünglings ist die Jahreszahl „.1.4.9.4.“ zu lesen.\n11.6 x 8.5 cm."
            },
            "http://www.w3.org/2000/01/rdf-schema#label": "b6r"
        },
        {
            "@id": "http://rdfh.ch/0b8940a6c903",
            "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#page",
            "http://0.0.0.0:3333/ontology/0803/incunabula/v2#description": {
                "@id": "http://rdfh.ch/0b8940a6c903/values/f752218c3b26",
                "@type": "http://api.knora.org/ontology/knora-api/v2#TextValue",
                "http://api.knora.org/ontology/knora-api/v2#valueAsString": "Beginn Kapitel 29.\nHolzschnitt zu Kap. 29: Von Verkennung der Mitmenschen.\nEin Narr verspottet einen Sterbenden, neben dessen Bett eine Frau betet, während sich unter dem Narren die Hölle in Gestalt eines gefrässigen Drachenkopfs auftut, 11.7 x 8.5 cm.\n"
            },
            "http://www.w3.org/2000/01/rdf-schema#label": "e8r"
        },
        {
            "@id": "http://rdfh.ch/0d1fc798cf03",
            "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#page",
            "http://0.0.0.0:3333/ontology/0803/incunabula/v2#description": {
                "@id": "http://rdfh.ch/0d1fc798cf03/values/e75f1e764d26",
                "@type": "http://api.knora.org/ontology/knora-api/v2#TextValue",
                "http://api.knora.org/ontology/knora-api/v2#valueAsString": "Beginn Kapitel 43.\nHolzschnitt zu Kap. 43: Missachten der ewigen Seligkeit\nEin Narr steht mit einer grossen Waage in einer Landschaft und wiegt das Himmelsfirmament (links) gegen eine Burg (rechts) auf. Die Zunge der Waage schlägt zugunsten der Burg aus, 11.5 x 8.4 cm.\n"
            },
            "http://www.w3.org/2000/01/rdf-schema#label": "g5r"
        },
        {
            "@id": "http://rdfh.ch/0d5ac1099503",
            "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#page",
            "http://0.0.0.0:3333/ontology/0803/incunabula/v2#description": {
                "@id": "http://rdfh.ch/0d5ac1099503/values/4dcdbebc7126",
                "@type": "http://api.knora.org/ontology/knora-api/v2#TextValue",
                "http://api.knora.org/ontology/knora-api/v2#valueAsString": "Beginn Kapitel 66.\nHolzschnitt zu Kap. 66: Von der Erforschung der Welt.\nEin Narr hat ein Schema des Universums auf den Boden Gezeichnet und vermisst es mit einem Zirkel. Von hinten blickt ein zweiter Narr über eine Mauer und wendet sich dem ersten mit spöttischen Gesten zu.\n11.6 x 8.4 cm."
            },
            "http://www.w3.org/2000/01/rdf-schema#label": "k4r"
        },
        {
            "@id": "http://rdfh.ch/0fb54d8bd503",
            "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#page",
            "http://0.0.0.0:3333/ontology/0803/incunabula/v2#description": {
                "@id": "http://rdfh.ch/0fb54d8bd503/values/9a966e995f26",
                "@type": "http://api.knora.org/ontology/knora-api/v2#TextValue",
                "http://api.knora.org/ontology/knora-api/v2#valueAsString": "Beginn Kapitel 58.\nHolzschnitt zu Kap. 58: Sich um die Angelegenheiten anderer kümmern.\nEin Narr versucht mit einem Wassereimer den Brand im Haus des Nachbarn zu löschen und wird dabei von einem anderen Narren, der an seinem Mantel zerrt, unterbrochen, den hinter ihm steht auch sein eigenes Haus in Flammen.\n11.6 x 8.5 cm."
            },
            "http://www.w3.org/2000/01/rdf-schema#label": "i2r"
        },
        {
            "@id": "http://rdfh.ch/0ff047fc9a03",
            "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#page",
            "http://0.0.0.0:3333/ontology/0803/incunabula/v2#description": {
                "@id": "http://rdfh.ch/0ff047fc9a03/values/b9ac70cc7926",
                "@type": "http://api.knora.org/ontology/knora-api/v2#TextValue",
                "http://api.knora.org/ontology/knora-api/v2#valueAsString": "Beginn Kapitel 81.\nHolzschnitt zu Kap. 81: Aus Küche und Keller.\nEin Narr führt von einem Boot aus vier Knechte am Strick, die sich in einer Küche über Spreis und Trank hermachen, während eine Frau, die am Herdfeuer sitzt, das Essen zubereitet, 11.7 x 8.5 cm."
            },
            "http://www.w3.org/2000/01/rdf-schema#label": "m1r"
        },
        {
            "@id": "http://rdfh.ch/114bd47ddb03",
            "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#page",
            "http://0.0.0.0:3333/ontology/0803/incunabula/v2#description": {
                "@id": "http://rdfh.ch/114bd47ddb03/values/c99f73e26726",
                "@type": "http://api.knora.org/ontology/knora-api/v2#TextValue",
                "http://api.knora.org/ontology/knora-api/v2#valueAsString": "Beginn Kapitel 69.\nHolzschnitt Lemmer 1979, S. 117: Variante zu Kap. 69.\nEin Narr, der vor einer Stadtkulisse steht, hat mit seiner Rechten einen Ball in die Luft geworfen und schlägt mit seiner Linken einen Mann, der sogleich nach seinem Dolch greift. Ein junger Mann beobachtet das Geschehen.\nDer Bildinhalt stimmt weitgehend mit dem ursprünglichen Holzschnitt überein.\n11.7 x 8.4 cm."
            },
            "http://www.w3.org/2000/01/rdf-schema#label": "k7r"
        },
        {
            "@id": "http://rdfh.ch/14dd8cbc3403",
            "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#page",
            "http://0.0.0.0:3333/ontology/0803/incunabula/v2#description": {
                "@id": "http://rdfh.ch/14dd8cbc3403/values/7e39f54a3726",
                "@type": "http://api.knora.org/ontology/knora-api/v2#TextValue",
                "http://api.knora.org/ontology/knora-api/v2#valueAsString": "Beginn Kapitel 23.\nHolzschnitt zu Kap. 23: Vom blinden Vertrauen auf das Glück.\nEin Narr schaut oben aus dem Fenster seines Hauses, das unten lichterloh brennt. Am Himmel erscheint die rächende Gotteshand, die mit einen Hammer auf Haus und Narr einschlägt. Auf der Fahne über dem Erker des Hauses ist der Baselstab zu erkennen.\n11.5 x 8.2 cm.\nUnkoloriert.\n"
            },
            "http://www.w3.org/2000/01/rdf-schema#label": "d6v"
        },
        {
            "@id": "http://rdfh.ch/167313af3a03",
            "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#page",
            "http://0.0.0.0:3333/ontology/0803/incunabula/v2#description": {
                "@id": "http://rdfh.ch/167313af3a03/values/1ab5d9ef4226",
                "@type": "http://api.knora.org/ontology/knora-api/v2#TextValue",
                "http://api.knora.org/ontology/knora-api/v2#valueAsString": "Beginn Kapitel 34.\nHolzschnitt zu Kap. 34: Ein Narr sein und es bleiben.\nEin Narr wird von drei Gänsen umgeben, deren eine von ihm wegfliegt.\n11.7 x 8.4 cm.\nUnkoloriert."
            },
            "http://www.w3.org/2000/01/rdf-schema#label": "f3v"
        },
        {
            "@id": "http://rdfh.ch/1b746fabbe03",
            "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#page",
            "http://0.0.0.0:3333/ontology/0803/incunabula/v2#description": {
                "@id": "http://rdfh.ch/1b746fabbe03/values/8318d9c71f26",
                "@type": "http://api.knora.org/ontology/knora-api/v2#TextValue",
                "http://api.knora.org/ontology/knora-api/v2#valueAsString": "Beginn Kapitel 6.\nHolzschnitt zu Kap. 6: Von mangelhafter Erziehung der Kinder.\nZwei Jungen geraten am Spieltisch über Karten und Würfen in Streit. Während der eine einen Dolch zückt und der andere nach seinem Schwert greift, sitzt ein älterer Narr mit verbundenen Augen ahnungslos neben dem Geschehen.\n11.7 x 8.5 cm."
            },
            "http://www.w3.org/2000/01/rdf-schema#label": "b8r"
        },
        {
            "@id": "http://rdfh.ch/1baf691c8403",
            "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#page",
            "http://0.0.0.0:3333/ontology/0803/incunabula/v2#description": {
                "@id": "http://rdfh.ch/1baf691c8403/values/2882816d3a26",
                "@type": "http://api.knora.org/ontology/knora-api/v2#TextValue",
                "http://api.knora.org/ontology/knora-api/v2#valueAsString": "Beginn Kapitel 28.\nHolzschnitt zu Kap. 28: Vom Nörgeln an Gottes Werken.\nEin Narr, der auf einem Berg ein Feuer entfacht hat, hält seine Hand schützend über die Augen, während er seinen Blick auf die hell am Himmel strahlende Sonne richtet. 11.7 x 8.5 cm."
            },
            "http://www.w3.org/2000/01/rdf-schema#label": "e7r"
        },
        {
            "@id": "http://rdfh.ch/1d0af69dc403",
            "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#page",
            "http://0.0.0.0:3333/ontology/0803/incunabula/v2#description": {
                "@id": "http://rdfh.ch/1d0af69dc403/values/4e9dc2b53326",
                "@type": "http://api.knora.org/ontology/knora-api/v2#TextValue",
                "http://api.knora.org/ontology/knora-api/v2#valueAsString": "Beginn Kapitel 18.\nHolzschnitt zu Kap. 18: Vom Dienst an zwei Herren.\nEin mit Spiess bewaffneter Narr bläst in ein Horn. Sein Hund versucht derweil im Hintergrund zwei Hasen gleichzeitig zu erjagen, 11.6 x 8.4 cm.\n"
            },
            "http://www.w3.org/2000/01/rdf-schema#label": "d5r"
        },
        {
            "@id": "http://rdfh.ch/1fa07c90ca03",
            "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#page",
            "http://0.0.0.0:3333/ontology/0803/incunabula/v2#description": {
                "@id": "http://rdfh.ch/1fa07c90ca03/values/c623c1aa3c26",
                "@type": "http://api.knora.org/ontology/knora-api/v2#TextValue",
                "http://api.knora.org/ontology/knora-api/v2#valueAsString": "Beginn Kapitel 31.\nHolzschnitt zu Kap. 31: Vom Hinausschieben auf morgen.\nEin Narr steht mit ausgebreiteten Armen auf einer Strasse. Auf seinen Händen sitzen zwei Raben, die beide „Cras“ – das lateinische Wort für „morgen“ – rufen. Auf dem Kopf des Narren sitzt ein Papagei und ahmt den Ruf der Krähen nach, 11.6 x 8.5 cm."
            },
            "http://www.w3.org/2000/01/rdf-schema#label": "f2r"
        },
        {
            "@id": "http://rdfh.ch/1fdb76019003",
            "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#page",
            "http://0.0.0.0:3333/ontology/0803/incunabula/v2#description": {
                "@id": "http://rdfh.ch/1fdb76019003/values/118a3f426d26",
                "@type": "http://api.knora.org/ontology/knora-api/v2#TextValue",
                "http://api.knora.org/ontology/knora-api/v2#valueAsString": "Beginn Kapitel 57.\nHolzschnitt zu Kap. 57: Von der Gnadenwahl Gottes.\nEin Narr, der auf einem Krebs reitet, stützt sich auf ein brechendes Schildrohr, das ihm die Hand  durchbohrt. Ein Vogel fliegt auf den offenen Mund des Narren zu.\n11.6 x 8.5 cm."
            },
            "http://www.w3.org/2000/01/rdf-schema#label": "i1r"
        },
        {
            "@id": "http://rdfh.ch/21360383d003",
            "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#page",
            "http://0.0.0.0:3333/ontology/0803/incunabula/v2#description": {
                "@id": "http://rdfh.ch/21360383d003/values/b630be944e26",
                "@type": "http://api.knora.org/ontology/knora-api/v2#TextValue",
                "http://api.knora.org/ontology/knora-api/v2#valueAsString": "Beginn Kapitel 45.\nHolzschnitt zu Kap. 45: Von selbstverschuldetem Unglück.\nIn Gestalt eines Narren springt Empedokles in den lodernden Krater des Ätna. Im Vordergrund lässt sich ein anderer Narr in einen Brunnen fallen. Beide werden von drei Männern beobachtet, die das Verhalten mit „Jn geschicht recht“  kommentieren, 11.7 x 8.3 cm.\n"
            },
            "http://www.w3.org/2000/01/rdf-schema#label": "g7r"
        },
        {
            "@id": "http://rdfh.ch/2171fdf39503",
            "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#page",
            "http://0.0.0.0:3333/ontology/0803/incunabula/v2#description": {
                "@id": "http://rdfh.ch/2171fdf39503/values/59740ba27226",
                "@type": "http://api.knora.org/ontology/knora-api/v2#TextValue",
                "http://api.knora.org/ontology/knora-api/v2#valueAsString": "Beginn Kapitel 68.\nHolzschnitt zu Kap. 68: Keinen Scherz verstehen.\nEin Kind, das auf einem Steckenpferd reitet und mit einem Stock als Gerte umher fuchtelt, wird von einem Narren am rechten Rand ausgeschimpft. Ein anderer Narr, der neben dem Kind steht, ist dabei, sein Schwert aus der Scheide zu ziehen.\n11.7 x 8.5 cm."
            },
            "http://www.w3.org/2000/01/rdf-schema#label": "k6r"
        },
        {
            "@id": "http://rdfh.ch/230784e69b03",
            "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#page",
            "http://0.0.0.0:3333/ontology/0803/incunabula/v2#description": {
                "@id": "http://rdfh.ch/230784e69b03/values/4ba763247b26",
                "@type": "http://api.knora.org/ontology/knora-api/v2#TextValue",
                "http://api.knora.org/ontology/knora-api/v2#valueAsString": "Beginn Kapitel 83.\nneuer Holzschitt (nicht in Lemmer 1979): Vor einer Häuserkulisse kniet ein Narr mit einem Beutel in der Linken und zwei Keulen in der Rechten vor einem Mann mit Hut und einem jüngeren Begleiter, 11.6 x 8.6 cm."
            },
            "http://www.w3.org/2000/01/rdf-schema#label": "m3r"
        },
        {
            "@id": "http://rdfh.ch/23427e576103",
            "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#page",
            "http://0.0.0.0:3333/ontology/0803/incunabula/v2#description": {
                "@id": "http://rdfh.ch/23427e576103/values/c32d62198426",
                "@type": "http://api.knora.org/ontology/knora-api/v2#TextValue",
                "http://api.knora.org/ontology/knora-api/v2#valueAsString": "Beginn Kapitel 96.\nHolzschnitt zu Kap. 96: Schenken und hinterdrein bereuen.\nEin Narr, der vor einem Haus steht, überreicht einem bärtigen Alten ein Geschenk, kratzt sich dabei aber unschlüssig am Kopf.\n11.6 x 8.3 cm.\nUnkoloriert.\nOben rechts Blattnummerierung (Graphitstift): \"128\"."
            },
            "http://www.w3.org/2000/01/rdf-schema#label": "q8r"
        },
        {
            "@id": "http://rdfh.ch/23cc8975d603",
            "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#page",
            "http://0.0.0.0:3333/ontology/0803/incunabula/v2#description": {
                "@id": "http://rdfh.ch/23cc8975d603/values/a63dbb7e6026",
                "@type": "http://api.knora.org/ontology/knora-api/v2#TextValue",
                "http://api.knora.org/ontology/knora-api/v2#valueAsString": "Beginn Kapitel 60.\nHolzschnitt zu Kap. 60: Von Selbstgefälligkeit.\nEin alter Narr steht am Ofen und rührt in einem Topf. Gleichzeitig schaut er sich dabei in einem Handspiegel an.\n11.7 x 8.5 cm."
            },
            "http://www.w3.org/2000/01/rdf-schema#label": "i4r"
        }
    ]
};

// http://localhost:3333/v2/search/Narr?schema=simple
const fulltextSearchForNarrSimple: ApiV2Simple.ResourcesSequence = {
    "@graph": [
        {
            "@id": "http://rdfh.ch/00505cf0a803",
            "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#page",
            "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#description": "Beginn Kapitel 105.\nHolzschnitt identisch mit Kap. 95: In einer Landschaft fasst ein Narr, der ein Zepter in der Linken hält, einem Mann an die Schulter und redet auf ihn ein, er möge die Feiertage missachten, 11.7 x 8.6 cm.",
            "http://www.w3.org/2000/01/rdf-schema#label": "p7v"
        },
        {
            "@id": "http://rdfh.ch/00c650d23303",
            "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#page",
            "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#description": "Beginn Kapitel 21.\nHolzschnitt zu Kap. 21: Andere tadeln und selbst unrecht handeln.\nEin Narr, der mit seinen Beinen im Sumpf steckt, zeigt auf einen nahen Weg, an dem ein Bildstock die Richtung weist.\n11.7 x 8.5 cm.\nUnkoloriert.\n",
            "http://www.w3.org/2000/01/rdf-schema#label": "d4v"
        },
        {
            "@id": "http://rdfh.ch/02abe871e903",
            "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#page",
            "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#description": "Beginn Kapitel 99.\nHolzschnitt zu Kap. 99: Von der Einbusse des christlichen Reiches\nAuf einem Hof kniet ein Narr vor den Vertretern der kirchlichen und weltlichen Obrigkeit, die vor ein Portal getreten sind, und bittet darum, sie mögen die Narrenkappe verschmähen. Im Hintergrund kommentieren zwei weitere Narren über die Hofmauer hinweg das Geschehen mit ungläubigen Gesten, 11.7 x 8.5 cm.",
            "http://www.w3.org/2000/01/rdf-schema#label": "o5v"
        },
        {
            "@id": "http://rdfh.ch/04416f64ef03",
            "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#page",
            "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#description": "Beginn Kapitel 109.\nHolzschnitt zu Kap. 109: Von Verachtung des Unglücks\nEin Narr hat sich in einem Boot zu weit vom Ufer entfernt. Nun birst der Schiffsrumpf, das Segel flattert haltlos umher. Der Narr hält sich an einem Seil der Takelage fest, 11.6 x 8.4 cm.",
            "http://www.w3.org/2000/01/rdf-schema#label": "q2v"
        },
        {
            "@id": "http://rdfh.ch/04f25db73f03",
            "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#page",
            "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#description": "Beginn Kapitel 44.\nHolzschnitt zu Kap. 44: Vom Lärmen in der Kirche\nEin junger Narr in edler Kleidung, der einen Jagdfalken auf dem Arm hält, von Hunden begleitet wird, und klappernde Schuhsohlen trägt, geht auf ein Portal zu, in dem eine Frau steht und ihm schöne Augen macht.\n11.7 x 8.5 cm.\nUnkoloriert.",
            "http://www.w3.org/2000/01/rdf-schema#label": "g6v"
        },
        {
            "@id": "http://rdfh.ch/05c7acceb703",
            "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#page",
            "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#description": "Titelblatt (Hartl 2001: Stultitia Navis I).\nHolzschnitt: \nErsatzholzschnitt für Titelblatt, recto:\nEin Schiff voller Narren fährt nach links. Hinten auf der Brücke trinkt ein Narr aus einer Flasche, vorne prügeln sich zwei weitere narren so sehr, dass einer von ihnen über Bord zu gehen droht. Oben die Inschrift \"Nauis stultoru(m).\"; auf dem Schiffsrumpf die Datierung \"1.4.9.7.\".\n6.5 x 11.5 cm.\noben rechts die bibliographische Angabe  (Graphitstift) \"Hain 3750\"; unten rechts Bibliotheksstempel (queroval, schwarz): \"BIBL. PUBL.| BASILEENSIS\".",
            "http://www.w3.org/2000/01/rdf-schema#label": "a1r; Titelblatt, recto"
        },
        {
            "@id": "http://rdfh.ch/075d33c1bd03",
            "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#page",
            "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#description": "Beginn Kapitel 4.\nHolzschnitt zu Kap. 4: Von neumodischen Sitten.\nEin alter Narr mit Becher hält einem jungen Mann in modischer Tracht einen Spiegel vor. Zwischen Narr und Jüngling steht der Name „.VLI.“; über den beiden schwebt eine Banderole mit der Aufschrift „vly . von . stouffen .  . frisch . vnd vngschaffen“; zwischen den Füssen des Jünglings ist die Jahreszahl „.1.4.9.4.“ zu lesen.\n11.6 x 8.5 cm.",
            "http://www.w3.org/2000/01/rdf-schema#label": "b6r"
        },
        {
            "@id": "http://rdfh.ch/0b8940a6c903",
            "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#page",
            "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#description": "Beginn Kapitel 29.\nHolzschnitt zu Kap. 29: Von Verkennung der Mitmenschen.\nEin Narr verspottet einen Sterbenden, neben dessen Bett eine Frau betet, während sich unter dem Narren die Hölle in Gestalt eines gefrässigen Drachenkopfs auftut, 11.7 x 8.5 cm.\n",
            "http://www.w3.org/2000/01/rdf-schema#label": "e8r"
        },
        {
            "@id": "http://rdfh.ch/0d1fc798cf03",
            "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#page",
            "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#description": "Beginn Kapitel 43.\nHolzschnitt zu Kap. 43: Missachten der ewigen Seligkeit\nEin Narr steht mit einer grossen Waage in einer Landschaft und wiegt das Himmelsfirmament (links) gegen eine Burg (rechts) auf. Die Zunge der Waage schlägt zugunsten der Burg aus, 11.5 x 8.4 cm.\n",
            "http://www.w3.org/2000/01/rdf-schema#label": "g5r"
        },
        {
            "@id": "http://rdfh.ch/0d5ac1099503",
            "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#page",
            "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#description": "Beginn Kapitel 66.\nHolzschnitt zu Kap. 66: Von der Erforschung der Welt.\nEin Narr hat ein Schema des Universums auf den Boden Gezeichnet und vermisst es mit einem Zirkel. Von hinten blickt ein zweiter Narr über eine Mauer und wendet sich dem ersten mit spöttischen Gesten zu.\n11.6 x 8.4 cm.",
            "http://www.w3.org/2000/01/rdf-schema#label": "k4r"
        },
        {
            "@id": "http://rdfh.ch/0fb54d8bd503",
            "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#page",
            "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#description": "Beginn Kapitel 58.\nHolzschnitt zu Kap. 58: Sich um die Angelegenheiten anderer kümmern.\nEin Narr versucht mit einem Wassereimer den Brand im Haus des Nachbarn zu löschen und wird dabei von einem anderen Narren, der an seinem Mantel zerrt, unterbrochen, den hinter ihm steht auch sein eigenes Haus in Flammen.\n11.6 x 8.5 cm.",
            "http://www.w3.org/2000/01/rdf-schema#label": "i2r"
        },
        {
            "@id": "http://rdfh.ch/0ff047fc9a03",
            "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#page",
            "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#description": "Beginn Kapitel 81.\nHolzschnitt zu Kap. 81: Aus Küche und Keller.\nEin Narr führt von einem Boot aus vier Knechte am Strick, die sich in einer Küche über Spreis und Trank hermachen, während eine Frau, die am Herdfeuer sitzt, das Essen zubereitet, 11.7 x 8.5 cm.",
            "http://www.w3.org/2000/01/rdf-schema#label": "m1r"
        },
        {
            "@id": "http://rdfh.ch/114bd47ddb03",
            "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#page",
            "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#description": "Beginn Kapitel 69.\nHolzschnitt Lemmer 1979, S. 117: Variante zu Kap. 69.\nEin Narr, der vor einer Stadtkulisse steht, hat mit seiner Rechten einen Ball in die Luft geworfen und schlägt mit seiner Linken einen Mann, der sogleich nach seinem Dolch greift. Ein junger Mann beobachtet das Geschehen.\nDer Bildinhalt stimmt weitgehend mit dem ursprünglichen Holzschnitt überein.\n11.7 x 8.4 cm.",
            "http://www.w3.org/2000/01/rdf-schema#label": "k7r"
        },
        {
            "@id": "http://rdfh.ch/14dd8cbc3403",
            "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#page",
            "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#description": "Beginn Kapitel 23.\nHolzschnitt zu Kap. 23: Vom blinden Vertrauen auf das Glück.\nEin Narr schaut oben aus dem Fenster seines Hauses, das unten lichterloh brennt. Am Himmel erscheint die rächende Gotteshand, die mit einen Hammer auf Haus und Narr einschlägt. Auf der Fahne über dem Erker des Hauses ist der Baselstab zu erkennen.\n11.5 x 8.2 cm.\nUnkoloriert.\n",
            "http://www.w3.org/2000/01/rdf-schema#label": "d6v"
        },
        {
            "@id": "http://rdfh.ch/167313af3a03",
            "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#page",
            "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#description": "Beginn Kapitel 34.\nHolzschnitt zu Kap. 34: Ein Narr sein und es bleiben.\nEin Narr wird von drei Gänsen umgeben, deren eine von ihm wegfliegt.\n11.7 x 8.4 cm.\nUnkoloriert.",
            "http://www.w3.org/2000/01/rdf-schema#label": "f3v"
        },
        {
            "@id": "http://rdfh.ch/1b746fabbe03",
            "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#page",
            "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#description": "Beginn Kapitel 6.\nHolzschnitt zu Kap. 6: Von mangelhafter Erziehung der Kinder.\nZwei Jungen geraten am Spieltisch über Karten und Würfen in Streit. Während der eine einen Dolch zückt und der andere nach seinem Schwert greift, sitzt ein älterer Narr mit verbundenen Augen ahnungslos neben dem Geschehen.\n11.7 x 8.5 cm.",
            "http://www.w3.org/2000/01/rdf-schema#label": "b8r"
        },
        {
            "@id": "http://rdfh.ch/1baf691c8403",
            "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#page",
            "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#description": "Beginn Kapitel 28.\nHolzschnitt zu Kap. 28: Vom Nörgeln an Gottes Werken.\nEin Narr, der auf einem Berg ein Feuer entfacht hat, hält seine Hand schützend über die Augen, während er seinen Blick auf die hell am Himmel strahlende Sonne richtet. 11.7 x 8.5 cm.",
            "http://www.w3.org/2000/01/rdf-schema#label": "e7r"
        },
        {
            "@id": "http://rdfh.ch/1d0af69dc403",
            "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#page",
            "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#description": "Beginn Kapitel 18.\nHolzschnitt zu Kap. 18: Vom Dienst an zwei Herren.\nEin mit Spiess bewaffneter Narr bläst in ein Horn. Sein Hund versucht derweil im Hintergrund zwei Hasen gleichzeitig zu erjagen, 11.6 x 8.4 cm.\n",
            "http://www.w3.org/2000/01/rdf-schema#label": "d5r"
        },
        {
            "@id": "http://rdfh.ch/1fa07c90ca03",
            "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#page",
            "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#description": "Beginn Kapitel 31.\nHolzschnitt zu Kap. 31: Vom Hinausschieben auf morgen.\nEin Narr steht mit ausgebreiteten Armen auf einer Strasse. Auf seinen Händen sitzen zwei Raben, die beide „Cras“ – das lateinische Wort für „morgen“ – rufen. Auf dem Kopf des Narren sitzt ein Papagei und ahmt den Ruf der Krähen nach, 11.6 x 8.5 cm.",
            "http://www.w3.org/2000/01/rdf-schema#label": "f2r"
        },
        {
            "@id": "http://rdfh.ch/1fdb76019003",
            "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#page",
            "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#description": "Beginn Kapitel 57.\nHolzschnitt zu Kap. 57: Von der Gnadenwahl Gottes.\nEin Narr, der auf einem Krebs reitet, stützt sich auf ein brechendes Schildrohr, das ihm die Hand  durchbohrt. Ein Vogel fliegt auf den offenen Mund des Narren zu.\n11.6 x 8.5 cm.",
            "http://www.w3.org/2000/01/rdf-schema#label": "i1r"
        },
        {
            "@id": "http://rdfh.ch/21360383d003",
            "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#page",
            "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#description": "Beginn Kapitel 45.\nHolzschnitt zu Kap. 45: Von selbstverschuldetem Unglück.\nIn Gestalt eines Narren springt Empedokles in den lodernden Krater des Ätna. Im Vordergrund lässt sich ein anderer Narr in einen Brunnen fallen. Beide werden von drei Männern beobachtet, die das Verhalten mit „Jn geschicht recht“  kommentieren, 11.7 x 8.3 cm.\n",
            "http://www.w3.org/2000/01/rdf-schema#label": "g7r"
        },
        {
            "@id": "http://rdfh.ch/2171fdf39503",
            "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#page",
            "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#description": "Beginn Kapitel 68.\nHolzschnitt zu Kap. 68: Keinen Scherz verstehen.\nEin Kind, das auf einem Steckenpferd reitet und mit einem Stock als Gerte umher fuchtelt, wird von einem Narren am rechten Rand ausgeschimpft. Ein anderer Narr, der neben dem Kind steht, ist dabei, sein Schwert aus der Scheide zu ziehen.\n11.7 x 8.5 cm.",
            "http://www.w3.org/2000/01/rdf-schema#label": "k6r"
        },
        {
            "@id": "http://rdfh.ch/230784e69b03",
            "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#page",
            "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#description": "Beginn Kapitel 83.\nneuer Holzschitt (nicht in Lemmer 1979): Vor einer Häuserkulisse kniet ein Narr mit einem Beutel in der Linken und zwei Keulen in der Rechten vor einem Mann mit Hut und einem jüngeren Begleiter, 11.6 x 8.6 cm.",
            "http://www.w3.org/2000/01/rdf-schema#label": "m3r"
        },
        {
            "@id": "http://rdfh.ch/23427e576103",
            "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#page",
            "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#description": "Beginn Kapitel 96.\nHolzschnitt zu Kap. 96: Schenken und hinterdrein bereuen.\nEin Narr, der vor einem Haus steht, überreicht einem bärtigen Alten ein Geschenk, kratzt sich dabei aber unschlüssig am Kopf.\n11.6 x 8.3 cm.\nUnkoloriert.\nOben rechts Blattnummerierung (Graphitstift): \"128\".",
            "http://www.w3.org/2000/01/rdf-schema#label": "q8r"
        },
        {
            "@id": "http://rdfh.ch/23cc8975d603",
            "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#page",
            "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#description": "Beginn Kapitel 60.\nHolzschnitt zu Kap. 60: Von Selbstgefälligkeit.\nEin alter Narr steht am Ofen und rührt in einem Topf. Gleichzeitig schaut er sich dabei in einem Handspiegel an.\n11.7 x 8.5 cm.",
            "http://www.w3.org/2000/01/rdf-schema#label": "i4r"
        }
    ]
};

// http://localhost:3333/v2/searchextended/%20%20%20PREFIX%20knora-api%3A%20%3Chttp%3A%2F%2Fapi.knora.org%2Fontology%2Fknora-api%2Fsimple%2Fv2%23%3E%0A%0A%20%20%20CONSTRUCT%20%7B%0A%20%20%20%20%20%20%3Fcomponent%20knora-api%3AisMainResource%20true%20.%20%23%20marking%20of%20the%20component%20searched%20for%20as%20the%20main%20resource%2C%20mandatory%0A%20%20%20%20%20%20%3Fcomponent%20knora-api%3Aseqnum%20%3Fseqnum%20.%20%23%20return%20the%20sequence%20number%20in%20the%20response%0A%20%20%20%20%20%20%3Fcomponent%20knora-api%3AhasStillImageFileValue%20%3Ffile%20.%20%23%20return%20the%20StillImageFile%20in%20the%20response%0A%20%20%0A%20%20%20%20%20%20%3Fcomponent%20knora-api%3AisPartOf%20%3Chttp%3A%2F%2Frdfh.ch%2Fc5058f3a%3E%20.%0A%20%20%20%7D%20WHERE%20%7B%0A%20%20%20%20%20%20%3Fcomponent%20a%20knora-api%3AResource%20.%20%23%20explicit%20type%20annotation%20for%20the%20component%20searched%20for%2C%20mandatory%0A%20%20%20%20%20%20%3Fcomponent%20a%20knora-api%3AStillImageRepresentation%20.%20%23%20additional%20restriction%20of%20the%20type%20of%20component%2C%20optional%0A%0A%20%20%20%20%20%20%3Fcomponent%20knora-api%3AisPartOf%20%3Chttp%3A%2F%2Frdfh.ch%2Fc5058f3a%3E%20.%20%23%20component%20relates%20to%20compound%20resource%20via%20this%20property%0A%20%20%20%20%20%20knora-api%3AisPartOf%20knora-api%3AobjectType%20knora-api%3AResource%20.%20%23%20type%20annotation%20for%20linking%20property%2C%20mandatory%0A%20%20%20%20%20%20%3Chttp%3A%2F%2Frdfh.ch%2Fc5058f3a%3E%20a%20knora-api%3AResource%20.%20%23%20type%20annotation%20for%20compound%20resource%2C%20mandatory%0A%0A%20%20%20%20%20%20%3Fcomponent%20knora-api%3Aseqnum%20%3Fseqnum%20.%20%23%20component%20must%20have%20a%20sequence%20number%2C%20no%20further%20restrictions%20given%0A%20%20%20%20%20%20knora-api%3Aseqnum%20knora-api%3AobjectType%20xsd%3Ainteger%20.%20%23%20type%20annotation%20for%20the%20value%20property%2C%20mandatory%0A%20%20%20%20%20%20%3Fseqnum%20a%20xsd%3Ainteger%20.%20%23%20type%20annotation%20for%20the%20sequence%20number%2C%20mandatory%0A%0A%20%20%20%20%20%20%3Fcomponent%20knora-api%3AhasStillImageFileValue%20%3Ffile%20.%20%23%20component%20must%20have%20a%20StillImageFile%2C%20no%20further%20restrictions%20given%0A%20%20%20%20%20%20knora-api%3AhasStillImageFileValue%20knora-api%3AobjectType%20knora-api%3AFile%20.%20%23%20type%20annotation%20for%20the%20value%20property%2C%20mandatory%0A%20%20%20%20%20%20%3Ffile%20a%20knora-api%3AFile%20.%20%23%20type%20annotation%20for%20the%20StillImageFile%2C%20mandatory%0A%20%20%20%7D%0A%20%20%20ORDER%20BY%20ASC(%3Fseqnum)%20%23%20order%20by%20sequence%20number%2C%20ascending%0A%20%20%20OFFSET%200%20%23get%20first%20page%20of%20results
let pagesOfZeitgloecklein: ApiV2WithValueObjects.ResourcesSequence = {
    "@graph": [
        {
            "@id": "http://rdfh.ch/8a0b1e75",
            "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#page",
            "http://0.0.0.0:3333/ontology/0803/incunabula/v2#partOfValue": {
                "@id": "http://rdfh.ch/8a0b1e75/values/ac9ddbf4-62a7-4cdc-b530-16cbbaa265bf",
                "@type": "http://api.knora.org/ontology/knora-api/v2#LinkValue",
                "http://api.knora.org/ontology/knora-api/v2#linkValueHasTarget": {
                    "@id": "http://rdfh.ch/c5058f3a",
                    "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book",
                    "http://www.w3.org/2000/01/rdf-schema#label": "Zeitglöcklein des Lebens und Leidens Christi"
                }
            },
            "http://0.0.0.0:3333/ontology/0803/incunabula/v2#seqnum": {
                "@id": "http://rdfh.ch/8a0b1e75/values/e71e39e902",
                "@type": "http://api.knora.org/ontology/knora-api/v2#IntValue",
                "http://api.knora.org/ontology/knora-api/v2#intValueAsInt": 1
            },
            "http://www.w3.org/2000/01/rdf-schema#label": "a1r, Titelblatt"
        },
        {
            "@id": "http://rdfh.ch/4f11adaf",
            "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#page",
            "http://0.0.0.0:3333/ontology/0803/incunabula/v2#partOfValue": {
                "@id": "http://rdfh.ch/4f11adaf/values/0490c077-a754-460b-9633-c78bfe97c784",
                "@type": "http://api.knora.org/ontology/knora-api/v2#LinkValue",
                "http://api.knora.org/ontology/knora-api/v2#linkValueHasTarget": {
                    "@id": "http://rdfh.ch/c5058f3a",
                    "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book",
                    "http://www.w3.org/2000/01/rdf-schema#label": "Zeitglöcklein des Lebens und Leidens Christi"
                }
            },
            "http://0.0.0.0:3333/ontology/0803/incunabula/v2#seqnum": {
                "@id": "http://rdfh.ch/4f11adaf/values/f3c585ce03",
                "@type": "http://api.knora.org/ontology/knora-api/v2#IntValue",
                "http://api.knora.org/ontology/knora-api/v2#intValueAsInt": 2
            },
            "http://www.w3.org/2000/01/rdf-schema#label": "a1v, Titelblatt, Rückseite"
        },
        {
            "@id": "http://rdfh.ch/14173cea",
            "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#page",
            "http://0.0.0.0:3333/ontology/0803/incunabula/v2#partOfValue": {
                "@id": "http://rdfh.ch/14173cea/values/31f0ac77-4966-4eda-b004-d1142a2b84c2",
                "@type": "http://api.knora.org/ontology/knora-api/v2#LinkValue",
                "http://api.knora.org/ontology/knora-api/v2#linkValueHasTarget": {
                    "@id": "http://rdfh.ch/c5058f3a",
                    "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book",
                    "http://www.w3.org/2000/01/rdf-schema#label": "Zeitglöcklein des Lebens und Leidens Christi"
                }
            },
            "http://0.0.0.0:3333/ontology/0803/incunabula/v2#seqnum": {
                "@id": "http://rdfh.ch/14173cea/values/ff6cd2b304",
                "@type": "http://api.knora.org/ontology/knora-api/v2#IntValue",
                "http://api.knora.org/ontology/knora-api/v2#intValueAsInt": 3
            },
            "http://www.w3.org/2000/01/rdf-schema#label": "a2r"
        },
        {
            "@id": "http://rdfh.ch/d91ccb2401",
            "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#page",
            "http://0.0.0.0:3333/ontology/0803/incunabula/v2#partOfValue": {
                "@id": "http://rdfh.ch/d91ccb2401/values/e62f9d58-fe66-468e-ba59-13ea81ef0ebb",
                "@type": "http://api.knora.org/ontology/knora-api/v2#LinkValue",
                "http://api.knora.org/ontology/knora-api/v2#linkValueHasTarget": {
                    "@id": "http://rdfh.ch/c5058f3a",
                    "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book",
                    "http://www.w3.org/2000/01/rdf-schema#label": "Zeitglöcklein des Lebens und Leidens Christi"
                }
            },
            "http://0.0.0.0:3333/ontology/0803/incunabula/v2#seqnum": {
                "@id": "http://rdfh.ch/d91ccb2401/values/0b141f9905",
                "@type": "http://api.knora.org/ontology/knora-api/v2#IntValue",
                "http://api.knora.org/ontology/knora-api/v2#intValueAsInt": 4
            },
            "http://www.w3.org/2000/01/rdf-schema#label": "a2v"
        },
        {
            "@id": "http://rdfh.ch/9e225a5f01",
            "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#page",
            "http://0.0.0.0:3333/ontology/0803/incunabula/v2#partOfValue": {
                "@id": "http://rdfh.ch/9e225a5f01/values/9c480175-7509-4094-af0d-a1a4f6b5c570",
                "@type": "http://api.knora.org/ontology/knora-api/v2#LinkValue",
                "http://api.knora.org/ontology/knora-api/v2#linkValueHasTarget": {
                    "@id": "http://rdfh.ch/c5058f3a",
                    "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book",
                    "http://www.w3.org/2000/01/rdf-schema#label": "Zeitglöcklein des Lebens und Leidens Christi"
                }
            },
            "http://0.0.0.0:3333/ontology/0803/incunabula/v2#seqnum": {
                "@id": "http://rdfh.ch/9e225a5f01/values/17bb6b7e06",
                "@type": "http://api.knora.org/ontology/knora-api/v2#IntValue",
                "http://api.knora.org/ontology/knora-api/v2#intValueAsInt": 5
            },
            "http://www.w3.org/2000/01/rdf-schema#label": "a3r"
        },
        {
            "@id": "http://rdfh.ch/6328e99901",
            "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#page",
            "http://0.0.0.0:3333/ontology/0803/incunabula/v2#partOfValue": {
                "@id": "http://rdfh.ch/6328e99901/values/83b134d7-6d67-43e4-bc78-60fc2c7cf8aa",
                "@type": "http://api.knora.org/ontology/knora-api/v2#LinkValue",
                "http://api.knora.org/ontology/knora-api/v2#linkValueHasTarget": {
                    "@id": "http://rdfh.ch/c5058f3a",
                    "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book",
                    "http://www.w3.org/2000/01/rdf-schema#label": "Zeitglöcklein des Lebens und Leidens Christi"
                }
            },
            "http://0.0.0.0:3333/ontology/0803/incunabula/v2#seqnum": {
                "@id": "http://rdfh.ch/6328e99901/values/2362b86307",
                "@type": "http://api.knora.org/ontology/knora-api/v2#IntValue",
                "http://api.knora.org/ontology/knora-api/v2#intValueAsInt": 6
            },
            "http://www.w3.org/2000/01/rdf-schema#label": "a3v"
        },
        {
            "@id": "http://rdfh.ch/282e78d401",
            "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#page",
            "http://0.0.0.0:3333/ontology/0803/incunabula/v2#partOfValue": {
                "@id": "http://rdfh.ch/282e78d401/values/f8498d6d-bc39-4d6e-acda-09a1f35d256e",
                "@type": "http://api.knora.org/ontology/knora-api/v2#LinkValue",
                "http://api.knora.org/ontology/knora-api/v2#linkValueHasTarget": {
                    "@id": "http://rdfh.ch/c5058f3a",
                    "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book",
                    "http://www.w3.org/2000/01/rdf-schema#label": "Zeitglöcklein des Lebens und Leidens Christi"
                }
            },
            "http://0.0.0.0:3333/ontology/0803/incunabula/v2#seqnum": {
                "@id": "http://rdfh.ch/282e78d401/values/2f09054908",
                "@type": "http://api.knora.org/ontology/knora-api/v2#IntValue",
                "http://api.knora.org/ontology/knora-api/v2#intValueAsInt": 7
            },
            "http://www.w3.org/2000/01/rdf-schema#label": "a4r"
        },
        {
            "@id": "http://rdfh.ch/ed33070f02",
            "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#page",
            "http://0.0.0.0:3333/ontology/0803/incunabula/v2#partOfValue": {
                "@id": "http://rdfh.ch/ed33070f02/values/f4246526-d730-4084-b792-0897ffa44d47",
                "@type": "http://api.knora.org/ontology/knora-api/v2#LinkValue",
                "http://api.knora.org/ontology/knora-api/v2#linkValueHasTarget": {
                    "@id": "http://rdfh.ch/c5058f3a",
                    "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book",
                    "http://www.w3.org/2000/01/rdf-schema#label": "Zeitglöcklein des Lebens und Leidens Christi"
                }
            },
            "http://0.0.0.0:3333/ontology/0803/incunabula/v2#seqnum": {
                "@id": "http://rdfh.ch/ed33070f02/values/3bb0512e09",
                "@type": "http://api.knora.org/ontology/knora-api/v2#IntValue",
                "http://api.knora.org/ontology/knora-api/v2#intValueAsInt": 8
            },
            "http://www.w3.org/2000/01/rdf-schema#label": "a4v"
        },
        {
            "@id": "http://rdfh.ch/b239964902",
            "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#page",
            "http://0.0.0.0:3333/ontology/0803/incunabula/v2#partOfValue": {
                "@id": "http://rdfh.ch/b239964902/values/7dfa406a-298a-4c7a-bdd8-9e9dddca7d25",
                "@type": "http://api.knora.org/ontology/knora-api/v2#LinkValue",
                "http://api.knora.org/ontology/knora-api/v2#linkValueHasTarget": {
                    "@id": "http://rdfh.ch/c5058f3a",
                    "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book",
                    "http://www.w3.org/2000/01/rdf-schema#label": "Zeitglöcklein des Lebens und Leidens Christi"
                }
            },
            "http://0.0.0.0:3333/ontology/0803/incunabula/v2#seqnum": {
                "@id": "http://rdfh.ch/b239964902/values/47579e130a",
                "@type": "http://api.knora.org/ontology/knora-api/v2#IntValue",
                "http://api.knora.org/ontology/knora-api/v2#intValueAsInt": 9
            },
            "http://www.w3.org/2000/01/rdf-schema#label": "a5r"
        },
        {
            "@id": "http://rdfh.ch/773f258402",
            "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#page",
            "http://0.0.0.0:3333/ontology/0803/incunabula/v2#partOfValue": {
                "@id": "http://rdfh.ch/773f258402/values/25c5e9fd-2cb2-4350-88bb-882be3373745",
                "@type": "http://api.knora.org/ontology/knora-api/v2#LinkValue",
                "http://api.knora.org/ontology/knora-api/v2#linkValueHasTarget": {
                    "@id": "http://rdfh.ch/c5058f3a",
                    "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book",
                    "http://www.w3.org/2000/01/rdf-schema#label": "Zeitglöcklein des Lebens und Leidens Christi"
                }
            },
            "http://0.0.0.0:3333/ontology/0803/incunabula/v2#seqnum": {
                "@id": "http://rdfh.ch/773f258402/values/53feeaf80a",
                "@type": "http://api.knora.org/ontology/knora-api/v2#IntValue",
                "http://api.knora.org/ontology/knora-api/v2#intValueAsInt": 10
            },
            "http://www.w3.org/2000/01/rdf-schema#label": "a5v"
        },
        {
            "@id": "http://rdfh.ch/3c45b4be02",
            "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#page",
            "http://0.0.0.0:3333/ontology/0803/incunabula/v2#partOfValue": {
                "@id": "http://rdfh.ch/3c45b4be02/values/c0d9fcf9-9084-49ee-b929-5881703c670c",
                "@type": "http://api.knora.org/ontology/knora-api/v2#LinkValue",
                "http://api.knora.org/ontology/knora-api/v2#linkValueHasTarget": {
                    "@id": "http://rdfh.ch/c5058f3a",
                    "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book",
                    "http://www.w3.org/2000/01/rdf-schema#label": "Zeitglöcklein des Lebens und Leidens Christi"
                }
            },
            "http://0.0.0.0:3333/ontology/0803/incunabula/v2#seqnum": {
                "@id": "http://rdfh.ch/3c45b4be02/values/5fa537de0b",
                "@type": "http://api.knora.org/ontology/knora-api/v2#IntValue",
                "http://api.knora.org/ontology/knora-api/v2#intValueAsInt": 11
            },
            "http://www.w3.org/2000/01/rdf-schema#label": "a6r"
        },
        {
            "@id": "http://rdfh.ch/014b43f902",
            "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#page",
            "http://0.0.0.0:3333/ontology/0803/incunabula/v2#partOfValue": {
                "@id": "http://rdfh.ch/014b43f902/values/5e130352-d154-4edd-a13b-1795055c20ff",
                "@type": "http://api.knora.org/ontology/knora-api/v2#LinkValue",
                "http://api.knora.org/ontology/knora-api/v2#linkValueHasTarget": {
                    "@id": "http://rdfh.ch/c5058f3a",
                    "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book",
                    "http://www.w3.org/2000/01/rdf-schema#label": "Zeitglöcklein des Lebens und Leidens Christi"
                }
            },
            "http://0.0.0.0:3333/ontology/0803/incunabula/v2#seqnum": {
                "@id": "http://rdfh.ch/014b43f902/values/6b4c84c30c",
                "@type": "http://api.knora.org/ontology/knora-api/v2#IntValue",
                "http://api.knora.org/ontology/knora-api/v2#intValueAsInt": 12
            },
            "http://www.w3.org/2000/01/rdf-schema#label": "a6v"
        },
        {
            "@id": "http://rdfh.ch/c650d23303",
            "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#page",
            "http://0.0.0.0:3333/ontology/0803/incunabula/v2#partOfValue": {
                "@id": "http://rdfh.ch/c650d23303/values/e6d75b14-35e5-4092-a5b6-7bc06a1f3847",
                "@type": "http://api.knora.org/ontology/knora-api/v2#LinkValue",
                "http://api.knora.org/ontology/knora-api/v2#linkValueHasTarget": {
                    "@id": "http://rdfh.ch/c5058f3a",
                    "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book",
                    "http://www.w3.org/2000/01/rdf-schema#label": "Zeitglöcklein des Lebens und Leidens Christi"
                }
            },
            "http://0.0.0.0:3333/ontology/0803/incunabula/v2#seqnum": {
                "@id": "http://rdfh.ch/c650d23303/values/77f3d0a80d",
                "@type": "http://api.knora.org/ontology/knora-api/v2#IntValue",
                "http://api.knora.org/ontology/knora-api/v2#intValueAsInt": 13
            },
            "http://www.w3.org/2000/01/rdf-schema#label": "a7r"
        },
        {
            "@id": "http://rdfh.ch/8b56616e03",
            "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#page",
            "http://0.0.0.0:3333/ontology/0803/incunabula/v2#partOfValue": {
                "@id": "http://rdfh.ch/8b56616e03/values/4bbf4e7a-fb6f-48d5-9927-002f85286a44",
                "@type": "http://api.knora.org/ontology/knora-api/v2#LinkValue",
                "http://api.knora.org/ontology/knora-api/v2#linkValueHasTarget": {
                    "@id": "http://rdfh.ch/c5058f3a",
                    "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book",
                    "http://www.w3.org/2000/01/rdf-schema#label": "Zeitglöcklein des Lebens und Leidens Christi"
                }
            },
            "http://0.0.0.0:3333/ontology/0803/incunabula/v2#seqnum": {
                "@id": "http://rdfh.ch/8b56616e03/values/839a1d8e0e",
                "@type": "http://api.knora.org/ontology/knora-api/v2#IntValue",
                "http://api.knora.org/ontology/knora-api/v2#intValueAsInt": 14
            },
            "http://www.w3.org/2000/01/rdf-schema#label": "a7v"
        },
        {
            "@id": "http://rdfh.ch/505cf0a803",
            "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#page",
            "http://0.0.0.0:3333/ontology/0803/incunabula/v2#partOfValue": {
                "@id": "http://rdfh.ch/505cf0a803/values/bc54a8a9-5ead-433a-b12f-7329aaa0d175",
                "@type": "http://api.knora.org/ontology/knora-api/v2#LinkValue",
                "http://api.knora.org/ontology/knora-api/v2#linkValueHasTarget": {
                    "@id": "http://rdfh.ch/c5058f3a",
                    "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book",
                    "http://www.w3.org/2000/01/rdf-schema#label": "Zeitglöcklein des Lebens und Leidens Christi"
                }
            },
            "http://0.0.0.0:3333/ontology/0803/incunabula/v2#seqnum": {
                "@id": "http://rdfh.ch/505cf0a803/values/8f416a730f",
                "@type": "http://api.knora.org/ontology/knora-api/v2#IntValue",
                "http://api.knora.org/ontology/knora-api/v2#intValueAsInt": 15
            },
            "http://www.w3.org/2000/01/rdf-schema#label": "a8r"
        },
        {
            "@id": "http://rdfh.ch/15627fe303",
            "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#page",
            "http://0.0.0.0:3333/ontology/0803/incunabula/v2#partOfValue": {
                "@id": "http://rdfh.ch/15627fe303/values/cb451884-484c-4d1e-a546-6bd98ec4a391",
                "@type": "http://api.knora.org/ontology/knora-api/v2#LinkValue",
                "http://api.knora.org/ontology/knora-api/v2#linkValueHasTarget": {
                    "@id": "http://rdfh.ch/c5058f3a",
                    "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book",
                    "http://www.w3.org/2000/01/rdf-schema#label": "Zeitglöcklein des Lebens und Leidens Christi"
                }
            },
            "http://0.0.0.0:3333/ontology/0803/incunabula/v2#seqnum": {
                "@id": "http://rdfh.ch/15627fe303/values/9be8b65810",
                "@type": "http://api.knora.org/ontology/knora-api/v2#IntValue",
                "http://api.knora.org/ontology/knora-api/v2#intValueAsInt": 16
            },
            "http://www.w3.org/2000/01/rdf-schema#label": "a8v"
        },
        {
            "@id": "http://rdfh.ch/da670e1e04",
            "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#page",
            "http://0.0.0.0:3333/ontology/0803/incunabula/v2#partOfValue": {
                "@id": "http://rdfh.ch/da670e1e04/values/fd45b16a-6da5-4753-8e38-b3ee6378f89b",
                "@type": "http://api.knora.org/ontology/knora-api/v2#LinkValue",
                "http://api.knora.org/ontology/knora-api/v2#linkValueHasTarget": {
                    "@id": "http://rdfh.ch/c5058f3a",
                    "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book",
                    "http://www.w3.org/2000/01/rdf-schema#label": "Zeitglöcklein des Lebens und Leidens Christi"
                }
            },
            "http://0.0.0.0:3333/ontology/0803/incunabula/v2#seqnum": {
                "@id": "http://rdfh.ch/da670e1e04/values/a78f033e11",
                "@type": "http://api.knora.org/ontology/knora-api/v2#IntValue",
                "http://api.knora.org/ontology/knora-api/v2#intValueAsInt": 17
            },
            "http://www.w3.org/2000/01/rdf-schema#label": "b1r"
        },
        {
            "@id": "http://rdfh.ch/9f6d9d5804",
            "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#page",
            "http://0.0.0.0:3333/ontology/0803/incunabula/v2#partOfValue": {
                "@id": "http://rdfh.ch/9f6d9d5804/values/6b10ee30-d80e-4473-97dd-1b02dfb6f9ba",
                "@type": "http://api.knora.org/ontology/knora-api/v2#LinkValue",
                "http://api.knora.org/ontology/knora-api/v2#linkValueHasTarget": {
                    "@id": "http://rdfh.ch/c5058f3a",
                    "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book",
                    "http://www.w3.org/2000/01/rdf-schema#label": "Zeitglöcklein des Lebens und Leidens Christi"
                }
            },
            "http://0.0.0.0:3333/ontology/0803/incunabula/v2#seqnum": {
                "@id": "http://rdfh.ch/9f6d9d5804/values/b336502312",
                "@type": "http://api.knora.org/ontology/knora-api/v2#IntValue",
                "http://api.knora.org/ontology/knora-api/v2#intValueAsInt": 18
            },
            "http://www.w3.org/2000/01/rdf-schema#label": "b1v"
        },
        {
            "@id": "http://rdfh.ch/64732c9304",
            "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#page",
            "http://0.0.0.0:3333/ontology/0803/incunabula/v2#partOfValue": {
                "@id": "http://rdfh.ch/64732c9304/values/78f6208c-38b0-4f3a-ac01-5cdc4fec1d3a",
                "@type": "http://api.knora.org/ontology/knora-api/v2#LinkValue",
                "http://api.knora.org/ontology/knora-api/v2#linkValueHasTarget": {
                    "@id": "http://rdfh.ch/c5058f3a",
                    "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book",
                    "http://www.w3.org/2000/01/rdf-schema#label": "Zeitglöcklein des Lebens und Leidens Christi"
                }
            },
            "http://0.0.0.0:3333/ontology/0803/incunabula/v2#seqnum": {
                "@id": "http://rdfh.ch/64732c9304/values/bfdd9c0813",
                "@type": "http://api.knora.org/ontology/knora-api/v2#IntValue",
                "http://api.knora.org/ontology/knora-api/v2#intValueAsInt": 19
            },
            "http://www.w3.org/2000/01/rdf-schema#label": "b2r"
        },
        {
            "@id": "http://rdfh.ch/2979bbcd04",
            "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#page",
            "http://0.0.0.0:3333/ontology/0803/incunabula/v2#partOfValue": {
                "@id": "http://rdfh.ch/2979bbcd04/values/f7512609-5839-4ca8-a5f0-c2189eaad2eb",
                "@type": "http://api.knora.org/ontology/knora-api/v2#LinkValue",
                "http://api.knora.org/ontology/knora-api/v2#linkValueHasTarget": {
                    "@id": "http://rdfh.ch/c5058f3a",
                    "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book",
                    "http://www.w3.org/2000/01/rdf-schema#label": "Zeitglöcklein des Lebens und Leidens Christi"
                }
            },
            "http://0.0.0.0:3333/ontology/0803/incunabula/v2#seqnum": {
                "@id": "http://rdfh.ch/2979bbcd04/values/cb84e9ed13",
                "@type": "http://api.knora.org/ontology/knora-api/v2#IntValue",
                "http://api.knora.org/ontology/knora-api/v2#intValueAsInt": 20
            },
            "http://www.w3.org/2000/01/rdf-schema#label": "b2v"
        },
        {
            "@id": "http://rdfh.ch/ee7e4a0805",
            "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#page",
            "http://0.0.0.0:3333/ontology/0803/incunabula/v2#partOfValue": {
                "@id": "http://rdfh.ch/ee7e4a0805/values/8345e64e-6ac5-4411-840e-50db0d0ec143",
                "@type": "http://api.knora.org/ontology/knora-api/v2#LinkValue",
                "http://api.knora.org/ontology/knora-api/v2#linkValueHasTarget": {
                    "@id": "http://rdfh.ch/c5058f3a",
                    "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book",
                    "http://www.w3.org/2000/01/rdf-schema#label": "Zeitglöcklein des Lebens und Leidens Christi"
                }
            },
            "http://0.0.0.0:3333/ontology/0803/incunabula/v2#seqnum": {
                "@id": "http://rdfh.ch/ee7e4a0805/values/d72b36d314",
                "@type": "http://api.knora.org/ontology/knora-api/v2#IntValue",
                "http://api.knora.org/ontology/knora-api/v2#intValueAsInt": 21
            },
            "http://www.w3.org/2000/01/rdf-schema#label": "b3r"
        },
        {
            "@id": "http://rdfh.ch/b384d94205",
            "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#page",
            "http://0.0.0.0:3333/ontology/0803/incunabula/v2#partOfValue": {
                "@id": "http://rdfh.ch/b384d94205/values/3673d715-2fef-47f7-b8dd-faa45d1295af",
                "@type": "http://api.knora.org/ontology/knora-api/v2#LinkValue",
                "http://api.knora.org/ontology/knora-api/v2#linkValueHasTarget": {
                    "@id": "http://rdfh.ch/c5058f3a",
                    "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book",
                    "http://www.w3.org/2000/01/rdf-schema#label": "Zeitglöcklein des Lebens und Leidens Christi"
                }
            },
            "http://0.0.0.0:3333/ontology/0803/incunabula/v2#seqnum": {
                "@id": "http://rdfh.ch/b384d94205/values/e3d282b815",
                "@type": "http://api.knora.org/ontology/knora-api/v2#IntValue",
                "http://api.knora.org/ontology/knora-api/v2#intValueAsInt": 22
            },
            "http://www.w3.org/2000/01/rdf-schema#label": "b3v"
        },
        {
            "@id": "http://rdfh.ch/788a687d05",
            "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#page",
            "http://0.0.0.0:3333/ontology/0803/incunabula/v2#partOfValue": {
                "@id": "http://rdfh.ch/788a687d05/values/a9cd6b23-ef0a-497f-93a0-3210f1d92b9f",
                "@type": "http://api.knora.org/ontology/knora-api/v2#LinkValue",
                "http://api.knora.org/ontology/knora-api/v2#linkValueHasTarget": {
                    "@id": "http://rdfh.ch/c5058f3a",
                    "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book",
                    "http://www.w3.org/2000/01/rdf-schema#label": "Zeitglöcklein des Lebens und Leidens Christi"
                }
            },
            "http://0.0.0.0:3333/ontology/0803/incunabula/v2#seqnum": {
                "@id": "http://rdfh.ch/788a687d05/values/ef79cf9d16",
                "@type": "http://api.knora.org/ontology/knora-api/v2#IntValue",
                "http://api.knora.org/ontology/knora-api/v2#intValueAsInt": 23
            },
            "http://www.w3.org/2000/01/rdf-schema#label": "b4r"
        },
        {
            "@id": "http://rdfh.ch/3d90f7b705",
            "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#page",
            "http://0.0.0.0:3333/ontology/0803/incunabula/v2#partOfValue": {
                "@id": "http://rdfh.ch/3d90f7b705/values/d3eb0cba-a0ae-4cc5-958d-37f7a0d549ec",
                "@type": "http://api.knora.org/ontology/knora-api/v2#LinkValue",
                "http://api.knora.org/ontology/knora-api/v2#linkValueHasTarget": {
                    "@id": "http://rdfh.ch/c5058f3a",
                    "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book",
                    "http://www.w3.org/2000/01/rdf-schema#label": "Zeitglöcklein des Lebens und Leidens Christi"
                }
            },
            "http://0.0.0.0:3333/ontology/0803/incunabula/v2#seqnum": {
                "@id": "http://rdfh.ch/3d90f7b705/values/fb201c8317",
                "@type": "http://api.knora.org/ontology/knora-api/v2#IntValue",
                "http://api.knora.org/ontology/knora-api/v2#intValueAsInt": 24
            },
            "http://www.w3.org/2000/01/rdf-schema#label": "b4v"
        },
        {
            "@id": "http://rdfh.ch/029686f205",
            "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#page",
            "http://0.0.0.0:3333/ontology/0803/incunabula/v2#partOfValue": {
                "@id": "http://rdfh.ch/029686f205/values/4f5d810d-12a7-4d28-b856-af5e7d04f1d2",
                "@type": "http://api.knora.org/ontology/knora-api/v2#LinkValue",
                "http://api.knora.org/ontology/knora-api/v2#linkValueHasTarget": {
                    "@id": "http://rdfh.ch/c5058f3a",
                    "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book",
                    "http://www.w3.org/2000/01/rdf-schema#label": "Zeitglöcklein des Lebens und Leidens Christi"
                }
            },
            "http://0.0.0.0:3333/ontology/0803/incunabula/v2#seqnum": {
                "@id": "http://rdfh.ch/029686f205/values/07c8686818",
                "@type": "http://api.knora.org/ontology/knora-api/v2#IntValue",
                "http://api.knora.org/ontology/knora-api/v2#intValueAsInt": 25
            },
            "http://www.w3.org/2000/01/rdf-schema#label": "b5r"
        }
    ]
};

// http://localhost:3333/v2/resources/http%3A%2F%2Frdfh.ch%2F0001%2FH6gBWUuJSuuO-CilHV8kQw
const Thing: ApiV2WithValueObjects.Resource = {
    "@id": "http://rdfh.ch/0001/H6gBWUuJSuuO-CilHV8kQw",
    "@type": "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing",
    "http://0.0.0.0:3333/ontology/0001/anything/v2#hasBoolean": {
        "@id": "http://rdfh.ch/0001/H6gBWUuJSuuO-CilHV8kQw/values/IN4R19yYR0ygi3K2VEHpUQ",
        "@type": "http://api.knora.org/ontology/knora-api/v2#BooleanValue",
        "http://api.knora.org/ontology/knora-api/v2#booleanValueAsBoolean": true
    },
    "http://0.0.0.0:3333/ontology/0001/anything/v2#hasColor": {
        "@id": "http://rdfh.ch/0001/H6gBWUuJSuuO-CilHV8kQw/values/TAziKNP8QxuyhC4Qf9-b6w",
        "@type": "http://api.knora.org/ontology/knora-api/v2#ColorValue",
        "http://api.knora.org/ontology/knora-api/v2#colorValueAsColor": "#ff3333"
    },
    "http://0.0.0.0:3333/ontology/0001/anything/v2#hasDate": {
        "@id": "http://rdfh.ch/0001/H6gBWUuJSuuO-CilHV8kQw/values/-rG4F5FTTu2iB5mTBPVn5Q",
        "@type": "http://api.knora.org/ontology/knora-api/v2#DateValue",
        "http://api.knora.org/ontology/knora-api/v2#dateValueHasCalendar": "GREGORIAN",
        "http://api.knora.org/ontology/knora-api/v2#dateValueHasEndDay": 13,
        "http://api.knora.org/ontology/knora-api/v2#dateValueHasEndEra": "CE",
        "http://api.knora.org/ontology/knora-api/v2#dateValueHasEndMonth": 5,
        "http://api.knora.org/ontology/knora-api/v2#dateValueHasEndYear": 2018,
        "http://api.knora.org/ontology/knora-api/v2#dateValueHasStartDay": 13,
        "http://api.knora.org/ontology/knora-api/v2#dateValueHasStartEra": "CE",
        "http://api.knora.org/ontology/knora-api/v2#dateValueHasStartMonth": 5,
        "http://api.knora.org/ontology/knora-api/v2#dateValueHasStartYear": 2018,
        "http://api.knora.org/ontology/knora-api/v2#valueAsString": "GREGORIAN:2018-05-13 CE"
    },
    "http://0.0.0.0:3333/ontology/0001/anything/v2#hasDecimal": {
        "@id": "http://rdfh.ch/0001/H6gBWUuJSuuO-CilHV8kQw/values/bXMwnrHvQH2DMjOFrGmNzg",
        "@type": "http://api.knora.org/ontology/knora-api/v2#DecimalValue",
        "http://api.knora.org/ontology/knora-api/v2#decimalValueAsDecimal": "1.5"
    },
    "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger": {
        "@id": "http://rdfh.ch/0001/H6gBWUuJSuuO-CilHV8kQw/values/dJ1ES8QTQNepFKF5-EAqdg",
        "@type": "http://api.knora.org/ontology/knora-api/v2#IntValue",
        "http://api.knora.org/ontology/knora-api/v2#intValueAsInt": 1
    },
    "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInterval": {
        "@id": "http://rdfh.ch/0001/H6gBWUuJSuuO-CilHV8kQw/values/RbDKPKHWTC-0lkRKae-E6A",
        "@type": "http://api.knora.org/ontology/knora-api/v2#IntervalValue",
        "http://api.knora.org/ontology/knora-api/v2#intervalValueHasEnd": "216000",
        "http://api.knora.org/ontology/knora-api/v2#intervalValueHasStart": "0"
    },
    "http://0.0.0.0:3333/ontology/0001/anything/v2#hasListItem": {
        "@id": "http://rdfh.ch/0001/H6gBWUuJSuuO-CilHV8kQw/values/XAhEeE3kSVqM4JPGdLt4Ew",
        "@type": "http://api.knora.org/ontology/knora-api/v2#ListValue",
        "http://api.knora.org/ontology/knora-api/v2#listValueAsListNode": {
            "@id": "http://rdfh.ch/lists/0001/treeList01"
        },
        "http://api.knora.org/ontology/knora-api/v2#listValueAsListNodeLabel": "Tree list node 01"
    },
    "http://0.0.0.0:3333/ontology/0001/anything/v2#hasOtherListItem": {
        "@id": "http://rdfh.ch/0001/H6gBWUuJSuuO-CilHV8kQw/values/j8VQjbD0RsyxpyuvfFJCDA",
        "@type": "http://api.knora.org/ontology/knora-api/v2#ListValue",
        "http://api.knora.org/ontology/knora-api/v2#listValueAsListNode": {
            "@id": "http://rdfh.ch/lists/0001/otherTreeList01"
        },
        "http://api.knora.org/ontology/knora-api/v2#listValueAsListNodeLabel": "Other Tree list node 01"
    },
    "http://0.0.0.0:3333/ontology/0001/anything/v2#hasOtherThingValue": {
        "@id": "http://rdfh.ch/0001/H6gBWUuJSuuO-CilHV8kQw/values/uvRVxzL1RD-t9VIQ1TpfUw",
        "@type": "http://api.knora.org/ontology/knora-api/v2#LinkValue",
        "http://api.knora.org/ontology/knora-api/v2#linkValueHasTarget": {
            "@id": "http://rdfh.ch/0001/0C-0L1kORryKzJAJxxRyRQ",
            "@type": "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing",
            "http://www.w3.org/2000/01/rdf-schema#label": "Sierra"
        }
    },
    "http://0.0.0.0:3333/ontology/0001/anything/v2#hasRichtext": {
        "@id": "http://rdfh.ch/0001/H6gBWUuJSuuO-CilHV8kQw/values/rvB4eQ5MTF-Qxq0YgkwaDg",
        "@type": "http://api.knora.org/ontology/knora-api/v2#TextValue",
        "http://api.knora.org/ontology/knora-api/v2#textValueAsXml": "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<text><p>test with <strong>markup</strong></p></text>",
        "http://api.knora.org/ontology/knora-api/v2#textValueHasMapping": "http://rdfh.ch/standoff/mappings/StandardMapping"
    },
    "http://0.0.0.0:3333/ontology/0001/anything/v2#hasText": {
        "@id": "http://rdfh.ch/0001/H6gBWUuJSuuO-CilHV8kQw/values/SZyeLLmOTcCCuS3B0VksHQ",
        "@type": "http://api.knora.org/ontology/knora-api/v2#TextValue",
        "http://api.knora.org/ontology/knora-api/v2#valueAsString": "test"
    },
    "http://0.0.0.0:3333/ontology/0001/anything/v2#hasUri": {
        "@id": "http://rdfh.ch/0001/H6gBWUuJSuuO-CilHV8kQw/values/uBAmWuRhR-eo1u1eP7qqNg",
        "@type": "http://api.knora.org/ontology/knora-api/v2#UriValue",
        "http://api.knora.org/ontology/knora-api/v2#uriValueAsUri": "http://www.google.ch"
    },
    "http://www.w3.org/2000/01/rdf-schema#label": "testding"
};

// http://localhost:3333/v2/resources/http%3A%2F%2Frdfh.ch%2F0001%2FH6gBWUuJSuuO-CilHV8kQw?schema=simple
const ThingSimple: ApiV2Simple.Resource = {
    "@id": "http://rdfh.ch/0001/H6gBWUuJSuuO-CilHV8kQw",
    "@type": "http://0.0.0.0:3333/ontology/0001/anything/simple/v2#Thing",
    "http://0.0.0.0:3333/ontology/0001/anything/simple/v2#hasBoolean": true,
    "http://0.0.0.0:3333/ontology/0001/anything/simple/v2#hasColor": {
        "@type": "http://api.knora.org/ontology/knora-api/simple/v2#Color",
        "@value": "#ff3333"
    },
    "http://0.0.0.0:3333/ontology/0001/anything/simple/v2#hasDate": {
        "@type": "http://api.knora.org/ontology/knora-api/simple/v2#Date",
        "@value": "GREGORIAN:2018-05-13 CE"
    },
    "http://0.0.0.0:3333/ontology/0001/anything/simple/v2#hasDecimal": {
        "@type": "http://www.w3.org/2001/XMLSchema#decimal",
        "@value": "1.5"
    },
    "http://0.0.0.0:3333/ontology/0001/anything/simple/v2#hasInteger": 1,
    "http://0.0.0.0:3333/ontology/0001/anything/simple/v2#hasInterval": {
        "@type": "http://api.knora.org/ontology/knora-api/simple/v2#Interval",
        "@value": "0 - 216000"
    },
    "http://0.0.0.0:3333/ontology/0001/anything/simple/v2#hasListItem": "Tree list node 01",
    "http://0.0.0.0:3333/ontology/0001/anything/simple/v2#hasOtherListItem": "Other Tree list node 01",
    "http://0.0.0.0:3333/ontology/0001/anything/simple/v2#hasOtherThing": {
        "@id": "http://rdfh.ch/0001/0C-0L1kORryKzJAJxxRyRQ"
    },
    "http://0.0.0.0:3333/ontology/0001/anything/simple/v2#hasRichtext": "test with markup\u001e",
    "http://0.0.0.0:3333/ontology/0001/anything/simple/v2#hasText": "test",
    "http://0.0.0.0:3333/ontology/0001/anything/simple/v2#hasUri": {
        "@type": "http://www.w3.org/2001/XMLSchema#anyURI",
        "@value": "http://www.google.ch"
    },
    "http://www.w3.org/2000/01/rdf-schema#label": "testding"
};

// http://0.0.0.0:3333/v2/resources/http%3A%2F%2Frdfh.ch%2F8a0b1e75?schema=simple
const PageOfZeitgloecklein: ApiV2Simple.Resource = {
    "@id": "http://rdfh.ch/8a0b1e75",
    "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#page",
    "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#description": "Titel: \"Das andechtig zitglo(e)gglyn | des lebens vnd lide(n)s christi nach | den xxiiij stunden vßgeteilt.\"\nHolzschnitt: Schlaguhr mit Zifferblatt für 24 Stunden, auf deren oberem Rand zu beiden Seiten einer Glocke die Verkündigungsszene mit Maria (links) und dem Engel (rechts) zu sehen ist.\nBordüre: Ranken mit Fabelwesen, Holzschnitt.\nKolorierung: Rot, Blau, Grün, Gelb, Braun.\nBeschriftung oben Mitte (Graphitstift) \"B 1\".",
    "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#origname": "ad+s167_druck1=0001.tif",
    "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#page_comment": "Schramm, Bd. 21, Abb. 601.",
    "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#pagenum": "a1r, Titelblatt",
    "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#partOf": {
        "@id": "http://rdfh.ch/c5058f3a"
    },
    "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#seqnum": 1,
    "http://api.knora.org/ontology/knora-api/simple/v2#hasStillImageFile": [
        {
            "@type": "http://api.knora.org/ontology/knora-api/simple/v2#File",
            "@value": "http://localhost:1024/knora/incunabula_0000000002.jpg/full/95,128/0/default.jpg"
        },
        {
            "@type": "http://api.knora.org/ontology/knora-api/simple/v2#File",
            "@value": "http://localhost:1024/knora/incunabula_0000000002.jp2/full/2613,3505/0/default.jpg"
        }
    ],
    "http://www.w3.org/2000/01/rdf-schema#label": "a1r, Titelblatt"
};
