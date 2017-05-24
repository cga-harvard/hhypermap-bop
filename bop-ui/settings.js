/* eslint-disable no-console */
/*eslint angular/log: 0*/
/*eslint angular/json-functions: 0*/


const fs = require('fs');
const BASE_URL = process.env['TWEETS_BASE_URL'];

const tweetsSearchBaseUrl = BASE_URL ?
    BASE_URL + 'search' : 'http://bop.worldmap.harvard.edu/bopws/tweets/search?';

const tweetsExportBaseUrl = BASE_URL ?
    BASE_URL + 'export' : 'http://bop.worldmap.harvard.edu/bopws/tweets/export';

const config = {
    'appConfig': {
        'tweetsSearchBaseUrl': tweetsSearchBaseUrl,
        'tweetsExportBaseUrl': tweetsExportBaseUrl,
        'ratioInnerBbox': 0.98
    },
    'bopwsConfig': {
        'heatmapFacetLimit': process.env['HEATMAP_FACET_LIMIT'] || 10000,
        'csvDocsLimit': process.env['CSV_DOCS_LIMIT'] || 10000,
        'dataverse': {
            'AllowDataverseDeposit': true,
            'dataverseDepositUrl': 'https://dataverse.harvard.edu/deposit-bop-subset',
            'subsetRetrievalUrl': 'cb=https://bop.cga.edu/get_subset',
            'parameters': {
                'time': '{}',
                'text': '{}',
                'geo': '{}',
                'user': '{}'
            }
        }
    },
    'mapConfig': {
        'renderer': 'canvas',
        'view': {
            'center': [-9707182.048613328, 1585691.7893914054],
            'extent': [-20037508.34,-20037508.34,20037508.34,20037508.34],
            'maxZoom': 20,
            'minZoom': 3,
            'projection': 'EPSG:3857',
            'resolution': null,
            'resolutions': null,
            'rotation': null,
            'zoom': 3,
            'zoomFactor': null
        },
        'layers': [{
            'type': 'googleLayer',
            'backgroundLayer': true,
            'visible': true
        },{
            'type': 'Toner',
            'backgroundLayer': true,
            'visible': false
        },{
            'type': 'TileWMS',
            'backgroundLayer': true,
            'name': 'terrestris OSM WMS',
            'attribution': `Data © OpenStreetMap contributors <br>(
                <a href="http://www.openstreetmap.org/copyright">
                    http://www.openstreetmap.org/copyright)
                </a>`,
            'logo': {
                'src': 'https://www.terrestris.de/wp-content/uploads/icon_terrestris_tiny.png',
                'href': 'https://www.terrestris.de'
            },
            'ratio': null,
            'resolutions': null,
            'url': 'https://ows.terrestris.de/osm-gray/service',
            'params': {
                'LAYERS': 'OSM-WMS',
                'VERSION': '1.1.0',
                'FORMAT': 'image/png'
            },
            'crossOrigin': 'anonymous',
            'opacity': 1,
            'visible': false
        }]
    }
};


fs.writeFile('config/appConfig.json', JSON.stringify(config), (err) => {
    if (err) {
        console.error(err);
        return;
    }
    console.log('File has been created');
});
