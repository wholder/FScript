import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.text.*;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.UndoManager;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;

/**
 *  Simple Code Editing Text Pane with Undo and Redo
 *  Author: Wayne Holder, 2017
 *  License: MIT (https://opensource.org/licenses/MIT)
 */

class CodeEditPane extends JPanel {
  private static final short  NUMBERS_WIDTH = 25;
  private static final int    TAB_SIZE = 4;
  private JEditorPane         codePane;
  private Font                font = getCodeFont(12);

  class NumberedEditorKit extends StyledEditorKit {
    public ViewFactory getViewFactory () {
      return new NumberedViewFactory();
    }
  }

  class NumberedViewFactory implements ViewFactory {
    public View create (Element elem) {
      String kind = elem.getName();
      if (kind != null)
        if (kind.equals(AbstractDocument.ContentElementName)) {
          return new LabelView(elem);
        } else if (kind.equals(AbstractDocument.ParagraphElementName)) {
          return new NumberedParagraphView(elem);
        } else if (kind.equals(AbstractDocument.SectionElementName)) {
          return new BoxView(elem, View.Y_AXIS);
        } else if (kind.equals(StyleConstants.ComponentElementName)) {
          return new ComponentView(elem);
        } else if (kind.equals(StyleConstants.IconElementName)) {
          return new IconView(elem);
        }
      // default to text display
      return new LabelView(elem);
    }
  }

  class NumberedParagraphView extends ParagraphView {
    NumberedParagraphView (Element e) {
      super(e);
      this.setInsets((short) 0, (short) 0, (short) 0, (short) 0);
    }

    protected void setInsets (short top, short left, short bottom, short right) {
      super.setInsets(top, (short) (left + NUMBERS_WIDTH), bottom, right);
    }

    public void paintChild (Graphics g, Rectangle r, int n) {
      super.paintChild(g, r, n);
      int previousLineCount = getPreviousLineCount();
      int numberX = r.x - getLeftInset();
      int numberY = r.y + r.height - 5;
      g.drawString(Integer.toString(previousLineCount + n + 1), numberX, numberY);
    }

    int getPreviousLineCount () {
      int lineCount = 0;
      View parent = this.getParent();
      int count = parent.getViewCount();
      for (int i = 0; i < count; i++) {
        if (parent.getView(i) == this) {
          break;
        } else {
          lineCount += parent.getView(i).getViewCount();
        }
      }
      return lineCount;
    }
  }

  CodeEditPane (String title, boolean lineNumbers) {
    setLayout(new BorderLayout());
    codePane = new JEditorPane();
    codePane.setContentType("text/cpp");
    codePane.setFont(font);
    Border outside = BorderFactory.createTitledBorder(title);
    Border inside = BorderFactory.createEmptyBorder(5, 5, 5, 5);
    Border border = BorderFactory.createCompoundBorder(outside, inside);
    codePane.setBorder(border);
    codePane.setEditorKit(lineNumbers ? new NumberedEditorKit() : new StyledEditorKit());
    JScrollPane codeScrollpane = new JScrollPane(codePane);
    add(codeScrollpane, BorderLayout.CENTER);
    StyledDocument doc = (StyledDocument) codePane.getDocument();
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
          // ignore
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
          // ignore
        }
      }
    });
    // Setup tabs for StyledDocument
    BufferedImage img = new BufferedImage(5, 5, BufferedImage.TYPE_INT_RGB);
    FontMetrics fm = img.getGraphics().getFontMetrics(font);
    int charWidth = fm.charWidth('w');
    int tabWidth = charWidth * TAB_SIZE;
    TabStop[] tabs = new TabStop[35];
    for (int j = 0; j < tabs.length; j++) {
      int tab = j + 1;
      tabs[j] = new TabStop( tab * tabWidth );
    }
    TabSet tabSet = new TabSet(tabs);
    SimpleAttributeSet attributes = new SimpleAttributeSet();
    StyleConstants.setTabSet(attributes, tabSet);
    int length = doc.getLength();
    doc.setParagraphAttributes(0, length, attributes, false);
    codePane.updateUI();
    codePane.setEditable(true);
  }

  private static Font getCodeFont (int points) {
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
