/*
 * Copyright © 2015-2019 the contributors (see Contributors.md).
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

package org.knora.webapi.util.clientapi

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.util.FastFuture
import akka.pattern._
import akka.util.Timeout
import org.knora.webapi._
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.v2.responder.ontologymessages._
import org.knora.webapi.routing.KnoraRouteData
import org.knora.webapi.util.IriConversions._
import org.knora.webapi.util.jsonld.JsonLDUtil
import org.knora.webapi.util.{SmartIri, StringFormatter}

import scala.concurrent.{ExecutionContext, Future}

/**
  * The front end of the client code generator. It is responsible for producing [[ClientClassDefinition]] objects
  * representing the Knora classes used in an API.
  */
class GeneratorFrontEnd(routeData: KnoraRouteData, requestingUser: UserADM) {
    implicit private val system: ActorSystem = routeData.system
    implicit private val responderManager: ActorRef = routeData.appActor
    implicit private val settings: SettingsImpl = Settings(system)
    implicit private val timeout: Timeout = settings.defaultTimeout
    implicit private val executionContext: ExecutionContext = system.dispatchers.lookup(KnoraDispatchers.KnoraActorDispatcher)
    implicit private val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    // Return code documentation in English.
    private val userWithLang = requestingUser.copy(lang = LanguageCodes.EN)

