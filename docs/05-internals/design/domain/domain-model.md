# Domain Model

In the context of [DEV-1415: Domain Model](https://linear.app/dasch/project/domain-model-e39ceb242242)
we attempted to gain a clear overview over the DSP's domain,
as implicitly modelled by the ontologies, code, validations and documentation of the DSP-API.

The following document aims to give a higher level overview of said domain.

!!! Note

    - As a high level overview, this document does not aim for exhaustivity.
    - Naming is tried to be kept as simple as possible, 
      while trying to consolidate different naming schemes
      (ontologies, code, API),
      which in result means that no naming scheme is strictly followed.
    - The split between V2 and Admin is arbitrary as those are intertwined within the system.
      It merely serves the purpose of organizing the presented entities.

## Domain Entities

The following Entity-Relationship-Diagrams visualize the top level entities present in the DSP. 
The attributes of these entities should be exhaustive; 
cardinalities or validation constraints are normally not depicted. 
The indicated relationships are of conceptual nature and are more complicated in the actual system.


```mermaid
---
title: Admin
---
erDiagram
    User {
        IRI id
        string userName "unique"
        string email "unique"
        string givenName
        string familyName
        string password
        string language "2 character ISO language code"
        boolean status
        boolean systemAdmin
        IRI isInProject
        IRI isInProjectAdminGroup
    }

    Project {
        IRI id
        string shortcode "4 character hex"
        string shortname "xsd:NCNAME"
        string longname "optional"
        langstring description
        string keywords
        boolean status
        boolean selfjoin
        string logo "optional"
        string restrictedViewSize
        string restrictedViewWatermark
    }

    Group {
        IRI id
        string name
        langstring description
        IRI project
        boolean status
        boolean selfjoin
    }

    ListNode {
        IRI id
        IRI projectIri "only for root node"
        langstring labels
        langstring comments
        string name
        boolean isRootNode
        IRI hasSublistNode
        IRI hasRootNode
        integer listNodePosition
    }
    ObjectAccessPermission {
        string hasPermission "the RV, V, M, D, CR string"
        IRI project
        IRI group
    }
    DefaultObjectAccessPermission {
        string hasPermission "the RV, V, M, D, CR string"
        IRI project
        IRI group
        IRI property
        IRI resourceClass
    }
    AdministrativePermission {
        string hasPermission "a different string representation"
        IRI project
        IRI group
    }

    User }|--|{ Project: "is member/admin of"
    User }|--|{ Group: "is member of"
    ListNode }o--|| Project: "belongs to"
    Project ||--|{ DefaultObjectAccessPermission: defines
    Group }o--o{ AdministrativePermission: "is granted"
    Group }o--o{ ObjectAccessPermission: "is granted"
    DefaultObjectAccessPermission ||--|| ObjectAccessPermission: "is realized as"
```

!!! danger "Unclear/Unexpected Stuff"

    - User.phone?
    - Institution? (name, description, website, phone, address, email)
    - Project.belongsToInstitution?


```mermaid
---
title: Overview V2
---
erDiagram
    Ontology ||--o{ ResourceClass: "consists of"
    Ontology ||--o{ Cardinality: "consists of"
    ResourceClass ||--o{ Cardinality: defines
    Cardinality ||--|| Property: specifies
    ResourceClass ||--o{ Property: has
    ResourceClass ||--o{ Resource: "can be instantiated as"
    Property ||--o{ Value: "can be instantiated as"
    Resource ||--o{ Value: contains
    Value ||--|| ObjectAccessPermission: grants
    Resource ||--|| ObjectAccessPermission: grants
    ObjectAccessPermission }o--o{ Group: "to"
    Resource }o--|| User: "attached to"
    Resource }o--|| Project: "attached to"
```


```mermaid
---
title: Ontology
---
erDiagram
    Ontology {
        IRI id
        string ontologyName
        IRI attachedToProject
        string label
        string comment "optional"
        boolean isShared
        date lastModificationDate
    }
    ResourceClass {
        IRI id
        langstring label
        langstring comment
        IRI subClassOf
        Cardinality cardinalities
    }
    Property {
        IRI id
        IRI subjectType
        IRI objectType
        langstring label
        langstring comment
        IRI subPropertyOf
        IRI guiElement
        string guiAttribute
    }
    Cardinality {
        IRI property "the property this cardinality refers to"
        owl_cardinality cardinality "1, 0-1, 0-n, 1-n"
        integer guiOrder
    }
    Ontology ||--o{ ResourceClass: "consists of"
    Ontology ||--o{ Property: "consists of (?)"
    ResourceClass ||--o{ Cardinality: defines
    Cardinality ||--|| Property: specifies
```


```mermaid
---
title: Data
---
erDiagram
    Resource {
        IRI id
        string label
        boolean isDeleted
        IRI hasStandoffLinkTo "+Value"
        IRI attachedToUser
        IRI attachedToProject
        string hasPermission
        date creationDate
        date lastModificationDate
        date deleteDate
        IRI deletedBy
        string deleteComment
    }
    Value {
        IRI id
        date valueCreationDate
        IRI attachedToUser
        string hasPermission
        integer valueHasOrder
        langstring valueHasComment
        boolean isDeleted
        date deleteDate
        IRI deletedBy
        langstring deleteComment
        IRI previousValue
        string valueHasString
        UUID valueHasUUID
    }
    Resource ||--o{ Value: contains
    Value ||--|| ValueLiteral: "is represented by"
    Value }o--o{ OtherResource: "links to"
    Value }o--o{ ListNode: "links to"
```



## System Instances of Classes

Apart from class and property definitions, 
`knora-base` and `knora-admin` also provide a small number of class instances 
that should be present in any running DSP stack:

```mermaid
---
title: User Groups
---
erDiagram
    UserGroup ||--|| UnknownUser: ""
    UserGroup ||--|| KnownUser: ""
    UserGroup ||--|| Creator: ""
    UserGroup ||--|| ProjectMember: ""
    UserGroup ||--|| ProjectAdmin: ""
    UserGroup ||--|| SystemAdmin: ""

```

```mermaid
---
title: Built-in Users
---
erDiagram
    User ||--o{ AnonymousUser: ""
    User ||--o{ SystemUser: ""

```

```mermaid
---
title: Built-in Projects
---
erDiagram
    Project ||--|| SystemProject: ""
    Project ||--|| DefaultSharedOntologiesProject: ""

```


## Class Hierarchy

While `knora-admin` and `salsah-gui` have relatively flat class hierarchies, 
in `knora-base` there are very complicated - yet highly relevant - inheritance structures. 
The following class diagrams try to model these structures. 
For the sake of comprehensibility, it was necessary to split the ontology into multiple diagrams,
even though this obliterates the evident connections between those diagrams.

```mermaid
---
title: Resources
---
classDiagram
  %% Classes
  class Resource {
    string label
    boolean isDeleted
    Resource hasStandoffLinkTo
    LinkValue hasStandoffLinkToValue
    User attachedToUser
    Project attachedToProject
    string hasPermissions
    date creationDate
    date lastModificationDate
    date deleteDate
    User deletedBy
    string deleteComment
  }
  class Annotation {
    TextValue hasComment
    Resource isAnnotationOf
    LinkValue isAnnotationOfValue
  }
  class LinkObj{
    TextValue hasComment
    Resource hasLinkTo
    LinkValue hasLinkToValue
  }
  class Representation {
    FileValue hasFileValue
  }
  class ExternalResource {
    ExternalResValue hasExternalResValue
  }
  class Region {
    ColorValue hasColor
    Representation isRegionOf
    GeomValue hasGemoetry
    LinkValue isRegionOfValue
    TextValue hasComment
  }
  class ArchiveRepresentation {
    ArchiveFileValue hasArchiveFileValue
  }
  class AudioRepresentation {
    AudioFileValue hasAudioFileValue
  }
  class DDDRepresentation {
    DDDFileValue hasDDDFileValue
  }
  class DocumentRepresentation {
    DocumentFileValue hasDocumentFileValue
  }
  class MovingImageRepresentation {
    MovingImageFileValue hasMovingImageFileValue
  }
  class StillImageRepresentation {
    StillImageFileValue hasStillImageFileValue
  }
  class TextRepresentation {
    TextFileValue hasTextFileValue
  }

  class AudioFileValue {
    decimal duration
  }
  class DocumentFileValue {
    integer pageCount
    integer dimX
    integer dimY
  }
  class StillImageFileValue {
    integer dimX
    integer dimY
  }

  %% Relationships
  Resource <|-- Annotation
  Resource <|-- Representation
  Resource <|-- LinkObj
  Resource <|-- ExternalResource
  Resource <|-- DeletedResource
  Resource <|-- Region
  Representation <|-- ArchiveRepresentation
  Representation <|-- AudioRepresentation
  Representation <|-- DDDRepresentation
  Representation <|-- DocumentRepresentation
  Representation <|-- MovingImageRepresentation
  Representation <|-- StillImageRepresentation
  Representation <|-- TextRepresentation
  TextRepresentation <|-- XSLTransformation
```

```mermaid
---
title: Values
---
classDiagram
  %% Classes
  class Value {
    date valueCreationDate
    User attachedToUser
    string hasPermissions
    integer valueHasOrder
    string valueHasComment
    boolean isDeleted
    date deleteDate
    User deletedBy
    string deleteComment
    Value previousValue
    string valueHasString
    string valueHasUUID
  }
  class ColorBase {
    string valueHasColor
  }
  class DateBase {
    string valueHasCalendar
    string valueHasEndPrecision
    integer valueHasEndJDN
    string valueHasStartPrecision
    integer valueHasStartJDN
  }
  class IntBase {
    integer valueHasInteger
  }
  class BooleanBase {
    boolean valueHasBoolean
  }
  class DecimalBase {
    decimal valueHasDecimal
  }
  class UriBase {
    URI valueHasUri
  }
  class IntervalBase {
    decimal valueHasIntervalStart
    decimal valueHasIntervalEnd
  }
  class TimeBase {
    date valueHasTimeStamp
  }
  class ListValue {
    ListNode valueHasListNode
  }
  class TextValue {
    StandoffTag valueHasStandoff
    integer valueHasMaxStandoffStartIndex
    string valueHasLanguage
    XSLToStandoffMApping valueHasMapping
  }
  class LinkValue {
    integer valueHasRefCount
  }
  class GeomValue {
    string valueHasGeometry
  }
  class GeonameValue {
    string valueHasGeonameCode
  }
  class ExternalResValue {
    string extResId
    string extResProvider
    string extResAccessInfo
  }
  class FileValue {
    string internalFileName
    string internalMimeType
    string originalFileName
    string originalMimeType
  }

  %% Relationships
  ValueBase <|-- Value
  ValueBase <|-- ColorBase
  ValueBase <|-- DateBase
  ValueBase <|-- IntBase
  ValueBase <|-- BooleanBase
  ValueBase <|-- DecimalBase
  ValueBase <|-- UriBase
  ValueBase <|-- IntervalBase
  ValueBase <|-- TimeBase
  Value <|-- ListValue
  Value <|-- TextValue
  Value <|-- LinkValue
  Value <|-- GeomValue
  Value <|-- GeonameValue
  Value <|-- ExternalResValue
  Value <|-- FileValue
  Value <|-- DeletedValue
  Value <|-- ColorValue
  ColorBase <|-- ColorValue
  Value <|-- DateValue
  DateBase <|-- DateValue
  Value <|-- IntValue
  IntBase <|-- IntValue
  Value <|-- BooleanValue
  BooleanBase <|-- BooleanValue
  Value <|-- DecimalValue
  DecimalBase <|-- DecimalValue
  Value <|-- UriValue
  UriBase <|-- UriValue
  Value <|-- IntervalValue
  IntervalBase <|-- IntervalValue
  Value <|-- TimeValue
  TimeBase <|-- TimeValue
  FileValue <|-- ArchiveFileValue
  FileValue <|-- AudioFileValue
  FileValue <|-- DDDFileValue
  FileValue <|-- DocumentFileValue
  FileValue <|-- MovingImageFileValue
  FileValue <|-- StillImageFileValue
  FileValue <|-- TextFileValue
  ValueBase .. StandoffInternalReferenceTag
  ColorBase .. StandoffColorTag
  DateBase .. StandoffDateTag
  IntBase .. StandoffIntegerTag
  BooleanBase .. StandoffBooleanTag
  DecimalBase .. StandoffDecimalTag
  UriBase .. StandoffUriTag
  IntervalBase .. StandoffIntervalTag
  TimeBase .. StandoffTimeTag
```

```mermaid
---
title: Standoff in knora-base
---
classDiagram
  %% Classes
  class StandoffTag {
    integer standoffHasStartTag
    integer standoffHasEndTag
    string standoffHasUUID
    string standoffHasOriginalXMLID
    integer standoffHasStartIndex
    integer standoffHasEndIndex
    StandoffTag standoffTagHasStartParent
    StandoffTag standoffTagHasEndParent
    ??? standoffParentClassConstraint
  }
  class StandoffInternalReferenceTag {
    StandoffTag standoffHasInternalReference
  }
  class StandoffLinkTag {
    Resource standoffHasLink
  }

  %% Relationships
  StandoffTag <|-- StandoffDataTypeTag
  StandoffTag <|-- StandoffLinkTag
  StandoffDataTypeTag <|-- StandoffColorTag
  StandoffDataTypeTag <|-- StandoffDateTag
  StandoffDataTypeTag <|-- StandoffIntegerTag
  StandoffDataTypeTag <|-- StandoffBooleanTag
  StandoffDataTypeTag <|-- StandoffDecimalTag
  StandoffDataTypeTag <|-- StandoffUriTag
  StandoffDataTypeTag <|-- StandoffIntervalTag
  StandoffDataTypeTag <|-- StandoffTimeTag
  ValueBase .. ColorBase
  ValueBase .. DateBase
  ValueBase .. IntBase
  ValueBase .. BooleanBase
  ValueBase .. DecimalBase
  ValueBase .. UriBase
  ValueBase .. IntervalBase
  ValueBase .. TimeBase
  ValueBase .. StandoffInternalReferenceTag
  ColorBase .. StandoffColorTag
  DateBase .. StandoffDateTag
  IntBase .. StandoffIntegerTag
  BooleanBase .. StandoffBooleanTag
  DecimalBase .. StandoffDecimalTag
  UriBase .. StandoffUriTag
  IntervalBase .. StandoffIntervalTag
  TimeBase .. StandoffTimeTag
```



```mermaid
---
title: Standoff ontology
---
flowchart BT
  StandoffRootTag["StandoffRootTag \n ----- \n string standoffRootTagHasDocumentType"]
  StandoffHyperlinkTag["StandoffHyperlinkTag \n ----- \n string standoffHyperlinkTagHasTarget"]
  StandoffVisualTag -..-> StandoffTag 
  StandoffRootTag -.-> StandoffTag 
  StandoffHyperlinkTag -.-> StandoffTag 
  StandoffStructuralTag -....-> StandoffTag 

  StandoffItalicTag --> StandoffVisualTag 
  StandoffBoldTag --> StandoffVisualTag 
  StandoffCiteTag --> StandoffVisualTag 
  StandoffUnderlineTag --> StandoffVisualTag 
  StandoffStrikethroughTag --> StandoffVisualTag 
  StandoffSuperscriptTag --> StandoffVisualTag 
  StandoffSubscriptTag --> StandoffVisualTag 
  StandoffLineTag --> StandoffVisualTag 
  StandoffPreTag --> StandoffVisualTag 
  StandoffBlockquoteTag --> StandoffStructuralTag 
  StandoffCodeTag --> StandoffStructuralTag 
  StandoffParagraphTag --> StandoffStructuralTag 
  StandoffHeader_1_to_6_Tag --> StandoffStructuralTag 
  StandoffOrderedListTag --> StandoffStructuralTag 
  StandoffUnorderedListTag --> StandoffStructuralTag 
  StandoffListElementTag --> StandoffStructuralTag 
  StandoffTableBodyTag --> StandoffStructuralTag 
  StandoffTableTag --> StandoffStructuralTag 
  StandoffTableRowTag --> StandoffStructuralTag 
  StandoffTableCellTag --> StandoffStructuralTag 
  StandoffBrTag --> StandoffStructuralTag 
```



## Property Hierarchy

```mermaid
---
title: resource properties - values
---
flowchart BT
  hasValue ---> resourceProperty
  hasLinkTo --> resourceProperty

  hasFileValue ----> hasValue
  hasColor ---> hasValue
  hasGeometry ---> hasValue
  hasComment ---> hasValue
  hasSequenceBounds ---> hasValue 
  seqnum ---> hasValue
  hasExtResValue ---> hasValue
  hasLinkToValue ----> hasValue 

  hasArchiveFileValue ---> hasFileValue
  hasDocumentFileValue ---> hasFileValue
  hasTextFileValue ---> hasFileValue
  hasStillImageFileValue ---> hasFileValue
  hasMovingImageFileValue ---> hasFileValue
  hasAudioFileValue ---> hasFileValue
  hasDDDFileValue ---> hasFileValue

  isPartOfValue ---> hasLinkToValue
  hasAnnotationOfValue ---> hasLinkToValue
  hasRepresentationValue ---> hasLinkToValue
  hasStandoffLinkToValue ---> hasLinkToValue
  isSequenceOfValue ---> hasLinkToValue
  isRegionOfValue ---> hasLinkToValue

  isPartOf ---> hasLinkTo
  isAnnotationOf ---> hasLinkTo
  hasRepresentation ---> hasLinkTo
  hasStandoffLinkTo ---> hasLinkTo
  isSequenceOf ---> hasLinkTo
  isRegionOf ---> hasLinkTo
```



```mermaid
---
title: resource properties - resource metadata(?)
---
flowchart BT
  creationDate ---> objectCannotBeMarkedAsDeleted
  deleteDate --> objectCannotBeMarkedAsDeleted
  isDeleted ---> objectCannotBeMarkedAsDeleted
  deletedBy --> objectCannotBeMarkedAsDeleted
  standoffTagHasLink ---> objectCannotBeMarkedAsDeleted 
  hasSubListNode --> objectCannotBeMarkedAsDeleted
  standoffTagHasEnd ---> objectCannotBeMarkedAsDeleted
  standoffTagHasInternalReference --> objectCannotBeMarkedAsDeleted 
  valueHas ----> objectCannotBeMarkedAsDeleted
  standoffTagHasStart --> objectCannotBeMarkedAsDeleted 
  standoffTagHasStartIndex ---> objectCannotBeMarkedAsDeleted
  standoffTagHasEndIndex --> objectCannotBeMarkedAsDeleted
  standoffTagHasUuid ---> objectCannotBeMarkedAsDeleted
  standoffTagHasEndParent --> objectCannotBeMarkedAsDeleted
  standoffTagHasOriginalXMLID ---> objectCannotBeMarkedAsDeleted
  targetHasOriginalXMLID --> objectCannotBeMarkedAsDeleted
  standoffTagHasStartAncestor ----> objectCannotBeMarkedAsDeleted 
  standoffTagHasStartParent --> objectCannotBeMarkedAsDeleted
  standoffTagHasStartParent --> standoffTagHasStartAncestor
  hasMappingElement ---> objectCannotBeMarkedAsDeleted
  mappingHasXMLTagname --> objectCannotBeMarkedAsDeleted
  mappingHasXMLNamespace ---> objectCannotBeMarkedAsDeleted
  mappingHasXMLClass --> objectCannotBeMarkedAsDeleted
  mappingHasStandoffClass ---> objectCannotBeMarkedAsDeleted
  mappingHasXMLAttribute --> objectCannotBeMarkedAsDeleted
  mappingHasXMLAttributename ---> objectCannotBeMarkedAsDeleted
  mappingHasStandoffProperty --> objectCannotBeMarkedAsDeleted
  mappingHasStandoffDataTypeClass ---> objectCannotBeMarkedAsDeleted
  mappingElementRequiresSeparator --> objectCannotBeMarkedAsDeleted
  mappingHasDefaultXSLTransformation ---> objectCannotBeMarkedAsDeleted

  duration --> valueHas
  pageCount ---> valueHas
  dimY --> valueHas
  dimX ---> valueHas
  valueHasStandoff --> valueHas
  valueHasMaxStandoffStartIndex ---> valueHas
  previousValue --> valueHas
  ValueHasMapping ---> valueHas
  extResAccessInfo --> valueHas
  extResId ---> valueHas
  extResProvider --> valueHas
  fps ---> valueHas
  internalFilename --> valueHas
  internalMimeType ---> valueHas

  valueHasLanguage
```
