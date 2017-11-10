
import {ResourcesResponse} from "../ResourcesResponse";
import ResourcesSequence = ResourcesResponse.ApiV2WithValueObjects.ResourcesSequence;

// http://localhost:3333/v2/resources/http%3A%2F%2Fdata.knora.org%2Fc5058f3a
let zeitgloecklein: ResourcesSequence =
    {
        "@type": "http://schema.org/ItemList",
        "http://schema.org/itemListElement": {
            "@id": "http://data.knora.org/c5058f3a",
            "@type": "http://api.knora.org/ontology/incunabula/v2#book",
            "http://api.knora.org/ontology/incunabula/v2#citation": [
                {
                    "@id": "http://data.knora.org/c5058f3a/values/184e99ca01",
                    "@type": "http://api.knora.org/ontology/knora-api/v2#TextValue",
                    "http://api.knora.org/ontology/knora-api/v2#valueAsString": "Schramm Bd. XXI, S. 27"
                },
                {
                    "@id": "http://data.knora.org/c5058f3a/values/db77ec0302",
                    "@type": "http://api.knora.org/ontology/knora-api/v2#TextValue",
                    "http://api.knora.org/ontology/knora-api/v2#valueAsString": "GW 4168"
                },
                {
                    "@id": "http://data.knora.org/c5058f3a/values/9ea13f3d02",
                    "@type": "http://api.knora.org/ontology/knora-api/v2#TextValue",
                    "http://api.knora.org/ontology/knora-api/v2#valueAsString": "ISTC ib00512000"
                }
            ],
            "http://api.knora.org/ontology/incunabula/v2#location": {
                "@id": "http://data.knora.org/c5058f3a/values/92faf25701",
                "@type": "http://api.knora.org/ontology/knora-api/v2#TextValue",
                "http://api.knora.org/ontology/knora-api/v2#valueAsString": "UniversitÃ¤ts- und Stadtbibliothek KÃ¶ln, Sign: AD+S167"
            },
            "http://api.knora.org/ontology/incunabula/v2#physical_desc": {
                "@id": "http://data.knora.org/c5058f3a/values/5524469101",
                "@type": "http://api.knora.org/ontology/knora-api/v2#TextValue",
                "http://api.knora.org/ontology/knora-api/v2#valueAsString": "Dimension: 8Â°"
            },
            "http://api.knora.org/ontology/incunabula/v2#pubdate": {
                "@id": "http://data.knora.org/c5058f3a/values/cfd09f1e01",
                "@type": "http://api.knora.org/ontology/knora-api/v2#DateValue",
                "http://api.knora.org/ontology/knora-api/v2#dateValueHasCalendar": "JULIAN",
                "http://api.knora.org/ontology/knora-api/v2#dateValueHasEndYear": 1492,
                "http://api.knora.org/ontology/knora-api/v2#dateValueHasStartYear": 1492,
                "http://api.knora.org/ontology/knora-api/v2#valueAsString": "1492-01-01 - 1492-12-31"
            },
            "http://api.knora.org/ontology/incunabula/v2#publisher": {
                "@id": "http://data.knora.org/c5058f3a/values/497df9ab",
                "@type": "http://api.knora.org/ontology/knora-api/v2#TextValue",
                "http://api.knora.org/ontology/knora-api/v2#valueAsString": "Johann Amerbach"
            },
            "http://api.knora.org/ontology/incunabula/v2#publoc": {
                "@id": "http://data.knora.org/c5058f3a/values/0ca74ce5",
                "@type": "http://api.knora.org/ontology/knora-api/v2#TextValue",
                "http://api.knora.org/ontology/knora-api/v2#valueAsString": "Basel"
            },
            "http://api.knora.org/ontology/incunabula/v2#title": {
                "@id": "http://data.knora.org/c5058f3a/values/c3295339",
                "@type": "http://api.knora.org/ontology/knora-api/v2#TextValue",
                "http://api.knora.org/ontology/knora-api/v2#valueAsString": "ZeitglÃ¶cklein des Lebens und Leidens Christi"
            },
            "http://api.knora.org/ontology/incunabula/v2#url": {
                "@id": "http://data.knora.org/c5058f3a/values/10e00c7acc2704",
                "@type": "http://api.knora.org/ontology/knora-api/v2#TextValue",
                "http://api.knora.org/ontology/knora-api/v2#valueAsString": "http://www.ub.uni-koeln.de/cdm/compoundobject/collection/inkunabeln/id/1878/rec/1"
            },
            "http://schema.org/name": "ZeitglÃ¶cklein des Lebens und Leidens Christi"
        },
        "http://schema.org/numberOfItems": 1
    };

