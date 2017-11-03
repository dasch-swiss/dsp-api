/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and Sepideh Alassi.
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

package org.knora.webapi

/**
  * Contains string constants for IRIs from ontologies used by the application.
  */
object OntologyConstants {

    object Rdf {
        val RdfPrefixExpansion: IRI = "http://www.w3.org/1999/02/22-rdf-syntax-ns#"

        val Type: IRI = RdfPrefixExpansion + "type"
        val Subject: IRI = RdfPrefixExpansion + "subject"
        val Predicate: IRI = RdfPrefixExpansion + "predicate"
        val Object: IRI = RdfPrefixExpansion + "object"
        val Property: IRI = RdfPrefixExpansion + "Property"
    }

    object Rdfs {
        val RdfsPrefixExpansion = "http://www.w3.org/2000/01/rdf-schema#"

        val Label: IRI = RdfsPrefixExpansion + "label"
        val Comment: IRI = RdfsPrefixExpansion + "comment"
        val SubClassOf: IRI = RdfsPrefixExpansion + "subClassOf"
        val SubPropertyOf: IRI = RdfsPrefixExpansion + "subPropertyOf"
        val Datatype: IRI = RdfsPrefixExpansion + "Datatype"
    }

    object Owl {
        val OwlPrefixExpansion: IRI = "http://www.w3.org/2002/07/owl#"

        val Restriction: IRI = OwlPrefixExpansion + "Restriction"
        val OnProperty: IRI = OwlPrefixExpansion + "onProperty"
        val Cardinality: IRI = OwlPrefixExpansion + "cardinality"
        val MinCardinality: IRI = OwlPrefixExpansion + "minCardinality"
        val MaxCardinality: IRI = OwlPrefixExpansion + "maxCardinality"

        val ObjectProperty: IRI = OwlPrefixExpansion + "ObjectProperty"
        val DatatypeProperty: IRI = OwlPrefixExpansion + "DatatypeProperty"
        val AnnotationProperty: IRI = OwlPrefixExpansion + "AnnotationProperty"

        val Class: IRI = OwlPrefixExpansion + "Class"

        val WithRestrictions: IRI = OwlPrefixExpansion + "withRestrictions"
        val OnDatatype: IRI = OwlPrefixExpansion + "onDatatype"

        /**
          * Cardinality IRIs expressed as OWL restrictions, which specify the properties that resources of
          * a particular type can have.
          */
        val cardinalityOWLRestrictions = Set(
            Cardinality,
            MinCardinality,
            MaxCardinality
        )
    }

    object Xsd {
        val XsdPrefixExpansion: IRI = "http://www.w3.org/2001/XMLSchema#"

        val String: IRI = XsdPrefixExpansion + "string"
        val Boolean: IRI = XsdPrefixExpansion + "boolean"
        val Integer: IRI = XsdPrefixExpansion + "integer"
        val Decimal: IRI = XsdPrefixExpansion + "decimal"
        val Uri: IRI = XsdPrefixExpansion + "anyURI"
        val Pattern: IRI = XsdPrefixExpansion + "pattern"
        val DateTimeStamp: IRI = XsdPrefixExpansion + "dateTimeStamp"
    }

    object SchemaOrg {
        val SchemaOrgPrefixExpansion: IRI = "http://schema.org/"

        val Name: IRI = SchemaOrgPrefixExpansion + "name"
        val Thing: IRI = SchemaOrgPrefixExpansion + "Thing"
    }

    object KnoraInternal {
        // The start and end of an internal Knora ontology IRI.
        val InternalOntologyStart = "http://www.knora.org/ontology/"
    }

    /**
      * Ontology labels that are reserved for built-in ontologies.
      */
    val BuiltInOntologyLabels = Set(
        KnoraBase.KnoraBaseOntologyLabel,
        KnoraApi.KnoraApiOntologyLabel,
        SalsahGui.SalsahGuiOntologyLabel,
        Standoff.StandoffOntologyLabel,
        Dc.DcOntologyLabel
    )

    /**
      * IRIs of ontologies that we serve from Scala constants, rather than from the triplestore.
      */
    val ConstantOntologies = Set(
        KnoraApiV2Simple.KnoraApiOntologyIri,
        KnoraApiV2WithValueObjects.KnoraApiOntologyIri
    )

    object KnoraBase {
        val KnoraBaseOntologyLabel: String = "knora-base"
        val KnoraBaseOntologyIri: IRI = KnoraInternal.InternalOntologyStart + KnoraBaseOntologyLabel

        val KnoraBasePrefix: String = KnoraBaseOntologyLabel + ":"
        val KnoraBasePrefixExpansion: IRI = KnoraBaseOntologyIri + "#"

        val Resource: IRI = KnoraBasePrefixExpansion + "Resource"
        val ExternalResource: IRI = KnoraBasePrefixExpansion + "ExternalResource"
        val Representation: IRI = KnoraBasePrefixExpansion + "Representation"
        val AudioRepresentation: IRI = KnoraBasePrefixExpansion + "AudioRepresentation"
        val DDDRepresentation: IRI = KnoraBasePrefixExpansion + "DDDrepresentation"
        val DocumentRepresentation: IRI = KnoraBasePrefixExpansion + "DocumentRepresentation"
        val MovingImageRepresentation: IRI = KnoraBasePrefixExpansion + "MovingImageRepresentation"
        val StillImageRepresentation: IRI = KnoraBasePrefixExpansion + "StillImageRepresentation"
        val TextRepresentation: IRI = KnoraBasePrefixExpansion + "TextRepresentation"

