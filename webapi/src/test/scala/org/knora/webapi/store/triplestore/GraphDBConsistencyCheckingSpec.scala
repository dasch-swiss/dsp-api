package org.knora.webapi.store.triplestore

import akka.actor.Props
import akka.testkit.ImplicitSender
import com.typesafe.config.ConfigFactory
import org.knora.webapi.messages.v1respondermessages.triplestoremessages._
import org.knora.webapi.{TriplestoreResponseException, LiveActorMaker, CoreSpec}
import org.knora.webapi.store._
import scala.concurrent.duration._

/**
  * Created by benjamingeer on 28/03/16.
  */
class GraphDBConsistencyCheckingSpec extends CoreSpec(GraphDBConsistencyCheckingSpec.config) with ImplicitSender {
    val storeManager = system.actorOf(Props(new StoreManager with LiveActorMaker), STORE_MANAGER_ACTOR_NAME)

    private val timeout = 30.seconds

    val rdfDataObjects = List(
        RdfDataObject(path = "../knora-ontologies/knora-base.ttl", name = "http://www.knora.org/ontology/knora-base"),
        RdfDataObject(path = "../knora-ontologies/knora-dc.ttl", name = "http://www.knora.org/ontology/dc"),
        RdfDataObject(path = "../knora-ontologies/salsah-gui.ttl", name = "http://www.knora.org/ontology/salsah-gui"),
        RdfDataObject(path = "_test_data/ontologies/incunabula-onto.ttl", name = "http://www.knora.org/ontology/incunabula"),
        RdfDataObject(path = "_test_data/store.triplestore.GraphDBConsistencyCheckingSpec/incunabula-data.ttl", name = "http://www.knora.org/data/incunabula")
    )

    if (settings.triplestoreType.startsWith("graphdb")) {
        "Load test data" in {
            storeManager ! ResetTriplestoreContent(rdfDataObjects)
            expectMsg(300.seconds, ResetTriplestoreContentACK())
        }

        "not create a new resource with a missing property that has owl:cardinality 1" in {
            storeManager ! SparqlUpdateRequest(GraphDBConsistencyCheckingSpec.missingPartOf)

            expectMsgPF(timeout) {
                case akka.actor.Status.Failure(TriplestoreResponseException(msg: String, _)) =>
                    (msg.contains(s"${GraphDBConsistencyCheckingSpec.CONSISTENCY_CHECK_ERROR} content_prop_cardinality_1_not_less") &&
                        msg.trim.endsWith("http://data.knora.org/missingPartOf http://www.knora.org/ontology/incunabula#partOfValue *")) should ===(true)
            }
        }

        "not create a new resource with a missing inherited property that has owl:minCardinality 1" in {
            storeManager ! SparqlUpdateRequest(GraphDBConsistencyCheckingSpec.missingFileValue)

            expectMsgPF(timeout) {
                case akka.actor.Status.Failure(TriplestoreResponseException(msg: String, _)) =>
                    (msg.contains(s"${GraphDBConsistencyCheckingSpec.CONSISTENCY_CHECK_ERROR} content_prop_min_cardinality_1") &&
                        msg.trim.endsWith("http://data.knora.org/missingFileValue http://www.knora.org/ontology/knora-base#hasFileValue *")) should ===(true)
            }
        }

        "not create a new resource with two values for a property that has owl:maxCardinality 1" in {
            storeManager ! SparqlUpdateRequest(GraphDBConsistencyCheckingSpec.tooManyPublocs)

            expectMsgPF(timeout) {
                case akka.actor.Status.Failure(TriplestoreResponseException(msg: String, _)) =>
                    msg.contains(s"${GraphDBConsistencyCheckingSpec.CONSISTENCY_CHECK_ERROR} content_prop_max_cardinality_1") should ===(true)
            }
        }

        "not create a new resource with more than one lastModificationDate" in {
            storeManager ! SparqlUpdateRequest(GraphDBConsistencyCheckingSpec.tooManyLastModificationDates)

            expectMsgPF(timeout) {
                case akka.actor.Status.Failure(TriplestoreResponseException(msg: String, _)) =>
                    msg.contains(s"${GraphDBConsistencyCheckingSpec.CONSISTENCY_CHECK_ERROR} system_prop_max_cardinality_1") should ===(true)
            }
        }

        "not create a new resource with a property that cannot have a resource as a subject" in {
            storeManager ! SparqlUpdateRequest(GraphDBConsistencyCheckingSpec.wrongSubjectClass)

            expectMsgPF(timeout) {
                case akka.actor.Status.Failure(TriplestoreResponseException(msg: String, _)) =>
                    msg.contains(s"${GraphDBConsistencyCheckingSpec.CONSISTENCY_CHECK_ERROR} subject_class_constraint") should ===(true)
            }
        }

        "not create a new resource with properties whose objects have the wrong types" in {
            storeManager ! SparqlUpdateRequest(GraphDBConsistencyCheckingSpec.wrongObjectClass)

            expectMsgPF(timeout) {
                case akka.actor.Status.Failure(TriplestoreResponseException(msg: String, _)) =>
                    msg.contains(s"${GraphDBConsistencyCheckingSpec.CONSISTENCY_CHECK_ERROR} object_class_constraint") should ===(true)
            }
        }
    } else {
        s"Not running GraphDBConsistencyCheckingSpec with triplestore type ${settings.triplestoreType}" in {}
    }
}

object GraphDBConsistencyCheckingSpec {
    private val CONSISTENCY_CHECK_ERROR = "Consistency check"

    private val config = ConfigFactory.parseString(
        """
         # akka.loglevel = "DEBUG"
         # akka.stdout-loglevel = "DEBUG"
        """.stripMargin)

