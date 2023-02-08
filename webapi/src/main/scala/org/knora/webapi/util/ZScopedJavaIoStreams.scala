package org.knora.webapi.util

import zio._

import java.io._
import java.nio.file.Files
import java.nio.file.Path

object ZScopedJavaIoStreams {

  private def release(in: AutoCloseable): UIO[Unit] =
    ZIO.attempt(in.close()).logError("Unable to close AutoCloseable.").ignore

  def bufferedInputStream(in: InputStream): ZIO[Any with Scope, Throwable, InputStream] = {
    def acquire = ZIO.attempt(new BufferedInputStream(in))
    ZIO.acquireRelease(acquire)(release)
  }
  def bufferedOutputStream(in: OutputStream): ZIO[Any with Scope, Throwable, OutputStream] = {
    def acquire = ZIO.attempt(new BufferedOutputStream(in))
    ZIO.acquireRelease(acquire)(release)
  }

  def byteArrayOutputStream(): ZIO[Any with Scope, Throwable, ByteArrayOutputStream] = {
    def acquire                   = ZIO.attempt(new ByteArrayOutputStream())
    def release(os: OutputStream) = ZIO.succeed(os.close())
    ZIO.acquireRelease(acquire)(release)
  }

  def fileInputStream(path: Path): ZIO[Any with Scope, Throwable, InputStream] = {
    def acquire = ZIO.attempt(Files.newInputStream(path))
    if (!Files.exists(path)) {
      ZIO.fail(new IllegalArgumentException(s"File ${path.toAbsolutePath} does not exist"))
    } else {
      ZIO.acquireRelease(acquire)(release).flatMap(bufferedInputStream)
    }
  }

  /**
   * Creates a [[PipedInputStream]] so  that it is connected to the piped output stream `out`.
   * @param out
   */
  def pipedInputStream(out: PipedOutputStream): ZIO[Any with Scope, Throwable, PipedInputStream] = {
    def acquire = ZIO.attempt(new PipedInputStream(out))
    ZIO.acquireRelease(acquire)(release)
  }

  /**
   * Creates a piped output stream that is not yet connected to a
   * piped input stream. It must be connected to a piped input stream,
   * either by the receiver or the sender, before being used.
   */
  def pipedOutStream(): ZIO[Any with Scope, Throwable, PipedOutputStream] = {
    def acquire = ZIO.attempt(new PipedOutputStream())
    ZIO.acquireRelease(acquire)(release)
  }

  /**
   * Creates a piped output stream that is connected to a piped input stream.
   */
  def outputStreamPipedToInputStream(): ZIO[Any with Scope, Throwable, (PipedInputStream, PipedOutputStream)] =
    pipedOutStream().flatMap(out => pipedInputStream(out).map((_, out)))
}
