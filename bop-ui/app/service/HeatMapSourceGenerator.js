/*eslint angular/di: [2,"array"]*/
/*eslint angular/document-service: 2*/
/*eslint max-len: [2,150]*/
/**
 * HeatMapSourceGenerator Service
 */
(function() {
    angular
    .module('SolrHeatmapApp')
    .factory('HeatMapSourceGenerator', ['Map', '$rootScope', '$controller', '$filter', '$log',
        '$document', '$q', '$http', '$state', 'searchFilter', 'DateTimeService', 'DataCacheService',
        function(Map, $rootScope, $controller, $filter, $log, $document, $q,
            $http, $state, searchFilter, DateTimeService, DataCacheService) {
            var MapService= Map;
            var canceler = $q.defer();

            function simpleSearch(params, callback) {
                var sF = searchFilter;
                params['q.text'] = sF.text;
                params['q.user'] = sF.user;
                params['q.time'] = timeTextFormat(sF.time, sF.minDate, sF.maxDate);
                canceler.resolve();
                canceler = $q.defer();
                var config = {
                    url: solrHeatmapApp.appConfig.tweetsSearchBaseUrl,
                    method: 'GET',
                    params: params,
                    timeout: canceler.promise
                };
                $http(config).then(function(response) {
                    return callback(response);
                });
            }

            /**
             *
             */
            function createParamsForGeospatialSearch () {
                var sF = searchFilter;
                return {
                    'q.text': sF.text,
                    'q.user': sF.user,
                    'q.time': timeTextFormat(sF.time, sF.minDate, sF.maxDate),
                    'q.geo': sF.geo,
                    'a.hm.filter': sF.hm,
                    'a.time.limit': '1',
                    'a.time.gap': sF.gap,
                    'd.docs.limit': sF.numOfDocs,
                    'a.text.limit': sF.textLimit,
                    'a.user.limit': sF.userLimit,
                    'd.docs.sort': 'distance'
                };
            }

            function setUrlwithParams(params) {
                $state.go('search', {
                    text: params['q.text'],
                    user: params['q.user'],
                    time: params['q.time'],
                    geo: params['q.geo']
                }, {});
            }

            /**
             * Performs search with the given full configuration / search object.
             */
            function search(changeUrl){
                var config, params = createParamsForGeospatialSearch();
                changeUrl = angular.isUndefined(changeUrl) || changeUrl ? true : false;
                if (params) {
                    canceler.resolve();
                    canceler = $q.defer();

                    params['a.hm.limit'] = solrHeatmapApp.bopwsConfig.heatmapFacetLimit;
                    config = {
                        url: solrHeatmapApp.appConfig.tweetsSearchBaseUrl,
                        method: 'GET',
                        params: params,
                        timeout: canceler.promise
                    };
                    //load the data
                    $http(config)
                    .then(function successCallback(response) {
                        if (changeUrl) {
                            setUrlwithParams(params);
                        }
                        // check if we have a heatmap facet and update the map with it
                        var data = response.data;
                        // DataCacheService.insertData(config.params, data);
                        broadcastData(data);

                    }, function errorCallback(response) {
                        $log.error('An error occured while reading heatmap data');
                    })
                    .catch(function() {
                        $log.error('An error occured while reading heatmap data');
                    });
                } else {
                    $log.error('Spatial filter could not be computed.');
                }
            }

            function broadcastData(data) {
                data['a.text'] = data['a.text'] || [];
                if (data && data['a.hm']) {
                    MapService.createOrUpdateHeatMapLayer(data['a.hm']);

                    $rootScope.$broadcast('setCounter', data['a.matchDocs']);

                    $rootScope.$broadcast('setHistogram', data['a.time']);

                    $rootScope.$broadcast('setTweetList', data['d.docs']);

                    $rootScope.$broadcast('setSuggestWords', data['a.text']);

                    $rootScope.$broadcast('setUserSuggestWords', data['a.user']);
                }
            }

            function startCsvExport(numberOfDocuments){
                var config,
                    params = createParamsForGeospatialSearch();
                if (params) {
                    params['d.docs.limit'] = angular.isNumber(numberOfDocuments) ?
                            numberOfDocuments : solrHeatmapApp.bopwsConfig.csvDocsLimit;
                    config = {
                        url: solrHeatmapApp.appConfig.tweetsExportBaseUrl,
                        method: 'GET',
                        params: params
                    };

                    //start the export
                    $http(config)
                    .then(function successCallback(response) {
                        var anchor = angular.element('<a/>');
                        anchor.css({display: 'none'}); // Make sure it's not visible
                        angular.element($document.body).append(anchor); // Attach to document
                        anchor.attr({
                            href: 'data:attachment/csv;charset=utf-8,' + encodeURI(response.data),
                            target: '_blank',
                            download: 'bop_export.csv'
                        })[0].click();
                        anchor.remove(); // Clean it up afterwards
                    }, function errorCallback(response) {
                        $log.error('An error occured while exporting csv data');
                    })
                    .catch(function() {
                        $log.error('An error occured while exporting csv data');
                    });
                } else {
                    $log.error('Spatial filter could not be computed.');
                }
            }

            function timeTextFormat(textDate, minDate, maxDate) {
                return textDate === null ? DateTimeService.formatDatesToString(minDate, maxDate) : textDate;
            }

            return {
                startCsvExport: startCsvExport,
                search: search,
                simpleSearch: simpleSearch
            };
        }]
);
})();