    // Tries to create a new incunabula:page with a missing incunabula:partOf link.
    private val missingPartOf =
        """
          |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
          |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
          |PREFIX owl: <http://www.w3.org/2002/07/owl#>
          |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
          |
          |INSERT {
          |    GRAPH ?dataNamedGraph {
          |        ?resource rdf:type ?resourceClass ;
          |            knora-base:isDeleted "false"^^xsd:boolean ;
          |            knora-base:attachedToUser ?ownerIri ;
          |            knora-base:attachedToProject ?projectIri ;
          |            rdfs:label ?label ;
          |
          |
          |
          |                <http://www.knora.org/ontology/knora-base#hasViewPermission> <http://www.knora.org/ontology/knora-base#KnownUser> ;
          |
          |                <http://www.knora.org/ontology/knora-base#hasModifyPermission> <http://www.knora.org/ontology/knora-base#Owner> ;
          |
          |                <http://www.knora.org/ontology/knora-base#hasModifyPermission> <http://www.knora.org/ontology/knora-base#ProjectMember> ;
          |
          |                <http://www.knora.org/ontology/knora-base#hasRestrictedViewPermission> <http://www.knora.org/ontology/knora-base#UnknownUser> ;
          |
          |
          |            knora-base:creationDate ?currentTime .
          |
          |
          |
          |        # Value 1
          |        # Property: http://www.knora.org/ontology/incunabula#pagenum
          |
          |
          |        ?newValue1 rdf:type ?valueType1 ;
          |            knora-base:isDeleted "false"^^xsd:boolean .
          |
          |
          |
          |                ?newValue1 knora-base:valueHasString "recto" .
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |            ?newValue1 <http://www.knora.org/ontology/knora-base#attachedToUser> <http://data.knora.org/users/b83acc5f05> .
          |
          |            ?newValue1 <http://www.knora.org/ontology/knora-base#attachedToProject> <http://data.knora.org/projects/77275339> .
          |
          |            ?newValue1 <http://www.knora.org/ontology/knora-base#hasViewPermission> <http://www.knora.org/ontology/knora-base#KnownUser> .
          |
          |            ?newValue1 <http://www.knora.org/ontology/knora-base#hasViewPermission> <http://www.knora.org/ontology/knora-base#UnknownUser> .
          |
          |            ?newValue1 <http://www.knora.org/ontology/knora-base#hasModifyPermission> <http://www.knora.org/ontology/knora-base#ProjectMember> .
          |
          |            ?newValue1 <http://www.knora.org/ontology/knora-base#hasDeletePermission> <http://www.knora.org/ontology/knora-base#Owner> .
          |
          |
          |        ?newValue1 knora-base:valueHasOrder ?nextOrder1 ;
          |                             knora-base:valueCreationDate ?currentTime .
          |
          |
          |
          |
          |
          |
          |
          |
          |        ?resource ?property1 ?newValue1 .
          |
          |
          |
          |
          |        # Value 2
          |        # Property: http://www.knora.org/ontology/knora-base#hasStillImageFileValue
          |
          |
          |        ?newValue2 rdf:type ?valueType2 ;
          |            knora-base:isDeleted "false"^^xsd:boolean .
          |
          |
          |                ?newValue2 knora-base:originalFilename "test.jpg" ;
          |                                     knora-base:originalMimeType "image/jpeg" ;
          |                                     knora-base:internalFilename "full.jp2" ;
          |                                     knora-base:internalMimeType "image/jp2" ;
          |                                     knora-base:dimX 800 ;
          |                                     knora-base:dimY 800 ;
          |                                     knora-base:qualityLevel 100 ;
          |                                     knora-base:valueHasQname "full" .
          |
          |
          |
          |                ?newValue2 knora-base:valueHasString "test.jpg" .
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |            ?newValue2 <http://www.knora.org/ontology/knora-base#attachedToUser> <http://data.knora.org/users/b83acc5f05> .
          |
          |            ?newValue2 <http://www.knora.org/ontology/knora-base#attachedToProject> <http://data.knora.org/projects/77275339> .
          |
          |
          |        ?newValue2 knora-base:valueHasOrder ?nextOrder2 ;
          |                             knora-base:valueCreationDate ?currentTime .
          |
          |
          |
          |
          |
          |
          |
          |
          |        ?resource ?property2 ?newValue2 .
          |
          |
          |
          |
          |        # Value 3
          |        # Property: http://www.knora.org/ontology/knora-base#hasStillImageFileValue
          |
          |
          |        ?newValue3 rdf:type ?valueType3 ;
          |            knora-base:isDeleted "false"^^xsd:boolean .
          |
          |
          |                ?newValue3 knora-base:originalFilename "test.jpg" ;
          |                                     knora-base:originalMimeType "image/jpeg" ;
          |                                     knora-base:internalFilename "thumb.jpg" ;
          |                                     knora-base:internalMimeType "image/jpeg" ;
          |                                     knora-base:dimX 80 ;
          |                                     knora-base:dimY 80 ;
          |                                     knora-base:qualityLevel 10 ;
          |                                     knora-base:valueHasQname "thumbnail" .
          |
          |
          |                    ?newValue3 knora-base:valueIsPreview true .
          |
          |
          |                ?newValue3 knora-base:valueHasString "test.jpg" .
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |            ?newValue3 <http://www.knora.org/ontology/knora-base#attachedToUser> <http://data.knora.org/users/b83acc5f05> .
          |
          |            ?newValue3 <http://www.knora.org/ontology/knora-base#attachedToProject> <http://data.knora.org/projects/77275339> .
          |
          |
          |        ?newValue3 knora-base:valueHasOrder ?nextOrder3 ;
          |                             knora-base:valueCreationDate ?currentTime .
          |
          |
          |
          |
          |
          |
          |
          |
          |        ?resource ?property3 ?newValue3 .
          |
          |
          |
          |
          |        # Value 4
          |        # Property: http://www.knora.org/ontology/incunabula#hasRightSideband
          |
          |
          |
          |            ?resource ?linkProperty4 ?linkTarget4 .
          |
          |
          |
          |        ?newLinkValue4 rdf:type knora-base:LinkValue ;
          |            knora-base:isDeleted "false"^^xsd:boolean ;
          |            rdf:subject ?resource ;
          |            rdf:predicate ?linkProperty4 ;
          |            rdf:object ?linkTarget4 ;
          |            knora-base:valueHasRefCount 1 ;
          |
          |            knora-base:valueHasOrder ?nextOrder4 ;
          |            knora-base:valueCreationDate ?currentTime .
          |
          |
          |            ?newLinkValue4 <http://www.knora.org/ontology/knora-base#attachedToUser> <http://data.knora.org/users/b83acc5f05> .
          |
          |            ?newLinkValue4 <http://www.knora.org/ontology/knora-base#attachedToProject> <http://data.knora.org/projects/77275339> .
          |
          |            ?newLinkValue4 <http://www.knora.org/ontology/knora-base#hasViewPermission> <http://www.knora.org/ontology/knora-base#KnownUser> .
          |
          |            ?newLinkValue4 <http://www.knora.org/ontology/knora-base#hasModifyPermission> <http://www.knora.org/ontology/knora-base#ProjectMember> .
          |
          |            ?newLinkValue4 <http://www.knora.org/ontology/knora-base#hasDeletePermission> <http://www.knora.org/ontology/knora-base#Owner> .
          |
          |
          |
          |        ?resource ?linkValueProperty4 ?newLinkValue4 .
          |
          |
          |
          |
          |        # Value 5
          |        # Property: http://www.knora.org/ontology/incunabula#origname
          |
          |
          |        ?newValue5 rdf:type ?valueType5 ;
          |            knora-base:isDeleted "false"^^xsd:boolean .
          |
          |
          |
          |                ?newValue5 knora-base:valueHasString "Blatt" .
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |            ?newValue5 <http://www.knora.org/ontology/knora-base#attachedToUser> <http://data.knora.org/users/b83acc5f05> .
          |
          |            ?newValue5 <http://www.knora.org/ontology/knora-base#attachedToProject> <http://data.knora.org/projects/77275339> .
          |
          |            ?newValue5 <http://www.knora.org/ontology/knora-base#hasViewPermission> <http://www.knora.org/ontology/knora-base#KnownUser> .
          |
          |            ?newValue5 <http://www.knora.org/ontology/knora-base#hasViewPermission> <http://www.knora.org/ontology/knora-base#Owner> .
          |
          |            ?newValue5 <http://www.knora.org/ontology/knora-base#hasViewPermission> <http://www.knora.org/ontology/knora-base#ProjectMember> .
          |
          |            ?newValue5 <http://www.knora.org/ontology/knora-base#hasViewPermission> <http://www.knora.org/ontology/knora-base#UnknownUser> .
          |
          |
          |        ?newValue5 knora-base:valueHasOrder ?nextOrder5 ;
          |                             knora-base:valueCreationDate ?currentTime .
          |
          |
          |
          |
          |
          |
          |
          |
          |        ?resource ?property5 ?newValue5 .
          |
          |
          |
          |
          |        # Value 6
          |        # Property: http://www.knora.org/ontology/incunabula#seqnum
          |
          |
          |        ?newValue6 rdf:type ?valueType6 ;
          |            knora-base:isDeleted "false"^^xsd:boolean .
          |
          |
          |
          |                ?newValue6 knora-base:valueHasInteger 1 ;
          |                                     knora-base:valueHasString "1" .
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |            ?newValue6 <http://www.knora.org/ontology/knora-base#attachedToUser> <http://data.knora.org/users/b83acc5f05> .
          |
          |            ?newValue6 <http://www.knora.org/ontology/knora-base#attachedToProject> <http://data.knora.org/projects/77275339> .
          |
          |            ?newValue6 <http://www.knora.org/ontology/knora-base#hasViewPermission> <http://www.knora.org/ontology/knora-base#KnownUser> .
          |
          |            ?newValue6 <http://www.knora.org/ontology/knora-base#hasViewPermission> <http://www.knora.org/ontology/knora-base#ProjectMember> .
          |
          |            ?newValue6 <http://www.knora.org/ontology/knora-base#hasViewPermission> <http://www.knora.org/ontology/knora-base#UnknownUser> .
          |
          |            ?newValue6 <http://www.knora.org/ontology/knora-base#hasModifyPermission> <http://www.knora.org/ontology/knora-base#Owner> .
          |
          |
          |        ?newValue6 knora-base:valueHasOrder ?nextOrder6 ;
          |                             knora-base:valueCreationDate ?currentTime .
          |
          |
          |
          |
          |
          |
          |
          |
          |        ?resource ?property6 ?newValue6 .
          |
          |    }
          |}
          |
          |
          |    USING <http://www.ontotext.com/explicit>
          |
          |WHERE {
          |    BIND(IRI("http://www.knora.org/data/incunabula") AS ?dataNamedGraph)
          |    BIND(IRI("http://data.knora.org/missingPartOf") AS ?resource)
          |    BIND(IRI("http://www.knora.org/ontology/incunabula#page") AS ?resourceClass)
          |    BIND(IRI("http://data.knora.org/users/b83acc5f05") AS ?ownerIri)
          |    BIND(IRI("http://data.knora.org/projects/77275339") AS ?projectIri)
          |    BIND(str("Test-Page") AS ?label)
          |    BIND(NOW() AS ?currentTime)
          |
          |
          |
          |    # Value 1
          |    # Property: http://www.knora.org/ontology/incunabula#pagenum
          |
          |    BIND(IRI("http://www.knora.org/ontology/incunabula#pagenum") AS ?property1)
          |    BIND(IRI("http://data.knora.org/missingPartOf/values/nQ3tRObaQWe74WQv2_OdCg") AS ?newValue1)
          |    BIND(IRI("http://www.knora.org/ontology/knora-base#TextValue") AS ?valueType1)
          |
          |
          |
          |    ?property1 knora-base:objectClassConstraint ?propertyRange1 .
          |    ?valueType1 rdfs:subClassOf* ?propertyRange1 .
          |
          |
          |
          |    ?resourceClass rdfs:subClassOf* ?restriction1 .
          |    ?restriction1 a owl:Restriction .
          |    ?restriction1 owl:onProperty ?property1 .
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |            BIND(0 AS ?nextOrder1)
          |
          |
          |
          |
          |
          |
          |    # Value 2
          |    # Property: http://www.knora.org/ontology/knora-base#hasStillImageFileValue
          |
          |    BIND(IRI("http://www.knora.org/ontology/knora-base#hasStillImageFileValue") AS ?property2)
          |    BIND(IRI("http://data.knora.org/missingPartOf/values/GVE754RbT1CykpMnwR3Csw") AS ?newValue2)
          |    BIND(IRI("http://www.knora.org/ontology/knora-base#StillImageFileValue") AS ?valueType2)
          |
          |
          |
          |    ?property2 knora-base:objectClassConstraint ?propertyRange2 .
          |    ?valueType2 rdfs:subClassOf* ?propertyRange2 .
          |
          |
          |
          |    ?resourceClass rdfs:subClassOf* ?restriction2 .
          |    ?restriction2 a owl:Restriction .
          |    ?restriction2 owl:onProperty ?property2 .
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |            BIND(0 AS ?nextOrder2)
          |
          |
          |
          |
          |
          |
          |    # Value 3
          |    # Property: http://www.knora.org/ontology/knora-base#hasStillImageFileValue
          |
          |    BIND(IRI("http://www.knora.org/ontology/knora-base#hasStillImageFileValue") AS ?property3)
          |    BIND(IRI("http://data.knora.org/missingPartOf/values/LOT71U6hSQu7shi76oRxWQ") AS ?newValue3)
          |    BIND(IRI("http://www.knora.org/ontology/knora-base#StillImageFileValue") AS ?valueType3)
          |
          |
          |
          |    ?property3 knora-base:objectClassConstraint ?propertyRange3 .
          |    ?valueType3 rdfs:subClassOf* ?propertyRange3 .
          |
          |
          |
          |    ?resourceClass rdfs:subClassOf* ?restriction3 .
          |    ?restriction3 a owl:Restriction .
          |    ?restriction3 owl:onProperty ?property3 .
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |            BIND(1 AS ?nextOrder3)
          |
          |
          |
          |
          |
          |
          |    # Value 4
          |    # Property: http://www.knora.org/ontology/incunabula#hasRightSideband
          |
          |    BIND(IRI("http://www.knora.org/ontology/incunabula#hasRightSideband") AS ?linkProperty4)
          |    BIND(IRI("http://www.knora.org/ontology/incunabula#hasRightSidebandValue") AS ?linkValueProperty4)
          |    BIND(IRI("http://data.knora.org/missingPartOf/values/i5tE5i-RRLOH631soexPFw") AS ?newLinkValue4)
          |    BIND(IRI("http://data.knora.org/482a33d65c36") AS ?linkTarget4)
          |
          |
          |
          |    ?linkTarget4 rdf:type ?linkTargetClass4 .
          |    ?linkTargetClass4 rdfs:subClassOf+ knora-base:Resource .
          |
          |
          |
          |    ?linkProperty4 knora-base:objectClassConstraint ?expectedTargetClass4 .
          |    ?linkTargetClass4 rdfs:subClassOf* ?expectedTargetClass4 .
          |
          |
          |
          |    MINUS {
          |        ?linkTarget4 knora-base:isDeleted true .
          |    }
          |
          |
          |
          |    ?resourceClass rdfs:subClassOf* ?restriction4 .
          |    ?restriction4 a owl:Restriction .
          |    ?restriction4 owl:onProperty ?linkProperty4 .
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |            BIND(0 AS ?nextOrder4)
          |
          |
          |
          |
          |
          |
          |    # Value 5
          |    # Property: http://www.knora.org/ontology/incunabula#origname
          |
          |    BIND(IRI("http://www.knora.org/ontology/incunabula#origname") AS ?property5)
          |    BIND(IRI("http://data.knora.org/missingPartOf/values/MLWWT-F8SlKsZmRo4JMLHw") AS ?newValue5)
          |    BIND(IRI("http://www.knora.org/ontology/knora-base#TextValue") AS ?valueType5)
          |
          |
          |
          |    ?property5 knora-base:objectClassConstraint ?propertyRange5 .
          |    ?valueType5 rdfs:subClassOf* ?propertyRange5 .
          |
          |
          |
          |    ?resourceClass rdfs:subClassOf* ?restriction5 .
          |    ?restriction5 a owl:Restriction .
          |    ?restriction5 owl:onProperty ?property5 .
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |            BIND(0 AS ?nextOrder5)
          |
          |
          |
          |
          |
          |
          |    # Value 6
          |    # Property: http://www.knora.org/ontology/incunabula#seqnum
          |
          |    BIND(IRI("http://www.knora.org/ontology/incunabula#seqnum") AS ?property6)
          |    BIND(IRI("http://data.knora.org/missingPartOf/values/uWQtW_X3RxKjFyGrsQwbpQ") AS ?newValue6)
          |    BIND(IRI("http://www.knora.org/ontology/knora-base#IntValue") AS ?valueType6)
          |
          |
          |
          |    ?property6 knora-base:objectClassConstraint ?propertyRange6 .
          |    ?valueType6 rdfs:subClassOf* ?propertyRange6 .
          |
          |
          |
          |    ?resourceClass rdfs:subClassOf* ?restriction6 .
          |    ?restriction6 a owl:Restriction .
          |    ?restriction6 owl:onProperty ?property6 .
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |            BIND(0 AS ?nextOrder6)
          |
          |
          |
          |}
        """.stripMargin

