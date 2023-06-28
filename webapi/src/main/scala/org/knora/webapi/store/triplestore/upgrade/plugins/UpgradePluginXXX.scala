/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.triplestore.upgrade.plugins

import com.typesafe.scalalogging.Logger
import dsp.constants.SalsahGui
import dsp.errors.InconsistentRepositoryDataException
import org.knora.webapi.IRI
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.util.rdf._
import org.knora.webapi.store.triplestore.upgrade.UpgradePlugin
import org.knora.webapi.messages.util.rdf.jenaimpl.JenaIriNode

sealed trait TextType
object TextType {
  case object UnformattedText     extends TextType
  case object FormattedText       extends TextType
  case object CustomFormattedText extends TextType

  def fromIri(iri: IRI): TextType =
    iri match {
      case SalsahGui.Textarea | SalsahGui.SimpleText => UnformattedText
      case SalsahGui.Richtext                        => FormattedText
      case _                                         => throw InconsistentRepositoryDataException(s"Unknown text type: $iri")
    }
}

final case class TextValueProp(
  graph: IRI,
  project: IRI,
  ontology: IRI,
  iri: IRI,
  guiElement: IRI,
  textType: TextType,
  statementsToRemove: Set[Statement]
)

class UpgradePluginXXX(log: Logger) extends UpgradePlugin {
  private val predicatesToRemove: Set[IRI] = Set(
    OntologyConstants.KnoraBase.ObjectClassConstraint,
    SalsahGui.GuiElementProp,
    SalsahGui.GuiAttribute
  )

  private val nodeFactory: RdfNodeFactory = RdfFeatureFactory.getRdfNodeFactory()

  override def transform(model: RdfModel): Unit = {
    log.info("Transformation started.")
    println("Transformation started.")

    val ontologyToProjectMap: Map[IRI, IRI] = collectOntologyToProjectMap(model)
    log.debug(s"Found ${ontologyToProjectMap.size} ontologies for ${ontologyToProjectMap.values.toSet.size} projects.")
    println(s"Found ${ontologyToProjectMap.size} ontologies for ${ontologyToProjectMap.values.toSet.size} projects.")
    val textValuePropsPerOntology: Map[IRI, Seq[IRI]] = collectTextValuePropIris(model)
    log.debug(s"Found ${textValuePropsPerOntology.values.flatten.size} TextValue properties.")
    println(s"Found ${textValuePropsPerOntology.values.flatten.size} TextValue properties.")
    // val textValuePropIris         = textValuePropsPerOntology.values.flatten.toSet

    val textValueProps = makeTextValueProps(model, textValuePropsPerOntology, ontologyToProjectMap)
    // textValueProps.foreach(p => println(p))

    log.info("Transformation finished successfully.")
    println("Transformation finished successfully.")
  }

  def collectOntologyToProjectMap(model: RdfModel): Map[IRI, IRI] = {
    val ontologies: Set[IRI] =
      model
        .find(
          None,
          Some(nodeFactory.makeIriNode(OntologyConstants.Rdf.Type)),
          Some(nodeFactory.makeIriNode(OntologyConstants.Owl.Ontology))
        )
        .map(_.subj.stringValue.asInstanceOf[IRI])
        .toSet

    ontologies.map { ontology =>
      val project: IRI =
        model
          .find(
            Some(nodeFactory.makeIriNode(ontology)),
            Some(nodeFactory.makeIriNode(OntologyConstants.KnoraBase.AttachedToProject)),
            None
          )
          .map(_.obj.stringValue.asInstanceOf[IRI])
          .toList
          .head
      ontology -> project
    }.toMap
  }

  private def collectTextValuePropIris(model: RdfModel): Map[IRI, Seq[IRI]] =
    model
      .find(
        None,
        Some(nodeFactory.makeIriNode(OntologyConstants.KnoraBase.ObjectClassConstraint)),
        Some(nodeFactory.makeIriNode(OntologyConstants.KnoraBase.TextValue))
      )
      .flatMap(s => s.context.map(_ -> s.subj.stringValue.asInstanceOf[IRI]))
      .foldLeft(Map.empty[IRI, Seq[IRI]]) { case (acc, (k, v)) =>
        acc + (k -> (acc.getOrElse(k, Seq.empty[IRI]) :+ v))
      }

  private def makeTextValueProps(
    model: RdfModel,
    props: Map[IRI, Seq[IRI]],
    ontoToProjectMap: Map[IRI, IRI]
  ): Set[TextValueProp] =
    props.flatMap { case (onto: IRI, propIris: Seq[IRI]) =>
      val project: IRI = ontoToProjectMap.getOrElse(
        onto,
        throw InconsistentRepositoryDataException(s"Ontology $onto is not attached to a project")
      )
      val y: Seq[TextValueProp] = propIris.map(prop => makeTextValueProp(model, prop, project, onto))
      y.toSet
    }.toSet

  private def makeTextValueProp(model: RdfModel, prop: IRI, project: IRI, onto: IRI): TextValueProp = {
    val definitionStatements = model
      .find(
        Some(nodeFactory.makeIriNode(prop)),
        None,
        None
      )
      .toList
    val definitionByPredicate: Map[IRI, Seq[Statement]] =
      definitionStatements.groupBy(_.pred.stringValue.asInstanceOf[IRI])

    val statementsToRemove: Set[Statement] =
      predicatesToRemove.foldLeft(Set.empty[Statement]) { case (acc, pred) =>
        acc ++ definitionByPredicate.getOrElse(pred, Seq.empty[Statement])
      }

    val textType = definitionByPredicate
      .getOrElse(
        SalsahGui.GuiElementProp,
        throw InconsistentRepositoryDataException(s"GuiElement not defined for $prop in $project")
      )
      .map(_.obj)
      .toSet
      .toList match {
      case (obj: JenaIriNode) :: Nil => TextType.fromIri(obj.iri)
      case objects =>
        throw InconsistentRepositoryDataException(s"Unexpected GuiElements defined for $prop in $project: $objects")
    }

    TextValueProp(
      graph = definitionStatements.head.context.get,
      project = project,
      ontology = onto,
      iri = prop,
      guiElement = definitionByPredicate(SalsahGui.GuiElementProp).head.obj.stringValue.asInstanceOf[IRI],
      textType = textType,
      statementsToRemove = statementsToRemove
    )
  }

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
