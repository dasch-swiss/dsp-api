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

package org.knora.webapi.responders.v1

import akka.actor.Status
import akka.pattern._
import org.apache.commons.lang3.builder.HashCodeBuilder
import org.knora.webapi._
import org.knora.webapi.messages.v1respondermessages.ontologymessages._
import org.knora.webapi.messages.v1respondermessages.resourcemessages.SalsahGuiConversions
import org.knora.webapi.messages.v1respondermessages.triplestoremessages.{SparqlSelectRequest, SparqlSelectResponse, VariableResultsRow}
import org.knora.webapi.messages.v1respondermessages.usermessages.UserProfileV1
import org.knora.webapi.util.ActorUtil._
import org.knora.webapi.util._

import scala.collection.breakOut
import scala.collection.immutable.Iterable
import scala.concurrent.Future

/**
  * Handles requests for information about ontology entities.
  *
  * The results of these requests are cached to improve performance. To clear the cache, you currently have to restart
  * the application. When updates are implemented in this responder, it will clear its cache on each update.
  */
class OntologyResponderV1 extends ResponderV1 {

    private val OntologyCacheName = "ontologyCache"
    private val knoraIriUtil = new KnoraIriUtil

    /**
      * This was used to query the named graphs in SPARQL. In the future, we may need it again.
      * Currently, we se a workaround (`getNamedGraph`).
      * // get a list of the named graphs for ontology information
      * private val namedGraphs: Seq[IRI] = settings.projectNamedGraphs.map {
      * case (project: IRI, namedGraph: ProjectNamedGraphs) => namedGraph.ontology
      * }.toList
      */

    /**
      * Keys for the ontology cache.
      */
    private object OntologyCacheKeys {

        /**
          * The type of ontology cache keys for instances of [[ResourceEntityInfoV1]].
          *
          * @param resourceClassIri the IRI of the resource class described.
          */
        class ResourceEntityInfoKey(val resourceClassIri: IRI) extends Ordered[ResourceEntityInfoKey] {
            def compare(that: ResourceEntityInfoKey): Int = {
                this.resourceClassIri.compare(that.resourceClassIri)
            }

            override def equals(that: Any): Boolean = {
                that match {
                    case otherEntityInfoKey: ResourceEntityInfoKey => resourceClassIri == otherEntityInfoKey.resourceClassIri
                    case _ => false
                }
            }

            override def hashCode(): Int = {
                new HashCodeBuilder(17, 37).append(resourceClassIri).toHashCode
            }

            override def toString: String = s"ResourceEntityInfoKey($resourceClassIri)"
        }

        /**
          * The type of ontology cache keys for instances of [[PropertyEntityInfoV1]].
          *
          * @param propertyIri the IRI of the property described.
          */
        class PropertyEntityInfoKey(val propertyIri: IRI) extends Ordered[PropertyEntityInfoKey] {
            def compare(that: PropertyEntityInfoKey): Int = {
                this.propertyIri.compare(that.propertyIri)
            }

            override def equals(that: Any): Boolean = {
                that match {
                    case otherEntityInfoKey: PropertyEntityInfoKey => propertyIri == otherEntityInfoKey.propertyIri
                    case _ => false
                }
            }

            override def hashCode(): Int = {
                new HashCodeBuilder(19, 39).append(propertyIri).toHashCode
            }

            override def toString: String = s"PropertyEntityInfoKey($propertyIri)"
        }
    }

    /**
      * Receives a message extending [[OntologyResponderRequestV1]], and returns an appropriate response message, or
      * [[Status.Failure]]. If a serious error occurs (i.e. an error that isn't the client's fault), this
      * method first returns `Failure` to the sender, then throws an exception.
      */
    def receive = {
        case EntityInfoGetRequestV1(resourceIris, propertyIris, userProfile) => future2Message(sender(), getEntityInfoResponseV1(resourceIris, propertyIris, userProfile), log)
        case ResourceTypeGetRequestV1(resourceTypeIri, userProfile) => future2Message(sender(), getResourceTypeResponseV1(resourceTypeIri, userProfile), log)
        case checkSubClassRequest: CheckSubClassRequestV1 => future2Message(sender(), checkSubClass(checkSubClassRequest), log)
        case other => sender ! Status.Failure(UnexpectedMessageException(s"Unexpected message $other of type ${other.getClass.getCanonicalName}"))
    }

