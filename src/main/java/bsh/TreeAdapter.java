package bsh;

import bsh.congo.parser.BaseNode;
import bsh.congo.tree.*;
import bsh.congo.parser.BeanshellConstants.TokenType;

public class TreeAdapter extends BaseNode.Visitor {
    private BaseNode root;
    private Node legacyRoot, currentLegacyNode;

    static public Node convert(BaseNode root) {
        TreeAdapter adapter = new TreeAdapter(root);
        adapter.visit(root);
        return adapter.legacyRoot;
    }

    TreeAdapter(BaseNode root) {this.root = root;}

    BaseNode getRootNode() {return root;}

    void visit(BreakStatement bs) {
        BSHReturnStatement legacyBreakStatement = new BSHReturnStatement(bs);
        currentLegacyNode.add(legacyBreakStatement);
    }

    void visit(ContinueStatement cs) {
        BSHReturnStatement legacyContinueStatement = new BSHReturnStatement(cs);
        currentLegacyNode.add(legacyContinueStatement);
    }

    void visit(ImportDeclaration idecl) {
        BSHImportDeclaration legacyImport = new BSHImportDeclaration(idecl);
        currentLegacyNode.add(legacyImport);
    }

    void visit(PackageDeclaration pdecl) {
        BSHPackageDeclaration legacyPackageDeclaration = new BSHPackageDeclaration(pdecl);
        currentLegacyNode.add(legacyPackageDeclaration);
    }

    void visit(ReturnStatement rs) {
        BSHReturnStatement legacyReturnStatement = new BSHReturnStatement(rs);
        currentLegacyNode.add(legacyReturnStatement);
        currentLegacyNode = legacyReturnStatement;
        recurse(rs);
        currentLegacyNode = legacyReturnStatement.jjtGetParent();
    }
    
    void visit(ReturnType rt) {
        BSHReturnType legacyReturnType = new BSHReturnType(rt);
        currentLegacyNode.add(legacyReturnType);
        if (rt.firstChildOfType(TokenType.VOID) != null) {
            legacyReturnType.isVoid = true;
        } else {
            currentLegacyNode = legacyReturnType;
            recurse(rt);
            currentLegacyNode = legacyReturnType.jjtGetParent();
        }
    }

    void visit(ThrowStatement throwStatement) {
        BSHThrowStatement legacyThrowStatement = new BSHThrowStatement(throwStatement);
        currentLegacyNode.add(legacyThrowStatement);
        currentLegacyNode = legacyThrowStatement;
        recurse(throwStatement);
        currentLegacyNode = legacyThrowStatement.jjtGetParent();
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
        currentLegacyNode = legacyWs.jjtGetParent();
    }
}
