/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.util.standoff

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.xmlunit.builder.DiffBuilder
import org.xmlunit.builder.Input
import org.xmlunit.diff.Diff

import java.util.UUID

import org.knora.webapi.messages.StringFormatter

/**
 * Tests [[XMLToStandoffUtil]].
 */
class XMLToStandoffUtilSpec extends AnyWordSpec with Matchers {

  "The XML to standoff utility" should {

    "convert an XML document to text with standoff, then back to an equivalent XML document" in {
      val standoffUtil = new XMLToStandoffUtil(writeUuidsToXml = false)

      // Convert the XML document to text with standoff.
      val textWithStandoff: TextWithStandoff =
        standoffUtil.xml2TextWithStandoff(XMLToStandoffUtilSpec.simpleXmlDoc)

      // Convert the text with standoff back to XML.
      val backToXml: String = standoffUtil.textWithStandoff2Xml(textWithStandoff)

      // Compare the original XML with the regenerated XML.
      val xmlDiff: Diff = DiffBuilder
        .compare(Input.fromString(XMLToStandoffUtilSpec.simpleXmlDoc))
        .withTest(Input.fromString(backToXml))
        .build()
      xmlDiff.hasDifferences should be(false)
    }

    "convert an XML document with one nested empty tag to text with standoff, then back to an equivalent XML document" in {
      val standoffUtil = new XMLToStandoffUtil(writeUuidsToXml = false)

      // Convert the XML document to text with standoff.
      val textWithStandoff: TextWithStandoff =
        standoffUtil.xml2TextWithStandoff(XMLToStandoffUtilSpec.simpleXmlDocWithNestedEmptyTag)

      // Convert the text with standoff back to XML.
      val backToXml: String = standoffUtil.textWithStandoff2Xml(textWithStandoff)

      // Compare the original XML with the regenerated XML.
      val xmlDiff: Diff = DiffBuilder
        .compare(Input.fromString(XMLToStandoffUtilSpec.simpleXmlDocWithNestedEmptyTag))
        .withTest(Input.fromString(backToXml))
        .build()
      xmlDiff.hasDifferences should be(false)
    }

    "convert an XML document with multiple nested empty tags to text with standoff, then back to an equivalent XML document" in {
      val standoffUtil = new XMLToStandoffUtil(writeUuidsToXml = false)

      // Convert the XML document to text with standoff.
      val textWithStandoff: TextWithStandoff =
        standoffUtil.xml2TextWithStandoff(XMLToStandoffUtilSpec.simpleXmlDocWithNestedEmptyTags)

      // Convert the text with standoff back to XML.
      val backToXml: String = standoffUtil.textWithStandoff2Xml(textWithStandoff)

      // Compare the original XML with the regenerated XML.
      val xmlDiff: Diff = DiffBuilder
        .compare(Input.fromString(XMLToStandoffUtilSpec.simpleXmlDocWithNestedEmptyTags))
        .withTest(Input.fromString(backToXml))
        .build()
      xmlDiff.hasDifferences should be(false)
    }

    "convert an XML document with namespaces and CLIX milestones to standoff, then back to an equivalent XML document" in {
      val documentSpecificIDs = Map(
        "s02" -> UUID.randomUUID,
        "s03" -> UUID.randomUUID,
        "s04" -> UUID.randomUUID,
      )

      val standoffUtil = new XMLToStandoffUtil(
        defaultXmlNamespace = Some("http://www.example.org/ns1"),
        xmlNamespaces = Map("ns2" -> "http://www.example.org/ns2"),
        writeUuidsToXml = false,
        documentSpecificIDs = documentSpecificIDs,
      )

      // Convert the XML document to text with standoff.
      val textWithStandoff: TextWithStandoff =
        standoffUtil.xml2TextWithStandoff(XMLToStandoffUtilSpec.xmlDocWithClix)

      // Convert the text with standoff back to XML.
      val backToXml = standoffUtil.textWithStandoff2Xml(textWithStandoff)

      // Compare the original XML with the regenerated XML.
      val xmlDiff: Diff = DiffBuilder
        .compare(Input.fromString(XMLToStandoffUtilSpec.xmlDocWithClix))
        .withTest(Input.fromString(backToXml))
        .build()
      xmlDiff.hasDifferences should be(false)
    }

    "convert an XML document to a TextWithStandoff and check that information separator two has been inserted in the string" in {

      val BEBBXML =
        """<?xml version="1.0" encoding="UTF-8"?>
          |   <text>
          |      <p>
          |         <facsimile src="http://www.ub.unibas.ch/digi/bez/bernoullibriefe/jpg/bernoulli-jpg/BAU_5_000057165_321.jpg"/> Vir Celeberrime, Fautor et Amice Honoratissime. </p>
          |      <p>Accepi, post multorum annorum silentium, jam ante aliquot menses gratissimas Litteras Tuas<ref>Johannes Scheuchzer an Johann I Bernoulli von <entity ref="1735-02-11_Scheuchzer_Johannes-Bernoulli_Johann_I">1735.02.11</entity>.</ref> cum tribus exemplis Dissertationis eruditissimae de Tesseris Badensibus,<ref>Scheuchzer, Johannes, <i>Dissertatio philosophica de tesseris Badensibus, disquisitioni publicae exposita a Johanne Scheuchzero, M. D. Philosophiae naturalis Professore publico, Acad. Naturae Curiosorum dicto Philippo. Ac respp. pro consequendo rite examine philosophico Hartmanno Friderico Oerio, Rodolfo Huldrico, Marco Wyssio, Johanne Melchiore Boeschio ...</i>, Tiguri [Zürich] (Heidegger) 1735.</ref> quorum unum mihi servatum perlegi summa cum voluptate, reliqua duo distribui, ut jussisti, Adgnato meo Nicolao meoque Filio Danieli,<ref>Nicolaus I Bernoulli (1687-1759) und Daniel I Bernoulli (1700-1782).</ref> qui ambo gratias mecum Tibi agunt maximas: Perlectio hujus speciminis mihi sustulit scrupulum, ex quorundam<ref>Im Manuskript steht "quorundum".</ref> opinione subnatum, qui credunt tesseras illas esse opus a natura productum, ob ingentem earum jam repertarum multitudinem, sed ratiocinia Tua tam valida tamque certa mihi esse videntur, ut amplius dubitare non possim easdem illas tesseras ab arte humana provenisse, quem autem in usum tanta copia fuerit fabrefacta et tam exili sub forma saltem plerasque quas egomet ipse vidi divinare non possum. Distuli responsum ad litteras Tuas Vir Clarissime, quia scio Te multis negotiis esse obrutum, sicuti et ego sum, hoc praesertim tempore quo mihi de novo impositum est Decanatus in facultate nostra munus annuum humeris meis onerosissimum, tum et silentii mei causa fuit tenuitas mearum litterarum vix portorium merentium, quam ob rationem respondere forsan diutius distulissem commodam expectans mittendi litteras occasionem, nisi me ad scribendum impulisset iterata sollicitatio Clariss. Menckenii Actorum Lips. Editoris, qui impense me urget ut sibi procurem Fratris Tui<ref>Johann Jakob Scheuchzer (1672-1733).</ref> b.m. Vitae Historiam; En ipsa ejus verba "Confido Tua opera fieri posse, ut ab Haeredibus Celeberrimi Scheuchzeri brevem vitae Scheuchzerianae narrationem impetrare possim, quo magno me tibi beneficio obstrinxeris" etc. Memini jam ante annum me eadem de re sollicitatum statim scripsisse ad Cl. Gessnerum Tuum Collegam, sed nihil responsi obtinuisse: Spero nunc Te qui Manes beatissimi Fratris, Viri celeberrimi et de republica litter. longe maxime meriti, etiamnum flagrantissime colis, non commissurum, ut Tanti Viri memoria cum exuviis sepulte maneat; Quare si desiderio Orbis eruditi non minus quam Menckenii satisfacere volueris, rogo ut succinctam Fratris defuncti Biographiam quantocyus ad me mittas, quam porro Lipsiam mittendi occasionem habebo post octo decemve dies aut ad summum in fine hujus mensis. Vale Vir Amicissime et fave T. T. Joh. Bernoulli </p>
          |      <p>Basil. a.d. 18. Junj 1735.</p>
          |   </text>
          |
                """.stripMargin

      val standoffUtil = new XMLToStandoffUtil()

      // after every paragraph, information separator two should be inserted
      val textWithStandoff: TextWithStandoff = standoffUtil.xml2TextWithStandoff(
        BEBBXML,
        tagsWithSeparator = List(XMLTagSeparatorRequired(maybeNamespace = None, tagname = "p", maybeClassname = None)),
      )

      // make sure that there are as many information separator two as there are paragraphs (there are three paragraphs)
      assert(StringFormatter.INFORMATION_SEPARATOR_TWO.toString.r.findAllIn(textWithStandoff.text).length == 3)

    }

    "convert an XML document containing elements with classes to a TextWithStandoff and check that information separator two has been inserted in the string" in {

      val testXML =
        """<?xml version="1.0" encoding="UTF-8"?>
          |   <text>
          |      <text documentType="html">
          |                    <div class="paragraph">
          |                        This an element that has a class and it separates words.
          |                    </div>
          |                </text>
          |   </text>
          |
                """.stripMargin

      val standoffUtil = new XMLToStandoffUtil()

      // after every <div class="paragraph">, information separator two should be inserted
      val textWithStandoff: TextWithStandoff = standoffUtil.xml2TextWithStandoff(
        testXML,
        tagsWithSeparator =
          List(XMLTagSeparatorRequired(maybeNamespace = None, tagname = "div", maybeClassname = Some("paragraph"))),
      )

      // make sure that there are as many information separator two as there are paragraphs (there are three paragraphs)
      assert(StringFormatter.INFORMATION_SEPARATOR_TWO.toString.r.findAllIn(textWithStandoff.text).length == 1)

    }
  }
}

object XMLToStandoffUtilSpec {

  val simpleXmlDoc: String =
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

  val simpleXmlDocWithNestedEmptyTag: String =
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
      |        Here is a sentence with a sequence of <span><br/></span> empty tags: <foo /><bar /><baz />. And then some more text.
      |    </paragraph>
      |</article>""".stripMargin

  val simpleXmlDocWithNestedEmptyTags: String =
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
      |        Here is a sentence with a sequence of <span><span><br/><br/></span></span> empty tags: <foo /><bar /><baz />. And then some more text.
      |    </paragraph>
      |</article>""".stripMargin

  val xmlDocWithClix: String =
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
