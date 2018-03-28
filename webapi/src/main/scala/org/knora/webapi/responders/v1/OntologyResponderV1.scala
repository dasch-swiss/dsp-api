/*
 * Copyright © 2015-2018 the contributors (see Contributors.md).
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

package org.knora.webapi.responders.v1

import akka.actor.Status
import akka.pattern._
import org.knora.webapi._
import org.knora.webapi.messages.admin.responder.projectsmessages.{ProjectADM, ProjectsGetRequestADM, ProjectsGetResponseADM}
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.v1.responder.ontologymessages._
import org.knora.webapi.messages.v1.responder.resourcemessages.SalsahGuiConversions
import org.knora.webapi.messages.v2.responder.SuccessResponseV2
import org.knora.webapi.messages.v2.responder.ontologymessages.Cardinality.KnoraCardinalityInfo
import org.knora.webapi.messages.v2.responder.ontologymessages._
import org.knora.webapi.responders.Responder
import org.knora.webapi.util.ActorUtil._
import org.knora.webapi.util.IriConversions._
import org.knora.webapi.util.SmartIri

import scala.concurrent.Future

/**
  * Handles requests for information about ontology entities.
  *
  * All ontology data is loaded and cached when the application starts. To refresh the cache, you currently have to restart
  * the application.
  */
class OntologyResponderV1 extends Responder {

    private val valueUtilV1 = new ValueUtilV1(settings)

    /**
      * Receives a message extending [[OntologyResponderRequestV1]], and returns an appropriate response message, or
      * [[Status.Failure]]. If a serious error occurs (i.e. an error that isn't the client's fault), this
      * method first returns `Failure` to the sender, then throws an exception.
      */
    def receive = {
        case LoadOntologiesRequest(userProfile) => future2Message(sender(), loadOntologies(userProfile), log)
        case EntityInfoGetRequestV1(resourceIris, propertyIris, userProfile) => future2Message(sender(), getEntityInfoResponseV1(resourceIris, propertyIris, userProfile), log)
        case ResourceTypeGetRequestV1(resourceTypeIri, userProfile) => future2Message(sender(), getResourceTypeResponseV1(resourceTypeIri, userProfile), log)
        case checkSubClassRequest: CheckSubClassRequestV1 => future2Message(sender(), checkSubClass(checkSubClassRequest), log)
        case subClassesGetRequest: SubClassesGetRequestV1 => future2Message(sender(), getSubClasses(subClassesGetRequest), log)
        case NamedGraphsGetRequestV1(userProfile) => future2Message(sender(), getNamedGraphs(userProfile), log)
        case ResourceTypesForNamedGraphGetRequestV1(namedGraphIri, userProfile) => future2Message(sender(), getResourceTypesForNamedGraph(namedGraphIri, userProfile), log)
        case PropertyTypesForNamedGraphGetRequestV1(namedGraphIri, userProfile) => future2Message(sender(), getPropertyTypesForNamedGraph(namedGraphIri, userProfile), log)
        case PropertyTypesForResourceTypeGetRequestV1(restypeId, userProfile) => future2Message(sender(), getPropertyTypesForResourceType(restypeId, userProfile), log)
        case StandoffEntityInfoGetRequestV1(standoffClassIris, standoffPropertyIris, userProfile) => future2Message(sender(), getStandoffEntityInfoResponseV1(standoffClassIris, standoffPropertyIris, userProfile), log)
        case StandoffClassesWithDataTypeGetRequestV1(userProfile) => future2Message(sender(), getStandoffStandoffClassesWithDataTypeV1(userProfile), log)
        case StandoffAllPropertiesGetRequestV1(userProfile) => future2Message(sender(), getAllStandoffPropertyEntities(userProfile), log)
        case NamedGraphEntityInfoRequestV1(namedGraphIri, userProfile) => future2Message(sender(), getNamedGraphEntityInfoV1ForNamedGraph(namedGraphIri, userProfile), log)
        case other => handleUnexpectedMessage(sender(), other, log, this.getClass.getName)
    }