    /**
      * Returns a set of [[ClientClassDefinition]] instances representing the Knora classes used by a client API.
      */
    def getClientClassDefs(clientApi: ClientApi): Future[Set[ClientClassDefinition]] = {
        /**
          * An accumulator for client class definitions and ontologies.
          *
          * @param clientDefs the client class definitions accumulated so far.
          * @param ontologies the ontologies accumulated so far.
          */
        case class ClientDefsWithOntologies(clientDefs: Map[SmartIri, ClientClassDefinition], ontologies: Map[SmartIri, InputOntologyV2])

        /**
          * Recursively gets class definitions.
          *
          * @param classIri      the IRI of a class whose definition is needed.
          * @param definitionAcc the class definitions and ontologies collected so far.
          * @return the class definitions and ontologies resulting from recursion.
          */
        def getClassDefsRec(classIri: SmartIri, definitionAcc: ClientDefsWithOntologies): Future[ClientDefsWithOntologies] = {
            val className = classIri.getEntityName

            // Is this the IRI of a derived class that hasn't been generated yet?
            if (ClassRef.isGeneratedDerivedClassName(className)) {
                // Yes. Do we already have the definition of the base class?

                val baseClassIri: SmartIri = classIri.getOntologyFromEntity.makeEntityIri(ClassRef.toBaseClassName(className))

                if (definitionAcc.clientDefs.contains(baseClassIri)) {
                    // Yes. Return.
                    FastFuture.successful(definitionAcc)
                } else {
                    // No. Recurse to get it.
                    getClassDefsRec(baseClassIri, definitionAcc)
                }
            } else {
                // Get the IRI of the ontology containing the class.
                val classOntologyIri: SmartIri = classIri.getOntologyFromEntity

                for {
                    // Get the ontology containing the class.
                    ontologiesWithClassOntology <- getOntology(classOntologyIri, definitionAcc.ontologies)
                    classOntology = ontologiesWithClassOntology(classOntologyIri)

                    // Get the RDF definition of the class.
                    rdfClassDef: ClassInfoContentV2 = classOntology.classes.getOrElse(classIri, throw ClientApiGenerationException(s"Class <$classIri> not found"))

                    // Get the IRIs of the class's Knora properties.
                    rdfPropertyIris: Set[SmartIri] = rdfClassDef.directCardinalities.keySet.filter {
                        propertyIri => propertyIri.isKnoraEntityIri
                    }

                    // Get the ontologies containing the definitions of those properties.
                    ontologiesWithPropertyDefs: Map[SmartIri, InputOntologyV2] <- rdfPropertyIris.foldLeft(FastFuture.successful(ontologiesWithClassOntology)) {
                        case (acc, propertyIri) =>
                            val propertyOntologyIri = propertyIri.getOntologyFromEntity

                            for {
                                currentOntologies: Map[SmartIri, InputOntologyV2] <- acc
                                ontologiesWithPropertyDef: Map[SmartIri, InputOntologyV2] <- getOntology(propertyOntologyIri, currentOntologies)
                            } yield ontologiesWithPropertyDef
                    }

                    // Get the definitions of the properties.
                    rdfPropertyDefs: Map[SmartIri, PropertyInfoContentV2] = rdfPropertyIris.map {
                        propertyIri =>
                            val ontology = ontologiesWithPropertyDefs(propertyIri.getOntologyFromEntity)
                            propertyIri -> ontology.properties.getOrElse(propertyIri, throw ClientApiGenerationException(s"Property <$propertyIri> not found"))
                    }.toMap

                    // Convert the RDF class definition into a ClientClassDefinition.
                    clientClassDef: ClientClassDefinition = rdfClassDef2ClientClassDef(
                        clientApi = clientApi,
                        rdfClassDef = rdfClassDef,
                        rdfPropertyDefs = rdfPropertyDefs
                    )

                    // Make a new ClientDefsWithOntologies including that ClientClassDefinition as well as the ontologies
                    // we've collected.
                    accForRecursion = ClientDefsWithOntologies(
                        clientDefs = definitionAcc.clientDefs + (clientClassDef.classIri -> clientClassDef),
                        ontologies = ontologiesWithPropertyDefs
                    )

                    // Recursively get definitions of classes used as property object types.
                    accFromRecursion: ClientDefsWithOntologies <- clientClassDef.properties.foldLeft(FastFuture.successful(accForRecursion)) {
                        case (acc: Future[ClientDefsWithOntologies], clientPropertyDef: ClientPropertyDefinition) =>
                            // Does this property have something containing a class IRI as its object type
                            // (a ClassRef, or a CollectionType referring to a class IRI)?
                            clientPropertyDef.objectType match {
                                case typeWithClassIri: TypeWithClassIri =>
                                    typeWithClassIri.getClassIri match {
                                        case Some(objectTypeClassIri) =>
                                            // Yes.
                                            for {
                                                currentAcc: ClientDefsWithOntologies <- acc

                                                // Do we have this class definition already?
                                                newAcc: ClientDefsWithOntologies <- if (currentAcc.clientDefs.contains(objectTypeClassIri)) {
                                                    // Yes. Nothing to do.
                                                    acc
                                                } else {
                                                    // No. Recurse to get it.
                                                    for {
                                                        recursionResults: ClientDefsWithOntologies <- getClassDefsRec(
                                                            classIri = objectTypeClassIri,
                                                            definitionAcc = currentAcc
                                                        )
                                                    } yield recursionResults
                                                }
                                            } yield newAcc

                                        case None =>
                                            // This property doesn't have an object type containing a class IRI.
                                            acc
                                    }

                                // This property doesn't have an object type containing a class IRI.
                                case _ => acc
                            }
                    }
                } yield accFromRecursion
            }
        }

        // Iterate over all the class IRIs used in the client API, making a client definition for each class,
        // as well as for any other classes referred to by that class.

        val initialAcc = ClientDefsWithOntologies(clientDefs = Map.empty, ontologies = Map.empty)

        for {
            allDefs: ClientDefsWithOntologies <- clientApi.classIrisUsed.foldLeft(FastFuture.successful(initialAcc)) {
                case (acc: Future[ClientDefsWithOntologies], classIri) =>
                    for {
                        currentAcc: ClientDefsWithOntologies <- acc

                        recursionResults: ClientDefsWithOntologies <- getClassDefsRec(
                            classIri = classIri,
                            definitionAcc = currentAcc
                        )
                    } yield recursionResults
            }
        } yield transformClientClassDefs(
            clientApi = clientApi,
            clientDefs = allDefs.clientDefs
        )
    }

