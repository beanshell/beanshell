package bsh;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;

import java.util.ArrayList;
import java.util.List;

import bsh.congo.parser.Node;
import bsh.congo.parser.BaseNode;
import bsh.congo.parser.BeanshellParser;

public class TestHarness {

    static private FileSystem fileSystem = FileSystems.getDefault();

    static public void main(String[] args) throws IOException {
        if (args.length ==0) {
            usage();
        }
        Path path = fileSystem.getPath(args[0]);
        if (!Files.exists(path)) {
            System.err.println("File " + path + " does not exist.");
             System.exit(-1);
        }
        BeanshellParser parser = new BeanshellParser(path);
        List<Node> statements = parser.Statements();
        for (Node n : statements) {
            n.dump();
            TreeAdapter.convert((BaseNode) n).dump();
        }
        List<Node> convertedNodes = new ArrayList<Node>(); 
        for (Node n : statements) convertedNodes.add((SimpleNode) TreeAdapter.convert((BaseNode)n));
        for (Node n : convertedNodes) {
            n.dump();
        }
    }

    static void usage() {
        System.err.println("Usage: java bsh.TestHarness <filename>");
        System.exit(-1);
    }

}