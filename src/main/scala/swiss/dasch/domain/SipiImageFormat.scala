/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

/**
 * Defines the output format of the image. Used with the `--format` option.
 *
 * https://sipi.io/running/#command-line-options
 */
sealed trait SipiImageFormat {
  def extension: String
  def toCliString: String                          = extension
  def additionalExtensions: List[String]           = List.empty
  def allExtensions: List[String]                  = additionalExtensions.appended(this.extension)
  def acceptsExtension(extension: String): Boolean = allExtensions.contains(extension.toLowerCase)
}

object SipiImageFormat {

  case object Jpx extends SipiImageFormat {
    override def extension: String                  = "jpx"
    override def additionalExtensions: List[String] = List("jp2")
  }

  case object Jpg extends SipiImageFormat {
    override def extension: String                  = "jpg"
    override def additionalExtensions: List[String] = List("jpeg")
  }

  case object Tif extends SipiImageFormat {
    override def extension: String                  = "tif"
    override def additionalExtensions: List[String] = List("tiff")
  }

  case object Png extends SipiImageFormat {
    override def extension: String = "png"
  }

  val all: List[SipiImageFormat]                                = List(Jpx, Jpg, Tif, Png)
  val allExtensions: List[String]                               = all.flatMap(_.allExtensions)
  def fromExtension(extension: String): Option[SipiImageFormat] = all.find(_.acceptsExtension(extension))
}

object SipiVideoFormat {
  val allExtensions: List[String] = List()
}

object SipiOtherFormat {
  val allExtensions: List[String] = List()
}
