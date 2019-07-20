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
 * @author Wayne Holder - Converted format to standard Java and changed Vector and Hashtable to List and Map
 */

class LexAnn {
  // Defines what is allowed in words (e.g keywords variables etc.)
  private static final String ALLOW_WORD_START = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
  private static final String ALLOW_WORD = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz1234567890._";
  // general
  static final int TT_WORD = 9000;
  static final int TT_INTEGER = 9100;
  static final int TT_DOUBLE = 9150;
  static final int TT_EOF = 9200; // never set by this class
  static final int TT_EOL = 9300;
  static final int TT_STRING = 9500;
  static final int TT_FUNC = 9600;
  static final int TT_ARRAY = 9650;
  // keywords
  static final int TT_IF = 9700;
  static final int TT_EIF = 9800;
  static final int TT_ELSE = 9850;
  static final int TT_ELSIF = 9855;
  static final int TT_THEN = 9875;
  static final int TT_DEFFUNC = 9900;
  static final int TT_EDEFFUNC = 10000;
  static final int TT_WHILE = 10100;
  static final int TT_EWHILE = 10200;
  static final int TT_DEFINT = 10300;
  static final int TT_DEFSTRING = 10400;
  static final int TT_DEFDOUBLE = 10425;
  static final int TT_RETURN = 10450;
  static final int TT_EXIT = 10455;
  // math opts
  static final int TT_PLUS = 10500;
  static final int TT_MINUS = 10600;
  static final int TT_MULT = 10700;
  static final int TT_DIV = 10800;
  static final int TT_MOD = 10850;
  // logic
  static final int TT_LAND = 10900;
  static final int TT_LOR = 11000;
  static final int TT_LEQ = 11100;
  static final int TT_LNEQ = 11200;
  static final int TT_LGR = 11300;
  static final int TT_LLS = 11500;
  static final int TT_LGRE = 11600;
  static final int TT_LLSE = 11700;
  static final int TT_NOT = 11800;
  // other
  static final int TT_EQ = 11900;

  private static int EOL = -1;

  int             ttype;            // contains the current token type
  Object          value;            // contains the current  value
  private boolean pBack;
  private char[]  cBuf;
  private char[]  line;
  private int     c;
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
    c = 0;
  }

  /**
   * return the next char in the buffer
   */
  private int getChar () {
    if (pos < line.length) {
      return line[pos++];
    } else {
      return EOL;
    }
  }

  /**
   * return the character at a current line pos
   * without affecting internal counters
   */
  private int peekChar () {
    if (pos >= line.length) {
      return EOL;
    } else {
      return line[pos];
    }
  }

  /**
   * Read the next token
   *
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
    if (c == 0)
      c = getChar();
    value = null;
    while (c == ' ' || c == '\t' || c == '\n' || c == '\r')
      c = getChar();
    if (c == EOL) {
      ttype = TT_EOL;
    } else if (c == '#') {
      // Comments
      while (c != EOL) c = getChar();
      // get the next item, will be an eol marker
      nextT();
      // then the 'real' next token
      nextT();
    } else if (c == '"') {
      // Quoted Strings
      c = getChar();
      while ((c != EOL) && (c != '"')) {
        if (c == '\\') {
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
          cBuf[cPos++] = (char) c;
        }
        c = getChar();
      }
      value = new String(cBuf, 0, cPos);
      c = getChar();
      ttype = TT_STRING;
    } else if (ALLOW_WORD_START.indexOf(c) >= 0) {
      // Words
      while (ALLOW_WORD.indexOf(c) >= 0) {
        cBuf[cPos++] = (char) c;
        c = getChar();
      }
      value = new String(cBuf, 0, cPos);
      if (value.equals("if")) {
        ttype = TT_IF;
      } else if (value.equals("then")) {
        ttype = TT_THEN;
      } else if (value.equals("endif")) {
        ttype = TT_EIF;
      } else if (value.equals("else")) {
        ttype = TT_ELSE;
      } else if (value.equals("elseif")) {
        ttype = TT_ELSIF;
      } else if (value.equals("while")) {
        ttype = TT_WHILE;
      } else if (value.equals("endwhile")) {
        ttype = TT_EWHILE;
      } else if (value.equals("func")) {
        ttype = TT_DEFFUNC;
      } else if (value.equals("endfunc")) {
        ttype = TT_EDEFFUNC;
      } else if (value.equals("return")) {
        ttype = TT_RETURN;
      } else if (value.equals("exit")) {
        ttype = TT_EXIT;
      } else if (value.equals("int")) {
        ttype = TT_DEFINT;
      } else if (value.equals("string")) {
        ttype = TT_DEFSTRING;
      } else if (c == '(') {
        ttype = TT_FUNC;
      } else if (c == '[') {
        ttype = TT_ARRAY;
      } else {
        ttype = TT_WORD;
      }
    } else if (c >= '0' && c <= '9') {
      // Numbers
      while (c >= '0' && c <= '9') {
        cBuf[cPos++] = (char) c;
        c = getChar();
      }
      String str = new String(cBuf, 0, cPos);
      ttype = TT_INTEGER;
      value = Integer.parseInt(str);
    } else {
      // others
      if (c == '+') {
        ttype = TT_PLUS;
      } else if (c == '-') {
        ttype = TT_MINUS;
      } else if (c == '*') {
        ttype = TT_MULT;
      } else if (c == '/') {
        ttype = TT_DIV;
      } else if (c == '%') {
        ttype = TT_MOD;
      } else if (c == '>') {
        if (peekChar() == '=') {
          getChar();
          ttype = TT_LGRE;
        } else {
          ttype = TT_LGR;
        }
      } else if (c == '<') {
        if (peekChar() == '=') {
          getChar();
          ttype = TT_LLSE;
        } else {
          ttype = TT_LLS;
        }
      } else if (c == '=') {
        if (peekChar() == '=') {
          getChar();
          ttype = TT_LEQ;
        } else {
          ttype = TT_EQ;
        }
      } else if (c == '!') {
        if (peekChar() == '=') {
          getChar();
          ttype = TT_LNEQ;
        } else {
          ttype = TT_NOT;
        }
      } else if ((c == '|') && (peekChar() == '|')) {
        getChar();
        ttype = TT_LOR;
      } else if ((c == '&') && (peekChar() == '&')) {
        getChar();
        ttype = TT_LAND;
      } else {
        ttype = c;
      }
      c = getChar();
    }
  }
}



