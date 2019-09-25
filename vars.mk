REPO_PREFIX := github.com/daschswiss/
KNORA_API_REPO_NAME ?= knora-api
GRAPHDB_SE_REPO_NAME ?= knora-graphdb-se
GRAPHDB_FREE_REPO_NAME ?= knora-graphdb-free
SIPI_REPO_NAME ?= knora-sipi
ASSETS_REPO_NAME ?= knora-assets
UPGRADE_REPO_NAME ?= knora-upgrade
SALSAH1_REPO_NAME ?= salsah1

ifeq ($(BUILD_TAG),)
  BUILD_TAG := $(shell git describe --tag --abbrev=7 2> $(NULL))
endif

ifeq ($(GIT_EMAIL),)
  GIT_EMAIL := $(shell git config user.email 2> $(NULL))
endif