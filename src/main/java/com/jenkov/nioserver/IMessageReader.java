package com.jenkov.nioserver;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

/**
 * Created by jjenkov on 16-10-2015.
 */
public interface IMessageReader {

    //初始化Message Reader
    public void init(MessageBuffer readMessageBuffer);

    //将Socket channel中的数据读到byteBuffer中
    public void read(Socket socket, ByteBuffer byteBuffer) throws IOException;

    //获取已经读取完成的完整的Http message
    public List<Message> getMessages();



}
