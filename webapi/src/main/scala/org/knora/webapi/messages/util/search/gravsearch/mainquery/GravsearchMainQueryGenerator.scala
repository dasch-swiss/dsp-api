/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.util.search.gravsearch.mainquery

import org.knora.webapi._
import org.knora.webapi.exceptions.GravsearchException
import org.knora.webapi.messages.IriConversions._
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.util.ErrorHandlingMap
import org.knora.webapi.messages.util.rdf.SparqlSelectResult
import org.knora.webapi.messages.util.rdf.VariableResultsRow
import org.knora.webapi.messages.util.search._
import org.knora.webapi.messages.util.search.gravsearch.prequery.AbstractPrequeryGenerator
import org.knora.webapi.messages.util.search.gravsearch.prequery.NonTriplestoreSpecificGravsearchToPrequeryTransformer
import org.knora.webapi.settings.KnoraSettingsImpl

object GravsearchMainQueryGenerator {

  /**
   * Constants used in the processing of Gravsearch queries.
   *
   * These constants are used to create SPARQL CONSTRUCT queries to be executed by the triplestore and to process the results that are returned.
   */
  private object GravsearchConstants {

    // SPARQL variable representing the main resource and its properties
    val mainResourceVar: QueryVariable = QueryVariable("mainResourceVar")

    // SPARQL variable representing main and dependent resources
    val mainAndDependentResourceVar: QueryVariable = QueryVariable("mainAndDependentResource")

    // SPARQL variable representing the predicates of the main and dependent resources
    val mainAndDependentResourcePropVar: QueryVariable = QueryVariable("mainAndDependentResourceProp")

    // SPARQL variable representing the objects of the main and dependent resources
    val mainAndDependentResourceObjectVar: QueryVariable = QueryVariable("mainAndDependentResourceObj")

    // SPARQL variable representing the value objects of the main and dependent resources
    val mainAndDependentResourceValueObject: QueryVariable = QueryVariable("mainAndDependentResourceValueObject")

    // SPARQL variable representing the properties pointing to value objects from the main and dependent resources
    val mainAndDependentResourceValueProp: QueryVariable = QueryVariable("mainAndDependentResourceValueProp")

    // SPARQL variable representing the predicates of value objects of the main and dependent resources
    val mainAndDependentResourceValueObjectProp: QueryVariable = QueryVariable(
      "mainAndDependentResourceValueObjectProp"
    )

    // SPARQL variable representing the objects of value objects of the main and dependent resources
    val mainAndDependentResourceValueObjectObj: QueryVariable = QueryVariable("mainAndDependentResourceValueObjectObj")

    // SPARQL variable representing the standoff nodes of a (text) value object
    val standoffNodeVar: QueryVariable = QueryVariable("standoffNode")

    // SPARQL variable representing the predicates of a standoff node of a (text) value object
    val standoffPropVar: QueryVariable = QueryVariable("standoffProp")

    // SPARQL variable representing the objects of a standoff node of a (text) value object
    val standoffValueVar: QueryVariable = QueryVariable("standoffValue")

    // SPARQL variable representing the start index of a standoff node.
    val standoffStartIndexVar: QueryVariable = QueryVariable("startIndex")

    // SPARQL variable representing the standoff tag that is the target of an internal reference.
    val targetStandoffTagVar: QueryVariable = QueryVariable("targetStandoffTag")

    // SPARQL variable representing the original XML ID in a standoff tag that is the target of an internal reference.
    val targetOriginalXMLIDVar: QueryVariable = QueryVariable("targetOriginalXMLID")

    // SPARQL variable representing a list node pointed to by a (list) value object
    val listNode: QueryVariable = QueryVariable("listNode")

    // SPARQL variable representing the label of a list node pointed to by a (list) value object
    val listNodeLabel: QueryVariable = QueryVariable("listNodeLabel")

  }

  /**
   * Represents dependent resources organized by main resource.
   *
   * @param dependentResourcesPerMainResource a set of dependent resource Iris organized by main resource.
   */
  case class DependentResourcesPerMainResource(dependentResourcesPerMainResource: Map[IRI, Set[IRI]])

