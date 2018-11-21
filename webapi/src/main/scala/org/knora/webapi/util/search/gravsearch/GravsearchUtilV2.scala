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

package org.knora.webapi.util.search.gravsearch

import org.knora.webapi._
import org.knora.webapi.util.{ConstructResponseUtilV2, SmartIri, StringFormatter}
import org.knora.webapi.util.search._
import org.knora.webapi.util.IriConversions._


/**
  * Utility methods for [[org.knora.webapi.responders.v2.SearchResponderV2]] (Gravsearch).
  */
object GravsearchUtilV2 {

    object SparqlTransformation {

        protected implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

        /**
          * Transform the the Knora explicit graph name to GraphDB explicit graph name.
          *
          * @param statement the given statement whose graph name has to be renamed.
          * @return the statement with the renamed graph, if given.
          */
        def transformKnoraExplicitToGraphDBExplicit(statement: StatementPattern): Seq[StatementPattern] = {
            val transformedPattern = statement.copy(
                pred = statement.pred match {
                    case iri: IriRef if iri.iri == OntologyConstants.KnoraBase.MatchesTextIndex.toSmartIri => IriRef(OntologyConstants.Ontotext.LuceneFulltext.toSmartIri) // convert to special Lucene property
                    case other => other // no conversion needed
                },
                namedGraph = statement.namedGraph match {
                    case Some(IriRef(SmartIri(OntologyConstants.NamedGraphs.KnoraExplicitNamedGraph), _)) => Some(IriRef(OntologyConstants.NamedGraphs.GraphDBExplicitNamedGraph.toSmartIri))
                    case Some(IriRef(_, _)) => throw AssertionException(s"Named graphs other than ${OntologyConstants.NamedGraphs.KnoraExplicitNamedGraph} cannot occur in non-triplestore-specific generated search query SPARQL")
                    case None => None
                }
            )

            Seq(transformedPattern)
        }

        class GraphDBSelectToSelectTransformer extends SelectToSelectTransformer {
            def transformStatementInSelect(statementPattern: StatementPattern): Seq[StatementPattern] = Seq(statementPattern)

            def transformStatementInWhere(statementPattern: StatementPattern, inputOrderBy: Seq[OrderCriterion]): Seq[StatementPattern] = {
                transformKnoraExplicitToGraphDBExplicit(statementPattern)
            }

            def transformFilter(filterPattern: FilterPattern): Seq[QueryPattern] = Seq(filterPattern)

        }

        class NoInferenceSelectToSelectTransformer extends SelectToSelectTransformer {
            def transformStatementInSelect(statementPattern: StatementPattern): Seq[StatementPattern] = Seq(statementPattern)

            def transformStatementInWhere(statementPattern: StatementPattern, inputOrderBy: Seq[OrderCriterion]): Seq[StatementPattern] = {
                // TODO: if OntologyConstants.NamedGraphs.KnoraExplicitNamedGraph occurs, remove it and use property path syntax to emulate inference.
                Seq(statementPattern)
            }

            def transformFilter(filterPattern: FilterPattern): Seq[QueryPattern] = Seq(filterPattern)

        }

        /**
          * Transforms non-triplestore-specific query patterns to GraphDB-specific ones.
          */
        class GraphDBConstructToConstructTransformer extends ConstructToConstructTransformer {
            def transformStatementInConstruct(statementPattern: StatementPattern): Seq[StatementPattern] = Seq(statementPattern)

            def transformStatementInWhere(statementPattern: StatementPattern, inputOrderBy: Seq[OrderCriterion]): Seq[StatementPattern] = {
                transformKnoraExplicitToGraphDBExplicit(statementPattern)
            }

            def transformFilter(filterPattern: FilterPattern): Seq[QueryPattern] = Seq(filterPattern)
        }

        /**
          * Transforms non-triplestore-specific query patterns for a triplestore that does not have inference enabled.
          */
        class NoInferenceConstructToConstructTransformer extends ConstructToConstructTransformer {
            def transformStatementInConstruct(statementPattern: StatementPattern): Seq[StatementPattern] = Seq(statementPattern)

            def transformStatementInWhere(statementPattern: StatementPattern, inputOrderBy: Seq[OrderCriterion]): Seq[StatementPattern] = {
                // TODO: if OntologyConstants.NamedGraphs.KnoraExplicitNamedGraph occurs, remove it and use property path syntax to emulate inference.
                Seq(statementPattern)
            }

