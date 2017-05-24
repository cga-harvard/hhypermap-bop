/*eslint angular/di: [2,"array"]*/
/*eslint max-len: [2,100]*/

(function() {
    angular.module('SolrHeatmapApp')
    .factory('HeightModule', ['$window', function($window){

        var service = {
            itemHeight: 90,
            otherHeights: otherHeights,
            sideBarWidth:sideBarWidth,
            rightSideBarWidth: 4,
            bottomHeight: 4,
            topPanelHeight: topPanelHeight,
            documentHeight: documentHeight,
            availableHeight: availableHeight,
            getNumberofItems: calculateNumberofItems
        };

        function documentHeight() {
            var D = document;
            return Math.max(D.body.offsetHeight, D.documentElement.offsetHeight,
                D.body.clientHeight, D.documentElement.clientHeight);
        }

        function topPanelHeight() {
            if ($window.innerWidth < 1200) {
                return 4;
            }
            return 102;
        }

        function otherHeights() {
            if ($window.innerWidth < 1200) {
                return 560;
            }
            return 350;
        }

        function sideBarWidth() {
            if ($window.innerWidth < 800) {
                return 4;
            }
            return 400;
        }

        function availableHeight() {
            return documentHeight() - service.otherHeights();
        }

        function calculateNumberofItems() {
            var height = availableHeight();
            if (height > 0) {
                return Math.round(height / service.itemHeight);
            }
            return 10;
        }

        return service;
    }]);
})();
