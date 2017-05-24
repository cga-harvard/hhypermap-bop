#!/bin/bash

echo ""
echo "============== USAGE =============="
echo ""
echo ". deploy.sh"
echo ""
echo "==================================="
echo ""


git checkout master
git pull --rebase
make dist
git checkout dev5-deploy
git rebase master
git push --force

tar czf olgm.tar.gz \
  node_modules/openlayers/build/ol.js \
  node_modules/openlayers/build/ol.css \
  node_modules/openlayers/css/ol.css \
  css \
  dist/ol3gm.js \
  examples

scp olgm.tar.gz dev5.mapgears.com:
rm olgm.tar.gz

git checkout master
