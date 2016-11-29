
#
# Tweaks Solr so that, *if* it's run in Kontena, some things work better.
#

# Set the SOLR_HOST to the hostname instead of leaving blank. If left blank, Solr will default to
# the IP address.  But the hostname is both more meaningful if you see it, and
# can be used for a collection replica placement rule.
#
# $HOSTNAME works in Kontena as well as in many other environments.
# e.g. bop-solr-1.kontena.local
export SOLR_HOST=$HOSTNAME