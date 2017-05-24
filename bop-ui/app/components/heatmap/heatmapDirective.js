/*eslint angular/di: [2,"array"]*/
/*eslint angular/controller-as: 0*/
/**
 * ResultCounter Controller
 */

(function() {
    angular
    .module('search_heatmap_component', [])
    .directive('heatmap', heatmap);

    function heatmap() {
        return {
            restrict: 'EA',
            templateUrl: 'components/heatmap/heatmap.tpl.html',
            scope: {}
        };
    }

})();
