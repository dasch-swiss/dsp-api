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

package org.knora.webapi.messages.v2.responder.ontologymessages

import org.knora.webapi._
import org.knora.webapi.messages.store.triplestoremessages.{OntologyLiteralV2, SmartIriLiteralV2, StringLiteralV2}
import org.knora.webapi.messages.v2.responder.ontologymessages.Cardinality.KnoraCardinalityInfo
import org.knora.webapi.util.IriConversions._
import org.knora.webapi.util.{SmartIri, StringFormatter}

/**
  * Represents the `knora-api` ontology, version 2, in the [[ApiV2Simple]] schema.
  */
object KnoraApiV2Simple {

    private implicit val stringFormatter: StringFormatter = StringFormatter.getInstanceForConstantOntologies

    val OntologyMetadata = OntologyMetadataV2(
        ontologyIri = OntologyConstants.KnoraApiV2Simple.KnoraApiOntologyIri.toSmartIri,
        projectIri = Some(OntologyConstants.KnoraBase.SystemProject.toSmartIri),
        label = Some("The simplified knora-api ontology")
    )

    private val Result: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2Simple.Result,
        propertyType = OntologyConstants.Owl.DatatypeProperty,
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.DE -> "Ergebnis",
                    LanguageCodes.EN -> "result",
                    LanguageCodes.FR -> "résultat",
                    LanguageCodes.IT -> "risultato"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Provides a message indicating that an operation was successful"
                )
            )
        ),
        objectType = Some(OntologyConstants.Xsd.String)
    )

    private val Error: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2Simple.Error,
        propertyType = OntologyConstants.Owl.DatatypeProperty,
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.DE -> "Fehler",
                    LanguageCodes.EN -> "error",
                    LanguageCodes.FR -> "erreur",
                    LanguageCodes.IT -> "errore"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Provides a message indicating that an operation was unsuccessful"
                )
            )
        ),
        objectType = Some(OntologyConstants.Xsd.String)
    )

    private val ResourceProperty: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2Simple.ResourceProperty,
        propertyType = OntologyConstants.Rdf.Property,
        subjectType = Some(OntologyConstants.KnoraApiV2Simple.Resource),
        subPropertyOf = Set(OntologyConstants.KnoraApiV2Simple.ResourceProperty),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Resource property"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "The base property of properties that point from Knora resources to Knora resources or values. These properties are required to have cardinalities in the resource classes in which they are used."
                )
            )
        )
    )

    private val HasValue: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2Simple.HasValue,
        propertyType = OntologyConstants.Owl.DatatypeProperty,
        subjectType = Some(OntologyConstants.KnoraApiV2Simple.Resource),
        subPropertyOf = Set(OntologyConstants.KnoraApiV2Simple.ResourceProperty),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "has value"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "The base property of properties that point from Knora resources to Knora values."
                )
            )
        )
    )

    private val SubjectType: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2Simple.SubjectType,
        propertyType = OntologyConstants.Rdf.Property,
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Subject type"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Specifies the required type of the subjects of a property"
                )
            )
        )
    )

    private val ObjectType: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2Simple.ObjectType,
        propertyType = OntologyConstants.Rdf.Property,
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Object type"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Specifies the required type of the objects of a property"
                )
            )
        )
    )

    private val Date: ReadClassInfoV2 = makeDatatype(
        datatypeIri = OntologyConstants.KnoraApiV2Simple.Date,
        xsdStringRestrictionPattern = Some("(GREGORIAN|JULIAN):\\d{1,4}(-\\d{1,2}(-\\d{1,2})?)?( BC| AD| BCE| CE)?(:\\d{1,4}(-\\d{1,2}(-\\d{1,2})?)?( BC| AD| BCE| CE)?)?"),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Date literal"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Represents a date as a period with different possible precisions."
                )
            )
        )
    )

    private val Color: ReadClassInfoV2 = makeDatatype(
        datatypeIri = OntologyConstants.KnoraApiV2Simple.Color,
        xsdStringRestrictionPattern = Some("#([0-9a-fA-F]{3}){1,2}"),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Color literal"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Represents a color."
                )
            )
        )
    )

    private val Interval: ReadClassInfoV2 = makeDatatype(
        datatypeIri = OntologyConstants.KnoraApiV2Simple.Interval,
        xsdStringRestrictionPattern = Some("\\d+(\\.\\d+)?,\\d+(\\.\\d+)?"),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Interprivate val literal"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Represents an interval."
                )
            )
        )
    )

    private val Geoname: ReadClassInfoV2 = makeDatatype(
        datatypeIri = OntologyConstants.KnoraApiV2Simple.Geoname,
        xsdStringRestrictionPattern = Some("\\d{1,8}"),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Geoname code"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Represents a Geoname code."
                )
            )
        )
    )

    private val Geom: ReadClassInfoV2 = makeDatatype(
        datatypeIri = OntologyConstants.KnoraApiV2Simple.Geom,
        subClassOf = Some(OntologyConstants.Xsd.String),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Geometry specification"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Represents a geometry specification in JSON."
                )
            )
        )
    )

    private val File: ReadClassInfoV2 = makeDatatype(
        datatypeIri = OntologyConstants.KnoraApiV2Simple.File,
        subClassOf = Some(OntologyConstants.Xsd.Uri),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "File URI"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Represents a file URI."
                )
            )
        )
    )

    /**
      * Rules for transforming entities from `knora-base` into the corresponding entities from `knora-api`
      * in the [[ApiV2Simple]] schema.
      */
    object KnoraBaseTransformationRules {
        private val ResourceCardinalites = Map(
            OntologyConstants.KnoraApiV2Simple.HasIncomingLink -> Cardinality.MayHaveMany
        )

        /**
          * Properties to remove from `knora-base` before converting it to the [[ApiV2Simple]] schema.
          */
        val KnoraBasePropertiesToRemove: Set[SmartIri] = Set(
            OntologyConstants.KnoraBase.IsEditable,
            OntologyConstants.KnoraBase.CanBeInstantiated,
            OntologyConstants.KnoraBase.HasPermissions,
            OntologyConstants.KnoraBase.AttachedToUser,
            OntologyConstants.KnoraBase.AttachedToProject,
            OntologyConstants.KnoraBase.IsDeleted,
            OntologyConstants.KnoraBase.DeleteDate,
            OntologyConstants.KnoraBase.DeleteComment,
            OntologyConstants.KnoraBase.ObjectCannotBeMarkedAsDeleted,
            OntologyConstants.KnoraBase.ObjectDatatypeConstraint,
            OntologyConstants.KnoraBase.ObjectClassConstraint,
            OntologyConstants.KnoraBase.SubjectClassConstraint,
            OntologyConstants.KnoraBase.StandoffParentClassConstraint,
            OntologyConstants.KnoraBase.ValueHasStandoff,
            OntologyConstants.KnoraBase.ValueHasMapping,
            OntologyConstants.KnoraBase.HasMappingElement,
            OntologyConstants.KnoraBase.MappingHasStandoffClass,
            OntologyConstants.KnoraBase.MappingHasStandoffProperty,
            OntologyConstants.KnoraBase.MappingHasXMLClass,
            OntologyConstants.KnoraBase.MappingHasXMLNamespace,
            OntologyConstants.KnoraBase.MappingHasXMLTagname,
            OntologyConstants.KnoraBase.MappingHasXMLAttribute,
            OntologyConstants.KnoraBase.MappingHasXMLAttributename,
            OntologyConstants.KnoraBase.MappingHasDefaultXSLTransformation,
            OntologyConstants.KnoraBase.MappingHasStandoffDataTypeClass,
            OntologyConstants.KnoraBase.MappingElementRequiresSeparator,
            OntologyConstants.KnoraBase.IsRootNode,
            OntologyConstants.KnoraBase.HasRootNode,
            OntologyConstants.KnoraBase.HasSubListNode,
            OntologyConstants.KnoraBase.ListNodeName,
            OntologyConstants.KnoraBase.ListNodePosition,
            OntologyConstants.KnoraBase.ValueCreationDate,
            OntologyConstants.KnoraBase.ValueHas,
            OntologyConstants.KnoraBase.ValueHasComment,
            OntologyConstants.KnoraBase.ValueHasMapping,
            OntologyConstants.KnoraBase.ValueHasCalendar,
            OntologyConstants.KnoraBase.ValueHasColor,
            OntologyConstants.KnoraBase.ValueHasStartJDN,
            OntologyConstants.KnoraBase.ValueHasEndJDN,
            OntologyConstants.KnoraBase.ValueHasStartPrecision,
            OntologyConstants.KnoraBase.ValueHasEndPrecision,
            OntologyConstants.KnoraBase.ValueHasDecimal,
            OntologyConstants.KnoraBase.ValueHasGeometry,
            OntologyConstants.KnoraBase.ValueHasGeonameCode,
            OntologyConstants.KnoraBase.ValueHasInteger,
            OntologyConstants.KnoraBase.ValueHasBoolean,
            OntologyConstants.KnoraBase.ValueHasUri,
            OntologyConstants.KnoraBase.ValueHasIntervalStart,
            OntologyConstants.KnoraBase.ValueHasIntervalEnd,
            OntologyConstants.KnoraBase.ValueHasListNode,
            OntologyConstants.KnoraBase.Duration,
            OntologyConstants.KnoraBase.DimX,
            OntologyConstants.KnoraBase.DimY,
            OntologyConstants.KnoraBase.Fps,
            OntologyConstants.KnoraBase.QualityLevel,
            OntologyConstants.KnoraBase.InternalFilename,
            OntologyConstants.KnoraBase.InternalMimeType,
            OntologyConstants.KnoraBase.OriginalFilename,
            OntologyConstants.KnoraBase.OriginalMimeType,
            OntologyConstants.KnoraBase.IsPreview,
            OntologyConstants.KnoraBase.ValueHasOrder,
            OntologyConstants.KnoraBase.PreviousValue,
            OntologyConstants.KnoraBase.ValueHasRefCount,
            OntologyConstants.KnoraBase.ValueHasString,
            OntologyConstants.KnoraBase.PreviousValue,
            OntologyConstants.KnoraBase.HasExtResValue,
            OntologyConstants.KnoraBase.ExtResAccessInfo,
            OntologyConstants.KnoraBase.ExtResId,
            OntologyConstants.KnoraBase.ExtResProvider,
            OntologyConstants.KnoraBase.MapEntryKey,
            OntologyConstants.KnoraBase.MapEntryValue,
            OntologyConstants.KnoraBase.IsInMap,
            OntologyConstants.KnoraBase.ForProject, // TODO: remove admin stuff from here when it's moved to a separate ontology.
            OntologyConstants.KnoraBase.ForGroup,
            OntologyConstants.KnoraBase.ForResourceClass,
            OntologyConstants.KnoraBase.ForProperty,
            OntologyConstants.KnoraBase.Address,
            OntologyConstants.KnoraBase.Email,
            OntologyConstants.KnoraBase.GivenName,
            OntologyConstants.KnoraBase.FamilyName,
            OntologyConstants.KnoraBase.Password,
            OntologyConstants.KnoraBase.UsersActiveProject,
            OntologyConstants.KnoraBase.Status,
            OntologyConstants.KnoraBase.PreferredLanguage,
            OntologyConstants.KnoraBase.IsInProject,
            OntologyConstants.KnoraBase.IsInProjectAdminGroup,
            OntologyConstants.KnoraBase.IsInGroup,
            OntologyConstants.KnoraBase.IsInSystemAdminGroup,
            OntologyConstants.KnoraBase.InstitutionDescription,
            OntologyConstants.KnoraBase.InstitutionName,
            OntologyConstants.KnoraBase.InstitutionWebsite,
            OntologyConstants.KnoraBase.Phone,
            OntologyConstants.KnoraBase.KnoraProject,
            OntologyConstants.KnoraBase.ProjectShortname,
            OntologyConstants.KnoraBase.ProjectShortcode,
            OntologyConstants.KnoraBase.ProjectLongname,
            OntologyConstants.KnoraBase.ProjectDescription,
            OntologyConstants.KnoraBase.ProjectKeyword,
            OntologyConstants.KnoraBase.ProjectLogo,
            OntologyConstants.KnoraBase.BelongsToInstitution,
            OntologyConstants.KnoraBase.HasSelfJoinEnabled,
            OntologyConstants.KnoraBase.GroupName,
            OntologyConstants.KnoraBase.GroupDescription,
            OntologyConstants.KnoraBase.BelongsToProject
        ).map(_.toSmartIri)

        /**
          * Classes to remove from `knora-base` before converting it to the [[ApiV2Simple]] schema.
          */
        val KnoraBaseClassesToRemove: Set[SmartIri] = Set(
            OntologyConstants.KnoraBase.ValueBase,
            OntologyConstants.KnoraBase.DateBase,
            OntologyConstants.KnoraBase.UriBase,
            OntologyConstants.KnoraBase.BooleanBase,
            OntologyConstants.KnoraBase.IntBase,
            OntologyConstants.KnoraBase.DecimalBase,
            OntologyConstants.KnoraBase.IntervalBase,
            OntologyConstants.KnoraBase.ColorBase,
            OntologyConstants.KnoraBase.Value,
            OntologyConstants.KnoraBase.TextValue,
            OntologyConstants.KnoraBase.IntValue,
            OntologyConstants.KnoraBase.BooleanValue,
            OntologyConstants.KnoraBase.UriValue,
            OntologyConstants.KnoraBase.DecimalValue,
            OntologyConstants.KnoraBase.DateValue,
            OntologyConstants.KnoraBase.ColorValue,
            OntologyConstants.KnoraBase.GeomValue,
            OntologyConstants.KnoraBase.ListValue,
            OntologyConstants.KnoraBase.IntervalValue,
            OntologyConstants.KnoraBase.LinkValue,
            OntologyConstants.KnoraBase.GeonameValue,
            OntologyConstants.KnoraBase.FileValue,
            OntologyConstants.KnoraBase.DefaultObjectAccessPermission,
            OntologyConstants.KnoraBase.MappingElement,
            OntologyConstants.KnoraBase.MappingComponent,
            OntologyConstants.KnoraBase.MappingStandoffDataTypeClass,
            OntologyConstants.KnoraBase.MappingXMLAttribute,
            OntologyConstants.KnoraBase.XMLToStandoffMapping,
            OntologyConstants.KnoraBase.ExternalResource,
            OntologyConstants.KnoraBase.ExternalResValue,
            OntologyConstants.KnoraBase.Map,
            OntologyConstants.KnoraBase.MapEntry,
            OntologyConstants.KnoraBase.User, // TODO: remove admin stuff from here when it's moved to a separate ontology.
            OntologyConstants.KnoraBase.KnoraProject,
            OntologyConstants.KnoraBase.ListNode,
            OntologyConstants.KnoraBase.Permission,
            OntologyConstants.KnoraBase.UserGroup,
            OntologyConstants.KnoraBase.Institution,
            OntologyConstants.KnoraBase.AdministrativePermission
        ).map(_.toSmartIri)

        /**
          * After `knora-base` has been converted to the [[ApiV2Simple]] schema, these cardinalities must be
          * added to the specified classes to obtain `knora-api`.
          */
        val KnoraApiCardinalitiesToAdd: Map[SmartIri, Map[SmartIri, KnoraCardinalityInfo]] = Map(
            OntologyConstants.KnoraBase.Resource -> ResourceCardinalites
        ).map {
            case (classIri, cardinalities) =>
                classIri.toSmartIri.toOntologySchema(ApiV2Simple) -> cardinalities.map {
                    case (propertyIri, cardinality) =>
                        propertyIri.toSmartIri.toOntologySchema(ApiV2Simple) -> Cardinality.KnoraCardinalityInfo(cardinality)
                }
        }

        /**
          * Classes that need to be added to `knora-base`, after converting it to the [[ApiV2Simple]] schema, to obtain `knora-api`.
          */
        val KnoraApiClassesToAdd: Map[SmartIri, ReadClassInfoV2] = Set(
            File,
            Date,
            Color,
            Interval,
            Geoname,
            Geom
        ).map {
            classInfo => classInfo.entityInfoContent.classIri -> classInfo
        }.toMap

        /**
          * Properties that need to be added to `knora-base`, after converting it to the [[ApiV2Simple]] schema, to obtain `knora-api`.
          */
        val KnoraApiPropertiesToAdd: Map[SmartIri, ReadPropertyInfoV2] = Set(
            Result,
            Error,
            HasValue,
            ResourceProperty,
            SubjectType,
            ObjectType
        ).map {
            propertyInfo => propertyInfo.entityInfoContent.propertyIri -> propertyInfo
        }.toMap
    }

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
      * @param subPropertyOf the set of direct superproperties of this property.
      * @param predicates    the property's predicates.
      * @param subjectType   the required type of the property's subject.
      * @param objectType    the required type of the property's object.
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
                    predicateIri = OntologyConstants.KnoraApiV2Simple.SubjectType,
                    objects = Seq(SmartIriLiteralV2(subjType.toSmartIri))
                )
        }

        val maybeObjectTypePred = objectType.map {
            objType =>
                makePredicate(
                    predicateIri = OntologyConstants.KnoraApiV2Simple.ObjectType,
                    objects = Seq(SmartIriLiteralV2(objType.toSmartIri))
                )
        }

        val predsWithTypes = predicates ++ maybeSubjectTypePred ++ maybeObjectTypePred :+ propTypePred

        ReadPropertyInfoV2(
            entityInfoContent = PropertyInfoContentV2(
                propertyIri = propertyIri.toSmartIri,
                ontologySchema = ApiV2Simple,
                predicates = predsWithTypes.map {
                    pred => pred.predicateIri -> pred
                }.toMap,
                subPropertyOf = subPropertyOf.map(_.toSmartIri)
            )
        )
    }

    /**
      * Makes a [[ReadClassInfoV2]] representing an owl:Class.
      *
      * @param classIri               the IRI of the class.
      * @param subClassOf             the set of direct superclasses of this class.
      * @param predicates             the predicates of the class.
      * @param directCardinalities    the direct cardinalities of the class.
      * @param inheritedCardinalities the inherited cardinalities of the class.
      * @return a [[ReadClassInfoV2]].
      */
    private def makeClass(classIri: IRI,
                          subClassOf: Set[IRI] = Set.empty[IRI],
                          predicates: Seq[PredicateInfoV2] = Seq.empty[PredicateInfoV2],
                          directCardinalities: Map[IRI, Cardinality.Value] = Map.empty[IRI, Cardinality.Value],
                          inheritedCardinalities: Map[SmartIri, KnoraCardinalityInfo] = Map.empty[SmartIri, KnoraCardinalityInfo]): ReadClassInfoV2 = {

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
                subClassOf = subClassOf.map(_.toSmartIri),
                ontologySchema = ApiV2Simple
            ),
            inheritedCardinalities = inheritedCardinalities
        )
    }

    /**
      * Makes a [[ReadClassInfoV2]] representing an rdfs:Datatype.
      *
      * @param datatypeIri                 the IRI of the datatype.
      * @param subClassOf                  the superclass of the datatype.
      * @param xsdStringRestrictionPattern an optional xsd:pattern specifying
      *                                    the regular expression that restricts its values. This has the effect of making the
      *                                    class a subclass of a blank node with owl:onDatatype xsd:string.
      * @param predicates                  the predicates of the datatype.
      * @return a [[ReadClassInfoV2]].
      */
    private def makeDatatype(datatypeIri: IRI,
                             subClassOf: Option[IRI] = None,
                             xsdStringRestrictionPattern: Option[String] = None,
                             predicates: Seq[PredicateInfoV2] = Seq.empty[PredicateInfoV2]): ReadClassInfoV2 = {

        val rdfType = OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Rdfs.Datatype.toSmartIri))
        )

        ReadClassInfoV2(
            entityInfoContent = ClassInfoContentV2(
                classIri = datatypeIri.toSmartIri,
                xsdStringRestrictionPattern = xsdStringRestrictionPattern,
                predicates = predicates.map {
                    pred => pred.predicateIri -> pred
                }.toMap + rdfType,
                subClassOf = subClassOf.toSet.map {
                    iri: IRI => iri.toSmartIri
                },
                ontologySchema = ApiV2Simple
            )
        )
    }
}
