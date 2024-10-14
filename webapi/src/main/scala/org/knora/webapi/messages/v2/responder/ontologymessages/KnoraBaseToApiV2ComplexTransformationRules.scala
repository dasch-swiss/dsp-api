/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.v2.responder.ontologymessages

import org.eclipse.rdf4j.model.vocabulary.OWL
import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.model.vocabulary.RDFS
import org.eclipse.rdf4j.model.vocabulary.XSD

import org.knora.webapi.*
import org.knora.webapi.LanguageCode.*
import org.knora.webapi.messages.IriConversions.*
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.OntologyConstants.KnoraApiV2Complex as KA
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.v2.responder.ontologymessages.OwlCardinality.*
import org.knora.webapi.messages.v2.responder.ontologymessages.ReadPropertyInfoV2Builder.makeOwlAnnotationProperty
import org.knora.webapi.messages.v2.responder.ontologymessages.ReadPropertyInfoV2Builder.makeOwlDataTypeProperty
import org.knora.webapi.messages.v2.responder.ontologymessages.ReadPropertyInfoV2Builder.makeOwlObjectProperty
import org.knora.webapi.messages.v2.responder.ontologymessages.ReadPropertyInfoV2Builder.makeRdfProperty
import org.knora.webapi.slice.admin.domain.service.KnoraProjectRepo
import org.knora.webapi.slice.ontology.domain.model.Cardinality.*

/**
 * Rules for converting `knora-base` (or an ontology based on it) into `knora-api` in the [[ApiV2Complex]] schema.
 * See also [[OntologyConstants.CorrespondingIris]].
 */
object KnoraBaseToApiV2ComplexTransformationRules extends OntologyTransformationRules {
  private implicit val sf: StringFormatter = StringFormatter.getInstanceForConstantOntologies

  override val ontologyMetadata: OntologyMetadataV2 = OntologyMetadataV2(
    ontologyIri = KA.KnoraApiOntologyIri.toSmartIri,
    projectIri = Some(KnoraProjectRepo.builtIn.SystemProject.id.value.toSmartIri),
    label = Some("The knora-api ontology in the complex schema"),
  )

  private val Label = ReadPropertyInfoV2Builder.make(RDFS.LABEL, OWL.DATATYPEPROPERTY)

  private val Result = makeOwlDataTypeProperty(KA.Result, XSD.STRING)
    .withRdfLabel(
      Map(
        DE -> "Ergebnis",
        EN -> "result",
        FR -> "résultat",
        IT -> "risultato",
      ),
    )
    .withRdfCommentEn("Provides a message indicating that an operation was successful")

  private val Error = makeOwlDataTypeProperty(KA.Error, XSD.STRING)
    .withRdfLabelEn("error")
    .withRdfCommentEn("Provides an error message")

  private val CanDo = makeOwlDataTypeProperty(KA.CanDo, XSD.BOOLEAN)
    .withRdfLabelEn("can do")
    .withRdfCommentEn("Indicates whether an operation can be performed")

  private val MayHaveMoreResults = makeOwlDataTypeProperty(KA.MayHaveMoreResults, XSD.BOOLEAN)
    .withRdfLabelEn("May have more results")
    .withRdfCommentEn("Indicates whether more results may be available for a search query")

  private val UserHasPermission = makeOwlDataTypeProperty(KA.UserHasPermission, XSD.STRING)
    .withRdfLabelEn("user has permission")
    .withRdfCommentEn("Provides the requesting user's maximum permission on a resource or value.")

  private val ArkUrl = makeOwlDataTypeProperty(KA.ArkUrl, XSD.ANYURI)
    .withRdfLabelEn("ARK URL")
    .withRdfCommentEn("Provides the ARK URL of a resource or value.")

  private val VersionArkUrl = makeOwlDataTypeProperty(KA.VersionArkUrl, XSD.ANYURI)
    .withRdfLabelEn("version ARK URL")
    .withRdfCommentEn("Provides the ARK URL of a particular version of a resource or value.")

