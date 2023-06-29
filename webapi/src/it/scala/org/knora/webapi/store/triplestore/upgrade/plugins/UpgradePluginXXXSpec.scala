package org.knora.webapi.store.triplestore.upgrade.plugins

import com.typesafe.scalalogging.LazyLogging
import org.knora.webapi.messages.util.rdf._

class UpgradePluginXXXSpec extends UpgradePluginSpec with LazyLogging {
  private val nodeFactory: RdfNodeFactory = RdfFeatureFactory.getRdfNodeFactory()

  "Upgrade plugin XXX" should {
    "transform knora-base:hasComment to FormattedTextValue" in {
      // run the model
      val model: RdfModel = trigFileToModel("../test_data/upgrade/xxx/xxx_a.trig")
      val plugin          = new UpgradePluginXXX(log)
      plugin.transform(model)

      // check the ontology
      val repo = model.asRepository
      val query =
        """|PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
           |PREFIX salsah-gui: <http://www.knora.org/ontology/salsah-gui#>
           |ASK 
           |FROM <http://www.knora.org/ontology/knora-base> 
           |WHERE {
           |    BIND (knora-base:hasComment as ?hc)
           |    ?hc knora-base:objectClassConstraint knora-base:FormattedTextValue .
           |    FILTER NOT EXISTS { ?hc salsah-gui:guiElement ?guiElement . }
           |    FILTER NOT EXISTS { ?hc salsah-gui:guiAttribute ?guiAttribute . }
           |}
           |""".stripMargin
      val askResult = repo.doAsk(query)
      assert(askResult, "ASK should find knora-base:hasComment with the correctly updated shape")

      // check the data
      val query2 =
        """|PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
           |SELECT *
           |FROM <http://www.knora.org/data/0001/anything>
           |WHERE {
           |    ?s knora-base:hasComment ?v .
           |    ?v a knora-base:FormattedTextValue .
           |    ?v knora-base:valueHasString ?stringRepresentation . 
           |    ?v knora-base:valueHasMapping <http://rdfh.ch/standoff/mappings/StandardMapping> .
           |}
           |""".stripMargin

      val queryResult2: SparqlSelectResult = repo.doSelect(selectQuery = query2)
      val resBody: SparqlSelectResultBody  = queryResult2.results
      assert(resBody.bindings.size == 1)

      val foundResource: VariableResultsRow = resBody.bindings.head
      val subj: Option[String]              = foundResource.rowMap.get("s")
      assert(subj.contains("http://rdfh.ch/0001/2tk24CSISgemApos3pH26Q"))
      val stringRepresentation: Option[String] = foundResource.rowMap.get("stringRepresentation")
      assert(stringRepresentation.contains("a text value without markup\u001e"))
    }
  }
}

/*
 *
 * What needs to be covered in the test:
 *
 *   - [ ] removing GuiElement and GuiAttribute in ontology
 *   - [ ] removing old type TextValue in ontology
 *   - [ ] adding new type (Und-)FormattedTextValue in ontology
 *   - [ ] removing old type TextValue in data
 *   - [ ] adding new type (Und-)FormattedTextValue in data
 *
 *   - [ ] if no mapping in data and type in onto is simpleText/Paragraph, then use UnformattedTextValue
 *   - [ ] if standard mapping in data and type in onto is simpleText/Paragraph, then use FormattedTextValue
 *   - [ ] if no mapping in data and type in onto is richtext, then use FormattedTextValue
 *   - [ ] if standard mapping in data and type in onto is richtext, then use FormattedTextValue
 *   - [ ] if custom mapping in data and type in onto is richtext, then use CustomFormattedTextValue
 *
 *   - [ ] if data mixes standard and custom mapping, then throw exception
 *   - [ ] if data mixes standard mapping and no mapping, then add minimal standoff to the ones without mapping
 *
 *   - [ ] knora-base:hasComment is transformed to FormattedTextValue
 *
 */