    /**
      * Transforms client class definitions by adding `Stored*` and `Read*` classes, transforming the properties of their base
      * classes, and transforming the properties of response classes.
      *
      * @param clientApi  the client API definition.
      * @param clientDefs the client class definitions used by the API.
      * @return the transformed class definitions.
      */
    private def transformClientClassDefs(clientApi: ClientApi, clientDefs: Map[SmartIri, ClientClassDefinition]): Set[ClientClassDefinition] = {
        /**
          * Transforms the class reference object types of properties.
          *
          * @param properties         the properties to transform.
          * @param classIrisToReplace A map of source class IRIs to replacement class IRIs.
          * @return the transformed properties.
          */
        def transformPropertyObjectTypes(properties: Vector[ClientPropertyDefinition],
                                         classIrisToReplace: Map[SmartIri, SmartIri]): Vector[ClientPropertyDefinition] = {
            properties.map {
                propDef: ClientPropertyDefinition =>
                    propDef.objectType match {
                        case classRef: ClassRef =>
                            classIrisToReplace.get(classRef.classIri) match {
                                case Some(targetClassIri) =>
                                    propDef.copy(
                                        objectType = ClassRef(className = makeClientClassName(targetClassIri), classIri = targetClassIri)
                                    )

                                case None => propDef
                            }

                        case _ => propDef
                    }
            }
        }

        // Rename properties as requested.
        val classesWithRenamedProps: Map[SmartIri, ClientClassDefinition] = clientDefs.map {
            case (classIri: SmartIri, classDef: ClientClassDefinition) =>
                val transformedProps = classDef.properties.map {
                    propDef =>
                        clientApi.propertyNames.get(classIri) match {
                            case Some(propertyMap) =>
                                propertyMap.get(propDef.propertyIri) match {
                                    case Some(propertyName) => propDef.copy(propertyName = propertyName)
                                    case None => propDef
                                }

                            case None => propDef
                        }
                }

                classIri -> classDef.copy(properties = transformedProps)
        }

        // A class that has an ID property needs a Stored* subclass.
        val classIrisNeedingStoredClasses: Set[SmartIri] = classesWithRenamedProps.collect {
            case (classIri, classDef) if classDef.properties.exists(propDef => clientApi.idProperties.contains(propDef.propertyIri)) => classIri
        }.toSet

        // A map of the IRIs of classes that need Stored* classes, to the IRIs of their Stored* classes.
        val storedClassIris: Map[SmartIri, SmartIri] = classIrisNeedingStoredClasses.map {
            classIri => classIri -> classIri.getOntologyFromEntity.makeEntityIri(ClassRef.makeStoredClassName(classIri.getEntityName))
        }.toMap

        // A class that has one or more read-only properties needs a Read* subclass.
        val classIrisNeedingReadClasses: Set[SmartIri] = clientApi.classesWithReadOnlyProperties.keySet

        // A map of the IRIs of classes that need Read* classes, to the IRIs of their Read* classes.
        val readClassIris: Map[SmartIri, SmartIri] = classIrisNeedingReadClasses.map {
            classIri => classIri -> classIri.getOntologyFromEntity.makeEntityIri(ClassRef.makeReadClassName(classIri.getEntityName))
        }.toMap

        val classesNeedingStoredClasses: Map[SmartIri, ClientClassDefinition] = classesWithRenamedProps.filterKeys(classIrisNeedingStoredClasses)

        val generatedSubclassesAndTransformedBaseClasses: Map[SmartIri, ClientClassDefinition] = classesNeedingStoredClasses.flatMap {
            case (classIri: SmartIri, classDef: ClientClassDefinition) =>
                // A Stored* class contains only the ID property from the base class. The ID property's cardinality
                // in the Stored* class is MustHaveOne.
                val propsForStoredClass: Vector[ClientPropertyDefinition] = classDef.properties.collect {
                    case propDef if clientApi.idProperties.contains(propDef.propertyIri) =>
                        propDef.copy(cardinality = Cardinality.MustHaveOne)
                }

                if (propsForStoredClass.length > 1) {
                    throw ClientApiGenerationException(s"Class $classIri has more than one ID property: ${propsForStoredClass.map(_.propertyIri).mkString(", ")}")
                }

                val storedClassIri = storedClassIris(classIri)

                val storedClassDef: ClientClassDefinition = classDef.copy(
                    className = storedClassIri.getEntityName,
                    classIri = storedClassIri,
                    properties = propsForStoredClass,
                    subClassOf = Some(classDef.classIri)
                )

                val readOnlyPropertyIris: Set[SmartIri] = clientApi.classesWithReadOnlyProperties.getOrElse(classDef.classIri, Set.empty)

                val maybeReadClassIriAndDef: Option[(SmartIri, ClientClassDefinition)] = if (classIrisNeedingReadClasses.contains(classIri)) {
                    // In a Read* class, only read-only properties should be included.
                    val readOnlyPropsInClass: Vector[ClientPropertyDefinition] = classDef.properties.filter {
                        propDef => readOnlyPropertyIris.contains(propDef.propertyIri)
                    }

                    // To avoid circular dependencies, the read-only properties should point to Stored* classes.
                    val propsForReadClass = transformPropertyObjectTypes(
                        properties = readOnlyPropsInClass,
                        classIrisToReplace = storedClassIris
                    )

                    val readClassIri = readClassIris(classIri)

                    val readClassDef: ClientClassDefinition = classDef.copy(
                        className = readClassIri.getEntityName,
                        classIri = readClassIri,
                        properties = propsForReadClass,
                        subClassOf = Some(storedClassIri)
                    )

                    Some(readClassIri -> readClassDef)
                } else {
                    None
                }

                // Remove the ID property and read-only properties from the transformed base class.

                val baseClassDef: ClientClassDefinition = classDef.copy(
                    properties = classDef.properties.filterNot {
                        propDef =>
                            clientApi.idProperties.contains(propDef.propertyIri) || readOnlyPropertyIris.contains(propDef.propertyIri)
                    }
                )

                Map(
                    baseClassDef.classIri -> baseClassDef,
                    storedClassIri -> storedClassDef
                ) ++ maybeReadClassIriAndDef
        }

        // In a response class, every property should have another Read* class (if available) as its object type.

        val responseClasses: Map[SmartIri, ClientClassDefinition] = classesWithRenamedProps.filterKeys(clientApi.responseClasses)

        val transformedResponseClasses: Map[SmartIri, ClientClassDefinition] = responseClasses.map {
            case (classIri: SmartIri, classDef: ClientClassDefinition) =>
                val transformedProps = transformPropertyObjectTypes(
                    properties = classDef.properties,
                    classIrisToReplace = readClassIris
                )

                classIri -> classDef.copy(
                    properties = transformedProps
                )
        }

        (classesWithRenamedProps ++ generatedSubclassesAndTransformedBaseClasses ++ transformedResponseClasses).values.toSet
    }

