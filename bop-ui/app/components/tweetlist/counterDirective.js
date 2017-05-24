/*eslint angular/di: [2,"array"]*/
(function() {
    angular
    .module('search_tweetcounter_component', [])
    .directive('tweetCounter', [
        function tweetCounter() {
            return {
                link: tweetCounterLink,
                restrict: 'EA',
                templateUrl: 'components/tweetlist/counter.tpl.html',
                scope: {}
            };

            function tweetCounterLink(scope) {
                scope.$on('setCounter', function(e, data){
                    if (data < 1 || !data) {
                        data = 'No results found';
                    }
                    scope.counter = data;
                });

            }
        }]);

})();
