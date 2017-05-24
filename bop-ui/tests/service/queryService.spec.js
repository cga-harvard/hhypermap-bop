describe( 'queryService', function() {
    var subject, NormalizeService;

    beforeEach( module( 'SolrHeatmapApp' ) );

    beforeEach( inject( function( _queryService_, _Normalize_) {
        subject = _queryService_;
        NormalizeService = _Normalize_;
    }));
    describe('#createQueryFromExtent', function() {
        it('returns query string', function() {
            expect(subject.createQueryFromExtent({minX: 0, minY: 2, maxX: 1, maxY: 3})).toEqual('[2,0 TO 3,1]');
        });
    });
    describe('#getExtentFromQuery', function() {
        it('returns extent object', function() {
            expect(subject.getExtentFromQuery('[0,2 TO 1,3]')).toEqual({minX: 0, minY: 2, maxX: 1, maxY: 3});
        });
    });
    describe('#getExtentForProjectionFromQuery', function() {
        it('returns extent query', function() {
            expect(subject.getExtentForProjectionFromQuery('[0,2 TO 1,3]', 'EPSG:3857')).toEqual([222638.98158654716, -7.081154551613622e-10, 333958.4723798207, 111325.14286638486]);
        });
    });
});
