package bsh;

import bsh.congo.parser.BaseNode;
import bsh.congo.tree.*;
import bsh.congo.parser.BeanshellConstants.TokenType;

public class TreeAdapter extends BaseNode.Visitor {
    private BaseNode root;
    private bsh.congo.parser.Node legacyRoot, currentLegacyNode;

    static public SimpleNode convert(BaseNode root) {
        TreeAdapter adapter = new TreeAdapter(root);
        adapter.visit(root);
        return (SimpleNode) adapter.legacyRoot;
    }

    TreeAdapter(BaseNode root) {this.root = root;}

    BaseNode getRootNode() {return root;}

    void visit(BreakStatement bs) {
        BSHReturnStatement legacyBreakStatement = new BSHReturnStatement(bs);
        if (currentLegacyNode != null) currentLegacyNode.addChild(legacyBreakStatement);
    }

    void visit(ContinueStatement cs) {
        BSHReturnStatement legacyContinueStatement = new BSHReturnStatement(cs);
        if (currentLegacyNode != null) currentLegacyNode.addChild(legacyContinueStatement);
    }

    void visit(FormalParameter param) {
        BSHFormalParameter legacyParam = new BSHFormalParameter(param);
        if (currentLegacyNode != null) currentLegacyNode.addChild(legacyParam);
        currentLegacyNode = legacyParam;
        recurse(param);
        currentLegacyNode = legacyParam.getParent();
    }
    
    void visit(FormalParameters params) {
        BSHFormalParameters legacyFormalParameters = new BSHFormalParameters(params);
        if (currentLegacyNode != null) currentLegacyNode.addChild(legacyFormalParameters);
        currentLegacyNode = legacyFormalParameters;
        recurse(params);
        currentLegacyNode = legacyFormalParameters.getParent();
    }

    void visit(ImportDeclaration idecl) {
        BSHImportDeclaration legacyImport = new BSHImportDeclaration(idecl);
        if (legacyRoot == null) legacyRoot = legacyImport;
        if (currentLegacyNode != null) currentLegacyNode.addChild(legacyImport);
    }

    void visit(InvocationArguments arguments) {
        BSHArguments legacyArguments = new BSHArguments(arguments);
        if (currentLegacyNode != null) currentLegacyNode.addChild(legacyArguments);
        currentLegacyNode = legacyArguments;
        recurse(arguments);
        currentLegacyNode = legacyArguments.getParent();
    }

    void visit(ObjectType ot) {
        BSHType legacyType = new BSHType(ot);
        currentLegacyNode.addChild(legacyType);
    }

    void visit(PackageDeclaration pdecl) {
        BSHPackageDeclaration legacyPackageDeclaration = new BSHPackageDeclaration(pdecl);
        if (legacyRoot == null) legacyRoot = legacyPackageDeclaration;
        if (currentLegacyNode != null) currentLegacyNode.addChild(legacyPackageDeclaration);
    }

    void visit(PrimitiveArrayType pat) {
        BSHType legacyType = new BSHType(pat);
        if (currentLegacyNode != null) currentLegacyNode.addChild(legacyType);
        currentLegacyNode = legacyType;
        visit(pat.firstChildOfType(PrimitiveType.class));
        currentLegacyNode = currentLegacyNode.getParent();
    }
    
    void visit(ReferenceType rt) {
        BSHType legacyReferenceType = new BSHType(rt);
        if (currentLegacyNode != null) currentLegacyNode.addChild(legacyReferenceType);
        currentLegacyNode = legacyReferenceType;
        recurse(rt);
        currentLegacyNode = legacyReferenceType.getParent();
    }

    void visit(PrimitiveType pt) {
        BSHPrimitiveType legacyPrimitiveType = new BSHPrimitiveType(pt);
        if (currentLegacyNode != null) currentLegacyNode.addChild(legacyPrimitiveType);
    }

    void visit(ReturnStatement rs) {
        BSHReturnStatement legacyReturnStatement = new BSHReturnStatement(rs);
        if (legacyRoot == null) legacyRoot = legacyReturnStatement;
        if (currentLegacyNode != null) currentLegacyNode.addChild(legacyReturnStatement);
        currentLegacyNode = legacyReturnStatement;
        recurse(rs);
        currentLegacyNode = legacyReturnStatement.getParent();
    }
    
