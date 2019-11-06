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

import java.io.File
import java.nio.file.{Path, Paths}

import org.knora.webapi.ClientApiGenerationException
import org.knora.webapi.messages.v2.responder.ontologymessages.Cardinality.Cardinality
import org.knora.webapi.messages.v2.responder.ontologymessages.Cardinality._
import org.knora.webapi.util.clientapi.TypeScriptBackEnd.ImportInfo
import org.knora.webapi.util.{FileUtil, SmartIri, StringFormatter}

/**
  * Generates client API source code in TypeScript.
  */
class TypeScriptBackEnd extends GeneratorBackEnd {
    private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    // The directory where TypeScript compilation stubs are located.
    private val mockCodeDir: Path = Paths.get("_test_data/typescript-client-mock-src").toAbsolutePath

    /**
      * Represents information about an endpoint and its source code.
      *
      * @param className    the name of the endpoint class.
      * @param description  a description of the endpoint.
      * @param variableName a variable name that can be used for an instance of the endpoint class.
      * @param urlPath      the URL path of the endpoint, relative to the API path.
      * @param fileContent  the endpoint's source code.
      */
    private case class EndpointInfo(className: String,
                                    description: String,
                                    variableName: String,
                                    urlPath: String,
                                    fileContent: SourceCodeFileContent) {
        /**
          * Converts this [[EndpointInfo]] to an [[ImportInfo]] so the endpoint can be imported in another class.
          *
          * @param importedIn           the path of the class in which the endpoint is to be imported.
          * @param includeFileExtension if `true`, include the file extension in the import.
          * @return an [[ImportInfo]] referring to this endpoint.
          */
        def toImportInfo(importedIn: SourceCodeFilePath, includeFileExtension: Boolean = true): ImportInfo = {
            val importPath: String = importedIn.makeImportPath(thatSourceCodeFilePath = fileContent.filePath, includeFileExtension = includeFileExtension)

            ImportInfo(
                className = className,
                importPath = importPath,
                description = Some(description),
                variableName = Some(variableName),
                urlPath = Some(urlPath)
            )
        }
    }

    /**
      * Generates client API source code.
      *
      * @param apis the APIs from which source code is to be generated.
      * @return the generated source code.
      */
    override def generateClientSourceCode(apis: Set[ClientApiBackendInput], params: Map[String, String]): Set[SourceCodeFileContent] = {
        /**
          * Returns the files containing mock knora-api-js-lib code, used for testing the generated code.
          *
          * @param startDir       the directory to look for mock code in.
          * @param fileContentAcc the mock code collected so far.
          * @return the collected mock code.
          */
        def getMockFiles(startDir: File, fileContentAcc: Set[SourceCodeFileContent]): Set[SourceCodeFileContent] = {
            if (!startDir.exists) {
                throw ClientApiGenerationException(s"Directory $startDir does not exist")
            }

            if (!startDir.isDirectory) {
                throw ClientApiGenerationException(s"Path $startDir does not represent a directory")
            }

            val relativeStartPath: Seq[String] = mockCodeDir.relativize(startDir.toPath.toAbsolutePath).toString.split('/').toSeq.filterNot(_.isEmpty)
            val fileList: Seq[File] = startDir.listFiles.toSeq.filterNot(_.getName.startsWith("."))
            val files: Seq[File] = fileList.filterNot(_.isDirectory)
            val subdirectories: Seq[File] = fileList.filter(_.isDirectory)

            val fileContentInThisDir: Set[SourceCodeFileContent] = files.map {
                file: File =>
                    val filenameAndExtension: Array[String] = file.getName.split('.')
                    val filename = filenameAndExtension(0)

                    val fileExtension = if (filenameAndExtension.length == 2) {
                        filenameAndExtension(1)
                    } else {
                        ""
                    }

                    val sourceCodeFilePath = SourceCodeFilePath(
                        directoryPath = relativeStartPath,
                        filename = filename,
                        fileExtension = fileExtension
                    )

                    val text = FileUtil.readTextFile(file)

                    SourceCodeFileContent(
                        filePath = sourceCodeFilePath,
                        text = text
                    )
            }.toSet

            val accForRecursion = fileContentAcc ++ fileContentInThisDir

            subdirectories.foldLeft(accForRecursion) {
                case (acc, subdirectory) =>
                    getMockFiles(startDir = subdirectory, fileContentAcc = acc)
            }
        }

        val knoraApiConnectionSourceCode: SourceCodeFileContent = generateKnoraApiConnectionSourceCode(apis.map(_.apiDef))

        val mockCode: Set[SourceCodeFileContent] = if (params.contains("mock")) {
            getMockFiles(startDir = mockCodeDir.toFile, fileContentAcc = Set.empty)
        } else {
            Set.empty
        }

        apis.flatMap(api => generateApiSourceCode(api)) ++ mockCode + knoraApiConnectionSourceCode
    }

