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
  * See also [[OntologyConstants.CorrespondingIris]].
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

    private val UsersProperty: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraAdminV2.Users,
        propertyType = OntologyConstants.Owl.ObjectProperty,
        subjectType = Some(OntologyConstants.KnoraAdminV2.UsersResponse),
        objectType = Some(OntologyConstants.KnoraAdminV2.UserClass),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "users"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "The users returned in a UsersResponse."
                )
            )
        )
    )

    private val UserProperty: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraAdminV2.UserProperty,
        propertyType = OntologyConstants.Owl.ObjectProperty,
        subjectType = Some(OntologyConstants.KnoraAdminV2.UserResponse),
        objectType = Some(OntologyConstants.KnoraAdminV2.UserClass),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "user"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "The user returned in a UserResponse."
                )
            )
        )
    )

    private val ID: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraAdminV2.ID,
        propertyType = OntologyConstants.Owl.DatatypeProperty,
        objectType = Some(OntologyConstants.Xsd.Uri),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "ID"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "The ID of a user or group."
                )
            )
        )
    )

    private val Token: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraAdminV2.Token,
        propertyType = OntologyConstants.Owl.DatatypeProperty,
        subjectType = Some(OntologyConstants.KnoraAdminV2.UserClass),
        objectType = Some(OntologyConstants.Xsd.String),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "token"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "The user's token."
                )
            )
        )
    )

    private val Ontologies: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraAdminV2.Ontologies,
        propertyType = OntologyConstants.Owl.DatatypeProperty,
        subjectType = Some(OntologyConstants.KnoraAdminV2.ProjectClass),
        objectType = Some(OntologyConstants.Xsd.Uri),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "ontologies"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "The ontologies attached to a project."
                )
            )
        )
    )

    private val SessionID: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraAdminV2.SessionID,
        propertyType = OntologyConstants.Owl.DatatypeProperty,
        subjectType = Some(OntologyConstants.KnoraAdminV2.UserClass),
        objectType = Some(OntologyConstants.Xsd.String),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "session ID"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "The user's session ID."
                )
            )
        )
    )

    private val Description: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraAdminV2.Description,
        propertyType = OntologyConstants.Owl.DatatypeProperty,
        objectType = Some(OntologyConstants.Xsd.String),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "description"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "The description of a group or project."
                )
            )
        )
    )

    private val UsersResponse: ReadClassInfoV2 = makeClass(
        classIri = OntologyConstants.KnoraAdminV2.UsersResponse,
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "users response"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "A response providing a collection of users."
                )
            )
        ),
        directCardinalities = Map(
            OntologyConstants.KnoraAdminV2.Users -> Cardinality.MayHaveMany
        )
    )

    private val UserResponse = makeClass(
        classIri = OntologyConstants.KnoraAdminV2.UserResponse,
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "user response"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "A response providing a single user."
                )
            )
        ),
        directCardinalities = Map(
            OntologyConstants.KnoraAdminV2.UserProperty -> Cardinality.MustHaveOne
        )
    )

    private val ProjectsResponse: ReadClassInfoV2 = makeClass(
        classIri = OntologyConstants.KnoraAdminV2.ProjectsResponse,
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "projects response"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "A response providing a collection of projects."
                )
            )
        ),
        directCardinalities = Map(
            OntologyConstants.KnoraAdminV2.Projects -> Cardinality.MayHaveMany
        )
    )

    private val ProjectResponse = makeClass(
        classIri = OntologyConstants.KnoraAdminV2.ProjectResponse,
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "project response"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "A response providing a single project."
                )
            )
        ),
        directCardinalities = Map(
            OntologyConstants.KnoraAdminV2.ProjectProperty -> Cardinality.MustHaveOne
        )
    )

    private val GroupsResponse: ReadClassInfoV2 = makeClass(
        classIri = OntologyConstants.KnoraAdminV2.GroupsResponse,
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "groups response"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "A response providing a collection of groups."
                )
            )
        ),
        directCardinalities = Map(
            OntologyConstants.KnoraAdminV2.Groups -> Cardinality.MayHaveMany
        )
    )

    private val GroupResponse = makeClass(
        classIri = OntologyConstants.KnoraAdminV2.GroupResponse,
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "group response"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "A response providing a single group."
                )
            )
        ),
        directCardinalities = Map(
            OntologyConstants.KnoraAdminV2.GroupProperty -> Cardinality.MustHaveOne
        )
    )

    /**
      * Properties to remove from the ontology before converting it to the target schema.
      * See also [[OntologyConstants.CorrespondingIris]].
      */
    override val internalPropertiesToRemove: Set[SmartIri] = Set(
        OntologyConstants.KnoraAdmin.ProjectRestrictedViewSize,
        OntologyConstants.KnoraAdmin.ProjectRestrictedViewWatermark,
        OntologyConstants.KnoraAdmin.BelongsToInstitution
    ).map(_.toSmartIri)

    /**
      * Classes to remove from the ontology before converting it to the target schema.
      */
    override val internalClassesToRemove: Set[SmartIri] = Set(
        OntologyConstants.KnoraAdmin.Institution
    ).map(_.toSmartIri)

    /**
      * Cardinalities to add to the User class.
      */
    private val UserCardinalities = Map(
        OntologyConstants.KnoraAdminV2.ID -> Cardinality.MustHaveOne,
        OntologyConstants.KnoraAdminV2.Token -> Cardinality.MayHaveOne,
        OntologyConstants.KnoraAdminV2.SessionID -> Cardinality.MayHaveOne,
        OntologyConstants.KnoraAdminV2.SystemAdmin -> Cardinality.MayHaveOne
    )

    /**
      * Cardinalities to add to the Group class.
      */
    private val GroupCardinalities = Map(
        OntologyConstants.KnoraAdminV2.ID -> Cardinality.MustHaveOne
    )

    /**
      * Cardinalities to add to the Project class.
      */
    private val ProjectCardinalities = Map(
        OntologyConstants.KnoraAdminV2.Ontologies -> Cardinality.MayHaveMany
    )

    /**
      * After the ontology has been converted to the target schema, these cardinalities must be
      * added to the specified classes.
      */
    override val externalCardinalitiesToAdd: Map[SmartIri, Map[SmartIri, KnoraCardinalityInfo]] = Map(
        OntologyConstants.KnoraAdminV2.UserClass -> UserCardinalities,
        OntologyConstants.KnoraAdminV2.GroupClass -> GroupCardinalities,
        OntologyConstants.KnoraAdminV2.ProjectClass -> ProjectCardinalities
    ).map {
        case (classIri, cardinalities) =>
            classIri.toSmartIri -> cardinalities.map {
                case (propertyIri, cardinality) =>
                    propertyIri.toSmartIri -> Cardinality.KnoraCardinalityInfo(cardinality)
            }
    }

    /**
      * Classes that need to be added to the ontology after converting it to the target schema.
      */
    override val externalClassesToAdd: Map[SmartIri, ReadClassInfoV2] = Set(
        UsersResponse,
        UserResponse,
        ProjectsResponse,
        ProjectResponse,
        GroupsResponse,
        GroupResponse
    ).map {
        classInfo => classInfo.entityInfoContent.classIri -> classInfo
    }.toMap

    /**
      * Properties that need to be added to the ontology after converting it to the target schema.
      * See also [[OntologyConstants.CorrespondingIris]].
      */
    override val externalPropertiesToAdd: Map[SmartIri, ReadPropertyInfoV2] = Set(
        UsersProperty,
        UserProperty,
        ID,
        Token,
        SessionID,
        Ontologies,
        Description
    ).map {
        propertyInfo => propertyInfo.entityInfoContent.propertyIri -> propertyInfo
    }.toMap


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
      * @param propertyIri   the IRI of the property.
      * @param propertyType  the type of the property (owl:ObjectProperty, owl:DatatypeProperty, or rdf:Property).
      * @param predicates    the property's predicates.
      * @param subjectType   the required type of the property's subject.
      * @param objectType    the required type of the property's object.
      * @return a [[ReadPropertyInfoV2]].
      */
    private def makeProperty(propertyIri: IRI,
                             propertyType: IRI,
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
                }.toMap
            )
        )
    }

    /**
      * Makes a [[ReadClassInfoV2]].
      *
      * @param classIri            the IRI of the class.
      * @param predicates          the predicates of the class.
      * @param directCardinalities the direct cardinalities of the class.
      * @return a [[ReadClassInfoV2]].
      */
    private def makeClass(classIri: IRI,
                          predicates: Seq[PredicateInfoV2] = Seq.empty[PredicateInfoV2],
                          directCardinalities: Map[IRI, Cardinality.Value] = Map.empty[IRI, Cardinality.Value]): ReadClassInfoV2 = {
        val rdfType = OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.Class.toSmartIri))
        )

        ReadClassInfoV2(
            entityInfoContent = ClassInfoContentV2(
                classIri = classIri.toSmartIri,
                predicates = predicates.map {
                    pred => pred.predicateIri -> pred
                }.toMap + rdfType,
                directCardinalities = directCardinalities.map {
                    case (propertyIri, cardinality) => propertyIri.toSmartIri -> KnoraCardinalityInfo(cardinality)
                },
                subClassOf = Set.empty,
                ontologySchema = ApiV2Complex
            ),
            allBaseClasses = Set.empty
        )
    }
}
