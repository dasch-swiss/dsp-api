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
   TODO: document this (see `#744 <https://github.com/dhlab-basel/Knora/issues/744>`_).

Classes
=======

``Guielement``
   The instances of this class are individuals representing SALSAH GUI elements for data entry.

Individuals
===========

``Colorpicker``
   A GUI element for selecting a color.

``Date``
   A GUI element for selecting a date.

``Geometry``
   A GUI element for selecting the geometry of a two-dimensional region.

``Geonames``
   A GUI element for selecting a Geonames_ identifier.

``Iconclass``
   TODO: what is this (see `#744 <https://github.com/dhlab-basel/Knora/issues/744>`_)?

``Interval``
   A GUI element for selecting a time interval in an audio or video recording.

``List``
   A GUI element for selecting an item in a hierarchical list (see :ref:`knora-base-list-value`).
   TODO: How is this different from ``Pulldown`` (see `#744 <https://github.com/dhlab-basel/Knora/issues/744>`_)?

``Pulldown``
   TODO: How is this different from ``List`` (see `#744 <https://github.com/dhlab-basel/Knora/issues/744>`_)?

``Radio``
   A GUI element for selecting an item in a hierarchical list using radio buttons.

``Richtext``
   A GUI element for editing formatted text.

``Searchbox``
   A GUI element for searching for a particular resource.

``SimpleText``
   A GUI element for editing unformatted text.

``Slider``
   A GUI element for choosing numerical values using a slider.

``Spinbox``
   A GUI element for choosing numerical values using a spinbox.

``Textarea``
   TODO: How is this different from ``Richtext`` (see `#744 <https://github.com/dhlab-basel/Knora/issues/744>`_)?

``Checkbox``
   A GUI element for choosing a boolean value using a checkbox.

``Fileupload``
   A GUI element for uploading a file.

.. _Geonames: http://www.geonames.org/
