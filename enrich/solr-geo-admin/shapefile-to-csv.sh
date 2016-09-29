#!/usr/bin/env bash
set -ue

# DATA_DIR is where CSVs will go.
#DATA_DIR=/Volumes/...
#INPUT: COLLECTION
COLLECTION="$1"

# Summary: use GDAL's "OGR" tool to convert the ESRI ShapeFiles to CSV. Use EPGS 4326 (WGS-84).

case "$COLLECTION" in
  "ADMIN2" )
    INPUT=gadm28_levels
    SRSOPTS=
    # (already in EPSG-4326 it seems; no SRS metadata?)
    ;;
  "US_CENSUS_TRACT" )
    INPUT=Tract_2010Census_DP1
    SRSOPTS="-t_srs EPSG:4326"
    docker run -ti --rm -v "$DATA_DIR:/data" geodata/gdal \
      ogr2ogr -t_srs EPSG:4326 -f CSV "/data/$COLLECTION.csv" /data/Tract_2010Census_DP1/ -lco GEOMETRY=AS_WKT
    ;;
  "US_MA_CENSUS_BLOCK" )
    INPUT=MA_Blocks
    SRSOPTS="-t_srs EPSG:4326"
    ;;
   *)
  echo "Unrecognized collection $COLLECTION" >&2
  exit 1
  ;;
esac

docker run -ti --rm -v "$DATA_DIR:/data" geodata/gdal \
      ogr2ogr ${SRSOPTS} -f CSV "/data/$COLLECTION.csv" "/data/$INPUT" -lco GEOMETRY=AS_WKT