        val XMLToStandoffMapping: IRI = KnoraBasePrefixExpansion + "XMLToStandoffMapping"
        val MappingElement: IRI = KnoraBasePrefixExpansion + "MappingElement"
        val mappingHasStandoffClass: IRI = KnoraBasePrefixExpansion + "mappingHasStandoffClass"
        val mappingHasStandoffProperty: IRI = KnoraBasePrefixExpansion + "mappingHasStandoffProperty"
        val mappingHasXMLClass: IRI = KnoraBasePrefixExpansion + "mappingHasXMLClass"
        val mappingHasXMLNamespace: IRI = KnoraBasePrefixExpansion + "mappingHasXMLNamespace"
        val mappingHasXMLTagname: IRI = KnoraBasePrefixExpansion + "mappingHasXMLTagname"
        val mappingHasXMLAttribute: IRI = KnoraBasePrefixExpansion + "mappingHasXMLAttribute"
        val mappingHasXMLAttributename: IRI = KnoraBasePrefixExpansion + "mappingHasXMLAttributename"
        val mappingHasStandoffDataTypeClass: IRI = KnoraBasePrefixExpansion + "mappingHasStandoffDataTypeClass"
        val mappingElementRequiresSeparator: IRI = KnoraBasePrefixExpansion + "mappingElementRequiresSeparator"

        val XSLTransformation: IRI = KnoraBasePrefixExpansion + "XSLTransformation"
        val mappingHasDefaultXSLTransformation: IRI = KnoraBasePrefixExpansion + "mappingHasDefaultXSLTransformation"

        val IsMainResource: IRI = KnoraBasePrefixExpansion + "isMainResource"

        val AbstractResourceClasses = Set(
            Resource,
            ExternalResource,
            Representation,
            AudioRepresentation,
            DDDRepresentation,
            DocumentRepresentation,
            MovingImageRepresentation,
            StillImageRepresentation,
            TextRepresentation
        )

        val SubjectClassConstraint: IRI = KnoraBasePrefixExpansion + "subjectClassConstraint"
        val ObjectClassConstraint: IRI = KnoraBasePrefixExpansion + "objectClassConstraint"
        val ObjectDatatypeConstraint: IRI = KnoraBasePrefixExpansion + "objectDatatypeConstraint"

        val LinkObj: IRI = KnoraBasePrefixExpansion + "LinkObj"
        val HasLinkTo: IRI = KnoraBasePrefixExpansion + "hasLinkTo"
        val HasLinkToValue: IRI = KnoraBasePrefixExpansion + "hasLinkToValue"
        val Region: IRI = KnoraBasePrefixExpansion + "Region"
        val IsRegionOf: IRI = KnoraBasePrefixExpansion + "isRegionOf"

        val Value: IRI = KnoraBasePrefixExpansion + "Value"
        val ValueHas: IRI = KnoraBasePrefixExpansion + "valueHas"
        val ObjectCannotBeMarkedAsDeleted: IRI = KnoraBasePrefixExpansion + "objectCannotBeMarkedAsDeleted"

        val ValueHasString: IRI = KnoraBasePrefixExpansion + "valueHasString"
        val ValueHasMapping: IRI = KnoraBasePrefixExpansion + "valueHasMapping"
        val ValueHasInteger: IRI = KnoraBasePrefixExpansion + "valueHasInteger"
        val ValueHasDecimal: IRI = KnoraBasePrefixExpansion + "valueHasDecimal"
        val ValueHasStandoff: IRI = KnoraBasePrefixExpansion + "valueHasStandoff"
        val ValueHasResPtr: IRI = KnoraBasePrefixExpansion + "valueHasResPtr"
        val ValueHasStartJDN: IRI = KnoraBasePrefixExpansion + "valueHasStartJDN"
        val ValueHasEndJDN: IRI = KnoraBasePrefixExpansion + "valueHasEndJDN"
        val ValueHasCalendar: IRI = KnoraBasePrefixExpansion + "valueHasCalendar"
        val ValueHasStartPrecision: IRI = KnoraBasePrefixExpansion + "valueHasStartPrecision"
        val ValueHasEndPrecision: IRI = KnoraBasePrefixExpansion + "valueHasEndPrecision"
        val ValueHasBoolean: IRI = KnoraBasePrefixExpansion + "valueHasBoolean"
        val ValueHasUri: IRI = KnoraBasePrefixExpansion + "valueHasUri"
        val ValueHasColor: IRI = KnoraBasePrefixExpansion + "valueHasColor"
        val ValueHasGeometry: IRI = KnoraBasePrefixExpansion + "valueHasGeometry"
        val ValueHasListNode: IRI = KnoraBasePrefixExpansion + "valueHasListNode"
        val ValueHasIntervalStart: IRI = KnoraBasePrefixExpansion + "valueHasIntervalStart"
        val ValueHasIntervalEnd: IRI = KnoraBasePrefixExpansion + "valueHasIntervalEnd"
        val ValueHasOrder: IRI = KnoraBasePrefixExpansion + "valueHasOrder"
        val ValueHasRefCount: IRI = KnoraBasePrefixExpansion + "valueHasRefCount"
        val ValueHasComment: IRI = KnoraBasePrefixExpansion + "valueHasComment"
        val ValueHasGeonameCode: IRI = KnoraBasePrefixExpansion + "valueHasGeonameCode"

        val PreviousValue: IRI = KnoraBasePrefixExpansion + "previousValue"

        val ResourceProperty: IRI = KnoraBasePrefixExpansion + "resourceProperty"
        val HasValue: IRI = KnoraBasePrefixExpansion + "hasValue"
        val HasFileValue: IRI = KnoraBasePrefixExpansion + "hasFileValue"
        val HasStillImageFileValue: IRI = KnoraBasePrefixExpansion + "hasStillImageFileValue"
        val HasMovingImageFileValue: IRI = KnoraBasePrefixExpansion + "hasMovingImageFileValue"
        val HasAudioFileValue: IRI = KnoraBasePrefixExpansion + "hasAudioFileValue"
        val HasDDDFileValue: IRI = KnoraBasePrefixExpansion + "hasDDDFileValue"
        val HasTextFileValue: IRI = KnoraBasePrefixExpansion + "hasTextFileValue"
        val HasDocumentFileValue: IRI = KnoraBasePrefixExpansion + "hasDocumentFileValue"
        val HasComment: IRI = KnoraBasePrefixExpansion + "hasComment"

        val IsPreview: IRI = KnoraBasePrefixExpansion + "isPreview"
        val ResourceIcon: IRI = KnoraBasePrefixExpansion + "resourceIcon"

