#!/bin/bash

#ENV Variables that need to be set:
#	CLIENT_1 (optionally CLIENT_2) = client machines running tsung
#	TSUNG_CONFIG = path to tsung config file to use
#	TSUNG_TIME = time to let tsung run for before stopping
#	OUTPUT_PREFIX = prefix to name output files (log and stats)
#	LT_HOST = host to be load tested
#	LT_PORT = port of host to be load tested

TEMP_CONF="tsung_conf.xml"
TSUNG_BIN="/usr/local/bin/tsung"
rm -f $TEMP_CONF
#rm -f ./loadtesting/tsung*.*

if [ -z "$CLIENT_1" ]; then
	echo "Need to specify \$CLIENT_1!"
	exit 1
fi

if [ -z "$TSUNG_CONFIG" ]; then
	echo "Need to specify \$TSUNG_CONFIG!"
	exit 1
fi

if [ -z "$TSUNG_LOG" ]; then
	echo "Need to specify \$TSUNG_LOG!"
	exit 1
fi

if [ -z "$TSUNG_TIME" ]; then
	echo "Need to specify \$TSUNG_TIME (minutes)!"
	exit 1
fi

if [ -z "$OUTPUT_PREFIX" ]; then
	echo "Need to specify \$OUTPUT_PREFIX!"
	exit 1
fi

if [ -z "$LT_HOST" ]; then
	echo "Need to specify \$LT_HOST!"
	exit 1
fi

if [ -z "$LT_PORT" ]; then
	echo "Need to specify \$LT_PORT!"
	exit 1
fi

export C1="<client host='$CLIENT_1'/>"
export C2="<client host='$CLIENT_2'/>"
export M1="<monitor host='$CLIENT_1'/>"
export M2="<monitor host='$CLIENT_2'/>"

cat $TSUNG_CONFIG | sed "s|</clients>|$C1\n</clients>|g" | sed "s|</monitoring>|$M1\n</monitoring>|g" > $TEMP_CONF

if [ -n "$CLIENT_2" ]; then
	cat $TEMP_CONF | sed "s|</clients>|$C2\n</clients>|g" | sed "s|</monitoring>|$M2\n</monitoring>|g" > $TEMP_CONF.2
	mv $TEMP_CONF.2 $TEMP_CONF
fi

# Insert Host/Port into config file
cat $TEMP_CONF | sed "s|\[HOST\]|$LT_HOST|g" | sed "s|\[PORT\]|$LT_PORT|g" > $TEMP_CONF.2
mv $TEMP_CONF.2 $TEMP_CONF

# Clean up the .2 temp file
rm -f $TEMP_CONF.2

mkdir -p $TSUNG_LOG
$TSUNG_BIN -f $TEMP_CONF -l $TSUNG_LOG/ start &
SECONDS=`echo "$TSUNG_TIME 60 * p" | dc`
sleep $SECONDS
echo "Stopping tsung...."
$TSUNG_BIN stop

most_recent=`ls -1t $TSUNG_LOG/ | head -n 1`
most_recent_path="$TSUNG_LOG/$most_recent"
echo "Analysing statistics...."
pushd $most_recent_path
../../utils/tsung_log_parser.pl "$TSUNG_LOG" "$TSUNG_TIME" > tsung_stats.txt
popd
cp $most_recent_path/tsung_stats.txt ./loadtesting/$OUTPUT_PREFIX.txt
cp $most_recent_path/tsung_conf.xml ./loadtesting/$OUTPUT_PREFIX.xml
echo "Done."

