/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.v2.responder.ontologymessages

import org.eclipse.rdf4j.model.IRI as Rdf4jIRI
import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.model.vocabulary.RDFS

import org.knora.webapi.*
import org.knora.webapi.LanguageCode.*
import org.knora.webapi.messages.IriConversions.*
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.OntologyConstants.KnoraApiV2Complex as KA
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.store.triplestoremessages.OntologyLiteralV2
import org.knora.webapi.messages.store.triplestoremessages.SmartIriLiteralV2
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.messages.v2.responder.ontologymessages.KnoraBaseToApiV2ComplexTransformationRules.PredicateInfoV2Builder.makeRdfsCommentEn
import org.knora.webapi.messages.v2.responder.ontologymessages.KnoraBaseToApiV2ComplexTransformationRules.PredicateInfoV2Builder.makeRdfsLabel
import org.knora.webapi.messages.v2.responder.ontologymessages.KnoraBaseToApiV2ComplexTransformationRules.PredicateInfoV2Builder.makeRdfsLabelEn
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
      makeRdfsLabel(
        Map(
          DE -> "Ergebnis",
          EN -> "result",
          FR -> "résultat",
          IT -> "risultato",
        ),
      ),
      makeRdfsCommentEn("Provides a message indicating that an operation was successful"),
    ).map(_.build()),
    objectType = Some(OntologyConstants.Xsd.String),
  )

  private val Error: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.Error,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    predicates = Seq(makeRdfsLabelEn("error"), makeRdfsCommentEn("Provides an error message")).map(_.build()),
    objectType = Some(OntologyConstants.Xsd.String),
  )

  private val CanDo: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.CanDo,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    predicates = Seq(makeRdfsLabelEn("can do"), makeRdfsCommentEn("Indicates whether an operation can be performed"))
      .map(_.build()),
    objectType = Some(OntologyConstants.Xsd.Boolean),
  )

  private val MayHaveMoreResults: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.MayHaveMoreResults,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    predicates = Seq(
      makeRdfsLabelEn("May have more results"),
      makeRdfsCommentEn("Indicates whether more results may be available for a search query"),
    ).map(_.build()),
    objectType = Some(OntologyConstants.Xsd.Boolean),
  )

  private val UserHasPermission: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.UserHasPermission,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    predicates = Seq(
      makeRdfsLabelEn("user has permission"),
      makeRdfsCommentEn("Provides the requesting user's maximum permission on a resource or value."),
    ).map(_.build()),
    objectType = Some(OntologyConstants.Xsd.String),
  )

  private val ArkUrl: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.ArkUrl,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    predicates = Seq(makeRdfsLabelEn("ARK URL"), makeRdfsCommentEn("Provides the ARK URL of a resource or value."))
      .map(_.build()),
    objectType = Some(OntologyConstants.Xsd.Uri),
  )

  private val VersionArkUrl: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.VersionArkUrl,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    predicates = Seq(
      makeRdfsLabelEn("version ARK URL"),
      makeRdfsCommentEn("Provides the ARK URL of a particular version of a resource or value."),
    ).map(_.build()),
    objectType = Some(OntologyConstants.Xsd.Uri),
  )

  private val VersionDate: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.VersionDate,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    predicates = Seq(
      makeRdfsLabelEn("version date"),
      makeRdfsCommentEn("Provides the date of a particular version of a resource."),
    ).map(_.build()),
    objectType = Some(OntologyConstants.Xsd.Uri),
  )

  private val Author: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.Author,
    propertyType = OntologyConstants.Owl.ObjectProperty,
    predicates = Seq(
      makeRdfsLabelEn("author"),
      makeRdfsCommentEn("Specifies the author of a particular version of a resource."),
    ).map(_.build()),
    objectType = Some(KA.User),
  )

  private val IsShared: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.IsShared,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    predicates = Seq(
      makeRdfsLabelEn("is shared"),
      makeRdfsCommentEn("Indicates whether an ontology can be shared by multiple projects"),
    ).map(_.build()),
    objectType = Some(OntologyConstants.Xsd.Boolean),
  )

  private val IsBuiltIn: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.IsBuiltIn,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    predicates = Seq(
      makeRdfsLabelEn("is shared"),
      makeRdfsCommentEn("Indicates whether an ontology is built into Knora"),
    ).map(_.build()),
    objectType = Some(OntologyConstants.Xsd.Boolean),
  )

  private val IsEditable: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.IsEditable,
    propertyType = OntologyConstants.Owl.AnnotationProperty,
    predicates = Seq(
      makeRdfsLabelEn("is editable"),
      makeRdfsCommentEn("Indicates whether a property's values can be updated via the Knora API."),
    ).map(_.build()),
    subjectType = Some(OntologyConstants.Owl.ObjectProperty),
    objectType = Some(OntologyConstants.Xsd.Boolean),
  )

  private val IsResourceClass: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.IsResourceClass,
    propertyType = OntologyConstants.Owl.AnnotationProperty,
    predicates = Seq(
      makeRdfsLabelEn("is resource class"),
      makeRdfsCommentEn("Indicates whether class is a subclass of Resource."),
    ).map(_.build()),
    subjectType = Some(OntologyConstants.Owl.Class),
    objectType = Some(OntologyConstants.Xsd.Boolean),
  )

  private val IsValueClass: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.IsValueClass,
    propertyType = OntologyConstants.Owl.AnnotationProperty,
    predicates = Seq(
      makeRdfsLabelEn("is value class"),
      makeRdfsCommentEn("Indicates whether class is a subclass of Value."),
    ).map(_.build()),
    subjectType = Some(OntologyConstants.Owl.Class),
    objectType = Some(OntologyConstants.Xsd.Boolean),
  )

  private val IsStandoffClass: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.IsStandoffClass,
    propertyType = OntologyConstants.Owl.AnnotationProperty,
    predicates = Seq(
      makeRdfsLabelEn("is standoff class"),
      makeRdfsCommentEn("Indicates whether class is a subclass of StandoffTag."),
    ).map(_.build()),
    subjectType = Some(OntologyConstants.Owl.Class),
    objectType = Some(OntologyConstants.Xsd.Boolean),
  )

  private val IsLinkProperty: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.IsLinkProperty,
    propertyType = OntologyConstants.Owl.AnnotationProperty,
    predicates = Seq(
      makeRdfsLabelEn("is link property"),
      makeRdfsCommentEn("Indicates whether a property points to a resource"),
    ).map(_.build()),
    subjectType = Some(OntologyConstants.Owl.ObjectProperty),
    objectType = Some(OntologyConstants.Xsd.Boolean),
  )

  private val IsLinkValueProperty: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.IsLinkValueProperty,
    propertyType = OntologyConstants.Owl.AnnotationProperty,
    predicates = Seq(
      makeRdfsLabelEn("is link value property"),
      makeRdfsCommentEn("Indicates whether a property points to a link value (reification)"),
    ).map(_.build()),
    subjectType = Some(OntologyConstants.Owl.ObjectProperty),
    objectType = Some(OntologyConstants.Xsd.Boolean),
  )

  private val IsInherited: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.IsInherited,
    propertyType = OntologyConstants.Owl.AnnotationProperty,
    predicates = Seq(
      makeRdfsLabelEn("is inherited"),
      makeRdfsCommentEn("Indicates whether a cardinality has been inherited from a base class"),
    ).map(_.build()),
    subjectType = Some(OntologyConstants.Owl.Restriction),
    objectType = Some(OntologyConstants.Xsd.Boolean),
  )

  private val NewModificationDate: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.NewModificationDate,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    predicates = Seq(
      makeRdfsLabelEn("new modification date"),
      makeRdfsCommentEn("Specifies the new modification date of a resource"),
    ).map(_.build()),
    objectType = Some(OntologyConstants.Xsd.DateTimeStamp),
  )

  private val OntologyName: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.OntologyName,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    predicates = Seq(
      makeRdfsLabelEn("ontology name"),
      makeRdfsCommentEn("Represents the short name of an ontology"),
    ).map(_.build()),
    objectType = Some(OntologyConstants.Xsd.String),
  )

  private val MappingHasName: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.MappingHasName,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    predicates = Seq(
      makeRdfsLabelEn("Name of a mapping (will be part of the mapping's Iri)"),
      makeRdfsCommentEn("Represents the name of a mapping"),
    ).map(_.build()),
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
      makeRdfsLabel(
        Map(
          DE -> "hat eingehenden Verweis",
          EN -> "has incoming link",
          FR -> "liens entrants",
        ),
      ),
      makeRdfsCommentEn("Indicates that this resource referred to by another resource"),
    ).map(_.build()),
  )

  private val ValueAsString: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.ValueAsString,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    predicates = Seq(makeRdfsCommentEn("A plain string representation of a value").build()),
    subjectType = Some(KA.Value),
    objectType = Some(OntologyConstants.Xsd.String),
  )

  private val SubjectType: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.SubjectType,
    propertyType = OntologyConstants.Owl.ObjectProperty,
    predicates = Seq(
      makeRdfsLabelEn("Subject type"),
      makeRdfsCommentEn("Specifies the required type of the subjects of a property"),
    ).map(_.build()),
  )

  private val ObjectType: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.ObjectType,
    propertyType = OntologyConstants.Owl.ObjectProperty,
    predicates = Seq(
      makeRdfsLabelEn("Object type"),
      makeRdfsCommentEn("Specifies the required type of the objects of a property"),
    ).map(_.build()),
  )

  private val TextValueHasMarkup: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.TextValueHasMarkup,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    subPropertyOf = Set(KA.ValueHas),
    subjectType = Some(KA.TextValue),
    objectType = Some(OntologyConstants.Xsd.Boolean),
    predicates = Seq(
      makeRdfsLabelEn("text value has markup"),
      makeRdfsCommentEn("True if a text value has markup."),
    ).map(_.build()),
  )

  private val TextValueHasStandoff: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.TextValueHasStandoff,
    propertyType = OntologyConstants.Owl.ObjectProperty,
    subPropertyOf = Set(KA.ValueHas),
    subjectType = Some(KA.TextValue),
    objectType = Some(KA.StandoffTag),
    predicates = Seq(
      makeRdfsLabelEn("text value has standoff"),
      makeRdfsCommentEn("Standoff markup attached to a text value."),
    ).map(_.build()),
  )

  private val TextValueHasMaxStandoffStartIndex: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.TextValueHasMaxStandoffStartIndex,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    subPropertyOf = Set(KA.ValueHas),
    subjectType = Some(KA.TextValue),
    objectType = Some(OntologyConstants.Xsd.Integer),
    predicates = Seq(
      makeRdfsLabelEn("text value has max standoff start index"),
      makeRdfsCommentEn("The maximum knora-api:standoffTagHasStartIndex in a text value."),
    ).map(_.build()),
  )

  private val NextStandoffStartIndex: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.NextStandoffStartIndex,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    objectType = Some(OntologyConstants.Xsd.Integer),
    predicates = Seq(
      makeRdfsLabelEn("next standoff start index"),
      makeRdfsCommentEn("The next available knora-api:standoffTagHasStartIndex in a sequence of pages of standoff."),
    ).map(_.build()),
  )

  private val StandoffTagHasStartParentIndex: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.StandoffTagHasStartParentIndex,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    subjectType = Some(KA.StandoffTag),
    objectType = Some(OntologyConstants.Xsd.Integer),
    predicates = Seq(
      makeRdfsLabelEn("standoff tag has start parent index"),
      makeRdfsCommentEn("The next knora-api:standoffTagHasStartIndex of the start parent tag of a standoff tag."),
    ).map(_.build()),
  )

  private val StandoffTagHasEndParentIndex: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.StandoffTagHasEndParentIndex,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    subjectType = Some(KA.StandoffTag),
    objectType = Some(OntologyConstants.Xsd.Integer),
    predicates = Seq(
      makeRdfsLabelEn("standoff tag has end parent index"),
      makeRdfsCommentEn("The next knora-api:standoffTagHasStartIndex of the end parent tag of a standoff tag."),
    ).map(_.build()),
  )

  private val TextValueHasLanguage: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.TextValueHasLanguage,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    subPropertyOf = Set(KA.ValueHas),
    subjectType = Some(KA.TextValue),
    objectType = Some(OntologyConstants.Xsd.String),
    predicates = Seq(
      makeRdfsLabelEn("text value has language"),
      makeRdfsCommentEn("Language code attached to a text value."),
    ).map(_.build()),
  )

  private val TextValueAsXml: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.TextValueAsXml,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    subPropertyOf = Set(KA.ValueHas),
    subjectType = Some(KA.TextValue),
    objectType = Some(OntologyConstants.Xsd.String),
    predicates = Seq(
      makeRdfsLabelEn("Text value as XML"),
      makeRdfsCommentEn("A Text value represented in XML."),
    ).map(_.build()),
  )

  private val TextValueAsHtml: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.TextValueAsHtml,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    subPropertyOf = Set(KA.ValueHas),
    subjectType = Some(KA.TextValue),
    objectType = Some(OntologyConstants.Xsd.String),
    predicates = Seq(
      makeRdfsLabelEn("Text value as HTML"),
      makeRdfsCommentEn("A text value represented in HTML."),
    ).map(_.build()),
  )

  private val TextValueHasMapping: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.TextValueHasMapping,
    propertyType = OntologyConstants.Owl.ObjectProperty,
    subPropertyOf = Set(KA.ValueHas),
    subjectType = Some(KA.TextValue),
    objectType = Some(KA.XMLToStandoffMapping),
    predicates = Seq(
      makeRdfsLabelEn("Text value has mapping"),
      makeRdfsCommentEn(
        "Indicates the mapping that is used to convert a text value's markup from from XML to standoff.",
      ),
    ).map(_.build()),
  )

  private val DateValueHasStartYear: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.DateValueHasStartYear,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    subPropertyOf = Set(KA.ValueHas),
    subjectType = Some(KA.DateBase),
    objectType = Some(OntologyConstants.Xsd.Integer),
    predicates = Seq(
      makeRdfsLabelEn("Date value has start year"),
      makeRdfsCommentEn("Represents the start year of a date value."),
    ).map(_.build()),
  )

  private val DateValueHasEndYear: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.DateValueHasEndYear,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    subPropertyOf = Set(KA.ValueHas),
    subjectType = Some(KA.DateBase),
    objectType = Some(OntologyConstants.Xsd.Integer),
    predicates = Seq(
      makeRdfsLabelEn("Date value has end year"),
      makeRdfsCommentEn("Represents the end year of a date value."),
    ).map(_.build()),
  )

  private val DateValueHasStartMonth: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.DateValueHasStartMonth,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    subPropertyOf = Set(KA.ValueHas),
    subjectType = Some(KA.DateBase),
    objectType = Some(OntologyConstants.Xsd.Integer),
    predicates = Seq(
      makeRdfsLabelEn("Date value has start month"),
      makeRdfsCommentEn("Represents the start month of a date value."),
    ).map(_.build()),
  )

  private val DateValueHasEndMonth: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.DateValueHasEndMonth,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    subPropertyOf = Set(KA.ValueHas),
    subjectType = Some(KA.DateBase),
    objectType = Some(OntologyConstants.Xsd.Integer),
    predicates = Seq(
      makeRdfsLabelEn("Date value has end month"),
      makeRdfsCommentEn("Represents the end month of a date value."),
    ).map(_.build()),
  )

  private val DateValueHasStartDay: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.DateValueHasStartDay,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    subPropertyOf = Set(KA.ValueHas),
    subjectType = Some(KA.DateBase),
    objectType = Some(OntologyConstants.Xsd.Integer),
    predicates = Seq(
      makeRdfsLabelEn("Date value has start day"),
      makeRdfsCommentEn("Represents the start day of a date value."),
    ).map(_.build()),
  )

  private val DateValueHasEndDay: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.DateValueHasEndDay,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    subPropertyOf = Set(KA.ValueHas),
    subjectType = Some(KA.DateBase),
    objectType = Some(OntologyConstants.Xsd.Integer),
    predicates = Seq(
      makeRdfsLabelEn("Date value has end day"),
      makeRdfsCommentEn("Represents the end day of a date value."),
    ).map(_.build()),
  )

  private val DateValueHasStartEra: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.DateValueHasStartEra,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    subPropertyOf = Set(KA.ValueHas),
    subjectType = Some(KA.DateBase),
    objectType = Some(OntologyConstants.Xsd.String),
    predicates = Seq(
      makeRdfsLabelEn("Date value has start era"),
      makeRdfsCommentEn("Represents the start era of a date value."),
    ).map(_.build()),
  )

  private val DateValueHasEndEra: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.DateValueHasEndEra,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    subPropertyOf = Set(KA.ValueHas),
    subjectType = Some(KA.DateBase),
    objectType = Some(OntologyConstants.Xsd.String),
    predicates = Seq(
      makeRdfsLabelEn("Date value has end era"),
      makeRdfsCommentEn("Represents the end era of a date value."),
    ).map(_.build()),
  )

  private val DateValueHasCalendar: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.DateValueHasCalendar,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    subPropertyOf = Set(KA.ValueHas),
    subjectType = Some(KA.DateBase),
    objectType = Some(OntologyConstants.Xsd.String),
    predicates = Seq(
      makeRdfsLabelEn("Date value has calendar"),
      makeRdfsCommentEn("Represents the calendar of a date value."),
    ).map(_.build()),
  )

  private val LinkValueHasSource: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.LinkValueHasSource,
    propertyType = OntologyConstants.Owl.ObjectProperty,
    subPropertyOf = Set(KA.ValueHas),
    subjectType = Some(KA.LinkValue),
    objectType = Some(KA.Resource),
    predicates = Seq(
      makeRdfsLabelEn("Link value has source"),
      makeRdfsCommentEn("Represents the source resource of a link value."),
    ).map(_.build()),
  )

  private val LinkValueHasSourceIri: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.LinkValueHasSourceIri,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    subPropertyOf = Set(KA.ValueHas),
    subjectType = Some(KA.LinkValue),
    objectType = Some(OntologyConstants.Xsd.Uri),
    predicates = Seq(
      makeRdfsLabelEn("Link value has source IRI"),
      makeRdfsCommentEn("Represents the IRI of the source resource of a link value."),
    ).map(_.build()),
  )

  private val LinkValueHasTarget: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.LinkValueHasTarget,
    propertyType = OntologyConstants.Owl.ObjectProperty,
    subPropertyOf = Set(KA.ValueHas),
    subjectType = Some(KA.LinkValue),
    objectType = Some(KA.Resource),
    predicates = Seq(
      makeRdfsLabelEn("Link value has target"),
      makeRdfsCommentEn("Represents the target resource of a link value."),
    ).map(_.build()),
  )

  private val LinkValueHasTargetIri: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.LinkValueHasTargetIri,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    subPropertyOf = Set(KA.ValueHas),
    subjectType = Some(KA.LinkValue),
    objectType = Some(OntologyConstants.Xsd.Uri),
    predicates = Seq(
      makeRdfsLabelEn("Link value has target IRI"),
      makeRdfsCommentEn("Represents the IRI of the target resource of a link value."),
    ).map(_.build()),
  )

  private val IntValueAsInt: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.IntValueAsInt,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    subPropertyOf = Set(KA.ValueHas),
    subjectType = Some(KA.IntBase),
    objectType = Some(OntologyConstants.Xsd.Integer),
    predicates = Seq(
      makeRdfsLabelEn("Integer value as integer"),
      makeRdfsCommentEn("Represents the literal integer value of an IntValue."),
    ).map(_.build()),
  )

  private val DecimalValueAsDecimal: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.DecimalValueAsDecimal,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    subPropertyOf = Set(KA.ValueHas),
    subjectType = Some(KA.DecimalBase),
    objectType = Some(OntologyConstants.Xsd.Decimal),
    predicates = Seq(
      makeRdfsLabelEn("Decimal value as decimal"),
      makeRdfsCommentEn("Represents the literal decimal value of a DecimalValue."),
    ).map(_.build()),
  )

  private val IntervalValueHasStart: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.IntervalValueHasStart,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    subPropertyOf = Set(KA.ValueHas),
    subjectType = Some(KA.IntervalBase),
    objectType = Some(OntologyConstants.Xsd.Decimal),
    predicates = Seq(
      makeRdfsLabelEn("interval value has start"),
      makeRdfsCommentEn("Represents the start position of an interval."),
    ).map(_.build()),
  )

  private val IntervalValueHasEnd: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.IntervalValueHasEnd,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    subPropertyOf = Set(KA.ValueHas),
    subjectType = Some(KA.IntervalBase),
    objectType = Some(OntologyConstants.Xsd.Decimal),
    predicates = Seq(
      makeRdfsLabelEn("interval value has end"),
      makeRdfsCommentEn("Represents the end position of an interval."),
    ).map(_.build()),
  )

  private val BooleanValueAsBoolean: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.BooleanValueAsBoolean,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    subPropertyOf = Set(KA.ValueHas),
    subjectType = Some(KA.BooleanBase),
    objectType = Some(OntologyConstants.Xsd.Boolean),
    predicates = Seq(
      makeRdfsLabelEn("Boolean value as decimal"),
      makeRdfsCommentEn("Represents the literal boolean value of a BooleanValue."),
    ).map(_.build()),
  )

  private val GeometryValueAsGeometry: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.GeometryValueAsGeometry,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    subPropertyOf = Set(KA.ValueHas),
    subjectType = Some(KA.GeomValue),
    objectType = Some(OntologyConstants.Xsd.String),
    predicates = Seq(
      makeRdfsLabelEn("Geometry value as JSON"),
      makeRdfsCommentEn("Represents a 2D geometry value as JSON."),
    ).map(_.build()),
  )

  private val ListValueAsListNode: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.ListValueAsListNode,
    propertyType = OntologyConstants.Owl.ObjectProperty,
    subPropertyOf = Set(KA.ValueHas),
    subjectType = Some(KA.ListValue),
    objectType = Some(KA.ListNode),
    predicates = Seq(
      makeRdfsLabelEn("Hierarchical list value as list node"),
      makeRdfsCommentEn("Represents a reference to a hierarchical list node."),
    ).map(_.build()),
  )

  private val ColorValueAsColor: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.ColorValueAsColor,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    subPropertyOf = Set(KA.ValueHas),
    subjectType = Some(KA.ColorBase),
    objectType = Some(OntologyConstants.Xsd.String),
    predicates = Seq(
      makeRdfsLabelEn("Color value as color"),
      makeRdfsCommentEn("Represents the literal RGB value of a ColorValue."),
    ).map(_.build()),
  )

  private val UriValueAsUri: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.UriValueAsUri,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    subPropertyOf = Set(KA.ValueHas),
    subjectType = Some(KA.UriBase),
    objectType = Some(OntologyConstants.Xsd.Uri),
    predicates = Seq(
      makeRdfsLabelEn("URI value as URI"),
      makeRdfsCommentEn("Represents the literal URI value of a UriValue."),
    ).map(_.build()),
  )

  private val GeonameValueAsGeonameCode: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.GeonameValueAsGeonameCode,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    subPropertyOf = Set(KA.ValueHas),
    subjectType = Some(KA.GeonameValue),
    objectType = Some(OntologyConstants.Xsd.String),
    predicates = Seq(
      makeRdfsLabelEn("Geoname value as Geoname code"),
      makeRdfsCommentEn("Represents the literal Geoname code of a GeonameValue."),
    ).map(_.build()),
  )

  private val FileValueAsUrl: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.FileValueAsUrl,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    subPropertyOf = Set(KA.ValueHas),
    subjectType = Some(KA.FileValue),
    objectType = Some(OntologyConstants.Xsd.Uri),
    predicates = Seq(
      makeRdfsLabelEn("File value as URL"),
      makeRdfsCommentEn("The URL at which the file can be accessed."),
    ).map(_.build()),
  )

  private val FileValueHasFilename: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.FileValueHasFilename,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    subPropertyOf = Set(KA.ValueHas),
    subjectType = Some(KA.FileValue),
    objectType = Some(OntologyConstants.Xsd.String),
    predicates = Seq(
      makeRdfsLabelEn("File value has filename"),
      makeRdfsCommentEn("The name of the file that a file value represents."),
    ).map(_.build()),
  )

  private val StillImageFileValueHasDimX: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.StillImageFileValueHasDimX,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    subPropertyOf = Set(KA.ValueHas),
    subjectType = Some(KA.StillImageFileValue),
    objectType = Some(OntologyConstants.Xsd.Integer),
    predicates = Seq(
      makeRdfsLabelEn("Still image file value has X dimension"),
      makeRdfsCommentEn("The horizontal dimension of a still image file value."),
    ).map(_.build()),
  )

  private val StillImageFileValueHasDimY: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.StillImageFileValueHasDimY,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    subPropertyOf = Set(KA.ValueHas),
    subjectType = Some(KA.StillImageFileValue),
    objectType = Some(OntologyConstants.Xsd.Integer),
    predicates = Seq(
      makeRdfsLabelEn("Still image file value has Y dimension"),
      makeRdfsCommentEn("The vertical dimension of a still image file value."),
    ).map(_.build()),
  )

  private val StillImageFileValueHasIIIFBaseUrl: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.StillImageFileValueHasIIIFBaseUrl,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    subPropertyOf = Set(KA.ValueHas),
    subjectType = Some(KA.StillImageFileValue),
    objectType = Some(OntologyConstants.Xsd.Uri),
    predicates = Seq(
      makeRdfsLabelEn("Still image file value has IIIF base URL"),
      makeRdfsCommentEn("The IIIF base URL of a still image file value."),
    ).map(_.build()),
  )

  private val StillImageFileValueHasExternalUrl: ReadPropertyInfoV2 = makeProperty(
    propertyIri = KA.StillImageFileValueHasExternalUrl,
    propertyType = OntologyConstants.Owl.DatatypeProperty,
    subPropertyOf = Set(KA.ValueHas),
    subjectType = Some(KA.StillImageExternalFileValue),
    objectType = Some(OntologyConstants.Xsd.Uri),
    predicates = Seq(
      makeRdfsLabelEn("External Url to the image"),
      makeRdfsCommentEn("External Url to the image"),
    ).map(_.build()),
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

  final case class PredicateInfoV2Builder private (
    predicateIri: SmartIri,
    objects: Seq[OntologyLiteralV2] = Seq.empty,
  ) {
    self =>
    def withObject(obj: OntologyLiteralV2): PredicateInfoV2Builder =
      copy(objects = self.objects :+ obj)
    def withObjects(objs: Seq[OntologyLiteralV2]): PredicateInfoV2Builder =
      copy(objects = self.objects ++ objs)
    def withStringLiteral(lang: LanguageCode, value: String): PredicateInfoV2Builder =
      withObject(StringLiteralV2.from(value, Some(lang.code)))
    def withStringLiteral(value: String): PredicateInfoV2Builder =
      withObject(StringLiteralV2.from(value, None))
    def withStringLiterals(literals: Map[LanguageCode, String]): PredicateInfoV2Builder =
      withObjects(literals.map { case (lang, value) => StringLiteralV2.from(value, lang) }.toSeq)
    def build(): PredicateInfoV2 = PredicateInfoV2(self.predicateIri, self.objects)
  }
  object PredicateInfoV2Builder {
    def make(predicateIri: IRI): PredicateInfoV2Builder =
      PredicateInfoV2Builder(predicateIri.toSmartIri)
    def make(predicateIri: Rdf4jIRI): PredicateInfoV2Builder =
      make(predicateIri.toString)

    def makeRdfType(): PredicateInfoV2Builder = make(RDF.TYPE)

    def makeRdfsLabel(literals: Map[LanguageCode, String]): PredicateInfoV2Builder =
      makeRdfsLabel().withStringLiterals(literals)
    def makeRdfsLabelEn(value: String): PredicateInfoV2Builder = makeRdfsLabel(EN, value)
    private def makeRdfsLabel(lang: LanguageCode, value: String): PredicateInfoV2Builder =
      makeRdfsLabel().withStringLiteral(lang, value)
    private def makeRdfsLabel(): PredicateInfoV2Builder = make(RDFS.LABEL)

    def makeRdfsCommentEn(value: String): PredicateInfoV2Builder = makeRdfsComment(EN, value)
    private def makeRdfsComment(lang: LanguageCode, value: String): PredicateInfoV2Builder =
      makeRdfsComment().withStringLiteral(lang, value)
    private def makeRdfsComment(): PredicateInfoV2Builder = make(RDFS.COMMENT)
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
