#!/bin/bash
pkill -9 geo_tweets_harvester.py
python3 /home/ubuntu/geo-tweet-harvester/scripts/geo_tweets_harvester.py &
