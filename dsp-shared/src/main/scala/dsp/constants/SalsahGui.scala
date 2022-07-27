/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.constants

import dsp.errors._

/**
 * Contains string constants for IRIs from ontologies used by the application.
 */
object SalsahGui {

  /**
   * `IRI` is a synonym for `String`, used to improve code readability.
   */
  type IRI = String

  val InternalOntologyStart = "http://www.knora.org/ontology"

  val SalsahGuiOntologyLabel: String = "salsah-gui"
  val SalsahGuiOntologyIri: IRI      = InternalOntologyStart + "/" + SalsahGuiOntologyLabel
  val SalsahGuiPrefixExpansion: IRI  = SalsahGuiOntologyIri + "#"

  val GuiAttribute: IRI           = SalsahGuiPrefixExpansion + "guiAttribute"
  val GuiAttributeDefinition: IRI = SalsahGuiPrefixExpansion + "guiAttributeDefinition"
  val GuiOrder: IRI               = SalsahGuiPrefixExpansion + "guiOrder"
  val GuiElementProp: IRI         = SalsahGuiPrefixExpansion + "guiElement"
  val GuiElementClass: IRI        = SalsahGuiPrefixExpansion + "Guielement"

  val SimpleText: IRI  = SalsahGuiPrefixExpansion + "SimpleText"
  val Textarea: IRI    = SalsahGuiPrefixExpansion + "Textarea"
  val Pulldown: IRI    = SalsahGuiPrefixExpansion + "Pulldown"
  val Slider: IRI      = SalsahGuiPrefixExpansion + "Slider"
  val Spinbox: IRI     = SalsahGuiPrefixExpansion + "Spinbox"
  val Searchbox: IRI   = SalsahGuiPrefixExpansion + "Searchbox"
  val Date: IRI        = SalsahGuiPrefixExpansion + "Date"
  val Geometry: IRI    = SalsahGuiPrefixExpansion + "Geometry"
  val Colorpicker: IRI = SalsahGuiPrefixExpansion + "Colorpicker"
  val List: IRI        = SalsahGuiPrefixExpansion + "List"
  val Radio: IRI       = SalsahGuiPrefixExpansion + "Radio"
  val Checkbox: IRI    = SalsahGuiPrefixExpansion + "Checkbox"
  val Richtext: IRI    = SalsahGuiPrefixExpansion + "Richtext"
  val Interval: IRI    = SalsahGuiPrefixExpansion + "Interval"
  val TimeStamp: IRI   = SalsahGuiPrefixExpansion + "TimeStamp"
  val Geonames: IRI    = SalsahGuiPrefixExpansion + "Geonames"
  val Fileupload: IRI  = SalsahGuiPrefixExpansion + "Fileupload"

  val GuiElements = scala.collection.immutable.List(
    SimpleText,
    Textarea,
    Pulldown,
    Slider,
    Spinbox,
    Searchbox,
    Date,
    Geometry,
    Colorpicker,
    List,
    Radio,
    Checkbox,
    Richtext,
    Interval,
    TimeStamp,
    Geonames,
    Fileupload
  )

  val GuiAttributes = scala.collection.immutable.List(
    "ncolors",
    "hlist",
    "numprops",
    "size",
    "maxlength",
    "min",
    "max",
    "cols",
    "rows",
    "width",
    "wrap"
  )

  object SalsahGuiAttributeType extends Enumeration {

    val Integer: Value = Value(0, "integer")
    val Percent: Value = Value(1, "percent")
    val Decimal: Value = Value(2, "decimal")
    val Str: Value     = Value(3, "string")
    val Iri: Value     = Value(4, "iri")

    val valueMap: Map[String, Value] = values.map(v => (v.toString, v)).toMap

    def lookup(name: String): Value =
      valueMap.get(name) match {
        case Some(value) => value
        case None        => throw InconsistentRepositoryDataException(s"salsah-gui attribute type not found: $name")
      }
  }

  object External {
    // external representation of salsah-gui entities of the form: http://api.knora.org/ontology/salsah-gui/v2#...
    val ApiOntologyHostname: String   = "http://api.knora.org"
    val ApiOntologyStart: String      = ApiOntologyHostname + "/ontology/"
    val VersionSegment                = "/v2"
    val SalsahGuiOntologyIri: IRI     = ApiOntologyStart + SalsahGui.SalsahGuiOntologyLabel + VersionSegment
    val SalsahGuiPrefixExpansion: IRI = SalsahGuiOntologyIri + "#"

    val GuiAttribute: IRI           = SalsahGuiPrefixExpansion + "guiAttribute"
    val GuiOrder: IRI               = SalsahGuiPrefixExpansion + "guiOrder"
    val GuiElementProp: IRI         = SalsahGuiPrefixExpansion + "guiElement"
    val GuiAttributeDefinition: IRI = SalsahGuiPrefixExpansion + "guiAttributeDefinition"
    val GuiElementClass: IRI        = SalsahGuiPrefixExpansion + "Guielement"
    val Geometry: IRI               = SalsahGuiPrefixExpansion + "Geometry"
    val Colorpicker: IRI            = SalsahGuiPrefixExpansion + "Colorpicker"
    val Fileupload: IRI             = SalsahGuiPrefixExpansion + "Fileupload"
    val Richtext: IRI               = SalsahGuiPrefixExpansion + "Richtext"
  }
}