        val InternalMimeType: IRI = KnoraBasePrefixExpansion + "internalMimeType"
        val InternalFilename: IRI = KnoraBasePrefixExpansion + "internalFilename"
        val OriginalFilename: IRI = KnoraBasePrefixExpansion + "originalFilename"
        val OriginalMimeType: IRI = KnoraBasePrefixExpansion + "originalMimeType"
        val DimX: IRI = KnoraBasePrefixExpansion + "dimX"
        val DimY: IRI = KnoraBasePrefixExpansion + "dimY"
        val QualityLevel: IRI = KnoraBasePrefixExpansion + "qualityLevel"

        val TextValue: IRI = KnoraBasePrefixExpansion + "TextValue"
        val IntValue: IRI = KnoraBasePrefixExpansion + "IntValue"
        val BooleanValue: IRI = KnoraBasePrefixExpansion + "BooleanValue"
        val UriValue: IRI = KnoraBasePrefixExpansion + "UriValue"
        val DecimalValue: IRI = KnoraBasePrefixExpansion + "DecimalValue"
        val DateValue: IRI = KnoraBasePrefixExpansion + "DateValue"
        val Date: IRI = KnoraBasePrefixExpansion + "Date" // TODO: find a better solution for v2 simple
        val ColorValue: IRI = KnoraBasePrefixExpansion + "ColorValue"
        val Color: IRI = KnoraBasePrefixExpansion + "Color" // TODO: find a better solution for v2 simple
        val GeomValue: IRI = KnoraBasePrefixExpansion + "GeomValue"
        val Geom: IRI = KnoraBasePrefixExpansion + "Geom" // TODO: find a better solution for v2 simple
        val ListValue: IRI = KnoraBasePrefixExpansion + "ListValue"
        val IntervalValue: IRI = KnoraBasePrefixExpansion + "IntervalValue"
        val LinkValue: IRI = KnoraBasePrefixExpansion + "LinkValue"
        val GeonameValue: IRI = KnoraBasePrefixExpansion + "GeonameValue"
        val FileValue: IRI = KnoraBasePrefixExpansion + "FileValue"
        val AudioFileValue: IRI = KnoraBasePrefixExpansion + "AudioFileValue"
        val DDDFileValue: IRI = KnoraBasePrefixExpansion + "DDDFileValue"
        val DocumentFileValue: IRI = KnoraBasePrefixExpansion + "DocumentFileValue"
        val StillImageFileValue: IRI = KnoraBasePrefixExpansion + "StillImageFileValue"
        val StillImageFile: IRI = KnoraBasePrefixExpansion + "StillImageFile" // TODO: find a better solution for v2 simple
        val MovingImageFileValue: IRI = KnoraBasePrefixExpansion + "MovingImageFileValue"
        val TextFileValue: IRI = KnoraBasePrefixExpansion + "TextFileValue"

        val ValueClasses: Set[IRI] = Set(
            TextValue,
            IntValue,
            BooleanValue,
            UriValue,
            DecimalValue,
            DateValue,
            ColorValue,
            GeomValue,
            ListValue,
            IntervalValue,
            LinkValue,
            GeonameValue,
            FileValue,
            AudioFileValue,
            DDDFileValue,
            DocumentFileValue,
            StillImageFileValue,
            MovingImageFileValue,
            TextFileValue
        )

        val ListNode: IRI = KnoraBasePrefixExpansion + "ListNode"

        val IsDeleted: IRI = KnoraBasePrefixExpansion + "isDeleted"

        /* Resource creator */
        val AttachedToUser: IRI = KnoraBasePrefixExpansion + "attachedToUser"

        /* Resource's project */
        val AttachedToProject: IRI = KnoraBasePrefixExpansion + "attachedToProject"

        /* User */
        val User: IRI = KnoraBasePrefixExpansion + "User"
        val Email: IRI = KnoraBasePrefixExpansion + "email"
        val GivenName: IRI = KnoraBasePrefixExpansion + "givenName"
        val FamilyName: IRI = KnoraBasePrefixExpansion + "familyName"
        val Password: IRI = KnoraBasePrefixExpansion + "password"
        val UsersActiveProject: IRI = KnoraBasePrefixExpansion + "currentproject"
        val Status: IRI = KnoraBasePrefixExpansion + "status"
        val PreferredLanguage: IRI = KnoraBasePrefixExpansion + "preferredLanguage"
        val IsInProject: IRI = KnoraBasePrefixExpansion + "isInProject"
        val IsInProjectAdminGroup: IRI = KnoraBasePrefixExpansion + "isInProjectAdminGroup"
        val IsInGroup: IRI = KnoraBasePrefixExpansion + "isInGroup"
        val IsInSystemAdminGroup: IRI = KnoraBasePrefixExpansion + "isInSystemAdminGroup"

        /* Project */
        val KnoraProject: IRI = KnoraBasePrefixExpansion + "knoraProject"
        val ProjectShortname: IRI = KnoraBasePrefixExpansion + "projectShortname"
        val ProjectLongname: IRI = KnoraBasePrefixExpansion + "projectLongname"
        val ProjectDescription: IRI = KnoraBasePrefixExpansion + "projectDescription"
        val ProjectKeywords: IRI = KnoraBasePrefixExpansion + "projectKeywords"
        val ProjectLogo: IRI = KnoraBasePrefixExpansion + "projectLogo"
        val BelongsToInstitution: IRI = KnoraBasePrefixExpansion + "belongsToInstitution"
        val ProjectOntologyGraph: IRI = KnoraBasePrefixExpansion + "projectOntologyGraph"
        val ProjectDataGraph: IRI = KnoraBasePrefixExpansion + "projectDataGraph"
        val HasSelfJoinEnabled: IRI = KnoraBasePrefixExpansion + "hasSelfJoinEnabled"

        /* Group */
        val UserGroup: IRI = KnoraBasePrefixExpansion + "UserGroup"
        val GroupName: IRI = KnoraBasePrefixExpansion + "groupName"
        val GroupDescription: IRI = KnoraBasePrefixExpansion + "groupDescription"
        val BelongsToProject: IRI = KnoraBasePrefixExpansion + "belongsToProject"

