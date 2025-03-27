# Testing

## How to Write and Run Unit Tests

A test is not a [unit test](https://www.martinfowler.com/bliki/UnitTest.html) if:

* It talks to the database
* It communicates across the network
* It touches the file system
* It can’t run at the same time as any of your other unit tests
* You have to do special things to your environment (such as editing config files) to run it

Unit tests live in the `src/test` folder of our sbt projects.

Run all unit tests from terminal:

```shell
sbt test
```

## How to Write and Run Integration Tests

[Mostly you should consider writing unit tests](https://www.youtube.com/watch?v=VDfX44fZoMc). 
These can be executed fast and help developers more in their daily work.

You might need to create an integration test because:

* The test needs to talk to a database
* It requires network
* It is slow and cannot run in parallel with other tests
* You have to do special things to the environment in order to run it

In this case create it in the `src/it`  source set of our projects.

.NOTE
_Currently only the `webapi` project supports integration tests._

Run all integration tests from the terminal.

```shell
make integration-test
```

.NOTE
_The integration tests currently depend on a locally published Sipi container, the make target helps you with that._

## Debugging

You can debug unit tests directly in your IDE. 

For debugging the integration tests locally with `sbt` or your IDE you need to have a fresh SIPI container build:

```shell
make docker-build-sipi-image
```
