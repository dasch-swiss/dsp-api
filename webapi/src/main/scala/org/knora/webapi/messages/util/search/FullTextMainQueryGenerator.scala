/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.util.search

import org.knora.webapi._
import org.knora.webapi.config.AppConfig
import org.knora.webapi.messages.IriConversions._
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.StringFormatter

object FullTextMainQueryGenerator {

  /**
   * Constants for fulltext query.
   *
   * These constants are used to create SPARQL CONSTRUCT queries to be executed by the triplestore and to process the results that are returned.
   */
  object FullTextSearchConstants {

    // SPARQL variable representing the concatenated IRIs of value objects matching the search criteria
    val valueObjectConcatVar: QueryVariable = QueryVariable("valueObjectConcat")

    // SPARQL variable representing the resources matching the search criteria
    val resourceVar: QueryVariable = QueryVariable("resource")

    // SPARQL variable representing the predicates of a resource
    val resourcePropVar: QueryVariable = QueryVariable("resourceProp")

    // SPARQL variable representing the objects of a resource
    val resourceObjectVar: QueryVariable = QueryVariable("resourceObj")

    // SPARQL variable representing the property pointing to a value object from a resource
    val resourceValueProp: QueryVariable = QueryVariable("resourceValueProp")

    // SPARQL variable representing the value objects of a resource
    val resourceValueObject: QueryVariable = QueryVariable("resourceValueObject")

    // SPARQL variable representing the predicates of a value object
    val resourceValueObjectProp: QueryVariable = QueryVariable("resourceValueObjectProp")

    // SPARQL variable representing the objects of a value object
    val resourceValueObjectObj: QueryVariable = QueryVariable("resourceValueObjectObj")

    // SPARQL variable representing the standoff nodes of a (text) value object
    val standoffNodeVar: QueryVariable = QueryVariable("standoffNode")

    // SPARQL variable representing the predicates of a standoff node of a (text) value object
    val standoffPropVar: QueryVariable = QueryVariable("standoffProp")

    // SPARQL variable representing the objects of a standoff node of a (text) value object
    val standoffValueVar: QueryVariable = QueryVariable("standoffValue")

    // SPARQL variable representing the start index of a standoff node.
    val standoffStartIndexVar: QueryVariable = QueryVariable("startIndex")
  }

