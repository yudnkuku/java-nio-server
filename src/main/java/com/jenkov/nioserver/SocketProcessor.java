package com.jenkov.nioserver;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.*;

/**
 * Created by jjenkov on 16-10-2015.
 */
public class SocketProcessor implements Runnable {

    //构造函数传入，代表连接的Socket队列
    private Queue<Socket>  inboundSocketQueue   = null;

    //读数据MessageBuffer
    private MessageBuffer  readMessageBuffer    = null; //todo   Not used now - but perhaps will be later - to check for space in the buffer before reading from sockets

    //写数据MessageBuffer
    private MessageBuffer  writeMessageBuffer   = null; //todo   Not used now - but perhaps will be later - to check for space in the buffer before reading from sockets (space for more to write?)

    //MessageReader工厂类，构建MessageReader实例
    private IMessageReaderFactory messageReaderFactory = null;

    //响应消息队列
    private Queue<Message> outboundMessageQueue = new LinkedList<>(); //todo use a better / faster queue.

    //Socket Map，key->socket id, value->Socket
    private Map<Long, Socket> socketMap         = new HashMap<>();

    private ByteBuffer readByteBuffer  = ByteBuffer.allocate(1024 * 1024);
    private ByteBuffer writeByteBuffer = ByteBuffer.allocate(1024 * 1024);

    //Read/Write Selector
    private Selector   readSelector    = null;
    private Selector   writeSelector   = null;

    private IMessageProcessor messageProcessor = null;
    private WriteProxy        writeProxy       = null;

    private long              nextSocketId = 16 * 1024; //start incoming socket ids from 16K - reserve bottom ids for pre-defined sockets (servers).

    //有数据写的Socket集合
    private Set<Socket> emptyToNonEmptySockets = new HashSet<>();

    //无数据写的Socket集合
    private Set<Socket> nonEmptyToEmptySockets = new HashSet<>();


    public SocketProcessor(Queue<Socket> inboundSocketQueue, MessageBuffer readMessageBuffer, MessageBuffer writeMessageBuffer, IMessageReaderFactory messageReaderFactory, IMessageProcessor messageProcessor) throws IOException {
        this.inboundSocketQueue = inboundSocketQueue;

        this.readMessageBuffer    = readMessageBuffer;
        this.writeMessageBuffer   = writeMessageBuffer;
        this.writeProxy           = new WriteProxy(writeMessageBuffer, this.outboundMessageQueue);

        this.messageReaderFactory = messageReaderFactory;

        this.messageProcessor     = messageProcessor;

        this.readSelector         = Selector.open();
        this.writeSelector        = Selector.open();
    }

