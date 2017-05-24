describe( 'Normalize', function() {
    var subject;

    beforeEach( module( 'SolrHeatmapApp' ) );

    beforeEach( inject( function( _Normalize_) {
        subject = _Normalize_;
    }));

    describe('#normnalizeExtent', function() {
        describe('returns as-is', function(){
            it('if valid world', function() {
                expect(subject.normalizeExtent([-180, -90, 180, 90])).toEqual([-180, -90, 180, 90]);
            });
            it('if valid extent in world in', function() {
                expect(subject.normalizeExtent([-160, -70, 150, 70])).toEqual([-160, -70, 150, 70]);
            });
        });
        describe('shifted ', function(){
            describe('one degree', function(){
                it('westwards, returns one-true world', function() {
                    expect(subject.normalizeExtent([-181, -90, 179, 90])).toEqual([-180, -90, 180, 90]);
                });
                it('eastwards, returns one-true world', function() {
                    expect(subject.normalizeExtent([-179, -90, 181, 90])).toEqual([-180, -90, 180, 90]);
                });
            });
            it('more than one world westwards', function(){
                expect(subject.normalizeExtent([-720, -90, -360, 90])).toEqual([-180, -90, 180, 90]);
            });
            it('to the south', function() {
                expect(subject.normalizeExtent([-180, -91, 180, 89])).toEqual([-180, -90, 180, 90]);
            });
        });
        describe('multiple worlds', function(){
            it('returns one-true world', function() {
                expect(subject.normalizeExtent([-360, -90, 180, 90])).toEqual([-180, -90, 180, 90]);
            });
            it('returns one-true world', function() {
                expect(subject.normalizeExtent([-360, -180, 180, 90])).toEqual([-180, -90, 180, 90]);
            });
        });
    });
});
/*
            it('one degree westwards, returns one-true world', function() {
                expect(subject.normalizeExtent()).toEqual();
            });
*/