    void visit(ReturnType rt) {
        BSHReturnType legacyReturnType = new BSHReturnType(rt);
        if (currentLegacyNode != null) currentLegacyNode.addChild(legacyReturnType);
        if (rt.firstChildOfType(TokenType.VOID) != null) {
            legacyReturnType.isVoid = true;
        } else {
            currentLegacyNode = legacyReturnType;
            recurse(rt);
            currentLegacyNode = legacyReturnType.getParent();
        }
    }

    void visit(ThrowStatement throwStatement) {
        BSHThrowStatement legacyThrowStatement = new BSHThrowStatement(throwStatement);
        if (legacyRoot == null) legacyRoot = legacyThrowStatement;
        if (currentLegacyNode != null) currentLegacyNode.addChild(legacyThrowStatement);
        currentLegacyNode = legacyThrowStatement;
        recurse(throwStatement);
        currentLegacyNode = legacyThrowStatement.getParent();
    }

    void visit(TryStatement tryStatement) {
        BSHTryStatement legacyTryStatement = new BSHTryStatement(tryStatement);
        if (legacyRoot == null) legacyRoot = legacyTryStatement;
        if (currentLegacyNode != null) currentLegacyNode.addChild(legacyTryStatement);
        currentLegacyNode = legacyTryStatement;
        recurse(tryStatement);
        currentLegacyNode = legacyTryStatement.getParent();
    }

    void visit(TryWithResources tryWithResources) {
        BSHTryWithResources legacyTryWithResources = new BSHTryWithResources(tryWithResources);
        if (legacyRoot == null) legacyRoot = legacyTryWithResources;
        if (currentLegacyNode != null) currentLegacyNode.addChild(legacyTryWithResources);
        currentLegacyNode = legacyTryWithResources;
        recurse(tryWithResources);
        currentLegacyNode = legacyTryWithResources.getParent();
    }

    void visit(UnaryExpression ue) {
        BSHUnaryExpression legacyUe = new BSHUnaryExpression(ue);
        if (legacyRoot == null) legacyRoot = legacyUe;
        if (currentLegacyNode != null) currentLegacyNode.addChild(legacyUe);
        currentLegacyNode = legacyUe;
        visit(ue.firstChildOfType(Expression.class));
        currentLegacyNode = legacyUe.getParent();
    }

    void visit(VariableDeclarator vd) {
        BSHVariableDeclarator legacyVd = new BSHVariableDeclarator(vd);
        if (currentLegacyNode != null) currentLegacyNode.addChild(legacyVd);
        bsh.congo.parser.Token equals = vd.firstChildOfType(TokenType.ASSIGN);
        if (equals != null) {
            currentLegacyNode = legacyVd;
            visit(equals.nextSibling());
            currentLegacyNode = legacyVd.getParent();
        }
    }

    void visit(DoStatement ds) {
        BSHWhileStatement legacyDoStatement = new BSHWhileStatement(ds);
        if (legacyRoot == null) legacyRoot = legacyDoStatement;
        if (legacyRoot == null) {
            legacyRoot = legacyDoStatement;
        }
        if (currentLegacyNode != null) currentLegacyNode.addChild(legacyDoStatement);
        currentLegacyNode = legacyDoStatement;
        recurse(ds);
        currentLegacyNode = legacyDoStatement.getParent();
    }

    void visit(WhileStatement ws) {
        BSHWhileStatement legacyWhileStatement = new BSHWhileStatement(ws);
        if (legacyRoot == null) legacyRoot = legacyWhileStatement;
        if (legacyRoot == null) {
            legacyRoot = legacyWhileStatement;
        }
        if (currentLegacyNode != null) currentLegacyNode.addChild(legacyWhileStatement);
        currentLegacyNode = legacyWhileStatement;
        recurse(ws);
        currentLegacyNode = legacyWhileStatement.getParent();
    }

    void visit(AdditiveExpression addExp) {
        BSHBinaryExpression legacyAdditiveExpession = new BSHBinaryExpression(addExp);
        if (legacyRoot == null) legacyRoot = legacyAdditiveExpession;
        if (currentLegacyNode != null) currentLegacyNode.addChild(legacyAdditiveExpession);
        currentLegacyNode = legacyAdditiveExpession;
        recurse(addExp);
        currentLegacyNode = legacyAdditiveExpession.getParent();
    }

