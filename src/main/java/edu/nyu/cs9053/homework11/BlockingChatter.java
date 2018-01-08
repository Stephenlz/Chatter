package edu.nyu.cs9053.homework11;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class BlockingChatter implements Chatter {
    private static final String FILE_PATH = "Moby Dick.txt";
    private static final String EASTER_EGG = "java";
    private static final int BUFFER_SIZE = 1024;

    private final List<String> lines;
    private final InputStream chatServerInput;
    private final OutputStream chatServerOutput;
    private final InputStream userInput;

    public BlockingChatter(InputStream chatServerInput, OutputStream chatServerOutput, InputStream userInput) {
        if (chatServerInput == null || chatServerOutput == null || userInput == null) {
            throw new IllegalArgumentException();
        }
        this.lines = new ArrayList<>();
        this.chatServerInput = chatServerInput;
        this.chatServerOutput = chatServerOutput;
        this.userInput = userInput;
        readFile();
    }

    @Override
    public void run() {
        Thread userThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                readFromUser();
            }
        });
        Thread serverThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                readFromServer();
            }
        });
        userThread.start();
        serverThread.start();
    }

    private void readFile() {
        String line = null;
        try (FileReader fileReader = new FileReader(FILE_PATH);
             BufferedReader bufferedReader = new BufferedReader(fileReader)) {
            while ((line = bufferedReader.readLine()) != null) {
                lines.add(line);
            }
        } catch (IOException ioe) {
            System.out.printf("Exception - %s%n", ioe.getMessage());
            Thread.currentThread().interrupt();
        }
    }

    private byte[] randomSelect() {
        if (lines.size() == 0) {
            return null;
        }
        Random random = new Random();
        byte[] randomLine = null;
        while (randomLine == null || randomLine.length < 1) {
            int selectNum = random.nextInt(lines.size());
            randomLine = lines.get(selectNum).getBytes();
        }
        return randomLine;
    }

    private void readFromUser() {
        byte[] rawMessage = new byte[BUFFER_SIZE];
        try {
            int readLen = userInput.read(rawMessage, 0, BUFFER_SIZE);
            if (readLen == -1) {
                System.out.printf("stream is closed%n");
            } else {
                byte[] compressMessage = new byte[readLen];
                System.arraycopy(rawMessage, 0, compressMessage, 0, readLen);
                sendToServer(compressMessage);
            }
        } catch (IOException ioe) {
            System.out.printf("Exception - %s%n", ioe.getMessage());
            Thread.currentThread().interrupt();
        }
    }

    private void readFromServer() {
        byte[] rawMessage = new byte[BUFFER_SIZE];
        try {
            int readLen = chatServerInput.read(rawMessage, 0, BUFFER_SIZE);
            if (readLen == -1) {
                System.out.print("Stream is closed%n");
            } else {
                byte[] compressMessage = new byte[readLen];
                System.arraycopy(rawMessage, 0, compressMessage, 0, readLen);
                String decodeMessage = new String(compressMessage, "UTF-8");
                decodeMessage = decodeMessage.replaceAll("\n", "");
                System.out.printf("%s%n", decodeMessage);
                String[] serverMessage = decodeMessage.split(" ");
                if (serverMessage.length == 2 && serverMessage[1].equals(EASTER_EGG)) {
                    sendToServer(randomSelect());
                }
            }
        } catch (IOException ioe) {
            System.out.printf("Exception - %s%n", ioe.getMessage());
            Thread.currentThread().interrupt();
        }
    }

    private synchronized void sendToServer(byte[] message) {
        if (message == null || message.length <= 1) {
            return;
        }
        try {
            chatServerOutput.write(message, 0, message.length);
            chatServerOutput.flush();
        } catch (IOException ioe) {
            System.out.printf("Exception - %s%n", ioe.getMessage());
            Thread.currentThread().interrupt();
        }
    }
}