    /**
      * Loads and caches all ontology information.
      *
      * @param userProfile the profile of the user making the request.
      * @return a [[LoadOntologiesResponse]].
      */
    private def loadOntologies(userProfile: UserADM): Future[LoadOntologiesResponse] = {

        for {
            // forward the request to the v2 ontologies responder
            successResponse: SuccessResponseV2 <- (responderManager ? LoadOntologiesRequestV2(userProfile)).mapTo[SuccessResponseV2]

        } yield LoadOntologiesResponse()
    }

    /**
      * Wraps OWL class information from `OntologyResponderV2` for use in API v1.
      */
    private def classInfoMapV2ToV1(classInfoMap: Map[SmartIri, ReadClassInfoV2]): Map[IRI, ClassInfoV1] = {
        classInfoMap.map {
            case (smartIri, classInfoV2) => smartIri.toString -> new ClassInfoV1(classInfoV2)
        }
    }

    /**
      * Wraps OWL property information from `OntologyResponderV2` for use in API v1.
      */
    private def propertyInfoMapV2ToV1(propertyInfoMap: Map[SmartIri, ReadPropertyInfoV2]): Map[IRI, PropertyInfoV1] = {
        propertyInfoMap.map {
            case (smartIri, propertyInfoV2) => smartIri.toString -> new PropertyInfoV1(propertyInfoV2)
        }
    }

    /**
      * Given a list of resource IRIs and a list of property IRIs (ontology entities), returns an [[EntityInfoGetResponseV1]] describing both resource and property entities.
      *
      * @param resourceClassIris the IRIs of the resource entities to be queried.
      * @param propertyIris      the IRIs of the property entities to be queried.
      * @param userProfile       the profile of the user making the request.
      * @return an [[EntityInfoGetResponseV1]].
      */
    private def getEntityInfoResponseV1(resourceClassIris: Set[IRI] = Set.empty[IRI], propertyIris: Set[IRI] = Set.empty[IRI], userProfile: UserADM): Future[EntityInfoGetResponseV1] = {
        for {
            response: EntityInfoGetResponseV2 <- (responderManager ? EntityInfoGetRequestV2(resourceClassIris.map(_.toSmartIri), propertyIris.map(_.toSmartIri), userProfile)).mapTo[EntityInfoGetResponseV2]
        } yield EntityInfoGetResponseV1(resourceClassInfoMap = classInfoMapV2ToV1(response.classInfoMap), propertyInfoMap = propertyInfoMapV2ToV1(response.propertyInfoMap))
    }


    /**
      * Given a list of standoff class IRIs and a list of property IRIs (ontology entities), returns an [[StandoffEntityInfoGetResponseV1]] describing both resource and property entities.
      *
      * @param standoffClassIris    the IRIs of the resource entities to be queried.
      * @param standoffPropertyIris the IRIs of the property entities to be queried.
      * @param userProfile          the profile of the user making the request.
      * @return an [[EntityInfoGetResponseV1]].
      */
    private def getStandoffEntityInfoResponseV1(standoffClassIris: Set[IRI] = Set.empty[IRI], standoffPropertyIris: Set[IRI] = Set.empty[IRI], userProfile: UserADM): Future[StandoffEntityInfoGetResponseV1] = {
        for {
            response: StandoffEntityInfoGetResponseV2 <- (responderManager ? StandoffEntityInfoGetRequestV2(standoffClassIris.map(_.toSmartIri), standoffPropertyIris.map(_.toSmartIri), userProfile)).mapTo[StandoffEntityInfoGetResponseV2]


        } yield StandoffEntityInfoGetResponseV1(standoffClassInfoMap = classInfoMapV2ToV1(response.standoffClassInfoMap), standoffPropertyInfoMap = propertyInfoMapV2ToV1(response.standoffPropertyInfoMap))
    }

    /**
      * Gets information about all standoff classes that are a subclass of a data type standoff class.
      *
      * @param userProfile the profile of the user making the request.
      * @return a [[StandoffClassesWithDataTypeGetResponseV1]]
      */
    private def getStandoffStandoffClassesWithDataTypeV1(userProfile: UserADM): Future[StandoffClassesWithDataTypeGetResponseV1] = {
        for {
            response: StandoffClassesWithDataTypeGetResponseV2 <- (responderManager ? StandoffClassesWithDataTypeGetRequestV2(userProfile)).mapTo[StandoffClassesWithDataTypeGetResponseV2]
        } yield StandoffClassesWithDataTypeGetResponseV1(standoffClassInfoMap = classInfoMapV2ToV1(response.standoffClassInfoMap))
    }

