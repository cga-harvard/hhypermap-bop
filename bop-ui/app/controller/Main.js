/*eslint angular/no-services: [2,{"directive":["$http","$q"],"controller":["$resource"]}]*/
/*eslint angular/di: [2,"array"]*/
/*eslint max-len: [2,110]*/
/**
 * Main Controller
 */
(function() {
    angular
    .module('SolrHeatmapApp')
    .controller('MainController',
                ['Map', 'HeatMapSourceGenerator', '$http', '$scope', '$location',
                    '$rootScope', '$stateParams', 'searchFilter', 'queryService',
        function(Map, HeatMapSourceGenerator, $http, $scope, $location,
                 $rootScope, $stateParams, searchFilter, queryService) {
            var MapService = Map;
            var HeatMapSourceGeneratorService = HeatMapSourceGenerator;
            var mapIsMoved = false;
            var isBackbuttonPressed = false;
            // var isThereInteraction = false;

            var vm = this;
            vm.$state = $stateParams;
            vm.isThereInteraction = false;

            vm.setupEvents = function() {
                MapService.getMap().getView().on('change:center', function(evt){
                    mapIsMoved = !mapIsMoved ? true : false;
                });
                MapService.getMap().getView()
                    .on('change:resolution', function(evt){
                        vm.isThereInteraction = true;
                    });
                MapService.getMap().on('moveend', function(evt){
                    if ((mapIsMoved || searchFilter.geo === '[-90,-180 TO 90,180]') && !isBackbuttonPressed) {
                        vm.isThereInteraction = true;
                        changeGeoSearch();
                        mapIsMoved = false;
                    }else if(!isBackbuttonPressed){
                        vm.isThereInteraction = true;
                        mapIsMoved = false;
                        changeGeoSearch(false);
                    }else {
                        isBackbuttonPressed = false;
                        HeatMapSourceGeneratorService.search();
                    }
                });

                var locationChangeEventBroadcast = $rootScope.$on('$locationChangeSuccess', function() {
                    if (!vm.isThereInteraction) {
                        isBackbuttonPressed = true;
                        var extent = queryService.
                          getExtentForProjectionFromQuery($location.search().geo,
                                                          solrHeatmapApp.initMapConf.view.projection);
                        MapService.getMap().getView().fit(extent, MapService.getMapSize());
                    }
                    vm.isThereInteraction = false;
                });

                function changeGeoSearch(changeUrl) {
                    changeUrl = angular.isUndefined(changeUrl) || changeUrl ? true : false;
                    MapService.checkBoxOfTransformInteraction();
                    var currentExtent = MapService.getCurrentExtentQuery();
                    searchFilter.setFilter({geo: currentExtent.geo, hm: currentExtent.hm });
                    HeatMapSourceGeneratorService.search(changeUrl);
                }
            };

            vm.response = function(response) {
                var data = response ? response.data : undefined;
                if (data && data.mapConfig) {
                    var mapConf = data.mapConfig,
                        appConf = data.appConfig,
                        bopwsConfig = data.bopwsConfig,
                        instructions = data.instructions;

                    if(solrHeatmapApp.$state.geo) {
                        mapConf.view.initExtent = mapConf.view.extent;
                        mapConf.view.extent = queryService.
                          getExtentForProjectionFromQuery(solrHeatmapApp.$state.geo,
                                                          mapConf.view.projection);
                        mapConf.view.extent = MapService
                            .calculateFullScreenExtentFromBoundingBox(mapConf.view.extent);
                    }
                    MapService.init({
                        mapConfig: mapConf
                    });
                    solrHeatmapApp.appConfig = appConf;
                    solrHeatmapApp.initMapConf = mapConf;
                    solrHeatmapApp.bopwsConfig = bopwsConfig;
                    solrHeatmapApp.instructions = instructions;

                    // fire event mapReady
                    $rootScope.$broadcast('mapReady', MapService.getMap());

                    solrHeatmapApp.setupEvents();
                    /*
                    * register some events
                    */

                // Prepared featureInfo (display number of elements)
                //solrHeatmapApp.map.on('singleclick',
                //                          MapService.displayFeatureInfo);

                } else {
                    throw new Error('Could not find the mapConfig');
                }
            };
            vm.badResponse = function(data) {
                throw new Error('Error while loading the config.json');
            };

            solrHeatmapApp = vm;

            //  get the app config
            $http.get('./config/appConfig.json')
                .then(solrHeatmapApp.response, solrHeatmapApp.badResponse);
        }]
);
})();
