describe( 'searchFilter', function() {
    var subject, MapService;

    beforeEach( module( 'SolrHeatmapApp' ) );

    beforeEach( inject( function( _searchFilter_, _Map_) {
        subject = _searchFilter_;
        MapService = _Map_;
    }));

    it('has geo default filter', function() {
        expect(subject.geo).toEqual('[-90,-180 TO 90,180]');
    });
    it('has default text filter', function() {
        expect(subject.text).toEqual(null);
    });
    it('has default user filter', function() {
        expect(subject.user).toEqual(null);
    });
    describe('#setFilter', function() {
        it('sets the user', function() {
            subject.setFilter({user: 'Diego'});
            expect(subject.user).toEqual('Diego');
        });
        it('sets the text', function() {
            subject.setFilter({text: 'San Diego'});
            expect(subject.text).toEqual('San Diego');
        });
        it('sets the geo', function() {
            subject.setFilter({geo: '[2,1 TO 2,1]'});
            expect(subject.geo).toEqual('[2,1 TO 2,1]');
        });
    });
    describe('#resetFilter', function() {
        beforeEach(function() {
            spyOn(MapService, 'getCurrentExtentQuery').and.returnValue({ geo:['[-90,-180 TO 90,180]'] });
        });
        it('resets the user', function() {
            subject.setFilter({user: 'Diego'});
            subject.resetFilter();
            expect(subject.user).toEqual(null);
        });
        it('resets the text', function() {
            subject.setFilter({text: 'San Diego'});
            subject.resetFilter();
            expect(subject.text).toEqual(null);
        });
        it('resets the geo', function() {
            subject.setFilter({geo: '[2,1 TO 2,1]'});
            subject.resetFilter();
            expect(subject.geo).toEqual(['[-90,-180 TO 90,180]']);
        });
    });
});