    // Tries to create an incunabula:page with a missing file value (the cardinality is inherited).
    private val missingFileValue =
        """
          |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
          |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
          |PREFIX owl: <http://www.w3.org/2002/07/owl#>
          |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
          |
          |INSERT {
          |    GRAPH ?dataNamedGraph {
          |        ?resource rdf:type ?resourceClass ;
          |            knora-base:isDeleted "false"^^xsd:boolean ;
          |            knora-base:attachedToUser ?ownerIri ;
          |            knora-base:attachedToProject ?projectIri ;
          |            rdfs:label ?label ;
          |
          |
          |
          |                <http://www.knora.org/ontology/knora-base#hasViewPermission> <http://www.knora.org/ontology/knora-base#KnownUser> ;
          |
          |                <http://www.knora.org/ontology/knora-base#hasModifyPermission> <http://www.knora.org/ontology/knora-base#Owner> ;
          |
          |                <http://www.knora.org/ontology/knora-base#hasModifyPermission> <http://www.knora.org/ontology/knora-base#ProjectMember> ;
          |
          |                <http://www.knora.org/ontology/knora-base#hasRestrictedViewPermission> <http://www.knora.org/ontology/knora-base#UnknownUser> ;
          |
          |
          |            knora-base:creationDate ?currentTime .
          |
          |
          |
          |        # Value 0
          |        # Property: http://www.knora.org/ontology/incunabula#partOf
          |
          |
          |
          |            ?resource ?linkProperty0 ?linkTarget0 .
          |
          |
          |
          |        ?newLinkValue0 rdf:type knora-base:LinkValue ;
          |            knora-base:isDeleted "false"^^xsd:boolean ;
          |            rdf:subject ?resource ;
          |            rdf:predicate ?linkProperty0 ;
          |            rdf:object ?linkTarget0 ;
          |            knora-base:valueHasRefCount 1 ;
          |
          |            knora-base:valueHasOrder ?nextOrder0 ;
          |            knora-base:valueCreationDate ?currentTime .
          |
          |
          |            ?newLinkValue0 <http://www.knora.org/ontology/knora-base#attachedToUser> <http://data.knora.org/users/b83acc5f05> .
          |
          |            ?newLinkValue0 <http://www.knora.org/ontology/knora-base#attachedToProject> <http://data.knora.org/projects/77275339> .
          |
          |            ?newLinkValue0 <http://www.knora.org/ontology/knora-base#hasViewPermission> <http://www.knora.org/ontology/knora-base#KnownUser> .
          |
          |            ?newLinkValue0 <http://www.knora.org/ontology/knora-base#hasViewPermission> <http://www.knora.org/ontology/knora-base#ProjectMember> .
          |
          |            ?newLinkValue0 <http://www.knora.org/ontology/knora-base#hasViewPermission> <http://www.knora.org/ontology/knora-base#UnknownUser> .
          |
          |            ?newLinkValue0 <http://www.knora.org/ontology/knora-base#hasDeletePermission> <http://www.knora.org/ontology/knora-base#Owner> .
          |
          |
          |
          |        ?resource ?linkValueProperty0 ?newLinkValue0 .
          |
          |
          |
          |
          |        # Value 1
          |        # Property: http://www.knora.org/ontology/incunabula#pagenum
          |
          |
          |        ?newValue1 rdf:type ?valueType1 ;
          |            knora-base:isDeleted "false"^^xsd:boolean .
          |
          |
          |
          |                ?newValue1 knora-base:valueHasString "recto" .
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |            ?newValue1 <http://www.knora.org/ontology/knora-base#attachedToUser> <http://data.knora.org/users/b83acc5f05> .
          |
          |            ?newValue1 <http://www.knora.org/ontology/knora-base#attachedToProject> <http://data.knora.org/projects/77275339> .
          |
          |            ?newValue1 <http://www.knora.org/ontology/knora-base#hasViewPermission> <http://www.knora.org/ontology/knora-base#KnownUser> .
          |
          |            ?newValue1 <http://www.knora.org/ontology/knora-base#hasViewPermission> <http://www.knora.org/ontology/knora-base#UnknownUser> .
          |
          |            ?newValue1 <http://www.knora.org/ontology/knora-base#hasModifyPermission> <http://www.knora.org/ontology/knora-base#ProjectMember> .
          |
          |            ?newValue1 <http://www.knora.org/ontology/knora-base#hasDeletePermission> <http://www.knora.org/ontology/knora-base#Owner> .
          |
          |
          |        ?newValue1 knora-base:valueHasOrder ?nextOrder1 ;
          |                             knora-base:valueCreationDate ?currentTime .
          |
          |
          |
          |
          |
          |
          |
          |
          |        ?resource ?property1 ?newValue1 .
          |
          |
          |
          |
          |        # Value 4
          |        # Property: http://www.knora.org/ontology/incunabula#hasRightSideband
          |
          |
          |
          |            ?resource ?linkProperty4 ?linkTarget4 .
          |
          |
          |
          |        ?newLinkValue4 rdf:type knora-base:LinkValue ;
          |            knora-base:isDeleted "false"^^xsd:boolean ;
          |            rdf:subject ?resource ;
          |            rdf:predicate ?linkProperty4 ;
          |            rdf:object ?linkTarget4 ;
          |            knora-base:valueHasRefCount 1 ;
          |
          |            knora-base:valueHasOrder ?nextOrder4 ;
          |            knora-base:valueCreationDate ?currentTime .
          |
          |
          |            ?newLinkValue4 <http://www.knora.org/ontology/knora-base#attachedToUser> <http://data.knora.org/users/b83acc5f05> .
          |
          |            ?newLinkValue4 <http://www.knora.org/ontology/knora-base#attachedToProject> <http://data.knora.org/projects/77275339> .
          |
          |            ?newLinkValue4 <http://www.knora.org/ontology/knora-base#hasViewPermission> <http://www.knora.org/ontology/knora-base#KnownUser> .
          |
          |            ?newLinkValue4 <http://www.knora.org/ontology/knora-base#hasModifyPermission> <http://www.knora.org/ontology/knora-base#ProjectMember> .
          |
          |            ?newLinkValue4 <http://www.knora.org/ontology/knora-base#hasDeletePermission> <http://www.knora.org/ontology/knora-base#Owner> .
          |
          |
          |
          |        ?resource ?linkValueProperty4 ?newLinkValue4 .
          |
          |
          |
          |
          |        # Value 5
          |        # Property: http://www.knora.org/ontology/incunabula#origname
          |
          |
          |        ?newValue5 rdf:type ?valueType5 ;
          |            knora-base:isDeleted "false"^^xsd:boolean .
          |
          |
          |
          |                ?newValue5 knora-base:valueHasString "Blatt" .
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |            ?newValue5 <http://www.knora.org/ontology/knora-base#attachedToUser> <http://data.knora.org/users/b83acc5f05> .
          |
          |            ?newValue5 <http://www.knora.org/ontology/knora-base#attachedToProject> <http://data.knora.org/projects/77275339> .
          |
          |            ?newValue5 <http://www.knora.org/ontology/knora-base#hasViewPermission> <http://www.knora.org/ontology/knora-base#KnownUser> .
          |
          |            ?newValue5 <http://www.knora.org/ontology/knora-base#hasViewPermission> <http://www.knora.org/ontology/knora-base#Owner> .
          |
          |            ?newValue5 <http://www.knora.org/ontology/knora-base#hasViewPermission> <http://www.knora.org/ontology/knora-base#ProjectMember> .
          |
          |            ?newValue5 <http://www.knora.org/ontology/knora-base#hasViewPermission> <http://www.knora.org/ontology/knora-base#UnknownUser> .
          |
          |
          |        ?newValue5 knora-base:valueHasOrder ?nextOrder5 ;
          |                             knora-base:valueCreationDate ?currentTime .
          |
          |
          |
          |
          |
          |
          |
          |
          |        ?resource ?property5 ?newValue5 .
          |
          |
          |
          |
          |        # Value 6
          |        # Property: http://www.knora.org/ontology/incunabula#seqnum
          |
          |
          |        ?newValue6 rdf:type ?valueType6 ;
          |            knora-base:isDeleted "false"^^xsd:boolean .
          |
          |
          |
          |                ?newValue6 knora-base:valueHasInteger 1 ;
          |                                     knora-base:valueHasString "1" .
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |            ?newValue6 <http://www.knora.org/ontology/knora-base#attachedToUser> <http://data.knora.org/users/b83acc5f05> .
          |
          |            ?newValue6 <http://www.knora.org/ontology/knora-base#attachedToProject> <http://data.knora.org/projects/77275339> .
          |
          |            ?newValue6 <http://www.knora.org/ontology/knora-base#hasViewPermission> <http://www.knora.org/ontology/knora-base#KnownUser> .
          |
          |            ?newValue6 <http://www.knora.org/ontology/knora-base#hasViewPermission> <http://www.knora.org/ontology/knora-base#ProjectMember> .
          |
          |            ?newValue6 <http://www.knora.org/ontology/knora-base#hasViewPermission> <http://www.knora.org/ontology/knora-base#UnknownUser> .
          |
          |            ?newValue6 <http://www.knora.org/ontology/knora-base#hasModifyPermission> <http://www.knora.org/ontology/knora-base#Owner> .
          |
          |
          |        ?newValue6 knora-base:valueHasOrder ?nextOrder6 ;
          |                             knora-base:valueCreationDate ?currentTime .
          |
          |
          |
          |
          |
          |
          |
          |
          |        ?resource ?property6 ?newValue6 .
          |
          |    }
          |}
          |
          |
          |    USING <http://www.ontotext.com/explicit>
          |
          |WHERE {
          |    BIND(IRI("http://www.knora.org/data/incunabula") AS ?dataNamedGraph)
          |    BIND(IRI("http://data.knora.org/missingFileValue") AS ?resource)
          |    BIND(IRI("http://www.knora.org/ontology/incunabula#page") AS ?resourceClass)
          |    BIND(IRI("http://data.knora.org/users/b83acc5f05") AS ?ownerIri)
          |    BIND(IRI("http://data.knora.org/projects/77275339") AS ?projectIri)
          |    BIND(str("Test-Page") AS ?label)
          |    BIND(NOW() AS ?currentTime)
          |
          |
          |
          |    # Value 0
          |    # Property: http://www.knora.org/ontology/incunabula#partOf
          |
          |    BIND(IRI("http://www.knora.org/ontology/incunabula#partOf") AS ?linkProperty0)
          |    BIND(IRI("http://www.knora.org/ontology/incunabula#partOfValue") AS ?linkValueProperty0)
          |    BIND(IRI("http://data.knora.org/missingFileValue/values/RFzfHLk1R-mU66NAFrVTYQ") AS ?newLinkValue0)
          |    BIND(IRI("http://data.knora.org/c5058f3a") AS ?linkTarget0)
          |
          |
          |
          |    ?linkTarget0 rdf:type ?linkTargetClass0 .
          |    ?linkTargetClass0 rdfs:subClassOf+ knora-base:Resource .
          |
          |
          |
          |    ?linkProperty0 knora-base:objectClassConstraint ?expectedTargetClass0 .
          |    ?linkTargetClass0 rdfs:subClassOf* ?expectedTargetClass0 .
          |
          |
          |
          |    MINUS {
          |        ?linkTarget0 knora-base:isDeleted true .
          |    }
          |
          |
          |
          |    ?resourceClass rdfs:subClassOf* ?restriction0 .
          |    ?restriction0 a owl:Restriction .
          |    ?restriction0 owl:onProperty ?linkProperty0 .
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |            BIND(0 AS ?nextOrder0)
          |
          |
          |
          |
          |
          |
          |    # Value 1
          |    # Property: http://www.knora.org/ontology/incunabula#pagenum
          |
          |    BIND(IRI("http://www.knora.org/ontology/incunabula#pagenum") AS ?property1)
          |    BIND(IRI("http://data.knora.org/missingFileValue/values/nQ3tRObaQWe74WQv2_OdCg") AS ?newValue1)
          |    BIND(IRI("http://www.knora.org/ontology/knora-base#TextValue") AS ?valueType1)
          |
          |
          |
          |    ?property1 knora-base:objectClassConstraint ?propertyRange1 .
          |    ?valueType1 rdfs:subClassOf* ?propertyRange1 .
          |
          |
          |
          |    ?resourceClass rdfs:subClassOf* ?restriction1 .
          |    ?restriction1 a owl:Restriction .
          |    ?restriction1 owl:onProperty ?property1 .
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |            BIND(0 AS ?nextOrder1)
          |
          |
          |
          |
          |
          |
          |    # Value 4
          |    # Property: http://www.knora.org/ontology/incunabula#hasRightSideband
          |
          |    BIND(IRI("http://www.knora.org/ontology/incunabula#hasRightSideband") AS ?linkProperty4)
          |    BIND(IRI("http://www.knora.org/ontology/incunabula#hasRightSidebandValue") AS ?linkValueProperty4)
          |    BIND(IRI("http://data.knora.org/missingFileValue/values/i5tE5i-RRLOH631soexPFw") AS ?newLinkValue4)
          |    BIND(IRI("http://data.knora.org/482a33d65c36") AS ?linkTarget4)
          |
          |
          |
          |    ?linkTarget4 rdf:type ?linkTargetClass4 .
          |    ?linkTargetClass4 rdfs:subClassOf+ knora-base:Resource .
          |
          |
          |
          |    ?linkProperty4 knora-base:objectClassConstraint ?expectedTargetClass4 .
          |    ?linkTargetClass4 rdfs:subClassOf* ?expectedTargetClass4 .
          |
          |
          |
          |    MINUS {
          |        ?linkTarget4 knora-base:isDeleted true .
          |    }
          |
          |
          |
          |    ?resourceClass rdfs:subClassOf* ?restriction4 .
          |    ?restriction4 a owl:Restriction .
          |    ?restriction4 owl:onProperty ?linkProperty4 .
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |            BIND(0 AS ?nextOrder4)
          |
          |
          |
          |
          |
          |
          |    # Value 5
          |    # Property: http://www.knora.org/ontology/incunabula#origname
          |
          |    BIND(IRI("http://www.knora.org/ontology/incunabula#origname") AS ?property5)
          |    BIND(IRI("http://data.knora.org/missingFileValue/values/MLWWT-F8SlKsZmRo4JMLHw") AS ?newValue5)
          |    BIND(IRI("http://www.knora.org/ontology/knora-base#TextValue") AS ?valueType5)
          |
          |
          |
          |    ?property5 knora-base:objectClassConstraint ?propertyRange5 .
          |    ?valueType5 rdfs:subClassOf* ?propertyRange5 .
          |
          |
          |
          |    ?resourceClass rdfs:subClassOf* ?restriction5 .
          |    ?restriction5 a owl:Restriction .
          |    ?restriction5 owl:onProperty ?property5 .
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |            BIND(0 AS ?nextOrder5)
          |
          |
          |
          |
          |
          |
          |    # Value 6
          |    # Property: http://www.knora.org/ontology/incunabula#seqnum
          |
          |    BIND(IRI("http://www.knora.org/ontology/incunabula#seqnum") AS ?property6)
          |    BIND(IRI("http://data.knora.org/missingFileValue/values/uWQtW_X3RxKjFyGrsQwbpQ") AS ?newValue6)
          |    BIND(IRI("http://www.knora.org/ontology/knora-base#IntValue") AS ?valueType6)
          |
          |
          |
          |    ?property6 knora-base:objectClassConstraint ?propertyRange6 .
          |    ?valueType6 rdfs:subClassOf* ?propertyRange6 .
          |
          |
          |
          |    ?resourceClass rdfs:subClassOf* ?restriction6 .
          |    ?restriction6 a owl:Restriction .
          |    ?restriction6 owl:onProperty ?property6 .
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |            BIND(0 AS ?nextOrder6)
          |
          |
          |
          |}
        """.stripMargin

