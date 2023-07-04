/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.triplestore.upgrade.plugins

import com.typesafe.scalalogging.Logger

import dsp.constants.SalsahGui
import dsp.errors.InconsistentRepositoryDataException
import dsp.valueobjects.UuidUtil
import org.knora.webapi.IRI
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.OntologyConstants.KnoraBase
import org.knora.webapi.messages.util.rdf._
import org.knora.webapi.messages.util.rdf.jenaimpl.JenaIriNode
import org.knora.webapi.store.triplestore.upgrade.UpgradePlugin
import org.knora.webapi.store.triplestore.upgrade.plugins.TextType.CustomFormattedText
import org.knora.webapi.store.triplestore.upgrade.plugins.TextType.FormattedText
import org.knora.webapi.store.triplestore.upgrade.plugins.TextType.UnformattedText

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

/**
 * Represents a property that has a text value as its object class constraint.
 */
final case class TextValueProp(
  graph: IRI,
  project: IRI,
  ontology: IRI,
  iri: IRI,
  textType: TextType,
  objectClassConstraint: Statement,
  statementsToRemove: Set[Statement]
)

/**
 * Represents a all necessary information to adjust a text value in data.
 */
final case class DataAdjustment(
  graph: IRI,
  resourceIri: IRI,
  valueIri: IRI,
  statementsToRemove: Set[Statement],
  statementsToInsert: Set[Statement]
)

/**
 * Represents a value that may need to be adjusted.
 */
final case class AdjustableData(
  graph: IRI,
  resourceIri: IRI,
  valueIri: IRI,
  mapping: Option[IRI]
)

class UpgradePluginPR2710(log: Logger) extends UpgradePlugin {
  private val predicatesToRemove: Set[IRI] = Set(
    KnoraBase.ObjectClassConstraint,
    SalsahGui.GuiElementProp,
    SalsahGui.GuiAttribute
  )

  private val nodeFactory: RdfNodeFactory = RdfFeatureFactory.getRdfNodeFactory()

  private val biblioTitlePropertyIri: IRI = "http://www.knora.org/ontology/0801/biblio#publicationHasTitle"
  private val leooMappingIri: IRI         = "http://rdfh.ch/projects/yTerZGyxjZVqFMNNKXCDPF/mappings/leooLetterMapping"

  private def typeToNode(textType: TextType): RdfNode =
    textType match {
      case TextType.UnformattedText     => nodeFactory.makeIriNode(KnoraBase.UnformattedTextValue)
      case TextType.FormattedText       => nodeFactory.makeIriNode(KnoraBase.FormattedTextValue)
      case TextType.CustomFormattedText => nodeFactory.makeIriNode(KnoraBase.CustomFormattedTextValue)
    }

