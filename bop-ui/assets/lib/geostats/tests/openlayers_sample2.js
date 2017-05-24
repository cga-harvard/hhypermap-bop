// get user defined class
// if equals value
// handle both numeric and string (unique values classification)
function getClass(val, a) {
	
	var separator = ' - '
	
	// return 2;
    for(var i= 0; i < a.length; i++) {
	    // all classification except uniqueValues
		if(a[i].indexOf(separator) != -1) {
			var item = a[i].split(/separator/);
			if(val <= parseFloat(item[1])) {return i;}
		} else {
			if(val == a[i]) {return i;}
		}
    }
}

// Function called from body tag
function init(color_x, class_x) {

    var lon = -0.786;
    var lat = 0.100;
    var zoom = 3;

    OpenLayers.ImgPath = "http://js.mapbox.com/theme/dark/";
    
    var context_x = {
        getColour: function(feature) {
    	color = color_x;
    	return color[getClass(feature.attributes["value"], class_x)];
        }
    };    

    var template = {
		fillOpacity: 0.9,
        strokeColor: "#ffffff",
        strokeWidth: 1,
        fillColor: "${getColour}",
        label : "${value}",
        fontColor: "#ffffff",
    };

    // Style
    var style_x = new OpenLayers.Style(template, {context: context_x});
    var styleMap_x = new OpenLayers.StyleMap({'default': style_x});
    
    // options de la carte
	var options = {
		scales: [10000000, 5000000, 2500000, 1000000, 500000, 250000],
        controls: [],  // Remove all controls
        // allOverlays: true
    };

    // Create a new map with options defined above
    map = new OpenLayers.Map( 'map', options );
    

    // Create polygon layer as vector features
    // http://dev.openlayers.org/docs/files/OpenLayers/Layer/Vector-js.html

    layer_x = new OpenLayers.Layer.GML("choropleth", "tests/geostats.json",
                { format: OpenLayers.Format.GeoJSON,
    			styleMap: styleMap_x,
	            isBaseLayer: true,
	            projection: new OpenLayers.Projection("EPSG:4326")
	           });

    map.addLayers([layer_x]);
    map.setCenter(new OpenLayers.LonLat(lon, lat), zoom);
    
    // Add map controls:
	// http://dev.openlayers.org/docs/files/OpenLayers/Control-js.html
    map.addControl(new OpenLayers.Control.MousePosition());
    map.addControl(new OpenLayers.Control.Navigation());
    map.addControl(new OpenLayers.Control.PanZoomBar());
    map.addControl(new OpenLayers.Control.ScaleLine({geodesic: true, maxWidth: 200, bottomOutUnits: "", bottomInUnits: ""}));

}