/*eslint angular/controller-as: 0*/
/*eslint angular/di: [2,"array"]*/
/*eslint max-len: [2,90]*/
/**
 * Export Directive
 */
(function() {
    angular
    .module('search_dataverseButton_component', [])
    .directive('dataverseButton', ['HeatMapSourceGenerator', 'dataverseService',
        function(HeatMapSourceGenerator, dataverseService) {
            return {
                link: Link,
                restrict: 'EA',
                templateUrl: 'components/dataverseButton/dataverseButton.tpl.html',
                scope: {}
            };

            function Link(scope) {
                var vm = scope;
                vm.sendToDataverse = sendToDataverse;
                vm.dataversefn = dataverseService.getDataverse;
                vm.callbackMessage = '';

                function sendToDataverse() {
                    dataverseService.dataverseRequest(function (response) {
                        vm.response = response;
                        if (response.status === 200) {
                            vm.callbackMessage = 'Dataverse success response';
                        } else {
                            vm.callbackMessage = 'Url error';
                        }
                        angular.element('#dataverseresponse').modal('show');
                    });
                }
            }
        }]);
})();
