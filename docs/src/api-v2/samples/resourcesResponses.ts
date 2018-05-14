import {ResourcesResponse} from "../ResourcesResponse";
import ApiV2WithValueObjects = ResourcesResponse.ApiV2WithValueObjects;
import ApiV2Simple = ResourcesResponse.ApiV2Simple

// http://localhost:3333/v2/resources/http%3A%2F%2Frdfh.ch%2Fc5058f3a
const Zeitgloecklein: ApiV2WithValueObjects.ResourcesSequence = {
    "@type": "http://schema.org/ItemList",
    "http://schema.org/itemListElement": {
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
        "http://0.0.0.0:3333/ontology/0803/incunabula/v2#hasAuthor": {
            "@id": "http://rdfh.ch/c5058f3a/values/8653a672",
            "@type": "http://api.knora.org/ontology/knora-api/v2#TextValue",
            "http://api.knora.org/ontology/knora-api/v2#valueAsString": "Berthold, der Bruder"
        },
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
    },
    "http://schema.org/numberOfItems": 1
};

// http://localhost:3333/v2/resources/http%3A%2F%2Frdfh.ch%2Fc5058f3a?schema=simple
const ZeitgloeckleinSimple: ApiV2Simple.ResourcesSequence = {
    "@type": "http://schema.org/ItemList",
    "http://schema.org/itemListElement": {
        "@id": "http://rdfh.ch/c5058f3a",
        "@type": "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#book",
        "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#citation": [
            "Schramm Bd. XXI, S. 27",
            "GW 4168",
            "ISTC ib00512000"
        ],
        "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#hasAuthor": "Berthold, der Bruder",
        "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#location": "Universitäts- und Stadtbibliothek Köln, Sign: AD+S167",
        "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#physical_desc": "Dimension: 8°",
        "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#pubdate": "JULIAN:1492 CE",
        "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#publisher": "Johann Amerbach",
        "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#publoc": "Basel",
        "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#title": "Zeitglöcklein des Lebens und Leidens Christi",
        "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#url": "http://www.ub.uni-koeln.de/cdm/compoundobject/collection/inkunabeln/id/1878/rec/1",
        "http://www.w3.org/2000/01/rdf-schema#label": "Zeitglöcklein des Lebens und Leidens Christi"
    },
    "http://schema.org/numberOfItems": 1
};