  override def transform(model: RdfModel): Unit = {
    log.info("Transformation started.")

    val ontologyToProjectMap: Map[IRI, IRI] = collectOntologyToProjectMap(model)
    log.debug(s"Found ${ontologyToProjectMap.size} ontologies for ${ontologyToProjectMap.values.toSet.size} projects.")

    val textValuePropsPerOntology: Map[IRI, Seq[IRI]] = collectTextValuePropIris(model)
    log.debug(s"Found ${textValuePropsPerOntology.values.flatten.size} TextValue properties.")

    val textValueProps: Set[TextValueProp] = makeTextValueProps(model, textValuePropsPerOntology, ontologyToProjectMap)
    log.debug(s"Created ${textValueProps.size} TextValueProperty objects.")

    val verifiedTextValueProps: Set[TextValueProp] = verifyTextValueProps(model, textValueProps)
    log.debug(s"Verified ${verifiedTextValueProps.size} TextValueProperty objects against usage in data.")

    val dataAdjustments = collectDataAdjustments(model, verifiedTextValueProps)
    log.debug(s"Found ${dataAdjustments.size} values in data to adjust.")

    val dataStatementsToRemove = dataAdjustments.flatMap(_.statementsToRemove)
    log.debug(s"Found ${dataStatementsToRemove.size} statements in data to remove.")
    val dataStatementsToInsert = dataAdjustments.flatMap(_.statementsToInsert)
    log.debug(s"Found ${dataStatementsToInsert.size} statements in data to insert.")

    val ontologyStatementsToRemove = verifiedTextValueProps.flatMap(_.statementsToRemove)
    log.debug(s"Found ${ontologyStatementsToRemove.size} statements in ontologies to remove.")
    val ontologyStatementsToInsert = verifiedTextValueProps.map(tvp =>
      nodeFactory.makeStatement(
        tvp.objectClassConstraint.subj,
        tvp.objectClassConstraint.pred,
        typeToNode(tvp.textType),
        tvp.objectClassConstraint.context
      )
    )
    log.debug(s"Found ${ontologyStatementsToInsert.size} statements in ontologies to insert.")

    val statementsToRemove = dataStatementsToRemove ++ ontologyStatementsToRemove
    log.info(s"Removing ${statementsToRemove.size} statements.")
    model.removeStatements(statementsToRemove)

    val statementsToInsert = dataStatementsToInsert ++ ontologyStatementsToInsert
    log.info(s"Inserting ${statementsToInsert.size} statements.")
    model.addStatements(statementsToInsert)

    log.info("Transformation finished successfully.")
  }

