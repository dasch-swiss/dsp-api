/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.v2.responder.ontologymessages

import org.eclipse.rdf4j.model.IRI as Rdf4jIRI
import org.eclipse.rdf4j.model.vocabulary.RDFS
import org.eclipse.rdf4j.model.vocabulary.RDF
import org.knora.webapi.LanguageCodes.*
import org.knora.webapi.*
import org.knora.webapi.messages.IriConversions.*
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.OntologyConstants.KnoraApiV2Complex as KA
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.store.triplestoremessages.OntologyLiteralV2
import org.knora.webapi.messages.store.triplestoremessages.SmartIriLiteralV2
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.messages.v2.responder.ontologymessages.OwlCardinality.*
import org.knora.webapi.slice.admin.domain.service.KnoraProjectRepo
import org.knora.webapi.slice.ontology.domain.model.Cardinality.*

/**
 * Rules for converting `knora-base` (or an ontology based on it) into `knora-api` in the [[ApiV2Complex]] schema.
 * See also [[OntologyConstants.CorrespondingIris]].
 */
object KnoraBaseToApiV2ComplexTransformationRules extends OntologyTransformationRules {
  private implicit val stringFormatter: StringFormatter = StringFormatter.getInstanceForConstantOntologies

  override val ontologyMetadata: OntologyMetadataV2 = OntologyMetadataV2(
    ontologyIri = KA.KnoraApiOntologyIri.toSmartIri,
    projectIri = Some(KnoraProjectRepo.builtIn.SystemProject.id.value.toSmartIri),
    label = Some("The knora-api ontology in the complex schema"),
  )

  private val Label: ReadPropertyInfoV2 = makeProperty(
    propertyIri = OntologyConstants.Rdfs.Label,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
  )

