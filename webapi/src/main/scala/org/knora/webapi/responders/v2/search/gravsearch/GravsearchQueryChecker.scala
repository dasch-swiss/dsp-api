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

package org.knora.webapi.responders.v2.search.gravsearch

import org.knora.webapi._
import org.knora.webapi.responders.v2.search._
import org.knora.webapi.responders.v2.search.gravsearch.types.{GravsearchEntityTypeInfo, GravsearchTypeInspectionResult, NonPropertyTypeInfo, PropertyTypeInfo}

object GravsearchQueryChecker {
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
    val forbiddenPredicates: Set[IRI] = Set(
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
        OntologyConstants.KnoraApiV2WithValueObjects.HasIncomingLinkValue,
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
