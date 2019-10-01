REPO_PREFIX := github.com/daschswiss
KNORA_API_REPO_NAME := knora-api
KNORA_GRAPHDB_SE_REPO_NAME := knora-graphdb-se
KNORA_GRAPHDB_FREE_REPO_NAME := knora-graphdb-free
KNORA_SIPI_REPO_NAME := knora-sipi
KNORA_ASSETS_REPO_NAME := knora-assets
KNORA_UPGRADE_REPO_NAME := knora-upgrade
KNORA_SALSAH1_REPO_NAME := knora-salsah1

ifeq ($(BUILD_TAG),)
  BUILD_TAG := $(shell git describe --tag --abbrev=7 2> $(NULL))
endif
ifeq ($(BUILD_TAG),)
  BUILD_TAG := unknown
  $(warning unable to set BUILD_TAG. Set the value manually.)
endif

ifeq ($(KNORA_API_IMAGE),)
  KNORA_API_IMAGE := $(REPO_PREFIX)/$(KNORA_API_REPO_NAME):$(BUILD_TAG)
endif

ifeq ($(KNORA_GRAPHDB_SE_IMAGE),)
  KNORA_GRAPHDB_SE_IMAGE := $(REPO_PREFIX)/$(KNORA_GRAPHDB_SE_REPO_NAME):$(BUILD_TAG)
endif

ifeq ($(KNORA_GRAPHDB_FREE_IMAGE),)
  KNORA_GRAPHDB_FREE_IMAGE := $(REPO_PREFIX)/$(KNORA_GRAPHDB_FREE_REPO_NAME):$(BUILD_TAG)
endif

ifeq ($(KNORA_SIPI_IMAGE),)
  KNORA_SIPI_IMAGE := $(REPO_PREFIX)/$(KNORA_SIPI_REPO_NAME):$(BUILD_TAG)
endif

ifeq ($(KNORA_ASSETS_IMAGE),)
  KNORA_ASSETS_IMAGE := $(REPO_PREFIX)/$(KNORA_ASSETS_REPO_NAME):$(BUILD_TAG)
endif

ifeq ($(KNORA_UPGRADE_IMAGE),)
  KNORA_UPGRADE_IMAGE := $(REPO_PREFIX)/$(KNORA_UPGRADE_REPO_NAME):$(BUILD_TAG)
endif

ifeq ($(KNORA_SALSAH_IMAGE),)
  KNORA_SALSAH_IMAGE := $(REPO_PREFIX)/$(KNORA_SALSAH_REPO_NAME):$(BUILD_TAG)
endif

ifeq ($(GIT_EMAIL),)
  GIT_EMAIL := $(shell git config user.email 2> $(NULL))
endif
