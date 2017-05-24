describe( 'HeatMapSourceGenerator', function() {
    var subject, NormalizeService, olSpy, mapSpy, viewSpy, mapViewSpy, olgmSpy,
        olgmObjSpy, defaultConfig, defaultViewConfig, layer, HeightModule, $window;

    beforeEach( module( 'SolrHeatmapApp' ) );

    beforeEach( inject( function( _Map_, _Normalize_, _HeightModule_, _$window_) {
        subject = _Map_;
        NormalizeService = _Normalize_;
        HeightModule = _HeightModule_;
        $window = _$window_;
        mapViewSpy = jasmine.createSpyObj('view', ['set', 'fit']);
        mapSpy = jasmine.createSpyObj('map', ['getView', 'addLayer', 'addInteraction', 'addOverlay', 'on']);
        mapSpy.getView.and.returnValue(mapViewSpy);
        olSpy = spyOn(ol, 'Map').and.returnValue(mapSpy);
        viewSpy = spyOn(ol, 'View');
        olgmObjSpy = jasmine.createSpyObj('olGM', ['activate']);
        olgmSpy = spyOn(olgm, 'OLGoogleMaps').and.returnValue(olgmObjSpy);

        defaultConfig = { mapConfig: { view: {}}};
        defaultViewConfig = { center: [0, 0 ], maxZoom: undefined, minZoom: undefined, projection: 'EPSG:3857', resolution: undefined, resolutions: undefined, rotation: undefined, zoom: 2, zoomFactor: undefined };
    }));

    describe('#init', function() {
        it('creates an OL map', function() {
            subject.init(defaultConfig);
            expect(olSpy).toHaveBeenCalled();
        });
        it('has a default config', function() {
            subject.init(defaultConfig);
            expect(viewSpy).toHaveBeenCalledWith(defaultViewConfig);
        });
        describe('can change settings', function() {
            it('change zoom', function() {
                defaultConfig = { mapConfig: { view: { zoom: 3}}};
                defaultViewConfig.zoom = 3;
                subject.init(defaultConfig);
                expect(viewSpy).toHaveBeenCalledWith(defaultViewConfig);
            });
            it('change resoltion', function() {
                defaultConfig = { mapConfig: { view: { resolution: '10'}}};
                defaultViewConfig.resolution = '10';
                subject.init(defaultConfig);
                expect(viewSpy).toHaveBeenCalledWith(defaultViewConfig);
            });
            it('change rotation', function() {
                defaultConfig = { mapConfig: { view: { rotation: 1}}};
                defaultViewConfig.rotation = 1;
                subject.init(defaultConfig);
                expect(viewSpy).toHaveBeenCalledWith(defaultViewConfig);
            });
            it('change zoomFactor', function() {
                defaultConfig = { mapConfig: { view: { zoomFactor: 1}}};
                defaultViewConfig.zoomFactor = 1;
                subject.init(defaultConfig);
                expect(viewSpy).toHaveBeenCalledWith(defaultViewConfig);
            });
            it('change minZoom', function() {
                defaultConfig = { mapConfig: { view: { minZoom: 1}}};
                defaultViewConfig.minZoom = 1;
                subject.init(defaultConfig);
                expect(viewSpy).toHaveBeenCalledWith(defaultViewConfig);
            });
            it('change maxZoom', function() {
                defaultConfig = { mapConfig: { view: { maxZoom: 1}}};
                defaultViewConfig.maxZoom = 1;
                subject.init(defaultConfig);
                expect(viewSpy).toHaveBeenCalledWith(defaultViewConfig);
            });
            it('change resolutions', function() {
                defaultConfig = { mapConfig: { view: { resolutions: [0,0]}}};
                defaultViewConfig.resolutions = [0,0];
                subject.init(defaultConfig);
                expect(viewSpy).toHaveBeenCalledWith(defaultViewConfig);
            });
        });
        it('can change the view extent', function() {
            layer = jasmine.createSpyObj('layer', ['addFilter']);
            spyOn(subject, 'getLayersBy').and.returnValue([layer]);
            spyOn(subject, 'getMap').and.returnValue(mapSpy);
            spyOn(subject, 'getMapProjection').and.returnValue('EPSG:4326');
            spyOn(subject, 'getMapSize').and.returnValue([100,50]);
            defaultConfig = { mapConfig: { view: { extent: [0,0], initExtent: [0, 0, 0, 0] } } };
            subject.init(defaultConfig);
            expect(mapSpy.getView).toHaveBeenCalled();
            expect(mapViewSpy.set).toHaveBeenCalled();
            expect(mapViewSpy.fit).toHaveBeenCalled();
        });
        describe('broken config', function() {
            it('sets renderer to undefined', function() {
                defaultConfig = { mapConfig: { view: '' , renderer: 1}};
                subject.init(defaultConfig);
                expect(olSpy).toHaveBeenCalled();
            });
        });
    });
    describe('#getMapView', function() {
        var getViewSpy;
        beforeEach(function() {
            getViewSpy = jasmine.createSpyObj('map', ['getView']);
            spyOn(subject, 'getMap').and.returnValue(getViewSpy);
        });
        it('calls the spy', function() {
            subject.getMapView();
            expect(getViewSpy.getView).toHaveBeenCalled();
        });
    });
    describe('#getMapSize', function() {
        var getMapSpy;
        beforeEach(function() {
            getMapSpy = jasmine.createSpyObj('map', ['getSize']);
            spyOn(subject, 'getMap').and.returnValue(getMapSpy);
        });
        it('calls the spy', function() {
            subject.getMapSize();
            expect(getMapSpy.getSize).toHaveBeenCalled();
        });
    });
    describe('#getMapProjection', function() {
        var getMapSpy;
        beforeEach(function() {
            getMapSpy = jasmine.createSpyObj('map', ['getCode']);
            var mapView = { getProjection: function() { return getMapSpy; } };
            spyOn(subject, 'getMapView').and.returnValue(mapView);
        });
        it('calls the spy', function() {
            subject.getMapProjection();
            expect(getMapSpy.getCode).toHaveBeenCalled();
        });
    });
    describe('#getMapLayers', function() {
        var getMapSpy;
        beforeEach(function() {
            getMapSpy = jasmine.createSpyObj('map', ['getArray']);
            var mapView = { getLayers: function() { return getMapSpy; } };
            spyOn(subject, 'getMap').and.returnValue(mapView);
        });
        it('calls the spy', function() {
            subject.getLayers();
            expect(getMapSpy.getArray).toHaveBeenCalled();
        });
    });
    describe('#getInteractions', function() {
        var getMapSpy;
        beforeEach(function() {
            getMapSpy = jasmine.createSpyObj('map', ['getArray']);
            var mapView = { getInteractions: function() { return getMapSpy; } };
            spyOn(subject, 'getMap').and.returnValue(mapView);
        });
        it('calls the spy', function() {
            subject.getInteractions();
            expect(getMapSpy.getArray).toHaveBeenCalled();
        });
    });
    describe('#getMapZoom', function() {
        var getMapSpy;
        beforeEach(function() {
            getMapSpy = jasmine.createSpyObj('map', ['getZoom']);
            spyOn(subject, 'getMapView').and.returnValue(getMapSpy);
        });
        it('calls the spy', function() {
            subject.getMapZoom();
            expect(getMapSpy.getZoom).toHaveBeenCalled();
        });
    });
    describe('#getLayersBy', function() {
        beforeEach(function() {
            layer = { get: function(key) { return 'SD'; }};
            spyOn(subject, 'getLayers').and.returnValue([layer]);
        });
        it('returns layer for key value SD', function() {
            expect(subject.getLayersBy('key', 'SD')).toEqual([layer]);
        });
        it('returns empty array for key value SF', function() {
            expect(subject.getLayersBy('key', 'SF')).toEqual([]);
        });
    });
    describe('#getInteractionByClass', function() {
        function SD() {}
        function SF() {}
        beforeEach(function() {
            spyOn(subject, 'getInteractions').and.returnValue([new SD()]);
        });
        it('returns interaction for class ', function() {
            expect(subject.getInteractionsByClass(SD)).toEqual([new SD]);
        });
        it('returns interaction for class ', function() {
            expect(subject.getInteractionsByClass(SF)).toEqual([]);
        });
    });
    describe('#getInteractionByType', function() {
        var interaction;
        beforeEach(function() {
            interaction = { type_: 'SD' };
        });
        it('returns interaction for class ', function() {
            expect(subject.getInteractionsByType([interaction], 'SD')).toEqual([interaction]);
        });
        it('returns interaction for class ', function() {
            expect(subject.getInteractionsByType([interaction], 'SF')).toEqual([]);
        });
    });
    describe('#getCurrentExtent', function() {
        var extent, mapZoomSpy, layerSpy, view;
        beforeEach(function() {
            layer = { getSource: function() { return { getFeatures: function() { return [{getGeometry: function() { return { getExtent: function() { return [0,0,0,0];}};}}];}}; }};
            view = { calculateExtent: function(size) { return [0, 0, 0, 0]; }};
            spyOn(subject, 'getMapView').and.returnValue(view);
            mapZoomSpy = spyOn(subject, 'getMapZoom').and.returnValue(10);
            spyOn(subject, 'getMapSize').and.returnValue(10);
            layerSpy = spyOn(subject, 'getLayersBy').and.returnValue([layer]);
            spyOn(subject, 'getMapProjection').and.returnValue('EPSG:4326');
        });
        it('returns extent', function() {
            expect(subject.getCurrentExtent()).toEqual({hm: {minX: 0, maxX: 0, minY: 0, maxY: 0}, geo: {minX: 0, maxX: 0, minY: 0, maxY: 0}});
        });
        describe('zoom <= 1', function(){
            beforeEach(function() {
                mapZoomSpy.and.returnValue(1);
            });
            it('returns extent', function() {
                expect(subject.getCurrentExtent()).toEqual({
                    hm: {minY: -90, maxY: 90, minX: -180, maxX: 180},
                    geo: {minX: 0, maxX: 0, minY: 0, maxY: 0}
                });
            });
        });
        describe('no TransformInteractionLayer', function(){
            beforeEach(function() {
                layerSpy.and.returnValue([]);
            });
            it('returns null', function() {
                expect(subject.getCurrentExtent()).toEqual(null);
            });
        });
    });
    describe('#createOrUpdateHeatMapLayer', function() {
        var data, layerSpy, setSourceSpy, setRadiusSpy;
        beforeEach(function() {
            var clearSourceSpy = jasmine.createSpyObj('getSource', ['clear']);
            layer = { getFilters: function() { return [{ setActive: function() {}}]; }, setSource: function() {}, setRadius: function() {}, getSource: function() { return { clear: function() {}, getFeatures: function() { return [{getGeometry: function() { return { getExtent: function() { return [0,0,0,0];}};}}];}}; }};
            setSourceSpy = spyOn(layer, 'setSource');
            setRadiusSpy = spyOn(layer, 'setRadius');
            data = { columns: 4, rows: 4, gridLevel: 2, maxX: 1, maxY: 1, minY: 0, minX: 0,
                    projection: 'EPSG:4326', counts_ints2D: [[0,0,0,0],[0,0,0,0],[0,0,0,0],[0,0,0,0]]};
        });
        describe('heatmap layer does already exist', function() {
            beforeEach(function() {
                layerSpy = spyOn(subject, 'getLayersBy').and.returnValue([layer]);
            });
            it('sets the source with the new Heatmap', function() {
                subject.createOrUpdateHeatMapLayer(data);
                expect(setSourceSpy).toHaveBeenCalled();
            });
        });
        describe('heatmap layer does not exist', function() {
            var strippedLayer;
            beforeEach(function() {
                var called = 0;
                strippedLayer = { getFilters: function() { return [{ setActive: function() {}}]; }, setSource: function() {}, getSource: function() { return { clear: function() {}, getFeatures: function() { return [];}}; }};
                layerSpy = spyOn(subject, 'getLayersBy').and.callFake(function(key, value) {
                    if(value === 'HeatMapLayer') {
                        if(called > 0) {
                            return [strippedLayer];
                        }
                        called++;
                        return [];
                    }
                    return [strippedLayer];
                });
                mapSpy = jasmine.createSpyObj('getMap', ['addLayer']);
                spyOn(subject, 'getMap').and.returnValue(mapSpy);
                spyOn(ol.filter.Mask.prototype, 'setActive');
            });
            it('sets the source with the new Heatmap', function() {
                subject.createOrUpdateHeatMapLayer(data);
                expect(mapSpy.addLayer).toHaveBeenCalled();
            });
        });
    });
    describe('#resetMap', function() {
        var getViewSpy;
        beforeEach(function() {
            layer = { getSource: function() { return { clear: function() {}, getFeatures: function() { return [{setGeometry: function() { }}];}}; }};
            getViewSpy = jasmine.createSpyObj('map', ['setCenter', 'setZoom']);
            spyOn(subject, 'getMapView').and.returnValue(getViewSpy);
            spyOn(subject, 'getInteractions').and.returnValue([]);
            spyOn(subject, 'getLayersBy').and.returnValue([layer]);
            spyOn(subject, 'checkBoxOfTransformInteraction');
        });
        describe('no initialZoom or center is defined',function() {
            beforeEach(function() {
                solrHeatmapApp.initMapConf = defaultConfig.mapConfig;
            });
            it('does not call the spy', function() {
                subject.resetMap();
                expect(getViewSpy.setZoom).not.toHaveBeenCalled();
            });
        });
        describe('initialZoom and center is defined',function() {
            beforeEach(function() {
                solrHeatmapApp.initMapConf = { view: { zoom: 1, center: [0,0], extent: [0,0,0,0] }};
            });
            it('does not call the spy', function() {
                subject.resetMap();
                expect(getViewSpy.setZoom).toHaveBeenCalled();
            });
        });
    });
    describe('#resetMap', function() {
        var getView, setGeometrySpy;
        beforeEach(function() {
            var feature = { getGeometry: function() {return {getCoordinates: function() { return [0,0]; }};}, setGeometry: function() {}};
            layer = { getSource: function() { return { getFeatures: function() { return [feature];}}; }};
            getView = { calculateExtent: function (extent) {return [-20037508.34,-20037508.34,20037508.34,20037508.34];}};
            setGeometrySpy = spyOn(feature, 'setGeometry');
            spyOn(subject, 'getLayersBy').and.returnValue([layer]);
            spyOn(subject, 'getMapView').and.returnValue(getView);
            spyOn(subject, 'getMapSize').and.returnValue([10,10]);
            spyOn(subject, 'getInteractions').and.returnValue([]);
        });
        it('does call the spy', function() {
            subject.checkBoxOfTransformInteraction();
            expect(setGeometrySpy).toHaveBeenCalled();
        });
    });
    describe('#calculateReducedBoundingBox', function() {
        beforeEach(function() {
            solrHeatmapApp.appConfig.ratioInnerBbox = 2;
            spyOn(HeightModule, 'documentHeight').and.returnValue(400);
            spyOn(HeightModule, 'topPanelHeight').and.returnValue(200);
            spyOn(HeightModule, 'sideBarWidth').and.returnValue(400);
            HeightModule.rightSideBarWidth = 200;
            HeightModule.bottomHeight = 200;
            $window.innerWidth = 800;
        });
        it('returns boundingbox', function() {
            expect(subject.calculateReducedBoundingBoxFromInFullScreen({minX: 0, minY: 2, maxX: 1, maxY: 3})).toEqual({minX: 0.5, minY: 2.25, maxX: 0.75, maxY: 2.5});
        });
        describe('#reducedQueryForExtent', function() {
            it('returns extent query', function() {
                expect(subject.getReducedQueryFromExtent('[0,2 TO 1,3]')).toEqual('[2.25,0.5 TO 2.5,0.75]');
            });
        });
    });
    describe('#getCurrentExtentQuery', function() {
        var view, layerSpy, mapZoomSpy;
        beforeEach(function() {
            layer = { getSource: function() {return {getFeatures: function() {return [{getGeometry: function() {return {getExtent: function() {return [0,0,0,0];}};}}];}}; }};
            view = { calculateExtent: function(size) { return [0,0, 0, 0]; }};
            spyOn(subject, 'getMapView').and.returnValue(view);
            mapZoomSpy = spyOn(subject, 'getMapZoom').and.returnValue(10);
            spyOn(subject, 'getMapSize').and.returnValue(10);
            layerSpy = spyOn(subject, 'getLayersBy').and.returnValue([layer]);
            spyOn(subject, 'getMapProjection').and.returnValue('EPSG:4326');
        });
        it('returns extent query', function() {
            expect(subject.getCurrentExtentQuery()).toEqual({ geo: '[0,0 TO 0,0]', hm: '[0,0 TO 0,0]' });
        });
    });
    describe('#updateTransformationLayerFromQueryForMap', function() {
        it('sets new extent', function() {
            layer = { getSource: function() { return { clear: function() {}, getFeatures: function() { return [{setGeometry: function() { }}];}}; }};
            spyOn(subject, 'getMapProjection').and.returnValue('EPSG:4326');
            spyOn(subject, 'getInteractions').and.returnValue([]);
            var layerSpy = spyOn(subject, 'getLayersBy').and.returnValue([layer]);
            subject.updateTransformationLayerFromQueryForMap('[0,2 TO 1,3]', 'EPSG:3857');
        });
    });
});
