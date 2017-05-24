describe( 'GeospatialFilterDirective', function() {
    var $scope, scope, rootScope, Map, HeatMapSourceGenerator, element, compiledElement;

    beforeEach( module( 'SolrHeatmapApp' ) );
    beforeEach( module( 'search_geospatialFilter_component' ) );

    beforeEach( inject( function($compile, $controller, $rootScope, _Map_, _HeatMapSourceGenerator_) {
        rootScope = $rootScope;
        $scope = $rootScope.$new();
        element = angular.element('<geospatial-filter></geospatial-filter>');
        compiledElement = $compile(element)($scope);
        $scope.$digest();
        scope = compiledElement.isolateScope();
        Map = _Map_;
        HeatMapSourceGenerator = _HeatMapSourceGenerator_;
    }));
    it( 'filterString has default', function() {
        expect(scope.filter.geo).toEqual('[-90,-180 TO 90,180]');
    });
    describe('#updateFilterString', function() {
        it('updates the string', function() {
            scope.updateFilterString('[1,1 TO 1,1]');
            expect(scope.filter.geo).toEqual('[1,1 TO 1,1]');
        });
    });
    describe('#search', function() {
        var mapSpy, heatMapSourceGeneratorSpy;
        beforeEach(function() {
            mapSpy = spyOn(Map, 'updateTransformationLayerFromQueryForMap');
            heatMapSourceGeneratorSpy = spyOn(HeatMapSourceGenerator, 'search');
        });
        it('updates TransformationLayer', function() {
            scope.filter.geo = '[1,1 TO 1,1]';
            scope.search();
            expect(mapSpy).toHaveBeenCalledTimes(1);
            expect(mapSpy).toHaveBeenCalledWith('[1,1 TO 1,1]');
        });
        it('searches with current state', function() {
            scope.search();
            expect(heatMapSourceGeneratorSpy).toHaveBeenCalledTimes(1);
        });
    });
});
