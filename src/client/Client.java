package client;

import model.*;
import org.apache.log4j.Logger;

import java.io.FileOutputStream;
import java.io.IOException;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.*;
import java.util.stream.Stream;

public class Client {
    private static ClientManager firstClientManager;
    private static ClientManager secondClientManager;
    private static FileListResponseType fileListResponseType;
    private static final Logger loggerManager = LoggerManager.getInstance(Client.class);


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
            long size = firstClientManager.getFileSize(fileId);
            System.out.println("File " + fileId + " is " + size + " bytes. Starting to download...");
            return size;
        } catch (IOException e) {
            throw new IOException("Can't get file size -- there is no such file");
        }
    }

    //how to tell start and end bytes.
    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            throw new IllegalArgumentException("ip:port is mandatory");
        }

        final Network firstNetwork;
        final Network secondNetwork;

        try {
            firstNetwork = getNetworkFromParams(args[0]);
            secondNetwork = getNetworkFromParams(args[1]);
        } catch (UnknownHostException e) {
            loggerManager.error("There is no such hosts");
            return;
        }

        //TODO: Should we keep looping until 1 port gives datalist o try both and then quit?
        // this also applies to getting size etc, idk if we should consider this or not

        try {
            firstClientManager = new ClientManager(firstNetwork);
        } catch (SocketException e) {
            loggerManager.error("Cannot connect to the first network");
            return;
        }

        try {
            secondClientManager = new ClientManager(secondNetwork);
        } catch (SocketException e) {
            loggerManager.error("Cannot connect to the second network");
            return;
        }

        while (true) {
            try {
                fileListResponseType = firstClientManager.getFileList();
            } catch (IOException e) {
                try {
                    fileListResponseType = secondClientManager.getFileList();
                } catch (IOException x) {
                    loggerManager.error("Cannot create connection to the server. Quiting...");
                }
            }

            printFileList();
            int fileId = scanFileId();

            long size;
            try {
                size = getFileSize(fileId);
            } catch (IOException e) {
                loggerManager.error(e.getMessage());
                continue;
            }

            long startByte = 1;
            boolean switch_ = true;
            boolean downloadFinished = false;
            ArrayList<FileDataResponseType> response = new ArrayList<>();
            FileDataResponseType file = new FileDataResponseType(3, fileId, startByte, size, new byte[(int) size]);
            long startTime = System.currentTimeMillis();
            long restSize = size;
            long failureSize = 0;

            long firstLoadingTime = 0;
            long secondLoadingTime = 0;

            long firstDownloadedSize = 0;
            long secondDownloadedSize = 0;

            long startPosition = 1;
            long failureStartPosition = 0;

            while (!downloadFinished) { // -> false false false false -> true true false false false
                final long currentRestSize = failureSize == 0 ? restSize : failureSize;
                long chunkSize = currentRestSize < ResponseType.MAX_RESPONSE_SIZE ? currentRestSize / 2 : ResponseType.MAX_RESPONSE_SIZE / 2;

                final long currentStartPosition = failureStartPosition == 0 ? startPosition : failureStartPosition;

                double chunkCoefficient = firstLoadingTime == 0 || secondLoadingTime == 0 ? 1
                        : Math.round((double) firstDownloadedSize / firstLoadingTime / secondDownloadedSize / secondLoadingTime * 10.0) / 10.0;

                long secondChunkSize = (long) Math.floor(chunkSize * chunkCoefficient);
                long firstChunkSize = 2 * chunkSize - secondChunkSize;

                long firstFinishByte = currentStartPosition + firstChunkSize;
                long secondFinishByte = currentStartPosition + secondChunkSize + firstChunkSize;

                long firstStartTime = System.currentTimeMillis();
                List<FileDataResponseType> currentFirstResponse = firstClientManager.getFileData(fileId, currentStartPosition, firstFinishByte);

                if (currentFirstResponse.get(currentFirstResponse.size() - 1).getEnd_byte() < firstFinishByte) {
                    failureStartPosition = currentFirstResponse.get(currentFirstResponse.size() - 1).getEnd_byte() + 1;
                    failureSize = firstFinishByte - currentFirstResponse.get(currentFirstResponse.size() - 1).getEnd_byte();
                    continue;
                }

                firstDownloadedSize = currentFirstResponse.get(currentFirstResponse.size() - 1).getEnd_byte() - currentStartPosition;
                firstLoadingTime = System.currentTimeMillis() - firstStartTime;

                long secondStartTime = System.currentTimeMillis();
                List<FileDataResponseType> currentSecondResponse = secondClientManager.getFileData(fileId, currentStartPosition + firstChunkSize, secondFinishByte);
                secondDownloadedSize = currentSecondResponse.get(currentFirstResponse.size() - 1).getEnd_byte() - currentStartPosition + firstChunkSize;
                secondLoadingTime = System.currentTimeMillis() - secondStartTime;

                if (currentSecondResponse.get(currentFirstResponse.size() - 1).getEnd_byte() < firstFinishByte) {
                    if (failureStartPosition == 0) failureStartPosition = currentSecondResponse.get(currentFirstResponse.size() - 1).getEnd_byte() + 1;
                    failureSize = secondFinishByte - currentSecondResponse.get(currentSecondResponse.size() - 1).getEnd_byte();
                    continue;
                }

                startPosition = currentSecondResponse.get(currentSecondResponse.size() - 1).getEnd_byte() + 1;
                restSize -= secondFinishByte - startByte;


//                long finalStartByte = startByte;
//
//                ClientManager finalCurrentClientManager = switch_ ? firstClientManager : secondClientManager;
//                Callable<ArrayList<FileDataResponseType>> task = () -> finalCurrentClientManager.getFileData(fileId, finalStartByte, size);
//                // create a FutureTask<Boolean> instance and pass the task to its constructor
//                FutureTask<ArrayList<FileDataResponseType>> futureTask = new FutureTask<>(task);
//                // create a new thread and start it with the FutureTask as its target
//                Thread thread = new Thread(futureTask);
//                thread.start();
//                // wait for the task to complete, but with a time constraint of X seconds
//                try {
//                    response.addAll(futureTask.get(3, TimeUnit.SECONDS));
//                    // do something with the result
//                } catch (TimeoutException e) {
//
//                } catch (ExecutionException | InterruptedException e) {
//                    e.printStackTrace();
//                }
//
//                if (response.isEmpty()) {
//                    System.out.println("Switching!!!!!!!");
//                    switch_ = !switch_;
//                } else if ((response.get(response.size() - 1).getEnd_byte()) < size) {
//                    System.out.println("Switching!!!!!!!");
//                    startByte = response.get(response.size() - 1).getEnd_byte() + 1;
//                    switch_ = false;
//                } else {
//                    downloadFinished = true;
//                }
            }
            long endTime = System.currentTimeMillis();
            long elapsedTime = endTime - startTime;

            /**
             * Combinging all responses into one response*/

            for (FileDataResponseType fileDataResponseType : response) {
                file.addData(fileDataResponseType.getData(), fileDataResponseType.getStart_byte(), fileDataResponseType.getEnd_byte());
            }

            try {
                FileOutputStream fos = new FileOutputStream("output.txt");
                fos.write(file.getData());
                fos.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            MessageDigest md = null;

            try {
                md = MessageDigest.getInstance("MD5");
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            }

            md.update(file.getData());
            byte[] mdBytes = md.digest();

            StringBuffer md5 = new StringBuffer();
            for (int i = 0; i < mdBytes.length; i++) {
                md5.append(Integer.toString((mdBytes[i] & 0xff) + 0x100, 16).substring(1));
            }
            System.out.println("File " + fileId + " downloaded in " + elapsedTime + " ms. The md5 hash is " + md5);
            System.out.println();
        }
    }

}
