/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and André Fatton.
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

package org.knora.webapi.util.standoff

import java.util.UUID

import org.knora.webapi.CoreSpec
import org.knora.webapi.util.KnoraIdUtil
import org.xmlunit.builder.{DiffBuilder, Input}
import org.xmlunit.diff.Diff

/**
  * Tests [[XMLToStandoffUtil]].
  */
class XMLToStandoffUtilSpec extends CoreSpec {

    val knoraIdUtil = new KnoraIdUtil

    "The XML to standoff utility" should {

        "convert an XML document to text with standoff, then back to an equivalent XML document" in {
            val standoffUtil = new XMLToStandoffUtil(writeUuidsToXml = false)

            // Convert the XML document to text with standoff.
            val textWithStandoff: TextWithStandoff = standoffUtil.xml2TextWithStandoff(XMLToStandoffUtilSpec.simpleXmlDoc, log = logger)

            // Convert the text with standoff back to XML.
            val backToXml: String = standoffUtil.textWithStandoff2Xml(textWithStandoff)

            // Compare the original XML with the regenerated XML.
            val xmlDiff: Diff = DiffBuilder.compare(Input.fromString(XMLToStandoffUtilSpec.simpleXmlDoc)).withTest(Input.fromString(backToXml)).build()
            xmlDiff.hasDifferences should be(false)
        }

        "convert an XML document with namespaces and CLIX milestones to standoff, then back to an equivalent XML document" in {
            val documentSpecificIDs = Map(
                "s02" -> UUID.randomUUID,
                "s03" -> UUID.randomUUID,
                "s04" -> UUID.randomUUID
            )

            val standoffUtil = new XMLToStandoffUtil(
                defaultXmlNamespace = Some("http://www.example.org/ns1"),
                xmlNamespaces = Map("ns2" -> "http://www.example.org/ns2"),
                writeUuidsToXml = false,
                documentSpecificIDs = documentSpecificIDs
            )

            // Convert the XML document to text with standoff.
            val textWithStandoff: TextWithStandoff = standoffUtil.xml2TextWithStandoff(XMLToStandoffUtilSpec.xmlDocWithClix, log = logger)

            // Convert the text with standoff back to XML.
            val backToXml = standoffUtil.textWithStandoff2Xml(textWithStandoff)

            // Compare the original XML with the regenerated XML.
            val xmlDiff: Diff = DiffBuilder.compare(Input.fromString(XMLToStandoffUtilSpec.xmlDocWithClix)).withTest(Input.fromString(backToXml)).build()
            xmlDiff.hasDifferences should be(false)
        }

        "calculate the diffs between a critical text and a diplomatic transcription (1)" in {
            val regionID = UUID.randomUUID

            val documentSpecificIDs = Map(
                "1" -> regionID
            )

            val standoffUtil = new XMLToStandoffUtil(documentSpecificIDs = documentSpecificIDs)

            val diplomaticTranscription =
                """<?xml version="1.0" encoding="UTF-8"?>
                  |<region id="1">
                  |<center><underline>CLI</underline></center>.
                  |<underline>Examen modi Renaldiniani inscribendi q&#780;vis polygona regularia in circulo,
                  |depromti ex Lib. II. de Resol. &#38; Composi: Mathem: p. 367.</underline> (<underline>vid. Sturmii
                  |Mathesin enucleatam p. 38.</underline>)
                  |</region>
                """.stripMargin

            val criticalText =
                """<?xml version="1.0" encoding="UTF-8"?>
                  |<region id="1">
                  |<center>
                  |<bold>CLI</bold><medskip/>
                  |<bold>Examen modi Renaldiniani inscribendi quaevis polygona regularia in circulo,
                  |depromti ex Lib. II. de <expansion>Resolutione</expansion> &#38; Compositione Mathematica p. 367.
                  |(vide Sturmii Mathesin enucleatam p. 38.)</bold>
                  |</center>
                  |</region>
                """.stripMargin

            val diploTextWithStandoff: TextWithStandoff = standoffUtil.xml2TextWithStandoff(diplomaticTranscription, log = logger)
            val criticalTextWithStandoff: TextWithStandoff = standoffUtil.xml2TextWithStandoff(criticalText, log = logger)

            val criticalTextDiffs: Seq[StandoffDiff] = standoffUtil.makeStandoffDiffs(
                baseText = diploTextWithStandoff.text,
                derivedText = criticalTextWithStandoff.text
            )

            val criticalTextDiffsAsXml: String = standoffUtil.standoffDiffs2Xml(
                baseText = diploTextWithStandoff.text,
                derivedText = criticalTextWithStandoff.text,
                standoffDiffs = criticalTextDiffs
            )

            val expectedCriticalTextDiffsAsXml =
                """<?xml version="1.0" encoding="UTF-8"?>
                  |<diffs><ins>
                  |</ins>
                  |CLI<del>.</del>
                  |Examen modi Renaldiniani inscribendi q<del>&#780;</del><ins>uae</ins>vis polygona regularia in circulo,
                  |depromti ex Lib. II. de Resol<del>.</del><ins>utione</ins> &#38; Composi<del>:</del><ins>tione</ins> Mathem<del>:</del><ins>atica</ins> p. 367.<del> </del><ins>
                  |</ins>(vid<del>.</del><ins>e</ins> Sturmii<del>
                  |</del><ins> </ins>Mathesin enucleatam p. 38.)
                  |<ins>
                  |</ins></diffs>
                """.stripMargin

            val xmlDiff: Diff = DiffBuilder.compare(Input.fromString(expectedCriticalTextDiffsAsXml)).withTest(Input.fromString(criticalTextDiffsAsXml)).build()
            xmlDiff.hasDifferences should be(false)

            val (standoffAdded: Set[StandoffTag], standoffRemoved: Set[StandoffTag]) = standoffUtil.findChangedStandoffTags(
                oldStandoff = diploTextWithStandoff.standoff,
                newStandoff = criticalTextWithStandoff.standoff
            )

            standoffAdded.exists(_.uuid == regionID) should be(false)
            standoffRemoved.exists(_.uuid == regionID) should be(false)
        }

        "calculate the diffs between a critical text and a diplomatic transcription (2)" in {

            val regionID = UUID.randomUUID

            val documentSpecificIDs = Map(
                "2" -> regionID
            )

            val standoffUtil = new XMLToStandoffUtil(documentSpecificIDs = documentSpecificIDs)

            val diplomaticTranscription =
                """<?xml version="1.0" encoding="UTF-8"?>
                  |<region id="2">
                  |Modus hic est: Fiat triang: æquil: <math>ABD</math>, divisâq́  diametro <math>AB</math> in tot partes æquales,
                  |quot laterum est figura inscribenda, duabusq́  earum p̃termissis <strike>et</strike> à <math>B</math> versùs <math>A</math>, ducat,
                  |per initium tertiæ recta <math>DF</math>, &amp; hinc recta <math>FB</math>, quam putat esse latus polygoni optati
                  |<underline>Anal</underline>: Sit secta diameter utcunq́  in <math>E</math>, <math>e</math> , ductæq́  <math>DEF</math>, {<math>FA</math>,} <strike>&amp;</strike> <math>FB</math> &amp; demissa in diametrum per-
                  |pendicularis <math>FG</math>, fiat <math>CB=a</math>, <math>CE</math> vel <math>Ce=b</math>, <math>FB=x</math>, unde <math>AF=\sqrt{\mathstrut}\,\overline{4aa-xx}</math>
                  |</region>
                """.stripMargin

            val criticalText =
                """<?xml version="1.0" encoding="UTF-8"?>
                  |<region id="2">
                  |Modus hic est: Fiat triang[ulum] aequil[aterum] <math>ABD</math> divisaque diametro <math>AB</math> in tot partes aequales, quot laterum est figura inscribenda, duabusque earum praetermissis a <math>B</math> versus <math>A</math>, ducatur per initium tertiae recta <math>DF</math>, &amp; hinc recta <math>FB</math>, quam putat esse latus polygoni optati.
                  |<italic>Anal[ysis]</italic>: Sit secta diameter utcunque in <math>E</math>, <math>e</math>, ductaeque <math>DEF</math>, <math>FA</math>, &amp; <math>FB</math> &amp; demissa in diametrum perpendicularis <math>FG</math>, fiat <math>CB=a</math>, <math>CE</math> vel <math>Ce=b</math>, <math>FB=x</math>, unde <math>AF=\sqrt{4aa-xx}</math>
                  |</region>
                """.stripMargin


            val diploTextWithStandoff: TextWithStandoff = standoffUtil.xml2TextWithStandoff(diplomaticTranscription, log = logger)
            val criticalTextWithStandoff: TextWithStandoff = standoffUtil.xml2TextWithStandoff(criticalText, log = logger)

            val criticalTextDiffs: Seq[StandoffDiff] = standoffUtil.makeStandoffDiffs(
                baseText = diploTextWithStandoff.text,
                derivedText = criticalTextWithStandoff.text
            )

            val criticalTextDiffsAsXml: String = standoffUtil.standoffDiffs2Xml(
                baseText = diploTextWithStandoff.text,
                derivedText = criticalTextWithStandoff.text,
                standoffDiffs = criticalTextDiffs
            )

            //println(ScalaPrettyPrinter.prettyPrint(criticalTextDiffsAsXml))

            val expectedCriticalTextDiffsAsXml =
                """<?xml version="1.0" encoding="UTF-8"?>
                |<diffs>
                |Modus hic est: Fiat triang<del>: æquil:</del><ins>[ulum] aequil[aterum]</ins> ABD<del>,</del> divis<del>âq́ </del><ins>aque</ins> diametro AB in tot partes <del>æ</del><ins>ae</ins>quales,<del>
                |</del><ins> </ins>quot laterum est figura inscribenda, duabusq<del>́ </del><ins>ue</ins> earum p<del>̃</del><ins>rae</ins>termissis <del>et à</del><ins>a</ins> B vers<del>ù</del><ins>u</ins>s A, ducat<del>,
                |</del><ins>ur </ins>per initium terti<del>æ</del><ins>ae</ins> recta DF, &amp; hinc recta FB, quam putat esse latus polygoni optati<ins>.</ins>
                |Anal<ins>[ysis]</ins>: Sit secta diameter utcunq<del>́ </del><ins>ue</ins> in E, e<del> </del>, duct<del>æq́ </del><ins>aeque</ins> DEF, <del>{</del>FA,<del>}</del> &amp; FB &amp; demissa in diametrum per<del>-
                |</del>pendicularis FG, fiat CB=a, CE vel Ce=b, FB=x, unde AF=\sqrt{<del>\mathstrut}\,\overline{</del>4aa-xx}
                |</diffs>
                """.stripMargin


            val xmlDiff: Diff = DiffBuilder.compare(Input.fromString(expectedCriticalTextDiffsAsXml)).withTest(Input.fromString(criticalTextDiffsAsXml)).build()
            xmlDiff.hasDifferences should be(false)

            val (standoffAdded: Set[StandoffTag], standoffRemoved: Set[StandoffTag]) = standoffUtil.findChangedStandoffTags(
                oldStandoff = diploTextWithStandoff.standoff,
                newStandoff = criticalTextWithStandoff.standoff
            )

            standoffAdded.exists(_.uuid == regionID) should be(false)
            standoffRemoved.exists(_.uuid == regionID) should be(false)

        }

        "calculate the diffs in a workflow with two versions of a diplomatic transcription and two versions of an editorial text" in {
            val paragraphID = UUID.randomUUID
            val strikeID = UUID.randomUUID
            val blueID = UUID.randomUUID

            val documentSpecificIDs = Map(
                "1" -> paragraphID,
                "2" -> strikeID,
                "3" -> blueID
            )

            val standoffUtil = new XMLToStandoffUtil(documentSpecificIDs = documentSpecificIDs)

            // The diplomatic transcription has a structural tag (paragraph), an abbreviation ('d' for 'den'), a
            // strikethrough, and a repeated word (which could be the author's mistake or the transcriber's mistake).
            val diplomaticTranscription1 =
                """<?xml version="1.0" encoding="UTF-8"?>
                  |<paragraph id="1">Ich habe d Bus <strike id="2">heute </strike>genommen, weil ich ich verspätet war.</paragraph>
                """.stripMargin

            // Convert the markup in the transcription to standoff and back again to check that it's correct.

            val diplo1TextWithStandoff: TextWithStandoff = standoffUtil.xml2TextWithStandoff(diplomaticTranscription1, log = logger)

            val diplo1TextBackTtoXml: String = standoffUtil.textWithStandoff2Xml(diplo1TextWithStandoff)

            val diplo1XmlDiff: Diff = DiffBuilder.compare(Input.fromString(diplomaticTranscription1)).withTest(Input.fromString(diplo1TextBackTtoXml)).build()
            diplo1XmlDiff.hasDifferences should be(false)

            // The editor keeps the <paragraph> tag, expands the abbreviation, deletes the text marked with strikethrough,
            // and corrects the repeated word.
            val editorialText1 =
                """<?xml version="1.0" encoding="UTF-8"?>
                  |<paragraph id="1">Ich habe den Bus genommen, weil ich verspätet war.</paragraph>
                """.stripMargin

            val edito1TextWithStandoff: TextWithStandoff = standoffUtil.xml2TextWithStandoff(editorialText1, log = logger)

            // Find the differences between the version 1 of the transcription and version 1 of the editorial text,
            // so they can be linked together.

            val editorialStandoffDiffs1: Seq[StandoffDiff] = standoffUtil.makeStandoffDiffs(
                baseText = diplo1TextWithStandoff.text,
                derivedText = edito1TextWithStandoff.text
            )

            // Check that the editor's diffs are correct, by converting them to XML (which makes the test more readable).

            val expectedEditorialDiffs1AsXml =
                """<?xml version="1.0" encoding="UTF-8"?>
                  |<diffs>Ich habe d<ins>en</ins> Bus <del>heute </del>genommen, weil ich <del>ich </del>verspätet war.</diffs>
                """.stripMargin

            val editorialDiffs1AsXml: String = standoffUtil.standoffDiffs2Xml(
                baseText = diplo1TextWithStandoff.text,
                derivedText = edito1TextWithStandoff.text,
                standoffDiffs = editorialStandoffDiffs1
            )

            val xmlDiff1: Diff = DiffBuilder.compare(Input.fromString(expectedEditorialDiffs1AsXml)).withTest(Input.fromString(editorialDiffs1AsXml)).build()
            xmlDiff1.hasDifferences should be(false)

            // Now suppose the transcription has been updated. The new transcription changes 'Bus' to 'Bahn', corrects
            // the repeated word, which turns out to have been a transcription error, and adds a <blue> tag for ink
            // colour.
            val diplomaticTranscription2 =
                """<?xml version="1.0" encoding="UTF-8"?>
                  |<paragraph id="1">Ich habe d Bahn <strike id="2">heute </strike>genommen, weil ich <blue id="3">verspätet</blue> war.</paragraph>
                """.stripMargin

            // The editor now rebases the editorial text against the revised transcription, by making new diffs.
            // Find the differences between the version 2 of the transcription and version 1 of the editorial text.

            val diplo2TextWithStandoff: TextWithStandoff = standoffUtil.xml2TextWithStandoff(diplomaticTranscription2, log = logger)
            val blueTag = diplo2TextWithStandoff.standoff.find(_.uuid == blueID).getOrElse("<blue> tag not in standoff")

            val editorialStandoffDiffs2: Seq[StandoffDiff] = standoffUtil.makeStandoffDiffs(
                baseText = diplo2TextWithStandoff.text,
                derivedText = edito1TextWithStandoff.text
            )

            // Check that the editor's diffs are correct. Since the transcriber and editor now agree that 'ich' should
            // not be repeated, there should be no diff for that. However, the change from 'Bus' to 'Bahn' should show
            // up as a deletion and an insertion.

            val expectedEditorialDiffs2AsXml =
                """<?xml version="1.0" encoding="UTF-8"?>
                  |<diffs>Ich habe d<del> Bahn heute</del><ins>en Bus</ins> genommen, weil ich verspätet war.</diffs>
                """.stripMargin

            val editorialDiffs2AsXml: String = standoffUtil.standoffDiffs2Xml(
                baseText = diplo2TextWithStandoff.text,
                derivedText = edito1TextWithStandoff.text,
                standoffDiffs = editorialStandoffDiffs2
            )

            val xmlDiff2: Diff = DiffBuilder.compare(Input.fromString(expectedEditorialDiffs2AsXml)).withTest(Input.fromString(editorialDiffs2AsXml)).build()
            xmlDiff2.hasDifferences should be(false)

            // Also find out which standoff tags have changed in the new version of the transcription.

            val (addedTags, removedTags) = standoffUtil.findChangedStandoffTags(diplo1TextWithStandoff.standoff, diplo2TextWithStandoff.standoff)

            addedTags should be(Set(blueTag)) // Just the <blue> tag was added.
            removedTags should be(Set()) // No tags were removed.

            // The editor now corrects the editorial text to take into account the change from 'Bus' to 'Bahn'. This
            // means that the abbreviation 'd' in the transcription now has to be expanded as 'die' rather than 'der'.
            // The editor also takes the <blue> tag.

            val editorialText2 =
                """<?xml version="1.0" encoding="UTF-8"?>
                  |<paragraph id="1">Ich habe die Bahn genommen, weil ich <blue id="3">verspätet</blue> war.</paragraph>
                """.stripMargin

            val edito2TextWithStandoff: TextWithStandoff = standoffUtil.xml2TextWithStandoff(editorialText2, log = logger)

            // We now rebase the revised editorial text against the revised transcription, so they can be linked
            // together.

            val editorialStandoffDiffs3: Seq[StandoffDiff] = standoffUtil.makeStandoffDiffs(
                baseText = diplo2TextWithStandoff.text,
                derivedText = edito2TextWithStandoff.text
            )

            // Check that the editor's diffs are correct.

            val expectedEditorialDiffs3AsXml =
                """<?xml version="1.0" encoding="UTF-8"?>
                  |<diffs>Ich habe d<ins>ie</ins> Bahn <del>heute </del>genommen, weil ich verspätet war.</diffs>
                """.stripMargin

            val editorialDiffs3AsXml: String = standoffUtil.standoffDiffs2Xml(
                baseText = diplo2TextWithStandoff.text,
                derivedText = edito2TextWithStandoff.text,
                standoffDiffs = editorialStandoffDiffs3
            )

            val xmlDiff3: Diff = DiffBuilder.compare(Input.fromString(expectedEditorialDiffs3AsXml)).withTest(Input.fromString(editorialDiffs3AsXml)).build()
            xmlDiff3.hasDifferences should be(false)

        }

        "calculate diffs in markup for two versions that are identical on the literal (content) level (1)" in {

            val regionID = UUID.randomUUID

            val documentSpecificIDs = Map(
                "1" -> regionID
            )

            val standoffUtil = new XMLToStandoffUtil(documentSpecificIDs = documentSpecificIDs)

            val diplomaticTranscription =
                """<?xml version="1.0" encoding="UTF-8"?>
                  |<region id="1">
                  |<rendition type="center underline">CLI</rendition>.<lb/><lb/>
                  |<rendition type="underline">Examen modi Renaldiniani inscribendi q&#780;vis polygona regularia in circulo,<lb/>
                  |depromti ex Lib. II. de Resol. &#38; Composi: Mathem: p. 367.</rendition> (<rendition type="underline">vid. Sturmii<lb/>
                  |Mathesin enucleatam p. 38.</rendition>)
                  |</region>
                """.stripMargin

            val criticalText =
                """<?xml version="1.0" encoding="UTF-8"?>
                |<region id="1">
                |CLI<corr repl="">.</corr><lb/><lb/>
                |Examen modi Renaldiniani inscribendi <corr repl="quaevis">q&#780;vis</corr> polygona regularia in circulo,<lb/>
                |depromti ex Lib. II. de <abbr expan="Resolutione">Resol.</abbr> &#38; <abbr expan="Compositione">Composi:</abbr> <abbr expan="Mathematica">Mathem:</abbr> p. 367.<lb/> (vid<corr repl="e">.</corr> Sturmii
                |Mathesin enucleatam p. 38.)
                |</region>
                """.stripMargin

            val diploTextWithStandoff: TextWithStandoff = standoffUtil.xml2TextWithStandoff(diplomaticTranscription, log = logger)
            val criticalTextWithStandoff: TextWithStandoff = standoffUtil.xml2TextWithStandoff(criticalText, log = logger)

            val criticalTextDiffs: Seq[StandoffDiff] = standoffUtil.makeStandoffDiffs(
                baseText = diploTextWithStandoff.text,
                derivedText = criticalTextWithStandoff.text
            )

            val criticalTextDiffsAsXml: String = standoffUtil.standoffDiffs2Xml(
                baseText = diploTextWithStandoff.text,
                derivedText = criticalTextWithStandoff.text,
                standoffDiffs = criticalTextDiffs
            )

            assert(diploTextWithStandoff.text == criticalTextWithStandoff.text)

            val (standoffAdded: Set[StandoffTag], standoffRemoved: Set[StandoffTag]) = standoffUtil.findChangedStandoffTags(
                oldStandoff = diploTextWithStandoff.standoff,
                newStandoff = criticalTextWithStandoff.standoff
            )

            standoffAdded.exists(_.uuid == regionID) should be(false)
            standoffRemoved.exists(_.uuid == regionID) should be(false)

        }

        "calculate diffs in markup for two versions that are identical on the literal (content) level (2)" in {

            val regionID = UUID.randomUUID

            val documentSpecificIDs = Map(
                "2" -> regionID
            )

            val standoffUtil = new XMLToStandoffUtil(documentSpecificIDs = documentSpecificIDs)

            val diplomaticTranscription =
                """<?xml version="1.0" encoding="UTF-8"?>
                   |<region id="2">
                  |Modus hic est: Fiat triang: æquil: <formula notation="TeX">ABD</formula>, divisâq́ diametro <formula notation="TeX">AB</formula> in tot partes æquales,<lb/>
                  |quot laterum est figura inscribenda, duabusq́ earum p̃termissis <rendition type="strike">et</rendition> à <formula notation="TeX">B</formula> versùs <formula notation="TeX">A</formula>, ducat,<lb/>
                   |per initium tertiæ recta <formula notation="TeX">DF</formula>, &amp; hinc recta <formula notation="TeX">FB</formula>, quam putat esse latus polygoni optati<lb/><lb/>
                  |<rendition type="underline">Anal</rendition>: Sit secta diameter utcunq́ in <formula notation="TeX">E</formula>, <formula notation="TeX">e</formula> , ductæq́ <formula notation="TeX">DEF</formula>, <rendition type="above"><formula notation="TeX">FA</formula>,</rendition> &amp; <formula notation="TeX">FB</formula> &amp; demissa in diametrum per-<lb/>
                   |pendicularis <formula notation="TeX">FG</formula>, fiat <formula notation="TeX">CB=a</formula>, <formula notation="TeX">CE</formula> vel <formula notation="TeX">Ce=b</formula>, <formula notation="TeX">FB=x</formula>, unde <formula notation="TeX">AF=\sqrt{\mathstrut}\,\overline{4aa-xx}</formula><lb/>
                   |</region>
                """.stripMargin

            val criticalText =
                """<?xml version="1.0" encoding="UTF-8"?>
                   |<region id="2">
                  |Modus hic est: Fiat <abbr expan="triangulum">triang:</abbr> <corr repl="ae">æ</corr>quil: <formula notation="TeX">ABD</formula>, <corr repl="diviasque">divisâq́</corr> diametro <formula notation="TeX">AB</formula> in tot partes <corr repl="ae">æ</corr>quales,<lb/>
                  |quot laterum est figura inscribenda, <corr repl="duabusque">duabusq́</corr> earum <corr repl="praetermissis">p̃termissis</corr> <del>et</del> <corr repl="a">à</corr> <formula notation="TeX">B</formula> vers<corr repl="u">ù</corr>s <formula notation="TeX">A</formula>, <corr repl="ducatur">ducat</corr>,<lb/>
                   |per initium terti<corr repl="ae">æ</corr> recta <formula notation="TeX">DF</formula>, &amp; hinc recta <formula notation="TeX">FB</formula>, quam putat esse latus polygoni optati<corr repl="."></corr><lb/><lb/>
                  |<abbr expan="Analysis">Anal</abbr>: Sit secta diameter <corr repl="utunque">utcunq́</corr> in <formula notation="TeX">E</formula>, <formula notation="TeX">e</formula> , <corr repl="ductaeque">ductæq́</corr> <formula notation="TeX">DEF</formula>, <add><formula notation="TeX">FA</formula>,</add> &amp; <formula notation="TeX">FB</formula> &amp; demissa in diametrum per<corr repl="">-</corr>
                   |pendicularis <formula notation="TeX">FG</formula>, fiat <formula notation="TeX">CB=a</formula>, <formula notation="TeX">CE</formula> vel <formula notation="TeX">Ce=b</formula>, <formula notation="TeX">FB=x</formula>, unde <formula notation="TeX">AF=\sqrt{\mathstrut}\,\overline{4aa-xx}</formula><lb/>
                   |</region>
                """.stripMargin

            val diploTextWithStandoff: TextWithStandoff = standoffUtil.xml2TextWithStandoff(diplomaticTranscription, log = logger)
            val criticalTextWithStandoff: TextWithStandoff = standoffUtil.xml2TextWithStandoff(criticalText, log = logger)

            val criticalTextDiffs: Seq[StandoffDiff] = standoffUtil.makeStandoffDiffs(
                baseText = diploTextWithStandoff.text,
                derivedText = criticalTextWithStandoff.text
            )

            val criticalTextDiffsAsXml: String = standoffUtil.standoffDiffs2Xml(
                baseText = diploTextWithStandoff.text,
                derivedText = criticalTextWithStandoff.text,
                standoffDiffs = criticalTextDiffs
            )

            assert(diploTextWithStandoff.text == criticalTextWithStandoff.text)

            val (standoffAdded: Set[StandoffTag], standoffRemoved: Set[StandoffTag]) = standoffUtil.findChangedStandoffTags(
                oldStandoff = diploTextWithStandoff.standoff,
                newStandoff = criticalTextWithStandoff.standoff
            )

            standoffAdded.exists(_.uuid == regionID) should be(false)
            standoffRemoved.exists(_.uuid == regionID) should be(false)

        }

        "convert an XML document to standoff" in {

            val BEBBXML =
                """<?xml version="1.0" encoding="UTF-8"?>
                  |   <text>
                  |      <p>
                  |         <facsimile src="http://www.ub.unibas.ch/digi/bez/bernoullibriefe/jpg/bernoulli-jpg/BAU_5_000057165_321.jpg"/> Vir Celeberrime, Fautor et Amice Honoratissime. </p>
                  |      <p>Accepi, post multorum annorum silentium, jam ante aliquot menses gratissimas Litteras Tuas<ref>Johannes Scheuchzer an Johann I Bernoulli von <entity ref="1735-02-11_Scheuchzer_Johannes-Bernoulli_Johann_I">1735.02.11</entity>.</ref> cum tribus exemplis Dissertationis eruditissimae de Tesseris Badensibus,<ref>Scheuchzer, Johannes, <i>Dissertatio philosophica de tesseris Badensibus, disquisitioni publicae exposita a Johanne Scheuchzero, M. D. Philosophiae naturalis Professore publico, Acad. Naturae Curiosorum dicto Philippo. Ac respp. pro consequendo rite examine philosophico Hartmanno Friderico Oerio, Rodolfo Huldrico, Marco Wyssio, Johanne Melchiore Boeschio ...</i>, Tiguri [Zürich] (Heidegger) 1735.</ref> quorum unum mihi servatum perlegi summa cum voluptate, reliqua duo distribui, ut jussisti, Adgnato meo Nicolao meoque Filio Danieli,<ref>Nicolaus I Bernoulli (1687-1759) und Daniel I Bernoulli (1700-1782).</ref> qui ambo gratias mecum Tibi agunt maximas: Perlectio hujus speciminis mihi sustulit scrupulum, ex quorundam<ref>Im Manuskript steht "quorundum".</ref> opinione subnatum, qui credunt tesseras illas esse opus a natura productum, ob ingentem earum jam repertarum multitudinem, sed ratiocinia Tua tam valida tamque certa mihi esse videntur, ut amplius dubitare non possim easdem illas tesseras ab arte humana provenisse, quem autem in usum tanta copia fuerit fabrefacta et tam exili sub forma saltem plerasque quas egomet ipse vidi divinare non possum. Distuli responsum ad litteras Tuas Vir Clarissime, quia scio Te multis negotiis esse obrutum, sicuti et ego sum, hoc praesertim tempore quo mihi de novo impositum est Decanatus in facultate nostra munus annuum humeris meis onerosissimum, tum et silentii mei causa fuit tenuitas mearum litterarum vix portorium merentium, quam ob rationem respondere forsan diutius distulissem commodam expectans mittendi litteras occasionem, nisi me ad scribendum impulisset iterata sollicitatio Clariss. Menckenii Actorum Lips. Editoris, qui impense me urget ut sibi procurem Fratris Tui<ref>Johann Jakob Scheuchzer (1672-1733).</ref> b.m. Vitae Historiam; En ipsa ejus verba "Confido Tua opera fieri posse, ut ab Haeredibus Celeberrimi Scheuchzeri brevem vitae Scheuchzerianae narrationem impetrare possim, quo magno me tibi beneficio obstrinxeris" etc. Memini jam ante annum me eadem de re sollicitatum statim scripsisse ad Cl. Gessnerum Tuum Collegam, sed nihil responsi obtinuisse: Spero nunc Te qui Manes beatissimi Fratris, Viri celeberrimi et de republica litter. longe maxime meriti, etiamnum flagrantissime colis, non commissurum, ut Tanti Viri memoria cum exuviis sepulte maneat; Quare si desiderio Orbis eruditi non minus quam Menckenii satisfacere volueris, rogo ut succinctam Fratris defuncti Biographiam quantocyus ad me mittas, quam porro Lipsiam mittendi occasionem habebo post octo decemve dies aut ad summum in fine hujus mensis. Vale Vir Amicissime et fave T. T. Joh. Bernoulli </p>
                  |      <p>Basil. a.d. 18. Junj 1735.
                  |</p>
                  |   </text>
                  |
                """.stripMargin


            val standoffUtil = new XMLToStandoffUtil()

            val textWithStandoff: TextWithStandoff = standoffUtil.xml2TextWithStandoff(BEBBXML, log = logger)

            val root = textWithStandoff.standoff.filter((standoffTag: StandoffTag) => standoffTag match {
                case tag: HierarchicalStandoffTag => tag.parentIndex.isEmpty
                case _ => false
            })
        }
    }
}

