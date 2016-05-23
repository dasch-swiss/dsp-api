/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and André Fatton.
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

package org.knora.webapi.messages.v1.responder.resourcemessages

import java.util.UUID

import org.knora.webapi._
import org.knora.webapi.messages.v1.responder.{KnoraRequestV1, KnoraResponseV1}
import org.knora.webapi.messages.v1.responder.sipimessages.SipiResponderConversionRequestV1
import org.knora.webapi.messages.v1.responder.usermessages.{UserDataV1, UserProfileV1}
import org.knora.webapi.messages.v1.responder.valuemessages._
import spray.httpx.SprayJsonSupport
import spray.json._

import scala.collection.breakOut
import scala.collection.immutable.Iterable

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// API requests

/**
  * Represents an API request payload that asks the Knora API server to create a new resource
  * and properties attached to that resource.
  *
  * @param restype_id the resource type of the resource to be created.
  * @param label      the rdfs:label of the resource.
  * @param properties the properties to be created as a Map of property types to property value(s).
  * @param file       a file to be attached to the resource (GUI-case).
  * @param project_id the IRI of the project the resources is added to.
  */
case class CreateResourceApiRequestV1(restype_id: IRI,
                                      label: String,
                                      properties: Map[IRI, Seq[CreateResourceValueV1]],
                                      file: Option[CreateFileV1] = None,
                                      project_id: IRI) {

    def toJsValue = ResourceV1JsonProtocol.createResourceApiRequestV1Format.write(this)

}

/**
  * Represents a property value to be created.
  *
  * @param richtext_value a richtext object to be added to the resource.
  * @param int_value      an integer literal to be used in the value.
  */
case class CreateResourceValueV1(richtext_value: Option[CreateRichtextV1] = None,
                                 link_value: Option[IRI] = None,
                                 int_value: Option[Int] = None,
                                 float_value: Option[Float] = None,
                                 date_value: Option[String] = None,
                                 color_value: Option[String] = None,
                                 geom_value: Option[String] = None,
                                 hlist_value: Option[IRI] = None,
                                 comment: Option[String] = None)

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Messages

/**
  * An abstract trait representing a request message that can be sent to `ResourcesResponderV1`.
  */
sealed trait ResourcesResponderRequestV1 extends KnoraRequestV1

/**
  * Requests a description of a resource. A successful response will be a [[ResourceInfoResponseV1]].
  *
  * @param iri         the IRI of the resource to be queried.
  * @param userProfile the profile of the user making the request.
  */
case class ResourceInfoGetRequestV1(iri: IRI, userProfile: UserProfileV1) extends ResourcesResponderRequestV1

/**
  * Requests a full description of a resource, along with its properties, their values, incoming references, and other
  * information. A successful response will be a [[ResourceFullResponseV1]].
  *
  * @param iri         the IRI of the resource to be queried.
  * @param userProfile the profile of the user making the request.
  * @param getIncoming if `true`, information about incoming references will be included in the response.
  */
case class ResourceFullGetRequestV1(iri: IRI, userProfile: UserProfileV1, getIncoming: Boolean = true) extends ResourcesResponderRequestV1

/**
  * Requests a [[ResourceContextResponseV1]] describing the context of a resource (i.e. the resources that are part of it).
  *
  * @param iri         the IRI of the resource to be queried.
  * @param userProfile the profile of the user making the request.
  * @param resinfo     if `true`, the [[ResourceContextResponseV1]] will include a [[ResourceInfoV1]].
  */
case class ResourceContextGetRequestV1(iri: IRI, userProfile: UserProfileV1, resinfo: Boolean) extends ResourcesResponderRequestV1

/**
  * Requests the permissions for the current user on the given resource. A successful response will be a [[ResourceRightsResponseV1]].
  *
  * @param iri         the IRI of the resource to be queried.
  * @param userProfile the profile of the user making the request.
  */
case class ResourceRightsGetRequestV1(iri: IRI, userProfile: UserProfileV1) extends ResourcesResponderRequestV1

/**
  * Requests a search for resources matching the given string.
  *
  * @param searchString    the string to search for.
  * @param resourceTypeIri if set, restrict search to this resource class.
  * @param numberOfProps   the amount of describing properties to be returned for each found resource (e.g if set to two, for an incunabula book its title and creator would be returned).
  * @param limitOfResults  limits number of resources to be returned.
  * @param userProfile     the profile of the user making the request.
  */
case class ResourceSearchGetRequestV1(searchString: String, resourceTypeIri: Option[IRI], numberOfProps: Int, limitOfResults: Int, userProfile: UserProfileV1) extends ResourcesResponderRequestV1

/**
  * Requests the creation of a new resource of the given type with the given properties.
  *
  * @param resourceTypeIri the type of the new resource.
  * @param label           the rdfs:label of the resource.
  * @param values          the properties to add: type and value(s): a Map of propertyIris to ApiValueV1.
  * @param projectIri      the IRI of the project the resources is added to.
  * @param userProfile     the profile of the user making the request.
  * @param apiRequestID    the ID of the API request.
  */
case class ResourceCreateRequestV1(resourceTypeIri: IRI, label: String, values: Map[IRI, Seq[CreateValueV1WithComment]], file: Option[SipiResponderConversionRequestV1] = None, projectIri: IRI, userProfile: UserProfileV1, apiRequestID: UUID) extends ResourcesResponderRequestV1

