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
        if (currentLegacyNode != null) currentLegacyNode.add(legacyBreakStatement);
    }

    void visit(ContinueStatement cs) {
        BSHReturnStatement legacyContinueStatement = new BSHReturnStatement(cs);
        if (currentLegacyNode != null) currentLegacyNode.add(legacyContinueStatement);
    }

    void visit(FormalParameter param) {
        BSHFormalParameter legacyParam = new BSHFormalParameter(param);
        if (currentLegacyNode != null) currentLegacyNode.add(legacyParam);
        currentLegacyNode = legacyParam;
        recurse(param);
        currentLegacyNode = legacyParam.jjtGetParent();
    }
    
    void visit(FormalParameters params) {
        BSHFormalParameters legacyFormalParameters = new BSHFormalParameters(params);
        if (currentLegacyNode != null) currentLegacyNode.add(legacyFormalParameters);
        currentLegacyNode = legacyFormalParameters;
        recurse(params);
        currentLegacyNode = legacyFormalParameters.jjtGetParent();
    }

    void visit(ImportDeclaration idecl) {
        BSHImportDeclaration legacyImport = new BSHImportDeclaration(idecl);
        if (currentLegacyNode != null) currentLegacyNode.add(legacyImport);
    }

    void visit(InvocationArguments arguments) {
        BSHArguments legacyArguments = new BSHArguments(arguments);
        if (currentLegacyNode != null) currentLegacyNode.add(legacyArguments);
        currentLegacyNode = legacyArguments;
        recurse(arguments);
        currentLegacyNode = legacyArguments.jjtGetParent();
    }

    void visit(ObjectType ot) {
        BSHType legacyType = new BSHType(ot);
        currentLegacyNode.add(legacyType);
    }

    void visit(PackageDeclaration pdecl) {
        BSHPackageDeclaration legacyPackageDeclaration = new BSHPackageDeclaration(pdecl);
        if (currentLegacyNode != null) currentLegacyNode.add(legacyPackageDeclaration);
    }

    void visit(PrimitiveArrayType pat) {
        BSHType legacyType = new BSHType(pat);
        if (currentLegacyNode != null) currentLegacyNode.add(legacyType);
        currentLegacyNode = legacyType;
        visit(pat.firstChildOfType(PrimitiveType.class));
        currentLegacyNode = currentLegacyNode.jjtGetParent();
    }
    
    void visit(ReferenceType rt) {
        BSHType legacyReferenceType = new BSHType(rt);
        if (currentLegacyNode != null) currentLegacyNode.add(legacyReferenceType);
        currentLegacyNode = legacyReferenceType;
        recurse(rt);
        currentLegacyNode = legacyReferenceType.jjtGetParent();
    }

    void visit(PrimitiveType pt) {
        BSHPrimitiveType legacyPrimitiveType = new BSHPrimitiveType(pt);
        if (currentLegacyNode != null) currentLegacyNode.add(legacyPrimitiveType);
    }

    void visit(ReturnStatement rs) {
        BSHReturnStatement legacyReturnStatement = new BSHReturnStatement(rs);
        if (currentLegacyNode != null) currentLegacyNode.add(legacyReturnStatement);
        currentLegacyNode = legacyReturnStatement;
        recurse(rs);
        currentLegacyNode = legacyReturnStatement.jjtGetParent();
    }
    
    void visit(ReturnType rt) {
        BSHReturnType legacyReturnType = new BSHReturnType(rt);
        if (currentLegacyNode != null) currentLegacyNode.add(legacyReturnType);
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
        if (currentLegacyNode != null) currentLegacyNode.add(legacyThrowStatement);
        currentLegacyNode = legacyThrowStatement;
        recurse(throwStatement);
        currentLegacyNode = legacyThrowStatement.jjtGetParent();
    }

    void visit(TryStatement tryStatement) {
        BSHTryStatement legacyTryStatement = new BSHTryStatement(tryStatement);
        if (currentLegacyNode != null) currentLegacyNode.add(legacyTryStatement);
        currentLegacyNode = legacyTryStatement;
        recurse(tryStatement);
        currentLegacyNode = legacyTryStatement.jjtGetParent();
    }

    void visit(TryWithResources tryWithResources) {
        BSHTryWithResources legacyTryWithResources = new BSHTryWithResources(tryWithResources);
        if (currentLegacyNode != null) currentLegacyNode.add(legacyTryWithResources);
        currentLegacyNode = legacyTryWithResources;
        recurse(tryWithResources);
        currentLegacyNode = legacyTryWithResources.jjtGetParent();
    }

    void visit(UnaryExpression ue) {
        BSHUnaryExpression legacyUe = new BSHUnaryExpression(ue);
        if (currentLegacyNode != null) currentLegacyNode.add(legacyUe);
        currentLegacyNode = legacyUe;
        visit(ue.firstChildOfType(Expression.class));
        currentLegacyNode = legacyUe.jjtGetParent();
    }

    void visit(VariableDeclarator vd) {
        BSHVariableDeclarator legacyVd = new BSHVariableDeclarator(vd);
        if (currentLegacyNode != null) currentLegacyNode.add(legacyVd);
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
        if (currentLegacyNode != null) currentLegacyNode.add(legacyDoStatement);
        currentLegacyNode = legacyDoStatement;
        recurse(ds);
        currentLegacyNode = legacyDoStatement.jjtGetParent();
    }

    void visit(WhileStatement ws) {
        BSHWhileStatement legacyWhileStatement = new BSHWhileStatement(ws);
        if (legacyRoot == null) {
            legacyRoot = legacyWhileStatement;
        }
        if (currentLegacyNode != null) currentLegacyNode.add(legacyWhileStatement);
        currentLegacyNode = legacyWhileStatement;
        recurse(ws);
        currentLegacyNode = legacyWhileStatement.jjtGetParent();
    }

    void visit(AdditiveExpression addExp) {
        BSHBinaryExpression legacyAdditiveExpession = new BSHBinaryExpression(addExp);
        if (currentLegacyNode != null) currentLegacyNode.add(legacyAdditiveExpession);
        currentLegacyNode = legacyAdditiveExpession;
        recurse(addExp);
        currentLegacyNode = legacyAdditiveExpession.jjtGetParent();
    }

    void visit(MultiplicativeExpression multExp) {
        BSHBinaryExpression legacyMultiplicativeExpression = new BSHBinaryExpression(multExp);
        if (currentLegacyNode != null) currentLegacyNode.add(legacyMultiplicativeExpression);
        currentLegacyNode = legacyMultiplicativeExpression;
        recurse(multExp);
        currentLegacyNode = legacyMultiplicativeExpression.jjtGetParent();
    }

    void visit(PowerExpression powExp) {
        BSHBinaryExpression legacyPowerExpression = new BSHBinaryExpression(powExp);
        if (currentLegacyNode != null) currentLegacyNode.add(legacyPowerExpression);
        currentLegacyNode = legacyPowerExpression;
        recurse(powExp);
        currentLegacyNode = legacyPowerExpression.jjtGetParent();
    }

    void visit(ConditionalOrExpression condOrExpression) {
        BSHBinaryExpression legacyConditionalOrExpression = new BSHBinaryExpression(condOrExpression);
        if (currentLegacyNode != null) currentLegacyNode.add(legacyConditionalOrExpression);
        currentLegacyNode = legacyConditionalOrExpression;
        recurse(condOrExpression);
        currentLegacyNode = legacyConditionalOrExpression.jjtGetParent();
    }

    void visit(ConditionalAndExpression condAndExpression) {
        BSHBinaryExpression legacyConditionalAndExpression = new BSHBinaryExpression(condAndExpression);
        if (currentLegacyNode != null) currentLegacyNode.add(legacyConditionalAndExpression);
        currentLegacyNode = legacyConditionalAndExpression;
        recurse(condAndExpression);
        currentLegacyNode = legacyConditionalAndExpression.jjtGetParent();
    }

    void visit(InclusiveOrExpression inclusiveOrExpression) {
        BSHBinaryExpression legacyInclusiveOrExpression = new BSHBinaryExpression(inclusiveOrExpression);
        if (currentLegacyNode != null) currentLegacyNode.add(legacyInclusiveOrExpression);
        currentLegacyNode = legacyInclusiveOrExpression;
        recurse(inclusiveOrExpression);
        currentLegacyNode = legacyInclusiveOrExpression.jjtGetParent();
    }

    void visit(ExclusiveOrExpression exclusiveOrExpression) {
        BSHBinaryExpression legacyExclusiveOrExpression = new BSHBinaryExpression(exclusiveOrExpression);
        if (currentLegacyNode != null) currentLegacyNode.add(legacyExclusiveOrExpression);
        currentLegacyNode = legacyExclusiveOrExpression;
        recurse(exclusiveOrExpression);
        currentLegacyNode = legacyExclusiveOrExpression.jjtGetParent();
    }

    void visit(EqualityExpression equalityExpression) {
        BSHBinaryExpression legacyEqualityExpression = new BSHBinaryExpression(equalityExpression);
        if (currentLegacyNode != null) currentLegacyNode.add(legacyEqualityExpression);
        currentLegacyNode = legacyEqualityExpression;
        recurse(equalityExpression);
        currentLegacyNode = legacyEqualityExpression.jjtGetParent();
    }

    void visit(AndExpression andExpression) {
        BSHBinaryExpression legacyAndExpression = new BSHBinaryExpression(andExpression);
        if (currentLegacyNode != null) currentLegacyNode.add(legacyAndExpression);
        currentLegacyNode = legacyAndExpression;
        recurse(andExpression);
        currentLegacyNode = legacyAndExpression.jjtGetParent();
    }

    void visit(RelationalExpression relationalExpression) {
        BSHBinaryExpression legacyRelationalExpression = new BSHBinaryExpression(relationalExpression);
        if (currentLegacyNode != null) currentLegacyNode.add(legacyRelationalExpression);
        currentLegacyNode = legacyRelationalExpression;
        recurse(relationalExpression);
        currentLegacyNode = legacyRelationalExpression.jjtGetParent();
    }

    void visit(ShiftExpression shiftExpression) {
        BSHBinaryExpression legacyShiftExpression = new BSHBinaryExpression(shiftExpression);
        if (currentLegacyNode != null) currentLegacyNode.add(legacyShiftExpression);
        currentLegacyNode = legacyShiftExpression;
        recurse(shiftExpression);
        currentLegacyNode = legacyShiftExpression.jjtGetParent();
    }

    void visit(InstanceOfExpression instanceOfExpression) {
        BSHBinaryExpression legacyInstanceOfExpression = new BSHBinaryExpression(instanceOfExpression);
        if (currentLegacyNode != null) currentLegacyNode.add(legacyInstanceOfExpression);
        currentLegacyNode = legacyInstanceOfExpression;
        recurse(instanceOfExpression);
        currentLegacyNode = legacyInstanceOfExpression.jjtGetParent();
    }

    void visit(NullCoalesceElvisSpaceShipExpression nullCoalesceElvisSpaceShipExpression) {
        BSHBinaryExpression legacyNullCoalesceElvisSpaceShipExpression = new BSHBinaryExpression(nullCoalesceElvisSpaceShipExpression);
        if (currentLegacyNode != null) currentLegacyNode.add(legacyNullCoalesceElvisSpaceShipExpression);
        currentLegacyNode = legacyNullCoalesceElvisSpaceShipExpression;
        recurse(nullCoalesceElvisSpaceShipExpression);
        currentLegacyNode = legacyNullCoalesceElvisSpaceShipExpression.jjtGetParent();
    }

    void visit(CodeBlock block) {
        BSHBlock legacyBlock = new BSHBlock(block);
        if (currentLegacyNode != null) currentLegacyNode.add(legacyBlock);
        currentLegacyNode = legacyBlock;
        recurse(block);
        currentLegacyNode = legacyBlock.jjtGetParent();
    }
}
