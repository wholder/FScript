package murlen.util.fscriptME;
// The lexer - kind of - it started life as a re-implementation of
// StreamTokenizer - hence the peculiarities.


/**
 * <b>Re-Implementation of StreamTokenizer for FScript</b>
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
 * <p>This class is a re-implementation of Sun's StreamTokenizer class
 * as it was causing problems (especially parsing -ve numbers).</p>
 *
 * @author murlen
 * @author Joachim Van der Auwera
 * @version 0.5
 * <p>
 * changes by Joachim Van der Auwera
 * 31.08.2001
 * - simplified (speeded up) handling of comments (there was also an
 * inconsistency in the newline handling inside and outside comments).
 * - small mistake disallowed the letter 'A' in TT_WORD
 * @todo in literal string, allow "" to represent a single quote, so the line
 * string a=""""
 * declares a string a, which is initialised to one double quote.
 *
 * 20:07:2012
 * @version 0.51
 * @author wholder
 * @author Wayne Holder - Converted format to standard Java and refactored code to modernize it
 */

class LexAnn {
  // Defines what is allowed in words (e.g keywords variables etc.)
  private static final String ALLOW_WORD_START  = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
  private static final String ALLOW_WORD        = ALLOW_WORD_START + "1234567890._";
  public enum Token {
    // Types
    TT_WORD,
    TT_INTEGER,
    TT_DOUBLE,
    TT_STRING,
    // Functions and arrays
    TT_FUNC,
    TT_ARRAY,
    // Keywords
    TT_IF,
    TT_EIF,
    TT_ELSE,
    TT_ELSIF,
    TT_THEN,
    TT_DEFFUNC,
    TT_EDEFFUNC,
    TT_WHILE,
    TT_EWHILE,
    TT_DEFINT,
    TT_DEFSTRING,
    TT_DEFDOUBLE,
    TT_RETURN,
    TT_EXIT,
    // Math operators
    TT_PLUS,
    TT_MINUS,
    TT_MULT,
    TT_DIV,
    TT_MOD,
    // Logic operators
    TT_LAND,
    TT_LOR,
    TT_LEQ,
    TT_LNEQ,
    TT_LGR,
    TT_LLS,
    TT_LGRE,
    TT_LLSE,
    TT_NOT,
    // Other tokens
    TT_EQ,
    TT_COMMA,
    TT_LPAREN,
    TT_RPAREN,
    TT_LBRACE,
    TT_RBRACE,
    TT_EOF,         // never set by this class
    TT_EOL,
  }

  Token           ttype;            // contains the current token type
  Object          value;            // contains the current  value
  private boolean pBack;
  private char[]  cBuf;
  private char[]  line;
  private int     cc;
  private int     pos;

  /**
   * String representation of token (needs work)
   */
  public String toString () {
    return value + ":" + ttype;
  }

  /**
   * Convinience constructor which sets line as well
   */
  LexAnn (String firstLine) {
    cBuf = new char[1024];
    setString(firstLine);
  }

  /**
   * Sets the internal line buffer
   *
   * @param str - the string to use
   */
  void setString (String str) {
    line = str.toCharArray();
    pos = 0;
    cc = 0;
  }

  /**
   * return the next char in the buffer
   */
  private int getChar () {
    if (pos < line.length) {
      return line[pos++];
    } else {
      return -1;
    }
  }

  /**
   * return the character at a current line pos
   * without affecting internal counters
   */
  private int peekChar () {
    if (pos >= line.length) {
      return -1;
    } else {
      return line[pos];
    }
  }

  /**
   * Read the next token
   */
  void nextToken () {
    if (!pBack) {
      nextT();
    } else {
      pBack = false;
    }
  }

  /**
   * Causes next call to nextToken to return same value
   */
  void pushBack () {
    pBack = true;
  }