    /**
      * Generates TypeScript source code for an API.
      *
      * @param api the API for which source code is to be generated.
      * @return a set of [[SourceCodeFileContent]] objects containing the generated source code.
      */
    private def generateApiSourceCode(api: ClientApiBackendInput): Set[SourceCodeFileContent] = {
        // Generate file paths for class definitions.
        val clientClassCodePaths: Map[String, SourceCodeFilePath] = api.clientClassDefs.map {
            clientClassDef =>
                val filePath: SourceCodeFilePath = makeClassFilePath(apiDef = api.apiDef, className = clientClassDef.className)
                clientClassDef.className -> filePath
        }.toMap

        // Generate source code for class definitions.
        val classSourceCode: Set[SourceCodeFileContent] = generateClassSourceCode(
            apiDef = api.apiDef,
            clientClassDefs = api.clientClassDefs,
            clientClassCodePaths = clientClassCodePaths
        )

        // Generate source code for endpoints.
        val endpointInfos: Seq[EndpointInfo] = api.apiDef.endpoints.map {
            endpoint =>
                generateEndpointInfo(
                    apiDef = api.apiDef,
                    endpoint = endpoint,
                    clientClassDefs = api.clientClassDefs,
                    clientClassCodePaths = clientClassCodePaths
                )
        }

        // Generate source code for the main endpoint.
        val mainEndpointSourceCode = generateMainEndpointSourceCode(
            apiDef = api.apiDef,
            endpointInfos = endpointInfos
        )

        classSourceCode ++ endpointInfos.map(_.fileContent) + mainEndpointSourceCode
    }

    /**
      * Generates knora-api-connection.ts.
      *
      * @param apiDefs the API definitions to be used.
      * @return the source code of knora-api-connection.ts.
      */
    private def generateKnoraApiConnectionSourceCode(apiDefs: Set[ClientApi]): SourceCodeFileContent = {
        val knoraApiConnectionFilePath = SourceCodeFilePath(
            directoryPath = Seq.empty,
            filename = "knora-api-connection",
            fileExtension = "ts"
        )

        val importInfos: Set[ImportInfo] = apiDefs.map {
            apiDef =>
                val mainEndpointFilePath: SourceCodeFilePath = makeMainEndpointFilePath(apiDef)

                ImportInfo(
                    className = apiDef.name,
                    importPath = knoraApiConnectionFilePath.makeImportPath(mainEndpointFilePath, includeFileExtension = false),
                    description = Some(apiDef.description),
                    variableName = Some(makeVariableName(apiDef.name)),
                    urlPath = Some(apiDef.urlPath)
                )
        }

        // Generate the source code of knora-api-connection.ts.
        val text: String = clientapi.typescript.txt.generateKnoraApiConnection(
            apis = importInfos.toVector.sortBy(_.className)
        ).toString()

        SourceCodeFileContent(
            filePath = SourceCodeFilePath(
                directoryPath = Seq.empty,
                filename = "knora-api-connection",
                fileExtension = "ts"
            ),
            text = text
        )
    }