            def transformFilter(filterPattern: FilterPattern): Seq[QueryPattern] = Seq(filterPattern)
        }

        /**
          * Creates a syntactically valid variable base name, based on the given entity.
          *
          * @param entity the entity to be used to create a base name for a variable.
          * @return a base name for a variable.
          */
        def escapeEntityForVariable(entity: Entity): String = {
            val entityStr = entity match {
                case QueryVariable(varName) => varName
                case IriRef(iriLiteral, _) => iriLiteral.toString
                case XsdLiteral(stringLiteral, _) => stringLiteral
                case _ => throw GravsearchException(s"A unique variable name could not be made for ${entity.toSparql}")
            }

            entityStr.replaceAll("[:/.#-]", "").replaceAll("\\s", "") // TODO: check if this is complete and if it could lead to collision of variable names
        }

        /**
          * Creates a unique variable name from the given entity and the local part of a property IRI.
          *
          * @param base        the entity to use to create the variable base name.
          * @param propertyIri the IRI of the property whose local part will be used to form the unique name.
          * @return a unique variable.
          */
        def createUniqueVariableNameFromEntityAndProperty(base: Entity, propertyIri: IRI): QueryVariable = {
            val propertyHashIndex = propertyIri.lastIndexOf('#')

            if (propertyHashIndex > 0) {
                val propertyName = propertyIri.substring(propertyHashIndex + 1)
                QueryVariable(escapeEntityForVariable(base) + "__" + escapeEntityForVariable(QueryVariable(propertyName)))
            } else {
                throw AssertionException(s"Invalid property IRI: $propertyIri")
            }
        }

        /**
          * Represents the IRIs of resources and value objects.
          *
          * @param resourceIris    resource IRIs.
          * @param valueObjectIris value object IRIs.
          */
        case class ResourceIrisAndValueObjectIris(resourceIris: Set[IRI], valueObjectIris: Set[IRI])

        /**
          * Traverses value property assertions and returns the IRIs of the value objects and the dependent resources, recursively traversing their value properties as well.
          * This is method is needed in order to determine if the whole graph pattern is still present in the results after permissions checking handled in [[ConstructResponseUtilV2.splitMainResourcesAndValueRdfData]].
          * Due to insufficient permissions, some of the resources (both main and dependent resources) and/or values may have been filtered out.
          *
          * @param valuePropertyAssertions the assertions to be traversed.
          * @return a [[ResourceIrisAndValueObjectIris]] representing all resource and value object IRIs that have been found in `valuePropertyAssertions`.
          */
        def traverseValuePropertyAssertions(valuePropertyAssertions: Map[IRI, Seq[ConstructResponseUtilV2.ValueRdfData]]): ResourceIrisAndValueObjectIris = {

            // look at the value objects and ignore the property IRIs (we are only interested in value instances)
            val resAndValObjIris: Seq[ResourceIrisAndValueObjectIris] = valuePropertyAssertions.values.flatten.foldLeft(Seq.empty[ResourceIrisAndValueObjectIris]) {
                (acc: Seq[ResourceIrisAndValueObjectIris], assertion) =>

                    if (assertion.nestedResource.nonEmpty) {
                        // this is a link value
                        // recursively traverse the dependent resource's values

                        val dependentRes: ConstructResponseUtilV2.ResourceWithValueRdfData = assertion.nestedResource.get

                        // recursively traverse the link value's nested resource and its assertions
                        val resAndValObjIrisForDependentRes: ResourceIrisAndValueObjectIris = traverseValuePropertyAssertions(dependentRes.valuePropertyAssertions)

                        // get the dependent resource's IRI from the current link value's rdf:object, or rdf:subject in case of an incoming link
                        val dependentResIri: IRI = if (assertion.isIncomingLink) {
                            assertion.assertions.getOrElse(OntologyConstants.Rdf.Subject, throw InconsistentTriplestoreDataException(s"expected ${OntologyConstants.Rdf.Subject} for link value ${assertion.valueObjectIri}"))
                        } else {
                            assertion.assertions.getOrElse(OntologyConstants.Rdf.Object, throw InconsistentTriplestoreDataException(s"expected ${OntologyConstants.Rdf.Object} for link value ${assertion.valueObjectIri}"))
                        }

                        // append results from recursion and current value object
                        ResourceIrisAndValueObjectIris(
                            resourceIris = resAndValObjIrisForDependentRes.resourceIris + dependentResIri,
                            valueObjectIris = resAndValObjIrisForDependentRes.valueObjectIris + assertion.valueObjectIri
                        ) +: acc
                    } else {
                        // not a link value or no dependent resource given (in order to avoid infinite recursion)
                        // no dependent resource present
                        // append results for current value object
                        ResourceIrisAndValueObjectIris(
                            resourceIris = Set.empty[IRI],
                            valueObjectIris = Set(assertion.valueObjectIri)
                        ) +: acc
                    }
            }

            // convert the collection of `ResourceIrisAndValueObjectIris` into one
            ResourceIrisAndValueObjectIris(
                resourceIris = resAndValObjIris.flatMap(_.resourceIris).toSet,
                valueObjectIris = resAndValObjIris.flatMap(_.valueObjectIris).toSet
            )

        }

