/*eslint angular/di: [2,"array"]*/
/*eslint max-len: [2,100]*/
/**
 * DatePickerCtrl Controller
 */
(function() {

    angular
    .module('search_datepicker_component', [])
    .directive('datePicker', ['HeatMapSourceGenerator',
        'searchFilter', 'DateTimeService',
        function(HeatMapSourceGenerator, searchFilter, DateTimeService) {
            return {
                link: datePickerFilterLink,
                templateUrl: 'components/datepicker/datepicker.tpl.html',
                restrict: 'EA',
                scope: {}
            };

            function datePickerFilterLink(scope) {

                var vm = scope;

                vm.dateOptions = searchFilter;
                vm.dateOptions.startingDate = 1;
                vm.dateOptions.showWeeks = false;

                vm.initialDateOptions = {
                    minDate: vm.dateOptions.minDate,
                    maxDate: vm.dateOptions.maxDate
                };

                vm.$watch(function(){
                    return vm.dateOptions.time;
                }, function(newValue, oldValue){
                    if (angular.isString(newValue)) {
                        vm.dateString = newValue;
                        changeDatepicker();
                    }
                });

                vm.startDate = {
                    opened: false
                };

                vm.endDate = {
                    opened: false
                };

                /**
                 * Set initial values for min and max dates in both of datepicker.
                 */
                vm.datepickerStartDate = vm.dateOptions.minDate;
                vm.datepickerEndDate = vm.dateOptions.maxDate;

                vm.onChangeDatepicker = onChangeDatepicker;

                vm.openEndDate = openEndDate;

                vm.openStartDate = openStartDate;

                vm.onSubmitDateText = onSubmitDateText;

                /**
                 * Will be called on click on start datepicker.
                 * `minDate` will be reset to the initial value (e.g. 2000-01-01),
                 * `maxDate` will be adjusted with the `scope.datepickerEndDate` value to
                 *  restrict it not to be below the `minDate`.
                 */
                function openStartDate() {
                    vm.startDate.opened = true;
                    vm.dateOptions.minDate = vm.initialDateOptions.minDate;
                    vm.dateOptions.maxDate = vm.datepickerEndDate;
                }


                /**
                 * Will be called on click on end datepicker.
                 * `maxDate` will be reset to the initial value (e.g. 2016-12-31),
                 * `minDate` will be adjusted with the `scope.datepickerStartDate` value to
                 *  restrict it not to be bigger than the `maxDate`.
                 */
                function openEndDate() {
                    vm.endDate.opened = true;
                    vm.dateOptions.maxDate = vm.initialDateOptions.maxDate;
                    vm.dateOptions.minDate = vm.datepickerStartDate;
                }

                /**
                 * Will be fired after the start and the end date was chosen.
                 */
                function onChangeDatepicker(){
                    vm.dateString = DateTimeService.formatDatesToString(vm.datepickerStartDate,
                                                            vm.datepickerEndDate);
                    performDateSearch();
                }

                function stringToStartEndDateArray(dateString) {
                    if (!angular.isString(dateString)) {
                        return null;
                    }
                    var dateArray = dateString.split(' TO ');
                    if (angular.isString(dateString) && dateArray.length === 2) {
                        dateArray[0] = new Date(dateArray[0].slice(1,11));
                        dateArray[1] = new Date(dateArray[1].slice(0,10));
                        if (dateArray[0] === 'Invalid Date' || dateArray[0] === 'Invalid Date') {
                            return null;
                        }
                        return dateArray;
                    }
                    return null;
                }

                function changeDatepicker(){
                    var dateArray = stringToStartEndDateArray(vm.dateString);
                    if (dateArray !== null) {
                        vm.datepickerStartDate = dateArray[0];
                        vm.datepickerEndDate = dateArray[1];
                        return true;
                    } else{
                        vm.dateString = DateTimeService.formatDatesToString(vm.datepickerStartDate,
                                                                vm.datepickerEndDate);
                        return false;
                    }
                }

                function onSubmitDateText() {
                    var hasDatepickerChanged = changeDatepicker();
                    if (hasDatepickerChanged) {
                        performDateSearch();
                    }
                }

                function performDateSearch() {
                    searchFilter.setFilter({time: vm.dateString});
                    HeatMapSourceGenerator.search();
                }
            }
        }]);



})();