    /**
      * Gets all standoff property entities.
      *
      * @param userProfile the profile of the user making the request.
      * @return a [[StandoffAllPropertiesGetResponseV1]].
      */
    private def getAllStandoffPropertyEntities(userProfile: UserADM): Future[StandoffAllPropertiesGetResponseV1] = {
        for {
            response: StandoffAllPropertyEntitiesGetResponseV2 <- (responderManager ? StandoffAllPropertyEntitiesGetRequestV2(userProfile)).mapTo[StandoffAllPropertyEntitiesGetResponseV2]
        } yield StandoffAllPropertiesGetResponseV1(standoffAllPropertiesInfoMap = propertyInfoMapV2ToV1(response.standoffAllPropertiesEntityInfoMap))
    }

    /**
      * Given the IRI of a resource type, returns a [[ResourceTypeResponseV1]] describing the resource type and its possible
      * properties.
      *
      * @param resourceTypeIri the IRI of the resource type to be queried.
      * @param userProfile     the profile of the user making the request.
      * @return a [[ResourceTypeResponseV1]].
      */
    private def getResourceTypeResponseV1(resourceTypeIri: String, userProfile: UserADM): Future[ResourceTypeResponseV1] = {

        for {
            // Get all information about the resource type, including its property cardinalities.
            resourceClassInfoResponse: EntityInfoGetResponseV1 <- getEntityInfoResponseV1(resourceClassIris = Set(resourceTypeIri), userProfile = userProfile)
            resourceClassInfo: ClassInfoV1 = resourceClassInfoResponse.resourceClassInfoMap.getOrElse(resourceTypeIri, throw NotFoundException(s"Resource class $resourceTypeIri not found"))

            // Get all information about those properties.
            propertyInfo: EntityInfoGetResponseV1 <- getEntityInfoResponseV1(propertyIris = resourceClassInfo.knoraResourceCardinalities.keySet, userProfile = userProfile)

            // Build the property definitions.
            propertyDefinitions: Vector[PropertyDefinitionV1] = resourceClassInfo.knoraResourceCardinalities.filterNot {
                // filter out the properties that point to LinkValue objects
                case (propertyIri, _) =>
                    resourceClassInfo.linkValueProperties(propertyIri) || propertyIri == OntologyConstants.KnoraBase.HasStandoffLinkTo
            }.map {
                case (propertyIri: IRI, cardinalityInfo: KnoraCardinalityInfo) =>
                    propertyInfo.propertyInfoMap.get(propertyIri) match {
                        case Some(entityInfo: PropertyInfoV1) =>

                            if (entityInfo.isLinkProp) {
                                // It is a linking prop: its valuetype_id is knora-base:LinkValue.
                                // It is restricted to the resource class that is given for knora-base:objectClassConstraint
                                // for the given property which goes in the attributes that will be read by the GUI.

                                PropertyDefinitionV1(
                                    id = propertyIri,
                                    name = propertyIri,
                                    label = entityInfo.getPredicateObject(predicateIri = OntologyConstants.Rdfs.Label, preferredLangs = Some(userProfile.lang, settings.fallbackLanguage)),
                                    description = entityInfo.getPredicateObject(predicateIri = OntologyConstants.Rdfs.Comment, preferredLangs = Some(userProfile.lang, settings.fallbackLanguage)),
                                    vocabulary = entityInfo.ontologyIri,
                                    occurrence = cardinalityInfo.cardinality.toString,
                                    valuetype_id = OntologyConstants.KnoraBase.LinkValue,
                                    attributes = valueUtilV1.makeAttributeString(entityInfo.getPredicateStringObjectsWithoutLang(OntologyConstants.SalsahGui.GuiAttribute) + valueUtilV1.makeAttributeRestype(entityInfo.getPredicateObject(OntologyConstants.KnoraBase.ObjectClassConstraint).getOrElse(throw InconsistentTriplestoreDataException(s"Property $propertyIri has no knora-base:objectClassConstraint")))),
                                    gui_name = entityInfo.getPredicateObject(OntologyConstants.SalsahGui.GuiElementProp).map(iri => SalsahGuiConversions.iri2SalsahGuiElement(iri)),
                                    guiorder = cardinalityInfo.guiOrder
                                )

                            } else {

                                PropertyDefinitionV1(
                                    id = propertyIri,
                                    name = propertyIri,
                                    label = entityInfo.getPredicateObject(predicateIri = OntologyConstants.Rdfs.Label, preferredLangs = Some(userProfile.lang, settings.fallbackLanguage)),
                                    description = entityInfo.getPredicateObject(predicateIri = OntologyConstants.Rdfs.Comment, preferredLangs = Some(userProfile.lang, settings.fallbackLanguage)),
                                    vocabulary = entityInfo.ontologyIri,
                                    occurrence = cardinalityInfo.cardinality.toString,
                                    valuetype_id = entityInfo.getPredicateObject(OntologyConstants.KnoraBase.ObjectClassConstraint).getOrElse(throw InconsistentTriplestoreDataException(s"Property $propertyIri has no knora-base:objectClassConstraint")),
                                    attributes = valueUtilV1.makeAttributeString(entityInfo.getPredicateStringObjectsWithoutLang(OntologyConstants.SalsahGui.GuiAttribute)),
                                    gui_name = entityInfo.getPredicateObject(OntologyConstants.SalsahGui.GuiElementProp).map(iri => SalsahGuiConversions.iri2SalsahGuiElement(iri)),
                                    guiorder = cardinalityInfo.guiOrder
                                )
                            }
                        case None =>
                            throw new InconsistentTriplestoreDataException(s"Resource type $resourceTypeIri is defined as having property $propertyIri, which doesn't exist")
                    }
            }.toVector.sortBy(_.guiorder)

            // Build the API response.
            resourceTypeResponse = ResourceTypeResponseV1(
                restype_info = ResTypeInfoV1(
                    name = resourceTypeIri,
                    label = resourceClassInfo.getPredicateObject(predicateIri = OntologyConstants.Rdfs.Label, preferredLangs = Some(userProfile.lang, settings.fallbackLanguage)),
                    description = resourceClassInfo.getPredicateObject(predicateIri = OntologyConstants.Rdfs.Comment, preferredLangs = Some(userProfile.lang, settings.fallbackLanguage)),
                    iconsrc = resourceClassInfo.getPredicateObject(OntologyConstants.KnoraBase.ResourceIcon),
                    properties = propertyDefinitions
                )
            )
        } yield resourceTypeResponse
    }

