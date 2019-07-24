
/*
 * FScriptRunner implements a simple GUI for editing and running FScript code
 *  Author: Wayne Holder, 2019
 *  License: MIT (https://opensource.org/licenses/MIT)
 */

import murlen.util.fscriptME.FSException;
import murlen.util.fscriptME.FScript;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.prefs.Preferences;

public class FScriptRunner extends JFrame {
  private transient Preferences prefs = Preferences.userRoot().node(this.getClass().getName());
  private CodeEditPane          output;
  private CodeEditPane          code;

  /**
   * BasicIO implements the external functions available to the FScript Interpreter
   * The FSCript test program FSTest.java shows how to implement additional external functions
   */
  class BasicIO extends FScript {

    public Object callFunction (String name, List<Object> param) throws FSException {
      switch (name) {
        case "print":
        case "println": {
          StringBuilder str = new StringBuilder();
          for (Object obj : param) {
            str.append(obj);
          }
          output.appendText(str.toString() + ("println".equals(name) ? "\n" : ""));
          break;
        }
        case "add":
          return (Integer) param.get(0) + (Integer) param.get(1);
        default:
          super.callFunction(name, param);
          break;
      }
      return 0;
    }
  }

  private FScriptRunner () throws IOException {
    super("FScript Runner");
    setLayout(new BorderLayout());
    JPanel panel = new JPanel(new GridLayout(2, 1));
    panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
    panel.add(output = new CodeEditPane("Output", false));
    panel.add(code = new CodeEditPane("FScript", true));
    add(panel, BorderLayout.CENTER);
    JButton run = new JButton("RUN");
    add(run, BorderLayout.SOUTH);
    run.addActionListener(ev -> {
      try {
        BasicIO runner = new BasicIO();
        runner.addLines(code.getText());
        output.setText("");
        Object ret = runner.runCode();
        output.appendText("Code returned: " + ret + "\n");
      } catch (FSException ex) {
        output.appendText(ex.getMessage() + "\n");
      }
    });
    code.setText(getFile(new File("loop.script")));
    setSize(640, 480);
    setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
    setLocation(prefs.getInt("window.x", 10), prefs.getInt("window.y", 10));
    // Track window resize/move events and save in prefs
    addComponentListener(new ComponentAdapter() {
      public void componentMoved (ComponentEvent ev)  {
        Rectangle bounds = ev.getComponent().getBounds();
        prefs.putInt("window.x", bounds.x);
        prefs.putInt("window.y", bounds.y);
      }
    });
    setVisible(true);
  }

  private static String getFile (File file) throws IOException {
    FileInputStream fis = new FileInputStream(file);
    byte[] data = new byte[fis.available()];
    //noinspection ResultOfMethodCallIgnored
    fis.read(data);
    fis.close();
    return new String(data, StandardCharsets.UTF_8);
  }

  public static void main (String[] args) throws IOException {
    new FScriptRunner();
  }
}
