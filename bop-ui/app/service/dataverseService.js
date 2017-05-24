
/*eslint angular/di: [2,"array"]*/
(function () {
    angular
    .module('SolrHeatmapApp')
    .factory('dataverseService', ['$rootScope', '$http',
        function ($rootScope, $http) {
            var dataverse = {
                AllowDataverseDeposit: false
            };

            var mapReadyEvent = $rootScope.$on('mapReady', function(even, _) {
                dataverse = solrHeatmapApp.bopwsConfig.dataverse;
            });

            function prepareDataverseUrl() {
                var dv = dataverse;
                if (dv.AllowDataverseDeposit) {
                    var urlArray = [dv.dataverseDepositUrl, dv.subsetRetrievalUrl,
                        paramsToString(dv.parameters)];
                    return urlArray.join('?');
                }
                return false;
            }

            function paramsToString(params) {
                var stringParams = [];
                for (var key in params) {
                    stringParams.push(key + '=' + params[key]);
                }
                return stringParams.join('&');
            }

            function dataverseRequest(callback) {
                var config = {
                    url: prepareDataverseUrl(),
                    method: 'GET'
                };
                $http(config).then(function(response) {
                    return callback(response);
                }, function errorCallback(response) {
                    return callback(response);
                });
            }

            return {
                getDataverse: function () {
                    return dataverse;
                },
                prepareDataverseUrl: prepareDataverseUrl,
                dataverseRequest: dataverseRequest
            };
        }]);
})();