// http://localhost:3333/v2/search/Narr
let fulltextSearchForNarr: ApiV2WithValueObjects.ResourcesSequence = {
    "@type": "http://schema.org/ItemList",
    "http://schema.org/itemListElement": [
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
            "@id": "http://rdfh.ch/0801/beol/4BhkDasRSLaB-ujyKWplaw",
            "@type": "http://0.0.0.0:3333/ontology/0801/beol/v2#letter",
            "http://0.0.0.0:3333/ontology/0801/beol/v2#hasText": {
                "@id": "http://rdfh.ch/0801/beol/4BhkDasRSLaB-ujyKWplaw/values/5_qMgwspTsmJYdmanJXe_g",
                "@type": "http://api.knora.org/ontology/knora-api/v2#TextValue",
                "http://api.knora.org/ontology/knora-api/v2#textValueAsHtml": "<div>\n   <div id=\"transcription\">\n      \n      <p>\n         <a class=\"facsimile salsah-link\" href=\"http://rdfh.ch/0801/beol/ofF53oirSzm9mFm2WjZQ0Q\"></a> Bâle ce 28. Mars 1730. \n      </p>\n      \n      <p>Monsieur et treshonoré Ami</p>\n      \n      <p>J'ai toujours differé de Vous repondre dans l'esperance de pouvoir Vous mander l'issue\n         de l'affaire du Diac. W. mais selon le train qu'elle prend, elle pourra bien trotter\n         encore quelque temps avant qu'on en vienne à la sentence finale. Mrs. les Theologiens\n         ont livré au Conseil une deduction de leurs griefs tres vigoureuse et longue, contenant\n         environ 300 pages in folio; la Lecture a couté au Magistrat cinq seances, chacune\n         de 4 heures. Non obstant les plus foudroyantes accusations dont W. est chargé, il\n         ne laisse pas de trouver encore des Patrons dans le Conseil, qui voudroient bien le\n         sauver s'ils pouvoient, mais il n'y a pas moyen, quoique le Pere de l'accusé et l'accusé\n         lui meme ayent eu assés de temps d'user de toutes leurs ruses et artifices (dont ils\n         sont capables) pour gagner la pluralité des suffrages, ce qu'on a pu remarquer en\n         ce que la lecture de la seconde partie ayant été faite il y eût une si grande emotion\n         dans les Esprits des Conseillers Auditeurs, à cause des crimes inoüis dont cette partie\n         étoit remplie à la charge de W. que peu s'en falloit qu'on n'on n'envoyat sur le champ\n         4 fuseliers pour le saisir et trainer en prison; mais on fut bien surpris de voir\n         qu'à la troisieme lecture et les suivantes, ce feu fut extremement rallenti quoi qu'on\n         y eut entendu des imputations pour le moins aussi importantes que celles qui furent\n         lües par la seconde lecture; il y en eut meme, à ce que j'apprends, qui opinerent\n         pour la suppression des deux dernieres parties sans en faire la lecture; <a class=\"facsimile salsah-link\" href=\"http://rdfh.ch/0801/beol/u6hoabVIReOnDn3jCkWf4A\"></a> d'où Vous voyés que les W. Pere et fils ont bien sçû mettre à profit le delai qu'ils\n         avoient d'une session à l'autre; Cependant les plus zelés pour la cause de Dieu ont\n         prévalu, malgrés les infatigables pratiques qu'on a mises en oeuvre, ensorte qu'à\n         la derniere des 5 Sessions il fut decreté, que dans le Conseil des treize on questionneroit\n         sur touts les articles d'accusations le D. W. et cela en presence de Msrs. les Theologiens\n         ses Accusateurs; Ce qui fut executé jeudi passé à la maison de Ville; l'examen dura\n         depuis 2 heures jusqu'à 8 heures du soir; j'ai appris que W. se defendit miserablement,\n         ne faisant autre chose que nier les faits les plus averés par temoins irreprochables,\n         suivant en cela le dogme 15 qu'il a enseigné à ses Disciples. Ainsi le rapport des\n         treise étant fait au Magistrat samedi aprés, et voyant qu'il n'y avoit pas moyen de\n         ramener le Delinquent à la reconnoissance de ses Crimes, ni à la repentance spontanée,\n         il fut conclû que les Theologiens dresseroient des interrogatoires sur les quels on\n         ecouteroit chacun des Temoins sous un sermant corporel; Ce qui demandera encore du\n         temps, d'autant plus que quelques uns des Temoins demeurent ailleurs, qu'il faudra\n         ou citer s'ils ne sont pas loins, ou leur faire faire leurs depositions sous sermant\n         devant le Magistrat du lieu où ils se trouvent. Quelques uns croyent que le D. W.\n         quand il verra le serieux, il se resoudra plutot à chanter le <em>peccavi</em> qu'à se voir convaincu par les Temoins; il le fera peutetre déja demain par une Requete\n         qu'il presentera peutetre au Conseil. Voici en attendant la sentence definitive, la\n         quintessence des Heresies, telles que Mrs. les Theolog. l'ont tirée eux memes de leur\n         long Memoire. Je Vous suis bien obligé de la communication de la Lettre du Ministere\n         de Schafouse au Notre, je l'avois déja luë auparavant; elle contient bien plus de\n         vigueur et de bon sens que celle de chés Vous et celle de Berne. Je suis avec toute\n         la sincerité de mon ame Monsieur Votre tres humble et tr. ob. serviteur J. Bernoulli\n         \n      </p>\n      \n      <p>\n         <a class=\"facsimile salsah-link\" href=\"http://rdfh.ch/0801/beol/nI6vp40wTy6Qvmjya85CFA\"></a> P. S. Je viens d'apprendre que les partisans de W. courent en effet de maison en\n         maison chés les Conseillers, pour demander grace en faveur de l'accusé promettant,\n         qu'il reconnoitra ses crimes et les confessera; c'est sans doute pour prevenir la\n         conviction juridique par les Temoins jurés, ce qui le ruineroit entierement. Mais\n         voilà une repentance par force, qui ne le reconciliera pas avec Dieu, qu'il a si horriblement\n         offensé. \n      </p>\n      \n      <p>\n         <a class=\"facsimile salsah-link\" href=\"http://rdfh.ch/0801/beol/tl7VCSivQ-qToHWNSK4dqQ\"></a> Recapitulation\n      </p>\n      \n      <p>H. Diaconus W. ist folgend. Irrthumer überwiesen, welche er den Leüthen beyzubringen\n         getrachtet hat: \n      </p>\n      \n      <p>1. Das hohe Geheimnuß der Dreyeinigkeit vernichtet er. </p>\n      \n      <p>2. hat er auf allerhand weiß zu verstehen geben, Christus seye nicht der wahre Gott\n         und eines wesens mit dem Vatter, und deßwegen alle örter H. Schrifft, so davon handlen,\n         verdrähet, derowegen möge Christus auch nicht inn allen fählen angebetten werden.\n         \n      </p>\n      \n      <p>3. Christus habe nicht für unsere Sünden genuggethan sondern den Menschen nur gute\n         Lehren und Exempel gegeben. \n      </p>\n      \n      <p>4. Der H. Geyst seye nicht der wahre Gott, sondern etwas mehr als ein Mensch, deßwegen\n         könne man nicht gewiß seyn, daß er müsse angebetten werden. \n      </p>\n      \n      <p>5. Die H. Schrifft seye nicht in allen Stucken das ohnfehlbare wort Gottes, nachdem\n         die Heyl. Scribenten nicht inn allen dingen von Gott inspirirt, oder unfehlbar gewesen.\n      </p>\n      \n      <p>6. Die Verfasser haben geredt nach den Vorurtheylen und irrigen meynungen der Menschen.</p>\n      \n      <p>7. Christus selbst habe sich gerichtet nach d. Superstition und Aberglauben der Juden.</p>\n      \n      <p>8. Die H. Schrifft seye aus vielen Ursachen gantz undeütlich. </p>\n      \n      <p>9. Hat Er alle so gar die außtrucklichste wort u. örter d. H. Schrifft von dem Satan,\n         welche Er in seinen Collegiis berühret, von anderen dingen erkläret, und den teüffel\n         fast gäntzlich darauß gemustert.\n      </p>\n      \n      <p>\n         <a class=\"facsimile salsah-link\" href=\"http://rdfh.ch/0801/beol/ID8r3mKXQ3ORZK6b2MNP8g\"></a> 10. Es seyen keine vom teüffel besessene gewesen, deren die H. Evangelisten meldung\n         thun, sondern man müsse dieses nur von Kranckheiten verstehen.\n      </p>\n      \n      <p>11. In dem H. Christo haben sich böse gelüst u. gedancken befunden. </p>\n      \n      <p>12. Die bösen gelüst seyen keine Sünd, so lang sie nicht ins werck außbrechen, sondern\n         nur eitele gedancken.\n      </p>\n      \n      <p>13. Man könne inn denen unterschiedlichen Religionen, Papistisch, griechisch etc.\n         seelig werden.\n      </p>\n      \n      <p>14. Um gelds willen od. auß boßheit die Religion änderen seye keine Sünd, sondern\n         eine blosse narrheit.\n      </p>\n      \n      <p>15. In dem nothfall und um sein Leben zu Salviren seye es wohl erlaubt List, betrug\n         und ränck zu gebrauchen, dieweilen auch d. Apostel Paulus dergleichen gebraucht habe,\n         ja man dörffe die allergrösten Missethaten vor d. hohen Obrigkeit selbst ablaügnen,\n         u. seye d. ein narr, der sie bekenne, ehe er sonst überwiesen seye. \n      </p>\n      \n      <p>16. Man müsse das Christenthum den Leüthen nicht schwer sondern leicht machen, ihnen\n         daher nicht vorstellen, d. sie dadurch inn noth und Trübsal gerathen können. \n      </p>\n      \n      <p>17. Die Seelen d. Menschen schlaffen nach dem Todt und seyen unempfindlich biß an\n         jüngsten tag.\n      </p>\n      \n      <p>18. Die Peyn d. verdammten in d. Höll werde nicht ewig währen. </p>\n      \n      <p>Neben diesen gehegten Irrthümmern hatte er vor ein gefährliches griechisches testament\n         außzugeben u. von deßen beschaffenheit U. G. H. biß auff dreymahl falschen bericht\n         abgestattet, deren bericht keiner mit dem noch endlich gelüfferten Muster Diac. XII\n         ersten Cap. Matth. überein kommt.\n         \n      </p>\n      \n   </div>\n   <div id=\"references\">\n      <ol></ol>\n   </div>\n</div>"
            },
            "http://www.w3.org/2000/01/rdf-schema#label": "1730-03-28_Bernoulli_Johann_I-Scheuchzer_Johann_Jakob"
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
        }
    ],
    "http://schema.org/numberOfItems": 25
};

