/*eslint angular/di: [2,"array"]*/
/*eslint max-len: [2,100]*/

(function() {
    angular
    .module('search_responsiveLayout_component', [])
    .directive('responsiveLayout', ['$window', function($window) {

        return {
            link: responsiveLayoutLink,
            restrict: 'EA',
            templateUrl: 'components/responsiveView/responsiveView.tpl.html',
            scope: {}
        };

        function responsiveLayoutLink(scope) {
            var vm = scope;

            vm.fullscreen = checkWidth($window.innerWidth);

            vm.$watch(function(){
                return $window.innerWidth;
            }, function(newValue, oldValue){
                vm.fullscreen = checkWidth(newValue);
            });

            function checkWidth(width) {
                if ( width >= 800 && width < 1200 ) {
                    return false;
                }else{
                    return true;
                }
            }
        }
    }]);
})();