    /**
      * Generates source code for the main endpoint of an API.
      *
      * @param apiDef        the API definition.
      * @param endpointInfos information about the endpoints that belong to the API.
      * @return the source code for the main endpoint.
      */
    private def generateMainEndpointSourceCode(apiDef: ClientApi,
                                               endpointInfos: Seq[EndpointInfo]): SourceCodeFileContent = {
        // Generate the main endpoint's file path.
        val mainEndpointFilePath: SourceCodeFilePath = makeMainEndpointFilePath(apiDef)

        // Generate the main endpoint's source code.
        val text: String = clientapi.typescript.txt.generateTypeScriptMainEndpoint(
            name = apiDef.name,
            description = apiDef.description,
            endpoints = endpointInfos.map(_.toImportInfo(importedIn = mainEndpointFilePath, includeFileExtension = false))
        ).toString()

        SourceCodeFileContent(filePath = mainEndpointFilePath, text = text)
    }

    /**
      * Generates source code for an API endpoint.
      *
      * @param apiDef               the API definition.
      * @param endpoint             the endpoint definition.
      * @param clientClassDefs      the definitions of the classes used in the API.
      * @param clientClassCodePaths the file paths of generated class definitions.
      * @return the source code of the endpoint.
      */
    private def generateEndpointInfo(apiDef: ClientApi,
                                     endpoint: ClientEndpoint,
                                     clientClassDefs: Set[ClientClassDefinition],
                                     clientClassCodePaths: Map[String, SourceCodeFilePath]): EndpointInfo = {
        // Generate the endpoint's file path.
        val endpointFilePath: SourceCodeFilePath = makeEndpointFilePath(apiDef = apiDef, endpoint = endpoint)

        // Determine which classes need to be imported by the endpoint.
        val classDefsImported: Set[ClientClassDefinition] = clientClassDefs.filter {
            clientClassDef => endpoint.classIrisUsed.contains(clientClassDef.classIri)
        }

        // Make an ImportInfo for each imported class.
        val classInfos: Vector[ImportInfo] = classDefsImported.toVector.sortBy(_.classIri).map {
            clientClassDef =>
                ImportInfo(
                    className = clientClassDef.className,
                    description = clientClassDef.classDescription,
                    importPath = endpointFilePath.makeImportPath(clientClassCodePaths(clientClassDef.className), includeFileExtension = false)
                )
        }

        // Generate the source code of the endpoint.
        val text: String = clientapi.typescript.txt.generateTypeScriptEndpoint(
            name = endpoint.name,
            description = endpoint.description,
            importedClasses = classInfos,
            functions = endpoint.functions
        ).toString()

        val fileContent = SourceCodeFileContent(filePath = endpointFilePath, text = text)

        EndpointInfo(
            className = endpoint.name,
            description = endpoint.description,
            variableName = makeVariableName(endpoint.name),
            urlPath = endpoint.urlPath,
            fileContent = fileContent
        )
    }

    /**
      * Generates source code for classes.
      *
      * @param apiDef               the API definition.
      * @param clientClassDefs      the definitions of the classes for which source code is to be generated.
      * @param clientClassCodePaths the file paths to be used for the generated classes.
      * @return the generated source code.
      */
    private def generateClassSourceCode(apiDef: ClientApi,
                                        clientClassDefs: Set[ClientClassDefinition],
                                        clientClassCodePaths: Map[String, SourceCodeFilePath]): Set[SourceCodeFileContent] = {
        clientClassDefs.map {
            clientClassDef =>
                val classFilePath: SourceCodeFilePath = clientClassCodePaths(clientClassDef.className)

                val subClassOf: Option[ClassRef] = clientClassDef.subClassOf.map {
                    subClassOfIri: SmartIri => ClassRef(className = subClassOfIri.getEntityName.capitalize, classIri = subClassOfIri)
                }

                val propertiesNeedingCustomConverters: Vector[ClientPropertyDefinition] = clientClassDef.properties.filter {
                    propertyDef => propertyDef.objectType match {
                        case collectionType: CollectionType => true
                        case _ => false
                    }
                }

                val customConverterImports: Vector[ImportInfo] = propertiesNeedingCustomConverters.map {
                    propertyDef =>
                        val converterName: String = TypeScriptBackEnd.makeConverterName(propertyDef)

                        val converterPath: SourceCodeFilePath = makeCustomConverterFilePath(
                            apiDef = apiDef,
                            propertyDefinition = propertyDef,
                            converterClassName = converterName
                        )

                        val convertImportPath = classFilePath.makeImportPath(
                            thatSourceCodeFilePath = converterPath,
                            includeFileExtension = false
                        )

                        ImportInfo(
                            className = converterName,
                            importPath = convertImportPath
                        )
                }

                val importsWithBaseClass: Set[ClassRef] = clientClassDef.classObjectTypesUsed ++ subClassOf

                val importInfos: Vector[ImportInfo] = importsWithBaseClass.toVector.sortBy(_.classIri).map {
                    classRef =>
                        val classImportPath: String = classFilePath.makeImportPath(
                            thatSourceCodeFilePath = clientClassCodePaths(classRef.className),
                            includeFileExtension = false
                        )

                        ImportInfo(
                            className = classRef.className,
                            importPath = classImportPath
                        )
                } ++ customConverterImports

                val classText: String = clientapi.typescript.txt.generateTypeScriptClass(
                    classDef = clientClassDef,
                    subClassOf = subClassOf,
                    importedClasses = importInfos
                ).toString()

                SourceCodeFileContent(filePath = classFilePath, text = classText)
        }
    }