    void visit(MultiplicativeExpression multExp) {
        BSHBinaryExpression legacyMultiplicativeExpression = new BSHBinaryExpression(multExp);
        if (legacyRoot == null) legacyRoot = legacyMultiplicativeExpression;
        if (currentLegacyNode != null) currentLegacyNode.addChild(legacyMultiplicativeExpression);
        currentLegacyNode = legacyMultiplicativeExpression;
        recurse(multExp);
        currentLegacyNode = legacyMultiplicativeExpression.getParent();
    }

    void visit(PowerExpression powExp) {
        BSHBinaryExpression legacyPowerExpression = new BSHBinaryExpression(powExp);
        if (legacyRoot == null) legacyRoot = legacyPowerExpression;
        if (currentLegacyNode != null) currentLegacyNode.addChild(legacyPowerExpression);
        currentLegacyNode = legacyPowerExpression;
        recurse(powExp);
        currentLegacyNode = legacyPowerExpression.getParent();
    }

    void visit(ConditionalOrExpression condOrExpression) {
        BSHBinaryExpression legacyConditionalOrExpression = new BSHBinaryExpression(condOrExpression);
        if (legacyRoot == null) legacyRoot = legacyConditionalOrExpression;
        if (currentLegacyNode != null) currentLegacyNode.addChild(legacyConditionalOrExpression);
        currentLegacyNode = legacyConditionalOrExpression;
        recurse(condOrExpression);
        currentLegacyNode = legacyConditionalOrExpression.getParent();
    }

    void visit(ConditionalAndExpression condAndExpression) {
        BSHBinaryExpression legacyConditionalAndExpression = new BSHBinaryExpression(condAndExpression);
        if (legacyRoot == null) legacyRoot = legacyConditionalAndExpression;
        if (currentLegacyNode != null) currentLegacyNode.addChild(legacyConditionalAndExpression);
        currentLegacyNode = legacyConditionalAndExpression;
        recurse(condAndExpression);
        currentLegacyNode = legacyConditionalAndExpression.getParent();
    }

    void visit(InclusiveOrExpression inclusiveOrExpression) {
        BSHBinaryExpression legacyInclusiveOrExpression = new BSHBinaryExpression(inclusiveOrExpression);
        if (legacyRoot == null) legacyRoot = legacyInclusiveOrExpression;
        if (currentLegacyNode != null) currentLegacyNode.addChild(legacyInclusiveOrExpression);
        currentLegacyNode = legacyInclusiveOrExpression;
        recurse(inclusiveOrExpression);
        currentLegacyNode = legacyInclusiveOrExpression.getParent();
    }

    void visit(ExclusiveOrExpression exclusiveOrExpression) {
        BSHBinaryExpression legacyExclusiveOrExpression = new BSHBinaryExpression(exclusiveOrExpression);
        if (legacyRoot == null) legacyRoot = legacyExclusiveOrExpression;
        if (currentLegacyNode != null) currentLegacyNode.addChild(legacyExclusiveOrExpression);
        currentLegacyNode = legacyExclusiveOrExpression;
        recurse(exclusiveOrExpression);
        currentLegacyNode = legacyExclusiveOrExpression.getParent();
    }

    void visit(EqualityExpression equalityExpression) {
        BSHBinaryExpression legacyEqualityExpression = new BSHBinaryExpression(equalityExpression);
        if (legacyRoot == null) legacyRoot = legacyEqualityExpression;
        if (currentLegacyNode != null) currentLegacyNode.addChild(legacyEqualityExpression);
        currentLegacyNode = legacyEqualityExpression;
        recurse(equalityExpression);
        currentLegacyNode = legacyEqualityExpression.getParent();
    }

    void visit(AndExpression andExpression) {
        BSHBinaryExpression legacyAndExpression = new BSHBinaryExpression(andExpression);
        if (legacyRoot == null) legacyRoot = legacyAndExpression;
        if (currentLegacyNode != null) currentLegacyNode.addChild(legacyAndExpression);
        currentLegacyNode = legacyAndExpression;
        recurse(andExpression);
        currentLegacyNode = legacyAndExpression.getParent();
    }

