/*eslint angular/controller-as: 0*/
/**
 * InfoWindowController
 */
(function() {
    angular
    .module('SolrHeatmapApp')
    .controller('InfoWindowController',
        function ($scope, $uibModalInstance, infoMsg, toolName) {

            $scope.infoMsg = infoMsg;
            $scope.toolName = toolName;

            $scope.ok = function () {
                $uibModalInstance.close();
            };
        });
})();
