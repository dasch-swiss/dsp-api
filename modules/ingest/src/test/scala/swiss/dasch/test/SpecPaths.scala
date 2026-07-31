/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.test

import zio.nio.file.Path

import java.io.FileNotFoundException
import java.nio.file.FileSystems
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import java.util.Collections

/**
 * Classpath test fixtures, exposed as real filesystem paths. Under sbt these resources are loose
 * files/directories on the classpath already, so `getResource` resolves to a real `file:` path.
 * Under Bazel, test resources are packaged into a resource jar, so the resource lives at a `jar:`
 * URI - not a real `java.io.File`, which every consumer of these paths needs
 * (`FileUtils.copyDirectory`, `Body.fromFile`, `Path#toFile` are all default-filesystem-only).
 * Materialize to a real temp file/dir once per JVM in that case; return the loose path directly
 * otherwise.
 */
object SpecPaths {

  def pathFromResource(resource: String): Path = {
    val in = getClass.getClassLoader.getResourceAsStream(resource)
    if (in eq null) throw new FileNotFoundException(s"classpath resource not found: $resource")
    val tmp = Files.createTempFile("spec-paths-", s"-${resource.replace('/', '_')}")
    try Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING)
    finally in.close()
    Path.fromJava(tmp)
  }

  private def resourceDir(resource: String): Path = {
    val url = getClass.getClassLoader.getResource(resource)
    if (url eq null) throw new FileNotFoundException(s"classpath resource not found: $resource")
    val uri = url.toURI
    if (uri.getScheme == "jar") {
      val fs         = FileSystems.newFileSystem(uri, Collections.emptyMap[String, Any]())
      val sourceRoot = fs.getPath("/" + resource)
      val targetRoot = Files.createTempDirectory("spec-paths-").resolve(resource)
      Files.walkFileTree(
        sourceRoot,
        new SimpleFileVisitor[java.nio.file.Path] {
          override def preVisitDirectory(dir: java.nio.file.Path, attrs: BasicFileAttributes) = {
            Files.createDirectories(targetRoot.resolve(sourceRoot.relativize(dir).toString))
            FileVisitResult.CONTINUE
          }
          override def visitFile(file: java.nio.file.Path, attrs: BasicFileAttributes) = {
            Files.copy(file, targetRoot.resolve(sourceRoot.relativize(file).toString))
            FileVisitResult.CONTINUE
          }
        },
      )
      Path.fromJava(targetRoot)
    } else Path(url.getPath)
  }

  val testFolder: Path   = resourceDir("test-folder-structure")
  val testZip: Path      = pathFromResource("test-import.zip")
  val testTextFile: Path = pathFromResource("test.txt")
}
