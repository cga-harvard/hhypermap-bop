/*eslint angular/di: [2,"array"]*/
/**
 * BackgroundLayer Controller
 */
(function() {
    angular
    .module('SolrHeatmapApp')
    .controller('BackgroundLayerController',
        ['MapService', '$scope', function(MapService, $scope) {

            var vm = this;

            /**
             *
             */
            vm.layers = {};
            vm.selectedLayer = {};

            /**
             *
             */
            vm.$on('mapReady', function(event, map) {
                vm.layers = map.getLayers().getArray();
                vm.selectedLayer = {
                    name: vm.getBackgroundLayers()[0].get('name')
                };
            });

            /**
             *
             */
            vm.isBackgroundLayer = function(layer) {
                var isBackgroundLayer = false;
                if (layer.get('backgroundLayer')) {
                    isBackgroundLayer = true;
                }
                return isBackgroundLayer;
            };

            /**
             *
             */
            vm.setBackgroundLayer = function(layer) {
                angular.forEach(vm.getBackgroundLayers(), function(bgLayer) {
                    if (bgLayer === layer) {
                        layer.setVisible(true);
                        vm.selectedLayer = {name: layer.get('name')};
                    } else {
                        bgLayer.setVisible(false);
                    }
                });
            };

            /**
             *
             */
            vm.getBackgroundLayers = function() {
                var layers = MapService.getMap().getLayers().getArray();

                return layers.filter(function(l) {
                    if (l.get('backgroundLayer')) {
                        return true;
                    } else {
                        return false;
                    }
                });
            };

        }]
);
})();
