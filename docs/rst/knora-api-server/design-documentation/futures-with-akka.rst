.. Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
   Tobias Schweizer, André Kilchenmann, and André Fatton.

   This file is part of Knora.

   Knora is free software: you can redistribute it and/or modify
   it under the terms of the GNU Affero General Public License as published
   by the Free Software Foundation, either version 3 of the License, or
   (at your option) any later version.

   Knora is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
   GNU Affero General Public License for more details.

   You should have received a copy of the GNU Affero General Public
   License along with Knora.  If not, see <http://www.gnu.org/licenses/>.

.. _futures-with-akka:

Futures with Akka
==================

Introduction
--------------

`Scala's documentation on futures`_ introduces them in this way:

    Futures provide a nice way to reason about performing many operations in
    parallel – in an efficient and non-blocking way. The idea is simple, a
    Future is a sort of a placeholder object that you can create for a result
    that does not yet exist. Generally, the result of the Future is computed
    concurrently and can be later collected. Composing concurrent tasks in
    this way tends to result in faster, asynchronous, non-blocking parallel
    code.

The rest of that page is well worth reading to get an overview of how
futures work and what you can do with them.

In Akka_, one of the standard patterns for communication between actors is the
`ask pattern`_, in which you send a message to an actor and you expect a
reply. When you call the ``ask`` function (which can be written as a question
mark, ``?``, which acts as an infix operator), it immediately returns a
``Future``, which will complete when the reply is sent. As the Akka
documentation explains in `Use with Actors`_, it is possible to block the
calling thread until the future completes, using ``Await.result``. However,
they say: 'Blocking is discouraged though as it will cause performance
problems.' In particular, by not blocking, you can do several ``ask`` requests
in parallel.

One way to avoid blocking is to register a callback on the future, which
will be called when it completes (perhaps by another thread), like this:

::

    future.onComplete {
        case Success(result) => println(result)
        case Failure(ex) => ex.printStackTrace()
    }

But this won't work if you're writing a method that needs return a value
based on the result of a future. In this case, you can register a
callback that transforms the result of a future into another future:

::

    val newFuture = future.map(x => x + 1)

However, registering callbacks explicitly gets cumbersome when you need
to work with several futures together. In this case, the most convenient
alternative to blocking is to use ``Future`` as a monad. The links above
explain what this means in detail, but the basic idea is that a special
syntax, called a ``for``-comprehension, allows you to write code that
uses futures as if they were complete, without blocking. In reality, a
``for``-comprehension is syntactic sugar for calling methods like
``map``, but it's much easier to write and to read. You can do things
like this:

::

    val fooFuture = (fooActor ? GetFoo("foo")).mapTo[Foo]
    val barFuture = (barActor ? GetBar("bar")).mapTo[Bar]

    val totalFuture = for {
        foo: Foo <- fooFuture
        bar: Bar <- barFuture

        total = foo.getCount + bar.getCount
    } yield total

Here the messages to ``fooActor`` and ``barActor`` are sent and processed in
parallel, but you're guaranteed that ``total`` won't be calculated until the
values it needs are available. Note that if you construct ``fooFuture`` and
``barFuture`` inside the ``for`` comprehension, they won't be run in parallel
(see `Scala for-comprehension with concurrently running futures`_).

With one line of code, you can even make a list of messages to be sent
to actors, send them all in parallel, get back a list of futures, and
convert it to a single future which will complete when all the results
are available; see ``org.knora.webapi.util.ActorUtils.parallelAsk``.

.. _handling-errors-with-futures:

Handling Errors with Futures
-----------------------------

The constructors and methods of ``Future`` (like those of ``Try``) catch
exceptions, which cause the future to fail. This very useful property of
futures means that you usually don't need ``try``-``catch`` blocks when
using the ``Future`` monad (although it is sometimes helpful to include
them, in order to catch low-level exceptions and wrap them in
higher-level ones). Any exception thrown in code that's being run
asynchronously by ``Future`` (including in the ``yield`` expression of a
``for`` comprehension) will be caught, and the result will be a
``Future`` containing a ``Failure``. Also, in the previous example, if
``fooActor`` or ``barActor`` returns a ``Status.Failure`` message, the
``for``-comprehension will also yield a failed future.

However, you need to be careful with *the first line* of the
``for``-comprehension. For example, this code doesn't handle exceptions
correctly:

::

    private def doFooQuery(iri: IRI): Future[String] = {
        for {
            queryResponse <- (storeManager ? SparqlSelectRequest(queries.sparql.v1.txt.getFoo(iri).toString())).mapTo[SparqlSelectResponse]
            ...
       } yield ...
    }

The ``getFoo()`` method calls a
`Twirl <https://github.com/playframework/twirl>`__ template function to
generate SPARQL. The ``?`` operator returns a ``Future``. However, the
template function *is not run asynchronously*, because it is called
before the ``Future`` constructor is called. So if the template function
throws an exception, it won't be caught here. Instead, you can do this:

