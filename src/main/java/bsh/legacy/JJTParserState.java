package bsh.legacy;

import java.util.ArrayList;
import java.util.Collections;

import bsh.congo.parser.Node;

public class JJTParserState implements java.io.Serializable {
  private java.util.List<Node> nodes;
  private java.util.List<Integer> marks;

  /* number of nodes on stack */
  private int sp;
  /* current mark */
  private int mk;
  private boolean node_created;

  public JJTParserState() {
    nodes = new java.util.ArrayList<>();
    marks = new java.util.ArrayList<>();
    sp = 0;
    mk = 0;
  }

  /* Determines whether the current node was actually closed and
     pushed.  This should only be called in the final user action of a
     node scope. */
  public boolean nodeCreated() {
    return node_created;
  }

  /* Call this to reinitialize the node stack.  It is called
     automatically by the parser's ReInit() method. */
  public void reset() {
    nodes.clear();
    marks.clear();
    sp = 0;
    mk = 0;
  }

  /* Returns the root node of the AST.  It only makes sense to call
     this after a successful parse. */
  public Node rootNode() {
    return nodes.get(0);
  }

  /* Pushes a node on to the stack. */
  public void pushNode(Node n) {
    nodes.add(n);
    ++sp;
  }

  /* Returns the node on the top of the stack, and remove it from the
     stack.  */
  public Node popNode() {
   --sp;
    if (sp < mk) {
      mk = marks.remove(marks.size()-1).intValue();
    }
    return nodes.remove(nodes.size()-1);
  }

  /* Returns the node currently on the top of the stack. */
  public Node peekNode() {
    return nodes.get(nodes.size()-1);
  }

  /* Returns the number of children on the stack in the current node
     scope. */
  public int nodeArity() {
    return sp - mk;
  }

  /* Parameter is currently unused. */
  public void clearNodeScope(@SuppressWarnings("unused") final Node n) {
    while (sp > mk) {
      popNode();
    }
    mk = marks.remove(marks.size()-1).intValue();
  }

  public void openNodeScope(final Node n) {
    marks.add(Integer.valueOf(mk));
    mk = sp;
    n.open();
  }

  /* A definite node is constructed from a specified number of
     children.  That number of nodes are popped from the stack and
     made the children of the definite node.  Then the definite node
     is pushed on to the stack. */
  public void closeNodeScope(final Node n, final int numIn) {
    mk = marks.remove(marks.size()-1).intValue();
    int num = numIn;
    ArrayList<Node> poppedNodes = new ArrayList<>();
    while (num-- > 0) {
      poppedNodes.add(popNode());
    }
    Collections.reverse(poppedNodes);
    for (Node child : poppedNodes) {
      n.addChild(child);
      child.setParent(n);
    }
    n.close();
    pushNode(n);
    node_created = true;
  }


  /* A conditional node is constructed if its condition is true.  All
     the nodes that have been pushed since the node was opened are
     made children of the conditional node, which is then pushed
     on to the stack.  If the condition is false the node is not
     constructed and they are left on the stack. */
  public void closeNodeScope(final Node n, final boolean condition) {
    if (condition) {
      int a = nodeArity();
      mk = marks.remove(marks.size()-1).intValue();
      ArrayList<Node> poppedNodes = new ArrayList<>();
      while (a-- > 0) {
        poppedNodes.add(popNode());
      }
      Collections.reverse(poppedNodes);
      for (Node child : poppedNodes) {
        n.addChild(child);
        child.setParent(n);
      }
      n.close();
      pushNode(n);
      node_created = true;
    } else {
      mk = marks.remove(marks.size()-1).intValue();
      node_created = false;
    }
  }
}
