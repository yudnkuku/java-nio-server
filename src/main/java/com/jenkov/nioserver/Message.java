package com.jenkov.nioserver;

import java.nio.ByteBuffer;

/**
 * Created by jjenkov on 16-10-2015.
 */
public class Message {

    //MessageBuffer
    private MessageBuffer messageBuffer = null;

    //socket id
    public long socketId = 0; // the id of source socket or destination socket, depending on whether is going in or out.

    //共享数组，MessageBuffer中不同类型的数组-small（4MB）/medium（16MB）/large（16MB）
    public byte[] sharedArray = null;

    //sharedArray中消息的偏移地址，由对应的QueueIntFlip实例维护
    //针对不同大小的消息构建了不同的QueueIntFlip实例，分别为QueueIntFlip(1024),QueueIntFlip(128),QueueIntFlip(16)
    public int    offset      = 0;

    //分配给消息的容量大小(块大小)，有三档：4KB 128KB 1MB
    public int    capacity    = 0; //the size of the section in the sharedArray allocated to this message.

    //消息长度，此消息长度必然小于capacity
    public int    length      = 0; //the number of bytes used of the allocated section.

    public Object metaData    = null;

    public Message(MessageBuffer messageBuffer) {
        this.messageBuffer = messageBuffer;
    }

    /**
     * 将byteBuffer中的数据写到Message实例中(内部维护了一个sharedArray存放数据，sharedArray)，如果byteBuffer中的长度过长需要扩容
     * @param byteBuffer
     * @return
     */
    public int writeToMessage(ByteBuffer byteBuffer){
        int remaining = byteBuffer.remaining();

        //如果消息长度太大，需要扩容处理
        while(this.length + remaining > capacity){
            if(!this.messageBuffer.expandMessage(this)) {
                System.out.println("消息总量超过16MB，无法完成扩容");
                return -1;
            }
        }

        int bytesToCopy = Math.min(remaining, this.capacity - this.length);

        //将byteBuffer中的数据复制到sharedArray offset处
        byteBuffer.get(this.sharedArray, this.offset + this.length, bytesToCopy);

        //更新Message length属性
        this.length += bytesToCopy;

        return bytesToCopy;
    }




    /**
     * Writes data from the byte array into this message - meaning into the buffer backing this message.
     *
     * @param byteArray The byte array containing the message data to write.
     * @return
     */
    public int writeToMessage(byte[] byteArray){
        return writeToMessage(byteArray, 0, byteArray.length);
    }


    /**
     * Writes data from the byte array into this message - meaning into the buffer backing this message.
     *
     * @param byteArray The byte array containing the message data to write.
     * @return
     */
    public int writeToMessage(byte[] byteArray, int offset, int length){
        int remaining = length;

        while(this.length + remaining > capacity){
            if(!this.messageBuffer.expandMessage(this)) {
                return -1;
            }
        }

        int bytesToCopy = Math.min(remaining, this.capacity - this.length);
        System.arraycopy(byteArray, offset, this.sharedArray, this.offset + this.length, bytesToCopy);
        this.length += bytesToCopy;
        return bytesToCopy;
    }




    /**
     * In case the buffer backing the nextMessage contains more than one HTTP message, move all data after the first
     * message to a new Message object.
     *
     * @param message   The message containing the partial message (after the first message).
     * @param endIndex  The end index of the first message in the buffer of the message given as parameter.
     */
    public void writePartialMessageToMessage(Message message, int endIndex){
//        int startIndexOfPartialMessage = message.offset + endIndex;
        int startIndexOfPartialMessage = endIndex + 1;
        int lengthOfPartialMessage     = (message.offset + message.length) - endIndex + 1;

        System.arraycopy(message.sharedArray, startIndexOfPartialMessage, this.sharedArray, this.offset, lengthOfPartialMessage);
    }

    public int writeToByteBuffer(ByteBuffer byteBuffer){
        return 0;
    }



}