    /**
      * Checks whether a certain Knora resource or value class is a subclass of another class.
      *
      * @param checkSubClassRequest a [[CheckSubClassRequestV1]]
      * @return a [[CheckSubClassResponseV1]].
      */
    private def checkSubClass(checkSubClassRequest: CheckSubClassRequestV1): Future[CheckSubClassResponseV1] = {
        for {
            response: CheckSubClassResponseV2 <- (responderManager ? CheckSubClassRequestV2(subClassIri = checkSubClassRequest.subClassIri.toSmartIri, superClassIri = checkSubClassRequest.superClassIri.toSmartIri, checkSubClassRequest.userProfile)).mapTo[CheckSubClassResponseV2]

        } yield CheckSubClassResponseV1(response.isSubClass)
    }

    /**
      * Gets the IRIs of the subclasses of a resource class.
      *
      * @param getSubClassesRequest a [[SubClassesGetRequestV1]].
      * @return a [[SubClassesGetResponseV1]].
      */
    private def getSubClasses(getSubClassesRequest: SubClassesGetRequestV1): Future[SubClassesGetResponseV1] = {
        for {
            response: SubClassesGetResponseV2 <- (responderManager ? SubClassesGetRequestV2(getSubClassesRequest.resourceClassIri.toSmartIri, getSubClassesRequest.userProfile)).mapTo[SubClassesGetResponseV2]

            subClasses = response.subClasses.map {
                subClassInfoV2 => SubClassInfoV1(
                    id = subClassInfoV2.id.toString,
                    label = subClassInfoV2.label
                )
            }

        } yield SubClassesGetResponseV1(subClasses)
    }