    void visit(RelationalExpression relationalExpression) {
        BSHBinaryExpression legacyRelationalExpression = new BSHBinaryExpression(relationalExpression);
        if (legacyRoot == null) legacyRoot = legacyRelationalExpression;
        if (currentLegacyNode != null) currentLegacyNode.addChild(legacyRelationalExpression);
        currentLegacyNode = legacyRelationalExpression;
        recurse(relationalExpression);
        currentLegacyNode = legacyRelationalExpression.getParent();
    }

    void visit(ShiftExpression shiftExpression) {
        BSHBinaryExpression legacyShiftExpression = new BSHBinaryExpression(shiftExpression);
        if (legacyRoot == null) legacyRoot = legacyShiftExpression;
        if (currentLegacyNode != null) currentLegacyNode.addChild(legacyShiftExpression);
        currentLegacyNode = legacyShiftExpression;
        recurse(shiftExpression);
        currentLegacyNode = legacyShiftExpression.getParent();
    }

    void visit(InstanceOfExpression instanceOfExpression) {
        BSHBinaryExpression legacyInstanceOfExpression = new BSHBinaryExpression(instanceOfExpression);
        if (legacyRoot == null) legacyRoot = legacyInstanceOfExpression;
        if (currentLegacyNode != null) currentLegacyNode.addChild(legacyInstanceOfExpression);
        currentLegacyNode = legacyInstanceOfExpression;
        recurse(instanceOfExpression);
        currentLegacyNode = legacyInstanceOfExpression.getParent();
    }

    void visit(NullCoalesceElvisSpaceShipExpression nullCoalesceElvisSpaceShipExpression) {
        BSHBinaryExpression legacyNullCoalesceElvisSpaceShipExpression = new BSHBinaryExpression(nullCoalesceElvisSpaceShipExpression);
        if (legacyRoot == null) legacyRoot = legacyNullCoalesceElvisSpaceShipExpression;
        if (currentLegacyNode != null) currentLegacyNode.addChild(legacyNullCoalesceElvisSpaceShipExpression);
        currentLegacyNode = legacyNullCoalesceElvisSpaceShipExpression;
        recurse(nullCoalesceElvisSpaceShipExpression);
        currentLegacyNode = legacyNullCoalesceElvisSpaceShipExpression.getParent();
    }

    void visit(CodeBlock block) {
        BSHBlock legacyBlock = new BSHBlock(block);
        if (legacyRoot == null) legacyRoot = legacyBlock;
        if (currentLegacyNode != null) currentLegacyNode.addChild(legacyBlock);
        currentLegacyNode = legacyBlock;
        recurse(block);
        currentLegacyNode = legacyBlock.getParent();
    }

    void visit(NoVarDeclaration varDeclaration) {
        BSHTypedVariableDeclaration legacyVarDeclaration = new BSHTypedVariableDeclaration(varDeclaration);
        if (legacyRoot == null) legacyRoot = legacyVarDeclaration;
        if (currentLegacyNode != null) currentLegacyNode.addChild(legacyVarDeclaration);
    }

    void visit(ConditionalExpression conditionalExpression) {
        BSHTernaryExpression legacyTernaryExpression = new BSHTernaryExpression(conditionalExpression);
        if (legacyRoot == null) legacyRoot = legacyTernaryExpression;
        if (currentLegacyNode != null) currentLegacyNode.addChild(legacyTernaryExpression);
        currentLegacyNode = legacyTernaryExpression;
        recurse(conditionalExpression);
        currentLegacyNode = legacyTernaryExpression.getParent();
    }

    void visit(AssignmentExpression assignmentExpression) {
        BSHAssignment legacyAssignment = new BSHAssignment(assignmentExpression);
        if (legacyRoot == null) legacyRoot = legacyAssignment;
        if (currentLegacyNode !=null) currentLegacyNode.addChild(legacyAssignment);
        currentLegacyNode =legacyAssignment;
        recurse(assignmentExpression);
        currentLegacyNode = legacyAssignment.getParent();
    }

    void visit(ClassicSwitchStatement switchStatement) {
        BSHSwitchStatement legacySwitchStatement = new BSHSwitchStatement(switchStatement);
        if (legacyRoot == null) legacyRoot = legacySwitchStatement;
        if (currentLegacyNode != null) currentLegacyNode.addChild(legacySwitchStatement);
        currentLegacyNode = legacySwitchStatement;
        recurse(switchStatement);
        currentLegacyNode = legacySwitchStatement.getParent();
    }

