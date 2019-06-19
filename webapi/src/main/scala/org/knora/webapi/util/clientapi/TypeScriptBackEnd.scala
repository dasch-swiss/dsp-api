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

import org.apache.commons.lang3.StringUtils
import org.knora.webapi.util.StringFormatter
import org.knora.webapi.util.clientapi.TypeScriptBackEnd.ImportInfo

/**
  * Generates client API source code in TypeScript.
  */
class TypeScriptBackEnd extends GeneratorBackEnd {
    private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    /**
      * Represents information about an endpoint and its source code.
      *
      * @param className    the name of the endpoint class.
      * @param variableName a variable name that can be used for an instance of the endpoint class.
      * @param importPath   the file path to be used for importing the endpoint in the main endpoint.
      * @param urlPath      the URL path of the endpoint, relative to the API path.
      * @param fileContent  the endpoint's source code.
      */
    private case class EndpointInfo(className: String,
                                    variableName: String,
                                    importPath: String,
                                    urlPath: String,
                                    fileContent: ClientSourceCodeFileContent) {
        def toImportInfo: ImportInfo = ImportInfo(
            className = className,
            importPath = importPath,
            variableName = Some(variableName),
            urlPath = Some(urlPath)
        )
    }


    /**
      * Generates client API source code.
      *
      * @param apis the APIs from which source code is to be generated.
      * @return the generated source code.
      */
    def generateClientSourceCode(apis: Set[ClientApiBackendInput]): Set[ClientSourceCodeFileContent] = {
        apis.flatMap(api => generateApiSourceCode(api))
    }

    private def generateApiSourceCode(api: ClientApiBackendInput): Set[ClientSourceCodeFileContent] = {
        val clientClassCodePaths: Map[String, String] = api.clientClassDefs.map {
            clientClassDef =>
                val filePath = makeClassFilePath(apiName = api.apiDef.name, className = clientClassDef.className)
                clientClassDef.className -> filePath
        }.toMap

        val clientInterfaceCodePaths: Map[String, String] = api.clientClassDefs.map {
            clientClassDef =>
                val filePath = makeInterfaceFilePath(apiName = api.apiDef.name, className = clientClassDef.className)
                clientClassDef.className -> filePath
        }.toMap

        val classSourceCode: Set[ClientSourceCodeFileContent] = generateClassSourceCode(
            clientClassDefs = api.clientClassDefs,
            clientClassCodePaths = clientClassCodePaths,
            clientInterfaceCodePaths = clientInterfaceCodePaths
        )

        val interfaceSourceCode: Set[ClientSourceCodeFileContent] = generateInterfaceSourceCode(
            clientClassDefs = api.clientClassDefs,
            clientInterfaceCodePaths = clientInterfaceCodePaths
        )

        val endpointInfos: Set[EndpointInfo] = api.apiDef.endpoints.map {
            endpoint =>
                generateEndpointInfo(
                    apiDef = api.apiDef,
                    endpoint = endpoint,
                    clientClassDefs = api.clientClassDefs,
                    clientClassCodePaths = clientClassCodePaths
                )
        }

        val mainEndpointSourceCode = generateMainEndpointSourceCode(
            apiDef = api.apiDef,
            endpointInfos = endpointInfos
        )

        classSourceCode ++ interfaceSourceCode ++ endpointInfos.map(_.fileContent) + mainEndpointSourceCode
    }


    private def generateMainEndpointSourceCode(apiDef: ClientApi,
                                               endpointInfos: Set[EndpointInfo]): ClientSourceCodeFileContent = {
        val mainEndpointFilePath = makeMainEndpointFilePath(apiDef.name)

        val text: String = clientapi.txt.generateTypeScriptMainEndpoint(
            name = apiDef.name,
            description = apiDef.description,
            endpoints = endpointInfos.map(_.toImportInfo)
        ).toString()

        ClientSourceCodeFileContent(filePath = mainEndpointFilePath, text = text)
    }

