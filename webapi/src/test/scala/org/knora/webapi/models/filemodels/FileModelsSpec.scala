package org.knora.webapi.models.filemodels

import org.knora.webapi.{ApiV2Complex, CoreSpec}
import org.knora.webapi.exceptions.AssertionException
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.messages.IriConversions._
import org.knora.webapi.messages.util.ErrorHandlingMap
import org.knora.webapi.messages.v2.responder.resourcemessages.{CreateResourceV2, CreateValueInNewResourceV2}
import org.knora.webapi.messages.v2.responder.valuemessages.{DocumentFileValueContentV2, FileValueV2}

import java.time.Instant
import java.util.UUID
import spray.json._
import spray.json.DefaultJsonProtocol._

import scala.collection.immutable.{AbstractMap, SeqMap, SortedMap}

class FileModelsSpec extends CoreSpec {
  implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

  "FileModelsUtil," when {

    "creating a JSON-LD context," should {

      "handle `anything` ontology correctly" in {
        val expectedContext = """"@context" : {
                                |    "rdf": "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
                                |    "knora-api": "http://api.knora.org/ontology/knora-api/v2#",
                                |    "rdfs": "http://www.w3.org/2000/01/rdf-schema#",
                                |    "xsd": "http://www.w3.org/2001/XMLSchema#",
                                |    "anything": "http://0.0.0.0:3333/ontology/0001/anything/v2#"
                                |  }
                                |""".stripMargin

        val context = FileModelUtil.getJsonLdContext("anything")
        context should equal(expectedContext)
      }

      "handle non-specified ontology correctly" in {
        val expectedContext = """"@context" : {
                                |    "rdf": "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
                                |    "knora-api": "http://api.knora.org/ontology/knora-api/v2#",
                                |    "rdfs": "http://www.w3.org/2000/01/rdf-schema#",
                                |    "xsd": "http://www.w3.org/2001/XMLSchema#"
                                |  }
                                |""".stripMargin

        val context = FileModelUtil.getJsonLdContext("knora-api")
        context should equal(expectedContext)
      }
    }
  }
  "FileModels," when {

    "creating an UploadFileRequest," should {

      "create a valid representation of a DocumentRepresentation with default values" in {
        val internalFilename = "document-file.pdf"
        val documentRepresentation = UploadFileRequest.make(
          fileType = FileType.DocumentFile(),
          internalFilename = internalFilename
        )
        documentRepresentation.fileType match {
          case FileType.DocumentFile(pg, x, y) =>
            pg should equal(Some(1))
            x should equal(Some(100))
            y should equal(Some(100))
          case _ =>
            throw AssertionException(s"FileType ${documentRepresentation.fileType} did not match DocumentFile(_, _, _)")
        }
        documentRepresentation.internalFilename should equal(internalFilename)
      }

      "create a valid representation of a DocumentRepresentation with custom values" in {
//        val project = SharedTestDataADM.beolProject
//        val resourceIri = stringFormatter.makeRandomResourceIri(project.shortcode)
//        val comment = Some("This is a custom comment")
//        val className = Some("biblio:ThingDocument")
//        val ontologyName = "biblio"
//        val internalMimetype = Some("application/msword")
//        val originalFilename = Some("document-file.docm")
//        val originalMimetype = Some("application/vnd.ms-word.document.macroEnabled.12")
//        val customValueIri = Some("http://www.knora.org/ontology/0801/biblio#hasThingDocumentValue".toSmartIri)
//        val customValueUUID = Some(UUID.randomUUID())
//        val customValueCreationDate = Some(Instant.now())
//        val valuePermissions = Some("V knora-admin:UnknownUser,knora-admin:KnownUser|M knora-admin:ProjectMember")
//        val label = "a custom label"
//        val resourcePermissions = Some("V knora-admin:UnknownUser|M knora-admin:ProjectMember,knora-admin:KnownUser")
//
        val internalFilename = "document-file.doc"
        val dimX = Some(20)
        val dimY = Some(30)
        val pageCount = Some(550)
        val documentRepresentation = UploadFileRequest.make(
          fileType = FileType.DocumentFile(
            pageCount = pageCount,
            dimX = dimX,
            dimY = dimY
          ),
          internalFilename = internalFilename
        )
        documentRepresentation.fileType match {
          case FileType.DocumentFile(pg, x, y) =>
            pg should equal(pageCount)
            x should equal(dimX)
            y should equal(dimY)
          case _ =>
            throw AssertionException(s"FileType ${documentRepresentation.fileType} did not match DocumentFile(_, _, _)")
        }
        documentRepresentation.internalFilename should equal(internalFilename)
      }
    }

    "generating a JSON-LD representation of a UploadFileRequest," should {

      "correctly serialize a DocumentRepresentation with default values" in {
        val internalFilename = "document-file.pdf"
        val documentRepresentation = UploadFileRequest.make(
          fileType = FileType.DocumentFile(),
          internalFilename = internalFilename
        )
        val json = documentRepresentation.toJsonLd().parseJson
        val expectedJSON = Map(
          "@type" -> "knora-api:DocumentRepresentation".toJson,
          "knora-api:hasDocumentFileValue" -> Map(
            "@type" -> "knora-api:DocumentFileValue",
            "knora-api:fileValueHasFilename" -> "document-file.pdf"
          ).toJson,
          "knora-api:attachedToProject" -> Map(
            "@id" -> "http://rdfh.ch/projects/0001"
          ).toJson,
          "rdfs:label" -> "test label".toJson,
          "@context" -> Map(
            "rdf" -> "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
            "knora-api" -> "http://api.knora.org/ontology/knora-api/v2#",
            "rdfs" -> "http://www.w3.org/2000/01/rdf-schema#",
            "xsd" -> "http://www.w3.org/2001/XMLSchema#"
          ).toJson
        ).toJson
        json should equal(expectedJSON)
      }

    }

    "generating a message representation of a UploadFileRequest," should {

      "correctly serialize a DocumentRepresentation with default values" in {
        val internalFilename = "document-file.pdf"
        val documentRepresentation = UploadFileRequest.make(
          fileType = FileType.DocumentFile(),
          internalFilename = internalFilename
        )
        val msg = documentRepresentation.toMessage()

        msg.resourceClassIri should equal(FileModelUtil.getFileRepresentationClassIri(FileType.DocumentFile()))
        msg.label should equal("test label")
        msg.values should equal(
          Map(
            FileModelUtil.getFileRepresentationPropertyIri(FileType.DocumentFile()) -> List(
              CreateValueInNewResourceV2(
                valueContent = DocumentFileValueContentV2(
                  ontologySchema = ApiV2Complex,
                  fileValue = FileValueV2(
                    internalFilename = internalFilename,
                    internalMimeType = "application/pdf",
                    originalFilename = None,
                    originalMimeType = Some("application/pdf")
                  ),
                  pageCount = Some(1),
                  dimX = Some(100),
                  dimY = Some(100),
                  comment = None
                ),
                customValueIri = None,
                customValueUUID = None,
                customValueCreationDate = None,
                permissions = None
              )
            )
          )
        )
        msg.projectADM should equal(SharedTestDataADM.anythingProject)
        msg.permissions should equal(None)

      }

    }

  }
}
