#!/bin/bash
USERID=`whoami`
count=0
while read p; 
do
  HOST=`echo $p | cut  -f 1 -d' '`
  PORT=`echo $p | awk '{print $2}'`
  echo "ssh -t -n -f $USERID@$HOST  'lsof -t -i:$PORT | xargs -r kill'" > kill_file_server.sh
  source kill_file_server.sh
  count=`echo "scale=0;$count + 1" | bc`
done < file_server_config.txt
