/*eslint angular/di: [2,"array"]*/
(function() {
    angular.module('SolrHeatmapApp')
    .factory('DateTimeService', [function(){
        var service = {
            formatDatesToString: formatDatesToString,
            getDurationFormatFromGap: getDurationFormatFromGap,
            getGapFromTimeString: getGapFromTimeString

        };

        /**
         * Returns the formatted date object that can be parsed by API.
         * @param {minDate} date full date object
                        (e.g. 'Sat Jan 01 2000 01:00:00 GMT+0100 (CET))
         * @return {String} formatted date as string
            (e.g. [2013-03-10T00:00:00 TO 2013-03-21T00:00:00])
         */
        function formatDatesToString(minDate, maxDate) {
            return '[' + minDate.toISOString().replace('.000Z','') + ' TO ' +
              maxDate.toISOString().replace('.000Z','') + ']';
        }

        function getDurationFormatFromGap(gap) {
            var obj = [];
            if (gap === 'PT1H') {
                obj = ['MMM.D.H[h]', 'hours'];
            }else if(gap === 'P1D') {
                obj = ['MMM-DD', 'days'];
            } else if(gap === 'P1W' || gap === 'P7D'){
                obj = ['YYYY-MMM', 'weeks'];
            } else if (gap === 'P1M') {
                obj = ['YYYY-MMM', 'months'];
            } else if (gap === 'P1Y') {
                obj = ['YYYY', 'years'];
            }
            return {
                format: obj[0],
                duration: obj[1]
            };
        }

        function getGapFromTimeString(timeString) {
            if (!timeString) {
                return;
            }
            var gap,
                partition = 80,
                dates = formatStringToDates(timeString),
                diffms = moment(dates[1]).diff(dates[0]),
                hours = diffms/(1000*3600),
                days = hours/24,
                years = days/365,
                months = years * 12;

            if (hours <= partition) {
                gap = 'PT1H';
            }else if (days <= partition) {
                gap = 'P1D';
            }else if (days/7 <= partition) {
                gap = 'P1W';
            }else if (months <= partition) {
                gap = 'P1M';
            }else {
                gap = 'P1Y';
            }
            return gap;
        }

        function formatStringToDates(stringDates){
            var dates = stringDates.split(' TO ');
            dates[0] = dates[0].slice(1);
            dates[1] = dates[1].slice(0, -1);
            return dates;
        }

        return service;
    }]);
})();
