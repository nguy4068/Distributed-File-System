import java.util.*;
import java.io.*;
import fileserver.*;
import client.*;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import org.apache.thrift.TException;
import org.apache.thrift.transport.TTransportException;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;
//Class server is a  facade to handle RPC request from file coordinator
class Server{
	String IP;//ip of the server
	int port;//port that the server runs on
	boolean is_coordinator;
	public Server(String info){
		//info comprises of IP address, port number, and a flag to indicate if the server is also coordinator or not
		String[] tokens = info.split(" ");
		this.IP = tokens[0];
		this.port = Integer.parseInt(tokens[1]);
		int flag = Integer.parseInt(tokens[2]);
		if (flag == 0){
			this.is_coordinator = false;
		}else{
			this.is_coordinator = true;
		}
	}
	//Handle RPC for writing to a file server
	public Result write(String filename, String content){
		try{
			TTransport connection = new TSocket(IP, port);
			TProtocol protocol = new TBinaryProtocol(connection);
			connection.open();
			FileServerService.Client stub = new FileServerService.Client(protocol);
			Result result = stub.write(filename, content);
			connection.close();
			return result;
		}catch (TException e){
			System.out.println("Failed to perform remote write operation on node" + this.IP);
			e.printStackTrace();
			return null;
			
		}
	} 
	//forward write request to the coordinator if the file server is not coordinator itself
	public void forwardWrite(String sourceIP, int port, String filename, String content){
		try{
			TTransport connection = new TSocket(this.IP, this.port);
			TProtocol protocol = new TBinaryProtocol(connection);
			connection.open();
			FileServerService.Client stub = new FileServerService.Client(protocol);
			stub.forwardWrite(sourceIP, port,filename, content);
		}catch (TException e){
			System.out.println("Unable to forward write request to " + this.IP + " with port " + this.port);
			e.printStackTrace();
		}
	}
	//forward read request to the coordinator if the file server is not coordinator itself
	public Result forwardRead(String sourceIP, int sourcePort ,String filename){
		try{
			TTransport connection = new TSocket(IP, port);
			TProtocol protocol = new TBinaryProtocol(connection);
			connection.open();
			FileServerService.Client stub = new FileServerService.Client(protocol);
			Result result = stub.forwardRead(sourceIP, sourcePort, filename);
			return result;
		}catch (TException e){
			System.out.println("Unable to forward write request to" + this.IP);
			e.printStackTrace();
			return null;
		}
	}
	//get file version from a provided file names
	public int getFileVersion(String filename){
		try{
			TTransport connection = new TSocket(IP, port);
			TProtocol protocol = new TBinaryProtocol(connection);
			connection.open();
			FileServerService.Client stub = new FileServerService.Client(protocol);
			int result = stub.getFileVersion(filename);
			return result;
		}catch (TException e){
			System.out.println("Unable to get file version on remote file server " + this.IP);
			e.printStackTrace();
			return -1;
		}
	}
	//Update the file version for a provided file name
	public void setFileVersion(String filename, int version){
		try{
			TTransport connection = new TSocket(IP, port);
			TProtocol protocol = new TBinaryProtocol(connection);
			connection.open();
			FileServerService.Client stub = new FileServerService.Client(protocol);
			stub.setFileVersion(filename, version);
		}catch (TException e){
			System.out.println("Unable to set file version for " + filename);
			e.printStackTrace();
		}
	}
	/**
	 @param sourceIP the client host that initiates the write request to file server
	 @param sourcePort port of client host, for later ACK message
	 @param filename name of file that is going to be writen to file server
	 @param content the content of the file
	 */
	public Result checkWrite(String sourceIP, int sourcePort, String filename, String content){
		try{
			TTransport connection = new TSocket(IP, port);
			TProtocol protocol = new TBinaryProtocol(connection);
			connection.open();
			FileServerService.Client stub = new FileServerService.Client(protocol);
			return stub.checkWrite(sourceIP, sourcePort, filename, content);
		}catch (TException e){
			System.out.println("Failed to check write for server " + this.IP);
			e.printStackTrace();
			return null;
		}
	}
  /**
   This function is first called by client as client does not know if the contacting file server is a coordinator or not
   @param file file that client wants to read from
   */
  public Result checkRead(String filename){
		try{
			TTransport connection = new TSocket(IP, port);
			TProtocol protocol = new TBinaryProtocol(connection);
			connection.open();
			FileServerService.Client stub = new FileServerService.Client(protocol);
			return stub.checkRead(filename);
		}catch (TException e){
			System.out.println("Failed to check read");
			e.printStackTrace();
			return null;
		}
	}
	/**
	function used to directly read the file when the file server knows that it's in the read quorum
	 */
	public Result read(String filename){
		try{
			TTransport connection = new TSocket(IP, port);
			TProtocol protocol = new TBinaryProtocol(connection);
			connection.open();
			FileServerService.Client stub = new FileServerService.Client(protocol);
			return stub.read(filename);
		}catch (TException e){
			System.out.println("Failed to check read");
			e.printStackTrace();
			return null;
		}
				
		
		
	}
	
};
//Class handle logic of coordinator and file server, and listening to incoming RPC call from
//client and other file servers
public class FileServerServiceHandler implements FileServerService.Iface{
	double read_ratio;//read ratio to determine number of nodes in the read quorum
	int num_reads;//number of servers in the read quorum
	int num_writes = 0;//number of servers in the write quorum
	int num_servers = 0;//total number of file servesr
	boolean is_coordinator = false;//indicate if the current file server is also a coordinator or not
	int id = 0;//id of the file server
	List<Server> server_peers;//list of other file servers
	boolean acquired_lock = false;
	Server coordinator = null;//Coordinator, each file server will have information of coordinator if it's not coordinator
	HashMap<String,Integer> file_info;//Map to store information of files
	HashMap<String,Boolean> file_sync;//Map to store state of file (whether it's currently read or written or not)
	String IP = "localhost";//IP address of the current file server
	int port = 4068;//port of current file server
	boolean spinning = false;
	String message;
	String offline_storage;
	public FileServerServiceHandler(String server_list, double read_ratio, double write_ratio, int id){
		File file = new File(server_list);
		this.id = id;
		this.file_info = new HashMap<>();
		this.file_sync = new HashMap<>();
		//read in cache to obtain the file version first
		offline_storage = "cache" + id + ".txt";
		try{
			File storage_file = new File(offline_storage);
			Scanner scanner = new Scanner(storage_file);
			while (scanner.hasNext()){
				String info_file = scanner.nextLine();
				String[] tokens = info_file.split(" ");
				String filename = tokens[0];
				int version = Integer.parseInt(tokens[1]);
				file_info.put(filename, version);
			}
		}catch(FileNotFoundException e){
			System.out.println("No cache for server " + id);
		}
		try{
			//scanning in list of file servers
			Scanner scanner = new Scanner(file);
			this.server_peers = new ArrayList<>();
			while (scanner.hasNext()){
				String info = scanner.nextLine();
				Server server = new Server(info);
				if (num_servers == id){
					this.is_coordinator = server.is_coordinator;
					this.IP = server.IP;
					this.port = server.port;
				}
				server_peers.add(server);
				//get the coordinator contact point
				if (server.is_coordinator){
					this.coordinator = server;
				}
				num_servers++;
			}
			System.out.println("num servers " + num_servers);
			double nr = read_ratio*num_servers;
			double nw = write_ratio*num_servers;
			System.out.println("write ratio " + write_ratio);
			System.out.println("read ratio " + read_ratio);
			//Total number of nodes in read quorum
			this.num_reads = (int) (read_ratio*num_servers);
			//Total number of nodes in write quorum
			this.num_writes = (int) (write_ratio*num_servers);
			//If tehe obtained value is a float, not integer, then do round up
			if (num_reads < nr && num_reads < num_servers){
				num_reads++;
			}
			if (num_writes < nw && num_writes < num_servers){
				num_writes++;
			}
			System.out.println("Num writes " + this.num_writes);
			System.out.println("Num reads " + this.num_reads);
		}catch(FileNotFoundException e){
			System.out.println("Invalid server list file name");
		}	
	}
	//Perform read quorum logic
	public Result performReadQuorum(String sourceIP, int sourcePort, String filename){
		int count = num_reads;
		// since it is the coordinator, assemble the quorum
		String contents = "";
		List<Server> server_quorum = new ArrayList<>();
		//randomly select file servers for read quorum
		while (count > 0) {
			int size = server_peers.size();
			int index = (int) Math.random() * size;
			Server server = server_peers.get(index);
			server_peers.remove(index);
			server_quorum.add(server);
			count--;
		}
		Server most_recent_server = null;
		int most_recent_version = -1;
		// find the most recent version number from the quorum
		for (int i = 0; i < num_reads; i++) {
				server_peers.add(server_quorum.get(i));
				Server server = server_quorum.get(i);
				int version = 0;
				if (server.IP.equals(this.IP) && server.port == this.port){
					version = this.getFileVersion(filename);
				}else if(sourceIP != null && (!sourceIP.equals(server.IP) || sourcePort != server.port)){
					version = server.getFileVersion(filename);
				}else if (sourceIP == null){
					version = server.getFileVersion(filename);
				}
				if (version > most_recent_version) {
			  		most_recent_version = version;
			  		most_recent_server = server;
				}

			// once we find the most recent version of a file, we read it from the
			// most recent server

		}
		//Preparing for the return result
		Result result = new Result();
		if (most_recent_version == -1){
			System.out.println("Can't find any file from quorum with that file");
			result.resultRead = "FAIL: file does not exist";
			return result;
		}
		System.out.println("Read file from " + most_recent_server.IP + " with version " + most_recent_version);
		//If the file server itself has highest file version, then don't need to make RPC call
		if (most_recent_server.IP.equals(this.IP) && most_recent_server.port == this.port){
			result = this.read(filename);

		}else if(sourceIP!=null && most_recent_server.IP.equals(sourceIP) && most_recent_server.port == sourcePort){
			//need to read from the request file server
			result.needread = true;
			result.readVersion = most_recent_version;
		}else{
			//Read from a remote file server
			result = most_recent_server.read(filename);
			result.readVersion = most_recent_version;
		}
		return result;
	}
	//Forward read request from file server to a coordinator
	public Result forwardRead(String sourceIP, int sourcePort, String filename){
		return performReadQuorum(sourceIP, sourcePort, filename);
	}
	//Check read will be called by client first because it does not know if the contacting server is coordinator
	public Result checkRead(String filename){
			Result r = new Result();
		    if (is_coordinator) {
			   System.out.println("I am the coordinator and I will perform read quorum logic");
		       r = performReadQuorum(null, 0, filename);
			   return r;
		    } else {
				// the server is not a coordinator
				// forward the request to the coordinator
				System.out.println("I am not the coordinator and I will forward the read request");
				if (this.coordinator != null) {
					Result result = coordinator.forwardRead(this.IP, this.port, filename);
					if (result.needread && (this.getFileVersion(filename) > result.readVersion)){	
						System.out.println("I will need to read the file as well");
						result = read(filename);
					}
					return result;
				}
		    }
		return null;
	}
	//Started to read in the file
	public Result read(String filename){
		//acquire the lock first
		while (test_and_set(filename)){
			//spinning
		}
		Result r = new Result();
    	if (!file_info.containsKey(filename)) {
      		// if the file does not exist, we don't proceed
      		file_sync.put(filename, false);
			r.resultRead = "File does not exist";
      		return r;
   		}

		try {
			//get the correct file name for the file server, for example if the
			//file name is hello.txt, then file server 0 should have saved it as hello0.txt
			String[] tokens = filename.split("\\.");
			String name = "";
			for (int i = 0; i < tokens.length - 2; i++){
				name = name + tokens[i] + ".";
			}
			if (tokens.length >= 2){
				name = name + tokens[tokens.length - 2];
				name = name + this.id + "." + tokens[tokens.length-1];
				System.out.println("read in file " + name);
			}	
			File file = new File(name);
			boolean exist = file.exists();
			//If the file exists then open and read the content of the file
			if (exist){
				BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file)));
				String content = "";
				String line;
				while ((line = br.readLine()) != null) {
					content += line + "\n";

				}
				//release the lock here
				System.out.println("Release read lock due to successful read");
				file_sync.put(filename,false);
				//return success message with content
				r.resultRead =  "SUCCESS: " + content;
				r.readVersion = this.getFileVersion(filename);
				return r;
			}else{
				//File does not exist send FAIL message
				System.out.println("File does not exist");
				r.resultRead = "FAIL: File does not exist";
				return r;
			} 
		}catch (Exception ex) {
				System.out.println("Release read lock due to fail read");
				file_sync.put(filename,false);
				r.resultRead = "FAIL: File does not exist";
				return r;

		}
    	
}
   //only coordinator will be invoked with this function, oneway, does not need to return
	public void forwardWrite(String sourceIP, int sourcePort, String filename, String content){
		   performWriteQuorum(sourceIP, sourcePort, filename, content);
	}
	public void announceFinish(String message){
		System.out.println("Coordinator finished with message " + message);
		this.message = message;
		System.out.println("Update spinning to false to break the loop");
		this.spinning = false;

	}
	//Function will be called when all writes in the write quorum have been done
	public void ACK(String destIP, int destPort, String message){
		try{
			TTransport connection = new TSocket(destIP, destPort);
			TProtocol protocol = new TBinaryProtocol(connection);
			connection.open();
			ClientService.Client stub = new ClientService.Client(protocol);
			System.out.println("Send ACK message to file server " + destIP);
			stub.finishWrite(message);
			connection.close();
		}catch (TException e){
			System.out.println("Failed to send ACK to client" + destIP);
			e.printStackTrace();
		}

	}
	//Only coordinator will perform this function
	/**
	Perform the write quorum logic for the file system
	@param sourceIP ip address of the client that requests the write operation
	@param sourcePort port number of the client that requests the write operation
	@param filename name of the file that should have content be updated
	@param content content that the file should update
	 */
	public void performWriteQuorum(String sourceIP, int sourcePort, String filename, String content){
		System.out.println("I am the coordinator and I will handle the write quorum logic");
		int count = num_writes;
		int selected = 0;
		List<Server> server_quorum = new ArrayList<>();
		System.out.println(server_peers.size());
		System.out.println("need " + count);
		//Randomly choose nodes for the write quorum
		while (count > 0){
			int size = server_peers.size();
			int index = (int)(Math.random()*size);
			Server server = server_peers.get(index);
			//only add if it's not a source initiation
			System.out.println("valid write quorum to add " + server.IP);
			server_peers.remove(index);
			server_quorum.add(server);
			selected++;
			count--;
			
		}
		System.out.println("Finish choosing quorum now start to send message to quorum");
		int oldest_version = 0;
		int success_node = server_quorum.size();
		boolean success = true;
		//loop through selected nodes in the quorum and send write request to each of them
		for (int i = 0; i < selected; i++){
			//add back the node to the server quorum
			server_peers.add(server_quorum.get(i));
			Server server = server_quorum.get(i);
			Result result;
			//If the selected server is the current server, then don't make RPC call
			if (server.IP.equals(this.IP) && server.port == this.port){
				result = this.write(filename,content);
			}else{
				//If the selected server is remote server, then make RPC call
				result = server.write(filename, content);
			}
			//If result write is true, then it means the new content has been written to
			//the file in that file server, keep track on the oldest version of the file
			if (result.resultWrite){
				System.out.println("Update success for server file " + server.IP);
				int version = server.getFileVersion(filename);
				if (version > oldest_version){
						oldest_version = version;
				}
			}else{
				//If not, then the file server fail to update the file, remove
				//that server from the quorum, and proceding normally to the next ones
				System.out.println("Failed to update for one of the server " + server.IP);
				success = false;
				server_quorum.remove(i);
				success_node--;
			}
		}		
		System.out.println("Finish sending files to target file server");
		System.out.println("Update file version number for all servers in quorum");
		//After finishing sending all files, then loop through list of nodes that successfully
		//update the file in their storage, and tell them to update the version of the file to the oldest one
		for (int i = 0; i < success_node; i++){
			Server server = server_quorum.get(i);
			if (server.IP.equals(this.IP) && server.port == this.port){
				//directly update here
				this.setFileVersion(filename,oldest_version);
			}else{
				//make RPC call here
				server.setFileVersion(filename, oldest_version);

			}
			
		}
		//if the request was forwarded from a client then send back the ACK to the request client
		if (sourceIP != null){
			System.out.println("Broadcast message back to the contactor");
			if (success){
				ACK(sourceIP, sourcePort, "write " + filename);
			}else{
				ACK(sourceIP, sourcePort, "Failed to update some nodes in quorum for file " + filename);
			}
		}else{
			if (success){
				this.message =  "write " + filename;
			}else{
				this.message = "Failed to update some nodes in quorum for file " + filename;
			}
		}
		

	}
	//Function first invoked by the client
	/**
	@param sourceIP the IP address of client
	@param sourcePort the port of the client
	@param filename file name that should be updated with new content
	@param content new content
	 */
	public Result checkWrite(String sourceIP, int sourcePort, String filename, String content){
		Result r = new Result();
		//If the server is the coordinator then directly performs the write quorum logic
		if (is_coordinator){
			performWriteQuorum(null,0,filename,content);
			if (this.message.equals("write " + filename)){
				r.status = "finish";
				r.resultWrite = true;
			}else{
				r.status = "finish";
				r.resultWrite = false;
			}
		}else{
			//If the server is not the coordinator, then forward the request to the coordinators
				//not coordinator, forward request to the coordinator
		   		System.out.println("Wait for coordinator to handle write quorum logic");
				if (this.coordinator != null){
					//one way RPC call
					coordinator.forwardWrite(sourceIP,sourcePort,filename, content);
					r.status = "In progress";
				}
		}
		return r;
		
	}
	//Function to get version of a filename
	public int getFileVersion(String filename){
		System.out.println("Claim the lock to read file version");
		//acquire the lock first before reading file version
		while (test_and_set(filename)){

		}
		if (file_info.containsKey(filename)){
			int version = file_info.get(filename);
			file_sync.put(filename,false);
			return version;
		}else{
			file_sync.put(filename,false);
			return -1;
		}
	}
	//Update the file version
	public void setFileVersion(String filename, int version){
			while (test_and_set(filename)){

			}
			file_info.put(filename, version);
			file_sync.put(filename,false);
	}
	//Function acts as a check for semaphore to avoid concurrent read and write to a file
	public boolean test_and_set(String filename){
		if (!file_sync.containsKey(filename)){
			return false;
		}
		boolean temp = file_sync.get(filename);
		file_sync.put(filename,true);
		return temp;
	}
	//Function to update the content of the file
	//Will be called for each file server selected in the quorum
	public Result write(String filename, String content){
		//Claim the lock first
		while (test_and_set(filename)){
			//spinning
		}
		Result r = new Result();
		System.out.println("Claim the lock");
		if (!file_info.containsKey(filename)){
			//First time the server see the file, save the file
			System.out.println("First time seeing the file");
			file_info.put(filename,0);
			try{
				System.out.println("Write more file info to local storage");
				FileWriter file = new FileWriter(offline_storage, true);
				String new_file = filename + " " + 0 + "\n";
				file.write(new_file);
				file.close();
			}catch (Exception e){
				System.out.println("Can not append to local cache");
			}
		}
		//Formatting the correct file name to save in local storage
		//If the file name is hello.txt, then it will be saved as hello0.txt in file server 0
		String[] tokens = filename.split("\\.");
		String name = "";
		for (int i = 0; i < tokens.length - 2; i++){
			name = name + tokens[i] + ".";
		}
		if (tokens.length >= 2){
			name = name + tokens[tokens.length - 2];
			name = name + this.id + "." +tokens[tokens.length-1];
			System.out.println(name);
		}else{
			name = filename + this.id;
			System.out.println(name);
		}
		
		try{
			//perform writing file on local storage
			System.out.println("Open file to write");
			File file = new File(name);
			file.delete();
			FileWriter fw = new FileWriter(name);
			fw.write(content);
			fw.close();
			int old_version_num = file_info.get(filename);
			old_version_num++;
			file_info.put(filename,old_version_num);
			System.out.println("Release the lock");
			file_sync.put(filename, false);
			r.resultWrite = true;
			return r;
		}catch (Exception e){
			//If error happens then change write result to false
			System.out.println("Release the lock");
			file_sync.put(filename,false);
			e.printStackTrace();
			r.resultWrite = false;
			return r;
		}
	}
	
	
}
