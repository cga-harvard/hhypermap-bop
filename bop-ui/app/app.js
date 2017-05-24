/**
 * The main solrHeatmapApp module
 */
(function() {
    angular.module('SolrHeatmapApp', [
        'templates-components',
        'ui.bootstrap',
        'rzModule',
        'search_components',
        'ui.router'
    ]);
    angular.module('SolrHeatmapApp')
    .config(function($locationProvider, $stateProvider, $urlRouterProvider) {
        $locationProvider.html5Mode({
            enabled: false,
            requireBase: false
        });
        $urlRouterProvider.otherwise('/search');
        $stateProvider.state({
            name: 'search',
            url: '/search?time&geo&text&user',
            component: 'search',
            resolve: {
                search: function($stateParams,HeatMapSourceGenerator,searchFilter,
                    DateTimeService) {
                    searchFilter.setFilter($stateParams);
                }
            }
        });
    });
})();
