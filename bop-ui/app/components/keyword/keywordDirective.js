(function() {
    angular
    .module('search_keyword_component', [])
    .directive('keyword', function keyword() {
        return {
            scope: {
                tag: '@',
                remove: '@',
                removefn: '&'
            },
            link: keywordLink,
            templateUrl: 'components/keyword/keyword.tpl.html'
        };

        function keywordLink(scope) {
            var vm = scope;
            vm.removeTag = vm.remove === 'true' ? true : false;
        }
    });
})();
