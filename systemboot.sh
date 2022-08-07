#!/bin/bash
count=0
USERID=`whoami`
while read p; do
   varname=`echo $p | cut  -f 1 -d' '`
   varval=`echo $p | cut --complement -f 1 -d' '`
   setenvstring="$setenvstring setenv $varname $varval &&"
   if [ $varname == "PROJ_PATH" ] ; then
      PROJ_PATH=`echo $p | cut --complement -f 1 -d' '`
   elif [ $varname == "SLEEP" ] ; then
      SLEEP_TIME=$varval
      echo "sleep time $SLEEP_TIME"
   fi
   count=`echo "scale=0;$count + 1" | bc`
done < env.txt
count=0
while read p; 
do
   echo "read node $p"
   HOST=`echo $p | cut  -f 1 -d' '`
   NODE_COMMAND=`echo "ant fileserver -Did=$count -Doutput=output$count.log"`
   CA=`echo "$setenvstring  cd $PROJ_PATH &&  nohup $NODE_COMMAND & "`
   COMMAND_FILE=`echo "file_server_command$count.sh"`
   echo "ssh -t -n -f $USERID@$HOST \"csh -c '$CA'\" > /dev/null"  > $COMMAND_FILE
   count=`echo "scale=0;$count + 1" | bc`
done < file_server_config.txt

echo "Starting to file server"

a=0
while [ $a -lt $count ]
do
   echo "fire file_server_command$a.sh"
   command_file=`echo "file_server_command$a.sh"`
   source $command_file
   a=`expr $a + 1`
   echo "Sleep $SLEEP_TIME seconds"
   sleep $SLEEP_TIME
done

echo "Sleeping while waiting for nodes to finish joining Chord system"
sleep 15
echo "Nodes finish joining chord, now you can go ahead and run the client"

########################
