# TODO the source is not yet published; get dkakkar to do it
#   https://github.com/dkakkar/Twitter-Sentiment-Classifier/pull/3
FROM dkakkar/twitter-sentiment-classifier

MAINTAINER David Smiley <dsmiley@apache.org>

RUN apt-get update && \
    apt-get install ucspi-tcp -y && \
    apt-get clean

ENV PORT=1234

EXPOSE $PORT

# TODO would be nice to fail fast if the data file isn't present. Or maybe actually run the
#    program to ensure it doesn't fail for that and other reasons.

# (-u is important)
CMD tcpserver -v -D 0.0.0.0 $PORT python3 -u sentiment.py