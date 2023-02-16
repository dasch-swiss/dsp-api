# Class and Property Hierarchies

## Class Hierarchy

While `knora-admin` and `salsah-gui` have relatively flat class hierarchies, 
in `knora-base` there are very complicated - yet highly relevant - inheritance structures. 
The following class diagrams try to model these structures. 
For the sake of comprehensibility, it was necessary to split the ontology into multiple diagrams,
even though this obliterates the evident connections between those diagrams.

### Resources

```mermaid
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

### Values

!!! Note "Legend"

    - doted lines: the boxes are copies from another diagram.


```mermaid
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
  class ArchiveFileValue {

  }
  class AudioFileValue {
    decimal duration
  }
  class DDDFileValue
  class DocumentFileValue {
    integer pageCount
    integer dimX
    integer dimY
  }
  class MovingImageFileValue
  class StillImageFileValue {
    integer dimX
    integer dimY
  }
  class TextFileValue


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

### Standoff in knora-base

```mermaid
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

### Standoff Ontology

```mermaid
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

### Properties and Values

```mermaid
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

### Resource Metadata

```mermaid
flowchart BT
  creationDate ---> objectCannotBeMarkedAsDeleted
  deleteDate --> objectCannotBeMarkedAsDeleted
  isDeleted ---> objectCannotBeMarkedAsDeleted
  isRootNode ---> objectCannotBeMarkedAsDeleted
  hasRootNode ---> objectCannotBeMarkedAsDeleted
  lastModificationDate ---> objectCannotBeMarkedAsDeleted
  listNodePosition ---> objectCannotBeMarkedAsDeleted
  listNodeName ---> objectCannotBeMarkedAsDeleted
  deleteComment ---> objectCannotBeMarkedAsDeleted
  hasPermissions ---> objectCannotBeMarkedAsDeleted
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
  originalFilename ---> valueHas
  originalMimeType ---> valueHas
  valueHasComment ---> valueHas
  valueCreationDate ---> valueHas
  valueHasUUID ---> valueHas
  valueHasCalendar ---> valueHas
  valueHasColor ---> valueHas
  valueHasEndJDN ---> valueHas
  valueHasEndPrecision ---> valueHas
  valueHasDecimal ---> valueHas
  valueHasGeometry ---> valueHas
  valueHasGeonameCode ---> valueHas
  valueHasInteger ---> valueHas
  valueHasBoolean ---> valueHas
  valueHasUri ---> valueHas
  valueHasIntervalEnd ---> valueHas
  valueHasIntervalStart ---> valueHas
  valueHasTimeStamp ---> valueHas
  valueHasListNode ---> valueHas
  valueHasOrder ---> valueHas
  valueHasRefCount ---> valueHas
  valueHasStartJDN ---> valueHas
  valueHasStartPrecision ---> valueHas
  valueHasString ---> valueHas

  valueHasLanguage
```

## Property Triple Structure

### Text Related Triples

```mermaid
flowchart LR
  %% Classes
  MappingComponent(MappingComponent)
  MappingElement(MappingElement)
  MappingXMLAttribute(MappingXMLAttribute)
  MappingStandoffDataTypeClass(MappingStandoffDataTypeClass)
  TextValue(TextValue)
  XMLToStandoffMapping(XMLToStandoffMapping)
  XSLTransformation(XSLTransformation)
  StandoffTag(StandoffTag)
  StandoffInternalReferenceTag(StandoffInternalReferenceTag)
  Resource(Resource)

  %% Duplicates
  _StandoffTag{{StandoffTag}}

  %% Values
  string1([xsd:string])
  string2([xsd:string])
  string3([xsd:string])
  boolean1([xsd:boolean])
  integer1([xsd:integer])

  %% Relations
  TextValue --> valueHasMapping --> XMLToStandoffMapping
  XMLToStandoffMapping --> hasMappingElement --> MappingElement
  XMLToStandoffMapping --> mappingHasDefaultXSLTransformation --> XSLTransformation
  TextValue --> valueHasStandoff --> StandoffTag
  subgraph standoffProperties
    standoffTagHasEndIndex
    standoffTagHasStartIndex
    standoffTagHasEnd
    standoffTagHasStart
    standoffTagHasLink
    standoffTagHasOriginalXMLID
    targetHasOriginalXMLID
    standoffTagHasUuid
    standoffTagHasEndParent
    standoffTagHasStartAncestor
    standoffTagHasStartParent
    standoffTagHasInternalReference
  end
  StandoffTag --> standoffTagHasEndIndex --> integer1
  StandoffTag --> standoffTagHasStartIndex --> integer1
  StandoffTag --> standoffTagHasEnd --> integer1
  StandoffTag --> standoffTagHasStart --> integer1
  StandoffTag --> standoffTagHasLink --> Resource
  StandoffTag --> standoffTagHasOriginalXMLID --> string2
  StandoffTag --> targetHasOriginalXMLID --> string2
  StandoffTag --> standoffTagHasUuid --> string2
  StandoffTag --> standoffTagHasEndParent --> _StandoffTag
  StandoffTag --> standoffTagHasStartAncestor --> _StandoffTag
  StandoffTag --> standoffTagHasStartParent --> _StandoffTag
  StandoffInternalReferenceTag --> standoffTagHasInternalReference --> _StandoffTag
  TextValue --> valueHasLanguage --> string3

  MappingComponent --> mappingHasXMLAttributename --> string1
  MappingComponent --> mappingHasStandoffClass --> string1
  MappingComponent --> mappingHasXMLNamespace --> string1
  MappingElement --> mappingHasXMLClass --> string1
  MappingElement --> mappingHasXMLTagname --> string1
  MappingElement --> mappingHasXMLAttribute --> MappingXMLAttribute --> mappingHasStandoffProperty
  MappingElement --> mappingHasStandoffDataTypeClass --> MappingStandoffDataTypeClass
  MappingElement --> mappingElementRequiresSeparator --> boolean1