        /**
          * Checks that the correct schema is used in a statement pattern and that the predicate is allowed in Gravsearch.
          * If the statement is in the CONSTRUCT clause in the complex schema, non-property variables may refer only to resources or Knora values.
          *
          * @param statementPattern     the statement pattern to be checked.
          * @param querySchema          the API v2 ontology schema used in the query.
          * @param typeInspectionResult the type inspection result.
          * @param inConstructClause    `true` if the statement is in the CONSTRUCT clause.
          */
        def checkStatement(statementPattern: StatementPattern, querySchema: ApiV2Schema, typeInspectionResult: GravsearchTypeInspectionResult, inConstructClause: Boolean = false): Unit = {
            // Check each entity in the statement.
            for (entity <- Seq(statementPattern.subj, statementPattern.pred, statementPattern.obj)) {
                entity match {
                    case iriRef: IriRef =>
                        // The entity is an IRI. If it has a schema, check that it's the query schema.
                        iriRef.iri.checkApiV2Schema(querySchema, throw GravsearchException(s"${iriRef.toSparql} is not in the correct schema"))

                        // If we're in the CONSTRUCT clause, don't allow rdf, rdfs, or owl IRIs.
                        if (inConstructClause && iriRef.iri.toString.contains('#')) {
                            iriRef.iri.getOntologyFromEntity.toString match {
                                case OntologyConstants.Rdf.RdfOntologyIri |
                                     OntologyConstants.Rdfs.RdfsOntologyIri |
                                     OntologyConstants.Owl.OwlOntologyIri =>
                                    throw GravsearchException(s"${iriRef.toSparql} is not allowed in a CONSTRUCT clause")

                                case _ => ()
                            }
                        }

                    case queryVar: QueryVariable =>
                        // If the entity is a variable and its type is a Knora IRI, check that the type IRI is in the query schema.
                        typeInspectionResult.getTypeOfEntity(entity) match {
                            case Some(typeInfo: GravsearchEntityTypeInfo) =>
                                typeInfo match {
                                    case propertyTypeInfo: PropertyTypeInfo =>
                                        propertyTypeInfo.objectTypeIri.checkApiV2Schema(querySchema, throw GravsearchException(s"${entity.toSparql} is not in the correct schema"))

                                    case nonPropertyTypeInfo: NonPropertyTypeInfo =>
                                        nonPropertyTypeInfo.typeIri.checkApiV2Schema(querySchema, throw GravsearchException(s"${entity.toSparql} is not in the correct schema"))

                                        // If it's a variable that doesn't represent a property, and we're using the complex schema and the statement
                                        // is in the CONSTRUCT clause, check that it refers to a resource or value.
                                        if (inConstructClause && querySchema == ApiV2WithValueObjects) {
                                            val typeIriStr = nonPropertyTypeInfo.typeIri.toString

                                            if (!(typeIriStr == OntologyConstants.KnoraApiV2WithValueObjects.Resource || OntologyConstants.KnoraApiV2WithValueObjects.ValueClasses.contains(typeIriStr))) {
                                                throw GravsearchException(s"${queryVar.toSparql} is not allowed in a CONSTRUCT clause")
                                            }
                                        }
                                }

                            case None => ()
                        }

                    case xsdLiteral: XsdLiteral =>
                        val literalOK: Boolean = if (inConstructClause) {
                            // The only literal allowed in the CONSTRUCT clause is the boolean object of knora-api:isMainResource .
                            if (xsdLiteral.datatype.toString == OntologyConstants.Xsd.Boolean) {
                                statementPattern.pred match {
                                    case iriRef: IriRef =>
                                        val iriStr = iriRef.iri.toString
                                        iriStr == OntologyConstants.KnoraApiV2Simple.IsMainResource || iriStr == OntologyConstants.KnoraApiV2WithValueObjects.IsMainResource

                                    case _ => false
                                }
                            } else {
                                false
                            }
                        } else {
                            true
                        }

                        if (!literalOK) {
                            throw GravsearchException(s"Statement not allowed in CONSTRUCT clause: ${statementPattern.toSparql.trim}")
                        }

                    case _ => ()
                }
            }

            // Check that the predicate is allowed in a Gravsearch query.
            statementPattern.pred match {
                case iriRef: IriRef =>
                    if (forbiddenPredicates.contains(iriRef.iri.toString)) {
                        throw GravsearchException(s"Predicate ${iriRef.iri.toSparql} cannot be used in a Gravsearch query")
                    }

                case _ => ()
            }
        }