// http://localhost:3333/v2/search/Narr
let fulltextSearchForNarr: ResourcesSequence = {
    "@type": "http://schema.org/ItemList",
    "http://schema.org/itemListElement": [
        {
            "@id": "http://data.knora.org/00505cf0a803",
            "@type": "http://api.knora.org/ontology/incunabula/v2#page",
            "http://api.knora.org/ontology/incunabula/v2#description": {
                "@id": "http://data.knora.org/00505cf0a803/values/549527258a26",
                "@type": "http://api.knora.org/ontology/knora-api/v2#TextValue",
                "http://api.knora.org/ontology/knora-api/v2#valueAsString": "Beginn Kapitel 105.\nHolzschnitt identisch mit Kap. 95: In einer Landschaft fasst ein Narr, der ein Zepter in der Linken hÃ¤lt, einem Mann an die Schulter und redet auf ihn ein, er mÃ¶ge die Feiertage missachten, 11.7 x 8.6 cm."
            },
            "http://schema.org/name": "p7v"
        },
        {
            "@id": "http://data.knora.org/00c650d23303",
            "@type": "http://api.knora.org/ontology/incunabula/v2#page",
            "http://api.knora.org/ontology/incunabula/v2#description": {
                "@id": "http://data.knora.org/00c650d23303/values/af68552c3626",
                "@type": "http://api.knora.org/ontology/knora-api/v2#TextValue",
                "http://api.knora.org/ontology/knora-api/v2#valueAsString": "Beginn Kapitel 21.\nHolzschnitt zu Kap. 21: Andere tadeln und selbst unrecht handeln.\nEin Narr, der mit seinen Beinen im Sumpf steckt, zeigt auf einen nahen Weg, an dem ein Bildstock die Richtung weist.\n11.7 x 8.5 cm.\nUnkoloriert.\n"
            },
            "http://schema.org/name": "d4v"
        },
        {
            "@id": "http://data.knora.org/02abe871e903",
            "@type": "http://api.knora.org/ontology/incunabula/v2#page",
            "http://api.knora.org/ontology/incunabula/v2#description": {
                "@id": "http://data.knora.org/02abe871e903/values/1852a8aa8526",
                "@type": "http://api.knora.org/ontology/knora-api/v2#TextValue",
                "http://api.knora.org/ontology/knora-api/v2#valueAsString": "Beginn Kapitel 99.\nHolzschnitt zu Kap. 99: Von der Einbusse des christlichen Reiches\nAuf einem Hof kniet ein Narr vor den Vertretern der kirchlichen und weltlichen Obrigkeit, die vor ein Portal getreten sind, und bittet darum, sie mÃ¶gen die Narrenkappe verschmÃ¤hen. Im Hintergrund kommentieren zwei weitere Narren Ã¼ber die Hofmauer hinweg das Geschehen mit unglÃ¤ubigen Gesten, 11.7 x 8.5 cm."
            },
            "http://schema.org/name": "o5v"
        },
        {
            "@id": "http://data.knora.org/04416f64ef03",
            "@type": "http://api.knora.org/ontology/incunabula/v2#page",
            "http://api.knora.org/ontology/incunabula/v2#description": {
                "@id": "http://data.knora.org/04416f64ef03/values/6ce3c0ef8b26",
                "@type": "http://api.knora.org/ontology/knora-api/v2#TextValue",
                "http://api.knora.org/ontology/knora-api/v2#valueAsString": "Beginn Kapitel 109.\nHolzschnitt zu Kap. 109: Von Verachtung des UnglÃ¼cks\nEin Narr hat sich in einem Boot zu weit vom Ufer entfernt. Nun birst der Schiffsrumpf, das Segel flattert haltlos umher. Der Narr hÃ¤lt sich an einem Seil der Takelage fest, 11.6 x 8.4 cm."
            },
            "http://schema.org/name": "q2v"
        },
        {
            "@id": "http://data.knora.org/04f25db73f03",
            "@type": "http://api.knora.org/ontology/incunabula/v2#page",
            "http://api.knora.org/ontology/incunabula/v2#description": {
                "@id": "http://data.knora.org/04f25db73f03/values/aa8971af4d26",
                "@type": "http://api.knora.org/ontology/knora-api/v2#TextValue",
                "http://api.knora.org/ontology/knora-api/v2#valueAsString": "Beginn Kapitel 44.\nHolzschnitt zu Kap. 44: Vom LÃ¤rmen in der Kirche\nEin junger Narr in edler Kleidung, der einen Jagdfalken auf dem Arm hÃ¤lt, von Hunden begleitet wird, und klappernde Schuhsohlen trÃ¤gt, geht auf ein Portal zu, in dem eine Frau steht und ihm schÃ¶ne Augen macht.\n11.7 x 8.5 cm.\nUnkoloriert."
            },
            "http://schema.org/name": "g6v"
        },
        {
            "@id": "http://data.knora.org/05c7acceb703",
            "@type": "http://api.knora.org/ontology/incunabula/v2#page",
            "http://api.knora.org/ontology/incunabula/v2#description": {
                "@id": "http://data.knora.org/05c7acceb703/values/5f23f3171d26",
                "@type": "http://api.knora.org/ontology/knora-api/v2#TextValue",
                "http://api.knora.org/ontology/knora-api/v2#valueAsString": "Titelblatt (Hartl 2001: Stultitia Navis I).\nHolzschnitt: \nErsatzholzschnitt fÃ¼r Titelblatt, recto:\nEin Schiff voller Narren fÃ¤hrt nach links. Hinten auf der BrÃ¼cke trinkt ein Narr aus einer Flasche, vorne prÃ¼geln sich zwei weitere narren so sehr, dass einer von ihnen Ã¼ber Bord zu gehen droht. Oben die Inschrift \"Nauis stultoru(m).\"; auf dem Schiffsrumpf die Datierung \"1.4.9.7.\".\n6.5 x 11.5 cm.\noben rechts die bibliographische Angabe  (Graphitstift) \"Hain 3750\"; unten rechts Bibliotheksstempel (queroval, schwarz): \"BIBL. PUBL.| BASILEENSIS\"."
            },
            "http://schema.org/name": "a1r; Titelblatt, recto"
        },
        {
            "@id": "http://data.knora.org/075d33c1bd03",
            "@type": "http://api.knora.org/ontology/incunabula/v2#page",
            "http://api.knora.org/ontology/incunabula/v2#description": {
                "@id": "http://data.knora.org/075d33c1bd03/values/77718ce21e26",
                "@type": "http://api.knora.org/ontology/knora-api/v2#TextValue",
                "http://api.knora.org/ontology/knora-api/v2#valueAsString": "Beginn Kapitel 4.\nHolzschnitt zu Kap. 4: Von neumodischen Sitten.\nEin alter Narr mit Becher hÃ¤lt einem jungen Mann in modischer Tracht einen Spiegel vor. Zwischen Narr und JÃ¼ngling steht der Name â€ž.VLI.â€œ; Ã¼ber den beiden schwebt eine Banderole mit der Aufschrift â€žvly . von . stouffen .  . frisch . vnd vngschaffenâ€œ; zwischen den FÃ¼ssen des JÃ¼nglings ist die Jahreszahl â€ž.1.4.9.4.â€œ zu lesen.\n11.6 x 8.5 cm."
            },
            "http://schema.org/name": "b6r"
        },
        {
            "@id": "http://data.knora.org/0b8940a6c903",
            "@type": "http://api.knora.org/ontology/incunabula/v2#page",
            "http://api.knora.org/ontology/incunabula/v2#description": {
                "@id": "http://data.knora.org/0b8940a6c903/values/f752218c3b26",
                "@type": "http://api.knora.org/ontology/knora-api/v2#TextValue",
                "http://api.knora.org/ontology/knora-api/v2#valueAsString": "Beginn Kapitel 29.\nHolzschnitt zu Kap. 29: Von Verkennung der Mitmenschen.\nEin Narr verspottet einen Sterbenden, neben dessen Bett eine Frau betet, wÃ¤hrend sich unter dem Narren die HÃ¶lle in Gestalt eines gefrÃ¤ssigen Drachenkopfs auftut, 11.7 x 8.5 cm.\n"
            },
            "http://schema.org/name": "e8r"
        },
        {
            "@id": "http://data.knora.org/0d1fc798cf03",
            "@type": "http://api.knora.org/ontology/incunabula/v2#page",
            "http://api.knora.org/ontology/incunabula/v2#description": {
                "@id": "http://data.knora.org/0d1fc798cf03/values/e75f1e764d26",
                "@type": "http://api.knora.org/ontology/knora-api/v2#TextValue",
                "http://api.knora.org/ontology/knora-api/v2#valueAsString": "Beginn Kapitel 43.\nHolzschnitt zu Kap. 43: Missachten der ewigen Seligkeit\nEin Narr steht mit einer grossen Waage in einer Landschaft und wiegt das Himmelsfirmament (links) gegen eine Burg (rechts) auf. Die Zunge der Waage schlÃ¤gt zugunsten der Burg aus, 11.5 x 8.4 cm.\n"
            },
            "http://schema.org/name": "g5r"
        },
        {
            "@id": "http://data.knora.org/0d5ac1099503",
            "@type": "http://api.knora.org/ontology/incunabula/v2#page",
            "http://api.knora.org/ontology/incunabula/v2#description": {
                "@id": "http://data.knora.org/0d5ac1099503/values/4dcdbebc7126",
                "@type": "http://api.knora.org/ontology/knora-api/v2#TextValue",
                "http://api.knora.org/ontology/knora-api/v2#valueAsString": "Beginn Kapitel 66.\nHolzschnitt zu Kap. 66: Von der Erforschung der Welt.\nEin Narr hat ein Schema des Universums auf den Boden Gezeichnet und vermisst es mit einem Zirkel. Von hinten blickt ein zweiter Narr Ã¼ber eine Mauer und wendet sich dem ersten mit spÃ¶ttischen Gesten zu.\n11.6 x 8.4 cm."
            },
            "http://schema.org/name": "k4r"
        },
        {
            "@id": "http://data.knora.org/0fb54d8bd503",
            "@type": "http://api.knora.org/ontology/incunabula/v2#page",
            "http://api.knora.org/ontology/incunabula/v2#description": {
                "@id": "http://data.knora.org/0fb54d8bd503/values/9a966e995f26",
                "@type": "http://api.knora.org/ontology/knora-api/v2#TextValue",
                "http://api.knora.org/ontology/knora-api/v2#valueAsString": "Beginn Kapitel 58.\nHolzschnitt zu Kap. 58: Sich um die Angelegenheiten anderer kÃ¼mmern.\nEin Narr versucht mit einem Wassereimer den Brand im Haus des Nachbarn zu lÃ¶schen und wird dabei von einem anderen Narren, der an seinem Mantel zerrt, unterbrochen, den hinter ihm steht auch sein eigenes Haus in Flammen.\n11.6 x 8.5 cm."
            },
            "http://schema.org/name": "i2r"
        },
        {
            "@id": "http://data.knora.org/0ff047fc9a03",
            "@type": "http://api.knora.org/ontology/incunabula/v2#page",
            "http://api.knora.org/ontology/incunabula/v2#description": {
                "@id": "http://data.knora.org/0ff047fc9a03/values/b9ac70cc7926",
                "@type": "http://api.knora.org/ontology/knora-api/v2#TextValue",
                "http://api.knora.org/ontology/knora-api/v2#valueAsString": "Beginn Kapitel 81.\nHolzschnitt zu Kap. 81: Aus KÃ¼che und Keller.\nEin Narr fÃ¼hrt von einem Boot aus vier Knechte am Strick, die sich in einer KÃ¼che Ã¼ber Spreis und Trank hermachen, wÃ¤hrend eine Frau, die am Herdfeuer sitzt, das Essen zubereitet, 11.7 x 8.5 cm."
            },
            "http://schema.org/name": "m1r"
        },
        {
            "@id": "http://data.knora.org/114bd47ddb03",
            "@type": "http://api.knora.org/ontology/incunabula/v2#page",
            "http://api.knora.org/ontology/incunabula/v2#description": {
                "@id": "http://data.knora.org/114bd47ddb03/values/c99f73e26726",
                "@type": "http://api.knora.org/ontology/knora-api/v2#TextValue",
                "http://api.knora.org/ontology/knora-api/v2#valueAsString": "Beginn Kapitel 69.\nHolzschnitt Lemmer 1979, S. 117: Variante zu Kap. 69.\nEin Narr, der vor einer Stadtkulisse steht, hat mit seiner Rechten einen Ball in die Luft geworfen und schlÃ¤gt mit seiner Linken einen Mann, der sogleich nach seinem Dolch greift. Ein junger Mann beobachtet das Geschehen.\nDer Bildinhalt stimmt weitgehend mit dem ursprÃ¼nglichen Holzschnitt Ã¼berein.\n11.7 x 8.4 cm."
            },
            "http://schema.org/name": "k7r"
        },
        {
            "@id": "http://data.knora.org/14dd8cbc3403",
            "@type": "http://api.knora.org/ontology/incunabula/v2#page",
            "http://api.knora.org/ontology/incunabula/v2#description": {
                "@id": "http://data.knora.org/14dd8cbc3403/values/7e39f54a3726",
                "@type": "http://api.knora.org/ontology/knora-api/v2#TextValue",
                "http://api.knora.org/ontology/knora-api/v2#valueAsString": "Beginn Kapitel 23.\nHolzschnitt zu Kap. 23: Vom blinden Vertrauen auf das GlÃ¼ck.\nEin Narr schaut oben aus dem Fenster seines Hauses, das unten lichterloh brennt. Am Himmel erscheint die rÃ¤chende Gotteshand, die mit einen Hammer auf Haus und Narr einschlÃ¤gt. Auf der Fahne Ã¼ber dem Erker des Hauses ist der Baselstab zu erkennen.\n11.5 x 8.2 cm.\nUnkoloriert.\n"
            },
            "http://schema.org/name": "d6v"
        },
        {
            "@id": "http://data.knora.org/167313af3a03",
            "@type": "http://api.knora.org/ontology/incunabula/v2#page",
            "http://api.knora.org/ontology/incunabula/v2#description": {
                "@id": "http://data.knora.org/167313af3a03/values/1ab5d9ef4226",
                "@type": "http://api.knora.org/ontology/knora-api/v2#TextValue",
                "http://api.knora.org/ontology/knora-api/v2#valueAsString": "Beginn Kapitel 34.\nHolzschnitt zu Kap. 34: Ein Narr sein und es bleiben.\nEin Narr wird von drei GÃ¤nsen umgeben, deren eine von ihm wegfliegt.\n11.7 x 8.4 cm.\nUnkoloriert."
            },
            "http://schema.org/name": "f3v"
        },
        {
            "@id": "http://data.knora.org/1b746fabbe03",
            "@type": "http://api.knora.org/ontology/incunabula/v2#page",
            "http://api.knora.org/ontology/incunabula/v2#description": {
                "@id": "http://data.knora.org/1b746fabbe03/values/8318d9c71f26",
                "@type": "http://api.knora.org/ontology/knora-api/v2#TextValue",
                "http://api.knora.org/ontology/knora-api/v2#valueAsString": "Beginn Kapitel 6.\nHolzschnitt zu Kap. 6: Von mangelhafter Erziehung der Kinder.\nZwei Jungen geraten am Spieltisch Ã¼ber Karten und WÃ¼rfen in Streit. WÃ¤hrend der eine einen Dolch zÃ¼ckt und der andere nach seinem Schwert greift, sitzt ein Ã¤lterer Narr mit verbundenen Augen ahnungslos neben dem Geschehen.\n11.7 x 8.5 cm."
            },
            "http://schema.org/name": "b8r"
        },
        {
            "@id": "http://data.knora.org/1baf691c8403",
            "@type": "http://api.knora.org/ontology/incunabula/v2#page",
            "http://api.knora.org/ontology/incunabula/v2#description": {
                "@id": "http://data.knora.org/1baf691c8403/values/2882816d3a26",
                "@type": "http://api.knora.org/ontology/knora-api/v2#TextValue",
                "http://api.knora.org/ontology/knora-api/v2#valueAsString": "Beginn Kapitel 28.\nHolzschnitt zu Kap. 28: Vom NÃ¶rgeln an Gottes Werken.\nEin Narr, der auf einem Berg ein Feuer entfacht hat, hÃ¤lt seine Hand schÃ¼tzend Ã¼ber die Augen, wÃ¤hrend er seinen Blick auf die hell am Himmel strahlende Sonne richtet. 11.7 x 8.5 cm."
            },
            "http://schema.org/name": "e7r"
        },
        {
            "@id": "http://data.knora.org/1d0af69dc403",
            "@type": "http://api.knora.org/ontology/incunabula/v2#page",
            "http://api.knora.org/ontology/incunabula/v2#description": {
                "@id": "http://data.knora.org/1d0af69dc403/values/4e9dc2b53326",
                "@type": "http://api.knora.org/ontology/knora-api/v2#TextValue",
                "http://api.knora.org/ontology/knora-api/v2#valueAsString": "Beginn Kapitel 18.\nHolzschnitt zu Kap. 18: Vom Dienst an zwei Herren.\nEin mit Spiess bewaffneter Narr blÃ¤st in ein Horn. Sein Hund versucht derweil im Hintergrund zwei Hasen gleichzeitig zu erjagen, 11.6 x 8.4 cm.\n"
            },
            "http://schema.org/name": "d5r"
        },
        {
            "@id": "http://data.knora.org/1fa07c90ca03",
            "@type": "http://api.knora.org/ontology/incunabula/v2#page",
            "http://api.knora.org/ontology/incunabula/v2#description": {
                "@id": "http://data.knora.org/1fa07c90ca03/values/c623c1aa3c26",
                "@type": "http://api.knora.org/ontology/knora-api/v2#TextValue",
                "http://api.knora.org/ontology/knora-api/v2#valueAsString": "Beginn Kapitel 31.\nHolzschnitt zu Kap. 31: Vom Hinausschieben auf morgen.\nEin Narr steht mit ausgebreiteten Armen auf einer Strasse. Auf seinen HÃ¤nden sitzen zwei Raben, die beide â€žCrasâ€œ â€“ das lateinische Wort fÃ¼r â€žmorgenâ€œ â€“ rufen. Auf dem Kopf des Narren sitzt ein Papagei und ahmt den Ruf der KrÃ¤hen nach, 11.6 x 8.5 cm."
            },
            "http://schema.org/name": "f2r"
        },
        {
            "@id": "http://data.knora.org/1fdb76019003",
            "@type": "http://api.knora.org/ontology/incunabula/v2#page",
            "http://api.knora.org/ontology/incunabula/v2#description": {
                "@id": "http://data.knora.org/1fdb76019003/values/118a3f426d26",
                "@type": "http://api.knora.org/ontology/knora-api/v2#TextValue",
                "http://api.knora.org/ontology/knora-api/v2#valueAsString": "Beginn Kapitel 57.\nHolzschnitt zu Kap. 57: Von der Gnadenwahl Gottes.\nEin Narr, der auf einem Krebs reitet, stÃ¼tzt sich auf ein brechendes Schildrohr, das ihm die Hand  durchbohrt. Ein Vogel fliegt auf den offenen Mund des Narren zu.\n11.6 x 8.5 cm."
            },
            "http://schema.org/name": "i1r"
        },
        {
            "@id": "http://data.knora.org/21360383d003",
            "@type": "http://api.knora.org/ontology/incunabula/v2#page",
            "http://api.knora.org/ontology/incunabula/v2#description": {
                "@id": "http://data.knora.org/21360383d003/values/b630be944e26",
                "@type": "http://api.knora.org/ontology/knora-api/v2#TextValue",
                "http://api.knora.org/ontology/knora-api/v2#valueAsString": "Beginn Kapitel 45.\nHolzschnitt zu Kap. 45: Von selbstverschuldetem UnglÃ¼ck.\nIn Gestalt eines Narren springt Empedokles in den lodernden Krater des Ã„tna. Im Vordergrund lÃ¤sst sich ein anderer Narr in einen Brunnen fallen. Beide werden von drei MÃ¤nnern beobachtet, die das Verhalten mit â€žJn geschicht rechtâ€œ  kommentieren, 11.7 x 8.3 cm.\n"
            },
            "http://schema.org/name": "g7r"
        },
        {
            "@id": "http://data.knora.org/2171fdf39503",
            "@type": "http://api.knora.org/ontology/incunabula/v2#page",
            "http://api.knora.org/ontology/incunabula/v2#description": {
                "@id": "http://data.knora.org/2171fdf39503/values/59740ba27226",
                "@type": "http://api.knora.org/ontology/knora-api/v2#TextValue",
                "http://api.knora.org/ontology/knora-api/v2#valueAsString": "Beginn Kapitel 68.\nHolzschnitt zu Kap. 68: Keinen Scherz verstehen.\nEin Kind, das auf einem Steckenpferd reitet und mit einem Stock als Gerte umher fuchtelt, wird von einem Narren am rechten Rand ausgeschimpft. Ein anderer Narr, der neben dem Kind steht, ist dabei, sein Schwert aus der Scheide zu ziehen.\n11.7 x 8.5 cm."
            },
            "http://schema.org/name": "k6r"
        },
        {
            "@id": "http://data.knora.org/230784e69b03",
            "@type": "http://api.knora.org/ontology/incunabula/v2#page",
            "http://api.knora.org/ontology/incunabula/v2#description": {
                "@id": "http://data.knora.org/230784e69b03/values/4ba763247b26",
                "@type": "http://api.knora.org/ontology/knora-api/v2#TextValue",
                "http://api.knora.org/ontology/knora-api/v2#valueAsString": "Beginn Kapitel 83.\nneuer Holzschitt (nicht in Lemmer 1979): Vor einer HÃ¤userkulisse kniet ein Narr mit einem Beutel in der Linken und zwei Keulen in der Rechten vor einem Mann mit Hut und einem jÃ¼ngeren Begleiter, 11.6 x 8.6 cm."
            },
            "http://schema.org/name": "m3r"
        },
        {
            "@id": "http://data.knora.org/23427e576103",
            "@type": "http://api.knora.org/ontology/incunabula/v2#page",
            "http://api.knora.org/ontology/incunabula/v2#description": {
                "@id": "http://data.knora.org/23427e576103/values/c32d62198426",
                "@type": "http://api.knora.org/ontology/knora-api/v2#TextValue",
                "http://api.knora.org/ontology/knora-api/v2#valueAsString": "Beginn Kapitel 96.\nHolzschnitt zu Kap. 96: Schenken und hinterdrein bereuen.\nEin Narr, der vor einem Haus steht, Ã¼berreicht einem bÃ¤rtigen Alten ein Geschenk, kratzt sich dabei aber unschlÃ¼ssig am Kopf.\n11.6 x 8.3 cm.\nUnkoloriert.\nOben rechts Blattnummerierung (Graphitstift): \"128\"."
            },
            "http://schema.org/name": "q8r"
        },
        {
            "@id": "http://data.knora.org/23cc8975d603",
            "@type": "http://api.knora.org/ontology/incunabula/v2#page",
            "http://api.knora.org/ontology/incunabula/v2#description": {
                "@id": "http://data.knora.org/23cc8975d603/values/a63dbb7e6026",
                "@type": "http://api.knora.org/ontology/knora-api/v2#TextValue",
                "http://api.knora.org/ontology/knora-api/v2#valueAsString": "Beginn Kapitel 60.\nHolzschnitt zu Kap. 60: Von SelbstgefÃ¤lligkeit.\nEin alter Narr steht am Ofen und rÃ¼hrt in einem Topf. Gleichzeitig schaut er sich dabei in einem Handspiegel an.\n11.7 x 8.5 cm."
            },
            "http://schema.org/name": "i4r"
        }
    ],
    "http://schema.org/numberOfItems": 25
};