    /**
      * Gets an ontology from Knora.
      *
      * @param ontologyIri the IRI of the ontology.
      * @param ontologies  the ontologies collected so far.
      * @return `ontologies` plus the requested ontology.
      */
    private def getOntology(ontologyIri: SmartIri, ontologies: Map[SmartIri, InputOntologyV2]): Future[Map[SmartIri, InputOntologyV2]] = {
        val requestMessage = OntologyEntitiesGetRequestV2(
            ontologyIri = ontologyIri,
            allLanguages = false,
            requestingUser = userWithLang
        )

        for {
            ontologiesResponse: ReadOntologyV2 <- (responderManager ? requestMessage).mapTo[ReadOntologyV2]

            responseAsJsonLD = ontologiesResponse.toJsonLDDocument(
                targetSchema = ApiV2Complex,
                settings = settings,
                schemaOptions = Set.empty
            ).toCompactString

            ontology: InputOntologyV2 = InputOntologyV2.fromJsonLD(JsonLDUtil.parseJsonLD(responseAsJsonLD)).unescape
        } yield ontologies + (ontologyIri -> ontology)
    }

    /**
      * Converts RDF class definitions into [[ClientClassDefinition]] instances.
      *
      * @param clientApi       the client API definition.
      * @param rdfClassDef     the class definition to be converted.
      * @param rdfPropertyDefs the definitions of the properties used in the class.
      * @return a [[ClientClassDefinition]] describing the class.
      */
    private def rdfClassDef2ClientClassDef(clientApi: ClientApi, rdfClassDef: ClassInfoContentV2, rdfPropertyDefs: Map[SmartIri, PropertyInfoContentV2]): ClientClassDefinition = {
        implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

        val isResourceClass = rdfClassDef.getPredicateBooleanObject(OntologyConstants.KnoraApiV2Complex.IsResourceClass.toSmartIri)

        if (isResourceClass) {
            rdfResourceClassDef2ClientClassDef(
                clientApi = clientApi,
                rdfClassDef = rdfClassDef,
                rdfPropertyDefs = rdfPropertyDefs
            )
        } else {
            rdfNonResourceClassDef2ClientClassDef(
                clientApi = clientApi,
                rdfClassDef = rdfClassDef,
                rdfPropertyDefs = rdfPropertyDefs
            )
        }
    }

