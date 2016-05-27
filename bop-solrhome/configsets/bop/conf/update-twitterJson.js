/*
  In order for this to be executed, it must be properly wired into solrconfig.xml; by default it is commented out in
  the example solrconfig.xml and must be uncommented to be enabled.
*/

var JavaLong = Java.type('java.lang.Long');

var COORD_FIELD = params.get("coordField");

function processAdd(cmd) {

  var doc = cmd.solrDoc;  // org.apache.solr.common.SolrInputDocument

  // parse ID as an unsigned long into signed long
  var idFld = doc.getField("id");
  idFld.setValue(JavaLong.parseUnsignedLong(idFld.getValue()), 1.0);

  // combine first coordinate value (degrees longitude) with second (degrees latitude) into one "lat, lon" value
  var coordFld = doc.getField(COORD_FIELD);
  if (coordFld != null && coordFld.getValueCount() == 2) {
    var coordX = coordFld.getValue().get(1);
    var coordY = coordFld.getValue().get(0);
    coordFld.setValue(coordX + "," + coordY, 1.0);
  }

}

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