  private val VersionDate = makeOwlDataTypeProperty(KA.VersionDate, XSD.ANYURI)
    .withRdfLabelEn("version date")
    .withRdfCommentEn("Provides the date of a particular version of a resource.")

  private val Author = makeOwlObjectProperty(KA.Author, KA.User)
    .withRdfLabelEn("author")
    .withRdfCommentEn("Specifies the author of a particular version of a resource.")

  private val IsShared = makeOwlDataTypeProperty(KA.IsShared, XSD.BOOLEAN)
    .withRdfLabelEn("is shared")
    .withRdfCommentEn("Indicates whether an ontology can be shared by multiple projects")

  private val IsBuiltIn = makeOwlDataTypeProperty(KA.IsBuiltIn, XSD.BOOLEAN)
    .withRdfLabelEn("is shared")
    .withRdfCommentEn("Indicates whether an ontology is built into Knora")

  private val IsEditable = makeOwlAnnotationProperty(KA.IsEditable, XSD.BOOLEAN)
    .withSubjectType(RDF.PROPERTY)
    .withRdfLabelEn("is editable")
    .withRdfCommentEn("Indicates whether a property's values can be updated via the Knora API.")

  private val IsResourceClass = makeOwlAnnotationProperty(KA.IsResourceClass, XSD.BOOLEAN)
    .withSubjectType(OWL.CLASS)
    .withRdfLabelEn("is resource class")
    .withRdfCommentEn("Indicates whether class is a subclass of Resource.")

  private val IsValueClass = makeOwlAnnotationProperty(KA.IsValueClass, XSD.BOOLEAN)
    .withSubjectType(OWL.CLASS)
    .withRdfLabelEn("is value class")
    .withRdfCommentEn("Indicates whether class is a subclass of Value.")

  private val IsStandoffClass = makeOwlAnnotationProperty(KA.IsStandoffClass, XSD.BOOLEAN)
    .withSubjectType(OWL.CLASS)
    .withRdfLabelEn("is standoff class")
    .withRdfCommentEn("Indicates whether class is a subclass of StandoffTag.")

  private val IsLinkProperty = makeOwlAnnotationProperty(KA.IsLinkProperty, XSD.BOOLEAN)
    .withSubjectType(OWL.OBJECTPROPERTY)
    .withRdfLabelEn("is link property")
    .withRdfCommentEn("Indicates whether a property points to a resource")

  private val IsLinkValueProperty = makeOwlAnnotationProperty(KA.IsLinkValueProperty, XSD.BOOLEAN)
    .withSubjectType(OWL.OBJECTPROPERTY)
    .withRdfLabelEn("is link value property")
    .withRdfCommentEn("Indicates whether a property points to a link value (reification)")

  private val IsInherited = makeOwlAnnotationProperty(KA.IsInherited, XSD.BOOLEAN)
    .withSubjectType(OWL.RESTRICTION)
    .withRdfLabelEn("is inherited")
    .withRdfCommentEn("Indicates whether a cardinality has been inherited from a base class")

  private val NewModificationDate = makeOwlDataTypeProperty(KA.NewModificationDate, XSD.DATETIMESTAMP)
    .withRdfLabelEn("new modification date")
    .withRdfCommentEn("Specifies the new modification date of a resource")

  private val OntologyName = makeOwlDataTypeProperty(KA.OntologyName, XSD.STRING)
    .withRdfLabelEn("ontology name")
    .withRdfCommentEn("Represents the short name of an ontology")

  private val MappingHasName = makeOwlDataTypeProperty(KA.MappingHasName, XSD.STRING)
    .withRdfLabelEn("Name of a mapping (will be part of the mapping's Iri)")
    .withRdfCommentEn("Represents the name of a mapping")

