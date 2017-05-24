/*eslint angular/controller-as: 0*/
/*eslint angular/di: [2,"array"]*/
/*eslint max-len: [2,90]*/
/**
 * Export Directive
 */
(function() {
    angular
    .module('search_buttonPanel_component', [])
    .directive('buttonPanel', [function() {
        return {
            link: link,
            restrict: 'EA',
            templateUrl: 'components/buttonPanel/buttonPanel.tpl.html',
            scope: {}
        };

        function link(scope) {

        }
    }]);
})();