object XMLToStandoffUtilSpec {

    val simpleXmlDoc =
        """<?xml version="1.0" encoding="UTF-8"?>
          |<article id="first">
          |    <title>Special Relativity</title>
          |
          |    <paragraph>
          |        In physics, special relativity is the generally accepted and experimentally well confirmed physical
          |        theory regarding the relationship between space and time. In <person personId="6789">Albert Einstein</person>'s
          |        original pedagogical treatment, it is based on two postulates:
          |
          |        <orderedList>
          |            <listItem>that the laws of physics are invariant (i.e. identical) in all inertial systems
          |                (non-accelerating frames of reference).</listItem>
          |            <listItem>that the speed of light in a vacuum is the same for all observers, regardless of the
          |                motion of the light source.</listItem>
          |        </orderedList>
          |    </paragraph>
          |
          |    <paragraph>
          |        <person personId="6789">Einstein</person> originally proposed it in
          |        <date value="1905" calendar="gregorian">1905</date> in <citation
          |        citationId="einstein_1905a"/>.
          |
          |        Here is a sentence with a sequence of empty tags: <foo /><bar /><baz />. And then some more text.
          |    </paragraph>
          |</article>""".stripMargin

    val xmlDocWithClix =
        """<?xml version="1.0" encoding="UTF-8"?>
          |<lg xmlns="http://www.example.org/ns1" xmlns:ns2="http://www.example.org/ns2">
          | <l>
          |  <seg foo="x" ns2:bar="y">Scorn not the sonnet;</seg>
          |  <ns2:s sID="s02"/>critic, you have frowned,</l>
          | <l>Mindless of its just honours;<ns2:s eID="s02"/>
          |  <ns2:s sID="s03"/>with this key</l>
          | <l>Shakespeare unlocked his heart;<ns2:s eID="s03"/>
          |  <ns2:s sID="s04"/>the melody</l>
          | <l>Of this small lute gave ease to Petrarch's wound.<ns2:s eID="s04"/>
          | </l>
          |</lg>
        """.stripMargin
}
