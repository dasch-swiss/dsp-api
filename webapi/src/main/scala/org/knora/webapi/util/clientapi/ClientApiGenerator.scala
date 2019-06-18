package org.knora.webapi.util.clientapi

import java.io.File
import java.nio.file.Paths

import org.knora.webapi.ClientApiGenerationException
import org.knora.webapi.routing.admin.AdminClientApi
import org.knora.webapi.util.{FileUtil, StringFormatter}
import org.rogach.scallop.{ScallopConf, ScallopOption}

/**
  * A command-line program for generating client API class definitions based on RDF class definitions served by Knora.
  */
object ClientApiGenerator {
    private val TYPESCRIPT: String = "typescript"

    def main(args: Array[String]) {
        // Get the command-line arguments.
        val conf = new GenerateClientClassConf(args)

        // Initialise StringFormatter.
        StringFormatter.initForClient(knoraHostAndPort = s"${conf.host()}:${conf.port()}")
        implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

        // Get the API definitions.
        val apiDefs: Set[ClientApi] = getApiDefs

        // Construct the generator front end.
        val generatorFrontEnd = new GeneratorFrontEnd(
            useHttps = conf.useHttps(),
            host = conf.host(),
            port = conf.port()
        )

        // Construct the generator back end for the specified target.
        val generatorBackEnd: GeneratorBackEnd = getBackEnd(conf.target())

        // Get the class definitions from the front end.
        val backEndInputs: Set[ClientApiBackendInput] = apiDefs.map {
            apiDef => ClientApiBackendInput(apiDef, generatorFrontEnd.getClientClassDefs(apiDef))
        }

        // Generate source code.
        val sourceCode: Set[ClientSourceCodeFileContent] = generatorBackEnd.generateClientSourceCode(backEndInputs)

        // Save the source code to files.
        for (sourceCodeFileContent <- sourceCode) {
            val outputFile: File = Paths.get(conf.outputDir(), sourceCodeFileContent.filename).toFile
            FileUtil.writeTextFile(file = outputFile, content = sourceCodeFileContent.text)
            println(s"Wrote ${outputFile.getAbsolutePath}")
        }
    }

    /**
      * Constructs a set of all [[ClientApi]] instances.
      */
    private def getApiDefs: Set[ClientApi] = {
        Set(new AdminClientApi)
    }

    /**
      * Returns the [[GeneratorBackEnd]] with the specified name.
      *
      * @param name the name of the code generator back end.
      * @return the corresponding [[GeneratorBackEnd]].
      */
    private def getBackEnd(name: String): GeneratorBackEnd = {
        name match {
            case TYPESCRIPT => new TypeScriptBackEnd
            case _ => throw ClientApiGenerationException(s"Unknown target: $name")
        }
    }
}

/**
  * Parses command-line arguments.
  */
class GenerateClientClassConf(arguments: Seq[String]) extends ScallopConf(arguments) {
    val host: ScallopOption[String] = opt[String](default = Some("0.0.0.0"))
    val port: ScallopOption[Int] = opt[Int](default = Some(3333))
    val useHttps: ScallopOption[Boolean] = opt[Boolean]()
    val target: ScallopOption[String] = opt[String](required = true)
    val outputDir: ScallopOption[String] = opt[String](default = Some("."))
    verify()
}
