package org.knora.webapi.store.triplestore.upgrade.plugins

import com.typesafe.scalalogging.LazyLogging
import org.knora.webapi.messages.util.rdf._

class UpgradePluginPR2288Spec extends UpgradePluginSpec with LazyLogging {

  val plugin = new UpgradePluginPR2288(log)

  val nf = RdfFeatureFactory.getRdfNodeFactory()
  val lastModDateIri = nf.makeIriNode("http://www.knora.org/ontology/knora-base#lastModificationDate")
  val thingWithoutIri = nf.makeIriNode("http://rdfh.ch/0001/thing-without-mod-date")
  val thingWithoutValue =
    nf.makeDatatypeLiteral("2020-01-01T10:00:00.673298Z", "http://www.w3.org/2001/XMLSchema#dateTime")
  val thingWithIri = nf.makeIriNode("http://rdfh.ch/0001/thing-with-mod-date")
  val thingWithValue =
    nf.makeDatatypeLiteral("2020-03-01T10:00:00.673298Z", "http://www.w3.org/2001/XMLSchema#dateTime")

  val modelStr =
    """
      |@prefix xsd:         <http://www.w3.org/2001/XMLSchema#> .
      |@prefix knora-base:  <http://www.knora.org/ontology/knora-base#> .
      |@prefix anything:    <http://www.knora.org/ontology/0001/anything#> .
      |
      |<http://rdfh.ch/0001/thing-without-mod-date>
      |    a                            anything:Thing ;
      |    knora-base:creationDate      "2020-01-01T10:00:00.673298Z"^^xsd:dateTime .
      |
      |<http://rdfh.ch/0001/thing-with-mod-date>
      |    a                               anything:Thing ;
      |    knora-base:creationDate         "2020-02-01T10:00:00.673298Z"^^xsd:dateTime ;
      |    knora-base:lastModificationDate "2020-03-01T10:00:00.673298Z"^^xsd:dateTime  .
      |
      |""".stripMargin

  "Upgrade plugin PR2288" should {
    "add a statement if creationDate is given but no lastModificationDate" in {
      val model: RdfModel = stringToModel(modelStr)
      val sizeBefore = model.size

      plugin.transform(model)

      val expected = nf.makeStatement(thingWithoutIri, lastModDateIri, thingWithoutValue)
      assert(model.contains(expected), "Statement is present")
      assert(model.size - sizeBefore == 1, "One statement was added ")
    }
    "not change existing statements if creationDate and lastModificationDate are present" in {
      val model: RdfModel = stringToModel(modelStr)

      plugin.transform(model)

      val expected = nf.makeStatement(thingWithIri, lastModDateIri, thingWithValue)
      assert(model.contains(expected))
    }
  }
}
