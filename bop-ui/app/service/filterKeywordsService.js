/*eslint angular/di: [2,"array"]*/
(function() {
    angular
    .module('SolrHeatmapApp')
    .factory('filterKeywordService', [function() {
        return {
            filter: filter
        };

        function filter(keywordObj) {
            return keywordObj.value.length >= 3 && !iskeywordInTheBlacklist(keywordObj.value);
        }

        function iskeywordInTheBlacklist(comparedWord) {
            var blacklist = ["t.co","https","http","in","at","i'm","a","to","for",
            "the","this","and","you","de","our","with","of","we're","be","on","i",
            "w","la","amp","is","my","en","que","by","me","y","ca","it","if","el",
            "do","1","no","from","you're","e","c","up","o","del","los","that"];

            var comparedList = blacklist.filter(function(blackWord) {
                return blackWord === comparedWord;
            });
            return comparedList.length === 0 ? false : true;
        }

    }]);
})();