    /**
      * Generates the file path of an API's main endpoint.
      *
      * @param apiDef the API definition.
      * @return the file path of the API's main endpoint.
      */
    private def makeMainEndpointFilePath(apiDef: ClientApi): SourceCodeFilePath = {
        val apiLocalName: String = stringFormatter.camelCaseToSeparatedLowerCase(apiDef.name)

        SourceCodeFilePath(
            directoryPath = Seq("api", apiDef.directoryName),
            filename = s"$apiLocalName",
            fileExtension = "ts"
        )
    }

    /**
      * Generates the file path of an endpoint.
      *
      * @param apiDef   the API definition.
      * @param endpoint the definition of the endpoint.
      * @return the file path of the endpoint.
      */
    private def makeEndpointFilePath(apiDef: ClientApi, endpoint: ClientEndpoint): SourceCodeFilePath = {
        val endpointLocalName: String = stringFormatter.camelCaseToSeparatedLowerCase(endpoint.name)

        SourceCodeFilePath(
            directoryPath = Seq("api", apiDef.directoryName, endpoint.directoryName),
            filename = endpointLocalName,
            fileExtension = "ts"
        )
    }

    /**
      * Generates the file path of a class.
      *
      * @param apiDef    the API definition.
      * @param className the name of the class.
      * @return the file path of the generated class.
      */
    private def makeClassFilePath(apiDef: ClientApi, className: String): SourceCodeFilePath = {
        val classLocalName: String = stringFormatter.camelCaseToSeparatedLowerCase(className)

        SourceCodeFilePath(
            directoryPath = Seq("models", apiDef.directoryName),
            filename = classLocalName,
            fileExtension = "ts"
        )
    }

    /**
      * Generates the file path of a custom converter that is expected to exist.
      *
      * @param apiDef             the API definition.
      * @param propertyDefinition the definition of the property that needs a custom converter.
      * @return the file path of the custom converter.
      */
    private def makeCustomConverterFilePath(apiDef: ClientApi,
                                            propertyDefinition: ClientPropertyDefinition,
                                            converterClassName: String): SourceCodeFilePath = {
        val converterLocalName: String = stringFormatter.camelCaseToSeparatedLowerCase(converterClassName)

        SourceCodeFilePath(
            directoryPath = Seq("models", apiDef.directoryName, "custom-converters"),
            filename = converterLocalName,
            fileExtension = "ts"
        )
    }

    /**
      * Generates a variable name that can be used for an instance of a class.
      *
      * @param className the name of the class.
      * @return a variable name that can be used for an instance of the class.
      */
    private def makeVariableName(className: String): String = {
        className.substring(0, 1).toLowerCase + className.substring(1)
    }
}

/**
  * Classes and functions used by Twirl templates.
  */
object TypeScriptBackEnd {

    /**
      * Represents information about an imported class or interface.
      *
      * @param className    the name of the class.
      * @param importPath   the file path to be used for importing the class.
      * @param description  a description of the class.
      * @param variableName a variable name that can be used for an instance of the class.
      * @param urlPath      if this class represents an endpoint, the URL path of the endpoint.
      */
    case class ImportInfo(className: String,
                          importPath: String,
                          description: Option[String] = None,
                          variableName: Option[String] = None,
                          urlPath: Option[String] = None)

