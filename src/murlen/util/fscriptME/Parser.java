package murlen.util.fscriptME;

import static murlen.util.fscriptME.LexAnn.*;
import static murlen.util.fscriptME.LexAnn.Token.*;

import java.lang.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * <b>Parser - Does the parsing - i.e it's the brains of the code.</b>
 * <p>
 * <I>Copyright (C) 2002 murlen.</I></p>
 * <p>
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public
 * License as published by the Free Software Foundation; either
 * version 2 of the License, or (at your option) any later version.</p>
 * <p>
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.</p>
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc.,59 Temple Place, Suite 330, Boston MA 0211-1307 USA
 * </p>
 *
 * @author murlen
 * @version 0.5
 * <p>
 * * modifications by Joachim Van der Auwera
 * 14.08.2001 added support for indexed variables
 * 20.08.2001 -clean handling of setVar with null value
 * - cleaner handling of if with null condition
 * - make sure running empty script does nothing
 * - extra info when throwing an exception (with surrounding lines)
 * - changed operator prioritues for && and ||
 * - fixed bug in parseIf with handling of nesting of if clauses with else
 * - check for missing endif or endwhile (caused infinit loops)
 * - check for a null to prevernt excepion in parseExpr
 * 28.08.2001
 * - call to host.getVar() replaced by host.getVarEntry() (and added
 * proper exception handling, in that case)
 * 31.08.2001
 * - test on if condition being of correct type re-introduced
 * 10.09.2001
 * - added < <= > >= on strings
 *
 * 20:07:2012
 * @version 0.51
 * @author wholder
 * @author Wayne Holder - Converted format to standard Java and refactored code to modernize it
 */
class Parser {
  private static Map<Token, Integer> opPrio = new HashMap<>();    // operator priority table
  private Map<String, Object> vars = new HashMap<>();               // function local variables
  private Map<String, Object> funcs = new HashMap<>();              // function map
  private Map<String, Object> gVars  = new HashMap<>();             // global variables
  private LineLoader          code;                                 // the code
  private LexAnn              tok;                                  // tokenizer
  private FScript             host;                                 // link to hosting FScript object
  private Object              retVal;                               // return value
  private int                 maxLine;
  private String[]            error;
  private boolean             nested;

  static {
    // Setup operator priority table from low to high
    opPrio.put(TT_LOR,   1);
    opPrio.put(TT_LAND,  2);
    opPrio.put(TT_LEQ,   5);
    opPrio.put(TT_LNEQ,  5);
    opPrio.put(TT_LGR,   5);
    opPrio.put(TT_LGRE,  5);
    opPrio.put(TT_LLS,   5);
    opPrio.put(TT_LLSE,  5);
    opPrio.put(TT_PLUS,  10);
    opPrio.put(TT_MINUS, 10);
    opPrio.put(TT_MULT,  20);
    opPrio.put(TT_DIV,   20);
    opPrio.put(TT_MOD,   20);
  }

  // Simple data class used internally to store function defs
  class FuncEntry {
    List<String> paramNames= new ArrayList<>();       // list of parameter names
    Map<String, Object> paramMap = new HashMap<>();   // Hashtable of parameters
    int startLine;                                    // start line of function
    int endLine;                                      // end line of function

    public String toString () {
      return startLine + " " + endLine + " " + paramNames + " " + paramMap;
    }
  }

  // exception that occurs when someone calls return
  class RetException extends FSException {
  }

  // exception thrown by call to exit
  class ExitException extends RetException {
  }

  /**
   * Public constructor
   *
   * @param host a reference to the FScript object
   */
  Parser (FScript host) {
    this.host = host;
  }

  // Used only for function calls - note it is private
  private Parser (FScript host, Map<String, Object> local, Map<String, Object> global, Map<String, Object> funcs) {
    this.host = host;
    vars = local;
    gVars = global;
    this.funcs = funcs;
    nested = true;
  }

  /**
   * Sets the LineLoader class to be used for input
   *
   * @param in - the class
   */
  void setCode (LineLoader in) {
    code = in;
  }

