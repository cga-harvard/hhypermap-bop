module.exports = function ( config ) {
  config.set({
    basePath: '.',
    files: [
      'assets/lib/angular/angular.min.js',
      "assets/lib/jquery/jquery-2.1.3.min.js",
      "assets/lib/bootstrap/js/bootstrap.min.js",
      "assets/lib/ui-bootstrap/ui-bootstrap-tpls-1.3.3.min.js",
      "assets/lib/angular/angular-ui-router.js",
      "assets/lib/proj4js/proj4.js",
      "config/3857.js",
      "assets/lib/ol3/ol.js",
      "assets/lib/ol3-ext/filter/filter.js",
      "assets/lib/ol3-ext/filter/maskfilter.js",
      "assets/lib/ol3-ext/interaction/transforminteraction.js",
      'node_modules/angularjs-slider/dist/rzslider.js',
      'node_modules/angular-mocks/angular-mocks.js',
      'assets/lib/ol3-google-maps/dist/ol3gm.js',
      'assets/lib/geostats/lib/geostats.min.js',
      'https://maps.googleapis.com/maps/api/js?v=3&key=AIzaSyAjtdt_Db2IOZDVYznVqot45xUs1tNhhDw',
      'app/**/*.js',
      'tests/**/*.spec.js',
      'app/**/*.tpl.html',
      'assets/lib/moment/min/moment.min.js'
    ],
    exclude: [
    ],
    frameworks: [ 'jasmine' ],
    preprocessors: {
      'app/**/*.js': ['coverage'],
      'app/**/*.tpl.html': ['ng-html2js']
    },
    ngHtml2JsPreprocessor: {
        stripPrefix: 'app/',
        moduleName: 'templates-components'
    },
    reporters: ['spec','coverage'],
    port: 9018,
    runnerPort: 9100,
    urlRoot: '/',
    autoWatch: false,
    coverageReporter: {
      dir: 'reports',
      reporters:[
        {type: 'html', subdir: 'html/'},
        {type: 'lcovonly', subdir: 'coverage/', file: 'lcov.info'},
      ]
    },
    browsers: [
      'PhantomJS'
    ]
  });
};