  private val Result: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.Result,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    predicates = Seq(
      PredicateInfoV2Builder.makeRdfsLabel(
        Map(
          DE -> "Ergebnis",
          EN -> "result",
          FR -> "résultat",
          IT -> "risultato",
        ),
      ),
      PredicateInfoV2Builder.makeRdfsComment(EN, "Provides a message indicating that an operation was successful"),
    ).map(_.build()),
    objectType = Some(OntologyConstants.Xsd.String),
  )

  private val Error: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.Error,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    predicates = Seq(
      PredicateInfoV2Builder.makeRdfsLabel(EN, "error"),
      PredicateInfoV2Builder.makeRdfsComment(EN, "Provides an error message"),
    ).map(_.build()),
    objectType = Some(OntologyConstants.Xsd.String),
  )

  private val CanDo: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.CanDo,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          EN -> "can do",
        ),
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          EN -> "Indicates whether an operation can be performed",
        ),
      ),
    ),
    objectType = Some(OntologyConstants.Xsd.Boolean),
  )

  private val MayHaveMoreResults: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.MayHaveMoreResults,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          EN -> "May have more results",
        ),
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          EN -> "Indicates whether more results may be available for a search query",
        ),
      ),
    ),
    objectType = Some(OntologyConstants.Xsd.Boolean),
  )

  private val UserHasPermission: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.UserHasPermission,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          EN -> "user has permission",
        ),
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          EN -> "Provides the requesting user's maximum permission on a resource or value.",
        ),
      ),
    ),
    objectType = Some(OntologyConstants.Xsd.String),
  )

  private val ArkUrl: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.ArkUrl,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    predicates = Seq(
      PredicateInfoV2Builder.makeRdfsLabel(EN, "ARK URL"),
      PredicateInfoV2Builder.makeRdfsComment(EN, "Provides the ARK URL of a resource or value."),
    ).map(_.build()),
    objectType = Some(OntologyConstants.Xsd.Uri),
  )

  private val VersionArkUrl: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.VersionArkUrl,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          EN -> "version ARK URL",
        ),
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          EN -> "Provides the ARK URL of a particular version of a resource or value.",
        ),
      ),
    ),
    objectType = Some(OntologyConstants.Xsd.Uri),
  )

  private val VersionDate: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.VersionDate,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          EN -> "version date",
        ),
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          EN -> "Provides the date of a particular version of a resource.",
        ),
      ),
    ),
    objectType = Some(OntologyConstants.Xsd.Uri),
  )

  private val Author: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.Author,
    propertyType = OntologyConstants.Owl.ObjectProperty,
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          EN -> "author",
        ),
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          EN -> "Specifies the author of a particular version of a resource.",
        ),
      ),
    ),
    objectType = Some(KA.User),
  )

  private val IsShared: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.IsShared,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          EN -> "is shared",
        ),
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          EN -> "Indicates whether an ontology can be shared by multiple projects",
        ),
      ),
    ),
    objectType = Some(OntologyConstants.Xsd.Boolean),
  )

  private val IsBuiltIn: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.IsBuiltIn,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          EN -> "is shared",
        ),
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          EN -> "Indicates whether an ontology is built into Knora",
        ),
      ),
    ),
    objectType = Some(OntologyConstants.Xsd.Boolean),
  )

  private val IsEditable: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.IsEditable,
    propertyType = OntologyConstants.Owl.AnnotationProperty,
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          EN -> "is editable",
        ),
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          EN -> "Indicates whether a property's values can be updated via the Knora API.",
        ),
      ),
    ),
    subjectType = Some(OntologyConstants.Owl.ObjectProperty),
    objectType = Some(OntologyConstants.Xsd.Boolean),
  )

  private val IsResourceClass: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.IsResourceClass,
    propertyType = OntologyConstants.Owl.AnnotationProperty,
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          EN -> "is resource class",
        ),
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          EN -> "Indicates whether class is a subclass of Resource.",
        ),
      ),
    ),
    subjectType = Some(OntologyConstants.Owl.Class),
    objectType = Some(OntologyConstants.Xsd.Boolean),
  )

  private val IsValueClass: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.IsValueClass,
    propertyType = OntologyConstants.Owl.AnnotationProperty,
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          EN -> "is value class",
        ),
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          EN -> "Indicates whether class is a subclass of Value.",
        ),
      ),
    ),
    subjectType = Some(OntologyConstants.Owl.Class),
    objectType = Some(OntologyConstants.Xsd.Boolean),
  )

  private val IsStandoffClass: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.IsStandoffClass,
    propertyType = OntologyConstants.Owl.AnnotationProperty,
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          EN -> "is standoff class",
        ),
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          EN -> "Indicates whether class is a subclass of StandoffTag.",
        ),
      ),
    ),
    subjectType = Some(OntologyConstants.Owl.Class),
    objectType = Some(OntologyConstants.Xsd.Boolean),
  )

  private val IsLinkProperty: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.IsLinkProperty,
    propertyType = OntologyConstants.Owl.AnnotationProperty,
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          EN -> "is link property",
        ),
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          EN -> "Indicates whether a property points to a resource",
        ),
      ),
    ),
    subjectType = Some(OntologyConstants.Owl.ObjectProperty),
    objectType = Some(OntologyConstants.Xsd.Boolean),
  )

  private val IsLinkValueProperty: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.IsLinkValueProperty,
    propertyType = OntologyConstants.Owl.AnnotationProperty,
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          EN -> "is link value property",
        ),
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          EN -> "Indicates whether a property points to a link value (reification)",
        ),
      ),
    ),
    subjectType = Some(OntologyConstants.Owl.ObjectProperty),
    objectType = Some(OntologyConstants.Xsd.Boolean),
  )

  private val IsInherited: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.IsInherited,
    propertyType = OntologyConstants.Owl.AnnotationProperty,
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          EN -> "is inherited",
        ),
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          EN -> "Indicates whether a cardinality has been inherited from a base class",
        ),
      ),
    ),
    subjectType = Some(OntologyConstants.Owl.Restriction),
    objectType = Some(OntologyConstants.Xsd.Boolean),
  )

  private val NewModificationDate: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.NewModificationDate,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          EN -> "new modification date",
        ),
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          EN -> "Specifies the new modification date of a resource",
        ),
      ),
    ),
    objectType = Some(OntologyConstants.Xsd.DateTimeStamp),
  )

  private val OntologyName: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.OntologyName,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          EN -> "ontology name",
        ),
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          EN -> "Represents the short name of an ontology",
        ),
      ),
    ),
    objectType = Some(OntologyConstants.Xsd.String),
  )

  private val MappingHasName: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.MappingHasName,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          EN -> "Name of a mapping (will be part of the mapping's Iri)",
        ),
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          EN -> "Represents the name of a mapping",
        ),
      ),
    ),
    objectType = Some(OntologyConstants.Xsd.String),
  )

  private val HasIncomingLinkValue: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.HasIncomingLinkValue,
    isResourceProp = true,
    propertyType = OntologyConstants.Owl.ObjectProperty,
    subjectType = Some(KA.Resource),
    objectType = Some(KA.LinkValue),
    subPropertyOf = Set(KA.HasLinkToValue),
    isLinkValueProp = true,
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          DE -> "hat eingehenden Verweis",
          EN -> "has incoming link",
          FR -> "liens entrants",
        ),
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          EN -> "Indicates that this resource referred to by another resource",
        ),
      ),
    ),
  )

  private val ValueAsString: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.ValueAsString,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(EN -> "A plain string representation of a value"),
      ),
    ),
    subjectType = Some(KA.Value),
    objectType = Some(OntologyConstants.Xsd.String),
  )

  private val SubjectType: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.SubjectType,
    propertyType = OntologyConstants.Owl.ObjectProperty,
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          EN -> "Subject type",
        ),
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          EN -> "Specifies the required type of the subjects of a property",
        ),
      ),
    ),
  )

  private val ObjectType: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.ObjectType,
    propertyType = OntologyConstants.Owl.ObjectProperty,
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          EN -> "Object type",
        ),
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          EN -> "Specifies the required type of the objects of a property",
        ),
      ),
    ),
  )

  private val TextValueHasMarkup: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.TextValueHasMarkup,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    subPropertyOf = Set(KA.ValueHas),
    subjectType = Some(KA.TextValue),
    objectType = Some(OntologyConstants.Xsd.Boolean),
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          EN -> "text value has markup",
        ),
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          EN -> "True if a text value has markup.",
        ),
      ),
    ),
  )

  private val TextValueHasStandoff: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.TextValueHasStandoff,
    propertyType = OntologyConstants.Owl.ObjectProperty,
    subPropertyOf = Set(KA.ValueHas),
    subjectType = Some(KA.TextValue),
    objectType = Some(KA.StandoffTag),
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          EN -> "text value has standoff",
        ),
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          EN -> "Standoff markup attached to a text value.",
        ),
      ),
    ),
  )

  private val TextValueHasMaxStandoffStartIndex: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.TextValueHasMaxStandoffStartIndex,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    subPropertyOf = Set(KA.ValueHas),
    subjectType = Some(KA.TextValue),
    objectType = Some(OntologyConstants.Xsd.Integer),
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          EN -> "text value has max standoff start index",
        ),
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          EN -> "The maximum knora-api:standoffTagHasStartIndex in a text value.",
        ),
      ),
    ),
  )

  private val NextStandoffStartIndex: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.NextStandoffStartIndex,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    objectType = Some(OntologyConstants.Xsd.Integer),
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          EN -> "next standoff start index",
        ),
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          EN -> "The next available knora-api:standoffTagHasStartIndex in a sequence of pages of standoff.",
        ),
      ),
    ),
  )

  private val StandoffTagHasStartParentIndex: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.StandoffTagHasStartParentIndex,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    subjectType = Some(KA.StandoffTag),
    objectType = Some(OntologyConstants.Xsd.Integer),
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          EN -> "standoff tag has start parent index",
        ),
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          EN -> "The next knora-api:standoffTagHasStartIndex of the start parent tag of a standoff tag.",
        ),
      ),
    ),
  )

  private val StandoffTagHasEndParentIndex: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.StandoffTagHasEndParentIndex,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    subjectType = Some(KA.StandoffTag),
    objectType = Some(OntologyConstants.Xsd.Integer),
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          EN -> "standoff tag has end parent index",
        ),
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          EN -> "The next knora-api:standoffTagHasStartIndex of the end parent tag of a standoff tag.",
        ),
      ),
    ),
  )

  private val TextValueHasLanguage: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.TextValueHasLanguage,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    subPropertyOf = Set(KA.ValueHas),
    subjectType = Some(KA.TextValue),
    objectType = Some(OntologyConstants.Xsd.String),
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          EN -> "text value has language",
        ),
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          EN -> "Language code attached to a text value.",
        ),
      ),
    ),
  )

  private val TextValueAsXml: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.TextValueAsXml,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    subPropertyOf = Set(KA.ValueHas),
    subjectType = Some(KA.TextValue),
    objectType = Some(OntologyConstants.Xsd.String),
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          EN -> "Text value as XML",
        ),
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          EN -> "A Text value represented in XML.",
        ),
      ),
    ),
  )

  private val TextValueAsHtml: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.TextValueAsHtml,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    subPropertyOf = Set(KA.ValueHas),
    subjectType = Some(KA.TextValue),
    objectType = Some(OntologyConstants.Xsd.String),
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          EN -> "Text value as HTML",
        ),
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          EN -> "A text value represented in HTML.",
        ),
      ),
    ),
  )

  private val TextValueHasMapping: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.TextValueHasMapping,
    propertyType = OntologyConstants.Owl.ObjectProperty,
    subPropertyOf = Set(KA.ValueHas),
    subjectType = Some(KA.TextValue),
    objectType = Some(KA.XMLToStandoffMapping),
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          EN -> "Text value has mapping",
        ),
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          EN -> "Indicates the mapping that is used to convert a text value's markup from from XML to standoff.",
        ),
      ),
    ),
  )

  private val DateValueHasStartYear: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.DateValueHasStartYear,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    subPropertyOf = Set(KA.ValueHas),
    subjectType = Some(KA.DateBase),
    objectType = Some(OntologyConstants.Xsd.Integer),
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          EN -> "Date value has start year",
        ),
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          EN -> "Represents the start year of a date value.",
        ),
      ),
    ),
  )

  private val DateValueHasEndYear: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.DateValueHasEndYear,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    subPropertyOf = Set(KA.ValueHas),
    subjectType = Some(KA.DateBase),
    objectType = Some(OntologyConstants.Xsd.Integer),
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          EN -> "Date value has end year",
        ),
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          EN -> "Represents the end year of a date value.",
        ),
      ),
    ),
  )

  private val DateValueHasStartMonth: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.DateValueHasStartMonth,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    subPropertyOf = Set(KA.ValueHas),
    subjectType = Some(KA.DateBase),
    objectType = Some(OntologyConstants.Xsd.Integer),
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          EN -> "Date value has start month",
        ),
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          EN -> "Represents the start month of a date value.",
        ),
      ),
    ),
  )

  private val DateValueHasEndMonth: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.DateValueHasEndMonth,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    subPropertyOf = Set(KA.ValueHas),
    subjectType = Some(KA.DateBase),
    objectType = Some(OntologyConstants.Xsd.Integer),
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          EN -> "Date value has end month",
        ),
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          EN -> "Represents the end month of a date value.",
        ),
      ),
    ),
  )

  private val DateValueHasStartDay: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.DateValueHasStartDay,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    subPropertyOf = Set(KA.ValueHas),
    subjectType = Some(KA.DateBase),
    objectType = Some(OntologyConstants.Xsd.Integer),
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          EN -> "Date value has start day",
        ),
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          EN -> "Represents the start day of a date value.",
        ),
      ),
    ),
  )

  private val DateValueHasEndDay: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.DateValueHasEndDay,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    subPropertyOf = Set(KA.ValueHas),
    subjectType = Some(KA.DateBase),
    objectType = Some(OntologyConstants.Xsd.Integer),
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          EN -> "Date value has end day",
        ),
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          EN -> "Represents the end day of a date value.",
        ),
      ),
    ),
  )

  private val DateValueHasStartEra: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.DateValueHasStartEra,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    subPropertyOf = Set(KA.ValueHas),
    subjectType = Some(KA.DateBase),
    objectType = Some(OntologyConstants.Xsd.String),
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          EN -> "Date value has start era",
        ),
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          EN -> "Represents the start era of a date value.",
        ),
      ),
    ),
  )

  private val DateValueHasEndEra: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.DateValueHasEndEra,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    subPropertyOf = Set(KA.ValueHas),
    subjectType = Some(KA.DateBase),
    objectType = Some(OntologyConstants.Xsd.String),
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          EN -> "Date value has end era",
        ),
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          EN -> "Represents the end era of a date value.",
        ),
      ),
    ),
  )

  private val DateValueHasCalendar: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.DateValueHasCalendar,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    subPropertyOf = Set(KA.ValueHas),
    subjectType = Some(KA.DateBase),
    objectType = Some(OntologyConstants.Xsd.String),
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          EN -> "Date value has calendar",
        ),
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          EN -> "Represents the calendar of a date value.",
        ),
      ),
    ),
  )

  private val LinkValueHasSource: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.LinkValueHasSource,
    propertyType = OntologyConstants.Owl.ObjectProperty,
    subPropertyOf = Set(KA.ValueHas),
    subjectType = Some(KA.LinkValue),
    objectType = Some(KA.Resource),
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          EN -> "Link value has source",
        ),
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          EN -> "Represents the source resource of a link value.",
        ),
      ),
    ),
  )

  private val LinkValueHasSourceIri: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.LinkValueHasSourceIri,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    subPropertyOf = Set(KA.ValueHas),
    subjectType = Some(KA.LinkValue),
    objectType = Some(OntologyConstants.Xsd.Uri),
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          EN -> "Link value has source IRI",
        ),
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          EN -> "Represents the IRI of the source resource of a link value.",
        ),
      ),
    ),
  )

  private val LinkValueHasTarget: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.LinkValueHasTarget,
    propertyType = OntologyConstants.Owl.ObjectProperty,
    subPropertyOf = Set(KA.ValueHas),
    subjectType = Some(KA.LinkValue),
    objectType = Some(KA.Resource),
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          EN -> "Link value has target",
        ),
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          EN -> "Represents the target resource of a link value.",
        ),
      ),
    ),
  )

  private val LinkValueHasTargetIri: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.LinkValueHasTargetIri,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    subPropertyOf = Set(KA.ValueHas),
    subjectType = Some(KA.LinkValue),
    objectType = Some(OntologyConstants.Xsd.Uri),
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          EN -> "Link value has target IRI",
        ),
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          EN -> "Represents the IRI of the target resource of a link value.",
        ),
      ),
    ),
  )

  private val IntValueAsInt: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.IntValueAsInt,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    subPropertyOf = Set(KA.ValueHas),
    subjectType = Some(KA.IntBase),
    objectType = Some(OntologyConstants.Xsd.Integer),
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          EN -> "Integer value as integer",
        ),
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          EN -> "Represents the literal integer value of an IntValue.",
        ),
      ),
    ),
  )

  private val DecimalValueAsDecimal: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.DecimalValueAsDecimal,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    subPropertyOf = Set(KA.ValueHas),
    subjectType = Some(KA.DecimalBase),
    objectType = Some(OntologyConstants.Xsd.Decimal),
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          EN -> "Decimal value as decimal",
        ),
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          EN -> "Represents the literal decimal value of a DecimalValue.",
        ),
      ),
    ),
  )

  private val IntervalValueHasStart: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.IntervalValueHasStart,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    subPropertyOf = Set(KA.ValueHas),
    subjectType = Some(KA.IntervalBase),
    objectType = Some(OntologyConstants.Xsd.Decimal),
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          EN -> "interval value has start",
        ),
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          EN -> "Represents the start position of an interval.",
        ),
      ),
    ),
  )

  private val IntervalValueHasEnd: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.IntervalValueHasEnd,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    subPropertyOf = Set(KA.ValueHas),
    subjectType = Some(KA.IntervalBase),
    objectType = Some(OntologyConstants.Xsd.Decimal),
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          EN -> "interval value has end",
        ),
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          EN -> "Represents the end position of an interval.",
        ),
      ),
    ),
  )

  private val BooleanValueAsBoolean: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.BooleanValueAsBoolean,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    subPropertyOf = Set(KA.ValueHas),
    subjectType = Some(KA.BooleanBase),
    objectType = Some(OntologyConstants.Xsd.Boolean),
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          EN -> "Boolean value as decimal",
        ),
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          EN -> "Represents the literal boolean value of a BooleanValue.",
        ),
      ),
    ),
  )

  private val GeometryValueAsGeometry: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.GeometryValueAsGeometry,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    subPropertyOf = Set(KA.ValueHas),
    subjectType = Some(KA.GeomValue),
    objectType = Some(OntologyConstants.Xsd.String),
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          EN -> "Geometry value as JSON",
        ),
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          EN -> "Represents a 2D geometry value as JSON.",
        ),
      ),
    ),
  )

  private val ListValueAsListNode: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.ListValueAsListNode,
    propertyType = OntologyConstants.Owl.ObjectProperty,
    subPropertyOf = Set(KA.ValueHas),
    subjectType = Some(KA.ListValue),
    objectType = Some(KA.ListNode),
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          EN -> "Hierarchical list value as list node",
        ),
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          EN -> "Represents a reference to a hierarchical list node.",
        ),
      ),
    ),
  )

  private val ColorValueAsColor: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.ColorValueAsColor,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    subPropertyOf = Set(KA.ValueHas),
    subjectType = Some(KA.ColorBase),
    objectType = Some(OntologyConstants.Xsd.String),
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          EN -> "Color value as color",
        ),
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          EN -> "Represents the literal RGB value of a ColorValue.",
        ),
      ),
    ),
  )

  private val UriValueAsUri: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.UriValueAsUri,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    subPropertyOf = Set(KA.ValueHas),
    subjectType = Some(KA.UriBase),
    objectType = Some(OntologyConstants.Xsd.Uri),
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          EN -> "URI value as URI",
        ),
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          EN -> "Represents the literal URI value of a UriValue.",
        ),
      ),
    ),
  )

  private val GeonameValueAsGeonameCode: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.GeonameValueAsGeonameCode,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    subPropertyOf = Set(KA.ValueHas),
    subjectType = Some(KA.GeonameValue),
    objectType = Some(OntologyConstants.Xsd.String),
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          EN -> "Geoname value as Geoname code",
        ),
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          EN -> "Represents the literal Geoname code of a GeonameValue.",
        ),
      ),
    ),
  )

  private val FileValueAsUrl: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.FileValueAsUrl,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    subPropertyOf = Set(KA.ValueHas),
    subjectType = Some(KA.FileValue),
    objectType = Some(OntologyConstants.Xsd.Uri),
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          EN -> "File value as URL",
        ),
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          EN -> "The URL at which the file can be accessed.",
        ),
      ),
    ),
  )

  private val FileValueHasFilename: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.FileValueHasFilename,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    subPropertyOf = Set(KA.ValueHas),
    subjectType = Some(KA.FileValue),
    objectType = Some(OntologyConstants.Xsd.String),
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          EN -> "File value has filename",
        ),
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          EN -> "The name of the file that a file value represents.",
        ),
      ),
    ),
  )

  private val StillImageFileValueHasDimX: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.StillImageFileValueHasDimX,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    subPropertyOf = Set(KA.ValueHas),
    subjectType = Some(KA.StillImageFileValue),
    objectType = Some(OntologyConstants.Xsd.Integer),
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          EN -> "Still image file value has X dimension",
        ),
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          EN -> "The horizontal dimension of a still image file value.",
        ),
      ),
    ),
  )

  private val StillImageFileValueHasDimY: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.StillImageFileValueHasDimY,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    subPropertyOf = Set(KA.ValueHas),
    subjectType = Some(KA.StillImageFileValue),
    objectType = Some(OntologyConstants.Xsd.Integer),
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          EN -> "Still image file value has Y dimension",
        ),
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          EN -> "The vertical dimension of a still image file value.",
        ),
      ),
    ),
  )

  private val StillImageFileValueHasIIIFBaseUrl: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.StillImageFileValueHasIIIFBaseUrl,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    subPropertyOf = Set(KA.ValueHas),
    subjectType = Some(KA.StillImageFileValue),
    objectType = Some(OntologyConstants.Xsd.Uri),
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(
          EN -> "Still image file value has IIIF base URL",
        ),
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(
          EN -> "The IIIF base URL of a still image file value.",
        ),
      ),
    ),
  )
  private val StillImageFileValueHasExternalUrl: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.StillImageFileValueHasExternalUrl,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    subPropertyOf = Set(KA.ValueHas),
    subjectType = Some(KA.StillImageExternalFileValue),
    objectType = Some(OntologyConstants.Xsd.Uri),
    predicates = Seq(
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Label,
        objectsWithLang = Map(EN -> "External Url to the image"),
      ),
      makePredicate(
        predicateIri = OntologyConstants.Rdfs.Comment,
        objectsWithLang = Map(EN -> "External Url to the image"),
      ),
    ),
  )

  private val ResourceCardinalities = Map(
    KA.HasIncomingLinkValue -> Unbounded,
    KA.ArkUrl               -> ExactlyOne,
    KA.VersionArkUrl        -> ExactlyOne,
    KA.VersionDate          -> ZeroOrOne,
    KA.UserHasPermission    -> ExactlyOne,
    KA.IsDeleted            -> ZeroOrOne,
  )

  private val DateBaseCardinalities = Map(
    KA.DateValueHasStartYear  -> ExactlyOne,
    KA.DateValueHasEndYear    -> ExactlyOne,
    KA.DateValueHasStartMonth -> ZeroOrOne,
    KA.DateValueHasEndMonth   -> ZeroOrOne,
    KA.DateValueHasStartDay   -> ZeroOrOne,
    KA.DateValueHasEndDay     -> ZeroOrOne,
    KA.DateValueHasStartEra   -> ExactlyOne,
    KA.DateValueHasEndEra     -> ExactlyOne,
    KA.DateValueHasCalendar   -> ExactlyOne,
  )

  private val UriBaseCardinalities = Map(
    KA.UriValueAsUri -> ExactlyOne,
  )

  private val BooleanBaseCardinalities = Map(
    KA.BooleanValueAsBoolean -> ExactlyOne,
  )

  private val IntBaseCardinalities = Map(
    KA.IntValueAsInt -> ExactlyOne,
  )

  private val DecimalBaseCardinalities = Map(
    KA.DecimalValueAsDecimal -> ExactlyOne,
  )

  private val IntervalBaseCardinalities = Map(
    KA.IntervalValueHasStart -> ExactlyOne,
    KA.IntervalValueHasEnd   -> ExactlyOne,
  )

  private val ColorBaseCardinalities = Map(
    KA.ColorValueAsColor -> ExactlyOne,
  )

  private val ValueCardinalities = Map(
    KA.ValueAsString     -> ZeroOrOne,
    KA.HasPermissions    -> ExactlyOne,
    KA.UserHasPermission -> ExactlyOne,
    KA.ArkUrl            -> ExactlyOne,
    KA.VersionArkUrl     -> ExactlyOne,
    KA.IsDeleted         -> ZeroOrOne,
    KA.ValueHasUUID      -> ExactlyOne,
  )

  private val TextValueCardinalities = Map(
    KA.TextValueHasStandoff              -> Unbounded,
    KA.TextValueHasMarkup                -> ZeroOrOne,
    KA.TextValueHasLanguage              -> ZeroOrOne,
    KA.TextValueAsXml                    -> ZeroOrOne,
    KA.TextValueAsHtml                   -> ZeroOrOne,
    KA.TextValueHasMapping               -> ZeroOrOne,
    KA.TextValueHasMaxStandoffStartIndex -> ZeroOrOne,
  )

  private val StandoffTagCardinalities = Map(
    KA.StandoffTagHasStartParentIndex -> ZeroOrOne,
    KA.StandoffTagHasEndParentIndex   -> ZeroOrOne,
  )

  private val LinkValueCardinalities = Map(
    KA.LinkValueHasSource    -> ZeroOrOne,
    KA.LinkValueHasTarget    -> ZeroOrOne,
    KA.LinkValueHasSourceIri -> ZeroOrOne,
    KA.LinkValueHasTargetIri -> ZeroOrOne,
  )

  private val GeomValueCardinalities = Map(
    KA.GeometryValueAsGeometry -> ExactlyOne,
  )

  private val ListValueCardinalities = Map(
    KA.ListValueAsListNode -> ExactlyOne,
  )

  private val GeonameValueCardinalities = Map(
    KA.GeonameValueAsGeonameCode -> ExactlyOne,
  )

  private val FileValueCardinalities = Map(
    KA.FileValueAsUrl       -> ExactlyOne,
    KA.FileValueHasFilename -> ExactlyOne,
  )

  private val StillImageFileValueCardinalities = Map(
    KA.StillImageFileValueHasDimX        -> ExactlyOne,
    KA.StillImageFileValueHasDimY        -> ExactlyOne,
    KA.StillImageFileValueHasIIIFBaseUrl -> ExactlyOne,
  )

  private val StillImageExternalFileValueCardinalities = Map(
    KA.StillImageFileValueHasIIIFBaseUrl -> ExactlyOne,
    KA.StillImageFileValueHasExternalUrl -> ExactlyOne,
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
    OntologyConstants.KnoraBase.ExtResProvider,
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
    OntologyConstants.KnoraBase.ExternalResValue,
  ).map(_.toSmartIri)

  /**
   * After `knora-base` has been converted to the [[ApiV2Complex]] schema, these cardinalities must be
   * added to the specified classes to obtain `knora-api`.
   */
  override val externalCardinalitiesToAdd: Map[SmartIri, Map[SmartIri, KnoraCardinalityInfo]] = Map(
    KA.Resource                    -> ResourceCardinalities,
    KA.DateBase                    -> DateBaseCardinalities,
    KA.UriBase                     -> UriBaseCardinalities,
    KA.BooleanBase                 -> BooleanBaseCardinalities,
    KA.IntBase                     -> IntBaseCardinalities,
    KA.DecimalBase                 -> DecimalBaseCardinalities,
    KA.IntervalBase                -> IntervalBaseCardinalities,
    KA.ColorBase                   -> ColorBaseCardinalities,
    KA.Value                       -> ValueCardinalities,
    KA.TextValue                   -> TextValueCardinalities,
    KA.StandoffTag                 -> StandoffTagCardinalities,
    KA.LinkValue                   -> LinkValueCardinalities,
    KA.GeomValue                   -> GeomValueCardinalities,
    KA.ListValue                   -> ListValueCardinalities,
    KA.GeonameValue                -> GeonameValueCardinalities,
    KA.FileValue                   -> FileValueCardinalities,
    KA.StillImageFileValue         -> StillImageFileValueCardinalities,
    KA.StillImageExternalFileValue -> StillImageExternalFileValueCardinalities,
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
    StillImageFileValueHasExternalUrl,
  ).map { propertyInfo =>
    propertyInfo.entityInfoContent.propertyIri -> propertyInfo
  }.toMap

  private def makePredicate(predicateIri: IRI, objectsWithLang: Map[String, String]): PredicateInfoV2 =
    PredicateInfoV2Builder.make(predicateIri).withStringLiterals(objectsWithLang).build()

  final case class PredicateInfoV2Builder private (
    predicateIri: SmartIri,
    objects: Seq[OntologyLiteralV2] = Seq.empty,
  ) {
    self =>
    def withObject(obj: OntologyLiteralV2): PredicateInfoV2Builder =
      copy(objects = self.objects :+ obj)
    def withObjects(objs: Seq[OntologyLiteralV2]): PredicateInfoV2Builder =
      copy(objects = self.objects ++ objs)
    def withStringLiteral(lang: String, value: String): PredicateInfoV2Builder =
      withObject(StringLiteralV2.from(value, Some(lang)))
    def withStringLiteral(value: String): PredicateInfoV2Builder =
      withObject(StringLiteralV2.from(value, None))
    def withStringLiterals(literals: Map[String, String]): PredicateInfoV2Builder =
      withObjects(literals.map { case (lang, value) => StringLiteralV2.from(value, Some(lang)) }.toSeq)
    def build(): PredicateInfoV2 = PredicateInfoV2(self.predicateIri, self.objects)
  }
  object PredicateInfoV2Builder {
    def make(predicateIri: IRI): PredicateInfoV2Builder =
      PredicateInfoV2Builder(predicateIri.toSmartIri)
    def make(predicateIri: Rdf4jIRI): PredicateInfoV2Builder =
      make(predicateIri.toString)

    def makeRdfType(): PredicateInfoV2Builder = make(RDF.TYPE)

    def makeRdfsLabel(): PredicateInfoV2Builder = make(RDFS.LABEL)
    def makeRdfsLabel(lang: String, value: String): PredicateInfoV2Builder =
      makeRdfsLabel().withStringLiteral(lang, value)
    def makeRdfsLabel(literals: Map[String, String]): PredicateInfoV2Builder =
      makeRdfsLabel().withStringLiterals(literals)

    def makeRdfsComment(): PredicateInfoV2Builder = make(RDFS.COMMENT)
    def makeRdfsComment(lang: String, value: String): PredicateInfoV2Builder =
      makeRdfsComment().withStringLiteral(lang, value)
  }

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
    objectType: Option[IRI] = None,
  ): ReadPropertyInfoV2 = {
    val propTypePred =
      PredicateInfoV2Builder.makeRdfType().withObject(SmartIriLiteralV2(propertyType.toSmartIri)).build()

    val maybeSubjectTypePred = subjectType.map { subjType =>
      PredicateInfoV2Builder
        .make(KA.SubjectType)
        .withObject(SmartIriLiteralV2(subjType.toSmartIri))
    }.map(_.build())

    val maybeObjectTypePred = objectType.map { objType =>
      PredicateInfoV2Builder
        .make(KA.ObjectType)
        .withObject(SmartIriLiteralV2(objType.toSmartIri))
    }.map(_.build())

    val predsWithTypes = predicates ++ maybeSubjectTypePred ++ maybeObjectTypePred :+ propTypePred

    ReadPropertyInfoV2(
      entityInfoContent = PropertyInfoContentV2(
        propertyIri = propertyIri.toSmartIri,
        ontologySchema = ApiV2Complex,
        predicates = predsWithTypes.map { pred =>
          pred.predicateIri -> pred
        }.toMap,
        subPropertyOf = subPropertyOf.map(iri => iri.toSmartIri),
      ),
      isResourceProp = isResourceProp,
      isEditable = isEditable,
      isLinkProp = isLinkProp,
      isLinkValueProp = isLinkValueProp,
    )
  }
}
