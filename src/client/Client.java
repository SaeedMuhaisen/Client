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
    public static void main(String[] args) {
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
            boolean downloadFinished = false;
            byte[] output = new byte[(int) size];

            long startTime = System.currentTimeMillis();
            long failureSize = 0;

            long firstLoadingTime = 0;
            long secondLoadingTime = 0;

            long firstDownloadedSize = 0;
            long secondDownloadedSize = 0;

            long startPosition = 1;
            long failureStartPosition = 0;

            ArrayList<FileDataResponseType> full =new ArrayList<>();

            long totalForFirst = 0;
            long totalForSecond = 0;
            int stepCounter = 1;

            while (!downloadFinished) {
                final long restSize = size - startPosition + 1;
                final long currentRestSize = failureSize == 0 ? restSize : failureSize;
                long chunkSize = currentRestSize < ResponseType.MAX_DATA_SIZE * 2 ? currentRestSize / 2 : ResponseType.MAX_DATA_SIZE;

                final long currentStartPosition = failureStartPosition == 0 ? startPosition : failureStartPosition;

                failureSize = 0;
                failureStartPosition = 0;

                double chunkCoefficient = firstLoadingTime == 0 || secondLoadingTime == 0 ? 1
                        : (double) (firstLoadingTime * secondDownloadedSize) / (firstDownloadedSize * secondLoadingTime);

                long secondChunkSize = (long) Math.floor(2 * chunkSize / (chunkCoefficient + 1));
                long firstChunkSize = restSize % 2 == 0 ? 2 * chunkSize - secondChunkSize : 2 * chunkSize - secondChunkSize + 1;

                long firstFinishByte = currentStartPosition + firstChunkSize - 1;
                long secondFinishByte = currentStartPosition + secondChunkSize + firstChunkSize - 1;

                try {
                    long firstStartTime = System.currentTimeMillis();
                    List<FileDataResponseType> currentFirstResponse = firstClientManager.getFileData(fileId, currentStartPosition, firstFinishByte);
                    firstDownloadedSize = firstFinishByte - currentStartPosition + 1;
                    firstLoadingTime = System.currentTimeMillis() - firstStartTime;
                    full.addAll(currentFirstResponse);

                    for (FileDataResponseType fileDataResponseType : currentFirstResponse) {
                        final byte[] dataArray = fileDataResponseType.toByteArray();
                        final int startIndex = (int) fileDataResponseType.getStart_byte() - 1;
                        final int endIndex = (int) fileDataResponseType.getEnd_byte() - 1;


                        if (endIndex + 1 - startIndex >= 0)
                            System.arraycopy(dataArray, 0, output, startIndex, endIndex + 1 - startIndex);
                    }
                } catch (IOException e) {
                    failureStartPosition = currentStartPosition;
                    failureSize = firstFinishByte - currentStartPosition;
                    continue;
                }

                List<FileDataResponseType> currentSecondResponse;
                try {
                    long secondStartTime = System.currentTimeMillis();
                    currentSecondResponse = secondClientManager.getFileData(fileId, firstFinishByte + 1, secondFinishByte);
                    secondDownloadedSize = secondFinishByte - currentStartPosition - firstChunkSize + 1;
                    secondLoadingTime = System.currentTimeMillis() - secondStartTime;
                    full.addAll(currentSecondResponse);
                    for (FileDataResponseType fileDataResponseType : currentSecondResponse) {
                        final byte[] dataArray = fileDataResponseType.toByteArray();
                        final int startIndex = (int) fileDataResponseType.getStart_byte() - 1;
                        final int endIndex = (int) fileDataResponseType.getEnd_byte() - 1;


                        if (endIndex + 1 - startIndex >= 0)
                            System.arraycopy(dataArray, 0, output, startIndex, endIndex + 1 - startIndex);
                    }
                } catch (IOException e) {
                    startPosition = currentStartPosition + firstChunkSize + 1;
                    continue;
                }

                startPosition = secondFinishByte + 1;

                downloadFinished = startPosition == size + 1;

                System.out.println("Step " + stepCounter);
                System.out.println("The average speed of the first connection " + firstDownloadedSize / firstLoadingTime + " byte/ms");
                System.out.println("The average speed of the second connection " + secondDownloadedSize / secondLoadingTime  + " byte/ms");
                System.out.println("Elapsed time " + (System.currentTimeMillis() - startTime) + "ms");

                totalForFirst += firstDownloadedSize;
                totalForSecond += secondDownloadedSize;
                System.out.println("Percentage completed " + Math.round((double) (totalForFirst + totalForSecond) / size * 100) + "%");
                System.out.println();
                stepCounter++;
            }

            long endTime = System.currentTimeMillis();
            long elapsedTime = endTime - startTime;

            System.out.println("The average speed for the first port " + Math.round((double) totalForFirst / elapsedTime) + " byte/ms");
            System.out.println("The average speed for the second port " + Math.round((double) totalForSecond / elapsedTime) + " byte/ms");

            /**
             * Combinging all responses into one response*/
            FileDataResponseType file = new FileDataResponseType(3,fileId,1,size,new byte[(int) size]);
            for (FileDataResponseType fileDataResponseType : full) {
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