    /**
      * Converts Knora resource class definitions into [[ClientClassDefinition]] instances.
      *
      * @param clientApi       the client API definition.
      * @param rdfClassDef     the class definition to be converted.
      * @param rdfPropertyDefs the definitions of the properties used in the class.
      * @return a [[ClientClassDefinition]] describing the class.
      */
    private def rdfResourceClassDef2ClientClassDef(clientApi: ClientApi, rdfClassDef: ClassInfoContentV2, rdfPropertyDefs: Map[SmartIri, PropertyInfoContentV2]): ClientClassDefinition = {
        implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

        val classDescription: Option[String] = rdfClassDef.getPredicateStringLiteralObject(OntologyConstants.Rdfs.Comment.toSmartIri)

        val cardinalitiesWithoutLinkProps = rdfClassDef.directCardinalities.filter {
            case (propertyIri, _) =>
                rdfPropertyDefs.get(propertyIri) match {
                    case Some(rdfPropertyDef) => !rdfPropertyDef.getPredicateBooleanObject(OntologyConstants.KnoraApiV2Complex.IsLinkProperty.toSmartIri)
                    case None => true
                }
        }

        val clientPropertyDefs: Vector[ClientPropertyDefinition] = cardinalitiesWithoutLinkProps.map {
            case (propertyIri, knoraCardinalityInfo) =>
                val propertyName = propertyIri.getEntityName

                val isOptionalSet = isOptionalSetProperty(
                    clientApi = clientApi,
                    classIri = rdfClassDef.classIri,
                    propertyIri = propertyIri
                )

                if (propertyIri.isKnoraEntityIri) {
                    val rdfPropertyDef = rdfPropertyDefs(propertyIri)
                    val propertyDescription: Option[String] = rdfPropertyDef.getPredicateStringLiteralObject(OntologyConstants.Rdfs.Comment.toSmartIri)
                    val ontologyObjectType: SmartIri = rdfPropertyDef.requireIriObject(OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri, throw InconsistentTriplestoreDataException(s"Property $propertyIri has no knora-api:objectType"))
                    val isResourceProp = rdfPropertyDef.getPredicateBooleanObject(OntologyConstants.KnoraApiV2Complex.IsResourceProperty.toSmartIri)
                    val isEditable = rdfPropertyDef.getPredicateBooleanObject(OntologyConstants.KnoraApiV2Complex.IsEditable.toSmartIri)

                    if (isResourceProp) {
                        val isLinkValueProp = rdfPropertyDef.getPredicateBooleanObject(OntologyConstants.KnoraApiV2Complex.IsLinkValueProperty.toSmartIri)

                        val clientObjectType: ClientObjectType = if (isLinkValueProp) {
                            LinkVal(ontologyObjectType)
                        } else {
                            resourcePropObjectTypeToClientObjectType(ontologyObjectType)
                        }

                        ClientPropertyDefinition(
                            propertyName = propertyName,
                            propertyDescription = propertyDescription,
                            propertyIri = propertyIri,
                            objectType = clientObjectType,
                            cardinality = knoraCardinalityInfo.cardinality,
                            isOptionalSet = isOptionalSet,
                            isEditable = isEditable
                        )
                    } else {
                        ClientPropertyDefinition(
                            propertyName = propertyName,
                            propertyDescription = propertyDescription,
                            propertyIri = propertyIri,
                            objectType = nonResourcePropObjectTypeToClientObjectType(ontologyObjectType),
                            cardinality = knoraCardinalityInfo.cardinality,
                            isOptionalSet = isOptionalSet,
                            isEditable = isEditable
                        )
                    }
                } else {
                    ClientPropertyDefinition(
                        propertyName = propertyName,
                        propertyDescription = None,
                        propertyIri = propertyIri,
                        objectType = StringDatatype,
                        cardinality = knoraCardinalityInfo.cardinality,
                        isOptionalSet = isOptionalSet,
                        isEditable = propertyIri.toString == OntologyConstants.Rdfs.Label // Labels of resources are editable
                    )
                }
        }.toVector.sortBy(_.propertyIri)

        ClientClassDefinition(
            className = makeClientClassName(rdfClassDef.classIri),
            classDescription = classDescription,
            classIri = rdfClassDef.classIri,
            properties = clientPropertyDefs.sortBy(_.propertyIri)
        )
    }

