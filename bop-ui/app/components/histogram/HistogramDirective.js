/*eslint angular/di: [2,"array"]*/
/*eslint angular/document-service: 0 */
(function() {

    angular
    .module('search_timehistogram_component', [])
    .directive('timeHistogram', ['$rootScope', 'HeatMapSourceGenerator',
        'searchFilter', 'DateTimeService', 'NumberService',
        function timeHistogram($rootScope, HeatMapSourceGenerator,
            searchFilter, DateTimeService, NumberService) {
            var directive = {
                templateUrl: 'components/histogram/histogram.tpl.html',
                restrict: 'EA',
                link: link,
                scope: {}
            };
            return directive;

            function link(scope, element, attr) {
                var HistogramBars;
                var vm = scope;

                vm.barId = attr.barid;
                vm.histogramBarsDimensions = {};
                vm.yLegendRange = [];

                vm.slider = defaultSliderValue();

                vm.$on('setHistogramRangeSlider', function(even, histogram) {
                    HistogramBars = makeHistogram(histogram);
                    HistogramBars.renderingSvgBars();
                    vm.histogramBarsDimensions = HistogramBars.dimensions;
                    vm.yLegendRange = createRange(vm.histogramBarsDimensions.maxValue, 2);
                });

                vm.$on('changeSlider', function(event, slider) {
                    HistogramBars.renderingSvgBars(slider.minValue, slider.maxValue);
                });

                vm.$on('setHistogram', setHistogram);

                vm.$on('slideEnded', slideEnded);

                /**
                 * Create histogram
                 */
                function makeHistogram(histogram) {

                    var barsheight = 54;
                    var histogrambarsWidth = 350;
                    var paddingBar = 8;

                    findHistogramMaxValue();

                    return {
                        renderingSvgBars: renderingSvgBars,
                        dimensions: getDimensions()
                    };

                    function findHistogramMaxValue() {
                        histogram.maxValue = Math.max.apply(null,
                            histogram.counts.map(function(obj) {
                                return obj.count;
                            })
                        );
                    }

                    function getDimensions() {
                        return {
                            barsheight: barsheight,
                            histogrambarsWidth: histogrambarsWidth,
                            paddingBar: paddingBar,
                            counts: histogram.counts,
                            gap: histogram.gap,
                            maxValue: histogram.maxValue
                        };
                    }

                    function renderingSvgBars(minValue, maxValue) {
                        if (histogram.counts) {
                            minValue = minValue || 0;
                            maxValue = maxValue || histogram.counts.length - 1;
                            histogram.bars = document.getElementById(scope.barId);
                            var rectWidth = (histogrambarsWidth - 2*paddingBar) / histogram.counts.length;
                            var svgRect = histogram.counts.map(renderSvgBar);
                            histogram.bars.innerHTML = '<svg style="padding-left:' + paddingBar + 'px" width="100%" height="' +
                                barsheight + '">' + svgRect.join('') + '</svg>';
                        }

                        function renderSvgBar(bar, barKey) {
                            var height = histogram.maxValue === 0 ?
                                0 : barsheight * bar.count / histogram.maxValue;
                            var y = barsheight - height;
                            var translate = (rectWidth) * barKey;
                            var color = getColor(barKey, minValue, maxValue);
                            return '<g transform="translate(' + translate + ', 0)">' +
                                 '  <rect width="' + rectWidth + '" height="' + height +
                                 '" y="' + y + '" fill="' + color + '"></rect>' +
                                 '</g>';
                        }

                        function getColor(barkey, minvalue, maxvalue) {
                            return barkey >= minvalue && barkey <= maxvalue ? '#88b5dd' : '#E3E3E3';
                        }
                    }
                }

                function setHistogram(event, dataHistogram) {
                    if (!dataHistogram.counts.length) {
                        disableSlider(true);
                        return;
                    }
                    dataHistogram.counts = generateAllDates(dataHistogram.counts);
                    disableSlider(false);
                    var firstDate = new Date(dataHistogram.counts[0].value);
                    var lastDate = new Date(dataHistogram.counts[dataHistogram.counts.length - 1].value);
                    if (vm.slider.options.ceil === 1 || isTheInitialDate() ||
                        dataHistogram.counts.length - 1 > vm.slider.options.ceil) {
                        vm.slider.counts = dataHistogram.counts;
                        vm.slider.options.ceil = dataHistogram.counts.length - 1;
                        vm.slider.maxValue = vm.slider.options.ceil;
                        vm.slider.minValue = 0;
                        dataHistogram.slider = vm.slider;
                        $rootScope.$broadcast('setHistogramRangeSlider', dataHistogram);
                    }else if ( (dataHistogram.counts.length - 1 <= vm.slider.options.ceil && !vm.slider.changeTime) ||
                        vm.slider.oldFirstDate > firstDate || vm.slider.oldLastDate < lastDate) {
                        vm.slider.oldFirstDate = firstDate;
                        vm.slider.oldLastDate = lastDate;
                        dataHistogram.counts = getSubDataHistogram(dataHistogram, vm.slider);
                        $rootScope.$broadcast('setHistogramRangeSlider', dataHistogram);
                    }else{
                        vm.slider.changeTime = false;
                        $rootScope.$broadcast('changeSlider', vm.slider);
                    }
                }

                function generateAllDates(data) {
                    var newData = [];
                    var unitOfTime = DateTimeService.getDurationFormatFromGap(searchFilter.gap).duration;
                    if (!unitOfTime) {
                        return data;
                    }
                    data.forEach(function (datetime, index) {
                        if (index < data.length - 1) {
                            var startDate = moment(datetime.value);
                            var nextHour = startDate.add(1, unitOfTime);
                            var nextDate = moment(data[index + 1].value);
                            newData.push(datetime);
                            while (new Date(nextHour.toJSON()) < new Date(nextDate.toJSON())) {
                                newData.push({
                                    count: 0,
                                    value: nextHour.toJSON()
                                });
                                nextHour = nextHour.add(1, unitOfTime);
                            }
                        }
                    });
                    return newData;
                }

                function getSubDataHistogram(dataHistogram, slider) {
                    var index = 0;
                    var newData = slider.counts.map(function (bar) {
                        var barDate = new Date(bar.value);
                        if(slider.oldFirstDate > barDate || slider.oldLastDate < barDate
                            || index === dataHistogram.counts.length){
                            bar.count = 0;
                        }else if(dataHistogram.counts[index]) {
                            bar.count = dataHistogram.counts[index].count;
                            index++;
                        }
                        return bar;
                    });
                    return newData;
                }

                function isTheInitialDate() {
                    var initialDate = DateTimeService.formatDatesToString(searchFilter.minDate, searchFilter.maxDate);
                    return initialDate === searchFilter.time;
                }

                function slideEnded() {
                    solrHeatmapApp.isThereInteraction = true;
                    var minKey = vm.slider.minValue;
                    var maxKey = vm.slider.maxValue;
                    vm.datepickerStartDate = minKey === 0 ?
                        searchFilter.minDate : new Date(vm.slider.counts[minKey].value);
                    vm.datepickerEndDate = maxKey === vm.slider.counts.length - 1 ?
                        searchFilter.maxDate : new Date(vm.slider.counts[maxKey].value);
                    vm.dateString = DateTimeService.formatDatesToString(vm.datepickerStartDate,
                                                            vm.datepickerEndDate);
                    performDateSearch();
                }

                function performDateSearch() {
                    vm.slider.changeTime = true;
                    searchFilter.setFilter({time: vm.dateString});
                    HeatMapSourceGenerator.search();
                }

                function disableSlider(option) {
                    if (option) {
                        vm.slider.options.disabled = true;
                        vm.slider.options.getSelectionBarColor = function() {
                            return '#e3e3e3';
                        };
                    }else {
                        vm.slider.options.disabled = false;
                        vm.slider.options.getSelectionBarColor =
                            defaultSliderValue().options.getSelectionBarColor;
                    }

                }

                function defaultSliderValue() {
                    return {
                        minValue: 0,
                        maxValue: 1,
                        changeTime: false,
                        options: {
                            floor: 0,
                            ceil: 1,
                            step: 1,
                            minRange: 1,
                            noSwitching: true,
                            hideLimitLabels: true,
                            getSelectionBarColor: function() {
                                return '#609dd2';
                            },
                            translate: function() {
                                return '';
                            }
                        }
                    };
                }


                function createRange(maxValue, partition) {
                    if (maxValue < partition) {
                        return [];
                    }
                    var range =[];
                    partition = partition || 1;
                    maxValue = maxValue || 0;
                    var step = Math.floor(maxValue/partition);
                    for (var i = 0; i <= maxValue; i = i+step) {
                        range.push(NumberService.compactInteger(i));
                    }
                    return range;
                }

                vm.Yaxis = {
                    value: 0,
                    options: {
                        step: 1,
                        floor: 0,
                        ceil: 2,
                        vertical: true,
                        minRange: 1,
                        showTicks: true,
                        translate: function() {
                            return '';
                        },
                        getSelectionBarColor: function() {
                            return '#609dd2';
                        }
                    }
                };

            }
        }]);

})();
