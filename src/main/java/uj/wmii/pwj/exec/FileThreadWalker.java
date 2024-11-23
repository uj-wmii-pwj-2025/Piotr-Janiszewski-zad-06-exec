package uj.wmii.pwj.exec;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;

public class FileThreadWalker {
    static final int SIZE = 500;
    static final int REPEATS = 10_000_000;

    public static void main(String[] args) throws InterruptedException {
        long tt0 = System.currentTimeMillis();
        Random r = new Random();
        CountDownLatch latch = new CountDownLatch(REPEATS);
        for (int i = 0; i < REPEATS; i++) {
            int dirIdx = r.nextInt(SIZE);
            int fileIdx = r.nextInt(SIZE);
            int lineIdx = r.nextInt(SIZE);
            Path p = Path.of("data/" + dirIdx + "/" + fileIdx + ".txt");
            MyRunnable runnable = new MyRunnable(p, lineIdx, latch);
            Thread.ofPlatform().name("T-" + i).start(runnable);
        }
        latch.await();
        long tt1 = System.currentTimeMillis();
        System.out.println("TOTAL: " + (tt1 - tt0) + " ms");
    }

}

record MyRunnable(Path path, int lineIdx, CountDownLatch latch) implements Runnable {

    private static final Random r = new Random();

    @Override
    public void run() {
        String line;
        try {
            List<String> lines = Files.readAllLines(path);
            line = lines.get(lineIdx);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            latch.countDown();
        }
        if (r.nextInt(10000) == 12) System.out.println(Thread.currentThread().getName() + " " + line);
    }
}