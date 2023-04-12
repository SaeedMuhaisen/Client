package client;

import model.FileDescriptor;
import model.FileListResponseType;
import model.Network;

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
        int num = scanner.nextInt();
        System.out.println("File " + num + " has been selected. Getting the size information...");
        long size = inst1.getFileSize(num);
        System.out.println("File 2 is " + size + " bytes. Starting to download...");

        inst1.getFileData(num, 1, size);

    }
}
