# This code harvest Geotweets then MessagePacks them and populate a Kafka Topic.
# Author:tmostak
# Modified by: Devika Kakkar
# Modified on: October 20, 2016

import time
from datetime import datetime
from urllib2 import URLError
import sys
import subprocess
import math
import threading
import os 
import imp
import msgpack
import lz4
import lz4tools
import xxhash
from kafka import KafkaProducer
from kafka import KafkaConsumer
from kafka import KafkaProducer
from kafka.errors import KafkaError


from twitter import *

# Authorization credentials

def authenticateUser (userName, appInfo):
    #print >> sys.stderr, 'Authenticating user ' 
    if (userName == os.environ["USER_1"]):
        oauthToken_user_1 = os.environ["OAUTH_TOKEN_USER_1"]
        oauthSecret_user_1 = os.environ["OAUTH_SECRET_USER_1"]
        return oauthToken_user_1, oauthSecret_user_1
    
    elif (userName == os.environ["USER_2"]):
        oauthToken_user_2 = os.environ["OAUTH_TOKEN_USER_2"]
        oauthSecret_user_2 =  os.environ["OAUTH_SECRET_USER_2"]
        return oauthToken_user_2, oauthSecret_user_2
        
    elif (userName == os.environ["USER_3"]):
        oauthToken_user_3 = os.environ["OAUTH_TOKEN_USER_3"]
        oauthSecret_user_3 =  os.environ["OAUTH_SECRET_USER_3"]
        return oauthToken_user_3, oauthSecret_user_3
        

    elif (userName == os.environ["USER_4"]):
        oauthToken_user_4 = os.environ["OAUTH_TOKEN_USER_4"]
        oauthSecret_user_4 =  os.environ["OAUTH_SECRET_USER_4"]
        return oauthToken_user_4, oauthSecret_user_4
        

    elif (userName == os.environ["USER_5"]):
        oauthToken_user_5 = os.environ["OAUTH_TOKEN_USER_5"]
        oauthSecret_user_5 =  os.environ["OAUTH_SECRET_USER_5"]
        return oauthToken_user_5, oauthSecret_user_5
        
# The Harvester class

class Harvester (threading.Thread):
    maxErrors = 10
    def __init__(self, username, harvName, boundingBox, appInfo): #boundingBox is tuple in form (minX, minY, maxX, maxY)
        super(Harvester,self).__init__()
        self.username = username
        self.harvName = harvName
        self.boundingBox = boundingBox # should be tuple of len 4
        self.location = str(self.boundingBox[0]) + "," + str(self.boundingBox[1]) + "," +  str(self.boundingBox[2]) + "," + str(self.boundingBox[3])
        self.daemon = True
        self.appInfo = appInfo
        self.token, self.secret = authenticateUser(self.username, self.appInfo) 

    def run(self):
        twitterStream = TwitterStream(auth=OAuth(self.token, self.secret, self.appInfo["key"], self.appInfo["secret"]))
        #print >> sys.stderr, 'Filtering the public timeline for '  
        streamIt = twitterStream.statuses.filter(locations=self.location)
        errorCount = 0
        while errorCount < 10: 
            try:
                for tweet in streamIt:
                        coordinates = tweet.get('coordinates')
                        if coordinates!=None:
                                #packed = msgpack.packb(tweet)
                                result = producer.send(os.environ["TWEET_TOPIC"],tweet)
                                md = result.get()
                                print("send() - %s" % str(md))                                

            except Exception as x:
                print "Disconnected from Twitter: ", x
                errorCount += 1
                time.sleep((2 * errorCount) ** 2 ) #exponentially back off
                sys.exc_clear()

# Kafka Producer
#time.sleep(10)

#producer = KafkaProducer(bootstrap_servers=os.environ["KAFKA_HOST_PORT"],compression_type='lz4',acks=1,retries=30,linger_ms=100,retry_backoff_ms=1000,value_serializer=msgpack.dumps)
producer = KafkaProducer(bootstrap_servers=os.environ["KAFKA_HOST_PORT"],compression_type=os.environ["COMPRESSION_TYPE"],acks= int(os.environ["ACKS"]), retries=int(os.environ["RETRIES"]),linger_ms=int(os.environ["LINGER_MS"]),retry_backoff_ms=int(os.environ["RETRY_BACKOFF_MS"]),value_serializer=msgpack.dumps)   


def main():
    appParams = {}
    userParams = []
    try:
        # App parameters
        appParams["name"] = os.environ["APP_PARAMS_NAME"]
        appParams["key"] = os.environ["APP_PARAMS_KEY"]
        appParams["secret"] = os.environ["APP_PARAMS_SECRET"]

        #Bounding boxes
        bounding_box_1 = os.environ["BOUNDING_BOX_1"]
        lyst_bb_1 = bounding_box_1.split(',')

        bounding_box_2 = os.environ["BOUNDING_BOX_2"]
        lyst_bb_2 = bounding_box_2.split(',')

        bounding_box_3 = os.environ["BOUNDING_BOX_3"]
        lyst_bb_3 = bounding_box_3.split(',')

        bounding_box_4 = os.environ["BOUNDING_BOX_4"]
        lyst_bb_4 = bounding_box_4.split(',')

        bounding_box_5 = os.environ["BOUNDING_BOX_5"]
        lyst_bb_5 = bounding_box_5.split(',')


       # User parameters
        userParams=[[lyst_bb_1[0], lyst_bb_1[1], lyst_bb_1[2], lyst_bb_1[3], lyst_bb_1[4], lyst_bb_1[5]], [lyst_bb_2[0], lyst_bb_2[1], lyst_bb_2[2], lyst_bb_2[3], lyst_bb_2[4], lyst_bb_2[5]], [lyst_bb_3[0], lyst_bb_3[1], lyst_bb_3[2], lyst_bb_3[3], lyst_bb_3[4], lyst_bb_3[5]], [lyst_bb_1[0], lyst_bb_4[1], lyst_bb_4[2], lyst_bb_4[3], lyst_bb_4[4], lyst_bb_4[5]],[lyst_bb_5[0], lyst_bb_5[1], lyst_bb_5[2], lyst_bb_5[3], lyst_bb_5[4], lyst_bb_5[5]]]
        
    except Exception as x:
        print x
        sys.exit(1)
    harvs = []
    for p in userParams:
       harvs.append(Harvester(p[1], p[0], (p[2], p[3], p[4], p[5]), appParams)) 

    numHarvs = len(harvs)
 
    for h in harvs:
        h.start()
        
    while len(harvs) > 0: 
        try:
            for h in range(numHarvs):
                harvs[h].join(1)
                if harvs[h].isAlive() == False:
                    harvs[h] = Harvester(userParams[h][1], userParams[h][0], (userParams[h][2], userParams[h][3], userParams[h][4], userParams[h][5]), appParams)
                    harvs[h].start()
        except KeyboardInterrupt: #allows us to cntl-c
            producer.flush()
            producer.close()
            sys.exit(2)
            
    
# Calling name    
if __name__ == "__main__":
    main()