  private val HasIncomingLinkValue = makeOwlObjectProperty(KA.HasIncomingLinkValue, KA.LinkValue)
    .withRdfCommentEn("Indicates that this resource referred to by another resource")
    .withRdfLabel(
      Map(
        DE -> "hat eingehenden Verweis",
        EN -> "has incoming link",
        FR -> "liens entrants",
      ),
    )
    .withSubjectType(KA.Resource)
    .withSubPropertyOf(KA.HasLinkToValue)
    .withIsResourceProp()
    .withIsLinkValueProp()

  private val ValueAsString = makeOwlDataTypeProperty(KA.ValueAsString, XSD.STRING)
    .withSubjectType(KA.Value)
    .withRdfCommentEn("A plain string representation of a value")

  private val SubjectType = makeRdfProperty(KA.SubjectType)
    .withRdfLabelEn("Subject type")
    .withRdfCommentEn("Specifies the required type of the subjects of a property")

  private val ObjectType = makeRdfProperty(KA.ObjectType)
    .withRdfLabelEn("Object type")
    .withRdfCommentEn("Specifies the required type of the objects of a property")

  private val TextValueHasMarkup = makeOwlDataTypeProperty(KA.TextValueHasMarkup, XSD.BOOLEAN)
    .withSubPropertyOf(KA.ValueHas)
    .withSubjectType(KA.TextValue)
    .withRdfLabelEn("text value has markup")
    .withRdfCommentEn("True if a text value has markup.")

  private val TextValueHasStandoff = makeOwlObjectProperty(KA.TextValueHasStandoff, KA.StandoffTag)
    .withSubPropertyOf(KA.ValueHas)
    .withSubjectType(KA.TextValue)
    .withRdfLabelEn("text value has standoff")
    .withRdfCommentEn("Standoff markup attached to a text value.")

  private val TextValueHasMaxStandoffStartIndex =
    makeOwlDataTypeProperty(KA.TextValueHasMaxStandoffStartIndex, XSD.INTEGER)
      .withSubPropertyOf(KA.ValueHas)
      .withSubjectType(KA.TextValue)
      .withRdfLabelEn("text value has max standoff start index")
      .withRdfCommentEn("The maximum knora-api:standoffTagHasStartIndex in a text value.")

  private val NextStandoffStartIndex = makeOwlDataTypeProperty(KA.NextStandoffStartIndex, XSD.INTEGER)
    .withRdfLabelEn("next standoff start index")
    .withRdfCommentEn("The next available knora-api:standoffTagHasStartIndex in a sequence of pages of standoff.")

  private val StandoffTagHasStartParentIndex = makeOwlDataTypeProperty(KA.StandoffTagHasStartParentIndex, XSD.INTEGER)
    .withSubjectType(KA.StandoffTag)
    .withRdfLabelEn("standoff tag has start parent index")
    .withRdfCommentEn("The next knora-api:standoffTagHasStartIndex of the start parent tag of a standoff tag.")

  private val StandoffTagHasEndParentIndex = makeOwlDataTypeProperty(KA.StandoffTagHasEndParentIndex, XSD.INTEGER)
    .withSubjectType(KA.StandoffTag)
    .withRdfLabelEn("standoff tag has end parent index")
    .withRdfCommentEn("The next knora-api:standoffTagHasStartIndex of the end parent tag of a standoff tag.")

  private val TextValueHasLanguage = makeOwlDataTypeProperty(KA.TextValueHasLanguage, XSD.STRING)
    .withSubPropertyOf(KA.ValueHas)
    .withSubjectType(KA.TextValue)
    .withRdfLabelEn("text value has language")
    .withRdfCommentEn("Language code attached to a text value.")

  private val TextValueAsXml = makeOwlDataTypeProperty(KA.TextValueAsXml, XSD.STRING)
    .withSubPropertyOf(KA.ValueHas)
    .withSubjectType(KA.TextValue)
    .withRdfLabelEn("Text value as XML")
    .withRdfCommentEn("A Text value represented in XML.")