    private def generateEndpointInfo(apiDef: ClientApi,
                                     endpoint: ClientEndpoint,
                                     clientClassDefs: Set[ClientClassDefinition],
                                     clientClassCodePaths: Map[String, String]): EndpointInfo = {
        val endpointFilePath = makeEndpointFilePath(apiName = apiDef.name, endpoint = endpoint)

        val classDefsImported: Set[ClientClassDefinition] = clientClassDefs.filter {
            clientClassDef => endpoint.classIrisUsed.contains(clientClassDef.classIri)
        }

        val classInfos: Set[ImportInfo] = classDefsImported.map {
            clientClassDef =>
                ImportInfo(
                    className = clientClassDef.className,
                    importPath = s"../../../${stripExtension(clientClassCodePaths(clientClassDef.className))}"
                )
        }

        val text: String = clientapi.txt.generateTypeScriptEndpoint(
            name = endpoint.name,
            description = endpoint.description,
            importedClasses = classInfos,
            functions = endpoint.functions
        ).toString()

        val fileContent = ClientSourceCodeFileContent(filePath = endpointFilePath, text = text)

        EndpointInfo(
            className = endpoint.name,
            variableName = makeVariableName(endpoint.name),
            urlPath = endpoint.urlPath,
            importPath = s"./${stripLeadingDirs(stripExtension(endpointFilePath), 2)}",
            fileContent = fileContent
        )
    }

    private def generateClassSourceCode(clientClassDefs: Set[ClientClassDefinition],
                                        clientClassCodePaths: Map[String, String],
                                        clientInterfaceCodePaths: Map[String, String]): Set[ClientSourceCodeFileContent] = {
        clientClassDefs.map {
            clientClassDef =>
                val classFilePath = clientClassCodePaths(clientClassDef.className)
                val interfacePathInClass = s"../../${stripExtension(clientInterfaceCodePaths(clientClassDef.className))}"

                val classText: String = clientapi.txt.generateTypeScriptClass(
                    classDef = clientClassDef,
                    interfacePathInClass = interfacePathInClass
                ).toString()

                ClientSourceCodeFileContent(filePath = classFilePath, text = classText)
        }
    }

    private def generateInterfaceSourceCode(clientClassDefs: Set[ClientClassDefinition],
                                            clientInterfaceCodePaths: Map[String, String]): Set[ClientSourceCodeFileContent] = {
        clientClassDefs.map {
            clientClassDef =>
                val interfaceFilePath = clientInterfaceCodePaths(clientClassDef.className)

                val importedInterfaces = clientClassDef.classObjectTypesUsed.map {
                    classRef =>
                        ImportInfo(
                            className = classRef.className,
                            importPath = clientInterfaceCodePaths(classRef.className)
                        )
                }

                val interfaceText: String = clientapi.txt.generateTypeScriptInterface(
                    classDef = clientClassDef,
                    importedInterfaces = importedInterfaces
                ).toString()

                ClientSourceCodeFileContent(filePath = interfaceFilePath, text = interfaceText)
        }
    }

    private def makeMainEndpointFilePath(apiName: String): String = {
        val apiLocalName = stringFormatter.camelCaseToSeparatedLowerCase(apiName)
        s"api/$apiLocalName/$apiLocalName-endpoint.ts"
    }

    private def makeEndpointFilePath(apiName: String, endpoint: ClientEndpoint): String = {
        val apiLocalName = stringFormatter.camelCaseToSeparatedLowerCase(apiName)
        val endpointLocalName = stringFormatter.camelCaseToSeparatedLowerCase(endpoint.name)
        s"api/$apiLocalName/$endpointLocalName/$endpointLocalName.ts"
    }

    private def makeClassFilePath(apiName: String, className: String): String = {
        val apiLocalName = stringFormatter.camelCaseToSeparatedLowerCase(apiName)
        val classLocalName = stringFormatter.camelCaseToSeparatedLowerCase(className)
        s"models/$apiLocalName/$classLocalName.ts"
    }

    private def makeInterfaceFilePath(apiName: String, className: String): String = {
        val apiLocalName = stringFormatter.camelCaseToSeparatedLowerCase(apiName)
        val classLocalName = stringFormatter.camelCaseToSeparatedLowerCase(className)
        s"interfaces/models/$apiLocalName/i-$classLocalName.ts"
    }

    private def stripLeadingDirs(filePath: String, numberToStrip: Int) = {
        filePath.substring(StringUtils.ordinalIndexOf(filePath, "/", numberToStrip) + 1)
    }

    private def stripExtension(filePath: String): String = {
        filePath.take(filePath.lastIndexOf("."))
    }

    private def makeVariableName(className: String): String = {
        className.substring(0, 1).toLowerCase + className.substring(1)
    }
}

/**
  * Classes used by Twirl templates.
  */
object TypeScriptBackEnd {

    /**
      * Represents information about an imported class or interface.
      *
      * @param className    the name of the class.
      * @param importPath   the file path to be used for importing the class.
      * @param variableName a variable name that can be used for an instance of the class.
      * @param urlPath      if this class represents an endpoint, the URL path of the endpoint.
      */
    case class ImportInfo(className: String,
                          importPath: String,
                          variableName: Option[String] = None,
                          urlPath: Option[String] = None)

}