    // Tries to create an incunabula:book with two incunabula:publoc values (at most one is allowed).
    private val tooManyPublocs =
        """
          |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
          |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
          |PREFIX owl: <http://www.w3.org/2002/07/owl#>
          |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
          |
          |INSERT {
          |    GRAPH ?dataNamedGraph {
          |        ?resource rdf:type ?resourceClass ;
          |            knora-base:isDeleted "false"^^xsd:boolean ;
          |            knora-base:attachedToUser ?ownerIri ;
          |            knora-base:attachedToProject ?projectIri ;
          |            rdfs:label ?label ;
          |
          |
          |
          |                <http://www.knora.org/ontology/knora-base#hasViewPermission> <http://www.knora.org/ontology/knora-base#KnownUser> ;
          |
          |                <http://www.knora.org/ontology/knora-base#hasModifyPermission> <http://www.knora.org/ontology/knora-base#Owner> ;
          |
          |                <http://www.knora.org/ontology/knora-base#hasModifyPermission> <http://www.knora.org/ontology/knora-base#ProjectMember> ;
          |
          |                <http://www.knora.org/ontology/knora-base#hasRestrictedViewPermission> <http://www.knora.org/ontology/knora-base#UnknownUser> ;
          |
          |
          |            knora-base:creationDate ?currentTime .
          |
          |
          |
          |        # Value 0
          |        # Property: http://www.knora.org/ontology/incunabula#title
          |
          |
          |        ?newValue0 rdf:type ?valueType0 ;
          |            knora-base:isDeleted "false"^^xsd:boolean .
          |
          |
          |
          |                ?newValue0 knora-base:valueHasString "A beautiful book" .
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |            ?newValue0 <http://www.knora.org/ontology/knora-base#attachedToUser> <http://data.knora.org/users/b83acc5f05> .
          |
          |            ?newValue0 <http://www.knora.org/ontology/knora-base#attachedToProject> <http://data.knora.org/projects/77275339> .
          |
          |            ?newValue0 <http://www.knora.org/ontology/knora-base#hasViewPermission> <http://www.knora.org/ontology/knora-base#KnownUser> .
          |
          |            ?newValue0 <http://www.knora.org/ontology/knora-base#hasViewPermission> <http://www.knora.org/ontology/knora-base#UnknownUser> .
          |
          |            ?newValue0 <http://www.knora.org/ontology/knora-base#hasModifyPermission> <http://www.knora.org/ontology/knora-base#ProjectMember> .
          |
          |            ?newValue0 <http://www.knora.org/ontology/knora-base#hasDeletePermission> <http://www.knora.org/ontology/knora-base#Owner> .
          |
          |
          |        ?newValue0 knora-base:valueHasOrder ?nextOrder0 ;
          |                             knora-base:valueCreationDate ?currentTime .
          |
          |
          |
          |
          |
          |
          |
          |
          |        ?resource ?property0 ?newValue0 .
          |
          |
          |
          |
          |        # Value 1
          |        # Property: http://www.knora.org/ontology/incunabula#pubdate
          |
          |
          |        ?newValue1 rdf:type ?valueType1 ;
          |            knora-base:isDeleted "false"^^xsd:boolean .
          |
          |
          |
          |                ?newValue1 knora-base:valueHasStartJDC 2457360 ;
          |                                     knora-base:valueHasEndJDC 2457360 ;
          |                                     knora-base:valueHasStartPrecision "DAY" ;
          |                                     knora-base:valueHasEndPrecision "DAY" ;
          |                                     knora-base:valueHasCalendar "GREGORIAN" ;
          |                                     knora-base:valueHasString "2015-12-03" .
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |            ?newValue1 <http://www.knora.org/ontology/knora-base#attachedToUser> <http://data.knora.org/users/b83acc5f05> .
          |
          |            ?newValue1 <http://www.knora.org/ontology/knora-base#attachedToProject> <http://data.knora.org/projects/77275339> .
          |
          |            ?newValue1 <http://www.knora.org/ontology/knora-base#hasViewPermission> <http://www.knora.org/ontology/knora-base#KnownUser> .
          |
          |            ?newValue1 <http://www.knora.org/ontology/knora-base#hasViewPermission> <http://www.knora.org/ontology/knora-base#UnknownUser> .
          |
          |            ?newValue1 <http://www.knora.org/ontology/knora-base#hasModifyPermission> <http://www.knora.org/ontology/knora-base#ProjectMember> .
          |
          |            ?newValue1 <http://www.knora.org/ontology/knora-base#hasDeletePermission> <http://www.knora.org/ontology/knora-base#Owner> .
          |
          |
          |        ?newValue1 knora-base:valueHasOrder ?nextOrder1 ;
          |                             knora-base:valueCreationDate ?currentTime .
          |
          |
          |
          |
          |
          |
          |
          |
          |        ?resource ?property1 ?newValue1 .
          |
          |
          |
          |
          |        # Value 2
          |        # Property: http://www.knora.org/ontology/incunabula#citation
          |
          |
          |        ?newValue2 rdf:type ?valueType2 ;
          |            knora-base:isDeleted "false"^^xsd:boolean .
          |
          |
          |
          |                ?newValue2 knora-base:valueHasString "noch ein letztes" .
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |            ?newValue2 <http://www.knora.org/ontology/knora-base#attachedToUser> <http://data.knora.org/users/b83acc5f05> .
          |
          |            ?newValue2 <http://www.knora.org/ontology/knora-base#attachedToProject> <http://data.knora.org/projects/77275339> .
          |
          |            ?newValue2 <http://www.knora.org/ontology/knora-base#hasViewPermission> <http://www.knora.org/ontology/knora-base#KnownUser> .
          |
          |            ?newValue2 <http://www.knora.org/ontology/knora-base#hasViewPermission> <http://www.knora.org/ontology/knora-base#UnknownUser> .
          |
          |            ?newValue2 <http://www.knora.org/ontology/knora-base#hasModifyPermission> <http://www.knora.org/ontology/knora-base#ProjectMember> .
          |
          |            ?newValue2 <http://www.knora.org/ontology/knora-base#hasDeletePermission> <http://www.knora.org/ontology/knora-base#Owner> .
          |
          |
          |        ?newValue2 knora-base:valueHasOrder ?nextOrder2 ;
          |                             knora-base:valueCreationDate ?currentTime .
          |
          |
          |
          |
          |
          |
          |
          |
          |        ?resource ?property2 ?newValue2 .
          |
          |
          |
          |
          |        # Value 3
          |        # Property: http://www.knora.org/ontology/incunabula#citation
          |
          |
          |        ?newValue3 rdf:type ?valueType3 ;
          |            knora-base:isDeleted "false"^^xsd:boolean .
          |
          |
          |
          |                ?newValue3 knora-base:valueHasString "ein Zitat" .
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |            ?newValue3 <http://www.knora.org/ontology/knora-base#attachedToUser> <http://data.knora.org/users/b83acc5f05> .
          |
          |            ?newValue3 <http://www.knora.org/ontology/knora-base#attachedToProject> <http://data.knora.org/projects/77275339> .
          |
          |            ?newValue3 <http://www.knora.org/ontology/knora-base#hasViewPermission> <http://www.knora.org/ontology/knora-base#KnownUser> .
          |
          |            ?newValue3 <http://www.knora.org/ontology/knora-base#hasViewPermission> <http://www.knora.org/ontology/knora-base#UnknownUser> .
          |
          |            ?newValue3 <http://www.knora.org/ontology/knora-base#hasModifyPermission> <http://www.knora.org/ontology/knora-base#ProjectMember> .
          |
          |            ?newValue3 <http://www.knora.org/ontology/knora-base#hasDeletePermission> <http://www.knora.org/ontology/knora-base#Owner> .
          |
          |
          |        ?newValue3 knora-base:valueHasOrder ?nextOrder3 ;
          |                             knora-base:valueCreationDate ?currentTime .
          |
          |
          |
          |
          |
          |
          |
          |
          |        ?resource ?property3 ?newValue3 .
          |
          |
          |
          |
          |        # Value 4
          |        # Property: http://www.knora.org/ontology/incunabula#citation
          |
          |
          |        ?newValue4 rdf:type ?valueType4 ;
          |            knora-base:isDeleted "false"^^xsd:boolean .
          |
          |
          |
          |                ?newValue4 knora-base:valueHasString "und noch eines" .
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |            ?newValue4 <http://www.knora.org/ontology/knora-base#attachedToUser> <http://data.knora.org/users/b83acc5f05> .
          |
          |            ?newValue4 <http://www.knora.org/ontology/knora-base#attachedToProject> <http://data.knora.org/projects/77275339> .
          |
          |            ?newValue4 <http://www.knora.org/ontology/knora-base#hasViewPermission> <http://www.knora.org/ontology/knora-base#KnownUser> .
          |
          |            ?newValue4 <http://www.knora.org/ontology/knora-base#hasViewPermission> <http://www.knora.org/ontology/knora-base#UnknownUser> .
          |
          |            ?newValue4 <http://www.knora.org/ontology/knora-base#hasModifyPermission> <http://www.knora.org/ontology/knora-base#ProjectMember> .
          |
          |            ?newValue4 <http://www.knora.org/ontology/knora-base#hasDeletePermission> <http://www.knora.org/ontology/knora-base#Owner> .
          |
          |
          |        ?newValue4 knora-base:valueHasOrder ?nextOrder4 ;
          |                             knora-base:valueCreationDate ?currentTime .
          |
          |
          |
          |
          |
          |
          |
          |
          |        ?resource ?property4 ?newValue4 .
          |
          |
          |
          |
          |        # Value 5
          |        # Property: http://www.knora.org/ontology/incunabula#citation
          |
          |
          |        ?newValue5 rdf:type ?valueType5 ;
          |            knora-base:isDeleted "false"^^xsd:boolean .
          |
          |
          |
          |                ?newValue5 knora-base:valueHasString "This citation refers to another resource" .
          |
          |
          |
          |
          |                    ?newValue5 knora-base:valueHasStandoff
          |                        [
          |
          |
          |                                    rdf:type knora-base:StandoffVisualAttribute ;
          |                                    knora-base:standoffHasAttribute "bold" ;
          |
          |
          |                            knora-base:standoffHasStart 5 ;
          |                            knora-base:standoffHasEnd 13
          |                        ] .
          |
          |                    ?newValue5 knora-base:valueHasStandoff
          |                        [
          |
          |
          |                                    rdf:type knora-base:StandoffLink ;
          |                                    knora-base:standoffHasAttribute "_link" ;
          |                                    knora-base:standoffHasLink <http://data.knora.org/c5058f3a> ;
          |
          |
          |                            knora-base:standoffHasStart 32 ;
          |                            knora-base:standoffHasEnd 40
          |                        ] .
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |            ?newValue5 <http://www.knora.org/ontology/knora-base#attachedToUser> <http://data.knora.org/users/b83acc5f05> .
          |
          |            ?newValue5 <http://www.knora.org/ontology/knora-base#attachedToProject> <http://data.knora.org/projects/77275339> .
          |
          |            ?newValue5 <http://www.knora.org/ontology/knora-base#hasViewPermission> <http://www.knora.org/ontology/knora-base#KnownUser> .
          |
          |            ?newValue5 <http://www.knora.org/ontology/knora-base#hasViewPermission> <http://www.knora.org/ontology/knora-base#UnknownUser> .
          |
          |            ?newValue5 <http://www.knora.org/ontology/knora-base#hasModifyPermission> <http://www.knora.org/ontology/knora-base#ProjectMember> .
          |
          |            ?newValue5 <http://www.knora.org/ontology/knora-base#hasDeletePermission> <http://www.knora.org/ontology/knora-base#Owner> .
          |
          |
          |        ?newValue5 knora-base:valueHasOrder ?nextOrder5 ;
          |                             knora-base:valueCreationDate ?currentTime .
          |
          |
          |
          |
          |
          |
          |
          |
          |        ?resource ?property5 ?newValue5 .
          |
          |
          |
          |
          |        # Value 6
          |        # Property: http://www.knora.org/ontology/incunabula#publoc
          |
          |
          |        ?newValue6 rdf:type ?valueType6 ;
          |            knora-base:isDeleted "false"^^xsd:boolean .
          |
          |
          |
          |                ?newValue6 knora-base:valueHasString "Entenhausen" .
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |            ?newValue6 <http://www.knora.org/ontology/knora-base#attachedToUser> <http://data.knora.org/users/b83acc5f05> .
          |
          |            ?newValue6 <http://www.knora.org/ontology/knora-base#attachedToProject> <http://data.knora.org/projects/77275339> .
          |
          |            ?newValue6 <http://www.knora.org/ontology/knora-base#hasViewPermission> <http://www.knora.org/ontology/knora-base#KnownUser> .
          |
          |            ?newValue6 <http://www.knora.org/ontology/knora-base#hasViewPermission> <http://www.knora.org/ontology/knora-base#ProjectMember> .
          |
          |            ?newValue6 <http://www.knora.org/ontology/knora-base#hasViewPermission> <http://www.knora.org/ontology/knora-base#UnknownUser> .
          |
          |            ?newValue6 <http://www.knora.org/ontology/knora-base#hasDeletePermission> <http://www.knora.org/ontology/knora-base#Owner> .
          |
          |
          |        ?newValue6 knora-base:valueHasOrder ?nextOrder6 ;
          |                             knora-base:valueCreationDate ?currentTime .
          |
          |
          |
          |
          |
          |
          |
          |
          |        ?resource ?property6 ?newValue6 .
          |
          |
          |
          |
          |        # Value 7
          |        # Property: http://www.knora.org/ontology/incunabula#publoc
          |
          |
          |        ?newValue7 rdf:type ?valueType7 ;
          |            knora-base:isDeleted "false"^^xsd:boolean .
          |
          |
          |
          |                ?newValue7 knora-base:valueHasString "Bebenhausen" .
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |            ?newValue7 <http://www.knora.org/ontology/knora-base#attachedToUser> <http://data.knora.org/users/b83acc5f05> .
          |
          |            ?newValue7 <http://www.knora.org/ontology/knora-base#attachedToProject> <http://data.knora.org/projects/77275339> .
          |
          |            ?newValue7 <http://www.knora.org/ontology/knora-base#hasViewPermission> <http://www.knora.org/ontology/knora-base#KnownUser> .
          |
          |            ?newValue7 <http://www.knora.org/ontology/knora-base#hasViewPermission> <http://www.knora.org/ontology/knora-base#ProjectMember> .
          |
          |            ?newValue7 <http://www.knora.org/ontology/knora-base#hasViewPermission> <http://www.knora.org/ontology/knora-base#UnknownUser> .
          |
          |            ?newValue7 <http://www.knora.org/ontology/knora-base#hasDeletePermission> <http://www.knora.org/ontology/knora-base#Owner> .
          |
          |
          |        ?newValue7 knora-base:valueHasOrder ?nextOrder7 ;
          |                             knora-base:valueCreationDate ?currentTime .
          |
          |
          |
          |
          |
          |
          |
          |
          |        ?resource ?property7 ?newValue7 .
          |
          |    }
          |}
          |
          |
          |    USING <http://www.ontotext.com/explicit>
          |
          |WHERE {
          |    BIND(IRI("http://www.knora.org/data/incunabula") AS ?dataNamedGraph)
          |    BIND(IRI("http://data.knora.org/tooManyPublocs") AS ?resource)
          |    BIND(IRI("http://www.knora.org/ontology/incunabula#book") AS ?resourceClass)
          |    BIND(IRI("http://data.knora.org/users/b83acc5f05") AS ?ownerIri)
          |    BIND(IRI("http://data.knora.org/projects/77275339") AS ?projectIri)
          |    BIND(str("Test-Book") AS ?label)
          |    BIND(NOW() AS ?currentTime)
          |
          |
          |
          |    # Value 0
          |    # Property: http://www.knora.org/ontology/incunabula#title
          |
          |    BIND(IRI("http://www.knora.org/ontology/incunabula#title") AS ?property0)
          |    BIND(IRI("http://data.knora.org/tooManyPublocs/values/IKVNJVSWTryEtK4i9OCSIQ") AS ?newValue0)
          |    BIND(IRI("http://www.knora.org/ontology/knora-base#TextValue") AS ?valueType0)
          |
          |
          |
          |    ?property0 knora-base:objectClassConstraint ?propertyRange0 .
          |    ?valueType0 rdfs:subClassOf* ?propertyRange0 .
          |
          |
          |
          |    ?resourceClass rdfs:subClassOf* ?restriction0 .
          |    ?restriction0 a owl:Restriction .
          |    ?restriction0 owl:onProperty ?property0 .
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |            BIND(0 AS ?nextOrder0)
          |
          |
          |
          |
          |
          |
          |    # Value 1
          |    # Property: http://www.knora.org/ontology/incunabula#pubdate
          |
          |    BIND(IRI("http://www.knora.org/ontology/incunabula#pubdate") AS ?property1)
          |    BIND(IRI("http://data.knora.org/tooManyPublocs/values/L4YSL2SeSkKVt-J9OQAMog") AS ?newValue1)
          |    BIND(IRI("http://www.knora.org/ontology/knora-base#DateValue") AS ?valueType1)
          |
          |
          |
          |    ?property1 knora-base:objectClassConstraint ?propertyRange1 .
          |    ?valueType1 rdfs:subClassOf* ?propertyRange1 .
          |
          |
          |
          |    ?resourceClass rdfs:subClassOf* ?restriction1 .
          |    ?restriction1 a owl:Restriction .
          |    ?restriction1 owl:onProperty ?property1 .
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |            BIND(0 AS ?nextOrder1)
          |
          |
          |
          |
          |
          |
          |    # Value 2
          |    # Property: http://www.knora.org/ontology/incunabula#citation
          |
          |    BIND(IRI("http://www.knora.org/ontology/incunabula#citation") AS ?property2)
          |    BIND(IRI("http://data.knora.org/tooManyPublocs/values/oTvvcMRgR_CC-Os-61I-Qw") AS ?newValue2)
          |    BIND(IRI("http://www.knora.org/ontology/knora-base#TextValue") AS ?valueType2)
          |
          |
          |
          |    ?property2 knora-base:objectClassConstraint ?propertyRange2 .
          |    ?valueType2 rdfs:subClassOf* ?propertyRange2 .
          |
          |
          |
          |    ?resourceClass rdfs:subClassOf* ?restriction2 .
          |    ?restriction2 a owl:Restriction .
          |    ?restriction2 owl:onProperty ?property2 .
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |            BIND(0 AS ?nextOrder2)
          |
          |
          |
          |
          |
          |
          |    # Value 3
          |    # Property: http://www.knora.org/ontology/incunabula#citation
          |
          |    BIND(IRI("http://www.knora.org/ontology/incunabula#citation") AS ?property3)
          |    BIND(IRI("http://data.knora.org/tooManyPublocs/values/Jvcncu3iSr2_fWdWdOfn-w") AS ?newValue3)
          |    BIND(IRI("http://www.knora.org/ontology/knora-base#TextValue") AS ?valueType3)
          |
          |
          |
          |    ?property3 knora-base:objectClassConstraint ?propertyRange3 .
          |    ?valueType3 rdfs:subClassOf* ?propertyRange3 .
          |
          |
          |
          |    ?resourceClass rdfs:subClassOf* ?restriction3 .
          |    ?restriction3 a owl:Restriction .
          |    ?restriction3 owl:onProperty ?property3 .
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |            BIND(1 AS ?nextOrder3)
          |
          |
          |
          |
          |
          |
          |    # Value 4
          |    # Property: http://www.knora.org/ontology/incunabula#citation
          |
          |    BIND(IRI("http://www.knora.org/ontology/incunabula#citation") AS ?property4)
          |    BIND(IRI("http://data.knora.org/tooManyPublocs/values/7wJJcQLtS2mG_tyPKCe1Ig") AS ?newValue4)
          |    BIND(IRI("http://www.knora.org/ontology/knora-base#TextValue") AS ?valueType4)
          |
          |
          |
          |    ?property4 knora-base:objectClassConstraint ?propertyRange4 .
          |    ?valueType4 rdfs:subClassOf* ?propertyRange4 .
          |
          |
          |
          |    ?resourceClass rdfs:subClassOf* ?restriction4 .
          |    ?restriction4 a owl:Restriction .
          |    ?restriction4 owl:onProperty ?property4 .
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |            BIND(2 AS ?nextOrder4)
          |
          |
          |
          |
          |
          |
          |    # Value 5
          |    # Property: http://www.knora.org/ontology/incunabula#citation
          |
          |    BIND(IRI("http://www.knora.org/ontology/incunabula#citation") AS ?property5)
          |    BIND(IRI("http://data.knora.org/tooManyPublocs/values/y7zDf5oNSE6-9GNNgXSbwA") AS ?newValue5)
          |    BIND(IRI("http://www.knora.org/ontology/knora-base#TextValue") AS ?valueType5)
          |
          |
          |
          |    ?property5 knora-base:objectClassConstraint ?propertyRange5 .
          |    ?valueType5 rdfs:subClassOf* ?propertyRange5 .
          |
          |
          |
          |    ?resourceClass rdfs:subClassOf* ?restriction5 .
          |    ?restriction5 a owl:Restriction .
          |    ?restriction5 owl:onProperty ?property5 .
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |            BIND(3 AS ?nextOrder5)
          |
          |
          |
          |
          |
          |
          |    # Value 6
          |    # Property: http://www.knora.org/ontology/incunabula#publoc
          |
          |    BIND(IRI("http://www.knora.org/ontology/incunabula#publoc") AS ?property6)
          |    BIND(IRI("http://data.knora.org/tooManyPublocs/values/1ryBgY4MSn2Y8K8QAPiJBw0") AS ?newValue6)
          |    BIND(IRI("http://www.knora.org/ontology/knora-base#TextValue") AS ?valueType6)
          |
          |
          |
          |    ?property6 knora-base:objectClassConstraint ?propertyRange6 .
          |    ?valueType6 rdfs:subClassOf* ?propertyRange6 .
          |
          |
          |
          |    ?resourceClass rdfs:subClassOf* ?restriction6 .
          |    ?restriction6 a owl:Restriction .
          |    ?restriction6 owl:onProperty ?property6 .
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |            BIND(0 AS ?nextOrder6)
          |
          |
          |    # Value 7
          |    # Property: http://www.knora.org/ontology/incunabula#publoc
          |
          |    BIND(IRI("http://www.knora.org/ontology/incunabula#publoc") AS ?property7)
          |    BIND(IRI("http://data.knora.org/tooManyPublocs/values/1ryBgY4MSn2Y8K8QAPiJBw1") AS ?newValue7)
          |    BIND(IRI("http://www.knora.org/ontology/knora-base#TextValue") AS ?valueType7)
          |
          |
          |
          |    ?property7 knora-base:objectClassConstraint ?propertyRange7 .
          |    ?valueType7 rdfs:subClassOf* ?propertyRange7 .
          |
          |
          |
          |    ?resourceClass rdfs:subClassOf* ?restriction7 .
          |    ?restriction7 a owl:Restriction .
          |    ?restriction7 owl:onProperty ?property7 .
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |            BIND(1 AS ?nextOrder7)
          |}
        """.stripMargin