  private val TextValueAsHtml = makeOwlDataTypeProperty(KA.TextValueAsHtml, XSD.STRING)
    .withSubPropertyOf(KA.ValueHas)
    .withSubjectType(KA.TextValue)
    .withRdfLabelEn("Text value as HTML")
    .withRdfCommentEn("A text value represented in HTML.")

  private val TextValueHasMapping = makeOwlObjectProperty(KA.TextValueHasMapping, KA.XMLToStandoffMapping)
    .withSubPropertyOf(KA.ValueHas)
    .withSubjectType(KA.TextValue)
    .withRdfLabelEn("Text value has mapping")
    .withRdfCommentEn("Indicates the mapping that is used to convert a text value's markup from from XML to standoff.")

  private val DateValueHasStartYear = makeOwlDataTypeProperty(KA.DateValueHasStartYear, XSD.INTEGER)
    .withSubPropertyOf(KA.ValueHas)
    .withSubjectType(KA.DateBase)
    .withRdfLabelEn("Date value has start year")
    .withRdfCommentEn("Represents the start year of a date value.")

  private val DateValueHasEndYear = makeOwlDataTypeProperty(KA.DateValueHasEndYear, XSD.INTEGER)
    .withSubPropertyOf(KA.ValueHas)
    .withSubjectType(KA.DateBase)
    .withRdfLabelEn("Date value has end year")
    .withRdfCommentEn("Represents the end year of a date value.")

  private val DateValueHasStartMonth = makeOwlDataTypeProperty(KA.DateValueHasStartMonth, XSD.INTEGER)
    .withSubPropertyOf(KA.ValueHas)
    .withSubjectType(KA.DateBase)
    .withRdfLabelEn("Date value has start month")
    .withRdfCommentEn("Represents the start month of a date value.")

  private val DateValueHasEndMonth = makeOwlDataTypeProperty(KA.DateValueHasEndMonth, XSD.INTEGER)
    .withSubPropertyOf(KA.ValueHas)
    .withSubjectType(KA.DateBase)
    .withRdfLabelEn("Date value has end month")
    .withRdfCommentEn("Represents the end month of a date value.")

  private val DateValueHasStartDay = makeOwlDataTypeProperty(KA.DateValueHasStartDay, XSD.INTEGER)
    .withSubPropertyOf(KA.ValueHas)
    .withSubjectType(KA.DateBase)
    .withRdfLabelEn("Date value has start day")
    .withRdfCommentEn("Represents the start day of a date value.")

  private val DateValueHasEndDay = makeOwlDataTypeProperty(KA.DateValueHasEndDay, XSD.INTEGER)
    .withSubPropertyOf(KA.ValueHas)
    .withSubjectType(KA.DateBase)
    .withRdfLabelEn("Date value has end day")
    .withRdfCommentEn("Represents the end day of a date value.")

  private val DateValueHasStartEra = makeOwlDataTypeProperty(KA.DateValueHasStartEra, XSD.STRING)
    .withSubPropertyOf(KA.ValueHas)
    .withSubjectType(KA.DateBase)
    .withRdfLabelEn("Date value has start era")
    .withRdfCommentEn("Represents the start era of a date value.")

  private val DateValueHasEndEra = makeOwlDataTypeProperty(KA.DateValueHasEndEra, XSD.STRING)
    .withSubPropertyOf(KA.ValueHas)
    .withSubjectType(KA.DateBase)
    .withRdfLabelEn("Date value has end era")
    .withRdfCommentEn("Represents the end era of a date value.")

  private val DateValueHasCalendar = makeOwlDataTypeProperty(KA.DateValueHasCalendar, XSD.STRING)
    .withSubPropertyOf(KA.ValueHas)
    .withSubjectType(KA.DateBase)
    .withRdfLabelEn("Date value has calendar")
    .withRdfCommentEn("Represents the calendar of a date value.")