  /**
   * Main parsing function
   *
   * @param from - the start line number
   * @param to   - the end line number
   * @return       returns an Object (currently a Integer or String) depending on the return value of the code parsed, or null if none.
   */
  Object parse (int from, int to) throws FSException {
    // Nothing to do when starting beyond the code end
    if (code.lineCount() <= from) {
      return null;
    }
    maxLine = to;
    code.setCurLine(from);
    String line = code.getCurrentLine();
    tok = new LexAnn(line);
    checkLine(line);
    getNextToken();
    while (tok.ttype != TT_EOF) {
      // a script must always start with a word...
      try {
        parseStmt();
      } catch (ExitException ex) {
        throw ex;
      } catch (RetException ex) {
        return retVal;
      }
      getNextToken();
    }
    return retVal;
  }

  /**
   * Reset the parser state
   */
  void reset () {
    vars.clear();
    gVars.clear();
  }

  // Statement - top level thing
  private void parseStmt () throws FSException {
    switch (tok.ttype) {
      case TT_IF:
      case TT_ENDIF:
      case TT_WHILE:
      case TT_ENDWHILE:
      case TT_DEFINT:
      case TT_DEFSTRING:
      case TT_DEFFUNC:
      case TT_EXIT:
      case TT_ENDDEFFUNC:
      case TT_RETURN:
        parseKeyWord();
        break;
      case TT_FUNC:
        parseFunc();
        break;
      case TT_ARRAY:
        parseArrayAssign();
        break;
      case TT_WORD:
        parseAssign();
        break;
      case TT_EOL:
        tok.nextToken();
        break;
      case TT_EOF:
        // all done
        break;
      default:
        parseError("Expected identifier");
    }
  }

  private void parseFunc () throws FSException {
    String name = (String) tok.value;
    // should be a '('
    getNextToken();
    if (tok.ttype != TT_LPAREN) {
      parseError("Expected '('");
    }
    parseCallFunc(name);
    getNextToken();
  }

  private void parseArrayAssign () throws FSException {
    String name = (String) tok.value;
    getNextToken();                       // should be a '['
    if (tok.ttype != TT_LBRACE) {
      parseError("Expected '['");
    }
    getNextToken();                       // should be the index
    Object index = parseExpr();
    getNextToken();                       // should be a ']'
    if (tok.ttype != TT_RBRACE) {
      parseError("Expected ']'");
    }
    if (tok.ttype != TT_EQ) {
      parseError("Expected '='");
    } else {
      getNextToken();
      Object val = parseExpr();
      try {
        host.setVar(name, index, val);
      } catch (Exception e) {
        parseError(e.getMessage());
      }
    }
  }

  private void parseKeyWord () throws FSException {
    switch (tok.ttype) {
      case TT_DEFINT:
      case TT_DEFSTRING:
      case TT_DEFDOUBLE:
        parseVarDef();
        break;
      case TT_IF:
        parseIf();
        break;
      case TT_WHILE:
        parseWhile();
        break;
      case TT_RETURN:
        parseReturn();
        break;
      case TT_DEFFUNC:
        parseFunctionDef();
        break;
      case TT_EXIT:
        parseExit();
      default:
        // We should never get here
        parseError("Not a keyword");
    }
  }

  // Handle 'return' statements
  private void parseReturn () throws FSException {
    getNextToken();
    retVal = parseExpr();
    throw new RetException();
  }

  // Handle 'exit' statements
  private void parseExit () throws FSException {
    getNextToken();
    retVal = parseExpr();
    throw new ExitException();
  }

  // Assignment parser
  private void parseAssign () throws FSException {
    String name;
    Object val;
    name = (String) tok.value;
    getNextToken();
    if (tok.ttype != TT_EQ) {
      parseError("Expected '='");
    } else {
      getNextToken();
      val = parseExpr();
      if (hasVar(name)) {
        setVar(name, val);
      } else {
        try {
          host.setVar(name, null, val);
        } catch (Exception e) {
          parseError(e.getMessage());
        }
      }
    }
  }

