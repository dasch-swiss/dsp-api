/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.util.search.gravsearch

import zio.*

import dsp.errors.AssertionException
import dsp.errors.GravsearchException
import org.knora.webapi.*
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.util.search.*
import org.knora.webapi.messages.util.search.gravsearch.types.GravsearchEntityTypeInfo
import org.knora.webapi.messages.util.search.gravsearch.types.GravsearchTypeInspectionResult
import org.knora.webapi.messages.util.search.gravsearch.types.NonPropertyTypeInfo
import org.knora.webapi.messages.util.search.gravsearch.types.PropertyTypeInfo

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
  def checkStatement(
    statementPattern: StatementPattern,
    querySchema: ApiV2Schema,
    typeInspectionResult: GravsearchTypeInspectionResult,
    inConstructClause: Boolean = false,
  ): Unit = {
    // Check each entity in the statement.
    for (entity <- Seq(statementPattern.subj, statementPattern.pred, statementPattern.obj)) {
      entity match {
        case iriRef: IriRef =>
          if (iriRef.iri.isApiV2Schema(querySchema)) iriRef.iri
          else throw GravsearchException(s"${iriRef.toSparql} is not in the correct schema")

          if (inConstructClause && iriRef.iri.toString.contains('#')) {
            iriRef.iri.getOntologyFromEntity.toString match {
              case OntologyConstants.Rdf.RdfOntologyIri | OntologyConstants.Rdfs.RdfsOntologyIri |
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
                  if (propertyTypeInfo.objectTypeIri.isApiV2Schema(querySchema)) propertyTypeInfo.objectTypeIri
                  else throw GravsearchException(s"${entity.toSparql} is not in the correct schema")

                case nonPropertyTypeInfo: NonPropertyTypeInfo =>
                  if (nonPropertyTypeInfo.typeIri.isApiV2Schema(querySchema)) nonPropertyTypeInfo.typeIri
                  else throw GravsearchException(s"${entity.toSparql} is not in the correct schema")

                  // If it's a variable that doesn't represent a property, and we're using the complex schema and the statement
                  // is in the CONSTRUCT clause, check that it refers to a resource or value.
                  if (inConstructClause && querySchema == ApiV2Complex) {
                    if (!(nonPropertyTypeInfo.isResourceType || nonPropertyTypeInfo.isValueType)) {
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
                  iriStr == OntologyConstants.KnoraApiV2Simple.IsMainResource || iriStr == OntologyConstants.KnoraApiV2Complex.IsMainResource

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
  def checkConstructClause(
    constructClause: ConstructClause,
    typeInspectionResult: GravsearchTypeInspectionResult,
  ): Task[Unit] =
    for {
      querySchema <-
        ZIO
          .fromOption(constructClause.querySchema)
          .orElseFail(AssertionException(s"ConstructClause has no QuerySchema"))
      _ <- ZIO.attempt(
             for (statementPattern <- constructClause.statements) {
               checkStatement(
                 statementPattern = statementPattern,
                 querySchema = querySchema,
                 typeInspectionResult = typeInspectionResult,
                 inConstructClause = true,
               )
             },
           )
    } yield ()

  // A set of predicates that aren't allowed in Gravsearch.
  val forbiddenPredicates: Set[IRI] = Set(
    OntologyConstants.KnoraApiV2Complex.AttachedToUser,
    OntologyConstants.KnoraApiV2Complex.HasPermissions,
    OntologyConstants.KnoraApiV2Complex.CreationDate,
    OntologyConstants.KnoraApiV2Complex.LastModificationDate,
    OntologyConstants.KnoraApiV2Complex.Result,
    OntologyConstants.KnoraApiV2Complex.IsEditable,
    OntologyConstants.KnoraApiV2Complex.IsLinkProperty,
    OntologyConstants.KnoraApiV2Complex.IsLinkValueProperty,
    OntologyConstants.KnoraApiV2Complex.IsInherited,
    OntologyConstants.KnoraApiV2Complex.OntologyName,
    OntologyConstants.KnoraApiV2Complex.MappingHasName,
    OntologyConstants.KnoraApiV2Complex.HasIncomingLinkValue,
    OntologyConstants.KnoraApiV2Complex.DateValueHasStartYear,
    OntologyConstants.KnoraApiV2Complex.DateValueHasEndYear,
    OntologyConstants.KnoraApiV2Complex.DateValueHasStartMonth,
    OntologyConstants.KnoraApiV2Complex.DateValueHasEndMonth,
    OntologyConstants.KnoraApiV2Complex.DateValueHasStartDay,
    OntologyConstants.KnoraApiV2Complex.DateValueHasEndDay,
    OntologyConstants.KnoraApiV2Complex.DateValueHasStartEra,
    OntologyConstants.KnoraApiV2Complex.DateValueHasEndEra,
    OntologyConstants.KnoraApiV2Complex.DateValueHasCalendar,
    OntologyConstants.KnoraApiV2Complex.TextValueAsHtml,
    OntologyConstants.KnoraApiV2Complex.TextValueAsXml,
    OntologyConstants.KnoraApiV2Complex.GeometryValueAsGeometry,
    OntologyConstants.KnoraApiV2Complex.LinkValueHasTarget,
    OntologyConstants.KnoraApiV2Complex.LinkValueHasTargetIri,
    OntologyConstants.KnoraApiV2Complex.FileValueAsUrl,
    OntologyConstants.KnoraApiV2Complex.FileValueHasFilename,
    OntologyConstants.KnoraApiV2Complex.StillImageFileValueHasIIIFBaseUrl,
  )
}