    /**
      * Returns all the existing named graphs as a [[NamedGraphsResponseV1]].
      *
      * @param userProfile the profile of the user making the request.
      * @return a [[NamedGraphsResponseV1]].
      */
    private def getNamedGraphs(userProfile: UserADM): Future[NamedGraphsResponseV1] = {

        for {
            projectsResponse <- (responderManager ? ProjectsGetRequestADM(userProfile)).mapTo[ProjectsGetResponseADM]
            readOntologyMetadataV2 <- (responderManager ? OntologyMetadataGetRequestV2(requestingUser = userProfile)).mapTo[ReadOntologyMetadataV2]

            projectsMap: Map[IRI, ProjectADM] = projectsResponse.projects.map {
                project => project.id -> project
            }.toMap

            namedGraphs: Seq[NamedGraphV1] = readOntologyMetadataV2.ontologies.toVector.map(_.toOntologySchema(InternalSchema)).filter {
                ontologyMetadata =>
                    // In V1, the only built-in ontology we show is knora-base.
                    val ontologyLabel = ontologyMetadata.ontologyIri.getOntologyName
                    ontologyLabel == OntologyConstants.KnoraBase.KnoraBaseOntologyLabel || !OntologyConstants.BuiltInOntologyLabels.contains(ontologyLabel)
            }.map {
                ontologyMetadata =>
                    val project = projectsMap(ontologyMetadata.projectIri.get.toString)

                    NamedGraphV1(
                        id = ontologyMetadata.ontologyIri.toString,
                        shortname = project.shortname,
                        longname = project.longname.getOrElse(throw InconsistentTriplestoreDataException(s"Project ${project.id} has no longname")),
                        description = project.description.headOption.getOrElse(throw InconsistentTriplestoreDataException(s"Project ${project.id} has no description")).toString,
                        project_id = project.id,
                        uri = ontologyMetadata.ontologyIri.toString,
                        active = project.status
                    )
            }

            response = NamedGraphsResponseV1(
                vocabularies = namedGraphs
            )
        } yield response
    }

    /**
      * Gets the [[NamedGraphEntityInfoV1]] for a named graph
      *
      * @param namedGraphIri the IRI of the named graph to query
      * @param userProfile   the profile of the user making the request.
      * @return a [[NamedGraphEntityInfoV1]].
      */
    def getNamedGraphEntityInfoV1ForNamedGraph(namedGraphIri: IRI, userProfile: UserADM): Future[NamedGraphEntityInfoV1] = {
        for {
            response: OntologyKnoraEntitiesIriInfoV2 <- (responderManager ? OntologyKnoraEntityIrisGetRequestV2(namedGraphIri.toSmartIri, userProfile)).mapTo[OntologyKnoraEntitiesIriInfoV2]

            classIrisForV1 = response.classIris.map(_.toString) -- OntologyConstants.KnoraBase.AbstractResourceClasses
            propertyIrisForV1 = response.propertyIris.map(_.toString) - OntologyConstants.KnoraBase.ResourceProperty

        } yield NamedGraphEntityInfoV1(
            namedGraphIri = response.ontologyIri.toString,
            resourceClasses = classIrisForV1,
            propertyIris = propertyIrisForV1
        )
    }