  // Handle function execution
  Object callFunction (String name, List<Object> params) throws FSException {
    // Check we have a definition for the function
    if (funcs.containsKey(name)) {
      FuncEntry fDef = (FuncEntry) funcs.get(name);
      // Check params and def match
      if (fDef.paramNames.size() != params.size()) {
        parseError("Expected " + fDef.paramNames.size() + " parameters, Found " + params.size());
      }
      // Create a new parser instance to handle call
      Parser parser;
      Map<String, Object> locals = new HashMap<>();
      // Push the params into the local scope
      for (int ii = 0; ii < fDef.paramNames.size(); ii++) {
        locals.put(fDef.paramNames.get(ii), params.get(ii));
      }
      // Watch for recursive calls
      if (!nested) {
        parser = new Parser(host, locals, vars, funcs);
      } else {
        parser = new Parser(host, locals, gVars, funcs);
      }
      // Cache the current execution point
      int oldLine = code.getCurLine();
      parser.setCode(code);
      // Let it rip
      Object val = parser.parse(fDef.startLine + 1, fDef.endLine - 1);
      // Reset execution point
      code.setCurLine(oldLine);
      return val;
    } else {
      // Calls into super class code...}
      try {
        return host.callFunction(name, params);
      } catch (ExitException e) {
        throw e;
      } catch (Exception e) {
        parseError(e.getMessage());
      }
    }
    return null;
  }

  // Parses function calls
  private Object parseCallFunc (String name) throws FSException {
    List<Object> params = new ArrayList<>();
    // Set up the parameters
    do {
      getNextToken();
      if (tok.ttype == TT_COMMA) {
        getNextToken();
      } else if (tok.ttype == TT_RPAREN) {
        break;
      }
      params.add(parseExpr());
    } while (tok.ttype == TT_COMMA);
    return callFunction(name, params);
  }

  // Handle function definitions
  private void parseFunctionDef () throws FSException {
    FuncEntry fDef = new FuncEntry();
    fDef.startLine = code.getCurLine();
    getNextToken();
    // should be the function name
    if (tok.ttype != TT_FUNC) {
      parseError("Expected function start identifier");
    }
    String fName = (String) tok.value;
    getNextToken();
    // should be a '('
    if (tok.ttype != TT_LPAREN) {
      parseError("Expected (");
    }
    getNextToken();
    // parse the header...
    while (tok.ttype != TT_RPAREN) {
      if (tok.ttype != TT_DEFINT && tok.ttype != TT_DEFSTRING) {
        parseError("Expected type name");
      }
      Object val = null; // keep the compiler happy..
      if (tok.ttype == TT_DEFINT) {
        val = 0;
      } else if (tok.ttype == TT_DEFSTRING) {
        val = "";
      }
      getNextToken();
      if (tok.ttype != TT_WORD) {
        parseError("Expected function parameter name identifier");
      }
      String name = (String) tok.value;
      fDef.paramNames.add(name);
      fDef.paramMap.put(name, val);
      getNextToken();
      if (tok.ttype == TT_COMMA) getNextToken();
    }
    // now we just skip to the endfunction
    while ((tok.ttype != TT_ENDDEFFUNC) && (tok.ttype != TT_EOF)) {
      getNextToken();
      if (tok.ttype == TT_DEFFUNC)
        parseError("Nested functions are illegal");
    }
    fDef.endLine = code.getCurLine();
    getNextToken();
    funcs.put(fName, fDef);
  }

