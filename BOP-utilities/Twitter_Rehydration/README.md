# Overview

This code is useful to get the details of (hydrate) a collection of Tweet IDs. Returns fully-hydrated Tweet objects for up to 100 Tweets per request, as specified by comma-separated values passed to the id parameter. 

# Sample Data

 Refer to sample_input and sample_output folder to see the format of input and output files. 

## How to use the script

The code works for python 2.7.11. To install Python 2 follow instructions here: https://www.python.org/downloads/

Follow the steps below to run the code:

#### Required libraries

To use the code, you must have the following libraries installed:

i. tweepy (https://pypi.python.org/pypi/tweepy)

The required libraries could also be installed from requirements.txt using:

            pip install -r requirements.txt

#### Steps

The code can be run using the steps below:

i. Download Tweet_Rehydration_API.py from src folder and run it from the terminal using:

             python2 Tweet_Rehydration_API.py
             
ii. Provide the following file paths:

![Alt text](https://github.com/dkakkar/Twitter_Rehydration/blob/master/screenshot.png "Optional title")

    idfilepath: Path to file containing tweet ids
    credentialfilepath: Path to file containing User's Twitter credentials such as Consumer Key, Consumer Secret, Oauth Token and Oauth Secret
    outfilepath: Path to output text where fully-hydrated Tweet objects are written

#### Output

The output is a text file whose each line is a fully-hydrated Tweet object corresponding to the input Tweet ID 



