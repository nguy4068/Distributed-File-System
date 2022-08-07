namespace java fileserver
struct Result {
  1: bool needread,
  2: string resultRead,
  3: bool resultWrite,
  4: string status,
  5: i32 readVersion,
}

service FileServerService{
	Result write(1: string filename, 2: string content),
	Result read(1: string filename),
	Result checkWrite(1: string sourceIP, 2: i32 sourcePort, 3: string filename, 4: string content),
	Result checkRead(1: string filename),
	i32 getFileVersion(1: string filename),
	void setFileVersion(1: string filename, 2: i32 version),
	oneway void forwardWrite(1: string sourceIP, 2: i32 sourcePort, 3: string filename, 4: string content),
	Result forwardRead(1: string sourceIP, 2: i32 sourcePort, 3: string filename),
	oneway void announceFinish(1: string result),
}