  // Expression parser
  private Object parseExpr () throws FSException {
    ETreeNode curNode = null;
    boolean end = false;
    Object val;
    boolean negate = false; // flag for unary minus
    boolean not = false;    // flag for unary not.
    boolean prevOp = true;  // flag - true if previous value was an operator
    while (!end) {
      switch (tok.ttype) {
        // the various possible 'values'
        case TT_INTEGER:
        case TT_DOUBLE:
        case TT_STRING:
        case TT_WORD:
        case TT_FUNC:
        case TT_ARRAY: {
          if (!prevOp) {
            parseError("Expected Operator");
          } else {
            val = null;
            ETreeNode node = new ETreeNode();
            node.type = ETreeNode.E_VAL;
            switch (tok.ttype) {
              // numbers - just get them
              case TT_INTEGER:
                // strings - just get again
              case TT_STRING:
                val = tok.value;
                break;
              // functions - evaluate them
              case TT_FUNC:
                String funcName = (String) tok.value;
                getNextToken();
                val = parseCallFunc(funcName);
                break;
              case TT_ARRAY:
                // arrays - evaluate them
                String aryName = (String) tok.value;
                getNextToken();         // should be a '['
                getNextToken();         // should be the index
                Object index = parseExpr();
                try {
                  val = host.getVar(aryName, index);
                } catch (Exception e) {
                  parseError(e.getMessage());
                }
                break;
              case TT_WORD:
                // variables - resolve them
                if (hasVar((String) tok.value)) {
                  val = getVar((String) tok.value);
                } else {
                  try {
                    val = host.getVar((String) tok.value, null);
                  } catch (Exception e) {
                    parseError(e.getMessage());
                  }
                }
                break;
            }
            // unary not
            if (not) {
              if (val instanceof Integer) {
                if ((Integer) val != 0) {
                  val = 0;
                } else {
                  val = 1;
                }
                not = false;
              } else {
                parseError("Type mismatch for !");
              }
            }
            // unary minus
            if (negate) {
              if (val instanceof Integer) {
                val = -(Integer) val;
              } else {
                parseError("Type mistmatch for unary -");
              }
            }
            node.value = val;
            if (curNode != null) {
              if (curNode.left == null) {
                curNode.left = node;
                node.parent = curNode;
                curNode = node;

              } else if (curNode.right == null) {
                curNode.right = node;
                node.parent = curNode;
                curNode = node;
              }
            } else {
              curNode = node;
            }
            prevOp = false;
          }
          break;
        }
        /*
         * Operators - have to be more carefull with these.
         * We build an expression tree - inserting the nodes at the right
         * points to get a reasonable approximation to correct operator
         *  precedence
         */
        case TT_LEQ:
        case TT_LNEQ:
        case TT_MULT:
        case TT_DIV:
        case TT_MOD:
        case TT_PLUS:
        case TT_MINUS:
        case TT_LGR:
        case TT_LGRE:
        case TT_LLSE:
        case TT_LLS:
        case TT_NOT:
        case TT_LAND:
        case TT_LOR: {
          if (prevOp) {
            if (tok.ttype == TT_MINUS) {
              negate = true;
            } else if (tok.ttype == TT_NOT) {
              not = true;
            } else {
              parseError("Expected expression");
            }
          } else {
            ETreeNode node = new ETreeNode();
            node.type = ETreeNode.E_OP;
            node.value = tok.ttype;
            if (curNode.parent != null) {
              int curPrio = getPrio(tok.ttype);
              int parPrio = getPrio((Token) curNode.parent.value);
              if (curPrio <= parPrio) {
                // This nodes parent is the current node's grandparent
                node.parent = curNode.parent.parent;
                // Our nodes left leg is now linked into the current node's parent
                node.left = curNode.parent;
                // Hook into grandparent
                if (curNode.parent.parent != null) {
                  curNode.parent.parent.right = node;
                }
                // Current nodes parent is now us (because of above)
                curNode.parent = node;
                // Set the current node.
                curNode = node;
              } else {
                // Current node's parent's right is now us
                curNode.parent.right = node;
                // Our nodes left is the current node
                node.left = curNode;
                // our nodes parent is the current node's parent
                node.parent = curNode.parent;
                // Curent nodes parent is now us.
                curNode.parent = node;
                // set the current node.
                curNode = node;
              }
            } else {
              // Our node's left is the current node
              node.left = curNode;
              // Current node's parent is us now
              // We don't have to set our parent, as it is null
              curNode.parent = node;
              // Set current node
              curNode = node;
            }
            prevOp = true;
          }
          break;
        }
        case TT_LPAREN: {
          // Start of an bracketed expression, recursively call ourself to get a value
          getNextToken();
          val = parseExpr();
          ETreeNode node = new ETreeNode();
          node.value = val;
          node.type = ETreeNode.E_VAL;
          if (curNode != null) {
            if (curNode.left == null) {
              curNode.left = node;
              node.parent = curNode;
              curNode = node;
            } else if (curNode.right == null) {
              curNode.right = node;
              node.parent = curNode;
              curNode = node;
            }
          } else {
            curNode = node;
          }
          prevOp = false;
          break;
        }
        default: {
          end = true;
        }
      }
      if (!end) {
        tok.nextToken();
      }
    }
    // Find the top of the tree we just built.
    if (curNode == null) parseError("Missing Expression");
    assert curNode != null;
    while (curNode.parent != null) {
      curNode = curNode.parent;
    }
    return evalETree(curNode);
  }

