package io.dropwizard.metrics.atsd;

/**
 * @author Dmitry Korchagin.
 */

import io.dropwizard.metrics.*;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class LoadTest {
    private static final int DEFAULT_BUFFER_SIZE = 102401;
    private static final int EOF = -1;
    private static final String HOSTNAME = "localhost";
    private static final int PORT = 8085;
    private static ServerSocket serverSocket;

    @Before
    public void setUp() {
        try {
            serverSocket = new ServerSocket(PORT);
        } catch (IOException e) {
            System.out.println("unable to open socket");
        }
    }


//    @Ignore
    @Test
    public void testLoad() {
        int metricCount = 1000000;
        int duration = 1000;
//        HashMap<String, String> tags = new HashMap<>();
//        tags = Utils::sanitize(tags);

        MetricRegistry metrics = new MetricRegistry();

        for (int i = 0; i < metricCount; i++) {
            metrics.meter(new MetricName("metricName" + String.valueOf(i)));
        }

        final AtsdTCPSender sender = new AtsdTCPSender(new InetSocketAddress(HOSTNAME, PORT));
        final AtsdReporter reporter = AtsdReporter.forRegistry(metrics)
                .setMetricPrefix("test")
                .build(sender);

        final CountDownLatch startServerLatch = new CountDownLatch(1);

        ExecutorService executorService = Executors.newSingleThreadExecutor();
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    startServerLatch.await(1000, TimeUnit.MILLISECONDS);
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                reporter.report();
                reporter.stop();
            }
        });

        Socket clientSocket = null;
        try {
            startServerLatch.countDown();
            clientSocket = serverSocket.accept();
            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

            int count = 0;
            char[] buffer = new char[DEFAULT_BUFFER_SIZE];
            long startTime = System.currentTimeMillis();
            int n;
            boolean counting = true;
            while (EOF != (n = in.read(buffer))) {
                for (int i = 0; i < n; i++) {
                    char c = buffer[i];
                    if (counting && c == '\n') {
                        count++;
                    }
                }
                if (System.currentTimeMillis() - startTime > duration) {
                    counting = false;
                }
            }
            long endTime = System.currentTimeMillis() - startTime;

            System.out.println("Recived " + count + " series in " + duration + " ms.");
            System.out.println("Recived " + metricCount + " series in " + endTime + " ms.");
        } catch (Exception e) {
            System.out.println("Can not accept socket");
        } finally {
            try {
                clientSocket.close();
            } catch (Exception ex) {
                System.out.println("Unable to close socket");
            }
        }
    }

    @After
    public void tearDown() {
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (Exception e) {
                System.out.println("Unable to close socket");
            }

        }
    }

}
