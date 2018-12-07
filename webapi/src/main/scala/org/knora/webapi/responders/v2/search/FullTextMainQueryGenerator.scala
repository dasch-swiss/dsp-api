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

package org.knora.webapi.responders.v2.search

import org.knora.webapi.util.IriConversions._
import org.knora.webapi.util.StringFormatter
import org.knora.webapi.{IRI, OntologyConstants}

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
    }

    /**
      * Creates a CONSTRUCT query for the given resource and value object IRIs.
      *
      * @param resourceIris    the IRIs of the resources to be queried.
      * @param valueObjectIris the IRIs of the value objects to be queried.
      * @return a [[ConstructQuery]].
      */
    def createMainQuery(resourceIris: Set[IRI], valueObjectIris: Set[IRI]): ConstructQuery = {
        implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

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
