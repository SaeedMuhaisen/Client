package client;

import model.*;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.SocketException;
import java.util.ArrayList;

public class ClientManager {

    private final SocketManager dsocket;

    public ClientManager(Network network) throws SocketException {
        this.dsocket = new SocketManager(network.getIp(), network.getPort());
    }

    public FileListResponseType getFileList() throws IOException {
        //throw new IOException(); //For debugging
        send_packet(new RequestType(RequestType.REQUEST_TYPES.GET_FILE_LIST, 0, 0, 0, null));
        return new FileListResponseType(receive_packet_max_size().getData());
    }

    public long getFileSize(int file_id) throws IOException {
        send_packet(new RequestType(RequestType.REQUEST_TYPES.GET_FILE_SIZE, file_id, 0, 0, null));

        FileSizeResponseType response = new FileSizeResponseType(receive_packet_max_size().getData());

        if (response.getFileSize() > 0) {
            return response.getFileSize();
        } else {
            throw new IOException("There is no such file");
        }
    }

    public ArrayList<FileDataResponseType> getFileData(int file_id, long start, long end) throws IOException {
        send_packet(new RequestType(RequestType.REQUEST_TYPES.GET_FILE_DATA, file_id, start, end, null));
        long maxReceivedByte;
        FileDataResponseType response;
        ArrayList<FileDataResponseType> full=new ArrayList<>();
        int i=0;
        for (maxReceivedByte = -1; maxReceivedByte < end; maxReceivedByte = Math.max(response.getEnd_byte(), maxReceivedByte)) {
            response = new FileDataResponseType(receive_packet_max_size().getData());
            debug(response.toString());
            full.add(response);
            /**this is for debugging, we break the download after 5 loops to switch servers*/
            i++;

        }

        return full;
    }


    public void debugInvalidResponse() throws IOException {
        final long start = System.currentTimeMillis();
        final ResponseType responseType = getInvalidResponse();
        debug(String.valueOf(System.currentTimeMillis() - start));
        debug(responseType.toString());
    }

    private ResponseType getInvalidResponse() throws IOException {
        send_packet(new RequestType(3, 1, 1, 1, null));
        return new ResponseType(receive_packet_max_size().getData());
    }

    private void send_packet(RequestType req) throws IOException {
        dsocket.send(req);
    }

    private DatagramPacket receive_packet(byte[] data) throws IOException {
        return dsocket.receive(data);
    }

    private DatagramPacket receive_packet_max_size() throws IOException {
        return receive_packet(new byte[ResponseType.MAX_RESPONSE_SIZE]);
    }

    private void debug(String debugString) {
        LoggerManager.getInstance(this.getClass()).debug(debugString);
    }
}
