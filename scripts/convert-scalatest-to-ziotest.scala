#!/usr/bin/env scala-cli

//> using dep "org.scala-lang.modules::scala-parser-combinators:2.3.0"

import java.io.{File, PrintWriter}
import scala.io.Source
import scala.util.matching.Regex

object ConvertScalaTestToZioTest {
  
  def main(args: Array[String]): Unit = {
    if (args.length != 1) {
      println("Usage: convert-scalatest-to-ziotest.scala <file-path>")
      System.exit(1)
    }
    
    val filePath = args(0)
    val content = Source.fromFile(filePath).mkString
    val convertedContent = convertToZioTest(content)
    
    // Write back to file
    val writer = new PrintWriter(new File(filePath))
    try {
      writer.write(convertedContent)
    } finally {
      writer.close()
    }
    
    println(s"Converted $filePath to ZIO Test format")
  }
  
  def convertToZioTest(content: String): String = {
    var result = content
    
    // Convert test method signatures
    val testPattern: Regex = """"([^"]+)" in \{""".r
    result = testPattern.replaceAllIn(result, m => 
      s"""test("${m.group(1)}") {"""
    )
    
    // Convert ScalaTest assertions to ZIO Test assertions
    // Basic equality assertions
    result = result.replaceAll(
      """assert\(([^,]+) == ([^,]+), ([^)]+)\)""",
      """assert($1)(equalTo($2))"""
    )
    
    result = result.replaceAll(
      """assert\(([^,]+) == ([^)]+)\)""",
      """assert($1)(equalTo($2))"""
    )
    
    // Convert basic assert to assertTrue equivalent
    result = result.replaceAll(
      """assert\(([^)]+)\)""",
      """assert($1)(isTrue)"""
    )
    
    // Convert should matchers
    result = result.replaceAll(
      """([^.]+)\.status should equal\(([^)]+)\)""",
      """assert($1.status)(equalTo($2))"""
    )
    
    result = result.replaceAll(
      """([^.]+) should equal\(([^)]+)\)""",
      """assert($1)(equalTo($2))"""
    )
    
    result = result.replaceAll(
      """([^.]+) should be\(([^)]+)\)""",
      """assert($1)(equalTo($2))"""
    )
    
    // Wrap test bodies to return ZIO effect when needed
    // This is a simple heuristic - may need refinement
    val testBodyPattern = """(test\("[^"]+"\) \{\s*)((?:[^{}]*\{[^{}]*\})*[^{}]*?)(\s*\})""".r
    result = testBodyPattern.replaceAllIn(result, m => {
      val testDecl = m.group(1)
      val testBody = m.group(2)
      val closing = m.group(3)
      
      // Check if test body already returns ZIO or contains ZIO operations
      if (testBody.contains("ZIO.") || testBody.contains(".map(") || 
          testBody.contains(".flatMap(") || testBody.contains("assert(") && testBody.contains(")(")) {
        // Already seems to be ZIO-based, just add ZIO.succeed if no explicit ZIO return
        if (!testBody.trim.startsWith("ZIO.") && !testBody.contains("assert(")) {
          s"${testDecl}ZIO.succeed {\n${testBody}\n      }${closing}"
        } else {
          s"${testDecl}${testBody}${closing}"
        }
      } else {
        s"${testDecl}ZIO.succeed {\n${testBody}\n      }${closing}"
      }
    })
    
    result
  }
}

ConvertScalaTestToZioTest.main(args)