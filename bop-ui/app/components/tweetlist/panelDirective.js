/*eslint angular/di: [2,"array"]*/
(function() {
    angular
    .module('search_tweetpanel_component', [])
    .directive('tweetPanel', ['PanelInformationService',
        function tweetPanel(PanelInformationService) {
            return {
                link: tweetPanelLink,
                restrict: 'EA',
                templateUrl: 'components/tweetlist/tweetPanel.tpl.html',
                scope: {}
            };

            function tweetPanelLink(scope) {
                var vm = scope;
                vm.selectedTweet = PanelInformationService.selectedTweet;
                vm.panelEmpty = true;

                vm.$watch(function(){
                    return PanelInformationService.selectedTweet;
                }, function(newValue, oldValue){
                    vm.selectedTweet = newValue;
                    vm.panelEmpty = IsPanelEmpty();
                });

                function IsPanelEmpty() {
                    if (Object.keys(vm.selectedTweet).length === 0) {
                        return true;
                    }
                    return false;
                }

            }
        }]);

})();
