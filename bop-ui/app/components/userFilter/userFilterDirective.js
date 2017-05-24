/*eslint angular/controller-as: 0*/
/*eslint angular/di: [2,"array"]*/
/*eslint max-len: [2,100]*/
/**
 * Filter by user directive
 */
(function() {
    angular
    .module('search_userFilter_component', [])
    .directive('userFilter', [ function() {
        return {
            restrict: 'EA',
            template: '<keyword-input number-keywords="5" text="user" limit="userLimit"' +
                        'listen-keyword-event="setUserSuggestWords" placeholder="@user">' +
                    '</keyword-input>',
            scope: {}
        };
    }]);
})();