    /**
      * Gets all the resource classes and their properties for a named graph.
      *
      * @param namedGraphIriOption the IRI of the named graph or None if all the named graphs should be queried.
      * @param userProfile         the profile of the user making the request.
      * @return [[ResourceTypesForNamedGraphResponseV1]].
      */
    private def getResourceTypesForNamedGraph(namedGraphIriOption: Option[IRI], userProfile: UserADM): Future[ResourceTypesForNamedGraphResponseV1] = {

        // get the resource types for a named graph
        def getResourceTypes(namedGraphIri: IRI): Future[Seq[ResourceTypeV1]] = {
            for {

                // get NamedGraphEntityInfoV1 for the given named graph
                namedGraphEntityInfo: NamedGraphEntityInfoV1 <- getNamedGraphEntityInfoV1ForNamedGraph(namedGraphIri, userProfile)

                // get resinfo for each resource class in namedGraphEntityInfo
                resInfosForNamedGraphFuture: Set[Future[(String, ResourceTypeResponseV1)]] = namedGraphEntityInfo.resourceClasses.map {
                    (resClassIri) =>
                        for {
                            resInfo <- getResourceTypeResponseV1(resClassIri, userProfile)
                        } yield (resClassIri, resInfo)
                }

                resInfosForNamedGraph: Set[(IRI, ResourceTypeResponseV1)] <- Future.sequence(resInfosForNamedGraphFuture)

                resourceTypes: Vector[ResourceTypeV1] = resInfosForNamedGraph.map {
                    case (resClassIri, resInfo) =>

                        val properties = resInfo.restype_info.properties.map {
                            (prop) =>
                                PropertyTypeV1(
                                    id = prop.id,
                                    label = prop.label.getOrElse(throw InconsistentTriplestoreDataException(s"No label given for ${prop.id}"))
                                )
                        }.toVector

                        ResourceTypeV1(
                            id = resClassIri,
                            label = resInfo.restype_info.label.getOrElse(throw InconsistentTriplestoreDataException(s"No label given for $resClassIri")),
                            properties = properties
                        )
                }.toVector

            } yield resourceTypes
        }

        // get resource types for named graph depending on given IRI-Option
        namedGraphIriOption match {
            case Some(namedGraphIri) => // get the resource types for the given named graph
                for {
                    resourceTypes <- getResourceTypes(namedGraphIri)
                } yield ResourceTypesForNamedGraphResponseV1(resourcetypes = resourceTypes)

            case None => // map over all named graphs and collect the resource types
                for {
                    projectNamedGraphsResponse: NamedGraphsResponseV1 <- getNamedGraphs(userProfile)
                    projectNamedGraphIris: Seq[IRI] = projectNamedGraphsResponse.vocabularies.map(_.uri)
                    resourceTypesPerProject: Seq[Future[Seq[ResourceTypeV1]]] = projectNamedGraphIris.map(iri => getResourceTypes(iri))
                    resourceTypes: Seq[Seq[ResourceTypeV1]] <- Future.sequence(resourceTypesPerProject)
                } yield ResourceTypesForNamedGraphResponseV1(resourcetypes = resourceTypes.flatten)
        }

    }