  /**
   * Represents value object variables and value object Iris organized by main resource.
   *
   * @param valueObjectVariablesAndValueObjectIris a set of value object Iris organized by value object variable and main resource.
   */
  case class ValueObjectVariablesAndValueObjectIris(
    valueObjectVariablesAndValueObjectIris: Map[IRI, Map[QueryVariable, Set[IRI]]]
  )

  /**
   * Collects the Iris of dependent resources per main resource from the results returned by the prequery.
   * Dependent resource Iris are grouped by main resource.
   *
   * @param prequeryResponse the results returned by the prequery.
   * @param transformer      the transformer that was used to turn the Gravsearch query into the prequery.
   * @param mainResourceVar  the variable representing the main resource.
   * @return a [[DependentResourcesPerMainResource]].
   */
  def getDependentResourceIrisPerMainResource(
    prequeryResponse: SparqlSelectResult,
    transformer: NonTriplestoreSpecificGravsearchToPrequeryTransformer,
    mainResourceVar: QueryVariable
  ): DependentResourcesPerMainResource = {

    // variables representing dependent resources
    val dependentResourceVariablesGroupConcat: Set[QueryVariable] = transformer.dependentResourceVariablesGroupConcat

    val dependentResourcesPerMainRes = prequeryResponse.results.bindings.foldLeft(Map.empty[IRI, Set[IRI]]) {
      case (acc: Map[IRI, Set[IRI]], resultRow: VariableResultsRow) =>
        // collect all the dependent resource Iris for the current main resource from prequery's response

        // the main resource's Iri
        val mainResIri: String = resultRow.rowMap(mainResourceVar.variableName)

        // get the Iris of all the dependent resources for the given main resource
        val dependentResIris: Set[IRI] = dependentResourceVariablesGroupConcat.flatMap {
          dependentResVar: QueryVariable =>
            // check if key exists: the variable representing dependent resources
            // could be contained in an OPTIONAL or a UNION and be unbound
            // It would be suppressed by `VariableResultsRow` in that case.
            //
            // Example: the query contains a dependent resource variable ?book within an OPTIONAL or a UNION.
            // If the query returns results for the dependent resource ?book (Iris of resources that match the given criteria),
            // those would be accessible via the variable ?book__Concat containing the aggregated results (Iris).
            val dependentResIriOption: Option[IRI] = resultRow.rowMap.get(dependentResVar.variableName)

            dependentResIriOption match {
              case Some(depResIri: IRI) =>
                // IRIs are concatenated by GROUP_CONCAT using a separator, split them.
                // Ignore empty strings, which could result from unbound variables in a UNION.
                depResIri.split(AbstractPrequeryGenerator.groupConcatSeparator).toSeq.filter(_.nonEmpty)

              case None => Set.empty[IRI] // no Iri present since variable was inside aan OPTIONAL or UNION
            }

        }

        acc + (mainResIri -> dependentResIris)
    }

    DependentResourcesPerMainResource(
      new ErrorHandlingMap(
        dependentResourcesPerMainRes,
        key => throw GravsearchException(s"main resource not found: $key")
      )
    )
  }

