package bsh;

import bsh.congo.parser.Node;
import bsh.congo.tree.*;

public class TreeAdapter extends Node.Visitor {
    private Node root;
    private bsh.Node legacyRoot, currentLegacyNode;

    static public bsh.Node convert(Node root) {
        TreeAdapter adapter = new TreeAdapter(root);
        adapter.visit(root);
        return adapter.legacyRoot;
    }

    TreeAdapter(Node root) {this.root = root;}

    Node getRootNode() {return root;} 

    void visit(WhileStatement ws) {
        BSHWhileStatement legacyWs = new BSHWhileStatement(ws);
        if (legacyRoot == null) {
            legacyRoot = legacyWs;
        }
        if (currentLegacyNode != null) {
            currentLegacyNode.add(legacyWs);
        }
        currentLegacyNode = legacyWs;
        recurse(ws);
    }
}
