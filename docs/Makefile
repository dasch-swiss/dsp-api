# Makefile for API V1 and V2 typescript documentation
#

# You can set these variables from the command line.
FORMATSRCDIRV1  = src/api-v1
FORMATDOCDIRV1  = target/site/api-v1
FORMATSRCDIRV2  = src/api-v2
FORMATDOCDIRV2  = target/site/api-v2

TYPEDOC         = typedoc

# User-friendly check for typedoc
ifeq ($(shell which $(TYPEDOC) >/dev/null 2>&1; echo $$?), 1)
$(error The '$(TYPEDOC)' command was not found. Make sure you have Typedoc installed.)
endif

# Graphviz diagrams to be converted to PNG
DOT_FIGURES = $(shell find ./ -type f -name '*.dot')
PNG_FIGURES = $(patsubst %.dot,%.dot.png,$(DOT_FIGURES))

.PHONY: clean
clean: ## to clean
	rm -rf $(FORMATDOCDIRV1)/*
	rm -rf $(FORMATDOCDIRV2)/*
	rm -f $(PNG_FIGURES)

.PHONY: jsonformat
jsonformat: ## to make a documentation of the json request and response format
	$(TYPEDOC) --out $(FORMATDOCDIRV1) --exclude **/sampleRequests/* --readme none --name "DSP API V1 Format Documentation" --module commonjs $(FORMATSRCDIRV1)
	$(TYPEDOC) --out $(FORMATDOCDIRV2) --exclude **/$(FORMATSRCDIRV2)/samples/* --readme none --name "DSP API V2 JSON-LD Format Documentation" --module commonjs $(FORMATSRCDIRV2)
	@echo "Format Docs Build finished. The index page for V1 is $(FORMATDOCDIR)/index.html, for V2 $(FORMATDOCDIRV2)/index.html"

.PHONY: jsonformattest
jsonformattest: ## to make a documentation of the json request and response format
	$(TYPEDOC) --out $(FORMATDOCDIRV1) --readme none --name "DSP API V1 Format Documentation" --module commonjs $(FORMATSRCDIRV1)
	$(TYPEDOC) --out $(FORMATDOCDIRV2) --readme none --name "DSP API V2 JSON-LD Format Documentation" --module commonjs $(FORMATSRCDIRV2)
	@echo "Sample requests test finished."

.PHONY: graphvizfigures
graphvizfigures: $(PNG_FIGURES) ## to generate images from dot files

%.dot.png: %.dot
	dot -Tpng $< -o $@


.PHONY: help
help: ## this help
	@echo "Please use \`make <target>' where <target> is one of"
	@awk 'BEGIN {FS = ":.*?## "} /^[a-zA-Z_-]+:.*?## / {printf "\033[36m%-30s\033[0m %s\n", $$1, $$2}' $(MAKEFILE_LIST) | sort

.DEFAULT_GOAL := help