/**
  * Checks whether a resource belongs to a certain OWL class or to a subclass of that class. This message is used
  * internally by Knora, and is not part of Knora API v1. A successful response will be a [[ResourceCheckClassResponseV1]].
  *
  * @param resourceIri the IRI of the resource.
  * @param owlClass    the IRI of the OWL class to compare the resource's class to.
  * @param userProfile the profile of the user making the request.
  */
case class ResourceCheckClassRequestV1(resourceIri: IRI, owlClass: IRI, userProfile: UserProfileV1) extends ResourcesResponderRequestV1

/**
  * Represents a response to a [[ResourceCheckClassRequestV1]].
  *
  * @param isInClass `true` if the resource is in the specified OWL class or a subclass of that class.
  */
case class ResourceCheckClassResponseV1(isInClass: Boolean)

/**
  * Represents the Knora API v1 JSON response to a request for information about a resource.
  *
  * @param resource_info basic information about the resource.
  * @param rights        a permission code indicating what rights the user who made the request has on the resource.
  * @param userdata      information about the user that made the request.
  */
case class ResourceInfoResponseV1(resource_info: Option[ResourceInfoV1] = None,
                                  rights: Option[Int] = None,
                                  userdata: UserDataV1) extends KnoraResponseV1 {
    def toJsValue = ResourceV1JsonProtocol.resourceInfoResponseV1Format.write(this)
}

/**
  * Represents the Knora API v1 JSON response to a request for a full description of a resource, along with its
  * properties, their values, incoming references, and other information.
  *
  * @param resinfo  basic information about the resource.
  * @param resdata  additional information about the resource.
  * @param props    the resource's properties with their values.
  * @param incoming incoming references to the resource.
  * @param access   `OK` if the user has access to the resource, otherwise `NO_ACCESS`.
  * @param userdata information about the user that made the request.
  */
case class ResourceFullResponseV1(resinfo: Option[ResourceInfoV1] = None,
                                  resdata: Option[ResourceDataV1] = None,
                                  props: Option[PropsV1] = None,
                                  incoming: Seq[IncomingV1] = Nil,
                                  access: String,
                                  userdata: UserDataV1) extends KnoraResponseV1 {
    def toJsValue = ResourceV1JsonProtocol.resourceFullResponseV1Format.write(this)
}

/**
  * Describes the context of a resource, i.e. the resources that are part of the specified resource.
  *
  * @param resource_context resources relating to this resource via `knora-base:partOf`.
  * @param userdata         information about the user that made the request.
  */
case class ResourceContextResponseV1(resource_context: ResourceContextV1,
                                     userdata: UserDataV1) extends KnoraResponseV1 {
    def toJsValue = ResourceContextV1JsonProtocol.resourceContextResponseV1Format.write(this)
}


/**
  * Describes the permissions that the current user has on a given resurce.
  *
  * @param rights   the permissions for the given user on this resource
  * @param userdata information about the user that made the request.
  */
case class ResourceRightsResponseV1(rights: Option[Int],
                                    userdata: UserDataV1) extends KnoraResponseV1 {

    def toJsValue = ResourceV1JsonProtocol.resourceRightsResponseV1Format.write(this)
}

/**
  * Describes the answer to a resource search request [[ResourceSearchGetRequestV1]]:
  * the resources matching the given criteria (search string, resource class).
  *
  * @param resources the resorces that match the given given search criteria.
  * @param userdata  information about the user that made the request.
  */
case class ResourceSearchResponseV1(resources: Seq[ResourceSearchResultRowV1] = Vector.empty[ResourceSearchResultRowV1],
                                    userdata: UserDataV1) extends KnoraResponseV1 {

    def toJsValue = ResourceV1JsonProtocol.resourceSearchResponseV1Format.write(this)
}

/**
  * Describes the answer to a newly created resource [[ResourceCreateRequestV1]].
  *
  * @param res_id   the IRI of the new resource.
  * @param results  the values that have been attached to the resource. The key in the Map refers
  *                 to the property Iri and the Seq contains all instances of values of this type.
  * @param userdata information about the user that made the request.
  */
case class ResourceCreateResponseV1(res_id: IRI,
                                    results: Map[IRI, Seq[ResourceCreateValueResponseV1]] = Map.empty[IRI, Seq[ResourceCreateValueResponseV1]],
                                    userdata: UserDataV1) extends KnoraResponseV1 {
    def toJsValue = ResourceV1JsonProtocol.resourceCreateResponseV1Format.write(this)
}

/**
  * Requests the properties of a given resource.
  *
  * @param iri the iri of the given resource.
  */
case class PropertiesGetRequestV1(iri: IRI, userProfile: UserProfileV1) extends ResourcesResponderRequestV1


// TODO: refactor PropertiesGetResponseV1 (https://github.com/dhlab-basel/Knora/issues/134#issue-154443186)

/**
  * Describes the answer to a [[PropertiesGetRequestV1]].
  *
  * @param properties the properties of the specified resource.
  */
