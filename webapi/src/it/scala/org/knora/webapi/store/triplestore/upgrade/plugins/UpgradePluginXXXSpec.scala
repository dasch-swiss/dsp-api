package org.knora.webapi.store.triplestore.upgrade.plugins

import com.typesafe.scalalogging.LazyLogging
import org.knora.webapi.messages.util.rdf._
import org.knora.webapi.messages.OntologyConstants

class UpgradePluginXXXSpec extends UpgradePluginSpec with LazyLogging {
  private val nodeFactory: RdfNodeFactory = RdfFeatureFactory.getRdfNodeFactory()

  "Upgrade plugin XXX" should {
    "transform knora-base:hasComment to FormattedTextValue" in {
      // run the model
      val model: RdfModel = trigFileToModel("../test_data/upgrade/xxx/xxx_a.trig")
      val plugin          = new UpgradePluginXXX(log)
      plugin.transform(model)
      val repo = model.asRepository

      // check the ontology
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

    "only adjust the objectClassConstraint and the Type, if the ontology and mappings in data align" in {
      // run the model
      val model: RdfModel = trigFileToModel("../test_data/upgrade/xxx/xxx_b.trig")
      val plugin          = new UpgradePluginXXX(log)
      plugin.transform(model)
      val repo = model.asRepository

      // check the ontology
      val query1 =
        """|SELECT ?p ?o 
           |FROM <http://www.knora.org/ontology/0001/freetest>
           |WHERE { <http://www.knora.org/ontology/0001/freetest#hasSimpleText> ?p ?o . }
           |""".stripMargin
      val res1: SparqlSelectResult     = repo.doSelect(query1)
      val resMap1: Map[String, String] = res1.results.bindings.map(row => row.rowMap("p") -> row.rowMap("o")).toMap
      val expected1 = Map(
        OntologyConstants.Rdf.Type                         -> "http://www.w3.org/2002/07/owl#ObjectProperty",
        OntologyConstants.Rdfs.Label                       -> "Simple Text",
        OntologyConstants.Rdfs.SubPropertyOf               -> OntologyConstants.KnoraBase.HasValue,
        OntologyConstants.KnoraBase.SubjectClassConstraint -> "http://www.knora.org/ontology/0001/freetest#FreeTest",
        OntologyConstants.KnoraBase.ObjectClassConstraint  -> OntologyConstants.KnoraBase.UnformattedTextValue
      )
      assert(resMap1 == expected1)
      val query2 =
        """|SELECT ?p ?o 
           |FROM <http://www.knora.org/ontology/0001/freetest>
           |WHERE { <http://www.knora.org/ontology/0001/freetest#hasTextareaText> ?p ?o . }
           |""".stripMargin
      val res2: SparqlSelectResult     = repo.doSelect(query2)
      val resMap2: Map[String, String] = res2.results.bindings.map(row => row.rowMap("p") -> row.rowMap("o")).toMap
      val expected2 = Map(
        OntologyConstants.Rdf.Type                         -> "http://www.w3.org/2002/07/owl#ObjectProperty",
        OntologyConstants.Rdfs.Label                       -> "Text Area Text",
        OntologyConstants.Rdfs.SubPropertyOf               -> OntologyConstants.KnoraBase.HasValue,
        OntologyConstants.KnoraBase.SubjectClassConstraint -> "http://www.knora.org/ontology/0001/freetest#FreeTest",
        OntologyConstants.KnoraBase.ObjectClassConstraint  -> OntologyConstants.KnoraBase.UnformattedTextValue
      )
      assert(resMap2 == expected2)
      val query3 =
        """|SELECT ?p ?o 
           |FROM <http://www.knora.org/ontology/0001/freetest>
           |WHERE { <http://www.knora.org/ontology/0001/freetest#hasRichText> ?p ?o . }
           |""".stripMargin
      val res3: SparqlSelectResult     = repo.doSelect(query3)
      val resMap3: Map[String, String] = res3.results.bindings.map(row => row.rowMap("p") -> row.rowMap("o")).toMap
      val expected3 = Map(
        OntologyConstants.Rdf.Type                         -> "http://www.w3.org/2002/07/owl#ObjectProperty",
        OntologyConstants.Rdfs.Label                       -> "Richtext Text",
        OntologyConstants.Rdfs.SubPropertyOf               -> OntologyConstants.KnoraBase.HasValue,
        OntologyConstants.KnoraBase.SubjectClassConstraint -> "http://www.knora.org/ontology/0001/freetest#FreeTest",
        OntologyConstants.KnoraBase.ObjectClassConstraint  -> OntologyConstants.KnoraBase.FormattedTextValue
      )
      assert(resMap3 == expected3)

      // check the data
      val query4 =
        """|PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
           |PREFIX freetest: <http://www.knora.org/ontology/0001/freetest#>
           |ASK
           |FROM <http://www.knora.org/data/0001/freetest>
           |WHERE {
           |    BIND(<http://rdfh.ch/0001/VkOHrWPzS2OZkQtCyYT3ng> as ?s)
           |    ?s a freetest:FreeTest .
           |    ?s freetest:hasSimpleText ?simpleText .
           |    ?s freetest:hasTextareaText ?textareaText .
           |    ?s freetest:hasRichText ?richText .
           |}
           |""".stripMargin
      val res4: Boolean = repo.doAsk(query4)
      assert(res4, "The FreeTest resource should still have exactly 3 text values.")
      val query5 =
        """|PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
           |ASK
           |FROM <http://www.knora.org/data/0001/freetest>
           |WHERE {
           |    BIND (<http://rdfh.ch/0001/VkOHrWPzS2OZkQtCyYT3ng/values/AdEsJfjFT5Ox07BC8ztUDg> as ?s)
           |    ?s a knora-base:UnformattedTextValue .
           |    ?s knora-base:valueHasString "simple text" .
           |}
           |""".stripMargin
      val res5: Boolean = repo.doAsk(query5)
      assert(res5, "The simple text value should have an updated type but the same text value.")
      val query6 =
        """|PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
           |ASK
           |FROM <http://www.knora.org/data/0001/freetest>
           |WHERE {
           |    BIND (<http://rdfh.ch/0001/VkOHrWPzS2OZkQtCyYT3ng/values/d71beeUvQAqMueB6eRZfVA> as ?s)
           |    ?s a knora-base:UnformattedTextValue .
           |    ?s knora-base:valueHasString "textarea text" .
           |}
           |""".stripMargin
      val res6: Boolean = repo.doAsk(query6)
      assert(res6, "The textarea text value should have an updated type but the same text value.")
      val query7 =
        """|PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
           |ASK
           |FROM <http://www.knora.org/data/0001/freetest>
           |WHERE {
           |    BIND (<http://rdfh.ch/0001/VkOHrWPzS2OZkQtCyYT3ng/values/irjNZuctTCmg3NvaVL751g> as ?s)
           |    ?s a knora-base:FormattedTextValue .
           |    ?s knora-base:valueHasString "richtext text with formatting" .
           |    ?s knora-base:valueHasMapping <http://rdfh.ch/standoff/mappings/StandardMapping> .
           |    ?s knora-base:valueHasStandoff <http://rdfh.ch/0001/VkOHrWPzS2OZkQtCyYT3ng/values/irjNZuctTCmg3NvaVL751g/standoff/0> .
           |    ?s knora-base:valueHasStandoff <http://rdfh.ch/0001/VkOHrWPzS2OZkQtCyYT3ng/values/irjNZuctTCmg3NvaVL751g/standoff/1> .
           |    ?s knora-base:valueHasStandoff <http://rdfh.ch/0001/VkOHrWPzS2OZkQtCyYT3ng/values/irjNZuctTCmg3NvaVL751g/standoff/2> .
           |}
           |""".stripMargin
      val res7: Boolean = repo.doAsk(query7)
      assert(res7, "The richtext text value should have an updated type but the same text value, mapping and standoff.")
    }
  }
}

/*
 *
 * What needs to be covered in the test:
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
 */