  // Get operator priority
  private int getPrio (Token op) {
    return opPrio.get(op);
  }

  // Evaluate the expression tree recursively
  private Object evalETree (ETreeNode node) throws FSException {
    if (node.type == ETreeNode.E_VAL) {
      return node.value;
    }
    Object lVal = evalETree(node.left);
    Object rVal = evalETree(node.right);
    switch ((Token) node.value) {
      // Call the various eval functions
      case TT_PLUS:
        return evalPlus(lVal, rVal);
      case TT_MINUS:
        return evalMinus(lVal, rVal);
      case TT_MULT:
        return evalMult(lVal, rVal);
      case TT_DIV:
        return evalDiv(lVal, rVal);
      case TT_LEQ:
        return evalEq(lVal, rVal);
      case TT_LNEQ:
        return evalNEq(lVal, rVal);
      case TT_LLS:
        return evalLs(lVal, rVal);
      case TT_LLSE:
        return evalLse(lVal, rVal);
      case TT_LGR:
        return evalGr(lVal, rVal);
      case TT_LGRE:
        return evalGre(lVal, rVal);
      case TT_MOD:
        return evalMod(lVal, rVal);
      case TT_LAND:
        return evalAnd(lVal, rVal);
      case TT_LOR:
        return evalOr(lVal, rVal);
    }
    return null;
  }

  // Addition
  private Object evalPlus (Object lVal, Object rVal) throws FSException {
    if (lVal instanceof Integer && rVal instanceof Integer) {
      return (Integer) lVal + (Integer) rVal;
    } else if (lVal instanceof String || rVal instanceof String) {
      // Little bit of bulletproofing
      String lv = lVal != null ? lVal.toString() : "null";
      String rv = rVal != null ? rVal.toString() : "null";
      return lv + rv;
    } else {
      parseError("Type Mismatch for operator +");
    }
    return null;
  }

  // Subtraction
  private Object evalMinus (Object lVal, Object rVal) throws FSException {
    if (lVal instanceof Integer && rVal instanceof Integer) {
      return (Integer) lVal - (Integer) rVal;
    } else {
      parseError("Type Mismatch for operator -");
    }
    return null;
  }

  // Multiplication
  private Object evalMult (Object lVal, Object rVal) throws FSException {
    if (lVal instanceof Integer && rVal instanceof Integer) {
      return (Integer) lVal * (Integer) rVal;
    } else {
      parseError("Type Mismatch for operator *");
    }
    return null;
  }

  // Modulus
  private Object evalMod (Object lVal, Object rVal) throws FSException {
    if (lVal instanceof Integer && rVal instanceof Integer) {
      return (Integer) lVal % (Integer) rVal;
    } else {
      parseError("Type Mismatch for operator %");
    }
    return null;
  }

  // Logical AND
  private Object evalAnd (Object lVal, Object rVal) throws FSException {
    if (lVal instanceof Integer && rVal instanceof Integer) {
      return (Integer) lVal != 0 && (Integer) rVal != 0 ? 1 : 0;
    } else {
      parseError("Type Mismatch for operator &&");
    }
    return null;
  }

  // Logical Or
  private Object evalOr (Object lVal, Object rVal) throws FSException {
    if (lVal instanceof Integer && rVal instanceof Integer) {
      return (Integer) lVal != 0 || (Integer) rVal != 0 ? 1 : 0;
    } else {
      parseError("Type Mismatch for operator ||");
    }
    return null;
  }