  // Internal next token function
  private void nextT () {
    int cPos = 0;
    if (cc == 0)
      cc = getChar();
    value = null;
    while (cc == ' ' || cc == '\t' || cc == '\n' || cc == '\r') {
      cc = getChar();
    }
    if (cc < 0) {
      ttype = Token.TT_EOL;
    } else if (cc == '#') {
      // Advance to next line
      do {
        cc = getChar();
      } while (cc >= 0);
      nextT();
    } else if (cc == '"') {
      // Quoted Strings
      cc = getChar();
      while ((cc >= 0) && (cc != '"')) {
        if (cc == '\\') {
          switch (peekChar()) {
            case 'n': {
              cBuf[cPos++] = '\n';
              getChar();
              break;
            }
            case 't': {
              cBuf[cPos++] = 't';
              getChar();
              break;
            }
            case 'r': {
              cBuf[cPos++] = '\r';
              getChar();
              break;
            }
            case '\"': {
              cBuf[cPos++] = '"';
              getChar();
              break;
            }
            case '\\': {
              cBuf[cPos++] = '\\';
              getChar();
              break;
            }
          }
        } else {
          cBuf[cPos++] = (char) cc;
        }
        cc = getChar();
      }
      value = new String(cBuf, 0, cPos);
      cc = getChar();
      ttype = Token.TT_STRING;
    } else if (ALLOW_WORD_START.indexOf(cc) >= 0) {
      // Words
      while (ALLOW_WORD.indexOf(cc) >= 0) {
        cBuf[cPos++] = (char) cc;
        cc = getChar();
      }
      // Skip trailing whitespace, if any
      while (cc == ' ' || cc == '\t') {
        cc = getChar();
      }
      value = new String(cBuf, 0, cPos);
      if (value.equals("if")) {
        ttype = Token.TT_IF;
      } else if (value.equals("then")) {
        ttype = Token.TT_THEN;
      } else if (value.equals("endif")) {
        ttype = Token.TT_EIF;
      } else if (value.equals("else")) {
        ttype = Token.TT_ELSE;
      } else if (value.equals("elseif")) {
        ttype = Token.TT_ELSIF;
      } else if (value.equals("while")) {
        ttype = Token.TT_WHILE;
      } else if (value.equals("endwhile")) {
        ttype = Token.TT_EWHILE;
      } else if (value.equals("func")) {
        ttype = Token.TT_DEFFUNC;
      } else if (value.equals("endfunc")) {
        ttype = Token.TT_EDEFFUNC;
      } else if (value.equals("return")) {
        ttype = Token.TT_RETURN;
      } else if (value.equals("exit")) {
        ttype = Token.TT_EXIT;
      } else if (value.equals("int")) {
        ttype = Token.TT_DEFINT;
      } else if (value.equals("string")) {
        ttype = Token.TT_DEFSTRING;
      } else if (cc == '(') {
        ttype = Token.TT_FUNC;
      } else if (cc == '[') {
        ttype = Token.TT_ARRAY;
      } else {
        ttype = Token.TT_WORD;
      }
    } else if (cc >= '0' && cc <= '9') {
      // Numbers
      while (cc >= '0' && cc <= '9') {
        cBuf[cPos++] = (char) cc;
        cc = getChar();
      }
      String str = new String(cBuf, 0, cPos);
      ttype = Token.TT_INTEGER;
      value = Integer.parseInt(str);
    } else {
      // others
      if (cc == '+') {
        ttype = Token.TT_PLUS;
      } else if (cc == '-') {
        ttype = Token.TT_MINUS;
      } else if (cc == '*') {
        ttype = Token.TT_MULT;
      } else if (cc == '/') {
        ttype = Token.TT_DIV;
      } else if (cc == '%') {
        ttype = Token.TT_MOD;
      } else if (cc == '>') {
        if (peekChar() == '=') {
          getChar();
          ttype = Token.TT_LGRE;
        } else {
          ttype = Token.TT_LGR;
        }
      } else if (cc == '<') {
        if (peekChar() == '=') {
          getChar();
          ttype = Token.TT_LLSE;
        } else {
          ttype = Token.TT_LLS;
        }
      } else if (cc == '=') {
        if (peekChar() == '=') {
          getChar();
          ttype = Token.TT_LEQ;
        } else {
          ttype = Token.TT_EQ;
        }
      } else if (cc == '!') {
        if (peekChar() == '=') {
          getChar();
          ttype = Token.TT_LNEQ;
        } else {
          ttype = Token.TT_NOT;
        }
      } else if ((cc == '|') && (peekChar() == '|')) {
        getChar();
        ttype = Token.TT_LOR;
      } else if ((cc == '&') && (peekChar() == '&')) {
        getChar();
        ttype = Token.TT_LAND;
      } else if (cc == ',') {
        ttype = Token.TT_COMMA;
      } else if (cc == '(') {
        ttype = Token.TT_LPAREN;
      } else if (cc == ')') {
        ttype = Token.TT_RPAREN;
      } else if (cc == '[') {
        ttype = Token.TT_LBRACE;
      } else if (cc == ']') {
        ttype = Token.TT_RBRACE;
      }
      cc = getChar();
    }
  }
}



