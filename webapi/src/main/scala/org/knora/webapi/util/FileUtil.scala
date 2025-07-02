/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.util

import com.typesafe.scalalogging.Logger

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import scala.io.BufferedSource
import scala.io.Codec
import scala.io.Source
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import dsp.errors.FileWriteException
import dsp.errors.NotFoundException
import org.knora.webapi.config.AppConfig
import org.knora.webapi.messages.util.rdf.JsonLDDocument
import org.knora.webapi.messages.util.rdf.JsonLDUtil
import org.knora.webapi.messages.util.rdf.RdfModel

/**
 * Functions for reading and writing files.
 */
object FileUtil {

  /**
   * Writes a string to a file.
   *
   * @param file    the destination file.
   * @param content the string to write.
   */
  def writeTextFile(file: Path, content: String) =
    writeBinaryFile(file, content.getBytes(StandardCharsets.UTF_8))

  /**
   * Reads a text file into a string.
   *
   * @param file the source file.
   * @return the contents of the file.
   */
  def readTextFile(file: Path): String = {
    val source = Source.fromFile(file.toFile)(Codec.UTF8)
    try {
      source.mkString
    } finally {
      source.close()
    }
  }

  def readAsJsonLd(file: Path): JsonLDDocument = JsonLDUtil.parseJsonLD(readTextFile(file))
  def readAsRdfXml(file: Path): RdfModel       = RdfModel.fromRdfXml(readTextFile(file))
  def readAsTurtle(file: Path): RdfModel       = RdfModel.fromTurtle(readTextFile(file))

  /**
   * Reads a file from the classpath into a string.
   *
   * @param filename the name of the file.
   * @return the contents of the file.
   */
  def readTextResource(filename: String): String = {
    // https://alvinalexander.com/scala/scala-exception-handling-try-catch-finally#toc_0

    var sourceOption = None: Option[BufferedSource]

    try {
      val source: BufferedSource = Source.fromResource(filename)(Codec.UTF8)

      if (source.nonEmpty) {
        sourceOption = Some(source)
      }

      source.mkString
    } catch {
      case _: Exception =>
        throw NotFoundException(s"The requested file could not be read: $filename")
    } finally {
      if (sourceOption.nonEmpty) {
        sourceOption.get.close
      }

    }
  }

  /**
   * Writes a byte array to a file.
   *
   * @param file    the destination file.
   * @param content the binary data to write.
   */
  private def writeBinaryFile(file: Path, content: Array[Byte]) = Files.write(file, content)

  /**
   * Generates a byte array representing a Zip file containing the specified data. The Zip file data is
   * generated in memory only; no disk access is performed.
   *
   * @param contents a map of file names to byte arrays representing file contents.
   * @return a byte array containing the Zip file data.
   */
  def createZipFileBytes(contents: Map[String, Array[Byte]]): Array[Byte] = {
    val byteArrayOutputStream = new ByteArrayOutputStream()
    val zipOutputStream       = new ZipOutputStream(byteArrayOutputStream)

    val bytesTry = Try {
      contents.foreach { case (filename: String, content: Array[Byte]) =>
        val entry: ZipEntry = new ZipEntry(filename)
        zipOutputStream.putNextEntry(entry)
        zipOutputStream.write(content)
        zipOutputStream.closeEntry()
      }
    }

    zipOutputStream.close()

    bytesTry match {
      case Success(_)  => byteArrayOutputStream.toByteArray
      case Failure(ex) => throw ex
    }
  }

  /**
   * Creates an empty file in the default temporary-file directory specified in the application's configuration
   *
   * @param appConfig     the application's configuration
   * @param fileExtension the extension to be used for the temporary file name, if any,
   * @return the location where the file has been written to.
   */
  def createTempFile(fileExtension: Option[String] = None, appConfig: AppConfig): Path = {
    val tempDataDir = Paths.get(appConfig.tmpDatadir)

    // check if the location for writing temporary files exists
    if (!Files.exists(tempDataDir)) {
      throw FileWriteException(s"Data directory ${appConfig.tmpDatadir} does not exist on server")
    }

    val extension = fileExtension.getOrElse("bin")

    val file: Path = Files.createTempFile(tempDataDir, "tmp_", "." + extension)

    if (!Files.isWritable(file)) {
      throw FileWriteException(s"File $file cannot be written.")
    }

    file
  }

  /**
   * Deletes a temporary file.
   *
   * @param file the file to be deleted.
   * @param log      a logging adapter.
   * @return `true` if the file was deleted by this method.
   */
  def deleteFileFromTmpLocation(file: Path, log: Logger): Boolean = {

    if (!Files.isWritable(file)) {
      val ex = FileWriteException(s"File $file cannot be deleted.")
      log.error(ex.getMessage, ex)
    }

    Files.deleteIfExists(file)
  }
}
