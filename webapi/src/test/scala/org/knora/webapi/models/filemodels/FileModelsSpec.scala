package org.knora.webapi.models.filemodels

import org.knora.webapi.{ApiV2Complex, CoreSpec}
import org.knora.webapi.exceptions.AssertionException
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.messages.IriConversions._
import org.knora.webapi.messages.v2.responder.resourcemessages.{CreateResourceV2, CreateValueInNewResourceV2}
import org.knora.webapi.messages.v2.responder.valuemessages.{DocumentFileValueContentV2, FileValueV2}

import java.time.Instant
import java.util.UUID
import spray.json._
import spray.json.DefaultJsonProtocol._

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

      "handle non-specified ontology correctly if no ontology IRI is provided" in {
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

      "handle non-specified ontology correctly if an ontology IRI is provided" in {
        val expectedContext = """"@context" : {
                                |    "rdf": "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
                                |    "knora-api": "http://api.knora.org/ontology/knora-api/v2#",
                                |    "rdfs": "http://www.w3.org/2000/01/rdf-schema#",
                                |    "xsd": "http://www.w3.org/2001/XMLSchema#",
                                |    "biblio": "http://www.knora.org/ontology/0801/biblio",
                                |  }
                                |""".stripMargin

        val context = FileModelUtil.getJsonLdContext(
          ontology = "biblio",
          ontologyIRI = Some("http://www.knora.org/ontology/0801/biblio")
        )
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

      "create a valid representation of a StillImageRepresentation with default values" in {
        val internalFilename = "image.jp2"
        val stillImageRepresentation = UploadFileRequest.make(
          fileType = FileType.StillImageFile(),
          internalFilename = internalFilename
        )
        stillImageRepresentation.fileType match {
          case FileType.StillImageFile(x, y) =>
            x should equal(100)
            y should equal(100)
          case _ =>
            throw AssertionException(
              s"FileType ${stillImageRepresentation.fileType} did not match StillImageFile(_, _)"
            )
        }
        stillImageRepresentation.internalFilename should equal(internalFilename)
      }

      "create a valid representation of a StillImageRepresentation with custom values" in {
        val internalFilename = "image.jp2"
        val stillImageRepresentation = UploadFileRequest.make(
          fileType = FileType.StillImageFile(dimX = 10, dimY = 10),
          internalFilename = internalFilename
        )
        stillImageRepresentation.fileType match {
          case FileType.StillImageFile(x, y) =>
            x should equal(10)
            y should equal(10)
          case _ =>
            throw AssertionException(
              s"FileType ${stillImageRepresentation.fileType} did not match StillImageFile(_, _)"
            )
        }
        stillImageRepresentation.internalFilename should equal(internalFilename)
      }

      "create a valid representation of a MovingImageRepresentation with default values" in {
        val internalFilename = "video.mp4"
        val movingImageRepresentation = UploadFileRequest.make(
          fileType = FileType.MovingImageFile(),
          internalFilename = internalFilename
        )
        movingImageRepresentation.fileType match {
          case FileType.MovingImageFile(x, y) =>
            x should equal(100)
            y should equal(100)
          case _ =>
            throw AssertionException(
              s"FileType ${movingImageRepresentation.fileType} did not match MovingImageFile(_, _)"
            )
        }
        movingImageRepresentation.internalFilename should equal(internalFilename)
      }

      "create a valid representation of a MovingImageRepresentation with custom values" in {
        val internalFilename = "video.mp4"
        val movingImageRepresentation = UploadFileRequest.make(
          fileType = FileType.MovingImageFile(10, 11),
          internalFilename = internalFilename
        )
        movingImageRepresentation.fileType match {
          case FileType.MovingImageFile(x, y) =>
            x should equal(10)
            y should equal(11)
          case _ =>
            throw AssertionException(
              s"FileType ${movingImageRepresentation.fileType} did not match MovingImageFile(_, _)"
            )
        }
        movingImageRepresentation.internalFilename should equal(internalFilename)
      }

      "create a valid representation of a AudioRepresentation" in {
        val internalFilename = "audio.mpeg"
        val audioRepresentation = UploadFileRequest.make(
          fileType = FileType.AudioFile,
          internalFilename = internalFilename
        )
        audioRepresentation.fileType should equal(FileType.AudioFile)
        audioRepresentation.internalFilename should equal(internalFilename)
      }

      "create a valid representation of a TextRepresentation" in {
        val internalFilename = "text.txt"
        val textRepresentation = UploadFileRequest.make(
          fileType = FileType.TextFile,
          internalFilename = internalFilename
        )
        textRepresentation.fileType should equal(FileType.TextFile)
        textRepresentation.internalFilename should equal(internalFilename)
      }

      "create a valid representation of a ArchiveRepresentation" in {
        val internalFilename = "archive.zip"
        val archiveRepresentation = UploadFileRequest.make(
          fileType = FileType.ArchiveFile,
          internalFilename = internalFilename
        )
        archiveRepresentation.fileType should equal(FileType.ArchiveFile)
        archiveRepresentation.internalFilename should equal(internalFilename)
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

      "correctly serialize a DocumentRepresentation with custom values" in {
        val className = Some("ThingDocument")
        val ontologyName = "biblio"
        val shortcode = SharedTestDataADM.beolProject.shortcode
        val internalFilename = "document-file.pdf"
        val ontologyIRI = SharedTestDataADM.beolProject.ontologies.find(_.endsWith(ontologyName))
        val label = "a custom label"

        val documentRepresentation = UploadFileRequest.make(
          fileType = FileType.DocumentFile(pageCount = None, dimX = Some(20), dimY = None),
          internalFilename = internalFilename,
          label = label
        )
        val json = documentRepresentation
          .toJsonLd(
            className = className,
            ontologyName = ontologyName,
            shortcode = shortcode,
            ontologyIRI = ontologyIRI
          )
          .parseJson
        val expectedJSON = Map(
          "@type" -> s"$ontologyName:${className.get}".toJson,
          "knora-api:hasDocumentFileValue" -> Map(
            "@type" -> "knora-api:DocumentFileValue",
            "knora-api:fileValueHasFilename" -> internalFilename
          ).toJson,
          "knora-api:attachedToProject" -> Map(
            "@id" -> s"http://rdfh.ch/projects/$shortcode"
          ).toJson,
          "rdfs:label" -> label.toJson,
          "@context" -> Map(
            "rdf" -> "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
            "knora-api" -> "http://api.knora.org/ontology/knora-api/v2#",
            "rdfs" -> "http://www.w3.org/2000/01/rdf-schema#",
            "xsd" -> "http://www.w3.org/2001/XMLSchema#",
            ontologyName -> ontologyIRI.get
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

      "correctly serialize a DocumentRepresentation with custom values" in {

        //        val project = SharedTestDataADM.beolProject
        //        val resourceIri = stringFormatter.makeRandomResourceIri(project.shortcode)
        //        val comment = Some("This is a custom comment")
        //        val internalMimetype = Some("application/msword")
        //        val originalFilename = Some("document-file.docm")
        //        val originalMimetype = Some("application/vnd.ms-word.document.macroEnabled.12")
        //        val customValueIri = Some("http://www.knora.org/ontology/0801/biblio#hasThingDocumentValue".toSmartIri)
        //        val customValueUUID = Some(UUID.randomUUID())
        //        val customValueCreationDate = Some(Instant.now())
        //        val valuePermissions = Some("V knora-admin:UnknownUser,knora-admin:KnownUser|M knora-admin:ProjectMember")
        //        val resourcePermissions = Some("V knora-admin:UnknownUser|M knora-admin:ProjectMember,knora-admin:KnownUser")

        // TODO: implement
      }

    }

  }
}