    /**
      * Gets the property types defined in the given named graph. If there is no named graph defined, get property types for all existing named graphs.
      *
      * @param namedGraphIriOption the IRI of the named graph or None if all the named graphs should be queried.
      * @param userProfile         the profile of the user making the request.
      * @return a [[PropertyTypesForNamedGraphResponseV1]].
      */
    private def getPropertyTypesForNamedGraph(namedGraphIriOption: Option[IRI], userProfile: UserADM): Future[PropertyTypesForNamedGraphResponseV1] = {

        def getPropertiesForNamedGraph(namedGraphIri: IRI, userProfile: UserADM): Future[Seq[PropertyDefinitionInNamedGraphV1]] = {
            for {
                namedGraphEntityInfo <- getNamedGraphEntityInfoV1ForNamedGraph(namedGraphIri, userProfile)
                propertyIris: Set[IRI] = namedGraphEntityInfo.propertyIris
                entities: EntityInfoGetResponseV1 <- getEntityInfoResponseV1(propertyIris = propertyIris, userProfile = userProfile)
                propertyInfoMap: Map[IRI, PropertyInfoV1] = entities.propertyInfoMap.filterNot {
                    case (propertyIri, propertyEntityInfo) => propertyEntityInfo.isLinkValueProp
                }

                propertyDefinitions: Vector[PropertyDefinitionInNamedGraphV1] = propertyInfoMap.map {
                    case (propertyIri: IRI, entityInfo: PropertyInfoV1) =>

                        if (entityInfo.isLinkProp) {
                            // It is a linking prop: its valuetype_id is knora-base:LinkValue.
                            // It is restricted to the resource class that is given for knora-base:objectClassConstraint
                            // for the given property which goes in the attributes that will be read by the GUI.

                            PropertyDefinitionInNamedGraphV1(
                                id = propertyIri,
                                name = propertyIri,
                                label = entityInfo.getPredicateObject(predicateIri = OntologyConstants.Rdfs.Label, preferredLangs = Some(userProfile.lang, settings.fallbackLanguage)),
                                description = entityInfo.getPredicateObject(predicateIri = OntologyConstants.Rdfs.Comment, preferredLangs = Some(userProfile.lang, settings.fallbackLanguage)),
                                vocabulary = entityInfo.ontologyIri,
                                valuetype_id = OntologyConstants.KnoraBase.LinkValue,
                                attributes = valueUtilV1.makeAttributeString(entityInfo.getPredicateStringObjectsWithoutLang(OntologyConstants.SalsahGui.GuiAttribute) + valueUtilV1.makeAttributeRestype(entityInfo.getPredicateObject(OntologyConstants.KnoraBase.ObjectClassConstraint).getOrElse(throw InconsistentTriplestoreDataException(s"Property $propertyIri has no knora-base:objectClassConstraint")))),
                                gui_name = entityInfo.getPredicateObject(OntologyConstants.SalsahGui.GuiElementProp).map(iri => SalsahGuiConversions.iri2SalsahGuiElement(iri))
                            )

                        } else {
                            PropertyDefinitionInNamedGraphV1(
                                id = propertyIri,
                                name = propertyIri,
                                label = entityInfo.getPredicateObject(predicateIri = OntologyConstants.Rdfs.Label, preferredLangs = Some(userProfile.lang, settings.fallbackLanguage)),
                                description = entityInfo.getPredicateObject(predicateIri = OntologyConstants.Rdfs.Comment, preferredLangs = Some(userProfile.lang, settings.fallbackLanguage)),
                                vocabulary = entityInfo.ontologyIri,
                                valuetype_id = entityInfo.getPredicateObject(OntologyConstants.KnoraBase.ObjectClassConstraint).getOrElse(throw InconsistentTriplestoreDataException(s"Property $propertyIri has no knora-base:objectClassConstraint")),
                                attributes = valueUtilV1.makeAttributeString(entityInfo.getPredicateStringObjectsWithoutLang(OntologyConstants.SalsahGui.GuiAttribute)),
                                gui_name = entityInfo.getPredicateObject(OntologyConstants.SalsahGui.GuiElementProp).map(iri => SalsahGuiConversions.iri2SalsahGuiElement(iri))
                            )

                        }

                }.toVector
            } yield propertyDefinitions
        }

        namedGraphIriOption match {
            case Some(namedGraphIri) => // get all the property types for the given named graph
                for {
                    propertyTypes <- getPropertiesForNamedGraph(namedGraphIri, userProfile)

                } yield PropertyTypesForNamedGraphResponseV1(properties = propertyTypes)
            case None => // get the property types for all named graphs (collect them by mapping over all named graphs)

                for {
                    projectNamedGraphsResponse: NamedGraphsResponseV1 <- getNamedGraphs(userProfile)
                    projectNamedGraphIris: Seq[IRI] = projectNamedGraphsResponse.vocabularies.map(_.uri)
                    propertyTypesPerProject: Seq[Future[Seq[PropertyDefinitionInNamedGraphV1]]] = projectNamedGraphIris.map(iri => getPropertiesForNamedGraph(iri, userProfile))
                    propertyTypes: Seq[Seq[PropertyDefinitionInNamedGraphV1]] <- Future.sequence(propertyTypesPerProject)
                } yield PropertyTypesForNamedGraphResponseV1(properties = propertyTypes.flatten)
        }

    }

    /**
      * Gets the property types defined for the given resource class.
      *
      * @param resourceClassIri the IRI of the resource class to query for.
      * @param userProfile      the profile of the user making the request.
      * @return a [[PropertyTypesForResourceTypeResponseV1]].
      */
    private def getPropertyTypesForResourceType(resourceClassIri: IRI, userProfile: UserADM): Future[PropertyTypesForResourceTypeResponseV1] = {
        for {
            resInfo: ResourceTypeResponseV1 <- getResourceTypeResponseV1(resourceClassIri, userProfile)
            propertyTypes = resInfo.restype_info.properties.toVector

        } yield PropertyTypesForResourceTypeResponseV1(properties = propertyTypes)

    }

}