// http://localhost:3333/v2/search/Narr?schema=simple
const fulltextSearchForNarrSimple: ApiV2Simple.ResourcesSequence = {
    "@type": "http://schema.org/ItemList",
    "http://schema.org/itemListElement": [
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
            "@id": "http://rdfh.ch/0801/beol/4BhkDasRSLaB-ujyKWplaw",
            "@type": "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#letter",
            "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#hasText": "\n      \n          Bâle ce 28. Mars 1730. \u001e\n      Monsieur et treshonoré Ami\u001e\n      J'ai toujours differé de Vous repondre dans l'esperance de pouvoir Vous mander l'issue de l'affaire du Diac. W. mais selon le train qu'elle prend, elle pourra bien trotter encore quelque temps avant qu'on en vienne à la sentence finale. Mrs. les Theologiens ont livré au Conseil une deduction de leurs griefs tres vigoureuse et longue, contenant environ 300 pages in folio; la Lecture a couté au Magistrat cinq seances, chacune de 4 heures. Non obstant les plus foudroyantes accusations dont W. est chargé, il ne laisse pas de trouver encore des Patrons dans le Conseil, qui voudroient bien le sauver s'ils pouvoient, mais il n'y a pas moyen, quoique le Pere de l'accusé et l'accusé lui meme ayent eu assés de temps d'user de toutes leurs ruses et artifices (dont ils sont capables) pour gagner la pluralité des suffrages, ce qu'on a pu remarquer en ce que la lecture de la seconde partie ayant été faite il y eût une si grande emotion dans les Esprits des Conseillers Auditeurs, à cause des crimes inoüis dont cette partie étoit remplie à la charge de W. que peu s'en falloit qu'on n'on n'envoyat sur le champ 4 fuseliers pour le saisir et trainer en prison; mais on fut bien surpris de voir qu'à la troisieme lecture et les suivantes, ce feu fut extremement rallenti quoi qu'on y eut entendu des imputations pour le moins aussi importantes que celles qui furent lües par la seconde lecture; il y en eut meme, à ce que j'apprends, qui opinerent pour la suppression des deux dernieres parties sans en faire la lecture;  d'où Vous voyés que les W. Pere et fils ont bien sçû mettre à profit le delai qu'ils avoient d'une session à l'autre; Cependant les plus zelés pour la cause de Dieu ont prévalu, malgrés les infatigables pratiques qu'on a mises en oeuvre, ensorte qu'à la derniere des 5 Sessions il fut decreté, que dans le Conseil des treize on questionneroit sur touts les articles d'accusations le D. W. et cela en presence de Msrs. les Theologiens ses Accusateurs; Ce qui fut executé jeudi passé à la maison de Ville; l'examen dura depuis 2 heures jusqu'à 8 heures du soir; j'ai appris que W. se defendit miserablement, ne faisant autre chose que nier les faits les plus averés par temoins irreprochables, suivant en cela le dogme 15 qu'il a enseigné à ses Disciples. Ainsi le rapport des treise étant fait au Magistrat samedi aprés, et voyant qu'il n'y avoit pas moyen de ramener le Delinquent à la reconnoissance de ses Crimes, ni à la repentance spontanée, il fut conclû que les Theologiens dresseroient des interrogatoires sur les quels on ecouteroit chacun des Temoins sous un sermant corporel; Ce qui demandera encore du temps, d'autant plus que quelques uns des Temoins demeurent ailleurs, qu'il faudra ou citer s'ils ne sont pas loins, ou leur faire faire leurs depositions sous sermant devant le Magistrat du lieu où ils se trouvent. Quelques uns croyent que le D. W. quand il verra le serieux, il se resoudra plutot à chanter le peccavi qu'à se voir convaincu par les Temoins; il le fera peutetre déja demain par une Requete qu'il presentera peutetre au Conseil. Voici en attendant la sentence definitive, la quintessence des Heresies, telles que Mrs. les Theolog. l'ont tirée eux memes de leur long Memoire. Je Vous suis bien obligé de la communication de la Lettre du Ministere de Schafouse au Notre, je l'avois déja luë auparavant; elle contient bien plus de vigueur et de bon sens que celle de chés Vous et celle de Berne. Je suis avec toute la sincerité de mon ame Monsieur Votre tres humble et tr. ob. serviteur J. Bernoulli \u001e\n      \n          P. S. Je viens d'apprendre que les partisans de W. courent en effet de maison en maison chés les Conseillers, pour demander grace en faveur de l'accusé promettant, qu'il reconnoitra ses crimes et les confessera; c'est sans doute pour prevenir la conviction juridique par les Temoins jurés, ce qui le ruineroit entierement. Mais voilà une repentance par force, qui ne le reconciliera pas avec Dieu, qu'il a si horriblement offensé. \u001e\n      \n          Recapitulation\u001e\n      H. Diaconus W. ist folgend. Irrthumer überwiesen, welche er den Leüthen beyzubringen getrachtet hat: \u001e\n      1. Das hohe Geheimnuß der Dreyeinigkeit vernichtet er. \u001e\n      2. hat er auf allerhand weiß zu verstehen geben, Christus seye nicht der wahre Gott und eines wesens mit dem Vatter, und deßwegen alle örter H. Schrifft, so davon handlen, verdrähet, derowegen möge Christus auch nicht inn allen fählen angebetten werden. \u001e\n      3. Christus habe nicht für unsere Sünden genuggethan sondern den Menschen nur gute Lehren und Exempel gegeben. \u001e\n      4. Der H. Geyst seye nicht der wahre Gott, sondern etwas mehr als ein Mensch, deßwegen könne man nicht gewiß seyn, daß er müsse angebetten werden. \u001e\n      5. Die H. Schrifft seye nicht in allen Stucken das ohnfehlbare wort Gottes, nachdem die Heyl. Scribenten nicht inn allen dingen von Gott inspirirt, oder unfehlbar gewesen.\u001e\n      6. Die Verfasser haben geredt nach den Vorurtheylen und irrigen meynungen der Menschen.\u001e\n      7. Christus selbst habe sich gerichtet nach d. Superstition und Aberglauben der Juden.\u001e\n      8. Die H. Schrifft seye aus vielen Ursachen gantz undeütlich. \u001e\n      9. Hat Er alle so gar die außtrucklichste wort u. örter d. H. Schrifft von dem Satan, welche Er in seinen Collegiis berühret, von anderen dingen erkläret, und den teüffel fast gäntzlich darauß gemustert.\u001e\n      \n          10. Es seyen keine vom teüffel besessene gewesen, deren die H. Evangelisten meldung thun, sondern man müsse dieses nur von Kranckheiten verstehen.\u001e\n      11. In dem H. Christo haben sich böse gelüst u. gedancken befunden. \u001e\n      12. Die bösen gelüst seyen keine Sünd, so lang sie nicht ins werck außbrechen, sondern nur eitele gedancken.\u001e\n      13. Man könne inn denen unterschiedlichen Religionen, Papistisch, griechisch etc. seelig werden.\u001e\n      14. Um gelds willen od. auß boßheit die Religion änderen seye keine Sünd, sondern eine blosse narrheit.\u001e\n      15. In dem nothfall und um sein Leben zu Salviren seye es wohl erlaubt List, betrug und ränck zu gebrauchen, dieweilen auch d. Apostel Paulus dergleichen gebraucht habe, ja man dörffe die allergrösten Missethaten vor d. hohen Obrigkeit selbst ablaügnen, u. seye d. ein narr, der sie bekenne, ehe er sonst überwiesen seye. \u001e\n      16. Man müsse das Christenthum den Leüthen nicht schwer sondern leicht machen, ihnen daher nicht vorstellen, d. sie dadurch inn noth und Trübsal gerathen können. \u001e\n      17. Die Seelen d. Menschen schlaffen nach dem Todt und seyen unempfindlich biß an jüngsten tag.\u001e\n      18. Die Peyn d. verdammten in d. Höll werde nicht ewig währen. \u001e\n      Neben diesen gehegten Irrthümmern hatte er vor ein gefährliches griechisches testament außzugeben u. von deßen beschaffenheit U. G. H. biß auff dreymahl falschen bericht abgestattet, deren bericht keiner mit dem noch endlich gelüfferten Muster Diac. XII ersten Cap. Matth. überein kommt.\n\u001e\n   ",
            "http://www.w3.org/2000/01/rdf-schema#label": "1730-03-28_Bernoulli_Johann_I-Scheuchzer_Johann_Jakob"
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
        }
    ],
    "http://schema.org/numberOfItems": 25
};

