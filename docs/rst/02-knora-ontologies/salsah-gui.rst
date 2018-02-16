.. Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
   Tobias Schweizer, André Kilchenmann, and Sepideh Alassi.

   This file is part of Knora.

   Knora is free software: you can redistribute it and/or modify
   it under the terms of the GNU Affero General Public License as published
   by the Free Software Foundation, either version 3 of the License, or
   (at your option) any later version.

   Knora is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
   GNU Affero General Public License for more details.

   You should have received a copy of the GNU Affero General Public
   License along with Knora.  If not, see <http://www.gnu.org/licenses/>.

.. _salsah-gui:

***********************
The SALSAH GUI Ontology
***********************

.. contents:: :local:

Overview
========

The SALSAH GUI ontology provides entities that can be used in project-specific ontologies
to indicate to SALSAH (or to another GUI) how data should be entered and displayed.

The SALSAH GUI ontology is identified by the IRI ``http://www.knora.org/ontology/salsah-gui``.
In the Knora documentation in general, it is identified by the prefix ``salsah-gui``, but for brevity,
we omit the prefix in this document.

.. _salsah-gui-properties:

Properties
==========

``guiOrder``
   Can be attached to an ``owl:Restriction`` representing a cardinality in a resource class, to indicate
   the order in which properties should be displayed in the GUI. The object is a non-negative integer.
   For example, a property with ``guiOrder`` 0 would be displayed first, followed by a property with ``guiOrder``
   1, and so on.

``guiElement``
   Can be attached to a property definition to indicate which SALSAH GUI element should be used to enter
   data for the property. This should be one of the individuals of class ``Guielement`` described below.

``guiAttribute``
   Can be attached to a property definition to provide attributes for the GUI element specified in
   ``guiElement``. The attributes 

``guiAttributeDefinition``
   Used only in the ``salsah-gui`` ontology itself, to specify the form of the objects of ``guiAttribute``
   when used with a particular ``Guielement``.

Classes
=======

``Guielement``
   The instances of this class are individuals representing SALSAH GUI elements for data entry.

Individuals
===========

``Colorpicker``
   A GUI element for selecting a color. A property definition that uses this element may
   also contain a ``guiAttribute`` predicate whose object is a string in the form
   ``"ncolors=N"``, where ``N`` is an integer specifying the number of colors to display.

``Date``
   A GUI element for selecting a date.

``Geometry``
   A GUI element for selecting the geometry of a two-dimensional region.

``Geonames``
   A GUI element for selecting a Geonames_ identifier.

``Interval``
   A GUI element for selecting a time interval in an audio or video recording.

``List``
   A GUI element for selecting an item in a hierarchical list (see :ref:`knora-base-list-value`).
   A property definition that uses this element must also contain a ``guiAttribute`` predicate
   whose object is a string in the form ``"hlist=<LIST_IRI>"``, where ``LIST_IRI`` is the IRI of a
   ``knora-base:ListNode``.

``Pulldown``
   A GUI element for selecting an item in a flat list (see :ref:`knora-base-list-value`) using a pull-down menu.
   A property definition that uses this element must also contain a ``guiAttribute`` predicate
   whose object is a string in the form ``"hlist=<LIST_IRI>"``, where ``LIST_IRI`` is the IRI of a
   ``knora-base:ListNode``.

``Radio``
   A GUI element for selecting an item in a flat list (see :ref:`knora-base-list-value`) using radio buttons.
   A property definition that uses this element must also contain a ``guiAttribute`` predicate
   whose object is a string in the form ``"hlist=<LIST_IRI>"``, where ``LIST_IRI`` is the IRI of a
   ``knora-base:ListNode``.

``Richtext``
   A GUI element for editing multi-line formatted text.

``Searchbox``
   A GUI element for searching for a resource by matching text in its ``rdfs:label``. For Knora API v1,
   a property definition that uses this element may also contain a ``guiAttribute`` predicate whose object
   is a string in the form ``"numprops=N"``, where ``N`` is an integer specifying the number of describing
   properties to be returned for each found resource. For Knora API v2, the ``guiAttribute`` has no effect.

``SimpleText``
   A GUI element for editing a single line of unformatted text. A property definition that uses this element may
   also contain a ``guiAttribute`` predicate with one or both of the following objects: a string in the form
   ``"size=N"``, where ``N`` is an integer specifying the size of the text field, and a string in the form
   ``"maxlength=N"``, where ``N`` is an integer specifying the maximum length of the string to be input.

``Slider``
   A GUI element for choosing numerical values using a slider. A property definition that uses this element may
   also contain a ``guiAttribute`` predicate with one or both of the following objects: a string in the form
   ``"min=N"``, where ``N`` is an integer specifying the minimum value of the input, and a string in the form
   ``"max=N"``, where ``N`` is an integer specifying the maximum value of the input.

``Spinbox``
   A GUI element for choosing numerical values using a spinbox. A property definition that uses this element may
   also contain a ``guiAttribute`` predicate with one or both of the following objects: a string in the form
   ``"min=N"``, where ``N`` is an integer specifying the minimum value of the input, and a string in the form
   ``"max=N"``, where ``N`` is an integer specifying the maximum value of the input.

``Textarea``
   A GUI element for editing multi-line unformatted text. A property definition that uses this element may
   also contain a ``guiAttribute`` predicate with one or more of the following objects: a string in the form
   ``"width=N"``, where ``N`` is a percentage of the window width (an integer followed by ``%``),
   ``"cols=N"``, where ``N`` is an integer representing the number of colums in the text entry box, and a string
   in the form ``"rows=N"``, where ``N`` is an integer specifying the height of the text entry box in rows.

``Checkbox``
   A GUI element for choosing a boolean value using a checkbox.

``Fileupload``
   A GUI element for uploading a file.

.. _Geonames: http://www.geonames.org/