  /**
   * Collects object variables and their values per main resource from the results returned by the prequery.
   * Value objects variables and their Iris are grouped by main resource.
   *
   * @param prequeryResponse the results returned by the prequery.
   * @param transformer      the transformer that was used to turn the Gravsearch query into the prequery.
   * @param mainResourceVar  the variable representing the main resource.
   * @return [[ValueObjectVariablesAndValueObjectIris]].
   */
  def getValueObjectVarsAndIrisPerMainResource(
    prequeryResponse: SparqlSelectResult,
    transformer: NonTriplestoreSpecificGravsearchToPrequeryTransformer,
    mainResourceVar: QueryVariable
  ): ValueObjectVariablesAndValueObjectIris = {

    // value objects variables present in the prequery's WHERE clause
    val valueObjectVariablesConcat = transformer.valueObjectVariablesGroupConcat

    val valueObjVarsAndIris: Map[IRI, Map[QueryVariable, Set[IRI]]] =
      prequeryResponse.results.bindings.foldLeft(Map.empty[IRI, Map[QueryVariable, Set[IRI]]]) {
        (acc: Map[IRI, Map[QueryVariable, Set[IRI]]], resultRow: VariableResultsRow) =>
          // the main resource's Iri
          val mainResIri: String = resultRow.rowMap(mainResourceVar.variableName)

          // the the variables representing value objects and their Iris
          val valueObjVarToIris: Map[QueryVariable, Set[IRI]] = valueObjectVariablesConcat.map {
            valueObjVarConcat: QueryVariable =>
              // check if key exists: the variable representing value objects
              // could be contained in an OPTIONAL or a UNION and be unbound
              // It would be suppressed by `VariableResultsRow` in that case.

              // this logic works like in the case of dependent resources, see `getDependentResourceIrisPerMainResource` above.
              val valueObjIrisOption: Option[IRI] = resultRow.rowMap.get(valueObjVarConcat.variableName)

              val valueObjIris: Set[IRI] = valueObjIrisOption match {

                case Some(valObjIris) =>
                  // IRIs are concatenated by GROUP_CONCAT using a separator, split them.
                  // Ignore empty strings, which could result from unbound variables in a UNION.
                  valObjIris.split(AbstractPrequeryGenerator.groupConcatSeparator).toSet.filter(_.nonEmpty)

                case None => Set.empty[IRI] // since variable was inside aan OPTIONAL or UNION

              }

              valueObjVarConcat -> valueObjIris
          }.toMap

          val valueObjVarToIrisErrorHandlingMap = new ErrorHandlingMap(
            valueObjVarToIris,
            { key: QueryVariable =>
              throw GravsearchException(s"variable not found: $key")
            }
          )
          acc + (mainResIri -> valueObjVarToIrisErrorHandlingMap)
      }

    ValueObjectVariablesAndValueObjectIris(
      new ErrorHandlingMap(valueObjVarsAndIris, key => throw GravsearchException(s"main resource not found: $key"))
    )
  }

