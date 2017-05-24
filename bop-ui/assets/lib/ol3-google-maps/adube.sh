#!/bin/bash

rm -rf /opt/ol3/3.8.2-git/src/olgm
cp -ap src /opt/ol3/3.8.2-git/src/olgm
cd /opt/ol3/3.8.2-git/
make lint
