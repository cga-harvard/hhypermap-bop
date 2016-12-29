var CONDITION_FIELD = params.get('conditionField');
var SOURCE_COPY_FIELD = params.get('sourceCopyField');
var DEST_COPY_FIELD = params.get('destCopyField');

function processAdd(cmd) {

  var doc = cmd.solrDoc;  // org.apache.solr.common.SolrInputDocument

  var condVal = doc.getFieldValue(CONDITION_FIELD);
  if (condVal == true || condVal == "true") {
    var srcVal = doc.getFieldValue(SOURCE_COPY_FIELD);
    doc.setField(DEST_COPY_FIELD, srcVal);
  }

}


// These are required:

function processDelete(cmd) {
  // no-op
}

function processMergeIndexes(cmd) {
  // no-op
}

function processCommit(cmd) {
  // no-op
}

function processRollback(cmd) {
  // no-op
}

function finish() {
  // no-op
}