    private val tooManyLastModificationDates =
        """
          |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
          |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
          |PREFIX owl: <http://www.w3.org/2002/07/owl#>
          |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
          |
          |INSERT {
          |    GRAPH ?dataNamedGraph {
          |        ?resource rdf:type ?resourceClass ;
          |            knora-base:isDeleted "false"^^xsd:boolean ;
          |            knora-base:lastModificationDate "2016-01-23T11:31:24Z"^^xsd:dateTimeStamp ;
          |			   knora-base:lastModificationDate ?currentTime ;
          |            knora-base:attachedToUser ?ownerIri ;
          |            knora-base:attachedToProject ?projectIri ;
          |            rdfs:label ?label ;
          |
          |
          |
          |                <http://www.knora.org/ontology/knora-base#hasViewPermission> <http://www.knora.org/ontology/knora-base#KnownUser> ;
          |
          |                <http://www.knora.org/ontology/knora-base#hasModifyPermission> <http://www.knora.org/ontology/knora-base#Owner> ;
          |
          |                <http://www.knora.org/ontology/knora-base#hasModifyPermission> <http://www.knora.org/ontology/knora-base#ProjectMember> ;
          |
          |                <http://www.knora.org/ontology/knora-base#hasRestrictedViewPermission> <http://www.knora.org/ontology/knora-base#UnknownUser> ;
          |
          |
          |            knora-base:creationDate ?currentTime .
          |
          |
          |
          |        # Value 0
          |        # Property: http://www.knora.org/ontology/incunabula#title
          |
          |
          |        ?newValue0 rdf:type ?valueType0 ;
          |            knora-base:isDeleted "false"^^xsd:boolean .
          |
          |
          |
          |                ?newValue0 knora-base:valueHasString "A beautiful book" .
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |            ?newValue0 <http://www.knora.org/ontology/knora-base#attachedToUser> <http://data.knora.org/users/b83acc5f05> .
          |
          |            ?newValue0 <http://www.knora.org/ontology/knora-base#attachedToProject> <http://data.knora.org/projects/77275339> .
          |
          |            ?newValue0 <http://www.knora.org/ontology/knora-base#hasViewPermission> <http://www.knora.org/ontology/knora-base#KnownUser> .
          |
          |            ?newValue0 <http://www.knora.org/ontology/knora-base#hasViewPermission> <http://www.knora.org/ontology/knora-base#UnknownUser> .
          |
          |            ?newValue0 <http://www.knora.org/ontology/knora-base#hasModifyPermission> <http://www.knora.org/ontology/knora-base#ProjectMember> .
          |
          |            ?newValue0 <http://www.knora.org/ontology/knora-base#hasDeletePermission> <http://www.knora.org/ontology/knora-base#Owner> .
          |
          |
          |        ?newValue0 knora-base:valueHasOrder ?nextOrder0 ;
          |                             knora-base:valueCreationDate ?currentTime .
          |
          |
          |
          |
          |
          |
          |
          |
          |        ?resource ?property0 ?newValue0 .
          |
          |
          |
          |
          |        # Value 1
          |        # Property: http://www.knora.org/ontology/incunabula#pubdate
          |
          |
          |        ?newValue1 rdf:type ?valueType1 ;
          |            knora-base:isDeleted "false"^^xsd:boolean .
          |
          |
          |
          |                ?newValue1 knora-base:valueHasStartJDC 2457360 ;
          |                                     knora-base:valueHasEndJDC 2457360 ;
          |                                     knora-base:valueHasStartPrecision "DAY" ;
          |                                     knora-base:valueHasEndPrecision "DAY" ;
          |                                     knora-base:valueHasCalendar "GREGORIAN" ;
          |                                     knora-base:valueHasString "2015-12-03" .
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |            ?newValue1 <http://www.knora.org/ontology/knora-base#attachedToUser> <http://data.knora.org/users/b83acc5f05> .
          |
          |            ?newValue1 <http://www.knora.org/ontology/knora-base#attachedToProject> <http://data.knora.org/projects/77275339> .
          |
          |            ?newValue1 <http://www.knora.org/ontology/knora-base#hasViewPermission> <http://www.knora.org/ontology/knora-base#KnownUser> .
          |
          |            ?newValue1 <http://www.knora.org/ontology/knora-base#hasViewPermission> <http://www.knora.org/ontology/knora-base#UnknownUser> .
          |
          |            ?newValue1 <http://www.knora.org/ontology/knora-base#hasModifyPermission> <http://www.knora.org/ontology/knora-base#ProjectMember> .
          |
          |            ?newValue1 <http://www.knora.org/ontology/knora-base#hasDeletePermission> <http://www.knora.org/ontology/knora-base#Owner> .
          |
          |
          |        ?newValue1 knora-base:valueHasOrder ?nextOrder1 ;
          |                             knora-base:valueCreationDate ?currentTime .
          |
          |
          |
          |
          |        ?resource ?property1 ?newValue1 .
          |
          |
          |
          |
          |        # Value 6
          |        # Property: http://www.knora.org/ontology/incunabula#publoc
          |
          |
          |        ?newValue6 rdf:type ?valueType6 ;
          |            knora-base:isDeleted "false"^^xsd:boolean .
          |
          |
          |
          |                ?newValue6 knora-base:valueHasString "Entenhausen" .
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |            ?newValue6 <http://www.knora.org/ontology/knora-base#attachedToUser> <http://data.knora.org/users/b83acc5f05> .
          |
          |            ?newValue6 <http://www.knora.org/ontology/knora-base#attachedToProject> <http://data.knora.org/projects/77275339> .
          |
          |            ?newValue6 <http://www.knora.org/ontology/knora-base#hasViewPermission> <http://www.knora.org/ontology/knora-base#KnownUser> .
          |
          |            ?newValue6 <http://www.knora.org/ontology/knora-base#hasViewPermission> <http://www.knora.org/ontology/knora-base#ProjectMember> .
          |
          |            ?newValue6 <http://www.knora.org/ontology/knora-base#hasViewPermission> <http://www.knora.org/ontology/knora-base#UnknownUser> .
          |
          |            ?newValue6 <http://www.knora.org/ontology/knora-base#hasDeletePermission> <http://www.knora.org/ontology/knora-base#Owner> .
          |
          |
          |        ?newValue6 knora-base:valueHasOrder ?nextOrder6 ;
          |                             knora-base:valueCreationDate ?currentTime .
          |
          |
          |
          |
          |
          |
          |
          |
          |        ?resource ?property6 ?newValue6 .
          |
          |    }
          |}
          |
          |
          |    USING <http://www.ontotext.com/explicit>
          |
          |WHERE {
          |    BIND(IRI("http://www.knora.org/data/incunabula") AS ?dataNamedGraph)
          |    BIND(IRI("http://data.knora.org/tooManyLastModificationDates") AS ?resource)
          |    BIND(IRI("http://www.knora.org/ontology/incunabula#book") AS ?resourceClass)
          |    BIND(IRI("http://data.knora.org/users/b83acc5f05") AS ?ownerIri)
          |    BIND(IRI("http://data.knora.org/projects/77275339") AS ?projectIri)
          |    BIND(str("Test-Book") AS ?label)
          |    BIND(NOW() AS ?currentTime)
          |
          |
          |
          |    # Value 0
          |    # Property: http://www.knora.org/ontology/incunabula#title
          |
          |    BIND(IRI("http://www.knora.org/ontology/incunabula#title") AS ?property0)
          |    BIND(IRI("http://data.knora.org/tooManyLastModificationDates/values/IKVNJVSWTryEtK4i9OCSIQ") AS ?newValue0)
          |    BIND(IRI("http://www.knora.org/ontology/knora-base#TextValue") AS ?valueType0)
          |
          |
          |
          |    ?property0 knora-base:objectClassConstraint ?propertyRange0 .
          |    ?valueType0 rdfs:subClassOf* ?propertyRange0 .
          |
          |
          |
          |    ?resourceClass rdfs:subClassOf* ?restriction0 .
          |    ?restriction0 a owl:Restriction .
          |    ?restriction0 owl:onProperty ?property0 .
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |            BIND(0 AS ?nextOrder0)
          |
          |
          |
          |
          |
          |
          |    # Value 1
          |    # Property: http://www.knora.org/ontology/incunabula#pubdate
          |
          |    BIND(IRI("http://www.knora.org/ontology/incunabula#pubdate") AS ?property1)
          |    BIND(IRI("http://data.knora.org/tooManyLastModificationDates/values/L4YSL2SeSkKVt-J9OQAMog") AS ?newValue1)
          |    BIND(IRI("http://www.knora.org/ontology/knora-base#DateValue") AS ?valueType1)
          |
          |
          |
          |    ?property1 knora-base:objectClassConstraint ?propertyRange1 .
          |    ?valueType1 rdfs:subClassOf* ?propertyRange1 .
          |
          |
          |
          |    ?resourceClass rdfs:subClassOf* ?restriction1 .
          |    ?restriction1 a owl:Restriction .
          |    ?restriction1 owl:onProperty ?property1 .
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |            BIND(0 AS ?nextOrder1)
          |
          |
          |
          |
          |            
          |    # Value 6
          |    # Property: http://www.knora.org/ontology/incunabula#publoc
          |
          |    BIND(IRI("http://www.knora.org/ontology/incunabula#publoc") AS ?property6)
          |    BIND(IRI("http://data.knora.org/tooManyLastModificationDates/values/1ryBgY4MSn2Y8K8QAPiJBw") AS ?newValue6)
          |    BIND(IRI("http://www.knora.org/ontology/knora-base#TextValue") AS ?valueType6)
          |
          |
          |
          |    ?property6 knora-base:objectClassConstraint ?propertyRange6 .
          |    ?valueType6 rdfs:subClassOf* ?propertyRange6 .
          |
          |
          |
          |    ?resourceClass rdfs:subClassOf* ?restriction6 .
          |    ?restriction6 a owl:Restriction .
          |    ?restriction6 owl:onProperty ?property6 .
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |            BIND(0 AS ?nextOrder6)
          |
          |
          |
          |}
        """.stripMargin


