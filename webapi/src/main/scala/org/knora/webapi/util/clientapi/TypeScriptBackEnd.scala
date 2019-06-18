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

/**
  * Generates client API source code in TypeScript.
  */
class TypeScriptBackEnd extends GeneratorBackEnd {
    private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

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
        val clientSourceCodePaths: Map[String, String] = api.clientClassDefs.map {
            clientClassDef =>
                val filePath = makeClassFilePath(apiName = api.apiDef.name, className = clientClassDef.className)
                clientClassDef.className -> filePath
        }.toMap

        val classSourceCode: Set[ClientSourceCodeFileContent] = generateClassSourceCode(
            clientClassDefs = api.clientClassDefs,
            clientSourceCodePaths = clientSourceCodePaths
        )

        val endpointSourceCode: Set[ClientSourceCodeFileContent] = api.apiDef.endpoints.map {
            endpoint =>
                generateEndpointSourceCode(
                    apiDef = api.apiDef,
                    endpoint = endpoint,
                    clientClassDefs = api.clientClassDefs,
                    clientSourceCodePaths = clientSourceCodePaths
                )
        }

        val mainEndpointSourceCode = generateMainEndpointSourceCode(
            apiDef = api.apiDef,
            endpointSourceCode = endpointSourceCode
        )

        classSourceCode ++ endpointSourceCode + mainEndpointSourceCode
    }


    private def generateMainEndpointSourceCode(apiDef: ClientApi,
                                               endpointSourceCode: Set[ClientSourceCodeFileContent]): ClientSourceCodeFileContent = {
        val mainEndpointFilePath = makeMainEndpointFilePath(apiDef.name)

        val endpointImportFilePaths: Set[String] = endpointSourceCode.map {
            endpointFileContent => s"./${stripLeadingDirs(stripExtension(endpointFileContent.filePath), 2)}"
        }

        // TODO: run template
        ClientSourceCodeFileContent(filePath = mainEndpointFilePath, text = "")
    }

    private def generateEndpointSourceCode(apiDef: ClientApi,
                                           endpoint: ClientEndpoint,
                                           clientClassDefs: Set[ClientClassDefinition],
                                           clientSourceCodePaths: Map[String, String]): ClientSourceCodeFileContent = {
        val endpointFilePath = makeEndpointFilePath(apiName = apiDef.name, endpoint = endpoint)

        val classDefsImported: Set[ClientClassDefinition] = clientClassDefs.filter {
            clientClassDef => endpoint.classIrisUsed.contains(clientClassDef.classIri)
        }

        val classDefImportPaths: Set[String] = classDefsImported.map {
            clientClassDef => s"../../../${stripExtension(clientSourceCodePaths(clientClassDef.className))})"
        }

        // TODO: run template
        ClientSourceCodeFileContent(filePath = endpointFilePath, text = "")
    }

    private def generateClassSourceCode(clientClassDefs: Set[ClientClassDefinition],
                                        clientSourceCodePaths: Map[String, String]): Set[ClientSourceCodeFileContent] = {
        clientClassDefs.map {
            clientClassDef =>
                val filePath = clientSourceCodePaths(clientClassDef.className)
                val text: String = clientapi.txt.generateTypeScriptClass(clientClassDef).toString()
                ClientSourceCodeFileContent(filePath = filePath, text = text)
        }
    }

    private def makeMainEndpointFilePath(apiName: String): String = {
        val apiLocalName = stringFormatter.camelCaseToSeparatedLowerCase(apiName)
        s"api/$apiLocalName/$apiLocalName-endpoint.ts"
    }

    private def makeEndpointFilePath(apiName: String, endpoint: ClientEndpoint): String = {
        val apiLocalName = stringFormatter.camelCaseToSeparatedLowerCase(apiName)
        val endpointLocalName = stringFormatter.camelCaseToSeparatedLowerCase(endpoint.name)
        s"api/$apiLocalName/$endpointLocalName/$endpointLocalName-endpoint.ts"
    }

    private def makeInterfaceFilePath(apiName: String, className: String): String = {
        val apiLocalName = stringFormatter.camelCaseToSeparatedLowerCase(apiName)
        val classLocalName = stringFormatter.camelCaseToSeparatedLowerCase(className)
        s"interfaces/models/$apiLocalName/i-$classLocalName.ts"
    }

    private def makeClassFilePath(apiName: String, className: String): String = {
        val apiLocalName = stringFormatter.camelCaseToSeparatedLowerCase(apiName)
        val classLocalName = stringFormatter.camelCaseToSeparatedLowerCase(className)
        s"models/$apiLocalName/$classLocalName.ts"
    }

    private def stripLeadingDirs(filePath: String, numberToStrip: Int) = {
        filePath.substring(StringUtils.ordinalIndexOf(filePath, "/", numberToStrip) + 1)
    }

    private def stripExtension(filePath: String): String = {
        filePath.take(filePath.lastIndexOf("."))
    }
}
