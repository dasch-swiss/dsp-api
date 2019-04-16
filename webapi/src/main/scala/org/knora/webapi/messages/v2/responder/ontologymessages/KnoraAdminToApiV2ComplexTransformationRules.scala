/*
 * Copyright © 2015-2019 the contributors (see Contributors.md).
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

package org.knora.webapi.messages.v2.responder.ontologymessages

import org.knora.webapi._
import org.knora.webapi.messages.store.triplestoremessages.{OntologyLiteralV2, SmartIriLiteralV2, StringLiteralV2}
import org.knora.webapi.messages.v2.responder.ontologymessages.Cardinality.KnoraCardinalityInfo
import org.knora.webapi.util.IriConversions._
import org.knora.webapi.util.{SmartIri, StringFormatter}

/**
  * Rules for converting `knora-admin` from the internal schema to the [[ApiV2Complex]] schema.
  */
object KnoraAdminToApiV2ComplexTransformationRules extends OntologyTransformationRules {
    private implicit val stringFormatter: StringFormatter = StringFormatter.getInstanceForConstantOntologies

    /**
      * The metadata to be used for the transformed ontology.
      */
    override val ontologyMetadata: OntologyMetadataV2 = OntologyMetadataV2(
        ontologyIri = OntologyConstants.KnoraAdminV2.KnoraAdminOntologyIri.toSmartIri,
        projectIri = Some(OntologyConstants.KnoraAdmin.SystemProject.toSmartIri),
        label = Some("The knora-admin ontology in the complex schema")
    )

    /**
      * Properties to remove from the ontology before converting it to the target schema.
      */
    override val internalPropertiesToRemove: Set[SmartIri] = Set.empty // TODO

    /**
      * Classes to remove from the ontology before converting it to the target schema.
      */
    override val internalClassesToRemove: Set[SmartIri] = Set.empty // TODO

    /**
      * After the ontology has been converted to the target schema, these cardinalities must be
      * added to the specified classes.
      */
    override val externalCardinalitiesToAdd: Map[SmartIri, Map[SmartIri, KnoraCardinalityInfo]] = Map.empty // TODO

    /**
      * Classes that need to be added to the ontology after converting it to the target schema.
      */
    override val externalClassesToAdd: Map[SmartIri, ReadClassInfoV2] = Map.empty // TODO

    /**
      * Properties that need to be added to the ontology after converting it to the target schema.
      */
    override val externalPropertiesToAdd: Map[SmartIri, ReadPropertyInfoV2] = Map.empty // TODO

    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Convenience functions for building ontology entities, to make the code above more concise.

    /**
      * Makes a [[PredicateInfoV2]].
      *
      * @param predicateIri    the IRI of the predicate.
      * @param objects         the non-language-specific objects of the predicate.
      * @param objectsWithLang the language-specific objects of the predicate.
      * @return a [[PredicateInfoV2]].
      */
    private def makePredicate(predicateIri: IRI,
                              objects: Seq[OntologyLiteralV2] = Seq.empty[OntologyLiteralV2],
                              objectsWithLang: Map[String, String] = Map.empty[String, String]): PredicateInfoV2 = {
        PredicateInfoV2(
            predicateIri = predicateIri.toSmartIri,
            objects = objects ++ objectsWithLang.map {
                case (lang, str) => StringLiteralV2(str, Some(lang))
            }
        )
    }

    /**
      * Makes a [[ReadPropertyInfoV2]].
      *
      * @param propertyIri     the IRI of the property.
      * @param propertyType    the type of the property (owl:ObjectProperty, owl:DatatypeProperty, or rdf:Property).
      * @param subPropertyOf   the set of direct superproperties of this property.
      * @param predicates      the property's predicates.
      * @param subjectType     the required type of the property's subject.
      * @param objectType      the required type of the property's object.
      * @return a [[ReadPropertyInfoV2]].
      */
    private def makeProperty(propertyIri: IRI,
                             propertyType: IRI,
                             subPropertyOf: Set[IRI] = Set.empty[IRI],
                             predicates: Seq[PredicateInfoV2] = Seq.empty[PredicateInfoV2],
                             subjectType: Option[IRI] = None,
                             objectType: Option[IRI] = None): ReadPropertyInfoV2 = {
        val propTypePred = makePredicate(
            predicateIri = OntologyConstants.Rdf.Type,
            objects = Seq(SmartIriLiteralV2(propertyType.toSmartIri))
        )

        val maybeSubjectTypePred = subjectType.map {
            subjType =>
                makePredicate(
                    predicateIri = OntologyConstants.KnoraApiV2Complex.SubjectType,
                    objects = Seq(SmartIriLiteralV2(subjType.toSmartIri))
                )
        }

        val maybeObjectTypePred = objectType.map {
            objType =>
                makePredicate(
                    predicateIri = OntologyConstants.KnoraApiV2Complex.ObjectType,
                    objects = Seq(SmartIriLiteralV2(objType.toSmartIri))
                )
        }

        val predsWithTypes = predicates ++ maybeSubjectTypePred ++ maybeObjectTypePred :+ propTypePred

        ReadPropertyInfoV2(
            entityInfoContent = PropertyInfoContentV2(
                propertyIri = propertyIri.toSmartIri,
                ontologySchema = ApiV2Complex,
                predicates = predsWithTypes.map {
                    pred => pred.predicateIri -> pred
                }.toMap,
                subPropertyOf = subPropertyOf.map(iri => iri.toSmartIri)
            )
        )
    }
}