    void visit(ClassicSwitchLabel switchLabel) {
        BSHSwitchLabel legacySwitchLabel = new BSHSwitchLabel(switchLabel);
        if (currentLegacyNode != null) currentLegacyNode.addChild(legacySwitchLabel);
        currentLegacyNode = legacySwitchLabel;
        recurse(switchLabel);
        currentLegacyNode = legacySwitchLabel.getParent();
    }

    void visit(CatchBlock catchBlock) {
        BSHMultiCatch multiCatch = new BSHMultiCatch(catchBlock);
        currentLegacyNode.addChild(multiCatch);
        visit(catchBlock.getBlock());
    }

    void visit(IfStatement ifStatement) {
        BSHIfStatement legacyIfStatement = new BSHIfStatement(ifStatement);
        if (legacyRoot == null) legacyRoot = legacyIfStatement;
        if (currentLegacyNode!=null) currentLegacyNode.addChild(legacyIfStatement);
        currentLegacyNode = legacyIfStatement;
        recurse(ifStatement);
        currentLegacyNode = legacyIfStatement.getParent();
    }

    void visit(EnhancedForStatement enhancedForStatement) {
        BSHEnhancedForStatement legacyEnhancedForStatement = new BSHEnhancedForStatement(enhancedForStatement);
        if (legacyRoot == null) legacyRoot = legacyEnhancedForStatement;
        if (currentLegacyNode != null) currentLegacyNode.addChild(legacyEnhancedForStatement);
        currentLegacyNode = legacyEnhancedForStatement;
        recurse(enhancedForStatement);
        currentLegacyNode = legacyEnhancedForStatement.getParent();
    }

    void visit(DotThis dotThis) {
        BSHPrimarySuffix primarySuffix = new BSHPrimarySuffix(dotThis);
        currentLegacyNode.addChild(primarySuffix);
    }

    void visit(DotSuper dotSuper) {
        BSHPrimarySuffix primarySuffix = new BSHPrimarySuffix(dotSuper);
        currentLegacyNode.addChild(primarySuffix);
    }

    void visit(DotNew dotNew) {
        BSHPrimarySuffix primarySuffix = new BSHPrimarySuffix(dotNew);
        currentLegacyNode.addChild(primarySuffix);
    }
    
    void visit(DotName dotName) {
        BSHPrimarySuffix primarySuffix = new BSHPrimarySuffix(dotName);
        currentLegacyNode.addChild(primarySuffix);
    }

    void visit(MethodCall methodCall) {
        BSHPrimarySuffix primarySuffix = new BSHPrimarySuffix(methodCall);
        if (currentLegacyNode != null) currentLegacyNode.addChild(primarySuffix);
    }

    void visit(Property property) {
        BSHPrimarySuffix primarySuffix = new BSHPrimarySuffix(property);
        currentLegacyNode.addChild(primarySuffix);
    }

    void visit(LiteralExpression literalExpression) {
        BSHLiteral legacyLiteral = new BSHLiteral(literalExpression);
        if (legacyRoot == null) legacyRoot = legacyLiteral;
        if (currentLegacyNode != null) currentLegacyNode.addChild(legacyLiteral);
    }

    void visit(ForStatement forStatement) {
        BSHForStatement legacyForStatement = new BSHForStatement(forStatement);
        if (legacyRoot == null) legacyRoot = legacyForStatement;
        if (currentLegacyNode != null) currentLegacyNode.addChild(legacyForStatement);
        currentLegacyNode = legacyForStatement;
        recurse(forStatement);
        currentLegacyNode = legacyForStatement.getParent();
    }

    void visit(ClassDeclaration classDeclaration) {
        BSHClassDeclaration legacyClassDeclaration = new BSHClassDeclaration(classDeclaration);
        if (legacyRoot == null) legacyRoot = legacyClassDeclaration;
        if (currentLegacyNode != null) currentLegacyNode.addChild(legacyClassDeclaration);
        recurse(classDeclaration);
        currentLegacyNode = currentLegacyNode.getParent();
    }

    void visit(InterfaceDeclaration interfaceDeclaration) {
        BSHClassDeclaration legacyInterfaceDeclaration = new BSHClassDeclaration(interfaceDeclaration);
        if (legacyRoot == null) legacyRoot = legacyInterfaceDeclaration;
        if (currentLegacyNode != null) currentLegacyNode.addChild(legacyInterfaceDeclaration);
        recurse(interfaceDeclaration);
        currentLegacyNode = currentLegacyNode.getParent();
    }

