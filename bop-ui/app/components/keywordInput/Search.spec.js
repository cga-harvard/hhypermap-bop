describe( 'SearchDirective', function() {
    var $scope, scope, rootScope, HeatMapSourceGeneratorService, MapService, element, compiledElement, searchFilter;

    beforeEach( module( 'SolrHeatmapApp' ) );

    beforeEach( inject( function($compile, $controller, $rootScope, _HeatMapSourceGenerator_, _Map_, _searchFilter_) {
        rootScope = $rootScope;
        $scope = $rootScope.$new();

        element = angular.element('<keyword-input number-keywords="5" text="text" limit="textLimit"' +
                    'listen-keyword-event="setSuggestWords">' +
                '</keyword-input>');
        compiledElement = $compile(element)($scope);
        $scope.$digest();
        scope = compiledElement.isolateScope();

        HeatMapSourceGeneratorService = _HeatMapSourceGenerator_;
        MapService = _Map_;
        searchFilter = _searchFilter_;
    }));
    it( 'searchInput is empty string', function() {
        expect(scope.filter.text).toEqual(null);
    });
    describe('#doSearch', function() {
        var searchSpy;
        beforeEach(function() {
            searchSpy = spyOn(HeatMapSourceGeneratorService, 'search');
        });
        describe('calls search on HeatMapSourceGeneratorService', function() {
            it('once', function() {
                scope.doSearch();
                expect(searchSpy).toHaveBeenCalledTimes(1);
            });
            it('with searchInput and clean textSearchInput', function() {
                scope.filter[scope.text] = 'San Diego';
                scope.textSearchInput.value = '';
                scope.doSearch();
                expect(scope.filter[scope.text]).toEqual('San Diego');
            });
            it('with a previous value in scope.filter.text and new value in the input', function() {
                scope.textSearchInput.value = 'california';
                scope.filter.text = 'San Diego';
                scope.doSearch();
                expect(scope.filter[scope.text]).toEqual('"San Diego" "california"');
            });
        });
    });
    describe('#watch filter.text', function() {
        it('watch filter.text to change keyWordStringToArray', function() {
            scope.filter.text = 'San Diego';
            scope.$digest();
            expect(scope.filterArray).toEqual(['San Diego']);
        });
        it('watch composite filter.text to change keyWordStringToArray', function() {
            scope.filter.text = '"San Diego" "houston" "San Francisco"';
            scope.$digest();
            expect(scope.filterArray).toEqual(['San Diego', 'houston', 'San Francisco']);
        });
    });

    describe('#onKeyPress', function() {
        it('search on enter key pressed', function() {
            var searchSpy = spyOn(scope, 'doSearch');
            scope.onKeyPress({which: 13});
            expect(searchSpy).toHaveBeenCalledTimes(1);
        });
        it('does not search on all other keys', function() {
            var searchSpy = spyOn(scope, 'doSearch');
            scope.onKeyPress({which: 14});
            expect(searchSpy).not.toHaveBeenCalledTimes(1);
        });
    });

    describe('#toggleSuggestKeywords', function() {
        var searchSpy;
        beforeEach(function() {
            searchSpy = spyOn(HeatMapSourceGeneratorService, 'search');
        });
        it('call search', function() {
            scope.toggleSuggestKeywords();
            expect(searchSpy).toHaveBeenCalledTimes(1);
        });
        it('set the value of the timeLimit', function() {
            scope.tagSwitch.value = true;
            scope.toggleSuggestKeywords();
            expect(searchFilter.textLimit).toEqual(5);
        });
    });

    describe('#removeKeyWord', function() {
        var searchSpy;
        beforeEach(function() {
            scope.filterArray = ['test1', 'test2', 'test3'];
            searchSpy = spyOn(HeatMapSourceGeneratorService, 'search');
        });
        it('remove keyword on backspace key pressed', function() {
            scope.onKeyPress({which: 8});
            expect(scope.filter[scope.text]).toEqual('"test1" "test2"');
            expect(searchSpy).toHaveBeenCalled();
        });
        it('remove keyword from tag in input', function() {
            scope.removeKeyWord('test2');
            expect(scope.filter[scope.text]).toEqual('"test1" "test3"');
            expect(searchSpy).toHaveBeenCalled();
        });
    });

    describe('#listenSuggestWords', function() {
        it('Should populate with suggest key word', function() {
            var dataRawKeywords = [
                {value: 'test1', count: 300},
                {value: 'te2', count: 200},
                {value: 't3', count: 100}
            ];
            rootScope.$broadcast('setSuggestWords', dataRawKeywords);
            expect(scope.suggestedKeywords).toEqual([
                {value: 'test1', count: 300},
                {value: 'te2', count: 200}
            ]);
        });
    });
});
