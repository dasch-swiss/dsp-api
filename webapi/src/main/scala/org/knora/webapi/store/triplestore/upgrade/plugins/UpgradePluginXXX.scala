/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.triplestore.upgrade.plugins

import org.knora.webapi.IRI
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.util.rdf._
import org.knora.webapi.store.triplestore.upgrade.UpgradePlugin
import org.knora.webapi.messages.util.rdf.jenaimpl.JenaStatement
import org.knora.webapi.messages.util.rdf.jenaimpl.JenaIriNode

class UpgradePluginXXX() extends UpgradePlugin {
  private val nodeFactory: RdfNodeFactory = RdfFeatureFactory.getRdfNodeFactory()

  override def transform(model: RdfModel): Unit = {
    val textValuePropsPerOntology = collectTextValueProps(model)

    println(textValuePropsPerOntology)

    val textValueProps = textValuePropsPerOntology.values.flatten.toSet

    println(textValueProps)

    //
    // val graphs: Set[IRI] = collectGraphs(model)
    // println(graphs)
    // val projects: Set[IRI] = collectProjects(model)
    // println(projects)

    // val modelsPerGraph: Map[IRI, RdfModel] =
    //   model.groupBy(_.context).map { case (k, v) => k.map(_ -> v.asInstanceOf[RdfModel]) }.flatten.toMap
    // println(modelsPerGraph.view.mapValues(_.size).toMap)

    // val ontologies: Map[IRI, RdfModel] = modelsPerGraph.filter(_._1.contains("/ontology/"))
    // println(ontologies.keySet)

    // val textValuePropsPerOntology = ontologies.toSeq.map { case (k, v) => k -> collectTextValueProps(v) }
    // println(textValuePropsPerOntology)
  }

  private def collectTextValueProps(model: RdfModel): Map[IRI, Seq[IRI]] =
    model
      .find(
        None,
        Some(nodeFactory.makeIriNode(OntologyConstants.KnoraBase.ObjectClassConstraint)),
        Some(nodeFactory.makeIriNode(OntologyConstants.KnoraBase.TextValue))
      )
      .map(s => s.context.map(_ -> s.subj.stringValue.asInstanceOf[IRI]))
      .flatten
      .foldLeft(Map.empty[IRI, Seq[IRI]]) { case (acc, (k, v)) =>
        acc + (k -> (acc.getOrElse(k, Seq.empty[IRI]) :+ v))
      }

  // private def collectGraphs(model: RdfModel): Set[IRI] =
  //   model.map(_.context).toSet.flatten

  // private def collectProjects(model: RdfModel): Set[String] =
  //   model
  //     .find(None, Some(nodeFactory.makeIriNode(OntologyConstants.KnoraBase.AttachedToProject)), None)
  //     .map(_.obj.stringValue)
  //     .toSet

  // private def collectTextValueProps(model: RdfModel): Set[IRI] =
  //   model
  //     .find(
  //       None,
  //       Some(nodeFactory.makeIriNode(OntologyConstants.KnoraBase.ObjectClassConstraint)),
  //       Some(nodeFactory.makeIriNode(OntologyConstants.KnoraBase.TextValue))
  //     )
  //     .map(_.subj.stringValue.asInstanceOf[IRI])
  //     .toSet

  /*
   * TODO:
   *  - go project by project
   *    -> find all projects
   *    - for each project, go ontology by ontology
   *      -> find all ontologies of the project
   *      - for each ontology, find all properties that have a TextValue as object
   *         - explicitely defined
   *         - inherited from kb:hasComment
   *           -> get the propertie's guiElement and optionally guiAttribute
   *           -> remove the guiElement and guiAttribute
   *         - if guiElement was Richtext, see if the data uses StandardMapping (or no mapping at all) or any other mapping
   *           -> if StandardMapping or no mapping, make the objectClassConstraint FormattedTextValue
   *           -> if any other mapping, make the objectClassConstraint CustomFormattedTextValue
   *           -> if standard mapping and custom mapping are used, crash for now!
   *        - if guiElement was SimpleText or Textarea, see if the data uses any mapping
   *           -> if yes, treat as above
   *           -> if no, make the objectClassConstraint UnformattedTextValue
   *    - for each project data set, adjust the data accordingly
   *      - for each of the identified properties used in data, get the value object the property points to
   *        -> adjust the rdf:type according to the determined new objectClassConstraint of the property
   * ... more?
   */

}