        /* Built-In Groups */
        val UnknownUser: IRI = KnoraBasePrefixExpansion + "UnknownUser"
        val KnownUser: IRI = KnoraBasePrefixExpansion + "KnownUser"
        val ProjectMember: IRI = KnoraBasePrefixExpansion + "ProjectMember"
        val Creator: IRI = KnoraBasePrefixExpansion + "Creator"
        val SystemAdmin: IRI = KnoraBasePrefixExpansion + "SystemAdmin"
        val ProjectAdmin: IRI = KnoraBasePrefixExpansion + "ProjectAdmin"

        /* Institution */
        val Institution: IRI = KnoraBasePrefixExpansion + "Institution"

        /* Permissions */
        val HasPermissions: IRI = KnoraBasePrefixExpansion + "hasPermissions"

        val PermissionListDelimiter: Char = '|'
        val GroupListDelimiter: Char = ','

        val RestrictedViewPermission: String = "RV"
        val ViewPermission: String = "V"
        val ModifyPermission: String = "M"
        val DeletePermission: String = "D"
        val ChangeRightsPermission: String = "CR"
        val MaxPermission: String = ChangeRightsPermission

        val ObjectAccessPermissionAbbreviations: Seq[String] = Seq(
            RestrictedViewPermission,
            ViewPermission,
            ModifyPermission,
            DeletePermission,
            ChangeRightsPermission
        )

        val ProjectResourceCreateAllPermission: String = "ProjectResourceCreateAllPermission"
        val ProjectResourceCreateRestrictedPermission: String = "ProjectResourceCreateRestrictedPermission"
        val ProjectAdminAllPermission: String = "ProjectAdminAllPermission"
        val ProjectAdminGroupAllPermission: String = "ProjectAdminGroupAllPermission"
        val ProjectAdminGroupRestrictedPermission: String = "ProjectAdminGroupRestrictedPermission"
        val ProjectAdminRightsAllPermission: String = "ProjectAdminRightsAllPermission"
        val ProjectAdminOntologyAllPermission: String = "ProjectAdminOntologyAllPermission"

        val AdministrativePermissionAbbreviations: Seq[String] = Seq(
            ProjectResourceCreateAllPermission,
            ProjectResourceCreateRestrictedPermission,
            ProjectAdminAllPermission,
            ProjectAdminGroupAllPermission,
            ProjectAdminGroupRestrictedPermission,
            ProjectAdminRightsAllPermission,
            ProjectAdminOntologyAllPermission
        )

        val HasDefaultRestrictedViewPermission: IRI = KnoraBasePrefixExpansion + "hasDefaultRestrictedViewPermission"
        val HasDefaultViewPermission: IRI = KnoraBasePrefixExpansion + "hasDefaultViewPermission"
        val HasDefaultModifyPermission: IRI = KnoraBasePrefixExpansion + "hasDefaultModifyPermission"
        val HasDefaultDeletePermission: IRI = KnoraBasePrefixExpansion + "hasDefaultDeletePermission"
        val HasDefaultChangeRightsPermission: IRI = KnoraBasePrefixExpansion + "hasDefaultChangeRightsPermission"

        val DefaultPermissionProperties: Set[IRI] = Set(
            HasDefaultRestrictedViewPermission,
            HasDefaultViewPermission,
            HasDefaultModifyPermission,
            HasDefaultDeletePermission,
            HasDefaultChangeRightsPermission
        )

        /* Standoff */

        val StandoffTagHasStart: IRI = KnoraBasePrefixExpansion + "standoffTagHasStart"
        val StandoffTagHasEnd: IRI = KnoraBasePrefixExpansion + "standoffTagHasEnd"
        val StandoffTagHasStartIndex: IRI = KnoraBasePrefixExpansion + "standoffTagHasStartIndex"
        val StandoffTagHasEndIndex: IRI = KnoraBasePrefixExpansion + "standoffTagHasEndIndex"
        val StandoffTagHasStartParent: IRI = KnoraBasePrefixExpansion + "standoffTagHasStartParent"
        val StandoffTagHasEndParent: IRI = KnoraBasePrefixExpansion + "standoffTagHasEndParent"
        val StandoffTagHasUUID: IRI = KnoraBasePrefixExpansion + "standoffTagHasUUID"
        val StandoffTagHasOriginalXMLID: IRI = KnoraBasePrefixExpansion + "standoffTagHasOriginalXMLID"
        val StandoffTagHasInternalReference: IRI = KnoraBasePrefixExpansion + "standoffTagHasInternalReference"

        val StandoffTagHasLink: IRI = KnoraBasePrefixExpansion + "standoffTagHasLink"
        val HasStandoffLinkTo: IRI = KnoraBasePrefixExpansion + "hasStandoffLinkTo"
        val HasStandoffLinkToValue: IRI = KnoraBasePrefixExpansion + "hasStandoffLinkToValue"

        val StandoffDateTag: IRI = KnoraBasePrefixExpansion + "StandoffDateTag"
        val StandoffColorTag: IRI = KnoraBasePrefixExpansion + "StandoffColorTag"
        val StandoffIntegerTag: IRI = KnoraBasePrefixExpansion + "StandoffIntegerTag"
        val StandoffDecimalTag: IRI = KnoraBasePrefixExpansion + "StandoffDecimalTag"
        val StandoffIntervalTag: IRI = KnoraBasePrefixExpansion + "StandoffIntervalTag"
        val StandoffBooleanTag: IRI = KnoraBasePrefixExpansion + "StandoffBooleanTag"
        val StandoffLinkTag: IRI = KnoraBasePrefixExpansion + "StandoffLinkTag"
        val StandoffUriTag: IRI = KnoraBasePrefixExpansion + "StandoffUriTag"
        val StandoffInternalReferenceTag: IRI = KnoraBasePrefixExpansion + "StandoffInternalReferenceTag"

        val StandardMapping: IRI = "http://data.knora.org/projects/standoff/mappings/StandardMapping"

        val AdministrativePermission: IRI = KnoraBasePrefixExpansion + "AdministrativePermission"
        val DefaultObjectAccessPermission: IRI = KnoraBasePrefixExpansion + "DefaultObjectAccessPermission"
        val ForProject: IRI = KnoraBasePrefixExpansion + "forProject"
        val ForGroup: IRI = KnoraBasePrefixExpansion + "forGroup"
        val ForResourceClass: IRI = KnoraBasePrefixExpansion + "forResourceClass"
        val ForProperty: IRI = KnoraBasePrefixExpansion + "forProperty"