// http://localhost:3333/v2/searchextended/%20%20%20PREFIX%20knora-api%3A%20%3Chttp%3A%2F%2Fapi.knora.org%2Fontology%2Fknora-api%2Fsimple%2Fv2%23%3E%0A%0A%20%20%20CONSTRUCT%20%7B%0A%20%20%20%20%20%20%3Fcomponent%20knora-api%3AisMainResource%20true%20.%20%23%20marking%20of%20the%20component%20searched%20for%20as%20the%20main%20resource%2C%20mandatory%0A%20%20%20%20%20%20%3Fcomponent%20knora-api%3Aseqnum%20%3Fseqnum%20.%20%23%20return%20the%20sequence%20number%20in%20the%20response%0A%20%20%20%20%20%20%3Fcomponent%20knora-api%3AhasStillImageFileValue%20%3Ffile%20.%20%23%20return%20the%20StillImageFile%20in%20the%20response%0A%20%20%0A%20%20%20%20%20%20%3Fcomponent%20knora-api%3AisPartOf%20%3Chttp%3A%2F%2Frdfh.ch%2Fc5058f3a%3E%20.%0A%20%20%20%7D%20WHERE%20%7B%0A%20%20%20%20%20%20%3Fcomponent%20a%20knora-api%3AResource%20.%20%23%20explicit%20type%20annotation%20for%20the%20component%20searched%20for%2C%20mandatory%0A%20%20%20%20%20%20%3Fcomponent%20a%20knora-api%3AStillImageRepresentation%20.%20%23%20additional%20restriction%20of%20the%20type%20of%20component%2C%20optional%0A%0A%20%20%20%20%20%20%3Fcomponent%20knora-api%3AisPartOf%20%3Chttp%3A%2F%2Frdfh.ch%2Fc5058f3a%3E%20.%20%23%20component%20relates%20to%20compound%20resource%20via%20this%20property%0A%20%20%20%20%20%20knora-api%3AisPartOf%20knora-api%3AobjectType%20knora-api%3AResource%20.%20%23%20type%20annotation%20for%20linking%20property%2C%20mandatory%0A%20%20%20%20%20%20%3Chttp%3A%2F%2Frdfh.ch%2Fc5058f3a%3E%20a%20knora-api%3AResource%20.%20%23%20type%20annotation%20for%20compound%20resource%2C%20mandatory%0A%0A%20%20%20%20%20%20%3Fcomponent%20knora-api%3Aseqnum%20%3Fseqnum%20.%20%23%20component%20must%20have%20a%20sequence%20number%2C%20no%20further%20restrictions%20given%0A%20%20%20%20%20%20knora-api%3Aseqnum%20knora-api%3AobjectType%20xsd%3Ainteger%20.%20%23%20type%20annotation%20for%20the%20value%20property%2C%20mandatory%0A%20%20%20%20%20%20%3Fseqnum%20a%20xsd%3Ainteger%20.%20%23%20type%20annotation%20for%20the%20sequence%20number%2C%20mandatory%0A%0A%20%20%20%20%20%20%3Fcomponent%20knora-api%3AhasStillImageFileValue%20%3Ffile%20.%20%23%20component%20must%20have%20a%20StillImageFile%2C%20no%20further%20restrictions%20given%0A%20%20%20%20%20%20knora-api%3AhasStillImageFileValue%20knora-api%3AobjectType%20knora-api%3AFile%20.%20%23%20type%20annotation%20for%20the%20value%20property%2C%20mandatory%0A%20%20%20%20%20%20%3Ffile%20a%20knora-api%3AFile%20.%20%23%20type%20annotation%20for%20the%20StillImageFile%2C%20mandatory%0A%20%20%20%7D%0A%20%20%20ORDER%20BY%20ASC(%3Fseqnum)%20%23%20order%20by%20sequence%20number%2C%20ascending%0A%20%20%20OFFSET%200%20%23get%20first%20page%20of%20results
let pagesOfZeitgloecklein: ApiV2WithValueObjects.ResourcesSequence = {
    "@type": "http://schema.org/ItemList",
    "http://schema.org/itemListElement": [
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
    ],
    "http://schema.org/numberOfItems": 25
};

