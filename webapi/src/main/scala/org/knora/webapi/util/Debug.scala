/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.util

/**
 * Contains methods useful for debugging
 */
object Debug {

  /**
   * prints out the classpath
   */
  def printClasspath(): Unit = {
    // debug classpath
    def urls(cl: ClassLoader): Array[java.net.URL] = Option(cl) match {
      case None                             => Array()
      case Some(u: java.net.URLClassLoader) => u.getURLs ++ urls(cl.getParent)
      case Some(_)                          => urls(cl.getParent)
    }

    val res = urls(getClass.getClassLoader)
    println(res.filterNot(_.toString.contains("ivy")).mkString("\n"))
  }

  /**
   * Prints out the file paths for the resources
   *
   * @param resources a sequence of resource.
   */
  def printResources(resources: Seq[String]): Unit = {
    println(s"printing resources: $resources")
    resources.foreach { res =>
      val url = getClass.getClassLoader.getResource(res)
      println(url)
    }
  }

}
