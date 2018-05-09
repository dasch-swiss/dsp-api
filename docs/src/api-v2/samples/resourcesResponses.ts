
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
            "http://www.w3.org/2000/01/rdf-schema#label": "ZeitglÃ¶cklein des Lebens und Leidens Christi"
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
            "http://www.w3.org/2000/01/rdf-schema#label": "p7v"
        },
        {
            "@id": "http://data.knora.org/00c650d23303",
            "@type": "http://api.knora.org/ontology/incunabula/v2#page",
            "http://api.knora.org/ontology/incunabula/v2#description": {
                "@id": "http://data.knora.org/00c650d23303/values/af68552c3626",
                "@type": "http://api.knora.org/ontology/knora-api/v2#TextValue",
                "http://api.knora.org/ontology/knora-api/v2#valueAsString": "Beginn Kapitel 21.\nHolzschnitt zu Kap. 21: Andere tadeln und selbst unrecht handeln.\nEin Narr, der mit seinen Beinen im Sumpf steckt, zeigt auf einen nahen Weg, an dem ein Bildstock die Richtung weist.\n11.7 x 8.5 cm.\nUnkoloriert.\n"
            },
            "http://www.w3.org/2000/01/rdf-schema#label": "d4v"
        },
        {
            "@id": "http://data.knora.org/02abe871e903",
            "@type": "http://api.knora.org/ontology/incunabula/v2#page",
            "http://api.knora.org/ontology/incunabula/v2#description": {
                "@id": "http://data.knora.org/02abe871e903/values/1852a8aa8526",
                "@type": "http://api.knora.org/ontology/knora-api/v2#TextValue",
                "http://api.knora.org/ontology/knora-api/v2#valueAsString": "Beginn Kapitel 99.\nHolzschnitt zu Kap. 99: Von der Einbusse des christlichen Reiches\nAuf einem Hof kniet ein Narr vor den Vertretern der kirchlichen und weltlichen Obrigkeit, die vor ein Portal getreten sind, und bittet darum, sie mÃ¶gen die Narrenkappe verschmÃ¤hen. Im Hintergrund kommentieren zwei weitere Narren Ã¼ber die Hofmauer hinweg das Geschehen mit unglÃ¤ubigen Gesten, 11.7 x 8.5 cm."
            },
            "http://www.w3.org/2000/01/rdf-schema#label": "o5v"
        },
        {
            "@id": "http://data.knora.org/04416f64ef03",
            "@type": "http://api.knora.org/ontology/incunabula/v2#page",
            "http://api.knora.org/ontology/incunabula/v2#description": {
                "@id": "http://data.knora.org/04416f64ef03/values/6ce3c0ef8b26",
                "@type": "http://api.knora.org/ontology/knora-api/v2#TextValue",
                "http://api.knora.org/ontology/knora-api/v2#valueAsString": "Beginn Kapitel 109.\nHolzschnitt zu Kap. 109: Von Verachtung des UnglÃ¼cks\nEin Narr hat sich in einem Boot zu weit vom Ufer entfernt. Nun birst der Schiffsrumpf, das Segel flattert haltlos umher. Der Narr hÃ¤lt sich an einem Seil der Takelage fest, 11.6 x 8.4 cm."
            },
            "http://www.w3.org/2000/01/rdf-schema#label": "q2v"
        },
        {
            "@id": "http://data.knora.org/04f25db73f03",
            "@type": "http://api.knora.org/ontology/incunabula/v2#page",
            "http://api.knora.org/ontology/incunabula/v2#description": {
                "@id": "http://data.knora.org/04f25db73f03/values/aa8971af4d26",
                "@type": "http://api.knora.org/ontology/knora-api/v2#TextValue",
                "http://api.knora.org/ontology/knora-api/v2#valueAsString": "Beginn Kapitel 44.\nHolzschnitt zu Kap. 44: Vom LÃ¤rmen in der Kirche\nEin junger Narr in edler Kleidung, der einen Jagdfalken auf dem Arm hÃ¤lt, von Hunden begleitet wird, und klappernde Schuhsohlen trÃ¤gt, geht auf ein Portal zu, in dem eine Frau steht und ihm schÃ¶ne Augen macht.\n11.7 x 8.5 cm.\nUnkoloriert."
            },
            "http://www.w3.org/2000/01/rdf-schema#label": "g6v"
        },
        {
            "@id": "http://data.knora.org/05c7acceb703",
            "@type": "http://api.knora.org/ontology/incunabula/v2#page",
            "http://api.knora.org/ontology/incunabula/v2#description": {
                "@id": "http://data.knora.org/05c7acceb703/values/5f23f3171d26",
                "@type": "http://api.knora.org/ontology/knora-api/v2#TextValue",
                "http://api.knora.org/ontology/knora-api/v2#valueAsString": "Titelblatt (Hartl 2001: Stultitia Navis I).\nHolzschnitt: \nErsatzholzschnitt fÃ¼r Titelblatt, recto:\nEin Schiff voller Narren fÃ¤hrt nach links. Hinten auf der BrÃ¼cke trinkt ein Narr aus einer Flasche, vorne prÃ¼geln sich zwei weitere narren so sehr, dass einer von ihnen Ã¼ber Bord zu gehen droht. Oben die Inschrift \"Nauis stultoru(m).\"; auf dem Schiffsrumpf die Datierung \"1.4.9.7.\".\n6.5 x 11.5 cm.\noben rechts die bibliographische Angabe  (Graphitstift) \"Hain 3750\"; unten rechts Bibliotheksstempel (queroval, schwarz): \"BIBL. PUBL.| BASILEENSIS\"."
            },
            "http://www.w3.org/2000/01/rdf-schema#label": "a1r; Titelblatt, recto"
        },
        {
            "@id": "http://data.knora.org/075d33c1bd03",
            "@type": "http://api.knora.org/ontology/incunabula/v2#page",
            "http://api.knora.org/ontology/incunabula/v2#description": {
                "@id": "http://data.knora.org/075d33c1bd03/values/77718ce21e26",
                "@type": "http://api.knora.org/ontology/knora-api/v2#TextValue",
                "http://api.knora.org/ontology/knora-api/v2#valueAsString": "Beginn Kapitel 4.\nHolzschnitt zu Kap. 4: Von neumodischen Sitten.\nEin alter Narr mit Becher hÃ¤lt einem jungen Mann in modischer Tracht einen Spiegel vor. Zwischen Narr und JÃ¼ngling steht der Name â€ž.VLI.â€œ; Ã¼ber den beiden schwebt eine Banderole mit der Aufschrift â€žvly . von . stouffen .  . frisch . vnd vngschaffenâ€œ; zwischen den FÃ¼ssen des JÃ¼nglings ist die Jahreszahl â€ž.1.4.9.4.â€œ zu lesen.\n11.6 x 8.5 cm."
            },
            "http://www.w3.org/2000/01/rdf-schema#label": "b6r"
        },
        {
            "@id": "http://data.knora.org/0b8940a6c903",
            "@type": "http://api.knora.org/ontology/incunabula/v2#page",
            "http://api.knora.org/ontology/incunabula/v2#description": {
                "@id": "http://data.knora.org/0b8940a6c903/values/f752218c3b26",
                "@type": "http://api.knora.org/ontology/knora-api/v2#TextValue",
                "http://api.knora.org/ontology/knora-api/v2#valueAsString": "Beginn Kapitel 29.\nHolzschnitt zu Kap. 29: Von Verkennung der Mitmenschen.\nEin Narr verspottet einen Sterbenden, neben dessen Bett eine Frau betet, wÃ¤hrend sich unter dem Narren die HÃ¶lle in Gestalt eines gefrÃ¤ssigen Drachenkopfs auftut, 11.7 x 8.5 cm.\n"
            },
            "http://www.w3.org/2000/01/rdf-schema#label": "e8r"
        },
        {
            "@id": "http://data.knora.org/0d1fc798cf03",
            "@type": "http://api.knora.org/ontology/incunabula/v2#page",
            "http://api.knora.org/ontology/incunabula/v2#description": {
                "@id": "http://data.knora.org/0d1fc798cf03/values/e75f1e764d26",
                "@type": "http://api.knora.org/ontology/knora-api/v2#TextValue",
                "http://api.knora.org/ontology/knora-api/v2#valueAsString": "Beginn Kapitel 43.\nHolzschnitt zu Kap. 43: Missachten der ewigen Seligkeit\nEin Narr steht mit einer grossen Waage in einer Landschaft und wiegt das Himmelsfirmament (links) gegen eine Burg (rechts) auf. Die Zunge der Waage schlÃ¤gt zugunsten der Burg aus, 11.5 x 8.4 cm.\n"
            },
            "http://www.w3.org/2000/01/rdf-schema#label": "g5r"
        },
        {
            "@id": "http://data.knora.org/0d5ac1099503",
            "@type": "http://api.knora.org/ontology/incunabula/v2#page",
            "http://api.knora.org/ontology/incunabula/v2#description": {
                "@id": "http://data.knora.org/0d5ac1099503/values/4dcdbebc7126",
                "@type": "http://api.knora.org/ontology/knora-api/v2#TextValue",
                "http://api.knora.org/ontology/knora-api/v2#valueAsString": "Beginn Kapitel 66.\nHolzschnitt zu Kap. 66: Von der Erforschung der Welt.\nEin Narr hat ein Schema des Universums auf den Boden Gezeichnet und vermisst es mit einem Zirkel. Von hinten blickt ein zweiter Narr Ã¼ber eine Mauer und wendet sich dem ersten mit spÃ¶ttischen Gesten zu.\n11.6 x 8.4 cm."
            },
            "http://www.w3.org/2000/01/rdf-schema#label": "k4r"
        },
        {
            "@id": "http://data.knora.org/0fb54d8bd503",
            "@type": "http://api.knora.org/ontology/incunabula/v2#page",
            "http://api.knora.org/ontology/incunabula/v2#description": {
                "@id": "http://data.knora.org/0fb54d8bd503/values/9a966e995f26",
                "@type": "http://api.knora.org/ontology/knora-api/v2#TextValue",
                "http://api.knora.org/ontology/knora-api/v2#valueAsString": "Beginn Kapitel 58.\nHolzschnitt zu Kap. 58: Sich um die Angelegenheiten anderer kÃ¼mmern.\nEin Narr versucht mit einem Wassereimer den Brand im Haus des Nachbarn zu lÃ¶schen und wird dabei von einem anderen Narren, der an seinem Mantel zerrt, unterbrochen, den hinter ihm steht auch sein eigenes Haus in Flammen.\n11.6 x 8.5 cm."
            },
            "http://www.w3.org/2000/01/rdf-schema#label": "i2r"
        },
        {
            "@id": "http://data.knora.org/0ff047fc9a03",
            "@type": "http://api.knora.org/ontology/incunabula/v2#page",
            "http://api.knora.org/ontology/incunabula/v2#description": {
                "@id": "http://data.knora.org/0ff047fc9a03/values/b9ac70cc7926",
                "@type": "http://api.knora.org/ontology/knora-api/v2#TextValue",
                "http://api.knora.org/ontology/knora-api/v2#valueAsString": "Beginn Kapitel 81.\nHolzschnitt zu Kap. 81: Aus KÃ¼che und Keller.\nEin Narr fÃ¼hrt von einem Boot aus vier Knechte am Strick, die sich in einer KÃ¼che Ã¼ber Spreis und Trank hermachen, wÃ¤hrend eine Frau, die am Herdfeuer sitzt, das Essen zubereitet, 11.7 x 8.5 cm."
            },
            "http://www.w3.org/2000/01/rdf-schema#label": "m1r"
        },
        {
            "@id": "http://data.knora.org/114bd47ddb03",
            "@type": "http://api.knora.org/ontology/incunabula/v2#page",
            "http://api.knora.org/ontology/incunabula/v2#description": {
                "@id": "http://data.knora.org/114bd47ddb03/values/c99f73e26726",
                "@type": "http://api.knora.org/ontology/knora-api/v2#TextValue",
                "http://api.knora.org/ontology/knora-api/v2#valueAsString": "Beginn Kapitel 69.\nHolzschnitt Lemmer 1979, S. 117: Variante zu Kap. 69.\nEin Narr, der vor einer Stadtkulisse steht, hat mit seiner Rechten einen Ball in die Luft geworfen und schlÃ¤gt mit seiner Linken einen Mann, der sogleich nach seinem Dolch greift. Ein junger Mann beobachtet das Geschehen.\nDer Bildinhalt stimmt weitgehend mit dem ursprÃ¼nglichen Holzschnitt Ã¼berein.\n11.7 x 8.4 cm."
            },
            "http://www.w3.org/2000/01/rdf-schema#label": "k7r"
        },
        {
            "@id": "http://data.knora.org/14dd8cbc3403",
            "@type": "http://api.knora.org/ontology/incunabula/v2#page",
            "http://api.knora.org/ontology/incunabula/v2#description": {
                "@id": "http://data.knora.org/14dd8cbc3403/values/7e39f54a3726",
                "@type": "http://api.knora.org/ontology/knora-api/v2#TextValue",
                "http://api.knora.org/ontology/knora-api/v2#valueAsString": "Beginn Kapitel 23.\nHolzschnitt zu Kap. 23: Vom blinden Vertrauen auf das GlÃ¼ck.\nEin Narr schaut oben aus dem Fenster seines Hauses, das unten lichterloh brennt. Am Himmel erscheint die rÃ¤chende Gotteshand, die mit einen Hammer auf Haus und Narr einschlÃ¤gt. Auf der Fahne Ã¼ber dem Erker des Hauses ist der Baselstab zu erkennen.\n11.5 x 8.2 cm.\nUnkoloriert.\n"
            },
            "http://www.w3.org/2000/01/rdf-schema#label": "d6v"
        },
        {
            "@id": "http://data.knora.org/167313af3a03",
            "@type": "http://api.knora.org/ontology/incunabula/v2#page",
            "http://api.knora.org/ontology/incunabula/v2#description": {
                "@id": "http://data.knora.org/167313af3a03/values/1ab5d9ef4226",
                "@type": "http://api.knora.org/ontology/knora-api/v2#TextValue",
                "http://api.knora.org/ontology/knora-api/v2#valueAsString": "Beginn Kapitel 34.\nHolzschnitt zu Kap. 34: Ein Narr sein und es bleiben.\nEin Narr wird von drei GÃ¤nsen umgeben, deren eine von ihm wegfliegt.\n11.7 x 8.4 cm.\nUnkoloriert."
            },
            "http://www.w3.org/2000/01/rdf-schema#label": "f3v"
        },
        {
            "@id": "http://data.knora.org/1b746fabbe03",
            "@type": "http://api.knora.org/ontology/incunabula/v2#page",
            "http://api.knora.org/ontology/incunabula/v2#description": {
                "@id": "http://data.knora.org/1b746fabbe03/values/8318d9c71f26",
                "@type": "http://api.knora.org/ontology/knora-api/v2#TextValue",
                "http://api.knora.org/ontology/knora-api/v2#valueAsString": "Beginn Kapitel 6.\nHolzschnitt zu Kap. 6: Von mangelhafter Erziehung der Kinder.\nZwei Jungen geraten am Spieltisch Ã¼ber Karten und WÃ¼rfen in Streit. WÃ¤hrend der eine einen Dolch zÃ¼ckt und der andere nach seinem Schwert greift, sitzt ein Ã¤lterer Narr mit verbundenen Augen ahnungslos neben dem Geschehen.\n11.7 x 8.5 cm."
            },
            "http://www.w3.org/2000/01/rdf-schema#label": "b8r"
        },
        {
            "@id": "http://data.knora.org/1baf691c8403",
            "@type": "http://api.knora.org/ontology/incunabula/v2#page",
            "http://api.knora.org/ontology/incunabula/v2#description": {
                "@id": "http://data.knora.org/1baf691c8403/values/2882816d3a26",
                "@type": "http://api.knora.org/ontology/knora-api/v2#TextValue",
                "http://api.knora.org/ontology/knora-api/v2#valueAsString": "Beginn Kapitel 28.\nHolzschnitt zu Kap. 28: Vom NÃ¶rgeln an Gottes Werken.\nEin Narr, der auf einem Berg ein Feuer entfacht hat, hÃ¤lt seine Hand schÃ¼tzend Ã¼ber die Augen, wÃ¤hrend er seinen Blick auf die hell am Himmel strahlende Sonne richtet. 11.7 x 8.5 cm."
            },
            "http://www.w3.org/2000/01/rdf-schema#label": "e7r"
        },
        {
            "@id": "http://data.knora.org/1d0af69dc403",
            "@type": "http://api.knora.org/ontology/incunabula/v2#page",
            "http://api.knora.org/ontology/incunabula/v2#description": {
                "@id": "http://data.knora.org/1d0af69dc403/values/4e9dc2b53326",
                "@type": "http://api.knora.org/ontology/knora-api/v2#TextValue",
                "http://api.knora.org/ontology/knora-api/v2#valueAsString": "Beginn Kapitel 18.\nHolzschnitt zu Kap. 18: Vom Dienst an zwei Herren.\nEin mit Spiess bewaffneter Narr blÃ¤st in ein Horn. Sein Hund versucht derweil im Hintergrund zwei Hasen gleichzeitig zu erjagen, 11.6 x 8.4 cm.\n"
            },
            "http://www.w3.org/2000/01/rdf-schema#label": "d5r"
        },
        {
            "@id": "http://data.knora.org/1fa07c90ca03",
            "@type": "http://api.knora.org/ontology/incunabula/v2#page",
            "http://api.knora.org/ontology/incunabula/v2#description": {
                "@id": "http://data.knora.org/1fa07c90ca03/values/c623c1aa3c26",
                "@type": "http://api.knora.org/ontology/knora-api/v2#TextValue",
                "http://api.knora.org/ontology/knora-api/v2#valueAsString": "Beginn Kapitel 31.\nHolzschnitt zu Kap. 31: Vom Hinausschieben auf morgen.\nEin Narr steht mit ausgebreiteten Armen auf einer Strasse. Auf seinen HÃ¤nden sitzen zwei Raben, die beide â€žCrasâ€œ â€“ das lateinische Wort fÃ¼r â€žmorgenâ€œ â€“ rufen. Auf dem Kopf des Narren sitzt ein Papagei und ahmt den Ruf der KrÃ¤hen nach, 11.6 x 8.5 cm."
            },
            "http://www.w3.org/2000/01/rdf-schema#label": "f2r"
        },
        {
            "@id": "http://data.knora.org/1fdb76019003",
            "@type": "http://api.knora.org/ontology/incunabula/v2#page",
            "http://api.knora.org/ontology/incunabula/v2#description": {
                "@id": "http://data.knora.org/1fdb76019003/values/118a3f426d26",
                "@type": "http://api.knora.org/ontology/knora-api/v2#TextValue",
                "http://api.knora.org/ontology/knora-api/v2#valueAsString": "Beginn Kapitel 57.\nHolzschnitt zu Kap. 57: Von der Gnadenwahl Gottes.\nEin Narr, der auf einem Krebs reitet, stÃ¼tzt sich auf ein brechendes Schildrohr, das ihm die Hand  durchbohrt. Ein Vogel fliegt auf den offenen Mund des Narren zu.\n11.6 x 8.5 cm."
            },
            "http://www.w3.org/2000/01/rdf-schema#label": "i1r"
        },
        {
            "@id": "http://data.knora.org/21360383d003",
            "@type": "http://api.knora.org/ontology/incunabula/v2#page",
            "http://api.knora.org/ontology/incunabula/v2#description": {
                "@id": "http://data.knora.org/21360383d003/values/b630be944e26",
                "@type": "http://api.knora.org/ontology/knora-api/v2#TextValue",
                "http://api.knora.org/ontology/knora-api/v2#valueAsString": "Beginn Kapitel 45.\nHolzschnitt zu Kap. 45: Von selbstverschuldetem UnglÃ¼ck.\nIn Gestalt eines Narren springt Empedokles in den lodernden Krater des Ã„tna. Im Vordergrund lÃ¤sst sich ein anderer Narr in einen Brunnen fallen. Beide werden von drei MÃ¤nnern beobachtet, die das Verhalten mit â€žJn geschicht rechtâ€œ  kommentieren, 11.7 x 8.3 cm.\n"
            },
            "http://www.w3.org/2000/01/rdf-schema#label": "g7r"
        },
        {
            "@id": "http://data.knora.org/2171fdf39503",
            "@type": "http://api.knora.org/ontology/incunabula/v2#page",
            "http://api.knora.org/ontology/incunabula/v2#description": {
                "@id": "http://data.knora.org/2171fdf39503/values/59740ba27226",
                "@type": "http://api.knora.org/ontology/knora-api/v2#TextValue",
                "http://api.knora.org/ontology/knora-api/v2#valueAsString": "Beginn Kapitel 68.\nHolzschnitt zu Kap. 68: Keinen Scherz verstehen.\nEin Kind, das auf einem Steckenpferd reitet und mit einem Stock als Gerte umher fuchtelt, wird von einem Narren am rechten Rand ausgeschimpft. Ein anderer Narr, der neben dem Kind steht, ist dabei, sein Schwert aus der Scheide zu ziehen.\n11.7 x 8.5 cm."
            },
            "http://www.w3.org/2000/01/rdf-schema#label": "k6r"
        },
        {
            "@id": "http://data.knora.org/230784e69b03",
            "@type": "http://api.knora.org/ontology/incunabula/v2#page",
            "http://api.knora.org/ontology/incunabula/v2#description": {
                "@id": "http://data.knora.org/230784e69b03/values/4ba763247b26",
                "@type": "http://api.knora.org/ontology/knora-api/v2#TextValue",
                "http://api.knora.org/ontology/knora-api/v2#valueAsString": "Beginn Kapitel 83.\nneuer Holzschitt (nicht in Lemmer 1979): Vor einer HÃ¤userkulisse kniet ein Narr mit einem Beutel in der Linken und zwei Keulen in der Rechten vor einem Mann mit Hut und einem jÃ¼ngeren Begleiter, 11.6 x 8.6 cm."
            },
            "http://www.w3.org/2000/01/rdf-schema#label": "m3r"
        },
        {
            "@id": "http://data.knora.org/23427e576103",
            "@type": "http://api.knora.org/ontology/incunabula/v2#page",
            "http://api.knora.org/ontology/incunabula/v2#description": {
                "@id": "http://data.knora.org/23427e576103/values/c32d62198426",
                "@type": "http://api.knora.org/ontology/knora-api/v2#TextValue",
                "http://api.knora.org/ontology/knora-api/v2#valueAsString": "Beginn Kapitel 96.\nHolzschnitt zu Kap. 96: Schenken und hinterdrein bereuen.\nEin Narr, der vor einem Haus steht, Ã¼berreicht einem bÃ¤rtigen Alten ein Geschenk, kratzt sich dabei aber unschlÃ¼ssig am Kopf.\n11.6 x 8.3 cm.\nUnkoloriert.\nOben rechts Blattnummerierung (Graphitstift): \"128\"."
            },
            "http://www.w3.org/2000/01/rdf-schema#label": "q8r"
        },
        {
            "@id": "http://data.knora.org/23cc8975d603",
            "@type": "http://api.knora.org/ontology/incunabula/v2#page",
            "http://api.knora.org/ontology/incunabula/v2#description": {
                "@id": "http://data.knora.org/23cc8975d603/values/a63dbb7e6026",
                "@type": "http://api.knora.org/ontology/knora-api/v2#TextValue",
                "http://api.knora.org/ontology/knora-api/v2#valueAsString": "Beginn Kapitel 60.\nHolzschnitt zu Kap. 60: Von SelbstgefÃ¤lligkeit.\nEin alter Narr steht am Ofen und rÃ¼hrt in einem Topf. Gleichzeitig schaut er sich dabei in einem Handspiegel an.\n11.7 x 8.5 cm."
            },
            "http://www.w3.org/2000/01/rdf-schema#label": "i4r"
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
                    "http://www.w3.org/2000/01/rdf-schema#label": "Zeitglöcklein des Lebens und Leidens Christi"
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
            "http://www.w3.org/2000/01/rdf-schema#label": "a1r, Titelblatt"
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
                    "http://www.w3.org/2000/01/rdf-schema#label": "Zeitglöcklein des Lebens und Leidens Christi"
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
            "http://www.w3.org/2000/01/rdf-schema#label": "a1v, Titelblatt, RÃ¼ckseite"
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
                    "http://www.w3.org/2000/01/rdf-schema#label": "ZeitglÃ¶cklein des Lebens und Leidens Christi"
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
            "http://www.w3.org/2000/01/rdf-schema#label": "a2r"
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
                    "http://www.w3.org/2000/01/rdf-schema#label": "ZeitglÃ¶cklein des Lebens und Leidens Christi"
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
            "http://www.w3.org/2000/01/rdf-schema#label": "a2v"
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
                    "http://www.w3.org/2000/01/rdf-schema#label": "ZeitglÃ¶cklein des Lebens und Leidens Christi"
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
            "http://www.w3.org/2000/01/rdf-schema#label": "a3r"
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
                    "http://www.w3.org/2000/01/rdf-schema#label": "ZeitglÃ¶cklein des Lebens und Leidens Christi"
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
            "http://www.w3.org/2000/01/rdf-schema#label": "a3v"
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
                    "http://www.w3.org/2000/01/rdf-schema#label": "ZeitglÃ¶cklein des Lebens und Leidens Christi"
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
            "http://www.w3.org/2000/01/rdf-schema#label": "a4r"
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
                    "http://www.w3.org/2000/01/rdf-schema#label": "ZeitglÃ¶cklein des Lebens und Leidens Christi"
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
            "http://www.w3.org/2000/01/rdf-schema#label": "a4v"
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
                    "http://www.w3.org/2000/01/rdf-schema#label": "ZeitglÃ¶cklein des Lebens und Leidens Christi"
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
            "http://www.w3.org/2000/01/rdf-schema#label": "a5r"
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
                    "http://www.w3.org/2000/01/rdf-schema#label": "ZeitglÃ¶cklein des Lebens und Leidens Christi"
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
            "http://www.w3.org/2000/01/rdf-schema#label": "a5v"
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
                    "http://www.w3.org/2000/01/rdf-schema#label": "ZeitglÃ¶cklein des Lebens und Leidens Christi"
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
            "http://www.w3.org/2000/01/rdf-schema#label": "a6r"
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
                    "http://www.w3.org/2000/01/rdf-schema#label": "ZeitglÃ¶cklein des Lebens und Leidens Christi"
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
            "http://www.w3.org/2000/01/rdf-schema#label": "a6v"
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
                    "http://www.w3.org/2000/01/rdf-schema#label": "ZeitglÃ¶cklein des Lebens und Leidens Christi"
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
            "http://www.w3.org/2000/01/rdf-schema#label": "a7r"
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
                    "http://www.w3.org/2000/01/rdf-schema#label": "ZeitglÃ¶cklein des Lebens und Leidens Christi"
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
            "http://www.w3.org/2000/01/rdf-schema#label": "a7v"
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
                    "http://www.w3.org/2000/01/rdf-schema#label": "ZeitglÃ¶cklein des Lebens und Leidens Christi"
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
            "http://www.w3.org/2000/01/rdf-schema#label": "a8r"
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
                    "http://www.w3.org/2000/01/rdf-schema#label": "ZeitglÃ¶cklein des Lebens und Leidens Christi"
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
            "http://www.w3.org/2000/01/rdf-schema#label": "a8v"
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
                    "http://www.w3.org/2000/01/rdf-schema#label": "ZeitglÃ¶cklein des Lebens und Leidens Christi"
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
            "http://www.w3.org/2000/01/rdf-schema#label": "b1r"
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
                    "http://www.w3.org/2000/01/rdf-schema#label": "ZeitglÃ¶cklein des Lebens und Leidens Christi"
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
            "http://www.w3.org/2000/01/rdf-schema#label": "b1v"
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
                    "http://www.w3.org/2000/01/rdf-schema#label": "ZeitglÃ¶cklein des Lebens und Leidens Christi"
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
            "http://www.w3.org/2000/01/rdf-schema#label": "b2r"
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
                    "http://www.w3.org/2000/01/rdf-schema#label": "ZeitglÃ¶cklein des Lebens und Leidens Christi"
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
            "http://www.w3.org/2000/01/rdf-schema#label": "b2v"
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
                    "http://www.w3.org/2000/01/rdf-schema#label": "ZeitglÃ¶cklein des Lebens und Leidens Christi"
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
            "http://www.w3.org/2000/01/rdf-schema#label": "b3r"
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
                    "http://www.w3.org/2000/01/rdf-schema#label": "ZeitglÃ¶cklein des Lebens und Leidens Christi"
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
            "http://www.w3.org/2000/01/rdf-schema#label": "b3v"
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
                    "http://www.w3.org/2000/01/rdf-schema#label": "ZeitglÃ¶cklein des Lebens und Leidens Christi"
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
            "http://www.w3.org/2000/01/rdf-schema#label": "b4r"
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
                    "http://www.w3.org/2000/01/rdf-schema#label": "ZeitglÃ¶cklein des Lebens und Leidens Christi"
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
            "http://www.w3.org/2000/01/rdf-schema#label": "b4v"
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
                    "http://www.w3.org/2000/01/rdf-schema#label": "ZeitglÃ¶cklein des Lebens und Leidens Christi"
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
            "http://www.w3.org/2000/01/rdf-schema#label": "b5r"
        }
    ],
    "http://schema.org/numberOfItems": 25
};