// Euler letter L176-O
const EulerLetter: ApiV2WithValueObjects.ResourcesSequence = {
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
};

const EulerLetterSimple: ApiV2Simple.ResourcesSequence = {
    "@type": "http://schema.org/ItemList",
    "http://schema.org/itemListElement": {
        "@id": "http://rdfh.ch/0801/beol/-0tI3HXgSSOeDtkf-SA00w",
        "@type": "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#letter",
        "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#creationDate": "GREGORIAN:1756-01-03 CE",
        "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#hasAuthor": {
            "@id": "http://rdfh.ch/biblio/QNWEqmjxQ9W-_hTwKlKP-Q"
        },
        "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#hasRecipient": {
            "@id": "http://rdfh.ch/biblio/Yv2elBDtSMqoJeKRcxsW8A"
        },
        "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#hasSubject": [
            "Other quadratic forms",
            "Berlin Academy",
            "Other professional tasks",
            "Errands",
            "Book trade, orders",
            "Johann Albrecht Euler"
        ],
        "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#hasText": "\n        Hochwohlgebohrner Herr\u001e\n        Hochgeehrtester Herr Etats Rath\u001e\n        Bey dem Antritt dieses neuen Jahrs lege ich zuvorderst meinen herzlichsten Wunsch für das beständige Wohlseyn Eur. Hochwohlgeb. ab, und empfehle mich dabey gehorsamst sammt den meinigen zu Dero fortdaurenden Wohlgewogenheit\\,.\\, Zugleich statte ich auch Eur. Hochwohlgeb. meine verpflichtetste Danksagung ab für den gütigen Antheil welchen Dieselben an unserem Zustand zu nehmen belieben und habe das Vergnügen Eur. Hochwohlgeb. zu berichten, daß S[ein]\\,{}^{\\text{e}}\\, Königl[iche] Majestät bey dem Anfang dieses Jahrs Dero Pathen unsern ältesten Sohn mit einer jährlichen Besoldung von \\,200\\, Rthl. begnadiget.1\u001e\n        Ich habe nun schon eine geraume Zeit so viel andere Geschäfte gehabt daß ich an numerische Theoremata, dergleichen ich Eur. Hochwohlgeb. das letste mal vorzulegen die Ehre gehabt, nicht habe denken können. Die Partes Matheseos applicatae nehmen mir die meiste Zeit weg, wo es immer mehr zu untersuchen gibt, je mehr man damit umgeht.2\u001e\n        Weil nun mein Kopf mit so viel anderen Sachen angefüllet ist, so mag das wohl die Ursache seyn, daß ich mich in das von Eur. Hochwohlgeb. communicirte und nach der Hand verbesserte Theorema nicht finden kan. Vielleicht haben Eur. Hochwohlgeb. vergessen noch eine wesentliche Condition hinzuzusetzen.3\u001e\n        Das Theorema war: Si sit\\,aa+bb=P^{2}+eQ^{2}\\,erit etiam\u001e\n        \n          \\,a^{2}+\\left(\\left(2e+1\\right)b-eP-eQ\\right)^{2}=M^{2}+eN^{2}\\text{;}\\,\n        \u001e\n        weil ich den Grund desselben nicht einsehen konnte, so habe ich die Richtigkeit desselben durch Exempel erforschen wollen.\u001e\n        I. Da \\,1^{2}+4^{2}=17=3^{2}+2\\cdot 2^{2}\\,, so ist \\,a=1\\,, \\,b=4\\,, \\,P=3\\,, \\,Q=2\\, und \\,e=2\\,, allso müste seyn\u001e\n        \n          \\,1^{2}+\\left(5\\cdot 4-2\\cdot 3-2\\cdot 2\\right)^{2}=1^{2}+10^{2}=101=M^{2}+2N^{2}\\,\n        \u001e\n        welches unmöglich ist.\u001e\n        II. Da \\,9^{2}+4^{2}=97=7^{2}+3\\cdot 4^{2}\\,, so ist \\,a=9\\,; \\,b=4\\,; \\,P=7\\,; \\,Q=4\\, und \\,e=3\\,, allso müsste seyn\u001e\n        \n          \\,9^{2}+\\left(7\\cdot 4-3\\cdot 7-3\\cdot 4\\right)^{2}=9^{2}+5^{2}=106=M^{2}+3N^{2}\\,\n        \u001e\n        welches ebenfalls unmöglich ist.\u001e\n        Da ich nun nicht einmal ein Exempel finden kan, welches einträfe, so schliesse ich daraus, daß eine gewisse Bedingung in den Zahlen \\,a\\,, \\,b\\,, \\,P\\, und \\,Q\\, müsse weggelassen seyn, welche ich aber nicht ausfündig machen kan.4\u001e\n        Ich habe dem H. Spener zu wissen gethan, daß Eur. Hochwohlgeb. die Rechnung für die überschickten Bücher verlangen; bekomme ich dieselbe vor Schliessung dieses Briefs, wie ich ihm habe sagen lassen, so werde ich sie beylegen.5\u001e\n        Sonsten da er nicht alle verlangte Bücher gehabt, so werde ich inskünftige dergleichen Commissionen dem M.\\,{}^{\\text{r}}\\,Neaulme, welcher weit activer ist und alles schaffen kan, auftragen. Wegen des Werks: La Clef du Cabinet des Princes füge hier die Antwort des M.\\,{}^{\\text{r}}\\,de Bourdeaux bey.6\u001e\n        Sollte dasselbe vor der Ankunft einer Resolution von Eur. Hochwohlgeb. schon verkauft worden seyn, so hat sich M.\\,{}^{\\text{r}}\\,Neaulme anheischig gemacht, dasselbe auch zu liefern.\u001e\n        Ich habe die Ehre mit der schuldigsten Hochachtung zu verharren\u001e\n        Eur. Hochwohlgebohrnen\u001e\n        gehorsamster Diener\u001e\n        \n          L. Euler\n        \u001e\n        Berlin den 3\\,{}^{\\text{ten}}\\,Januarii\u001e\n        1756.\u001e\n        \n          Berlin, January 3rd, 1756\n        \u001e\n        \n          Original, 1 fol. – RGADA, f. 181, n. 1413, č. V, fol. 123rv\n        \u001e\n        \n          Published: Correspondance (1843), t. I, p. 636–637; Euler-Goldbach (1965), p. 385–386\n        \u001e\n      ",
        "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#letterHasLanguage": "German",
        "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#letterHasNumber": "176",
        "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#letterHasOriginal": {
            "@id": "http://rdfh.ch/0801/beol/1PUuT3mcRGmeunDS3ZNyOA"
        },
        "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#letterHasRepertoriumNumber": "890",
        "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#letterHasTranslation": {
            "@id": "http://rdfh.ch/0801/beol/yBr2EjBMTkeCeVmGPfjCtw"
        },
        "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#letterIsPublished": [
            {
                "@id": "http://rdfh.ch/0801/beol/79Rz6MaAQESBalJeWl2TVA"
            },
            {
                "@id": "http://rdfh.ch/0801/beol/pFN8MlXBSYaB7oZRxUpV0Q"
            }
        ],
        "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#letterIsReplyTo": [
            {
                "@id": "http://rdfh.ch/0801/beol/_iz5PE4rT8KNfbmfW8DxrQ"
            },
            {
                "@id": "http://rdfh.ch/0801/beol/f5a92GjJRriC1GTY_NzfvQ"
            }
        ],
        "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#location": "Berlin",
        "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#title": " Euler to Goldbach, January 3rd, 1756",
        "http://api.knora.org/ontology/knora-api/simple/v2#hasStandoffLinkTo": [
            {
                "@id": "http://rdfh.ch/0801/beol/KvfXRPkXTziMYMVYSz9tBg"
            },
            {
                "@id": "http://rdfh.ch/biblio/up0Q0ZzPSLaULC2tlTs1sA"
            },
            {
                "@id": "http://rdfh.ch/biblio/Z_-TT-8_QNSRv-O7dKCW0w"
            },
            {
                "@id": "http://rdfh.ch/0801/beol/nRO3f9ENSsqTH8S0Z1uO9w"
            },
            {
                "@id": "http://rdfh.ch/0801/beol/spy5H95GTV2RElphXFPbbw"
            },
            {
                "@id": "http://rdfh.ch/biblio/FsJNrctNTMuwJPCX-7OTVg"
            },
            {
                "@id": "http://rdfh.ch/0801/beol/08Y_rCK5QM-gvchjtixomw"
            },
            {
                "@id": "http://rdfh.ch/biblio/DhgjcrRhRfunaSt77-bUxg"
            },
            {
                "@id": "http://rdfh.ch/biblio/bP1CO3j3TCOUHYdQqKw9pA"
            },
            {
                "@id": "http://rdfh.ch/0801/beol/kZeMXLrQTQONISqizXtf5g"
            },
            {
                "@id": "http://rdfh.ch/biblio/vR3fWAXxRqShBZvWKVA9tA"
            },
            {
                "@id": "http://rdfh.ch/biblio/sAImr-uGRBGpsdBdoI6XCw"
            },
            {
                "@id": "http://rdfh.ch/0801/beol/FJCOlKBdRtW8caqnN4A3Vw"
            }
        ],
        "http://www.w3.org/2000/01/rdf-schema#label": "L176-O"
    },
    "http://schema.org/numberOfItems": 1
};

// http://0.0.0.0:3333/v2/resources/http%3A%2F%2Frdfh.ch%2F8a0b1e75?schema=simple
const PageOfZeitgloecklein: ApiV2Simple.ResourcesSequence = {
    "@type": "http://schema.org/ItemList",
    "http://schema.org/itemListElement": {
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
            "http://localhost:1024/knora/incunabula_0000000002.jpg/full/95,128/0/default.jpg",
            "http://localhost:1024/knora/incunabula_0000000002.jp2/full/2613,3505/0/default.jpg"
        ],
        "http://www.w3.org/2000/01/rdf-schema#label": "a1r, Titelblatt"
    },
    "http://schema.org/numberOfItems": 1
};

