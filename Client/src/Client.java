import org.apache.thrift.TException;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.TTransportException;
import org.apache.thrift.transport.TServerTransport;
import org.apache.thrift.server.TServer.Args;
import org.apache.thrift.server.TThreadPoolServer;
import org.apache.thrift.transport.TServerSocket;
import java.io.*;
import java.util.*;
import fileserver.*;
import client.*;
/**
Main class the user should interact with the system from
 */
class Timer{
	HashMap<String, Long> task_info;
	public Timer(){
		task_info = new HashMap<>();
	}
}
//Server facade to handle RPC call to file server from client
class ServerContact{
	String IP;
	int port;
	public ServerContact(String info){
		String[] tokens = info.split(" ");
		this.IP = tokens[0];
		this.port = Integer.parseInt(tokens[1]);
	}
	//Send write request from client to file server
	public Result write(String sourceIP, int sourcePort, String filename, String content){
		try{
			TTransport connection = new TSocket(IP, port);
			TProtocol protocol = new TBinaryProtocol(connection);
			connection.open();
			FileServerService.Client stub = new FileServerService.Client(protocol);
			System.out.println("file content " + content);
			Result result = stub.checkWrite(sourceIP, sourcePort, filename, content);
			connection.close();
			return result;
		}catch (TException e){
			System.out.println("Failed to perform remote write operation on node" + this.IP);
			e.printStackTrace();
			return null;
			
		}
		
		
	} 
	//Send read request from client to server
	public Result read(String filename){
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
	
};
//Client, where user can interact and send read or write request
public class Client {
	public static List<ServerContact> file_servers;
	public static int numservers;
	public static String IP = "kh4250-03.cselabs.umn.edu";
	public static int port = 4068;
	public static Thread listenThread;
	public static Thread performThread;
	public static volatile boolean run = true;
	public static TThreadPoolServer server;
	//Function to print out ACK message from coordinator
	public void printMessage(String message){
		System.out.println(message);
	}
	public static void main(String[] args){
		//Timer is used to keep track on the time that a write request is started and handled
		Timer timer = new Timer();
		Client controller = new Client();
		file_servers = new ArrayList<>();
      		if (args.length == 3) {
      			
        		// read the file and put the defs and meanings into the DHT
        		String filename = args[0];
				IP = args[1];
				port = Integer.parseInt(args[2]);
        		File file = new File(filename);
        		try{
        			//scanning in supernode information
        			Scanner s = new Scanner(file);
        			System.out.println("Scanning in file server information");
        			while (s.hasNext()){	
        				String info = s.nextLine();
        				ServerContact server = new ServerContact(info);
        				file_servers.add(server);
        			}
					//start client work
					numservers = file_servers.size();
					ClientServiceHandler client_handler = new ClientServiceHandler(timer,controller);
					ClientService.Processor processor = new ClientService.Processor(client_handler);
					Runnable listen = new Runnable() {
						public void run(){
							clientListen(processor);
						}
					};
					Runnable perform = new Runnable(){
						public void run(){
							startClient(timer);
						}
					};
					//start thread to perform input work
					performThread = new Thread(perform);
					//start thread to perform listening work
					listenThread = new Thread(listen);
					performThread.start();
					listenThread.start();
        			
        		}catch (FileNotFoundException e){
        			System.out.println("Wrong file path");
        		}

      		} else {
        		System.out.println("Please provide a file server path");
      		}
  }	
  //Thread to listen to ACK message coming from coordinator
  public static void clientListen(ClientService.Processor processor){
		try{
			TServerTransport serverTransport = new TServerSocket(port);
			server = new TThreadPoolServer(new TThreadPoolServer.Args(serverTransport).processor(processor));
			//System.out.println("Starting a multithreaded compute node server...");
			server.serve();
			
		}catch (Exception e){
			e.printStackTrace();
		}
  }
  //Thread to take in user requirement and process 
  public static void startClient(Timer timer){
  	boolean exit = false;
  	Scanner scanner = new Scanner(System.in);
  	while (!exit){
		System.out.println();
      	System.out.println("Use the following commands to get file, or print to file");
      	System.out.println("1. To get a file: read [filename]");
     	System.out.println("2. To write a file: write [filename]");
      	System.out.println("4. To quit: quit");
      	System.out.println();
  		String command = scanner.nextLine();
		  //if client quits then shut down both threads
  		if (command.equals("quit")){
			run = false;
			server.stop();
  			System.out.println("Bye!");
  			break;
  		}
  		String[] tokens = command.split(" ");
  		if (tokens.length > 2){
  			System.out.println("Too many arguments");
  			continue;
  			
  		}else{
			//else take in the command options
  			String option = tokens[0];
  			String filename = tokens[1];
			//if user want to read a file
  			if (option.equals("read")){
				//randomly choose a contact file server
  				int index = (int)( Math.random()*numservers);
  				ServerContact file_server = file_servers.get(index);
  				System.out.println("Waiting for content");
				long startTime = System.currentTimeMillis();
  				Result read_result = file_server.read(filename);
				if (read_result == null){
					System.out.println("Failed to read file due to lost connection");
					continue;
				}
				long endTime = System.currentTimeMillis();
				long duration = endTime - startTime;
				String result = read_result.resultRead;
				if (result == null){
					System.out.println("You provide wrong file name");
					continue;
				}
  				int space = result.indexOf(" ");
  				int l = result.length();
				//If the file user wants to read does not exist
  				if (result.startsWith("FAIL: ")){
  					System.out.println("You provide wrong file name");
  				}else if (result.startsWith("SUCCESS: ")){
				//Extract the information and print out for users
					int version = read_result.readVersion;
					System.out.println("Read request for " + filename + " was done in " + duration + " ms");
					System.out.println(filename + " has latest version of " + version);
					System.out.println(result);
  					String content = result.substring(space+1,l);
  					try{
						File file = new File(filename);
						file.delete();
						FileWriter fw = new FileWriter(filename);
						fw.write(content);
						fw.close();
						System.out.println("Retrieve the file content successfully, please open file " + filename+ " to check it!");
					}catch (Exception e){
						e.printStackTrace();
					}
  				}
  				
  			}else if(option.equals("write")){
				//If user wants to write a file
  				File file = new File(filename);
  				try{
				    	BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file)));
				    	String content = "";
				    	String line;
				    	while ((line = br.readLine()) != null) {
				      		content += line + "\n";

				    	}
						System.out.println("File name " + filename + " File content " + content);
						//randomly choose a contact file server
				    	int index = (int)(Math.random()*numservers);
				    	ServerContact fileserver = file_servers.get(index);
						System.out.println("Contact server " + fileserver.IP);
						long startTime = System.currentTimeMillis();
						String taskname = "write " + filename;
						timer.task_info.put(taskname,startTime);
				    	Result result = fileserver.write(IP,port,filename, content);
						if (result == null){
							System.out.println("Failed to write to file with null result");
						}else if (result.status.equals("finish")){
							if (result.resultWrite){
								System.out.println("Write to file successfully");
							}else{
								System.out.println("Failed to write to file");
							}
				    		
				    	}
			    	}catch (Exception e){
			    		e.printStackTrace();
			    	}
  			}else{
				System.out.println("Invalid commands");
			}
  		}
  	}
  }

  
	
}
