package uj.wmii.pwj.exec;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class FilesCreator {

    static final int SIZE = 500;

    public static void main(String[] args) throws IOException {
        long tt0 = System.currentTimeMillis();
        for (int i = 0; i < SIZE; i++) {
            long t0 = System.currentTimeMillis();
            System.out.println("Directory " + i + " of " + SIZE);
            Path p = Path.of("data/" + i);
            Files.createDirectories(p);
            for (int j = 0; j < SIZE; j++) {
                p = Path.of("data/" + i + "/" + j + ".txt");
                Files.createFile(p);
                String s;
                for (int k = 0; k < SIZE; k++) {
                    s = i+":"+j+":"+k+":Lorem ipsum dolor sil amet...\n";
                    Files.write(p, s.getBytes(), StandardOpenOption.APPEND);
                }
            }
            long t1 = System.currentTimeMillis();
            System.out.println("completed in " + (t1 - t0) + " ms");
        }
        long tt1 = System.currentTimeMillis();
        System.out.println("TOTAL: " + (tt1 - tt0) + " ms");
    }
}
