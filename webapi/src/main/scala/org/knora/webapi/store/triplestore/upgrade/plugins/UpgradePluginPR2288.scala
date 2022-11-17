package org.knora.webapi.store.triplestore.upgrade.plugins

import com.typesafe.scalalogging.Logger
import org.knora.webapi.messages.util.rdf.{RdfFeatureFactory, RdfModel, Statement}
import org.knora.webapi.store.triplestore.upgrade.UpgradePlugin

import scala.concurrent.duration.DurationLong

class UpgradePluginPR2288(log: Logger) extends UpgradePlugin {
  private val nodeFactory     = RdfFeatureFactory.getRdfNodeFactory()
  private val creationDateIri = nodeFactory.makeIriNode("http://www.knora.org/ontology/knora-base#creationDate")
  private val lastModDateIri  = nodeFactory.makeIriNode("http://www.knora.org/ontology/knora-base#lastModificationDate")

  override def transform(model: RdfModel): Unit = {
    val t0 = System.nanoTime()
    log.info(s"Starting ${this.getClass.getSimpleName}")

    val statementsWithCreationDateIri =
      LazyList.from(model.find(subj = None, pred = Some(creationDateIri), obj = None))

    val subjectHasNoLastModificationDate: Statement => Boolean = statement =>
      model.find(subj = Some(statement.subj), pred = Some(lastModDateIri), obj = None).isEmpty

    val newStatements = statementsWithCreationDateIri
      .filter(subjectHasNoLastModificationDate)
      .map(statement => nodeFactory.makeStatement(statement.subj, lastModDateIri, statement.obj))
      .toSet

    model.addStatements(newStatements)

    log.info(s"Created ${newStatements.size} new statements:\n${newStatements.mkString("++ ", "\n", "")}")
    log.info(s"Finished ${this.getClass.getSimpleName} in " + (System.nanoTime() - t0).nanos.toCoarsest.toString())
  }
}
