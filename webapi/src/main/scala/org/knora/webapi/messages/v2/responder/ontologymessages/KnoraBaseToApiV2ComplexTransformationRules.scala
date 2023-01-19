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
 * Rules for converting `knora-base` (or an ontology based on it) into `knora-api` in the [[ApiV2Complex]] schema.
 * See also [[OntologyConstants.CorrespondingIris]].
 */
object KnoraBaseToApiV2ComplexTransformationRules extends OntologyTransformationRules {
  private implicit val stringFormatter: StringFormatter = StringFormatter.getInstanceForConstantOntologies

  override val ontologyMetadata: OntologyMetadataV2 = OntologyMetadataV2(
    ontologyIri = OntologyConstants.KnoraApiV2Complex.KnoraApiOntologyIri.toSmartIri,
    projectIri = Some(OntologyConstants.KnoraAdmin.SystemProject.toSmartIri),
    label = Some("The knora-api ontology in the complex schema")
  )

  private val Label: ReadPropertyInfoV2 = makeProperty(
    propertyIri = OntologyConstants.Rdfs.Label,
    propertyType = OntologyConstants.Owl.DatatypeProperty
  )

  private val Result: ReadPropertyInfoV2 = makeProperty(
    propertyIri = OntologyConstants.KnoraApiV2Complex.Result,
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
    propertyIri = OntologyConstants.KnoraApiV2Complex.Error,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          LanguageCodes.EN -> "error"
        )
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          LanguageCodes.EN -> "Provides an error message"
        )
      )
    ),
    objectType = Some(OntologyConstants.Xsd.String)
  )

  private val CanDo: ReadPropertyInfoV2 = makeProperty(
    propertyIri = OntologyConstants.KnoraApiV2Complex.CanDo,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          LanguageCodes.EN -> "can do"
        )
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          LanguageCodes.EN -> "Indicates whether an operation can be performed"
        )
      )
    ),
    objectType = Some(OntologyConstants.Xsd.Boolean)
  )

  private val MayHaveMoreResults: ReadPropertyInfoV2 = makeProperty(
    propertyIri = OntologyConstants.KnoraApiV2Complex.MayHaveMoreResults,
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

  private val UserHasPermission: ReadPropertyInfoV2 = makeProperty(
    propertyIri = OntologyConstants.KnoraApiV2Complex.UserHasPermission,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          LanguageCodes.EN -> "user has permission"
        )
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          LanguageCodes.EN -> "Provides the requesting user's maximum permission on a resource or value."
        )
      )
    ),
    objectType = Some(OntologyConstants.Xsd.String)
  )

  private val ArkUrl: ReadPropertyInfoV2 = makeProperty(
    propertyIri = OntologyConstants.KnoraApiV2Complex.ArkUrl,
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
          LanguageCodes.EN -> "Provides the ARK URL of a resource or value."
        )
      )
    ),
    objectType = Some(OntologyConstants.Xsd.Uri)
  )

  private val VersionArkUrl: ReadPropertyInfoV2 = makeProperty(
    propertyIri = OntologyConstants.KnoraApiV2Complex.VersionArkUrl,
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
          LanguageCodes.EN -> "Provides the ARK URL of a particular version of a resource or value."
        )
      )
    ),
    objectType = Some(OntologyConstants.Xsd.Uri)
  )

  private val VersionDate: ReadPropertyInfoV2 = makeProperty(
    propertyIri = OntologyConstants.KnoraApiV2Complex.VersionDate,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          LanguageCodes.EN -> "version date"
        )
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          LanguageCodes.EN -> "Provides the date of a particular version of a resource."
        )
      )
    ),
    objectType = Some(OntologyConstants.Xsd.Uri)
  )

  private val Author: ReadPropertyInfoV2 = makeProperty(
    propertyIri = OntologyConstants.KnoraApiV2Complex.Author,
    propertyType = OntologyConstants.Owl.ObjectProperty,
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          LanguageCodes.EN -> "author"
        )
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          LanguageCodes.EN -> "Specifies the author of a particular version of a resource."
        )
      )
    ),
    objectType = Some(OntologyConstants.KnoraApiV2Complex.User)
  )

  private val IsShared: ReadPropertyInfoV2 = makeProperty(
    propertyIri = OntologyConstants.KnoraApiV2Complex.IsShared,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          LanguageCodes.EN -> "is shared"
        )
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          LanguageCodes.EN -> "Indicates whether an ontology can be shared by multiple projects"
        )
      )
    ),
    objectType = Some(OntologyConstants.Xsd.Boolean)
  )

  private val IsBuiltIn: ReadPropertyInfoV2 = makeProperty(
    propertyIri = OntologyConstants.KnoraApiV2Complex.IsBuiltIn,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          LanguageCodes.EN -> "is shared"
        )
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          LanguageCodes.EN -> "Indicates whether an ontology is built into Knora"
        )
      )
    ),
    objectType = Some(OntologyConstants.Xsd.Boolean)
  )

  private val IsEditable: ReadPropertyInfoV2 = makeProperty(
    propertyIri = OntologyConstants.KnoraApiV2Complex.IsEditable,
    propertyType = OntologyConstants.Owl.AnnotationProperty,
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          LanguageCodes.EN -> "is editable"
        )
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          LanguageCodes.EN -> "Indicates whether a property's values can be updated via the Knora API."
        )
      )
    ),
    subjectType = Some(OntologyConstants.Rdf.Property),
    objectType = Some(OntologyConstants.Xsd.Boolean)
  )

  private val IsResourceClass: ReadPropertyInfoV2 = makeProperty(
    propertyIri = OntologyConstants.KnoraApiV2Complex.IsResourceClass,
    propertyType = OntologyConstants.Owl.AnnotationProperty,
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          LanguageCodes.EN -> "is resource class"
        )
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          LanguageCodes.EN -> "Indicates whether class is a subclass of Resource."
        )
      )
    ),
    subjectType = Some(OntologyConstants.Owl.Class),
    objectType = Some(OntologyConstants.Xsd.Boolean)
  )

  private val IsValueClass: ReadPropertyInfoV2 = makeProperty(
    propertyIri = OntologyConstants.KnoraApiV2Complex.IsValueClass,
    propertyType = OntologyConstants.Owl.AnnotationProperty,
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          LanguageCodes.EN -> "is value class"
        )
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          LanguageCodes.EN -> "Indicates whether class is a subclass of Value."
        )
      )
    ),
    subjectType = Some(OntologyConstants.Owl.Class),
    objectType = Some(OntologyConstants.Xsd.Boolean)
  )

  private val IsStandoffClass: ReadPropertyInfoV2 = makeProperty(
    propertyIri = OntologyConstants.KnoraApiV2Complex.IsStandoffClass,
    propertyType = OntologyConstants.Owl.AnnotationProperty,
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          LanguageCodes.EN -> "is standoff class"
        )
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          LanguageCodes.EN -> "Indicates whether class is a subclass of StandoffTag."
        )
      )
    ),
    subjectType = Some(OntologyConstants.Owl.Class),
    objectType = Some(OntologyConstants.Xsd.Boolean)
  )

  private val IsLinkProperty: ReadPropertyInfoV2 = makeProperty(
    propertyIri = OntologyConstants.KnoraApiV2Complex.IsLinkProperty,
    propertyType = OntologyConstants.Owl.AnnotationProperty,
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          LanguageCodes.EN -> "is link property"
        )
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          LanguageCodes.EN -> "Indicates whether a property points to a resource"
        )
      )
    ),
    subjectType = Some(OntologyConstants.Owl.ObjectProperty),
    objectType = Some(OntologyConstants.Xsd.Boolean)
  )

  private val IsLinkValueProperty: ReadPropertyInfoV2 = makeProperty(
    propertyIri = OntologyConstants.KnoraApiV2Complex.IsLinkValueProperty,
    propertyType = OntologyConstants.Owl.AnnotationProperty,
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          LanguageCodes.EN -> "is link value property"
        )
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          LanguageCodes.EN -> "Indicates whether a property points to a link value (reification)"
        )
      )
    ),
    subjectType = Some(OntologyConstants.Owl.ObjectProperty),
    objectType = Some(OntologyConstants.Xsd.Boolean)
  )

  private val IsInherited: ReadPropertyInfoV2 = makeProperty(
    propertyIri = OntologyConstants.KnoraApiV2Complex.IsInherited,
    propertyType = OntologyConstants.Owl.AnnotationProperty,
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          LanguageCodes.EN -> "is inherited"
        )
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          LanguageCodes.EN -> "Indicates whether a cardinality has been inherited from a base class"
        )
      )
    ),
    subjectType = Some(OntologyConstants.Owl.Restriction),
    objectType = Some(OntologyConstants.Xsd.Boolean)
  )

  private val NewModificationDate: ReadPropertyInfoV2 = makeProperty(
    propertyIri = OntologyConstants.KnoraApiV2Complex.NewModificationDate,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          LanguageCodes.EN -> "new modification date"
        )
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          LanguageCodes.EN -> "Specifies the new modification date of a resource"
        )
      )
    ),
    objectType = Some(OntologyConstants.Xsd.DateTimeStamp)
  )

  private val OntologyName: ReadPropertyInfoV2 = makeProperty(
    propertyIri = OntologyConstants.KnoraApiV2Complex.OntologyName,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          LanguageCodes.EN -> "ontology name"
        )
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          LanguageCodes.EN -> "Represents the short name of an ontology"
        )
      )
    ),
    objectType = Some(OntologyConstants.Xsd.String)
  )

  private val MappingHasName: ReadPropertyInfoV2 = makeProperty(
    propertyIri = OntologyConstants.KnoraApiV2Complex.MappingHasName,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          LanguageCodes.EN -> "Name of a mapping (will be part of the mapping's Iri)"
        )
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          LanguageCodes.EN -> "Represents the name of a mapping"
        )
      )
    ),
    objectType = Some(OntologyConstants.Xsd.String)
  )

  private val HasIncomingLinkValue: ReadPropertyInfoV2 = makeProperty(
    propertyIri = OntologyConstants.KnoraApiV2Complex.HasIncomingLinkValue,
    isResourceProp = true,
    propertyType = OntologyConstants.Owl.ObjectProperty,
    subjectType = Some(OntologyConstants.KnoraApiV2Complex.Resource),
    objectType = Some(OntologyConstants.KnoraApiV2Complex.LinkValue),
    subPropertyOf = Set(OntologyConstants.KnoraApiV2Complex.HasLinkToValue),
    isLinkValueProp = true,
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

  private val ValueAsString: ReadPropertyInfoV2 = makeProperty(
    propertyIri = OntologyConstants.KnoraApiV2Complex.ValueAsString,
    propertyType = OntologyConstants.Owl.ObjectProperty,
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(LanguageCodes.EN -> "A plain string representation of a value")
      )
    ),
    subjectType = Some(OntologyConstants.KnoraApiV2Complex.Value),
    objectType = Some(OntologyConstants.Xsd.String)
  )

  private val SubjectType: ReadPropertyInfoV2 = makeProperty(
    propertyIri = OntologyConstants.KnoraApiV2Complex.SubjectType,
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
    propertyIri = OntologyConstants.KnoraApiV2Complex.ObjectType,
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

  private val TextValueHasMarkup: ReadPropertyInfoV2 = makeProperty(
    propertyIri = OntologyConstants.KnoraApiV2Complex.TextValueHasMarkup,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    subPropertyOf = Set(OntologyConstants.KnoraApiV2Complex.ValueHas),
    subjectType = Some(OntologyConstants.KnoraApiV2Complex.TextValue),
    objectType = Some(OntologyConstants.Xsd.Boolean),
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          LanguageCodes.EN -> "text value has markup"
        )
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          LanguageCodes.EN -> "True if a text value has markup."
        )
      )
    )
  )

  private val TextValueHasStandoff: ReadPropertyInfoV2 = makeProperty(
    propertyIri = OntologyConstants.KnoraApiV2Complex.TextValueHasStandoff,
    propertyType = OntologyConstants.Owl.ObjectProperty,
    subPropertyOf = Set(OntologyConstants.KnoraApiV2Complex.ValueHas),
    subjectType = Some(OntologyConstants.KnoraApiV2Complex.TextValue),
    objectType = Some(OntologyConstants.KnoraApiV2Complex.StandoffTag),
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          LanguageCodes.EN -> "text value has standoff"
        )
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          LanguageCodes.EN -> "Standoff markup attached to a text value."
        )
      )
    )
  )

  private val TextValueHasMaxStandoffStartIndex: ReadPropertyInfoV2 = makeProperty(
    propertyIri = OntologyConstants.KnoraApiV2Complex.TextValueHasMaxStandoffStartIndex,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    subPropertyOf = Set(OntologyConstants.KnoraApiV2Complex.ValueHas),
    subjectType = Some(OntologyConstants.KnoraApiV2Complex.TextValue),
    objectType = Some(OntologyConstants.Xsd.Integer),
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          LanguageCodes.EN -> "text value has max standoff start index"
        )
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          LanguageCodes.EN -> "The maximum knora-api:standoffTagHasStartIndex in a text value."
        )
      )
    )
  )

  private val NextStandoffStartIndex: ReadPropertyInfoV2 = makeProperty(
    propertyIri = OntologyConstants.KnoraApiV2Complex.NextStandoffStartIndex,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    objectType = Some(OntologyConstants.Xsd.Integer),
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          LanguageCodes.EN -> "next standoff start index"
        )
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          LanguageCodes.EN -> "The next available knora-api:standoffTagHasStartIndex in a sequence of pages of standoff."
        )
      )
    )
  )

  private val StandoffTagHasStartParentIndex: ReadPropertyInfoV2 = makeProperty(
    propertyIri = OntologyConstants.KnoraApiV2Complex.StandoffTagHasStartParentIndex,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    subjectType = Some(OntologyConstants.KnoraApiV2Complex.StandoffTag),
    objectType = Some(OntologyConstants.Xsd.Integer),
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          LanguageCodes.EN -> "standoff tag has start parent index"
        )
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          LanguageCodes.EN -> "The next knora-api:standoffTagHasStartIndex of the start parent tag of a standoff tag."
        )
      )
    )
  )

  private val StandoffTagHasEndParentIndex: ReadPropertyInfoV2 = makeProperty(
    propertyIri = OntologyConstants.KnoraApiV2Complex.StandoffTagHasEndParentIndex,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    subjectType = Some(OntologyConstants.KnoraApiV2Complex.StandoffTag),
    objectType = Some(OntologyConstants.Xsd.Integer),
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          LanguageCodes.EN -> "standoff tag has end parent index"
        )
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          LanguageCodes.EN -> "The next knora-api:standoffTagHasStartIndex of the end parent tag of a standoff tag."
        )
      )
    )
  )

  private val TextValueHasLanguage: ReadPropertyInfoV2 = makeProperty(
    propertyIri = OntologyConstants.KnoraApiV2Complex.TextValueHasLanguage,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    subPropertyOf = Set(OntologyConstants.KnoraApiV2Complex.ValueHas),
    subjectType = Some(OntologyConstants.KnoraApiV2Complex.TextValue),
    objectType = Some(OntologyConstants.Xsd.String),
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          LanguageCodes.EN -> "text value has language"
        )
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          LanguageCodes.EN -> "Language code attached to a text value."
        )
      )
    )
  )

  private val TextValueAsXml: ReadPropertyInfoV2 = makeProperty(
    propertyIri = OntologyConstants.KnoraApiV2Complex.TextValueAsXml,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    subPropertyOf = Set(OntologyConstants.KnoraApiV2Complex.ValueHas),
    subjectType = Some(OntologyConstants.KnoraApiV2Complex.TextValue),
    objectType = Some(OntologyConstants.Xsd.String),
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          LanguageCodes.EN -> "Text value as XML"
        )
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          LanguageCodes.EN -> "A Text value represented in XML."
        )
      )
    )
  )

  private val TextValueAsHtml: ReadPropertyInfoV2 = makeProperty(
    propertyIri = OntologyConstants.KnoraApiV2Complex.TextValueAsHtml,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    subPropertyOf = Set(OntologyConstants.KnoraApiV2Complex.ValueHas),
    subjectType = Some(OntologyConstants.KnoraApiV2Complex.TextValue),
    objectType = Some(OntologyConstants.Xsd.String),
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          LanguageCodes.EN -> "Text value as HTML"
        )
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          LanguageCodes.EN -> "A text value represented in HTML."
        )
      )
    )
  )

  private val TextValueHasMapping: ReadPropertyInfoV2 = makeProperty(
    propertyIri = OntologyConstants.KnoraApiV2Complex.TextValueHasMapping,
    propertyType = OntologyConstants.Owl.ObjectProperty,
    subPropertyOf = Set(OntologyConstants.KnoraApiV2Complex.ValueHas),
    subjectType = Some(OntologyConstants.KnoraApiV2Complex.TextValue),
    objectType = Some(OntologyConstants.KnoraApiV2Complex.XMLToStandoffMapping),
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          LanguageCodes.EN -> "Text value has mapping"
        )
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          LanguageCodes.EN -> "Indicates the mapping that is used to convert a text value's markup from from XML to standoff."
        )
      )
    )
  )

  private val DateValueHasStartYear: ReadPropertyInfoV2 = makeProperty(
    propertyIri = OntologyConstants.KnoraApiV2Complex.DateValueHasStartYear,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    subPropertyOf = Set(OntologyConstants.KnoraApiV2Complex.ValueHas),
    subjectType = Some(OntologyConstants.KnoraApiV2Complex.DateBase),
    objectType = Some(OntologyConstants.Xsd.Integer),
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          LanguageCodes.EN -> "Date value has start year"
        )
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          LanguageCodes.EN -> "Represents the start year of a date value."
        )
      )
    )
  )

  private val DateValueHasEndYear: ReadPropertyInfoV2 = makeProperty(
    propertyIri = OntologyConstants.KnoraApiV2Complex.DateValueHasEndYear,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    subPropertyOf = Set(OntologyConstants.KnoraApiV2Complex.ValueHas),
    subjectType = Some(OntologyConstants.KnoraApiV2Complex.DateBase),
    objectType = Some(OntologyConstants.Xsd.Integer),
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          LanguageCodes.EN -> "Date value has end year"
        )
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          LanguageCodes.EN -> "Represents the end year of a date value."
        )
      )
    )
  )

  private val DateValueHasStartMonth: ReadPropertyInfoV2 = makeProperty(
    propertyIri = OntologyConstants.KnoraApiV2Complex.DateValueHasStartMonth,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    subPropertyOf = Set(OntologyConstants.KnoraApiV2Complex.ValueHas),
    subjectType = Some(OntologyConstants.KnoraApiV2Complex.DateBase),
    objectType = Some(OntologyConstants.Xsd.Integer),
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          LanguageCodes.EN -> "Date value has start month"
        )
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          LanguageCodes.EN -> "Represents the start month of a date value."
        )
      )
    )
  )

  private val DateValueHasEndMonth: ReadPropertyInfoV2 = makeProperty(
    propertyIri = OntologyConstants.KnoraApiV2Complex.DateValueHasEndMonth,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    subPropertyOf = Set(OntologyConstants.KnoraApiV2Complex.ValueHas),
    subjectType = Some(OntologyConstants.KnoraApiV2Complex.DateBase),
    objectType = Some(OntologyConstants.Xsd.Integer),
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          LanguageCodes.EN -> "Date value has end month"
        )
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          LanguageCodes.EN -> "Represents the end month of a date value."
        )
      )
    )
  )

  private val DateValueHasStartDay: ReadPropertyInfoV2 = makeProperty(
    propertyIri = OntologyConstants.KnoraApiV2Complex.DateValueHasStartDay,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    subPropertyOf = Set(OntologyConstants.KnoraApiV2Complex.ValueHas),
    subjectType = Some(OntologyConstants.KnoraApiV2Complex.DateBase),
    objectType = Some(OntologyConstants.Xsd.Integer),
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          LanguageCodes.EN -> "Date value has start day"
        )
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          LanguageCodes.EN -> "Represents the start day of a date value."
        )
      )
    )
  )

  private val DateValueHasEndDay: ReadPropertyInfoV2 = makeProperty(
    propertyIri = OntologyConstants.KnoraApiV2Complex.DateValueHasEndDay,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    subPropertyOf = Set(OntologyConstants.KnoraApiV2Complex.ValueHas),
    subjectType = Some(OntologyConstants.KnoraApiV2Complex.DateBase),
    objectType = Some(OntologyConstants.Xsd.Integer),
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          LanguageCodes.EN -> "Date value has end day"
        )
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          LanguageCodes.EN -> "Represents the end day of a date value."
        )
      )
    )
  )

  private val DateValueHasStartEra: ReadPropertyInfoV2 = makeProperty(
    propertyIri = OntologyConstants.KnoraApiV2Complex.DateValueHasStartEra,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    subPropertyOf = Set(OntologyConstants.KnoraApiV2Complex.ValueHas),
    subjectType = Some(OntologyConstants.KnoraApiV2Complex.DateBase),
    objectType = Some(OntologyConstants.Xsd.String),
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          LanguageCodes.EN -> "Date value has start era"
        )
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          LanguageCodes.EN -> "Represents the start era of a date value."
        )
      )
    )
  )

  private val DateValueHasEndEra: ReadPropertyInfoV2 = makeProperty(
    propertyIri = OntologyConstants.KnoraApiV2Complex.DateValueHasEndEra,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    subPropertyOf = Set(OntologyConstants.KnoraApiV2Complex.ValueHas),
    subjectType = Some(OntologyConstants.KnoraApiV2Complex.DateBase),
    objectType = Some(OntologyConstants.Xsd.String),
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          LanguageCodes.EN -> "Date value has end era"
        )
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          LanguageCodes.EN -> "Represents the end era of a date value."
        )
      )
    )
  )

  private val DateValueHasCalendar: ReadPropertyInfoV2 = makeProperty(
    propertyIri = OntologyConstants.KnoraApiV2Complex.DateValueHasCalendar,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    subPropertyOf = Set(OntologyConstants.KnoraApiV2Complex.ValueHas),
    subjectType = Some(OntologyConstants.KnoraApiV2Complex.DateBase),
    objectType = Some(OntologyConstants.Xsd.String),
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          LanguageCodes.EN -> "Date value has calendar"
        )
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          LanguageCodes.EN -> "Represents the calendar of a date value."
        )
      )
    )
  )

  private val LinkValueHasSource: ReadPropertyInfoV2 = makeProperty(
    propertyIri = OntologyConstants.KnoraApiV2Complex.LinkValueHasSource,
    propertyType = OntologyConstants.Owl.ObjectProperty,
    subPropertyOf = Set(OntologyConstants.KnoraApiV2Complex.ValueHas),
    subjectType = Some(OntologyConstants.KnoraApiV2Complex.LinkValue),
    objectType = Some(OntologyConstants.KnoraApiV2Complex.Resource),
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          LanguageCodes.EN -> "Link value has source"
        )
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          LanguageCodes.EN -> "Represents the source resource of a link value."
        )
      )
    )
  )

  private val LinkValueHasSourceIri: ReadPropertyInfoV2 = makeProperty(
    propertyIri = OntologyConstants.KnoraApiV2Complex.LinkValueHasSourceIri,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    subPropertyOf = Set(OntologyConstants.KnoraApiV2Complex.ValueHas),
    subjectType = Some(OntologyConstants.KnoraApiV2Complex.LinkValue),
    objectType = Some(OntologyConstants.Xsd.Uri),
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          LanguageCodes.EN -> "Link value has source IRI"
        )
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          LanguageCodes.EN -> "Represents the IRI of the source resource of a link value."
        )
      )
    )
  )

  private val LinkValueHasTarget: ReadPropertyInfoV2 = makeProperty(
    propertyIri = OntologyConstants.KnoraApiV2Complex.LinkValueHasTarget,
    propertyType = OntologyConstants.Owl.ObjectProperty,
    subPropertyOf = Set(OntologyConstants.KnoraApiV2Complex.ValueHas),
    subjectType = Some(OntologyConstants.KnoraApiV2Complex.LinkValue),
    objectType = Some(OntologyConstants.KnoraApiV2Complex.Resource),
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          LanguageCodes.EN -> "Link value has target"
        )
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          LanguageCodes.EN -> "Represents the target resource of a link value."
        )
      )
    )
  )

  private val LinkValueHasTargetIri: ReadPropertyInfoV2 = makeProperty(
    propertyIri = OntologyConstants.KnoraApiV2Complex.LinkValueHasTargetIri,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    subPropertyOf = Set(OntologyConstants.KnoraApiV2Complex.ValueHas),
    subjectType = Some(OntologyConstants.KnoraApiV2Complex.LinkValue),
    objectType = Some(OntologyConstants.Xsd.Uri),
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          LanguageCodes.EN -> "Link value has target IRI"
        )
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          LanguageCodes.EN -> "Represents the IRI of the target resource of a link value."
        )
      )
    )
  )

  private val IntValueAsInt: ReadPropertyInfoV2 = makeProperty(
    propertyIri = OntologyConstants.KnoraApiV2Complex.IntValueAsInt,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    subPropertyOf = Set(OntologyConstants.KnoraApiV2Complex.ValueHas),
    subjectType = Some(OntologyConstants.KnoraApiV2Complex.IntBase),
    objectType = Some(OntologyConstants.Xsd.Integer),
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          LanguageCodes.EN -> "Integer value as integer"
        )
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          LanguageCodes.EN -> "Represents the literal integer value of an IntValue."
        )
      )
    )
  )

  private val DecimalValueAsDecimal: ReadPropertyInfoV2 = makeProperty(
    propertyIri = OntologyConstants.KnoraApiV2Complex.DecimalValueAsDecimal,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    subPropertyOf = Set(OntologyConstants.KnoraApiV2Complex.ValueHas),
    subjectType = Some(OntologyConstants.KnoraApiV2Complex.DecimalBase),
    objectType = Some(OntologyConstants.Xsd.Decimal),
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          LanguageCodes.EN -> "Decimal value as decimal"
        )
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          LanguageCodes.EN -> "Represents the literal decimal value of a DecimalValue."
        )
      )
    )
  )

  private val IntervalValueHasStart: ReadPropertyInfoV2 = makeProperty(
    propertyIri = OntologyConstants.KnoraApiV2Complex.IntervalValueHasStart,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    subPropertyOf = Set(OntologyConstants.KnoraApiV2Complex.ValueHas),
    subjectType = Some(OntologyConstants.KnoraApiV2Complex.IntervalBase),
    objectType = Some(OntologyConstants.Xsd.Decimal),
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          LanguageCodes.EN -> "interval value has start"
        )
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          LanguageCodes.EN -> "Represents the start position of an interval."
        )
      )
    )
  )

  private val IntervalValueHasEnd: ReadPropertyInfoV2 = makeProperty(
    propertyIri = OntologyConstants.KnoraApiV2Complex.IntervalValueHasEnd,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    subPropertyOf = Set(OntologyConstants.KnoraApiV2Complex.ValueHas),
    subjectType = Some(OntologyConstants.KnoraApiV2Complex.IntervalBase),
    objectType = Some(OntologyConstants.Xsd.Decimal),
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          LanguageCodes.EN -> "interval value has end"
        )
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          LanguageCodes.EN -> "Represents the end position of an interval."
        )
      )
    )
  )

  private val BooleanValueAsBoolean: ReadPropertyInfoV2 = makeProperty(
    propertyIri = OntologyConstants.KnoraApiV2Complex.BooleanValueAsBoolean,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    subPropertyOf = Set(OntologyConstants.KnoraApiV2Complex.ValueHas),
    subjectType = Some(OntologyConstants.KnoraApiV2Complex.BooleanBase),
    objectType = Some(OntologyConstants.Xsd.Boolean),
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          LanguageCodes.EN -> "Boolean value as decimal"
        )
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          LanguageCodes.EN -> "Represents the literal boolean value of a BooleanValue."
        )
      )
    )
  )

  private val GeometryValueAsGeometry: ReadPropertyInfoV2 = makeProperty(
    propertyIri = OntologyConstants.KnoraApiV2Complex.GeometryValueAsGeometry,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    subPropertyOf = Set(OntologyConstants.KnoraApiV2Complex.ValueHas),
    subjectType = Some(OntologyConstants.KnoraApiV2Complex.GeomValue),
    objectType = Some(OntologyConstants.Xsd.String),
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          LanguageCodes.EN -> "Geometry value as JSON"
        )
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          LanguageCodes.EN -> "Represents a 2D geometry value as JSON."
        )
      )
    )
  )

  private val ListValueAsListNode: ReadPropertyInfoV2 = makeProperty(
    propertyIri = OntologyConstants.KnoraApiV2Complex.ListValueAsListNode,
    propertyType = OntologyConstants.Owl.ObjectProperty,
    subPropertyOf = Set(OntologyConstants.KnoraApiV2Complex.ValueHas),
    subjectType = Some(OntologyConstants.KnoraApiV2Complex.ListValue),
    objectType = Some(OntologyConstants.KnoraApiV2Complex.ListNode),
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          LanguageCodes.EN -> "Hierarchical list value as list node"
        )
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          LanguageCodes.EN -> "Represents a reference to a hierarchical list node."
        )
      )
    )
  )

  private val ColorValueAsColor: ReadPropertyInfoV2 = makeProperty(
    propertyIri = OntologyConstants.KnoraApiV2Complex.ColorValueAsColor,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    subPropertyOf = Set(OntologyConstants.KnoraApiV2Complex.ValueHas),
    subjectType = Some(OntologyConstants.KnoraApiV2Complex.ColorBase),
    objectType = Some(OntologyConstants.Xsd.String),
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          LanguageCodes.EN -> "Color value as color"
        )
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          LanguageCodes.EN -> "Represents the literal RGB value of a ColorValue."
        )
      )
    )
  )

  private val UriValueAsUri: ReadPropertyInfoV2 = makeProperty(
    propertyIri = OntologyConstants.KnoraApiV2Complex.UriValueAsUri,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    subPropertyOf = Set(OntologyConstants.KnoraApiV2Complex.ValueHas),
    subjectType = Some(OntologyConstants.KnoraApiV2Complex.UriBase),
    objectType = Some(OntologyConstants.Xsd.Uri),
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          LanguageCodes.EN -> "URI value as URI"
        )
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          LanguageCodes.EN -> "Represents the literal URI value of a UriValue."
        )
      )
    )
  )

  private val GeonameValueAsGeonameCode: ReadPropertyInfoV2 = makeProperty(
    propertyIri = OntologyConstants.KnoraApiV2Complex.GeonameValueAsGeonameCode,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    subPropertyOf = Set(OntologyConstants.KnoraApiV2Complex.ValueHas),
    subjectType = Some(OntologyConstants.KnoraApiV2Complex.GeonameValue),
    objectType = Some(OntologyConstants.Xsd.String),
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          LanguageCodes.EN -> "Geoname value as Geoname code"
        )
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          LanguageCodes.EN -> "Represents the literal Geoname code of a GeonameValue."
        )
      )
    )
  )

  private val FileValueAsUrl: ReadPropertyInfoV2 = makeProperty(
    propertyIri = OntologyConstants.KnoraApiV2Complex.FileValueAsUrl,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    subPropertyOf = Set(OntologyConstants.KnoraApiV2Complex.ValueHas),
    subjectType = Some(OntologyConstants.KnoraApiV2Complex.FileValue),
    objectType = Some(OntologyConstants.Xsd.Uri),
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          LanguageCodes.EN -> "File value as URL"
        )
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          LanguageCodes.EN -> "The URL at which the file can be accessed."
        )
      )
    )
  )

  private val FileValueHasFilename: ReadPropertyInfoV2 = makeProperty(
    propertyIri = OntologyConstants.KnoraApiV2Complex.FileValueHasFilename,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    subPropertyOf = Set(OntologyConstants.KnoraApiV2Complex.ValueHas),
    subjectType = Some(OntologyConstants.KnoraApiV2Complex.FileValue),
    objectType = Some(OntologyConstants.Xsd.String),
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          LanguageCodes.EN -> "File value has filename"
        )
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          LanguageCodes.EN -> "The name of the file that a file value represents."
        )
      )
    )
  )

  private val StillImageFileValueHasDimX: ReadPropertyInfoV2 = makeProperty(
    propertyIri = OntologyConstants.KnoraApiV2Complex.StillImageFileValueHasDimX,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    subPropertyOf = Set(OntologyConstants.KnoraApiV2Complex.ValueHas),
    subjectType = Some(OntologyConstants.KnoraApiV2Complex.StillImageFileValue),
    objectType = Some(OntologyConstants.Xsd.Integer),
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          LanguageCodes.EN -> "Still image file value has X dimension"
        )
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          LanguageCodes.EN -> "The horizontal dimension of a still image file value."
        )
      )
    )
  )

  private val StillImageFileValueHasDimY: ReadPropertyInfoV2 = makeProperty(
    propertyIri = OntologyConstants.KnoraApiV2Complex.StillImageFileValueHasDimY,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    subPropertyOf = Set(OntologyConstants.KnoraApiV2Complex.ValueHas),
    subjectType = Some(OntologyConstants.KnoraApiV2Complex.StillImageFileValue),
    objectType = Some(OntologyConstants.Xsd.Integer),
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          LanguageCodes.EN -> "Still image file value has Y dimension"
        )
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          LanguageCodes.EN -> "The vertical dimension of a still image file value."
        )
      )
    )
  )

  private val StillImageFileValueHasIIIFBaseUrl: ReadPropertyInfoV2 = makeProperty(
    propertyIri = OntologyConstants.KnoraApiV2Complex.StillImageFileValueHasIIIFBaseUrl,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    subPropertyOf = Set(OntologyConstants.KnoraApiV2Complex.ValueHas),
    subjectType = Some(OntologyConstants.KnoraApiV2Complex.StillImageFileValue),
    objectType = Some(OntologyConstants.Xsd.Uri),
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          LanguageCodes.EN -> "Still image file value has IIIF base URL"
        )
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          LanguageCodes.EN -> "The IIIF base URL of a still image file value."
        )
      )
    )
  )

  private val DocumentFileValueHasDimX: ReadPropertyInfoV2 = makeProperty(
    propertyIri = OntologyConstants.KnoraApiV2Complex.DocumentFileValueHasDimX,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    subPropertyOf = Set(OntologyConstants.KnoraApiV2Complex.ValueHas),
    subjectType = Some(OntologyConstants.KnoraApiV2Complex.DocumentFileValue),
    objectType = Some(OntologyConstants.Xsd.Integer),
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          LanguageCodes.EN -> "Document file value has X dimension"
        )
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          LanguageCodes.EN -> "The horizontal dimension of a document file value."
        )
      )
    )
  )

  private val DocumentFileValueHasDimY: ReadPropertyInfoV2 = makeProperty(
    propertyIri = OntologyConstants.KnoraApiV2Complex.DocumentFileValueHasDimY,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    subPropertyOf = Set(OntologyConstants.KnoraApiV2Complex.ValueHas),
    subjectType = Some(OntologyConstants.KnoraApiV2Complex.DocumentFileValue),
    objectType = Some(OntologyConstants.Xsd.Integer),
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          LanguageCodes.EN -> "Document file value has Y dimension"
        )
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          LanguageCodes.EN -> "The vertical dimension of a document file value."
        )
      )
    )
  )

  private val DocumentFileValueHasPageCount: ReadPropertyInfoV2 = makeProperty(
    propertyIri = OntologyConstants.KnoraApiV2Complex.DocumentFileValueHasPageCount,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    subPropertyOf = Set(OntologyConstants.KnoraApiV2Complex.ValueHas),
    subjectType = Some(OntologyConstants.KnoraApiV2Complex.DocumentFileValue),
    objectType = Some(OntologyConstants.Xsd.Integer),
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          LanguageCodes.EN -> "Document file value has page count"
        )
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          LanguageCodes.EN -> "The page count of a document file value."
        )
      )
    )
  )

  private val ResourceCardinalities = Map(
    OntologyConstants.KnoraApiV2Complex.HasIncomingLinkValue -> Unbounded,
    OntologyConstants.KnoraApiV2Complex.ArkUrl               -> ExactlyOne,
    OntologyConstants.KnoraApiV2Complex.VersionArkUrl        -> ExactlyOne,
    OntologyConstants.KnoraApiV2Complex.VersionDate          -> ZeroOrOne,
    OntologyConstants.KnoraApiV2Complex.UserHasPermission    -> ExactlyOne,
    OntologyConstants.KnoraApiV2Complex.IsDeleted            -> ZeroOrOne
  )

  private val DateBaseCardinalities = Map(
    OntologyConstants.KnoraApiV2Complex.DateValueHasStartYear  -> ExactlyOne,
    OntologyConstants.KnoraApiV2Complex.DateValueHasEndYear    -> ExactlyOne,
    OntologyConstants.KnoraApiV2Complex.DateValueHasStartMonth -> ZeroOrOne,
    OntologyConstants.KnoraApiV2Complex.DateValueHasEndMonth   -> ZeroOrOne,
    OntologyConstants.KnoraApiV2Complex.DateValueHasStartDay   -> ZeroOrOne,
    OntologyConstants.KnoraApiV2Complex.DateValueHasEndDay     -> ZeroOrOne,
    OntologyConstants.KnoraApiV2Complex.DateValueHasStartEra   -> ExactlyOne,
    OntologyConstants.KnoraApiV2Complex.DateValueHasEndEra     -> ExactlyOne,
    OntologyConstants.KnoraApiV2Complex.DateValueHasCalendar   -> ExactlyOne
  )

  private val UriBaseCardinalities = Map(
    OntologyConstants.KnoraApiV2Complex.UriValueAsUri -> ExactlyOne
  )

  private val BooleanBaseCardinalities = Map(
    OntologyConstants.KnoraApiV2Complex.BooleanValueAsBoolean -> ExactlyOne
  )

  private val IntBaseCardinalities = Map(
    OntologyConstants.KnoraApiV2Complex.IntValueAsInt -> ExactlyOne
  )

  private val DecimalBaseCardinalities = Map(
    OntologyConstants.KnoraApiV2Complex.DecimalValueAsDecimal -> ExactlyOne
  )

  private val IntervalBaseCardinalities = Map(
    OntologyConstants.KnoraApiV2Complex.IntervalValueHasStart -> ExactlyOne,
    OntologyConstants.KnoraApiV2Complex.IntervalValueHasEnd   -> ExactlyOne
  )

  private val ColorBaseCardinalities = Map(
    OntologyConstants.KnoraApiV2Complex.ColorValueAsColor -> ExactlyOne
  )

  private val ValueCardinalities = Map(
    OntologyConstants.KnoraApiV2Complex.ValueAsString     -> ZeroOrOne,
    OntologyConstants.KnoraApiV2Complex.HasPermissions    -> ExactlyOne,
    OntologyConstants.KnoraApiV2Complex.UserHasPermission -> ExactlyOne,
    OntologyConstants.KnoraApiV2Complex.ArkUrl            -> ExactlyOne,
    OntologyConstants.KnoraApiV2Complex.VersionArkUrl     -> ExactlyOne,
    OntologyConstants.KnoraApiV2Complex.IsDeleted         -> ZeroOrOne,
    OntologyConstants.KnoraApiV2Complex.ValueHasUUID      -> ExactlyOne
  )

  private val TextValueCardinalities = Map(
    OntologyConstants.KnoraApiV2Complex.TextValueHasStandoff              -> Unbounded,
    OntologyConstants.KnoraApiV2Complex.TextValueHasMarkup                -> ZeroOrOne,
    OntologyConstants.KnoraApiV2Complex.TextValueHasLanguage              -> ZeroOrOne,
    OntologyConstants.KnoraApiV2Complex.TextValueAsXml                    -> ZeroOrOne,
    OntologyConstants.KnoraApiV2Complex.TextValueAsHtml                   -> ZeroOrOne,
    OntologyConstants.KnoraApiV2Complex.TextValueHasMapping               -> ZeroOrOne,
    OntologyConstants.KnoraApiV2Complex.TextValueHasMaxStandoffStartIndex -> ZeroOrOne
  )

  private val StandoffTagCardinalities = Map(
    OntologyConstants.KnoraApiV2Complex.StandoffTagHasStartParentIndex -> ZeroOrOne,
    OntologyConstants.KnoraApiV2Complex.StandoffTagHasEndParentIndex   -> ZeroOrOne
  )

  private val LinkValueCardinalities = Map(
    OntologyConstants.KnoraApiV2Complex.LinkValueHasSource    -> ZeroOrOne,
    OntologyConstants.KnoraApiV2Complex.LinkValueHasTarget    -> ZeroOrOne,
    OntologyConstants.KnoraApiV2Complex.LinkValueHasSourceIri -> ZeroOrOne,
    OntologyConstants.KnoraApiV2Complex.LinkValueHasTargetIri -> ZeroOrOne
  )

  private val GeomValueCardinalities = Map(
    OntologyConstants.KnoraApiV2Complex.GeometryValueAsGeometry -> ExactlyOne
  )

  private val ListValueCardinalities = Map(
    OntologyConstants.KnoraApiV2Complex.ListValueAsListNode -> ExactlyOne
  )

  private val GeonameValueCardinalities = Map(
    OntologyConstants.KnoraApiV2Complex.GeonameValueAsGeonameCode -> ExactlyOne
  )

  private val FileValueCardinalities = Map(
    OntologyConstants.KnoraApiV2Complex.FileValueAsUrl       -> ExactlyOne,
    OntologyConstants.KnoraApiV2Complex.FileValueHasFilename -> ExactlyOne
  )

  private val StillImageFileValueCardinalities = Map(
    OntologyConstants.KnoraApiV2Complex.StillImageFileValueHasDimX        -> ExactlyOne,
    OntologyConstants.KnoraApiV2Complex.StillImageFileValueHasDimY        -> ExactlyOne,
    OntologyConstants.KnoraApiV2Complex.StillImageFileValueHasIIIFBaseUrl -> ExactlyOne
  )

  private val DocumentFileValueCardinalities = Map(
    OntologyConstants.KnoraApiV2Complex.DocumentFileValueHasPageCount -> ExactlyOne,
    OntologyConstants.KnoraApiV2Complex.DocumentFileValueHasDimX      -> ZeroOrOne,
    OntologyConstants.KnoraApiV2Complex.DocumentFileValueHasDimY      -> ZeroOrOne
  )

  /**
   * Properties to remove from `knora-base` before converting it to the [[ApiV2Complex]] schema.
   * See also [[OntologyConstants.CorrespondingIris]].
   */
  override val internalPropertiesToRemove: Set[SmartIri] = Set(
    OntologyConstants.Rdf.Subject,
    OntologyConstants.Rdf.Predicate,
    OntologyConstants.Rdf.Object,
    OntologyConstants.KnoraBase.OntologyVersion,
    OntologyConstants.KnoraBase.ObjectCannotBeMarkedAsDeleted,
    OntologyConstants.KnoraBase.ObjectDatatypeConstraint,
    OntologyConstants.KnoraBase.ObjectClassConstraint,
    OntologyConstants.KnoraBase.SubjectClassConstraint,
    OntologyConstants.KnoraBase.StandoffParentClassConstraint,
    OntologyConstants.KnoraBase.TargetHasOriginalXMLID,
    OntologyConstants.KnoraBase.ValueHasStandoff,
    OntologyConstants.KnoraBase.ValueHasLanguage,
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
    OntologyConstants.KnoraBase.ValueHasMapping,
    OntologyConstants.KnoraBase.ValueHasMaxStandoffStartIndex,
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
    OntologyConstants.KnoraBase.ExtResProvider
  ).map(_.toSmartIri)

  /**
   * Classes to remove from `knora-base` before converting it to the [[ApiV2Complex]] schema.
   */
  override val internalClassesToRemove: Set[SmartIri] = Set(
    OntologyConstants.KnoraBase.MappingElement,
    OntologyConstants.KnoraBase.MappingComponent,
    OntologyConstants.KnoraBase.MappingStandoffDataTypeClass,
    OntologyConstants.KnoraBase.MappingXMLAttribute,
    OntologyConstants.KnoraBase.XMLToStandoffMapping,
    OntologyConstants.KnoraBase.ExternalResource,
    OntologyConstants.KnoraBase.ExternalResValue
  ).map(_.toSmartIri)

  /**
   * After `knora-base` has been converted to the [[ApiV2Complex]] schema, these cardinalities must be
   * added to the specified classes to obtain `knora-api`.
   */
  override val externalCardinalitiesToAdd: Map[SmartIri, Map[SmartIri, KnoraCardinalityInfo]] = Map(
    OntologyConstants.KnoraApiV2Complex.Resource            -> ResourceCardinalities,
    OntologyConstants.KnoraApiV2Complex.DateBase            -> DateBaseCardinalities,
    OntologyConstants.KnoraApiV2Complex.UriBase             -> UriBaseCardinalities,
    OntologyConstants.KnoraApiV2Complex.BooleanBase         -> BooleanBaseCardinalities,
    OntologyConstants.KnoraApiV2Complex.IntBase             -> IntBaseCardinalities,
    OntologyConstants.KnoraApiV2Complex.DecimalBase         -> DecimalBaseCardinalities,
    OntologyConstants.KnoraApiV2Complex.IntervalBase        -> IntervalBaseCardinalities,
    OntologyConstants.KnoraApiV2Complex.ColorBase           -> ColorBaseCardinalities,
    OntologyConstants.KnoraApiV2Complex.Value               -> ValueCardinalities,
    OntologyConstants.KnoraApiV2Complex.TextValue           -> TextValueCardinalities,
    OntologyConstants.KnoraApiV2Complex.StandoffTag         -> StandoffTagCardinalities,
    OntologyConstants.KnoraApiV2Complex.LinkValue           -> LinkValueCardinalities,
    OntologyConstants.KnoraApiV2Complex.GeomValue           -> GeomValueCardinalities,
    OntologyConstants.KnoraApiV2Complex.ListValue           -> ListValueCardinalities,
    OntologyConstants.KnoraApiV2Complex.GeonameValue        -> GeonameValueCardinalities,
    OntologyConstants.KnoraApiV2Complex.FileValue           -> FileValueCardinalities,
    OntologyConstants.KnoraApiV2Complex.StillImageFileValue -> StillImageFileValueCardinalities,
    OntologyConstants.KnoraApiV2Complex.DocumentFileValue   -> DocumentFileValueCardinalities
  ).map { case (classIri, cardinalities) =>
    classIri.toSmartIri -> cardinalities.map { case (propertyIri, cardinality) =>
      propertyIri.toSmartIri -> KnoraCardinalityInfo(cardinality)
    }
  }

  /**
   * Classes that need to be added to `knora-base`, after converting it to the [[ApiV2Complex]] schema, to obtain `knora-api`.
   */
  override val externalClassesToAdd: Map[SmartIri, ReadClassInfoV2] = Map.empty[SmartIri, ReadClassInfoV2]

  /**
   * Properties that need to be added to `knora-base`, after converting it to the [[ApiV2Complex]] schema, to obtain `knora-api`.
   * See also [[OntologyConstants.CorrespondingIris]].
   */
  override val externalPropertiesToAdd: Map[SmartIri, ReadPropertyInfoV2] = Set(
    Label,
    Result,
    MayHaveMoreResults,
    Error,
    CanDo,
    UserHasPermission,
    VersionDate,
    ArkUrl,
    VersionArkUrl,
    Author,
    IsShared,
    IsBuiltIn,
    IsResourceClass,
    IsStandoffClass,
    IsValueClass,
    IsEditable,
    IsLinkProperty,
    IsLinkValueProperty,
    IsInherited,
    NewModificationDate,
    OntologyName,
    MappingHasName,
    ValueAsString,
    HasIncomingLinkValue,
    SubjectType,
    ObjectType,
    TextValueHasMarkup,
    TextValueHasStandoff,
    TextValueHasMaxStandoffStartIndex,
    NextStandoffStartIndex,
    StandoffTagHasStartParentIndex,
    StandoffTagHasEndParentIndex,
    TextValueHasLanguage,
    TextValueAsXml,
    TextValueAsHtml,
    TextValueHasMapping,
    DateValueHasStartYear,
    DateValueHasEndYear,
    DateValueHasStartMonth,
    DateValueHasEndMonth,
    DateValueHasStartDay,
    DateValueHasEndDay,
    DateValueHasStartEra,
    DateValueHasEndEra,
    DateValueHasCalendar,
    IntervalValueHasStart,
    IntervalValueHasEnd,
    LinkValueHasSource,
    LinkValueHasSourceIri,
    LinkValueHasTarget,
    LinkValueHasTargetIri,
    IntValueAsInt,
    DecimalValueAsDecimal,
    BooleanValueAsBoolean,
    GeometryValueAsGeometry,
    ListValueAsListNode,
    ColorValueAsColor,
    UriValueAsUri,
    GeonameValueAsGeonameCode,
    FileValueAsUrl,
    FileValueHasFilename,
    StillImageFileValueHasDimX,
    StillImageFileValueHasDimY,
    StillImageFileValueHasIIIFBaseUrl,
    DocumentFileValueHasDimX,
    DocumentFileValueHasDimY,
    DocumentFileValueHasPageCount
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
   * @param propertyIri     the IRI of the property.
   * @param propertyType    the type of the property (owl:ObjectProperty, owl:DatatypeProperty, or rdf:Property).
   * @param isResourceProp  true if this is a subproperty of `knora-api:hasValue` or `knora-api:hasLinkTo`.
   * @param subPropertyOf   the set of direct superproperties of this property.
   * @param isEditable      true if this is a Knora resource property that can be edited via the Knora API.
   * @param isLinkValueProp true if the property points to a link value (reification).
   * @param predicates      the property's predicates.
   * @param subjectType     the required type of the property's subject.
   * @param objectType      the required type of the property's object.
   * @return a [[ReadPropertyInfoV2]].
   */
  private def makeProperty(
    propertyIri: IRI,
    propertyType: IRI,
    isResourceProp: Boolean = false,
    subPropertyOf: Set[IRI] = Set.empty[IRI],
    isEditable: Boolean = false,
    isLinkProp: Boolean = false,
    isLinkValueProp: Boolean = false,
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
        predicateIri = OntologyConstants.KnoraApiV2Complex.SubjectType,
        objects = Seq(SmartIriLiteralV2(subjType.toSmartIri))
      )
    }

    val maybeObjectTypePred = objectType.map { objType =>
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
        predicates = predsWithTypes.map { pred =>
          pred.predicateIri -> pred
        }.toMap,
        subPropertyOf = subPropertyOf.map(iri => iri.toSmartIri)
      ),
      isResourceProp = isResourceProp,
      isEditable = isEditable,
      isLinkProp = isLinkProp,
      isLinkValueProp = isLinkValueProp
    )
  }
}