        val SystemProject: IRI = KnoraBasePrefixExpansion + "SystemProject"

        /**
          * The system user is the owner of objects that are created by the system, rather than directly by the user,
          * such as link values for standoff resource references.
          */
        val SystemUser: IRI = KnoraBasePrefixExpansion + "SystemUser"

        val CreationDate: IRI = KnoraBasePrefixExpansion + "creationDate"
        val ValueCreationDate: IRI = KnoraBasePrefixExpansion + "valueCreationDate"

        val Map: IRI = KnoraBasePrefixExpansion + "Map"
        val MapEntry: IRI = KnoraBasePrefixExpansion + "MapEntry"
        val MapEntryKey: IRI = KnoraBasePrefixExpansion + "mapEntryKey"
        val MapEntryValue: IRI = KnoraBasePrefixExpansion + "mapEntryValue"

        val LastModificationDate: IRI = KnoraBasePrefixExpansion + "lastModificationDate"
    }

    object Standoff {
        val StandoffOntologyLabel: String = "standoff"
        val StandoffPrefixExpansion: IRI = KnoraInternal.InternalOntologyStart + StandoffOntologyLabel + "#"

        val StandoffRootTag: IRI = StandoffPrefixExpansion + "StandoffRootTag"
        val StandoffParagraphTag: IRI = StandoffPrefixExpansion + "StandoffParagraphTag"
        val StandoffItalicTag: IRI = StandoffPrefixExpansion + "StandoffItalicTag"
        val StandoffBoldTag: IRI = StandoffPrefixExpansion + "StandoffBoldTag"
        val StandoffUnderlineTag: IRI = StandoffPrefixExpansion + "StandoffUnderlineTag"
        val StandoffStrikethroughTag: IRI = StandoffPrefixExpansion + "StandoffStrikethroughTag"

        val StandoffHeader1Tag: IRI = StandoffPrefixExpansion + "StandoffHeader1Tag"
        val StandoffHeader2Tag: IRI = StandoffPrefixExpansion + "StandoffHeader2Tag"
        val StandoffHeader3Tag: IRI = StandoffPrefixExpansion + "StandoffHeader3Tag"
        val StandoffHeader4Tag: IRI = StandoffPrefixExpansion + "StandoffHeader4Tag"
        val StandoffHeader5Tag: IRI = StandoffPrefixExpansion + "StandoffHeader5Tag"
        val StandoffHeader6Tag: IRI = StandoffPrefixExpansion + "StandoffHeader6Tag"

        val StandoffSuperscriptTag: IRI = StandoffPrefixExpansion + "StandoffSuperscriptTag"
        val StandoffSubscriptTag: IRI = StandoffPrefixExpansion + "StandoffSubscriptTag"
        val StandoffOrderedListTag: IRI = StandoffPrefixExpansion + "StandoffOrderedListTag"
        val StandoffUnorderedListTag: IRI = StandoffPrefixExpansion + "StandoffUnorderedListTag"
        val StandoffListElementTag: IRI = StandoffPrefixExpansion + "StandoffListElementTag"
        val StandoffStyleElementTag: IRI = StandoffPrefixExpansion + "StandoffStyleTag"
    }

    object SalsahGui {
        val SalsahGuiOntologyLabel: String = "salsah-gui"
        val SalsahGuiOntologyIri: IRI = KnoraInternal.InternalOntologyStart + SalsahGuiOntologyLabel
        val SalsahGuiPrefixExpansion: IRI = SalsahGuiOntologyIri + "#"

        val GuiAttribute: IRI = SalsahGuiPrefixExpansion + "guiAttribute"
        val GuiOrder: IRI = SalsahGuiPrefixExpansion + "guiOrder"
        val GuiElement: IRI = SalsahGuiPrefixExpansion + "guiElement"
        val SimpleText: IRI = SalsahGuiPrefixExpansion + "SimpleText"
        val Textarea: IRI = SalsahGuiPrefixExpansion + "Textarea"
        val Pulldown: IRI = SalsahGuiPrefixExpansion + "Pulldown"
        val Slider: IRI = SalsahGuiPrefixExpansion + "Slider"
        val Spinbox: IRI = SalsahGuiPrefixExpansion + "Spinbox"
        val Searchbox: IRI = SalsahGuiPrefixExpansion + "Searchbox"
        val Date: IRI = SalsahGuiPrefixExpansion + "Date"
        val Geometry: IRI = SalsahGuiPrefixExpansion + "Geometry"
        val Colorpicker: IRI = SalsahGuiPrefixExpansion + "Colorpicker"
        val List: IRI = SalsahGuiPrefixExpansion + "List"
        val Radio: IRI = SalsahGuiPrefixExpansion + "Radio"
        val Checkbox: IRI = SalsahGuiPrefixExpansion + "Checkbox"
        val Richtext: IRI = SalsahGuiPrefixExpansion + "Richtext"
        val Interval: IRI = SalsahGuiPrefixExpansion + "Interval"
        val Geonames: IRI = SalsahGuiPrefixExpansion + "Geonames"
        val Fileupload: IRI = SalsahGuiPrefixExpansion + "Fileupload"

        object attributeNames {
            val resourceClass: String = "restypeid"
            val assignmentOperator: String = "="
        }

    }

    object Dc {
        val DcOntologyLabel: String = "dc"
    }

    object KnoraXmlImportV1 {

        object ProjectSpecificXmlImportNamespace {
            val XmlImportNamespaceStart: String = KnoraApi.ApiOntologyStart
            val XmlImportNamespaceEnd: String = "/xml-import/v1#"
        }

        val KnoraXmlImportNamespacePrefixLabel: String = "knoraXmlImport"
        val KnoraXmlImportNamespaceV1: IRI = KnoraApi.ApiOntologyStart + KnoraXmlImportNamespacePrefixLabel + "/v1#"

        val Resources: IRI = KnoraXmlImportNamespaceV1 + "resources"
    }

    object KnoraApi {
        // The hostname of a Knora API ontology IRI.
        val ApiOntologyHostname: String = "http://api.knora.org"

