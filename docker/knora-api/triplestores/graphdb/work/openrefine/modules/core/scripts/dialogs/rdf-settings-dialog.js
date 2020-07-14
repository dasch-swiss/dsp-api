function RDFSettingsDialog(checkSettings, doneCallback) {
    this._timerID = null;
    this._doneCallback = doneCallback;
    this._createDialog(checkSettings, doneCallback);
}

RDFSettingsDialog.prototype._createDialog = function(checkSettings) {
    var self = this;
    var dialog = $(DOM.loadHTML("core", "scripts/dialogs/rdf-settings-dialog.html"));
    this._elmts = DOM.bind(dialog);

    this._elmts.okButton.click(function() { self._ok(); });
    this._elmts.cancelButton.click(function() { self._dismiss(); });
    this._elmts.resetButton.click(function() {
        self._getSavedTemplate(function(t) {
            self._fillInTemplate(t);
            updateRecordFields();
            updateEscapeValues();
        }, true);
    });

    var updateRecordFields = function() {
        self._setEnableDisableRecords(!self._elmts.ignoreRecords[0].checked);
    };
    this._elmts.ignoreRecords.on('change', updateRecordFields);

    var updateEscapeValues = function() {
        self._elmts.escapeValues.attr('disabled', !self._elmts.generateIRIs[0].checked);
    };
    this._elmts.generateIRIs.on('change', updateEscapeValues);

    this._getSavedTemplate(function(t) {
        self._fillInTemplate(t);
        updateRecordFields();
        updateEscapeValues();
        if (checkSettings) {
            if (!self._hasSettings) {
                self._level = DialogSystem.showDialog(dialog);
            } else {
                self._doneCallback();
            }
        }
    });

    if (!checkSettings) {
        this._level = DialogSystem.showDialog(dialog);
    }
};

RDFSettingsDialog.prototype._setEnableDisableRecords = function(enabled) {
    this._elmts.recordType.attr('disabled', !enabled);
    this._elmts.recordNumber.attr('disabled', !enabled);
    this._elmts.recordId.attr('disabled', !enabled);
    this._elmts.recordToRowLink.attr('disabled', !enabled);
}

RDFSettingsDialog.prototype._getSavedTemplate = function(f, forceDefault) {
    var params = {
        project: theProject.id,
        name: "ontotext.rdf.settings",
        class: "com.ontotext.forest.openrefine.RDFSettings"
    };

    if (forceDefault) {
        params.forceDefault = true;
    }

    $.getJSON(
        "command/core/get-preference?" + $.param(params),
        null,
        function(data) {
            if (data.value !== null) {
                f(data.value);
            } else {
                f(null);
            }
        }
    );
};

RDFSettingsDialog.prototype._saveSettings = function(newValue, successCallback, errorCallback) {
    $.post(
        "command/core/set-preference",
        {
            project: theProject.id,
            name : "ontotext.rdf.settings",
            value : JSON.stringify(newValue)
        },
        function(o) {
            if (o.code == "error") {
                if (errorCallback) {
                    errorCallback(o);
                }
                alert(o.message);
            } else if (successCallback) {
                successCallback();
            }
        },
        "json"
    );
}

RDFSettingsDialog.prototype._fillInTemplate = function(t) {
    this._elmts.baseIRI[0].value = t.baseIRI;
    this._elmts.baseIRIPrefix[0].value = t.baseIRIPrefix;
    this._elmts.generateIRIs[0].checked = t.generateIRIs;
    this._elmts.escapeValues[0].checked = t.escapeValues;
    this._elmts.detectIRIs[0].checked = t.detectIRIs;
    this._elmts.useRecon[0].checked = t.useRecon;
    this._elmts.rowType[0].value = t.rowType;
    this._elmts.rowNumber[0].value = t.rowNumber;
    this._elmts.recordType[0].value = t.recordType;
    this._elmts.recordNumber[0].value = t.recordNumber;
    this._elmts.recordId[0].value = t.recordId;
    this._elmts.recordType[0].value = t.recordType;
    this._elmts.recordToRowLink[0].value = t.recordToRowLink;
    this._elmts.ignoreRecords[0].checked = t.ignoreRecords;
    this._hasSettings = t.hasSettings;
};

RDFSettingsDialog.prototype._dismiss = function() {
    DialogSystem.dismissUntil(this._level - 1);
    if (this._doneCallback) {
        this._doneCallback();
    }
};

RDFSettingsDialog.prototype._ok = function() {
    var newSettings = {
        class: "com.ontotext.forest.openrefine.RDFSettings",

        baseIRI: this._elmts.baseIRI[0].value,
        baseIRIPrefix: this._elmts.baseIRIPrefix[0].value,
        generateIRIs: this._elmts.generateIRIs[0].checked,
        escapeValues: this._elmts.escapeValues[0].checked,
        detectIRIs: this._elmts.detectIRIs[0].checked,
        useRecon: this._elmts.useRecon[0].checked,
        rowType: this._elmts.rowType[0].value,
        rowNumber: this._elmts.rowNumber[0].value,
        recordType: this._elmts.recordType[0].value,
        recordNumber: this._elmts.recordNumber[0].value,
        recordId: this._elmts.recordId[0].value,
        recordToRowLink: this._elmts.recordToRowLink[0].value,
        ignoreRecords: this._elmts.ignoreRecords[0].checked
    };
    var self = this;
    this._saveSettings(newSettings, function() {
        self._dismiss();
        if (self._doneCallback) {
            self._doneCallback();
        }
    });
};