package client;

import model.FileDataResponseType;
import model.FileDescriptor;
import model.FileListResponseType;
import model.Network;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Scanner;

public class Client {
    //how to tell start and end bytes.
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            throw new IllegalArgumentException("ip:port is mandatory");
        }
        String[] adr1 = args[0].split(":");
        String ip1 = adr1[0];
        int port1 = Integer.parseInt(adr1[1]);

        String[] adr2 = args[1].split(":");
        String ip2 = adr2[0];
        int port2 = Integer.parseInt(adr2[1]);

        ClientManager inst1 = new ClientManager(new Network(ip1, port1));
        ClientManager inst2 = new ClientManager(new Network(ip2, port2));
        FileListResponseType response1 = inst1.getFileList();
        FileListResponseType response2 = inst1.getFileList();
        System.out.println("File List: ");
        for (FileDescriptor file : response1.getFileDescriptors()) {
            System.out.println(file.getFile_id() + " - " + file.getFile_name());
        }
        System.out.println("Enter a number:");
        Scanner scanner = new Scanner(System.in);
        int fileId = scanner.nextInt();
        System.out.println("File " + fileId + " has been selected. Getting the size information...");

        long size = inst1.getFileSize(fileId);
        System.out.println("File +"+ fileId+ " is " + size + " bytes. Starting to download...");

        long startByte=1;
        boolean switch_=true;
        boolean downloadFinished=false;
        ArrayList<FileDataResponseType> response= new ArrayList<>();
        FileDataResponseType file=new FileDataResponseType(3,fileId,startByte,size,new byte[(int) size]);
        long startTime = System.currentTimeMillis();
        long endTime;
        while(!downloadFinished){
            if(switch_) {
                ClientManager inst = new ClientManager(new Network(ip1, port1));
                response.addAll(inst.getFileData(fileId, startByte, size));

                if((response.get(response.size()-1).getEnd_byte())<size){
                    startByte=response.get(response.size()-1).getEnd_byte()+1;
                    switch_=false;
                }
                else{
                    downloadFinished=true;
                }
            }
            else{
                ClientManager inst = new ClientManager(new Network(ip2, port2));
                response.addAll(inst.getFileData(fileId, startByte, size));
                if((response.get(response.size()-1).getEnd_byte())<size){
                    startByte=response.get(response.size()-1).getEnd_byte()+1;
                    switch_=false;
                }
                else{
                    downloadFinished=true;

                }
            }

        }
        endTime = System.currentTimeMillis();
        long elapsedTime = endTime - startTime;
        System.out.println("time: "+elapsedTime+ "ms");
        /**
         * Combinging all responses into one response*/


        for (int i=0;i<response.size();i++) {
            file.addData(response.get(i).getData(),response.get(i).getStart_byte(),response.get(i).getEnd_byte());
        }

        try {
            FileOutputStream fos = new FileOutputStream("output.txt");
            fos.write(file.getData());
            fos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

}
