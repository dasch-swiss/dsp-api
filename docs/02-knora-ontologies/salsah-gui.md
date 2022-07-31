<!---
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
-->

# The SALSAH GUI Ontology

## Overview

The SALSAH GUI ontology provides entities that can be used in
user-created ontologies to indicate to SALSAH (or to another GUI)
how data should be entered and displayed.

The SALSAH GUI ontology is identified by the IRI
`http://www.knora.org/ontology/salsah-gui`. In the Knora documentation
in general, it is identified by the prefix `salsah-gui`, but for
brevity, we omit the prefix in this document.

## Properties

### guiOrder
 
`guiOrder` can be attached to a cardinality
in a resource class, to indicate the order in which properties
should be displayed in the GUI. The object is a non-negative
integer. For example, a property with `guiOrder` 0 would be
displayed first, followed by a property with `guiOrder` 1, and so
on.

### guiElement

`guiElement` can be attached to a property definition to indicate which
GUI element should be used to enter data for the property. This
should be one of the individuals of class `Guielement` described
below.

### guiAttribute

`guiAttribute` can be attached to a property definition to provide attributes for
the GUI element specified in `guiElement`. The objects of this
predicate are written in a DSL with the following syntax:

```ebnf
object          = attribute name, "=", attribute value ;
attribute name  = identifier ;
identifier      = letter , { letter } ;
attribute value = integer | decimal | percent | string | iri ;
percent         = integer, "%" ;
iri             = "<", string, ">" ;
```

The attributes used with each GUI element are described below under
[Individuals](#individuals).

### guiAttributeDefinition

`guiAttributeDefinition` is used only in the `salsah-gui` ontology itself, as a predicate
attached to instances of `Guielement` (see [Individuals](#individuals)),
to specify the attributes that can be given as objects of `guiAttribute` when a given
`Guielement` is used. The objects of this predicate are written in
a DSL with the following syntax:

```ebnf
object              = attribute name, [ "(required)" ], ":", attribute type, [ enumerated values ] ;
enumerated values   = "(", enumerated value, { "|", enumerated value } ")" ;
attribute name      = identifier ;
attribute type      = "integer" | "decimal" | "percent" | "string" | "iri" ;
enumerated value    = identifier ;
identifier          = letter , { letter } ;
```

Enumerated values are allowed only if `attribute type` is `string`.
If enumerated values are provided for an attribute, the attribute
value given via `guiAttribute` must be one of the enumerated values.

## Classes

### Guielement

The instances of class `Guielement` are individuals representing GUI
elements for data entry.

## Individuals

### Colorpicker

`Colorpicker` is a GUI element for selecting a color. A property definition that uses
this element may also contain a `guiAttribute` predicate whose
object is a string in the form `"ncolors=N"`, where `N` is an
integer specifying the number of colors to display.

### Date

`Date` is a GUI element for selecting a date.

### Geometry

`Geometry` is a GUI element for selecting the geometry of a two-dimensional
region.

### Geonames

`Geonames` is a GUI element for selecting a [Geonames](http://www.geonames.org/)
identifier.

### Interval

`Interval` is a GUI element for selecting a time interval in an audio or video
recording.

### List

`List` is a GUI element for selecting an item in a hierarchical list (see
[ListValue](knora-base.md#listvalue)). A property definition that
uses this element must also contain this `guiAttribute` predicate: 

`"hlist=<LIST_IRI>"`, where `LIST_IRI` is the IRI of a
`knora-base:ListNode` that is the root node of a hierarchical list.

### Pulldown

`Pulldown` is a GUI element for selecting an item in a flat list (see
[ListValue](knora-base.md#listvalue)) using a pull-down menu. A
property definition that uses this element must also contain this
`guiAttribute` predicate: 

`"hlist=<LIST_IRI>"`, where `LIST_IRI` is the IRI of a
`knora-base:ListNode` that is the root node of a hierarchical list.

### Radio

`Radio` is a GUI element for selecting an item in a flat list (see
[ListValue](knora-base.md#listvalue)) using radio buttons. A property
definition that uses this element must also contain this
`guiAttribute` predicate:

`"hlist=<LIST_IRI>"`, where `LIST_IRI` is the IRI of a
`knora-base:ListNode` that is the root node of a hierarchical list.

### Richtext

`Richtext` is a GUI element for editing multi-line formatted text.

### Searchbox

`Searchbox` is a GUI element for searching for a resource by matching text in its
`rdfs:label`. For DSP-API v1, a property definition that uses this
element may also contain this `guiAttribute` predicate:

`"numprops=N"`, where `N` is an integer specifying the number of
describing properties to be returned for each found resource.

For DSP-API v2, the `guiAttribute` has no effect.

### SimpleText

`SimpleText` is a GUI element for editing a single line of unformatted text. A
property definition that uses this element may also contain a
`guiAttribute` predicate with one or both of the following objects:

-   `"size=N"`, where `N` is an integer specifying the size of the
text field.
-   `"maxlength=N"`, where `N` is an integer specifying the maximum
length of the string to be input.

### Slider

`Slider` is a GUI element for choosing numerical values using a slider. A
property definition that uses this element must also contain a
`guiAttribute` predicate with both of the following objects:

-   `"min=N"`, where `N` is an integer specifying the minimum value
of the input.
-   `"max=N"`, where `N` is an integer specifying the maximum value 
of the input.

### Spinbox

`Spinbox` is a GUI element for choosing numerical values using a spinbox. A
property definition that uses this element may also contain a
`guiAttribute` predicate with one or both of the following objects:

-   `"min=N"`, where `N` is an integer specifying the minimum value
of the input.
-   `"max=N"`, where `N` is an integer specifying the maximum value
of the input.

### Textarea

`Textarea` is a GUI element for editing multi-line unformatted text. A property
definition that uses this element may also contain a `guiAttribute`
predicate with one or more of the following objects:

-   `"width=N"`, where `N` is a percentage of the window width (an
integer followed by `%`).
-   `"cols=N"`, where `N` is an integer representing the number of
colums in the text entry box.
-   `"rows=N"`, where `N` is an integer specifying the height of the
text entry box in rows.
-   `"wrap=W"`, where `W` is `soft` or `hard` (see
[wrap](https://www.w3.org/TR/html5/sec-forms.html#element-attrdef-textarea-wrap)).

### Checkbox

`Checkbox` is a GUI element for choosing a boolean value using a checkbox.

### Fileupload

`Fileupload` is a GUI element for uploading a file.