  /**
   * Creates a CONSTRUCT query for the given resource and value object IRIs.
   *
   * @param resourceIris    the IRIs of the resources to be queried.
   * @param valueObjectIris the IRIs of the value objects to be queried.
   * @param targetSchema    the target API schema.
   * @param schemaOptions   the schema options submitted with the request.
   * @param appConfig       the application's configuration.
   * @return a [[ConstructQuery]].
   */
  def createMainQuery(
    resourceIris: Set[IRI],
    valueObjectIris: Set[IRI],
    targetSchema: ApiV2Schema,
    schemaOptions: Set[SchemaOption],
    appConfig: AppConfig
  ): ConstructQuery = {
    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    import FullTextSearchConstants._

    // WHERE patterns for the resources: check that the resource are a knora-base:Resource and that it is not marked as deleted
    val wherePatternsForResources = Seq(
      ValuesPattern(
        resourceVar,
        resourceIris.map(iri => IriRef(iri.toSmartIri))
      ), // a ValuePattern that binds the resource IRIs to the resource variable
      StatementPattern.makeInferred(
        subj = resourceVar,
        pred = IriRef(OntologyConstants.Rdf.Type.toSmartIri),
        obj = IriRef(OntologyConstants.KnoraBase.Resource.toSmartIri)
      ),
      StatementPattern.makeExplicit(
        subj = resourceVar,
        pred = IriRef(OntologyConstants.KnoraBase.IsDeleted.toSmartIri),
        obj = XsdLiteral(value = "false", datatype = OntologyConstants.Xsd.Boolean.toSmartIri)
      ),
      StatementPattern.makeExplicit(subj = resourceVar, pred = resourcePropVar, obj = resourceObjectVar)
    )

    //  mark resources as the main resource and a knora-base:Resource in CONSTRUCT clause and return direct assertions about resources
    val constructPatternsForResources = Seq(
      StatementPattern(
        subj = resourceVar,
        pred = IriRef(OntologyConstants.KnoraBase.IsMainResource.toSmartIri),
        obj = XsdLiteral(value = "true", datatype = OntologyConstants.Xsd.Boolean.toSmartIri)
      ),
      StatementPattern(
        subj = resourceVar,
        pred = IriRef(OntologyConstants.Rdf.Type.toSmartIri),
        obj = IriRef(OntologyConstants.KnoraBase.Resource.toSmartIri)
      ),
      StatementPattern(subj = resourceVar, pred = resourcePropVar, obj = resourceObjectVar)
    )

    if (valueObjectIris.nonEmpty) {
      // value objects are to be queried

      // WHERE patterns for statements about the resources' values
      val wherePatternsForValueObjects = Seq(
        ValuesPattern(resourceValueObject, valueObjectIris.map(iri => IriRef(iri.toSmartIri))),
        StatementPattern.makeInferred(
          subj = resourceVar,
          pred = IriRef(OntologyConstants.KnoraBase.HasValue.toSmartIri),
          obj = resourceValueObject
        ),
        StatementPattern.makeExplicit(subj = resourceVar, pred = resourceValueProp, obj = resourceValueObject),
        StatementPattern.makeExplicit(
          subj = resourceValueObject,
          pred = IriRef(OntologyConstants.KnoraBase.IsDeleted.toSmartIri),
          obj = XsdLiteral(value = "false", datatype = OntologyConstants.Xsd.Boolean.toSmartIri)
        ),
        StatementPattern.makeExplicit(
          subj = resourceValueObject,
          pred = resourceValueObjectProp,
          obj = resourceValueObjectObj
        )
      )

      // return assertions about value objects
      val constructPatternsForValueObjects = Seq(
        StatementPattern(
          subj = resourceVar,
          pred = IriRef(OntologyConstants.KnoraBase.HasValue.toSmartIri),
          obj = resourceValueObject
        ),
        StatementPattern(subj = resourceVar, pred = resourceValueProp, obj = resourceValueObject),
        StatementPattern(subj = resourceValueObject, pred = resourceValueObjectProp, obj = resourceValueObjectObj)
      )

      // Check whether the response should include a page of standoff.
      val queryStandoff: Boolean = SchemaOptions.queryStandoffWithTextValues(targetSchema, schemaOptions)

      // WHERE patterns for querying the first page of standoff in each text value
      val wherePatternsForStandoff: Seq[QueryPattern] = if (queryStandoff) {
        Seq(
          ValuesPattern(resourceValueObject, valueObjectIris.map(iri => IriRef(iri.toSmartIri))),
          StatementPattern.makeExplicit(
            subj = resourceValueObject,
            pred = IriRef(OntologyConstants.KnoraBase.ValueHasStandoff.toSmartIri),
            obj = standoffNodeVar
          ),
          StatementPattern.makeExplicit(subj = standoffNodeVar, pred = standoffPropVar, obj = standoffValueVar),
          StatementPattern.makeExplicit(
            subj = standoffNodeVar,
            pred = IriRef(OntologyConstants.KnoraBase.StandoffTagHasStartIndex.toSmartIri),
            obj = standoffStartIndexVar
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
                  value = (appConfig.standoffPerPage - 1).toString,
                  datatype = OntologyConstants.Xsd.Integer.toSmartIri
                )
              )
            )
          )
        )
      } else {
        Seq.empty[QueryPattern]
      }

      // return standoff
      val constructPatternsForStandoff: Seq[StatementPattern] = if (queryStandoff) {
        Seq(
          StatementPattern(
            subj = resourceValueObject,
            pred = IriRef(OntologyConstants.KnoraBase.ValueHasStandoff.toSmartIri),
            obj = standoffNodeVar
          ),
          StatementPattern(subj = standoffNodeVar, pred = standoffPropVar, obj = standoffValueVar)
        )
      } else {
        Seq.empty[StatementPattern]
      }

      ConstructQuery(
        constructClause = ConstructClause(
          statements = constructPatternsForResources ++ constructPatternsForValueObjects ++ constructPatternsForStandoff
        ),
        whereClause = WhereClause(
          Seq(
            UnionPattern(
              Seq(wherePatternsForResources, wherePatternsForValueObjects, wherePatternsForStandoff).filter(_.nonEmpty)
            )
          )
        )
      )

    } else {
      // no value objects are to be queried

      ConstructQuery(
        constructClause = ConstructClause(
          statements = constructPatternsForResources
        ),
        whereClause = WhereClause(
          Seq(
            UnionPattern(
              Seq(wherePatternsForResources)
            )
          )
        )
      )
    }

  }

}
