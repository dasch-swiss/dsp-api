package org.knora.webapi.messages.v2.responder.metadatamessages

import java.util.UUID

import org.apache.jena.graph.Graph
import org.knora.webapi.{CoreSpec, RdfMediaTypes}
import org.knora.webapi.exceptions.ForbiddenException
import org.knora.webapi.messages.util.RdfFormatUtil
import org.knora.webapi.sharedtestdata.SharedTestDataADM

class MetadataMessagesV2Spec extends CoreSpec() {
    private val graphDataContent: String =
        """
        @prefix dsp-repo: <http://ns.dasch.swiss/repository#> .
        @prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
        @prefix anything: <http://www.knora.org/ontology/0001/anything#> .

        <anything> dsp-repo:hasDescription "A project to test Knora functionalities" .
        <anything> dsp-repo:hasShortcode "0001" .
        """
    // Parse the request to a Jena Graph.
    private val requestGraph: Graph = RdfFormatUtil.parseToJenaGraph(
        rdfStr = graphDataContent,
        mediaType = RdfMediaTypes.`text/turtle`
    )
    "MetadataPutRequestV2" should {
        "return 'BadRequest' if the requesting user is not the project or a system admin" in {

            val caught = intercept[ForbiddenException](
                MetadataPutRequestV2(
                    graph = requestGraph,
                    projectADM = SharedTestDataADM.anythingProject,
                    requestingUser = SharedTestDataADM.anythingUser2,
                    apiRequestID = UUID.randomUUID()
                )
            )
            assert(caught.getMessage === "A new metadata for a project can only be created by a system or project admin.")
        }
    }
}
