package bsh;

class BSHLambdaExpression extends SimpleNode {

    String singleParamName;

    private boolean initializedValues = false;
    private Modifiers[] paramsModifiers;
    private Class<?>[] paramsTypes;
    private String[] paramsNames;
    private Node body;

    public BSHLambdaExpression(int i) {
        super(i);
    }

    private void initValues(CallStack callstack, Interpreter interpreter) throws EvalError {
        if (this.initializedValues) return;
        if (this.jjtGetNumChildren() == 2) {
            BSHFormalParameters parameters = (BSHFormalParameters) this.jjtGetChild(0);
            this.paramsTypes = parameters.eval(callstack, interpreter);
            this.paramsModifiers = parameters.getParamModifiers();
            this.paramsNames = parameters.getParamNames();
            this.body = this.jjtGetChild(1);
        } else {
            this.paramsTypes = new Class[] { null };
            this.paramsModifiers = new Modifiers[] { null };
            this.paramsNames = new String[] { this.singleParamName };
            this.body = this.jjtGetChild(0);
        }
        this.initializedValues = true;
    }

    @Override
    public Object eval(CallStack callstack, Interpreter interpreter) throws EvalError {
        this.initValues(callstack, interpreter);
        return BshLambda.fromLambdaExpression(this, callstack.top(), this.paramsModifiers, this.paramsTypes, this.paramsNames, this.body);
    }

}