```

### Resource Triples Structure

!!! Note "Legend"

    - round boxes: resources
    - square boxes: properties
    - hexagonal boxes: resoures that are duplicated for graphical reasons
    - oval boxes: xsd values
    - grey squares: thematic units

```mermaid
flowchart LR
  LinkValue(LinkValue)
  GeomValue(GeomValue)
  ColorValue(ColorValue)
  ExternalResValue(ExternalResValue)

  date([xsd:dateTime])
  string([xsd:string])
  _Representation{{Representation}}
  _Representation2{{Representation}}
  _TextValue{{TextValue}}
  _Value{{Value}}
  _Resource{{Resource}}

  subgraph Resources
    Resource(Resource)
    Annotation(Annotation)
    Region(Region)
    ExternalResource(ExternalResource)
    Representation(Representation)
    StillImageRepresentation("StillImageRepresentation etc.")
  end

  subgraph Links
    hasRepresentation
    hasLinkTo
    isSequenceOf
    hasStandoffLinkTo
    isPartOf
  end

  subgraph LinkValues
    hasRepresentationValue
    hasLinkToValue
    isSequenceOfValue
    hasStandoffLinkToValue
    isPartOfValue
  end

  subgraph FileValues
    direction LR
    hasFileValue
    FileValue(FileValue)
    hasStillImageRepresentation["hasStillImageRepresentation etc."]
    StillImageFileValue("StillImageFileValue etc.")
  end

  Resource --> creationDate --> date
  Resource --> hasComment --> _TextValue
  Resource --> hasValue --> _Value

  Resource --> hasRepresentation --> _Representation

  Resource --> hasLinkTo --> _Resource
  Resource --> isSequenceOf --> _Resource
  Resource --> hasStandoffLinkTo --> _Resource
  Resource --> isPartOf --> _Resource

  Resource --> hasLinkToValue --> LinkValue
  Resource --> isSequenceOfValue --> LinkValue
  Resource --> hasStandoffLinkToValue --> LinkValue
  Resource --> hasRepresentationValue --> LinkValue
  Resource --> isPartOfValue --> LinkValue

  Annotation --> isAnnotationOfValue --> LinkValue
  Annotation --> isAnnotationOf --> _Resource

  Region --> isRegionOfValue --> LinkValue
  Region --> isRegionOf --> _Representation2
  Region --> hasGeometry --> GeomValue
  Region --> hasColorValue --> ColorValue

  ExternalResource --> hasExtResValue --> ExternalResValue
  ExternalResValue --> extResId --> string
  ExternalResValue --> extResAccessInfo --> string
  ExternalResValue --> extResProvider --> string

  Representation --> hasFileValue --> FileValue
  StillImageRepresentation --> hasStillImageRepresentation --> StillImageFileValue
```

### Properties without Subject Class Constraint

```mermaid
flowchart LR
  na[[no subject class constraint defined]]

  %% Classes
  User(admin:User)
  IntValue(IntValue)
  IntervalValue(IntervalValue)

  %% Values
  date([xsd:dateTime])
  boolean([xsd:boolean])

  %% Relations
  na --> deletedBy --> User
  na --> seqnum --> IntValue
  na --> hasSequenceBounds --> IntervalValue
  na --> deleteDate --> date
  na --> isDeleted --> boolean
```