// Euler letter L176-O
const EulerLetter: ResourcesSequence = {
    "@type": "http://schema.org/ItemList",
    "http://schema.org/itemListElement": {
        "@id": "http://rdfh.ch/0801/beol/-0tI3HXgSSOeDtkf-SA00w",
        "@type": "http://0.0.0.0:3333/ontology/0801/beol/v2#letter",
        "http://0.0.0.0:3333/ontology/0801/beol/v2#creationDate": {
            "@id": "http://rdfh.ch/0801/beol/-0tI3HXgSSOeDtkf-SA00w/values/w3ZlkuU2T0-0DMrvW8HLJA",
            "@type": "http://api.knora.org/ontology/knora-api/v2#DateValue",
            "http://api.knora.org/ontology/knora-api/v2#dateValueHasCalendar": "GREGORIAN",
            "http://api.knora.org/ontology/knora-api/v2#dateValueHasEndDay": 3,
            "http://api.knora.org/ontology/knora-api/v2#dateValueHasEndEra": "CE",
            "http://api.knora.org/ontology/knora-api/v2#dateValueHasEndMonth": 1,
            "http://api.knora.org/ontology/knora-api/v2#dateValueHasEndYear": 1756,
            "http://api.knora.org/ontology/knora-api/v2#dateValueHasStartDay": 3,
            "http://api.knora.org/ontology/knora-api/v2#dateValueHasStartEra": "CE",
            "http://api.knora.org/ontology/knora-api/v2#dateValueHasStartMonth": 1,
            "http://api.knora.org/ontology/knora-api/v2#dateValueHasStartYear": 1756,
            "http://api.knora.org/ontology/knora-api/v2#valueAsString": "GREGORIAN:1756-01-03 CE"
        },
        "http://0.0.0.0:3333/ontology/0801/beol/v2#hasAuthorValue": {
            "@id": "http://rdfh.ch/0801/beol/-0tI3HXgSSOeDtkf-SA00w/values/-dzjDkhPRzy-9q0v7QtI7w",
            "@type": "http://api.knora.org/ontology/knora-api/v2#LinkValue",
            "http://api.knora.org/ontology/knora-api/v2#linkValueHasTarget": {
                "@id": "http://rdfh.ch/biblio/QNWEqmjxQ9W-_hTwKlKP-Q",
                "@type": "http://0.0.0.0:3333/ontology/0801/beol/v2#person",
                "http://www.w3.org/2000/01/rdf-schema#label": "Leonhard Euler"
            }
        },
        "http://0.0.0.0:3333/ontology/0801/beol/v2#hasRecipientValue": {
            "@id": "http://rdfh.ch/0801/beol/-0tI3HXgSSOeDtkf-SA00w/values/gkdrd8ZXQhucrNLXIf2-qw",
            "@type": "http://api.knora.org/ontology/knora-api/v2#LinkValue",
            "http://api.knora.org/ontology/knora-api/v2#linkValueHasTarget": {
                "@id": "http://rdfh.ch/biblio/Yv2elBDtSMqoJeKRcxsW8A",
                "@type": "http://0.0.0.0:3333/ontology/0801/beol/v2#person",
                "http://www.w3.org/2000/01/rdf-schema#label": "Christian Goldbach"
            }
        },
        "http://0.0.0.0:3333/ontology/0801/beol/v2#hasSubject": [
            {
                "@id": "http://rdfh.ch/0801/beol/-0tI3HXgSSOeDtkf-SA00w/values/JpGklfqZSxuu7VI1zIyucw",
                "@type": "http://api.knora.org/ontology/knora-api/v2#ListValue",
                "http://api.knora.org/ontology/knora-api/v2#listValueAsListNode": {
                    "@id": "http://rdfh.ch/lists/0801/other_quadratic_forms"
                },
                "http://api.knora.org/ontology/knora-api/v2#listValueAsListNodeLabel": "Other quadratic forms"
            },
            {
                "@id": "http://rdfh.ch/0801/beol/-0tI3HXgSSOeDtkf-SA00w/values/SiEBL-EASQSNeFBprbjY3A",
                "@type": "http://api.knora.org/ontology/knora-api/v2#ListValue",
                "http://api.knora.org/ontology/knora-api/v2#listValueAsListNode": {
                    "@id": "http://rdfh.ch/lists/0801/berlin_academy"
                },
                "http://api.knora.org/ontology/knora-api/v2#listValueAsListNodeLabel": "Berlin Academy"
            },
            {
                "@id": "http://rdfh.ch/0801/beol/-0tI3HXgSSOeDtkf-SA00w/values/H4IZDQ1eS2WBrMhZRX-vkA",
                "@type": "http://api.knora.org/ontology/knora-api/v2#ListValue",
                "http://api.knora.org/ontology/knora-api/v2#listValueAsListNode": {
                    "@id": "http://rdfh.ch/lists/0801/other_professional_tasks"
                },
                "http://api.knora.org/ontology/knora-api/v2#listValueAsListNodeLabel": "Other professional tasks"
            },
            {
                "@id": "http://rdfh.ch/0801/beol/-0tI3HXgSSOeDtkf-SA00w/values/MdjXlOTNRfqYKEWNIh6kfg",
                "@type": "http://api.knora.org/ontology/knora-api/v2#ListValue",
                "http://api.knora.org/ontology/knora-api/v2#listValueAsListNode": {
                    "@id": "http://rdfh.ch/lists/0801/errands"
                },
                "http://api.knora.org/ontology/knora-api/v2#listValueAsListNodeLabel": "Errands"
            },
            {
                "@id": "http://rdfh.ch/0801/beol/-0tI3HXgSSOeDtkf-SA00w/values/rfao0tqnQTiYyLdlJrNF6w",
                "@type": "http://api.knora.org/ontology/knora-api/v2#ListValue",
                "http://api.knora.org/ontology/knora-api/v2#listValueAsListNode": {
                    "@id": "http://rdfh.ch/lists/0801/book_trade_orders"
                },
                "http://api.knora.org/ontology/knora-api/v2#listValueAsListNodeLabel": "Book trade, orders"
            },
            {
                "@id": "http://rdfh.ch/0801/beol/-0tI3HXgSSOeDtkf-SA00w/values/_fCIiKjzRYqjXkEhykto5A",
                "@type": "http://api.knora.org/ontology/knora-api/v2#ListValue",
                "http://api.knora.org/ontology/knora-api/v2#listValueAsListNode": {
                    "@id": "http://rdfh.ch/lists/0801/johann_albrecht_euler"
                },
                "http://api.knora.org/ontology/knora-api/v2#listValueAsListNodeLabel": "Johann Albrecht Euler"
            }
        ],
        "http://0.0.0.0:3333/ontology/0801/beol/v2#hasText": {
            "@id": "http://rdfh.ch/0801/beol/-0tI3HXgSSOeDtkf-SA00w/values/GJQB9IHYTl2RhG4g4ru0YA",
            "@type": "http://api.knora.org/ontology/knora-api/v2#TextValue",
            "http://api.knora.org/ontology/knora-api/v2#textValueAsHtml": "<div>\n   <div id=\"transcription\">\n      \n      <p>Hochwohlgebohrner Herr</p>\n      \n      <p>Hochgeehrtester Herr <em>Etats</em> Rath\n      </p>\n      \n      <p>Bey dem Antritt dieses neuen Jahrs lege ich zuvorderst meinen herzlichsten Wunsch\n         für das beständige Wohlseyn Eur. Hochwohlgeb. ab, und empfehle mich dabey gehorsamst\n         sammt den meinigen zu Dero fortdaurenden Wohlgewogenheit<span class=\"math\">\\(\\,.\\,\\)</span> Zugleich statte ich auch Eur. Hochwohlgeb. meine verpflichtetste Danksagung ab für\n         den gütigen Antheil welchen Dieselben an unserem Zustand zu nehmen belieben und habe\n         das Vergnügen Eur. Hochwohlgeb. zu berichten, daß&nbsp;S[ein]<span class=\"math\">\\(\\,{}^{\\text{e}}\\,\\)</span> Königl[iche] <em>Majestät<a class=\"salsah-link\" href=\"http://rdfh.ch/biblio/DhgjcrRhRfunaSt77-bUxg\"></a></em> bey dem Anfang dieses Jahrs Dero Pathen unsern ältesten Sohn mit einer jährlichen\n         Besoldung von <a class=\"salsah-link\" href=\"http://rdfh.ch/biblio/bP1CO3j3TCOUHYdQqKw9pA\"></a><span class=\"math\">\\(\\,200\\,\\)</span> Rthl. begnadiget.<a class=\"salsah-link\" href=\"http://rdfh.ch/0801/beol/spy5H95GTV2RElphXFPbbw\"><sup>1</sup></a></p>\n      \n      <p>Ich habe nun schon eine geraume Zeit so viel andere Geschäfte gehabt daß&nbsp;ich an <em>numeri</em>sche <em>Theoremata</em>, dergleichen ich Eur. Hochwohlgeb. das letste mal vorzulegen die Ehre gehabt, nicht\n         habe denken können. Die <em>Partes Matheseos applicatae</em> nehmen mir die meiste Zeit weg, wo es immer mehr zu untersuchen gibt, je mehr man\n         damit umgeht.<a class=\"salsah-link\" href=\"http://rdfh.ch/0801/beol/KvfXRPkXTziMYMVYSz9tBg\"><sup>2</sup></a></p>\n      \n      <p>Weil nun mein Kopf mit so viel anderen Sachen angefüllet ist, so mag das wohl die\n         Ursache seyn, daß&nbsp;ich mich in das von Eur. Hochwohlgeb. <em>communicir</em>te und nach der Hand verbesserte <em>Theorema</em> nicht finden kan. Vielleicht haben Eur. Hochwohlgeb. vergessen noch eine wesentliche\n         <em>Condition</em> hinzuzusetzen.<a class=\"salsah-link\" href=\"http://rdfh.ch/0801/beol/FJCOlKBdRtW8caqnN4A3Vw\"><sup>3</sup></a></p>\n      \n      <p>Das <em>Theorema</em> war: <em>Si sit</em><span class=\"math\">\\(\\,aa+bb=P^{2}+eQ^{2}\\,\\)</span><em>erit etiam</em></p>\n      \n      <p>\n         <span class=\"math\">\\(\\,a^{2}+\\left(\\left(2e+1\\right)b-eP-eQ\\right)^{2}=M^{2}+eN^{2}\\text{;}\\,\\)</span>\n         \n      </p>\n      \n      <p>weil ich den Grund desselben nicht einsehen konnte, so habe ich die Richtigkeit desselben\n         durch <em>Exempel</em> erforschen wollen.\n      </p>\n      \n      <p>I. Da <span class=\"math\">\\(\\,1^{2}+4^{2}=17=3^{2}+2\\cdot 2^{2}\\,\\)</span>, so ist <span class=\"math\">\\(\\,a=1\\,\\)</span>, <span class=\"math\">\\(\\,b=4\\,\\)</span>, <span class=\"math\">\\(\\,P=3\\,\\)</span>, <span class=\"math\">\\(\\,Q=2\\,\\)</span> und <span class=\"math\">\\(\\,e=2\\,\\)</span>, allso müste seyn\n      </p>\n      \n      <p>\n         <span class=\"math\">\\(\\,1^{2}+\\left(5\\cdot 4-2\\cdot 3-2\\cdot 2\\right)^{2}=1^{2}+10^{2}=101=M^{2}+2N^{2}\\,\\)</span>\n         \n      </p>\n      \n      <p>welches unmöglich ist.</p>\n      \n      <p>II. Da <span class=\"math\">\\(\\,9^{2}+4^{2}=97=7^{2}+3\\cdot 4^{2}\\,\\)</span>, so ist <span class=\"math\">\\(\\,a=9\\,\\)</span>; <span class=\"math\">\\(\\,b=4\\,\\)</span>; <span class=\"math\">\\(\\,P=7\\,\\)</span>; <span class=\"math\">\\(\\,Q=4\\,\\)</span> und <span class=\"math\">\\(\\,e=3\\,\\)</span>, allso müsste seyn\n      </p>\n      \n      <p>\n         <span class=\"math\">\\(\\,9^{2}+\\left(7\\cdot 4-3\\cdot 7-3\\cdot 4\\right)^{2}=9^{2}+5^{2}=106=M^{2}+3N^{2}\\,\\)</span>\n         \n      </p>\n      \n      <p>welches ebenfalls unmöglich ist.</p>\n      \n      <p>Da ich nun nicht einmal ein <em>Exempel</em> finden kan, welches einträfe, so schliesse ich daraus, daß&nbsp;eine gewisse Bedingung\n         in den Zahlen <span class=\"math\">\\(\\,a\\,\\)</span>, <span class=\"math\">\\(\\,b\\,\\)</span>, <span class=\"math\">\\(\\,P\\,\\)</span> und <span class=\"math\">\\(\\,Q\\,\\)</span> müsse weggelassen seyn, welche ich aber nicht ausfündig machen kan.<a class=\"salsah-link\" href=\"http://rdfh.ch/0801/beol/kZeMXLrQTQONISqizXtf5g\"><sup>4</sup></a></p>\n      \n      <p>Ich habe dem H. <em>Spener<a class=\"salsah-link\" href=\"http://rdfh.ch/biblio/Z_-TT-8_QNSRv-O7dKCW0w\"></a></em> zu wissen gethan, daß&nbsp;Eur. Hochwohlgeb. die Rechnung für die überschickten Bücher\n         verlangen; bekomme ich dieselbe vor Schliessung dieses Briefs, wie ich ihm habe sagen\n         lassen, so werde ich sie beylegen.<a class=\"salsah-link\" href=\"http://rdfh.ch/0801/beol/08Y_rCK5QM-gvchjtixomw\"><sup>5</sup></a></p>\n      \n      <p>Sonsten da er nicht alle verlangte Bücher gehabt, so werde ich inskünftige dergleichen\n         <em>Commission</em>en dem <em>M.<span class=\"math\">\\(\\,{}^{\\text{r}}\\,\\)</span>Neaulme<a class=\"salsah-link\" href=\"http://rdfh.ch/biblio/FsJNrctNTMuwJPCX-7OTVg\"></a></em>, welcher weit <em>activer</em> ist und alles schaffen kan, auftragen. Wegen des Werks: <em>La Clef du Cabinet des Princes<a class=\"salsah-link\" href=\"http://rdfh.ch/biblio/up0Q0ZzPSLaULC2tlTs1sA\"></a><a class=\"salsah-link\" href=\"http://rdfh.ch/biblio/sAImr-uGRBGpsdBdoI6XCw\"></a></em> füge hier die Antwort des <em>M.<span class=\"math\">\\(\\,{}^{\\text{r}}\\,\\)</span>de Bourdeaux<a class=\"salsah-link\" href=\"http://rdfh.ch/biblio/vR3fWAXxRqShBZvWKVA9tA\"></a></em> bey.<a class=\"salsah-link\" href=\"http://rdfh.ch/0801/beol/nRO3f9ENSsqTH8S0Z1uO9w\"><sup>6</sup></a></p>\n      \n      <p>Sollte dasselbe vor der Ankunft einer <em>Resolution</em> von Eur. Hochwohlgeb. schon verkauft worden seyn, so hat sich <em>M.<span class=\"math\">\\(\\,{}^{\\text{r}}\\,\\)</span></em><em>Neaulme<a class=\"salsah-link\" href=\"http://rdfh.ch/biblio/FsJNrctNTMuwJPCX-7OTVg\"></a></em> anheischig gemacht, dasselbe auch zu liefern.\n      </p>\n      \n      <p>Ich habe die Ehre mit der schuldigsten Hochachtung zu verharren</p>\n      \n      <p>Eur. Hochwohlgebohrnen</p>\n      \n      <p>gehorsamster Diener</p>\n      \n      <p>\n         <em>L. Euler</em>\n         \n      </p>\n      \n      <p><em>Berlin</em> den 3<span class=\"math\">\\(\\,{}^{\\text{ten}}\\,\\)</span><em>Januarii</em></p>\n      \n      <p>1756.</p>\n      \n      <p>\n         <sub>Berlin, January 3rd, 1756</sub>\n         \n      </p>\n      \n      <p>\n         <sub>Original, 1 fol. – RGADA, f. 181, n. 1413, č. V, fol. 123rv</sub>\n         \n      </p>\n      \n      <p>\n         <sub>Published: <em>Correspondance</em> (1843), t. I, p. 636–637; <em>Euler-Goldbach</em> (1965), p. 385–386</sub>\n         \n      </p>\n      \n   </div>\n   <div id=\"references\">\n      <ol></ol>\n   </div>\n</div>"
        },
        "http://0.0.0.0:3333/ontology/0801/beol/v2#letterHasLanguage": {
            "@id": "http://rdfh.ch/0801/beol/-0tI3HXgSSOeDtkf-SA00w/values/ilKXhfZnS9uAYDNQzi6m8Q",
            "@type": "http://api.knora.org/ontology/knora-api/v2#TextValue",
            "http://api.knora.org/ontology/knora-api/v2#valueAsString": "German"
        },
        "http://0.0.0.0:3333/ontology/0801/beol/v2#letterHasNumber": {
            "@id": "http://rdfh.ch/0801/beol/-0tI3HXgSSOeDtkf-SA00w/values/hS_gqlOtQsCqA-gqrgIHCQ",
            "@type": "http://api.knora.org/ontology/knora-api/v2#TextValue",
            "http://api.knora.org/ontology/knora-api/v2#valueAsString": "176"
        },
        "http://0.0.0.0:3333/ontology/0801/beol/v2#letterHasOriginalValue": {
            "@id": "http://rdfh.ch/0801/beol/-0tI3HXgSSOeDtkf-SA00w/values/veu6BVjbTcKFPAEmcYRjXQ",
            "@type": "http://api.knora.org/ontology/knora-api/v2#LinkValue",
            "http://api.knora.org/ontology/knora-api/v2#linkValueHasTarget": {
                "@id": "http://rdfh.ch/0801/beol/1PUuT3mcRGmeunDS3ZNyOA",
                "@type": "http://0.0.0.0:3333/ontology/0801/beol/v2#manuscript",
                "http://www.w3.org/2000/01/rdf-schema#label": "L176 Original Manuscript"
            }
        },
        "http://0.0.0.0:3333/ontology/0801/beol/v2#letterHasRepertoriumNumber": {
            "@id": "http://rdfh.ch/0801/beol/-0tI3HXgSSOeDtkf-SA00w/values/na-JeCAxSXyYjpx0Cn-qrA",
            "@type": "http://api.knora.org/ontology/knora-api/v2#TextValue",
            "http://api.knora.org/ontology/knora-api/v2#valueAsString": "890"
        },
        "http://0.0.0.0:3333/ontology/0801/beol/v2#letterHasTranslationValue": {
            "@id": "http://rdfh.ch/0801/beol/-0tI3HXgSSOeDtkf-SA00w/values/_iqsIP5NSLiBE8hPnDwAKw",
            "@type": "http://api.knora.org/ontology/knora-api/v2#LinkValue",
            "http://api.knora.org/ontology/knora-api/v2#linkValueHasTarget": {
                "@id": "http://rdfh.ch/0801/beol/yBr2EjBMTkeCeVmGPfjCtw",
                "@type": "http://0.0.0.0:3333/ontology/0801/beol/v2#letter",
                "http://www.w3.org/2000/01/rdf-schema#label": "L176-T"
            }
        },
        "http://0.0.0.0:3333/ontology/0801/beol/v2#letterIsPublishedValue": [
            {
                "@id": "http://rdfh.ch/0801/beol/-0tI3HXgSSOeDtkf-SA00w/values/gOwn9vzMT-2XHkgZe42fTA",
                "@type": "http://api.knora.org/ontology/knora-api/v2#LinkValue",
                "http://api.knora.org/ontology/knora-api/v2#linkValueHasTarget": {
                    "@id": "http://rdfh.ch/0801/beol/79Rz6MaAQESBalJeWl2TVA",
                    "@type": "http://0.0.0.0:3333/ontology/0801/beol/v2#publishedLetter",
                    "http://www.w3.org/2000/01/rdf-schema#label": "L176-O published in Fuß edition"
                }
            },
            {
                "@id": "http://rdfh.ch/0801/beol/-0tI3HXgSSOeDtkf-SA00w/values/5-CVslP4R9KY1_d3d83qMw",
                "@type": "http://api.knora.org/ontology/knora-api/v2#LinkValue",
                "http://api.knora.org/ontology/knora-api/v2#linkValueHasTarget": {
                    "@id": "http://rdfh.ch/0801/beol/pFN8MlXBSYaB7oZRxUpV0Q",
                    "@type": "http://0.0.0.0:3333/ontology/0801/beol/v2#publishedLetter",
                    "http://www.w3.org/2000/01/rdf-schema#label": "L176-O published in Yushkevich-Winter edition"
                }
            }
        ],
        "http://0.0.0.0:3333/ontology/0801/beol/v2#letterIsReplyToValue": [
            {
                "@id": "http://rdfh.ch/0801/beol/-0tI3HXgSSOeDtkf-SA00w/values/iW0aQbZ2Qaeigqr5RN-Hlg",
                "@type": "http://api.knora.org/ontology/knora-api/v2#LinkValue",
                "http://api.knora.org/ontology/knora-api/v2#linkValueHasTarget": {
                    "@id": "http://rdfh.ch/0801/beol/_iz5PE4rT8KNfbmfW8DxrQ",
                    "@type": "http://0.0.0.0:3333/ontology/0801/beol/v2#letter",
                    "http://www.w3.org/2000/01/rdf-schema#label": "L174-O"
                }
            },
            {
                "@id": "http://rdfh.ch/0801/beol/-0tI3HXgSSOeDtkf-SA00w/values/utSZPRdcSjC78DHtGBVZ3Q",
                "@type": "http://api.knora.org/ontology/knora-api/v2#LinkValue",
                "http://api.knora.org/ontology/knora-api/v2#linkValueHasTarget": {
                    "@id": "http://rdfh.ch/0801/beol/f5a92GjJRriC1GTY_NzfvQ",
                    "@type": "http://0.0.0.0:3333/ontology/0801/beol/v2#letter",
                    "http://www.w3.org/2000/01/rdf-schema#label": "L175-O"
                }
            }
        ],
        "http://0.0.0.0:3333/ontology/0801/beol/v2#location": {
            "@id": "http://rdfh.ch/0801/beol/-0tI3HXgSSOeDtkf-SA00w/values/xpStwEfxSdGH14StZ_KWHQ",
            "@type": "http://api.knora.org/ontology/knora-api/v2#TextValue",
            "http://api.knora.org/ontology/knora-api/v2#valueAsString": "Berlin"
        },
        "http://0.0.0.0:3333/ontology/0801/beol/v2#title": {
            "@id": "http://rdfh.ch/0801/beol/-0tI3HXgSSOeDtkf-SA00w/values/rqHQuCoeRAmfUElMDuEfFg",
            "@type": "http://api.knora.org/ontology/knora-api/v2#TextValue",
            "http://api.knora.org/ontology/knora-api/v2#valueAsString": " Euler to Goldbach, January 3rd, 1756"
        },
        "http://api.knora.org/ontology/knora-api/v2#hasStandoffLinkToValue": [
            {
                "@id": "http://rdfh.ch/0801/beol/-0tI3HXgSSOeDtkf-SA00w/values/fsxYuF6BSl6opUqcetx4Zg",
                "@type": "http://api.knora.org/ontology/knora-api/v2#LinkValue",
                "http://api.knora.org/ontology/knora-api/v2#linkValueHasTarget": {
                    "@id": "http://rdfh.ch/0801/beol/KvfXRPkXTziMYMVYSz9tBg",
                    "@type": "http://0.0.0.0:3333/ontology/0801/beol/v2#endnote",
                    "http://www.w3.org/2000/01/rdf-schema#label": "L176 note-2"
                }
            },
            {
                "@id": "http://rdfh.ch/0801/beol/-0tI3HXgSSOeDtkf-SA00w/values/aMwl67I7RGa5Dvk-lczTdQ",
                "@type": "http://api.knora.org/ontology/knora-api/v2#LinkValue",
                "http://api.knora.org/ontology/knora-api/v2#linkValueHasTarget": {
                    "@id": "http://rdfh.ch/biblio/up0Q0ZzPSLaULC2tlTs1sA",
                    "@type": "http://0.0.0.0:3333/ontology/0801/beol/v2#person",
                    "http://www.w3.org/2000/01/rdf-schema#label": "Claude Jordan"
                }
            },
            {
                "@id": "http://rdfh.ch/0801/beol/-0tI3HXgSSOeDtkf-SA00w/values/1F3yY5ywSfS8n8U6RYE12w",
                "@type": "http://api.knora.org/ontology/knora-api/v2#LinkValue",
                "http://api.knora.org/ontology/knora-api/v2#linkValueHasTarget": {
                    "@id": "http://rdfh.ch/biblio/Z_-TT-8_QNSRv-O7dKCW0w",
                    "@type": "http://0.0.0.0:3333/ontology/0801/beol/v2#person",
                    "http://www.w3.org/2000/01/rdf-schema#label": "Johann Carl (the Elder) Spener"
                }
            },
            {
                "@id": "http://rdfh.ch/0801/beol/-0tI3HXgSSOeDtkf-SA00w/values/NIpFHSbKSe-BDC8RUmuhqg",
                "@type": "http://api.knora.org/ontology/knora-api/v2#LinkValue",
                "http://api.knora.org/ontology/knora-api/v2#linkValueHasTarget": {
                    "@id": "http://rdfh.ch/0801/beol/nRO3f9ENSsqTH8S0Z1uO9w",
                    "@type": "http://0.0.0.0:3333/ontology/0801/beol/v2#endnote",
                    "http://www.w3.org/2000/01/rdf-schema#label": "L176 note-6"
                }
            },
            {
                "@id": "http://rdfh.ch/0801/beol/-0tI3HXgSSOeDtkf-SA00w/values/wu7kCf2CR-e2aAvT8Q1DhQ",
                "@type": "http://api.knora.org/ontology/knora-api/v2#LinkValue",
                "http://api.knora.org/ontology/knora-api/v2#linkValueHasTarget": {
                    "@id": "http://rdfh.ch/0801/beol/spy5H95GTV2RElphXFPbbw",
                    "@type": "http://0.0.0.0:3333/ontology/0801/beol/v2#endnote",
                    "http://www.w3.org/2000/01/rdf-schema#label": "L176 note-1"
                }
            },
            {
                "@id": "http://rdfh.ch/0801/beol/-0tI3HXgSSOeDtkf-SA00w/values/YQRbSoBGR_-QrpHB_o0ReQ",
                "@type": "http://api.knora.org/ontology/knora-api/v2#LinkValue",
                "http://api.knora.org/ontology/knora-api/v2#linkValueHasTarget": {
                    "@id": "http://rdfh.ch/biblio/FsJNrctNTMuwJPCX-7OTVg",
                    "@type": "http://0.0.0.0:3333/ontology/0801/beol/v2#person",
                    "http://www.w3.org/2000/01/rdf-schema#label": "Jean Neaulme"
                }
            },
            {
                "@id": "http://rdfh.ch/0801/beol/-0tI3HXgSSOeDtkf-SA00w/values/mEhOhC8STn6t816gMUMLUQ",
                "@type": "http://api.knora.org/ontology/knora-api/v2#LinkValue",
                "http://api.knora.org/ontology/knora-api/v2#linkValueHasTarget": {
                    "@id": "http://rdfh.ch/0801/beol/08Y_rCK5QM-gvchjtixomw",
                    "@type": "http://0.0.0.0:3333/ontology/0801/beol/v2#endnote",
                    "http://www.w3.org/2000/01/rdf-schema#label": "L176 note-5"
                }
            },
            {
                "@id": "http://rdfh.ch/0801/beol/-0tI3HXgSSOeDtkf-SA00w/values/IG_gpA_vQBW-8hBbP1y0LQ",
                "@type": "http://api.knora.org/ontology/knora-api/v2#LinkValue",
                "http://api.knora.org/ontology/knora-api/v2#linkValueHasTarget": {
                    "@id": "http://rdfh.ch/biblio/DhgjcrRhRfunaSt77-bUxg",
                    "@type": "http://0.0.0.0:3333/ontology/0801/beol/v2#person",
                    "http://www.w3.org/2000/01/rdf-schema#label": "Friedrich II."
                }
            },
            {
                "@id": "http://rdfh.ch/0801/beol/-0tI3HXgSSOeDtkf-SA00w/values/veG9DY7pR46P62Ylvm7Nzw",
                "@type": "http://api.knora.org/ontology/knora-api/v2#LinkValue",
                "http://api.knora.org/ontology/knora-api/v2#linkValueHasTarget": {
                    "@id": "http://rdfh.ch/biblio/bP1CO3j3TCOUHYdQqKw9pA",
                    "@type": "http://0.0.0.0:3333/ontology/0801/beol/v2#person",
                    "http://www.w3.org/2000/01/rdf-schema#label": "Johann Albrecht Euler"
                }
            },
            {
                "@id": "http://rdfh.ch/0801/beol/-0tI3HXgSSOeDtkf-SA00w/values/udl1maFWTUu1MLcla4bm1g",
                "@type": "http://api.knora.org/ontology/knora-api/v2#LinkValue",
                "http://api.knora.org/ontology/knora-api/v2#linkValueHasTarget": {
                    "@id": "http://rdfh.ch/0801/beol/kZeMXLrQTQONISqizXtf5g",
                    "@type": "http://0.0.0.0:3333/ontology/0801/beol/v2#endnote",
                    "http://www.w3.org/2000/01/rdf-schema#label": "L176 note-4"
                }
            },
            {
                "@id": "http://rdfh.ch/0801/beol/-0tI3HXgSSOeDtkf-SA00w/values/uwc5JnP4R36elNlokOTX7A",
                "@type": "http://api.knora.org/ontology/knora-api/v2#LinkValue",
                "http://api.knora.org/ontology/knora-api/v2#linkValueHasTarget": {
                    "@id": "http://rdfh.ch/biblio/vR3fWAXxRqShBZvWKVA9tA",
                    "@type": "http://0.0.0.0:3333/ontology/0801/beol/v2#person",
                    "http://www.w3.org/2000/01/rdf-schema#label": "Etienne de Bourdeaux"
                }
            },
            {
                "@id": "http://rdfh.ch/0801/beol/-0tI3HXgSSOeDtkf-SA00w/values/dyhBkbFrQBSRevFJGMUfwQ",
                "@type": "http://api.knora.org/ontology/knora-api/v2#LinkValue",
                "http://api.knora.org/ontology/knora-api/v2#linkValueHasTarget": {
                    "@id": "http://rdfh.ch/biblio/sAImr-uGRBGpsdBdoI6XCw",
                    "@type": "http://0.0.0.0:3333/ontology/0802/biblio/v2#Book",
                    "http://www.w3.org/2000/01/rdf-schema#label": "jordanclbhae"
                }
            },
            {
                "@id": "http://rdfh.ch/0801/beol/-0tI3HXgSSOeDtkf-SA00w/values/6ry3IMMKRNq9x29EiAhYTw",
                "@type": "http://api.knora.org/ontology/knora-api/v2#LinkValue",
                "http://api.knora.org/ontology/knora-api/v2#linkValueHasTarget": {
                    "@id": "http://rdfh.ch/0801/beol/FJCOlKBdRtW8caqnN4A3Vw",
                    "@type": "http://0.0.0.0:3333/ontology/0801/beol/v2#endnote",
                    "http://www.w3.org/2000/01/rdf-schema#label": "L176 note-3"
                }
            }
        ],
        "http://www.w3.org/2000/01/rdf-schema#label": "L176-O"
    },
    "http://schema.org/numberOfItems": 1
}