    /**
      * Converts Knora non-resource class definitions into [[ClientClassDefinition]] instances.
      *
      * @param clientApi       the client API definition.
      * @param rdfClassDef     the class definition to be converted.
      * @param rdfPropertyDefs the definitions of the properties used in the class.
      * @return a [[ClientClassDefinition]] describing the class.
      */
    private def rdfNonResourceClassDef2ClientClassDef(clientApi: ClientApi, rdfClassDef: ClassInfoContentV2, rdfPropertyDefs: Map[SmartIri, PropertyInfoContentV2]): ClientClassDefinition = {
        implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

        val classDescription: Option[String] = rdfClassDef.getPredicateStringLiteralObject(OntologyConstants.Rdfs.Comment.toSmartIri)

        val clientPropertyDefs = rdfClassDef.directCardinalities.map {
            case (propertyIri, knoraCardinalityInfo) =>
                val propertyName = propertyIri.getEntityName

                val isOptionalSet = isOptionalSetProperty(
                    clientApi = clientApi,
                    classIri = rdfClassDef.classIri,
                    propertyIri = propertyIri
                )

                if (propertyIri.isKnoraEntityIri) {
                    val rdfPropertyDef = rdfPropertyDefs(propertyIri)
                    val propertyDescription: Option[String] = rdfPropertyDef.getPredicateStringLiteralObject(OntologyConstants.Rdfs.Comment.toSmartIri)
                    val ontologyObjectType: SmartIri = rdfPropertyDef.getPredicateIriObject(OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri).getOrElse(OntologyConstants.Xsd.String.toSmartIri)

                    ClientPropertyDefinition(
                        propertyName = propertyName,
                        propertyDescription = propertyDescription,
                        propertyIri = propertyIri,
                        objectType = nonResourcePropObjectTypeToClientObjectType(ontologyObjectType),
                        cardinality = knoraCardinalityInfo.cardinality,
                        isOptionalSet = isOptionalSet,
                        isEditable = true
                    )
                } else {
                    ClientPropertyDefinition(
                        propertyName = propertyName,
                        propertyDescription = None,
                        propertyIri = propertyIri,
                        objectType = StringDatatype,
                        cardinality = knoraCardinalityInfo.cardinality,
                        isOptionalSet = isOptionalSet,
                        isEditable = true
                    )
                }
        }.toVector.sortBy(_.propertyIri)

        ClientClassDefinition(
            className = makeClientClassName(rdfClassDef.classIri),
            classDescription = classDescription,
            classIri = rdfClassDef.classIri,
            properties = clientPropertyDefs.sortBy(_.propertyIri)
        )
    }

