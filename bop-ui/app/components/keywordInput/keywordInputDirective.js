/*eslint angular/di: [2,"array"]*/
(function() {
    angular
    .module('search_keywordinput_component', [])
    .directive('keywordInput', ['HeatMapSourceGenerator', 'searchFilter', '$window', 'filterKeywordService',
        function keywordInput(HeatMapSourceGenerator, searchFilter, $window, filterKeywordService) {

            return {
                link: keywordInputLink,
                restrict: 'EA',
                templateUrl: 'components/keywordInput/keywordInput.tpl.html',
                scope: {
                    listenKeywordEvent: '@',
                    numberKeywords: '=',
                    limit: '@',
                    text: '@',
                    placeholder: '@'
                }
            };

            function keywordInputLink(scope) {
                var vm = scope;
                var numberKeywords = vm.numberKeywords || 5;

                vm.filter = searchFilter;
                vm.filterArray = [];
                vm.suggestedKeywords = [];
                vm.textSearchInput = {value: '', previousLength: 0 };
                vm.focus = false;
                vm.tagSwitch = {value: false, disable: false};

                vm.doSearch = doSearch;
                vm.removeKeyWord = removeKeyWord;
                vm.addSuggestedKeywordToSearchInput = addSuggestedKeywordToSearchInput;
                vm.onKeyPress = onKeyPress;

                vm.toggleSuggestKeywords = toggleSuggestKeywords;

                listenSuggestWords();

                vm.$watch(function(){
                    return vm.filter[vm.text];
                }, function(newValue, oldValue){
                    vm.filterArray = keyWordStringToArray(newValue);
                });

                /**
                 *
                 */
                function getKeyboardCodeFromEvent(keyEvt) {
                    return $window.event ? keyEvt.keyCode : keyEvt.which;
                }

                /**
                 *
                 */
                function onKeyPress($event) {
                    // only fire the search if Enter-key (13) is pressed
                    if (getKeyboardCodeFromEvent($event) === 13) {
                        vm.doSearch();
                    }else if (getKeyboardCodeFromEvent($event) === 8) {
                        removeKeyWordFromDeleteKey();
                    }
                }

                function doSearch() {
                    if (vm.textSearchInput.value.length) {
                        vm.filter[vm.text] = concatSearchInput(vm.filter[vm.text], vm.textSearchInput.value);
                        vm.textSearchInput = {value: '', previousLength: 0};
                    }
                    search();
                }

                function concatSearchInput(searchInput, newValue) {
                    if (searchInput && searchInput !== '') {
                        var keywordslength = searchInput.split('"="').length;
                        if (keywordslength === 1) {
                            return '"' + searchInput + '" "' + newValue + '"';
                        }else if(keywordslength > 1){
                            return searchInput + ' "' + newValue + '"';
                        }
                    }
                    return newValue === '' ? null : newValue;
                }

                function keyWordStringToArray(keyWordString) {
                    keyWordString = keyWordString || '';
                    return keyWordString.split('"').filter(function(val){
                        return val !== '' && val !== ' ';
                    });
                }

                function removeKeyWord(keyword) {
                    var fiterText;
                    vm.filterArray.forEach(function(value) {
                        if (value !== keyword) {
                            fiterText = concatSearchInput(fiterText, value);
                            return;
                        }
                    });
                    vm.filter[vm.text] = fiterText;
                    search();
                }

                function removeKeyWordFromDeleteKey() {
                    if (vm.textSearchInput.value === '' && vm.textSearchInput.previousLength === 0) {
                        removeKeyWord(vm.filterArray.pop());
                    }
                    vm.textSearchInput.previousLength = vm.textSearchInput.value.length;
                }

                function addSuggestedKeywordToSearchInput(keyword) {
                    vm.textSearchInput.value = keyword;
                    doSearch();
                }

                function listenSuggestWords() {
                    vm.$on(vm.listenKeywordEvent, function(event, dataRawKeywords) {
                        if (!angular.isArray(dataRawKeywords)) {
                            dataRawKeywords = [];
                        }
                        vm.tagSwitch.disable = false;
                        vm.suggestedKeywords = dataRawKeywords.filter(filterKeywordService.filter).slice(0, 10);
                    });
                }

                function filterKeywords(keywordObj) {
                    return keywordObj.value.length >= 3;
                }

                function toggleSuggestKeywords() {
                    if (vm.limit) {
                        vm.filter[vm.limit] = vm.tagSwitch.value ? numberKeywords : null;
                        vm.tagSwitch.disable = true;
                        search();
                    }
                }

                function search() {
                    try {
                        solrHeatmapApp.isThereInteraction = true;
                    } catch (e) {
                        void 0;
                    } finally {
                        HeatMapSourceGenerator.search();
                    }
                }
            }
        }]);
})();