        // The start and end of a Knora API ontology IRI.
        val ApiOntologyStart: String = ApiOntologyHostname + "/ontology/"

        val KnoraApiOntologyLabel: String = "knora-api"

        val KnoraApiPrefix: String = KnoraApiOntologyLabel + ":"
    }

    object KnoraApiV2WithValueObjects {

        val VersionSegment = "/v2"

        val KnoraApiOntologyIri: IRI = KnoraApi.ApiOntologyStart + KnoraApi.KnoraApiOntologyLabel + VersionSegment

        val KnoraApiV2PrefixExpansion: IRI = KnoraApiOntologyIri + "#"

        val Result: IRI = KnoraApiV2PrefixExpansion + "result"

        val SubjectType: IRI = KnoraApiV2PrefixExpansion + "subjectType"
        val ObjectType: IRI = KnoraApiV2PrefixExpansion + "objectType"

        val HasOntologies: IRI = KnoraApiV2PrefixExpansion + "hasOntologies"

        val HasOntologiesWithClasses: IRI = KnoraApiV2PrefixExpansion + "hasOntologiesWithClasses"

        val BelongsToOntology: IRI = KnoraApiV2PrefixExpansion + "belongsToOntology"

        val IsEditable: IRI = KnoraApiV2PrefixExpansion + "isEditable"
        val CanBeInstantiated: IRI = KnoraApiV2PrefixExpansion + "canBeInstantiated"
        val IsInherited: IRI = KnoraApiV2PrefixExpansion + "isInherited"

        val HasClasses: IRI = KnoraApiV2PrefixExpansion + "hasClasses"
        val HasProperties: IRI = KnoraApiV2PrefixExpansion + "hasProperties"
        val HasStandoffClasses: IRI = KnoraApiV2PrefixExpansion + "hasStandoffClasses"
        val HasStandoffProperties: IRI = KnoraApiV2PrefixExpansion + "hasStandoffProperties"

        val ValueAsString: IRI = KnoraApiV2PrefixExpansion + "valueAsString"
        val ValueCreationDate: IRI = KnoraApiV2PrefixExpansion + "valueCreationDate"

        val AttachedToUser: IRI = KnoraApiV2PrefixExpansion + "attachedToUser"
        val AttachedToProject: IRI = KnoraApiV2PrefixExpansion + "attachedToProject"
        val HasStandoffLinkTo: IRI = KnoraApiV2PrefixExpansion + "hasStandoffLinkTo"
        val HasStandoffLinkToValue: IRI = KnoraApiV2PrefixExpansion + "hasStandoffLinkToValue"
        val HasPermissions: IRI = KnoraApiV2PrefixExpansion + "hasPermissions"
        val CreationDate: IRI = KnoraApiV2PrefixExpansion + "creationDate"
        val LastModificationDate: IRI = KnoraApiV2PrefixExpansion + "lastModificationDate"

        val Resource: IRI = KnoraApiV2PrefixExpansion + "Resource"
        val Region: IRI = KnoraApiV2PrefixExpansion + "Region"
        val Representation: IRI = KnoraApiV2PrefixExpansion + "Representation"
        val StillImageRepresentation: IRI = KnoraApiV2PrefixExpansion + "StillImageRepresentation"
        val MovingImageRepresentation: IRI = KnoraApiV2PrefixExpansion + "MovingImageRepresentation"
        val AudioRepresentation: IRI = KnoraApiV2PrefixExpansion + "AudioRepresentation"
        val DDDRepresentation: IRI = KnoraApiV2PrefixExpansion + "DDDRepresentation"
        val TextRepresentation: IRI = KnoraApiV2PrefixExpansion + "TextRepresentation"
        val DocumentRepresentation: IRI = KnoraApiV2PrefixExpansion + "DocumentRepresentation"
        val XMLToStandoffMapping: IRI = KnoraApiV2PrefixExpansion + "XMLToStandoffMapping"
        val ListNode: IRI = KnoraApiV2PrefixExpansion + "ListNode"

        val Value: IRI = KnoraApiV2PrefixExpansion + "Value"
        val TextValue: IRI = KnoraApiV2PrefixExpansion + "TextValue"
        val IntegerValue: IRI = KnoraApiV2PrefixExpansion + "IntegerValue"
        val DecimalValue: IRI = KnoraApiV2PrefixExpansion + "DecimalValue"
        val BooleanValue: IRI = KnoraApiV2PrefixExpansion + "BooleanValue"
        val DateValue: IRI = KnoraApiV2PrefixExpansion + "DateValue"
        val GeomValue: IRI = KnoraApiV2PrefixExpansion + "GeomValue"
        val IntervalValue: IRI = KnoraApiV2PrefixExpansion + "IntervalValue"
        val LinkValue: IRI = KnoraApiV2PrefixExpansion + "LinkValue"
        val ListValue: IRI = KnoraApiV2PrefixExpansion + "ListValue"
        val UriValue: IRI = KnoraApiV2PrefixExpansion + "UriValue"
        val GeonameValue: IRI = KnoraApiV2PrefixExpansion + "GeonameValue"
        val FileValue: IRI = KnoraApiV2PrefixExpansion + "FileValue"
        val ColorValue: IRI = KnoraApiV2PrefixExpansion + "ColorValue"
        val StillImageFileValue: IRI = KnoraApiV2PrefixExpansion + "StillImageFileValue"
        val MovingImageFileValue: IRI = KnoraApiV2PrefixExpansion + "MovingImageFileValue"
        val AudioFileValue: IRI = KnoraApiV2PrefixExpansion + "AudioFileValue"
        val DDDFileValue: IRI = KnoraApiV2PrefixExpansion + "DDDFileValue"
        val TextFileValue: IRI = KnoraApiV2PrefixExpansion + "TextFileValue"
        val DocumentFileValue: IRI = KnoraApiV2PrefixExpansion + "DocumentFileValue"

        val ResourceProperty: IRI = KnoraApiV2PrefixExpansion + "resourceProperty"
        val HasValue: IRI = KnoraApiV2PrefixExpansion + "hasValue"
        val ValueHas: IRI = KnoraApiV2PrefixExpansion + "valueHas"
        val HasLinkTo: IRI = KnoraApiV2PrefixExpansion + "hasLinkTo"

