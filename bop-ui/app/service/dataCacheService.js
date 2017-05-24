/*eslint angular/di: [2,"array"]*/
(function () {
    angular
    .module('SolrHeatmapApp')
    .factory('DataCacheService', ['$window', function ($window) {

        function insertData(objkey, objValue) {
            var stringObjKey = angular.toJson(objkey);
            var stringObjValue = angular.toJson(objValue);
            $window.localStorage.setItem(stringObjKey, stringObjValue);
            return true;
        }

        function getObjData(objKey) {
            var dataObj;
            var stringObjKey = angular.toJson(objKey);
            var stringObjValue = $window.localStorage.getItem(stringObjKey) || false;
            try {
                dataObj = angular.fromJson(stringObjValue);
            } catch (e) {
                dataObj = null;
            } finally {
                return dataObj;
            }
        }

        function localStorageSpace(){
            var data = '';

            // console.log('Current local storage: ');
            for(var key in $window.localStorage){
                if($window.localStorage.hasOwnProperty(key)){
                    data += $window.localStorage[key];
                    // console.log( key + " = " + ((
                        // $window.localStorage[key].length * 16)/(8 * 1024)).toFixed(2) + ' KB' );
                }
            }
            // console.log(data ? '\n' + 'Total space used: ' +
                // ((data.length * 16)/(8 * 1024)).toFixed(2) + ' KB' : 'Empty (0 KB)');
            // console.log(data ? 'Approx. space remaining: '
                // + (5120 - ((data.length * 16)/(8 * 1024)).toFixed(2)) + ' KB' : '5 MB');
        }

        return {
            insertData: insertData,
            getObjData: getObjData,
            localStorageSpace: localStorageSpace
        };


    }]);
})();
