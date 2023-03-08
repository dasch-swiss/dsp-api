/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.v2.responder.ontologymessages

import org.knora.webapi._
import org.knora.webapi.messages.IriConversions._
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.store.triplestoremessages.OntologyLiteralV2
import org.knora.webapi.messages.store.triplestoremessages.SmartIriLiteralV2
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.messages.v2.responder.ontologymessages.OwlCardinality._
import org.knora.webapi.slice.ontology.domain.model.Cardinality._

/**
 * Rules for converting `knora-base` (or an ontology based on it) into `knora-api` in the [[ApiV2Simple]] schema.
 * See also [[OntologyConstants.CorrespondingIris]].
 */
object KnoraBaseToApiV2SimpleTransformationRules extends OntologyTransformationRules {

  private implicit val stringFormatter: StringFormatter = StringFormatter.getInstanceForConstantOntologies

  override val ontologyMetadata: OntologyMetadataV2 = OntologyMetadataV2(
    ontologyIri = OntologyConstants.KnoraApiV2Simple.KnoraApiOntologyIri.toSmartIri,
    projectIri = Some(OntologyConstants.KnoraAdmin.SystemProject.toSmartIri),
    label = Some("The knora-api ontology in the simple schema")
  )

  private val Label: ReadPropertyInfoV2 = makeProperty(
    propertyIri = OntologyConstants.Rdfs.Label,
    propertyType = OntologyConstants.Owl.DatatypeProperty
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

  private val MayHaveMoreResults: ReadPropertyInfoV2 = makeProperty(
    propertyIri = OntologyConstants.KnoraApiV2Simple.MayHaveMoreResults,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          LanguageCodes.EN -> "May have more results"
        )
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          LanguageCodes.EN -> "Indicates whether more results may be available for a search query"
        )
      )
    ),
    objectType = Some(OntologyConstants.Xsd.Boolean)
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

  private val ArkUrl: ReadPropertyInfoV2 = makeProperty(
    propertyIri = OntologyConstants.KnoraApiV2Simple.ArkUrl,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          LanguageCodes.EN -> "ARK URL"
        )
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          LanguageCodes.EN -> "Provides the ARK URL of a resource."
        )
      )
    ),
    objectType = Some(OntologyConstants.Xsd.Uri)
  )

  private val VersionArkUrl: ReadPropertyInfoV2 = makeProperty(
    propertyIri = OntologyConstants.KnoraApiV2Simple.VersionArkUrl,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          LanguageCodes.EN -> "version ARK URL"
        )
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          LanguageCodes.EN -> "Provides the ARK URL of a particular version of a resource."
        )
      )
    ),
    objectType = Some(OntologyConstants.Xsd.Uri)
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
    datatypeInfo = DatatypeInfoV2(
      onDatatype = OntologyConstants.Xsd.String.toSmartIri,
      pattern = Some(
        "(GREGORIAN|JULIAN|ISLAMIC):\\d{1,4}(-\\d{1,2}(-\\d{1,2})?)?( BC| AD| BCE| CE)?(:\\d{1,4}(-\\d{1,2}(-\\d{1,2})?)?( BC| AD| BCE| CE)?)?"
      )
    ),
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
    datatypeInfo = DatatypeInfoV2(
      onDatatype = OntologyConstants.Xsd.String.toSmartIri,
      pattern = Some("#([0-9a-fA-F]{3}){1,2}")
    ),
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
    datatypeInfo = DatatypeInfoV2(
      onDatatype = OntologyConstants.Xsd.String.toSmartIri,
      pattern = Some("\\d+(\\.\\d+)?,\\d+(\\.\\d+)?")
    ),
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          LanguageCodes.EN -> "Interval literal"
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
    datatypeInfo = DatatypeInfoV2(
      onDatatype = OntologyConstants.Xsd.String.toSmartIri,
      pattern = Some("\\d{1,8}")
    ),
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
    datatypeInfo = DatatypeInfoV2(
      onDatatype = OntologyConstants.Xsd.String.toSmartIri
    ),
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
    datatypeInfo = DatatypeInfoV2(
      onDatatype = OntologyConstants.Xsd.Uri.toSmartIri
    ),
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

  private val ListNode: ReadClassInfoV2 = makeDatatype(
    datatypeIri = OntologyConstants.KnoraApiV2Simple.ListNode,
    datatypeInfo = DatatypeInfoV2(
      onDatatype = OntologyConstants.Xsd.String.toSmartIri
    ),
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          LanguageCodes.EN -> "List Node"
        )
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          LanguageCodes.EN -> "Represents a list node."
        )
      )
    )
  )

  private val HasIncomingLink: ReadPropertyInfoV2 = makeProperty(
    propertyIri = OntologyConstants.KnoraApiV2Simple.HasIncomingLink,
    propertyType = OntologyConstants.Owl.ObjectProperty,
    subjectType = Some(OntologyConstants.KnoraApiV2Simple.Resource),
    objectType = Some(OntologyConstants.KnoraApiV2Simple.Resource),
    subPropertyOf = Set(OntologyConstants.KnoraApiV2Simple.HasLinkTo),
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          LanguageCodes.DE -> "hat eingehenden Verweis",
          LanguageCodes.EN -> "has incoming link"
        )
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          LanguageCodes.EN -> "Indicates that this resource referred to by another resource"
        )
      )
    )
  )

  private val ResourceCardinalites = Map(
    OntologyConstants.KnoraApiV2Simple.HasIncomingLink -> Unbounded,
    OntologyConstants.KnoraApiV2Simple.ArkUrl          -> ExactlyOne,
    OntologyConstants.KnoraApiV2Simple.VersionArkUrl   -> ExactlyOne
  )

  /**
   * Properties to remove from `knora-base` before converting it to the [[ApiV2Simple]] schema.
   * See also [[OntologyConstants.CorrespondingIris]].
   */
  override val internalPropertiesToRemove: Set[SmartIri] = Set(
    OntologyConstants.KnoraBase.OntologyVersion,
    OntologyConstants.KnoraBase.CreationDate,
    OntologyConstants.KnoraBase.LastModificationDate,
    OntologyConstants.KnoraBase.IsEditable,
    OntologyConstants.KnoraBase.CanBeInstantiated,
    OntologyConstants.KnoraBase.HasPermissions,
    OntologyConstants.KnoraBase.AttachedToUser,
    OntologyConstants.KnoraBase.AttachedToProject,
    OntologyConstants.KnoraBase.IsDeleted,
    OntologyConstants.KnoraBase.DeleteDate,
    OntologyConstants.KnoraBase.DeletedBy,
    OntologyConstants.KnoraBase.DeleteComment,
    OntologyConstants.KnoraBase.ObjectCannotBeMarkedAsDeleted,
    OntologyConstants.KnoraBase.ObjectDatatypeConstraint,
    OntologyConstants.KnoraBase.ObjectClassConstraint,
    OntologyConstants.KnoraBase.SubjectClassConstraint,
    OntologyConstants.KnoraBase.StandoffParentClassConstraint,
    OntologyConstants.KnoraBase.ValueHasLanguage,
    OntologyConstants.KnoraBase.ValueHasStandoff,
    OntologyConstants.KnoraBase.ValueHasMapping,
    OntologyConstants.KnoraBase.ValueHasMaxStandoffStartIndex,
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
    OntologyConstants.KnoraBase.StandoffTagHasLink,
    OntologyConstants.KnoraBase.StandoffTagHasInternalReference,
    OntologyConstants.KnoraBase.StandoffTagHasStart,
    OntologyConstants.KnoraBase.StandoffTagHasEnd,
    OntologyConstants.KnoraBase.StandoffTagHasStartIndex,
    OntologyConstants.KnoraBase.StandoffTagHasEndIndex,
    OntologyConstants.KnoraBase.StandoffTagHasStartParent,
    OntologyConstants.KnoraBase.StandoffTagHasStartAncestor,
    OntologyConstants.KnoraBase.StandoffTagHasEndParent,
    OntologyConstants.KnoraBase.StandoffTagHasUUID,
    OntologyConstants.KnoraBase.StandoffTagHasOriginalXMLID,
    OntologyConstants.KnoraBase.TargetHasOriginalXMLID,
    OntologyConstants.KnoraBase.IsRootNode,
    OntologyConstants.KnoraBase.HasRootNode,
    OntologyConstants.KnoraBase.HasSubListNode,
    OntologyConstants.KnoraBase.ListNodeName,
    OntologyConstants.KnoraBase.ListNodePosition,
    OntologyConstants.KnoraBase.ValueCreationDate,
    OntologyConstants.KnoraBase.ValueHasUUID,
    OntologyConstants.KnoraBase.ValueHas,
    OntologyConstants.KnoraBase.ValueHasComment,
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
    OntologyConstants.KnoraBase.ValueHasTimeStamp,
    OntologyConstants.KnoraBase.ValueHasListNode,
    OntologyConstants.KnoraBase.Duration,
    OntologyConstants.KnoraBase.DimX,
    OntologyConstants.KnoraBase.DimY,
    OntologyConstants.KnoraBase.Fps,
    OntologyConstants.KnoraBase.InternalFilename,
    OntologyConstants.KnoraBase.InternalMimeType,
    OntologyConstants.KnoraBase.OriginalFilename,
    OntologyConstants.KnoraBase.OriginalMimeType,
    OntologyConstants.KnoraBase.ValueHasOrder,
    OntologyConstants.KnoraBase.PreviousValue,
    OntologyConstants.KnoraBase.ValueHasRefCount,
    OntologyConstants.KnoraBase.ValueHasString,
    OntologyConstants.KnoraBase.PreviousValue,
    OntologyConstants.KnoraBase.HasExtResValue,
    OntologyConstants.KnoraBase.ExtResAccessInfo,
    OntologyConstants.KnoraBase.ExtResId,
    OntologyConstants.KnoraBase.ExtResProvider,
    OntologyConstants.KnoraBase.PageCount
  ).map(_.toSmartIri)

  /**
   * Classes to remove from `knora-base` before converting it to the [[ApiV2Simple]] schema. Standoff classes
   * are removed, too, but aren't included here, because this is taken care of in [[ReadOntologyV2]].
   */
  override val internalClassesToRemove: Set[SmartIri] = Set(
    OntologyConstants.KnoraBase.ValueBase,
    OntologyConstants.KnoraBase.DateBase,
    OntologyConstants.KnoraBase.UriBase,
    OntologyConstants.KnoraBase.BooleanBase,
    OntologyConstants.KnoraBase.IntBase,
    OntologyConstants.KnoraBase.DecimalBase,
    OntologyConstants.KnoraBase.IntervalBase,
    OntologyConstants.KnoraBase.TimeBase,
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
    OntologyConstants.KnoraBase.TimeValue,
    OntologyConstants.KnoraBase.LinkValue,
    OntologyConstants.KnoraBase.GeonameValue,
    OntologyConstants.KnoraBase.FileValue,
    OntologyConstants.KnoraBase.MappingElement,
    OntologyConstants.KnoraBase.MappingComponent,
    OntologyConstants.KnoraBase.MappingStandoffDataTypeClass,
    OntologyConstants.KnoraBase.MappingXMLAttribute,
    OntologyConstants.KnoraBase.XMLToStandoffMapping,
    OntologyConstants.KnoraBase.ExternalResource,
    OntologyConstants.KnoraBase.ExternalResValue,
    OntologyConstants.KnoraBase.ListNode
  ).map(_.toSmartIri)

  /**
   * After `knora-base` has been converted to the [[ApiV2Simple]] schema, these cardinalities must be
   * added to the specified classes to obtain `knora-api`.
   */
  override val externalCardinalitiesToAdd: Map[SmartIri, Map[SmartIri, KnoraCardinalityInfo]] = Map(
    OntologyConstants.KnoraBase.Resource -> ResourceCardinalites
  ).map { case (classIri, cardinalities) =>
    classIri.toSmartIri.toOntologySchema(ApiV2Simple) -> cardinalities.map { case (propertyIri, cardinality) =>
      propertyIri.toSmartIri.toOntologySchema(ApiV2Simple) -> KnoraCardinalityInfo(cardinality)
    }
  }

  /**
   * Classes that need to be added to `knora-base`, after converting it to the [[ApiV2Simple]] schema, to obtain `knora-api`.
   */
  override val externalClassesToAdd: Map[SmartIri, ReadClassInfoV2] = Set(
    File,
    Date,
    Color,
    Interval,
    Geoname,
    Geom,
    ListNode
  ).map { classInfo =>
    classInfo.entityInfoContent.classIri -> classInfo
  }.toMap

  /**
   * Properties that need to be added to `knora-base`, after converting it to the [[ApiV2Simple]] schema, to obtain `knora-api`.
   * See also [[OntologyConstants.CorrespondingIris]].
   */
  override val externalPropertiesToAdd: Map[SmartIri, ReadPropertyInfoV2] = Set(
    Label,
    Result,
    MayHaveMoreResults,
    Error,
    ArkUrl,
    VersionArkUrl,
    HasValue,
    ResourceProperty,
    SubjectType,
    ObjectType,
    HasIncomingLink
  ).map { propertyInfo =>
    propertyInfo.entityInfoContent.propertyIri -> propertyInfo
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
  private def makePredicate(
    predicateIri: IRI,
    objects: Seq[OntologyLiteralV2] = Seq.empty[OntologyLiteralV2],
    objectsWithLang: Map[String, String] = Map.empty[String, String]
  ): PredicateInfoV2 =
    PredicateInfoV2(
      predicateIri = predicateIri.toSmartIri,
      objects = objects ++ objectsWithLang.map { case (lang, str) =>
        StringLiteralV2(str, Some(lang))
      }
    )

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
  private def makeProperty(
    propertyIri: IRI,
    propertyType: IRI,
    subPropertyOf: Set[IRI] = Set.empty[IRI],
    predicates: Seq[PredicateInfoV2] = Seq.empty[PredicateInfoV2],
    subjectType: Option[IRI] = None,
    objectType: Option[IRI] = None
  ): ReadPropertyInfoV2 = {
    val propTypePred = makePredicate(
      predicateIri = OntologyConstants.Rdf.Type,
      objects = Seq(SmartIriLiteralV2(propertyType.toSmartIri))
    )

    val maybeSubjectTypePred = subjectType.map { subjType =>
      makePredicate(
        predicateIri = OntologyConstants.KnoraApiV2Simple.SubjectType,
        objects = Seq(SmartIriLiteralV2(subjType.toSmartIri))
      )
    }

    val maybeObjectTypePred = objectType.map { objType =>
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
        predicates = predsWithTypes.map { pred =>
          pred.predicateIri -> pred
        }.toMap,
        subPropertyOf = subPropertyOf.map(_.toSmartIri)
      )
    )
  }

  /**
   * Makes a [[ReadClassInfoV2]] representing an rdfs:Datatype.
   *
   * @param datatypeIri  the IRI of the datatype.
   * @param datatypeInfo a [[DatatypeInfoV2]] describing the datatype.
   * @param predicates   the predicates of the datatype.
   * @return a [[ReadClassInfoV2]].
   */
  private def makeDatatype(
    datatypeIri: IRI,
    datatypeInfo: DatatypeInfoV2,
    predicates: Seq[PredicateInfoV2] = Seq.empty[PredicateInfoV2]
  ): ReadClassInfoV2 = {

    val rdfType = OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
      predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
      objects = Seq(SmartIriLiteralV2(OntologyConstants.Rdfs.Datatype.toSmartIri))
    )

    ReadClassInfoV2(
      entityInfoContent = ClassInfoContentV2(
        classIri = datatypeIri.toSmartIri,
        datatypeInfo = Some(datatypeInfo),
        predicates = predicates.map { pred =>
          pred.predicateIri -> pred
        }.toMap + rdfType,
        ontologySchema = ApiV2Simple
      ),
      allBaseClasses = Seq(datatypeIri.toSmartIri)
    )
  }
}
