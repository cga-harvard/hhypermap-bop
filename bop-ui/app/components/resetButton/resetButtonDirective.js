/*eslint angular/controller-as: 0*/
/*eslint angular/di: [2,"array"]*/
/*eslint max-len: [2,90]*/
/**
 * Export Directive
 */
(function() {
    angular
    .module('search_resetButton_component', [])
    .directive('resetButton', ['HeatMapSourceGenerator', 'searchFilter', 'Map',
        function(HeatMapSourceGenerator, searchFilter, Map) {
            return {
                link: ResetLink,
                restrict: 'EA',
                template: '<button class="btn btn-primary from-panel" id="resetbtn" ' +
                    'title="RESET" type="button" ng-click="reset()">RESET</button>',
                scope: {}
            };

            function ResetLink(scope) {
                scope.reset = function reset() {
                    // Reset the map
                    Map.resetMap();
                    searchFilter.resetFilter();
                    HeatMapSourceGenerator.search();
                };
            }
        }])
    .directive('basemapButton', ['Map', function(Map) {
        return {
            link: link,
            template: '<button class="btn btn-default from-panel" type="button" ' +
                'ng-click="toggleBaseMaps()">BASEMAPS</button>',
            scope: {}
        };

        function link(scope) {
            scope.toggleBaseMaps = function() {
                Map.toggleBaseMaps();
            };
        }
    }]);
})();
