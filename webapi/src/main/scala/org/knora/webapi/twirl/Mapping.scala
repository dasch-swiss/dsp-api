package org.knora.webapi.twirl

import org.knora.webapi.IRI

/**
  * Represents a standoff datatype class of an XML tag.
  *
  * @param datatype the Iri of the standoff datatype class.
  * @param attributeName the XML attribute that holds the typed value.
  * @param mappingStandoffDataTypeClassElementIri the Iri of the standoff datatype element (to be used to create the element in the triplestore).
  */
case class MappingStandoffDatatypeClass(datatype: IRI, attributeName: String, mappingStandoffDataTypeClassElementIri: IRI)

/**
  * Represents an attribute of an XML tag.
  *
  * @param attributeName the name of the XML attribute.
  * @param namespace the namespace of the XML attribute.
  * @param standoffProperty the Iri of standoff property the XML attribute is mapped to.
  * @param mappingXMLAttributeElementIri the Iri of the attribute element (to be used to create the element in the triplestore).
  */
case class MappingXMLAttribute(attributeName: String, namespace: String, standoffProperty: IRI, mappingXMLAttributeElementIri: IRI)

/**
  * Represents an element of an XML to standoff mapping.
  *
  * @param tagName the name of the XML tag.
  * @param namespace the namespace of the XML tag.
  * @param className the classname of the XML tag.
  * @param standoffClass the IRI of the standoff class the XML tag is mapped to.
  * @param attributes the attributes of the XML tag.
  * @param standoffDataTypeClass the standoff data type class of the xml tag.
  * @param mappingElementIri the Iri of the mapping element (to be used to create the element in the triplestore).
  */
case class MappingElement(tagName: String, namespace: String, className: String, standoffClass: IRI, attributes: Seq[MappingXMLAttribute] = Seq.empty[MappingXMLAttribute], standoffDataTypeClass: Option[MappingStandoffDatatypeClass] = None, mappingElementIri: IRI, separatorRequired: Boolean)