::

    private def doFooQuery(iri: IRI): Future[String] = {
        for {
            queryString <- Future(queries.sparql.v1.txt.getFoo(iri).toString())
            queryResponse <- (storeManager ? SparqlSelectRequest(queryString)).mapTo[SparqlSelectResponse]
            ...
       } yield ...
    }

Here the ``Future`` constructor will call the template function
asynchronously, and catch any exceptions it throws. This is only
necessary if you need to call the template function at the *very
beginning* of a ``for``-comprehension. In the rest of the ``for``
comprehension, you'll already implicitly have a ``Future`` object.

Designing with Futures
-----------------------

In the current design, the Knora API Server almost never blocks to wait for a
future to complete. The normal flow of control works like this:

1. Incoming HTTP requests are handled by an actor called ``KnoraHttpService``,
   which delegates them to routing functions (in the ``routing`` package).

2. For each request, a routing function gets a ``spray-http``
   ``RequestContext``, and calls ``RouteUtils.runJsonRoute`` to send a message
   to a supervisor actor to fulfil the request. Having sent the message, the
   ``runJsonRoute`` gets a future in return. It does not block to wait for the
   future to complete, but instead registers a callback to process the result
   of the future when it becomes available.

3. The supervisor forwards the message to be handled by the next available
   actor in a pool of responder actors that are able to handle that type of
   message.

4. The responder's ``receive`` method receives the message, and calls some
   private method that produces a reply message inside a future. This usually
   involves sending messages to other actors using ``ask``, getting futures
   back, and combining them into a single future containing the reply message.

5. The responder passes that future to ``ActorUtils.future2Message``, which
   registers a callback on it. When the future completes (perhaps in another
   thread), the callback sends the reply message. In the meantime, the
   responder doesn't block, so it can start handling the next request.

6. When the responder's reply becomes available (causing the future created by
   ``RouteUtils.runJsonRoute`` to complete), the callback registered in (2)
   calls ``complete`` on the ``RequestContext``, which sends an HTTP response
   to the client.

The basic rule of thumb is this: if you're writing a method in an actor,
and anything in the method needs to come from a future (e.g. because you
need to use ``ask`` to get some information from another actor), have
the method return a future.

Mixing Futures with non-Futures
--------------------------------

If you have a ``match ... case`` or ``if`` expression, and one branch
obtains some data in a future, but another branch can produce the data
immediately, you can wrap the result of the latter branch in a future,
so that both branches have the same type:

::

    def getTotalOfFooAndBar(howToGetFoo: String): Future[Int] = {
        for {
            foo <- howToGetFoo match {
                case "askForIt" => (fooActor ? GetFoo("foo")).mapTo[Foo]
                case "createIt" => Future(new Foo())
            }

            bar <- (barActor ? GetBar("bar")).mapTo[Bar]

            total = foo.getCount + bar.getCount
        } yield total
    }

How to Write For-Comprehensions
--------------------------------

Here are some basic rules for writing ``for``-comprehensions:

1. The first line of a ``for``-comprehension has to be a "generator",
   i.e. it has to use the ``<-`` operator. If you want to write an
   assignment (using ``=``) as the first line, the workaround is to wrap
   the right-hand side in a monad (like ``Future``) and use ``<-``
   instead.

2. Assignments (using ``=``) are written without ``val``.

3. You're not allowed to write statements that throw away their return
   values, so if you want to call something like ``println`` that
   returns ``Unit``, you have to assign its return value to ``_``.

The ``yield`` returns an object of the same type as the generators,
which all have to produce the same type (e.g. ``Future``).

Execution Contexts
-------------------

Whenever you use a future, there has to be an implicit 'execution context' in
scope. `Scala's documentation on futures`_ says, 'you can think of execution
contexts as thread pools'.

If you don't have an execution context in scope, you'll get a compile error
asking you to include one, and suggesting that you could use
``import scala.concurrent.ExecutionContext.Implicits.global``. Don't do this, because
the global Scala execution context is not the most efficient option. Instead,
you can use the one provided by the Akka ``ActorSystem``:

::

    implicit val executionContext = system.dispatcher

Akka's execution contexts can be configured (see Dispatchers_). You can
see a `Listing of the Reference Configuration`_.

.. _Scala's documentation on futures: http://docs.scala-lang.org/overviews/core/futures.html
.. _Akka: http://akka.io/
.. _ask pattern: http://doc.akka.io/docs/akka/snapshot/scala/actors.html#Ask__Send-And-Receive-Future
.. _Use with Actors: http://doc.akka.io/docs/akka/snapshot/scala/futures.html#Use_With_Actors
.. _Scala for-comprehension with concurrently running futures: http://buransky.com/scala/scala-for-comprehension-with-concurrently-running-futures/
.. _Dispatchers: http://doc.akka.io/docs/akka/snapshot/scala/dispatchers.html
.. _Listing of the Reference Configuration: http://doc.akka.io/docs/akka/snapshot/general/configuration.html#Listing_of_the_Reference_Configuration
