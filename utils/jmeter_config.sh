#!/bin/bash
$TEMP_CONF="$1"

# Insert Host/Port into config file
cat $TEMP_CONF | sed "s|\[HOST\]|$LT_HOST|g" | sed "s|\[PORT\]|$LT_PORT|g" > $TEMP_CONF.2
mv $TEMP_CONF.2 $TEMP_CONF

# Clean up the .2 temp file
rm -f $TEMP_CONF.2