    void visit(EnumDeclaration enumDeclaration) {
        BSHClassDeclaration legacyEnumDeclaration = new BSHClassDeclaration(enumDeclaration);
        if (legacyRoot == null) legacyRoot = legacyEnumDeclaration;
        if (currentLegacyNode != null) currentLegacyNode.addChild(legacyEnumDeclaration);
        recurse(enumDeclaration);
        currentLegacyNode = currentLegacyNode.getParent();
    }

    void visit(EnumBody enumBody) {
        BSHBlock legacyEnumBlock = new BSHBlock(enumBody);
        currentLegacyNode.addChild(legacyEnumBlock);
        recurse(enumBody);
        currentLegacyNode = currentLegacyNode.getParent();
    }

    void visit(EnumConstant enumConstant) {
        BSHEnumConstant legacyEnumConstant = new BSHEnumConstant(enumConstant);
        currentLegacyNode.addChild(legacyEnumConstant);
    }

    void visit(CastExpression castExpression) {
        BSHCastExpression legacyCastExpression = new BSHCastExpression(castExpression);
        if (legacyRoot == null) legacyRoot = legacyCastExpression;
        if (currentLegacyNode != null) currentLegacyNode.addChild(legacyCastExpression);
        recurse(castExpression);
        currentLegacyNode = currentLegacyNode.getParent();
    }

    void visit(ArrayInitializer arrayInitializer) {
        BSHArrayInitializer legacyArrayInitializer = new BSHArrayInitializer(arrayInitializer);
        if (legacyRoot == null) legacyRoot = legacyArrayInitializer;
        if (currentLegacyNode != null) currentLegacyNode.addChild(legacyArrayInitializer);
        recurse(arrayInitializer);
        currentLegacyNode = currentLegacyNode.getParent();
    }

    void visit(ArrayDimsAndInits arrayDimsAndInits) {
        BSHArrayDimensions legacyArrayDimensions = new BSHArrayDimensions(arrayDimsAndInits);
        currentLegacyNode.addChild(legacyArrayDimensions);
        recurse(arrayDimsAndInits);
        currentLegacyNode = currentLegacyNode.getParent();
    }

    void visit(AllocationExpression allocationExpression) {
        BSHAllocationExpression legacyAllocationExpression = new BSHAllocationExpression(allocationExpression);
        if (legacyRoot == null) legacyRoot = legacyAllocationExpression;
        if (currentLegacyNode != null) currentLegacyNode.addChild(legacyAllocationExpression);
        recurse(allocationExpression);
        currentLegacyNode = currentLegacyNode.getParent();
    }

    void visit(bsh.congo.tree.Name name) {
        BSHAmbiguousName legacyName = new BSHAmbiguousName(name);
        if (legacyRoot == null) legacyRoot = legacyName;
        if (currentLegacyNode != null) currentLegacyNode.addChild(legacyName);
    }

    void visit(bsh.congo.tree.FormalComment formalComment) {
        BSHFormalComment legacyFormalComment = new BSHFormalComment(formalComment);
        if (legacyRoot == null) legacyRoot = legacyFormalComment;
        if (currentLegacyNode != null) currentLegacyNode.addChild(legacyFormalComment);
    }

    void visit(bsh.congo.tree.MethodDeclaration methodDeclaration) {
        BSHMethodDeclaration legacyMethodDeclaration = new BSHMethodDeclaration(methodDeclaration);
        if (legacyRoot == null) legacyRoot = legacyMethodDeclaration;
        if (currentLegacyNode != null) currentLegacyNode.addChild(legacyMethodDeclaration);
        currentLegacyNode = legacyMethodDeclaration;
        recurse(methodDeclaration);
        currentLegacyNode = currentLegacyNode.getParent();
    }

    void visit(bsh.congo.tree.LabeledStatement labeledStatement) {
        BSHLabeledStatement legacyLabeledStatement = new BSHLabeledStatement(labeledStatement);
        if (legacyRoot == null) legacyRoot = legacyLabeledStatement;
        if (currentLegacyNode != null) currentLegacyNode.addChild(legacyLabeledStatement);
        currentLegacyNode = legacyLabeledStatement;
        recurse(labeledStatement);
        currentLegacyNode = currentLegacyNode.getParent();
    }
}
