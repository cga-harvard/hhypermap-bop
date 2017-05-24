/*eslint angular/di: [2,"array"]*/
/*eslint angular/document-service: 2*/
/*eslint max-len: [2,100]*/
/**
 * Map service
 */
(function() {
    angular.module('SolrHeatmapApp')
    .factory('Map',
             ['$rootScope', '$filter', '$document', 'Normalize', '$controller',
             'queryService', 'HeightModule', '$window',
        function($rootScope, $filter, $document, Normalize, $controller,
            queryService, HeightModule, $window) {
            var NormalizeService = Normalize;
            var service = {};
            var map = {},
                defaults = {
                    renderer: 'canvas',
                    view: {
                        center: [0 ,0],
                        projection: 'EPSG:3857',
                        zoom: 2
                    }
                },
                rs = $rootScope;

            /**
             *
             */
            function buildMapLayers(layerConfig) {
                var layer,
                    layers = [];

                if (angular.isArray(layerConfig)) {

                    angular.forEach(layerConfig, function(conf) {
                        if (conf.type === 'googleLayer') {
                            service.googleLayer = new olgm.layer.Google({
                                backgroundLayer: conf.visible,
                                mapTypeId: google.maps.MapTypeId.TERRAIN
                            });
                            layer = service.googleLayer;
                        }
                        if (conf.type === 'Toner') {
                            service.tonerLayer = new ol.layer.Tile({
                                source: new ol.source.Stamen({
                                    layer: 'toner-lite'
                                }),
                                backgroundLayer: conf.backgroundLayer,
                                visible: conf.visible
                            });
                            layer = service.tonerLayer;
                        }
                        if (conf.type === 'TileWMS') {
                            layer = new ol.layer.Tile({
                                name: conf.name,
                                backgroundLayer: conf.backgroundLayer,
                                displayInLayerPanel: conf.displayInLayerPanel,
                                source: new ol.source.TileWMS({
                                    attributions: [new ol.Attribution({
                                        html: conf.attribution
                                    })],
                                    crossOrigin: conf.crossOrigin,
                                    logo: conf.logo,
                                    params: conf.params,
                                    ratio: conf.ratio,
                                    resolutions: conf.resoltions,
                                    url: conf.url
                                }),
                                opacity: conf.opacity,
                                visible: conf.visible
                            });
                        }
                        if (conf.type === 'ImageWMS') {
                            layer = new ol.layer.Image({
                                name: conf.name,
                                backgroundLayer: conf.backgroundLayer,
                                displayInLayerPanel: conf.displayInLayerPanel,
                                source: new ol.source.ImageWMS({
                                    attributions: [new ol.Attribution({
                                        html: conf.attribution
                                    })],
                                    crossOrigin: conf.crossOrigin,
                                    logo: conf.logo,
                                    params: conf.params,
                                    resolutions: conf.resoltions,
                                    url: conf.url
                                }),
                                opacity: conf.opacity,
                                visible: conf.visible
                            });
                        }
                        layers.push(layer);
                    });
                }
                return layers;
            }

            /**
            *
            */
            service.getMap = function() {
                return map;
            };

            service.getMapView = function() {
                return service.getMap().getView();
            };

            service.getMapZoom = function() {
                return service.getMapView().getZoom();
            };

            service.getMapSize = function() {
                return service.getMap().getSize();
            };

            service.getMapProjection = function() {
                return service.getMapView().getProjection().getCode();
            };

            service.getLayers = function() {
                return service.getMap().getLayers().getArray();
            };

            service.getInteractions = function () {
                return service.getMap().getInteractions().getArray();
            };

            service.getLayersBy = function(key, value) {
                var layers = service.getLayers();
                return layers.filter(function (layer) {
                    return layer.get(key) === value;
                });
            };

            /**
             *
             */
            service.getInteractionsByClass = function(value) {
                var interactions = service.getInteractions();
                return $filter('filter')(interactions, function(interaction) {
                    return interaction instanceof value;
                });
            };

            /**
             *
             */
            service.getInteractionsByType = function(interactions, type) {
                return $filter('filter')(interactions, function(interaction) {
                    return interaction.type_ === type;
                });
            };

            service.updateTransformationLayerFromQueryForMap = function(query) {
                var extent = queryService.
                    getExtentForProjectionFromQuery(query,
                                                    service.getMapProjection());
                setTransactionBBox(extent);
            };

            /**
            * Helper method to change active mode of masks for backgroundLayer and
            * heatmap layer
            */
            var _switchMasks = function(hmAvailable) {
                var heatMapLayer = service.getLayersBy('name', 'HeatMapLayer')[0];
                var heatMapMask = heatMapLayer.getFilters()[0];
                var backgroundLayer = service.getLayersBy('backgroundLayer', true)[0],
                    backgroundLayerMask = backgroundLayer.getFilters()[0];

                // disable mask of backgroundLayer if heatmap is available and vice versa
                backgroundLayerMask.setActive(!hmAvailable);
                // enable mask of heatMapLayer if heatmap is available and vice versa
                heatMapMask.setActive(hmAvailable);
            };

            function fillNullValueToEmptyArray(heatmap) {
                return heatmap.map(function (row) {
                    if (row === null) {
                        return [];
                    }else{
                        return row;
                    }
                });
            }

            function rescaleHeatmapValue(value, minMaxValue){
                if (value === null){
                    return 0;
                }

                if (value === -1){
                    return -1;
                }

                if (value === 0){
                    return 0;
                }

                if ((minMaxValue[1] - minMaxValue[0]) === 0){
                    return 0;
                }
                var scaledValue = (value - minMaxValue[0]) / (minMaxValue[1] - minMaxValue[0]);

                return scaledValue;
            }

            function getClassifications(hmParams) {
                var flattenCount = [];
                hmParams.counts_ints2D.forEach(function(row) {
                    flattenCount.push.apply(flattenCount, row);
                });
                var series = new geostats(flattenCount);
                var numberOfClassifications = hmParams.gradientArray.length - 5;
                return series.getClassJenks(numberOfClassifications);
            }

            function closestValue(arrayOfValues, value) {
                //it makes sure that nothing above zero is assigned to the zero bin.
                if (value === 0) {
                    return 0;
                }
                var currValue = arrayOfValues[0];
                var currIndex = 1;
                for (var i = 1; i < arrayOfValues.length; i++) {
                    if (Math.abs(value - arrayOfValues[i]) < Math.abs(value - currValue)) {
                        currValue = arrayOfValues[i];
                        currIndex = i;
                    }
                }
                return currIndex;
            }

            /*
             *
             */
            function createHeatMapSource(hmParams) {
                var counts_ints2D = hmParams.counts_ints2D,
                    gridLevel = hmParams.gridLevel,
                    gridColumns = hmParams.columns,
                    gridRows = hmParams.rows,
                    minX = hmParams.minX,
                    minY = hmParams.minY,
                    maxX = hmParams.maxX,
                    maxY = hmParams.maxY,
                    hmProjection = hmParams.projection,
                    dx = maxX - minX,
                    dy = maxY - minY,
                    sx = dx / gridColumns,
                    sy = dy / gridRows,
                    olFeatures = [],
                    minMaxValue,
                    sumOfAllVals = 0,
                    classifications,
                    olVecSrc;

                if (!counts_ints2D) {
                    return null;
                }
                counts_ints2D = fillNullValueToEmptyArray(counts_ints2D);
                classifications = getClassifications(hmParams);
                minMaxValue = [0, classifications.length - 1];
                for (var i = 0 ; i < gridRows ; i++){
                    for (var j = 0 ; j < gridColumns ; j++){
                        var hmVal = counts_ints2D[counts_ints2D.length-i-1][j],
                            lon,
                            lat,
                            feat,
                            coords;

                        if (hmVal && hmVal !== null){
                            lat = minY + i*sy + (0.5 * sy);
                            lon = minX + j*sx + (0.5 * sx);
                            coords = ol.proj.transform(
                              [lon, lat],
                              hmProjection,
                              map.getView().getProjection().getCode()
                            );

                            var classifiedValue = closestValue(classifications, hmVal);
                            var scaledValue = rescaleHeatmapValue(classifiedValue, minMaxValue);

                            feat = new ol.Feature({
                                name: hmVal,
                                scaledValue: scaledValue,
                                geometry: new ol.geom.Point(coords),
                                opacity: 1,
                                weight: 1
                            });

                            feat.set('weight', scaledValue);
                            feat.set('origVal', hmVal);

                            olFeatures.push(feat);
                        }
                    }
                }

                olVecSrc = new ol.source.Vector({
                    features: olFeatures,
                    useSpatialIndex: true
                });
                return olVecSrc;
            }

            function createCircle_() {
                var radius = this.getRadius();
                var blur = this.getBlur();
                var halfSize = radius + blur + 1;
                var size = 2 * halfSize;
                var context = ol.dom.createCanvasContext2D(size, size);
                context.shadowOffsetX = context.shadowOffsetY = this.shadow_;
                context.shadowBlur = blur;
                context.shadowColor = '#000';
                context.beginPath();
                var center = halfSize - this.shadow_;
                context.arc(center, center, radius, 0, Math.PI * 2, true);
                context.fill();
                return context.canvas.toDataURL();
            }

            function displayTooltip(evt, overlay, tooltip) {
                var pixel = evt.pixel;
                var feature = map.forEachFeatureAtPixel(pixel, function(feat) {
                    return feat;
                });

                var name = feature ? feature.get('name') : undefined;
                tooltip.style.display = name ? '' : 'none';
                if (name) {
                    overlay.setPosition(evt.coordinate);
                    tooltip.innerHTML = name;
                }
            }

            service.createOrUpdateHeatMapLayer = function(hmData) {
                var existingHeatMapLayers, transformInteractionLayer, olVecSrc, newHeatMapLayer;

                hmData.heatmapRadius = 20;
                hmData.blur = 6;
                hmData.gradientArray = ['#000000', '#0000df', '#0000df', '#00effe',
                    '#00effe', '#00ff42',' #00ff42', '#00ff42',
                    '#feec30', '#ff5f00', '#ff0000'];

                existingHeatMapLayers = service.getLayersBy('name', 'HeatMapLayer');
                transformInteractionLayer = service.getLayersBy('name',
                                                                "TransformInteractionLayer")[0];
                olVecSrc = createHeatMapSource(hmData);

                if (existingHeatMapLayers && existingHeatMapLayers.length > 0){
                    var currHeatmapLayer = existingHeatMapLayers[0];
                    // Update layer source
                    var layerSrc = currHeatmapLayer.getSource();
                    if (layerSrc){
                        layerSrc.clear();
                    }
                    currHeatmapLayer.setSource(olVecSrc);
                    // currHeatmapLayer.setRadius(hmData.heatmapRadius);
                } else {
                    newHeatMapLayer = new ol.layer.Heatmap({
                        name: 'HeatMapLayer',
                        source: olVecSrc,
                        radius: hmData.heatmapRadius,
                        blur: hmData.blur,
                        gradient: hmData.gradientArray
                    });

                    try {
                        service.getMap().addLayer(newHeatMapLayer);
                    } catch(err) {
                        void 0;
                    }

                }
            };

            /**
             * This method adds a transfrom interaction to the mapand a mask to background layer
             * The area outer the feature which can be modified by the transfrom interaction
             * will have a white shadow
             */
            function generateMaskAndAssociatedInteraction(bboxFeature, fromSrs) {
                var polygon = new ol.Feature(ol.geom.Polygon.fromExtent(bboxFeature)),
                    backGroundLayer = service.getLayersBy('backgroundLayer', true)[0];

                if (fromSrs !== service.getMapProjection()){
                    var polygonNew = ol.proj.transformExtent(bboxFeature, fromSrs,
                                                    service.getMapProjection());
                    polygon = new ol.Feature(ol.geom.Polygon.fromExtent(polygonNew));
                }

                // TransformInteractionLayer
                // holds the value of q.geo
                var vector = new ol.layer.Vector({
                    name: 'TransformInteractionLayer',
                    source: new ol.source.Vector(),
                    style: new ol.style.Style({
                        fill: new ol.style.Fill({
                            color: [255,255,255,0]
                        }),
                        stroke: new ol.style.Stroke({
                            color: [0,0,0,0],
                            width: 0
                        })
                    })
                });
                service.getMap().addLayer(vector);
                vector.getSource().addFeature(polygon);
            }

            function setTransactionBBox(extent) {
                var transformationLayer = service.getLayersBy('name',
                                                              'TransformInteractionLayer')[0],
                    vectorSrc = transformationLayer.getSource(),
                    currentBbox = vectorSrc.getFeatures()[0],
                    polyNew;

                polyNew = ol.geom.Polygon.fromExtent(extent);
                currentBbox.setGeometry(polyNew);
            }

            service.calculateReducedBoundingBoxFromInFullScreen = function(extent) {
                var sideBarPercent = 1 - (HeightModule.sideBarWidth()/$window.innerWidth);
                var rightSideBarWidth = 1 - (HeightModule.rightSideBarWidth/$window.innerWidth);
                var bottomHeight = 1 - (HeightModule.bottomHeight/$window.innerWidth);
                var topBarPercent = 1 -
                    (HeightModule.topPanelHeight()/HeightModule.documentHeight());
                if(solrHeatmapApp.appConfig) {
                    var dx = extent.maxX - extent.minX,
                        dy = extent.maxY - extent.minY,
                        minX = extent.minX + (1 - sideBarPercent) * dx,
                        maxX = extent.minX + (rightSideBarWidth) * dx,
                        minY = extent.minY + (1 - bottomHeight) * dy,
                        maxY = extent.minY + (topBarPercent) * dy;
                    return {minX: minX, minY: minY, maxX: maxX, maxY: maxY};
                }
                return extent;
            };

            service.calculateFullScreenExtentFromBoundingBox = function(extent) {
                extent = {
                    minX: extent[0], minY: extent[1],
                    maxX: extent[2], maxY: extent[3]
                };
                var sideBarPercent = 1 - (HeightModule.sideBarWidth()/$window.innerWidth);
                var topBarPercent = 1 -
                    (HeightModule.topPanelHeight()/HeightModule.documentHeight());

                var dx = extent.maxX - extent.minX,
                    dy = extent.maxY - extent.minY,
                    minX = extent.minX + dx - (dx/sideBarPercent),
                    maxY = extent.minY + dy/topBarPercent;
                return [minX, extent.minY, extent.maxX, maxY];
            };

            /*
             * For change:resolution event (zoom in map):
             * If bounding of transform interaction is grater than the map extent
             * the transform box will be resized to solrHeatmapApp.appConfig.ratioInnerBbox percent
             */
            service.checkBoxOfTransformInteraction = function() {
                var mapExtent = service.getMapView().calculateExtent(service.getMapSize());

                // calculate reduced bounding box
                var reducedBoundingBox = service.calculateReducedBoundingBoxFromInFullScreen({
                    minX: mapExtent[0], minY: mapExtent[1],
                    maxX: mapExtent[2], maxY: mapExtent[3]
                });

                setTransactionBBox([reducedBoundingBox.minX, reducedBoundingBox.minY,
                    reducedBoundingBox.maxX, reducedBoundingBox.maxY]);
            };

            /**
             * Helper method to reset the map
             */
            service.resetMap = function() {
                // Reset view
                var intitalCenter = solrHeatmapApp.initMapConf.view.center,
                    intitalZoom = solrHeatmapApp.initMapConf.view.zoom;
                if (intitalZoom && intitalCenter) {
                    var vw = service.getMapView();
                    vw.setCenter(intitalCenter);
                    vw.setZoom(intitalZoom);
                    service.checkBoxOfTransformInteraction();
                }
            };

            service.getReducedQueryFromExtent = function(extentQuery) {
                var extent = queryService.getExtentFromQuery(extentQuery);
                return queryService.
                    createQueryFromExtent(
                        service.calculateReducedBoundingBoxFromInFullScreen(extent));
            };

            service.getCurrentExtentQuery = function(){
                var currentExtent = service.getCurrentExtent();
                return {
                    geo: queryService.createQueryFromExtent(currentExtent.geo),
                    hm: queryService.createQueryFromExtent(currentExtent.hm)
                };
            };

            service.createExtentFromNormalize = function(normalizedExtent) {
                return {
                    minX: normalizedExtent[0],
                    minY: normalizedExtent[1],
                    maxX: normalizedExtent[2],
                    maxY: normalizedExtent[3]
                };
            };

            /**
             * Builds geospatial filter depending on the current map extent.
             * This filter will be used later for `q.geo` parameter of the API
             * search or export request.
             */
            service.getCurrentExtent = function(){
                var viewProj = service.getMapProjection(),
                    extent = service.getMapView().calculateExtent(service.getMapSize()),
                    extentWgs84 = ol.proj.transformExtent(extent, viewProj, 'EPSG:4326'),
                    transformInteractionLayer = service.
                                    getLayersBy('name', 'TransformInteractionLayer')[0],
                    currentBbox,
                    currentBboxExtentWgs84,
                    currentExtent = {},
                    currentExtentBox = {};

                if (!transformInteractionLayer) {
                    return null;
                }
                currentBbox = transformInteractionLayer.getSource().getFeatures()[0];
                currentBboxExtentWgs84 = ol.proj.transformExtent(
                                currentBbox.getGeometry().getExtent(), viewProj, 'EPSG:4326');

                // default: Zoom level <= 1 query whole world
                if (service.getMapZoom() <= 1) {
                    extentWgs84 = [-180, -90 ,180, 90];
                }

                if (extent && extentWgs84){
                    var normalizedExtentMap = NormalizeService.normalizeExtent(extentWgs84);
                    var normalizedExtentBox = NormalizeService
                            .normalizeExtent(currentBboxExtentWgs84);

                    currentExtent = service.createExtentFromNormalize(normalizedExtentMap);

                    currentExtentBox = service.createExtentFromNormalize(normalizedExtentBox);

                    var roundToFixed = function(value){
                        return parseFloat(Math.round(value* 100) / 100).toFixed(2);
                    };
                    // Reset the date fields
                    $rootScope.$broadcast('geoFilterUpdated', '[' +
                                            roundToFixed(currentExtentBox.minX) + ',' +
                                            roundToFixed(currentExtentBox.minY) + ' TO ' +
                                            roundToFixed(currentExtentBox.maxX) + ',' +
                                            roundToFixed(currentExtentBox.maxY) + ']');
                }

                return {hm: currentExtent, geo: currentExtentBox};
            };

            service.removeAllfeatures = function() {
                if (angular.isObject(map)) {
                    var layersWithBbox = service.getLayersBy('isbbox', true);
                    layersWithBbox[0].getSource().clear();
                }
            };

            service.addCircle = function(point, style) {

                var geojsonObject = {
                    "type": "Feature",
                    "geometry": {"type": "Point", "coordinates": ol.proj.fromLonLat(point)}
                };

                if (angular.isObject(map) && Object.keys(map).length !== 0) {
                    var layersWithBbox = service.getLayersBy('isbbox', true);
                    var features = (new ol.format.GeoJSON).readFeatures(geojsonObject);

                    if (layersWithBbox.length) {
                        layersWithBbox[0].getSource().addFeatures(features);
                    }else{
                        var vectorLayer = new ol.layer.Vector({
                            isbbox: true,
                            source: new ol.source.Vector({
                                features: features
                            })
                        });
                        vectorLayer.setStyle(style);
                        map.addLayer(vectorLayer);
                    }

                }
            };

            service.toggleBaseMaps = function() {
                service.googleLayer.setVisible(!service.googleLayer.getVisible());
                service.tonerLayer.setVisible(!service.tonerLayer.getVisible());
            };

            /**
             *
             */
            service.init = function(config) {
                var viewConfig = angular.extend(defaults.view,
                                                    config.mapConfig.view),
                    rendererConfig = config.mapConfig.renderer ?
                        config.mapConfig.renderer : defaults.renderer,
                    layerConfig = config.mapConfig.layers;

                map = new ol.Map({
                    // use OL3-Google-Maps recommended default interactions
                    interactions: olgm.interaction.defaults(),
                    controls: ol.control.defaults().extend([
                        new ol.control.ScaleLine(),
                        new ol.control.ZoomSlider()
                    ]),
                    layers: buildMapLayers(layerConfig),
                    renderer: angular.isString(rendererConfig) ?
                                            rendererConfig : undefined,
                    target: 'map',

                    view: new ol.View({
                        center: angular.isArray(viewConfig.center) ?
                                viewConfig.center : undefined,
                        maxZoom: angular.isNumber(viewConfig.maxZoom) ?
                                viewConfig.maxZoom : undefined,
                        minZoom: angular.isNumber(viewConfig.minZoom) ?
                                viewConfig.minZoom : undefined,
                        projection: angular.isString(viewConfig.projection) ?
                                viewConfig.projection : undefined,
                        resolution: angular.isString(viewConfig.resolution) ?
                                viewConfig.resolution : undefined,
                        resolutions: angular.isArray(viewConfig.resolutions) ?
                                viewConfig.resolutions : undefined,
                        rotation: angular.isNumber(viewConfig.rotation) ?
                                viewConfig.rotation : undefined,
                        zoom: angular.isNumber(viewConfig.zoom) ?
                                viewConfig.zoom : undefined,
                        zoomFactor: angular.isNumber(viewConfig.zoomFactor) ?
                                viewConfig.zoomFactor : undefined
                    })
                });

                var olGM = new olgm.OLGoogleMaps({map: map}); // map is the ol.Map instance
                olGM.activate();

                if (angular.isArray(viewConfig.extent)) {
                    var vw = map.getView();
                    vw.set('extent', viewConfig.extent);
                    generateMaskAndAssociatedInteraction(viewConfig.extent, viewConfig.projection);

                    if (viewConfig.initExtent) {
                        vw.fit(viewConfig.extent, service.getMapSize());
                    }
                }

                var tooltip = $window.document.getElementById('tooltip');
                var overlay = new ol.Overlay({
                    element: tooltip,
                    offset: [10, 0],
                    positioning: 'bottom-left'
                });
                map.addOverlay(overlay);

                map.on('pointermove', function (evt) {
                    displayTooltip(evt, overlay, tooltip);
                });
            };
            return service;
        }]
);
})();
