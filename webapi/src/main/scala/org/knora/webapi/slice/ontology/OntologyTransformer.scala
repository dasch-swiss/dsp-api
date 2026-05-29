package org.knora.webapi.slice.ontology

import org.apache.jena.graph.Node
import org.apache.jena.graph.NodeFactory
import org.apache.jena.graph.Triple
import org.apache.jena.riot.Lang
import org.apache.jena.riot.RDFParser
import org.apache.jena.riot.system.StreamRDF
import org.apache.jena.riot.system.StreamRDFBase
import org.apache.jena.riot.system.StreamRDFLib
import org.apache.jena.sparql.core.Quad
import zio.IO
import zio.ZIO
import zio.ZLayer

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path

import org.knora.webapi.messages.StringFormatter

final case class TransformerError(message: String)

final class OntologyTransformer(sf: StringFormatter) { self =>

  def toKnoraBase(rdf: Path): IO[TransformerError, Path] =
    ZIO
      .attemptBlocking {
        val nq = Files.createTempFile("onto-transformer-", ".nq")
        val in = new BufferedInputStream(new FileInputStream(rdf.toFile))
        try {
          val os = new BufferedOutputStream(new FileOutputStream(nq.toFile))
          try {
            val writer = StreamRDFLib.writer(os)
            val sink   = rewritingSink(writer)
            sink.start()
            try RDFParser.source(in).lang(Lang.JSONLD).parse(sink)
            finally sink.finish()
          } finally os.close()
        } finally in.close()
        nq
      }
      .mapError(e => TransformerError(s"Failed to transform RDF: ${e.getMessage}"))

  private def rewritingSink(downstream: StreamRDF): StreamRDF = {
    def rewriteUri(uri: String): String =
      if (uri != null) sf.toSmartIri(uri).toInternalSchema.toString else uri

    def rewriteNode(n: Node): Node =
      if (n != null && n.isURI) NodeFactory.createURI(rewriteUri(n.getURI)) else n

    new StreamRDFBase {
      override def start(): Unit                             = downstream.start()
      override def finish(): Unit                            = downstream.finish()
      override def base(base: String): Unit                  = downstream.base(base)
      override def prefix(prefix: String, iri: String): Unit = downstream.prefix(prefix, rewriteUri(iri))
      override def triple(t: Triple): Unit                   =
        downstream.triple(
          Triple.create(rewriteNode(t.getSubject), rewriteNode(t.getPredicate), rewriteNode(t.getObject)),
        )
      override def quad(q: Quad): Unit =
        downstream.quad(
          Quad.create(
            rewriteNode(q.getGraph),
            rewriteNode(q.getSubject),
            rewriteNode(q.getPredicate),
            rewriteNode(q.getObject),
          ),
        )
    }
  }
}

object OntologyTransformer {
  val layer: ZLayer[StringFormatter, Nothing, OntologyTransformer] = ZLayer.derive[OntologyTransformer]
}
