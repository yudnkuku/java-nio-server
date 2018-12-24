package com.jenkov.client;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class NIOClient {

    private static final int SOCKET_NUM = 8000;

    private static final int PORT = 9999;

    private static final String HTTPMSG = "GET / HTTP/1.1\r\n" +
            "Content-Length: 5\r\n" +
            "\r\n12345";

    private static final Charset CHARSET = Charset.forName("UTF-8");

    private Selector selector;

    private List<SocketChannel> socketChannelList = new ArrayList<>();

    public NIOClient() throws IOException{
        selector = Selector.open();
        for (int i = 0; i < SOCKET_NUM; i++) {
            SocketChannel socketChannel = SocketChannel.open(
                    new InetSocketAddress("127.0.0.1", PORT));
            socketChannel.configureBlocking(false);
            socketChannel.register(selector, SelectionKey.OP_READ);
            socketChannelList.add(socketChannel);
        }
    }
    
    public static void main(String[] args) throws Exception {
        new NIOClient().start();
    }

    private void start() throws Exception {
        for(SocketChannel sc : socketChannelList) {
            sc.write(CHARSET.encode(HTTPMSG));
            //写完数据之后休眠10ms再写
            Thread.sleep(20);
        }
        new ClientThread().start();
    }

    private class ClientThread extends Thread {
        @Override
        public void run() {
            while(true) {
                try {
                    while(selector.select() > 0) {
                        Set<SelectionKey> selectionKeys = selector.selectedKeys();
                        Iterator<SelectionKey> keyIterator = selectionKeys.iterator();
                        while(keyIterator.hasNext()) {
                            SelectionKey key = keyIterator.next();
                            if(key.isReadable()) {
                                SocketChannel sc = (SocketChannel) key.channel();
                                ByteBuffer buffer = ByteBuffer.allocate(1024);
                                StringBuffer response = new StringBuffer();
                                while(sc != null && sc.read(buffer) > 0) {
                                    buffer.flip();
                                    response.append(CHARSET.decode(buffer));
                                }
                                System.out.println(sc + "\n接受到的响应：" + response.toString());
                            }
                            keyIterator.remove();
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