    public void run() {
        //死循环执行
        while(true){
            try{
                executeCycle();
            } catch(IOException e){
                e.printStackTrace();
            }

            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }


    public void executeCycle() throws IOException {
        takeNewSockets();
        readFromSockets();
        writeToSockets();
    }

    //初始化Socket
    public void takeNewSockets() throws IOException {
        Socket newSocket = this.inboundSocketQueue.poll();

        //循环初始化Socket
        while(newSocket != null){
            newSocket.socketId = this.nextSocketId++;
            //设置非阻塞模式
            newSocket.socketChannel.configureBlocking(false);

            //初始化MessageReader
            newSocket.messageReader = this.messageReaderFactory.createMessageReader();

            //使用readMessageBuffer初始化MessageReader
            newSocket.messageReader.init(this.readMessageBuffer);

            //初始化MessageWriter
            newSocket.messageWriter = new MessageWriter();

            this.socketMap.put(newSocket.socketId, newSocket);

            //注册channel到readSelector上，准备读
            SelectionKey key = newSocket.socketChannel.register(this.readSelector, SelectionKey.OP_READ);

            //attach socket
            key.attach(newSocket);

            //继续下一个Socket
            newSocket = this.inboundSocketQueue.poll();
        }
    }

    //从所有准备读的通道中读取消息
    public void readFromSockets() throws IOException {
        //非阻塞返回准备读的channel
        int readReady = this.readSelector.selectNow();

        if(readReady > 0){
            Set<SelectionKey> selectedKeys = this.readSelector.selectedKeys();
            Iterator<SelectionKey> keyIterator = selectedKeys.iterator();

            //遍历SelectionKey set
            while(keyIterator.hasNext()) {
                SelectionKey key = keyIterator.next();

                //从通道中读数据
                readFromSocket(key);

                keyIterator.remove();
            }
            selectedKeys.clear();
        }
    }

    //从通道中读数据
    private void readFromSocket(SelectionKey key) throws IOException {
        Socket socket = (Socket) key.attachment();

        //调用MessageReader将socket里的数据读到readByteBuffer中，readByteBuffer初始化大小为1MB
        socket.messageReader.read(socket, this.readByteBuffer);

        List<Message> fullMessages = socket.messageReader.getMessages();
        if(fullMessages.size() > 0){
            //读取的完整的HTTP消息交给messageProcessor处理
            for(Message message : fullMessages){
                message.socketId = socket.socketId;
                this.messageProcessor.process(message, this.writeProxy);  //the message processor will eventually push outgoing messages into an IMessageWriter for this socket.
            }
            fullMessages.clear();
        }

        //Socket数据读完，从socketMap中移除该Socket，并取消注册key，关闭相应通道
        if(socket.endOfStreamReached){
            System.out.println("Socket closed: " + socket.socketId);
            this.socketMap.remove(socket.socketId);
            key.attach(null);
            key.cancel();
            key.channel().close();
        }
    }


    public void writeToSockets() throws IOException {

        //从响应消息队列取出消息
        takeNewOutboundMessages();

        //取消注册所有没有数据写的Socket通道
        cancelEmptySockets();

        //注册所有有数据写的Socket通道
        registerNonEmptySockets();

        //非阻塞选择写准备的Socket
        int writeReady = this.writeSelector.selectNow();

        if(writeReady > 0){
            Set<SelectionKey>      selectionKeys = this.writeSelector.selectedKeys();
            Iterator<SelectionKey> keyIterator   = selectionKeys.iterator();

            while(keyIterator.hasNext()){
                SelectionKey key = keyIterator.next();

                Socket socket = (Socket) key.attachment();

                socket.messageWriter.write(socket, this.writeByteBuffer);

                //MessageWriter中的消息数据写完，将Socket加入EmptySockets集合
                if(socket.messageWriter.isEmpty()){
                    this.nonEmptyToEmptySockets.add(socket);
                }

                keyIterator.remove();
            }

            selectionKeys.clear();

        }
    }

    //如果socket关联的MessageWriter有数据可写，将socket注册到writeSelector
    private void registerNonEmptySockets() throws ClosedChannelException {
        for(Socket socket : emptyToNonEmptySockets){
            socket.socketChannel.register(this.writeSelector, SelectionKey.OP_WRITE, socket);
        }
        emptyToNonEmptySockets.clear();
    }

    //解除所有没有数据可写的socket关联的MessageWriter和writeSelector的注册关系
    private void cancelEmptySockets() {
        for(Socket socket : nonEmptyToEmptySockets){
            SelectionKey key = socket.socketChannel.keyFor(this.writeSelector);

            key.cancel();
        }
        nonEmptyToEmptySockets.clear();
    }

    //从消息队列中拿服务器响应消息数据，存入MessageWriter消息集合中
    //并根据socket关联的MessageWriter判断是否有数据可写
    private void takeNewOutboundMessages() {
        Message outMessage = this.outboundMessageQueue.poll();
        while(outMessage != null){
            //socketMap很关键，直接关联了响应消息和对应的socket和MessageWriter
            Socket socket = this.socketMap.get(outMessage.socketId);

            if(socket != null){
                MessageWriter messageWriter = socket.messageWriter;
                //判断MessageWriter是否有数据可写
                if(messageWriter.isEmpty()){
                    //没有数据，加入emptySocket集合
                    messageWriter.enqueue(outMessage);
                    nonEmptyToEmptySockets.remove(socket);
                    emptyToNonEmptySockets.add(socket);    //not necessary if removed from nonEmptyToEmptySockets in prev. statement.
                } else{
                   messageWriter.enqueue(outMessage);
                }
            }

            outMessage = this.outboundMessageQueue.poll();
        }
    }

}
