import org.apache.thrift.server.TServer.Args;
import org.apache.thrift.server.TServer;
import org.apache.thrift.server.TThreadPoolServer;
import org.apache.thrift.server.TSimpleServer;
import org.apache.thrift.transport.TServerSocket;
import org.apache.thrift.transport.TServerTransport;
import fileserver.*;
import java.util.Scanner;
import java.io.*;
import java.net.*;
/**
Class to listen for connection to node
 */
public class FileServer{
	public static FileServerServiceHandler file_server_handler;
	public static FileServerService.Processor processor;
	public static int our_port = 4068;
	public static String our_host = "localhost";
	public static double default_read_ratio = 0.4;
	public static double default_write_ratio = 0.7;
	public static void main(String[] args){
		try {
			//reads in configuration of its own and the super node config
			String list_server_files = "file_server_config.txt";
		        double read_ratio = default_read_ratio;
		        double write_ratio = default_write_ratio;
			int id = 0;
			if (args.length == 5){
				//Read in super node config and current node configs
				list_server_files = args[0];
				id = Integer.parseInt(args[1]);
				double input_read_ratio = Double.parseDouble(args[2]);
				double input_write_ratio = Double.parseDouble(args[3]);
				double sum = input_read_ratio + input_write_ratio;
				if (input_write_ratio > 0.5 && sum > 1.0){
					read_ratio = input_read_ratio;
					write_ratio = input_write_ratio;
				}

			}
			file_server_handler = new FileServerServiceHandler(list_server_files, read_ratio, write_ratio,id);
			our_port = file_server_handler.port;
			our_host = file_server_handler.IP;
			processor = new FileServerService.Processor(file_server_handler);

		Runnable simple = new Runnable() {
			public void run(){
				simple(processor);
			}
		};
		new Thread(simple).start();
	}catch (Exception x){
		x.printStackTrace();
	}
	}
	//starts listening for RPC
	public static void simple(FileServerService.Processor processor){
		try{
			TServerTransport serverTransport = new TServerSocket(our_port);
			TThreadPoolServer server = new TThreadPoolServer(new TThreadPoolServer.Args(serverTransport).processor(processor));
			//System.out.println("Starting a multithreaded compute node server...");
			server.serve();
		}catch (Exception e){
			e.printStackTrace();
		}
	}
}
