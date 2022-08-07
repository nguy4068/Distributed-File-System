import java.util.*;
import java.io.*;
import org.apache.thrift.TException;
import org.apache.thrift.transport.TTransportException;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;
import client.*;

public class ClientServiceHandler implements ClientService.Iface{
    Timer timer;
    Client controller;
    public ClientServiceHandler(Timer timer, Client controller){
        this.timer = timer;
        this.controller = controller;
    }
    public void finishWrite(String taskname){
        long endTime = System.currentTimeMillis();
        long duration = endTime - timer.task_info.get(taskname);
        String message = "Task " + taskname + " completed in " + duration + " ms";
        controller.printMessage(message);
        timer.task_info.remove(taskname);
    }
}