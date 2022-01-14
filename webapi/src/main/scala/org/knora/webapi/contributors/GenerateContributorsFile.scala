/*
 * Copyright © 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.contributors

import java.net.{URL, URLConnection}
import java.nio.file.{Path, Paths}

import org.knora.webapi.exceptions.AssertionException
import org.knora.webapi.messages.twirl.Contributor
import org.knora.webapi.util.FileUtil
import org.rogach.scallop.{ScallopConf, ScallopOption}
import spray.json.{JsArray, JsNull, JsNumber, JsObject, JsString, JsValue, JsonParser}

import scala.io.Source

/**
 * Generates the file Contributors.md, using the GitHub API.
 */
object GenerateContributorsFile extends App {

  // Configuration

  val contributorsUrl = "https://api.github.com/repos/dasch-swiss/knora-api/contributors"
  val defaultOutputFile = "Contributors.md"

  // Command-line args

  private val conf = new GenerateContributorsFileConf(args.toIndexedSeq)
  private val token = conf.token.toOption
  private val outputFile: Path = Paths.get(conf.output())

  // Get the list of contributors.

  val contributorsJson = getFromGitHubApi(contributorsUrl)

  val contributors: Vector[Contributor] = contributorsJson.asInstanceOf[JsArray].elements.map {
    case elem: JsObject =>
      Contributor(
        login = elem.fields("login").asInstanceOf[JsString].value,
        apiUrl = elem.fields("url").asInstanceOf[JsString].value,
        htmlUrl = elem.fields("html_url").asInstanceOf[JsString].value,
        contributions = elem.fields("contributions").asInstanceOf[JsNumber].value.toInt
      )

    case other => throw AssertionException(s"Expected JsObject, got $other")
  }

  // Get the names of the contributors.

  val contributorsWithNames: Seq[Contributor] = contributors.map { contributor =>
    val userJson = getFromGitHubApi(contributor.apiUrl)

    val userName = userJson.asJsObject.fields("name") match {
      case JsString(value) => Some(value)
      case JsNull          => None
      case other           => throw AssertionException(s"Unexpected JSON type $other")
    }

    contributor.copy(
      name = userName
    )
  }

  // Sort them by number of contributions.
  val contributorsSorted = contributorsWithNames.sortBy(_.contributions).reverse

  // Generate Markdown.
  val contributorsText: String =
    org.knora.webapi.messages.twirl.queries.util.txt.generateContributorsMarkdown(contributorsSorted).toString

  // Write Contributors.md.
  FileUtil.writeTextFile(file = outputFile, content = contributorsText)

  /**
   * Makes an HTTP GET connection to the GitHub API.
   *
   * @param url a GitHub API URL.
   * @return the response, parsed as JSON.
   */
  private def getFromGitHubApi(url: String): JsValue = {
    val connection: URLConnection = new URL(url).openConnection

    token match {
      case Some(tokenStr) =>
        connection.setRequestProperty("Authorization", s"token $tokenStr")

      case None => ()
    }

    val responseStr: String = Source.fromInputStream(connection.getInputStream).mkString

    // Parse the JSON response.

    JsonParser(responseStr)
  }

  /**
   * Parses command-line arguments.
   */
  private class GenerateContributorsFileConf(arguments: Seq[String]) extends ScallopConf(arguments) {
    banner(s"""
              |Generates a file listing the contributors to Knora.
              |
              |Usage: org.knora.webapi.util.GenerateContributorsFile [ -t TOKEN ] [ -o OUTPUT ]
            """.stripMargin)

    val token: ScallopOption[String] = opt[String](descr = "GitHub API token")
    val output: ScallopOption[String] =
      opt[String](descr = s"Output Turtle file (defaults to $defaultOutputFile)", default = Some(defaultOutputFile))
    verify()
  }

}
