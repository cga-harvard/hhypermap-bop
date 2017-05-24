describe( 'ExportDirective', function() {
    var $scope, scope, element, rootScope, HeatMapSourceGeneratorService, compiledElement, MapService, searchFilter;

    beforeEach(module('SolrHeatmapApp'));
    beforeEach(module('search_resetButton_component'));

    beforeEach(inject( function($compile, $rootScope, _HeatMapSourceGenerator_, _Map_, _searchFilter_) {
        rootScope = $rootScope;
        $scope = $rootScope.$new();

        element = angular.element('<reset-button></reset-button>');
        compiledElement = $compile(element)($scope);
        $scope.$digest();
        scope = compiledElement.isolateScope();

        HeatMapSourceGeneratorService = _HeatMapSourceGenerator_;
        MapService = _Map_;
        searchFilter = _searchFilter_;
    }));

    describe('#reset', function() {
        var searchSpy, mapSpy, filterSpy;
        beforeEach(function() {
            searchSpy = spyOn(HeatMapSourceGeneratorService, 'search');
            mapSpy = spyOn(MapService, 'resetMap');
            filterSpy = spyOn(searchFilter, 'resetFilter');
        });
        it('calls search on HeatMapSourceGeneratorService once', function() {
            scope.reset();
            expect(searchSpy).toHaveBeenCalledTimes(1);
        });
        it('calls restMap on MapService once', function() {
            scope.reset();
            expect(mapSpy).toHaveBeenCalledTimes(1);
        });
        it('calls resetFilter on searchFilter once', function() {
            scope.reset();
            expect(filterSpy).toHaveBeenCalledTimes(1);
        });
    });
});