  /**
   * finds all ontologies in the model and maps them to the project they belong to.
   */
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
            Some(nodeFactory.makeIriNode(KnoraBase.AttachedToProject)),
            None
          )
          .map(_.obj.stringValue.asInstanceOf[IRI])
          .toList
          .head
      ontology -> project
    }.toMap
  }

  /**
   * finds all text value properties in the model and creates a map from ontology to text value property.
   */
  private def collectTextValuePropIris(model: RdfModel): Map[IRI, Seq[IRI]] =
    model
      .find(
        None,
        Some(nodeFactory.makeIriNode(KnoraBase.ObjectClassConstraint)),
        Some(nodeFactory.makeIriNode(KnoraBase.TextValue))
      )
      .flatMap(s => s.context.map(_ -> s.subj.stringValue.asInstanceOf[IRI]))
      .foldLeft(Map.empty[IRI, Seq[IRI]]) { case (acc, (k, v)) =>
        acc + (k -> (acc.getOrElse(k, Seq.empty[IRI]) :+ v))
      }

  /**
   * Given a map of text value properties per ontology, creates a set of [[TextValueProp]] objects.
   *
   * Notice that these objects are not yet verified against usage in data.
   * This means that they may not actually be correct.
   * E.g., if an ontology defines a property as unformatted text, but in data the corresponding values are formatted,
   * then this property will will need to be adjusted in a separate step (see `verifyTextValueProps`).
   */
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
      .get(SalsahGui.GuiElementProp)
      .map(_.map(_.obj).toSet.toList match {
        case (obj: JenaIriNode) :: Nil => TextType.fromIri(obj.iri)
        case objects =>
          throw InconsistentRepositoryDataException(s"Unexpected GuiElements defined for $prop in $project: $objects")
      })
      .getOrElse(TextType.UnformattedText)

    val objectClassConstraint = definitionByPredicate
      .getOrElse(
        KnoraBase.ObjectClassConstraint,
        throw InconsistentRepositoryDataException(s"Rdf.Type not defined for $prop in $project")
      )
      .head

    TextValueProp(
      graph = definitionStatements.head.context.get,
      project = project,
      ontology = onto,
      iri = prop,
      textType = textType,
      objectClassConstraint = objectClassConstraint,
      statementsToRemove = statementsToRemove
    )
  }

  /**
   * Verifies that the TextValueProperty as constructed from the ontology aligns with the usage of the property in data.
   * If not, the TextValueProperty is updated accordingly.
   * This happens e.g. when a property is defined as unformatted text but in data it has standoff defined
   */
  private def verifyTextValueProps(model: RdfModel, props: Set[TextValueProp]): Set[TextValueProp] = {
    val repo: RdfRepository = model.asRepository
    props.map { prop =>
      prop.textType match {
        case CustomFormattedText => prop
        case _ =>
          (propertyHasStandardMapping(repo, prop), propertyHasCustomMapping(repo, prop)) match {
            case (true, false)  => prop.copy(textType = FormattedText)
            case (false, true)  => prop.copy(textType = CustomFormattedText)
            case (false, false) => prop
            case (true, true) =>
              throw InconsistentRepositoryDataException(
                s"Property ${prop.iri} in ${prop.project} has both a standard and a custom mapping in data."
              )
          }
      }
    }
  }

  private def propertyHasStandardMapping(repo: RdfRepository, prop: TextValueProp): Boolean =
    repo.doAsk(hasStandardMappingQuery(prop))

  private def hasStandardMappingQuery(prop: TextValueProp) =
    s"""|PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
        |PREFIX salsah-gui: <http://www.knora.org/ontology/salsah-gui#>
        |ASK
        |WHERE {
        |    GRAPH ?g {
        |        ?res <${prop.iri}> ?val .
        |        ?val knora-base:valueHasMapping <${KnoraBase.StandardMapping}> .
        |    }
        |}""".stripMargin

  private def propertyHasCustomMapping(repo: RdfRepository, prop: TextValueProp): Boolean =
    repo.doAsk(hasCustomMappingQuery(prop))

  private def hasCustomMappingQuery(prop: TextValueProp) =
    s"""|PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
        |PREFIX salsah-gui: <http://www.knora.org/ontology/salsah-gui#>
        |ASK
        |WHERE {
        |    GRAPH ?g {
        |        ?res <${prop.iri}> ?val .
        |        ?val knora-base:valueHasMapping ?mapping .
        |        FILTER (?mapping != <${KnoraBase.StandardMapping}>) .
        |    }
        |}""".stripMargin

  /**
   * Given a set of verified TextValueProps, produces a set of DataAdjustments that need to be made to the repository.
   */
  private def collectDataAdjustments(model: RdfModel, props: Set[TextValueProp]): Set[DataAdjustment] = {
    val repo = model.asRepository
    def query(prop: TextValueProp): String =
      s"""|PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
          |SELECT ?g ?res ?val ?mapping
          |WHERE {
          |    GRAPH ?g {
          |        ?res <${prop.iri}> ?val .
          |        OPTIONAL {
          |            ?val knora-base:valueHasMapping ?mapping .
          |        }
          |    }
          |}""".stripMargin
    props.flatMap { prop =>
      val queryResult: SparqlSelectResult = repo.doSelect(selectQuery = query(prop))
      val adjustables: Seq[AdjustableData] =
        queryResult.results.bindings.map(row =>
          AdjustableData(
            graph = row.rowMap
              .getOrElse("g", throw InconsistentRepositoryDataException(s"Graph missing for ${prop.iri}: $row")),
            resourceIri = row.rowMap
              .getOrElse("res", throw InconsistentRepositoryDataException(s"Resource missing for ${prop.iri}: $row")),
            valueIri = row.rowMap
              .getOrElse("val", throw InconsistentRepositoryDataException(s"Value missing for ${prop.iri}: $row")),
            mapping = row.rowMap.get("mapping")
          )
        )
      adjustables.map(adjustable => collectDataAdjustment(model, adjustable, prop)).toSet
    }
  }

  /**
   * Given a single verified TextValueProp and a AdjustableData, produces a DataAdjustment that can be applied to the repository.
   */
  private def collectDataAdjustment(
    model: RdfModel,
    adjustable: AdjustableData,
    prop: TextValueProp
  ): DataAdjustment =
    (prop.textType, adjustable.mapping) match {
      case (UnformattedText, None) => changeTypeToUnformattedTextValue(adjustable)
      case (UnformattedText, Some(KnoraBase.StandardMapping)) =>
        throw InconsistentRepositoryDataException(
          s"value ${adjustable.valueIri} has standard mapping but is defined as unformatted text after verification (should never happen)."
        )
      case (FormattedText, None)                            => addStandardMappingToValue(model, adjustable)
      case (FormattedText, Some(KnoraBase.StandardMapping)) => changeTypeToFormattedTextValue(adjustable)
      case (UnformattedText | FormattedText, Some(mapping)) =>
        throw InconsistentRepositoryDataException(
          s"Unexpected custom mapping $mapping found on value ${adjustable.valueIri}."
        )
      case (CustomFormattedText, None | Some(KnoraBase.StandardMapping)) =>
        if (prop.iri == biblioTitlePropertyIri) addLeooMappingToValue(model, adjustable)
        else throw InconsistentRepositoryDataException(s"Cannot create custom mapping in plugin for $prop")
      case (CustomFormattedText, Some(mapping)) => changeTypeToCustomFormattedTextValue(adjustable)
    }

  /**
   * Simple DataAdjustment that changes the rdf:type of a value from TextValue to UnformattedTextValue.
   */
  private def changeTypeToUnformattedTextValue(adjustable: AdjustableData): DataAdjustment =
    changeType(adjustable, KnoraBase.UnformattedTextValue)

  /**
   * Simple DataAdjustment that changes the rdf:type of a value from TextValue to FormattedTextValue.
   */
  private def changeTypeToFormattedTextValue(adjustable: AdjustableData): DataAdjustment =
    changeType(adjustable, KnoraBase.FormattedTextValue)

  /**
   * Simple DataAdjustment that changes the rdf:type of a value from TextValue to CustomFormattedTextValue.
   */
  private def changeTypeToCustomFormattedTextValue(adjustable: AdjustableData): DataAdjustment =
    changeType(adjustable, KnoraBase.CustomFormattedTextValue)

  private def changeType(adjustable: AdjustableData, newType: IRI): DataAdjustment =
    DataAdjustment(
      graph = adjustable.graph,
      resourceIri = adjustable.resourceIri,
      valueIri = adjustable.valueIri,
      statementsToRemove = Set(
        nodeFactory.makeStatement(
          subj = nodeFactory.makeIriNode(adjustable.valueIri),
          pred = nodeFactory.makeIriNode(OntologyConstants.Rdf.Type),
          obj = nodeFactory.makeIriNode(KnoraBase.TextValue),
          context = Some(adjustable.graph)
        )
      ),
      statementsToInsert = Set(
        nodeFactory.makeStatement(
          subj = nodeFactory.makeIriNode(adjustable.valueIri),
          pred = nodeFactory.makeIriNode(OntologyConstants.Rdf.Type),
          obj = nodeFactory.makeIriNode(newType),
          context = Some(adjustable.graph)
        )
      )
    )

  /**
   * Creates a complex DataAdjustment that takes an unformatted text value and adds a standard mapping and the minimal set of standoff properties to it.
   */
  private def addStandardMappingToValue(model: RdfModel, adjustable: AdjustableData): DataAdjustment = {
    val newMappingStatement: Statement = nodeFactory.makeStatement(
      subj = nodeFactory.makeIriNode(adjustable.valueIri),
      pred = nodeFactory.makeIriNode(KnoraBase.ValueHasMapping),
      obj = nodeFactory.makeIriNode(KnoraBase.StandardMapping),
      context = Some(adjustable.graph)
    )

    val typeStatement: Statement = findTypeStatement(adjustable, model)
    val newTypeStatement: Statement =
      copyStatement(typeStatement, obj = Some(nodeFactory.makeIriNode(KnoraBase.FormattedTextValue)))

    val stringValueStatement: Statement = findStringValueStatement(adjustable, model)
    val newString                       = stringValueStatement.obj.stringValue + "\u001E"
    val newStringValue: Statement =
      copyStatement(stringValueStatement, obj = Some(nodeFactory.makeStringLiteral(newString)))

    val newStandoffStatements: Set[Statement] =
      generateStandardMappingStandoffStatements(adjustable, newString.length())

    val statementsToRemove = Set(typeStatement, stringValueStatement)
    val statementsToInsert = Set(newMappingStatement, newTypeStatement, newStringValue) ++ newStandoffStatements
    DataAdjustment(
      graph = adjustable.graph,
      resourceIri = adjustable.resourceIri,
      valueIri = adjustable.valueIri,
      statementsToRemove = statementsToRemove,
      statementsToInsert = statementsToInsert
    )
  }

  /**
   * Creates a complex DataAdjustment that takes an unformatted text value
   * and adds the BEOL LEOO mapping and the minimal set of standoff properties to it.
   */
  private def addLeooMappingToValue(model: RdfModel, adjustable: AdjustableData): DataAdjustment = {
    val newMappingStatement: Statement = nodeFactory.makeStatement(
      subj = nodeFactory.makeIriNode(adjustable.valueIri),
      pred = nodeFactory.makeIriNode(KnoraBase.ValueHasMapping),
      obj = nodeFactory.makeIriNode(leooMappingIri),
      context = Some(adjustable.graph)
    )

    val typeStatement: Statement = findTypeStatement(adjustable, model)
    val newTypeStatement: Statement =
      copyStatement(typeStatement, obj = Some(nodeFactory.makeIriNode(KnoraBase.CustomFormattedTextValue)))

    val stringValueStatement: Statement = findStringValueStatement(adjustable, model)
    val stringValue: String             = stringValueStatement.obj.stringValue

    val newStandoffStatements: Set[Statement] =
      generateLeooMappingStandoffStatements(adjustable, stringValue.length())

    val statementsToRemove = Set(typeStatement)
    val statementsToInsert = Set(newMappingStatement, newTypeStatement) ++ newStandoffStatements
    DataAdjustment(
      graph = adjustable.graph,
      resourceIri = adjustable.resourceIri,
      valueIri = adjustable.valueIri,
      statementsToRemove = statementsToRemove,
      statementsToInsert = statementsToInsert
    )
  }

  private def findTypeStatement(adjustable: AdjustableData, model: RdfModel): Statement =
    model
      .findFirst(
        subj = Some(nodeFactory.makeIriNode(adjustable.valueIri)),
        pred = Some(nodeFactory.makeIriNode(OntologyConstants.Rdf.Type)),
        obj = Some(nodeFactory.makeIriNode(KnoraBase.TextValue)),
        context = Some(adjustable.graph)
      )
      .getOrElse(
        throw InconsistentRepositoryDataException(
          s"Value ${adjustable.valueIri} with type TextValue not found in model"
        )
      )

  private def findStringValueStatement(adjustable: AdjustableData, model: RdfModel): Statement =
    model
      .findFirst(
        subj = Some(nodeFactory.makeIriNode(adjustable.valueIri)),
        pred = Some(nodeFactory.makeIriNode(KnoraBase.ValueHasString)),
        obj = None,
        context = Some(adjustable.graph)
      )
      .getOrElse(
        throw InconsistentRepositoryDataException(
          s"Value ${adjustable.valueIri} has no kb:valueHasString"
        )
      )

  private def generateStandardMappingStandoffStatements(value: AdjustableData, stringLength: Int): Set[Statement] = {
    lazy val newUuid = UuidUtil.makeRandomBase64EncodedUuid
    val standoffIri0 = s"${value.valueIri}/standoff/0"
    val standoffIri1 = s"${value.valueIri}/standoff/1"
    Set(
      makeIntStatement(value.valueIri, KnoraBase.ValueHasMaxStandoffStartIndex, 1, value.graph),
      makeStatement(value.valueIri, KnoraBase.ValueHasStandoff, standoffIri0, value.graph),
      makeStatement(value.valueIri, KnoraBase.ValueHasStandoff, standoffIri1, value.graph),
      makeStatement(standoffIri0, OntologyConstants.Rdf.Type, OntologyConstants.Standoff.StandoffRootTag, value.graph),
      makeIntStatement(standoffIri0, KnoraBase.StandoffTagHasStart, 0, value.graph),
      makeIntStatement(standoffIri0, KnoraBase.StandoffTagHasEnd, stringLength, value.graph),
      makeIntStatement(standoffIri0, KnoraBase.StandoffTagHasStartIndex, 0, value.graph),
      makeStringStatement(standoffIri0, KnoraBase.StandoffTagHasUUID, newUuid, value.graph),
      makeStatement(
        standoffIri1,
        OntologyConstants.Rdf.Type,
        OntologyConstants.Standoff.StandoffParagraphTag,
        value.graph
      ),
      makeIntStatement(standoffIri1, KnoraBase.StandoffTagHasStart, 0, value.graph),
      makeIntStatement(standoffIri1, KnoraBase.StandoffTagHasEnd, stringLength - 1, value.graph),
      makeIntStatement(standoffIri1, KnoraBase.StandoffTagHasStartIndex, 1, value.graph),
      makeStringStatement(standoffIri1, KnoraBase.StandoffTagHasUUID, newUuid, value.graph),
      makeStatement(standoffIri1, KnoraBase.StandoffTagHasStartParent, standoffIri0, value.graph)
    )
  }

  private def generateLeooMappingStandoffStatements(value: AdjustableData, stringLength: Int): Set[Statement] = {
    lazy val newUuid = UuidUtil.makeRandomBase64EncodedUuid
    val standoffIri0 = s"${value.valueIri}/standoff/0"
    Set(
      makeIntStatement(value.valueIri, KnoraBase.ValueHasMaxStandoffStartIndex, 0, value.graph),
      makeStatement(value.valueIri, KnoraBase.ValueHasStandoff, standoffIri0, value.graph),
      makeStatement(standoffIri0, OntologyConstants.Rdf.Type, OntologyConstants.Standoff.StandoffRootTag, value.graph),
      makeIntStatement(standoffIri0, KnoraBase.StandoffTagHasStart, 0, value.graph),
      makeIntStatement(standoffIri0, KnoraBase.StandoffTagHasEnd, stringLength, value.graph),
      makeIntStatement(standoffIri0, KnoraBase.StandoffTagHasStartIndex, 0, value.graph),
      makeStringStatement(standoffIri0, KnoraBase.StandoffTagHasUUID, newUuid, value.graph)
    )
  }

  private def makeStatement(subj: IRI, pred: IRI, obj: IRI, context: IRI): Statement =
    nodeFactory.makeStatement(
      subj = nodeFactory.makeIriNode(subj),
      pred = nodeFactory.makeIriNode(pred),
      obj = nodeFactory.makeIriNode(obj),
      context = Some(context)
    )

  private def makeIntStatement(subj: IRI, pred: IRI, obj: Int, context: IRI): Statement =
    nodeFactory.makeStatement(
      subj = nodeFactory.makeIriNode(subj),
      pred = nodeFactory.makeIriNode(pred),
      obj = nodeFactory.makeDatatypeLiteral(obj.toString(), OntologyConstants.Xsd.Integer),
      context = Some(context)
    )

  private def makeStringStatement(subj: IRI, pred: IRI, obj: String, context: IRI): Statement =
    nodeFactory.makeStatement(
      subj = nodeFactory.makeIriNode(subj),
      pred = nodeFactory.makeIriNode(pred),
      obj = nodeFactory.makeStringLiteral(obj),
      context = Some(context)
    )

  private def copyStatement(
    statement: Statement,
    subj: Option[RdfResource] = None,
    pred: Option[IriNode] = None,
    obj: Option[RdfNode] = None,
    context: Option[Option[IRI]] = None
  ): Statement =
    nodeFactory.makeStatement(
      subj = subj.getOrElse(statement.subj),
      pred = pred.getOrElse(statement.pred),
      obj = obj.getOrElse(statement.obj),
      context = context.getOrElse(statement.context)
    )
}
