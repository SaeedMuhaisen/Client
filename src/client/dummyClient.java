package client;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Scanner;

import model.*;
import model.ResponseType.RESPONSE_TYPES;

public class dummyClient {

    private DatagramPacket receive_packet(int port, InetAddress IPAddress, RequestType req) throws IOException {
        byte[] sendData = req.toByteArray();
        DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, IPAddress, port);
        DatagramSocket dsocket = new DatagramSocket();
        dsocket.send(sendPacket);
        byte[] receiveData = new byte[ResponseType.MAX_RESPONSE_SIZE];
        DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
        dsocket.receive(receivePacket);
        return receivePacket;
    }

    private void sendInvalidRequest(String ip, int port) throws IOException {
        InetAddress IPAddress = InetAddress.getByName(ip);
        RequestType req = new RequestType(4, 0, 0, 0, null);
        DatagramPacket receivePacket = receive_packet(port, IPAddress, req);
        ResponseType response = new ResponseType(receivePacket.getData());
        loggerManager.getInstance(this.getClass()).debug(response.toString());
    }

    private FileListResponseType getFileList(String ip, int port) throws IOException {
        InetAddress IPAddress = InetAddress.getByName(ip);
        RequestType req = new RequestType(RequestType.REQUEST_TYPES.GET_FILE_LIST, 0, 0, 0, null);
        DatagramPacket receivePacket = receive_packet(port, IPAddress, req);
        FileListResponseType response = new FileListResponseType(receivePacket.getData());
        return response;
    }

    private long getFileSize(String ip, int port, int file_id) throws IOException {
        InetAddress IPAddress = InetAddress.getByName(ip);
        RequestType req = new RequestType(RequestType.REQUEST_TYPES.GET_FILE_SIZE, file_id, 0, 0, null);
        DatagramPacket receivePacket = receive_packet(port, IPAddress, req);
        FileSizeResponseType response = new FileSizeResponseType(receivePacket.getData());
        return response.getFileSize();
    }
    public static String sss="";
    private void getFileData(String ip, int port, int file_id, long start, long end) throws IOException {
        InetAddress IPAddress = InetAddress.getByName(ip);
        RequestType req = new RequestType(RequestType.REQUEST_TYPES.GET_FILE_DATA, file_id, start, end, null);
        byte[] sendData = req.toByteArray();
        DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, IPAddress, port);
        DatagramSocket dsocket = new DatagramSocket();
        dsocket.send(sendPacket);
        byte[] receiveData = new byte[ResponseType.MAX_RESPONSE_SIZE];
        long maxReceivedByte = -1;
        while (maxReceivedByte < end) {
            DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
            dsocket.receive(receivePacket);
            FileDataResponseType response = new FileDataResponseType(receivePacket.getData());
            loggerManager.getInstance(this.getClass()).debug(response.toString());
            if (response.getResponseType() != RESPONSE_TYPES.GET_FILE_DATA_SUCCESS) {
                break;
            }
            if (response.getEnd_byte() > maxReceivedByte) {
                maxReceivedByte = response.getEnd_byte();
            }

        }
        /*for(int i: receiveData){
            System.out.print(i);
        }*/
        String s = Base64.getEncoder().encodeToString(receiveData);
        sss+=s;


    }

    //how to tell start and end bytes.
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            throw new IllegalArgumentException("ip:port is mandatory");
        }
        String[] adr1 = args[0].split(":");
        String ip1 = adr1[0];
        int port1 = Integer.valueOf(adr1[1]);
        dummyClient inst = new dummyClient();
        FileListResponseType response = inst.getFileList(ip1, port1);
        System.out.println("File List: ");
        for (FileDescriptor file : response.getFileDescriptors()) {
            System.out.println(file.getFile_id() + " - " + file.getFile_name());
        }
        System.out.println("Enter a number:");
        Scanner scanner = new Scanner(System.in);
        int num = scanner.nextInt();
        System.out.println("File " + num + " has been selected. Getting the size information...");
        long size = inst.getFileSize(ip1, port1, num);
        System.out.println("File 2 is " + size + " bytes. Starting to download...");

        for (long start = 1; start <= size; start += 1000) {
            long end = size - start <= 999 ? size : start + 999;
            inst.getFileData(ip1, port1, num, start, end);
            int j = 0;
        }
        System.out.println("This is the md5 hash: "+generateMD5Hash(sss));

    }
    public static String generateMD5Hash(String text) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("MD5");
        md.update(text.getBytes());
        byte[] digest = md.digest();

        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format("%02x", b & 0xff));
        }

        return sb.toString();
    }
}