        val IsRegionOf: IRI = KnoraApiV2PrefixExpansion + "isRegionOf"
        val HasGeometry: IRI = KnoraApiV2PrefixExpansion + "hasGeometry"
        val HasColor: IRI = KnoraApiV2PrefixExpansion + "hasColor"
        val HasComment: IRI = KnoraApiV2PrefixExpansion + "hasComment"
        val HasFileValue: IRI = KnoraApiV2PrefixExpansion + "hasFileValue"
        val HasStillImageFileValue: IRI = KnoraApiV2PrefixExpansion + "hasStillImageFileValue"
        val HasMovingImageFileValue: IRI = KnoraApiV2PrefixExpansion + "hasMovingImageFileValue"
        val HasAudioFileValue: IRI = KnoraApiV2PrefixExpansion + "hasAudioFileValue"
        val HasDDDFileValue: IRI = KnoraApiV2PrefixExpansion + "hasDDDFileValue"
        val HasTextFileValue: IRI = KnoraApiV2PrefixExpansion + "hasTextFileValue"
        val HasDocumentFileValue: IRI = KnoraApiV2PrefixExpansion + "hasDocumentFileValue"

        val DateValueHasStartYear: IRI = KnoraApiV2PrefixExpansion + "dateValueHasStartYear"
        val DateValueHasEndYear: IRI = KnoraApiV2PrefixExpansion + "dateValueHasEndYear"
        val DateValueHasStartMonth: IRI = KnoraApiV2PrefixExpansion + "dateValueHasStartMonth"
        val DateValueHasEndMonth: IRI = KnoraApiV2PrefixExpansion + "dateValueHasEndMonth"
        val DateValueHasStartDay: IRI = KnoraApiV2PrefixExpansion + "dateValueHasStartDay"
        val DateValueHasEndDay: IRI = KnoraApiV2PrefixExpansion + "dateValueHasEndDay"
        val DateValueHasCalendar: IRI = KnoraApiV2PrefixExpansion + "dateValueHasCalendar"

        val TextValueAsHtml: IRI = KnoraApiV2PrefixExpansion + "textValueAsHtml"
        val TextValueAsXml: IRI = KnoraApiV2PrefixExpansion + "textValueAsXml"
        val TextValueHasMapping: IRI = KnoraApiV2PrefixExpansion + "textValueHasMapping"

        val IntegerValueAsInteger: IRI = KnoraApiV2PrefixExpansion + "integerValueAsInteger"

        val DecimalValueAsDecimal: IRI = KnoraApiV2PrefixExpansion + "decimalValueAsDecimal"

        val GeometryValueAsGeometry: IRI = KnoraApiV2PrefixExpansion + "geometryValueAsGeometry"

        val LinkValueHasTarget: IRI = KnoraApiV2PrefixExpansion + "linkValueHasTarget"
        val LinkValueHasTargetIri: IRI = KnoraApiV2PrefixExpansion + "linkValueHasTargetIri"

        val FileValueAsUrl: IRI = KnoraApiV2PrefixExpansion + "fileValueAsUrl"
        val FileValueIsPreview: IRI = KnoraApiV2PrefixExpansion + "fileValueIsPreview"
        val FileValueHasFilename: IRI = KnoraApiV2PrefixExpansion + "fileValueHasFilename"

        val StillImageFileValueHasDimX: IRI = KnoraApiV2PrefixExpansion + "stillImageFileValueHasDimX"
        val StillImageFileValueHasDimY: IRI = KnoraApiV2PrefixExpansion + "stillImageFileValueHasDimY"
        val StillImageFileValueHasIIIFBaseUrl: IRI = KnoraApiV2PrefixExpansion + "stillImageFileValueHasIIIFBaseUrl"

        val MovingImageFileValueHasDimX: IRI = KnoraApiV2PrefixExpansion + "movingImageFileValueHasDimX"
        val MovingImageFileValueHasDimY: IRI = KnoraApiV2PrefixExpansion + "movingImageFileValueHasDimY"
        val MovingImageFileValueHasFps: IRI = KnoraApiV2PrefixExpansion + "movingImageFileValueHasFps"
        val MovingImageFileValueHasQualityLevel: IRI = KnoraApiV2PrefixExpansion + "movingImageFileValueHasQualityLevel"
        val MovingImageFileValueHasDuration: IRI = KnoraApiV2PrefixExpansion + "movingImageFileValueHasDuration"

        val AudioFileValueHasDuration: IRI = KnoraApiV2PrefixExpansion + "audioFileValueHasDuration"

        val IntervalValueHasStart: IRI = KnoraApiV2PrefixExpansion + "intervalValueHasStart"
        val IntervalValueHasEnd: IRI = KnoraApiV2PrefixExpansion + "intervalValueHasEnd"

        val BooleanValueAsBoolean: IRI = KnoraApiV2PrefixExpansion + "booleanValueAsBoolean"

        val HierarchicalListValueAsListNode: IRI = KnoraApiV2PrefixExpansion + "hierarchicalListValueAsListNode"

        val ColorValueAsColor: IRI = KnoraApiV2PrefixExpansion + "colorValueAsColor"

        val UriValueAsUri: IRI = KnoraApiV2PrefixExpansion + "uriValueAsUri"

        val GeonameValueAsGeonameCode: IRI = KnoraApiV2PrefixExpansion + "geonameValueAsGeonameCode"

        val ResourceIcon: IRI = KnoraApiV2PrefixExpansion + "resourceIcon"

    }

    object KnoraApiV2Simple {

        val VersionSegment = "/simple/v2"

        val KnoraApiOntologyIri: IRI = KnoraApi.ApiOntologyStart + KnoraApi.KnoraApiOntologyLabel + VersionSegment

        val KnoraApiV2PrefixExpansion: IRI = KnoraApiOntologyIri + "#"

        val Result: IRI = KnoraApiV2PrefixExpansion + "result"
        val Error: IRI = KnoraApiV2PrefixExpansion + "error"

        val SubjectType: IRI = KnoraApiV2PrefixExpansion + "subjectType"

        val ObjectType: IRI = KnoraApiV2PrefixExpansion + "objectType"

