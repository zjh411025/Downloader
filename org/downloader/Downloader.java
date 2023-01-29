package org.downloader;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

public class Downloader {
    private static final int DEFAULT_THREAD_COUNT = 4;  // Ĭ���߳�����
    private final int threadCount; // �߳�����
    private final String url;
    private final AtomicBoolean canceled; // ȡ��״̬�������һ�����̳߳����쳣����ȡ��������������
    private final String storageLocation;
    private final boolean Delete_log;
    private final String log_path;
    private final boolean Remove_files;
    private DownloadFile file; // ���ص��ļ�����
    private long fileSize; // �ļ���С
    private long beginTime; // ��ʼʱ��
    private Logger logger;

    public Downloader(String url, String filepath, boolean log_m, boolean remove_files) {
        this(url, DEFAULT_THREAD_COUNT, filepath, log_m, remove_files);
    }

    public Downloader(String url, int threadCount, String filename, boolean log_m, boolean remove_files) {
        this.url = url;
        this.threadCount = threadCount;
        this.canceled = new AtomicBoolean(false);
        this.storageLocation = filename;
        this.log_path = storageLocation + ".log";
        this.logger = new Logger(log_path, url, threadCount);
        this.Delete_log = log_m;
        this.Remove_files = remove_files;
    }


    public void start() {
        boolean reStart = Files.exists(Path.of(storageLocation + ".log"));
        File file_path = new File(new File(storageLocation).getParent());
        if (!file_path.exists()) {
            file_path.mkdirs();
        }

        if (Remove_files) {
            if (new File(storageLocation).exists()) {
                new File(storageLocation).delete();
                if (reStart) {
                    logger = new Logger(storageLocation + ".log");
                    System.out.printf("* �����ϴ����ؽ���[�����أ�%.2fMB]��%s\n", logger.getWroteSize() / 1014.0 / 1024, url);
                } else {
                    System.out.println("* ��ʼ���أ�" + url);
                }
                if (-1 == (this.fileSize = getFileSize())) return;
                System.out.printf("* �ļ���С��%.2fMB\n", fileSize / 1024.0 / 1024);

                this.beginTime = System.currentTimeMillis();
                try {
                    this.file = new DownloadFile(storageLocation, fileSize, logger);
                    if (reStart) {
                        file.setWroteSize(logger.getWroteSize());
                    }
                    // �����߳�����
                    dispatcher(reStart);
                    // ѭ����ӡ����
                    printDownloadProgress();
                } catch (IOException e) {
                    System.err.println("x �����ļ�ʧ��[" + e.getMessage() + "]");
                }
            } else {
                if (reStart) {
                    logger = new Logger(storageLocation + ".log");
                    System.out.printf("* �����ϴ����ؽ���[�����أ�%.2fMB]��%s\n", logger.getWroteSize() / 1014.0 / 1024, url);
                } else {
                    System.out.println("* ��ʼ���أ�" + url);
                }
                if (-1 == (this.fileSize = getFileSize())) return;
                System.out.printf("* �ļ���С��%.2fMB\n", fileSize / 1024.0 / 1024);

                this.beginTime = System.currentTimeMillis();
                try {
                    this.file = new DownloadFile(storageLocation, fileSize, logger);
                    if (reStart) {
                        file.setWroteSize(logger.getWroteSize());
                    }
                    // �����߳�����
                    dispatcher(reStart);
                    // ѭ����ӡ����
                    printDownloadProgress();
                } catch (IOException e) {
                    System.err.println("x �����ļ�ʧ��[" + e.getMessage() + "]");
                }
            }

        } else {
            if (!new File(storageLocation).exists()) {
                if (reStart) {
                    logger = new Logger(storageLocation + ".log");
                    System.out.printf("* �����ϴ����ؽ���[�����أ�%.2fMB]��%s\n", logger.getWroteSize() / 1014.0 / 1024, url);
                } else {
                    System.out.println("* ��ʼ���أ�" + url);
                }
                if (-1 == (this.fileSize = getFileSize())) return;
                System.out.printf("* �ļ���С��%.2fMB\n", fileSize / 1024.0 / 1024);

                this.beginTime = System.currentTimeMillis();
                try {
                    this.file = new DownloadFile(storageLocation, fileSize, logger);
                    if (reStart) {
                        file.setWroteSize(logger.getWroteSize());
                    }
                    // �����߳�����
                    dispatcher(reStart);
                    // ѭ����ӡ����
                    printDownloadProgress();
                } catch (IOException e) {
                    System.err.println("x �����ļ�ʧ��[" + e.getMessage() + "]");
                }
            } else {
                System.out.println("�ļ����ڲ�������");
            }
        }
    }
    /**
     * ������������ÿ���߳������ĸ����������
     */
    private void dispatcher(boolean reStart) {
        long blockSize = fileSize / threadCount; // ÿ���߳�Ҫ���ص�������
        long lowerBound = 0, upperBound = 0;
        long[][] bounds = null;
        int threadID = 0;
        if (reStart) {
            bounds = logger.getBounds();
        }

        for (int i = 0; i < threadCount; i++) {
            if (reStart) {
                threadID = (int) (bounds[i][0]);
                lowerBound = bounds[i][1];
                upperBound = bounds[i][2];
            } else {
                threadID = i;
                lowerBound = i * blockSize;
                // fileSize-1 !!!!! fu.ck������һ����Ĵ�
                upperBound = (i == threadCount - 1) ? fileSize - 1 : lowerBound + blockSize;
            }

            new DownloadTask(url, lowerBound, upperBound, file, canceled, threadID).start();

        }

    }

    /**
     * ѭ����ӡ���ȣ�ֱ��������ϣ�������ȡ��
     */
    private void printDownloadProgress() {
        long downloadedSize = file.getWroteSize();
        int i = 0;
        long lastSize = 0; // ����ǰ��������
        while (!canceled.get() && downloadedSize < fileSize) {
            if (i++ % 4 == 3) { // ÿ3���ӡһ��
                System.out.printf("���ؽ��ȣ�%.2f%%, �����أ�%.2fMB����ǰ�ٶȣ�%.2fMB/s\n", downloadedSize / (double) fileSize * 100, downloadedSize / 1024.0 / 1024, (downloadedSize - lastSize) / 1024.0 / 1024 / 3);
                lastSize = downloadedSize;
                i = 0;
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ignore) {
            }
            downloadedSize = file.getWroteSize();
        }
        file.close();
        if (canceled.get()) {
            try {
                Files.delete(Path.of(storageLocation));
            } catch (IOException ignore) {
            }
            System.err.println("x ����ʧ�ܣ�������ȡ��");
        } else {
            System.out.println("* ���سɹ���������ʱ" + (System.currentTimeMillis() - beginTime) / 1000 + "��");
            if (Delete_log) {
                new File(log_path).delete();
            }
        }
    }

    /**
     * @return Ҫ���ص��ļ��ĳߴ�
     */
    private long getFileSize() {
        if (fileSize != 0) {
            return fileSize;
        }
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(3000);
            conn.setRequestMethod("HEAD");
            conn.connect();
            System.out.println("* ���ӷ������ɹ�");
        } catch (MalformedURLException e) {
            throw new RuntimeException("URL����");
        } catch (IOException e) {
            System.err.println("x ���ӷ�����ʧ��[" + e.getMessage() + "]");
            return -1;
        }
        return conn.getContentLengthLong();
    }
}

