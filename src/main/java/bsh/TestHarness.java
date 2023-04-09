package bsh;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.ArrayList;

import bsh.congo.parser.Node;
import bsh.congo.parser.BeanshellParser;
import bsh.congo.parser.ParseException;
import bsh.congo.tree.SynchronizedStatement;

public class TestHarness {

    static private FileSystem fileSystem = FileSystems.getDefault();

    static public void main(String[] args) {
        if (args.length ==0) {
            usage();
        }
        int successes = 0;
        List<Path> failures = new ArrayList<>();
        for (String arg : args) {
            Path path = fileSystem.getPath(arg);
            if (!Files.exists(path)) {
                System.out.println("File " + path + " does not exist.");
                continue;
            }
            try {
               dump(path);
               ++successes;
            } catch (Exception e) {
                failures.add(path);
                e.printStackTrace();
            }
        }
        System.out.println("" + failures.size() + " failures");
        System.out.println("" + successes + " successes");
        for (Path path : failures) {
            System.out.println("Parse failed on: " + path);
        }
    }

    static void dump(Path path) throws IOException {
        BeanshellParser parser = new BeanshellParser(path);
        List<Node> statements = parser.Statements();
        for (Node n : parser.Statements()) {
            System.out.println(n.getClass().getCanonicalName() + ":" + n.getLocation());
            System.out.println("------");
            n.dump();
            System.out.println("------");
        }
    }

    static void usage() {
        System.err.println("Usage: java bsh.TestHarness <filename>");
        System.exit(-1);
    }
}
