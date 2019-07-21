package murlen.util.fscriptME;

import java.util.ArrayList;
import java.util.List;

/**
 * <b>LineLoader - used by FScript to load source text</b>
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
 * * 20.08.2001
 * - getLine added
 * - setCurLine test was wrong, allowed setting line one too far
 *
 * 20:07:2012
 * @version 0.51
 * @author wholder
 * @author Wayne Holder - Converted format to standard Java and refactored code to modernize it
 */

final class LineLoader {
  private List<String>  lines = new ArrayList<>();
  private int           curLine;

  /**
   * Reset the LineLoader
   */
  final void reset () {
    lines.clear();
    curLine = 0;
  }

  /**
   * Method to incrementally add lines to buffer
   *
   * @param s the line to load
   */
  private void addLine (String s) {
    if (!s.trim().equals("")) {
      lines.add(s);
    } else {
      // Add blank lines to keep error msg lines in sync with file lines.
      lines.add("");
    }
  }

  /**
   * Add \n separated lines
   *
   * @param str the lines to add
   */
  final void addLines (String str) {
    if (!str.trim().equals("")) {
      int pos = str.indexOf('\n');
      while (pos >= 0) {
        addLine(str.substring(0, pos));
        str = str.substring(pos + 1);
        pos = str.indexOf('\n');
      }
      if (!str.trim().equals("")) {
        addLine(str);
      }
    }
  }

  /**
   * Sets the current execution line
   *
   * @param line the line number
   */
  final void setCurLine (int line) {
    if (line > lines.size()) {
      line = lines.size() - 1;
    } else if (line < 0) {
      line = 0;
    }
    curLine = line;
  }

  /**
   * Returns the current execution line
   */
  final int getCurLine () {
    return curLine;
  }

  /**
   * Returns the total number of lines in buffer
   */
  final int lineCount () {
    return lines.size();
  }

  /**
   * Returns the text of the current line
   */
  final String getLine () {
    return lines.get(curLine);
  }

  /**
   * Returns the text of the requested line
   */
  final String getLine (int lineNum) {
    if (lineNum < 0 || lineNum >= lines.size()) {
      return "";
    }
    return lines.get(lineNum);
  }
}
