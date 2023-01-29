package org.downloader;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.concurrent.atomic.AtomicBoolean;

class DownloadTask extends Thread {
    private final String url;
    private long lowerBound; // ���ص��ļ�����
    private long upperBound;
    private AtomicBoolean canceled;
    private DownloadFile downloadFile;
    private int threadId;

    DownloadTask(String url, long lowerBound, long upperBound, DownloadFile downloadFile,
                        AtomicBoolean canceled, int threadID) {
        this.url = url;
        this.lowerBound = lowerBound;
        this.upperBound = upperBound;
        this.canceled = canceled;
        this.downloadFile = downloadFile;
        this.threadId = threadID;
    }

    @Override
    public void run() {
        ReadableByteChannel input = null;
        try {
            ByteBuffer buffer = ByteBuffer.allocate(1024 * 1024 * 2); // 2MB
            input = connect();
            System.out.println("* [�߳�" + threadId + "]���ӳɹ�����ʼ����...");

            int len;
            while (!canceled.get() && lowerBound <= upperBound) {

                buffer.clear();
                len = input.read(buffer);
                downloadFile.write(lowerBound, buffer, threadId, upperBound);
                lowerBound += len;
            }
            if (!canceled.get()) {
                System.out.println("* [�߳�" + threadId + "]�������" + ": " + lowerBound + "-" + upperBound);
            }
        } catch (IOException e) {
            canceled.set(true);
            System.err.println("x [�߳�" + threadId + "]��������[" + e.getMessage() + "]����������");
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * ����WEB��������������һ������ͨ��
     * @return ����ͨ��
     * @throws IOException �������Ӵ���
     */
    private ReadableByteChannel connect() throws IOException {
        HttpURLConnection conn = (HttpURLConnection)new URL(url).openConnection();
        conn.setConnectTimeout(3000);
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Range", "bytes=" + lowerBound + "-" + upperBound);
//        System.out.println("thread_"+ threadId +": " + lowerBound + "-" + upperBound);
        conn.connect();

        int statusCode = conn.getResponseCode();
        if (HttpURLConnection.HTTP_PARTIAL != statusCode) {
            conn.disconnect();
            throw new IOException("״̬�����" + statusCode);
        }

        return Channels.newChannel(conn.getInputStream());
    }
}