case class PropertiesGetResponseV1(properties: PropsGetV1) extends KnoraResponseV1 {
    def toJsValue = ResourceV1JsonProtocol.propertiesGetResponseV1Format.write(this)
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Components of messages

/**
  * Indicates whether a resource has parts, is part of another resource, or neither.
  */
object ResourceContextCodeV1 extends Enumeration {
    /**
      * Indicates that a resource has no parts and is not part of another resource.
      */
    val RESOURCE_CONTEXT_NONE = Value(0)

    /**
      * Indicates that a resource is part of another resource.
      */
    val RESOURCE_CONTEXT_IS_PARTOF = Value(1)

    /**
      * Indicates that a resource has parts.
      */
    val RESOURCE_CONTEXT_IS_COMPOUND = Value(2)

    object ResourceContextCodeV1Protocol extends DefaultJsonProtocol {

        implicit object ResourceContextCodeV1Format extends JsonFormat[ResourceContextCodeV1.Value] {
            def read(jsonVal: JsValue): Value = ???

            def write(contextCode: Value): JsValue = contextCode.id.toJson
        }

    }

}

/**
  * Describes the context of a resource, i.e. the other resources that are part of the queried resource.
  *
  * @param res_id           the IRI of each resource that is part of the queried resource.
  * @param resclass_name    obsolete.
  * @param preview          a thumbnail image of each resource that is part of the queried resource.
  * @param locations        full quality representations of the resource.
  * @param firstprop        the `rdfs:label` of each resource that is part of the queried resource.
  * @param region           unused, always an array of nulls.
  * @param context          indicates whether the queried resource is part of another resource, has parts of its own, or neither.
  * @param canonical_res_id the IRI of the containing resource.
  * @param resinfo          a [[ResourceInfoV1]] describing the containing resource.
  */
case class ResourceContextV1(res_id: Option[Seq[IRI]] = None,
                             resclass_name: Option[String] = None,
                             preview: Option[Seq[Option[LocationV1]]] = None,
                             locations: Option[Seq[Option[Seq[LocationV1]]]] = None,
                             firstprop: Option[Seq[Option[String]]] = None,
                             region: Option[Seq[Option[String]]] = None,
                             context: ResourceContextCodeV1.Value,
                             canonical_res_id: IRI,
                             resinfo: Option[ResourceInfoV1] = None,
                             parent_res_id: Option[IRI] = None,
                             parent_resinfo: Option[ResourceInfoV1] = None)

/**
  * Describes a resource that is part of the context of another resource. Used internally to construct instances of [[ResourceContextV1]].
  *
  * @param res_id    the IRI of a resource that is part of the queried resource.
  * @param preview   a thumbnail image of the resource represented by `res_id`.
  * @param locations full quality representations of the resource represented by `res_id` in various qualities.
  * @param firstprop the `rdfs:label` of the resource represented by `res_id`.
  * @param region    unused, always null.
  */
case class ResourceContextItemV1(res_id: IRI,
                                 preview: Option[LocationV1],
                                 locations: Option[Seq[LocationV1]],
                                 firstprop: Option[String],
                                 region: Option[String] = None)

/**
  * Represents basic information about a Knora resource, in Knora API v1 JSON.
  *
  * @param project_id            the IRI of the project that the resource is associated with.
  * @param person_id             the IRI of the resource's owner.
  * @param permissions           the permissions defined on the resource.
  * @param restype_id            the IRI of the resource's OWL class.
  * @param restype_name          same as `restype_id`.
  * @param restype_label         the label of the resource class.
  * @param restype_description   a description of the resource class.
  * @param restype_iconsrc       the URL of an icon for the resource class.
  * @param preview               the URL of a preview of the resource.
  * @param locations             if this resource has binary data, a list of the available representations of that data (e.g. resolutions of an image).
  * @param locdata               obsolete.
  * @param resclass_name         obsolete, always `object`.
  * @param resclass_has_location `true` if the resource has binary data.
  * @param lastmod               a timestamp of the last modification of the resource.
  * @param value_of              obsolete, always 0.
  * @param firstproperty         a string representation of the resource's first property.
  * @param regions               representation of regions pointing to this resource.
  */
case class ResourceInfoV1(project_id: IRI,
                          person_id: IRI,
                          permissions: Seq[(IRI, IRI)],
                          restype_id: IRI,
                          restype_name: Option[IRI] = None,
                          restype_label: Option[String] = None,
                          restype_description: Option[String] = None,
                          restype_iconsrc: Option[String] = None,
                          preview: Option[LocationV1] = None,
                          locations: Option[Seq[LocationV1]] = None,
                          locdata: Option[LocationV1] = None,
                          resclass_name: String = "object",
                          resclass_has_location: Boolean = false,
                          lastmod: String = "0000-00-00 00:00:00",
                          value_of: Int = 0,
                          firstproperty: Option[String] = None,
                          regions: Option[Seq[PropsGetForRegionV1]] = None)

/**
  * Provides additional information about a Knora resource, in Knora API v1 JSON.
  *
  * @param res_id        the IRI of the resource.
  * @param restype_name  the IRI of the resource's OWL class.
  * @param restype_label the `rdfs:label` of the resource's OWL class.
  * @param iconsrc       the icon of the resource's OWL class.
  * @param rights        a numeric code represting the permissions that the requesting user has on the resource.
  */
case class ResourceDataV1(res_id: IRI,
                          restype_name: IRI,
                          restype_label: Option[String] = None,
                          iconsrc: Option[String] = None,
                          rights: Option[Int])

/**
  * Represents information about a resource that has a reference to the queried resource.
  *
  * @param ext_res_id the IRI of the referring resource.
  * @param resinfo    a [[ResourceInfoV1]] describing the referring resource.
  * @param value      the `rdfs:label` of the referring resource.
  */
case class IncomingV1(ext_res_id: ExternalResourceIDV1,
                      resinfo: ResourceInfoV1,
                      value: Option[String])

/**
  * Represents an incoming reference from another resource.
  *
  * @param id  the IRI of the resource that is the source of the incoming reference.
  * @param pid the IRI of the property that points from the source resource to the target resource.
  */
case class ExternalResourceIDV1(id: IRI,
                                pid: IRI)

/**
  * Represents an available binary representation of the resource (e.g. an image at a particular resolution).
  *
  * @param format_name `JPEG`, `JPEG2000`, `TIFF`, etc.
  * @param origname    the name of the original file containing the binary data.
  * @param nx          the width of the image in pixels.
  * @param ny          the height of the image in pixels.
  * @param path        the URL from which this representation can be retrieved.
  */
case class LocationV1(format_name: String,
                      origname: String,
                      nx: Option[Int] = None,
                      ny: Option[Int] = None,
                      path: String,
                      fps: Int = 0,
                      duration: Int = 0,
                      protocol: String = "file")


/**
  * Represents a property of a resource.
  *
  * @param pid              the IRI of the property.
  * @param regular_property obsolete, always 1.
  * @param valuetype_id     the IRI of the OWL class of the values of this property.
  * @param guielement       the type of GUI element used to render this property.
  * @param is_annotation    obsolete, always 0.
  * @param label            the `rdfs:label` of this property.
  * @param attributes       HTML attributes for the GUI element used to render this property.
  * @param occurrence       the cardinality of this property: 1, 1-n, 0-1, or 0-n.
  * @param values           the property's literal values for this resource (may be an empty list).
  * @param value_ids        the IRIs of the value objects representing the property's values for this resource.
  * @param comments         any comments attached to the value objects.
  * @param value_restype    if the property's value is another resource, contains the `rdfs:label` of the OWL class
  *                         of each resource referred to.
  * @param value_firstprops if the property's value is another resource, contains the `rdfs:label` of each resource
  *                         referred to.
  * @param value_iconsrcs   if the property's value is another resource, contains the icon representing the OWL
  *                         class of each resource referred to.
  */
case class PropertyV1(pid: IRI,
                      regular_property: Int = 1,
                      valuetype_id: Option[IRI] = None,
                      guiorder: Option[Int] = None,
                      guielement: Option[String] = None,
                      is_annotation: String = "0",
                      label: Option[String] = None,
                      attributes: String = "",
                      occurrence: Option[String] = None,
                      values: Seq[ApiValueV1] = Nil,
                      value_ids: Seq[IRI] = Nil,
                      comments: Seq[String] = Nil,
                      value_restype: Seq[Option[String]] = Nil,
                      value_iconsrcs: Seq[Option[String]] = Nil,
                      value_firstprops: Seq[Option[String]] = Nil,
                      value_rights: Seq[Option[Int]],
                      locations: Seq[LocationV1] = Nil)

/**
  * Holds a list of [[PropertyV1]] objects representing the properties of a resource in
  * Knora API v1 format. In Knora API v1, we format these as a JSON object (a map of property IRIs to PropertyV1 objects)
  * rather than as a list, but future API versions will probably format them as a JSON array. This case class exists
  * to make it clearer that we are currently formatting this list in a particular way
  * (see [[ResourceV1JsonProtocol.PropsV1JsonFormat]]).
  *
  * @param properties a list of [[PropertyV1]] objects.
  */
case class PropsV1(properties: Seq[PropertyV1])

/**
  * Represents a property of a resource in the format as required for the properties route.
  *
  * @param pid           the IRI of the property.
  * @param label         the `rdfs:label` of this property.
  * @param valuetype_id  the IRI of the OWL class of the values of this property.
  * @param valuetype     a string indicating the OWL class of the values of this property.
  * @param guielement    the type of GUI element used to render this property.
  * @param attributes    HTML attributes for the GUI element used to render this property.
  * @param is_annotation obsolete, always 0.
  * @param values        the property's literal values for this resource (may be an empty list).
  */
case class PropertyGetV1(pid: IRI,
                         label: Option[String] = None,
                         valuetype_id: Option[IRI] = None,
                         valuetype: Option[String] = None,
                         guielement: Option[String] = None,
                         attributes: String = "",
                         is_annotation: String = "0",
                         values: Seq[PropertyGetValueV1])

/**
  * Represents the value of a property in the format as required for the properties route.
  *
  * @param person_id   the owner of the value.
  * @param comment     any comment attached to the value.
  * @param textval     the string representation of the value object.
  * @param value       literal(s) representing this value object.
  * @param id          the Iri of this value object.
  * @param lastmod     the date of the last modification of this value.
  * @param lastmod_utc the date of the last modification of this value as UTC.
  *
  */
case class PropertyGetValueV1(person_id: Option[IRI] = None,
                              comment: String,
                              textval: String,
                              value: ApiValueV1, // TODO: this is called 'val' in the old Salsah, but val is a keyword in scala
                              id: IRI,
                              lastmod: Option[String] = None,
                              lastmod_utc: Option[String] = None)

/**
  * Holds a list of [[PropertyGetV1]] objects representing the properties of a resource in the format as requested
  * by properties route (see [[ResourceV1JsonProtocol.PropsGetV1JsonFormat]]).
  *
  * @param properties a list of [[PropertyGetV1]] objects.
  */
case class PropsGetV1(properties: Seq[PropertyGetV1])

/**
  * Holds a list of [[PropertyGetV1]] objects representing the properties of a region in the format requested for the context query. If a resource
  * is pointed to by regions, these are returned in the resource's context query (`resinfo.regions`). Additionally, the region's Iri and the icon of its resource class are given
  * (see [[ResourceV1JsonProtocol.PropsGetForRegionV1JsonFormat]]).
  *
  * @param properties a list of [[PropertyGetV1]] objects.
  * @param res_id     the region's Iri.
  * @param iconsrc    the icon of the region's resource class.
  */
case class PropsGetForRegionV1(properties: Seq[PropertyGetV1], res_id: IRI, iconsrc: Option[String])

/**
  * Represents information about one resource matching the criteria of a [[ResourceSearchGetRequestV1]]
  *
  * @param id    Iri
  * @param value property value(s) of the resource (the amount depends on `numberOfProps` defined in [[ResourceSearchGetRequestV1]])
  */
case class ResourceSearchResultRowV1(id: IRI,
                                     value: Seq[String],
                                     rights: Option[Int] = None) {

    def toJsValue = ResourceV1JsonProtocol.resourceSearchResultV1Format.write(this)
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// GUI naming conversions

/**
  * Converts between SALSAH and Knora GUI naming conventions.
  */
object SalsahGuiConversions {
    /**
      * A [[Map]] of Knora IRIs to SALSAH GUI element names.
      */
    private val iris2SalsahGuiElements = Map(
        OntologyConstants.SalsahGui.SimpleText -> "text",
        OntologyConstants.SalsahGui.Textarea -> "textarea",
        OntologyConstants.SalsahGui.Pulldown -> "pulldown",
        OntologyConstants.SalsahGui.Slider -> "slider",
        OntologyConstants.SalsahGui.Spinbox -> "spinbox",
        OntologyConstants.SalsahGui.Searchbox -> "searchbox",
        OntologyConstants.SalsahGui.Date -> "date",
        OntologyConstants.SalsahGui.Geometry -> "geometry",
        OntologyConstants.SalsahGui.Colorpicker -> "colorpicker",
        OntologyConstants.SalsahGui.List -> "hlist",
        OntologyConstants.SalsahGui.Radio -> "radio",
        OntologyConstants.SalsahGui.Richtext -> "richtext",
        OntologyConstants.SalsahGui.Time -> "time",
        OntologyConstants.SalsahGui.Interval -> "interval",
        OntologyConstants.SalsahGui.Geonames -> "geoname",
        "fileupload" -> "fileupload" // this is hardcoded
    )

    /**
      * A [[Map]] of SALSAH GUI element names to their Knora IRIs.
      */
    private val salsahGuiElements2Iris = iris2SalsahGuiElements.map(_.swap)

    /**
      * Converts a Knora IRI representing a GUI element to the corresponding SALSAH GUI element name.
      *
      * @param iri the Knora IRI of the GUI element.
      * @return the corresponding SALSAH GUI element name.
      */
    def iri2SalsahGuiElement(iri: IRI): String = {
        iris2SalsahGuiElements.get(iri) match {
            case Some(salsahGuiElement) => salsahGuiElement
            case None => throw new InconsistentTriplestoreDataException(s"No SALSAH GUI element found for IRI: $iri")
        }
    }

    /**
      * Converts a SALSAH GUI element name to its Knora IRI.
      *
      * @param salsahGuiElement a SALSAH GUI element name.
      * @return the corresponding Knora IRI.
      */
    def salsahGuiElement2Iri(salsahGuiElement: String): IRI = {
        salsahGuiElements2Iris.get(salsahGuiElement) match {
            case Some(iri) => iri
            case None => throw new InconsistentTriplestoreDataException(s"No IRI found for SALSAH GUI element: $salsahGuiElement")
        }
    }
}

/**
  * Describes values that have been attached to a new resource.
  *
  * @param value the value that has been attached to the resource.
  * @param id    the value object Iri of the value.
  */
case class ResourceCreateValueResponseV1(value: ResourceCreateValueObjectResponseV1, id: IRI) {
    def toJsValue = ResourceV1JsonProtocol.resourceCreateValueResponseV1Format.write(this)
}

/**
  * Represents the possible value types to be returned to the client.
  */
object LiteralValueType extends Enumeration {
    type ValueType = Value
    val StringValue = Value(0, "string")
    val IntegerValue = Value(1, "integer")
    val FloatValue = Value(2, "float")

    object LiteralValueTypeV1Protocol extends DefaultJsonProtocol {

        implicit object LiteralValueTypeFormat extends JsonFormat[LiteralValueType.Value] {
            def read(jsonVal: JsValue): Value = ???

            def write(valueType: Value): JsValue = JsString(valueType.toString)
        }

    }

}

/**
  * Represents the value. All values have a textual representation. The other types depend on the current value type.
  *
  * @param textval        textual representation of the value.
  * @param ival           integer value if it is an [[IntegerValueV1]].
  * @param fval           float value if it is a [[FloatValueV1]].
  * @param dateval1       start date if it is a [[DateValueV1]].
  * @param dateval2       end date if it is a [[DateValueV1]].
  * @param dateprecision1 the start date's precision if it is a [[DateValueV1]].
  * @param dateprecision2 the end date's precision if it is a [[DateValueV1]].
  * @param calendar       the date's calendar if it is a [[DateValueV1]].
  * @param timeval1
  * @param timeval2
  * @param resource_id    the Iri of the new resource.
  * @param property_id    the Iri of the property the value belongs to.
  * @param person_id      the person that created the value.
  * @param order          the order of the value (valueHasOrder).
  */
case class ResourceCreateValueObjectResponseV1(textval: Map[LiteralValueType.Value, String],
                                               ival: Option[Map[LiteralValueType.Value, Int]] = None,
                                               fval: Option[Map[LiteralValueType.Value, Float]] = None,
                                               dateval1: Option[Map[LiteralValueType.Value, String]] = None,
                                               dateval2: Option[Map[LiteralValueType.Value, String]] = None,
                                               dateprecision1: Option[Map[LiteralValueType.Value, KnoraPrecisionV1.Value]] = None,
                                               dateprecision2: Option[Map[LiteralValueType.Value, KnoraPrecisionV1.Value]] = None,
                                               calendar: Option[Map[LiteralValueType.Value, KnoraCalendarV1.Value]] = None,
                                               timeval1: Option[Map[LiteralValueType.Value, Int]] = None,
                                               timeval2: Option[Map[LiteralValueType.Value, Int]] = None,
                                               resource_id: Map[LiteralValueType.Value, IRI],
                                               property_id: Map[LiteralValueType.Value, IRI],
                                               person_id: Map[LiteralValueType.Value, IRI],
                                               order: Map[LiteralValueType.Value, Int]) {
    // TODO: do we need to add geonames here?

    def toJsValue = ResourceV1JsonProtocol.resourceCreateValueObjectResponseV1Format.write(this)
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// JSON formatting

/**
  * A spray-json protocol for generating Knora API v1 JSON providing data about resources and their properties.
  */
object ResourceV1JsonProtocol extends DefaultJsonProtocol with NullOptions with SprayJsonSupport {

    import LiteralValueType.LiteralValueTypeV1Protocol._
    import org.knora.webapi.messages.v1.responder.usermessages.UserDataV1JsonProtocol._
    import org.knora.webapi.messages.v1.responder.valuemessages.ApiValueV1JsonProtocol._

    implicit val locationFormat: JsonFormat[LocationV1] = jsonFormat8(LocationV1)


    /**
      * Converts an optional list to an [[Option]] containing a tuple of the list's name and [[JsValue]]. The
      * [[Option]] will be a [[Some]] if the list is non-empty, or a [[None]] if the list is empty.
      *
      * @param name       the list's name.
      * @param list       the list.
      * @param jsValueFun a function that returns the list's [[JsValue]].
      * @return either a [[Some]] containing the list's name and [[JsValue]], or a [[None]].
      */
    private def list2JsonOption(name: String, list: Seq[Any], jsValueFun: () => JsValue): Option[(String, JsValue)] = {
        if (list.nonEmpty) {
            // We need jsValueFun so spray-json will know the list's type at compile time.
            Some(name -> jsValueFun())
        } else {
            None
        }
    }

    /**
      * Converts between [[PropsV1]] objects and [[JsValue]] objects.
      */
    implicit object PropsV1JsonFormat extends JsonFormat[PropsV1] {

        /**
          * Not implemented.
          */
        def read(jsonVal: JsValue) = ???

        /**
          * Converts a [[PropsV1]] into a [[JsValue]].
          *
          * @param propsV1 the [[PropsV1]].
          * @return a [[JsValue]].
          */
        def write(propsV1: PropsV1): JsValue = {
            // Convert each PropertyV1 object into a JsObject.
            val properties: Map[IRI, JsValue] = propsV1.properties.map {
                propertyV1 =>
                    val fields = Map(
                        "pid" -> propertyV1.pid.toJson,
                        "regular_property" -> propertyV1.regular_property.toJson,
                        "valuetype_id" -> propertyV1.valuetype_id.toJson,
                        "guiorder" -> propertyV1.guiorder.toJson,
                        "guielement" -> propertyV1.guielement.toJson,
                        "is_annotation" -> propertyV1.is_annotation.toJson,
                        "label" -> propertyV1.label.toJson,
                        "attributes" -> propertyV1.attributes.toJson,
                        "occurrence" -> propertyV1.occurrence.toJson) ++
                        // Don't generate JSON for these lists if they're empty.
                        list2JsonOption("values", propertyV1.values, () => propertyV1.values.toJson) ++
                        list2JsonOption("value_ids", propertyV1.value_ids, () => propertyV1.value_ids.toJson) ++
                        list2JsonOption("comments", propertyV1.comments, () => propertyV1.comments.toJson) ++
                        list2JsonOption("value_restype", propertyV1.value_restype, () => propertyV1.value_restype.toJson) ++
                        list2JsonOption("value_iconsrcs", propertyV1.value_iconsrcs, () => propertyV1.value_iconsrcs.toJson) ++
                        list2JsonOption("value_firstprops", propertyV1.value_firstprops, () => propertyV1.value_firstprops.toJson) ++
                        list2JsonOption("value_rights", propertyV1.value_rights, () => propertyV1.value_rights.toJson) ++
                        list2JsonOption("locations", propertyV1.locations, () => propertyV1.locations.toJson)
                    (propertyV1.pid, JsObject(fields))
            }(breakOut)

            JsObject(properties)
        }
    }

    /**
      * Converts between [[PropsGetV1]] objects and [[JsValue]] objects.
      */
    implicit object PropsGetV1JsonFormat extends JsonFormat[PropsGetV1] {

        /**
          * Not implemented.
          */
        def read(jsonVal: JsValue) = ???

        /**
          * Converts a [[PropsGetV1]] into a [[JsValue]].
          *
          * @param propsGetV1 the [[PropsGetV1]].
          * @return a [[JsValue]].
          */
        def write(propsGetV1: PropsGetV1): JsValue = {
            // Convert each PropertyGetV1 object into a JsObject.
            val properties: Map[IRI, JsValue] = propsGetV1.properties.map {
                (propertyGetV1: PropertyGetV1) =>
                    val fields = Map(
                        "pid" -> propertyGetV1.pid.toJson,
                        "label" -> propertyGetV1.label.toJson,
                        "valuetype_id" -> propertyGetV1.valuetype_id.toJson,
                        "valuetype" -> propertyGetV1.valuetype.toJson,
                        "guielement" -> propertyGetV1.guielement.toJson,
                        "is_annotation" -> propertyGetV1.is_annotation.toJson,
                        "attributes" -> propertyGetV1.attributes.toJson) ++
                        // Don't generate JSON for these lists if they're empty.
                        list2JsonOption("values", propertyGetV1.values, () => propertyGetV1.values.toJson)
                    (propertyGetV1.pid, JsObject(fields))
            }(breakOut)

            JsObject(properties)
        }

    }

    /**
      * Converts between [[PropsGetForRegionV1]] objects and [[JsValue]] objects.
      */
    implicit object PropsGetForRegionV1JsonFormat extends JsonFormat[PropsGetForRegionV1] {

        def getRequiredString(jsObj: JsObject, key: String): String = {
            jsObj.fields.get(key) match {
                case Some(JsString(str)) => str
                case _ => throw InvalidApiJsonException(s"missing or invalid '$key'")
            }
        }

        def getOptionalString(jsObj: JsObject, key: String): Option[String] = {
            jsObj.fields.get(key) match {
                case Some(JsString(str)) => Some(str)
                case Some(JsNull) => None
                case None => None
                case _ => throw InvalidApiJsonException(s"'$key' must be a string")
            }
        }

        /**
          * Converts a [[JsValue]] to a [[PropsGetForRegionV1]].
          *
          * @param jsonVal the [[JsValue]] to be converted.
          * @return a [[PropsGetForRegionV1]].
          */
        def read(jsonVal: JsValue) = {

            val jsonObj = jsonVal.asJsObject

            val properties: Map[String, JsValue] = jsonObj.fields - "res_id" - "iconsrc"

            val propsConverted: Seq[PropertyGetV1] = properties.map {
                case (propname: String, prop: JsValue) =>
                    val propObj = prop.asJsObject

                    PropertyGetV1(
                        pid = getRequiredString(propObj, "pid"),

                        label = getOptionalString(propObj, "label"),

                        /*values = propObj.fields.get("values") match {
                            case Some(JsArray(valuesVector)) => valuesVector.map(_.convertTo[PropertyGetValueV1])
                            case _ => throw InvalidApiJsonException("missing or invalid 'values'")
                        }*/

                        // TODO: create an empty vector because for now we cannot recreate an ApiValueV1 from a JsValue since the read method
                        // TODO: of ValueV1JsonFormat in ValueMessagesV1 cannot deduce its value type.
                        values = Vector.empty[PropertyGetValueV1]
                    )
            }.toSeq

            PropsGetForRegionV1(
                res_id = getRequiredString(jsonObj, "res_id"),
                iconsrc = getOptionalString(jsonObj, "iconsrc"),
                properties = propsConverted
            )
        }

        /**
          * Converts a [[PropsGetForRegionV1]] into a [[JsValue]].
          *
          * @param propsGetForRegionV1 the [[PropsGetForRegionV1]].
          * @return a [[JsValue]].
          */
        def write(propsGetForRegionV1: PropsGetForRegionV1): JsValue = {
            // Convert each PropertyGetV1 object into a JsObject.
            val properties: Map[IRI, JsValue] = propsGetForRegionV1.properties.map {
                (propertyGetV1: PropertyGetV1) =>
                    val fields = Map(
                        "pid" -> propertyGetV1.pid.toJson,
                        "label" -> propertyGetV1.label.toJson,
                        "valuetype_id" -> propertyGetV1.valuetype_id.toJson,
                        "valuetype" -> propertyGetV1.valuetype.toJson,
                        "guielement" -> propertyGetV1.guielement.toJson,
                        "is_annotation" -> propertyGetV1.is_annotation.toJson,
                        "attributes" -> propertyGetV1.attributes.toJson) ++
                        // Don't generate JSON for these lists if they're empty.
                        list2JsonOption("values", propertyGetV1.values, () => propertyGetV1.values.toJson)
                    (propertyGetV1.pid, JsObject(fields))
            }(breakOut)

            JsObject(properties ++ Map("res_id" -> propsGetForRegionV1.res_id.toJson, "iconsrc" -> propsGetForRegionV1.iconsrc.toJson)) // add res_id and iconsrc to response
        }
    }


    /**
      * Converts between [[ResourceInfoV1]] objects and [[JsValue]] objects.
      */
    implicit object ResourceInfoV1Format extends JsonFormat[ResourceInfoV1] {
        /**
          * Not implemented.
          */
        def read(jsonVal: JsValue) = ???

        /**
          * Converts a [[ResourceInfoV1]] into [[JsValue]] for formatting as JSON.
          *
          * @param resInfoV1 the [[ResourceInfoV1]] to be converted.
          * @return a [[JsValue]].
          */
        def write(resInfoV1: ResourceInfoV1): JsValue = {
            // Don't include the "firstproperty" field if we have no data for it.
            val firstProp: Option[(String, JsString)] = resInfoV1.firstproperty match {
                case Some(propVal) => Some("firstproperty" -> JsString(propVal))
                case None => None
            }

            val fields = Map(
                "project_id" -> resInfoV1.project_id.toJson,
                "person_id" -> resInfoV1.person_id.toJson,
                "permissions" -> JsArray(resInfoV1.permissions.map {
                    case (p, o) => JsObject(Map("permission" -> JsString(p), "granted_to" -> JsString(o)))
                }.toVector),
                "restype_id" -> resInfoV1.restype_id.toJson,
                "restype_name" -> resInfoV1.restype_name.toJson,
                "restype_label" -> resInfoV1.restype_label.toJson,
                "restype_description" -> resInfoV1.restype_description.toJson,
                "restype_iconsrc" -> resInfoV1.restype_iconsrc.toJson,
                "preview" -> resInfoV1.preview.toJson,
                "locations" -> resInfoV1.locations.toJson,
                "locdata" -> resInfoV1.locdata.toJson,
                "resclass_name" -> resInfoV1.resclass_name.toJson,
                "resclass_has_location" -> resInfoV1.resclass_has_location.toJson,
                "lastmod" -> resInfoV1.lastmod.toJson,
                "value_of" -> resInfoV1.value_of.toJson,
                "regions" -> resInfoV1.regions.toJson
            ) ++ firstProp

            JsObject(fields)
        }
    }

    implicit val createResourceValueV1Format: RootJsonFormat[CreateResourceValueV1] = jsonFormat9(CreateResourceValueV1)
    implicit val createResourceApiRequestV1Format: RootJsonFormat[CreateResourceApiRequestV1] = jsonFormat5(CreateResourceApiRequestV1)
    implicit val resourceInfoResponseV1Format: RootJsonFormat[ResourceInfoResponseV1] = jsonFormat3(ResourceInfoResponseV1)
    implicit val resourceDataV1Format: JsonFormat[ResourceDataV1] = jsonFormat5(ResourceDataV1)
    implicit val externalResourceIDV1Format: JsonFormat[ExternalResourceIDV1] = jsonFormat2(ExternalResourceIDV1)
    implicit val incomingV1Format: JsonFormat[IncomingV1] = jsonFormat3(IncomingV1)
    implicit val resourceFullResponseV1Format: RootJsonFormat[ResourceFullResponseV1] = jsonFormat6(ResourceFullResponseV1)
    implicit val propertiesGetValueV1Format: JsonFormat[PropertyGetValueV1] = jsonFormat7(PropertyGetValueV1)
    implicit val propertiesGetResponseV1Format: RootJsonFormat[PropertiesGetResponseV1] = jsonFormat1(PropertiesGetResponseV1)
    implicit val resourceRightsResponseV1Format: RootJsonFormat[ResourceRightsResponseV1] = jsonFormat2(ResourceRightsResponseV1)
    implicit val resourceSearchResultV1Format: RootJsonFormat[ResourceSearchResultRowV1] = jsonFormat3(ResourceSearchResultRowV1)
    implicit val resourceSearchResponseV1Format: RootJsonFormat[ResourceSearchResponseV1] = jsonFormat2(ResourceSearchResponseV1)
    implicit val resourceCreateValueObjectResponseV1Format: RootJsonFormat[ResourceCreateValueObjectResponseV1] = jsonFormat14(ResourceCreateValueObjectResponseV1)
    implicit val resourceCreateValueResponseV1Format: RootJsonFormat[ResourceCreateValueResponseV1] = jsonFormat2(ResourceCreateValueResponseV1)
    implicit val resourceCreateResponseV1Format: RootJsonFormat[ResourceCreateResponseV1] = jsonFormat3(ResourceCreateResponseV1)
}

/**
  * A spray-json protocol for generating resource context information in Knora API v1 JSON format.
  */
object ResourceContextV1JsonProtocol extends DefaultJsonProtocol {

    import ResourceContextCodeV1.ResourceContextCodeV1Protocol._
    import ResourceV1JsonProtocol._
    import org.knora.webapi.messages.v1.responder.usermessages.UserDataV1JsonProtocol._

    implicit val resourceContextV1Format: JsonFormat[ResourceContextV1] = jsonFormat11(ResourceContextV1)
    implicit val resourceContextResponseV1Format: RootJsonFormat[ResourceContextResponseV1] = jsonFormat2(ResourceContextResponseV1)
}