        /**
          * Checks that the correct schema is used in a CONSTRUCT clause, that all the predicates used are allowed in Gravsearch,
          * and that in the complex schema, non-property variables refer only to resources or Knora values.
          *
          * @param constructClause      the CONSTRUCT clause to be checked.
          * @param typeInspectionResult the type inspection result.
          */
        def checkConstructClause(constructClause: ConstructClause, typeInspectionResult: GravsearchTypeInspectionResult): Unit = {
            for (statementPattern <- constructClause.statements) {
                checkStatement(
                    statementPattern = statementPattern,
                    querySchema = constructClause.querySchema.getOrElse(throw AssertionException(s"ConstructClause has no QuerySchema")),
                    typeInspectionResult = typeInspectionResult,
                    inConstructClause = true
                )
            }
        }

        // A set of predicates that aren't allowed in Gravsearch.
        val forbiddenPredicates: Set[IRI] =
            Set(
                OntologyConstants.Rdfs.Label,
                OntologyConstants.KnoraApiV2WithValueObjects.AttachedToUser,
                OntologyConstants.KnoraApiV2WithValueObjects.AttachedToProject,
                OntologyConstants.KnoraApiV2WithValueObjects.HasPermissions,
                OntologyConstants.KnoraApiV2WithValueObjects.CreationDate,
                OntologyConstants.KnoraApiV2WithValueObjects.LastModificationDate,
                OntologyConstants.KnoraApiV2WithValueObjects.Result,
                OntologyConstants.KnoraApiV2WithValueObjects.IsEditable,
                OntologyConstants.KnoraApiV2WithValueObjects.IsLinkProperty,
                OntologyConstants.KnoraApiV2WithValueObjects.IsLinkValueProperty,
                OntologyConstants.KnoraApiV2WithValueObjects.IsInherited,
                OntologyConstants.KnoraApiV2WithValueObjects.OntologyName,
                OntologyConstants.KnoraApiV2WithValueObjects.MappingHasName,
                OntologyConstants.KnoraApiV2WithValueObjects.HasIncomingLink,
                OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasStartYear,
                OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasEndYear,
                OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasStartMonth,
                OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasEndMonth,
                OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasStartDay,
                OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasEndDay,
                OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasStartEra,
                OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasEndEra,
                OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasCalendar,
                OntologyConstants.KnoraApiV2WithValueObjects.TextValueAsHtml,
                OntologyConstants.KnoraApiV2WithValueObjects.TextValueAsXml,
                OntologyConstants.KnoraApiV2WithValueObjects.GeometryValueAsGeometry,
                OntologyConstants.KnoraApiV2WithValueObjects.LinkValueHasTarget,
                OntologyConstants.KnoraApiV2WithValueObjects.LinkValueHasTargetIri,
                OntologyConstants.KnoraApiV2WithValueObjects.ListValueAsListNodeLabel,
                OntologyConstants.KnoraApiV2WithValueObjects.FileValueAsUrl,
                OntologyConstants.KnoraApiV2WithValueObjects.FileValueHasFilename,
                OntologyConstants.KnoraApiV2WithValueObjects.StillImageFileValueHasIIIFBaseUrl
            )
    }

    object FulltextSearch {

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
        }
    }

    object Gravsearch {

        /**
          * Constants used in the processing of Gravsearch queries.
          *
          * These constants are used to create SPARQL CONSTRUCT queries to be executed by the triplestore and to process the results that are returned.
          */
        object GravsearchConstants {

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

            // A set of types that can be treated as dates by the knora-api:toSimpleDate function.
            val dateTypes = Set(OntologyConstants.KnoraApiV2WithValueObjects.DateValue, OntologyConstants.KnoraApiV2WithValueObjects.StandoffTag)
        }

    }

}
