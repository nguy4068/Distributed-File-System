ssh -t -n -f nguy4068@kh4250-05.cselabs.umn.edu "csh -c ' setenv JAVA_LIBS /project/nguy4068/thrift-0.15.0/lib/java/build/libs && setenv JAVA_DEP /project/nguy4068/thrift-0.15.0/lib/java/build/deps && setenv THRIFT_LIB_PATH /project/nguy4068/thrift-0.15.0/compiler/cpp/thrift && setenv READ_RATIO 0.9 && setenv WRITE_RATIO 0.6 && setenv FILE_SERVER_CONFIG file_server_config.txt && setenv PROJ_PATH /home/nguy4068/CSCI5105/P3/src && setenv SLEEP 0 &&  cd /home/nguy4068/CSCI5105/P3/src &&  nohup ant fileserver -Did=1 -Doutput=output1.log & '" > /dev/null
