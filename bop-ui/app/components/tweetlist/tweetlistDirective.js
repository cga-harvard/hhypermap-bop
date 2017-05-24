/*eslint angular/di: [2,"array"]*/
(function() {
    angular
    .module('search_tweetlist_component', [])
    .directive('tweetlist', ['Map', 'HeightModule', 'PanelInformationService',
        function tweetlist(Map, HeightModule, PanelInformationService) {
            var MapService = Map;
            return {
                link: tweetlistLink,
                restrict: 'EA',
                templateUrl: 'components/tweetlist/tweetlist.tpl.html',
                scope: {}
            };

            function tweetlistLink(scope) {
                var vm = scope;
                vm.tweetList = [];
                vm.tweetList.exist = false;

                vm.selectTweet = selectTweet;
                vm.removeAllfeatures = removeAllfeatures;

                vm.availableHeight = HeightModule.availableHeight();

                vm.sendToTweetStatus = PanelInformationService.tweetStatusUrl;

                vm.$on('setTweetList', setTweetList);

                var stylePoint = new ol.style.Style({
                    image: new ol.style.Circle({
                        radius: 10,
                        fill: new ol.style.Fill({color: 'rgba(0,0,255,0.2)'}),
                        stroke: new ol.style.Stroke({color: 'rgba(0,0,255,0.9)', width: 2})
                    })
                });

                function setTweetList(event, tweetList) {
                    vm.tweetList = tweetList ? tweetList : [];
                    vm.tweetList.exist = true;
                    vm.availableHeight = HeightModule.availableHeight();
                }

                function selectTweet(tweet) {
                    PanelInformationService.selectedTweet = tweet;
                    addCircle(tweet.coord);
                }

                function removeAllfeatures() {
                    PanelInformationService.selectedTweet = {};
                    MapService.removeAllfeatures();
                }

                function addCircle(coordinates) {
                    var coordArray;
                    if (!angular.isString(coordinates)) {
                        return;
                    }
                    if (coordinates.includes(',')) {
                        coordArray = coordinates.split(',').map(function(val) {
                            return Number(val);
                        });
                        MapService.addCircle([coordArray[1], coordArray[0]], stylePoint);
                    }
                }


            }
        }]);

})();
