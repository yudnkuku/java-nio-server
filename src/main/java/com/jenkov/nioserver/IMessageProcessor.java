package com.jenkov.nioserver;

/**
 * Created by jjenkov on 16-10-2015.
 */
public interface IMessageProcessor {

    //处理消息，并将响应消息写入WriteProxy的响应消息队列
    public void process(Message message, WriteProxy writeProxy);

}
