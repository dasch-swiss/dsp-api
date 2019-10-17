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
        def urlses(cl: ClassLoader): Array[java.net.URL] = cl match {
            case null => Array()
            case u: java.net.URLClassLoader => u.getURLs ++ urlses(cl.getParent)
            case _ => urlses(cl.getParent)
        }
        val  urls = urlses(getClass.getClassLoader)
        println(urls.filterNot(_.toString.contains("ivy")).mkString("\n"))
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
