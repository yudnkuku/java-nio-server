package com.jenkov.nioserver;

/**
 * A shared buffer which can contain many messages inside. A message gets a section of the buffer to use. If the
 * message outgrows the section in size, the message requests a larger section and the message is copied to that
 * larger section. The smaller section is then freed again.
 *
 *
 * Created by jjenkov on 18-10-2015.
 */
public class MessageBuffer {

    public static int KB = 1024;
    public static int MB = 1024 * KB;

    private static final int CAPACITY_SMALL  =   4  * KB;
    private static final int CAPACITY_MEDIUM = 128  * KB;
    private static final int CAPACITY_LARGE  = 1024 * KB;

    //消息数组
    byte[]  smallMessageBuffer  = new byte[1024 *   4 * KB];   //4KB block * 1024
    byte[]  mediumMessageBuffer = new byte[128  * 128 * KB];   //128KB block * 128
    byte[]  largeMessageBuffer  = new byte[16   *   1 * MB];   //1MB block * 16

    //QueueIntFlip维护了消息数组的偏移地址,其容量是根据数组块的数量来确定的
    // 例如对于smallMessageBuffer block size 4KB * 1024，对应的QueueIntFlip capacity 1024
    QueueIntFlip smallMessageBufferFreeBlocks  = new QueueIntFlip(1024); // 1024 free sections
    QueueIntFlip mediumMessageBufferFreeBlocks = new QueueIntFlip(128);  // 128  free sections
    QueueIntFlip largeMessageBufferFreeBlocks  = new QueueIntFlip(16);   // 16   free sections

    //todo make all message buffer capacities and block sizes configurable
    //todo calculate free block queue sizes based on capacity and block size of buffers.

    public MessageBuffer() {
        //add all free sections to all free section queues.
        for(int i=0; i<smallMessageBuffer.length; i+= CAPACITY_SMALL){
            this.smallMessageBufferFreeBlocks.put(i);
        }
        for(int i=0; i<mediumMessageBuffer.length; i+= CAPACITY_MEDIUM){
            this.mediumMessageBufferFreeBlocks.put(i);
        }
        for(int i=0; i<largeMessageBuffer.length; i+= CAPACITY_LARGE){
            this.largeMessageBufferFreeBlocks.put(i);
        }
    }

    //获取Message实例，优先使用smallMessageBuffer，即最小单位是4KB的块
    public Message getMessage() {
        int nextFreeSmallBlock = this.smallMessageBufferFreeBlocks.take();

        if(nextFreeSmallBlock == -1) return null;

        Message message = new Message(this);       //todo get from Message pool - caps memory usage.

        message.sharedArray = this.smallMessageBuffer;
        message.capacity    = CAPACITY_SMALL;
        message.offset      = nextFreeSmallBlock;
        message.length      = 0;

        return message;
    }

    /**
     * 扩容操作，将消息复制到更大的数组中
     * @param message
     * @return
     */
    public boolean expandMessage(Message message){
        if(message.capacity == CAPACITY_SMALL){
            return moveMessage(message, this.smallMessageBufferFreeBlocks, this.mediumMessageBufferFreeBlocks, this.mediumMessageBuffer, CAPACITY_MEDIUM);
        } else if(message.capacity == CAPACITY_MEDIUM){
            return moveMessage(message, this.mediumMessageBufferFreeBlocks, this.largeMessageBufferFreeBlocks, this.largeMessageBuffer, CAPACITY_LARGE);
        } else {
            return false;
        }
    }

    private boolean moveMessage(Message message, QueueIntFlip srcBlockQueue, QueueIntFlip destBlockQueue, byte[] dest, int newCapacity) {
        int nextFreeBlock = destBlockQueue.take();
        if(nextFreeBlock == -1) return false;

        System.arraycopy(message.sharedArray, message.offset, dest, nextFreeBlock, message.length);

        //此时srcBlockQueue的writePos=capacity，会将writePos置0并将flipped标志位置true，表示数组反转，此时写位置在读位置前面
        //并将消息的offset写入srcBlockQueue
        srcBlockQueue.put(message.offset); //free smaller block after copy

        message.sharedArray = dest;
        message.offset      = nextFreeBlock;
        message.capacity    = newCapacity;
        return true;
    }





}
