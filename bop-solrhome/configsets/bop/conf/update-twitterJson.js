/*
 * Copyright 2016 President and Fellows of Harvard College
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
  In order for this to be executed, it must be properly wired into solrconfig.xml.
*/

var Long = Java.type('java.lang.Long');
var BigInteger = Java.type('java.math.BigInteger');

var BIG_INT_LONG_MIN = BigInteger.valueOf(Long.MIN_VALUE); // big negative number

var COORD_FIELD = params.get("coordField");

function processAdd(cmd) {

  var doc = cmd.solrDoc;  // org.apache.solr.common.SolrInputDocument

  // parse ID as an unsigned long, convert to sortable signed long (0 becomes Long.MIN_VALUE)
  var idFld = doc.getField("id");
  idFld.setValue(new BigInteger(idFld.getValue()).add(BIG_INT_LONG_MIN).longValueExact(), 1.0);
  // note: reverse: (BigInteger.valueOf(longValue) - BigInteger.valueOf(Long.MIN_VALUE)).toString()

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
