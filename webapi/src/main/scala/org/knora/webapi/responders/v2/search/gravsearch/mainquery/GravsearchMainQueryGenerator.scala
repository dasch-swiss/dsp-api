/*
 * Copyright © 2015-2018 the contributors (see Contributors.md).
 *
 * This file is part of Knora.
 *
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.responders.v2.search.gravsearch.mainquery

import org.knora.webapi.responders.v2.search._
import org.knora.webapi.responders.v2.search.gravsearch.types._
import org.knora.webapi.util.IriConversions._
import org.knora.webapi.util.StringFormatter
import org.knora.webapi.{GravsearchException, IRI, OntologyConstants}

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
        val mainAndDependentResourceValueObjectProp: QueryVariable = QueryVariable("mainAndDependentResourceValueObjectProp")

        // SPARQL variable representing the objects of value objects of the main and dependent resources
        val mainAndDependentResourceValueObjectObj: QueryVariable = QueryVariable("mainAndDependentResourceValueObjectObj")

        // SPARQL variable representing the standoff nodes of a (text) value object
        val standoffNodeVar: QueryVariable = QueryVariable("standoffNode")

        // SPARQL variable representing the predicates of a standoff node of a (text) value object
        val standoffPropVar: QueryVariable = QueryVariable("standoffProp")

        // SPARQL variable representing the objects of a standoff node of a (text) value object
        val standoffValueVar: QueryVariable = QueryVariable("standoffValue")

        // SPARQL variable representing a list node pointed to by a (list) value object
        val listNode: QueryVariable = QueryVariable("listNode")

        // SPARQL variable representing the label of a list node pointed to by a (list) value object
        val listNodeLabel: QueryVariable = QueryVariable("listNodeLabel")

    }

    /**
      *
      * Collects variables representing values that are present in the CONSTRUCT clause of the input query for the given [[Entity]] representing a resource.
      *
      * @param constructClause      the Construct clause to be looked at.
      * @param resource             the [[Entity]] representing the resource whose properties are to be collected
      * @param typeInspectionResult results of type inspection.
      * @param variableConcatSuffix the suffix appended to variable names in prequery results.
      * @return a Set of [[PropertyTypeInfo]] representing the value and link value properties to be returned to the client.
      */
    def collectValueVariablesForResource(constructClause: ConstructClause, resource: Entity, typeInspectionResult: GravsearchTypeInspectionResult, variableConcatSuffix: String): Set[QueryVariable] = {

        // make sure resource is a query variable or an IRI
        resource match {
            case queryVar: QueryVariable => ()
            case iri: IriRef => ()
            case literal: XsdLiteral => throw GravsearchException(s"${literal.toSparql} cannot represent a resource")
            case other => throw GravsearchException(s"${other.toSparql} cannot represent a resource")
        }

        // TODO: check in type information that resource represents a resource

        // get statements with the main resource as a subject
        val statementsWithResourceAsSubject: Seq[StatementPattern] = constructClause.statements.filter {
            statementPattern: StatementPattern => statementPattern.subj == resource
        }

        statementsWithResourceAsSubject.foldLeft(Set.empty[QueryVariable]) {
            (acc: Set[QueryVariable], statementPattern: StatementPattern) =>

                // check if the predicate is a Knora value  or linking property

                // create a key for the type annotations map
                val typeableEntity: TypeableEntity = statementPattern.pred match {
                    case iriRef: IriRef => TypeableIri(iriRef.iri)
                    case variable: QueryVariable => TypeableVariable(variable.variableName)
                    case other => throw GravsearchException(s"Expected an IRI or a variable as the predicate of a statement, but ${other.toSparql} given")
                }

                // if the given key exists in the type annotations map, add it to the collection
                if (typeInspectionResult.entities.contains(typeableEntity)) {

                    val propTypeInfo: PropertyTypeInfo = typeInspectionResult.entities(typeableEntity) match {
                        case propType: PropertyTypeInfo => propType

                        case _: NonPropertyTypeInfo =>
                            throw GravsearchException(s"Expected a property: ${statementPattern.pred.toSparql}")

                    }

                    val valueObjectVariable: Set[QueryVariable] = if (OntologyConstants.KnoraApi.isKnoraApiV2Resource(propTypeInfo.objectTypeIri)) {

                        // linking prop: get value object var and information which values are requested for dependent resource

                        // link value object variable
                        val valObjVar = SparqlTransformer.createUniqueVariableNameFromEntityAndProperty(statementPattern.obj, OntologyConstants.KnoraBase.LinkValue)

                        // return link value object variable and value objects requested for the dependent resource
                        Set(QueryVariable(valObjVar.variableName + variableConcatSuffix))

                    } else {
                        statementPattern.obj match {
                            case queryVar: QueryVariable => Set(QueryVariable(queryVar.variableName + variableConcatSuffix))
                            case other => throw GravsearchException(s"Expected a variable: ${other.toSparql}")
                        }
                    }

                    acc ++ valueObjectVariable

                } else {
                    // not a knora-api property
                    acc
                }
        }
    }

    /**
      * Creates the main query to be sent to the triplestore.
      * Requests two sets of information: about the main resources and the dependent resources.
      *
      * @param mainResourceIris      IRIs of main resources to be queried.
      * @param dependentResourceIris IRIs of dependent resources to be queried.
      * @param valueObjectIris       IRIs of value objects to be queried (for both main and dependent resources)
      * @return the main [[ConstructQuery]] query to be executed.
      */
    def createMainQuery(mainResourceIris: Set[IriRef], dependentResourceIris: Set[IriRef], valueObjectIris: Set[IRI]): ConstructQuery = {
        import GravsearchConstants._

        implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

        // WHERE patterns for the main resource variable: check that main resource is a knora-base:Resource and that it is not marked as deleted
        val wherePatternsForMainResource = Seq(
            ValuesPattern(mainResourceVar, mainResourceIris), // a ValuePattern that binds the main resources' IRIs to the main resource variable
            StatementPattern.makeInferred(subj = mainResourceVar, pred = IriRef(OntologyConstants.Rdf.Type.toSmartIri), obj = IriRef(OntologyConstants.KnoraBase.Resource.toSmartIri)),
            StatementPattern.makeExplicit(subj = mainResourceVar, pred = IriRef(OntologyConstants.KnoraBase.IsDeleted.toSmartIri), obj = XsdLiteral(value = "false", datatype = OntologyConstants.Xsd.Boolean.toSmartIri))
        )

        // mark main resource variable in CONSTRUCT clause
        val constructPatternsForMainResource = Seq(
            StatementPattern(subj = mainResourceVar, pred = IriRef(OntologyConstants.KnoraBase.IsMainResource.toSmartIri), obj = XsdLiteral(value = "true", datatype = OntologyConstants.Xsd.Boolean.toSmartIri))
        )

        // since a CONSTRUCT query returns a flat list of triples, we can handle main and dependent resources in the same way

        // WHERE patterns for direct statements about the main resource and dependent resources
        val wherePatternsForMainAndDependentResources = Seq(
            ValuesPattern(mainAndDependentResourceVar, mainResourceIris ++ dependentResourceIris), // a ValuePattern that binds the main and dependent resources' IRIs to a variable
            StatementPattern.makeInferred(subj = mainAndDependentResourceVar, pred = IriRef(OntologyConstants.Rdf.Type.toSmartIri), obj = IriRef(OntologyConstants.KnoraBase.Resource.toSmartIri)),
            StatementPattern.makeExplicit(subj = mainAndDependentResourceVar, pred = IriRef(OntologyConstants.KnoraBase.IsDeleted.toSmartIri), obj = XsdLiteral(value = "false", datatype = OntologyConstants.Xsd.Boolean.toSmartIri)),
            StatementPattern.makeExplicit(subj = mainAndDependentResourceVar, pred = mainAndDependentResourcePropVar, obj = mainAndDependentResourceObjectVar)
        )

        // mark main and dependent resources as a knora-base:Resource in CONSTRUCT clause and return direct assertions about all resources
        val constructPatternsForMainAndDependentResources = Seq(
            StatementPattern(subj = mainAndDependentResourceVar, pred = IriRef(OntologyConstants.Rdf.Type.toSmartIri), obj = IriRef(OntologyConstants.KnoraBase.Resource.toSmartIri)),
            StatementPattern(subj = mainAndDependentResourceVar, pred = mainAndDependentResourcePropVar, obj = mainAndDependentResourceObjectVar)
        )

        if (valueObjectIris.nonEmpty) {
            // value objects are to be queried

            val mainAndDependentResourcesValueObjectsValuePattern = ValuesPattern(mainAndDependentResourceValueObject, valueObjectIris.map(iri => IriRef(iri.toSmartIri)))

            // WHERE patterns for statements about the main and dependent resources' values
            val wherePatternsForMainAndDependentResourcesValues = Seq(
                mainAndDependentResourcesValueObjectsValuePattern,
                StatementPattern.makeInferred(subj = mainAndDependentResourceVar, pred = IriRef(OntologyConstants.KnoraBase.HasValue.toSmartIri), obj = mainAndDependentResourceValueObject),
                StatementPattern.makeExplicit(subj = mainAndDependentResourceVar, pred = mainAndDependentResourceValueProp, obj = mainAndDependentResourceValueObject),
                StatementPattern.makeExplicit(subj = mainAndDependentResourceValueObject, pred = IriRef(OntologyConstants.KnoraBase.IsDeleted.toSmartIri), obj = XsdLiteral(value = "false", datatype = OntologyConstants.Xsd.Boolean.toSmartIri)),
                StatementPattern.makeExplicit(subj = mainAndDependentResourceValueObject, pred = mainAndDependentResourceValueObjectProp, obj = mainAndDependentResourceValueObjectObj)
            )

            // return assertions about the main and dependent resources' values in CONSTRUCT clause
            val constructPatternsForMainAndDependentResourcesValues = Seq(
                StatementPattern(subj = mainAndDependentResourceVar, pred = IriRef(OntologyConstants.KnoraBase.HasValue.toSmartIri), obj = mainAndDependentResourceValueObject),
                StatementPattern(subj = mainAndDependentResourceVar, pred = mainAndDependentResourceValueProp, obj = mainAndDependentResourceValueObject),
                StatementPattern(subj = mainAndDependentResourceValueObject, pred = mainAndDependentResourceValueObjectProp, obj = mainAndDependentResourceValueObjectObj)
            )

            // WHERE patterns for standoff belonging to value objects (if any)
            val wherePatternsForStandoff = Seq(
                mainAndDependentResourcesValueObjectsValuePattern,
                StatementPattern.makeExplicit(subj = mainAndDependentResourceValueObject, pred = IriRef(OntologyConstants.KnoraBase.ValueHasStandoff.toSmartIri), obj = standoffNodeVar),
                StatementPattern.makeExplicit(subj = standoffNodeVar, pred = standoffPropVar, obj = standoffValueVar)
            )

            // return standoff assertions
            val constructPatternsForStandoff = Seq(
                StatementPattern(subj = mainAndDependentResourceValueObject, pred = IriRef(OntologyConstants.KnoraBase.ValueHasStandoff.toSmartIri), obj = standoffNodeVar),
                StatementPattern(subj = standoffNodeVar, pred = standoffPropVar, obj = standoffValueVar)
            )

            // WHERE patterns for list node pointed to by value objects (if any)
            val wherePatternsForListNode = Seq(
                mainAndDependentResourcesValueObjectsValuePattern,
                StatementPattern.makeExplicit(subj = mainAndDependentResourceValueObject, pred = IriRef(OntologyConstants.Rdf.Type.toSmartIri), obj = IriRef(OntologyConstants.KnoraBase.ListValue.toSmartIri)),
                StatementPattern.makeExplicit(subj = mainAndDependentResourceValueObject, pred = IriRef(OntologyConstants.KnoraBase.ValueHasListNode.toSmartIri), obj = listNode),
                StatementPattern.makeExplicit(subj = listNode, pred = IriRef(OntologyConstants.Rdfs.Label.toSmartIri), obj = listNodeLabel)
            )

            // return list node assertions
            val constructPatternsForListNode = Seq(
                StatementPattern(subj = mainAndDependentResourceValueObject, pred = IriRef(OntologyConstants.KnoraBase.ValueHasListNode.toSmartIri), obj = listNode),
                StatementPattern(subj = listNode, pred = IriRef(OntologyConstants.Rdfs.Label.toSmartIri), obj = listNodeLabel)
            )

            ConstructQuery(
                constructClause = ConstructClause(
                    statements = constructPatternsForMainResource ++ constructPatternsForMainAndDependentResources ++ constructPatternsForMainAndDependentResourcesValues ++ constructPatternsForStandoff ++ constructPatternsForListNode
                ),
                whereClause = WhereClause(
                    Seq(
                        UnionPattern(
                            Seq(wherePatternsForMainResource, wherePatternsForMainAndDependentResources, wherePatternsForMainAndDependentResourcesValues, wherePatternsForStandoff, wherePatternsForListNode)
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
