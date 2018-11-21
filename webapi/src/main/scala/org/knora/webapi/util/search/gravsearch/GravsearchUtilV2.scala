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
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.store.triplestoremessages.{SparqlConstructResponse, SparqlSelectResponse, VariableResultsRow}
import org.knora.webapi.util.{ConstructResponseUtilV2, ErrorHandlingMap, SmartIri, StringFormatter}
import org.knora.webapi.util.search._
import org.knora.webapi.util.IriConversions._


/**
  * Utility methods for [[org.knora.webapi.responders.v2.SearchResponderV2]] (Gravsearch).
  */
object GravsearchUtilV2 {

    protected implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    object SparqlTransformation {

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

        /**
          * Creates a CONSTRUCT query for the given resource and value object IRIs.
          *
          * @param resourceIris    the IRIs of the resources to be queried.
          * @param valueObjectIris the IRIs of the value objects to be queried.
          * @return a [[ConstructQuery]].
          */
        def createMainQuery(resourceIris: Set[IRI], valueObjectIris: Set[IRI]): ConstructQuery = {

            import FullTextSearchConstants._

            // WHERE patterns for the resources: check that the resource are a knora-base:Resource and that it is not marked as deleted
            val wherePatternsForResources = Seq(
                ValuesPattern(resourceVar, resourceIris.map(iri => IriRef(iri.toSmartIri))), // a ValuePattern that binds the resource IRIs to the resource variable
                StatementPattern.makeInferred(subj = resourceVar, pred = IriRef(OntologyConstants.Rdf.Type.toSmartIri), obj = IriRef(OntologyConstants.KnoraBase.Resource.toSmartIri)),
                StatementPattern.makeExplicit(subj = resourceVar, pred = IriRef(OntologyConstants.KnoraBase.IsDeleted.toSmartIri), obj = XsdLiteral(value = "false", datatype = OntologyConstants.Xsd.Boolean.toSmartIri)),
                StatementPattern.makeExplicit(subj = resourceVar, pred = resourcePropVar, obj = resourceObjectVar)
            )

            //  mark resources as the main resource and a knora-base:Resource in CONSTRUCT clause and return direct assertions about resources
            val constructPatternsForResources = Seq(
                StatementPattern(subj = resourceVar, pred = IriRef(OntologyConstants.KnoraBase.IsMainResource.toSmartIri), obj = XsdLiteral(value = "true", datatype = OntologyConstants.Xsd.Boolean.toSmartIri)),
                StatementPattern(subj = resourceVar, pred = IriRef(OntologyConstants.Rdf.Type.toSmartIri), obj = IriRef(OntologyConstants.KnoraBase.Resource.toSmartIri)),
                StatementPattern(subj = resourceVar, pred = resourcePropVar, obj = resourceObjectVar)
            )

            if (valueObjectIris.nonEmpty) {
                // value objects are to be queried

                // WHERE patterns for statements about the resources' values
                val wherePatternsForValueObjects = Seq(
                    ValuesPattern(resourceValueObject, valueObjectIris.map(iri => IriRef(iri.toSmartIri))),
                    StatementPattern.makeInferred(subj = resourceVar, pred = IriRef(OntologyConstants.KnoraBase.HasValue.toSmartIri), obj = resourceValueObject),
                    StatementPattern.makeExplicit(subj = resourceVar, pred = resourceValueProp, obj = resourceValueObject),
                    StatementPattern.makeExplicit(subj = resourceValueObject, pred = IriRef(OntologyConstants.KnoraBase.IsDeleted.toSmartIri), obj = XsdLiteral(value = "false", datatype = OntologyConstants.Xsd.Boolean.toSmartIri)),
                    StatementPattern.makeExplicit(subj = resourceValueObject, pred = resourceValueObjectProp, obj = resourceValueObjectObj)
                )

                // return assertions about value objects
                val constructPatternsForValueObjects = Seq(
                    StatementPattern(subj = resourceVar, pred = IriRef(OntologyConstants.KnoraBase.HasValue.toSmartIri), obj = resourceValueObject),
                    StatementPattern(subj = resourceVar, pred = resourceValueProp, obj = resourceValueObject),
                    StatementPattern(subj = resourceValueObject, pred = resourceValueObjectProp, obj = resourceValueObjectObj)
                )

                // WHERE patterns for standoff belonging to value objects (if any)
                val wherePatternsForStandoff = Seq(
                    ValuesPattern(resourceValueObject, valueObjectIris.map(iri => IriRef(iri.toSmartIri))),
                    StatementPattern.makeExplicit(subj = resourceValueObject, pred = IriRef(OntologyConstants.KnoraBase.ValueHasStandoff.toSmartIri), obj = standoffNodeVar),
                    StatementPattern.makeExplicit(subj = standoffNodeVar, pred = standoffPropVar, obj = standoffValueVar)
                )

                // return standoff
                val constructPatternsForStandoff = Seq(
                    StatementPattern(subj = resourceValueObject, pred = IriRef(OntologyConstants.KnoraBase.ValueHasStandoff.toSmartIri), obj = standoffNodeVar),
                    StatementPattern(subj = standoffNodeVar, pred = standoffPropVar, obj = standoffValueVar)
                )

                ConstructQuery(
                    constructClause = ConstructClause(
                        statements = constructPatternsForResources ++ constructPatternsForValueObjects ++ constructPatternsForStandoff
                    ),
                    whereClause = WhereClause(
                        Seq(
                            UnionPattern(
                                Seq(wherePatternsForResources, wherePatternsForValueObjects, wherePatternsForStandoff)
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

            import SparqlTransformation._

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
                            val valObjVar = createUniqueVariableNameFromEntityAndProperty(statementPattern.obj, OntologyConstants.KnoraBase.LinkValue)

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

        /**
          * Collects the Iris of dependent resources per main resource from the results returned by the prequery.
          * Dependent resource Iris are grouped by main resource.
          *
          * @param prequeryResponse the results returned by the prequery.
          * @param transformer      the transformer that was used to turn the Gravsearch query into the prequery.
          * @param mainResourceVar  the variable representing the main resource.
          * @return a [[DependentResourcesPerMainResource]].
          */
        def getDependentResourceIrisPerMainResource(prequeryResponse: SparqlSelectResponse,
                                                    transformer: NonTriplestoreSpecificConstructToSelectTransformer,
                                                    mainResourceVar: QueryVariable): DependentResourcesPerMainResource = {

            // variables representing dependent resources
            val dependentResourceVariablesGroupConcat: Set[QueryVariable] = transformer.getDependentResourceVariablesGroupConcat

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

                                    // IRIs are concatenated by GROUP_CONCAT using a separator, split them
                                    depResIri.split(transformer.groupConcatSeparator).toSeq

                                case None => Set.empty[IRI] // no Iri present since variable was inside aan OPTIONAL or UNION
                            }

                    }

                    acc + (mainResIri -> dependentResIris)
            }

            DependentResourcesPerMainResource(new ErrorHandlingMap(dependentResourcesPerMainRes, { key => throw GravsearchException(s"main resource not found: $key") }))
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
        def getValueObjectVarsAndIrisPerMainResource(prequeryResponse: SparqlSelectResponse,
                                                     transformer: NonTriplestoreSpecificConstructToSelectTransformer,
                                                     mainResourceVar: QueryVariable): ValueObjectVariablesAndValueObjectIris = {

            // value objects variables present in the prequery's WHERE clause
            val valueObjectVariablesConcat = transformer.getValueObjectVarsGroupConcat

            val valueObjVarsAndIris: Map[IRI, Map[QueryVariable, Set[IRI]]] = prequeryResponse.results.bindings.foldLeft(Map.empty[IRI, Map[QueryVariable, Set[IRI]]]) {
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

                                    // IRIs are concatenated by GROUP_CONCAT using a separator, split them
                                    valObjIris.split(transformer.groupConcatSeparator).toSet

                                case None => Set.empty[IRI] // since variable was inside aan OPTIONAL or UNION

                            }

                            valueObjVarConcat -> valueObjIris
                    }.toMap

                    val valueObjVarToIrisErrorHandlingMap = new ErrorHandlingMap(valueObjVarToIris, { key: QueryVariable => throw GravsearchException(s"variable not found: $key") })
                    acc + (mainResIri -> valueObjVarToIrisErrorHandlingMap)
            }

            ValueObjectVariablesAndValueObjectIris(new ErrorHandlingMap(valueObjVarsAndIris, { key => throw GravsearchException(s"main resource not found: $key") }))
        }

        /**
          * Removes the main resources from the main query's results that the requesting user has insufficient permissions on.
          * If the user does not have full permission on the full graph pattern (main resource, dependent resources, value objects)
          * then the main resource is excluded completely from the results.
          *
          * @param mainQueryResponse                     results returned by the main query.
          * @param dependentResourceIrisPerMainResource  Iris of dependent resources per main resource.
          * @param valueObjectVarsAndIrisPerMainResource variable names and Iris of value objects per main resource.
          * @return a Map of main resource Iris and their values.
          */
        def getMainQueryResultsWithFullGraphPattern(mainQueryResponse: SparqlConstructResponse,
                                                    dependentResourceIrisPerMainResource: DependentResourcesPerMainResource,
                                                    valueObjectVarsAndIrisPerMainResource: ValueObjectVariablesAndValueObjectIris,
                                                    requestingUser: UserADM): Map[IRI, ConstructResponseUtilV2.ResourceWithValueRdfData] = {

            import SparqlTransformation._

            // separate main resources and value objects (dependent resources are nested)
            // this method removes resources and values the requesting users has insufficient permissions on (missing view permissions).
            val queryResultsSep: Map[IRI, ConstructResponseUtilV2.ResourceWithValueRdfData] = ConstructResponseUtilV2.splitMainResourcesAndValueRdfData(constructQueryResults = mainQueryResponse, requestingUser = requestingUser)

            queryResultsSep.foldLeft(Map.empty[IRI, ConstructResponseUtilV2.ResourceWithValueRdfData]) {
                case (acc: Map[IRI, ConstructResponseUtilV2.ResourceWithValueRdfData], (mainResIri: IRI, values: ConstructResponseUtilV2.ResourceWithValueRdfData)) =>

                    // check for presence of dependent resources: dependentResourceIrisPerMainResource plus the dependent resources whose Iris where provided in the Gravsearch query.
                    val expectedDependentResources: Set[IRI] = dependentResourceIrisPerMainResource.dependentResourcesPerMainResource(mainResIri) /*++ dependentResourceIrisFromTypeInspection*/
                    // TODO: https://github.com/dhlab-basel/Knora/issues/924

                    // println(expectedDependentResources)

                    // check for presence of value objects: valueObjectIrisPerMainResource
                    val expectedValueObjects: Set[IRI] = valueObjectVarsAndIrisPerMainResource.valueObjectVariablesAndValueObjectIris(mainResIri).values.flatten.toSet

                    // value property assertions for the current main resource
                    val valuePropAssertions: Map[IRI, Seq[ConstructResponseUtilV2.ValueRdfData]] = values.valuePropertyAssertions

                    // all the IRIs of dependent resources and value objects contained in `valuePropAssertions`
                    val resAndValueObjIris: ResourceIrisAndValueObjectIris = traverseValuePropertyAssertions(valuePropAssertions)

                    // check if the client has sufficient permissions on all dependent resources present in the graph pattern
                    val allDependentResources: Boolean = resAndValueObjIris.resourceIris.intersect(expectedDependentResources) == expectedDependentResources

                    // check if the client has sufficient permissions on all value objects IRIs present in the graph pattern
                    val allValueObjects: Boolean = resAndValueObjIris.valueObjectIris.intersect(expectedValueObjects) == expectedValueObjects

                    // println(allValueObjects)

                    /*println("+++++++++")

                    println("graph pattern check for " + mainResIri)

                    println("expected dependent resources: " + expectedDependentResources)

                    println("all expected dependent resources present: " + allDependentResources)

                    println("given dependent resources " + resAndValueObjIris.resourceIris)

                    println("expected value objs: " + expectedValueObjects)

                    println("given value objs: " + resAndValueObjIris.valueObjectIris)

                    println("all expected value objects present: " + allValueObjects)*/

                    if (allDependentResources && allValueObjects) {
                        // sufficient permissions, include the main resource and its values
                        acc + (mainResIri -> values)
                    } else {
                        // insufficient permissions, skip the resource
                        acc
                    }
            }

        }

        /**
          * Given the results of the main query, filters out all values that the user did not ask for in the input query,
          * i.e that are not present in its CONSTRUCT clause.
          *
          * @param queryResultsWithFullGraphPattern        results with full graph pattern (that user has sufficient permissions on).
          * @param valueObjectVarsAndIrisPerMainResource   value object variables and their Iris per main resource.
          * @param allResourceVariablesFromTypeInspection  all variables representing resources.
          * @param dependentResourceIrisFromTypeInspection Iris of dependent resources used in the input query.
          * @param transformer                             the transformer that was used to turn the input query into the prequery.
          * @param typeInspectionResult                    results of type inspection of the input query.
          * @return results with only the values the user asked for in the input query's CONSTRUCT clause.
          */
        def getRequestedValuesFromResultsWithFullGraphPattern(queryResultsWithFullGraphPattern: Map[IRI, ConstructResponseUtilV2.ResourceWithValueRdfData],
                                                              valueObjectVarsAndIrisPerMainResource: ValueObjectVariablesAndValueObjectIris,
                                                              allResourceVariablesFromTypeInspection: Set[QueryVariable],
                                                              dependentResourceIrisFromTypeInspection: Set[IRI],
                                                              transformer: NonTriplestoreSpecificConstructToSelectTransformer,
                                                              typeInspectionResult: GravsearchTypeInspectionResult,
                                                              inputQuery: ConstructQuery): Map[IRI, ConstructResponseUtilV2.ResourceWithValueRdfData] = {

            // sort out those value objects that the user did not ask for in the input query's CONSTRUCT clause
            // those are present in the input query's WHERE clause but not in its CONSTRUCT clause

            // for each resource variable (both main and dependent resources),
            // collect the value object variables associated with it in the input query's CONSTRUCT clause
            // resource variables from types inspection are used
            //
            // Example: the statement "?page incunabula:seqnum ?seqnum ." is contained in the input query's CONSTRUCT clause.
            // ?seqnum (?seqnum__Concat) is a requested value and is associated with the resource variable ?page.
            val requestedValueObjectVariablesForAllResVars: Set[QueryVariable] = allResourceVariablesFromTypeInspection.flatMap {
                resVar =>
                    collectValueVariablesForResource(inputQuery.constructClause, resVar, typeInspectionResult, transformer.groupConcatVariableSuffix)
            }

            // for each resource Iri (only dependent resources),
            // collect the value object variables associated with it in the input query's CONSTRUCT clause
            // dependent resource Iris from types inspection are used
            //
            // Example: the statement "<http://rdfh.ch/5e77e98d2603> incunabula:title ?title ." is contained in the input query's CONSTRUCT clause.
            // ?title (?title__Concat) is a requested value and is associated with the dependent resource Iri <http://rdfh.ch/5e77e98d2603>.
            val requestedValueObjectVariablesForDependentResIris: Set[QueryVariable] = dependentResourceIrisFromTypeInspection.flatMap {
                depResIri =>
                    collectValueVariablesForResource(inputQuery.constructClause, IriRef(iri = depResIri.toSmartIri), typeInspectionResult, transformer.groupConcatVariableSuffix)
            }

            // combine all value object variables into one set
            val allRequestedValueObjectVariables: Set[QueryVariable] = requestedValueObjectVariablesForAllResVars ++ requestedValueObjectVariablesForDependentResIris

            // collect requested value object Iris for each main resource
            val requestedValObjIrisPerMainResource: Map[IRI, Set[IRI]] = queryResultsWithFullGraphPattern.keySet.map {
                mainResIri =>

                    // get all value object variables and Iris for the current main resource
                    val valueObjIrisForRes: Map[QueryVariable, Set[IRI]] = valueObjectVarsAndIrisPerMainResource.valueObjectVariablesAndValueObjectIris(mainResIri)

                    // get those value object Iris from the results that the user asked for in the input query's CONSTRUCT clause
                    val valObjIrisRequestedForRes: Set[IRI] = allRequestedValueObjectVariables.flatMap {
                        requestedQueryVar: QueryVariable =>
                            valueObjIrisForRes.getOrElse(requestedQueryVar, throw AssertionException(s"key $requestedQueryVar is absent in prequery's value object IRIs collection for resource $mainResIri"))
                    }

                    mainResIri -> valObjIrisRequestedForRes
            }.toMap

            // for each main resource, get only the requested value objects
            queryResultsWithFullGraphPattern.map {
                case (mainResIri: IRI, assertions: ConstructResponseUtilV2.ResourceWithValueRdfData) =>

                    // get the Iris of all the value objects requested for the current main resource
                    val valueObjIrisRequestedForRes: Set[IRI] = requestedValObjIrisPerMainResource.getOrElse(mainResIri, throw AssertionException(s"key $mainResIri is absent in requested value object IRIs collection for resource $mainResIri"))

                    /**
                      * Recursively filters out those values that the user does not want to see.
                      * Starts with the values of the main resource and also processes link values, possibly containing dependent resources with values.
                      *
                      * @param values the values to be filtered.
                      * @return filtered values.
                      */
                    def traverseAndFilterValues(values: ConstructResponseUtilV2.ResourceWithValueRdfData): Map[IRI, Seq[ConstructResponseUtilV2.ValueRdfData]] = {
                        values.valuePropertyAssertions.foldLeft(Map.empty[IRI, Seq[ConstructResponseUtilV2.ValueRdfData]]) {
                            case (acc, (propIri: IRI, values: Seq[ConstructResponseUtilV2.ValueRdfData])) =>

                                // filter values for the current resource
                                val valuesFiltered: Seq[ConstructResponseUtilV2.ValueRdfData] = values.filter {
                                    valueObj: ConstructResponseUtilV2.ValueRdfData =>
                                        // only return those value objects whose Iris are contained in valueObjIrisRequestedForRes
                                        valueObjIrisRequestedForRes(valueObj.valueObjectIri)
                                }

                                // if there are link values including a target resource, apply filter to their values too
                                val valuesFilteredRecursively: Seq[ConstructResponseUtilV2.ValueRdfData] = valuesFiltered.map {
                                    valObj: ConstructResponseUtilV2.ValueRdfData =>
                                        if (valObj.nestedResource.nonEmpty) {

                                            val targetResourceAssertions: ConstructResponseUtilV2.ResourceWithValueRdfData = valObj.nestedResource.get

                                            // apply filter to the target resource's values
                                            val targetResourceAssertionsFiltered: Map[IRI, Seq[ConstructResponseUtilV2.ValueRdfData]] = traverseAndFilterValues(targetResourceAssertions)

                                            valObj.copy(
                                                nestedResource = Some(targetResourceAssertions.copy(
                                                    valuePropertyAssertions = targetResourceAssertionsFiltered
                                                ))
                                            )
                                        } else {
                                            valObj
                                        }
                                }

                                // ignore properties if there are no value objects to be displayed.
                                // if the user does not want to see a value, the property pointing to that value has to be ignored.
                                if (valuesFilteredRecursively.nonEmpty) {
                                    acc + (propIri -> valuesFilteredRecursively)
                                } else {
                                    // ignore this property since there are no value objects
                                    // Example: the input query's WHERE clause contains the statement "?page incunabula:seqnum ?seqnum .",
                                    // but the statement is not present in its CONSTRUCT clause. Therefore, the property incunabula:seqnum can be ignored
                                    // since no value objects are returned for it.
                                    acc
                                }
                        }
                    }

                    // filter values for the current main resource
                    val requestedValuePropertyAssertions: Map[IRI, Seq[ConstructResponseUtilV2.ValueRdfData]] = traverseAndFilterValues(assertions)

                    // only return the requested values for the current main resource
                    mainResIri -> assertions.copy(
                        valuePropertyAssertions = requestedValuePropertyAssertions
                    )
            }
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
        case class ValueObjectVariablesAndValueObjectIris(valueObjectVariablesAndValueObjectIris: Map[IRI, Map[QueryVariable, Set[IRI]]])

    }

}
