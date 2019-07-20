import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultEditorKit;
import javax.swing.text.Document;
import javax.swing.text.PlainDocument;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.UndoManager;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

/**
 *  Simple Code Editing Text Pane with Undo and Redo
 *  Author: Wayne Holder, 2017
 *  License: MIT (https://opensource.org/licenses/MIT)
 */

class CodeEditPane extends JPanel {
  private static final int  TAB_SIZE = 4;
  private JEditorPane       codePane;

  CodeEditPane () {
    setLayout(new BorderLayout());
    codePane = new JEditorPane();
    codePane.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
    codePane.setEditorKit(new DefaultEditorKit());
    JScrollPane codeScrollpane = new JScrollPane(codePane);
    add(codeScrollpane, BorderLayout.CENTER);
    doLayout();
    // Note: must call setContentType(), setFont() after doLayout() or no line numbers and small font
    codePane.setContentType("text/cpp");
    codePane.setFont(getCodeFont(12));
    Document doc = codePane.getDocument();
    int cmdMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMask();
    UndoManager undoManager = new UndoManager();
    doc.addUndoableEditListener(undoManager);
    // Map undo action
    KeyStroke undoKeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_Z, cmdMask);
    codePane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(undoKeyStroke, "undoKeyStroke");
    codePane.getActionMap().put("undoKeyStroke", new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent ev) {
        try {
          undoManager.undo();
        } catch (CannotUndoException ex) {
          ex.printStackTrace();
        }
      }
    });
    // Map redo action
    KeyStroke redoKeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_Z, cmdMask + InputEvent.SHIFT_MASK);
    codePane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(redoKeyStroke, "redoKeyStroke");
    codePane.getActionMap().put("redoKeyStroke", new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent ev) {
        try {
          undoManager.redo();
        } catch (CannotRedoException ex) {
          ex.printStackTrace();
        }
      }
    });
    doc.putProperty(PlainDocument.tabSizeAttribute, TAB_SIZE);
    codePane.updateUI();
    codePane.setEditable(true);
  }

  static Font getCodeFont (int points) {
    String os = System.getProperty("os.name").toLowerCase();
    if (os.contains("win")) {
      return new Font("Consolas", Font.PLAIN, points);
    } else if (os.contains("mac")) {
      return new Font("Menlo", Font.PLAIN, points);
    } else if (os.contains("linux")) {
      return new Font("Courier", Font.PLAIN, points);
    } else {
      return new Font("Courier", Font.PLAIN, points);
    }
  }

  void appendText (String line) {
    Document doc = codePane.getDocument();
    try {
      doc.insertString(doc.getLength(), line, null);
    } catch (BadLocationException ex) {
      ex.printStackTrace();
    }
  }

  String getText () {
    return codePane.getText();
  }

  void setText (String text) {
    codePane.setText(text);
  }
}
