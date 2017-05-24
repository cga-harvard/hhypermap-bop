/*eslint angular/di: [2,"array"]*/
/*eslint max-len: [2,100]*/

(function() {
    angular.module('SolrHeatmapApp')
    .factory('queryService', ['Normalize', function(Normalize){
        var service = {};
        service.createQueryFromExtent = function(extent) {
            return '[' + extent.minY +
                ',' + extent.minX +
                ' TO ' + extent.maxY +
                ',' + extent.maxX + ']';
        };
        service.getExtentFromQuery = function(query) {
            var extent, min, max,
                extentSplit = function(extentString) {
                    return extentString.split(',');
                };
            extent = query.replace(/\[|\]/g,'').split(' TO ');
            min = extentSplit(extent[0]);
            max = extentSplit(extent[1]);
            return {
                minX: parseInt(min[0], 10),
                minY: parseInt(min[1], 10),
                maxX: parseInt(max[0], 10),
                maxY: parseInt(max[1], 10)
            };
        };
        service.getExtentForProjectionFromQuery = function(query, projection) {
            var extentObj = service.getExtentFromQuery(query);
            var extent = Normalize.normalizeExtent([
                extentObj.minY,
                extentObj.minX,
                extentObj.maxY,
                extentObj.maxX]);
            return ol.proj.transformExtent(extent, 'EPSG:4326', projection);
        };
        return service;
    }]);
})();