    /**
      * Returns `true` if the specified property IRI is an optional set property in the specified class.
      *
      * @param clientApi   the client API definition.
      * @param classIri    the class IRI.
      * @param propertyIri the property IRI.
      * @return
      */
    private def isOptionalSetProperty(clientApi: ClientApi, classIri: SmartIri, propertyIri: SmartIri): Boolean = {
        clientApi.classesWithOptionalSetProperties.get(classIri).exists(_.contains(propertyIri))
    }

    /**
      * Given the IRI of an RDF class, creates a client class name for it.
      *
      * @param classIri the class IRI.
      * @return the client class name.
      */
    private def makeClientClassName(classIri: SmartIri): String = {
        classIri.getEntityName.capitalize
    }

    /**
      * Given a Knora value object type, returns the corresponding [[ClientObjectType]].
      *
      * @param ontologyObjectType the RDF type.
      * @return the corresponding [[ClientObjectType]].
      */
    private def resourcePropObjectTypeToClientObjectType(ontologyObjectType: SmartIri): ClientObjectType = {
        ontologyObjectType.toString match {
            case OntologyConstants.KnoraApiV2Complex.Value => AbstractKnoraVal
            case OntologyConstants.KnoraApiV2Complex.TextValue => TextVal
            case OntologyConstants.KnoraApiV2Complex.IntValue => IntVal
            case OntologyConstants.KnoraApiV2Complex.DecimalValue => DecimalVal
            case OntologyConstants.KnoraApiV2Complex.BooleanValue => BooleanVal
            case OntologyConstants.KnoraApiV2Complex.DateValue => DateVal
            case OntologyConstants.KnoraApiV2Complex.GeomValue => GeomVal
            case OntologyConstants.KnoraApiV2Complex.IntervalValue => IntervalVal
            case OntologyConstants.KnoraApiV2Complex.ListValue => ListVal
            case OntologyConstants.KnoraApiV2Complex.UriValue => UriVal
            case OntologyConstants.KnoraApiV2Complex.GeonameValue => GeonameVal
            case OntologyConstants.KnoraApiV2Complex.ColorValue => ColorVal
            case OntologyConstants.KnoraApiV2Complex.StillImageFileValue => StillImageFileVal
            case OntologyConstants.KnoraApiV2Complex.MovingImageFileValue => MovingImageFileVal
            case OntologyConstants.KnoraApiV2Complex.AudioFileValue => AudioFileVal
            case OntologyConstants.KnoraApiV2Complex.DDDFileValue => DDDFileVal
            case OntologyConstants.KnoraApiV2Complex.TextFileValue => TextFileVal
            case OntologyConstants.KnoraApiV2Complex.DocumentFileValue => DocumentFileVal
            case _ => throw ClientApiGenerationException(s"Unexpected value type: $ontologyObjectType")
        }
    }

    /**
      * Given an object type that is not a Knora value type, returns the corresponding [[ClientObjectType]].
      *
      * @param ontologyObjectType the RDF type.
      * @return the corresponding [[ClientObjectType]].
      */
    private def nonResourcePropObjectTypeToClientObjectType(ontologyObjectType: SmartIri): ClientObjectType = {
        if (ontologyObjectType.isClientCollectionTypeIri) {
            ontologyObjectType.getClientCollectionType
        } else {
            ontologyObjectType.toString match {
                case OntologyConstants.Xsd.String => StringDatatype
                case OntologyConstants.Xsd.Boolean => BooleanDatatype
                case OntologyConstants.Xsd.Integer => IntegerDatatype
                case OntologyConstants.Xsd.Decimal => DecimalDatatype
                case OntologyConstants.Xsd.Uri => UriDatatype
                case OntologyConstants.Xsd.DateTime | OntologyConstants.Xsd.DateTimeStamp => DateTimeStampDatatype

                case _ =>
                    ClassRef(
                        className = makeClientClassName(ontologyObjectType),
                        classIri = ontologyObjectType
                    )
            }
        }
    }
}
