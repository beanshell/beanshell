package bsh;

import bsh.congo.parser.BaseNode;
import bsh.congo.tree.*;

public class TreeAdapter extends BaseNode.Visitor {
    private BaseNode root;
    private SimpleNode legacyRoot, currentLegacyNode;

    static public SimpleNode convert(BaseNode root) {
        TreeAdapter adapter = new TreeAdapter(root);
        adapter.visit(root);
        return adapter.legacyRoot;
    }

    TreeAdapter(BaseNode root) {this.root = root;}

    BaseNode getRootNode() {return root;}
    
    void visit(ImportDeclaration idecl) {
        BSHImportDeclaration legacyImport = new BSHImportDeclaration(idecl);
        currentLegacyNode.add(legacyImport);
    }

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