        val Region: IRI = KnoraApiV2PrefixExpansion + "Region"
        val Representation: IRI = KnoraApiV2PrefixExpansion + "Representation"
        val StillImageRepresentation: IRI = KnoraApiV2PrefixExpansion + "StillImageRepresentation"
        val MovingImageRepresentation: IRI = KnoraApiV2PrefixExpansion + "MovingImageRepresentation"
        val AudioRepresentation: IRI = KnoraApiV2PrefixExpansion + "AudioRepresentation"
        val DDDRepresentation: IRI = KnoraApiV2PrefixExpansion + "DDDRepresentation"
        val TextRepresentation: IRI = KnoraApiV2PrefixExpansion + "TextRepresentation"
        val DocumentRepresentation: IRI = KnoraApiV2PrefixExpansion + "DocumentRepresentation"

        val Date: IRI = KnoraApiV2PrefixExpansion + "Date"
        val Geom: IRI = KnoraApiV2PrefixExpansion + "Geom"
        val Color: IRI = KnoraApiV2PrefixExpansion + "Color"
        val Interval: IRI = KnoraApiV2PrefixExpansion + "Interval"
        val Geoname: IRI = KnoraApiV2PrefixExpansion + "Geoname"

        val Resource: IRI = KnoraApiV2PrefixExpansion + "Resource"

        val ResourceIcon: IRI = KnoraApiV2PrefixExpansion + "resourceIcon"

        val BelongsToOntology: IRI = KnoraApiV2PrefixExpansion + "belongsToOntology"

        val HasOntologies: IRI = KnoraApiV2PrefixExpansion + "hasOntologies"

        val HasOntologiesWithClasses: IRI = KnoraApiV2PrefixExpansion + "hasOntologiesWithClasses"

        val HasClasses: IRI = KnoraApiV2PrefixExpansion + "hasClasses"
        val HasProperties: IRI = KnoraApiV2PrefixExpansion + "hasProperties"
        val HasStandoffClasses: IRI = KnoraApiV2PrefixExpansion + "hasStandoffClasses"
        val HasStandoffProperties: IRI = KnoraApiV2PrefixExpansion + "hasStandoffProperties"

        val HasValue: IRI = KnoraApiV2PrefixExpansion + "hasValue"

        val HasLinkTo: IRI = KnoraApiV2PrefixExpansion + "hasLinkTo"

        val IsRegionOf: IRI = KnoraApiV2PrefixExpansion + "isRegionOf"
        val HasGeometry: IRI = KnoraApiV2PrefixExpansion + "hasGeometry"
        val HasColor: IRI = KnoraApiV2PrefixExpansion + "hasColor"
        val HasComment: IRI = KnoraApiV2PrefixExpansion + "hasComment"

        val HasFile: IRI = KnoraApiV2PrefixExpansion + "HasFile"

        val HasStillImageFile: IRI = KnoraApiV2PrefixExpansion + "HasStillImageFile"
        val HasMovingImageFile: IRI = KnoraApiV2PrefixExpansion + "HasMovingImageFile"
        val HasAudioFile: IRI = KnoraApiV2PrefixExpansion + "hasAudioFile"
        val HasDDDFile: IRI = KnoraApiV2PrefixExpansion + "hasDDDFile"
        val HasTextFile: IRI = KnoraApiV2PrefixExpansion + "hasTextFile"
        val HasDocumentFile: IRI = KnoraApiV2PrefixExpansion + "hasDocumentFile"

        val File: IRI = KnoraApiV2PrefixExpansion + "File"

        val StillImageFile: IRI = KnoraApiV2PrefixExpansion + "StillImageFile"
        val MovingImageFile: IRI = KnoraApiV2PrefixExpansion + "MovingImageFile"
        val AudioFile: IRI = KnoraApiV2PrefixExpansion + "AudioFile"
        val DDDFile: IRI = KnoraApiV2PrefixExpansion + "DDDFile"
        val TextFile: IRI = KnoraApiV2PrefixExpansion + "TextFile"
        val DocumentFile: IRI = KnoraApiV2PrefixExpansion + "DocumentFile"

        val HasStandoffLinkTo: IRI = KnoraApiV2PrefixExpansion + "hasStandoffLinkTo"
        val CreationDate: IRI = KnoraApiV2PrefixExpansion + "creationDate"
        val LastModificationDate: IRI = KnoraApiV2PrefixExpansion + "lastModificationDate"

        /**
          * Maps internal knora-base value types to the datatypes that represent them in the simplified API.
          */
        val LiteralValueTypes: Map[IRI, IRI] = Map(
            KnoraBase.TextValue -> Xsd.String,
            KnoraBase.IntValue -> Xsd.Integer,
            KnoraBase.BooleanValue -> Xsd.Boolean,
            KnoraBase.UriValue -> Xsd.Uri,
            KnoraBase.DecimalValue -> Xsd.Decimal,
            KnoraBase.DateValue -> Date,
            KnoraBase.ColorValue -> Color,
            KnoraBase.GeomValue -> Geom,
            KnoraBase.ListValue -> Xsd.String,
            KnoraBase.IntervalValue -> Interval,
            KnoraBase.GeonameValue -> Geoname,
            KnoraBase.FileValue -> File,
            KnoraBase.StillImageFileValue -> StillImageFile,
            KnoraBase.MovingImageFileValue -> MovingImageFile,
            KnoraBase.AudioFileValue -> AudioFile,
            KnoraBase.DDDFileValue -> DDDFile,
            KnoraBase.TextFileValue -> TextFile,
            KnoraBase.DocumentFileValue -> DocumentFile
        )

        /**
          * The datatypes that are used in the simplified API.
          */
        val Datatypes: Set[IRI] = LiteralValueTypes.values.toSet + Xsd.DateTimeStamp

    }

    object NamedGraphs {
        val AdminNamedGraph: IRI = "http://www.knora.org/data/admin"
        val PersistentMapNamedGraph: IRI = "http://www.knora.org/data/maps"
        val KnoraExplicitNamedGraph: IRI = "http://www.knora.org/explicit"
        val GraphDBExplicitNamedGraph: IRI = "http://www.ontotext.com/explicit"
    }

}
