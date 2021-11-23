package org.knora.webapi.models.filemodels

import org.knora.webapi.CoreSpec
import org.knora.webapi.sharedtestdata.SharedTestDataADM

class FileModelsSpec extends CoreSpec {
  "FileModelsUtil" when {

    "creating a JSON-LD context" should {

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
  "FileModels" when {
    "creating an UploadFileRequest" should {
      "create a valid representation of a DocumentRepresentation" in {
        val documentRepresentation = UploadFileRequest.make(
          fileType = FileType.DocumentFile,
          internalFilename = "document-file.pdf"
        )
        documentRepresentation.fileType should equal(FileType.DocumentFile)
        documentRepresentation.internalFilename should equal("document-file.pdf")
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
    }
  }
}