  /**
   * Creates the main query to be sent to the triplestore.
   * Requests two sets of information: about the main resources and the dependent resources.
   *
   * @param mainResourceIris      IRIs of main resources to be queried.
   * @param dependentResourceIris IRIs of dependent resources to be queried.
   * @param valueObjectIris       IRIs of value objects to be queried (for both main and dependent resources)
   * @param targetSchema          the target API schema.
   * @param schemaOptions         the schema options submitted with the request.
   * @return the main [[ConstructQuery]] query to be executed.
   */
  def createMainQuery(
    mainResourceIris: Set[IriRef],
    dependentResourceIris: Set[IriRef],
    valueObjectIris: Set[IRI],
    targetSchema: ApiV2Schema,
    schemaOptions: Set[SchemaOption],
    settings: KnoraSettingsImpl
  ): ConstructQuery = {
    import GravsearchConstants._

    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    // WHERE patterns for the main resource variable: check that main resource is a knora-base:Resource and that it is not marked as deleted
    val wherePatternsForMainResource = Seq(
      ValuesPattern(
        mainResourceVar,
        mainResourceIris
      ), // a ValuePattern that binds the main resources' IRIs to the main resource variable
      StatementPattern.makeInferred(
        subj = mainResourceVar,
        pred = IriRef(OntologyConstants.Rdf.Type.toSmartIri),
        obj = IriRef(OntologyConstants.KnoraBase.Resource.toSmartIri)
      ),
      StatementPattern.makeExplicit(
        subj = mainResourceVar,
        pred = IriRef(OntologyConstants.KnoraBase.IsDeleted.toSmartIri),
        obj = XsdLiteral(value = "false", datatype = OntologyConstants.Xsd.Boolean.toSmartIri)
      )
    )

    // mark main resource variable in CONSTRUCT clause
    val constructPatternsForMainResource = Seq(
      StatementPattern(
        subj = mainResourceVar,
        pred = IriRef(OntologyConstants.KnoraBase.IsMainResource.toSmartIri),
        obj = XsdLiteral(value = "true", datatype = OntologyConstants.Xsd.Boolean.toSmartIri)
      )
    )

    // since a CONSTRUCT query returns a flat list of triples, we can handle main and dependent resources in the same way

    // WHERE patterns for direct statements about the main resource and dependent resources
    val wherePatternsForMainAndDependentResources = Seq(
      ValuesPattern(
        mainAndDependentResourceVar,
        mainResourceIris ++ dependentResourceIris
      ), // a ValuePattern that binds the main and dependent resources' IRIs to a variable
      StatementPattern.makeInferred(
        subj = mainAndDependentResourceVar,
        pred = IriRef(OntologyConstants.Rdf.Type.toSmartIri),
        obj = IriRef(OntologyConstants.KnoraBase.Resource.toSmartIri)
      ),
      StatementPattern.makeExplicit(
        subj = mainAndDependentResourceVar,
        pred = IriRef(OntologyConstants.KnoraBase.IsDeleted.toSmartIri),
        obj = XsdLiteral(value = "false", datatype = OntologyConstants.Xsd.Boolean.toSmartIri)
      ),
      StatementPattern.makeExplicit(
        subj = mainAndDependentResourceVar,
        pred = mainAndDependentResourcePropVar,
        obj = mainAndDependentResourceObjectVar
      )
    )

    // mark main and dependent resources as a knora-base:Resource in CONSTRUCT clause and return direct assertions about all resources
    val constructPatternsForMainAndDependentResources = Seq(
      StatementPattern(
        subj = mainAndDependentResourceVar,
        pred = IriRef(OntologyConstants.Rdf.Type.toSmartIri),
        obj = IriRef(OntologyConstants.KnoraBase.Resource.toSmartIri)
      ),
      StatementPattern(
        subj = mainAndDependentResourceVar,
        pred = mainAndDependentResourcePropVar,
        obj = mainAndDependentResourceObjectVar
      )
    )

    if (valueObjectIris.nonEmpty) {
      // value objects are to be queried

      val mainAndDependentResourcesValueObjectsValuePattern =
        ValuesPattern(mainAndDependentResourceValueObject, valueObjectIris.map(iri => IriRef(iri.toSmartIri)))

      // WHERE patterns for statements about the main and dependent resources' values,
      // not including standoff markup in text values
      val wherePatternsForMainAndDependentResourcesValues = Seq(
        mainAndDependentResourcesValueObjectsValuePattern,
        StatementPattern.makeInferred(
          subj = mainAndDependentResourceVar,
          pred = IriRef(OntologyConstants.KnoraBase.HasValue.toSmartIri),
          obj = mainAndDependentResourceValueObject
        ),
        StatementPattern.makeExplicit(
          subj = mainAndDependentResourceVar,
          pred = mainAndDependentResourceValueProp,
          obj = mainAndDependentResourceValueObject
        ),
        StatementPattern.makeExplicit(
          subj = mainAndDependentResourceValueObject,
          pred = IriRef(OntologyConstants.KnoraBase.IsDeleted.toSmartIri),
          obj = XsdLiteral(value = "false", datatype = OntologyConstants.Xsd.Boolean.toSmartIri)
        ),
        StatementPattern.makeExplicit(
          subj = mainAndDependentResourceValueObject,
          pred = mainAndDependentResourceValueObjectProp,
          obj = mainAndDependentResourceValueObjectObj
        ),
        FilterPattern(
          CompareExpression(
            leftArg = mainAndDependentResourceValueObjectProp,
            operator = CompareExpressionOperator.NOT_EQUALS,
            rightArg = IriRef(OntologyConstants.KnoraBase.ValueHasStandoff.toSmartIri)
          )
        )
      )

      // return assertions about the main and dependent resources' values in CONSTRUCT clause
      val constructPatternsForMainAndDependentResourcesValues = Seq(
        StatementPattern(
          subj = mainAndDependentResourceVar,
          pred = IriRef(OntologyConstants.KnoraBase.HasValue.toSmartIri),
          obj = mainAndDependentResourceValueObject
        ),
        StatementPattern(
          subj = mainAndDependentResourceVar,
          pred = mainAndDependentResourceValueProp,
          obj = mainAndDependentResourceValueObject
        ),
        StatementPattern(
          subj = mainAndDependentResourceValueObject,
          pred = mainAndDependentResourceValueObjectProp,
          obj = mainAndDependentResourceValueObjectObj
        )
      )

      // Check whether the response should include standoff.
      val queryStandoff: Boolean = SchemaOptions.queryStandoffWithTextValues(targetSchema, schemaOptions)

      // WHERE patterns for querying the first page of standoff in each text value
      val wherePatternsForStandoff: Seq[QueryPattern] = if (queryStandoff) {
        Seq(
          mainAndDependentResourcesValueObjectsValuePattern,
          StatementPattern.makeExplicit(
            subj = mainAndDependentResourceValueObject,
            pred = IriRef(OntologyConstants.KnoraBase.ValueHasStandoff.toSmartIri),
            obj = standoffNodeVar
          ),
          StatementPattern.makeExplicit(subj = standoffNodeVar, pred = standoffPropVar, obj = standoffValueVar),
          StatementPattern.makeExplicit(
            subj = standoffNodeVar,
            pred = IriRef(OntologyConstants.KnoraBase.StandoffTagHasStartIndex.toSmartIri),
            obj = standoffStartIndexVar
          ),
          OptionalPattern(
            Seq(
              StatementPattern.makeExplicit(
                subj = standoffNodeVar,
                pred = IriRef(OntologyConstants.KnoraBase.StandoffTagHasInternalReference.toSmartIri),
                obj = targetStandoffTagVar
              ),
              StatementPattern.makeExplicit(
                subj = targetStandoffTagVar,
                pred = IriRef(OntologyConstants.KnoraBase.StandoffTagHasOriginalXMLID.toSmartIri),
                obj = targetOriginalXMLIDVar
              )
            )
          ),
          FilterPattern(
            AndExpression(
              leftArg = CompareExpression(
                leftArg = standoffStartIndexVar,
                operator = CompareExpressionOperator.GREATER_THAN_OR_EQUAL_TO,
                rightArg = XsdLiteral(value = "0", datatype = OntologyConstants.Xsd.Integer.toSmartIri)
              ),
              rightArg = CompareExpression(
                leftArg = standoffStartIndexVar,
                operator = CompareExpressionOperator.LESS_THAN_OR_EQUAL_TO,
                rightArg = XsdLiteral(
                  value = (settings.standoffPerPage - 1).toString,
                  datatype = OntologyConstants.Xsd.Integer.toSmartIri
                )
              )
            )
          )
        )
      } else {
        Seq.empty[QueryPattern]
      }

      // return standoff assertions
      val constructPatternsForStandoff: Seq[StatementPattern] = if (queryStandoff) {
        Seq(
          StatementPattern(
            subj = mainAndDependentResourceValueObject,
            pred = IriRef(OntologyConstants.KnoraBase.ValueHasStandoff.toSmartIri),
            obj = standoffNodeVar
          ),
          StatementPattern(subj = standoffNodeVar, pred = standoffPropVar, obj = standoffValueVar),
          StatementPattern(
            subj = standoffNodeVar,
            pred = IriRef(OntologyConstants.KnoraBase.TargetHasOriginalXMLID.toSmartIri),
            obj = targetOriginalXMLIDVar
          )
        )
      } else {
        Seq.empty[StatementPattern]
      }

      ConstructQuery(
        constructClause = ConstructClause(
          statements =
            constructPatternsForMainResource ++ constructPatternsForMainAndDependentResources ++ constructPatternsForMainAndDependentResourcesValues ++ constructPatternsForStandoff
        ),
        whereClause = WhereClause(
          Seq(
            UnionPattern(
              Seq(
                wherePatternsForMainResource,
                wherePatternsForMainAndDependentResources,
                wherePatternsForMainAndDependentResourcesValues,
                wherePatternsForStandoff
              ).filter(_.nonEmpty)
            )
          )
        )
      )

    } else {
      // no value objects are to be queried

      ConstructQuery(
        constructClause = ConstructClause(
          statements = constructPatternsForMainResource ++ constructPatternsForMainAndDependentResources
        ),
        whereClause = WhereClause(
          Seq(
            UnionPattern(
              Seq(wherePatternsForMainResource, wherePatternsForMainAndDependentResources)
            )
          )
        )
      )
    }
  }
}
