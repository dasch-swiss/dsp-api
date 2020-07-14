function SparqlExtras(button) {
  this._button = button;
  this._initializeUI();
}

SparqlExtras.handlers = {};

SparqlExtras.MenuItems = [
  {
    "id" : "sparql/generate-select",
    "label": "Generate SELECT",
    "click": function() { SparqlExtras.handlers.generateSelect(); }
  },
  {
    "id" : "sparql/generate-construct",
    "label": "Generate CONSTRUCT",
    "click": function() { SparqlExtras.handlers.generateConstruct(); }
  },
  {},
  {
    "id" : "sparql/open-in-repositories",
    "label": "Open in main SPARQL editor",
    "click": function() { SparqlExtras.handlers.openInMainEditor(); }
  },
  {},
  {
    "id" : "sparql/endpoint",
    "label": "Get SPARQL endpoint...",
    "click": function() { SparqlExtras.handlers.getSparqlEndpoint(); }
  },
  {},
  {
    "id" : "sparql/settings",
    "label": "RDF settings...",
    "click": function() { new RDFSettingsDialog(); }
  }
];

SparqlExtras.prototype._initializeUI = function() {
  this._button.click(function(evt) {
    MenuSystem.createAndShowStandardMenu(
        SparqlExtras.MenuItems,
        this,
        { horizontal: false }
    );

    evt.preventDefault();
    return false;
  });
};

SparqlExtras.prototype.setLoadedQuery = function(query) {
    window.yasqe.setValue(query);
    YASQE.storeQuery(yasqe);
};

SparqlExtras.prototype.loadQuery = function(type) {
    $.ajax({
        type: "GET",
        url: "../../rest/openrefine/" + type + "/" + theProject.id,
        success: SparqlExtras.prototype.setLoadedQuery
    });
};

SparqlExtras.handlers.generateSelect = function() {
    SparqlExtras.prototype.loadQuery('select');
};

SparqlExtras.handlers.generateConstruct = function() {
    SparqlExtras.prototype.loadQuery('construct');
};

SparqlExtras.handlers.openInMainEditor = function() {
    $.ajax({
        type: "POST",
        data: { query: window.yasqe.getValue() },
        url: "../../rest/openrefine/rewrite/" + theProject.id,
        success: function(data) {
            window.open('../sparql?name=' + encodeURIComponent('OntoRefine: ' + theProject.metadata.name) + '&query=' + encodeURIComponent(data), '_blank');
        },
        error: function(data) {
            alert(data.responseJSON);
        }
    });
};

SparqlExtras.handlers.getSparqlEndpoint = function() {
    $.ajax({
        type: "GET",
        url: "../../rest/openrefine/endpoint/" + theProject.id,
        success: function(data) {
            Refine.createEditPopup("Press Ctrl+C / Cmd+C to copy to clipboard", data, "scripts/views/data-table/editor-sparql-endpoint.html");
        }
    });
}

