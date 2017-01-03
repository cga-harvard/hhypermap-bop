var CONDITION_FIELD = params.get('conditionField');
var SOURCE_COPY_FIELD = params.get('sourceCopyField');
var DEST_COPY_FIELD = params.get('destCopyField');

var TWEET_TEXT_FIELD = "text"
var URL_FIELD = "urls";
var MENTION_FIELD = "mentions";
var HASHTAG_FIELD = "hashtags";

// note: we use Java Pattern because it's stateless/Thread-safe; JavaScript RegExp maintains lastIndex!
// TODO use https://raw.githubusercontent.com/twitter/twitter-text/master/java/src/com/twitter/Regex.java
var Pattern = Java.type('java.util.regex.Pattern');
var URL_PATTERN = Pattern.compile("\\b\\w+://\\S+");
var MENTION_PATTERN = Pattern.compile("\\B@(\\S+)");
var HASHTAG_PATTERN = Pattern.compile("\\B#(\\S+)");

function processAdd(cmd) {

  var doc = cmd.solrDoc;  // org.apache.solr.common.SolrInputDocument

  // PROCESS CONDITIONAL FIELD COPY
  var condVal = doc.getFieldValue(CONDITION_FIELD);
  if (condVal == true || condVal == "true") {
    var srcVal = doc.getFieldValue(SOURCE_COPY_FIELD);
    doc.setField(DEST_COPY_FIELD, srcVal);
  }

  // PROCESS ENTITIES
  //   TODO we ought to consume this on input since Twitter API extracts it for us, and probably
  //   uses the user's registered username casing in the username.
  var tweetText = doc.getFieldValue(TWEET_TEXT_FIELD);
  extractPattern(URL_PATTERN, tweetText, doc, URL_FIELD, function (matcher) {return matcher.group(0)});
  extractPattern(MENTION_PATTERN, tweetText, doc, MENTION_FIELD, function (matcher) {return matcher.group(1)});
  extractPattern(HASHTAG_PATTERN, tweetText, doc, HASHTAG_FIELD, function (matcher) {return matcher.group(1).toLowerCase()});
}

function extractPattern(pattern, text, doc, fieldName, matcherToValFunc) {
  var matcher = pattern.matcher(text);
  while (matcher.find()) {
    var substring = matcherToValFunc(matcher);
    doc.addField(fieldName, substring);
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