    /**
      * Given a list of resource IRIs and a list of property IRIs (ontology entities), returns an [[EntityInfoGetResponseV1]] describing both resource and property entities.
      *
      * @param resourceIris the IRIs of the resource entities to be queried.
      * @param propertyIris the IRIs of the property entities to be queried.
      * @param userProfile  the profile of the user making the request.
      * @return an [[EntityInfoGetResponseV1]].
      */
    private def getEntityInfoResponseV1(resourceIris: Set[IRI] = Set.empty[IRI], propertyIris: Set[IRI] = Set.empty[IRI], userProfile: UserProfileV1): Future[EntityInfoGetResponseV1] = {

        /**
          * Gets the IRI of the ontology that an entity belongs to, assuming that the entity IRI has the form `http://something.something#entityName`.
          * The ontology IRI is assumed to be the part before the `#`. TODO: is this adequate?
          *
          * @param entityIri the entity IRI (e.g. `http://www.knora.org/ontology/incunabula#publisher`).
          * @return the part before the `#`.
          */
        def getOntologyIri(entityIri: IRI): String = {
            entityIri.split("#")(0)
        }

        /**
          * Queries the ontology for the given resource entity Iris and returns a Map of [[ResourceEntityInfoV1]] to be cached.
          *
          * @param resourceIris the resource entity Iris to be queried.
          * @return a Map of [[ResourceEntityInfoV1]] to be cached.
          */
        def queryResourceEntityInfoResponse(resourceIris: Set[OntologyCacheKeys.ResourceEntityInfoKey]): Future[Map[OntologyCacheKeys.ResourceEntityInfoKey, ResourceEntityInfoV1]] = {

            case class OwlCardinality(propertyIri: IRI, cardinalityIri: IRI, cardinalityValue: Int, isLinkProp: Boolean, isLinkValueProp: Boolean, isFileValueProp: Boolean)

            for {
            // get information about resource entities
                sparqlQueryStringForResources <- Future(queries.sparql.v1.txt.getEntityInfoForResource(resourceIris.map(_.resourceClassIri).toVector).toString())
                // _ = println(sparqlQueryStringForResources)
                resourcesResponse <- (storeManager ? SparqlSelectRequest(sparqlQueryStringForResources)).mapTo[SparqlSelectResponse]

                // Filter the query results to get text in the user's preferred language (or the application's default language),
                // if possible.
                resourcesFilteredByLanguage = SparqlUtil.filterByLanguage(response = resourcesResponse,
                    langSpecificColumnName = "o",
                    preferredLanguage = userProfile.userData.lang,
                    settings = settings
                )

                // Group the resource query results by subject (resource type).
                resourcesGroupedBySubject: Map[IRI, Seq[VariableResultsRow]] = resourcesFilteredByLanguage.groupBy(_.rowMap("s"))

                // Convert the results for each subject into an ResourceEntityInfoV1.
                resourceEntities: Map[OntologyCacheKeys.ResourceEntityInfoKey, ResourceEntityInfoV1] = resourcesGroupedBySubject.map {
                    case (resourceType: IRI, rows: Seq[VariableResultsRow]) =>

                        // group by predicates (e.g. rdfs:subClassOf, rdf:type)
                        val groupedByPredicate: Map[IRI, Seq[VariableResultsRow]] = rows.groupBy(_.rowMap("p"))

                        // get Restrictions expressing cardinalities for this resource type (defined as a subclass of a blank node)
                        val owlCardinalities: Iterable[OwlCardinality] = groupedByPredicate.get(OntologyConstants.Rdfs.SubclassOf) match {
                            case Some(superClasses: Seq[VariableResultsRow]) =>

                                // filter Seq by checking for the row "isCardinality".
                                // "isCardinality" may be set to "true" or not exist all.
                                // Therefore, we use "exist" on an Option[String] and pass it a function which converts the given String to a Boolean
                                // If the Option is None, false is returned withut evaluating the passed function.
                                superClasses.filter {
                                    row =>
                                        // Only return cardinalities for Knora value properties or Knora link properties (not for Knora system
                                        // properties).
                                        val rowMap = row.rowMap
                                        InputValidation.optionStringToBoolean(rowMap.get("isCardinality")) &&
                                            (InputValidation.optionStringToBoolean(rowMap.get("isKnoraValueProp")) ||
                                                InputValidation.optionStringToBoolean(rowMap.get("isLinkProp")))
                                }.groupBy(_.rowMap("o")).map {
                                    // get rid of the names of the blank nodes and group by property names (in order to access them directly later)
                                    case (blankNode: String, rows: Seq[VariableResultsRow]) =>

                                        rows.groupBy(_.rowMap("oProp"))
                                }.map {
                                    case (restriction: Map[String, Seq[VariableResultsRow]]) =>

                                        // How to use a Set as a function predicate: http://alvinalexander.com/scala/how-use-scala-set-function-predicate
                                        val owlCardinalityIri = restriction.keys.find(OntologyConstants.Owl.cardinalityOWLRestrictions).get // get the cardinalityIri (contained in Set cardinalityOWLRestrictions)
                                    val owlCardinalityRowMap = restriction(owlCardinalityIri).head.rowMap

                                        OwlCardinality(
                                            propertyIri = restriction(OntologyConstants.Owl.OnProperty).head.rowMap("oVal"), // we grouped by predicates before: get the value of "owl:onProperty"
                                            cardinalityIri = owlCardinalityIri,
                                            cardinalityValue = owlCardinalityRowMap("oVal").toInt,
                                            isLinkProp = owlCardinalityRowMap.get("isLinkProp").exists(_.toBoolean),
                                            isLinkValueProp = InputValidation.optionStringToBoolean(owlCardinalityRowMap.get("isLinkValueProp")),
                                            isFileValueProp = InputValidation.optionStringToBoolean(owlCardinalityRowMap.get("isFileValueProp"))
                                        )
                                }

                            // TODO: May there be a resource type without cardinalities?
                            case None => Nil
                        }

                        // Identify the link properties, like value properties, and file value properties in the cardinalities.
                        val linkProps = owlCardinalities.filter(_.isLinkProp).map(_.propertyIri).toSet
                        val linkValueProps = owlCardinalities.filter(_.isLinkValueProp).map(_.propertyIri).toSet
                        val fileValueProps = owlCardinalities.filter(_.isFileValueProp).map(_.propertyIri).toSet

                        // Make sure there is a link value property for each link property.
                        val missingLinkValueProps = linkProps.map(linkProp => knoraIriUtil.linkPropertyIriToLinkValuePropertyIri(linkProp)) -- linkValueProps
                        if (missingLinkValueProps.nonEmpty) {
                            throw InconsistentTriplestoreDataException(s"Resource class $resourceType has cardinalities for one or more link properties without corresponding link value properties. The missing link value property or properties: ${missingLinkValueProps.mkString(", ")}")
                        }

                        // Make sure there is a link property for each link value property.
                        val missingLinkProps = linkValueProps.map(linkValueProp => knoraIriUtil.linkValuePropertyIri2LinkPropertyIri(linkValueProp)) -- linkProps
                        if (missingLinkProps.nonEmpty) {
                            throw InconsistentTriplestoreDataException(s"Resource class $resourceType has cardinalities for one or more link value properties without corresponding link properties. The missing link property or properties: ${missingLinkProps.mkString(", ")}")
                        }

                        // Make a PredicateInfoV1 for each of the subject's predicates.
                        val predicates: Map[IRI, PredicateInfoV1] = groupedByPredicate.map {
                            case (predicateIri, predicateRowList) =>
                                predicateIri -> PredicateInfoV1(
                                    predicateIri = predicateIri,
                                    ontologyIri = getOntologyIri(predicateIri),
                                    // Don't include cardinalities among the predicates, because we return them separately.
                                    objects = predicateRowList.filterNot(row => row.rowMap.get("isCardinality").exists(_.toBoolean)).map(row => row.rowMap("o")).toSet
                                )
                        }

                        new OntologyCacheKeys.ResourceEntityInfoKey(resourceType) -> ResourceEntityInfoV1(
                            resourceIri = resourceType,
                            predicates = new ErrorHandlingMap(predicates, { key: IRI => s"Predicate $key not found for ontology entity $resourceType" }),
                            cardinalities = owlCardinalities.map {
                                owlCardinality =>
                                    // Convert the OWL cardinality to a Knora Cardinality enum value.
                                    val propertyIri = owlCardinality.propertyIri
                                    val owlCardinalityIri = owlCardinality.cardinalityIri
                                    val owlCardinalityValue = owlCardinality.cardinalityValue
                                    val card = Cardinality.owlCardinality2KnoraCardinality(propertyIri, owlCardinalityIri, owlCardinalityValue)
                                    propertyIri -> card
                            }.toMap,
                            linkProperties = linkProps,
                            linkValueProperties = linkValueProps,
                            fileValueProperties = fileValueProps
                        )


                }(breakOut) // http://stackoverflow.com/questions/1715681/scala-2-8-breakout

            } yield new ErrorHandlingMap(resourceEntities, { key: OntologyCacheKeys.ResourceEntityInfoKey => s"Ontology entity $key not found" })
        }

        /**
          * Queries the ontology for the given property entity Iris and returns a Map of [[PropertyEntityInfoV1]] to be cached.
          *
          * @param propertyIris the property entity Iris to be queried.
          * @return a Map of [[PropertyEntityInfoV1]] to be cached.
          */
        def queryPropertyEntityInfoResponse(propertyIris: Set[OntologyCacheKeys.PropertyEntityInfoKey]): Future[Map[OntologyCacheKeys.PropertyEntityInfoKey, PropertyEntityInfoV1]] = {

            for {
            // get information about property entities
                sparqlQueryStringForProps <- Future(queries.sparql.v1.txt.getEntityInfoForProps(propertyIris.map(_.propertyIri).toVector).toString())
                propertiesResponse <- (storeManager ? SparqlSelectRequest(sparqlQueryStringForProps)).mapTo[SparqlSelectResponse]

                // Filter the query results to get text in the user's preferred language (or the application's default language),
                // if possible.
                propertiesFilteredByLanguage = SparqlUtil.filterByLanguage(response = propertiesResponse,
                    langSpecificColumnName = "o",
                    preferredLanguage = userProfile.userData.lang,
                    settings = settings
                )

                // Group the property query results by subject.
                propertiesGroupedBySubject: Map[IRI, Seq[VariableResultsRow]] = propertiesFilteredByLanguage.groupBy(_.rowMap("s"))

                // Convert the results for each subject into an ResourceEntityInfoV1.
                propertyEntities: Map[OntologyCacheKeys.PropertyEntityInfoKey, PropertyEntityInfoV1] = propertiesGroupedBySubject.map {
                    case (propertyIri: IRI, rows: Seq[VariableResultsRow]) =>

                        val isLinkProp = rows.head.rowMap.get("isLinkProp").exists(_.toBoolean)

                        // group by predicates (e.g. rdfs:subPropertyOf, rdf:type)
                        val groupedByPredicate: Map[IRI, Seq[VariableResultsRow]] = rows.groupBy(_.rowMap("p"))

                        // Make a PredicateInfoV1 for each of the subject's predicates.
                        val predicates: Map[IRI, PredicateInfoV1] = groupedByPredicate.map {
                            case (predicateIri, predicateRowList) =>
                                predicateIri -> PredicateInfoV1(
                                    predicateIri = predicateIri,
                                    ontologyIri = getOntologyIri(propertyIri),
                                    objects = predicateRowList.map(row => row.rowMap("o")).toSet
                                )
                        }

                        new OntologyCacheKeys.PropertyEntityInfoKey(propertyIri) -> PropertyEntityInfoV1(
                            propertyIri = propertyIri,
                            isLinkProp = isLinkProp,
                            predicates = new ErrorHandlingMap(predicates, { key: IRI => s"Predicate $key not found for ontology entity $propertyIri" })
                        )

                }(breakOut) // http://stackoverflow.com/questions/1715681/scala-2-8-breakout

            } yield new ErrorHandlingMap(propertyEntities, { key: OntologyCacheKeys.PropertyEntityInfoKey => s"Ontology entity $key not found" })

        }

        for {

        // if no resource Iris are given, just return an empty Map
            resourceEntityInfoMap: Map[IRI, ResourceEntityInfoV1] <- if (resourceIris.nonEmpty) {
                for {
                // Get the resource entity info from the cache, or query the triplestore and add the resource entity info to the cache.
                    resourceCacheResultMap <- CacheUtil.getOrCacheItems(
                        cacheName = OntologyCacheName,
                        cacheKeys = resourceIris.map(iri => new OntologyCacheKeys.ResourceEntityInfoKey(iri)),
                        queryFun = queryResourceEntityInfoResponse
                    )

                    resourceEntityInfoMapToWrap = resourceCacheResultMap.map {
                        case (entityInfoKey, entityInfo) => entityInfoKey.resourceClassIri -> entityInfo
                    }

                    resInfo = new ErrorHandlingMap(resourceEntityInfoMapToWrap, { key: IRI => s"Ontology entity $key not found" })

                } yield resInfo
            } else {
                Future(Map.empty[IRI, ResourceEntityInfoV1])
            }

            // if no property Iris are given, just return an empty Map
            propertyEntityInfoMap: Map[IRI, PropertyEntityInfoV1] <- if (propertyIris.nonEmpty) {
                for {
                // Get the property entity info from the cache, or query the triplestore and add the property entity info to the cache.
                    propertyCacheResultMap: Map[OntologyCacheKeys.PropertyEntityInfoKey, PropertyEntityInfoV1] <- CacheUtil.getOrCacheItems(
                        cacheName = OntologyCacheName,
                        cacheKeys = propertyIris.map(iri => new OntologyCacheKeys.PropertyEntityInfoKey(iri)),
                        queryFun = queryPropertyEntityInfoResponse
                    )

                    propertyEntityInfoMapToWrap: Map[IRI, PropertyEntityInfoV1] = propertyCacheResultMap.map {
                        case (entityInfoKey: OntologyCacheKeys.PropertyEntityInfoKey, entityInfo: PropertyEntityInfoV1) => entityInfoKey.propertyIri -> entityInfo
                    }

                    propInfo = new ErrorHandlingMap(propertyEntityInfoMapToWrap, { key: IRI => s"Ontology entity $key not found" })

                } yield propInfo
            } else {
                Future(Map.empty[IRI, PropertyEntityInfoV1])
            }

        } yield EntityInfoGetResponseV1(resourceEntityInfoMap = resourceEntityInfoMap, propertyEntityInfoMap = propertyEntityInfoMap)
    }


