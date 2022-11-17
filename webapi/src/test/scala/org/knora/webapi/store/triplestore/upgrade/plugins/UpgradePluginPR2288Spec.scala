package org.knora.webapi.store.triplestore.upgrade.plugins

import com.typesafe.scalalogging.LazyLogging
import org.knora.webapi.messages.util.rdf.{RdfFeatureFactory, RdfModel, RdfNodeFactory, Statement}

class UpgradePluginPR2288Spec extends UpgradePluginSpec with LazyLogging {
  private val nodeFactory: RdfNodeFactory = RdfFeatureFactory.getRdfNodeFactory()

  "Upgrade plugin PR2288" should {
    "tata" in {
      // Parse the input file.
      val creationDateIriNode = nodeFactory.makeIriNode("http://www.knora.org/ontology/knora-base#creationDate")
      val lastModificationDateIriNode =
        nodeFactory.makeIriNode("http://www.knora.org/ontology/knora-base#lastModificationDate")
      val model: RdfModel = trigFileToModel("test_data/upgrade/pr2288.trig")
      val allWithCreationDateIterator = model.find(
        subj = None,
        pred = Some(creationDateIriNode),
        obj = None,
        context = None
      )

      var statementsToAdd: List[Statement] = List()
      while (allWithCreationDateIterator.hasNext) {
        val current = allWithCreationDateIterator.next
        val lastModification =
          model.find(subj = Some(current.subj), pred = Some(lastModificationDateIriNode), obj = None, context = None)
        if (lastModification.isEmpty) {
          log.info(s"No lastMod found for ${current.subj} will add from creationDate ${current.obj}")
          val newMod: Statement = nodeFactory.makeStatement(current.subj, lastModificationDateIriNode, current.obj)
          statementsToAdd = statementsToAdd.prepended(newMod)
        }
        log.info("Would add: " + statementsToAdd.map(_.toString).mkString("\n"))
        log.info(current.toString)
      }

      model.addStatements(statementsToAdd.toSet)
      log.info("Model after:\n" + model.mkString("\n"))
      // Use the plugin to transform the input.
      val plugin = new UpgradePluginPR2288(log)
      plugin.transform(model)

    }
  }
}
