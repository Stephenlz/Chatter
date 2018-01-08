package edu.nyu.cs9053.homework11;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.*;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.util.Iterator;
import java.util.Set;

public class NonBlockingChatter implements Chatter {
    private static final int BUFFER_SIZE = 1024;
    private static final Charset UTF8 = Charset.forName("UTF-8");
    private final Selector selector;
    private final Pipe.SourceChannel userInput;
    private final ByteBuffer readBuffer;
    private final ByteBuffer writeBuffer;

    public NonBlockingChatter(SocketChannel chatServerChannel, Pipe.SourceChannel userInput) throws IOException {
        if (chatServerChannel == null || userInput == null) {
            throw new IllegalArgumentException();
        }
        this.selector = Selector.open();
        this.readBuffer = ByteBuffer.allocate(BUFFER_SIZE);
        this.writeBuffer = ByteBuffer.allocate(BUFFER_SIZE);
        this.userInput = userInput;
        chatServerChannel.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE);
        userInput.register(selector, SelectionKey.OP_READ);
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                work();
            } catch (IOException ioe) {
                System.out.printf("Exception - %s%n", ioe.getMessage());
                Thread.currentThread().interrupt();
            }
        }
    }

    private void work() throws IOException {
        int events = selector.select();
        if (events < 1) {
            return;
        }
        Set<SelectionKey> keys = selector.selectedKeys();
        Iterator<SelectionKey> iterator = keys.iterator();
        while (iterator.hasNext()) {
            SelectionKey key = iterator.next();
            try {
                if (key.isReadable()) {
                    if (key.channel() == userInput) {
                        writeBuffer.clear();
                        int result = userInput.read(writeBuffer);
                        if (result == -1) {
                            key.cancel();
                        }
                    } else {
                        SocketChannel server = (SocketChannel) key.channel();
                        readBuffer.clear();
                        int result = server.read(readBuffer);
                        if (result == -1) {
                            key.cancel();
                            continue;
                        }
                        readBuffer.flip();
                        CharsetDecoder decoder = UTF8.newDecoder();
                        CharBuffer charBuffer = decoder.decode(readBuffer);
                        String serverMessage = charBuffer.toString().replaceAll("\n", "");
                        System.out.printf("%s%n", serverMessage);
                    }

                } else if (key.isWritable()) {
                    SocketChannel server = (SocketChannel) key.channel();
                    if (writeBuffer.position() == 0) {
                        continue;
                    }
                    writeBuffer.flip();
                    server.write(writeBuffer);
                    writeBuffer.clear();
                }
            } finally {
                iterator.remove();
            }
        }
    }
}
