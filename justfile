# List all recipies
default:
    just --list --unsorted

# Build a docker image locally and run it with docker-compose up
build-and-run-docker:
    sbt Docker/publishLocal
    docker-compose up