    /**
      * Given the IRI of a resource type, returns a [[ResourceTypeResponseV1]] describing the resource type and its possible
      * properties.
      *
      * @param resourceTypeIri the IRI of the resource type to be queried.
      * @param userProfile     the profile of the user making the request.
      * @return a [[ResourceTypeResponseV1]].
      */
    private def getResourceTypeResponseV1(resourceTypeIri: String, userProfile: UserProfileV1): Future[ResourceTypeResponseV1] = {
        /**
          * Given a [[PredicateInfoV1]] representing assertions about the values of [[OntologyConstants.SalsahGui.GuiAttribute]] for a property,
          * combines the attributes into a string for use in an API v1 response.
          *
          * @param attributes the values of [[OntologyConstants.SalsahGui.GuiAttribute]] for a property.
          * @return a semicolon-delimited string containing the attributes, or [[None]] if no attributes were found.
          */
        def makeAttributeString(attributes: Set[String]): Option[String] = {
            if (attributes.isEmpty) {
                None
            } else {
                Some(attributes.mkString(";"))
            }
        }

        for {
        // Get all information about the resource type, including its property cardinalities.
            resourceInfoResponse: EntityInfoGetResponseV1 <- getEntityInfoResponseV1(resourceIris = Set(resourceTypeIri), userProfile = userProfile)
            resourceInfo: ResourceEntityInfoV1 = resourceInfoResponse.resourceEntityInfoMap(resourceTypeIri)

            // Get all information about those properties.
            propertyInfo: EntityInfoGetResponseV1 <- getEntityInfoResponseV1(propertyIris = resourceInfo.cardinalities.keySet, userProfile = userProfile)

            // Build the property definitions.
            propertyDefinitions: Set[PropertyDefinitionV1] = resourceInfo.cardinalities.filterNot {
                // filter out the properties that point to LinkValue objects
                case (propertyIri, cardinality) =>
                    resourceInfo.linkValueProperties(propertyIri)
            }.map {
                case (propertyIri: IRI, cardinality: Cardinality.Value) =>
                    propertyInfo.propertyEntityInfoMap.get(propertyIri) match {
                        case Some(entityInfo) =>
                            PropertyDefinitionV1(
                                id = propertyIri,
                                name = propertyIri,
                                label = entityInfo.getPredicateObject(OntologyConstants.Rdfs.Label),
                                description = entityInfo.getPredicateObject(OntologyConstants.Rdfs.Comment),
                                vocabulary = entityInfo.predicates.values.head.ontologyIri,
                                occurrence = cardinality.toString,
                                valuetype_id = entityInfo.getPredicateObject(OntologyConstants.Rdfs.Range).getOrElse(throw InconsistentTriplestoreDataException(s"Property $propertyIri has no rdfs:range")),
                                attributes = makeAttributeString(entityInfo.getPredicateObjects(OntologyConstants.SalsahGui.GuiAttribute)),
                                gui_name = entityInfo.getPredicateObject(OntologyConstants.SalsahGui.GuiElement).map(iri => SalsahGuiConversions.iri2SalsahGuiElement(iri))
                            )
                        case None =>
                            throw new InconsistentTriplestoreDataException(s"Resource type $resourceTypeIri is defined as having property $propertyIri, which doesn't exist")
                    }
            }(breakOut)

            // Build the API response.
            resourceTypeResponse = ResourceTypeResponseV1(
                restype_info = ResTypeInfoV1(
                    name = resourceTypeIri,
                    label = resourceInfo.getPredicateObject(OntologyConstants.Rdfs.Label),
                    description = resourceInfo.getPredicateObject(OntologyConstants.Rdfs.Comment),
                    iconsrc = resourceInfo.getPredicateObject(OntologyConstants.KnoraBase.ResourceIcon),
                    properties = propertyDefinitions
                ),
                userProfile.userData
            )
        } yield resourceTypeResponse
    }

    /**
      * Checks whether a certain OWL class is a subclass of another OWL class.
      *
      * @param checkSubClassRequest a [[CheckSubClassRequestV1]]
      * @return a [[CheckSubClassResponseV1]].
      */
    private def checkSubClass(checkSubClassRequest: CheckSubClassRequestV1): Future[CheckSubClassResponseV1] = {
        // TODO: cache this data.

        for {
            sparqlQuery <- Future(
                queries.sparql.v1.txt.checkSubClass(
                    subClassIri = checkSubClassRequest.subClassIri,
                    superClassIri = checkSubClassRequest.superClassIri
                ).toString()
            )

            queryResponse <- (storeManager ? SparqlSelectRequest(sparqlQuery)).mapTo[SparqlSelectResponse]

        } yield CheckSubClassResponseV1(isSubClass = queryResponse.results.bindings.nonEmpty)
    }
}
