(function() {
    angular
    .module('SolrHeatmapApp')
    .factory('Normalize', function() {

        return {
            normalizeExtent: normalize
        };

        /**
         * Clamps given number `num` to be inside the allowed range from `min`
         * to `max`.
         * Will also work as expected if `max` and `min` are accidently swapped.
         *
         * @param {number} num The number to clamp.
         * @param {number} min The minimum allowed number.
         * @param {number} max The maximim allowed number.
         * @return {number} The clamped number.
         */
        function clamp(num, min, max) {
            if (max < min) {
                var tmp = min;
                min = max;
                max = tmp;
            }
            return Math.min(Math.max(min, num), max);
        }

        /**
         * Determines whether passed longitude is outside of the range `-180`
         * and `+180`.
         *
         * @param {number} lon The longitude to check.
         * @return {boolean} Whether the longitude is outside of the range
         *  -180` and `+180`.
         */
        function outsideLonRange(lon) {
            return lon < -180 || lon > 180;
        }

        /**
         * Determines whether passed latitude is outside of the range `-90` and
         * `+90`.
         * @param {number} lat The longitude to check.
         * @return {boolean} Whether the latitude is outside of the range `-90`
         *  and `+90`.
         */
        function outsideLatRange(lat) {
            return lat < -90 || lat > 90;
        }

        /**
         * Clamps given longitude to be inside the allowed range from `-180` to
         * `+180`.
         * @param {number} lon The longitude to fit / clamp.
         * @return {number} The fitted / clamped longitude.
         */
        function clampLon(lon) {
            return clamp(lon, -180, 180);
        }

        /**
         * Clamps given latitude to be inside the allowed range from `-90` to
         * `+90`.
         * @param {number} lat The latitude to fit / clamp.
         * @return {number} The fitted / clamped latitude.
         */
        function clampLat(lat) {
            return clamp(lat, -90, 90);
        }

        /**
         * Normalizes an `EPSG:4326` extent which may stem from multiple worlds
         * so that the returned extent always is within the bounds of the one
         * true `EPSG:4326` world extent `[-180, -90, 180, 90]`.
         *
         * Examples:
         *
         *     // valid world in, returned as-is:
         *     normalize([-180, -90, 180, 90])  // => [-180, -90, 180, 90]
         *
         *     // valid extent in world in, returned as-is:
         *     normalize([-160, -70, 150, 70])  // => [-160, -70, 150, 70]
         *
         *     // shifted one degree westwards, returns one-true world:
         *     normalize([-181, -90, 179, 90])  // => [-180, -90, 180, 90]
         *
         *     // shifted one degree eastwards, returns one-true world:
         *     normalize([-179, -90, 181, 90])  // => [-180, -90, 180, 90]);
         *
         *     // shifted more than one world westwards, returns one-true world:
         *     normalize([-720, -90, -360, 90]) // => [-180, -90, 180, 90]);
         *
         *     // shifted to the south, returns one-true world:
         *     normalize([-180, -91, 180, 89])  // =>   [-180, -90, 180, 90]);
         *
         *     // multiple worlds, returns one-true world:
         *     normalize([-360, -90, 180, 90])  // =>   [-180, -90, 180, 90]);
         *
         *     // multiple worlds, returns one-true world:
         *     normalize([-360, -180, 180, 90]) // =>  [-180, -90, 180, 90]);
         *
         * @param {Array<number>} Extent to normalize: [minx, miny, maxx, maxy].
         * @return {Array<number>} Normalized extent: [minx, miny, maxx, maxy].
         */
        function normalize(extent) {
            var minX = extent[0];
            var minY = extent[1];
            var maxX = extent[2];
            var maxY = extent[3];
            var width = Math.min(maxX - minX, 360);
            var height = Math.min(maxY - minY, 180);

            var rangeCheck = function(min,max, rangeFunc, clampFunc, extra) {
                if (rangeFunc(min)) {
                    min = clampFunc(min);
                    max = min + extra;
                } else if (rangeFunc(max)) {
                    max = clampFunc(max);
                    min = max - extra;
                }
                return [min,max];
            };

            var x = rangeCheck(minX, maxX, outsideLonRange, clampLon, width);
            minX = x[0];
            maxX = x[1];
            var y = rangeCheck(minY, maxY, outsideLatRange, clampLat, height);
            minY = y[0];
            maxY = y[1];

            return [minX, minY, maxX, maxY];
        }
    });
})();
