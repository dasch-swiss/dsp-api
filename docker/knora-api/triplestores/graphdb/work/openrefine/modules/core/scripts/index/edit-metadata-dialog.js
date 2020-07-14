

function EditMetadataDialog(metaData, targetRowElem) {
  this._metaDataUIs = [];
  this._metaData = metaData;
  
  this._MetadataUI = function(tr, key, value, project) {
      var self = this;
      
      if (key === "date") {
          return;
      }
      
      var td0 = tr.insertCell(0);
      
      var keyLable = $.i18n._('core-index')[key] || key;
      $('<b></b>').text(keyLable).appendTo(td0);

      var td1 = tr.insertCell(1);

      var td2 = tr.insertCell(2);

      $(td2).text((value !== null) ? value : "");

      if (key !== "created" &&
              key !== "modified" && 
              key !== "rowCount" && 
              key !== "importOptionMetadata" && 
              key !== "id")  {
          $('<a class="btn btn-link text-primary" href="#"><i class="icon-edit"></i></a>').appendTo(td1).click(function() {
            var newValue = window.prompt($.i18n._('core-index')["change-metadata-value"]+" " + key, value);
            if (newValue !== null) {
              $(td2).text(newValue);
              metaData[key] = newValue;
              $.post(
                "command/core/set-metaData",
                {
                  project : project,
                  name : key,
                  value : newValue
                },
                function(o) {
                  if (o.code === "error") {
                    alert(o.message);
                  } 
                },
                "json"
              );
            }
            
            Refine.OpenProjectUI.refreshProject(targetRowElem, metaData);
          });
      }
  };
  
  this._createDialog();
}

EditMetadataDialog.prototype._createDialog = function() {
  var self = this;
  
  var frame = $(DOM.loadHTML("core", "scripts/project/edit-metadata-dialog.html"));
  this._elmts = DOM.bind(frame);  

  this._level = DialogSystem.showDialog(frame);
  this._elmts.closeButton.html($.i18n._('core-buttons')["close"]);
  this._elmts.closeButton.click(function() { self._dismiss(); });
  
  var body = $("#metadata-body");
    
  $('<h1>').text($.i18n._('core-index')["metaDatas"]).appendTo(body);

  var metadataTable = $("<table>")
  .addClass("list-table")
  .addClass("preferences")
  .appendTo(body)[0];

    var flattenObject = function(ob, key) {
        var toReturn = {};
        for ( var i in ob) {
            if (i !== key) {
                toReturn[i] = ob[i];
                continue;
            }
            for ( var x in ob[i]) {
                toReturn[ob[i][x].name] = ob[i][x].value;
            }
        }
        return toReturn;
    };
    
  var flatMetadata = flattenObject(this._metaData, "userMetadata");
      
  for (var k in flatMetadata) {
    var tr = metadataTable.insertRow(metadataTable.rows.length);
    var v;
    
    if (typeof flatMetadata[k] === 'string') {
        v = flatMetadata[k].replace(/\"/g, "");  
    } else {
        v = JSON.stringify(flatMetadata[k]);
    }
    
    this._metaDataUIs.push(new this._MetadataUI(tr, k, v, flatMetadata.id));
  }
  
  $(".dialog-container").css("top", Math.round(($(".dialog-overlay").height() - $(frame).height()) / 2) + "px");
};

EditMetadataDialog.prototype._dismiss = function() {
    DialogSystem.dismissUntil(this._level - 1);
};

