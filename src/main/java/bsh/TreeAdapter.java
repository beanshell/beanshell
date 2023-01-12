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

    void visit(FormalParameter param) {
        BSHFormalParameter legacyParam = new BSHFormalParameter(param);
        currentLegacyNode.add(legacyParam);
        currentLegacyNode = legacyParam;
        recurse(param);
        currentLegacyNode = legacyParam.jjtGetParent();
    }
    
    void visit(FormalParameters params) {
        BSHFormalParameters legacyFormalParameters = new BSHFormalParameters(params);
        currentLegacyNode.add(legacyFormalParameters);
        currentLegacyNode = legacyFormalParameters;
        recurse(params);
        currentLegacyNode = legacyFormalParameters.jjtGetParent();
    }

    void visit(ImportDeclaration idecl) {
        BSHImportDeclaration legacyImport = new BSHImportDeclaration(idecl);
        currentLegacyNode.add(legacyImport);
    }

    void visit(InvocationArguments arguments) {
        BSHArguments legacyArguments = new BSHArguments(arguments);
        currentLegacyNode.add(legacyArguments);
        currentLegacyNode = legacyArguments;
        recurse(arguments);
        currentLegacyNode = legacyArguments.jjtGetParent();
    }

    void visit(ObjectType ot) {
        BSHAmbiguousName legacyName = new BSHAmbiguousName(ot);
        currentLegacyNode.add(legacyName);
    }

    void visit(PackageDeclaration pdecl) {
        BSHPackageDeclaration legacyPackageDeclaration = new BSHPackageDeclaration(pdecl);
        currentLegacyNode.add(legacyPackageDeclaration);
    }

    void visit(PrimitiveArrayType pat) {
        BSHType legacyType = new BSHType(bsh.ParserTreeConstants.JJTTYPE);
        currentLegacyNode.add(legacyType);
        currentLegacyNode = legacyType;
        visit(pat.firstChildOfType(PrimitiveType.class));
        currentLegacyNode = currentLegacyNode.jjtGetParent();
    }    

    void visit(PrimitiveType pt) {
        BSHPrimitiveType legacyPrimitiveType = new BSHPrimitiveType(pt);
        currentLegacyNode.add(legacyPrimitiveType);
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

    void visit(UnaryExpression ue) {
        BSHUnaryExpression legacyUe = new BSHUnaryExpression(ue);
        currentLegacyNode.add(legacyUe);
        currentLegacyNode = legacyUe;
        visit(ue.firstChildOfType(Expression.class));
        currentLegacyNode = legacyUe.jjtGetParent();
    }

    void visit(VariableDeclarator vd) {
        BSHVariableDeclarator legacyVd = new BSHVariableDeclarator(vd);
        currentLegacyNode.add(legacyVd);
        bsh.congo.parser.Token equals = vd.firstChildOfType(TokenType.ASSIGN);
        if (equals != null) {
            currentLegacyNode = legacyVd;
            visit(equals.nextSibling());
            currentLegacyNode = legacyVd.jjtGetParent();
        }
    }

    void visit(DoStatement ds) {
        BSHWhileStatement legacyDoStatement = new BSHWhileStatement(ds);
        if (legacyRoot == null) {
            legacyRoot = legacyDoStatement;
        }
        if (currentLegacyNode != null) {
            currentLegacyNode.add(legacyDoStatement);
        }
        currentLegacyNode = legacyDoStatement;
        recurse(ds);
        currentLegacyNode = legacyDoStatement.jjtGetParent();
    }

    void visit(WhileStatement ws) {
        BSHWhileStatement legacyWhileStatement = new BSHWhileStatement(ws);
        if (legacyRoot == null) {
            legacyRoot = legacyWhileStatement;
        }
        if (currentLegacyNode != null) {
            currentLegacyNode.add(legacyWhileStatement);
        }
        currentLegacyNode = legacyWhileStatement;
        recurse(ws);
        currentLegacyNode = legacyWhileStatement.jjtGetParent();
    }
}
