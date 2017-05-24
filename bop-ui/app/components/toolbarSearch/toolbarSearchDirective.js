/*eslint angular/controller-as: 0*/
/*eslint angular/di: [2,"array"]*/
/*eslint max-len: [2,120]*/
/**
 * Search Directive
 */
(function() {
    angular
    .module('search_toolbarsearch_component', [])
    .directive('toolbarSearch', [
        function toolbarSearch() {
            return {
                restrict: 'EA',
                template: '<keyword-input number-keywords="50" text="text" limit="textLimit"' +
                            'listen-keyword-event="setSuggestWords" placeholder="Enter keyword">' +
                        '</keyword-input>',
                scope: {}
            };
        }]);
})();
