(function() {
    angular
    .module('search_cardwrapper_component', [])
    .directive('cardWrapper', function keyword() {
        return {
            scope: {},
            transclude: true,
            template: '<div class="row component-padding">' +
                        '<div class="col-xs-12 row-box">' +
                            '<ng-transclude></ng-transclude>' +
                        '</div>' +
                    '</div>'
        };
    });
})();
