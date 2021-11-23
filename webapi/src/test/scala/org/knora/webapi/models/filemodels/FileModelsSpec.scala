package org.knora.webapi.models.filemodels

import org.knora.webapi.CoreSpec
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.messages.IriConversions._

import java.time.Instant
import java.util.UUID

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
          fileType = FileType.DocumentFile,
          internalFilename = internalFilename
        )
        documentRepresentation.fileType should equal(FileType.DocumentFile)
        documentRepresentation.internalFilename should equal(internalFilename)
        documentRepresentation.internalMimeType should equal(None)
        documentRepresentation.dimX should equal(Some(100))
        documentRepresentation.dimY should equal(Some(100))
        documentRepresentation.resourcePermissions should equal(None)
        documentRepresentation.customValueUUID should equal(None)
        documentRepresentation.project should equal(SharedTestDataADM.anythingProject)
        documentRepresentation.customValueIri should equal(None)
        documentRepresentation.className should equal(FileModelUtil.getDefaultClassName(FileType.DocumentFile))
        documentRepresentation.comment should equal(None)
        documentRepresentation.customValueCreationDate should equal(None)
        documentRepresentation.label should equal("test label")
        documentRepresentation.ontologyName should equal("knora-api")
        documentRepresentation.originalFilename should equal(None)
        documentRepresentation.originalMimeType should equal(None)
        documentRepresentation.pageCount should equal(Some(1))
        documentRepresentation.resourceIri should not equal (None)
        documentRepresentation.shortcode should equal("0001")
        documentRepresentation.valuePermissions should equal(None)
      }

      "create a valid representation of a DocumentRepresentation with custom values" in {
        val internalFilename = "document-file.doc"
        val project = SharedTestDataADM.beolProject
        val resourceIri = stringFormatter.makeRandomResourceIri(project.shortcode)
        val comment = Some("This is a custom comment")
        val className = Some("biblio:ThingDocument")
        val ontologyName = "biblio"
        val dimX = Some(20)
        val dimY = Some(30)
        val pageCount = Some(550)
        val internalMimetype = Some("application/msword")
        val originalFilename = Some("document-file.docm")
        val originalMimetype = Some("application/vnd.ms-word.document.macroEnabled.12")
        val customValueIri = Some("http://www.knora.org/ontology/0801/biblio#hasThingDocumentValue".toSmartIri)
        val customValueUUID = Some(UUID.randomUUID())
        val customValueCreationDate = Some(Instant.now())
        val valuePermissions = Some("V knora-admin:UnknownUser,knora-admin:KnownUser|M knora-admin:ProjectMember")
        val label = "a custom label"
        val resourcePermissions = Some("V knora-admin:UnknownUser|M knora-admin:ProjectMember,knora-admin:KnownUser")

        val documentRepresentation = UploadFileRequest.make(
          fileType = FileType.DocumentFile,
          internalFilename = internalFilename,
          resourceIri = Some(resourceIri),
          comment = comment,
          className = className,
          ontologyName = ontologyName,
          shortcode = project.shortcode,
          dimX = dimX,
          dimY = dimY,
          pageCount = pageCount,
          internalMimeType = internalMimetype,
          originalFilename = originalFilename,
          originalMimeType = originalMimetype,
          customValueIri = customValueIri,
          customValueUUID = customValueUUID,
          customValueCreationDate = customValueCreationDate,
          valuePermissions = valuePermissions,
          label = label,
          resourcePermissions = resourcePermissions,
          project = Some(project)
        )
        documentRepresentation.fileType should equal(FileType.DocumentFile)
        documentRepresentation.internalFilename should equal(internalFilename)
        documentRepresentation.internalMimeType should equal(internalMimetype)
        documentRepresentation.dimX should equal(dimX)
        documentRepresentation.dimY should equal(dimY)
        documentRepresentation.resourcePermissions should equal(resourcePermissions)
        documentRepresentation.customValueUUID should equal(customValueUUID)
        documentRepresentation.project should equal(project)
        documentRepresentation.customValueIri should equal(customValueIri)
        documentRepresentation.className should equal(className.get)
        documentRepresentation.comment should equal(comment)
        documentRepresentation.customValueCreationDate should equal(customValueCreationDate)
        documentRepresentation.label should equal(label)
        documentRepresentation.ontologyName should equal(ontologyName)
        documentRepresentation.originalFilename should equal(originalFilename)
        documentRepresentation.originalMimeType should equal(originalMimetype)
        documentRepresentation.pageCount should equal(pageCount)
        documentRepresentation.resourceIri should equal(resourceIri)
        documentRepresentation.shortcode should equal(project.shortcode)
        documentRepresentation.valuePermissions should equal(valuePermissions)
      }
    }

    "generating a JSON-LD representation of a UploadFileRequest," should {
      "correctly serialize a DocumentRepresentation with default values" in {
        val internalFilename = "document-file.pdf"
        val documentRepresentation = UploadFileRequest.make(
          fileType = FileType.DocumentFile,
          internalFilename = internalFilename
        )
        val json = documentRepresentation.toJsonLd
        val expectedJson = """{
                             |  "@type" : "knora-api:DocumentRepresentation",
                             |  "knora-api:hasDocumentFileValue" : {
                             |    "@type" : "knora-api:DocumentFileValue",
                             |    "knora-api:fileValueHasFilename" : "document-file.pdf"
                             |  },
                             |  "knora-api:attachedToProject" : {
                             |    "@id" : "http://rdfh.ch/projects/0001"
                             |  },
                             |  "rdfs:label" : "test label",
                             |  "@context" : {
                             |    "rdf": "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
                             |    "knora-api": "http://api.knora.org/ontology/knora-api/v2#",
                             |    "rdfs": "http://www.w3.org/2000/01/rdf-schema#",
                             |    "xsd": "http://www.w3.org/2001/XMLSchema#"
                             |  }
                             |}""".stripMargin
        json should equal(expectedJson)
      }

    }
  }
}