    /**
      * Generates the name of a custom converter for a property whose object type is a collection.
      *
      * @param propertyDefinition the property definition.
      * @return the name of the custom converter.
      */
    def makeConverterName(propertyDefinition: ClientPropertyDefinition): String = {
        s"${propertyDefinition.propertyName.capitalize}Converter"
    }

    /**
      * Generates the type name used for a property's object type in json2typescript's `@JsonProperty` annotation.
      *
      * @param propertyDefinition the property definition.
      * @return the type name.
      */
    def makeJson2TypeScriptType(propertyDefinition: ClientPropertyDefinition): String = {
        propertyDefinition.objectType match {
            case _: CollectionType =>
                // json2typescript can't handle arbitrary collection signatures, so we assume there's a custom
                // converter for each collection type.
                makeConverterName(propertyDefinition)

            case other =>
                val propertyObjectType: String = makePropertyObjectType(other)

                // Capitalise TypeScript datatype names. (Class references are already capitalised.)
                val capitalisedType = propertyDefinition.objectType match {
                    case _: ClientDatatype => propertyObjectType.capitalize
                    case _ => propertyObjectType
                }

                // If the property can have multiple values, wrap the type in brackets.
                if (propertyDefinition.cardinality == MayHaveMany || propertyDefinition.cardinality == MustHaveSome) {
                    s"[$capitalisedType]"
                } else {
                    capitalisedType
                }
        }
    }

    /**
      * Generates a type annotation for a property object type.
      *
      * @param objectType the object type.
      * @return the corresponding type annotation.
      */
    def makePropertyObjectType(objectType: ClientObjectType): String = {
        objectType match {
            case StringDatatype => "string"
            case BooleanDatatype => "boolean"
            case IntegerDatatype => "number"
            case DecimalDatatype => "number"
            case UriDatatype => "string"
            case DateTimeStampDatatype => "string"
            case classRef: ClassRef => classRef.className
            case arrayType: ArrayType => s"${makePropertyObjectType(arrayType.elementType)}[]"
            case mapType: MapType => s"{ [key: ${makePropertyObjectType(mapType.keyType)}]: ${makePropertyObjectType(mapType.valueType)} }"
            case _ => throw ClientApiGenerationException(s"Type $objectType not yet supported")
        }
    }

    /**
      * Generates the default value for a property object type.
      *
      * @param objectType  the object type.
      * @param cardinality the property's cardinality.
      * @return the default value.
      */
    def makeDefaultValue(objectType: ClientObjectType, cardinality: Cardinality): String = {
        cardinality match {
            case MayHaveMany | MustHaveSome => "[]"

            case MayHaveOne => "undefined"

            case MustHaveOne =>
                objectType match {
                    case StringDatatype => "\"\""
                    case BooleanDatatype => "false"
                    case IntegerDatatype => "0"
                    case DecimalDatatype => "0"
                    case UriDatatype => "\"\""
                    case DateTimeStampDatatype => "\"\""
                    case classRef: ClassRef => s"new ${classRef.className}()"
                    case _: ArrayType => "[]"
                    case _: MapType => "{}"
                    case _ => throw ClientApiGenerationException(s"Type $objectType not supported in this template")
                }
        }
    }

    /**
      * Adds `[]` to the end of a type if the cardinality allows it to be an array.
      *
      * @param typeScriptType a TypeScript type.
      * @param cardinality    the property's cardinality.
      * @return the type, followed by `[]` if necessary.
      */
    def addBracketsForCardinality(typeScriptType: String, cardinality: Cardinality): String = {
        if (cardinality == MayHaveMany || cardinality == MustHaveSome) {
            s"$typeScriptType[]"
        } else {
            typeScriptType
        }
    }

    /**
      * Returns `?` if a cardinality represents an optional value.
      *
      * @param cardinality the cardinality on the property.
      * @return `?` if the cardinality represents an optional value, otherwise the empty string.
      */
    def handleOption(cardinality: Cardinality): String = {
        if (cardinality == MayHaveOne) {
            "?"
        } else {
            ""
        }
    }
}