    private val wrongSubjectClass =
        """
          |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
          |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
          |PREFIX owl: <http://www.w3.org/2002/07/owl#>
          |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
          |
          |INSERT {
          |    GRAPH ?dataNamedGraph {
          |        ?resource rdf:type ?resourceClass ;
          |            knora-base:valueHasString "A resource is not allowed to have a valueHasString property" ;
          |            knora-base:isDeleted "false"^^xsd:boolean ;
          |			   knora-base:lastModificationDate ?currentTime ;
          |            knora-base:attachedToUser ?ownerIri ;
          |            knora-base:attachedToProject ?projectIri ;
          |            rdfs:label ?label ;
          |
          |
          |
          |                <http://www.knora.org/ontology/knora-base#hasViewPermission> <http://www.knora.org/ontology/knora-base#KnownUser> ;
          |
          |                <http://www.knora.org/ontology/knora-base#hasModifyPermission> <http://www.knora.org/ontology/knora-base#Owner> ;
          |
          |                <http://www.knora.org/ontology/knora-base#hasModifyPermission> <http://www.knora.org/ontology/knora-base#ProjectMember> ;
          |
          |                <http://www.knora.org/ontology/knora-base#hasRestrictedViewPermission> <http://www.knora.org/ontology/knora-base#UnknownUser> ;
          |
          |
          |            knora-base:creationDate ?currentTime .
          |
          |
          |
          |        # Value 0
          |        # Property: http://www.knora.org/ontology/incunabula#title
          |
          |
          |        ?newValue0 rdf:type ?valueType0 ;
          |            knora-base:isDeleted "false"^^xsd:boolean .
          |
          |
          |
          |                ?newValue0 knora-base:valueHasString "A beautiful book" .
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |            ?newValue0 <http://www.knora.org/ontology/knora-base#attachedToUser> <http://data.knora.org/users/b83acc5f05> .
          |
          |            ?newValue0 <http://www.knora.org/ontology/knora-base#attachedToProject> <http://data.knora.org/projects/77275339> .
          |
          |            ?newValue0 <http://www.knora.org/ontology/knora-base#hasViewPermission> <http://www.knora.org/ontology/knora-base#KnownUser> .
          |
          |            ?newValue0 <http://www.knora.org/ontology/knora-base#hasViewPermission> <http://www.knora.org/ontology/knora-base#UnknownUser> .
          |
          |            ?newValue0 <http://www.knora.org/ontology/knora-base#hasModifyPermission> <http://www.knora.org/ontology/knora-base#ProjectMember> .
          |
          |            ?newValue0 <http://www.knora.org/ontology/knora-base#hasDeletePermission> <http://www.knora.org/ontology/knora-base#Owner> .
          |
          |
          |        ?newValue0 knora-base:valueHasOrder ?nextOrder0 ;
          |                             knora-base:valueCreationDate ?currentTime .
          |
          |
          |
          |
          |
          |
          |
          |
          |        ?resource ?property0 ?newValue0 .
          |
          |
          |
          |
          |        # Value 1
          |        # Property: http://www.knora.org/ontology/incunabula#pubdate
          |
          |
          |        ?newValue1 rdf:type ?valueType1 ;
          |            knora-base:isDeleted "false"^^xsd:boolean .
          |
          |
          |
          |                ?newValue1 knora-base:valueHasStartJDC 2457360 ;
          |                                     knora-base:valueHasEndJDC 2457360 ;
          |                                     knora-base:valueHasStartPrecision "DAY" ;
          |                                     knora-base:valueHasEndPrecision "DAY" ;
          |                                     knora-base:valueHasCalendar "GREGORIAN" ;
          |                                     knora-base:valueHasString "2015-12-03" .
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |            ?newValue1 <http://www.knora.org/ontology/knora-base#attachedToUser> <http://data.knora.org/users/b83acc5f05> .
          |
          |            ?newValue1 <http://www.knora.org/ontology/knora-base#attachedToProject> <http://data.knora.org/projects/77275339> .
          |
          |            ?newValue1 <http://www.knora.org/ontology/knora-base#hasViewPermission> <http://www.knora.org/ontology/knora-base#KnownUser> .
          |
          |            ?newValue1 <http://www.knora.org/ontology/knora-base#hasViewPermission> <http://www.knora.org/ontology/knora-base#UnknownUser> .
          |
          |            ?newValue1 <http://www.knora.org/ontology/knora-base#hasModifyPermission> <http://www.knora.org/ontology/knora-base#ProjectMember> .
          |
          |            ?newValue1 <http://www.knora.org/ontology/knora-base#hasDeletePermission> <http://www.knora.org/ontology/knora-base#Owner> .
          |
          |
          |        ?newValue1 knora-base:valueHasOrder ?nextOrder1 ;
          |                             knora-base:valueCreationDate ?currentTime .
          |
          |
          |
          |
          |        ?resource ?property1 ?newValue1 .
          |
          |
          |
          |
          |        # Value 6
          |        # Property: http://www.knora.org/ontology/incunabula#publoc
          |
          |
          |        ?newValue6 rdf:type ?valueType6 ;
          |            knora-base:isDeleted "false"^^xsd:boolean .
          |
          |
          |
          |                ?newValue6 knora-base:valueHasString "Entenhausen" .
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |            ?newValue6 <http://www.knora.org/ontology/knora-base#attachedToUser> <http://data.knora.org/users/b83acc5f05> .
          |
          |            ?newValue6 <http://www.knora.org/ontology/knora-base#attachedToProject> <http://data.knora.org/projects/77275339> .
          |
          |            ?newValue6 <http://www.knora.org/ontology/knora-base#hasViewPermission> <http://www.knora.org/ontology/knora-base#KnownUser> .
          |
          |            ?newValue6 <http://www.knora.org/ontology/knora-base#hasViewPermission> <http://www.knora.org/ontology/knora-base#ProjectMember> .
          |
          |            ?newValue6 <http://www.knora.org/ontology/knora-base#hasViewPermission> <http://www.knora.org/ontology/knora-base#UnknownUser> .
          |
          |            ?newValue6 <http://www.knora.org/ontology/knora-base#hasDeletePermission> <http://www.knora.org/ontology/knora-base#Owner> .
          |
          |
          |        ?newValue6 knora-base:valueHasOrder ?nextOrder6 ;
          |                             knora-base:valueCreationDate ?currentTime .
          |
          |
          |
          |
          |
          |
          |
          |
          |        ?resource ?property6 ?newValue6 .
          |
          |
          |    }
          |}
          |
          |
          |    USING <http://www.ontotext.com/explicit>
          |
          |WHERE {
          |    BIND(IRI("http://www.knora.org/data/incunabula") AS ?dataNamedGraph)
          |    BIND(IRI("http://data.knora.org/wrongSubjectClass") AS ?resource)
          |    BIND(IRI("http://www.knora.org/ontology/incunabula#book") AS ?resourceClass)
          |    BIND(IRI("http://data.knora.org/users/b83acc5f05") AS ?ownerIri)
          |    BIND(IRI("http://data.knora.org/projects/77275339") AS ?projectIri)
          |    BIND(str("Test-Book") AS ?label)
          |    BIND(NOW() AS ?currentTime)
          |
          |
          |
          |    # Value 0
          |    # Property: http://www.knora.org/ontology/incunabula#title
          |
          |    BIND(IRI("http://www.knora.org/ontology/incunabula#title") AS ?property0)
          |    BIND(IRI("http://data.knora.org/wrongSubjectClass/values/IKVNJVSWTryEtK4i9OCSIQ") AS ?newValue0)
          |    BIND(IRI("http://www.knora.org/ontology/knora-base#TextValue") AS ?valueType0)
          |
          |
          |
          |    ?property0 knora-base:objectClassConstraint ?propertyRange0 .
          |    ?valueType0 rdfs:subClassOf* ?propertyRange0 .
          |
          |
          |
          |    ?resourceClass rdfs:subClassOf* ?restriction0 .
          |    ?restriction0 a owl:Restriction .
          |    ?restriction0 owl:onProperty ?property0 .
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |            BIND(0 AS ?nextOrder0)
          |
          |
          |
          |
          |
          |
          |    # Value 1
          |    # Property: http://www.knora.org/ontology/incunabula#pubdate
          |
          |    BIND(IRI("http://www.knora.org/ontology/incunabula#pubdate") AS ?property1)
          |    BIND(IRI("http://data.knora.org/wrongSubjectClass/values/L4YSL2SeSkKVt-J9OQAMog") AS ?newValue1)
          |    BIND(IRI("http://www.knora.org/ontology/knora-base#DateValue") AS ?valueType1)
          |
          |
          |
          |    ?property1 knora-base:objectClassConstraint ?propertyRange1 .
          |    ?valueType1 rdfs:subClassOf* ?propertyRange1 .
          |
          |
          |
          |    ?resourceClass rdfs:subClassOf* ?restriction1 .
          |    ?restriction1 a owl:Restriction .
          |    ?restriction1 owl:onProperty ?property1 .
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |            BIND(0 AS ?nextOrder1)
          |
          |
          |
          |
          |
          |    # Value 6
          |    # Property: http://www.knora.org/ontology/incunabula#publoc
          |
          |    BIND(IRI("http://www.knora.org/ontology/incunabula#publoc") AS ?property6)
          |    BIND(IRI("http://data.knora.org/wrongSubjectClass/values/1ryBgY4MSn2Y8K8QAPiJBw") AS ?newValue6)
          |    BIND(IRI("http://www.knora.org/ontology/knora-base#TextValue") AS ?valueType6)
          |
          |
          |
          |    ?property6 knora-base:objectClassConstraint ?propertyRange6 .
          |    ?valueType6 rdfs:subClassOf* ?propertyRange6 .
          |
          |
          |
          |    ?resourceClass rdfs:subClassOf* ?restriction6 .
          |    ?restriction6 a owl:Restriction .
          |    ?restriction6 owl:onProperty ?property6 .
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |            BIND(0 AS ?nextOrder6)
          |
          |
          |
          |}
        """.stripMargin


