package org.downloader;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.atomic.AtomicLong;

class DownloadFile {
    private final RandomAccessFile file;
    private final FileChannel channel; // �̰߳�ȫ��
    private AtomicLong wroteSize; // ��д��ĳ���
    private Logger logger;

    DownloadFile(String fileName, long fileSize, Logger logger) throws IOException {
        this.wroteSize = new AtomicLong(0);
        this.logger = logger;
        this.file = new RandomAccessFile(fileName, "rw");
        file.setLength(fileSize);
        channel = file.getChannel();
    }

    /**
     * д����
     * @param offset дƫ����
     * @param buffer ����
     * @throws IOException д���ݳ����쳣
     */
    void write(long offset, ByteBuffer buffer, int threadID, long upperBound) throws IOException {
        buffer.flip();
        int length = buffer.limit();
        while (buffer.hasRemaining()) {
            channel.write(buffer, offset);
        }
        wroteSize.addAndGet(length);
        logger.updateLog(threadID, length, offset + length, upperBound); // ������־
    }

    /**
     * @return �Ѿ����ص���������Ϊ��֪����ʱ�������������Լ�ͳ����Ϣ
     */
    long getWroteSize() {
        return wroteSize.get();
    }

    // ��������ʱ����
    void setWroteSize(long wroteSize) {
        this.wroteSize.set(wroteSize);
    }

    void close() {
        try {
            file.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
