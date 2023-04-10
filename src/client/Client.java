package client;

import model.FileDescriptor;
import model.FileListResponseType;
import model.Network;

import java.util.Scanner;

public class Client {
    //how to tell start and end bytes.
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            throw new IllegalArgumentException("ip:port is mandatory");
        }
        String[] adr1 = args[0].split(":");
        String ip1 = adr1[0];
        int port1 = Integer.parseInt(adr1[1]);
        ClientManager inst = new ClientManager(new Network(ip1, port1));
        FileListResponseType response = inst.getFileList();
        System.out.println("File List: ");
        for (FileDescriptor file : response.getFileDescriptors()) {
            System.out.println(file.getFile_id() + " - " + file.getFile_name());
        }
        System.out.println("Enter a number:");
        Scanner scanner = new Scanner(System.in);
        int num = scanner.nextInt();
        System.out.println("File " + num + " has been selected. Getting the size information...");
        long size = inst.getFileSize(num);
        System.out.println("File 2 is " + size + " bytes. Starting to download...");

        inst.getFileData(num, 1, size);
    }
}