  // Division
  private Object evalDiv (Object lVal, Object rVal) throws FSException {
    if (lVal instanceof Integer && rVal instanceof Integer) {
      return (Integer) lVal / (Integer) rVal;
    } else {
      parseError("Type Mismatch for operator /");
    }
    return null;
  }

  // Logical equal
  private Object evalEq (Object lVal, Object rVal) throws FSException {
    if (lVal instanceof Integer && rVal instanceof Integer) {
      return lVal.equals(rVal) ? 1 : 0;
    } else if (lVal instanceof String && rVal instanceof String) {
      return lVal.equals(rVal) ? 1 : 0;
    } else {
      parseError("Type Mismatch for operator ==");
    }
    return null;
  }

  // Evaluate <
  private Object evalLs (Object lVal, Object rVal) throws FSException {
    if (lVal instanceof Integer && rVal instanceof Integer) {
      return (Integer) lVal < (Integer) rVal ? 1 : 0;
    } else if (lVal instanceof String && rVal instanceof String) {
      return ((String) lVal).compareTo((String) rVal) < 0 ? 1 : 0;
    } else {
      parseError("Type Mismatch for operator <");
    }
    return null;
  }

  // Evaluate <=
  private Object evalLse (Object lVal, Object rVal) throws FSException {
    if (lVal instanceof Integer && rVal instanceof Integer) {
      return (Integer) lVal <= (Integer) rVal ? 1 : 0;
    } else if (lVal instanceof String && rVal instanceof String) {
      return ((String) lVal).compareTo((String) rVal) <= 0 ? 1 : 0;
    } else {
      parseError("Type Mismatch for operator <=");
    }
    return null;
  }

  // Evaluate >
  private Object evalGr (Object lVal, Object rVal) throws FSException {
    if (lVal instanceof Integer && rVal instanceof Integer) {
      return (Integer) lVal > (Integer) rVal ? 1 : 0;
    } else if (lVal instanceof String && rVal instanceof String) {
      return ((String) lVal).compareTo((String) rVal) > 0 ? 1 : 0;
    } else {
      parseError("Type Mismatch for operator >");
    }
    return null;
  }

  // Evaluate >=
  private Object evalGre (Object lVal, Object rVal) throws FSException {
    if (lVal instanceof Integer && rVal instanceof Integer) {
      return (Integer) lVal >= (Integer) rVal ? 1 : 0;
    } else if (lVal instanceof String && rVal instanceof String) {
      return ((String) lVal).compareTo((String) rVal) >= 0 ? 1 : 0;
    } else {
      parseError("Type Mismatch for operator >=");
    }
    return null;
  }

  // Logical inequallity
  private Object evalNEq (Object lVal, Object rVal) throws FSException {
    if (lVal instanceof Integer && rVal instanceof Integer) {
      return !lVal.equals(rVal) ? 1 : 0;
    } else if (lVal instanceof String && rVal instanceof String) {
      return !lVal.equals(rVal) ? 1 : 0;
    } else {
      parseError("Type Mismatch for operator !=");
    }
    return null;
  }

