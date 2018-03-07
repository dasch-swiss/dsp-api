package org.knora.webapi.util

import scala.io.Source
import java.net.{URL, URLConnection}

import org.knora.webapi.AssertionException
import org.knora.webapi.twirl.Contributor
import spray.json._
import java.io.File

object GenerateContributorsFile extends App {



    // Configuration

    val ContributorsUrl = "https://api.github.com/repos/dhlab-basel/Knora/contributors"
    val GitHubApiToken = "3b18dcf6858e9c1e1bb8038ad87e43aad511386d"

    val Headers: Map[String, String] = Map(
        "Authorization" -> s"token $GitHubApiToken"
    )

    // Get the list of contributors.

    val contributorsJson = getFromGitHubApi(ContributorsUrl)

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

    val contributorsWithNames: Seq[Contributor] = contributors.map {
        contributor =>
            val userJson = getFromGitHubApi(contributor.apiUrl)

            contributor.copy(
                name = Some(userJson.asJsObject.fields("name").asInstanceOf[JsString].value)
            )
    }

    // Sort them by number of contributions.
    val contributorsSorted = contributorsWithNames.sortBy(_.contributions).reverse

    // Generate Markdown.
    val contributorsText: String = queries.util.txt.generateContributorsMarkdown(contributorsSorted).toString

    // Write Contributors.md.
    FileUtil.writeTextFile(new File("../Contributors.md"), contributorsText)

    /**
      * Makes an HTTP GET connection to the GitHub API.
      *
      * @param url a GitHub API URL.
      * @return the response, parsed as JSON.
      */
    private def getFromGitHubApi(url: String): JsValue = {
        val connection: URLConnection = new URL(url).openConnection

        Headers.foreach({
            case (name, value) => connection.setRequestProperty(name, value)
        })

        val responseStr: String = Source.fromInputStream(connection.getInputStream).mkString

        // Parse the JSON response.

        JsonParser(responseStr)
    }
}
