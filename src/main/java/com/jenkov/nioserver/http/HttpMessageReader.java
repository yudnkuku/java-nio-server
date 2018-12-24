package com.jenkov.nioserver.http;

import com.jenkov.nioserver.IMessageReader;
import com.jenkov.nioserver.Message;
import com.jenkov.nioserver.MessageBuffer;
import com.jenkov.nioserver.Socket;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by jjenkov on 18-10-2015.
 */
public class HttpMessageReader implements IMessageReader {

    private MessageBuffer messageBuffer    = null;

    private List<Message> completeMessages = new ArrayList<Message>();

    //从socket中读取的消息数据会写入nextMessage中，并解析，每次只会解析一个完整的http消息
    private Message       nextMessage      = null;

    public HttpMessageReader() {
    }

    @Override
    public void init(MessageBuffer readMessageBuffer) {
        this.messageBuffer        = readMessageBuffer;
        this.nextMessage          = messageBuffer.getMessage();
        this.nextMessage.metaData = new HttpHeaders();
    }

    @Override
    public void read(Socket socket, ByteBuffer byteBuffer) throws IOException {
        //调用Socket的read()方法，将通道中的消息数据读到byteBuffer中
        int bytesRead = socket.read(byteBuffer);

        //读byteBuffer之前flip()
        byteBuffer.flip();

        if(byteBuffer.remaining() == 0){
            byteBuffer.clear();
            return;
        }

        //将接收到的消息写入nextMessage中
        this.nextMessage.writeToMessage(byteBuffer);

        //解析HTTP请求，endIndex是HTTP请求body的结束索引
        int endIndex = HttpUtil.parseHttpRequest(this.nextMessage.sharedArray, this.nextMessage.offset, this.nextMessage.offset + this.nextMessage.length, (HttpHeaders) this.nextMessage.metaData);
        if(endIndex != -1){
            //endIndex!=-1表示消息大于一个完整的HTTP请求
            Message message = this.messageBuffer.getMessage();
            message.metaData = new HttpHeaders();

            //将nextMessage剩余部分消息写入message
            message.writePartialMessageToMessage(nextMessage, endIndex);
            //将解析完毕的nextMessage加入completeMessages集合
            completeMessages.add(nextMessage);
            nextMessage = message;
        }
        byteBuffer.clear();
    }


    @Override
    public List<Message> getMessages() {
        return this.completeMessages;
    }

}