    private val wrongObjectClass =
        """
          |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
          |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
          |PREFIX owl: <http://www.w3.org/2002/07/owl#>
          |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
          |
          |INSERT {
          |    GRAPH ?dataNamedGraph {
          |        ?resource rdf:type ?resourceClass ;
          |            knora-base:isDeleted "false"^^xsd:boolean ;
          |			   knora-base:lastModificationDate ?currentTime ;
          |            knora-base:attachedToUser ?ownerIri ;
          |            knora-base:attachedToProject ?projectIri ;
          |            rdfs:label ?label ;
          |
          |
          |
          |                <http://www.knora.org/ontology/knora-base#hasViewPermission> <http://www.knora.org/ontology/knora-base#KnownUser> ;
          |
          |                <http://www.knora.org/ontology/knora-base#hasModifyPermission> <http://www.knora.org/ontology/knora-base#Owner> ;
          |
          |                <http://www.knora.org/ontology/knora-base#hasModifyPermission> <http://www.knora.org/ontology/knora-base#ProjectMember> ;
          |
          |                <http://www.knora.org/ontology/knora-base#hasRestrictedViewPermission> <http://www.knora.org/ontology/knora-base#UnknownUser> ;
          |
          |
          |            knora-base:creationDate ?currentTime .
          |
          |
          |
          |        # Value 0
          |        # Property: http://www.knora.org/ontology/incunabula#title
          |
          |
          |        ?newValue0 rdf:type ?valueType0 ;
          |            knora-base:isDeleted "false"^^xsd:boolean .
          |
          |
          |
          |                ?newValue0 knora-base:valueHasString "A beautiful book" .
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |            ?newValue0 <http://www.knora.org/ontology/knora-base#attachedToUser> <http://data.knora.org/users/b83acc5f05> .
          |
          |            ?newValue0 <http://www.knora.org/ontology/knora-base#attachedToProject> <http://data.knora.org/projects/77275339> .
          |
          |            ?newValue0 <http://www.knora.org/ontology/knora-base#hasViewPermission> <http://www.knora.org/ontology/knora-base#KnownUser> .
          |
          |            ?newValue0 <http://www.knora.org/ontology/knora-base#hasViewPermission> <http://www.knora.org/ontology/knora-base#UnknownUser> .
          |
          |            ?newValue0 <http://www.knora.org/ontology/knora-base#hasModifyPermission> <http://www.knora.org/ontology/knora-base#ProjectMember> .
          |
          |            ?newValue0 <http://www.knora.org/ontology/knora-base#hasDeletePermission> <http://www.knora.org/ontology/knora-base#Owner> .
          |
          |
          |        ?newValue0 knora-base:valueHasOrder ?nextOrder0 ;
          |                             knora-base:valueCreationDate ?currentTime .
          |
          |
          |
          |
          |
          |
          |
          |
          |        ?resource ?property1 ?newValue0 .
          |
          |
          |
          |
          |        # Value 1
          |        # Property: http://www.knora.org/ontology/incunabula#pubdate
          |
          |
          |        ?newValue1 rdf:type ?valueType1 ;
          |            knora-base:isDeleted "false"^^xsd:boolean .
          |
          |
          |
          |                ?newValue1 knora-base:valueHasStartJDC 2457360 ;
          |                                     knora-base:valueHasEndJDC 2457360 ;
          |                                     knora-base:valueHasStartPrecision "DAY" ;
          |                                     knora-base:valueHasEndPrecision "DAY" ;
          |                                     knora-base:valueHasCalendar "GREGORIAN" ;
          |                                     knora-base:valueHasString "2015-12-03" .
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |            ?newValue1 <http://www.knora.org/ontology/knora-base#attachedToUser> <http://data.knora.org/users/b83acc5f05> .
          |
          |            ?newValue1 <http://www.knora.org/ontology/knora-base#attachedToProject> <http://data.knora.org/projects/77275339> .
          |
          |            ?newValue1 <http://www.knora.org/ontology/knora-base#hasViewPermission> <http://www.knora.org/ontology/knora-base#KnownUser> .
          |
          |            ?newValue1 <http://www.knora.org/ontology/knora-base#hasViewPermission> <http://www.knora.org/ontology/knora-base#UnknownUser> .
          |
          |            ?newValue1 <http://www.knora.org/ontology/knora-base#hasModifyPermission> <http://www.knora.org/ontology/knora-base#ProjectMember> .
          |
          |            ?newValue1 <http://www.knora.org/ontology/knora-base#hasDeletePermission> <http://www.knora.org/ontology/knora-base#Owner> .
          |
          |
          |        ?newValue1 knora-base:valueHasOrder ?nextOrder1 ;
          |                             knora-base:valueCreationDate ?currentTime .
          |
          |
          |
          |
          |        ?resource ?property0 ?newValue1 .
          |
          |
          |
          |
          |        # Value 6
          |        # Property: http://www.knora.org/ontology/incunabula#publoc
          |
          |
          |        ?newValue6 rdf:type ?valueType6 ;
          |            knora-base:isDeleted "false"^^xsd:boolean .
          |
          |
          |
          |                ?newValue6 knora-base:valueHasString "Entenhausen" .
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |            ?newValue6 <http://www.knora.org/ontology/knora-base#attachedToUser> <http://data.knora.org/users/b83acc5f05> .
          |
          |            ?newValue6 <http://www.knora.org/ontology/knora-base#attachedToProject> <http://data.knora.org/projects/77275339> .
          |
          |            ?newValue6 <http://www.knora.org/ontology/knora-base#hasViewPermission> <http://www.knora.org/ontology/knora-base#KnownUser> .
          |
          |            ?newValue6 <http://www.knora.org/ontology/knora-base#hasViewPermission> <http://www.knora.org/ontology/knora-base#ProjectMember> .
          |
          |            ?newValue6 <http://www.knora.org/ontology/knora-base#hasViewPermission> <http://www.knora.org/ontology/knora-base#UnknownUser> .
          |
          |            ?newValue6 <http://www.knora.org/ontology/knora-base#hasDeletePermission> <http://www.knora.org/ontology/knora-base#Owner> .
          |
          |
          |        ?newValue6 knora-base:valueHasOrder ?nextOrder6 ;
          |                             knora-base:valueCreationDate ?currentTime .
          |
          |
          |
          |
          |
          |
          |
          |
          |        ?resource ?property6 ?newValue6 .
          |
          |    }
          |}
          |
          |
          |    USING <http://www.ontotext.com/explicit>
          |
          |WHERE {
          |    BIND(IRI("http://www.knora.org/data/incunabula") AS ?dataNamedGraph)
          |    BIND(IRI("http://data.knora.org/wrongObjectClass") AS ?resource)
          |    BIND(IRI("http://www.knora.org/ontology/incunabula#book") AS ?resourceClass)
          |    BIND(IRI("http://data.knora.org/users/b83acc5f05") AS ?ownerIri)
          |    BIND(IRI("http://data.knora.org/projects/77275339") AS ?projectIri)
          |    BIND(str("Test-Book") AS ?label)
          |    BIND(NOW() AS ?currentTime)
          |
          |
          |
          |    # Value 0
          |    # Property: http://www.knora.org/ontology/incunabula#title
          |
          |    BIND(IRI("http://www.knora.org/ontology/incunabula#title") AS ?property0)
          |    BIND(IRI("http://data.knora.org/wrongObjectClass/values/IKVNJVSWTryEtK4i9OCSIQ") AS ?newValue0)
          |    BIND(IRI("http://www.knora.org/ontology/knora-base#TextValue") AS ?valueType0)
          |
          |
          |
          |    ?property0 knora-base:objectClassConstraint ?propertyRange0 .
          |    ?valueType0 rdfs:subClassOf* ?propertyRange0 .
          |
          |
          |
          |    ?resourceClass rdfs:subClassOf* ?restriction0 .
          |    ?restriction0 a owl:Restriction .
          |    ?restriction0 owl:onProperty ?property0 .
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |            BIND(0 AS ?nextOrder0)
          |
          |
          |
          |
          |
          |
          |    # Value 1
          |    # Property: http://www.knora.org/ontology/incunabula#pubdate
          |
          |    BIND(IRI("http://www.knora.org/ontology/incunabula#pubdate") AS ?property1)
          |    BIND(IRI("http://data.knora.org/wrongObjectClass/values/L4YSL2SeSkKVt-J9OQAMog") AS ?newValue1)
          |    BIND(IRI("http://www.knora.org/ontology/knora-base#DateValue") AS ?valueType1)
          |
          |
          |
          |    ?property1 knora-base:objectClassConstraint ?propertyRange1 .
          |    ?valueType1 rdfs:subClassOf* ?propertyRange1 .
          |
          |
          |
          |    ?resourceClass rdfs:subClassOf* ?restriction1 .
          |    ?restriction1 a owl:Restriction .
          |    ?restriction1 owl:onProperty ?property1 .
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |            BIND(0 AS ?nextOrder1)
          |
          |
          |
          |
          |
          |    # Value 6
          |    # Property: http://www.knora.org/ontology/incunabula#publoc
          |
          |    BIND(IRI("http://www.knora.org/ontology/incunabula#publoc") AS ?property6)
          |    BIND(IRI("http://data.knora.org/wrongObjectClass/values/1ryBgY4MSn2Y8K8QAPiJBw") AS ?newValue6)
          |    BIND(IRI("http://www.knora.org/ontology/knora-base#TextValue") AS ?valueType6)
          |
          |
          |
          |    ?property6 knora-base:objectClassConstraint ?propertyRange6 .
          |    ?valueType6 rdfs:subClassOf* ?propertyRange6 .
          |
          |
          |
          |    ?resourceClass rdfs:subClassOf* ?restriction6 .
          |    ?restriction6 a owl:Restriction .
          |    ?restriction6 owl:onProperty ?property6 .
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |            BIND(0 AS ?nextOrder6)
          |
          |
          |
          |}
        """.stripMargin

}