  // Parse "if" statement
  private void parseIf () throws FSException {
    Integer val;
    boolean then = false;
    getNextToken();
    try {
      val = (Integer) parseExpr();
    } catch (ClassCastException cce) {
      parseError("If condition needs to be Integer");
      return;
    }
    // handle the one line if-then construct
    if (tok.ttype == TT_THEN) {
      getNextToken();
      // is this a single line then (or just a optional then)
      if (tok.ttype != TT_EOL) {
        // single line if then construct - run separately
        if (val != 0) {
          parseStmt();
        } else {
          // consume to EOL
          while (tok.ttype != TT_EOL) {
            getNextToken();
          }
        }
        then = true;
      }
    }
    if (!then) {
      if (val != 0) {
        getNextToken();
        while (tok.ttype != TT_ENDIF &&
               tok.ttype != TT_ELSE &&
               tok.ttype != TT_EOF &&
               tok.ttype != TT_ELSIF) {
          // run the body of the if
          parseStmt();
          getNextToken();
        }
        if (tok.ttype == TT_ELSE || tok.ttype == TT_ELSIF) {
          // skip else clause -
          // have to do this taking into acount nesting
          int depth = 1;
          do {
            getNextToken();
            if (tok.ttype == TT_IF)
              depth++;
            if (tok.ttype == TT_EOF)
              parseError("can't find endif");
            if (tok.ttype == TT_ENDIF)
              depth--;
            // A then could indicate a one line
            // if - then construct, then we don't increment
            // depth
            if (tok.ttype == TT_THEN) {
              getNextToken();
              if (tok.ttype != TT_EOL) {
                depth--;
              }
              tok.pushBack();
            }
          } while (depth > 0);
          getNextToken();
        } else {
          getNextToken();
        }
      } else {
        // skip to else clause
        int depth = 1;
        do {
          getNextToken();
          if (tok.ttype == TT_IF) {
            depth++;
          }
          if (tok.ttype == TT_EOF) {
            parseError("can't find endif");
          }
          if ((tok.ttype == TT_ENDIF)) {
            depth--;
          }
          if ((tok.ttype == TT_ELSE || tok.ttype == TT_ELSIF) && depth == 1) {
            depth--;
          }
          // A then could indicate a one line
          // if - then construct, then we don't increment depth
          if (tok.ttype == TT_THEN) {
            getNextToken();
            if (tok.ttype != TT_EOL) {
              depth--;
            }
            tok.pushBack();
          }
        } while (depth > 0);
        if (tok.ttype == TT_ELSE) {
          getNextToken();
          getNextToken();
          // run else clause
          while (tok.ttype != TT_ENDIF) {
            parseStmt();
            getNextToken();
          }
          getNextToken();
        } else if (tok.ttype == TT_ELSIF) {
          parseIf();
        } else {
          getNextToken();
        }
      }
    }
  }

  // Parse While statements
  private void parseWhile () throws FSException {
    int startLine = code.getCurLine();
    getNextToken();
    Integer val = (Integer) parseExpr();
    getNextToken();
    while (val != 0) {
      while ((tok.ttype != TT_ENDWHILE) && (tok.ttype != TT_EOF)) {
        parseStmt();
        getNextToken();
      }
      // reset to start of while loop....
      code.setCurLine(startLine);
      resetTokens();
      getNextToken(); // a 'while' you would imagine.
      val = (Integer) parseExpr();
      getNextToken();
    }
    // skip to endwhile
    int depth = 1;
    do {
      getNextToken();
      if (tok.ttype == TT_WHILE) {
        depth++;
      }
      if (tok.ttype == TT_ENDWHILE) {
        depth--;
      }
      if (tok.ttype == TT_EOF) {
        parseError("can't find endwhile");
      }
    } while (depth > 0);
    getNextToken();

  }

  // Parse Variable definition
  private void parseVarDef () throws FSException {
    Token type = tok.ttype;
    if (tok.ttype != TT_DEFINT && tok.ttype != TT_DEFSTRING && tok.ttype != TT_DEFDOUBLE) {
      parseError("Expected 'int','string' or 'double'");
    }
    do {
      getNextToken();
      if (tok.ttype != TT_WORD) {
        parseError("Expected variable name identifier,");
      }
      String name = (String) tok.value;
      switch (type) {
        case TT_DEFINT: {
          addVar(name, 0);
          break;
        }
        case TT_DEFSTRING: {
          addVar(name, "");
          break;
        }
      }
      getNextToken();
      if (tok.ttype == TT_EQ) {
        getNextToken();
        setVar(name, parseExpr());
      } else if (tok.ttype != TT_COMMA && tok.ttype != TT_EOL) {
        parseError("Expected ','");
      }
    } while (tok.ttype != TT_EOL);
  }

