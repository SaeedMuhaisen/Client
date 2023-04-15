package client;

import model.FileDataResponseType;
import model.FileDescriptor;
import model.FileListResponseType;
import model.Network;

import java.io.FileOutputStream;
import java.io.IOException;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.concurrent.*;

public class Client {
    private static ClientManager clientManager;
    private static FileListResponseType fileListResponseType;


    private static Network getNetworkFromParams(String address) throws UnknownHostException {
        String[] addressParams = address.split(":");
        return new Network(addressParams[0], Integer.parseInt(addressParams[1]));
    }

    private static void printFileList() {
        System.out.println("File List: ");
        for (FileDescriptor file : fileListResponseType.getFileDescriptors()) {
            System.out.println(file.getFile_id() + " - " + file.getFile_name());
        }
    }

    private static int scanFileId() {
        Scanner scanner = new Scanner(System.in);
        System.out.println("Enter a number:");
        final int fileId = scanner.nextInt();
        System.out.println("File " + fileId + " has been selected. Getting the size information...");
        return fileId;
    }

    private static long getFileSize(int fileId) throws IOException {
        try {
            long size = clientManager.getFileSize(fileId);
            System.out.println("File " + fileId + " is " + size + " bytes. Starting to download...");
            return size;
        } catch (IOException e) {
            throw new IOException("Can't get file size");
        }
    }

    //how to tell start and end bytes.
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            throw new IllegalArgumentException("ip:port is mandatory");
        }
        while (true) {
            final Network firstNetwork = getNetworkFromParams(args[0]);
            final Network secondNetwork = getNetworkFromParams(args[1]);

            //TODO: Should we keep looping until 1 port gives datalist o try both and then quit?
            // this also applies to getting size etc, idk if we should consider this or not

            try {
                clientManager = new ClientManager(firstNetwork);
                fileListResponseType = clientManager.getFileList();
            } catch (IOException e) {
                try {
                    clientManager = new ClientManager(secondNetwork);
                    fileListResponseType = clientManager.getFileList();
                } catch (IOException x) {
                    System.out.println("Cannot create connection to the server. Quiting...");
                    break;
                }
            }

            printFileList();
            int fileId = scanFileId();
            long size = getFileSize(fileId);

            long startByte = 1;
            boolean switch_ = true;
            boolean downloadFinished = false;
            ArrayList<FileDataResponseType> response = new ArrayList<>();
            FileDataResponseType file = new FileDataResponseType(3, fileId, startByte, size, new byte[(int) size]);
            long startTime = System.currentTimeMillis();

            while (!downloadFinished) {
                clientManager = switch_ ?
                        new ClientManager(firstNetwork) :
                        new ClientManager(secondNetwork);

                ClientManager finalClientManager = clientManager;
                long finalStartByte = startByte;
                Callable<ArrayList<FileDataResponseType>> task = new Callable<ArrayList<FileDataResponseType>>() {
                    @Override
                    public ArrayList<FileDataResponseType> call() throws Exception {
                        return finalClientManager.getFileData(fileId, finalStartByte, size);

                    }
                };
                // create a FutureTask<Boolean> instance and pass the task to its constructor
                FutureTask<ArrayList<FileDataResponseType>> futureTask = new FutureTask<>(task);
                // create a new thread and start it with the FutureTask as its target
                Thread thread = new Thread(futureTask);
                thread.start();
                // wait for the task to complete, but with a time constraint of 3 seconds
                try {

                    response.addAll(futureTask.get(1, TimeUnit.NANOSECONDS));
                    // do something with the result
                } catch (TimeoutException e) {

                }

                if (response.isEmpty()) {
                    System.out.println("Switching!!!!!!!");
                    switch_ = switch_ ? false : true;

                } else if ((response.get(response.size() - 1).getEnd_byte()) < size) {
                    System.out.println("Switching!!!!!!!");
                    startByte = response.get(response.size() - 1).getEnd_byte() + 1;
                    switch_ = false;

                } else {
                    downloadFinished = true;
                }
            }
            long endTime = System.currentTimeMillis();
            long elapsedTime = endTime - startTime;

            /**
             * Combinging all responses into one response*/

            for (int i = 0; i < response.size(); i++) {
                file.addData(response.get(i).getData(), response.get(i).getStart_byte(), response.get(i).getEnd_byte());
            }

            try {
                FileOutputStream fos = new FileOutputStream("output.txt");
                fos.write(file.getData());
                fos.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(file.getData());
            byte[] mdBytes = md.digest();

            StringBuffer md5 = new StringBuffer();
            for (int i = 0; i < mdBytes.length; i++) {
                md5.append(Integer.toString((mdBytes[i] & 0xff) + 0x100, 16).substring(1));
            }
            System.out.println("File " + fileId + elapsedTime + " ms. The md5 hash is " + md5);
            System.out.println();
        }
    }

}