  private val LinkValueHasSource = makeOwlObjectProperty(KA.LinkValueHasSource, KA.Resource)
    .withSubPropertyOf(KA.ValueHas)
    .withSubjectType(KA.LinkValue)
    .withRdfLabelEn("Link value has source")
    .withRdfCommentEn("Represents the source resource of a link value.")

  private val LinkValueHasSourceIri = makeOwlDataTypeProperty(KA.LinkValueHasSourceIri, XSD.ANYURI)
    .withSubPropertyOf(KA.ValueHas)
    .withSubjectType(KA.LinkValue)
    .withRdfLabelEn("Link value has source IRI")
    .withRdfCommentEn("Represents the IRI of the source resource of a link value.")

  private val LinkValueHasTarget = makeOwlObjectProperty(KA.LinkValueHasTarget, KA.Resource)
    .withSubPropertyOf(KA.ValueHas)
    .withSubjectType(KA.LinkValue)
    .withRdfLabelEn("Link value has target")
    .withRdfCommentEn("Represents the target resource of a link value.")

  private val LinkValueHasTargetIri = makeOwlDataTypeProperty(KA.LinkValueHasTargetIri, XSD.ANYURI)
    .withSubPropertyOf(KA.ValueHas)
    .withSubjectType(KA.LinkValue)
    .withRdfLabelEn("Link value has target IRI")
    .withRdfCommentEn("Represents the IRI of the target resource of a link value.")

  private val IntValueAsInt = makeOwlDataTypeProperty(KA.IntValueAsInt, XSD.INTEGER)
    .withSubPropertyOf(KA.ValueHas)
    .withSubjectType(KA.IntBase)
    .withRdfLabelEn("Integer value as integer")
    .withRdfCommentEn("Represents the literal integer value of an IntValue.")

  private val DecimalValueAsDecimal = makeOwlDataTypeProperty(KA.DecimalValueAsDecimal, XSD.DECIMAL)
    .withSubPropertyOf(KA.ValueHas)
    .withSubjectType(KA.DecimalBase)
    .withRdfLabelEn("Decimal value as decimal")
    .withRdfCommentEn("Represents the literal decimal value of a DecimalValue.")

  private val IntervalValueHasStart = makeOwlDataTypeProperty(KA.IntervalValueHasStart, XSD.DECIMAL)
    .withSubPropertyOf(KA.ValueHas)
    .withSubjectType(KA.IntervalBase)
    .withRdfLabelEn("interval value has start")
    .withRdfCommentEn("Represents the start position of an interval.")

  private val IntervalValueHasEnd = makeOwlDataTypeProperty(KA.IntervalValueHasEnd, XSD.DECIMAL)
    .withSubPropertyOf(KA.ValueHas)
    .withSubjectType(KA.IntervalBase)
    .withRdfLabelEn("interval value has end")
    .withRdfCommentEn("Represents the end position of an interval.")

  private val BooleanValueAsBoolean = makeOwlDataTypeProperty(KA.BooleanValueAsBoolean, XSD.BOOLEAN)
    .withSubPropertyOf(KA.ValueHas)
    .withSubjectType(KA.BooleanBase)
    .withRdfLabelEn("Boolean value as decimal")
    .withRdfCommentEn("Represents the literal boolean value of a BooleanValue.")

  private val GeometryValueAsGeometry = makeOwlDataTypeProperty(KA.GeometryValueAsGeometry, XSD.STRING)
    .withSubPropertyOf(KA.ValueHas)
    .withSubjectType(KA.GeomValue)
    .withRdfLabelEn("Geometry value as JSON")
    .withRdfCommentEn("Represents a 2D geometry value as JSON.")