// http://localhost:3333/v2/searchextended/%20%20%20PREFIX%20knora-api%3A%20%3Chttp%3A%2F%2Fapi.knora.org%2Fontology%2Fknora-api%2Fsimple%2Fv2%23%3E%0A%0A%20%20%20CONSTRUCT%20%7B%0A%20%20%20%20%20%20%3Fcomponent%20knora-api%3AisMainResource%20true%20.%20%23%20marking%20of%20the%20component%20searched%20for%20as%20the%20main%20resource%2C%20mandatory%0A%20%20%20%20%20%20%3Fcomponent%20knora-api%3Aseqnum%20%3Fseqnum%20.%20%23%20return%20the%20sequence%20number%20in%20the%20response%0A%20%20%20%20%20%20%3Fcomponent%20knora-api%3AhasStillImageFileValue%20%3Ffile%20.%20%23%20return%20the%20StillImageFile%20in%20the%20response%0A%20%20%0A%20%20%20%20%20%20%3Fcomponent%20knora-api%3AisPartOf%20%3Chttp%3A%2F%2Fdata.knora.org%2Fc5058f3a%3E%20.%0A%20%20%20%7D%20WHERE%20%7B%0A%20%20%20%20%20%20%3Fcomponent%20a%20knora-api%3AResource%20.%20%23%20explicit%20type%20annotation%20for%20the%20component%20searched%20for%2C%20mandatory%0A%20%20%20%20%20%20%3Fcomponent%20a%20knora-api%3AStillImageRepresentation%20.%20%23%20additional%20restriction%20of%20the%20type%20of%20component%2C%20optional%0A%0A%20%20%20%20%20%20%3Fcomponent%20knora-api%3AisPartOf%20%3Chttp%3A%2F%2Fdata.knora.org%2Fc5058f3a%3E%20.%20%23%20component%20relates%20to%20compound%20resource%20via%20this%20property%0A%20%20%20%20%20%20knora-api%3AisPartOf%20knora-api%3AobjectType%20knora-api%3AResource%20.%20%23%20type%20annotation%20for%20linking%20property%2C%20mandatory%0A%20%20%20%20%20%20%3Chttp%3A%2F%2Fdata.knora.org%2Fc5058f3a%3E%20a%20knora-api%3AResource%20.%20%23%20type%20annotation%20for%20compound%20resource%2C%20mandatory%0A%0A%20%20%20%20%20%20%3Fcomponent%20knora-api%3Aseqnum%20%3Fseqnum%20.%20%23%20component%20must%20have%20a%20sequence%20number%2C%20no%20further%20restrictions%20given%0A%20%20%20%20%20%20knora-api%3Aseqnum%20knora-api%3AobjectType%20xsd%3Ainteger%20.%20%23%20type%20annotation%20for%20the%20value%20property%2C%20mandatory%0A%20%20%20%20%20%20%3Fseqnum%20a%20xsd%3Ainteger%20.%20%23%20type%20annotation%20for%20the%20sequence%20number%2C%20mandatory%0A%0A%20%20%20%20%20%20%3Fcomponent%20knora-api%3AhasStillImageFileValue%20%3Ffile%20.%20%23%20component%20must%20have%20a%20StillImageFile%2C%20no%20further%20restrictions%20given%0A%20%20%20%20%20%20knora-api%3AhasStillImageFileValue%20knora-api%3AobjectType%20knora-api%3AStillImageFile%20.%20%23%20type%20annotation%20for%20the%20value%20property%2C%20mandatory%0A%20%20%20%20%20%20%3Ffile%20a%20knora-api%3AStillImageFile%20.%20%23%20type%20annotation%20for%20the%20StillImageFile%2C%20mandatory%0A%20%20%20%7D%0A%20%20%20ORDER%20BY%20ASC(%3Fseqnum)%20%23%20order%20by%20sequence%20number%2C%20ascending%0A%20%20%20OFFSET%200%20%23get%20first%20page%20of%20results
let pagesOfZeitgloecklein: ResourcesSequence = {
    "@type": "http://schema.org/ItemList",
    "http://schema.org/itemListElement": [
        {
            "@id": "http://data.knora.org/8a0b1e75",
            "@type": "http://api.knora.org/ontology/incunabula/v2#page",
            "http://api.knora.org/ontology/incunabula/v2#partOfValue": {
                "@id": "http://data.knora.org/8a0b1e75/values/ac9ddbf4-62a7-4cdc-b530-16cbbaa265bf",
                "@type": "http://api.knora.org/ontology/knora-api/v2#LinkValue",
                "http://api.knora.org/ontology/knora-api/v2#linkValueHasTarget": {
                    "@id": "http://data.knora.org/c5058f3a",
                    "@type": "http://api.knora.org/ontology/incunabula/v2#book",
                    "http://schema.org/name": "Zeitglöcklein des Lebens und Leidens Christi"
                }
            },
            "http://api.knora.org/ontology/incunabula/v2#seqnum": {
                "@id": "http://data.knora.org/8a0b1e75/values/e71e39e902",
                "@type": "http://api.knora.org/ontology/knora-api/v2#IntValue",
                "http://api.knora.org/ontology/knora-api/v2#integerValueAsInteger": 1
            },
            "http://api.knora.org/ontology/knora-api/v2#hasStillImageFileValue": [
                {
                    "@id": "http://data.knora.org/8a0b1e75/reps/bf255339",
                    "@type": "http://api.knora.org/ontology/knora-api/v2#StillImageFileValue",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueAsUrl": "http://localhost:1024/knora/incunabula_0000000002.jpg/full/95,128/0/default.jpg",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueHasFilename": "incunabula_0000000002.jpg",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueIsPreview": true,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimX": 95,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimY": 128,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasIIIFBaseUrl": "http://localhost:1024/knora"
                },
                {
                    "@id": "http://data.knora.org/8a0b1e75/reps/7e4ba672",
                    "@type": "http://api.knora.org/ontology/knora-api/v2#StillImageFileValue",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueAsUrl": "http://localhost:1024/knora/incunabula_0000000002.jp2/full/2613,3505/0/default.jpg",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueHasFilename": "incunabula_0000000002.jp2",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueIsPreview": false,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimX": 2613,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimY": 3505,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasIIIFBaseUrl": "http://localhost:1024/knora"
                }
            ],
            "http://schema.org/name": "a1r, Titelblatt"
        },
        {
            "@id": "http://data.knora.org/4f11adaf",
            "@type": "http://api.knora.org/ontology/incunabula/v2#page",
            "http://api.knora.org/ontology/incunabula/v2#partOfValue": {
                "@id": "http://data.knora.org/4f11adaf/values/0490c077-a754-460b-9633-c78bfe97c784",
                "@type": "http://api.knora.org/ontology/knora-api/v2#LinkValue",
                "http://api.knora.org/ontology/knora-api/v2#linkValueHasTarget": {
                    "@id": "http://data.knora.org/c5058f3a",
                    "@type": "http://api.knora.org/ontology/incunabula/v2#book",
                    "http://schema.org/name": "Zeitglöcklein des Lebens und Leidens Christi"
                }
            },
            "http://api.knora.org/ontology/incunabula/v2#seqnum": {
                "@id": "http://data.knora.org/4f11adaf/values/f3c585ce03",
                "@type": "http://api.knora.org/ontology/knora-api/v2#IntValue",
                "http://api.knora.org/ontology/knora-api/v2#integerValueAsInteger": 2
            },
            "http://api.knora.org/ontology/knora-api/v2#hasStillImageFileValue": [
                {
                    "@id": "http://data.knora.org/4f11adaf/reps/3d71f9ab",
                    "@type": "http://api.knora.org/ontology/knora-api/v2#StillImageFileValue",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueAsUrl": "http://localhost:1024/knora/incunabula_0000000003.jpg/full/81,128/0/default.jpg",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueHasFilename": "incunabula_0000000003.jpg",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueIsPreview": true,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimX": 81,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimY": 128,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasIIIFBaseUrl": "http://localhost:1024/knora"
                },
                {
                    "@id": "http://data.knora.org/4f11adaf/reps/fc964ce5",
                    "@type": "http://api.knora.org/ontology/knora-api/v2#StillImageFileValue",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueAsUrl": "http://localhost:1024/knora/incunabula_0000000003.jp2/full/1870,2937/0/default.jpg",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueHasFilename": "incunabula_0000000003.jp2",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueIsPreview": false,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimX": 1870,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimY": 2937,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasIIIFBaseUrl": "http://localhost:1024/knora"
                }
            ],
            "http://schema.org/name": "a1v, Titelblatt, RÃ¼ckseite"
        },
        {
            "@id": "http://data.knora.org/14173cea",
            "@type": "http://api.knora.org/ontology/incunabula/v2#page",
            "http://api.knora.org/ontology/incunabula/v2#partOfValue": {
                "@id": "http://data.knora.org/14173cea/values/31f0ac77-4966-4eda-b004-d1142a2b84c2",
                "@type": "http://api.knora.org/ontology/knora-api/v2#LinkValue",
                "http://api.knora.org/ontology/knora-api/v2#linkValueHasTarget": {
                    "@id": "http://data.knora.org/c5058f3a",
                    "@type": "http://api.knora.org/ontology/incunabula/v2#book",
                    "http://schema.org/name": "ZeitglÃ¶cklein des Lebens und Leidens Christi"
                }
            },
            "http://api.knora.org/ontology/incunabula/v2#seqnum": {
                "@id": "http://data.knora.org/14173cea/values/ff6cd2b304",
                "@type": "http://api.knora.org/ontology/knora-api/v2#IntValue",
                "http://api.knora.org/ontology/knora-api/v2#integerValueAsInteger": 3
            },
            "http://api.knora.org/ontology/knora-api/v2#hasStillImageFileValue": [
                {
                    "@id": "http://data.knora.org/14173cea/reps/bbbc9f1e01",
                    "@type": "http://api.knora.org/ontology/knora-api/v2#StillImageFileValue",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueAsUrl": "http://localhost:1024/knora/incunabula_0000000004.jpg/full/91,128/0/default.jpg",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueHasFilename": "incunabula_0000000004.jpg",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueIsPreview": true,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimX": 91,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimY": 128,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasIIIFBaseUrl": "http://localhost:1024/knora"
                },
                {
                    "@id": "http://data.knora.org/14173cea/reps/7ae2f25701",
                    "@type": "http://api.knora.org/ontology/knora-api/v2#StillImageFileValue",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueAsUrl": "http://localhost:1024/knora/incunabula_0000000004.jp2/full/2033,2835/0/default.jpg",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueHasFilename": "incunabula_0000000004.jp2",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueIsPreview": false,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimX": 2033,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimY": 2835,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasIIIFBaseUrl": "http://localhost:1024/knora"
                }
            ],
            "http://schema.org/name": "a2r"
        },
        {
            "@id": "http://data.knora.org/d91ccb2401",
            "@type": "http://api.knora.org/ontology/incunabula/v2#page",
            "http://api.knora.org/ontology/incunabula/v2#partOfValue": {
                "@id": "http://data.knora.org/d91ccb2401/values/e62f9d58-fe66-468e-ba59-13ea81ef0ebb",
                "@type": "http://api.knora.org/ontology/knora-api/v2#LinkValue",
                "http://api.knora.org/ontology/knora-api/v2#linkValueHasTarget": {
                    "@id": "http://data.knora.org/c5058f3a",
                    "@type": "http://api.knora.org/ontology/incunabula/v2#book",
                    "http://schema.org/name": "ZeitglÃ¶cklein des Lebens und Leidens Christi"
                }
            },
            "http://api.knora.org/ontology/incunabula/v2#seqnum": {
                "@id": "http://data.knora.org/d91ccb2401/values/0b141f9905",
                "@type": "http://api.knora.org/ontology/knora-api/v2#IntValue",
                "http://api.knora.org/ontology/knora-api/v2#integerValueAsInteger": 4
            },
            "http://api.knora.org/ontology/knora-api/v2#hasStillImageFileValue": [
                {
                    "@id": "http://data.knora.org/d91ccb2401/reps/3908469101",
                    "@type": "http://api.knora.org/ontology/knora-api/v2#StillImageFileValue",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueAsUrl": "http://localhost:1024/knora/incunabula_0000000005.jpg/full/83,128/0/default.jpg",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueHasFilename": "incunabula_0000000005.jpg",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueIsPreview": true,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimX": 83,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimY": 128,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasIIIFBaseUrl": "http://localhost:1024/knora"
                },
                {
                    "@id": "http://data.knora.org/d91ccb2401/reps/f82d99ca01",
                    "@type": "http://api.knora.org/ontology/knora-api/v2#StillImageFileValue",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueAsUrl": "http://localhost:1024/knora/incunabula_0000000005.jp2/full/1886,2903/0/default.jpg",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueHasFilename": "incunabula_0000000005.jp2",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueIsPreview": false,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimX": 1886,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimY": 2903,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasIIIFBaseUrl": "http://localhost:1024/knora"
                }
            ],
            "http://schema.org/name": "a2v"
        },
        {
            "@id": "http://data.knora.org/9e225a5f01",
            "@type": "http://api.knora.org/ontology/incunabula/v2#page",
            "http://api.knora.org/ontology/incunabula/v2#partOfValue": {
                "@id": "http://data.knora.org/9e225a5f01/values/9c480175-7509-4094-af0d-a1a4f6b5c570",
                "@type": "http://api.knora.org/ontology/knora-api/v2#LinkValue",
                "http://api.knora.org/ontology/knora-api/v2#linkValueHasTarget": {
                    "@id": "http://data.knora.org/c5058f3a",
                    "@type": "http://api.knora.org/ontology/incunabula/v2#book",
                    "http://schema.org/name": "ZeitglÃ¶cklein des Lebens und Leidens Christi"
                }
            },
            "http://api.knora.org/ontology/incunabula/v2#seqnum": {
                "@id": "http://data.knora.org/9e225a5f01/values/17bb6b7e06",
                "@type": "http://api.knora.org/ontology/knora-api/v2#IntValue",
                "http://api.knora.org/ontology/knora-api/v2#integerValueAsInteger": 5
            },
            "http://api.knora.org/ontology/knora-api/v2#hasStillImageFileValue": [
                {
                    "@id": "http://data.knora.org/9e225a5f01/reps/b753ec0302",
                    "@type": "http://api.knora.org/ontology/knora-api/v2#StillImageFileValue",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueAsUrl": "http://localhost:1024/knora/incunabula_0000000006.jpg/full/92,128/0/default.jpg",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueHasFilename": "incunabula_0000000006.jpg",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueIsPreview": true,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimX": 92,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimY": 128,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasIIIFBaseUrl": "http://localhost:1024/knora"
                },
                {
                    "@id": "http://data.knora.org/9e225a5f01/reps/76793f3d02",
                    "@type": "http://api.knora.org/ontology/knora-api/v2#StillImageFileValue",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueAsUrl": "http://localhost:1024/knora/incunabula_0000000006.jp2/full/2053,2841/0/default.jpg",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueHasFilename": "incunabula_0000000006.jp2",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueIsPreview": false,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimX": 2053,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimY": 2841,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasIIIFBaseUrl": "http://localhost:1024/knora"
                }
            ],
            "http://schema.org/name": "a3r"
        },
        {
            "@id": "http://data.knora.org/6328e99901",
            "@type": "http://api.knora.org/ontology/incunabula/v2#page",
            "http://api.knora.org/ontology/incunabula/v2#partOfValue": {
                "@id": "http://data.knora.org/6328e99901/values/83b134d7-6d67-43e4-bc78-60fc2c7cf8aa",
                "@type": "http://api.knora.org/ontology/knora-api/v2#LinkValue",
                "http://api.knora.org/ontology/knora-api/v2#linkValueHasTarget": {
                    "@id": "http://data.knora.org/c5058f3a",
                    "@type": "http://api.knora.org/ontology/incunabula/v2#book",
                    "http://schema.org/name": "ZeitglÃ¶cklein des Lebens und Leidens Christi"
                }
            },
            "http://api.knora.org/ontology/incunabula/v2#seqnum": {
                "@id": "http://data.knora.org/6328e99901/values/2362b86307",
                "@type": "http://api.knora.org/ontology/knora-api/v2#IntValue",
                "http://api.knora.org/ontology/knora-api/v2#integerValueAsInteger": 6
            },
            "http://api.knora.org/ontology/knora-api/v2#hasStillImageFileValue": [
                {
                    "@id": "http://data.knora.org/6328e99901/reps/359f927602",
                    "@type": "http://api.knora.org/ontology/knora-api/v2#StillImageFileValue",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueAsUrl": "http://localhost:1024/knora/incunabula_0000000007.jpg/full/83,128/0/default.jpg",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueHasFilename": "incunabula_0000000007.jpg",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueIsPreview": true,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimX": 83,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimY": 128,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasIIIFBaseUrl": "http://localhost:1024/knora"
                },
                {
                    "@id": "http://data.knora.org/6328e99901/reps/f4c4e5af02",
                    "@type": "http://api.knora.org/ontology/knora-api/v2#StillImageFileValue",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueAsUrl": "http://localhost:1024/knora/incunabula_0000000007.jp2/full/1907,2926/0/default.jpg",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueHasFilename": "incunabula_0000000007.jp2",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueIsPreview": false,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimX": 1907,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimY": 2926,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasIIIFBaseUrl": "http://localhost:1024/knora"
                }
            ],
            "http://schema.org/name": "a3v"
        },
        {
            "@id": "http://data.knora.org/282e78d401",
            "@type": "http://api.knora.org/ontology/incunabula/v2#page",
            "http://api.knora.org/ontology/incunabula/v2#partOfValue": {
                "@id": "http://data.knora.org/282e78d401/values/f8498d6d-bc39-4d6e-acda-09a1f35d256e",
                "@type": "http://api.knora.org/ontology/knora-api/v2#LinkValue",
                "http://api.knora.org/ontology/knora-api/v2#linkValueHasTarget": {
                    "@id": "http://data.knora.org/c5058f3a",
                    "@type": "http://api.knora.org/ontology/incunabula/v2#book",
                    "http://schema.org/name": "ZeitglÃ¶cklein des Lebens und Leidens Christi"
                }
            },
            "http://api.knora.org/ontology/incunabula/v2#seqnum": {
                "@id": "http://data.knora.org/282e78d401/values/2f09054908",
                "@type": "http://api.knora.org/ontology/knora-api/v2#IntValue",
                "http://api.knora.org/ontology/knora-api/v2#integerValueAsInteger": 7
            },
            "http://api.knora.org/ontology/knora-api/v2#hasStillImageFileValue": [
                {
                    "@id": "http://data.knora.org/282e78d401/reps/b3ea38e902",
                    "@type": "http://api.knora.org/ontology/knora-api/v2#StillImageFileValue",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueAsUrl": "http://localhost:1024/knora/incunabula_0000000008.jpg/full/93,128/0/default.jpg",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueHasFilename": "incunabula_0000000008.jpg",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueIsPreview": true,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimX": 93,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimY": 128,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasIIIFBaseUrl": "http://localhost:1024/knora"
                },
                {
                    "@id": "http://data.knora.org/282e78d401/reps/72108c2203",
                    "@type": "http://api.knora.org/ontology/knora-api/v2#StillImageFileValue",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueAsUrl": "http://localhost:1024/knora/incunabula_0000000008.jp2/full/2049,2825/0/default.jpg",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueHasFilename": "incunabula_0000000008.jp2",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueIsPreview": false,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimX": 2049,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimY": 2825,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasIIIFBaseUrl": "http://localhost:1024/knora"
                }
            ],
            "http://schema.org/name": "a4r"
        },
        {
            "@id": "http://data.knora.org/ed33070f02",
            "@type": "http://api.knora.org/ontology/incunabula/v2#page",
            "http://api.knora.org/ontology/incunabula/v2#partOfValue": {
                "@id": "http://data.knora.org/ed33070f02/values/f4246526-d730-4084-b792-0897ffa44d47",
                "@type": "http://api.knora.org/ontology/knora-api/v2#LinkValue",
                "http://api.knora.org/ontology/knora-api/v2#linkValueHasTarget": {
                    "@id": "http://data.knora.org/c5058f3a",
                    "@type": "http://api.knora.org/ontology/incunabula/v2#book",
                    "http://schema.org/name": "ZeitglÃ¶cklein des Lebens und Leidens Christi"
                }
            },
            "http://api.knora.org/ontology/incunabula/v2#seqnum": {
                "@id": "http://data.knora.org/ed33070f02/values/3bb0512e09",
                "@type": "http://api.knora.org/ontology/knora-api/v2#IntValue",
                "http://api.knora.org/ontology/knora-api/v2#integerValueAsInteger": 8
            },
            "http://api.knora.org/ontology/knora-api/v2#hasStillImageFileValue": [
                {
                    "@id": "http://data.knora.org/ed33070f02/reps/3136df5b03",
                    "@type": "http://api.knora.org/ontology/knora-api/v2#StillImageFileValue",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueAsUrl": "http://localhost:1024/knora/incunabula_0000000009.jpg/full/83,128/0/default.jpg",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueHasFilename": "incunabula_0000000009.jpg",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueIsPreview": true,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimX": 83,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimY": 128,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasIIIFBaseUrl": "http://localhost:1024/knora"
                },
                {
                    "@id": "http://data.knora.org/ed33070f02/reps/f05b329503",
                    "@type": "http://api.knora.org/ontology/knora-api/v2#StillImageFileValue",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueAsUrl": "http://localhost:1024/knora/incunabula_0000000009.jp2/full/1896,2911/0/default.jpg",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueHasFilename": "incunabula_0000000009.jp2",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueIsPreview": false,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimX": 1896,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimY": 2911,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasIIIFBaseUrl": "http://localhost:1024/knora"
                }
            ],
            "http://schema.org/name": "a4v"
        },
        {
            "@id": "http://data.knora.org/b239964902",
            "@type": "http://api.knora.org/ontology/incunabula/v2#page",
            "http://api.knora.org/ontology/incunabula/v2#partOfValue": {
                "@id": "http://data.knora.org/b239964902/values/7dfa406a-298a-4c7a-bdd8-9e9dddca7d25",
                "@type": "http://api.knora.org/ontology/knora-api/v2#LinkValue",
                "http://api.knora.org/ontology/knora-api/v2#linkValueHasTarget": {
                    "@id": "http://data.knora.org/c5058f3a",
                    "@type": "http://api.knora.org/ontology/incunabula/v2#book",
                    "http://schema.org/name": "ZeitglÃ¶cklein des Lebens und Leidens Christi"
                }
            },
            "http://api.knora.org/ontology/incunabula/v2#seqnum": {
                "@id": "http://data.knora.org/b239964902/values/47579e130a",
                "@type": "http://api.knora.org/ontology/knora-api/v2#IntValue",
                "http://api.knora.org/ontology/knora-api/v2#integerValueAsInteger": 9
            },
            "http://api.knora.org/ontology/knora-api/v2#hasStillImageFileValue": [
                {
                    "@id": "http://data.knora.org/b239964902/reps/af8185ce03",
                    "@type": "http://api.knora.org/ontology/knora-api/v2#StillImageFileValue",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueAsUrl": "http://localhost:1024/knora/incunabula_0000000010.jpg/full/92,128/0/default.jpg",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueHasFilename": "incunabula_0000000010.jpg",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueIsPreview": true,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimX": 92,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimY": 128,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasIIIFBaseUrl": "http://localhost:1024/knora"
                },
                {
                    "@id": "http://data.knora.org/b239964902/reps/6ea7d80704",
                    "@type": "http://api.knora.org/ontology/knora-api/v2#StillImageFileValue",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueAsUrl": "http://localhost:1024/knora/incunabula_0000000010.jp2/full/2048,2830/0/default.jpg",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueHasFilename": "incunabula_0000000010.jp2",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueIsPreview": false,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimX": 2048,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimY": 2830,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasIIIFBaseUrl": "http://localhost:1024/knora"
                }
            ],
            "http://schema.org/name": "a5r"
        },
        {
            "@id": "http://data.knora.org/773f258402",
            "@type": "http://api.knora.org/ontology/incunabula/v2#page",
            "http://api.knora.org/ontology/incunabula/v2#partOfValue": {
                "@id": "http://data.knora.org/773f258402/values/25c5e9fd-2cb2-4350-88bb-882be3373745",
                "@type": "http://api.knora.org/ontology/knora-api/v2#LinkValue",
                "http://api.knora.org/ontology/knora-api/v2#linkValueHasTarget": {
                    "@id": "http://data.knora.org/c5058f3a",
                    "@type": "http://api.knora.org/ontology/incunabula/v2#book",
                    "http://schema.org/name": "ZeitglÃ¶cklein des Lebens und Leidens Christi"
                }
            },
            "http://api.knora.org/ontology/incunabula/v2#seqnum": {
                "@id": "http://data.knora.org/773f258402/values/53feeaf80a",
                "@type": "http://api.knora.org/ontology/knora-api/v2#IntValue",
                "http://api.knora.org/ontology/knora-api/v2#integerValueAsInteger": 10
            },
            "http://api.knora.org/ontology/knora-api/v2#hasStillImageFileValue": [
                {
                    "@id": "http://data.knora.org/773f258402/reps/2dcd2b4104",
                    "@type": "http://api.knora.org/ontology/knora-api/v2#StillImageFileValue",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueAsUrl": "http://localhost:1024/knora/incunabula_0000000011.jpg/full/84,128/0/default.jpg",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueHasFilename": "incunabula_0000000011.jpg",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueIsPreview": true,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimX": 84,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimY": 128,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasIIIFBaseUrl": "http://localhost:1024/knora"
                },
                {
                    "@id": "http://data.knora.org/773f258402/reps/ecf27e7a04",
                    "@type": "http://api.knora.org/ontology/knora-api/v2#StillImageFileValue",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueAsUrl": "http://localhost:1024/knora/incunabula_0000000011.jp2/full/1891,2880/0/default.jpg",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueHasFilename": "incunabula_0000000011.jp2",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueIsPreview": false,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimX": 1891,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimY": 2880,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasIIIFBaseUrl": "http://localhost:1024/knora"
                }
            ],
            "http://schema.org/name": "a5v"
        },
        {
            "@id": "http://data.knora.org/3c45b4be02",
            "@type": "http://api.knora.org/ontology/incunabula/v2#page",
            "http://api.knora.org/ontology/incunabula/v2#partOfValue": {
                "@id": "http://data.knora.org/3c45b4be02/values/c0d9fcf9-9084-49ee-b929-5881703c670c",
                "@type": "http://api.knora.org/ontology/knora-api/v2#LinkValue",
                "http://api.knora.org/ontology/knora-api/v2#linkValueHasTarget": {
                    "@id": "http://data.knora.org/c5058f3a",
                    "@type": "http://api.knora.org/ontology/incunabula/v2#book",
                    "http://schema.org/name": "ZeitglÃ¶cklein des Lebens und Leidens Christi"
                }
            },
            "http://api.knora.org/ontology/incunabula/v2#seqnum": {
                "@id": "http://data.knora.org/3c45b4be02/values/5fa537de0b",
                "@type": "http://api.knora.org/ontology/knora-api/v2#IntValue",
                "http://api.knora.org/ontology/knora-api/v2#integerValueAsInteger": 11
            },
            "http://api.knora.org/ontology/knora-api/v2#hasStillImageFileValue": [
                {
                    "@id": "http://data.knora.org/3c45b4be02/reps/ab18d2b304",
                    "@type": "http://api.knora.org/ontology/knora-api/v2#StillImageFileValue",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueAsUrl": "http://localhost:1024/knora/incunabula_0000000012.jpg/full/92,128/0/default.jpg",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueHasFilename": "incunabula_0000000012.jpg",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueIsPreview": true,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimX": 92,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimY": 128,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasIIIFBaseUrl": "http://localhost:1024/knora"
                },
                {
                    "@id": "http://data.knora.org/3c45b4be02/reps/6a3e25ed04",
                    "@type": "http://api.knora.org/ontology/knora-api/v2#StillImageFileValue",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueAsUrl": "http://localhost:1024/knora/incunabula_0000000012.jp2/full/2048,2840/0/default.jpg",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueHasFilename": "incunabula_0000000012.jp2",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueIsPreview": false,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimX": 2048,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimY": 2840,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasIIIFBaseUrl": "http://localhost:1024/knora"
                }
            ],
            "http://schema.org/name": "a6r"
        },
        {
            "@id": "http://data.knora.org/014b43f902",
            "@type": "http://api.knora.org/ontology/incunabula/v2#page",
            "http://api.knora.org/ontology/incunabula/v2#partOfValue": {
                "@id": "http://data.knora.org/014b43f902/values/5e130352-d154-4edd-a13b-1795055c20ff",
                "@type": "http://api.knora.org/ontology/knora-api/v2#LinkValue",
                "http://api.knora.org/ontology/knora-api/v2#linkValueHasTarget": {
                    "@id": "http://data.knora.org/c5058f3a",
                    "@type": "http://api.knora.org/ontology/incunabula/v2#book",
                    "http://schema.org/name": "ZeitglÃ¶cklein des Lebens und Leidens Christi"
                }
            },
            "http://api.knora.org/ontology/incunabula/v2#seqnum": {
                "@id": "http://data.knora.org/014b43f902/values/6b4c84c30c",
                "@type": "http://api.knora.org/ontology/knora-api/v2#IntValue",
                "http://api.knora.org/ontology/knora-api/v2#integerValueAsInteger": 12
            },
            "http://api.knora.org/ontology/knora-api/v2#hasStillImageFileValue": [
                {
                    "@id": "http://data.knora.org/014b43f902/reps/2964782605",
                    "@type": "http://api.knora.org/ontology/knora-api/v2#StillImageFileValue",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueAsUrl": "http://localhost:1024/knora/incunabula_0000000013.jpg/full/82,128/0/default.jpg",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueHasFilename": "incunabula_0000000013.jpg",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueIsPreview": true,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimX": 82,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimY": 128,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasIIIFBaseUrl": "http://localhost:1024/knora"
                },
                {
                    "@id": "http://data.knora.org/014b43f902/reps/e889cb5f05",
                    "@type": "http://api.knora.org/ontology/knora-api/v2#StillImageFileValue",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueAsUrl": "http://localhost:1024/knora/incunabula_0000000013.jp2/full/1860,2905/0/default.jpg",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueHasFilename": "incunabula_0000000013.jp2",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueIsPreview": false,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimX": 1860,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimY": 2905,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasIIIFBaseUrl": "http://localhost:1024/knora"
                }
            ],
            "http://schema.org/name": "a6v"
        },
        {
            "@id": "http://data.knora.org/c650d23303",
            "@type": "http://api.knora.org/ontology/incunabula/v2#page",
            "http://api.knora.org/ontology/incunabula/v2#partOfValue": {
                "@id": "http://data.knora.org/c650d23303/values/e6d75b14-35e5-4092-a5b6-7bc06a1f3847",
                "@type": "http://api.knora.org/ontology/knora-api/v2#LinkValue",
                "http://api.knora.org/ontology/knora-api/v2#linkValueHasTarget": {
                    "@id": "http://data.knora.org/c5058f3a",
                    "@type": "http://api.knora.org/ontology/incunabula/v2#book",
                    "http://schema.org/name": "ZeitglÃ¶cklein des Lebens und Leidens Christi"
                }
            },
            "http://api.knora.org/ontology/incunabula/v2#seqnum": {
                "@id": "http://data.knora.org/c650d23303/values/77f3d0a80d",
                "@type": "http://api.knora.org/ontology/knora-api/v2#IntValue",
                "http://api.knora.org/ontology/knora-api/v2#integerValueAsInteger": 13
            },
            "http://api.knora.org/ontology/knora-api/v2#hasStillImageFileValue": [
                {
                    "@id": "http://data.knora.org/c650d23303/reps/a7af1e9905",
                    "@type": "http://api.knora.org/ontology/knora-api/v2#StillImageFileValue",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueAsUrl": "http://localhost:1024/knora/incunabula_0000000014.jpg/full/93,128/0/default.jpg",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueHasFilename": "incunabula_0000000014.jpg",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueIsPreview": true,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimX": 93,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimY": 128,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasIIIFBaseUrl": "http://localhost:1024/knora"
                },
                {
                    "@id": "http://data.knora.org/c650d23303/reps/66d571d205",
                    "@type": "http://api.knora.org/ontology/knora-api/v2#StillImageFileValue",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueAsUrl": "http://localhost:1024/knora/incunabula_0000000014.jp2/full/2053,2830/0/default.jpg",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueHasFilename": "incunabula_0000000014.jp2",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueIsPreview": false,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimX": 2053,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimY": 2830,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasIIIFBaseUrl": "http://localhost:1024/knora"
                }
            ],
            "http://schema.org/name": "a7r"
        },
        {
            "@id": "http://data.knora.org/8b56616e03",
            "@type": "http://api.knora.org/ontology/incunabula/v2#page",
            "http://api.knora.org/ontology/incunabula/v2#partOfValue": {
                "@id": "http://data.knora.org/8b56616e03/values/4bbf4e7a-fb6f-48d5-9927-002f85286a44",
                "@type": "http://api.knora.org/ontology/knora-api/v2#LinkValue",
                "http://api.knora.org/ontology/knora-api/v2#linkValueHasTarget": {
                    "@id": "http://data.knora.org/c5058f3a",
                    "@type": "http://api.knora.org/ontology/incunabula/v2#book",
                    "http://schema.org/name": "ZeitglÃ¶cklein des Lebens und Leidens Christi"
                }
            },
            "http://api.knora.org/ontology/incunabula/v2#seqnum": {
                "@id": "http://data.knora.org/8b56616e03/values/839a1d8e0e",
                "@type": "http://api.knora.org/ontology/knora-api/v2#IntValue",
                "http://api.knora.org/ontology/knora-api/v2#integerValueAsInteger": 14
            },
            "http://api.knora.org/ontology/knora-api/v2#hasStillImageFileValue": [
                {
                    "@id": "http://data.knora.org/8b56616e03/reps/25fbc40b06",
                    "@type": "http://api.knora.org/ontology/knora-api/v2#StillImageFileValue",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueAsUrl": "http://localhost:1024/knora/incunabula_0000000015.jpg/full/81,128/0/default.jpg",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueHasFilename": "incunabula_0000000015.jpg",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueIsPreview": true,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimX": 81,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimY": 128,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasIIIFBaseUrl": "http://localhost:1024/knora"
                },
                {
                    "@id": "http://data.knora.org/8b56616e03/reps/e420184506",
                    "@type": "http://api.knora.org/ontology/knora-api/v2#StillImageFileValue",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueAsUrl": "http://localhost:1024/knora/incunabula_0000000015.jp2/full/1859,2911/0/default.jpg",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueHasFilename": "incunabula_0000000015.jp2",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueIsPreview": false,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimX": 1859,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimY": 2911,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasIIIFBaseUrl": "http://localhost:1024/knora"
                }
            ],
            "http://schema.org/name": "a7v"
        },
        {
            "@id": "http://data.knora.org/505cf0a803",
            "@type": "http://api.knora.org/ontology/incunabula/v2#page",
            "http://api.knora.org/ontology/incunabula/v2#partOfValue": {
                "@id": "http://data.knora.org/505cf0a803/values/bc54a8a9-5ead-433a-b12f-7329aaa0d175",
                "@type": "http://api.knora.org/ontology/knora-api/v2#LinkValue",
                "http://api.knora.org/ontology/knora-api/v2#linkValueHasTarget": {
                    "@id": "http://data.knora.org/c5058f3a",
                    "@type": "http://api.knora.org/ontology/incunabula/v2#book",
                    "http://schema.org/name": "ZeitglÃ¶cklein des Lebens und Leidens Christi"
                }
            },
            "http://api.knora.org/ontology/incunabula/v2#seqnum": {
                "@id": "http://data.knora.org/505cf0a803/values/8f416a730f",
                "@type": "http://api.knora.org/ontology/knora-api/v2#IntValue",
                "http://api.knora.org/ontology/knora-api/v2#integerValueAsInteger": 15
            },
            "http://api.knora.org/ontology/knora-api/v2#hasStillImageFileValue": [
                {
                    "@id": "http://data.knora.org/505cf0a803/reps/a3466b7e06",
                    "@type": "http://api.knora.org/ontology/knora-api/v2#StillImageFileValue",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueAsUrl": "http://localhost:1024/knora/incunabula_0000000016.jpg/full/93,128/0/default.jpg",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueHasFilename": "incunabula_0000000016.jpg",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueIsPreview": true,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimX": 93,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimY": 128,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasIIIFBaseUrl": "http://localhost:1024/knora"
                },
                {
                    "@id": "http://data.knora.org/505cf0a803/reps/626cbeb706",
                    "@type": "http://api.knora.org/ontology/knora-api/v2#StillImageFileValue",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueAsUrl": "http://localhost:1024/knora/incunabula_0000000016.jp2/full/2052,2815/0/default.jpg",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueHasFilename": "incunabula_0000000016.jp2",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueIsPreview": false,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimX": 2052,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimY": 2815,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasIIIFBaseUrl": "http://localhost:1024/knora"
                }
            ],
            "http://schema.org/name": "a8r"
        },
        {
            "@id": "http://data.knora.org/15627fe303",
            "@type": "http://api.knora.org/ontology/incunabula/v2#page",
            "http://api.knora.org/ontology/incunabula/v2#partOfValue": {
                "@id": "http://data.knora.org/15627fe303/values/cb451884-484c-4d1e-a546-6bd98ec4a391",
                "@type": "http://api.knora.org/ontology/knora-api/v2#LinkValue",
                "http://api.knora.org/ontology/knora-api/v2#linkValueHasTarget": {
                    "@id": "http://data.knora.org/c5058f3a",
                    "@type": "http://api.knora.org/ontology/incunabula/v2#book",
                    "http://schema.org/name": "ZeitglÃ¶cklein des Lebens und Leidens Christi"
                }
            },
            "http://api.knora.org/ontology/incunabula/v2#seqnum": {
                "@id": "http://data.knora.org/15627fe303/values/9be8b65810",
                "@type": "http://api.knora.org/ontology/knora-api/v2#IntValue",
                "http://api.knora.org/ontology/knora-api/v2#integerValueAsInteger": 16
            },
            "http://api.knora.org/ontology/knora-api/v2#hasStillImageFileValue": [
                {
                    "@id": "http://data.knora.org/15627fe303/reps/219211f106",
                    "@type": "http://api.knora.org/ontology/knora-api/v2#StillImageFileValue",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueAsUrl": "http://localhost:1024/knora/incunabula_0000000017.jpg/full/82,128/0/default.jpg",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueHasFilename": "incunabula_0000000017.jpg",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueIsPreview": true,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimX": 82,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimY": 128,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasIIIFBaseUrl": "http://localhost:1024/knora"
                },
                {
                    "@id": "http://data.knora.org/15627fe303/reps/e0b7642a07",
                    "@type": "http://api.knora.org/ontology/knora-api/v2#StillImageFileValue",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueAsUrl": "http://localhost:1024/knora/incunabula_0000000017.jp2/full/1865,2901/0/default.jpg",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueHasFilename": "incunabula_0000000017.jp2",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueIsPreview": false,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimX": 1865,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimY": 2901,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasIIIFBaseUrl": "http://localhost:1024/knora"
                }
            ],
            "http://schema.org/name": "a8v"
        },
        {
            "@id": "http://data.knora.org/da670e1e04",
            "@type": "http://api.knora.org/ontology/incunabula/v2#page",
            "http://api.knora.org/ontology/incunabula/v2#partOfValue": {
                "@id": "http://data.knora.org/da670e1e04/values/fd45b16a-6da5-4753-8e38-b3ee6378f89b",
                "@type": "http://api.knora.org/ontology/knora-api/v2#LinkValue",
                "http://api.knora.org/ontology/knora-api/v2#linkValueHasTarget": {
                    "@id": "http://data.knora.org/c5058f3a",
                    "@type": "http://api.knora.org/ontology/incunabula/v2#book",
                    "http://schema.org/name": "ZeitglÃ¶cklein des Lebens und Leidens Christi"
                }
            },
            "http://api.knora.org/ontology/incunabula/v2#seqnum": {
                "@id": "http://data.knora.org/da670e1e04/values/a78f033e11",
                "@type": "http://api.knora.org/ontology/knora-api/v2#IntValue",
                "http://api.knora.org/ontology/knora-api/v2#integerValueAsInteger": 17
            },
            "http://api.knora.org/ontology/knora-api/v2#hasStillImageFileValue": [
                {
                    "@id": "http://data.knora.org/da670e1e04/reps/9fddb76307",
                    "@type": "http://api.knora.org/ontology/knora-api/v2#StillImageFileValue",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueAsUrl": "http://localhost:1024/knora/incunabula_0000000018.jpg/full/92,128/0/default.jpg",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueHasFilename": "incunabula_0000000018.jpg",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueIsPreview": true,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimX": 92,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimY": 128,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasIIIFBaseUrl": "http://localhost:1024/knora"
                },
                {
                    "@id": "http://data.knora.org/da670e1e04/reps/5e030b9d07",
                    "@type": "http://api.knora.org/ontology/knora-api/v2#StillImageFileValue",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueAsUrl": "http://localhost:1024/knora/incunabula_0000000018.jp2/full/2037,2820/0/default.jpg",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueHasFilename": "incunabula_0000000018.jp2",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueIsPreview": false,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimX": 2037,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimY": 2820,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasIIIFBaseUrl": "http://localhost:1024/knora"
                }
            ],
            "http://schema.org/name": "b1r"
        },
        {
            "@id": "http://data.knora.org/9f6d9d5804",
            "@type": "http://api.knora.org/ontology/incunabula/v2#page",
            "http://api.knora.org/ontology/incunabula/v2#partOfValue": {
                "@id": "http://data.knora.org/9f6d9d5804/values/6b10ee30-d80e-4473-97dd-1b02dfb6f9ba",
                "@type": "http://api.knora.org/ontology/knora-api/v2#LinkValue",
                "http://api.knora.org/ontology/knora-api/v2#linkValueHasTarget": {
                    "@id": "http://data.knora.org/c5058f3a",
                    "@type": "http://api.knora.org/ontology/incunabula/v2#book",
                    "http://schema.org/name": "ZeitglÃ¶cklein des Lebens und Leidens Christi"
                }
            },
            "http://api.knora.org/ontology/incunabula/v2#seqnum": {
                "@id": "http://data.knora.org/9f6d9d5804/values/b336502312",
                "@type": "http://api.knora.org/ontology/knora-api/v2#IntValue",
                "http://api.knora.org/ontology/knora-api/v2#integerValueAsInteger": 18
            },
            "http://api.knora.org/ontology/knora-api/v2#hasStillImageFileValue": [
                {
                    "@id": "http://data.knora.org/9f6d9d5804/reps/1d295ed607",
                    "@type": "http://api.knora.org/ontology/knora-api/v2#StillImageFileValue",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueAsUrl": "http://localhost:1024/knora/incunabula_0000000019.jpg/full/82,128/0/default.jpg",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueHasFilename": "incunabula_0000000019.jpg",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueIsPreview": true,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimX": 82,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimY": 128,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasIIIFBaseUrl": "http://localhost:1024/knora"
                },
                {
                    "@id": "http://data.knora.org/9f6d9d5804/reps/dc4eb10f08",
                    "@type": "http://api.knora.org/ontology/knora-api/v2#StillImageFileValue",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueAsUrl": "http://localhost:1024/knora/incunabula_0000000019.jp2/full/1871,2911/0/default.jpg",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueHasFilename": "incunabula_0000000019.jp2",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueIsPreview": false,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimX": 1871,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimY": 2911,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasIIIFBaseUrl": "http://localhost:1024/knora"
                }
            ],
            "http://schema.org/name": "b1v"
        },
        {
            "@id": "http://data.knora.org/64732c9304",
            "@type": "http://api.knora.org/ontology/incunabula/v2#page",
            "http://api.knora.org/ontology/incunabula/v2#partOfValue": {
                "@id": "http://data.knora.org/64732c9304/values/78f6208c-38b0-4f3a-ac01-5cdc4fec1d3a",
                "@type": "http://api.knora.org/ontology/knora-api/v2#LinkValue",
                "http://api.knora.org/ontology/knora-api/v2#linkValueHasTarget": {
                    "@id": "http://data.knora.org/c5058f3a",
                    "@type": "http://api.knora.org/ontology/incunabula/v2#book",
                    "http://schema.org/name": "ZeitglÃ¶cklein des Lebens und Leidens Christi"
                }
            },
            "http://api.knora.org/ontology/incunabula/v2#seqnum": {
                "@id": "http://data.knora.org/64732c9304/values/bfdd9c0813",
                "@type": "http://api.knora.org/ontology/knora-api/v2#IntValue",
                "http://api.knora.org/ontology/knora-api/v2#integerValueAsInteger": 19
            },
            "http://api.knora.org/ontology/knora-api/v2#hasStillImageFileValue": [
                {
                    "@id": "http://data.knora.org/64732c9304/reps/9b74044908",
                    "@type": "http://api.knora.org/ontology/knora-api/v2#StillImageFileValue",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueAsUrl": "http://localhost:1024/knora/incunabula_0000000020.jpg/full/93,128/0/default.jpg",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueHasFilename": "incunabula_0000000020.jpg",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueIsPreview": true,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimX": 93,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimY": 128,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasIIIFBaseUrl": "http://localhost:1024/knora"
                },
                {
                    "@id": "http://data.knora.org/64732c9304/reps/5a9a578208",
                    "@type": "http://api.knora.org/ontology/knora-api/v2#StillImageFileValue",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueAsUrl": "http://localhost:1024/knora/incunabula_0000000020.jp2/full/2043,2815/0/default.jpg",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueHasFilename": "incunabula_0000000020.jp2",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueIsPreview": false,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimX": 2043,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimY": 2815,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasIIIFBaseUrl": "http://localhost:1024/knora"
                }
            ],
            "http://schema.org/name": "b2r"
        },
        {
            "@id": "http://data.knora.org/2979bbcd04",
            "@type": "http://api.knora.org/ontology/incunabula/v2#page",
            "http://api.knora.org/ontology/incunabula/v2#partOfValue": {
                "@id": "http://data.knora.org/2979bbcd04/values/f7512609-5839-4ca8-a5f0-c2189eaad2eb",
                "@type": "http://api.knora.org/ontology/knora-api/v2#LinkValue",
                "http://api.knora.org/ontology/knora-api/v2#linkValueHasTarget": {
                    "@id": "http://data.knora.org/c5058f3a",
                    "@type": "http://api.knora.org/ontology/incunabula/v2#book",
                    "http://schema.org/name": "ZeitglÃ¶cklein des Lebens und Leidens Christi"
                }
            },
            "http://api.knora.org/ontology/incunabula/v2#seqnum": {
                "@id": "http://data.knora.org/2979bbcd04/values/cb84e9ed13",
                "@type": "http://api.knora.org/ontology/knora-api/v2#IntValue",
                "http://api.knora.org/ontology/knora-api/v2#integerValueAsInteger": 20
            },
            "http://api.knora.org/ontology/knora-api/v2#hasStillImageFileValue": [
                {
                    "@id": "http://data.knora.org/2979bbcd04/reps/19c0aabb08",
                    "@type": "http://api.knora.org/ontology/knora-api/v2#StillImageFileValue",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueAsUrl": "http://localhost:1024/knora/incunabula_0000000021.jpg/full/82,128/0/default.jpg",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueHasFilename": "incunabula_0000000021.jpg",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueIsPreview": true,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimX": 82,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimY": 128,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasIIIFBaseUrl": "http://localhost:1024/knora"
                },
                {
                    "@id": "http://data.knora.org/2979bbcd04/reps/d8e5fdf408",
                    "@type": "http://api.knora.org/ontology/knora-api/v2#StillImageFileValue",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueAsUrl": "http://localhost:1024/knora/incunabula_0000000021.jp2/full/1865,2906/0/default.jpg",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueHasFilename": "incunabula_0000000021.jp2",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueIsPreview": false,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimX": 1865,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimY": 2906,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasIIIFBaseUrl": "http://localhost:1024/knora"
                }
            ],
            "http://schema.org/name": "b2v"
        },
        {
            "@id": "http://data.knora.org/ee7e4a0805",
            "@type": "http://api.knora.org/ontology/incunabula/v2#page",
            "http://api.knora.org/ontology/incunabula/v2#partOfValue": {
                "@id": "http://data.knora.org/ee7e4a0805/values/8345e64e-6ac5-4411-840e-50db0d0ec143",
                "@type": "http://api.knora.org/ontology/knora-api/v2#LinkValue",
                "http://api.knora.org/ontology/knora-api/v2#linkValueHasTarget": {
                    "@id": "http://data.knora.org/c5058f3a",
                    "@type": "http://api.knora.org/ontology/incunabula/v2#book",
                    "http://schema.org/name": "ZeitglÃ¶cklein des Lebens und Leidens Christi"
                }
            },
            "http://api.knora.org/ontology/incunabula/v2#seqnum": {
                "@id": "http://data.knora.org/ee7e4a0805/values/d72b36d314",
                "@type": "http://api.knora.org/ontology/knora-api/v2#IntValue",
                "http://api.knora.org/ontology/knora-api/v2#integerValueAsInteger": 21
            },
            "http://api.knora.org/ontology/knora-api/v2#hasStillImageFileValue": [
                {
                    "@id": "http://data.knora.org/ee7e4a0805/reps/970b512e09",
                    "@type": "http://api.knora.org/ontology/knora-api/v2#StillImageFileValue",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueAsUrl": "http://localhost:1024/knora/incunabula_0000000022.jpg/full/92,128/0/default.jpg",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueHasFilename": "incunabula_0000000022.jpg",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueIsPreview": true,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimX": 92,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimY": 128,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasIIIFBaseUrl": "http://localhost:1024/knora"
                },
                {
                    "@id": "http://data.knora.org/ee7e4a0805/reps/5631a46709",
                    "@type": "http://api.knora.org/ontology/knora-api/v2#StillImageFileValue",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueAsUrl": "http://localhost:1024/knora/incunabula_0000000022.jp2/full/2032,2825/0/default.jpg",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueHasFilename": "incunabula_0000000022.jp2",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueIsPreview": false,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimX": 2032,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimY": 2825,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasIIIFBaseUrl": "http://localhost:1024/knora"
                }
            ],
            "http://schema.org/name": "b3r"
        },
        {
            "@id": "http://data.knora.org/b384d94205",
            "@type": "http://api.knora.org/ontology/incunabula/v2#page",
            "http://api.knora.org/ontology/incunabula/v2#partOfValue": {
                "@id": "http://data.knora.org/b384d94205/values/3673d715-2fef-47f7-b8dd-faa45d1295af",
                "@type": "http://api.knora.org/ontology/knora-api/v2#LinkValue",
                "http://api.knora.org/ontology/knora-api/v2#linkValueHasTarget": {
                    "@id": "http://data.knora.org/c5058f3a",
                    "@type": "http://api.knora.org/ontology/incunabula/v2#book",
                    "http://schema.org/name": "ZeitglÃ¶cklein des Lebens und Leidens Christi"
                }
            },
            "http://api.knora.org/ontology/incunabula/v2#seqnum": {
                "@id": "http://data.knora.org/b384d94205/values/e3d282b815",
                "@type": "http://api.knora.org/ontology/knora-api/v2#IntValue",
                "http://api.knora.org/ontology/knora-api/v2#integerValueAsInteger": 22
            },
            "http://api.knora.org/ontology/knora-api/v2#hasStillImageFileValue": [
                {
                    "@id": "http://data.knora.org/b384d94205/reps/1557f7a009",
                    "@type": "http://api.knora.org/ontology/knora-api/v2#StillImageFileValue",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueAsUrl": "http://localhost:1024/knora/incunabula_0000000023.jpg/full/82,128/0/default.jpg",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueHasFilename": "incunabula_0000000023.jpg",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueIsPreview": true,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimX": 82,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimY": 128,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasIIIFBaseUrl": "http://localhost:1024/knora"
                },
                {
                    "@id": "http://data.knora.org/b384d94205/reps/d47c4ada09",
                    "@type": "http://api.knora.org/ontology/knora-api/v2#StillImageFileValue",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueAsUrl": "http://localhost:1024/knora/incunabula_0000000023.jp2/full/1869,2911/0/default.jpg",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueHasFilename": "incunabula_0000000023.jp2",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueIsPreview": false,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimX": 1869,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimY": 2911,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasIIIFBaseUrl": "http://localhost:1024/knora"
                }
            ],
            "http://schema.org/name": "b3v"
        },
        {
            "@id": "http://data.knora.org/788a687d05",
            "@type": "http://api.knora.org/ontology/incunabula/v2#page",
            "http://api.knora.org/ontology/incunabula/v2#partOfValue": {
                "@id": "http://data.knora.org/788a687d05/values/a9cd6b23-ef0a-497f-93a0-3210f1d92b9f",
                "@type": "http://api.knora.org/ontology/knora-api/v2#LinkValue",
                "http://api.knora.org/ontology/knora-api/v2#linkValueHasTarget": {
                    "@id": "http://data.knora.org/c5058f3a",
                    "@type": "http://api.knora.org/ontology/incunabula/v2#book",
                    "http://schema.org/name": "ZeitglÃ¶cklein des Lebens und Leidens Christi"
                }
            },
            "http://api.knora.org/ontology/incunabula/v2#seqnum": {
                "@id": "http://data.knora.org/788a687d05/values/ef79cf9d16",
                "@type": "http://api.knora.org/ontology/knora-api/v2#IntValue",
                "http://api.knora.org/ontology/knora-api/v2#integerValueAsInteger": 23
            },
            "http://api.knora.org/ontology/knora-api/v2#hasStillImageFileValue": [
                {
                    "@id": "http://data.knora.org/788a687d05/reps/93a29d130a",
                    "@type": "http://api.knora.org/ontology/knora-api/v2#StillImageFileValue",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueAsUrl": "http://localhost:1024/knora/incunabula_0000000024.jpg/full/92,128/0/default.jpg",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueHasFilename": "incunabula_0000000024.jpg",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueIsPreview": true,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimX": 92,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimY": 128,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasIIIFBaseUrl": "http://localhost:1024/knora"
                },
                {
                    "@id": "http://data.knora.org/788a687d05/reps/52c8f04c0a",
                    "@type": "http://api.knora.org/ontology/knora-api/v2#StillImageFileValue",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueAsUrl": "http://localhost:1024/knora/incunabula_0000000024.jp2/full/2040,2816/0/default.jpg",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueHasFilename": "incunabula_0000000024.jp2",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueIsPreview": false,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimX": 2040,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimY": 2816,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasIIIFBaseUrl": "http://localhost:1024/knora"
                }
            ],
            "http://schema.org/name": "b4r"
        },
        {
            "@id": "http://data.knora.org/3d90f7b705",
            "@type": "http://api.knora.org/ontology/incunabula/v2#page",
            "http://api.knora.org/ontology/incunabula/v2#partOfValue": {
                "@id": "http://data.knora.org/3d90f7b705/values/d3eb0cba-a0ae-4cc5-958d-37f7a0d549ec",
                "@type": "http://api.knora.org/ontology/knora-api/v2#LinkValue",
                "http://api.knora.org/ontology/knora-api/v2#linkValueHasTarget": {
                    "@id": "http://data.knora.org/c5058f3a",
                    "@type": "http://api.knora.org/ontology/incunabula/v2#book",
                    "http://schema.org/name": "ZeitglÃ¶cklein des Lebens und Leidens Christi"
                }
            },
            "http://api.knora.org/ontology/incunabula/v2#seqnum": {
                "@id": "http://data.knora.org/3d90f7b705/values/fb201c8317",
                "@type": "http://api.knora.org/ontology/knora-api/v2#IntValue",
                "http://api.knora.org/ontology/knora-api/v2#integerValueAsInteger": 24
            },
            "http://api.knora.org/ontology/knora-api/v2#hasStillImageFileValue": [
                {
                    "@id": "http://data.knora.org/3d90f7b705/reps/11ee43860a",
                    "@type": "http://api.knora.org/ontology/knora-api/v2#StillImageFileValue",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueAsUrl": "http://localhost:1024/knora/incunabula_0000000025.jpg/full/82,128/0/default.jpg",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueHasFilename": "incunabula_0000000025.jpg",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueIsPreview": true,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimX": 82,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimY": 128,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasIIIFBaseUrl": "http://localhost:1024/knora"
                },
                {
                    "@id": "http://data.knora.org/3d90f7b705/reps/d01397bf0a",
                    "@type": "http://api.knora.org/ontology/knora-api/v2#StillImageFileValue",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueAsUrl": "http://localhost:1024/knora/incunabula_0000000025.jp2/full/1866,2893/0/default.jpg",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueHasFilename": "incunabula_0000000025.jp2",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueIsPreview": false,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimX": 1866,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimY": 2893,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasIIIFBaseUrl": "http://localhost:1024/knora"
                }
            ],
            "http://schema.org/name": "b4v"
        },
        {
            "@id": "http://data.knora.org/029686f205",
            "@type": "http://api.knora.org/ontology/incunabula/v2#page",
            "http://api.knora.org/ontology/incunabula/v2#partOfValue": {
                "@id": "http://data.knora.org/029686f205/values/4f5d810d-12a7-4d28-b856-af5e7d04f1d2",
                "@type": "http://api.knora.org/ontology/knora-api/v2#LinkValue",
                "http://api.knora.org/ontology/knora-api/v2#linkValueHasTarget": {
                    "@id": "http://data.knora.org/c5058f3a",
                    "@type": "http://api.knora.org/ontology/incunabula/v2#book",
                    "http://schema.org/name": "ZeitglÃ¶cklein des Lebens und Leidens Christi"
                }
            },
            "http://api.knora.org/ontology/incunabula/v2#seqnum": {
                "@id": "http://data.knora.org/029686f205/values/07c8686818",
                "@type": "http://api.knora.org/ontology/knora-api/v2#IntValue",
                "http://api.knora.org/ontology/knora-api/v2#integerValueAsInteger": 25
            },
            "http://api.knora.org/ontology/knora-api/v2#hasStillImageFileValue": [
                {
                    "@id": "http://data.knora.org/029686f205/reps/8f39eaf80a",
                    "@type": "http://api.knora.org/ontology/knora-api/v2#StillImageFileValue",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueAsUrl": "http://localhost:1024/knora/incunabula_0000000026.jpg/full/93,128/0/default.jpg",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueHasFilename": "incunabula_0000000026.jpg",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueIsPreview": true,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimX": 93,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimY": 128,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasIIIFBaseUrl": "http://localhost:1024/knora"
                },
                {
                    "@id": "http://data.knora.org/029686f205/reps/4e5f3d320b",
                    "@type": "http://api.knora.org/ontology/knora-api/v2#StillImageFileValue",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueAsUrl": "http://localhost:1024/knora/incunabula_0000000026.jp2/full/2048,2804/0/default.jpg",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueHasFilename": "incunabula_0000000026.jp2",
                    "http://api.knora.org/ontology/knora-api/v2#fileValueIsPreview": false,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimX": 2048,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasDimY": 2804,
                    "http://api.knora.org/ontology/knora-api/v2#stillImageFileValueHasIIIFBaseUrl": "http://localhost:1024/knora"
                }
            ],
            "http://schema.org/name": "b5r"
        }
    ],
    "http://schema.org/numberOfItems": 25
};