  // Format an error message and throw FSException
  private void parseError (String s) throws FSException {
    error = new String[5];
    String tstr = tok.toString();
    // Set up our error block
    error[0] = s;
    error[1] = (new Integer(code.getCurLine())).toString();
    error[2] = code.getCurrentLine();
    error[3] = tstr;
    error[4] = vars.toString();
    error[5] = gVars.toString();
    // Build the display string
    int lineNum = code.getCurLine();
    StringBuilder err = new StringBuilder(s);
    err.append("\n\t at line: ");
    err.append(lineNum + 1);
    err.append(" ");
    if (lineNum >= 2) {
      err.append("\n\t\t  ");
      err.append(code.getLine(lineNum - 2));
    }
    if (lineNum >= 1) {
      err.append("\n\t\t  ");
      err.append(code.getLine(lineNum - 1));
    }
    err.append("\n\t\t> ");
    err.append(code.getLine(lineNum));
    err.append(" <");
    err.append("\n\t\t  ");
    err.append(code.getLine(lineNum + 1));
    err.append("\n\t\t  ");
    err.append(code.getLine(lineNum + 2));
    err.append("\n\t current token: ");
    err.append(tstr);
    if (vars.size() > 0) {
      err.append("\n\t Locals: ");
      err.append(vars);
    }
    if (gVars.size() > 0) {
      err.append("\n\t Globals: ");
      err.append(gVars);
    }
    throw new FSException(err.toString());
  }

  // Get the error block
  String[] getError () {
    return error;
  }

  // Misc token access routines
  private void getNextToken () {
    if ((tok.ttype == TT_EOL) && (code.getCurLine() < maxLine)) {
      code.setCurLine(code.getCurLine() + 1);
      tok.setString(code.getCurrentLine());
      tok.nextToken();
    } else if (tok.ttype == TT_EOL) {
      tok.ttype = TT_EOF; // the only place this gets set
    } else {
      tok.nextToken();
    }
  }

  private void resetTokens () {
    tok.setString(code.getCurrentLine());
    tok.nextToken();
  }

  // Add new variable and value to "vars" Map
  private void addVar (String name, Object value) throws FSException {
    if (vars.containsKey(name)) {
      parseError("Already defined in this scope: " + name);
    }
    vars.put(name, value);
  }

  // Get value of variable in "vars" Map
  Object getVar (String name) {
    if (vars.containsKey(name)) {
      return vars.get(name);
    } else {
      return gVars.get(name);
    }
  }

  // Set value of vaiable in "vars" Map, or "gVars" Map
  void setVar (String name, Object val) throws FSException {
    if (val == null) parseError("set variable " + name + " with null value");
    if (vars.containsKey(name)) {
      Object obj = vars.get(name);
      assert val != null;
      if (val.getClass() != obj.getClass()) {
        parseError("Incompatible types");
      }
      vars.put(name, val);
    } else if (gVars.containsKey(name)) {
      Object obj = gVars.get(name);
      assert val != null;
      if (val.getClass() != obj.getClass()) {
        parseError("Incompatible types");
      }
      gVars.put(name, val);
    }
  }

  // Returns true if "vars" Map or "gVars" Map contains variable "name"
  private boolean hasVar (String name) {
    return vars.containsKey(name) || gVars.containsKey(name);
  }

  // Gets the 'return' value from the parser
  Object getReturnValue () {
    return retVal;
  }

  // Can be called from external functions to force an exit
  void exit (Object ret) throws FSException {
    retVal = ret;
    throw new ExitException();
  }

  // Checks line for correctly formed ( ) and "
  // this is a little crude (i.e. the rdp should really pick it up)
  // but it's not all that good about it, hence somewhat kludgy fix
  private void checkLine (String line) throws FSException {
    boolean inQuotes = false;
    int brCount = 0;
    if (line != null) {
      if (!line.trim().startsWith("#")) {
        char[] chars = line.toCharArray();
        for (int idx = 0; idx < chars.length; idx++) {
          if (inQuotes) {
            if (chars[idx] == '"') {
              if (chars[idx - 1] != '\\') {
                inQuotes = false;
              }
            }
          } else {
            if (chars[idx] == '(') {
              brCount++;
            } else if (chars[idx] == ')') {
              brCount--;
            } else if (chars[idx] == '"') {
              if (idx >= 1) {
                if (chars[idx - 1] != '\\') {
                  inQuotes = true;
                }
              }
            }
          }
        }
        if (inQuotes) {
          parseError("Mismatched quotes");
        }
        if (brCount != 0) {
          parseError("Mismatched brackets");
        }
      }
    }
  }
}