  private val ListValueAsListNode = makeOwlObjectProperty(KA.ListValueAsListNode, KA.ListNode)
    .withSubPropertyOf(KA.ValueHas)
    .withSubjectType(KA.ListValue)
    .withRdfLabelEn("Hierarchical list value as list node")
    .withRdfCommentEn("Represents a reference to a hierarchical list node.")

  private val ColorValueAsColor = makeOwlDataTypeProperty(KA.ColorValueAsColor, XSD.STRING)
    .withSubPropertyOf(KA.ValueHas)
    .withSubjectType(KA.ColorBase)
    .withRdfLabelEn("Color value as color")
    .withRdfCommentEn("Represents the literal RGB value of a ColorValue.")

  private val UriValueAsUri = makeOwlDataTypeProperty(KA.UriValueAsUri, XSD.ANYURI)
    .withSubPropertyOf(KA.ValueHas)
    .withSubjectType(KA.UriBase)
    .withRdfLabelEn("URI value as URI")
    .withRdfCommentEn("Represents the literal URI value of a UriValue.")

  private val GeonameValueAsGeonameCode = makeOwlDataTypeProperty(KA.GeonameValueAsGeonameCode, XSD.STRING)
    .withSubPropertyOf(KA.ValueHas)
    .withSubjectType(KA.GeonameValue)
    .withRdfLabelEn("Geoname value as Geoname code")
    .withRdfCommentEn("Represents the literal Geoname code of a GeonameValue.")

  private val FileValueAsUrl = makeOwlDataTypeProperty(KA.FileValueAsUrl, XSD.ANYURI)
    .withSubPropertyOf(KA.ValueHas)
    .withSubjectType(KA.FileValue)
    .withRdfLabelEn("File value as URL")
    .withRdfCommentEn("The URL at which the file can be accessed.")

  private val FileValueHasFilename = makeOwlDataTypeProperty(KA.FileValueHasFilename, XSD.STRING)
    .withSubPropertyOf(KA.ValueHas)
    .withSubjectType(KA.FileValue)
    .withRdfLabelEn("File value has filename")
    .withRdfCommentEn("The name of the file that a file value represents.")

  private val StillImageFileValueHasDimX = makeOwlDataTypeProperty(KA.StillImageFileValueHasDimX, XSD.INTEGER)
    .withSubPropertyOf(KA.ValueHas)
    .withSubjectType(KA.StillImageFileValue)
    .withRdfLabelEn("Still image file value has X dimension")
    .withRdfCommentEn("The horizontal dimension of a still image file value.")

  private val StillImageFileValueHasDimY = makeOwlDataTypeProperty(KA.StillImageFileValueHasDimY, XSD.INTEGER)
    .withSubPropertyOf(KA.ValueHas)
    .withSubjectType(KA.StillImageFileValue)
    .withRdfLabelEn("Still image file value has Y dimension")
    .withRdfCommentEn("The vertical dimension of a still image file value.")

  private val StillImageFileValueHasIIIFBaseUrl =
    makeOwlDataTypeProperty(KA.StillImageFileValueHasIIIFBaseUrl, XSD.ANYURI)
      .withSubPropertyOf(KA.ValueHas)
      .withSubjectType(KA.StillImageFileValue)
      .withRdfLabelEn("Still image file value has IIIF base URL")
      .withRdfCommentEn("The IIIF base URL of a still image file value.")

  private val StillImageFileValueHasExternalUrl =
    makeOwlDataTypeProperty(KA.StillImageFileValueHasExternalUrl, XSD.ANYURI)
      .withSubPropertyOf(KA.ValueHas)
      .withSubjectType(KA.StillImageExternalFileValue)
      .withRdfLabelEn("External Url to the image")
      .withRdfCommentEn("External Url to the image")

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
    RDF.SUBJECT.toString,
    RDF.PREDICATE.toString,
    RDF.OBJECT.toString,
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
  ).map(_.build()).map(info => info.entityInfoContent.propertyIri -> info).toMap
}
